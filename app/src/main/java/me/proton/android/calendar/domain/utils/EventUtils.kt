package me.proton.android.calendar.domain.utils

import android.content.res.Resources
import biweekly.parameter.ParticipationStatus
import biweekly.property.DateOrDateTimeProperty
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.core.user.domain.entity.UserAddress
import java.time.LocalDate
import java.time.ZonedDateTime

interface EventUtils {

    // TODO: Once we have proper user management, check if we could get participation status for current user on event init
    fun Event.getParticipationStatus(userEmails: List<String>): ParticipationStatus?
    fun Event.isUserAddressAllowedSend(userAddresses: List<UserAddress>, isFreeUser: Boolean): Boolean
    fun Event.updateParticipationStatus(userEmails: List<String>, status: ParticipationStatus)

    /**
     * For given LocalDate and TimeZoneId, returns
     * Pair<1, 3> if on that day, this is first day out of 3 days that the Event spans.
     */
    fun Event.calculateFullDayCounter(date: LocalDate, timeZoneId: String): Pair<Int, Int>
    fun UiEvent.calculateFullDayCounter(date: LocalDate): Pair<Int, Int>
    fun Event.formatFullDayCounter(date: LocalDate, timeZoneId: String): String?
    fun UiEvent.formatFullDayCounter(date: LocalDate): String?
    fun Event.formatStart(timeZoneId: String, is24Hour: Boolean): Pair<String?, String?>
    fun Event.formatStartForNotification(timeZoneId: String, resources: Resources, is24Hour: Boolean?) : String
    fun Event.formatEnd(timeZoneId: String, is24Hour: Boolean): Pair<String?, String?>
    fun Event.formatStartEndForActualEndDate(timeZoneId: String, resources: Resources, is24Hour: Boolean): String

    /**
     * @return <formatted date?, formatted time?>
     */
    fun Event.formatDateOrDateTimeProperty(property: DateOrDateTimeProperty?, timeZoneId: String, is24Hour: Boolean?, isAllDay: Boolean) : Pair<String?, String?>

    /**
     * Occurrences are generated using DTSTART/DTEND timezone, but formatted with passed timeZoneId param.
     */
    fun Event.generateOccurrencesInFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): List<Event.Occurrence>?

    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone
     * or the first X occurrences, whatever comes first.
     *
     * If only [firstOccurrenceFromDateTime] is provided, first occurrence will start at that date, or later,
     * and we will return only that first one. NOT all occurrences between <firstOccurrenceFromDateTime - toDate>
     *
     * Occurrences are in passed timezone, not in original Event's timezone.
     *
     * You need to provide at least [toDate] or [occurrenceCount] or [firstOccurrenceFromDateTime]
     */
    fun Event.generateOccurrences(timeZoneId: String, toDate: LocalDate?, firstOccurrenceFromDateTime: ZonedDateTime?, occurrenceCount: Int?): List<Event.Occurrence>?

    /**
     * Generates all occurrences of a recurring Event until given LocalDate in TimeZone.
     *
     * Occurrences are in passed timezone, not in original Event's timezone.
     */
    fun Event.generateOccurrencesUntil(toDate: LocalDate, timeZoneId: String): List<Event.Occurrence>?

    /**
     * Generate first occurrence happening at [fromDateTime] or later.
     */
    fun Event.generateFirstOccurrenceSince(fromDateTime: ZonedDateTime): Event.Occurrence?

    /**
     * Filtered by exdates and taking single edits into consideration.
     */
    fun Event.generateFirstRealOccurrenceSince(allEvents: List<Event>, fromDateTime: ZonedDateTime): Event.Occurrence?

    /**
     * @return Exception Date if it has been set
     */
    fun Event.addExceptionDate(occurrenceNumber: Int, timezone: String? = null)
    fun Event.setRecurrenceId(recurrenceId: ZonedDateTime, hasTime: Boolean)

    /**
     * @param occurrenceNumber has to be 2 or more for this to make sense
     */
    fun Event.handleDeleteThisAndFuture(occurrenceNumber: Int)

    /**
     * Calculate all Exception Dates in either the timezone of the EXDATE property, or default system timezone (if event is All-Day).
     */
    fun Event.getExceptionDates(): List<ZonedDateTime>?

    /**
     * Occurrence is generated using DTSTART/DTEND timezone, but formatted with passed param.
     *
     * @param occurrenceNumber has to be a positive number
     */
    fun Event.generateOccurrence(occurrenceNumber: Int, timeZoneId: String): Event.Occurrence?

    // TODO remove nullability from dateTimeStart/End and use function from ICalUtils
    fun Event.overlapsWithFullDayRange(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean

    fun Event.overlapsWithDateRange(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime): Boolean

    /**
     * For Single edits only
     * @returns the occurrence number of the original event
     */
    fun Event.getSingleEditOriginalOccurrenceNumber(rootEvent: Event, timeZoneId: String): Int?
}
