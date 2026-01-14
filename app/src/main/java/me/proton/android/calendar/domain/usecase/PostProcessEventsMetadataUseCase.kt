package me.proton.android.calendar.domain.usecase

import android.database.SQLException
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Select EventsMetadata from DB, fetch EventEntity from backend if needed, call UpdateEventOccurrencesUseCase to make them up-to-date to present Events in UI.
 */
class PostProcessEventsMetadataUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val accountManager: AccountManager,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val widgetRefresher: WidgetRefresher,
    private val calendarsRepository: CalendarsRepository
) {

    suspend fun execute(): UseCase.Result {
        val userId =
            accountManager.getPrimaryUserId().firstOrNull() ?: return UseCase.Result.Error("Could not obtain UserId")

        val selectedMetadatas = database.eventsMetadataDao().selectEventsMetadata()
        val eventEntitiesForAlarms = mutableListOf<EventEntity>()

        selectedMetadatas.forEach { metadata ->
            if (calendarsRepository.shouldFetchEvent(userId, metadata)) {
                val fetchedEventEntity = runCatching {
                    fetchEventEntity(userId, metadata)
                }.getOrElse {
                    return UseCase.Result.Error(
                        "PostProcessEventsMetadataUseCase fetching error",
                        userErrorMessage = it.message
                    )
                }
                fetchedEventEntity?.let { entity ->
                    eventEntitiesForAlarms.add(entity)
                    calendarsRepository.persistEvents(entity)
                }
            } else {
                logger.i("Ignoring fetch for event ${metadata.id}")
            }
            try {
                updateEventOccurrencesUseCase.execute(userId.id, metadata)
            } catch (e: SQLException) {
                // should not happen and if it does then the Event doesn't exist in DB so we can't recover from that anyway
                logger.e("PostProcessEventsMetadataUseCase: Error updating event occurrences", e)
            }
        }

        database.eventsMetadataDao().delete(*selectedMetadatas.toTypedArray())

        updateAlarmsUseCase.execute(userId.id, eventEntitiesForAlarms).ifSuccessAndLogErrors(logger) {}
        widgetRefresher.refreshEventList()

        return UseCase.Result.Success<Unit>()
    }

    private suspend fun fetchEventEntity(userId: UserId, response: EventEntityMetadata): EventEntity? {
        return when (val result = calendarsRepository.fetchEventById(userId, response.calendarId, response.id)) {
            is ApiResponse.Success<EventApiResponse> -> result.data.event.toEventEntity()
            is ApiResponse.Error -> {
                // If event was not found just omit it, otherwise we'll retry this indefinitely
                if (result.isNotFound()) {
                    logger.i("Event ${response.id} (cal ID: ${response.calendarId}) not found on API: $result")
                    return null
                }
                else throw IllegalStateException(result.error)
            }

            is ApiResponse.Exception -> throw result.exception
        }
    }
}
