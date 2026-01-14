package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.RefreshCalendarPassphraseWorker
import me.proton.android.calendar.data.api.CalendarPassphrasesEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.CacheCalendarPassphraseUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import javax.inject.Inject

class CalendarPassphraseEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val workManager: WorkManager,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val logger: Logger,
): CalendarBaseEventListener<String, PassphraseEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Calendar

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, PassphraseEntity>>? {
        return response.body.deserialize<CalendarPassphrasesEvents>().calendarPassphrases?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.passphrase)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<PassphraseEntity>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE/UPDATE for calendarPassphrase in deleted calendar")
            return
        }
        entities.forEach {
            calendarsRepository.persistCalendarPassphrase(it)
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        keys.forEach {
            calendarsRepository.deleteCalendarPassphraseById(it)
        }
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        val events = (getActionMap(config)[Action.Create].orEmpty() + getActionMap(config)[Action.Update].orEmpty())
        if (events.isEmpty()) return

        when (val result = cacheCalendarPassphraseUseCase.execute(config.userId, config.asCalendar().calendarId)) {
            is UseCase.Result.InvalidParams -> logger.e("event loop calendar passphrase caching InvalidParams in CalendarPassphraseEventListener: ${result.message}")
            is UseCase.Result.Error -> logger.e("event loop calendar passphrase caching Error in CalendarPassphraseEventListener: ${result.message}")
            else -> {}
        }
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)

        logger.i("CalendarPassphraseEventListener onResetAll [${config.asCalendar().calendarId}]")

        val calendarId = config.asCalendar().calendarId

        // Wipe calendar passphrases from DB
        calendarsRepository.deleteCalendarPassphrases(calendarId)

        // Launch worker to refresh calendar passphrase
        RefreshCalendarPassphraseWorker.enqueue(workManager, userId = config.userId.id, calendarId)
    }
}
