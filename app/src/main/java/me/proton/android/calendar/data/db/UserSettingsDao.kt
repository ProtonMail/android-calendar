package me.proton.android.calendar.data.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity


@Dao
abstract class UserSettingsDao : BaseDao<UserSettingsEntity> {

//    lateinit var userId: String

    @Query("SELECT * FROM user_settings WHERE fkUserId = :userId")
    abstract suspend fun select(userId: String): UserSettingsEntity?

    @Query("DELETE FROM user_settings WHERE fkUserId = :userId")
    abstract suspend fun deleteByUserId(userId: String)

    @Query("SELECT timeFormat FROM user_settings WHERE fkUserId = :userId")
    abstract suspend fun selectTimeFormat(userId: String): Int?

    @Query("SELECT timeFormat FROM user_settings WHERE fkUserId = :userId")
    abstract fun flowTimeFormat(userId: String): Flow<Int?>

    @Query("SELECT weekStart FROM user_settings WHERE fkUserId = :userId")
    abstract suspend fun selectWeekStart(userId: String): Int?

    @Query("SELECT weekStart FROM user_settings WHERE fkUserId = :userId")
    abstract fun flowWeekStart(userId: String): Flow<Int?>
}
