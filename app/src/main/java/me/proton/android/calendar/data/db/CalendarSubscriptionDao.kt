package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity

@Dao
abstract class CalendarSubscriptionDao : BaseDao<CalendarSubscriptionEntity> {

    @Query("SELECT * FROM calendar_subscriptions WHERE calendarId = :calendarId")
    abstract suspend fun select(calendarId: String): CalendarSubscriptionEntity?

    @Query("SELECT * FROM calendar_subscriptions")
    abstract suspend fun selectCalendarSubscriptions(): List<CalendarSubscriptionEntity>

    @Query("SELECT * FROM calendar_subscriptions")
    abstract fun flowCalendarSubscriptions(): Flow<List<CalendarSubscriptionEntity>>

    @Query("DELETE FROM calendar_subscriptions WHERE calendarId = :calendarId")
    abstract suspend fun deleteByCalendarId(calendarId: String)

}
