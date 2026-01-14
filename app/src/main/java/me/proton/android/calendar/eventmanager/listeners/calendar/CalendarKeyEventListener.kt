package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.RefreshCalendarKeysWorker
import me.proton.android.calendar.common.worker.RefreshMemberFlagsWorker
import me.proton.android.calendar.data.api.CalendarKeysEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarKeyEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val workManager: WorkManager,
    private val logger: Logger,
): CalendarBaseEventListener<String, CalendarKeyEntity>(db) {
    override val order: Int = 3
    override val type: Type = Type.Calendar

    private var refreshMembersFlags: Boolean = false

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarKeyEntity>>? {
        return response.body.deserialize<CalendarKeysEvents>().calendarKeys?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.key)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarKeyEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarKey in deleted calendar")
            return
        }
        if (entities.isNotEmpty()) refreshMembersFlags = true
        entities.forEach {
            calendarsRepository.persistCalendarKey(it)
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach {
            calendarsRepository.deleteCalendarKeyById(it)
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        val updatedItems = getActionMap(config)[Action.Create].orEmpty() + getActionMap(config)[Action.Update].orEmpty()
        if (updatedItems.isEmpty()) return

        if (refreshMembersFlags) {
            // Launch worker to refresh members flags
            RefreshMemberFlagsWorker.enqueue(workManager, config.userId.id)
            refreshMembersFlags = false
        }
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)

        logger.i("CalendarKeyEventListener onResetAll [${config.asCalendar().calendarId}]")

        val calendarId = config.asCalendar().calendarId

        // Wipe calendar keys from DB
        calendarsRepository.deleteCalendarKeyByCalendarId(calendarId)

        // Launch worker to refresh calendar keys
        RefreshCalendarKeysWorker.enqueue(workManager, userId = config.userId.id, calendarId)
    }
}
