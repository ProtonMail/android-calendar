package me.proton.android.calendar.common.utils

import android.content.res.Resources
import android.os.Build
import biweekly.util.DateTimeComponents
import biweekly.util.ICalDate
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.aliasesTimezonesMap
import me.proton.android.calendar.common.allowedTimezoneIds
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.timezoneDisplayOverrides
import me.proton.android.calendar.common.windowsTimeZoneMap
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.utils.DateTimeUtils
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object DateTimeUtilsImpl : DateTimeUtils {

    override fun ZonedDateTime.formatDate(timeZoneId: String): String {
        return this
            .withZoneSameInstant(ZoneId.of(timeZoneId))
            .toLocalDate()
            .format(
                DateTimeFormatter
                    .ofLocalizedDate(FormatStyle.FULL)
                    .withLocale(getLocaleForFormatting())
            )
    }

    override fun ZonedDateTime.formatTime(timeZoneId: String, is24Hour: Boolean): String = this.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime().formatTime(is24Hour)

    /**
     * @param excludeTo will exclude exact toDateTime from rightmost range value
     */
    override fun ZonedDateTime.isBetween(fromDateTime: ZonedDateTime, toDateTime: ZonedDateTime, excludeFrom: Boolean, excludeTo: Boolean): Boolean {

        val thisInstant = this.toInstant()
        val fromInstant = fromDateTime.toInstant()
        val toInstant = toDateTime.toInstant()

        return (if (excludeFrom) thisInstant > fromInstant else thisInstant >= fromInstant) && (if (excludeTo) thisInstant < toInstant else thisInstant <= toInstant)
    }


    override fun Pair<ZonedDateTime, ZonedDateTime>.overlaps(other: Pair<ZonedDateTime, ZonedDateTime>): Boolean {
        return this.first <= other.second && this.second >= other.first
    }

    override fun Pair<Long, Long>.overlapsLong(other: Pair<Long, Long>): Boolean {
        return this.first <= other.second && this.second >= other.first
    }

    override fun LocalDate.isBetween(fromDate: LocalDate, toDate: LocalDate): Boolean {
        val thisLocalDate = this.atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC)
        val fromZonedDateTime = fromDate.atTime(LocalTime.MIDNIGHT).atZone(ZoneOffset.UTC)
        val toZonedDateTime = toDate.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC)

        return thisLocalDate.isBetween(fromZonedDateTime, toZonedDateTime, false, false)
    }

    /**
     * Calculate ISO week number for given date, taking custom week start into account.
     */
    override fun LocalDate.weekNumber(startWeekOn: DayOfWeek): Int {

        val firstDayOfTheWeekNumber = this.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset = if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

        var monday: LocalDate = this.minusDays(firstDayOfTheWeekOffset.toLong())
        while (monday.dayOfWeek != DayOfWeek.MONDAY) {
            monday = monday.plusDays(1)
        }

        return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }

    /**
     * Calculate week number difference between two dates
     */
    override fun calculateWeekNumberBetween(start: LocalDate, end: LocalDate, startWeekOn: DayOfWeek): Int {
        var startWeekNumber = start.weekNumber(startWeekOn)
        var endWeekNumber = end.weekNumber(startWeekOn)

        val yearDifference = abs(start.year - end.year)
        if (yearDifference == 0) return endWeekNumber - startWeekNumber

        // Handle case where first day of year's week number is previous year's last week number
        if (start.monthValue == 1 && startWeekNumber > 5) startWeekNumber = 0
        if (end.monthValue == 1 && endWeekNumber > 5) endWeekNumber = 0

        var tmpStart = start
        var weeksToAdd = 0
        for (i in 1 until yearDifference) {
            tmpStart =
                if (start.year < end.year) tmpStart.plusYears(i.toLong())
                else tmpStart.minusYears(i.toLong())
            weeksToAdd += calculateWeekNumberInYear(tmpStart, startWeekOn)
        }

        if (tmpStart.year + 1 == end.year) {
            // End is after start
            weeksToAdd += endWeekNumber + (calculateWeekNumberInYear(start, startWeekOn) - startWeekNumber)
        } else if (tmpStart.year - 1 == end.year) {
            // End is before start
            tmpStart = tmpStart.minusYears(1)
            weeksToAdd += (calculateWeekNumberInYear(tmpStart, startWeekOn) - endWeekNumber) + startWeekNumber
            weeksToAdd *= -1 // Turn negative
        }

        return weeksToAdd
    }

    /**
     * Calculate week number in a year
     */
    override fun calculateWeekNumberInYear(date: LocalDate, startWeekOn: DayOfWeek): Int {
        val lastDayWeekNumber = date.withDayOfYear(date.lengthOfYear()).weekNumber(startWeekOn)
        return if (lastDayWeekNumber == 1) date.withDayOfYear(date.lengthOfYear() - 7).weekNumber(startWeekOn)
        else lastDayWeekNumber
    }

    override fun LocalDate.toDate(timeZoneId: String?): Date = Date.from(this.atStartOfDay(ZoneId.of(timeZoneId ?: ZoneId.systemDefault().id)).toInstant())

    override fun DayOfWeek.format(firstLetter: Boolean): String {
        val locale = getLocaleForFormatting()
        val edgeCaseAbbreviation = locale.language == "ca" || locale.language == "zh"
        val textStyle = if (!firstLetter) TextStyle.FULL else if (edgeCaseAbbreviation) TextStyle.SHORT else TextStyle.NARROW
        val formatted = this.getDisplayName(
            textStyle,
            locale
        )
        return if (firstLetter) formatted.replaceFirstChar { it.titlecase(locale) } else formatted
    }

    override fun DayOfWeek.formatShort(): String {
        return this.getDisplayName(
            TextStyle.SHORT,
            getLocaleForFormatting()
        )
    }

    override fun Month.formatShort(): String {
        return this.getDisplayName(TextStyle.SHORT, getLocaleForFormatting())
    }

    override fun DayOfWeek.toBiweeklyDayOfWeek(): biweekly.util.DayOfWeek {
        return biweekly.util.DayOfWeek.values()[(this.ordinal + 1) % 7]
    }

    override fun biweekly.util.DayOfWeek.toDayOfWeek(): DayOfWeek {
        return DayOfWeek.of((this.calendarConstant)).minus(1)
    }

    override fun ICalDate.toZonedDateTime(timezone: String): ZonedDateTime {
        return if (this.hasTime()) {
            ZonedDateTime.ofInstant(this.toInstant(), ZoneId.of(timezone))
        } else {
            this.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
        }
    }

    override fun Date.toZonedDateTime(timezone: String, isAllDay: Boolean): ZonedDateTime {
        return if (!isAllDay) {
            ZonedDateTime.ofInstant(this.toInstant(), ZoneId.of(timezone))
        } else {
            this.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
        }
    }

    override fun formatTimeZoneId(timeZoneId: String, forInstant: Instant, displayId: Boolean): String {
        val rawOffset = TimeZone.getTimeZone(timeZoneId).getOffset(Date.from(forInstant).time).toLong()
        val offsetLocalTime = LocalTime.MIDNIGHT.plus(if (rawOffset < 0) -rawOffset else rawOffset, ChronoUnit.MILLIS)

        val offset = "${offsetLocalTime.hour}${if (offsetLocalTime.minute > 0) ":${offsetLocalTime.minute}" else ""}"

        val tzDisplay = timezoneDisplayOverrides[timeZoneId] ?: timeZoneId
        return "${if (displayId) "$tzDisplay " else ""}(GMT${if (rawOffset < 0) "-" else "+"}${offset})"
    }

    override fun getTimezoneOffsetDifferenceSeconds(instantA: Instant, instantB: Instant, timeZoneId: String): Int {
        val rawOffsetA = TimeZone.getTimeZone(timeZoneId).getOffset(Date.from(instantA).time).toLong()
        val rawOffsetB = TimeZone.getTimeZone(timeZoneId).getOffset(Date.from(instantB).time).toLong()

        val offsetLocalTimeA = LocalTime.MIDNIGHT.plus(if (rawOffsetA < 0) -rawOffsetA else rawOffsetA, ChronoUnit.MILLIS)
        val offsetLocalTimeB = LocalTime.MIDNIGHT.plus(if (rawOffsetB < 0) -rawOffsetB else rawOffsetB, ChronoUnit.MILLIS)
        val diff = offsetLocalTimeB.toSecondOfDay() - offsetLocalTimeA.toSecondOfDay()
        return if (rawOffsetA < 0) -diff else diff
    }

    override fun areTimeZoneOffsetsDifferent(timeZoneIdA: String, timeZoneIdB: String, forInstant: Instant?): Boolean? {

        if (!TimeZone.getAvailableIDs().contains(timeZoneIdA) || !TimeZone.getAvailableIDs().contains(timeZoneIdB)) {
            return null
        }

        val offsetA = TimeZone.getTimeZone(timeZoneIdA).getOffset(Date.from(forInstant ?: Instant.now()).time).toLong()
        val offsetB = TimeZone.getTimeZone(timeZoneIdB).getOffset(Date.from(forInstant ?: Instant.now()).time).toLong()

        return offsetA != offsetB
    }

    override fun dateToDateTime(zonedDateTime: ZonedDateTime, timeZoneId: String, setRawComponents: Boolean): ICalDate {
        val date = Date.from(
            ZonedDateTime.of(
                zonedDateTime.toLocalDate(),
                LocalTime.of(23, 59, 59),
                ZoneId.of(timeZoneId)
            ).withZoneSameInstant(
                ZoneId.of(timeZoneId)
            ).toInstant()
        )
        return if (setRawComponents) {
            ICalDate(
                date,
                DateTimeComponents(date),
                true
            )
        } else {
            ICalDate(
                date,
                true
            )
        }
    }

    override fun dateTimeToDate(iCalDate: ICalDate, timeZoneId: String, setRawComponents: Boolean): ICalDate {
        val date = Date.from(
            ZonedDateTime.of(
                iCalDate.toInstant().atZone(ZoneId.of(timeZoneId)).toLocalDate(),
                LocalTime.MIDNIGHT,
                ZoneId.systemDefault()
            ).toInstant()
        )
        val rawComponents =
            if (setRawComponents) {
                try {
                    DateTimeComponents.parse(
                        // Use parse to remove time from DateTimeComponents
                        DateTimeComponents(date).toString(false, false)
                    )
                } catch (e: IllegalArgumentException) {
                    TimberLogger.e("dateTimeToDate failed to parse DateTimeComponents")
                    null
                }
            } else {
                null
            }
        return if (setRawComponents && rawComponents != null) {
            ICalDate(
                date,
                rawComponents,
                false
            )
        } else {
            ICalDate(
                date,
                false
            )
        }
    }

    override fun startEndOverlapsWithFullDayRange(startDateTime: ZonedDateTime, endDateTime: ZonedDateTime, fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Boolean {

        val fromDateTime = fromDate.atStartOfDay(ZoneId.of(timeZoneId))
        val toDateTime = toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId))

        return (startDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBetween(fromDateTime, toDateTime, excludeFrom = false, excludeTo = true)) // starts in the range
                || (endDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBetween(fromDateTime, toDateTime, excludeFrom = true, excludeTo = false)) // ends in the range
                || ((startDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isBefore(fromDateTime)) && endDateTime.withZoneSameLocal(ZoneId.of(timeZoneId)).isAfter(toDateTime)) // starts before or ends after range, but happens during range
    }

    /**
     * If supplied TimeZone is not supported, fallback retaining UTC offset.
     */
    override fun fallbackTimeZone(timeZone: String, fallbackToDefault: Boolean): String? {
        return if (allowedTimezoneIds.contains(timeZone)) {
            timeZone
        } else {

            val aliasTimezone = aliasesTimezonesMap[timeZone]
            if (aliasTimezone != null) {
                fallbackTimeZone(aliasTimezone, fallbackToDefault)
            } else if (TimeZone.getAvailableIDs().contains(timeZone)) {

                val offset = TimeZone.getTimeZone(timeZone).getOffset(Date.from(Instant.now()).time)
                val alternativeTimezones = TimeZone.getAvailableIDs(offset).filter { allowedTimezoneIds.contains(it) }

                val alternative = alternativeTimezones.firstOrNull { it.startsWith(timeZone.substringBefore("/")) } ?: alternativeTimezones.firstOrNull()

                alternative ?: if (fallbackToDefault) TimeZone.getDefault().id else null
            } else if (!windowsTimeZoneMap[timeZone.lowercase(Locale.getDefault()).replace(".", "")].isNullOrEmpty()) {
                val windowsIdReplacement = windowsTimeZoneMap[timeZone.lowercase(Locale.getDefault()).replace(".", "")]
                    ?: return if (fallbackToDefault) TimeZone.getDefault().id
                    else null
                fallbackTimeZone(windowsIdReplacement, fallbackToDefault)
            } else {
                if (fallbackToDefault) TimeZone.getDefault().id
                else null
            }
        }
    }

    override fun LocalTime.formatTime(is24Hour: Boolean?, short: Boolean): String {
        return if (is24Hour == true) {
            this.format(DateTimeFormatter.ofPattern("HH:mm").withLocale(getLocaleForFormatting()))
        } else if (is24Hour == false) {
            this.format(DateTimeFormatter.ofPattern(
                if (short) "h a"
                else "hh:mm a"
            ).withLocale(getLocaleForFormatting()))
        } else {
            this.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(getLocaleForFormatting()))
        }
    }

    // TODO add and change parameters for customisation
    override fun LocalDate.formatWithDayOfWeek(showDayOfWeek: Boolean): String {

        val dateTimeFormatter = if (showDayOfWeek) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(getLocaleForFormatting())
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(getLocaleForFormatting())
        }

        return this.format(dateTimeFormatter)
    }

    override fun LocalDate.isLastDayOfWeekInMonth() = this.plusDays(7).monthValue != this.monthValue

    /**
     * This returns the excess number of days for the month's last week that are from the upcoming month
     */
    override fun getLastWeekOfMonthOffset(startWeekOn: DayOfWeek, lastDayOfMonth: LocalDate): Int {
        val weekEnd = startWeekOn.plus(6)
        var offset = 0
        (0 until 7).forEach {
            if (lastDayOfMonth.plusDays(it.toLong()).dayOfWeek == weekEnd) return offset
            offset++
        }

        return offset
    }

// TODO add function for calculating how many days-of-week are there in a given month, we can use it for "backwards" formatting then

    override fun LocalDate.weekInMonth(): Int = this.get(ChronoField.ALIGNED_WEEK_OF_MONTH)

    /**
     * Returns "January", etc.
     */
    override fun LocalDate.formatMonth(capitalize: Boolean): String {
        val dateFormat = SimpleDateFormat("LLLL", getLocaleForFormatting())
        val formattedMonth = dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
        if (capitalize)
            return formattedMonth.substring(0, 1).uppercase(getLocaleForFormatting()) +
                    formattedMonth.substring(1).lowercase(getLocaleForFormatting())
        return formattedMonth
    }

    override fun LocalDate.formatDayOfWeek(short: Boolean): String {
        val dateFormat = SimpleDateFormat(if (short) "EEEEE" else "EEEE", getLocaleForFormatting())
        return dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }

    override fun LocalDate.formatDayOfWeekMedium(): String {
        val dateFormat = SimpleDateFormat("EEE", getLocaleForFormatting())
        return dateFormat.format(Date.from(this.atStartOfDay(ZoneId.systemDefault()).toInstant()))
    }

    /**
     * We only allow Locales used to format date & time that our application is translated to.
     */
    override fun getLocaleForFormatting(): Locale {
        if (!CalendarFeatureFlag.ChangeLanguage.fallbackValue) return Locale.US
        val appDefaultLocale = Locale.getDefault()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            getSupportedLocaleOrNull(
                appDefaultLocale
            ) ?: getSupportedLocaleOrNull(
                Resources.getSystem().configuration.locales[0]
            ) ?: Locale.US // Fallback to English (US)
        } else {
            getSupportedLocaleOrNull(
                appDefaultLocale
            ) ?: getSupportedLocaleOrNull(
                Resources.getSystem().configuration.locale
            ) ?: Locale.US // Fallback to English (US)
        }
    }

    private fun getSupportedLocaleOrNull(locale: Locale): Locale? {
        return when (locale.toLanguageTag().lowercase()) {
            // Check for supported country specific language tags first
            "es-es", // Spanish (Spain)
            "es-419", // Spanish (Latin America)
            "es-mx", // Spanish (Mexico)
            "pt-br", // Portuguese (Brazil)
            "pt-pt", // Portuguese (Portugal)
            "sv-se", // Swedish
            "zh-tw", // Chinese Traditional (Taiwan)
            "zh-hant-tw" // Chinese Traditional (Taiwan)
            -> locale
            else -> {
                when (locale.language.lowercase()) {
                    // Check for supported languages
                    "en", // English
                    "ca", // Catalan
                    "cs", // Czech
                    "da", // Danish
                    "de", // German
                    "es", // Spanish
                    "fr", // French
                    "fi", // Finnish
                    "hu", // Hungarian
                    "it", // Italian
                    "in", // Indonesian, not supported by Locale class starting from Android 15, replaced with "id"
                    "nl", // Nederlands
                    "pl", // Polish
                    "pt", // Portuguese
                    "ro", // Romanian
                    "sv", // Swedish
                    "tr", // Turkish
                    "be", // Belarusian
                    "ru", // Russian
                    "uk", // Ukrainian
                    "ka", // Georgian
                    "zh", // Chinese
                    "hi" // Hindi
                    -> locale
                    else -> {
                        null
                    }
                }
            }
        }
    }

    override fun Collection<CalendarsRepository.EventsWindow>.getFullyOverlappingWindow(eventsWindow: CalendarsRepository.EventsWindow): CalendarsRepository.EventsWindow? {
        return this.find {
            eventsWindow.fromDate.isBetween(it.fromDate, it.toDate) && eventsWindow.toDate.isBetween(it.fromDate, it.toDate)
        }
    }

    override fun LocalDate.firstDayOfWeek(weekStartDayOfWeek: DayOfWeek): LocalDate {
        return this.with(TemporalAdjusters.previousOrSame(weekStartDayOfWeek))
    }

    override fun LocalDate.firstDayOfWeek(weekStart: Int?): LocalDate? {
        val weekStartDayOfWeek = weekStart?.let {
            AndroidUtils.getWeekStartDayOfWeek(weekStart)
        } ?: return null
        return this.with(TemporalAdjusters.previousOrSame(weekStartDayOfWeek))
    }

    override fun LocalDateTime.firstDayOfWeek(weekStartDayOfWeek: DayOfWeek): LocalDateTime {
        return this.with(TemporalAdjusters.previousOrSame(weekStartDayOfWeek))
    }

    override fun LocalDateTime.firstDayOfWeek(weekStart: Int?): LocalDateTime? {
        val weekStartDayOfWeek = weekStart?.let {
            AndroidUtils.getWeekStartDayOfWeek(weekStart)
        } ?: return null
        return this.with(TemporalAdjusters.previousOrSame(weekStartDayOfWeek))
    }

    /**
     * Returns the given time minus one hour. If hour is equal to 0, return 00:00 LocalTime.
     */
    override fun LocalTime.getTimeWithPadding(): LocalTime {
        return if (this.hour > 0) {
            // Add a 1 hour padding above the current time (and thus: the now line)
            this.minusHours(1)
        } else {
            // If hour is equal to 0, return 00:00 LocalTime
            this.minusMinutes(this.minute.toLong())
        }
    }
}
