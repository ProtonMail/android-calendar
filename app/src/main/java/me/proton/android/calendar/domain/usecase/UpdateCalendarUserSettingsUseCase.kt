package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.timezoneApiOverrides
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.util.kotlin.toInt
import javax.inject.Inject

class UpdateCalendarUserSettingsUseCase @Inject constructor(
    private val logger: Logger,
    private val settingsApi: SettingsApi,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID_TZ = "WORKER_ID_TZ"
        const val WORKER_ID_AUTO_DETECT = "WORKER_ID_AUTO_DETECT"
        const val WORKER_ID_WEEK_NUMBER = "WORKER_ID_WEEK_NUMBER"
        const val WORKER_ID_AUTO_IMPORT_INVITE = "WORKER_ID_AUTO_IMPORT_INVITE"
        const val WORKER_ID_DEFAULT_CALENDAR_ID = "WORKER_ID_DEFAULT_CALENDAR_ID"
    }

    suspend fun executePrimaryTimezone(userId: UserId, primaryTimezoneId: String): UseCase.Result {
        val primaryTimezone = timezoneApiOverrides[primaryTimezoneId] ?: primaryTimezoneId
        val response = settingsApi.updateCalendarUserPrimaryTimezone(userId, primaryTimezone)
        return when (response) {
            is ApiResponse.Success -> {
                calendarsRepository.persistCalendarUserSettings(
                    userId.id,
                    response.data.calendarUserSettings
                )
                calendarUserSettingsChangedUseCase.handlePrimaryTimezoneChange(userId.id)
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user primary timezone: $response")
                UseCase.Result.Error(response.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user primary timezone: $response")
                UseCase.Result.Error(response.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun executeAutoDetectPrimaryTimezone(userId: UserId, autoDetectPrimaryTimezone: Boolean): UseCase.Result {
        return when (
            val updateCalendarUserAutoDetectTimezoneResponse =
            settingsApi.updateCalendarUserAutoDetectTimezone(userId, autoDetectPrimaryTimezone.toInt())
        ) {
            is ApiResponse.Success -> {
                calendarsRepository.updateCalendarUserSettingsAutoDetectPrimaryTimezone(
                    userId.id,
                    updateCalendarUserAutoDetectTimezoneResponse.data.calendarUserSettings.autoDetectPrimaryTimezone
                )
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user auto detect primary timezone: $updateCalendarUserAutoDetectTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserAutoDetectTimezoneResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user auto detect primary timezone: $updateCalendarUserAutoDetectTimezoneResponse")
                UseCase.Result.Error(updateCalendarUserAutoDetectTimezoneResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun executeDisplayWeekNumber(userId: UserId, displayWeekNumber: Boolean): UseCase.Result {
        return when (
            val updateCalendarUserDisplayWeekNumberResponse =
                settingsApi.updateCalendarUserDisplayWeekNumber(userId, displayWeekNumber.toInt())
        ) {
            is ApiResponse.Success -> {
                calendarsRepository.updateCalendarUserSettingsDisplayWeekNumber(
                    userId.id,
                    updateCalendarUserDisplayWeekNumberResponse.data.calendarUserSettings.displayWeekNumber
                )
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user display week number: $updateCalendarUserDisplayWeekNumberResponse")
                UseCase.Result.Error(updateCalendarUserDisplayWeekNumberResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user display week number: $updateCalendarUserDisplayWeekNumberResponse")
                UseCase.Result.Error(updateCalendarUserDisplayWeekNumberResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun executeDefaultCalendarId(userId: UserId, defaultCalendarId: String): UseCase.Result {
        return when (
            val updateCalendarUserDefaultCalendarIdResponse =
                settingsApi.updateCalendarUserDefaultCalendarId(userId, defaultCalendarId)
        ) {
            is ApiResponse.Success -> {
                calendarsRepository.updateCalendarUserDefaultCalendarId(
                    userId.id,
                    defaultCalendarId
                )
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user default calendar id: $updateCalendarUserDefaultCalendarIdResponse")
                UseCase.Result.Error(updateCalendarUserDefaultCalendarIdResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user default calendar id: $updateCalendarUserDefaultCalendarIdResponse")
                UseCase.Result.Error(updateCalendarUserDefaultCalendarIdResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun executeAutoImportInvite(userId: UserId, autoImportInvite: Boolean): UseCase.Result {
        return when (
            val updateCalendarUserAutoImportInviteResponse =
                settingsApi.updateCalendarUserAutoImportInvite(userId, autoImportInvite)
        ) {
            is ApiResponse.Success -> {
                calendarsRepository.updateCalendarUserAutoImportInvite(
                    userId.id,
                    autoImportInvite
                )
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar user auto import invite: $updateCalendarUserAutoImportInviteResponse")
                UseCase.Result.Error(updateCalendarUserAutoImportInviteResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar user auto import invite: $updateCalendarUserAutoImportInviteResponse")
                UseCase.Result.Error(updateCalendarUserAutoImportInviteResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
