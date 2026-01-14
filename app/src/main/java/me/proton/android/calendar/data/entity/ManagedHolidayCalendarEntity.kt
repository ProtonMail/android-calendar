package me.proton.android.calendar.data.entity

import androidx.annotation.NonNull
import androidx.room.Entity
import androidx.room.ForeignKey
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
    tableName = AppDatabase.TABLE_MANAGED_HOLIDAY_CALENDARS,
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["userId"],
        childColumns = ["fkUserId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["fkUserId"])]
)
@Serializable
data class ManagedHolidayCalendarEntity(
    @PrimaryKey
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("Country")
    val country: String,
    @SerialName("CountryCode")
    val countryCode: String,
    @SerialName("LanguageCode")
    val languageCode: String,
    @SerialName("Language")
    val language: String,
    @SerialName("Timezones")
    val timezones: List<String>,
    @SerialName("Passphrase")
    val passphrase: String,
    @SerialName("SessionKey")
    val sessionKey: JsonObject,
    @SerialName("Hidden")
    val hidden: Boolean?,
    @NonNull
    @kotlinx.serialization.Transient
    val fkUserId: String = ""
)

fun ManagedHolidayCalendarEntity.getSessionKeyEntity(json: Json): SessionKeyEntity {
    return json.decodeFromJsonElement<SessionKeyEntity>(sessionKey)
}

@Serializable
data class SessionKeyEntity(
    @SerialName("Key")
    val key: String,
    @SerialName("Algorithm")
    val algorithm: String
)
