package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import me.proton.android.calendar.data.entity.EventAlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventAlarmsDao : BaseDao<EventAlarmEntity> {

    @Query("SELECT * FROM event_alarms WHERE eventId = :eventId")
    abstract fun selectByEventId(eventId: String): Flow<List<EventAlarmEntity>>

    @Query("SELECT * FROM event_alarms WHERE id = :id")
    abstract suspend fun select(id: String): EventAlarmEntity?

    @Query("SELECT * FROM event_alarms WHERE occurrence = (SELECT MIN(occurrence) FROM event_alarms WHERE occurrence >= :timestampSeconds)")
    abstract suspend fun selectUpcomingInclusive(timestampSeconds: Long): List<EventAlarmEntity>

    @Query("SELECT * FROM event_alarms WHERE occurrence >= :timestampSecondsFrom AND occurrence <= :timestampSecondsTo")
    abstract suspend fun selectAllBetweenInclusive(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity>

    @Query("DELETE FROM event_alarms WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM event_alarms WHERE eventId = :eventId")
    abstract suspend fun deleteAllByEventId(eventId: String)

    @Query("DELETE FROM event_alarms WHERE eventId = :eventId AND occurrence = :occurrence")
    abstract suspend fun deleteAllByEventIdAndOccurrence(eventId: String, occurrence: Long)

    @Query("DELETE FROM event_alarms WHERE calendarId = :calendarId")
    abstract suspend fun deleteAllByCalendar(calendarId: String)

}
