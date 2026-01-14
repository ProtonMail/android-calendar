package me.proton.android.calendar.domain.usecase

import biweekly.component.VAlarm
import com.google.crypto.tink.subtle.Base64
import com.proton.gopenpgp.crypto.SessionKey
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.JoinCalendarApiRequest
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.data.entity.getSessionKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.key.domain.publicKey
import me.proton.core.key.domain.signText
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import javax.inject.Inject

class JoinCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val userManager: UserManager,
    private val userAddressManager: UserAddressManager,
    private val cryptoContext: CryptoContext,
    private val crypto: Crypto,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val json: Json
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_JOIN_CALENDAR"
    }

    suspend fun joinHolidayCalendar(
        userId: UserId,
        managedHolidayCalendarEntity: ManagedHolidayCalendarEntity,
        calendarColor: String,
        defaultFullDayNotifications: List<VAlarm>?,
        priority: Int? = null
    ): UseCase.Result {

        val defaultUserEmail = userManager.getUser(userId).email
        val address = userAddressManager.getAddressesOrNull(userId)?.firstOrNull { address ->
            defaultUserEmail?.let { address.email == it } ?: address.canSend && address.canReceive
        } ?: return UseCase.Result.Error("JoinCalendarUseCase: No valid Address found")

        val memberAddressKey = address.keys.primary() ?: return UseCase.Result.Error("JoinCalendarUseCase: No valid Primary Address Key found for Address")

        // Encrypt the session key with the member primary key
        val sessionKeyEntity = managedHolidayCalendarEntity.getSessionKeyEntity(json)
        val sessionKey = SessionKey(
            Base64.decode(sessionKeyEntity.key),
            sessionKeyEntity.algorithm
        )
        val keyPacket = crypto.getKeyPacket(sessionKey, memberAddressKey.privateKey.publicKey(cryptoContext).key)

        // Sign the passphrase using the primary address key
        val tokenSignature = kotlin.runCatching { memberAddressKey.privateKey.signText(cryptoContext, managedHolidayCalendarEntity.passphrase) }.getOrNull() ?: return UseCase.Result.Error("JoinCalendarUseCase: could not sign token")

        val joinCalendarApiRequest = JoinCalendarApiRequest(
            signature = tokenSignature,
            passphraseKeyPacket = keyPacket!!,
            color = calendarColor,
            defaultFullDayNotifications = defaultFullDayNotifications?.map {
                NotificationEntity(
                    type = if (it.action.isEmail) 0 else 1,
                    trigger = it.trigger.duration.toString()
                )
            },
            priority = priority
        )

        return when (val joinCalendarResponse =
            calendarsApi.joinCalendar(userId, managedHolidayCalendarEntity.calendarId, address.addressId.id, joinCalendarApiRequest)
        ) {
            is ApiResponse.Success -> {
                val calendarId = joinCalendarResponse.data.calendar.id
                val memberEntity = joinCalendarResponse.data.members.firstOrNull() ?: return UseCase.Result.Error("JoinCalendarUseCase: memberEntity was null")
                val calendarKeyEntity = joinCalendarResponse.data.keys.firstOrNull() ?: return UseCase.Result.Error("JoinCalendarUseCase: calendarKeyEntity was null")
                val passphraseEntity = joinCalendarResponse.data.passphrase

                // Save Calendar to DB
                calendarsRepository.persistCalendar(userId.id, joinCalendarResponse.data.calendar)
                // Save Calendar Settings
                calendarsRepository.persistCalendarSettings(joinCalendarResponse.data.calendarSettings)
                // Save Member
                calendarsRepository.persistMember(memberEntity)
                // Save Calendar Key
                calendarsRepository.persistCalendarKey(calendarKeyEntity)
                // Save passphrase
                calendarsRepository.persistCalendarPassphrase(passphraseEntity)

                // cache Passphrase
                val cachePassphraseResult =
                    cacheCalendarPassphraseUseCase.execute(userId, passphraseEntity.calendarId)

                return when (cachePassphraseResult) {
                    is UseCase.Result.Success<*> -> {
                        UseCase.Result.Success(calendarId)
                    }
                    is UseCase.Result.Error -> {
                        logger.e("JoinCalendarUseCase: Error when caching Passphrase: ${cachePassphraseResult.message}")
                        cachePassphraseResult
                    }
                    is UseCase.Result.InvalidParams -> {
                        logger.e("JoinCalendarUseCase: InvalidParams when caching Passphrase: ${cachePassphraseResult.message}")
                        cachePassphraseResult
                    }
                }
            }
            is ApiResponse.Error -> {
                if (joinCalendarResponse.httpCode == 503) calendarsRepository.pingServer(userId)
                logger.e("api error join calendar: $joinCalendarResponse")
                UseCase.Result.Error(joinCalendarResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error join calendar: $joinCalendarResponse")
                UseCase.Result.Error(joinCalendarResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
