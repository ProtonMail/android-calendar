package me.proton.android.calendar.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.db.AppDatabase

@Entity(
    tableName = AppDatabase.TABLE_FETCHED_EVENTS_METADATA,
    foreignKeys = [ForeignKey(
        entity = CalendarEntity::class,
        parentColumns = ["id"],
        childColumns = ["calendarId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["calendarId"])]
)
@Serializable
data class FetchedEventsMetadataEntity(
    val userId: String,
    val calendarId: String,
    val windowStartTime: Long,
    val windowEndTime: Long,
    val validUntilMs: Long,

    @PrimaryKey(autoGenerate = true)
    var _id: Int = 0
)
