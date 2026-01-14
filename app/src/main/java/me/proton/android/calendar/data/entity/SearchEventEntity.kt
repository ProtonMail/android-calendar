package me.proton.android.calendar.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.domain.model.Event

@Entity(
    tableName = SearchDatabase.TABLE_SEARCH_EVENTS,
    primaryKeys = ["user_id","calendar_id","event_id"]
)
data class SearchEventEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "calendar_id")
    val calendarId: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "modify_time")
    val modifyTime: Long,
    @ColumnInfo(name = "summary")
    val summary: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "location")
    val location: String,
    @ColumnInfo(name = "organizer")
    val organizer: String,
    @ColumnInfo(name = "attendees")
    val attendees: String
) {

    companion object {

        fun from(userId: String, event: Event): SearchEventEntity =
            SearchEventEntity(
                userId,
                event.calendar.id,
                event.id,
                event.modifyTime,
                event.summary ?: "",
                event.description ?: "",
                event.location ?: "",
                "${event.iCalEvent.organizer?.extractEmail() ?: ""} ${event.iCalEvent.organizer?.commonName ?: ""}",
                "${event.iCalEvent.attendees?.joinToString(separator = "\n") { "${it.extractEmail() ?: ""} ${it.commonName ?: ""}" } }"
            )
        }

}
