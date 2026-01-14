package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.DEFAULT_CALENDAR_COLOR
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CreateCalendarApiRequest
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.key.domain.extension.primary
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.time.ZoneId
import javax.inject.Inject

class CreateCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val keySetupUseCase: KeySetupUseCase,
    private val userAddressManager: UserAddressManager,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val bootstrapCalendarUseCase: BootstrapCalendarUseCase
): UseCase {

    suspend fun execute(
        userId: UserId,
        name: String,
        description: String = "",
        color: String = DEFAULT_CALENDAR_COLOR,
        display: Int = 1,
        email: String? = null
    ) : UseCase.Result {

        val address = userAddressManager.getAddressesOrNull(userId)?.firstOrNull { address ->
            email?.let { address.email == it } ?: address.canSend && address.canReceive
        } ?: return UseCase.Result.Error("CreateCalendarUseCase: No valid Address found")

        val createCalendarApiRequest =
            CreateCalendarApiRequest(
                name = name,
                description = description,
                addressId = address.addressId.id,
                color = color,
                display = display
            )

        // Create calendar
        return when (val createCalendarApiResponse = calendarsApi.createCalendar(userId, createCalendarApiRequest)) {
            is ApiResponse.Success -> {

                handleCalendarCreated(userId, createCalendarApiResponse.data.calendar, address)
            }
            is ApiResponse.Error -> {
                if (createCalendarApiResponse.httpCode == 503) calendarsRepository.pingServer(userId)
                UseCase.Result.Error("CreateCalendarUseCase: error creating calendar: ${createCalendarApiResponse.error}")
            }
            is ApiResponse.Exception -> UseCase.Result.Error("CreateCalendarUseCase: error creating calendar: ${createCalendarApiResponse.exception.message ?: "(no exception message)"}")
        }
    }

    suspend fun handleCalendarCreated(userId: UserId, calendarEntity: CalendarEntity, address: UserAddress? = null): UseCase.Result {
        // Get member created for address
        return when (val memberListApiResponse = calendarsApi.getMemberList(userId, calendarEntity.id)) {
            is ApiResponse.Success -> {

                val calendarSettings = calendarsApi.getCalendarSettings(userId, calendarEntity.id).valueOrNullAndLogErrors(logger)?.calendarSettings

                // Save calendar in DB
                calendarsRepository.persistCalendar(userId.id, calendarEntity)

                calendarSettings?.let {
                    calendarsRepository.persistCalendarSettings(it)
                }

                // Save member in DB
                val memberEntity = memberListApiResponse.data.members.firstOrNull() ?: return UseCase.Result.Error("CreateCalendarUseCase: member was null")
                calendarsRepository.persistMember(memberEntity)

                val userAddress =
                    address ?: (calendarsRepository.getAddressForMember(userId, memberEntity.addressId, memberEntity.id, memberEntity.canonicalEmail) ?: return UseCase.Result.Error("CreateCalendarUseCase: No valid Address found"))

                val keySetupResult = keySetupUseCase.execute(
                    userId,
                    userAddress.addressId.id,
                    userAddress.keys.primary() ?: return UseCase.Result.Error("CreateCalendarUseCase: No valid Primary Address Key found for Address"),
                    calendarEntity.id,
                    memberEntity.id
                )

                when (keySetupResult) {
                    is UseCase.Result.Success<*> -> {

                        // save CalendarKey in DB
                        keySetupResult.returnValue.tryCast<CalendarKeyEntity> {
                            calendarsRepository.persistCalendarKey(this)
                        }

                        // TODO use another server call to only get the active Passphrase
                        // fetch CalendarPassphrase from server
                        val passphraseEntity = calendarsApi.getActivePassphrase(userId, calendarEntity.id)
                            .valueOrNullAndLogErrors(logger)?.passphrase
                            ?: return UseCase.Result.Error("CreateCalendarUseCase: could not fetch Calendar Passphrases")

                        // save Passphrase in DB
                        calendarsRepository.persistCalendarPassphrase(passphraseEntity)

                        val fetchedMember = calendarsRepository.fetchMembers(userId, calendarEntity.id)?.firstOrNull()
                        if (fetchedMember != null) {
                            calendarsRepository.persistMember(fetchedMember)
                        }

                        // cache Passphrase
                        val cachePassphraseResult =
                            cacheCalendarPassphraseUseCase.execute(userId, passphraseEntity.calendarId)

                        return when (cachePassphraseResult) {
                            is UseCase.Result.Success<*> -> {
                                UseCase.Result.Success(calendarEntity.id)
                            }
                            is UseCase.Result.Error -> {
                                logger.e("CreateCalendarUseCase: Error in KeySetupUseCase when caching Passphrase: ${cachePassphraseResult.message}")
                                cachePassphraseResult
                            }
                            is UseCase.Result.InvalidParams -> {
                                logger.e("CreateCalendarUseCase: InvalidParams in KeySetupUseCase when caching Passphrase: ${cachePassphraseResult.message}")
                                cachePassphraseResult
                            }
                        }

                    }
                    is UseCase.Result.InvalidParams -> {
                        logger.e("CreateCalendarUseCase: InvalidParams in KeySetupUseCase: ${keySetupResult.message}")
                        keySetupResult
                    }
                    is UseCase.Result.Error -> {
                        // Try and fetch the Member to check that the key setup wasn't done by another client in the meantime
                        val fetchedMember = calendarsRepository.fetchMembers(userId, calendarEntity.id)?.firstOrNull()
                        if (fetchedMember == null || fetchedMember.hasIncompleteKeySetup) {
                            logger.e("CreateCalendarUseCase: Error in KeySetupUseCase: ${keySetupResult.message}")
                            keySetupResult
                        } else { // key setup has been done on the server in the meantime

                            val fetchedCalendarEntity = calendarsRepository.fetchCalendarEntity(userId, calendarEntity.id)
                            if (fetchedCalendarEntity != null) {
                                val executeBootstrapResult = bootstrapCalendarUseCase.executeBootstrap(fetchedCalendarEntity, userId)
                                executeBootstrapResult.ifSuccessAndLogErrors(logger) { }
                                executeBootstrapResult
                            } else UseCase.Result.Error("could not fetch CalendarEntity to execute bootstrap")
                        }
                    }
                }

            }
            is ApiResponse.Error -> UseCase.Result.Error("CreateCalendarUseCase: error fetching members: ${memberListApiResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("CreateCalendarUseCase: error fetching members: ${memberListApiResponse.exception.message ?: "(no exception message)"}")
        }
    }
}
