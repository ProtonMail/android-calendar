package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.entity.UserSettings
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import javax.inject.Inject

class UpdateUserSettingsUseCase @Inject constructor(
    private val logger: Logger,
    private val settingsApi: SettingsApi,
    private val userSettingsRepository: UserSettingsRepository
): UseCase {

    companion object {
        const val WORKER_ID_TIME_FORMAT = "WORKER_ID_TIME_FORMAT"
        const val WORKER_ID_WEEK_START = "WORKER_ID_WEEK_START"
    }

    suspend fun executeTimeFormat(userId: UserId, timeFormat: Int): UseCase.Result {
        return when (val updateUserTimeFormatResponse =
            settingsApi.updateUserTimeFormat(userId, timeFormat)
        ) {
            is ApiResponse.Success -> {
                val responseValue = UserSettings.TimeFormat.enumOf(updateUserTimeFormatResponse.data.userSettings.timeFormat)
                val localSettings = userSettingsRepository.getUserSettings(userId).copy(timeFormat = responseValue)
                userSettingsRepository.updateUserSettings(localSettings)
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating user time format: $updateUserTimeFormatResponse")
                UseCase.Result.Error(updateUserTimeFormatResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating user time format: $updateUserTimeFormatResponse")
                UseCase.Result.Error(updateUserTimeFormatResponse.exception.message ?: "(no exception message)")
            }
        }
    }

    suspend fun executeWeekStart(userId: UserId, weekStart: Int): UseCase.Result {
        return when (val updateUserWeekStartResponse =
            settingsApi.updateUserWeekStart(userId, weekStart)
        ) {
            is ApiResponse.Success -> {
                val responseValue = UserSettings.WeekStart.enumOf(updateUserWeekStartResponse.data.userSettings.weekStart)
                val localSettings = userSettingsRepository.getUserSettings(userId).copy(weekStart = responseValue)
                userSettingsRepository.updateUserSettings(localSettings)
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating user week start: $updateUserWeekStartResponse")
                UseCase.Result.Error(updateUserWeekStartResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating user week start: $updateUserWeekStartResponse")
                UseCase.Result.Error(updateUserWeekStartResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
