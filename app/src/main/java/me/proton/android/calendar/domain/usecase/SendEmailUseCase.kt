package me.proton.android.calendar.domain.usecase

import androidx.annotation.VisibleForTesting
import biweekly.io.TimezoneInfo
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.Method
import com.google.crypto.tink.subtle.Base64
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
import me.proton.android.calendar.common.INVITE_EMAIL_MIME_TYPE
import me.proton.android.calendar.common.INVITE_ICS_FILE_NAME
import me.proton.android.calendar.common.INVITE_ICS_MIME_TYPE_TEMPLATE
import me.proton.android.calendar.common.utils.AndroidUtils.tryCastOrNull
import me.proton.android.calendar.common.utils.CryptoUtilsImpl.isValidForEncryption
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getCancelIcs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getInviteIcs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getResponseIcs
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressOrNull
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.util.Date
import javax.inject.Inject

class SendEmailUseCase @Inject constructor(
    private val logger: Logger,
    private val sendEmailDirectUseCase: SendEmailDirect,
    private val userAddressManager: UserAddressManager,
    private val json: Json,
    private val transformEventUseCase: TransformEventUseCase,
    private val calendarsRepository: CalendarsRepository,
    private val valueStoreProvider: ValueStoreProvider,
    private val crypto: Crypto,
    private val editCreateEventUseCase: EditCreateEventUseCase,
    private val resourceProvider: ResourceProvider,
    private val cryptoContext: CryptoContext,
    private val upgradeEventUseCase: UpgradeEventUseCase
): UseCase {

    suspend fun sendReplyToOrganizer(
        userId: UserId,
        event: Event,
        originalTimeZoneInfo: TimezoneInfo?,
        userAttendee: Attendee,
        organizerEmail: String,
        participationStatus: ParticipationStatus,
        sendPreferences: Map<Email, SendPreferences>,
        dtStamp: Date,
        eventEntity: EventEntity?,
        isProtonProtonInvite: Boolean,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean
    ): UseCase.Result {

        val userAttendeeEmail = userAttendee.extractEmail() ?: return UseCase.Result.InvalidParams("SendEmailUseCase userAttendee has empty email")
        val mailContent = getEmailContent(event, defaultTimeZone, timeFormatIs24Hours, MailType.REPLY, participationStatus, userAttendeeEmail)

        val ics = if (isProtonProtonInvite && eventEntity != null) {

            val upgradedEventEntity = (upgradeEventUseCase.execute(userId, eventEntity.id) as? UseCase.Result.Success<*>)?.returnValue.tryCastOrNull<EventEntity>() ?: return UseCase.Result.Error("SendEmailUseCase could not upgrade Event. Failed to cast upgrade result to EventEntity")

            val sharedPropertiesResult = getSharedProperties(userId, upgradedEventEntity)
            if (sharedPropertiesResult !is UseCase.Result.Success<*>) return sharedPropertiesResult

            getResponseIcs(
                event.iCalendar,
                userAttendee,
                participationStatus,
                originalTimeZoneInfo,
                dtStamp,
                isProtonProtonInvite,
                (sharedPropertiesResult.returnValue as Pair<*, *>).first as String,
                (sharedPropertiesResult.returnValue as Pair<*, *>).second as String
            )
        } else getResponseIcs(event.iCalendar, userAttendee, participationStatus, originalTimeZoneInfo, dtStamp, isProtonProtonInvite)

        val userAttendeeCanonicalEmail = canonicalizeProtonEmail(userAttendeeEmail, forceCanonicalization = true)
        val senderAddressId = userAddressManager.getAddressesOrNull(userId)?.find {
            canonicalizeProtonEmail(it.email, forceCanonicalization = true) == userAttendeeCanonicalEmail
        }?.addressId?.id ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendReplyToOrganizer failed to get address ID for sender") // TODO better error

        val senderAddress =
            userAddressManager.getAddressesOrNull(userId)?.find {
                it.addressId.id == senderAddressId
            } ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendReplyToOrganizer failed to get address for sender") // TODO better error

        if (!senderAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get UserAddress valid for encryption to organizer", UseCase.Error.Crypto.UserAddressInvalidForEncryption)
        }

        val attachmentBytes = ics.toByteArray()

        val sendEmailArguments = SendEmailDirect.Arguments(
            mailContent.first,
            mailContent.second,
            INVITE_EMAIL_MIME_TYPE,
            listOf(organizerEmail),
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE_TEMPLATE.format(Method.REPLY),
                    attachmentBytes
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddress, sendEmailArguments, sendPreferences)) {
            is SendEmailDirect.Result.Success -> return UseCase.Result.Success<Unit>()
            else -> UseCase.Result.Error("SendEmailUseCase sendReplyToOrganizer failed to send email to organizer: $sendEmailResult")
        }
    }

    suspend fun sendInviteToAttendees(
        userId: UserId,
        newEvent: Event,
        isCreate: Boolean,
        editedEvent: Event? = null,
        sendPreferences: Map<Email, SendPreferences>,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean,
        sendEmailUpdate: Boolean? = null
    ): UseCase.Result {

        val mailContent = getEmailContent(newEvent, defaultTimeZone, timeFormatIs24Hours, MailType.INVITE, sendEmailUpdate = sendEmailUpdate)

        val newEventEntity = (upgradeEventUseCase.execute(userId, newEvent.id) as? UseCase.Result.Success<*>)?.returnValue.tryCastOrNull<EventEntity>() ?: return UseCase.Result.Error("SendEmailUseCase could not upgrade Event. Failed to cast upgrade result to EventEntity")

        val sharedPropertiesResult = getSharedProperties(userId, newEventEntity)
        if (sharedPropertiesResult !is UseCase.Result.Success<*>) return sharedPropertiesResult

        val event =
            if (isCreate) transformEventUseCase.execute(newEventEntity) ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendInviteToAttendees failed to transform event entity")
            else editedEvent ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendInviteToAttendees edited event was null")

        if (isCreate) {
            // Add attendees
            newEvent.iCalEvent.attendees.forEach {
                event.iCalEvent.addAttendee(it)
            }
        }

        val ics = getInviteIcs(
            Event.from(event),
            (sharedPropertiesResult.returnValue as Pair<*, *>).first as String,
            (sharedPropertiesResult.returnValue as Pair<*, *>).second as String
        )

        val senderAddressResult = getSenderAddress(userId, newEventEntity, event.calendar.addressId)
        if (senderAddressResult !is UseCase.Result.Success<*>) return senderAddressResult

        val attachmentBytes = ics.toByteArray()

        val attendeeEmails = newEvent.iCalEvent.attendees.mapNotNull { it.extractEmail() }.filter { attendeeEmail ->
            // In case of edit of an invitation, participants that have errors in
            // send preferences are not removed from the event. We need to filter them out here.
            sendPreferences.keys.any { email ->
                attendeeEmail == email
            }
        }

        val sendEmailArguments = SendEmailDirect.Arguments(
            mailContent.first,
            mailContent.second,
            INVITE_EMAIL_MIME_TYPE,
            attendeeEmails,
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE_TEMPLATE.format(Method.REQUEST),
                    attachmentBytes
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddressResult.returnValue as UserAddress, sendEmailArguments, sendPreferences)) {
            is SendEmailDirect.Result.Success -> {

                if (!isCreate) return UseCase.Result.Success<Unit>()

                // Edit same event to add attendees if mail(s) have been sent

                // we need to pass SendPreferences for Auto-Added Invites
                val editEventResult = editCreateEventUseCase.execute(userId, event, sendPreferences = sendPreferences)

                if (editEventResult is UseCase.Result.InvalidParams) {
                    logger.e("SendEmailUseCase sendInviteToAttendees invalid params in edit event: ${editEventResult.message}")
                }
                if (editEventResult is UseCase.Result.Error) {
                    logger.e("SendEmailUseCase sendInviteToAttendees error in edit event: ${editEventResult.message}")
                }

                // If this edit fails then that's too bad, attendees will be notified but the organizer won't see them in the event so hopefully she will retry on her own will

                return UseCase.Result.Success<Unit>()
            }
            else -> UseCase.Result.Error("SendEmailUseCase sendInviteToAttendees failed to send email to organizer: $sendEmailResult")
        }
    }

    suspend fun sendCancellationToAttendees(
        userId: UserId,
        event: Event,
        attendeeEmails: List<String>,
        sendPreferences: Map<Email, SendPreferences>,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean
    ): UseCase.Result {

        val mailContent = getEmailContent(event, defaultTimeZone, timeFormatIs24Hours, MailType.CANCELLATION)

        val eventEntity = calendarsRepository.selectEventEntity(event.id) ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendCancellationToAttendees failed to select event entity")
        val sharedEventId = eventEntity.sharedEventId ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendCancellationToAttendees sharedEventID was null")

        val ics = getCancelIcs(
            event,
            sharedEventId
        )

        val senderAddressResult = getSenderAddress(userId, eventEntity, event.calendar.addressId)
        if (senderAddressResult !is UseCase.Result.Success<*>) return senderAddressResult

        val attachmentBytes = ics.toByteArray()

        val sendEmailArguments = SendEmailDirect.Arguments(
            mailContent.first,
            mailContent.second,
            INVITE_EMAIL_MIME_TYPE,
            attendeeEmails,
            listOf(
                SendEmailDirect.Arguments.Attachment(
                    INVITE_ICS_FILE_NAME,
                    attachmentBytes.size,
                    INVITE_ICS_MIME_TYPE_TEMPLATE.format(Method.CANCEL),
                    attachmentBytes
                )
            )
        )

        return when (val sendEmailResult = sendEmailDirectUseCase.invoke(senderAddressResult.returnValue as UserAddress, sendEmailArguments, sendPreferences)) {
            is SendEmailDirect.Result.Success -> {
                return UseCase.Result.Success<Unit>()
            }
            else -> UseCase.Result.Error("SendEmailUseCase sendCancellationToAttendees failed to send email to organizer: $sendEmailResult")
        }
    }

    private suspend fun getSharedProperties(userId: UserId, eventEntity: EventEntity): UseCase.Result {
        val sharedEventId = eventEntity.sharedEventId ?: return UseCase.Result.InvalidParams("SendEmailUseCase getSharedProperties sharedEventID was null")
        val calendarId = eventEntity.calendarId

        val calendarPrivateKeys = calendarsRepository.selectCalendarKeys(calendarId).filter { it.isActive }.map { it.privateKey }.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendInviteToAttendees: there are no active keys for calendar")
        val calendarPassphraseList = calendarsRepository.selectCalendarPassphrases(calendarId)
        if (calendarPassphraseList.isEmpty()) return UseCase.Result.InvalidParams("SendEmailUseCase getSharedProperties: there are no passphrase for calendar")
        val calendarPassphrase = calendarPassphraseList.map { it.toPassphrase(json) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId.id).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("SendEmailUseCase sendInviteToAttendees: there is no valid cached Calendar Passphrase") // gitleaks:allow

        if (eventEntity.sharedKeyPacket == null) {
            return UseCase.Result.InvalidParams("SendEmailUseCase getSharedProperties: EventEntity is not upgraded")
        }

        val sharedSessionKey = Base64.encode(crypto.decryptSessionKey(eventEntity.sharedKeyPacket, calendarPrivateKeys, keyPassphrase.toByteArray())?.key)

        return UseCase.Result.Success(
            Pair(sharedEventId, sharedSessionKey)
        )
    }

    private suspend fun getSenderAddress(userId: UserId, eventEntity: EventEntity, addressId: String?): UseCase.Result {
        val senderAddress =
            addressId?.let {
                userAddressManager.getAddressOrNull(userId, addressId)
                    ?: return UseCase.Result.InvalidParams("SendEmailUseCase getSenderAddress failed to get address for sender") // TODO better error
            } ?: run {
                val member = calendarsRepository.selectCalendarUserMember(eventEntity.calendarId) ?: return UseCase.Result.InvalidParams("SendEmailUseCase getSenderAddress: member was null")
                val senderAddressId = calendarsRepository.getAddressForMember(userId, member.addressId, member.id, member.canonicalEmail)?.addressId?.id ?: return UseCase.Result.InvalidParams("SendEmailUseCase getSenderAddress failed to get address ID for sender") // TODO better error

                userAddressManager.getAddressesOrNull(userId)?.find {
                    it.addressId.id == senderAddressId
                } ?: return UseCase.Result.InvalidParams("SendEmailUseCase getSenderAddress failed to get address for sender") // TODO better error
            }

        if (!senderAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get UserAddress valid for encryption to attendees", UseCase.Error.Crypto.UserAddressInvalidForEncryption)
        }

        return UseCase.Result.Success(senderAddress)
    }

    enum class MailType {
        INVITE,
        REPLY,
        CANCELLATION
    }

    private fun getEmailContent(
        newEvent: Event,
        defaultTimeZone: String,
        timeFormatIs24Hours: Boolean,
        mailType: MailType,
        newParticipationStatus: ParticipationStatus? = null,
        userAttendeeEmail: String? = null,
        sendEmailUpdate: Boolean? = null
    ): Pair<String, String> {
        val eventCopy = Event.from(newEvent)
        return Pair(
            // Subject
            getMailSubject(eventCopy, defaultTimeZone, timeFormatIs24Hours, mailType, sendEmailUpdate),
            // Body
            when (mailType) {
                MailType.CANCELLATION -> getCancelMailBody(eventCopy.summary)
                MailType.REPLY -> getReplyMailBody(newParticipationStatus, userAttendeeEmail, newEvent.summary)
                MailType.INVITE -> getInviteMailBody(eventCopy, defaultTimeZone, timeFormatIs24Hours, sendEmailUpdate)
            }
        )
    }

    private fun getMailSubject(
        event: Event,
        timezone: String,
        timeFormatIs24Hours: Boolean,
        mailType: MailType,
        sendEmailUpdate: Boolean?
    ): String {
        return if (!event.isAllDay()) {
            val dateTimeStart =
                event.formatStart(timezone, timeFormatIs24Hours)
            resourceProvider.provideString(
                when (mailType) {
                    MailType.CANCELLATION -> R.string.event_send_cancel_mail_subject_part_day
                    MailType.REPLY -> R.string.event_change_answer_mail_subject_part_day
                    MailType.INVITE -> {
                        if (sendEmailUpdate == true) R.string.event_send_update_invite_mail_subject_part_day
                        else R.string.event_send_invite_mail_subject_part_day
                    }
                },
                dateTimeStart.first,
                dateTimeStart.second,
                DateTimeUtilsImpl.formatTimeZoneId(
                    timezone,
                    event.iCalEvent.dateStart.value.toInstant(),
                    displayId = false
                )
            )
        } else if (!event.spansSingleDay(true, timeZoneId = timezone)) {
            resourceProvider.provideString(
                when (mailType) {
                    MailType.CANCELLATION -> R.string.event_send_cancel_mail_subject_multiple_day
                    MailType.REPLY -> R.string.event_change_answer_mail_subject_multiple_day
                    MailType.INVITE -> {
                        if (sendEmailUpdate == true) R.string.event_send_update_invite_mail_subject_multiple_day
                        else R.string.event_send_invite_mail_subject_multiple_day
                    }
                },
                event.formatStart(
                    timezone,
                    timeFormatIs24Hours
                ).first
            )
        } else {
            resourceProvider.provideString(
                when (mailType) {
                    MailType.CANCELLATION -> R.string.event_send_cancel_mail_subject_all_day
                    MailType.REPLY -> R.string.event_change_answer_mail_subject_all_day
                    MailType.INVITE -> {
                        if (sendEmailUpdate == true) R.string.event_send_update_invite_mail_subject_all_day
                        else R.string.event_send_invite_mail_subject_all_day
                    }
                },
                event.formatStart(
                    timezone,
                    timeFormatIs24Hours
                ).first
            )
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getInviteMailBody(
        event: Event,
        timezone: String,
        timeFormatIs24Hours: Boolean,
        sendEmailUpdate: Boolean?
    ): String {
        val formattedDateStart = event.formatStart(timezone, timeFormatIs24Hours)
        val formattedDateEnd = event.formatEnd(timezone, timeFormatIs24Hours)
        var body = resourceProvider.provideString(
            if (sendEmailUpdate == true) R.string.event_send_update_invite_mail_body
            else R.string.event_send_invite_mail_body,
            event.summary ?: resourceProvider.provideString(R.string.default_event_summary),
            if (event.isAllDay() && !event.spansSingleDay(timeZoneId = timezone)) {
                resourceProvider.provideString(
                    R.string.event_send_invite_mail_body_all_day_multiple,
                    formattedDateStart.first,
                    formattedDateEnd.first
                )
            } else if (event.isAllDay()) {
                resourceProvider.provideString(
                    R.string.event_send_invite_mail_body_all_day_single,
                    formattedDateStart.first
                )
            } else {
                resourceProvider.provideString(
                    R.string.event_send_invite_mail_body_part_day,
                    formattedDateStart.first,
                    formattedDateStart.second,
                    DateTimeUtilsImpl.formatTimeZoneId(
                        timezone,
                        event.iCalEvent.dateStart.value.toInstant(),
                        displayId = false
                    ),
                    formattedDateEnd.first,
                    formattedDateEnd.second,
                    DateTimeUtilsImpl.formatTimeZoneId(
                        timezone,
                        event.iCalEvent.dateEnd.value.toInstant(),
                        displayId = false
                    )
                )
            }
        )
        if (event.location != null) body += resourceProvider.provideString(
            R.string.event_send_invite_mail_body_where,
            event.location
        )
        if (event.description != null) body += resourceProvider.provideString(
            R.string.event_send_invite_mail_body_description,
            event.description
        )
        return body
    }

    private fun getCancelMailBody(summary: String?): String {
        return resourceProvider.provideString(
            R.string.event_send_cancel_mail_body,
            summary ?: resourceProvider.provideString(R.string.default_event_summary)
        )
    }

    private fun getReplyMailBody(participationStatus: ParticipationStatus?, userAttendeeEmail: String?, summary: String?): String {
        return when (participationStatus) {
            ParticipationStatus.ACCEPTED -> resourceProvider.provideString(R.string.event_change_answer_mail_body_accepted, userAttendeeEmail ?: "", summary ?: resourceProvider.provideString(R.string.default_event_summary))
            ParticipationStatus.DECLINED -> resourceProvider.provideString(R.string.event_change_answer_mail_body_declined, userAttendeeEmail ?: "", summary ?: resourceProvider.provideString(R.string.default_event_summary))
            ParticipationStatus.TENTATIVE -> resourceProvider.provideString(R.string.event_change_answer_mail_body_tentative, userAttendeeEmail ?: "", summary ?: resourceProvider.provideString(R.string.default_event_summary))
            else -> "" // TODO Shouldn't happen ?
        }
    }
}
