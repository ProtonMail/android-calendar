package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.EventEntityMetadata

@Dao
abstract class EventsMetadataDao : BaseDao<EventEntityMetadata> {

    /** GET All **/
    @Query("SELECT * FROM events_metadata")
    abstract suspend fun selectEventsMetadata(): List<EventEntityMetadata>

    /** DELETE **/
    @Query("DELETE FROM events_metadata")
    abstract suspend fun deleteAll()
    @Query("DELETE FROM events_metadata WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendarId(calendarId: String)
    @Query("DELETE FROM events_metadata WHERE id IN (:eventIds)")
    abstract suspend fun deleteByEventIds(eventIds: List<String>)

    @Query("SELECT EXISTS(SELECT * FROM events_metadata WHERE id = :eventId AND calendarId = :calendarId AND modifyTime > :modifyTime)")
    abstract suspend fun hasEventMetadataWithHigherModifyTime(eventId: String, calendarId: String, modifyTime: Long): Boolean

}
