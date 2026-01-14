package me.proton.android.calendar.data.entity

import androidx.annotation.NonNull
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.core.user.data.entity.UserEntity

@Entity(
    tableName = AppDatabase.TABLE_CALENDARS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["userId"],
        childColumns = ["fkUserId"],
        onDelete = CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
@Serializable
data class CalendarEntity(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("Type")
    val type: Int = 0, // normal calendar: 0, subscribed calendar: 1, holiday calendar: 2
    @SerialName("Owner")
    val owner: JsonObject?,
    @NonNull
    @kotlinx.serialization.Transient
    val fkUserId: String = "" // TODO Split in two classes: One RemoteEntity and one DBEntity
) {

    val isSubscribed: Boolean get() = type == 1
    val isHolidayCalendar: Boolean get() = type == 2
}

fun CalendarEntity.getCalendarOwnerEntity(json: Json): CalendarOwnerEntity? {
    return json.decodeFromJsonElement<CalendarOwnerEntity>(owner ?: return null)
}

@Serializable
data class CalendarOwnerEntity(
    @SerialName("Email")
    val email: String
)
