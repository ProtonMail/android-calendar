package me.proton.android.calendar.domain.usecase

import biweekly.ICalendar
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.Method
import biweekly.property.Status
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.clone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRecurrenceId
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmails
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.getDefaultAlarms
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.model.NotificationMigration
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.util.kotlin.toBoolean
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HandleIcsUseCase @Inject constructor(
    private val logger: Logger,
    private val json: Json,
    private val userAddressManager: UserAddressManager,
    private val calendarsRepository: CalendarsRepository,
    private val transformEventUseCase: TransformEventUseCase,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val handleDeleteUseCase: HandleDeleteUseCase,
    private val canonicalEmailsUseCase: GetCanonicalEmailsUseCase,
    private val eventDecryptor: EventDecryptor,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val featureFlagManager: FeatureFlagManager,
) {

    suspend fun execute(iCalString: String, userId: UserId, senderEmail: String?, recipientEmail: String?): IcsSurgeryUtils.HandleIcsResult {
        val isOpeningFromProtonMail = senderEmail != null || recipientEmail != null

        val timeZoneId = calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = timeZoneId, isOpeningFromProtonMail = isOpeningFromProtonMail)

        if (cleanIcsResult !is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            // Only log error as it doesn't contain any sensitive information
            if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error) logger.i("HandleIcsUseCase parsing error ${cleanIcsResult.javaClass}")
            return cleanIcsResult
        }

        val iCalendar = cleanIcsResult.iCalendar ?: return IcsSurgeryUtils.HandleIcsResult.Error.ParsingFailed

        return if (iCalendar.method.isPublish || !isOpeningFromProtonMail) {
            if (CalendarFeatureFlag.ImportIcs.fallbackValue) handleImportIcs(iCalendar, userId, isOpeningFromProtonMail)
            else IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Publish
        } else {
            handleInviteIcs(iCalendar, userId, senderEmail, recipientEmail)
        }
    }

    private suspend fun handleImportIcs(iCalendar: ICalendar, userId: UserId, isOpeningFromProtonMail: Boolean): IcsSurgeryUtils.HandleIcsResult {
        // Get default calendar
        val defaultCalendar = calendarsRepository.getDefaultCalendarIdWithFallback(userId.id, allowShared = true)?.let { defaultCalendarId ->
            calendarsRepository.selectCalendar(defaultCalendarId)
        } ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultCalendarFound

        val notifications = NotificationMigration(true, iCalendar.events.firstOrNull()?.alarms?.mapNotNull { Notification.fromVAlarm(it) })

        val newEvent = Event.from(
            ICalUtilsImpl.generateOfflineEventId(), Calendar(
                defaultCalendar.id,
                defaultCalendar.name,
                defaultCalendar.email,
                defaultCalendar.ownerEmail,
                defaultCalendar.description,
                defaultCalendar.color,
                defaultCalendar.priority,
                defaultCalendar.addressId,
                defaultCalendar.memberId,
                defaultCalendar.flags,
                defaultCalendar.display,
                defaultCalendar.type,
                defaultCalendar.permissions,
                defaultCalendar.defaultEventDuration,
                defaultCalendar.defaultPartDayNotifications,
                defaultCalendar.defaultFullDayNotifications
            ), iCalendar, Instant.now().epochSecond, notifications = notifications
        ) ?: return IcsSurgeryUtils.HandleIcsResult.Error.ParsingFailed

        if ((isOpeningFromProtonMail || !iCalendar.method.isPublish) && newEvent.iCalEvent.alarms.isNullOrEmpty()) {
            // We drop alarms when importing invitations as this would not be the user's. We must set the default calendar alarms instead.
            val calendarSettings = calendarsRepository.selectCalendarSettings(defaultCalendar.id)
            calendarSettings?.getDefaultAlarms(json, newEvent.isAllDay())?.let { defaultAlarms ->
                newEvent.addAlarms(defaultAlarms)
            }
        }

        // Fetch all events sharing UID from BE
        val eventsSharingUidResponse = (
                calendarsRepository.getEventsByUid(userId, iCalendar.events.first().uid.value).valueOrNullAndLogErrors(logger)
                    ?: return IcsSurgeryUtils.HandleIcsResult.Error.NetworkError
                ).events

        // Find an existing event from the ones sharing the same UID
        var existingEvent: Event? = null
        eventsSharingUidResponse.let {
            for (eventResponse in eventsSharingUidResponse) {
                val eventEntity = eventResponse.toEventEntity()
                val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
                if (event?.iCalEvent?.recurrenceId == iCalendar.events.first().recurrenceId) {
                    existingEvent = event
                    calendarsRepository.persistEvents(*(listOf(eventEntity)).toTypedArray())
                    listOf(eventResponse.toEventEntityMetadata()).forEach {
                        updateEventOccurrencesUseCase.execute(userId.id, it)
                    }
                    break
                }
            }
        }

        val immutableExistingEvent = existingEvent

        if (immutableExistingEvent != null && immutableExistingEvent == newEvent) {
            // Event already exists
            return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
        } else {
            when (val editCreateEventResult = editCreateEventUseCase.execute(userId, newEvent, isImport = true)) {
                is UseCase.Result.Success<*> -> {
                    var eventId: String? = null
                    editCreateEventResult.returnValue.tryCast<List<String>> {
                        eventId = this.firstOrNull()
                    }

                    makeCalendarVisible(newEvent, userId)

                    if (immutableExistingEvent != null) {
                        // If event with same UID existed and sync call succeeded, delete existing event locally since we overwrite on import
                        calendarsRepository.deleteEventsById(immutableExistingEvent.calendar.id, listOf(immutableExistingEvent.id))
                        calendarsRepository.deleteEventsMetadataByEventIds(listOf(immutableExistingEvent.id))
                    }

                    return IcsSurgeryUtils.HandleIcsResult.Success(eventId = eventId ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(), IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT, isRecurring = newEvent.isRecurring())
                }
                is UseCase.Result.InvalidParams -> {
                    logger.i("HandleIcsUseCase: invalid params in create event: ${editCreateEventResult.message}")
                    return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(editCreateEventResult.userErrorMessage)
                }
                is UseCase.Result.Error -> {
                    logger.i("HandleIcsUseCase: error in create event: ${editCreateEventResult.message}")
                    return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(editCreateEventResult.userErrorMessage)
                }
            }
        }
    }

    private suspend fun handleInviteIcs(iCalendar: ICalendar, userId: UserId, senderEmail: String?, recipientEmail: String?): IcsSurgeryUtils.HandleIcsResult {
        val allAddresses = userAddressManager.getAddressesOrNull(userId)
        val canonicalUserEmails = allAddresses?.map { address ->
            canonicalizeProtonEmail(address.email, forceCanonicalization = true)
        } ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError

        val canonicalSenderEmail = canonicalizeProtonEmail(senderEmail ?: "", forceCanonicalization = true)
        val canonicalRecipientEmail = canonicalizeProtonEmail(recipientEmail ?: "", forceCanonicalization = true)

        val organizerEmail = iCalendar.events.first().organizer?.extractEmail() ?: run {
            // The ORGANIZER field is mandatory in an invitation, but some providers forget about it.
            //  In those cases, we build one from the sender of the email: ORGANIZER;CN=address:mailto:address
            if (!senderEmail.isNullOrBlank() && (iCalendar.method.isRequest || iCalendar.method.isCancel)) {
                iCalendar.events.first().setOrganizer(senderEmail)
                senderEmail
            } else if (!recipientEmail.isNullOrBlank() && iCalendar.method.isReply) {
                iCalendar.events.first().setOrganizer(recipientEmail)
                recipientEmail
            } else {
                logger.i("HandleIcsUseCase error missing organizer")
                return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.MissingOrganizer
            }
        }

        // Find out if we are in organizer mode or attendee mode
        val canonicalOrganizerEmail = canonicalizeProtonEmail(organizerEmail, forceCanonicalization = true)
        val isOrganizerMode = canonicalUserEmails.firstOrNull { canonicalOrganizerEmail == it } != null

        // METHOD: We support REQUEST, CANCEL, REPLY.
        if (iCalendar.method.isAdd) {
            // TODO Remove once ADD is handled
            return if (isOrganizerMode) IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
            else IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Add
        }
        if (iCalendar.method.isCounter) {
            // TODO Remove once COUNTER is handled
            return if (isOrganizerMode) IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Counter
            else IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
        }
        if (iCalendar.method.isRefresh) {
            // TODO Remove once REFRESH is handled
            return if (isOrganizerMode) IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.Refresh
            else IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
        }

        if (!iCalendar.method.isRequest &&
            !iCalendar.method.isCancel &&
            !iCalendar.method.isReply) {
            return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method
        } // TODO Remove once other methods are handled

        var isCurrentUserSender = false // TODO Replace by val once we remove OPEN_ICS_FILES intent
        if (canonicalSenderEmail.isNotBlank() && canonicalRecipientEmail.isNotBlank()) {

            isCurrentUserSender = canonicalUserEmails.contains(canonicalSenderEmail) == true
            val isCurrentUserRecipient = canonicalUserEmails.contains(canonicalRecipientEmail)

            if (!isCurrentUserSender && !isCurrentUserRecipient) return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher

            val attendeeEmails = iCalendar.events.first().attendees.mapNotNull { it.extractEmail() }
            val canonicalAttendeeEmails = canonicalizeProtonEmails(attendeeEmails, forceCanonicalization = true)

            if (isOrganizerMode && isCurrentUserRecipient && !canonicalAttendeeEmails.values.contains(canonicalSenderEmail)) return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher
        }

        if (iCalendar.method.isCancel && isOrganizerMode) return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Method

        if (isOrganizerMode && iCalendar.method.isReply && iCalendar.events.first().recurrenceId?.value != null)
            return IcsSurgeryUtils.HandleIcsResult.Error.Unsupported.SingleEditReply

        // Try to extract the current user from the attendee list if it exists
        var userAttendee = iCalendar.events.first().attendees.find { attendee ->
            canonicalUserEmails.firstOrNull { canonicalUserEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(canonicalUserEmail, ignoreCase = true)
            } != null
        }

        // Make sure all attendees have a part stat (default is NEEDS-ACTION)
        iCalendar.events.first().attendees.forEach {
            if (it.participationStatus == null) it.participationStatus = ParticipationStatus.NEEDS_ACTION

            // When in organizer mode, we do not accept invalid emails, as there we require the attendee email to be canonicalizable to generate the token
            if (isOrganizerMode && it.extractEmail() == null) {
                logger.i("HandleIcsUseCase organizer mode invalid attendee email")
                return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees
            }
        }

        if (!isOrganizerMode && userAttendee == null) {
            // Check for supported party crasher case - only when organizer is not a Proton domain
            val isProtonOrganizer = ProtonUtilsImpl.isProtonDomain(organizerEmail)
            val isUserRecipient = allAddresses.any { it.email.equals(recipientEmail, ignoreCase = true) }
            if (isUserRecipient && !isProtonOrganizer) {
                val protonReplyEmail = allAddresses.firstOrNull { ProtonUtilsImpl.isProtonDomain(it.email) }?.email
                    ?: run {
                        val defCalId = calendarsRepository.getDefaultCalendarIdWithFallback(userId.id, allowShared = false)
                            ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultPersonalCalendarFound
                        calendarsRepository.selectCalendar(defCalId)?.email
                            ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultPersonalCalendarFound
                    }
                val ev = iCalendar.events.first()
                val replyCanon = canonicalizeProtonEmail(protonReplyEmail, true)
                val alreadyPresent = ev.attendees.any {
                    val email = it.extractEmail().orEmpty()
                    canonicalizeProtonEmail(email, true).equals(replyCanon, ignoreCase = true)
                }
                // Add the user to the attendees
                if (!alreadyPresent) {
                    ev.attendees.add(Attendee("", protonReplyEmail).apply {
                        uri = "mailto:$protonReplyEmail"
                        participationStatus = ParticipationStatus.NEEDS_ACTION
                        rsvp = true
                    })
                    userAttendee = iCalendar.events.first().attendees
                        .find { it.extractEmail()?.equals(protonReplyEmail, true) == true }
                }
            } else {
                // If current user is not in the attendee list and is not the organizer then it is a party crasher
                return IcsSurgeryUtils.HandleIcsResult.Error.PartyCrasher
            }
        }

        // Use the default calendar to create the event
        // Calendar needs to be active and user needs to be owner (we don't allow members to add invites in shared cals even with write permissions)
        val defaultCalendar = calendarsRepository.getDefaultCalendarIdWithFallback(userId.id, allowShared = false)?.let {
            calendarsRepository.selectCalendar(it)
        } ?: return IcsSurgeryUtils.HandleIcsResult.Error.NoDefaultPersonalCalendarFound

        // Fetch all events sharing UID from BE
        val eventsSharingUidResponse = (
                calendarsRepository.getEventsByUid(userId, iCalendar.events.first().uid.value).valueOrNullAndLogErrors(logger)
                    ?: return IcsSurgeryUtils.HandleIcsResult.Error.NetworkError
                ).events

        // IMPORTANT: We need parent event to clean recurrence id
        val parentEventEntity = eventsSharingUidResponse.firstOrNull { eventResponse ->
            eventResponse.sharedEvents.any {
                try {
                    // only root event contains RRULE
                    it.jsonObject.get("Data")?.jsonPrimitive?.content?.contains("RRULE:") == true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
        }?.toEventEntity()
        val parentEvent = if (parentEventEntity != null) {
            if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptor.decrypt(parentEventEntity)
            } else {
                transformEventUseCase.execute(parentEventEntity)
            }
        } else null

        // IMPORTANT: Unlike the rest of the surgery, clean recurrence id is called outside of cleanIcs, but it is still mandatory
        if (!iCalendar.cleanRecurrenceId(parentEvent?.iCalendar)) {
            logger.i("HandleIcsUseCase error invalid recurrence id")
            return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RecurrenceId
        }

        // Find an existing event from the ones sharing the same UID
        var existingEvent: Event? = null
        var existingEventEntity: EventEntity? = null
        eventsSharingUidResponse.let {
            for (eventResponse in eventsSharingUidResponse) {
                val eventEntity = eventResponse.toEventEntity()
                val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
                if (event?.iCalEvent?.recurrenceId == iCalendar.events.first().recurrenceId) {
                    existingEvent = event
                    existingEventEntity = eventEntity
                    calendarsRepository.persistEvents(*(listOf(eventEntity)).toTypedArray())
                    listOf(eventResponse.toEventEntityMetadata()).forEach {
                        updateEventOccurrencesUseCase.execute(userId.id, it)
                    }
                    break
                }
            }
        }

        val immutableExistingEvent = existingEvent
        val immutableExistingEventEntity = existingEventEntity

        val attendees = immutableExistingEvent?.iCalEvent?.attendees
        // Check using the original event that the reply sender is indeed an attendee
        if (iCalendar.method.isReply && isOrganizerMode && canonicalSenderEmail.isNotBlank() && attendees != null &&
            attendees.find {
                canonicalizeProtonEmail(it.extractEmail() ?: "", forceCanonicalization = true).equals(canonicalSenderEmail)
            } == null) return IcsSurgeryUtils.HandleIcsResult.Error.ReplyPartyCrasher(immutableExistingEvent.id)

        if (iCalendar.method.isReply && !isOrganizerMode) {
            return IcsSurgeryUtils.HandleIcsResult.Error.Method(existingEvent?.id)
        }
        if (existingEvent?.decryptionStatus is Event.DecryptionStatus.Failure) return IcsSurgeryUtils.HandleIcsResult.Error.DecryptionFailed(existingEvent?.id, existingEvent?.calendar?.id, existingEvent?.isRecurring())
        if (existingEvent?.calendar?.isActive == false) return IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar(existingEvent?.id)

        if (isCurrentUserSender && existingEvent != null) {
            // We are opening an invite sent by the current user, no changes are needed, open event details
            existingEvent?.let { makeCalendarVisible(it, userId) }
            return IcsSurgeryUtils.HandleIcsResult.Success(existingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EventNotFound, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = existingEvent?.isRecurring())
        }

        val isNew = eventsSharingUidResponse.isNullOrEmpty() || existingEvent == null || (existingEvent != null && existingEvent?.decryptionStatus is Event.DecryptionStatus.Failure)

        // If a series already exist, use the same calendar, else use the default one
        val existingCalendar = if (!eventsSharingUidResponse.isNullOrEmpty()) {
            val existingCalendarId = eventsSharingUidResponse.first().calendarId
            val existingCalendarEntity = calendarsRepository.selectCalendar(existingCalendarId)

            if (existingCalendarEntity?.isActive == false) {
                // If calendar is disabled, display error message and try to open event details
                return if (existingEvent != null) IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar(existingEvent?.id)
                else IcsSurgeryUtils.HandleIcsResult.Error.DisabledCalendar(null)
            }

            existingCalendarEntity
        } else null

        val notifications = NotificationMigration(true, iCalendar.events.firstOrNull()?.alarms?.mapNotNull { Notification.fromVAlarm(it) })

        // Create a new event with the clean iCalendar
        val newEvent = Event.from(
            ICalUtilsImpl.generateOfflineEventId(), Calendar(
                existingCalendar?.id ?: defaultCalendar.id,
                existingCalendar?.name ?: defaultCalendar.name,
                existingCalendar?.email ?: defaultCalendar.email,
                existingCalendar?.ownerEmail ?: defaultCalendar.ownerEmail,
                existingCalendar?.description ?: defaultCalendar.description,
                existingCalendar?.color ?: defaultCalendar.color,
                existingCalendar?.priority ?: defaultCalendar.priority,
                existingCalendar?.addressId ?: defaultCalendar.addressId,
                existingCalendar?.memberId ?: defaultCalendar.memberId,
                existingCalendar?.flags ?: defaultCalendar.flags,
                existingCalendar?.display ?: defaultCalendar.display,
                existingCalendar?.type ?: defaultCalendar.type,
                existingCalendar?.permissions ?: defaultCalendar.permissions,
                existingCalendar?.defaultEventDuration ?: defaultCalendar.defaultEventDuration,
                existingCalendar?.defaultPartDayNotifications ?: defaultCalendar.defaultPartDayNotifications,
                existingCalendar?.defaultFullDayNotifications ?: defaultCalendar.defaultFullDayNotifications
            ), iCalendar, Instant.now().epochSecond, notifications = notifications) ?: return IcsSurgeryUtils.HandleIcsResult.Error.ParsingFailed

        val isNewNonCancelled  = isNew && !isOrganizerMode && !iCalendar.method.isCancel
        val isNewSingleEditCancelled = isNew && existingEvent == null && iCalendar.method.isCancel

        val isReInvitation = newEvent.iCalendar.method.isRequest && !newEvent.isCancelled() && existingEvent != null && existingEvent?.isCancelled() == true
        if (isReInvitation && immutableExistingEvent != null) {
            val deleteResult = handleDeleteUseCase.handleDelete(userId, immutableExistingEvent.id, immutableExistingEvent.calendar.id, EventEditDeleteOption.ALL_EVENTS, null)
            if (deleteResult !is UseCase.Result.Success<*>) {
                deleteResult.ifSuccessAndLogErrors(logger) {}
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(
                    when (deleteResult) {
                        is UseCase.Result.Error -> deleteResult.userErrorMessage
                        is UseCase.Result.InvalidParams -> deleteResult.userErrorMessage
                        else -> null
                    }
                )
            }
        }

        if (isNewNonCancelled || isNewSingleEditCancelled || isReInvitation) {
            // Create brand new event
            if (!newEvent.iCalendar.setAttendeesXPmToken(userId, isOrganizerMode)) {
                logger.i("HandleIcsUseCase error failed to set attendees xpm token")
                return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees
            }
            if (isNewSingleEditCancelled) {
                newEvent.iCalendar.method = Method.request()
                newEvent.iCalEvent.status = Status.cancelled() // In case of un-invite, the status needs to be set to cancelled
            }
            return editCreateEventFromIcs(
                IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT,
                userId,
                newEvent
            )
        } else {
            // Event already exists, check if we need to update it using the ics content
            if (!isOrganizerMode && immutableExistingEvent != null && immutableExistingEventEntity != null && newEvent.iCalEvent.dateTimeStamp.value.after(immutableExistingEvent.iCalEvent.dateTimeStamp?.value)) {
                if (immutableExistingEventEntity.isProtonProtonInvite?.toBoolean() == true || immutableExistingEvent.sharedEventId == newEvent.iCalEvent.getExperimentalProperty(X_PM_SHARED_EVENT_ID)?.value) {
                    // Event is a proton to proton invite
                    // Fetch event to make sure we have the latest version
                    makeCalendarVisible(immutableExistingEvent, userId)
                    return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
                }
                if (!newEvent.iCalendar.setAttendeesXPmToken(userId, isOrganizerMode)) return IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees
                return updateEventAsAnAttendee(newEvent, immutableExistingEvent, canonicalUserEmails, userAttendee, userId)
            } else if (isOrganizerMode && immutableExistingEvent != null && immutableExistingEventEntity != null && !iCalendar.events.first().attendees.isNullOrEmpty()) {
                if (newEvent.hasProtonProtonProperties || newEvent.isProtonProtonReply) {
                    // Attendee added the event as a Proton to Proton invite
                    // Fetch event to make sure we have the latest version
                    makeCalendarVisible(immutableExistingEvent, userId)
                    return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
                }
                return updateEventAsAnOrganizer(immutableExistingEvent, immutableExistingEventEntity, iCalendar, userId)
            } else if (isOrganizerMode && immutableExistingEvent == null) {
                return IcsSurgeryUtils.HandleIcsResult.Error.EventDeleted
            }
        }

        if (immutableExistingEventEntity != null && immutableExistingEvent != null) {
            // Fetch event to make sure we have the latest version
            makeCalendarVisible(immutableExistingEvent, userId)
        }

        // If no update is needed, return the existing event id
        return IcsSurgeryUtils.HandleIcsResult.Success(immutableExistingEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EventNotFound, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = immutableExistingEvent.isRecurring())
    }

    private suspend fun ICalendar.setAttendeesXPmToken(userId: UserId, isOrganizerMode: Boolean): Boolean {
        val missingToken = this.events.first().attendees.firstOrNull { attendee ->
            attendee.getParameter(X_PM_TOKEN) == null
        } != null
        val eventUid = this.events.first().uid?.value
        if (missingToken && eventUid != null) {
            val canonicalEmails = canonicalEmailsUseCase.invoke(userId, this.events.first().attendees.mapNotNull { it.extractEmail() })
            
            if (isOrganizerMode && canonicalEmails.values.any { it.isNullOrEmpty() }) return false

            this.events.first().attendees.forEach { attendee ->
                val attendeeCanonicalEmail =
                    if (attendee.extractEmail() != null) canonicalEmails[attendee.extractEmail()]
                    else if (isOrganizerMode) return false
                    else attendee.email ?: return false // We still need to generate tokens for invalid emails when in attendee mode
                if (attendee.getParameter(X_PM_TOKEN) == null && attendeeCanonicalEmail != null) {
                    val token = ICalUtilsImpl.generateXPmToken(attendeeCanonicalEmail, eventUid)
                    attendee.addParameter(X_PM_TOKEN, token)
                }
            }
        }
        return true
    }

    private suspend fun updateEventAsAnAttendee(newEvent: Event, existingEvent: Event, canonicalUserEmails: List<String>?, userAttendee: Attendee?, userId: UserId): IcsSurgeryUtils.HandleIcsResult {
        // Update existing event as an attendee

        val newICalendar = newEvent.iCalendar.clone()

        val updatedEvent = if (newICalendar.method.isCancel) {
            // TODO Cancel just one occurrence: if the ICS contains a RECURRENCE-ID which matches an occurrence of the series for which no previous single edit exists.
            //  In that case you have to create a single edit with status CANCELLED and no alarms.

            // Cancel the event via the sync route by changing STATUS, DTSTAMP (update with the ICS DTSTAMP), and drop the alarms
            existingEvent.iCalEvent.status = Status.cancelled()
            existingEvent.clearAlarms()
            existingEvent.iCalEvent.dateTimeStamp = newICalendar.events.first().dateTimeStamp
            existingEvent
        } else {
            canonicalUserEmails?.let {
                val currentParticipationStatus = existingEvent.getParticipationStatus(canonicalUserEmails)

                val currentSequence = existingEvent.iCalEvent.sequence?.value
                if (currentSequence != null && currentSequence < newEvent.iCalEvent.sequence.value) {
                    // Sequence changed, clear participation status
                    newICalendar.events.first().attendees.firstOrNull { it == userAttendee }?.participationStatus =
                        ParticipationStatus.NEEDS_ACTION
                } else {
                    // Sequence did not change, keep current participation status
                    newICalendar.events.first().attendees.firstOrNull { it == userAttendee }?.participationStatus =
                        currentParticipationStatus

                    // Copy existing alarms
                    newICalendar.events.first().alarms.clear()
                    newICalendar.events.first().alarms.addAll(existingEvent.iCalEvent.alarms)
                }
            }

            val notifications = NotificationMigration(existingEvent.notifications.isMigrated, newICalendar.events.firstOrNull()?.alarms?.mapNotNull { Notification.fromVAlarm(it) })

            Event.from(existingEvent, iCalendar = newICalendar, notifications = notifications)
        }

        return editCreateEventFromIcs(
            IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT,
            userId,
            updatedEvent
        )
    }

    private suspend fun updateEventAsAnOrganizer(existingEvent: Event, existingEventEntity: EventEntity, iCalendar: ICalendar, userId: UserId): IcsSurgeryUtils.HandleIcsResult {
        // Update existing event as an organizer

        // Handle party crashers in replies
        val updatedAttendee = iCalendar.events.first().attendees.firstOrNull()
        val updatedAttendeeEmail = updatedAttendee?.extractEmail() ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError()
        val canonicalAttendeeEmail = canonicalEmailsUseCase.invoke(userId, listOf(updatedAttendeeEmail))[updatedAttendeeEmail] ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError()
        val existingEventCanonicalAttendeeEmails = canonicalEmailsUseCase.invoke(userId, existingEvent.iCalEvent.attendees.mapNotNull { it.extractEmail() })

        if (existingEvent.iCalEvent.attendees?.none {
                canonicalAttendeeEmail == existingEventCanonicalAttendeeEmails[it.extractEmail()]
            } == true) return IcsSurgeryUtils.HandleIcsResult.Error.ReplyPartyCrasher(existingEvent.id)


        val isRsvpCommentsEnabled = featureFlagManager.getOrDefault(
            userId,
            CalendarFeatureFlag.RsvpCommentsAndroid.featureId,
            FeatureFlag.default(
                CalendarFeatureFlag.RsvpCommentsAndroid.featureId.id,
                CalendarFeatureFlag.RsvpCommentsAndroid.fallbackValue
            )
        )

        val eventAttendees = if (isRsvpCommentsEnabled.value && existingEventEntity.attendeesInfo?.isNotEmpty() == true) {
            existingEventEntity.attendeesInfo
        } else {
            existingEventEntity.attendees
        }
        // Get attendees part from existing event entity
        val attendees = eventAttendees.map {
            json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
        }

        existingEvent.iCalEvent.attendees?.forEach { attendee ->

            if (attendee.extractEmail().equals(updatedAttendeeEmail, true)) {
                val attendeeToken = attendee.getParameter(X_PM_TOKEN)
                val attendeeStatusEvent = attendees.find { it.token == attendeeToken }

                // Find the current update time value for this attendee from the event entity attendees part
                val existingUpdateTime = attendeeStatusEvent?.updateTime
                val newUpdateTime = TimeUnit.MILLISECONDS.toSeconds(iCalendar.events.first().dateTimeStamp.value.time).toInt()

                // If new update time is more recent then update participation status, else do nothing and open event details
                if (existingUpdateTime == null || existingUpdateTime < newUpdateTime) {

                    val calendarId = existingEvent.calendar.id

                    // Update participation status for the attendee that replied
                    val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
                        userId,
                        calendarId,
                        existingEvent.id,
                        attendeeStatusEvent?.id ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(),
                        updatedAttendee.participationStatus.toInt(),
                        null,
                        null,
                        newUpdateTime
                    )

                    if (updateParticipationStatusUseCaseResult !is UseCase.Result.Success<*>) {
                        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }
                        return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError()
                    }

                    makeCalendarVisible(existingEvent, userId)

                    return IcsSurgeryUtils.HandleIcsResult.Success(
                        eventId = existingEvent.id,
                        IcsSurgeryUtils.HandleIcsAction.UPDATE_EVENT,
                        Pair(updatedAttendeeEmail, updatedAttendee.participationStatus),
                        isRecurring = existingEvent.isRecurring()
                    )
                }
            }
        }

        makeCalendarVisible(existingEvent, userId)
        return IcsSurgeryUtils.HandleIcsResult.Success(existingEvent.id, IcsSurgeryUtils.HandleIcsAction.OPEN_EVENT, isRecurring = existingEvent.isRecurring())
    }

    private suspend fun editCreateEventFromIcs(action: IcsSurgeryUtils.HandleIcsAction, userId: UserId, newEvent: Event): IcsSurgeryUtils.HandleIcsResult {
        val createLinkedEventAsAttendee =
            action == IcsSurgeryUtils.HandleIcsAction.CREATE_EVENT && newEvent.hasProtonProtonProperties
        when (val editCreateEventResult = editCreateEventUseCase.execute(userId, newEvent, createLinkedEventAsAttendee = createLinkedEventAsAttendee)) {
            is UseCase.Result.Success<*> -> {
                var eventId: String? = null
                editCreateEventResult.returnValue.tryCast<List<String>> {
                    eventId = this.firstOrNull()
                }

                makeCalendarVisible(newEvent, userId)

                return IcsSurgeryUtils.HandleIcsResult.Success(eventId = eventId ?: return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(), action, isRecurring = newEvent.isRecurring())
            }
            is UseCase.Result.InvalidParams -> {
                logger.i("HandleIcsUseCase: invalid params in create event: ${editCreateEventResult.message}")
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(editCreateEventResult.userErrorMessage)
            }
            is UseCase.Result.Error -> {
                logger.i("HandleIcsUseCase: error in create event: ${editCreateEventResult.message}")
                return IcsSurgeryUtils.HandleIcsResult.Error.EditCreateEventError(editCreateEventResult.userErrorMessage)
            }
        }
    }

    private suspend fun makeCalendarVisible(event: Event, userId: UserId) {
        if (!event.calendar.display) {
            // 1. Update in DB
            calendarsRepository.updateCalendarDisplay(event.calendar.id, true)
            // 2. Update on Server
            updateCalendarUseCase.executeUpdateDisplayFromDb(userId, event.calendar.id)
        }
    }
}
