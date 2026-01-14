package me.proton.android.calendar.domain.utils

import biweekly.util.ICalDate
import me.proton.android.calendar.domain.CalendarsRepository
import java.time.*
import java.util.*

interface DateTimeUtils {

    fun ZonedDateTime.formatDate(timeZoneId: String): String

    fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean

    fun Pair<ZonedDateTime, ZonedDateTime>.overlaps(other: Pair<ZonedDateTime, ZonedDateTime>): Boolean
    fun Pair<Long, Long>.overlapsLong(other: Pair<Long, Long>): Boolean

    fun LocalDate.isBetween(fromDate: LocalDate, toDate: LocalDate): Boolean

    /**
     * Calculate ISO week number for given date, taking custom week start into account.
     */
    fun LocalDate.weekNumber(startWeekOn: DayOfWeek): Int
    fun calculateWeekNumberBetween(start: LocalDate, end: LocalDate, startWeekOn: DayOfWeek): Int
    fun calculateWeekNumberInYear(date: LocalDate, startWeekOn: DayOfWeek): Int
    fun LocalDate.toDate(timeZoneId: String? = null): Date
    fun DayOfWeek.format(firstLetter: Boolean = false): String

    /**
     * Returns "Sat", "Mon", etc.
     */
    fun DayOfWeek.formatShort(): String

    /**
     * Returns "Dec", "Jan", etc.
     */
    fun Month.formatShort(): String
    fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek
    fun biweekly.util.DayOfWeek.toDayOfWeek(): DayOfWeek

    fun ICalDate.toZonedDateTime(timezone: String): ZonedDateTime

    fun Date.toZonedDateTime(timezone: String, isAllDay: Boolean): ZonedDateTime

    fun formatTimeZoneId(timeZoneId: String, forInstant: Instant, displayId: Boolean = true): String
    fun getTimezoneOffsetDifferenceSeconds(instantA: Instant, instantB: Instant, timeZoneId: String): Int

    fun areTimeZoneOffsetsDifferent(timeZoneIdA: String, timeZoneIdB: String, forInstant: Instant? = null): Boolean?

    fun dateToDateTime(zonedDateTime: ZonedDateTime, timeZoneId: String, rawComponents: Boolean = false): ICalDate
    fun dateTimeToDate(iCalDate: ICalDate, timeZoneId: String, rawComponents: Boolean = false): ICalDate

    fun startEndOverlapsWithFullDayRange(startDateTime: ZonedDateTime, endDateTime: ZonedDateTime, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean

    /**
     * If supplied TimeZone is not supported, fallback retaining UTC offset.
     */
    fun fallbackTimeZone(timeZone: String, fallbackToDefault: Boolean = true): String?

    fun LocalTime.formatTime(is24Hour: Boolean?, short: Boolean = false): String

    // TODO add and change parameters for customisation
    fun LocalDate.formatWithDayOfWeek(showDayOfWeek: Boolean = false): String

    fun LocalDate.isLastDayOfWeekInMonth(): Boolean

    fun getLastWeekOfMonthOffset(startWeekOn: DayOfWeek, lastDayOfMonth: LocalDate): Int

// TODO add function for calculating how many days-of-week are there in a given month, we can use it for "backwards" formatting then

    fun LocalDate.weekInMonth(): Int

    /**
     * Returns "January", etc.
     */
    fun LocalDate.formatMonth(capitalize: Boolean = false): String

    fun LocalDate.formatDayOfWeek(short: Boolean = false): String

    /**
     * Returns "Mon", "Tue"...
     */
    fun LocalDate.formatDayOfWeekMedium(): String

    /**
     * We only allow Locales used to format date & time that our application is translated to.
     */
    fun getLocaleForFormatting(): Locale

    /**
     * Gets [EventsWindow] from the collection if argument fully overlaps with it.
     */
    fun Collection<CalendarsRepository.EventsWindow>.getFullyOverlappingWindow(eventsWindow: CalendarsRepository.EventsWindow): CalendarsRepository.EventsWindow?

    fun LocalDate.firstDayOfWeek(weekStartDayOfWeek: DayOfWeek): LocalDate

    fun LocalDate.firstDayOfWeek(weekStart: Int?): LocalDate?

    fun LocalDateTime.firstDayOfWeek(weekStartDayOfWeek: DayOfWeek): LocalDateTime

    fun LocalDateTime.firstDayOfWeek(weekStart: Int?): LocalDateTime?

    /**
     * Returns the given time minus one hour. If hour is equal to 0, return 00:00 LocalTime.
     */
    fun LocalTime.getTimeWithPadding(): LocalTime
}
