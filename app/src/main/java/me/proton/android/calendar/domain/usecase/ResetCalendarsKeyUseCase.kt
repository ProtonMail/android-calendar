package me.proton.android.calendar.domain.usecase

import com.google.crypto.tink.subtle.Base64
import com.google.crypto.tink.subtle.Random
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.PassphraseApiRequest
import me.proton.android.calendar.data.api.ResetCalendarApiRequest
import me.proton.android.calendar.data.api.SetupKeyApiRequest
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.entity.keyholder.KeyHolderPrivateKey
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserAddressManager
import javax.inject.Inject

class ResetCalendarsKeyUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val crypto: Crypto,
    private val cryptoContext: CryptoContext,
    private val userAddressManager: UserAddressManager,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    suspend fun execute(userId: UserId) : UseCase.Result {

        // Get all the calendars to be reset
        val resetInfoResponse = calendarsApi.getResetInfo(userId)
        if (resetInfoResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("ResetCalendarsKeyUseCase: error getting calendars to reset from API: $resetInfoResponse")
        }

        val setupKeyApiRequestMap = hashMapOf<String, SetupKeyApiRequest>()

        val addresses = userAddressManager.getAddressesOrNull(userId) ?: return UseCase.Result.Error("ResetCalendarsKeyUseCase: error getting Addresses")

        // Reset key for each calendar
        resetInfoResponse.data.calendars.forEach {
            val calendarId = it.id

            // Get member for address
            when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarId)) {
                is ApiResponse.Success -> {

                    // Use admin member address
                    val adminMember = memberListApiResponse.data.members.firstOrNull { memberEntity ->
                        // TODO Take admin member that has decryptable key
                        memberEntity.hasPermission(MemberEntity.Permission.ADMIN)
                    } ?: return UseCase.Result.Error("ResetCalendarsKeyUseCase: no admin member")

                    val address = calendarsRepository.getAddressForMember(userId, adminMember.addressId, adminMember.id, adminMember.canonicalEmail, addresses) ?: return UseCase.Result.Error("ResetCalendarsKeyUseCase: No address found")

                    val memberAddressKey = address.keys.primary() ?: return UseCase.Result.Error("ResetCalendarsKeyUseCase: memberAddressKey was null")

                    setupKeyApiRequestMap[calendarId] = getSetupKeyApiRequest(
                        address.addressId.id,
                        memberAddressKey,
                        cryptoContext,
                        it.members
                    ) ?: return UseCase.Result.Error("ResetCalendarsKeyUseCase: getSetupKeyApiRequest was null")
                }
                is ApiResponse.Error -> UseCase.Result.Error("ResetCalendarsKeyUseCase: error fetching members: ${memberListApiResponse.error}")
                is ApiResponse.Exception -> UseCase.Result.Error(
                    "ResetCalendarsKeyUseCase: error fetching members: ${memberListApiResponse.exception.message ?: "(no exception message)"}"
                )
            }
        }

        // TODO: update ResetCalendarApiRequest and remove map once web has updated the route
        return when (val resetCalendarApiResponse = calendarsApi.resetCalendar(userId, ResetCalendarApiRequest(setupKeyApiRequestMap))) {
            is ApiResponse.Success -> {
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error("ResetCalendarsKeyUseCase: error in reset calendar: ${resetCalendarApiResponse.error}")
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error(
                    "ResetCalendarsKeyUseCase: error in reset calendar: ${resetCalendarApiResponse.exception.message ?: "(no exception message)"}"
                )
            }
        }
    }

    private fun getSetupKeyApiRequest(
        addressId: String,
        adminMemberAddressKey: KeyHolderPrivateKey,
        cryptoContext: CryptoContext,
        members: Map<String, String>) : SetupKeyApiRequest? {

        // Generate a random 32 bytes passphrase
        val calendarPassphrase = Base64.encode(Random.randBytes(32))

        // Create a new X25519 key that will be used as a Calendar key (please note that the UserID should be set to 'Calendar key')
        // and encrypt key with new 32 bytes string token
        val calendarPrivateKey =
            crypto.generateEccKey("not-a-name", "not-an-email@example.tld", calendarPassphrase.toByteArray())
        if (calendarPrivateKey == null) {
            logger.e("ResetCalendarsKeyUseCase: generateEncryptedKey was null")
            return null
        }

        // Sign the token using the admin member AddressKey
        val tokenSignature = kotlin.runCatching { adminMemberAddressKey.privateKey.signText(cryptoContext, calendarPassphrase) }.getOrNull()
        if (tokenSignature == null) {
            logger.e("ResetCalendarsKeyUseCase: signature was null")
            return null
        }

        // Encrypt the token with all the public keys in Members array
        val packets =
            crypto.encryptTextWithSessionKey(
                calendarPassphrase,
                ArrayList<String>(members.values)
            )

        val keyPackets = hashMapOf<String, String>()
        members.keys.forEachIndexed { index, memberId ->
            packets.second[index]?.let { keyPacket ->
                keyPackets[memberId] = keyPacket
            }
        }

        if (keyPackets.isNullOrEmpty()) {
            logger.e("ResetCalendarsKeyUseCase: keyPackets was null or empty")
            return null
        }

        val passphraseApiRequest = PassphraseApiRequest(
            packets.first,
            keyPackets
        )

        return SetupKeyApiRequest(
            privateKey = calendarPrivateKey,
            signature = tokenSignature,
            addressId = addressId,
            passphrase = passphraseApiRequest
        )
    }
}
