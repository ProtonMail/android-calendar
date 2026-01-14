package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import me.proton.android.calendar.data.entity.CalendarEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarsDao : BaseDao<CalendarEntity> {

    /** All calendars */
    @Query("SELECT COUNT(id) FROM calendars")
    abstract suspend fun countCalendars(): Int

    @Query("SELECT * FROM calendars")
    abstract fun selectCalendars(): List<CalendarEntity>

    @Query("SELECT * FROM calendars")
    abstract fun flowCalendars(): Flow<List<CalendarEntity>>

    /** All calendars for userId */

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract suspend fun selectCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE fkUserId = :userId")
    abstract fun flowCalendars(userId: String): Flow<List<CalendarEntity>>

    /** User calendars */

    @Query("SELECT * FROM calendars WHERE type == 0 AND fkUserId = :userId")
    abstract suspend fun selectUserCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE type == 0 AND fkUserId = :userId")
    abstract fun flowUserCalendars(userId: String): Flow<List<CalendarEntity>>

    /** Subscribed calendars */

    @Query("SELECT * FROM calendars WHERE type == 1 AND fkUserId = :userId")
    abstract suspend fun selectSubscribedCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE type == 1 AND fkUserId = :userId")
    abstract fun flowSubscribedCalendars(userId: String): Flow<List<CalendarEntity>>

    /** Holiday calendars */

    @Query("SELECT * FROM calendars WHERE type == 2 AND fkUserId = :userId")
    abstract suspend fun selectHolidayCalendars(userId: String): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE type == 2 AND fkUserId = :userId")
    abstract fun flowHolidayCalendars(userId: String): Flow<List<CalendarEntity>>

    /** By id */

    @Query("SELECT fkUserId FROM calendars WHERE id = :calendarId")
    abstract suspend fun selectCalendarUserId(calendarId: String): String?

    @Query("SELECT * FROM calendars WHERE id = :id")
    abstract suspend fun selectById(id: String): CalendarEntity?

    @Transaction
    @Query("SELECT EXISTS(SELECT * FROM calendars WHERE id = :calendarId)")
    abstract suspend fun hasCalendar(calendarId: String): Boolean

    @Query("DELETE FROM calendars")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM calendars WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM calendars WHERE fkUserId = :userId")
    abstract suspend fun deleteCalendars(userId: String)

}
