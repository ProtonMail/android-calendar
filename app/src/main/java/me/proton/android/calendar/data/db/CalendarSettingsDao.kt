package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.CalendarSettingsEntity


@Dao
abstract class CalendarSettingsDao : BaseDao<CalendarSettingsEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM calendar_settings WHERE calendarId = :calendarId")
    abstract suspend fun select(calendarId: String): CalendarSettingsEntity?

    @Query("SELECT * FROM calendar_settings")
    abstract suspend fun select(): List<CalendarSettingsEntity>

    @Query("SELECT * FROM calendar_settings")
    abstract fun flowCalendarSettings(): Flow<List<CalendarSettingsEntity>>

    @Query("DELETE FROM calendar_settings WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM calendar_settings WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendarId(calendarId: String)

}
