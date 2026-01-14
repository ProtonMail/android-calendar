package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase

@Entity(
    tableName = AppDatabase.TABLE_EVENT_ALARMS,
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["eventId", "occurrence"])]
)
@Serializable
data class EventAlarmEntity(
    @PrimaryKey
    @SerialName("ID")
    val id: String,
    @SerialName("Occurrence")
    val occurrence: Long, // next alarm occurrence timestamp
    @SerialName("Trigger")
    val trigger: String, //"-PT15H"
    @SerialName("Action")
    val action: Int, // 1 -- email, 2 -- push
    @SerialName("EventID")
    val eventId: String,
    @SerialName("MemberID")
    val memberId: String,
    @SerialName("CalendarID")
    val calendarId: String
)


