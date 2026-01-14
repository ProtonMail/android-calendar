package me.proton.android.calendar.domain.model

import biweekly.parameter.ParticipationStatus
import biweekly.property.Status
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Lightweight representation of an Event, not containing ICS payload.
 */
data class UiEvent(
    override val id: String,
    val calendarId: String,

    val uid: String,
    val summary: String?,
    val location: String?,
    val description: String?,
    val dateStart: ZonedDateTime, // this is actual date start of single event or occurrence of recurring event
    val dateEnd: ZonedDateTime,
    val isAllDay: Boolean,
    val occurrenceNumber: Int, // 0 means it's non-recurring

    val displayColor: String,

    val decryptionStatus: Event.DecryptionStatus,

    val participationStatus: ParticipationStatus?,
    val status: Status

) : BaseModel() {

    val isRecurring get() = occurrenceNumber > 0

    constructor() : this(
        "",
        "",
        "",
        "",
        "",
        "",
        ZonedDateTime.now(),
        ZonedDateTime.now(),
        false,
        0,
        "#CCCCCC",
        Event.DecryptionStatus.Failure.Generic,
        null,
        Status.confirmed()
    )

    /**
     * Attention: part-time Event ending at 00:00 is not considered to span the end-day.
     */
    fun spansSingleDay(actualEndDate: Boolean = false): Boolean {

        val dateTimeStart = this.dateStart
        val dateStart = dateTimeStart.toLocalDate()
        val dateTimeEnd = this.dateEnd
        val dateEnd = dateTimeEnd.toLocalDate()

        return if (this.isAllDay) {
            dateEnd == null || dateStart == dateEnd.minusDays(if (actualEndDate) 0 else 1)
        } else {

            if (dateTimeStart == dateTimeEnd) {
                true
            } else if (dateTimeEnd.toLocalTime() == LocalTime.MIDNIGHT) {
                // for part-day Event, if it ends on Midnight, we don't count it spanning that last day
                dateStart == dateEnd.minusDays(1)
            } else {
                dateStart == dateEnd
            }
        }
    }

    fun isInThePast(): Boolean = this.dateEnd.isBefore(ZonedDateTime.now())

    fun isCancelled(): Boolean = this.status.isCancelled

}


