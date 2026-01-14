package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase

@Entity(
    tableName = AppDatabase.TABLE_EVENTS_OCCURRENCES,
    foreignKeys = [ForeignKey(
        entity = EventEntity::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["eventId"])]
)
@Serializable
data class EventOccurrenceEntity(
    val userId: String,
    val calendarId: String,
    val eventId: String,
    val eventUid: String,
    val fullDay: Int,
    val startTime: Long? = null, // if these are null then event doesn't happen in this window
    val endTime: Long? = null,
    val windowStartTime: Long,
    val windowEndTime: Long,
    val firstOccurrenceStartTime: Long,
    val lastOccurrenceEndTime: Long? = null, // if non-null it means we generated last occurrence in one of the windows
    val rRule: String? = null,
    val modifyTime: Long,

    @PrimaryKey(autoGenerate = true)
    var _id: Int = 0
)

fun List<EventOccurrenceEntity>.distinct(): List<EventOccurrenceEntity> =
    this.distinctBy { (Triple(it.userId, it.calendarId, it.eventId)) }

