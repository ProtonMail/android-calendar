package me.proton.android.calendar.domain.usecase

import biweekly.component.VAlarm
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UpdateCalendarSettingsApiRequest
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class UpdateCalendarSettingsUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val calendarSettingsChangedUseCase: CalendarSettingsChangedUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_CALENDAR_SETTINGS"
    }

    suspend fun updateCalendarSettings(
        userId: UserId,
        calendarId: String,
        defaultEventDuration: Int? = null,
        defaultPartDayNotifications: List<VAlarm>? = null,
        defaultFullDayNotifications: List<VAlarm>? = null
    ): UseCase.Result {
        val updateCalendarSettingsApiRequest = UpdateCalendarSettingsApiRequest(
            defaultEventDuration = defaultEventDuration,
            defaultPartDayNotifications = defaultPartDayNotifications?.map {
                NotificationEntity(
                    type = if (it.action.isEmail) 0 else 1,
                    trigger = it.trigger.duration.toString()
                )
            },
            defaultFullDayNotifications = defaultFullDayNotifications?.map {
                NotificationEntity(
                    type = if (it.action.isEmail) 0 else 1,
                    trigger = it.trigger.duration.toString()
                )
            }
        )
        return when (val updateCalendarSettingsResponse =
            calendarsApi.updateCalendarSettings(userId, calendarId, updateCalendarSettingsApiRequest)
        ) {
            is ApiResponse.Success -> {
                calendarSettingsChangedUseCase.execute(userId, updateCalendarSettingsResponse.data.calendarSettings)
                return UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                logger.e("api error updating calendar settings: $updateCalendarSettingsResponse")
                UseCase.Result.Error(updateCalendarSettingsResponse.error)
            }
            is ApiResponse.Exception -> {
                logger.e("api error updating calendar settings: $updateCalendarSettingsResponse")
                UseCase.Result.Error(updateCalendarSettingsResponse.exception.message ?: "(no exception message)")
            }
        }
    }
}
