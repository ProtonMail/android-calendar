package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustIncomingAllDayEvent
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.generateXPmToken
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sanitise
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.data.joinToCalendar
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.NotificationMigration
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.key.domain.decryptDataOrNull
import me.proton.core.key.domain.decryptSessionKey
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.extension.publicKeyRing
import me.proton.core.key.domain.useKeys
import me.proton.core.key.domain.verifyData
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.toBoolean
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class TransformEventUseCase @Inject constructor(
    private val json: Json,
    private val database: AppDatabase,
    private val userAddressManager: UserAddressManager,
    private val logger: Logger,
    private val valueStoreProvider: ValueStoreProvider,
    private val crypto: Crypto,
    private val iCal: ICalUtilsImpl,
    private val obtainPinnedKeysUseCase: ObtainPinnedKeysUseCase,
    private val cryptoContext: CryptoContext,
    private val featureFlagManager: FeatureFlagManager
) : UseCase { // TODO ADD TEST

    /**
     * @param allowApiCall only set to [true] if you don't need "synchronous" result
     */
    suspend fun execute(eventEntity: EventEntity, allowApiCall: Boolean = false) : Event? {

        val calendarEntity = database.calendarsDao().selectById(eventEntity.calendarId) ?: return null
        val userId = calendarEntity.fkUserId
        val calendar = calendarEntity.joinToCalendar(database, json) ?: return null

        val calendarPrivateKeys = database.calendarKeysDao().select(eventEntity.calendarId).filter { it.isActive }.map { it.privateKey }
        if (calendarPrivateKeys.isEmpty()) {
            logger.e("TransformEventUseCase, calendarKey is null")
            return null
        }

        val calendarPassphrase = database.passphrasesDao().select(eventEntity.calendarId).map { it.toPassphrase(json) }.firstOrNull() { it.isActive }
        if (calendarPassphrase == null) {
            logger.e("TransformEventUseCase, calendarPassphrase is null")
            return null
        }

        val keyPassphrase = valueStoreProvider.provideValueStore(userId).getStringFromSet(ValueSet.CALENDAR_PASSPHRASE, calendarPassphrase.id) // gitleaks:allow
        if (keyPassphrase == null) {
            logger.e("TransformEventUseCase, keyPassphrase is null")
            return null
        }

        val userAddresses = userAddressManager.getAddressesOrNull(UserId(userId))
        if (userAddresses == null) {
            logger.e("TransformEventUseCase, userAddresses is null")
            return null
        }

        // used for decryption instead of CalendarKey if Event's SharedPart is encrypted with AddressKey
        val userAddressForAddressKeyPacket = userAddresses.find { it.addressId.id == eventEntity.addressId }

        val calendarParts = mutableListOf<String>()
        val verificationStatuses: MutableList<Event.SignatureVerification> = mutableListOf()
        val decryptionStatuses: MutableList<Event.DecryptionStatus> = mutableListOf()

        coroutineScope {

            // process Shared Events
            val processedSharedEvents = async {
                eventEntity.sharedEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Shared>(it)
                }.map { sharedEvent ->

                    if (eventEntity.addressKeyPacket != null) { // use Address Key with AddressKeyPacket
                        if (userAddressForAddressKeyPacket != null) {
                            getPlainText(
                                eventEntity.addressKeyPacket,
                                sharedEvent,
                                EncryptedWith.AddressKey(
                                    userAddressForAddressKeyPacket,
                                    cryptoContext,
                                    getPublicKeysForAuthor(UserId(userId), sharedEvent, userAddresses, allowApiCall)
                                )
                            )
                        } else ProcessResult(null, Event.DecryptionStatus.Failure.NoAddressKey, Event.SignatureVerification.FAILURE)
                    } else { // use Calendar Key with SharedKeyPacket
                        getPlainText(
                            eventEntity.sharedKeyPacket,
                            sharedEvent,
                            EncryptedWith.CalendarKey(
                                calendarPrivateKeys,
                                keyPassphrase,
                                getPublicKeysForAuthor(UserId(userId), sharedEvent, userAddresses, allowApiCall)
                            )
                        )
                    }

                }
            }

            // process Calendar Events
            val processedCalendarEvents = async {
                eventEntity.calendarEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Calendar>(it)
                }.map { calendarEvent ->
                    getPlainText(
                        eventEntity.calendarKeyPacket,
                        calendarEvent,
                        EncryptedWith.CalendarKey(
                            calendarPrivateKeys,
                            keyPassphrase,
                            getPublicKeysForAuthor(UserId(userId), calendarEvent, userAddresses, allowApiCall)
                        )
                    )
                }
            }

            // process Attendees Events
            val processedAttendeesEvents = async {
                eventEntity.attendeesEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Attendee>(it)
                }.map { attendeeEvent ->

                    if (eventEntity.addressKeyPacket != null) { // use Address Key with AddressKeyPacket
                        if (userAddressForAddressKeyPacket != null) {
                            getPlainText(
                                eventEntity.addressKeyPacket,
                                attendeeEvent,
                                EncryptedWith.AddressKey(
                                    userAddressForAddressKeyPacket,
                                    cryptoContext,
                                    getPublicKeysForAuthor(UserId(userId), attendeeEvent, userAddresses, allowApiCall)
                                )
                            )
                        } else ProcessResult(null, Event.DecryptionStatus.Failure.NoAddressKey, Event.SignatureVerification.FAILURE)
                    } else { // use Calendar Key with SharedKeyPacket
                        getPlainText(
                            eventEntity.sharedKeyPacket,
                            attendeeEvent,
                            EncryptedWith.CalendarKey(
                                calendarPrivateKeys,
                                keyPassphrase,
                                getPublicKeysForAuthor(UserId(userId), attendeeEvent, userAddresses, allowApiCall)
                            )
                        )
                    }

                }
            }

            listOf(processedSharedEvents, processedCalendarEvents, processedAttendeesEvents)
                .awaitAll().flatten().forEach { processResult ->
                    processResult.plainText?.let { calendarParts.add(it) }
                    decryptionStatuses.add(processResult.decryptionStatus)
                    verificationStatuses.add(processResult.signatureVerification)
                }

        }

        if (calendarParts.isEmpty()) return null

        val iCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts)

        if (iCalendar == null || iCalendar.events.isEmpty() || iCalendar.events.first().sanitise() == false) return null

        // Cross reference unencrypted Attendees and encrypted AttendeesEvents data to update participation status
        var currentUserAttendeeId: String? = null
        val attendeeComments = mutableMapOf<String, Pair<Event.SignatureVerification, String>>()
        if (!iCalendar.events.first().attendees.isNullOrEmpty()) {
            val canonicalUserEmails = userAddresses.map { canonicalizeProtonEmail(it.email, forceCanonicalization = true) }

            val isRsvpCommentsEnabled = featureFlagManager.getOrDefault(
                UserId(userId),
                CalendarFeatureFlag.RsvpCommentsAndroid.featureId,
                FeatureFlag.default(
                    CalendarFeatureFlag.RsvpCommentsAndroid.featureId.id,
                    CalendarFeatureFlag.RsvpCommentsAndroid.fallbackValue
                )
            )

            val eventAttendees = if (isRsvpCommentsEnabled.value && eventEntity.attendeesInfo?.isNotEmpty() == true) {
                eventEntity.attendeesInfo
            } else {
                eventEntity.attendees
            }
            // Get attendees part from existing event entity
            val attendees = eventAttendees.map {
                json.decodeFromJsonElement<Event.AttendeeStatusEvent>(it)
            }

            iCalendar.events.first().attendees.forEach { attendee ->
                val attendeeToken = attendee.getParameter(X_PM_TOKEN) ?: generateXPmToken(
                    canonicalizeProtonEmail(attendee.extractEmail() ?: ""), // Do not force canonicalization here
                    iCalendar.events.first().uid.value
                )
                val attendeeStatusEvent = attendees.find { it.token == attendeeToken }
                if (attendeeStatusEvent != null) {
                    val status = attendeeStatusEvent.participationStatus
                    if (canonicalUserEmails.any { it == canonicalizeProtonEmail(attendee.extractEmail() ?: "", forceCanonicalization = true) }) currentUserAttendeeId =
                        attendeeStatusEvent.id
                    attendee.participationStatus = status
                }
                // decrypt and match comments to attendee emails
                attendee.extractEmail()?.let { attendeeEmail ->
                    val plaintextComment = attendeeStatusEvent?.comment?.let {
                        decryptAttendeeComment(
                            it,
                            eventEntity,
                            userId,
                            attendeeEmail,
                            userAddressForAddressKeyPacket,
                            userAddresses,
                            allowApiCall,
                            calendarPrivateKeys,
                            keyPassphrase
                        )
                    }
                    plaintextComment?.takeIf { it.decryptionStatus == Event.DecryptionStatus.Success && it.plainText?.isNotBlank() == true }?.let {
                        attendeeComments[attendeeEmail] = it.signatureVerification to (it.plainText ?: "")
                    }

                    Unit // prevent false positive error logs below
                } ?: run {
                    logger.e("TransformEventUseCase, attendee email is null when matching comments")
                }
            }
        }

        // TODO move sanitising to helper function?
        iCalendar.adjustIncomingAllDayEvent()

        return Event.from(
            id = eventEntity.id,
            calendar = Calendar(
                calendarEntity.id,
                calendar.name,
                calendar.email,
                calendar.ownerEmail,
                calendar.description,
                calendar.color,
                calendar.priority,
                calendar.addressId,
                calendar.memberId,
                calendar.flags,
                calendar.display,
                calendarEntity.type,
                calendar.permissions,
                calendar.defaultEventDuration,
                calendar.defaultPartDayNotifications,
                calendar.defaultFullDayNotifications
            ),
            iCalendar = iCalendar,
            modifyTime = eventEntity.modifyTime,
            verificationStatus = when {
                verificationStatuses.all { it == Event.SignatureVerification.SUCCESS } -> {
                    Event.SignatureVerification.SUCCESS
                }
                verificationStatuses.any { it == Event.SignatureVerification.FAILURE } -> {
                    Event.SignatureVerification.FAILURE
                }
                verificationStatuses.any { it == Event.SignatureVerification.SIGNED_BUT_CANT_GET_KEYS } -> {
                    Event.SignatureVerification.SIGNED_BUT_CANT_GET_KEYS
                }
                verificationStatuses.any { it == Event.SignatureVerification.SIGNED_BUT_NO_KEYS } -> {
                    Event.SignatureVerification.SIGNED_BUT_NO_KEYS
                }
                verificationStatuses.all { it == Event.SignatureVerification.NOT_SIGNED || it == Event.SignatureVerification.SUCCESS } -> {
                    // if at least 1 part is NOT_SIGNED but the rest is NOT_SIGNED or SUCCESS, then we treat entire Event as NOT_SIGNED
                    Event.SignatureVerification.NOT_SIGNED
                }
                else -> null
            },
            decryptionStatus = when {
                decryptionStatuses.all { it == Event.DecryptionStatus.Success } -> {
                    Event.DecryptionStatus.Success
                }
                decryptionStatuses.any { it is Event.DecryptionStatus.Failure } -> {
                    decryptionStatuses.firstOrNull { it is Event.DecryptionStatus.Failure.NoAddressKey } ?: Event.DecryptionStatus.Failure.Generic
                }
                else -> null
            },
            currentUserAttendeeId = currentUserAttendeeId,
            sharedEventId = eventEntity.sharedEventId,
            isProtonProtonInvite = eventEntity.isProtonProtonInvite?.toBoolean(),
            notifications = NotificationMigration(true, eventEntity.notifications?.mapNotNull { json.decodeFromJsonElement<NotificationEntity>(it).toNotification() }),
            attendeeComments = attendeeComments,
            color = eventEntity.color
        )

    }

    private data class ProcessResult(
        val plainText: String?,
        val decryptionStatus: Event.DecryptionStatus,
        val signatureVerification: Event.SignatureVerification
    )

    /**
     * @return null if couldn't obtain keys because of error, empty list if there are no keys available
     */
    private suspend fun getPublicKeysForAuthor(userId: UserId, eventPart: Event.EventPart, userAddresses: List<UserAddress>, allowApiCall: Boolean): List<PublicKey>? {

        return kotlin.runCatching {

            val canonicalizedAuthorEmail = canonicalizeProtonEmail(eventPart.author, forceCanonicalization = true)

            // try to match EventPart's Author with User emails
            userAddresses.firstOrNull {
                canonicalizeProtonEmail(it.email, forceCanonicalization = true).equalsNoCase(canonicalizedAuthorEmail)
            }?.let {
                // current User is the Author of this EventPart
                return it.publicKeyRing(cryptoContext).keys.filter { key -> key.isActive }
            }

            if (allowApiCall) {
                // try to look for Author's pinned keys in Contacts and Public Key repository
                val pinnedKeyResult = obtainPinnedKeysUseCase.execute(userId, listOf(canonicalizedAuthorEmail))[eventPart.author]
                if (pinnedKeyResult is ObtainPinnedKeysUseCase.Result.Success) {
                    pinnedKeyResult.pinnedPublicKeys
                } else if (pinnedKeyResult is ObtainPinnedKeysUseCase.Result.Error.EmailNotInContacts || pinnedKeyResult is ObtainPinnedKeysUseCase.Result.Error.NoCorrectlySignedTrustedKeys) {
                    // case when we can't verify the signature and we don't treat it as error
                    emptyList()
                } else null // getting pinned keys failed for legitimate reason
            } else null // we can't look for pinned keys in API so we return error straight away

        }.getOrNull()
    }

    private sealed class EncryptedWith(open val authorsPublicKeys: List<PublicKey>?) {

        data class CalendarKey(
            val privateKeys: List<String>,
            val privateKeysPassphrase: String,
            override val authorsPublicKeys: List<PublicKey>?
        ): EncryptedWith(authorsPublicKeys)

        data class AddressKey(
            val userAddress: UserAddress,
            val cryptoContext: CryptoContext,
            override val authorsPublicKeys: List<PublicKey>?
        ): EncryptedWith(authorsPublicKeys)

    }

    /**
     * Get plaintext payload or decrypt & check signature if necessary.
     */
    private fun getPlainText(keyPacket: String?,
                             eventPart: Event.EventPart,
                             encryptedWith: EncryptedWith
    ): ProcessResult {

        // decrypt if necessary
        val plainText = if (eventPart.isEncrypted) {

            if (keyPacket != null) {
                when (encryptedWith) {
                    is EncryptedWith.CalendarKey -> {
                        val cipherText = Ciphertext.from(keyPacket, eventPart.data)
                        crypto.decryptText(cipherText.asArmoredPGPMessage(), encryptedWith.privateKeys, encryptedWith.privateKeysPassphrase.toByteArray())
                    }
                    is EncryptedWith.AddressKey -> {
                        encryptedWith.userAddress.useKeys(cryptoContext) {
                            kotlin.runCatching {
                                decryptSessionKey(Base64.decode(keyPacket, Base64.DEFAULT)).use {
                                    it.decryptDataOrNull(cryptoContext, Base64.decode(eventPart.data, Base64.DEFAULT))
                                }?.toString(StandardCharsets.UTF_8)
                            }.getOrNull()
                        }
                    }
                }
            } else null

        } else {
            eventPart.data
        }

        return if (plainText != null) {

            val signatureVerification: Event.SignatureVerification

            // verify signature if necessary
            if (eventPart.isSigned) {

                if (eventPart.signature != null) {
                    signatureVerification = if (encryptedWith.authorsPublicKeys == null) {
                        Event.SignatureVerification.SIGNED_BUT_CANT_GET_KEYS
                    } else if (encryptedWith.authorsPublicKeys?.isEmpty() == true) {
                        Event.SignatureVerification.SIGNED_BUT_NO_KEYS
                    } else {
                        val signatureOk = eventPart.signature?.let { signature ->
                            encryptedWith.authorsPublicKeys?.any {
                                it.verifyData(cryptoContext, plainText.toByteArray(), signature)
                            }
                        } ?: false

                        if (signatureOk) {
                            Event.SignatureVerification.SUCCESS
                        } else {
                            Event.SignatureVerification.FAILURE
                        }
                    }
                } else {
                    logger.v("EventPart ${eventPart.javaClass} is signed but there is no signature")
                    signatureVerification = Event.SignatureVerification.FAILURE
                }

            } else {
                signatureVerification = Event.SignatureVerification.NOT_SIGNED
            }

            ProcessResult(plainText, Event.DecryptionStatus.Success, signatureVerification)
        } else {
            ProcessResult(null, Event.DecryptionStatus.Failure.Generic, Event.SignatureVerification.FAILURE)
        }

    }

    private suspend fun decryptAttendeeComment(
        comment: Event.AttendeeStatusEventComment,
        eventEntity: EventEntity,
        userId: String,
        attendeeEmail: String,
        userAddressForAddressKeyPacket: UserAddress?,
        userAddresses: List<UserAddress>,
        allowApiCall: Boolean,
        calendarPrivateKeys: List<String>,
        keyPassphrase: String
    ): ProcessResult {

        val attendeeEvent = Event.EventPart.Attendee(
            comment.type,
            comment.message,
            null,
            attendeeEmail
        )

        return if (eventEntity.addressKeyPacket != null) { // use Address Key with AddressKeyPacket
            if (userAddressForAddressKeyPacket != null) {
                getPlainText(
                    eventEntity.addressKeyPacket,
                    attendeeEvent,
                    EncryptedWith.AddressKey(
                        userAddressForAddressKeyPacket,
                        cryptoContext,
                        getPublicKeysForAuthor(UserId(userId), attendeeEvent, userAddresses, allowApiCall)
                    )
                )
            } else ProcessResult(null, Event.DecryptionStatus.Failure.NoAddressKey, Event.SignatureVerification.FAILURE)
        } else { // use Calendar Key with SharedKeyPacket
            getPlainText(
                eventEntity.sharedKeyPacket,
                attendeeEvent,
                EncryptedWith.CalendarKey(
                    calendarPrivateKeys,
                    keyPassphrase,
                    getPublicKeysForAuthor(UserId(userId), attendeeEvent, userAddresses, allowApiCall)
                )
            )
        }
    }

}
