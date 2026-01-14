package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.WorkManager
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.worker.RefreshCalendarSettingsWorker
import me.proton.android.calendar.common.worker.UpdateAlarmsWorker
import me.proton.android.calendar.data.api.CalendarSettingsEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.getDefaultAlarms
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

class CalendarSettingsEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
    private val workManager: WorkManager
): CalendarBaseEventListener<String, CalendarSettingsEntity>(db) {
    override val order: Int = 4
    override val type: Type = Type.Calendar

    // Set of calendarIds for which alarms need to be updated
    private val updateAllDayEventsAlarms: HashSet<String> = hashSetOf()
    private val updatePartDayEventsAlarms: HashSet<String> = hashSetOf()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarSettingsEntity>>? {
        return response.body.deserialize<CalendarSettingsEvents>().calendarSettings?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.calendarSettings)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<CalendarSettingsEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarKey in deleted calendar")
            return
        }
        entities.forEach { newCalendarSettings ->
            val currentPartDayAlarms = calendarsRepository.selectCalendarSettings(newCalendarSettings.calendarId)?.getDefaultAlarms(
                Json.Default, false)
            val newPartDayAlarms = newCalendarSettings.getDefaultAlarms(Json.Default, false)

            val currentFullDayAlarms = calendarsRepository.selectCalendarSettings(newCalendarSettings.calendarId)?.getDefaultAlarms(
                Json.Default, true)
            val newFullDayAlarms = newCalendarSettings.getDefaultAlarms(Json.Default, true)

            // we persist new Calendar Settings because they are needed in calculations in next steps,
            //  but we have the previous Settings cached above
            calendarsRepository.persistCalendarSettings(newCalendarSettings)

            if (currentPartDayAlarms?.isTheSameAs(newPartDayAlarms) == false) {
                updatePartDayEventsAlarms.add(newCalendarSettings.calendarId)
            }

            if (currentFullDayAlarms?.isTheSameAs(newFullDayAlarms) == false) {
                updateAllDayEventsAlarms.add(newCalendarSettings.calendarId)
            }
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)

        val calendarIds = updateAllDayEventsAlarms.plus(updatePartDayEventsAlarms)
        calendarIds.forEach { calendarId ->
            // Launch worker to update alarms of calendar
            UpdateAlarmsWorker.enqueue(workManager,
                userId = config.userId.id,
                calendarId = calendarId,
                updateAllDayEventsAlarms = updateAllDayEventsAlarms,
                updatePartDayEventsAlarms = updatePartDayEventsAlarms,
            )
        }
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        super.onComplete(config)

        updateAllDayEventsAlarms.clear()
        updatePartDayEventsAlarms.clear()
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        logger.i("CalendarSettingsEventListener onResetAll")

        val calendarId = config.asCalendar().calendarId

        // Wipe calendar settings from DB
        calendarsRepository.deleteCalendarSettingsByCalendarId(calendarId)

        // Launch worker to refresh calendar settings
        RefreshCalendarSettingsWorker.enqueue(workManager, userId = config.userId.id, calendarId)
    }
}
