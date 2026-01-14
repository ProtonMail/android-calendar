package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PassphraseApiRequest
import me.proton.android.calendar.data.api.SetupKeyApiRequest
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Ciphertext
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.encryptText
import me.proton.core.key.domain.entity.keyholder.KeyHolderPrivateKey
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.entity.UserAddress
import javax.inject.Inject

class KeySetupUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val crypto: Crypto,
    private val cryptoContext: CryptoContext,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val MEMBERS_KEY_SETUP = "MEMBERS_KEY_SETUP"
    }

    suspend fun execute(userId: UserId, addressId: String, memberAddressKey: KeyHolderPrivateKey, calendarId: String, memberId: String) : UseCase.Result {
        // Generate a random 32 bytes passphrase
        val calendarPassphrase = Base64.encode(Random.randBytes(32))

        // Create a new X25519 key that will be used as a Calendar key (please note that the UserID should be set to 'Calendar key')
        // and encrypt key with new 32 bytes string token
        val calendarPrivateKey =
            crypto.generateEccKey("not-a-name", "not-an-email@example.tld", calendarPassphrase.toByteArray())
                ?: return UseCase.Result.Error("KeySetupUseCase: generateEncryptedKey was null")

        // Encrypt and sign the calendar passphrase using the member’s AddressKey

        val encryptedToken = kotlin.runCatching { memberAddressKey.privateKey.encryptText(cryptoContext, calendarPassphrase) }.getOrNull() ?: return UseCase.Result.Error("KeySetupUseCase: could not encrypt token")

        val tokenSignature = kotlin.runCatching { memberAddressKey.privateKey.signText(cryptoContext, calendarPassphrase) }.getOrNull() ?: return UseCase.Result.Error("KeySetupUseCase: could not sign token")

        val keyPackets = mapOf(memberId to (Ciphertext.from(encryptedToken).encodedKeyPacket ?: return UseCase.Result.Error("KeySetupUseCase: KeyPackets was null")))

        val passphraseApiRequest = PassphraseApiRequest(
            Ciphertext.from(encryptedToken).encodedDataPacket,
            keyPackets
        )
        val setupKeyApiRequest = SetupKeyApiRequest(
            privateKey = calendarPrivateKey,
            signature = tokenSignature,
            addressId = addressId,
            passphrase = passphraseApiRequest
        )
        return when (val setupKeyApiResponse = calendarsApi.setupKey(userId, calendarId, setupKeyApiRequest)) {
            is ApiResponse.Success -> {
                UseCase.Result.Success(setupKeyApiResponse.data.calendarKey)
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error("KeySetupUseCase: error in setup key: ${setupKeyApiResponse.error}")
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error(
                    "KeySetupUseCase: error in setup key: ${setupKeyApiResponse.exception.message ?: "(no exception message)"}"
                )
            }
        }
    }

    suspend fun execute(userId: UserId, calendarId: String, addresses: List<UserAddress>? = null) : UseCase.Result {
        // Get member for address
        return when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)) {
            is ApiResponse.Success -> {

                val member = memberListApiResponse.data.members.firstOrNull()
                    ?: return UseCase.Result.Error("KeySetupUseCase: could not fetch member")

                // get Address for that Member
                val address = calendarsRepository.getAddressForMember(
                    userId,
                    member.addressId,
                    member.id,
                    member.canonicalEmail,
                    addresses
                ) ?: return UseCase.Result.Error("KeySetupUseCase: No Address found")

                val keySetupResult = execute(
                    userId,
                    address.addressId.id,
                    address.keys.primary() ?: return UseCase.Result.Error("KeySetupUseCase: No valid Primary Address Key found for Address"),
                    calendarId,
                    member.id)

                when (keySetupResult) {
                    is UseCase.Result.InvalidParams -> { logger.e("KeySetupUseCase: InvalidParams: ${keySetupResult.message}") }
                    is UseCase.Result.Error -> { logger.e("KeySetupUseCase: Error: ${keySetupResult.message}") }
                    is UseCase.Result.Success<*> -> Unit
                }

                return keySetupResult
            }
            is ApiResponse.Error -> UseCase.Result.Error("KeySetupUseCase: error in fetch members: ${memberListApiResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("KeySetupUseCase: error in fetch members: ${memberListApiResponse.exception.message ?: "(no exception message)"}")
        }
    }

    suspend fun handleMembersWithIncompleteKeySetup(userId: UserId, memberIds: List<String>): UseCase.Result {
        // Do handle incomplete keys on members if needed
        val failedKeySetups = arrayListOf<String>()
        coroutineScope {
            memberIds.map {
                async {
                    val memberEntity = calendarsRepository.selectMemberById(it) ?: run {
                        logger.e("handleMembersWithIncompleteKeySetup: failed to select member from DB")
                        return@async
                    }
                    if (!memberEntity.hasIncompleteKeySetup) return@async
                    when (val keySetupResult = execute(userId, memberEntity.calendarId)) {
                        is UseCase.Result.Success<*> -> {
                            val fetchedMember = calendarsRepository.fetchMembers(userId, memberEntity.calendarId)?.firstOrNull()
                            if (fetchedMember == null) {
                                logger.e("handleMembersWithIncompleteKeySetup: error getting member from API after keySetupUseCase success")

                                var calendarFlags = memberEntity.flags
                                calendarFlags -= MemberEntity.CalendarFlags.INCOMPLETE_SETUP.value
                                // if calendar is inactive and no other error flags are set, make it active
                                if (calendarFlags == 0) calendarFlags = MemberEntity.CalendarFlags.ACTIVE.value

                                calendarsRepository.persistMember(
                                    memberEntity.copy(flags = calendarFlags)
                                )
                            } else {
                                calendarsRepository.persistMember(fetchedMember)
                            }
                        }
                        is UseCase.Result.InvalidParams -> {
                            logger.e("handleMembersWithIncompleteKeySetup: keySetupResult invalid params: ${keySetupResult.message}")
                        }
                        is UseCase.Result.Error -> {
                            // Try and fetch the Member to check that the key setup wasn't done by another client in the meantime
                            val fetchedMember = calendarsRepository.fetchMembers(userId, memberEntity.calendarId)?.firstOrNull()
                            if (fetchedMember == null || fetchedMember.hasIncompleteKeySetup) {
                                failedKeySetups.add(memberEntity.id)
                                logger.e("handleMembersWithIncompleteKeySetup: keySetupResult error: ${keySetupResult.message}")
                            } else {
                                calendarsRepository.persistMember(fetchedMember)
                            }
                        }
                    }
                }
            }.awaitAll()
        }

        return if (failedKeySetups.isEmpty()) {
            UseCase.Result.Success<Unit>()
        } else {
            UseCase.Result.Error("Failed to do key setup for some members")
        }
    }
}
