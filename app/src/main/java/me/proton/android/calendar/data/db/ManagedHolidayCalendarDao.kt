package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity

@Dao
abstract class ManagedHolidayCalendarDao : BaseDao<ManagedHolidayCalendarEntity> {

    @Query("SELECT * FROM managed_holiday_calendars")
    abstract suspend fun selectAll(): List<ManagedHolidayCalendarEntity>?

    @Query("SELECT * FROM managed_holiday_calendars WHERE calendarId = :calendarId")
    abstract suspend fun selectById(calendarId: String): ManagedHolidayCalendarEntity?

    @Query("DELETE FROM managed_holiday_calendars WHERE calendarId = :calendarId")
    abstract suspend fun deleteById(calendarId: String)

    @Transaction
    @Query("SELECT EXISTS(SELECT * FROM managed_holiday_calendars)")
    abstract suspend fun hasCalendar(): Boolean

    @Query("DELETE FROM managed_holiday_calendars")
    abstract suspend fun deleteAll()
}
