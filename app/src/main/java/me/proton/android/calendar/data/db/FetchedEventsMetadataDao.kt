package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.FetchedEventsMetadataEntity

@Dao
abstract class FetchedEventsMetadataDao : BaseDao<FetchedEventsMetadataEntity> {

    @Query("DELETE FROM fetched_events_metadata WHERE userId = :userId AND calendarId = :calendarId")
    abstract suspend fun deleteAllForCalendar(userId: String, calendarId: String)

    @Query("""
    SELECT EXISTS(
      SELECT 1 FROM fetched_events_metadata
      WHERE userId = :userId
        AND calendarId = :calendarId
        AND windowStartTime <= :windowStart
        AND windowEndTime >= :windowEnd
        AND validUntilMs >= :nowMs
    )
    """)
    abstract suspend fun hasWindowFullyOverlappingAt(
        userId: String,
        calendarId: String,
        windowStart: Long,
        windowEnd: Long,
        nowMs: Long
    ): Boolean

    @Query("""
    DELETE FROM fetched_events_metadata
    WHERE userId = :userId
      AND calendarId = :calendarId
      AND windowStartTime >= :start
      AND windowEndTime <= :end
    """)
    abstract suspend fun deleteWindowsWithin(
        userId: String,
        calendarId: String,
        start: Long,
        end: Long
    ): Int

}
