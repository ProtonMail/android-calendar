package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.getCachedMonthViewsTimeWindow
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetMinimalCalendarEventsUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val logger: Logger,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val loadingStateUseCase: LoadingStateUseCase,
) {

    companion object {
        const val GET_MINIMAL_CALENDAR_EVENTS = "GET_MINIMAL_CALENDAR_EVENTS"
    }

    suspend fun execute(userId: UserId, calendarId: String): UseCase.Result {
        val timezone = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id

        loadingStateUseCase.markMinimalCalendarFetching(calendarId, inProgress = true)

        val zoneId = if (timezone.isBlank()) ZoneId.systemDefault() else ZoneId.of(timezone)
        val now = LocalDate.now(zoneId)
        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val timeWindow = getCachedMonthViewsTimeWindow(now, weekStart)
        val fromDate = timeWindow.first
        val toDate = timeWindow.second

        val (result, entitiesAndMetadatas) = fetchEventsUseCase.splitFetchEvents(userId, listOf(calendarId), fromDate, toDate, zoneId.id)
        result.logErrors(logger)

        if (result is UseCase.Result.Success<*>) {
            if (entitiesAndMetadatas == null) {
                logger.e("GetMinimalCalendarEventsUseCase: null event list when Success")
                return UseCase.Result.Error("GetMinimalCalendarEventsUseCase: null event list when Success")
            }

            logger.v("GetMinimalCalendarEventsUseCase fetchEventsResult success: ${entitiesAndMetadatas.size}")
            val eventEntities = entitiesAndMetadatas.map { it.first }
            calendarsRepository.persistEvents(*(eventEntities).toTypedArray())
            entitiesAndMetadatas.map { it.second }.forEach {
                updateEventOccurrencesUseCase.execute(userId.id, it)
            }
            updateAlarmsUseCase.execute(userId.id, eventEntities)
            loadingStateUseCase.markMinimalCalendarFetching(calendarId, inProgress = false)
            return UseCase.Result.Success<Unit>()
        } else {
            loadingStateUseCase.markMinimalCalendarFetching(calendarId, inProgress = false)
            logger.e("GetMinimalCalendarEventsUseCase: failed to splitFetchEvents: $result")
            return UseCase.Result.Error("GetMinimalCalendarEventsUseCase: failed to splitFetchEvents")
        }
    }
}
