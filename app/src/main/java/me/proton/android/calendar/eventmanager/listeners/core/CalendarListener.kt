package me.proton.android.calendar.eventmanager.listeners.core

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.BootstrapAllCalendarsWorker
import me.proton.android.calendar.common.worker.BootstrapCalendarsWorker
import me.proton.android.calendar.data.api.CalendarsEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarListener @Inject constructor(
    database: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val workManager: WorkManager,
    private val logger: Logger
    ): CalendarBaseEventListener<String, CalendarEntity>(database) {
    override val order: Int = 1
    override val type: Type = Type.Core

    private val calendarsToBootstrap: HashSet<String> = hashSetOf()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarEntity>>? {
        return response.body.deserialize<CalendarsEvents>().calendars?.map {
            Event(requireNotNull(Action.Companion.map[it.action]), it.id, it.calendar)
        }
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onCreate(config, entities)

        entities.map {
            // We persist the calendar entity, bootstrap will be done in worker on success
            calendarsRepository.persistCalendar(config.userId.id, it)
            // Add id to the list of calendar to bootstrap in worker
            calendarsToBootstrap.add(it.id)
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarEntity>) {
        super.onUpdate(config, entities)

        entities.map {
            if (calendarsRepository.selectCalendar(it.id) == null) {
                // We persist the calendar entity, bootstrap will be done in worker on success
                calendarsRepository.persistCalendar(config.userId.id, it)
                // Add id to the list of calendar to bootstrap in worker
                calendarsToBootstrap.add(it.id)
            } else {
                calendarsRepository.persistCalendar(config.userId.id, it)
            }
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        keys.map {
            val calendar = calendarsRepository.selectCalendar(it)
            // For holiday and shared calendars, we rely on member delete event
            if (calendar?.type != Calendar.CalendarType.HOLIDAY.value && calendar?.isSharedWithMe == false) {
                calendarsRepository.deleteCalendarById(it)
            }
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)
        if (calendarsToBootstrap.isNotEmpty()) {
            // Launch worker to bootstrap calendars
            BootstrapCalendarsWorker.enqueue(workManager, config.userId.id, calendarsToBootstrap)
        }
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        super.onComplete(config)

        calendarsToBootstrap.clear()
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        logger.i("CalendarListener onResetAll")

        // Wipe all calendars from DB
        // Foreign keys on Calendar ID will also delete:
        //  - Calendar Settings
        //  - Passphrase
        //  - CalendarKeys
        //  - Members
        // CalendarUserSettings and UserSettings will not be deleted
        calendarsRepository.deleteCalendars(config.userId.id)

        // Launch worker to bootstrap all calendars
        BootstrapAllCalendarsWorker.enqueue(workManager, config.userId.id)
    }
}
