package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.SearchEventEntity

@Dao
abstract class SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSearchEvents(searchEvents: List<SearchEventEntity>)

    @Query("SELECT * FROM search_events")
    abstract suspend fun selectAll(): List<SearchEventEntity>

    @Query("SELECT * FROM search_events WHERE user_id = :userId")
    abstract suspend fun selectSearchEvents(userId: String): List<SearchEventEntity>

    @Query("SELECT * FROM search_events WHERE user_id = :userId")
    abstract fun flowSearchEvents(userId: String): Flow<List<SearchEventEntity>>

    @Query("DELETE FROM search_events")
    abstract suspend fun deleteAllSearchEvents(): Int

    @Query("DELETE FROM search_events WHERE user_id = :userId")
    abstract suspend fun deleteAll(userId: String): Int

    @Query("DELETE FROM search_events WHERE user_id = :userId AND calendar_id = :calendarId")
    abstract suspend fun deleteAllInCalendar(userId: String, calendarId: String): Int

    @Query("DELETE FROM search_events WHERE user_id = :userId AND calendar_id = :calendarId AND event_id IN (:eventIds)")
    abstract suspend fun deleteSearchEventsForEvents(userId: String, calendarId: String, eventIds: List<String>): Int

    @Query("SELECT EXISTS(SELECT * FROM search_events WHERE user_id = :userId AND event_id = :eventId AND calendar_id = :calendarId AND modify_time > :modifyTime)")
    abstract suspend fun hasEventWithHigherModifyTime(userId: String, calendarId: String, eventId: String, modifyTime: Long): Boolean

}
