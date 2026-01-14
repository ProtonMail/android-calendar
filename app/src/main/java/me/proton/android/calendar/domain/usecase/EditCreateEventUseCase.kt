package me.proton.android.calendar.domain.usecase

import android.util.Base64
import biweekly.parameter.ParticipationStatus
import com.proton.gopenpgp.crypto.SessionKey
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.CustomICalPropertyParameter
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.SESSION_KEY_ALGO
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.tryCastOrNull
import me.proton.android.calendar.common.utils.CryptoUtilsImpl.isValidForEncryption
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sanitiseForExternal
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.SyncEvent
import me.proton.android.calendar.data.api.SyncEventCreateContainer
import me.proton.android.calendar.data.api.SyncEventUpdateContainer
import me.proton.android.calendar.data.api.SyncEventsUpdateApiRequest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Ciphertext
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.PackageType
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.encryptSessionKey
import me.proton.core.key.domain.entity.key.PrivateKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.takeIfNotEmpty
import me.proton.core.util.kotlin.toInt
import javax.inject.Inject

class EditCreateEventUseCase @Inject constructor(
    private val logger: Logger,
    private val json: Json,
    private val calendarsApi: CalendarsApi,
    private val cryptoContext: CryptoContext,
    private val calendarsRepository: CalendarsRepository,
    private val userAddressManager: UserAddressManager,
    private val crypto: Crypto,
    private val valueStoreProvider: ValueStoreProvider,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val upgradeEventUseCase: UpgradeEventUseCase
): UseCase {

    /**
     * @param [sendPreferences] needed for Auto-Added Invites to encrypt SharedKeyPacket with attendee's Public Address Key
     */
    suspend fun execute(
        userId: UserId,
        newEvent: Event,
        oldCalendarId: String = newEvent.calendar.id,
        createLinkedEventAsAttendee: Boolean = false,
        sendPreferences: Map<Email, SendPreferences> = emptyMap(),
        isImport: Boolean = false
    ) : UseCase.Result {

        val sanitizedNewEvent = Event.from(newEvent)
        sanitizedNewEvent.iCalEvent.sanitiseForExternal()

        val oldEventEntity = if (sanitizedNewEvent.isSyncedWithApi()) {
            (upgradeEventUseCase.execute(userId, sanitizedNewEvent.id) as? UseCase.Result.Success<*>)?.returnValue.tryCastOrNull<EventEntity>() ?: return UseCase.Result.Error("EditCreateEventUseCase could not upgrade Event. Failed to cast upgrade result to EventEntity")
        } else null

        val userAddresses = userAddressManager.getAddressesOrNull(userId)?.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: User Addresses is empty")

        // 0. split Event according to the matrix
        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(sanitizedNewEvent.iCalendar)

        // 1. get Member's AddressKey for signing
        val newMemberKey = when (val result = getMemberKey(userId, userAddresses, sanitizedNewEvent.calendar.id)) {
            is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<MemberKey>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting newMemberKey")
            else -> return result
        }

        // 2. get old CalendarKey for decrypting
        val oldCalendarKey =
            when (val result = getCalendarKey(userId.id, oldCalendarId)) {
                is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<CalendarKey>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting oldCalendarKey")
                else -> return result
            }

        // 3. get old Session Keys if they were already present in old Event
        val oldSessionKeys = if (oldEventEntity != null) {
            when (val result = extractSessionKeys(oldEventEntity, oldCalendarKey)) {
                is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<SessionKeys>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting oldSessionKeys")
                else -> return result
            }
        } else null

        val isCalendarBeingChanged = oldCalendarId != sanitizedNewEvent.calendar.id

        // 4. get new CalendarKey for encrypting
        val newCalendarKey = if (isCalendarBeingChanged) {
            when (val result = getCalendarKey(userId.id, sanitizedNewEvent.calendar.id)) {
                is UseCase.Result.Success<*> -> result.returnValue.tryCastOrNull<CalendarKey>() ?: return UseCase.Result.Error("EditCreateEventUseCase: Error casting newCalendarKey")
                else -> return result
            }
        } else {
            oldCalendarKey
        }

        // 5. sign and encrypt Shared Parts
        val sharedPartICalString = calendarSplit.sharedPart.printToString()

        val signatureOfSharedPart = kotlin.runCatching { newMemberKey.key.signText(cryptoContext, sharedPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfSharedPart")

        val sharedPartToEncryptICalString = calendarSplit.sharedPartToEncrypt.printToString()

        val encryptedSharedPartCiphertext =
            if (createLinkedEventAsAttendee) {
                val sharedSessionKeyProperty = sanitizedNewEvent.iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SESSION_KEY)?.value ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, shared session key was null")
                val sharedSessionKey = SessionKey(Base64.decode(sharedSessionKeyProperty, Base64.DEFAULT), SESSION_KEY_ALGO)
                // TODO migrate to PublicKey.encryptSessionKey(cryptoContext, sessionKeyBytes)
                val sharedKeyPacket = crypto.getKeyPacket(
                    sharedSessionKey,
                    crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, calendar public key was null")
                )
                Ciphertext.from(sharedKeyPacket, "")
            } else if (oldSessionKeys?.shared != null) { // Shared Session Key was there already, encrypt it with new Calendar Key
                val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, oldSessionKeys.shared)
                val sharedSessionKey = SessionKey(oldSessionKeys.shared.key, SESSION_KEY_ALGO)
                val sharedKeyPacket = crypto.getKeyPacket(
                    sharedSessionKey,
                    crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not get public key to encrypt sharedSessionKey with oldSessionKey")
                )
                Ciphertext.from(sharedKeyPacket, encryptedSharedPart!!)
            } else { // there was no Shared Session Key, encrypt and generate it
                val encryptedSharedPart = crypto.encryptText(sharedPartToEncryptICalString, crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey)
                    ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedSharedPart!!)
            }

        val signatureOfEncryptedSharedPart = kotlin.runCatching { newMemberKey.key.signText(cryptoContext, sharedPartToEncryptICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedSharedPart")

        // 6. sign and encrypt Calendar Parts (not always present)
        val calendarPartICalString = calendarSplit.calendarPart?.printToString()
        val signatureOfCalendarPart = calendarPartICalString?.run {
            kotlin.runCatching { newMemberKey.key.signText(cryptoContext, calendarPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfCalendarPart")
        }

        val calendarPartToEncryptICalString = calendarSplit.calendarPartToEncrypt?.printToString()
        val encryptedCalendarPartCiphertext = if (calendarPartToEncryptICalString != null) {
            if (oldSessionKeys?.calendar != null) {
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, oldSessionKeys.calendar)
                val calendarSessionKey = SessionKey(oldSessionKeys.calendar.key, SESSION_KEY_ALGO)
                val calendarKeyPacket = crypto.getKeyPacket(
                    calendarSessionKey,
                    crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not get public key to encrypt calendarSessionKey with oldSessionKey")
                )
                Ciphertext.from(calendarKeyPacket, encryptedCalendarPart!!)
            } else {
                val encryptedCalendarPart = crypto.encryptText(calendarPartToEncryptICalString, crypto.getArmoredPublicKey(newCalendarKey.primaryPrivateKey)
                    ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: could not extract Calendar Public Key for encrypting"))
                Ciphertext.from(encryptedCalendarPart!!)
            }
        } else null

        val signatureOfEncryptedCalendarPart = calendarPartToEncryptICalString?.run {
            kotlin.runCatching { newMemberKey.key.signText(cryptoContext, calendarPartToEncryptICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedCalendarPart")
        }

        // 8. sign and encrypt Attendees Part (not always present)
        val attendeesPartICalString = calendarSplit.attendeesPart?.printToString()

        val attendeesEventContent =
            if (createLinkedEventAsAttendee) null // We don't send the attendeesEventContent part when creating a linked event as an attendee
            else if (attendeesPartICalString != null) {
                val encryptedAttendeesPartCiphertext = if (oldSessionKeys?.shared != null) {
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, oldSessionKeys.shared)
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                } else {
                    val sharedSessionKey = crypto.decryptSessionKey(encryptedSharedPartCiphertext.encodedKeyPacket ?: return UseCase.Result.InvalidParams("encoded shared key packet was null when encrypting attendees"), newCalendarKey.privateKeys, newCalendarKey.passphrase)
                    val encryptedAttendeesPart = crypto.encryptText(attendeesPartICalString, sharedSessionKey ?: return UseCase.Result.InvalidParams("shared session key was null when encrypting attendees"))
                    Ciphertext.from(null, encryptedAttendeesPart!!)
                }

                val signatureOfEncryptedAttendeesPart = kotlin.runCatching { newMemberKey.key.signText(cryptoContext, attendeesPartICalString) }.getOrNull() ?: return UseCase.Result.Error("EditCreateEventUseCase: could not create signatureOfEncryptedAttendeesPart")

                listOf(
                    Event.EventPart.Attendee(
                        3,
                        encryptedAttendeesPartCiphertext.encodedDataPacket,
                        signatureOfEncryptedAttendeesPart,
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    )
                )
            } else null

        // 9. generate AddedProtonAttendees part if applicable
        val addedProtonAttendees = if (sendPreferences.isNotEmpty()) {
            val sharedSessionKey = crypto.decryptSessionKey(
                encryptedSharedPartCiphertext.encodedKeyPacket
                    ?: return UseCase.Result.InvalidParams("encoded shared key packet was null when encrypting attendees"),
                newCalendarKey.privateKeys,
                newCalendarKey.passphrase
            )?.key ?: return UseCase.Result.InvalidParams("could not decrypt shared key for added proton attendees")

            createAddedProtonAttendees(sendPreferences, sharedSessionKey)
        } else null

        // 10. assemble API request, depending on action we're taking
        val sharedEventContent =
            if (createLinkedEventAsAttendee) null
            else {
                listOf(
                    Event.EventPart.Shared(
                        2,
                        sharedPartICalString,
                        signatureOfSharedPart,
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    ),
                    Event.EventPart.Shared(
                        3,
                        encryptedSharedPartCiphertext.encodedDataPacket,
                        signatureOfEncryptedSharedPart,
                        "" // on server, "author" will be extracted from MemberID and this value ignored
                    )
                )
            }

        val calendarEventContent = listOfNotNull(
            if (calendarPartICalString != null && signatureOfCalendarPart != null) {
                Event.EventPart.Calendar(
                    2,
                    calendarPartICalString,
                    signatureOfCalendarPart,
                    "" // on server, "author" will be extracted from MemberID and this value ignored
                )
            } else null,
            if (encryptedCalendarPartCiphertext != null && signatureOfEncryptedCalendarPart != null) {
                Event.EventPart.Calendar(
                    3,
                    encryptedCalendarPartCiphertext.encodedDataPacket,
                    signatureOfEncryptedCalendarPart,
                    "" // on server, "author" will be extracted from MemberID and this value ignored
                )
            } else null
        ).ifEmpty { null }

        val attendees = arrayListOf<Event.AttendeeStatusEvent>()
        val newEventAttendees =
            if (createLinkedEventAsAttendee) {
                // The array must only contain one Attendee (the user itself) with his own token and answered participation status
                val canonicalMemberEmails = userAddresses.map { address ->
                    canonicalizeProtonEmail(address.email, forceCanonicalization = true)
                }
                val userAttendee = sanitizedNewEvent.iCalEvent.attendees.find { attendee ->
                    canonicalMemberEmails.firstOrNull { userEmail ->
                        val attendeeEmail = attendee.extractEmail()
                        attendeeEmail != null && canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true).equals(userEmail, ignoreCase = true)
                    } != null
                } ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: create linked event, could not get user attendee from sanitizedNewEvent")
                listOf(userAttendee)
            } else sanitizedNewEvent.iCalEvent.attendees

        newEventAttendees.forEach { attendee ->
            if (attendee.participationStatus == null) attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
            val status = attendee.participationStatus?.toInt() ?: ParticipationStatus.NEEDS_ACTION.toInt()
            attendee.extractEmail()?.let {
                val xpmToken = attendee.getParameter(X_PM_TOKEN) ?: ICalUtilsImpl.generateXPmToken(canonicalizeProtonEmail(it), sanitizedNewEvent.uid) // TODO Maybe use API route ?
                attendees.add(
                    Event.AttendeeStatusEvent(null, xpmToken, status, null)
                )
            }
        }

        val organizerEmail = sanitizedNewEvent.iCalEvent.organizer?.extractEmail()
        val isOrganizer =
            if (organizerEmail != null) {
                val canonicalUserEmails = userAddresses.map { canonicalizeProtonEmail(it.email, forceCanonicalization = true) }
                val canonicalOrganizerEmail = canonicalizeProtonEmail(organizerEmail, forceCanonicalization = true)
                canonicalUserEmails.any { canonicalOrganizerEmail == it }.toInt()
            } else if (sanitizedNewEvent.iCalEvent.attendees.isNullOrEmpty()) 1
            else 0

        val syncRequestBody = if (sanitizedNewEvent.isSyncedWithApi()) {
            if (isCalendarBeingChanged) {
                // CREATE in new calendar
                SyncEventsUpdateApiRequest(
                    events = listOf(
                        SyncEventCreateContainer(
                            event = SyncEvent(
                                permissions = 1,
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,
                                sharedEventContent = sharedEventContent,
                                calendarKeyPacket = encryptedCalendarPartCiphertext?.encodedKeyPacket,
                                calendarEventContent = calendarEventContent,
                                sharedEventId = sanitizedNewEvent.sharedEventId,
                                uid = sanitizedNewEvent.uid,
                                sourceCalendarId = oldCalendarId,
                                notifications = sanitizedNewEvent.notifications.notifications?.map { NotificationEntity.fromNotification(it) },
                                color = sanitizedNewEvent.color
                            )
                        )
                    )
                )
            } else { // UPDATE
                SyncEventsUpdateApiRequest(
                    events = listOf(
                        SyncEventUpdateContainer(
                            id = sanitizedNewEvent.id,
                            event = SyncEvent(
                                permissions = 1,
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = null, // this is already present in existing event
                                sharedEventContent = sharedEventContent,
                                calendarKeyPacket = if (oldSessionKeys?.calendar == null) encryptedCalendarPartCiphertext?.encodedKeyPacket else null, // only attach newly generated Calendar KeyPacket when updating
                                calendarEventContent = calendarEventContent,
                                attendeesEventContent = attendeesEventContent,
                                attendees = attendees.takeIfNotEmpty(), // TODO to remove all attendees from event, send null value

                                // We don't allow for editing invitations yet so we only really add new attendees when creating new invitation or editing non-invitation event.
                                // In both cases adding attendees happens in the "second call to /sync after the first one that created event", which is right here.
                                // If we sent the SharedSessionKey already before, the attendee has the event auto-created in their calendar already, so we are done.
                                addedProtonAttendees = addedProtonAttendees,
                                notifications = sanitizedNewEvent.notifications.notifications?.map { NotificationEntity.fromNotification(it) },
                                color = sanitizedNewEvent.color
                            )
                        )
                    )
                )
            }
        } else { // CREATE
            if (createLinkedEventAsAttendee) {
                // This is a proton to proton invite
                val sharedEventId = sanitizedNewEvent.iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID)?.value
                SyncEventsUpdateApiRequest(
                    events = listOf(
                        SyncEventCreateContainer(
                            event = SyncEvent(
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,
                                attendees = attendees,
                                sharedEventId = sharedEventId,
                                uid = sanitizedNewEvent.uid,
                                notifications = sanitizedNewEvent.notifications.notifications?.map { NotificationEntity.fromNotification(it) },
                                color = sanitizedNewEvent.color
                            )
                        )
                    )
                )
            } else {
                SyncEventsUpdateApiRequest(
                    events = listOf(
                        SyncEventCreateContainer(
                            event = SyncEvent(
                                permissions = 1,
                                isOrganizer = isOrganizer,
                                sharedKeyPacket = encryptedSharedPartCiphertext.encodedKeyPacket,
                                sharedEventContent = sharedEventContent,
                                calendarKeyPacket = encryptedCalendarPartCiphertext?.encodedKeyPacket,
                                calendarEventContent = calendarEventContent,
                                attendeesEventContent =
                                if (sanitizedNewEvent.iCalendar.method?.isRequest == true) attendeesEventContent // If we create an event from an invitation we provide attendees
                                else null, // We first create without attendees
                                attendees =
                                if (sanitizedNewEvent.iCalendar.method?.isRequest == true) attendees.takeIfNotEmpty()  // If we create an event from an invitation we provide attendees
                                else null, // We first create without attendees,
                                notifications = sanitizedNewEvent.notifications.notifications?.map { NotificationEntity.fromNotification(it) },
                                color = sanitizedNewEvent.color
                            ),
                            overwrite = isImport.toInt() // We always overwrite for imports
                        )
                    ),
                    isImport = isImport.toInt()
                )
            }
        }

        return when (val syncResponse = calendarsApi.syncEvents(userId, sanitizedNewEvent.calendar.id, syncRequestBody)) {
            is ApiResponse.Success -> {
                val eventsToInsertOrUpdate = syncResponse.data.responses.mapNotNull {
                    if (it.response.isSuccessful) {
                        it.response.event
                    } else {
                        logger.e("EditCreateEventUseCase: error in sync: ${it.response.code}: ${it.response.error}: ${it.response.errorDescription}")
                        null
                    }
                }

                val eventEntities = eventsToInsertOrUpdate.map { it.toEventEntity() }
                calendarsRepository.persistEvents(*eventEntities.toTypedArray())
                updateAlarmsUseCase.execute(userId.id, eventEntities)
                eventsToInsertOrUpdate.map { it.toEventEntityMetadata() }.forEach {
                    updateEventOccurrencesUseCase.execute(userId.id, it)
                }

                // TODO we don't need it anymore, since all alarms are calculated locally
                // fetch and store alarms for just changed events
                /*eventsToInsertOrUpdate.forEach { eventEntity ->
                    val alarmsResponse = calendarsApi.getEventAlarms(userId, eventEntity.calendarId, eventEntity.id)
                    when (alarmsResponse) {
                        is ApiResponse.Success -> {
                            database.eventAlarmsDao().deleteAllByEventId(eventEntity.id)
                            alarmsResponse.data.alarms.forEach { database.eventAlarmsDao().updateOrInsert(it) }
                        }
                        is ApiResponse.Error -> logger.e("error getting alarms for created/edited event: ${alarmsResponse.error}")
                        is ApiResponse.Exception -> logger.e("exceptiom getting alarms for created/edited event: ${alarmsResponse.exception}")
                    }
                }*/

                // TODO collect and handle multiple errors
                val syncError = syncResponse.data.responses.firstOrNull { !it.response.isSuccessful }
                if (syncError != null) {
                    if (syncError.response.code == 2904) {
                        return UseCase.Result.Error(
                            "EditCreateEventUseCase: one of sync responses is an error ${syncError.response.code} ${syncError.response.error}",
                            error = UseCase.Error.Sync.LostZoomAccess,
                            userErrorMessage = syncError.response.error
                        )
                    } else if (syncError.response.code == 2905) {
                        return UseCase.Result.Error(
                            "EditCreateEventUseCase: one of sync responses is an error ${syncError.response.code} ${syncError.response.error}",
                            error = UseCase.Error.Sync.ZoomLinkDoesNotExist,
                            userErrorMessage = syncError.response.error
                        )
                    } else {
                        UseCase.Result.Error(
                            "EditCreateEventUseCase: one of sync responses is an error ${syncError.response.code} ${syncError.response.error}",
                            userErrorMessage = syncError.response.error
                        )
                    }
                } else {
                    UseCase.Result.Success(eventsToInsertOrUpdate.map { it.id })
                }
            }
            is ApiResponse.Error -> {
                if (syncResponse.httpCode == 503) calendarsRepository.pingServer(userId)
                UseCase.Result.Error("EditCreateEventUseCase: error in sync events: ${syncResponse.error}")
            }
            is ApiResponse.Exception -> UseCase.Result.Error("EditCreateEventUseCase: error in sync events: ${syncResponse.exception.message ?: "(no exception message)"}")
        }

    }

    private data class SessionKeys(
        val shared: SessionKey?,
        val calendar: SessionKey?,
    )

    private data class CalendarKey(
        val primaryPrivateKey: String,
        val privateKeys: List<String>,
        val passphrase: ByteArray
    )

    private data class MemberKey(
        val memberId: String,
        val key: PrivateKey
    )

    private fun extractSessionKeys(eventEntity: EventEntity, calendarKey: CalendarKey): UseCase.Result {

        if (eventEntity.sharedKeyPacket == null) {
            return UseCase.Result.InvalidParams("EditCreateEventUseCase: EventEntity is not upgraded")
        }

        val sharedSessionKey = crypto.decryptSessionKey(eventEntity.sharedKeyPacket, calendarKey.privateKeys, calendarKey.passphrase)

        val calendarSessionKey = if (eventEntity.calendarKeyPacket != null) {
            crypto.decryptSessionKey(eventEntity.calendarKeyPacket, calendarKey.privateKeys, calendarKey.passphrase)
        } else null

        if (sharedSessionKey == null && eventEntity.sharedKeyPacket?.isNotBlank() == true) {
            return UseCase.Result.InvalidParams("EditCreateEventUseCase: failed to decrypt shared session key")
        }

        if (calendarSessionKey == null && !eventEntity.calendarKeyPacket.isNullOrBlank()) {
            return UseCase.Result.InvalidParams("EditCreateEventUseCase: failed to decrypt old calendar session key")
        }

        return UseCase.Result.Success(SessionKeys(sharedSessionKey, calendarSessionKey))
    }

    private suspend fun getCalendarKey(userId: String, calendarId: String): UseCase.Result {

        val calendarKeys = calendarsRepository.selectCalendarKeys(calendarId)
        val calendarPrimaryPrivateKey = calendarKeys.firstOrNull { it.isActiveAndPrimary }?.privateKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no active primary key for calendar")
        val calendarPrivateKeys = calendarKeys.filter { it.isActive }.map { it.privateKey }.takeIfNotEmpty() ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there are no active keys for calendar")
        val calendarPassphraseList = calendarsRepository.selectCalendarPassphrases(calendarId)

        if (calendarPassphraseList.isEmpty()) return UseCase.Result.InvalidParams("EditCreateEventUseCase: there are no passphrase for calendar")

        val calendarPassphrase = calendarPassphraseList.map { it.toPassphrase(json) }.first { it.isActive }
        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid cached Calendar Passphrase") // gitleaks:allow

        return UseCase.Result.Success(CalendarKey(calendarPrimaryPrivateKey, calendarPrivateKeys, keyPassphrase.toByteArray()))
    }

    private suspend fun getMemberKey(userId: UserId, userAddresses: List<UserAddress>, calendarId: String): UseCase.Result {

        val member = calendarsRepository.selectCalendarUserMember(calendarId) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: member was null")
        val memberAddress = calendarsRepository.getAddressForMember(userId, member.addressId, member.id, member.canonicalEmail, userAddresses) ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid Member Address")

        if (!memberAddress.isValidForEncryption(cryptoContext, logger)) {
            return UseCase.Result.Error("couldn't get MemberAddress valid for encryption in EditCreateEventUseCase", UseCase.Error.Crypto.UserAddressInvalidForEncryption)
        }

        val memberAddressKey = memberAddress.keys.primary()?.privateKey ?: return UseCase.Result.InvalidParams("EditCreateEventUseCase: there is no valid Primary Address Key for Member")

        return UseCase.Result.Success(MemberKey(member.id, memberAddressKey))
    }

    /**
     * Encrypts SharedKeyPacket with Attendee's public Address Keys
     */
    private fun createAddedProtonAttendees(sendPreferences: Map<Email, SendPreferences>, sharedSessionKey: ByteArray): List<Event.AddedAttendee>? {
        return sendPreferences.mapNotNull { (email, sendPreferences) ->
            if (sendPreferences.pgpScheme == PackageType.ProtonMail && sendPreferences.publicKey != null) {
                Event.AddedAttendee(
                    email,
                    Base64.encodeToString(PublicKey(sendPreferences.publicKey, true, true, true, true).encryptSessionKey(cryptoContext, me.proton.core.crypto.common.pgp.SessionKey(sharedSessionKey)), Base64.DEFAULT)
                )
            } else null
        }.takeIfNotEmpty()
    }

}
