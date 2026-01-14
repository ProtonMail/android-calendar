package me.proton.android.calendar.domain.usecase

import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import me.proton.android.calendar.common.ApiResponseCode
import me.proton.android.calendar.common.EventDeletionReason
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.EventUtilsImpl.addExceptionDate
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.EventUtilsImpl.getSingleEditOriginalOccurrenceNumber
import me.proton.android.calendar.common.utils.EventUtilsImpl.handleDeleteThisAndFuture
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.iCalTimeZone
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.SyncEventDeleteContainer
import me.proton.android.calendar.data.api.SyncEventsUpdateApiRequest
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.util.kotlin.toBoolean
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import javax.inject.Inject

class HandleDeleteUseCase @Inject constructor( // TODO TESTS
    private val logger: Logger, // TODO remove unnecessary dependencies
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val calendarsRepository: CalendarsRepository,
    private val sendEmailUseCase: SendEmailUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val eventDecryptor: EventDecryptor
): UseCase {

    suspend fun handleDelete(
        userId: UserId,
        eventId: String,
        calendarId: String,
        deleteOption: EventEditDeleteOption,
        occurrenceNumber: Int?,
        deleteSingleEdits: Boolean = true,
        isOrphanSingleEdit: Boolean = false,
        deletionReason: EventDeletionReason = EventDeletionReason.ByUser
    ) : UseCase.Result {

        // TODO migrate to /sync route and handle recurring deletes

        logger.v("executing HandleDeleteUseCase $userId, $eventId, $deleteOption, $occurrenceNumber $deleteSingleEdits $isOrphanSingleEdit")

        val eventEntity = calendarsRepository.selectEventEntity(eventId)

        // maybe Event was deleted on server first
        if (eventEntity == null) {
            if (calendarsRepository.eventExistsOnServer(userId, eventId, calendarId) == false) {
                return UseCase.Result.Success<Unit>()
            } else return UseCase.Result.InvalidParams("HandleDeleteUseCase: event $eventId doesn't exist in DB")
        }

        val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
            eventDecryptor.decrypt(eventEntity)
        } else {
            transformEventUseCase.execute(eventEntity)
        } ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: event $eventId could not be transformed")

        // We need timezone when adding ex dates to handle DST
        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone

        // TODO when event is in the middle of chain, we need to select the root event and deal with it accordingly!!!

        val result = when (deleteOption) {
            EventEditDeleteOption.THIS_EVENT -> {

                if (event.isRecurring()) {
                    // add EXDATE to it
                    event.addExceptionDate(occurrenceNumber!!, timezone) // TODO
                    editCreateEventUseCase.execute(userId, event)
                } else if (event.isSingleEdit()) {

                    if (!isOrphanSingleEdit) {
                        val rootEventEntity = calendarsRepository.selectRootEventEntity(event.uid)
                        val rootEvent = rootEventEntity?.let { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                            eventDecryptor.decrypt(it)
                        } else {
                            transformEventUseCase.execute(it)
                        } }
                            ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: root event for $eventId doesn't exist in DB")

                        val originalOccurrenceNumber =
                            if (occurrenceNumber == 0) {
                                // If event is a single edit and occurrence number is 0, check that we are using the correct original event occurrence number
                                event.getSingleEditOriginalOccurrenceNumber(
                                    rootEvent,
                                    timezone ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: Time zone id was null")
                                ) ?: occurrenceNumber
                            } else occurrenceNumber!!

                        // add EXDATE to root event
                        rootEvent.addExceptionDate(originalOccurrenceNumber, timezone) // TODO

                        val isStandaloneSingleEdit = calendarsRepository.isStandaloneSingleEdit(
                            userId,
                            event.uid,
                            event.iCalEvent.recurrenceId,
                            timezone ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: timezone was null for isStandaloneSingleEdit")
                        ) ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: isStandaloneSingleEdit was null, error in call for getEventsByUid")

                        if (isStandaloneSingleEdit) {
                            // delete single edit and root event
                            deleteEvents(userId, listOf(event.id, rootEvent.id), rootEvent.calendar.id)
                        } else {
                            val editResult = editCreateEventUseCase.execute(userId, rootEvent)
                            editResult.ifSuccessAndLogErrors(logger) {}

                            // delete the single edit
                            deleteEvents(userId, listOf(event.id), event.calendar.id)
                        }
                    } else {
                        // delete the single edit
                        deleteEvents(userId, listOf(event.id), event.calendar.id)
                    }
                } else {
                    // delete the non-recurring event
                    deleteEvents(userId, listOf(event.id), event.calendar.id, deletionReason)
                }

            }
            EventEditDeleteOption.THIS_EVENT_AND_FUTURE -> {

                val rootEvent =
                    if (event.isSingleEdit()) calendarsRepository.selectRootEventEntity(event.uid)?.let { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                        eventDecryptor.decrypt(it)
                    } else {
                        transformEventUseCase.execute(it)
                    } }
                        ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: root event for $eventId doesn't exist in DB")
                    else event

                val originalOccurrenceNumber =
                    if (occurrenceNumber == 0) {
                        // If event is a single edit and occurrence number is 0, check that we are using the correct original event occurrence number
                        event.getSingleEditOriginalOccurrenceNumber(
                            rootEvent,
                            timezone ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: Time zone id was null")
                        ) ?: occurrenceNumber
                    } else occurrenceNumber!!

                val occurrenceStart = rootEvent.generateOccurrence(
                    originalOccurrenceNumber,
                    if (rootEvent.isAllDay()) ZoneId.systemDefault().id else rootEvent.iCalendar.iCalTimeZone(rootEvent.iCalEvent.dateStart).id
                )?.startDateTime
                    ?: return UseCase.Result.Error("HandleDeleteUseCase: could not generate occurrence in >delete this and following< events")

                rootEvent.handleDeleteThisAndFuture(originalOccurrenceNumber)
                val editResult = editCreateEventUseCase.execute(userId, rootEvent)
                editResult.ifSuccessAndLogErrors(logger) {}

                // delete single edits happening after this occurrence
                val deleteSingleEditsResult = deleteSingleEditsAfter(userId, rootEvent.id, occurrenceStart.minusNanos(1))
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) {}

                if ((editResult is UseCase.Result.Success<*>) && (deleteSingleEditsResult is UseCase.Result.Success<*>)) {
                    UseCase.Result.Success<Unit>()
                } else {
                    val userErrorMessage =
                        when {
                            editResult is UseCase.Result.Error -> editResult.userErrorMessage
                            editResult is UseCase.Result.InvalidParams -> editResult.userErrorMessage
                            deleteSingleEditsResult is UseCase.Result.Error -> deleteSingleEditsResult.userErrorMessage
                            deleteSingleEditsResult is UseCase.Result.InvalidParams -> deleteSingleEditsResult.userErrorMessage
                            else -> null
                        }
                    UseCase.Result.Error("HandleDeleteUseCase: error deleting >this and future< events", userErrorMessage = userErrorMessage)
                }

            }
            EventEditDeleteOption.ALL_EVENTS -> {

                // delete single edits and the original event as the last one
                val rootEvent =
                    if (event.isSingleEdit()) calendarsRepository.selectRootEventEntity(event.uid)?.let { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                        eventDecryptor.decrypt(it)
                    } else {
                        transformEventUseCase.execute(it)
                    } }
                        ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: root event for $eventId doesn't exist in DB")
                    else event

                // TODO maybe merge this into one request
                val deleteSingleEditsResult =
                    if (deleteSingleEdits) {
                        deleteSingleEditsAfter(
                            userId,
                            rootEvent.id,
                            rootEvent.getStart(ZoneId.systemDefault().id)!!.minusNanos(1)
                        )
                    } else UseCase.Result.Success<Unit>()
                deleteSingleEditsResult.ifSuccessAndLogErrors(logger) {}
                val deleteResult = deleteEvents(userId, listOf(rootEvent.id), rootEvent.calendar.id)
                deleteResult.ifSuccessAndLogErrors(logger) {}

                if ((deleteSingleEditsResult is UseCase.Result.Success<*>) && (deleteResult is UseCase.Result.Success<*>)) {
                    UseCase.Result.Success<Unit>()
                } else {
                    val userErrorMessage =
                        when {
                            deleteSingleEditsResult is UseCase.Result.Error -> deleteSingleEditsResult.userErrorMessage
                            deleteSingleEditsResult is UseCase.Result.InvalidParams -> deleteSingleEditsResult.userErrorMessage
                            deleteResult is UseCase.Result.Error -> deleteResult.userErrorMessage
                            deleteResult is UseCase.Result.InvalidParams -> deleteResult.userErrorMessage
                            else -> null
                        }
                    UseCase.Result.Error("HandleDeleteUseCase: error deleting >all< events", userErrorMessage = userErrorMessage)
                }
            }
        }

        result.ifSuccessAndLogErrors(logger) {}

        return result
    }

    private suspend fun deleteEvents(userId: UserId, eventIds: List<String>, calendarId: String, deletionReason: EventDeletionReason = EventDeletionReason.ByUser): UseCase.Result  {
        val syncRequestBody = SyncEventsUpdateApiRequest(
            events = eventIds.map { SyncEventDeleteContainer(id = it, deletionReason = deletionReason.value) }
        )

        return when (val syncResponse = calendarsApi.syncEvents(userId, calendarId, syncRequestBody)) {
            is ApiResponse.Success -> {

                // TODO check .isSuccessful on Proton Responses, this will still crash in case of malformed request etc.

                val errorEventIds = syncResponse.data.responses.mapNotNull {
                    if (it.response.code == ApiResponseCode.DOES_NOT_EXIST) {
                        // ignore error if event didn't exist on server
                        logger.i("HandleDeleteUseCase event didn't exist on server anymore")
                        null
                    } else {
                        logger.e("error deleting event on server: ${it.response.code} ${it.response.error}")
                        eventIds[it.index]
                    }
                }

                val successEventIds = eventIds.filterNot { it in errorEventIds }
                if (successEventIds.isNotEmpty()) {
                    calendarsRepository.deleteEventsById(calendarId, successEventIds)
                    calendarsRepository.deleteEventsMetadataByEventIds(successEventIds)
                    handleAlarmsUseCase.execute(userId)
                }

                if (errorEventIds.isEmpty()) {
                    UseCase.Result.Success<Unit>()
                } else {
                    val syncError = syncResponse.data.responses.firstOrNull { !it.response.isSuccessful }
                    UseCase.Result.Error("HandleDeleteUseCase: there were errors when deleting events ${syncError?.response?.code} ${syncError?.response?.error}", userErrorMessage = syncError?.response?.error)
                }
            }
            is ApiResponse.Error -> {
                if (syncResponse.httpCode == 503) calendarsRepository.pingServer(userId)
                UseCase.Result.Error("HandleDeleteUseCase: error in sync events: ${syncResponse.error}")
            }
            is ApiResponse.Exception -> UseCase.Result.Error("HandleDeleteUseCase: error in sync events: ${syncResponse.exception.message ?: "(no exception message)"}")
        }
    }

    // TODO maybe use UseCase.Params instead of overloaded methods
    suspend fun handleDeleteSingleEdits(userId: UserId, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {
        return deleteSingleEditsAfter(userId, eventId, recurrenceIdIsAfter)
    }

    private suspend fun deleteSingleEditsAfter(userId: UserId, eventId: String, recurrenceIdIsAfter: ZonedDateTime) : UseCase.Result {

        val eventEntity = calendarsRepository.selectEventEntity(eventId) ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: event $eventId doesn't exist in DB")
        val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
            eventDecryptor.decrypt(eventEntity)
        } else {
            transformEventUseCase.execute(eventEntity)
        } ?: return UseCase.Result.InvalidParams("HandleDeleteUseCase: event $eventId could not be transformed")

        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, event.uid, 0, 100) // TODO paging
        val eventsSharingUid = if (eventsSharingUidResponse is ApiResponse.Success) {
            eventsSharingUidResponse.data.events.mapNotNull {
                if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(it.toEventEntity())
                } else {
                    transformEventUseCase.execute(it.toEventEntity())
                }
            }
        } else {
            if (eventsSharingUidResponse is ApiResponse.Error && eventsSharingUidResponse.httpCode == 503) {
                calendarsRepository.pingServer(userId)
            }
            return UseCase.Result.Error("error fetching events sharing UID")
        }

        // we need to manually delete all "single-edited" events with RecurrenceID after just-deleted occurrence
        val eventsToDelete = eventsSharingUid.filter {
            it.iCalEvent.recurrenceId != null &&
                    ZonedDateTime.ofInstant(it.iCalEvent.recurrenceId.value.toInstant(), ZoneId.systemDefault()).isAfter(recurrenceIdIsAfter)
        }

        val deleteResult = if (eventsToDelete.isNotEmpty()) {
            deleteEvents(userId, eventsToDelete.map { it.id }, event.calendar.id)
        } else UseCase.Result.Success<Unit>()

        return deleteResult
    }

    suspend fun handleDeleteAsOrganizer(
        userId: UserId,
        event: Event,
        attendees: List<Attendee>,
        sendPreferences: Map<Email, SendPreferences>,
        timeFormatIs24Hours: Boolean,
        isPartOfChain: Boolean,
        isCalendarDisabled: Boolean
    ): UseCase.Result {

        var emailSent = false

        // The attendees list contains those we successfully fetched send preferences for (only send an email for those, skip sending email if the list is empty)
        if (!isCalendarDisabled && attendees.isNotEmpty()) {
            // If address is disabled, cancellation can't be sent
            // Bump sequence for the cancellation ics when deleting event with attendees
            event.iCalEvent.setSequence((event.iCalEvent.sequence?.value ?: 0) + 1)
            val sendCancellationResult = sendEmailUseCase.sendCancellationToAttendees(
                userId,
                event,
                attendees.mapNotNull { it.extractEmail() },
                sendPreferences,
                event.defaultTimeZone!!,
                timeFormatIs24Hours
            )
            sendCancellationResult.ifSuccessAndLogErrors(logger) { }

            if (sendCancellationResult is UseCase.Result.Error) {
                return UseCase.Result.Error(
                    "HandleDeleteUseCase: handleDeleteAsOrganizer error in send email (cancel as organizer): ${sendCancellationResult.message}",
                    sendCancellationResult.error
                )
            } else if (sendCancellationResult is UseCase.Result.InvalidParams) {
                return UseCase.Result.InvalidParams(
                    "HandleDeleteUseCase: handleDeleteAsOrganizer invalid params in send email: ${sendCancellationResult.message}"
                )
            }

            emailSent = true
        }

        val handleDeleteResult = handleDelete(
            userId,
            event.id,
            event.calendar.id,
            if (isPartOfChain) EventEditDeleteOption.ALL_EVENTS else EventEditDeleteOption.THIS_EVENT,
            if (isPartOfChain) null else 0
        )

        return if (handleDeleteResult is UseCase.Result.Success<*>) {
            UseCase.Result.Success(emailSent)
        } else handleDeleteResult
    }


    suspend fun handleDeleteAsAttendee(
        userId: UserId,
        event: Event,
        cancelledSingleEdits: List<Event>?,
        userEmail: String,
        sendPreferences: Map<Email, SendPreferences>,
        hasNonCancelledSingleEdit: Boolean,
        occurrenceNumber: Int,
        isOrphanSingleEdit: Boolean,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean,
        sendReply: Boolean
    ): UseCase.Result {

        var emailSent = false

        if (!event.calendar.isDisabled && sendPreferences.isNotEmpty() && sendReply) {

            val eventResponse = if (event.isProtonProtonInvite == null || event.isProtonProtonInvite == true) {
                calendarsRepository.fetchEventById(userId, event.calendar.id, event.id).valueOrNullAndLogErrors(logger)?.event
                    ?: return UseCase.Result.Error("HandleDeleteUseCase: handleDeleteAsAttendee fetchEventById event was null")
            } else null

            val isProtonProtonInvite = event.isProtonProtonInvite ?: eventResponse?.isProtonProtonInvite?.toBoolean()
            val updateTime = Instant.now()

            val userAttendee = event.iCalEvent.attendees.find { attendee ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && ProtonUtilsImpl.canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true)
                    .equals(ProtonUtilsImpl.canonicalizeProtonEmail(userEmail, forceCanonicalization = true), ignoreCase = true)
            } ?: return UseCase.Result.Error("HandleDeleteUseCase: handleDeleteAsAttendee userAttendee was null")

            // If address is disabled, cancellation can't be sent
            val organizerEmail = event.iCalEvent.organizer.extractEmail() ?: return UseCase.Result.Error("HandleDeleteUseCase: handleDeleteAsAttendee organizerEmail was null")
            val sendCancellationResult = sendEmailUseCase.sendReplyToOrganizer(
                userId,
                event,
                event.iCalendar.timezoneInfo,
                userAttendee.copy(),
                organizerEmail,
                ParticipationStatus.DECLINED,
                sendPreferences,
                Date.from(updateTime),
                eventResponse?.toEventEntity(),
                isProtonProtonInvite ?: false,
                defaultTimeZone,
                timeFormatIs24Hours
            )
            sendCancellationResult.ifSuccessAndLogErrors(logger) { }

            if (sendCancellationResult is UseCase.Result.Error) {
                return UseCase.Result.Error(
                    "HandleDeleteUseCase: handleDeleteAsAttendee error in send email: ${sendCancellationResult.message}",
                    sendCancellationResult.error
                )
            } else if (sendCancellationResult is UseCase.Result.InvalidParams) {
                return UseCase.Result.InvalidParams(
                    "HandleDeleteUseCase: handleDeleteAsAttendee invalid params in send email: ${sendCancellationResult.message}"
                )
            }

            // If the email was sent we update the participation status

            val attendeeId = event.currentUserAttendeeId
            if (attendeeId.isNullOrEmpty()) {
                return UseCase.Result.Error("HandleDeleteUseCase: handleDeleteAsAttendee attendeeId was null or empty")
            }

            val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
                userId,
                event.calendar.id,
                event.id,
                attendeeId,
                ParticipationStatus.DECLINED.toInt(),
                null, // No need to update the alarms since the event will be deleted
                null, // update personal part will not be called because of null PersonalPartString
                updateTime.epochSecond.toInt()
            )
            updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
            if (updateParticipationStatusUseCaseResult is UseCase.Result.Error) {
                return UseCase.Result.Error(
                    "HandleDeleteUseCase: handleDeleteAsAttendee error in update part stat: ${updateParticipationStatusUseCaseResult.message}"
                )
            } else if (updateParticipationStatusUseCaseResult is UseCase.Result.InvalidParams) {
                return UseCase.Result.InvalidParams(
                    "HandleDeleteUseCase: handleDeleteAsAttendee invalid params in update part stat: ${updateParticipationStatusUseCaseResult.message}"
                )
            }

            emailSent = true
        }

        val handleDeleteResult = handleDelete(
            userId,
            event.id,
            event.calendar.id,
            if (event.isRecurring() || (event.isSingleEdit() && !isOrphanSingleEdit && event.calendar.isDisabled)) EventEditDeleteOption.ALL_EVENTS else EventEditDeleteOption.THIS_EVENT,
            if (event.isRecurring() || (event.isSingleEdit() && !isOrphanSingleEdit && event.calendar.isDisabled)) null else if (event.isSingleEdit()) occurrenceNumber else 0,
            !(event.isRecurring() && hasNonCancelledSingleEdit) || event.calendar.isDisabled,
            isOrphanSingleEdit
        )

        return if (handleDeleteResult is UseCase.Result.Success<*>) {
            // Try to delete any existing cancelled single edits. Silently fail.
            if (!cancelledSingleEdits.isNullOrEmpty() && event.isRecurring()) {
                deleteEvents(userId, cancelledSingleEdits.map { it.id }, event.calendar.id)
            }
            UseCase.Result.Success(emailSent)
        } else handleDeleteResult
    }
}
