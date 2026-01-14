package me.proton.android.calendar.data.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sanitise
import me.proton.android.calendar.common.utils.ICalUtilsImpl.toICalendarFromPlaintextSharedPart
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent

@Serializable
data class SkeletonEventEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("SharedEvents")
    val sharedEvents: List<JsonElement>,
    @SerialName("ModifyTime")
    val modifyTime: Long,
    @SerialName("AddressID")
    val addressId: String?,
    @SerialName("Color")
    val color: String? = null
)

/**
 * Skeleton Event contains only data created using plaintext Shared Part,
 * valid Calendar ID and Calendar Color, and modifyTime, but the rest is dummy data.
 */
fun SkeletonEventEntity.toSkeletonEvent(json: Json, calendarColor: String? = null, calendarType: Int? = 0): SkeletonEvent? =
    toICalendarFromPlaintextSharedPart(json, this.sharedEvents)?.let {
        if (it.events.firstOrNull()?.sanitise() == true) {
            Event.from(
                this.id,
                Calendar(this.calendarId, "", "", "", "", calendarColor ?: "", 0, "", "", 0, false, calendarType ?: 0, 0, 0, emptyList(), emptyList()),
                it,
                modifyTime,
                null,
                null,
                color = this.color
            )
        } else null
    }
