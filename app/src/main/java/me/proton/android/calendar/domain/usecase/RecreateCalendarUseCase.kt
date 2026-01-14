package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class RecreateCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val calendarsRepository: CalendarsRepository,
) : UseCase {

    suspend fun execute(userId: UserId, calendarId: String): UseCase.Result {

        // Deletes the calendar and creates a new one with the same name, description, color, and display.
        val recreateCalendarResponse = calendarsApi.recreateCalendar(userId, calendarId)

        if (recreateCalendarResponse is ApiResponse.Error && recreateCalendarResponse.httpCode == 403 && recreateCalendarResponse.errorCode == 403) {
            return UseCase.Result.InvalidParams("recreateCalendarUseCase: password confirmation failed")
        }

        val newCalendar = recreateCalendarResponse.valueOrNullAndLogErrors(logger)?.calendar

        return if (newCalendar != null) {
            // Delete previous calendar from local database
            calendarsRepository.deleteCalendarById(calendarId)

            // Persist new calendar data in local database (calendar, member, calendar settings) and do key setup
            createCalendarUseCase.handleCalendarCreated(userId, newCalendar)

            // Set the new default Calendar in API but ignore error
            updateCalendarUserSettingsUseCase.executeDefaultCalendarId(userId, newCalendar.id)
                .ifSuccessAndLogErrors(logger) { }

            UseCase.Result.Success<Unit>()
        } else {
            UseCase.Result.Error("RecreateCalendarUseCase: error deleting calendar")
        }

    }

}