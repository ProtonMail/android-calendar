package me.proton.android.calendar.eventmanager.listeners.calendar

import androidx.work.WorkManager
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.common.worker.GetMinimalCalendarEventsWorker
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarEventsServerEvents
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ResetCalendarSearchUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UpdateEventOccurrencesUseCase
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.eventmanager.domain.extension.asCalendar
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.mapNotNullAsync
import javax.inject.Inject

class CalendarEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val resetCalendarSearchUseCase: ResetCalendarSearchUseCase,
    private val logger: Logger,
    private val workManager: WorkManager,
    private val widgetRefresher: WidgetRefresher,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase
): CalendarBaseEventListener<String, EventEntityMetadata>(db) {
    override val order: Int = 3
    override val type: Type = Type.Calendar

    private var eventEntities = hashMapOf<String, EventEntity>()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, EventEntityMetadata>>? {
        return response.body.deserialize<CalendarEventsServerEvents>().calendarEvents?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.event)
        }
    }

    override suspend fun onPrepare(config: EventManagerConfig, entities: List<EventEntityMetadata>) {
        // Fetch any event not cached as the provided metadata is not enough to create entities
        eventEntities.putAll(
            entities.filter {
                // We check if event modifyTime to see if it is up to date
                // We always assume we should fetch recurring events for simplicity
                // If not recurring, we fetch if event happens soon or if it is withing requested FetchWindows
                calendarsRepository.shouldFetchEvent(config.userId, it)
            }.mapNotNullAsync { metadata ->
                fetchEventEntity(config.userId, metadata)
            }.associateBy { event ->
                event.id
            }
        )
    }

    private suspend fun fetchEventEntity(userId: UserId, response: EventEntityMetadata): EventEntity? {
        return when (val result = calendarsRepository.fetchEventById(userId, response.calendarId, response.id)) {
            is ApiResponse.Success<EventApiResponse> -> result.data.event.toEventEntity()
            is ApiResponse.Error -> {
                // If event was not found just omit it, otherwise we'll retry this indefinitely
                if (result.isNotFound()) return null
                else throw IllegalStateException(result.error)
            }
            is ApiResponse.Exception -> throw result.exception
        }
    }

    override suspend fun onCreate(config: EventManagerConfig, entities: List<EventEntityMetadata>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action CREATE for calendarEvent in deleted calendar")
            return
        }

        val entityIds = entities.map { it.id }
        if (entityIds.isEmpty()) return
        val entitiesToCreate = entityIds.mapNotNull { eventEntities[it] }
        calendarsRepository.persistEvents(*entitiesToCreate.toTypedArray())

        val affectedEventEntityMetadatas = entities.filter {
            eventEntityMetadata -> entitiesToCreate.firstOrNull { it.id == eventEntityMetadata.id } != null
        }

        affectedEventEntityMetadatas.forEach {
            updateEventOccurrencesUseCase.execute(config.userId.id, it)
        }
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<EventEntityMetadata>) {
        if (!calendarsRepository.hasCalendar(config.asCalendar().calendarId)) {
            logger.i("action UPDATE for calendarEvent in deleted calendar")
            return
        }
        
        val entityIds = entities.map { it.id }
        if (entityIds.isEmpty()) return
        val entitiesToUpdate = entityIds.mapNotNull { eventEntities[it] }
        calendarsRepository.persistEvents(*entitiesToUpdate.toTypedArray())

        val affectedEventEntityMetadatas = entities.filter {
            eventEntityMetadata -> entitiesToUpdate.firstOrNull { it.id == eventEntityMetadata.id } != null
        }

        affectedEventEntityMetadatas.forEach {
            updateEventOccurrencesUseCase.execute(config.userId.id, it)
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        if (keys.isEmpty()) return
        calendarsRepository.deleteEventsMetadataByEventIds(keys)
        calendarsRepository.deleteEventsById(config.asCalendar().calendarId, keys)
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)

        logger.i("CalendarEventListener onResetAll [${config.asCalendar().calendarId}]")

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

    override suspend fun onSuccess(config: EventManagerConfig) {
        // Get all the created or updated events ids
        val entityIds = getActionMap(config)[Action.Create]?.mapNotNull {
            it.entity?.id
        }.orEmpty() + getActionMap(config)[Action.Update]?.mapNotNull {
            it.entity?.id
        }.orEmpty()

        val entitiesToPostProcess = entityIds.mapNotNull { eventEntities[it] }
        if (entitiesToPostProcess.isEmpty()) return

        // Post process received events
        updateAlarmsUseCase.execute(config.userId.id, entitiesToPostProcess)
        widgetRefresher.refreshEventList()

        // Clean cached entities
        eventEntities.clear()
    }
}
