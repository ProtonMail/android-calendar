package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class LeaveManagedCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_LEAVE_CALENDAR"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        return when (val leaveCalendarResponse =
            calendarsApi.leaveManagedCalendar(userId, calendarId)
        ) {
            is ApiResponse.Success -> {
                // Delete Calendar from DB
                calendarsRepository.deleteCalendarById(calendarId)
                return UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                if (leaveCalendarResponse.httpCode == 503) calendarsRepository.pingServer(userId)
                logger.e("api error join calendar: $leaveCalendarResponse")
                UseCase.Result.Error(leaveCalendarResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error join calendar: $leaveCalendarResponse")
                UseCase.Result.Error(leaveCalendarResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}