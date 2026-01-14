package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.FetchedEventsMetadataEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UpdateFetchedEventsMetadataUseCase @Inject constructor(
    private val database: AppDatabase,
    private val getFetchedEventWindowsValidity: GetFetchedEventWindowsValidity,
) {

    suspend fun execute(
        userId: String,
        calendarIds: List<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ) {
        val windowStart = fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond()
        val windowEnd = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toEpochSecond()

        val validityDuration = getFetchedEventWindowsValidity.getValidityDuration(userId)

        database.inTransaction {
            calendarIds.forEach { calId ->
                val shouldAddNewEntry = database.calendarsDao().hasCalendar(calId)
                if (shouldAddNewEntry) {
                    database.fetchedEventsMetadataDao().deleteWindowsWithin(
                        userId = userId,
                        calendarId = calId,
                        start = windowStart,
                        end = windowEnd,
                    )
                    database.fetchedEventsMetadataDao().insert(
                        FetchedEventsMetadataEntity(
                            userId = userId,
                            calId,
                            windowStartTime = windowStart,
                            windowEndTime = windowEnd,
                            validUntilMs = (Instant.now() + validityDuration).toEpochMilli(),
                        )
                    )
                }
            }
        }
    }

    suspend fun isWindowFullyFetched(
        userId: String,
        calendarId: String,
        windowStart: Instant,
        windowEnd: Instant,
    ): Boolean = hasValidFetchedWindowWithin(
        userId = userId,
        calendarId = calendarId,
        windowStart = windowStart,
        windowEnd = windowEnd,
    )

    suspend fun shouldFetch(
        userId: String,
        calendarId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Boolean {
        return hasValidFetchedWindowWithin(
            userId = userId,
            calendarId = calendarId,
            windowStart = fromDate.atStartOfDay(ZoneId.of(timeZoneId)).toInstant(),
            windowEnd = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toInstant(),
        ).not()
    }

    private suspend fun hasValidFetchedWindowWithin(
        userId: String,
        calendarId: String,
        windowStart: Instant,
        windowEnd: Instant,
    ): Boolean {
        return database.inTransaction {
            database.fetchedEventsMetadataDao().hasWindowFullyOverlappingAt(
                userId = userId,
                calendarId = calendarId,
                windowStart = windowStart.epochSecond,
                windowEnd = windowEnd.epochSecond,
                nowMs = Instant.now().toEpochMilli(),
            )
        }
    }
}
