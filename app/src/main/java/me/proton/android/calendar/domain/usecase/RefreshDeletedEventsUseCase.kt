package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Syncs the events by deleting those not returned by the new batch.
 * For the existing local events in a date range,
 * figure out which are to be deleted by comparing them to the new batch of events and metadatas.
 */
class RefreshDeletedEventsUseCase @Inject constructor(
    private val logger: Logger,
    private val featureFlagManager: FeatureFlagManager,
    private val database: AppDatabase,
    private val searchDatabase: SearchDatabase,
) : UseCase {

    suspend fun execute(
        userId: UserId,
        calendarIdsToFetch: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        eventsAndMetadatas: List<Pair<EventEntity, EventEntityMetadata>>,
    ): UseCase.Result {
        val deletionsRefreshEnabled = featureFlagManager
            .get(userId, CalendarFeatureFlag.RefreshDeletions.featureId)
            ?.value == true

        if (!deletionsRefreshEnabled) return UseCase.Result.Success(Unit)

        val tz = ZoneId.of(timeZoneId)
        val fromSec = fromDate.atStartOfDay(tz).toEpochSecond()
        val toSec   = toDate.plusDays(1).atStartOfDay(tz).toEpochSecond()

        val fetchedCalIdsToMetadataIds = eventsAndMetadatas
            .groupBy { it.second.calendarId }
            .mapValues { e -> e.value.map { it.second.id }.toSet() }

        val localEventCalendarKeys = database.eventOccurrencesDao()
            .selectEventKeysOverlapping(userId.id, calendarIdsToFetch, fromSec, toSec)
        val localEventIdsByCalIds = localEventCalendarKeys.groupBy({ it.calendarId }, { it.eventId })

        localEventIdsByCalIds.forEach { (calendarId, localEventIds) ->
            val fetchedMetadataIds = fetchedCalIdsToMetadataIds[calendarId].orEmpty()
            val eventIdsToDelete = localEventIds.distinct().filter { it !in fetchedMetadataIds }
            if (eventIdsToDelete.isEmpty()) return@forEach

            database.inTransaction {
                eventIdsToDelete.forEach { eventId ->
                    database.eventOccurrencesDao().deleteAllForEvent(userId.id, calendarId, eventId)
                    database.eventAlarmsDao().deleteAllByEventId(eventId)
                }
                database.eventsDao().deleteByIds(eventIdsToDelete)
                deleteSearchEventsForEvents(userId.id, calendarId, eventIdsToDelete)
                deleteEventsMetadataByEventIds(eventIdsToDelete)
            }
            logger.v("Manual refresh: deleted ${eventIdsToDelete.size} stale events in $calendarId.")
        }
        return UseCase.Result.Success(Unit)
    }

    private suspend fun deleteEventsMetadataByEventIds(eventIds: List<String>) {
        database.eventsMetadataDao().deleteByEventIds(eventIds)
    }

    private suspend fun deleteSearchEventsForEvents(userId: String, calendarId: String, eventIds: List<String>) {
        searchDatabase.searchDao().deleteSearchEventsForEvents(userId, calendarId, eventIds)
    }
}
