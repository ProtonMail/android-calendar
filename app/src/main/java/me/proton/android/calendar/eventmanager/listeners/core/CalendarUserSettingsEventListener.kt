package me.proton.android.calendar.eventmanager.listeners.core

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.HandleTimeZoneChangedWorker
import me.proton.android.calendar.common.worker.RefreshCalendarUserSettingsWorker
import me.proton.android.calendar.data.api.CalendarUserSettingsEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.util.kotlin.deserialize
import java.util.TimeZone
import javax.inject.Inject

class CalendarUserSettingsEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val database: AppDatabase,
    private val workManager: WorkManager,
    private val logger: Logger
): CalendarBaseEventListener<String, CalendarUserSettingsEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Core

    private var handlePrimaryTimezoneChange: Boolean = false

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, CalendarUserSettingsEntity>>? {
        return response.body.deserialize<CalendarUserSettingsEvents>().calendarUserSettings?.let {
            // CalendarUserSettings is a special case, it can only be updated
            listOf(Event(Action.Update, "null", it))
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)

        if (handlePrimaryTimezoneChange) {
            // Launch worker to handle time zone change
            HandleTimeZoneChangedWorker.enqueue(workManager, config.userId.id)
            handlePrimaryTimezoneChange = false
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<CalendarUserSettingsEntity>) {
        super.onUpdate(config, entities)

        entities.firstOrNull()?.let {
            val oldTimeZoneId = database.calendarUserSettingsDao().select(config.userId.id)?.primaryTimezone
            val newTimeZoneId = it.primaryTimezone
            if (oldTimeZoneId == null || TimeZone.getTimeZone(oldTimeZoneId).rawOffset != TimeZone.getTimeZone(newTimeZoneId).rawOffset) {
                handlePrimaryTimezoneChange = true
            }
            calendarsRepository.persistCalendarUserSettings(config.userId.id, it)
        }
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        // Log reset with calendarId in order to understand if user was stuck on a specific calendar loop
        logger.i("CalendarUserAddressListener onResetAll")

        // We delete CalendarUserSettings from DB and refresh from BE
        calendarsRepository.deleteCalendarUserSettingsByUserId(config.userId.id)

        // Launch worker to handle calendar user settings refresh
        RefreshCalendarUserSettingsWorker.enqueue(workManager, config.userId.id)
    }
}
