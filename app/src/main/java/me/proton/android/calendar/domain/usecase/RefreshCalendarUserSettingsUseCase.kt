package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarUserSettingsUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val settingsApi: SettingsApi,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase
) {

    companion object {
        const val REFRESH_CALENDAR_USER_SETTINGS = "REFRESH_CALENDAR_USER_SETTINGS"
    }

    suspend operator fun invoke(
        userId: UserId
    ): UseCase.Result {

        val calendarUserSettingsResponse = settingsApi.getCalendarUserSettings(userId)
        if (calendarUserSettingsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarUserSettingsUseCase: error getting calendar user settings from API: $calendarUserSettingsResponse")
        }

        val calendarUserSettings = calendarUserSettingsResponse.data.calendarUserSettings

        // Persist new settings in DB
        calendarsRepository.persistCalendarUserSettings(
            userId.id,
            calendarUserSettings
        )

        // Apply the change to event alarms if time zone changed
        calendarUserSettingsChangedUseCase.handlePrimaryTimezoneChange(userId.id)

        return UseCase.Result.Success(calendarUserSettings)
    }


}
