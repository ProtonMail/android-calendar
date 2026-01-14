package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.LocalDate
import javax.inject.Inject

class FetchCachedViewsEventsUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val widgetRefresher: WidgetRefresher,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_FETCH_CACHED_VIEWS_EVENTS"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String,
        selectedDate: LocalDate,
        displayTimeZoneId: String
    ): UseCase.Result {
        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val timeWindow = ProtonUtilsImpl.getCachedMonthViewsTimeWindow(selectedDate, weekStart)
        val fromDate = timeWindow.first
        val toDate = timeWindow.second
        val (fetchEventsResult, events) = fetchEventsUseCase.splitFetchEvents(
            userId,
            listOf(calendarId),
            fromDate,
            toDate,
            displayTimeZoneId
        )

        return if (fetchEventsResult is UseCase.Result.Success<*> && events != null) {
            val eventEntities = events.map { it.first }
            calendarsRepository.persistEvents(*(eventEntities).toTypedArray())
            events.forEach { (_, eventMetadata) ->
                updateEventOccurrencesUseCase.execute(userId.id, eventMetadata)
            }
            updateAlarmsUseCase.execute(userId.id, eventEntities)
            widgetRefresher.refreshEventList()
            UseCase.Result.Success<Unit>()
        } else {
            logger.e("FetchCachedViewsEventsUseCase fetching events failed")
            UseCase.Result.Error("FetchCachedViewsEventsUseCase fetching events failed")
        }
    }
}
