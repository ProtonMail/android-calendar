package me.proton.android.calendar.domain.usecase

import biweekly.parameter.ParticipationStatus
import biweekly.util.ICalDate
import biweekly.util.ICalDateFormat
import biweekly.util.Recurrence
import me.proton.android.calendar.common.EventDeletionReason
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.EventUtilsImpl.setRecurrenceId
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustOutgoingAllDayEvent
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustStartEndTimeZones
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustToWeekStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.clone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.iCalTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isDateTimeTheSame
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject

class HandleSaveUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val transformEventUseCase: TransformEventUseCase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val handleDeleteUseCase: HandleDeleteUseCase,
    private val sendEmailUseCase: SendEmailUseCase,
    private val eventDecryptor: EventDecryptor
) {

    sealed class HandleSaveOptionResult {
        class Success(val event: Event) : HandleSaveOptionResult()
        class Error(val error: UseCase.Result) : HandleSaveOptionResult()
    }

    suspend fun handleSave(
        editOption: EventEditDeleteOption? = null,
        occurrenceNumber: Int,
        timeFormatIs24Hours: Boolean,
        sendPreferences: Map<Email, SendPreferences>,
        event: Event,
        originalDbEvent: Event?,
        userSettings: UserSettingsEntity,
        eventTimeZoneId: String,
        userId: UserId,
        rruleManuallyEdited: Boolean,
        isCreate: Boolean,
        sendEmailUpdate: Boolean?
    ): UseCase.Result {

        if (event.isAllDay()) {
            event.iCalendar.adjustOutgoingAllDayEvent(event.defaultTimeZone!!)
        } else if (!event.isAllDay()) {
            event.iCalendar.adjustStartEndTimeZones(eventTimeZoneId, event.defaultTimeZone!!)
        }

        // we adjust RRule to WKST if event is newly created or if the recurrence rule is modified when editing
        if (isCreate || (!isCreate && rruleManuallyEdited)) {
            event.iCalEvent.recurrenceRule?.adjustToWeekStart(userSettings.weekStartDayOfWeek())
        }

        val eventEntity = calendarsRepository.selectEventEntity(event.id)
        val dbEvent = eventEntity?.let { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
            eventDecryptor.decrypt(it)
        } else {
            transformEventUseCase.execute(it)
        } }
        val immutableOriginalDbEvent = originalDbEvent
        val dbEventStartDate = dbEvent?.getStart(event.defaultTimeZone!!)
        val originalDbEventStartDate = immutableOriginalDbEvent?.getStart(event.defaultTimeZone!!)
        val dbEventWithOccurrence = dbEvent?.let { Event.withOccurrence(it, occurrenceNumber, event.defaultTimeZone!!) }
        val dbEventWithOccurrenceStartDate = dbEventWithOccurrence?.getStart(event.defaultTimeZone!!)

        val currentEventSequence = event.iCalEvent.sequence.value

        event.handleSequence(dbEvent, dbEventWithOccurrence, eventTimeZoneId)

        val handleOptionResult = when (editOption) {
            EventEditDeleteOption.THIS_EVENT -> {
                handleOptionThisEvent(
                    userId,
                    event,
                    dbEvent,
                    dbEventWithOccurrenceStartDate,
                    immutableOriginalDbEvent,
                    dbEventWithOccurrence
                )
            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {
                handleOptionThisEventAndFuture(
                    userId,
                    event,
                    dbEvent,
                    dbEventWithOccurrenceStartDate,
                    immutableOriginalDbEvent,
                    dbEventStartDate,
                    occurrenceNumber
                )
            }
            EventEditDeleteOption.ALL_EVENTS -> {
                handleOptionAllEvents(
                    userId,
                    event,
                    dbEvent,
                    dbEventWithOccurrence,
                    dbEventWithOccurrenceStartDate,
                    immutableOriginalDbEvent,
                    dbEventStartDate,
                    originalDbEventStartDate,
                    occurrenceNumber,
                    rruleManuallyEdited
                )
            }
            else -> HandleSaveOptionResult.Success(event) // else no special changes for regular event, just overwrite everything
        }

        val newEvent: Event = when (handleOptionResult) {
            is HandleSaveOptionResult.Success -> {
                handleOptionResult.event
            }
            is HandleSaveOptionResult.Error -> {
                return handleOptionResult.error
            }
        }

        // TODO make sure at least current day-of-week is in byDay list, when start date is changed but recurrence rule is not

        val addedAttendees = event.iCalEvent.attendees.filter { eventAttendee ->
            immutableOriginalDbEvent?.iCalEvent?.attendees?.map { it.extractEmail() }?.contains(eventAttendee.extractEmail()) == false
        }
        val removedAttendees = immutableOriginalDbEvent?.iCalEvent?.attendees?.filter { dbEventAttendee ->
            !event.iCalEvent.attendees.map { it.extractEmail() }.contains(dbEventAttendee.extractEmail())
        } ?: emptyList()

        return if (!isCreate && (!newEvent.iCalEvent.attendees.isNullOrEmpty() || !immutableOriginalDbEvent?.iCalEvent?.attendees.isNullOrEmpty()) && (sendEmailUpdate == null || sendEmailUpdate == true)) {
            // Update event and change attendees
            // Only keep attendees with valid send preferences
            val addedAttendeesToNotify = sendPreferences.filter { sendPrefs ->
                addedAttendees.any { sendPrefs.key == it.extractEmail() }
            }
            if (addedAttendees.isNotEmpty()) {
                val notifyAddedAttendeesResult = notifyAddedAttendees(
                    userId,
                    newEvent,
                    addedAttendeesToNotify,
                    event.defaultTimeZone!!,
                    timeFormatIs24Hours
                )
                if (notifyAddedAttendeesResult !is UseCase.Result.Success<*>) {
                    // Send email to new attendees is blocking for that flow
                    return notifyAddedAttendeesResult
                }
            }
            if (removedAttendees.isNotEmpty()) {
                immutableOriginalDbEvent?.let {
                    // Only keep attendees with valid send preferences
                    val removedAttendeesToNotify = sendPreferences.filter { sendPrefs ->
                        removedAttendees.any { sendPrefs.key == it.extractEmail() }
                    }
                    notifyRemovedAttendees(
                        userId,
                        immutableOriginalDbEvent,
                        removedAttendeesToNotify,
                        event.defaultTimeZone!!,
                        timeFormatIs24Hours
                    )
                } ?: run {
                    logger.e("HandleSaveUseCase handleSave originalDbEvent was null for notifyRemovedAttendees")
                }
            }
            val existingAttendees = newEvent.iCalEvent.attendees.filterNot { existingAttendee ->
                // Remove newly added attendees from the list as we just sent them an invite earlier.
                addedAttendees.any { it.extractEmail() == existingAttendee.extractEmail() }
            }
            val attendeesToNotify = sendPreferences.filter { sendPrefs ->
                // Make sure the send prefs matches event's attendees.
                existingAttendees.any { sendPrefs.key == it.extractEmail() }
            }
            // Update the event with attendee changes
            if (attendeesToNotify.isEmpty()) editCreateEvent(userId, newEvent, sendPreferences = addedAttendeesToNotify)
            else {
                resetParticipationStatus(currentEventSequence, newEvent, attendeesToNotify)
                editEventWithAttendees(
                    userId,
                    newEvent,
                    isCreate = false,
                    attendeesToNotify,
                    addedAttendeesToNotify,
                    event.defaultTimeZone!!,
                    timeFormatIs24Hours,
                    sendEmailUpdate
                )
            }
        } else if (!isCreate && sendEmailUpdate == false && (addedAttendees.isNotEmpty() || removedAttendees.isNotEmpty())) {
            // Just change attendees, no update for the existing event
            // Only keep attendees with valid send preferences
            val addedAttendeesToNotify = sendPreferences.filter { sendPrefs ->
                addedAttendees.any { sendPrefs.key == it.extractEmail() }
            }
            if (addedAttendees.isNotEmpty()) {
                val notifyAddedAttendeesResult = notifyAddedAttendees(
                    userId,
                    newEvent,
                    addedAttendeesToNotify,
                    event.defaultTimeZone!!,
                    timeFormatIs24Hours
                )
                if (notifyAddedAttendeesResult !is UseCase.Result.Success<*>) {
                    // Send email to new attendees is blocking for that flow
                    return notifyAddedAttendeesResult
                }
            }
            if (removedAttendees.isNotEmpty()) {
                immutableOriginalDbEvent?.let {
                    // Only keep attendees with valid send preferences
                    val removedAttendeesToNotify = sendPreferences.filter { sendPrefs ->
                        removedAttendees.any { sendPrefs.key == it.extractEmail() }
                    }
                    notifyRemovedAttendees(
                        userId,
                        immutableOriginalDbEvent,
                        removedAttendeesToNotify,
                        event.defaultTimeZone!!,
                        timeFormatIs24Hours
                    )
                } ?: run {
                    logger.e("HandleSaveUseCase handleSave originalDbEvent was null for notifyRemovedAttendees")
                }
            }
            // Update the event with attendee changes
            return editCreateEvent(userId, newEvent, sendPreferences = addedAttendeesToNotify)
        } else if (isCreate && !newEvent.iCalEvent.attendees.isNullOrEmpty()) {
            // Create event with attendees
            val attendeesToNotify = sendPreferences.filter { sendPrefs ->
                newEvent.iCalEvent.attendees.any { sendPrefs.key == it.extractEmail() }
            }
            createEventWithAttendees(
                userId,
                newEvent,
                isCreate = true,
                attendeesToNotify,
                event.defaultTimeZone!!,
                timeFormatIs24Hours
            )
        } else {
            editCreateEvent(userId, newEvent, dbEvent?.calendar?.id)
        }
    }

    private suspend fun handleOptionThisEvent(
        userId: UserId,
        event: Event,
        dbEvent: Event?,
        dbEventWithOccurrenceStartDate: ZonedDateTime?,
        immutableOriginalDbEvent: Event?,
        dbEventWithOccurrence: Event?
    ): HandleSaveOptionResult {

        return when {
            dbEvent?.isRecurring() == true -> {

                // TODO BUG:
                // 1. event is a regular event
                // 2. edit it and add rrule
                // 3. we get into this nullcheck here!

                if (dbEventWithOccurrenceStartDate == null) {
                    return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: dbEventWithOccurrenceStartDate == null"))
                }

                val editOriginalEventResult = handleOriginalEventNullSequence(userId, dbEvent)
                if (editOriginalEventResult is UseCase.Result.Error) {
                    return HandleSaveOptionResult.Error(
                        UseCase.Result.Error(
                            "HandleSaveUseCase: handleOriginalEventNullSequence error editing original event ${editOriginalEventResult.message}",
                            error = editOriginalEventResult.error,
                            userErrorMessage = editOriginalEventResult.userErrorMessage
                        )
                    )
                } else if (editOriginalEventResult is UseCase.Result.InvalidParams) {
                    return HandleSaveOptionResult.Error(
                        UseCase.Result.InvalidParams(
                            "HandleSaveUseCase: handleOriginalEventNullSequence error editing original event ${editOriginalEventResult.message}",
                            userErrorMessage = editOriginalEventResult.userErrorMessage
                        )
                    )
                }

                val eventToCreate = Event.from(event, id = ICalUtilsImpl.generateOfflineEventId())
                // event.uid is still the same

                // delete all recurring properties
                eventToCreate.iCalEvent.recurrenceRule = null
                eventToCreate.iCalEvent.exceptionDates.clear()

                // TODO dtstart/end is incorrect, when creating new event that is in different timezone -- we should normalize the time back to original!!!!!
                val timeHasBeenChanged = !eventToCreate.iCalendar.isDateTimeTheSame(dbEventWithOccurrence?.iCalendar)
                if (timeHasBeenChanged) { // TODO it looks like we always use occurrence start date anyway
                    logger.d("time has been changed")
                    // eventToCreate.setRecurrenceId(dbEventWithOccurrenceStartDate, !eventToCreate.isAllDay())
                } else {
                    logger.d("time is the same")
                }

                // Make sure timezone matches parent timezone
                if (!dbEvent.isAllDay() && dbEvent.iCalendar.iCalTimeZone(dbEvent.iCalEvent.dateStart).id != event.defaultTimeZone!!) {
                    eventToCreate.setRecurrenceId(
                        dbEventWithOccurrenceStartDate.withZoneSameInstant(
                            ZoneId.of(
                                dbEvent.iCalendar.iCalTimeZone(
                                    dbEvent.iCalEvent.dateStart
                                ).id
                            )
                        ),
                        !dbEvent.isAllDay()
                    )
                } else {
                    eventToCreate.setRecurrenceId(dbEventWithOccurrenceStartDate, !dbEvent.isAllDay())
                }

                HandleSaveOptionResult.Success(eventToCreate)
            }
            dbEvent?.isSingleEdit() == true -> {
                immutableOriginalDbEvent?.let {
                    val editOriginalEventResult = handleOriginalEventNullSequence(userId, it)
                    if (editOriginalEventResult is UseCase.Result.Error) {
                        return HandleSaveOptionResult.Error(
                            UseCase.Result.Error(
                                "HandleSaveUseCase: handleOriginalEventNullSequence error editing original event ${editOriginalEventResult.message}",
                                error = editOriginalEventResult.error,
                                userErrorMessage = editOriginalEventResult.userErrorMessage
                            )
                        )
                    } else if (editOriginalEventResult is UseCase.Result.InvalidParams) {
                        return HandleSaveOptionResult.Error(
                            UseCase.Result.InvalidParams(
                                "HandleSaveUseCase: handleOriginalEventNullSequence error editing original event ${editOriginalEventResult.message}",
                                userErrorMessage = editOriginalEventResult.userErrorMessage
                            )
                        )
                    }
                }

                val eventToCreate = Event.from(event)
                eventToCreate.iCalEvent.recurrenceRule = null
                eventToCreate.iCalEvent.exceptionDates.clear()

                HandleSaveOptionResult.Success(eventToCreate)
            }
            else -> {
                HandleSaveOptionResult.Success(event) // else no special changes for regular event, just overwrite everything
            }
        }
    }

    private suspend fun handleOptionThisEventAndFuture(
        userId: UserId,
        event: Event,
        dbEvent: Event?,
        dbEventWithOccurrenceStartDate: ZonedDateTime?,
        immutableOriginalDbEvent: Event?,
        dbEventStartDate: ZonedDateTime?,
        occurrenceNumber: Int
    ): HandleSaveOptionResult {

        // TODO THIS NEEDS TO BE FIXED, WE PROBABLY CAN'T FIND EVENTS IN DB
        if (dbEvent == null) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: dbEvent == null"))
        }
        if (!dbEvent.isSingleEdit() && dbEventWithOccurrenceStartDate == null) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: dbEventWithOccurrenceStartDate == null"))
        }
        if (dbEvent.isSingleEdit() && (immutableOriginalDbEvent == null || dbEventStartDate == null)) {
            return HandleSaveOptionResult.Error(
                UseCase.Result.Error(
                    "HandleSaveUseCase: ${
                        if (immutableOriginalDbEvent == null) "originalDbEvent == null"
                        else "dbEventStartDate == null"
                    }"
                )
            )
        }

        // delete single edits starting with just edited occurrence / single edit
        val eventId = if (dbEvent.isSingleEdit()) immutableOriginalDbEvent!!.id else event.id
        val deleteStartDate =
            if (dbEvent.isSingleEdit()) dbEventStartDate!!.minusNanos(1) else dbEventWithOccurrenceStartDate!!.minusNanos(
                1
            )
        val deleteSingleEditsResult = handleDeleteUseCase.handleDeleteSingleEdits(userId, eventId, deleteStartDate)
        deleteSingleEditsResult.ifSuccessAndLogErrors(logger) { }
        if (deleteSingleEditsResult is UseCase.Result.Error) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: failed to delete single edits: ${deleteSingleEditsResult.message}"))
        } else if (deleteSingleEditsResult is UseCase.Result.InvalidParams) {
            return HandleSaveOptionResult.Error(UseCase.Result.InvalidParams("HandleSaveUseCase: failed to delete single edits:  ${deleteSingleEditsResult.message}"))
        }

        // update original event:
        // - change COUNT to ((current occurrence number) - 1)
        // OR
        // - change UNTIL equal to (previous occurrence from just edited).endDate
        val dbEventToCopy = if (dbEvent.isSingleEdit()) immutableOriginalDbEvent else dbEvent
        val dbEventToUpdate = Event.from(dbEventToCopy!!)
        // Bump sequence for original event
        dbEventToUpdate.iCalEvent.setSequence((dbEventToUpdate.iCalEvent.sequence?.value ?: 0) + 1)
        val timezone = event.defaultTimeZone!!
        dbEventToUpdate.iCalEvent.recurrenceRule?.value?.let {
            dbEventToUpdate.iCalEvent.setRecurrenceRule(
                Recurrence.Builder(dbEventToUpdate.iCalEvent.recurrenceRule.value)
                    // count = 0 will not happen because this edit option is not available for first occurrence
                    .count(
                        if (it.count != null) occurrenceNumber - 1
                        else null
                    )
                    .until(
                        // Prioritize count over until
                        if (it.count != null) {
                            null
                        } else if (dbEventToUpdate.isAllDay()) {
                            ICalDate(
                                dbEventToUpdate.generateOccurrence(
                                    occurrenceNumber,
                                    timezone
                                )!!.startDateTime
                                    .minusDays(1)
                                    .with(ChronoField.HOUR_OF_DAY, 0)
                                    .toLocalDate()
                                    .toDate(ZoneId.systemDefault().id), false
                            )
                        } else {
                            // 1 second to midnight on the start-day of previous original occurrence
                            ICalDate(
                                Date.from(
                                    ZonedDateTime.of(
                                        dbEventToUpdate.generateOccurrence(
                                            occurrenceNumber,
                                            timezone
                                        )!!.startDateTime.minusDays(1).toLocalDate(),
                                        LocalTime.of(23, 59, 59), ZoneId.of(timezone)
                                    ).toInstant()
                                ), true
                            )
                        }
                    )
                    .build()
            )
        }

        val editOriginalEventResult =
            editCreateEventUseCase.execute(userId, dbEventToUpdate)
        if (editOriginalEventResult is UseCase.Result.Error) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error(
                "HandleSaveUseCase: error editing original event:  ${editOriginalEventResult.message}",
                error = editOriginalEventResult.error,
                userErrorMessage = editOriginalEventResult.userErrorMessage
            ))
        } else if (editOriginalEventResult is UseCase.Result.InvalidParams) {
            return HandleSaveOptionResult.Error(UseCase.Result.InvalidParams(
                "HandleSaveUseCase: error editing original event:  ${editOriginalEventResult.message}",
                userErrorMessage = editOriginalEventResult.userErrorMessage
            ))
        }

        // TODO delete exdates after this occurrence?

        // --------------------------------------

        val eventToCreate = Event.from(
            event,
            id = ICalUtilsImpl.generateOfflineEventId(),
            iCalendar = event.iCalendar.clone().apply {
                this.events.first().apply {
                    setUid(
                        ICalUtilsImpl.generateProtonUid(
                            event.uid,
                            ICalDateFormat.DATE_TIME_BASIC_WITHOUT_TZ.format(
                                ICalUtilsImpl.eventStartZonedDateTimeToDate(
                                    if (dbEvent.isSingleEdit()) dbEventStartDate!! else dbEventWithOccurrenceStartDate!!,
                                    dbEvent.isAllDay()
                                )
                            )
                        )
                    )
                    val nullDate: Date? = null
                    setRecurrenceId(nullDate)
                    event.iCalEvent.recurrenceRule?.value?.let {
                        setRecurrenceRule(
                            Recurrence.Builder(event.iCalEvent.recurrenceRule.value)
                                .count(
                                    if (it.count != null) {
                                        val originalCount = dbEventToCopy.iCalEvent.recurrenceRule?.value?.count
                                        if (originalCount != null && originalCount == it.count) {
                                            it.count - (occurrenceNumber - 1)
                                        } else {
                                            it.count
                                        }
                                    } else null
                                )
                                // UNTIL is copied from event's RRULE
                                .build()
                        )
                    }
                }
            }
        )
        eventToCreate.iCalEvent.exceptionDates.clear()
        if (dbEvent.isSingleEdit()) eventToCreate.iCalEvent.recurrenceId = null

        return HandleSaveOptionResult.Success(eventToCreate)
    }

    private suspend fun handleOptionAllEvents(
        userId: UserId,
        event: Event,
        dbEvent: Event?,
        dbEventWithOccurrence: Event?,
        dbEventWithOccurrenceStartDate: ZonedDateTime?,
        immutableOriginalDbEvent: Event?,
        dbEventStartDate: ZonedDateTime?,
        originalDbEventStartDate: ZonedDateTime?,
        occurrenceNumber: Int,
        rruleManuallyEdited: Boolean
    ): HandleSaveOptionResult {

        // All events expected behavior :
        // Single deletions and single edits are always reset when doing this operation.
        // - If Event start date : only time changed (same day), and RRule not changed
        //      -> update original event time with event value
        // - If Event start date : date changed (different day) / RRule changed
        //      -> update original event date and time with event values

        if (dbEvent?.isSingleEdit() == true && immutableOriginalDbEvent == null) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: Edit all events: originalEventStartDate was null for single edit"))
        }

        // delete all single edits
        val originalEventId =
            if (dbEvent?.isSingleEdit() == true) immutableOriginalDbEvent?.id
            else event.id
        val originalEventStartDate =
            if (dbEvent?.isSingleEdit() == true) originalDbEventStartDate
            else dbEventStartDate

        if (originalEventStartDate == null) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: Edit all events: originalEventStartDate was null"))
        }
        if (originalEventId == null) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: Edit all events: originalEventId was null"))
        }

        val originalEventWithOccurrence =
            if (dbEvent?.isSingleEdit() == true) immutableOriginalDbEvent?.let {
                Event.withOccurrence(
                    it,
                    occurrenceNumber,
                    event.defaultTimeZone!!
                )
            }
            else dbEventWithOccurrence

        if (originalEventWithOccurrence == null) {
            return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: Edit all events: originalEventWithOccurrence was null"))
        }

        // delete all single deletions
        event.iCalEvent.exceptionDates.clear()

        // TWO SEPARATE THINGS:
        // - if event.isAllDay != dbEvent.isAllDay() it means there was a conversion all-day-part-day
        // - the same DAY but different TIME

        val hasDayChanged =
            (if (dbEvent?.isSingleEdit() == true) {
                dbEventStartDate
            } else {
                dbEventWithOccurrenceStartDate
            })?.truncatedTo(ChronoUnit.DAYS) != event.getStart(event.defaultTimeZone!!).truncatedTo(ChronoUnit.DAYS)

        val handleResult = if (!hasDayChanged && !rruleManuallyEdited) {

            // update the original event's DTSTART only with new time (leave day the same)

            // TODO Try to reduce duplicated code between single edit and occurrence logic
            if (dbEvent?.isSingleEdit() == true) {
                if (immutableOriginalDbEvent == null) {
                    return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: Edit all events: dbEvent was null"))
                }

                // TODO Remove duplicated code
                val deleteSingleEditsResult =
                    handleDeleteUseCase.handleDeleteSingleEdits(userId, originalEventId, originalEventStartDate.minusNanos(1))
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) { }
                if (deleteSingleEditsResult is UseCase.Result.Error) {
                    return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: error deleting single edits:  ${deleteSingleEditsResult.message}"))
                } else if (deleteSingleEditsResult is UseCase.Result.InvalidParams) {
                    return HandleSaveOptionResult.Error(UseCase.Result.InvalidParams("HandleSaveUseCase: error deleting single edits:  ${deleteSingleEditsResult.message}"))
                }

                val newEvent = Event.from(event, id = immutableOriginalDbEvent.id)
                newEvent.iCalEvent.recurrenceId = null
                newEvent.iCalEvent.uid = immutableOriginalDbEvent.iCalEvent.uid

                // For single edit we need to calculate span to add days to original event date end
                val eventSpan = ChronoUnit.DAYS.between(
                    newEvent.getStart(event.defaultTimeZone!!).toLocalDate(),
                    newEvent.getEnd(event.defaultTimeZone!!).toLocalDate()
                )
                if (event.isAllDay()) {
                    // We use original event LocalDate
                    HandleSaveOptionResult.Success(newEvent.also {
                        it.iCalEvent.setStart(
                            immutableOriginalDbEvent.getStart(event.defaultTimeZone!!).toLocalDate()
                        )
                        it.iCalEvent.setEnd(
                            immutableOriginalDbEvent.getStart(event.defaultTimeZone!!).plusDays(eventSpan)
                                .toLocalDate()
                        )
                    })
                } else {
                    // We use currently edited event LocalTime but keep original event LocalDate
                    HandleSaveOptionResult.Success(newEvent.also {
                        it.iCalEvent.setStart(
                            immutableOriginalDbEvent.getStart(event.defaultTimeZone!!).toLocalDate(),
                            newEvent.getStart(event.defaultTimeZone!!).toLocalTime(),
                            event.defaultTimeZone
                        )
                        it.iCalEvent.setEnd(
                            immutableOriginalDbEvent.getStart(event.defaultTimeZone!!).plusDays(eventSpan)
                                .toLocalDate(),
                            newEvent.getEnd(event.defaultTimeZone!!).toLocalTime(),
                            event.defaultTimeZone
                        )
                    })
                }
            } else {
                if (dbEvent == null) {
                    return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: Edit all events: dbEvent was null"))
                }

                // TODO Remove duplicated code
                val deleteSingleEditsResult =
                    handleDeleteUseCase.handleDeleteSingleEdits(userId, originalEventId, originalEventStartDate.minusNanos(1))
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) { }
                if (deleteSingleEditsResult is UseCase.Result.Error) {
                    return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: error deleting single edits:  ${deleteSingleEditsResult.message}"))
                } else if (deleteSingleEditsResult is UseCase.Result.InvalidParams) {
                    return HandleSaveOptionResult.Error(UseCase.Result.InvalidParams("HandleSaveUseCase: error deleting single edits:  ${deleteSingleEditsResult.message}"))
                }

                val eventSpan = ChronoUnit.DAYS.between(
                    event.getStart(event.defaultTimeZone!!).toLocalDate(),
                    event.getEnd(event.defaultTimeZone!!).toLocalDate()
                )
                if (event.isAllDay()) {
                    HandleSaveOptionResult.Success(event.also {
                        it.iCalEvent.setStart(dbEvent.getStart(event.defaultTimeZone!!).toLocalDate())
                        it.iCalEvent.setEnd(
                            dbEvent.getStart(event.defaultTimeZone!!).plusDays(eventSpan).toLocalDate()
                        )
                    })
                } else {
                    HandleSaveOptionResult.Success(event.also {
                        it.iCalEvent.setStart(
                            dbEvent.getStart(event.defaultTimeZone!!).toLocalDate(),
                            event.getStart(event.defaultTimeZone!!).toLocalTime(),
                            event.defaultTimeZone
                        )
                        it.iCalEvent.setEnd(
                            dbEvent.getStart(event.defaultTimeZone!!).plusDays(eventSpan).toLocalDate(),
                            event.getEnd(event.defaultTimeZone!!).toLocalTime(),
                            event.defaultTimeZone
                        )
                    })
                }
            }

        } else {
            // update the original event's DTSTART with date and time
            //  which means no changes to just edited event, but it will overwrite the original event

            // TODO Remove duplicated code
            val deleteSingleEditsResult =
                handleDeleteUseCase.handleDeleteSingleEdits(userId, originalEventId, originalEventStartDate.minusNanos(1))
            deleteSingleEditsResult.ifSuccessAndLogErrors(logger) { }
            if (deleteSingleEditsResult is UseCase.Result.Error) {
                return HandleSaveOptionResult.Error(UseCase.Result.Error("HandleSaveUseCase: error deleting single edits:  ${deleteSingleEditsResult.message}"))
            } else if (deleteSingleEditsResult is UseCase.Result.InvalidParams) {
                return HandleSaveOptionResult.Error(UseCase.Result.InvalidParams("HandleSaveUseCase: error deleting single edits:  ${deleteSingleEditsResult.message}"))
            }

            if (dbEvent?.isSingleEdit() == true) {

                // clear recurrenceId and use original event id since single edit will replace original event
                val newEvent = Event.from(event, id = originalEventId)
                newEvent.iCalEvent.recurrenceId = null
                newEvent.iCalEvent.exceptionDates.clear()

                HandleSaveOptionResult.Success(newEvent)
            } else {
                HandleSaveOptionResult.Success(event)
            }
        }

        dbEvent?.let {
            val wasSequenceUpdated = it.iCalEvent.sequence?.value != handleResult.event.iCalEvent.sequence?.value
            val wasStartChanged = it.getStart("UTC") != handleResult.event.getStart("UTC")

            // bump SEQUENCE if it was not updated but should have been
            // because of different DTSTART of original Event and just-edited
            // that is about to overwrite the original one
            if (!wasSequenceUpdated && wasStartChanged) {
                handleResult.event.iCalEvent.setSequence((handleResult.event.iCalEvent.sequence?.value ?: 0) + 1)
            }
        }

        return handleResult
    }

    private suspend fun editEventWithAttendees(
        userId: UserId,
        newEvent: Event,
        isCreate: Boolean,
        attendeesToNotify: Map<Email, SendPreferences>,
        addedAttendeesToNotify: Map<Email, SendPreferences>,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean,
        sendEmailUpdate: Boolean?
    ): UseCase.Result {
        val sendEmailResult =
            if (sendEmailUpdate == true && attendeesToNotify.isEmpty()) {
                // When editing an invitation, we still update the event if no participants can be notified.
                UseCase.Result.Success<Unit>()
            } else {
                sendEmailUseCase.sendInviteToAttendees(
                    userId,
                    newEvent,
                    isCreate,
                    newEvent,
                    attendeesToNotify,
                    defaultTimeZone,
                    timeFormatIs24Hours,
                    sendEmailUpdate
                )
            }
        sendEmailResult.ifSuccessAndLogErrors(logger) { }

        if (sendEmailResult is UseCase.Result.Error) {
            return if (sendEmailResult.error == UseCase.Error.Crypto.UserAddressInvalidForEncryption) {
                UseCase.Result.Error(
                    "HandleSaveUseCase: error in send email (edit with attendees): ${sendEmailResult.message}",
                    UseCase.Error.Crypto.UserAddressInvalidForEncryption
                )
            } else {
                UseCase.Result.Error(
                    "HandleSaveUseCase: error in send email (edit with attendees): ${sendEmailResult.message}",
                    UseCase.Error.HandleSave.EditSendEmail
                )
            }
        } else if (sendEmailResult is UseCase.Result.InvalidParams) {
            return UseCase.Result.Error(
                "HandleSaveUseCase: invalid params in send email: ${sendEmailResult.message}",
                UseCase.Error.HandleSave.EditSendEmail
            )
        }

        return editCreateEvent(userId, newEvent, sendPreferences = addedAttendeesToNotify)
    }

    private suspend fun notifyAddedAttendees(
        userId: UserId,
        newEvent: Event,
        sendPreferences: Map<Email, SendPreferences>,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean,
    ): UseCase.Result {
        val sendEmailResult =
            if (sendPreferences.isEmpty()) {
                // When editing an invitation, we still update the event if no participants can be notified.
                UseCase.Result.Success<Unit>()
            } else {
                sendEmailUseCase.sendInviteToAttendees(
                    userId,
                    newEvent,
                    isCreate = false,
                    newEvent,
                    sendPreferences,
                    defaultTimeZone,
                    timeFormatIs24Hours,
                    sendEmailUpdate = false
                )
            }
        sendEmailResult.ifSuccessAndLogErrors(logger) { }

        if (sendEmailResult is UseCase.Result.Error) {
            return if (sendEmailResult.error == UseCase.Error.Crypto.UserAddressInvalidForEncryption) {
                UseCase.Result.Error(
                    "HandleSaveUseCase: error in send email (edit with attendees): ${sendEmailResult.message}",
                    UseCase.Error.Crypto.UserAddressInvalidForEncryption
                )
            } else {
                UseCase.Result.Error(
                    "HandleSaveUseCase: error in send email (edit with attendees): ${sendEmailResult.message}",
                    UseCase.Error.HandleSave.EditSendEmail
                )
            }
        } else if (sendEmailResult is UseCase.Result.InvalidParams) {
            return UseCase.Result.Error(
                "HandleSaveUseCase: invalid params in send email: ${sendEmailResult.message}",
                UseCase.Error.HandleSave.EditSendEmail
            )
        }

        return sendEmailResult
    }

    private suspend fun notifyRemovedAttendees(
        userId: UserId,
        originalEvent: Event,
        sendPreferences: Map<Email, SendPreferences>,
        newEventDefaultTimeZone: String,
        timeFormatIs24Hours: Boolean,
    ): UseCase.Result {
        val sendCancellationResult =
            if (sendPreferences.isEmpty()) {
                // When editing an invitation, we still update the event if no participants can be notified.
                UseCase.Result.Success<Unit>()
            } else {
                // Try to get original event time zone, if it fails use the updated event's time zone.
                val defaultTimeZone = originalEvent.iCalendar.timezoneInfo?.getTimezone(
                    originalEvent.iCalEvent.dateStart
                )?.timeZone?.id ?: newEventDefaultTimeZone
                sendEmailUseCase.sendCancellationToAttendees(
                    userId,
                    originalEvent,
                    sendPreferences.keys.toList(),
                    sendPreferences,
                    defaultTimeZone,
                    timeFormatIs24Hours
                )
            }
        sendCancellationResult.ifSuccessAndLogErrors(logger) { }

        if (sendCancellationResult is UseCase.Result.Error) {
            return UseCase.Result.Error(
                "HandleSaveUseCase: notifyRemovedAttendees error in send email: ${sendCancellationResult.message}",
                sendCancellationResult.error
            )
        } else if (sendCancellationResult is UseCase.Result.InvalidParams) {
            return UseCase.Result.InvalidParams(
                "HandleSaveUseCase: notifyRemovedAttendees invalid params in send email: ${sendCancellationResult.message}"
            )
        }

        return sendCancellationResult
    }

    private suspend fun createEventWithAttendees(
        userId: UserId,
        newEvent: Event,
        isCreate: Boolean,
        sendPreferences: Map<Email, SendPreferences>,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean
    ): UseCase.Result {
        val createEventResult = editCreateEvent(userId, newEvent)

        return if (createEventResult is UseCase.Result.Success<*>) {
            createEventResult.returnValue.tryCast<List<String>> {
                if (this.isNullOrEmpty()) {
                    return UseCase.Result.Error(
                        "HandleSaveUseCase: error in send mail: createEventResult returned null or empty new event ID",
                        UseCase.Error.HandleSave.CreateSendEmail
                    )
                }

                val sendEmailResult = sendEmailUseCase.sendInviteToAttendees(
                    userId,
                    Event.from(newEvent, id = this.first()),
                    isCreate,
                    null,
                    sendPreferences,
                    defaultTimeZone,
                    timeFormatIs24Hours
                )

                if (sendEmailResult is UseCase.Result.Error) {
                    return if (sendEmailResult.error == UseCase.Error.Crypto.UserAddressInvalidForEncryption) {
                        UseCase.Result.Error(
                            "HandleSaveUseCase: error in send mail (create with attendees): ${sendEmailResult.message}",
                            UseCase.Error.Crypto.UserAddressInvalidForEncryption
                        )
                    } else {
                        UseCase.Result.Error(
                            "HandleSaveUseCase: error in send mail (create with attendees): ${sendEmailResult.message}",
                            UseCase.Error.HandleSave.CreateSendEmail
                        )
                    }
                } else if (sendEmailResult is UseCase.Result.InvalidParams) {
                    return UseCase.Result.Error(
                        "HandleSaveUseCase: invalid params in send mail: ${sendEmailResult.message}",
                        UseCase.Error.HandleSave.CreateSendEmail
                    )
                }
            }

            UseCase.Result.Success<Unit>()
        } else {
            createEventResult
        }
    }

    /**
     * @param [oldCalendarId] provide if the calendar has just been changed
     */
    private suspend fun editCreateEvent(
        userId: UserId,
        newEvent: Event,
        oldCalendarId: String? = null,
        sendPreferences: Map<Email, SendPreferences> = emptyMap()
    ): UseCase.Result {

        val createEventResult = editCreateEventUseCase.execute(
            userId,
            newEvent,
            oldCalendarId = oldCalendarId ?: newEvent.calendar.id,
            sendPreferences = sendPreferences
        )

        return when (createEventResult) {
            is UseCase.Result.Error -> UseCase.Result.Error(
                "HandleSaveUseCase: error in editCreateEvent event: ${createEventResult.message}",
                createEventResult.error,
                userErrorMessage = createEventResult.userErrorMessage
            )
            is UseCase.Result.InvalidParams -> UseCase.Result.InvalidParams(
                "HandleSaveUseCase:invalid params in create event: ${createEventResult.message}",
                userErrorMessage = createEventResult.userErrorMessage
            )
            is UseCase.Result.Success<*> -> {
                val isCalendarBeingChanged = oldCalendarId != null && oldCalendarId != newEvent.calendar.id
                if (isCalendarBeingChanged) {
                    val deleteOldEventResult = handleDeleteUseCase.handleDelete(userId, newEvent.id, newEvent.calendar.id, EventEditDeleteOption.THIS_EVENT, occurrenceNumber = null, deletionReason = EventDeletionReason.CalendarChange)
                    if (deleteOldEventResult is UseCase.Result.Success<*>) createEventResult else deleteOldEventResult
                } else {
                    createEventResult
                }
            }
        }
    }

    private fun Event.handleSequence(dbEvent: Event?, dbEventWithOccurrence: Event? = null, eventTimeZoneId: String) {
        // Bump sequence when event is new or following changes :
        // - Status
        // - Start / End time in UTC
        // - Recurrence ID
        // - Recurrence Rule
        // Note that when editing a single edits we ignore the recurrence rule changes
        val dbEventStart =
            if (this.isRecurring()) dbEventWithOccurrence?.getStart(eventTimeZoneId)
            else dbEvent?.getStart(eventTimeZoneId)
        val dbEventEnd =
            if (this.isRecurring()) dbEventWithOccurrence?.getEnd(eventTimeZoneId)
            else dbEvent?.getEnd(eventTimeZoneId)

        val bumpSequence = dbEvent == null ||
                dbEvent.status != this.status ||
                dbEventStart != this.getStart(eventTimeZoneId) ||
                dbEventEnd != this.getEnd(eventTimeZoneId) || (!dbEvent.isSingleEdit() && dbEvent.iCalEvent.recurrenceRule != this.iCalEvent.recurrenceRule)

        if (bumpSequence || this.iCalEvent.sequence?.value == null) {
            if (this.isSyncedWithApi()) {
                this.iCalEvent.setSequence((this.iCalEvent.sequence?.value ?: 0) + 1) // TODO conflict resolution
            }
        }
    }

    private suspend fun handleOriginalEventNullSequence(userId: UserId, dbEvent: Event): UseCase.Result {
        // Update the sequence of parent if it didn't have a value before
        if (dbEvent.iCalEvent.sequence?.value == null) {
            dbEvent.iCalEvent.setSequence(0)
            return editCreateEventUseCase.execute(userId, dbEvent)
        }
        return UseCase.Result.Success<Unit>()
    }

    private fun resetParticipationStatus(originalEventSequence: Int?, newEvent: Event, attendeesToNotify: Map<Email, SendPreferences>) {
        val newEventSequence = newEvent.iCalEvent.sequence?.value

        val isSequenceUpdated = originalEventSequence != null && newEventSequence != null && originalEventSequence < newEventSequence

        if (isSequenceUpdated) {
            newEvent.iCalEvent.attendees.forEach { attendee ->
                val attendeeEmail = attendee.extractEmail()

                val shouldUpdate = (attendeeEmail != null && attendeesToNotify.keys.any {
                    canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true)
                        .equals(canonicalizeProtonEmail(it, forceCanonicalization = true), ignoreCase = true)
                })

                if (shouldUpdate) {
                    attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
                }
            }
        }
    }
}
