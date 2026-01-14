package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.GetMinimalCalendarEventsWorker
import me.proton.android.calendar.data.api.CalendarEventsServerEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ResetCalendarSearchUseCase
import me.proton.android.calendar.domain.usecase.SchedulePostProcessEventsMetadataUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarEventListenerNew @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val resetCalendarSearchUseCase: ResetCalendarSearchUseCase,
    private val logger: Logger,
    private val workManager: WorkManager,
    private val schedulePostProcessEventsMetadataUseCase: SchedulePostProcessEventsMetadataUseCase
) : CalendarBaseEventListener<String, EventEntityMetadata>(db) {
    override val order: Int = 3
    override val type: Type = Type.Calendar

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, EventEntityMetadata>>? {
        return response.body.deserialize<CalendarEventsServerEvents>().calendarEvents?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.event)
        }
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<EventEntityMetadata>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE for calendarEvent in deleted calendar")
            return
        }
        logger.i("CalendarEventListener2 onCreate with ${entities.size} new events [${config.asCalendar().calendarId}]")
        calendarsRepository.persistEventsMetadata(*entities.toTypedArray())
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<EventEntityMetadata>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action UPDATE for calendarEvent in deleted calendar")
            return
        }
        logger.i("CalendarEventListener2 onUpdate with ${entities.size} changed events [${config.asCalendar().calendarId}]")
        calendarsRepository.persistEventsMetadata(*entities.toTypedArray())
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)
        schedulePostProcessEventsMetadataUseCase.execute(config.userId.id)
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        if (keys.isEmpty()) return
        calendarsRepository.deleteEventsMetadataByEventIds(keys)
        calendarsRepository.deleteEventsById(config.asCalendar().calendarId, keys)
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        logger.i("CalendarEventListener2 onResetAll [${config.asCalendar().calendarId}]")

        val userId = config.userId
        val calendarId = config.asCalendar().calendarId

        // Wipe the calendar events from DB
        calendarsRepository.deleteEventsMetadataByCalendarId(calendarId)
        calendarsRepository.deleteAllEvents(calendarId)

        // Wipe all the calendar search events from DB
        resetCalendarSearchUseCase.execute(userId, listOf(calendarId))

        // Launch worker to fetch minimal events for calendar
        GetMinimalCalendarEventsWorker.enqueue(workManager, userId = userId.id, calendarId = calendarId)
    }
}
