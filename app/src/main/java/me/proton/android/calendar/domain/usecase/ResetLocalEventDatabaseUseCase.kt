package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.mapAsync
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class ResetLocalEventDatabaseUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val database: AppDatabase,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val userSettingsRepository: UserSettingsRepository,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val widgetRefresher: CalendarWidgetRefresher,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase
) {

    suspend operator fun invoke(
        userId: UserId
    ): UseCase.Result {

        // Wipe events DB
        database.eventsDao().deleteAll()

        // Get all calendar ids
        val calendarIds = database.calendarsDao().selectCalendars(userId.id)

        // Get user settings time zone id
        val timeZoneId = calendarsRepository.selectCalendarUserSettings(userId.id)?.primaryTimezone
            ?: ZoneId.systemDefault().id
        val zoneId = if (timeZoneId.isBlank()) ZoneId.systemDefault() else ZoneId.of(timeZoneId)

        // We fetch events for current, previous and next months
        val now = LocalDate.now(zoneId)
        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val timeWindow = ProtonUtilsImpl.getCachedMonthViewsTimeWindow(now, weekStart)
        val fromDate = timeWindow.first
        val toDate = timeWindow.second

        // Fetch and persist events
        calendarIds.mapAsync { calendarEntity ->
            val (fetchEventsResult, eventsAndMetadatas) = fetchEventsUseCase.splitFetchEvents(
                userId,
                listOf(calendarEntity.id),
                fromDate,
                toDate,
                timeZoneId
            )

            fetchEventsResult.ifSuccessAndLogErrors(logger) {
                if (eventsAndMetadatas == null) {
                    logger.e("fetchEventsResult: null event list when Success")
                } else {
                    logger.v("persisting events in bootstrap: ${eventsAndMetadatas.size}")
                    val eventEntities = eventsAndMetadatas.map { it.first }
                    calendarsRepository.persistEvents(*(eventEntities).toTypedArray())
                    eventsAndMetadatas.forEach { (_, eventMetadata) ->
                        updateEventOccurrencesUseCase.execute(userId.id, eventMetadata)
                    }
                    updateAlarmsUseCase.execute(userId.id, eventEntities)
                    widgetRefresher.refreshEventList()
                }
            }
        }

        return UseCase.Result.Success<Unit>()
    }


}
