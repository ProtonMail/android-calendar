package me.proton.android.calendar.domain.usecase

import com.proton.gopenpgp.crypto.SessionKey
import me.proton.android.calendar.common.SESSION_KEY_ALGO
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UpgradeEventApiRequest
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.decryptSessionKey
import me.proton.core.key.domain.useKeys
import me.proton.core.network.domain.ResponseCodes
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.util.kotlin.takeIfNotEmpty
import javax.inject.Inject

/**
 * Upgrades Event's AddressKeyPacket to SharedKeyPacket by re-encrypting it with CalendarKey and sending to server.
 */
class UpgradeEventUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val cryptoContext: CryptoContext,
    private val calendarsRepository: CalendarsRepository,
    private val userAddressManager: UserAddressManager,
    private val crypto: Crypto,
    private val database: AppDatabase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val fetchEventWithCommentsUseCase: GetEventWithCommentsUseCase
) : UseCase {

    suspend fun execute(userId: UserId, eventId: String): UseCase.Result {

        val eventEntity = database.eventsDao().selectById(eventId)
            ?: return UseCase.Result.InvalidParams("UpgradeEventUseCase: could not get EventEntity from DB")

        if (eventEntity.sharedKeyPacket != null) return UseCase.Result.Success(eventEntity)

        if (eventEntity.addressKeyPacket == null || eventEntity.addressId == null) return UseCase.Result.InvalidParams("UpgradeEventUseCase: not enough data for upgrading Event")

        val userAddresses = userAddressManager.getAddressesOrNull(userId)?.takeIfNotEmpty()
            ?: return UseCase.Result.InvalidParams("UpgradeEventUseCase: User Addresses is empty")

        val calendarPrimaryPrivateKey =
            database.calendarKeysDao().select(eventEntity.calendarId).firstOrNull { it.isActiveAndPrimary }?.privateKey
                ?: return UseCase.Result.InvalidParams("UpgradeEventUseCase: there is no active primary key for calendar")

        val userAddressForAddressKeyPacket = userAddresses.find { it.addressId.id == eventEntity.addressId }
            ?: return UseCase.Result.InvalidParams("UpgradeEventUseCase: User Address for decrypting not found")

        val encryptedSharedSessionKey = userAddressForAddressKeyPacket.useKeys(cryptoContext) {
            // decrypt SharedSessionKey with AddressKey
            decryptSessionKey(
                com.google.crypto.tink.subtle.Base64.decode(
                    eventEntity.addressKeyPacket,
                    com.google.crypto.tink.subtle.Base64.DEFAULT
                )
            ).use {
                // encrypt it with CalendarKey and return KeyPackets
                crypto.getKeyPacket(
                    SessionKey(it.key, SESSION_KEY_ALGO),
                    crypto.getArmoredPublicKey(calendarPrimaryPrivateKey)
                        ?: return UseCase.Result.InvalidParams("UpgradeEventUseCase: could not extract Calendar Public Key for encrypting")
                )
            }
        } ?: return UseCase.Result.Error("UpgradeEventUseCase: could not encrypt SharedSessionKey")

        return when (val upgradeResponse = calendarsApi.upgradeEvent(
            userId,
            eventEntity.calendarId,
            eventEntity.id,
            UpgradeEventApiRequest(encryptedSharedSessionKey)
        )) {
            is ApiResponse.Success -> {
                if (upgradeResponse.data.event.sharedKeyPacket == null) {
                    UseCase.Result.Error("UpgradeEventUseCase: Event returned after upgrading has empty sharedKeyPacket")
                } else {
                    calendarsRepository.persistEvents(upgradeResponse.data.event.toEventEntity())
                    updateEventOccurrencesUseCase.execute(userId.id, upgradeResponse.data.event.toEventEntityMetadata())
                    UseCase.Result.Success(upgradeResponse.data.event.toEventEntity())
                }
            }
            is ApiResponse.Error -> {
                if (upgradeResponse.errorCode == ResponseCodes.NOT_ALLOWED) {
                    // race condition with another client, Event is already upgraded, fetch and persist it
                    val event = fetchEventWithCommentsUseCase.execute(userId, eventEntity.calendarId, eventEntity.id)
                        .valueOrNullAndLogErrors(logger, "UpgradeEventUseCase")?.event
                    if (event == null) {
                        UseCase.Result.Error("UpgradeEventUseCase: could not fetch Event after NOT_ALLOWED")
                    } else if (event.sharedKeyPacket == null) {
                        UseCase.Result.Error("UpgradeEventUseCase: Event fetched after NOT_ALLOWED has empty sharedKeyPacket")
                    } else {
                        calendarsRepository.persistEvents(event.toEventEntity())
                        updateEventOccurrencesUseCase.execute(userId.id, event.toEventEntityMetadata())
                        UseCase.Result.Success(event.toEventEntity())
                    }
                } else {
                    UseCase.Result.Error("UpgradeEventUseCase: error: ${upgradeResponse.error}")
                }
            }
            is ApiResponse.Exception -> UseCase.Result.Error("UpgradeEventUseCase: error: ${upgradeResponse.exception.message ?: "(no exception message)"}")
        }

    }

}
