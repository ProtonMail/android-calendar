package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.util.kotlin.mapNotNullAsync
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class HandleAlarmsWithMissingEventUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase
) {

    companion object {
        const val HANDLE_ALARMS_WITH_MISSING_EVENT = "HANDLE_ALARMS_WITH_MISSING_EVENT"
    }

    suspend operator fun invoke(
        userId: UserId,
        calendarId: String,
        eventId: String
    ): UseCase.Result {

        val event = when (val result = calendarsRepository.fetchEventById(userId, calendarId, eventId)) {
            is ApiResponse.Success<EventApiResponse> -> {
                result.data.event
            }
            is ApiResponse.Error -> {
                if (result.isNotFound()) {
                    // Use InvalidParams here so that worker will not retry
                    return UseCase.Result.InvalidParams("CalendarAlarmEventListener Event not found for Alarm: ${result.error}")
                } else return UseCase.Result.Error("CalendarAlarmEventListener could not fetch missing Event for Alarm: ${result.error}")
            }
            is ApiResponse.Exception -> {
                return UseCase.Result.Error("CalendarAlarmEventListener could not fetch missing Event for Alarm: ${result.exception}")
            }
        }

        // Persist the newly fetched events
        calendarsRepository.persistEvents(event.toEventEntity())
        updateEventOccurrencesUseCase.execute(userId.id, event.toEventEntityMetadata())

        // Fetch alarms for event
        val alarmEntities = calendarsRepository.fetchEventAlarms(userId, calendarId, eventId).valueOrNullAndLogErrors(logger)?.alarms
            ?: return UseCase.Result.Error("CalendarAlarmEventListener failed to fetch alarms for event")

        // Persist alarms
        safePersistEventAlarmUseCase.invoke(alarmEntities)

        // schedule the just-synced alarms to fire
        handleAlarmsUseCase.execute(userId)

        return UseCase.Result.Success<Unit>()
    }


}
