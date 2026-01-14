package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import biweekly.component.VAlarm
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.model.Notification

// settings specific to Calendar, shared by all Calendar Members

@Entity(tableName = AppDatabase.TABLE_CALENDAR_SETTINGS,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])])
@Serializable
data class CalendarSettingsEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("DefaultEventDuration")
    val defaultEventDuration: Int, // Default event duration in minutes
    @SerialName("DefaultPartDayNotifications")
    val defaultPartDayNotifications: List<JsonElement>,
    @SerialName("DefaultFullDayNotifications")
    val defaultFullDayNotifications: List<JsonElement>

)

fun CalendarSettingsEntity.getDefaultAlarms(json: Json, isAllDay: Boolean): List<VAlarm> {

    val defaultNotifications =
        if (isAllDay) this.defaultFullDayNotifications else this.defaultPartDayNotifications

    return defaultNotifications.mapNotNull {
        if ((it as? JsonObject) != null) json.decodeFromJsonElement<NotificationEntity>(
            it
        ) else null
    }.mapNotNull { it.toVAlarm() }
}

fun CalendarSettingsEntity.getDefaultNotifications(json: Json, isAllDay: Boolean): List<Notification> {

    val defaultNotifications =
        if (isAllDay) this.defaultFullDayNotifications else this.defaultPartDayNotifications

    return defaultNotifications.mapNotNull {
        if ((it as? JsonObject) != null) json.decodeFromJsonElement<NotificationEntity>(
            it
        ) else null
    }.mapNotNull { it.toNotification() }
}
