package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.extension.asCalendar
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarSettingsUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val calendarsApi: CalendarsApi,
    private val calendarSettingsChangedUseCase: CalendarSettingsChangedUseCase
) {

    companion object {
        const val REFRESH_CALENDAR_SETTINGS = "REFRESH_CALENDAR_SETTINGS"
    }

    suspend operator fun invoke(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        val calendarSettingsResponse = calendarsApi.getCalendarSettings(userId, calendarId)
        if (calendarSettingsResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarSettingsUseCase: error getting calendar settings from API: $calendarSettingsResponse")
        }

        val calendarSettings = calendarSettingsResponse.data.calendarSettings

        // Persist the new settings in DB
        calendarsRepository.persistCalendarSettings(calendarSettings)

        // Apply the new settings to events alarms
        calendarSettingsChangedUseCase.execute(userId, calendarSettings)

        return UseCase.Result.Success(calendarSettings)
    }


}
