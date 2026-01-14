package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.HandleAlarmsWithMissingEventWorker
import me.proton.android.calendar.common.worker.HandleAlarmsWorker
import me.proton.android.calendar.data.api.CalendarAlarmsEvents
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.SafePersistEventAlarmUseCase
import me.proton.android.calendar.domain.usecase.ScheduleSyncAlarmsUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarAlarmEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase,
    private val workManager: WorkManager,
    private val logger: Logger,
    private val scheduleSyncAlarmsUseCase: ScheduleSyncAlarmsUseCase
): CalendarBaseEventListener<String, EventAlarmEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    private val alarmsWithMissingEvent = mutableSetOf<String>()
    private val missingEvents = mutableSetOf<String>()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, EventAlarmEntity>>? {
        return response.body.deserialize<CalendarAlarmsEvents>().calendarAlarms?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, mapToAlarmEntity(it))
        }
    }

    private fun mapToAlarmEntity(response: ServerEvent.AlarmsApiResponse) = response.alarm?.let {
        EventAlarmEntity(it.id, it.occurrence, it.trigger, it.action, it.eventId, it.memberId, it.calendarId)
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<EventAlarmEntity>) {
        val alarmsWithEvents = arrayListOf<EventAlarmEntity>()
        entities.forEach {
            if (calendarsRepository.hasEvent(it.eventId, it.calendarId)) {
                alarmsWithEvents.add(it)
            } else {
                // We keep ids of events to fetch and ids of alarms with missing event
                missingEvents.add(it.eventId)
                alarmsWithMissingEvent.add(it.id)
            }
        }

        // Only persist alarms with event for now. We will handle the rest from onSuccess in a worker.
        safePersistEventAlarmUseCase.invoke(alarmsWithEvents)
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach {
            calendarsRepository.deleteEventAlarmById(it)
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val actions = getActionMap(config)
        val alarmEvents = actions[Action.Create].orEmpty() + actions[Action.Update].orEmpty() + actions[Action.Delete].orEmpty()
        if (alarmEvents.isEmpty()) return

        missingEvents.forEach { eventId ->
            // Launch worker to handle alarms with missing events
            HandleAlarmsWithMissingEventWorker.enqueue(workManager, userId = config.userId.id,
                calendarId = config.asCalendar().calendarId, eventId = eventId)
        }

        // Launch worker to handle alarms
        HandleAlarmsWorker.enqueue(workManager, userId = config.userId.id, alarmEpochSeconds = null)
    }

    override suspend fun onFailure(config: EventManagerConfig) {
        // Launch worker to force refresh and sync alarms
        scheduleSyncAlarmsUseCase.execute(config.userId, force = true)
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        alarmsWithMissingEvent.clear()
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)

        logger.i("CalendarAlarmEventListener onResetAll [${config.asCalendar().calendarId}]")

        val calendarId = config.asCalendar().calendarId

        // We wipe the alarms from the DB
        calendarsRepository.deleteAllEventAlarmsByCalendar(calendarId)

        // Launch worker to force refresh and sync alarms
        scheduleSyncAlarmsUseCase.execute(config.userId, force = true)
    }
}
