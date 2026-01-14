package me.proton.android.calendar.data.entity

import androidx.annotation.NonNull
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.core.user.data.entity.UserEntity

// User settings specific to Calendar

@Entity(tableName = AppDatabase.TABLE_CALENDAR_USER_SETTINGS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["userId"],
        childColumns = ["fkUserId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
@Serializable
data class CalendarUserSettingsEntity(
    @PrimaryKey
    @kotlinx.serialization.Transient
    @NonNull
    val fkUserId: String = "", // TODO Split in two classes: One RemoteEntity and one DBEntity
    @SerialName("WeekLength")
    val weekLength: Int, // 0 - 7 days, 1 - 5 days
    @SerialName("DisplayWeekNumber")
    val displayWeekNumber: Int, // 0 off, 1 on
    @SerialName("AutoDetectPrimaryTimezone")
    val autoDetectPrimaryTimezone: Int, // 0 off, 1 on
    @SerialName("PrimaryTimezone")
    val primaryTimezone: String, // "Europe/Budapest"
    @SerialName("DisplaySecondaryTimezone")
    val displaySecondaryTimezone: Int, // 0 off, 1 on
    @SerialName("SecondaryTimezone")
    val secondaryTimezone: String?, // Can be null if DisplaySecondaryTimezone is 0
    @SerialName("ViewPreference")
    val viewPreference: Int, /* 0 - DAILY, 1 - WEEKLY, 2 - MONTHLY, 3 - YEARLY, 4 - PLANNING */
    @SerialName("DefaultCalendarID")
    val defaultCalendarId: String?, // TODO we still get null for old accounts (even when web says there is default calendar)
    @SerialName("AutoImportInvite")
    val autoImportInvite: Int, // 0 off, 1 on

) {

//    @PrimaryKey(autoGenerate = true)
//    var _id: Int = 0
}
