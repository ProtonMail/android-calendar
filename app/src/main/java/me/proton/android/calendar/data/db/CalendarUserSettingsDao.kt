package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity


@Dao
abstract class CalendarUserSettingsDao : BaseDao<CalendarUserSettingsEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun select(userId: String): CalendarUserSettingsEntity?

    @Query("DELETE FROM calendar_user_settings")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun deleteByUserId(userId: String)

    @Query("SELECT primaryTimezone FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun selectCalendarUserSettingsPrimaryTimezone(userId: String): String?

    @Query("SELECT primaryTimezone FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract fun flowCalendarUserSettingsPrimaryTimezone(userId: String): Flow<String>

    @Query("SELECT autoDetectPrimaryTimezone FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun selectAutoDetectPrimaryTimezone(userId: String): Int?

    @Query("SELECT autoDetectPrimaryTimezone FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract fun flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Flow<Int>

    @Query("UPDATE calendar_user_settings SET autoDetectPrimaryTimezone = :autoDetectPrimaryTimezone WHERE fkUserId = :userId")
    abstract suspend fun updateAutoDetectPrimaryTimezone(userId: String, autoDetectPrimaryTimezone: Int)

    @Query("SELECT displayWeekNumber FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun selectCalendarUserSettingsDisplayWeekNumber(userId: String): Int?

    @Query("SELECT displayWeekNumber FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int>

    @Query("SELECT autoImportInvite FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract fun flowCalendarUserSettingsAutoImportInvite(userId: String): Flow<Int>

    @Query("UPDATE calendar_user_settings SET displayWeekNumber = :displayWeekNumber WHERE fkUserId = :userId")
    abstract suspend fun updateDisplayWeekNumber(userId: String, displayWeekNumber: Int)

    @Query("SELECT defaultCalendarId FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract suspend fun selectCalendarUserDefaultCalendarId(userId: String): String?

    @Query("SELECT defaultCalendarId FROM calendar_user_settings WHERE fkUserId = :userId")
    abstract fun flowCalendarUserDefaultCalendarId(userId: String): Flow<String>

    @Query("UPDATE calendar_user_settings SET defaultCalendarId = :defaultCalendarId WHERE fkUserId = :userId")
    abstract suspend fun updateDefaultCalendarId(userId: String, defaultCalendarId: String)

    @Query("UPDATE calendar_user_settings SET autoImportInvite = :autoImportInvite WHERE fkUserId = :userId")
    abstract suspend fun updateAutoImportInvite(userId: String, autoImportInvite: Int)

}
