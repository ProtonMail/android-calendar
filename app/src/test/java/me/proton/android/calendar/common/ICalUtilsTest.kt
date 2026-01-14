package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import biweekly.ICalVersion
import biweekly.component.VAlarm
import biweekly.parameter.ParticipationStatus
import biweekly.parameter.Related
import biweekly.property.CalendarScale
import biweekly.property.Method
import biweekly.property.RecurrenceRule
import biweekly.property.Trigger
import biweekly.util.ByDay
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toBiweeklyDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.EventUtilsImpl.addExceptionDate
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatDateOrDateTimeProperty
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstRealOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrences
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesInFullDayRange
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.EventUtilsImpl.getExceptionDates
import me.proton.android.calendar.common.utils.EventUtilsImpl.handleDeleteThisAndFuture
import me.proton.android.calendar.common.utils.EventUtilsImpl.overlapsWithFullDayRange
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustOutgoingAllDayEvent
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustRRuleToStartDate
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustStartEndTimeZones
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustToWeekStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.clone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.createNewVEvent
import me.proton.android.calendar.common.utils.ICalUtilsImpl.eventStartZonedDateTimeToDate
import me.proton.android.calendar.common.utils.ICalUtilsImpl.explodeEventDayByDay
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOccurencesByRecurrenceId
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutDuplicates
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutDuplicatesInSubscribedCalendars
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.utils.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.common.utils.ICalUtilsImpl.generateProtonProdId
import me.proton.android.calendar.common.utils.ICalUtilsImpl.generateProtonUid
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getCancelIcs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getInviteIcs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getResponseIcs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isCalendarChangeAllowed
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isDateTimeTheSame
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.parseICalString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sanitise
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStartTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sortForMonthView
import me.proton.android.calendar.common.utils.ICalUtilsImpl.wrapInICalendar
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.filterFromTheEnd
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.test.shared.mocks.EventMocks
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone


internal class ICalUtilsTest {

    @Test
    fun `is date-time-timezone the same between two ICalendars`() {

        val iCalendar1 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar1.setStartTimeZone("Europe/Vilnius")
        iCalendar1.setEndTimeZone("Europe/Vilnius")

        val iCalendar2 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar2.setStartTimeZone("Europe/Vilnius")
        iCalendar2.setEndTimeZone("Europe/Vilnius")

        val iCalendar3 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar3.setStartTimeZone("Europe/Zurich")
        iCalendar3.setEndTimeZone("Europe/Zurich")

        val iCalendar4 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 14, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar4.setStartTimeZone("Europe/Vilnius")
        iCalendar4.setEndTimeZone("Europe/Vilnius")

        val iCalendar5 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(ZonedDateTime.of(2020, 1, 10, 12, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
            setDateEnd(Date.from(ZonedDateTime.of(2020, 1, 10, 13, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()), true)
        }.wrapInICalendar()
        iCalendar5.setStartTimeZone("Europe/Vilnius")
        iCalendar5.setEndTimeZone("Europe/Vilnius")

        assertThat(iCalendar1.isDateTimeTheSame(iCalendar2)).isFalse()
        assertThat(iCalendar2.isDateTimeTheSame(iCalendar3)).isFalse()
        assertThat(iCalendar3.isDateTimeTheSame(iCalendar4)).isFalse()
        assertThat(iCalendar4.isDateTimeTheSame(iCalendar5)).isTrue()
        assertThat(iCalendar5.isDateTimeTheSame(iCalendar5)).isTrue()
        assertThat(iCalendar5.isDateTimeTheSame(null)).isFalse()

        val iCalendar6 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(LocalDate.of(2020, 1, 10).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 1, 11).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
        }.wrapInICalendar()
        val iCalendar7 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(LocalDate.of(2020, 1, 10).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 1, 11).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
        }.wrapInICalendar()
        val iCalendar8 = ICalUtilsImpl.createNewVEvent().apply {
            setDateStart(Date.from(LocalDate.of(2020, 1, 10).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 1, 12).atStartOfDay(ZoneId.of("Europe/Vilnius")).toInstant()), false)
        }.wrapInICalendar()

        assertThat(iCalendar6.isDateTimeTheSame(iCalendar7)).isTrue()
        assertThat(iCalendar7.isDateTimeTheSame(iCalendar8)).isFalse()
        assertThat(iCalendar7.isDateTimeTheSame(null)).isFalse()

    }

    @Test
    fun `calculate ISO week number for a date`() {

        assertThat(
            LocalDate.of(2020, 12, 26).weekNumber(java.time.DayOfWeek.SATURDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2020, 12, 31).weekNumber(java.time.DayOfWeek.SATURDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2021, 1, 1).weekNumber(java.time.DayOfWeek.SATURDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2021, 1, 2).weekNumber(java.time.DayOfWeek.SATURDAY)
        ).isEqualTo(1)

        assertThat(
            LocalDate.of(2021, 7, 31).weekNumber(java.time.DayOfWeek.SATURDAY)
        ).isEqualTo(31)

        assertThat(
            LocalDate.of(2021, 8, 1).weekNumber(java.time.DayOfWeek.SATURDAY)
        ).isEqualTo(31)

        assertThat(
            LocalDate.of(2020, 12, 27).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(52)

        assertThat(
            LocalDate.of(2020, 12, 28).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2020, 12, 31).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2021, 1, 1).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2021, 1, 3).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(53)

        assertThat(
            LocalDate.of(2021, 1, 4).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(1)

        assertThat(
            LocalDate.of(2022, 8, 1).weekNumber(java.time.DayOfWeek.MONDAY)
        ).isEqualTo(31)

    }

    // TODO check out how we can fallback for timezones that are not handled on the device
    //  this test fails on the CI which means it will fail on random devices as well
    @Disabled
    @Test
    fun `all timezone IDs allowed by server are correctly recognised`() {

        val availableTimezoneIds = TimeZone.getAvailableIDs()

        allowedTimezoneIds.forEach {
            assertThat(availableTimezoneIds.contains(it)).isTrue()
        }

        assertThat(availableTimezoneIds.contains("Non/Existing_Timezone")).isFalse()

    }

    @Test
    fun `fallback to allowed timezone`() {

        val defaultSystemTimeZone = TimeZone.getDefault().id

        assertThat(fallbackTimeZone("Non/Existing_Timezone")).isEqualTo(defaultSystemTimeZone)

        assertThat(fallbackTimeZone("Europe/Zurich")).isEqualTo("Europe/Zurich")

        /* Windows Time Zones */

        assertThat(fallbackTimeZone("FLE Standard Time")).isEqualTo("Europe/Kyiv")

        assertThat(fallbackTimeZone("Taipei Standard Time")).isEqualTo("Asia/Taipei")

        assertThat(fallbackTimeZone("saskatchewan")).isEqualTo("America/Edmonton")

        assertThat(fallbackTimeZone("abu dhabi, muscat")).isEqualTo("Asia/Dubai")

        assertThat(fallbackTimeZone("helsinki, kyiv, riga, sofia, tallinn, vilnius")).isEqualTo("Europe/Helsinki")

        assertThat(fallbackTimeZone("W Europe Standard Time")).isEqualTo("Europe/Berlin")

        assertThat(fallbackTimeZone("W. Europe Standard Time")).isEqualTo("Europe/Berlin")

        /* Aliases Time Zones */

        assertThat(fallbackTimeZone("Europe/Bratislava")).isEqualTo("Europe/Prague")

        assertThat(fallbackTimeZone("Atlantic/St_Helena")).isEqualTo("Africa/Abidjan")

    }

    @Test
    fun `filter from the end of list`() {

        val list1 = listOf(0, 0, 1, 0, 0)
        assertThat(list1.filterFromTheEnd { it == 1 }).isEqualTo(listOf(1))

        val list2 = listOf(0, 0, 1, 1, 1)
        assertThat(list2.filterFromTheEnd { it == 1 }).isEqualTo(listOf(1, 1, 1))

        val list3 = listOf(1, 0, 0, 0, 0)
        assertThat(list3.filterFromTheEnd { it == 1 }).isEqualTo(listOf(1))

        val list4 = listOf(1, 1, 1, 1, 1)
        assertThat(list4.filterFromTheEnd { it == 1 }).isEqualTo(listOf(1, 1, 1, 1, 1))

        val list5 = listOf(5, 4, 3, 2, 1)
        assertThat(list5.filterFromTheEnd { it > 2 }).isEqualTo(listOf(5, 4, 3))

    }

    @Test
    fun `all-day event has no time and no timezone property`() {

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20))
        event.setEnd(LocalDate.of(2020, 1, 22))

        val calendar = event.wrapInICalendar()

        assertThat(event.dateStart.value.hasTime()).isFalse()
        assertThat(event.dateEnd.value.hasTime()).isFalse()

        assertThat(calendar.timezoneInfo.defaultTimezone).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd)).isNull()

    }

    @Test
    fun `partial-day event has correct time and timezone property`() {

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")
        event.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Zurich")

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Zurich")
        calendar.setEndTimeZone("Europe/Zurich")

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        val printedICal = calendar.printToString()
        assertThat(printedICal).contains("DTSTART;TZID=Europe/Zurich:20200120T100000")
        assertThat(printedICal).contains("DTEND;TZID=Europe/Zurich:20200120T110000")
    }

    @Test
    fun `multiple edits of datetimes and timezones properly overwrite old values`() {

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")
        event.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Zurich")

        event.setStart(LocalDate.of(2020, 1, 20))
        event.setEnd(LocalDate.of(2020, 1, 20))

        assertThat(event.dateStart.value.hasTime()).isFalse()
        assertThat(event.dateEnd.value.hasTime()).isFalse()

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Zurich")

        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart).component.timezoneId.value).isEqualTo("Europe/Zurich")

        calendar.setStartTimeZone(null)

        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
    }

    @Test
    fun `set only time of event's start and end`() {

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Vilnius")
        event.setEnd(LocalDate.of(2020, 2, 20), LocalTime.of(11, 0), "Europe/Vilnius")

        event.setStart(LocalTime.of(15, 10, 40), "Europe/Vilnius")
        event.setEnd(LocalTime.of(16, 20, 50), "Europe/Vilnius")

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        assertThat(event.getStart("Europe/Vilnius")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 20))
        assertThat(event.getEnd("Europe/Vilnius")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 2, 20))

        assertThat(event.getStart("Europe/Vilnius")!!.toLocalTime()).isEqualTo(LocalTime.of(15, 10, 40))
        assertThat(event.getEnd("Europe/Vilnius")!!.toLocalTime()).isEqualTo(LocalTime.of(16, 20, 50))

    }

    @Test
    fun `adjust WEEKLY RRULE to start date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=WEEKLY;BYDAY=TU,WE,TH,FR
    SEQUENCE:0
    SUMMARY:weekly on Tu, We, Th, Fr
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        var oldStartDate = event.iCalEvent.getStart(displayTimeZoneId)
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 30), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate(oldStartDate)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(3)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.FRIDAY))).isTrue()

        oldStartDate = event.iCalEvent.getStart(displayTimeZoneId)
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 27), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate(oldStartDate)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(3)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.MONDAY))).isTrue()

    }

    @Test
    fun `adjust MONTHLY RRULE to start date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=-1
    SEQUENCE:0
    SUMMARY:monthly on last Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        // original event should occur on 4th Tuesday every month

        // last Wednesday -- 2020-07-29
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 29), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.WEDNESDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(-1)

        // 1st Wednesday -- 2020-07-01
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 1), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.WEDNESDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(1)

        // 2nd Friday -- 2020-07-10
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 10), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.FRIDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(2)

        // fifth Wednesday -- 2020-07-29 -- we should display it as "last Wednesday" though
        event.iCalEvent.setStart(LocalDate.of(2020, 7, 29), LocalTime.of(13, 0), "Europe/Zurich")
        event.iCalendar.setStartTimeZone("Europe/Zurich")
        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.size).isEqualTo(1)
        assertThat(event.iCalEvent.recurrenceRule.value.byDay.contains(ByDay(DayOfWeek.WEDNESDAY))).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.bySetPos[0]).isEqualTo(-1)

    }

    @Test
    fun `adjust UNTIL RRULE to start date for part-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=DAILY;UNTIL=20200711
    SEQUENCE:0
    SUMMARY:blah
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.until).isEqualTo(ICalDate.from(ZonedDateTime.of(2020, 7, 28, 21, 59, 59, 0, ZoneId.of("UTC")).toInstant()))

    }

    @Test
    fun `adjust UNTIL RRULE to start date for all-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728
    DTEND;TZID=Europe/Zurich:20200728
    RRULE:FREQ=DAILY;UNTIL=20200711T215959Z
    SEQUENCE:0
    SUMMARY:blah
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.iCalendar.adjustRRuleToStartDate()
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isFalse()
        assertThat(event.iCalEvent.recurrenceRule.value.until).isEqualTo(ICalDate.from(LocalDate.of(2020, 7, 28).atStartOfDay(ZoneId.systemDefault()).withZoneSameInstant(
            ZoneId.of("UTC")).toInstant()))

    }

    @Test
    fun `adjust UNTIL RRULE to new timezone for part-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20220302T123000
    DTEND;TZID=Europe/Vilnius:20220302T130000
    RRULE:FREQ=DAILY;UNTIL=20220309T215959Z
    SEQUENCE:0
    SUMMARY:blah
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val oldTimeZoneId = "Europe/Vilnius"
        val newTimeZoneId = "Asia/Omsk"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.iCalendar.setDefaultTimeZone(newTimeZoneId)
        event.iCalendar.adjustRRuleToStartDate(event.getStart(oldTimeZoneId))
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isTrue()
        assertThat(event.iCalEvent.recurrenceRule.value.until).isEqualTo(ICalDate.from(ZonedDateTime.of(2022, 3, 9, 23, 59, 59, 0, ZoneId.of(newTimeZoneId)).toInstant()))

    }

    @Test
    fun `adjust RRULE to WEEK START`() {

        val eventNoAdjustment = createNewVEvent()
        eventNoAdjustment.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.WEEKLY).interval(1).byDay(DayOfWeek.FRIDAY).build())

        assertThat(eventNoAdjustment.recurrenceRule.value.workweekStarts).isNull()

        val eventWeekly = createNewVEvent()
        eventWeekly.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.WEEKLY).interval(2).byDay(DayOfWeek.FRIDAY).build())
        eventWeekly.recurrenceRule.adjustToWeekStart(java.time.DayOfWeek.SUNDAY)

        TestsLogger.d("${eventWeekly.wrapInICalendar().printToString()}")

        assertThat(eventWeekly.recurrenceRule.value.workweekStarts).isEqualTo(DayOfWeek.SUNDAY)

        val eventYearly = createNewVEvent()
        eventYearly.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.YEARLY).byWeekNo(10).build())
        eventYearly.recurrenceRule.adjustToWeekStart(java.time.DayOfWeek.MONDAY)

        TestsLogger.d("${eventYearly.wrapInICalendar().printToString()}")

        assertThat(eventYearly.recurrenceRule.value.workweekStarts).isEqualTo(DayOfWeek.MONDAY)


    }

    @Test
    fun `adjust time zones of start & end dates for partial-day event`() {

        val originalTimeZoneId = "Pacific/Saipan"
        val requestedTimeZoneId = "Europe/Vilnius"
        val requestedStartHour = 10
        val requestedEndHour = 15

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(requestedStartHour, 0, 10), originalTimeZoneId)
        event.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(requestedEndHour, 0, 10), originalTimeZoneId)

        val calendar = event.wrapInICalendar()

        TestsLogger.d("${calendar.printToString()}")

        calendar.adjustStartEndTimeZones(originalTimeZoneId, requestedTimeZoneId)

        // TODO assert for empty timezones in assignments

        TestsLogger.d("${calendar.printToString()}")
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart).component.timezoneId.value).isEqualTo(requestedTimeZoneId)
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd).component.timezoneId.value).isEqualTo(requestedTimeZoneId)

        val printedICal = calendar.printToString()
        assertThat(printedICal).contains("DTSTART;TZID=${requestedTimeZoneId}:20200120T${requestedStartHour}0000")
        assertThat(printedICal).contains("DTEND;TZID=${requestedTimeZoneId}:20200120T${requestedEndHour}0000")

    }

    @Test
    fun `adjust iCalendar for all-day event`() {

        val timeZoneId = "Europe/Zurich"

        // set
        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(1, 0), timeZoneId)
        event.setEnd(LocalDate.of(2020, 1, 22), LocalTime.of(2, 0), timeZoneId)

        val untilDate = Date.from(ZonedDateTime.of(LocalDate.of(2020, 1, 25), LocalTime.of(23, 59, 59), ZoneId.of("UTC")).withZoneSameInstant(
            ZoneId.of(timeZoneId)).toInstant())
        event.recurrenceRule = RecurrenceRule(Recurrence.Builder(Frequency.DAILY).until(untilDate).build())

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone(timeZoneId)
        calendar.setEndTimeZone(timeZoneId)

        calendar.adjustOutgoingAllDayEvent(timeZoneId)

        assertThat(calendar.events.first().dateStart.value.hasTime()).isFalse()
        assertThat(calendar.events.first().dateEnd.value.hasTime()).isFalse()

        assertThat(calendar.timezoneInfo.timezones).isEmpty()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateStart)).isNull()
        assertThat(calendar.timezoneInfo.getTimezone(event.dateEnd)).isNull()

        assertThat(ZonedDateTime.ofInstant(event.dateStart.value.toInstant(), ZoneId.systemDefault()).dayOfMonth).isEqualTo(20)
        assertThat(ZonedDateTime.ofInstant(event.dateEnd.value.toInstant(), ZoneId.systemDefault()).dayOfMonth).isEqualTo(23)

    }

    @Test
    fun `all-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    SUMMARY:event
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!
        val displayTimeZoneId = "Europe/Zurich"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 26),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 26),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `multi-day all-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200629
    SUMMARY:event
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!
        val displayTimeZoneId = "Europe/Zurich"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 26),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 28),
            LocalDate.of(2020, 6, 29),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 29),
            LocalDate.of(2020, 7, 2),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `part-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20200625T000000
    DTEND;TZID=UTC:20200625T003000
    SUMMARY:part-day on 25th Jun, starts at midnight
    UID:EEB9eHnvGPpXK62b799jf9kL8OpG@proton.me
    DTSTAMP:20200625T133626Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!
        val displayTimeZoneId = "UTC"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 24),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `part-day zero-duration even overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20200625T000000
    DTEND;TZID=UTC:20200625T000000
    SUMMARY:zero-duration on 25th Jun, starts at midnight
    UID:EEB9eHnvGPpXK62b799jf9kL8OpG@proton.me
    DTSTAMP:20200625T133626Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!
        val displayTimeZoneId = "UTC"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 24),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 27),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `multi-day part-day event overlaps with full day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20200625T000000
    DTEND;TZID=UTC:20200627T003000
    SUMMARY:part-day on 25th Jun until 27 Jun, starts at midnight
    UID:EEB9eHnvGPpXK62b799jf9kL8OpG@proton.me
    DTSTAMP:20200625T133626Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!
        val displayTimeZoneId = "UTC"

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 24),
            displayTimeZoneId
        )).isFalse()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 24),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 25),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 26),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 27),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 25),
            LocalDate.of(2020, 6, 28),
            displayTimeZoneId
        )).isTrue()

        assertThat(event.overlapsWithFullDayRange(
            LocalDate.of(2020, 6, 28),
            LocalDate.of(2020, 6, 28),
            displayTimeZoneId
        )).isFalse()

    }

    @Test
    fun `all-day event occurrence happening during DST-change lasts 24h`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 0.30.3//EN
    BEGIN:VEVENT
    DTSTAMP:20220124T093706Z
    DTSTART;VALUE=DATE:20220227
    DTEND;VALUE=DATE:20220228
    RRULE:FREQ=MONTHLY
    SEQUENCE:0
    SUMMARY:27th
    STATUS:CONFIRMED
    UID:dmBGpmqvRvfwGyuFj5qiT05P18Vk@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        // In Europe/Vilnius DST change is on Sunday, 27 March 2022 — 1 hour forward

        // this case doesn't fail if DTSTART is 27 March, it needs to be before that date
        // adding event_duration == 24h to START in order to get END makes the event last 25h
        // we have to add full days

        val occurrenceDuringDSTChange = event.generateOccurrence(2, displayTimeZoneId)!!

        assertThat(occurrenceDuringDSTChange.startDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 27, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrenceDuringDSTChange.endDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 28, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
    }

    @Test
    fun `occurrences of all-day event starting during DST-change last 24h`() {

        val iCalString = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Proton AG//AndroidCalendar 0.30.3//EN
            BEGIN:VEVENT
            DTSTAMP:20220124T092125Z
            DTSTART;VALUE=DATE:20220327
            DTEND;VALUE=DATE:20220328
            RRULE:FREQ=DAILY;COUNT=49
            SEQUENCE:1
            SUMMARY:Daily
            STATUS:CONFIRMED
            UID:zWhxzFUgDVXiog_ZxjJXuOAxXw2u@proton.me
            END:VEVENT
            END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        // In Europe/Vilnius DST change is on Sunday, 27 March 2022 — 1 hour forward

        // subtracting event END from START gives us event_duration == 23h, because it starts on the day of DST change
        // we can't add event_duration to START in order to get END, we have to add full days

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)!!
        val occurrence10 = event.generateOccurrence(10, displayTimeZoneId)!!

        assertThat(occurrence1.startDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 27, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrence1.endDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 28, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrence1.occurrenceNumber).isEqualTo(1)

        assertThat(occurrence10.startDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrence10.endDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 6, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrence10.occurrenceNumber).isEqualTo(10)
    }

    @Test
    fun `part-day event occurrences happening during DST-change have the same time of START and END`() {

        val iCalString = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Proton AG//AndroidCalendar 0.30.3//EN
            BEGIN:VEVENT
            DTSTAMP:20220124T115649Z
            DTSTART;TZID=Europe/Vilnius:20220227T010000
            DTEND;TZID=Europe/Vilnius:20220227T050000
            RRULE:FREQ=MONTHLY
            SEQUENCE:0
            SUMMARY:01:00-05:00 every 27th
            STATUS:CONFIRMED
            UID:sch4mDryiYH6aDkw3VsklnzfhKoW@proton.me
            END:VEVENT
            END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        // In Europe/Vilnius DST change is on Sunday, 27 March 2022 — 1 hour forward

        // we can't add event_duration to START in order to get END, because we might shorten/lengthen the duration
        // of an event by 1 hour due to DST change that affected END

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 3)!!

        assertThat(occurrences[0].occurrenceNumber).isEqualTo(1)
        assertThat(occurrences[0].startDateTime).isEqualTo(ZonedDateTime.of(2022, 2, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].endDateTime).isEqualTo(ZonedDateTime.of(2022, 2, 27, 5, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[1].occurrenceNumber).isEqualTo(2)
        assertThat(occurrences[1].startDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[1].endDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 27, 6, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[2].occurrenceNumber).isEqualTo(3)
        assertThat(occurrences[2].startDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[2].endDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 27, 5, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event with no timezone assignment`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.28.1//EN
    BEGIN:VEVENT
    DTSTAMP:20211112T135249Z
    DTSTART:20211112T140000Z
    DTEND:20211112T143000Z
    RRULE:FREQ=WEEKLY;UNTIL=20211202T225959Z
    SEQUENCE:1
    SUMMARY:Weekly
    STATUS:CONFIRMED
    UID:oEvspzDWmCtVXo2C_MqMWcx5WqPN@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT30M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Zurich"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrencesUntil(LocalDate.of(2021, 11, 26), displayTimeZoneId)!!

        assertThat(occurrences.size).isEqualTo(3)

        assertThat(occurrences[0].occurrenceNumber).isEqualTo(1)
        assertThat(occurrences[0].startDateTime).isEqualTo(ZonedDateTime.of(2021, 11, 12, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].endDateTime).isEqualTo(ZonedDateTime.of(2021, 11, 12, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[1].occurrenceNumber).isEqualTo(2)
        assertThat(occurrences[1].startDateTime).isEqualTo(ZonedDateTime.of(2021, 11, 19, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[1].endDateTime).isEqualTo(ZonedDateTime.of(2021, 11, 19, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[2].occurrenceNumber).isEqualTo(3)
        assertThat(occurrences[2].startDateTime).isEqualTo(ZonedDateTime.of(2021, 11, 26, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[2].endDateTime).isEqualTo(ZonedDateTime.of(2021, 11, 26, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T160000
    DTEND;TZID=/Europe/Budapest:20200625T163000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.generateOccurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(7)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 17, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 17, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of all-day recurring event ending in different month`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 0.31.6//EN
    BEGIN:VEVENT
    DTSTAMP:20220302T174715Z
    UID:9tRjtkUZB_GoQtAOk_BpdDyXdIfs@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    RRULE:FREQ=DAILY
    SUMMARY:Ddd
    DTSTART;VALUE=DATE:20220301
    DTEND;VALUE=DATE:20220402
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrencesUntil(LocalDate.of(2023, 2, 1), displayTimeZoneId)!!

        assertThat(occurrences[0].occurrenceNumber).isEqualTo(1)
        assertThat(occurrences[0].startDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].endDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[4].occurrenceNumber).isEqualTo(5)
        assertThat(occurrences[4].startDateTime).isEqualTo(ZonedDateTime.of(2022, 3, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[4].endDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 6, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[31].occurrenceNumber).isEqualTo(32) // Apr 1 2022 - May 2 2022
        assertThat(occurrences[31].startDateTime).isEqualTo(ZonedDateTime.of(2022, 4, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[31].endDateTime).isEqualTo(ZonedDateTime.of(2022, 5, 3, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(338) // Feb 1 2023 - Mar 4 2023
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2023, 2, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2023, 3, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of all-day event with BYDAY within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200501
    RRULE:FREQ=MONTHLY;BYDAY=1FR
    SEQUENCE:0
    UID:59rnikoltd7srebautub9lmhv2@google.com
    DTSTAMP:20200514T130648Z
    SUMMARY:Monthly 1st Friday
    DTEND;VALUE=DATE:20200502
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 5)!!

        assertThat(occurrences[0].occurrenceNumber).isEqualTo(1)
        assertThat(occurrences[0].startDateTime).isEqualTo(ZonedDateTime.of(2020, 5, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].endDateTime).isEqualTo(ZonedDateTime.of(2020, 5, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[2].occurrenceNumber).isEqualTo(3)
        assertThat(occurrences[2].startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 3, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[2].endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 4, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[4].occurrenceNumber).isEqualTo(5)
        assertThat(occurrences[4].startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 4, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[4].endDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate the only occurrence of part-day event with BYSETPOS`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4;UNTIL=20200805T215959Z
    SEQUENCE:0
    SUMMARY:monthly on fourth Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2030, 8, 1)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 28, 14, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 28, 14, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of all-day event with BYSETPOS within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 0.30.2//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Berlin
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20220107T085759Z
    RRULE:FREQ=MONTHLY;COUNT=2;BYDAY=WE;BYSETPOS=1
    SEQUENCE:0
    SUMMARY:Monthly 1
    STATUS:CONFIRMED
    UID:QiUzUbiB4rIdpiMx4F0w_UJiKZ57@proton.me
    DTSTART;VALUE=DATE:20211201
    DTEND;VALUE=DATE:20211202
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2022, 1, 31)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)!!

        assertThat(occurrences.size).isEqualTo(2)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2021, 12, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2021, 12, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(2)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2022, 1, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2022, 1,  6, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of all-day event with BYSETPOS within full-day range GMT+7 (Winter time) display TZ`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 0.30.2//EN
    BEGIN:VTIMEZONE
    TZID:Asia/Ho_Chi_Minh
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20220107T085759Z
    RRULE:FREQ=MONTHLY;COUNT=2;BYDAY=WE;BYSETPOS=1
    SEQUENCE:0
    SUMMARY:Monthly 1
    STATUS:CONFIRMED
    UID:QiUzUbiB4rIdpiMx4F0w_UJiKZ57@proton.me
    DTSTART;VALUE=DATE:20211201
    DTEND;VALUE=DATE:20211202
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Asia/Ho_Chi_Minh"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2022, 1, 31)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)

        occurrences!!.forEach {
            TestsLogger.e("$it")
        }

        assertThat(occurrences.size).isEqualTo(2)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2021, 12, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2021, 12, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(2)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2022, 1, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2022, 1,  6, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event with BYSETPOS within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4
    SEQUENCE:0
    SUMMARY:monthly on fourth Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2020, 10, 1)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(3)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 28, 14, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 28, 14, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(3)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 22, 14, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2020, 9,  22, 14, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate the only occurrence of part-day event with BYSETPOS since date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4
    SEQUENCE:0
    SUMMARY:monthly on fourth Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeFrom = ZonedDateTime.of(LocalDate.of(2020, 9, 20), LocalTime.of(15, 0), ZoneId.of("Europe/Vilnius"))

        val occurrence = event.generateFirstOccurrenceSince(displayRangeFrom)!!

        assertThat(occurrence.occurrenceNumber).isEqualTo(3)
        assertThat(occurrence.startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 22, 14, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrence.endDateTime).isEqualTo(ZonedDateTime.of(2020, 9,  22, 14, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of yearly all-day event with BYSETPOS`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 0.30.1//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Berlin
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20211227T131037Z
    RRULE:FREQ=MONTHLY;INTERVAL=12;BYDAY=FR;BYSETPOS=3
    SEQUENCE:0
    SUMMARY:Test Duplicated
    UID:ZrsZ8COhD5FpNFbptwnRsOumvMrE@proton.me
    STATUS:CONFIRMED
    DTSTART;VALUE=DATE:20211217
    DTEND;VALUE=DATE:20211218
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = ZoneId.systemDefault().id
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 3)!!

        assertThat(occurrences.size).isEqualTo(3)

        assertThat(occurrences[0].startDateTime).isEqualTo(ZonedDateTime.of(2021, 12, 17, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].endDateTime).isEqualTo(ZonedDateTime.of(2021, 12,  18, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[1].startDateTime).isEqualTo(ZonedDateTime.of(2022, 12, 16, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[1].endDateTime).isEqualTo(ZonedDateTime.of(2022, 12,  17, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences[2].startDateTime).isEqualTo(ZonedDateTime.of(2023, 12, 15, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[2].endDateTime).isEqualTo(ZonedDateTime.of(2023, 12,  16, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrence of monthly part day event with BYSETPOS happening only once`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 0.29.2//EN
    BEGIN:VEVENT
    DTSTAMP:20220107T095005Z
    RRULE:FREQ=MONTHLY;COUNT=1;BYDAY=MO;BYSETPOS=1
    SEQUENCE:0
    SUMMARY:1623
    STATUS:CONFIRMED
    UID:eybGDM3d4SmguHin5s6THwOEIvbs@proton.me
    DTSTART;VALUE=DATE:20220103
    DTEND;VALUE=DATE:20220104
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15H
    END:VALARM
    BEGIN:VALARM
    ACTION:EMAIL
    TRIGGER;RELATED=START:-PT15H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = ZoneId.systemDefault().id
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, LocalDate.of(2022, 3, 5), null, null)!!

        assertThat(occurrences.size).isEqualTo(1)

        assertThat(occurrences[0].startDateTime).isEqualTo(ZonedDateTime.of(2022, 1, 3, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].endDateTime).isEqualTo(ZonedDateTime.of(2022, 1,  4, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences[0].occurrenceNumber).isEqualTo(1)

    }

    @Test
    fun `generate occurrences of part-day event with BYSETPOS within full-day range, different than system timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20200904T073000
    DTEND;TZID=Europe/Vilnius:20200904T080000
    RRULE:FREQ=MONTHLY;BYDAY=FR;BYSETPOS=1
    UID:rQ2fLVjx407UjHiYF272uJMxBerr@proton.me
    DTSTAMP:20200925T070905Z
    SUMMARY:monthly on first Friday
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2020, 12, 31)

        val occurrences = event.generateOccurrencesUntil(displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(4)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 4, 7, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 9, 4, 8, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(4)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2020, 12, 4, 7, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2020, 12, 4, 8, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of full-day event within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 1)

        val occurrences = event.generateOccurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().occurrenceNumber).isEqualTo(6)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate multiple occurrences of full-day event within full-day range`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeFrom = LocalDate.of(2020, 7, 1)
        val displayRangeTo = LocalDate.of(2020, 7, 5)

        val occurrences = event.generateOccurrencesInFullDayRange(displayRangeFrom, displayRangeTo, displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(5)

        assertThat(occurrences.first().occurrenceNumber).isEqualTo(6)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 2, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(occurrences.last().occurrenceNumber).isEqualTo(10)
        assertThat(occurrences.last().startDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 5, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.last().endDateTime).isEqualTo(ZonedDateTime.of(2020, 7, 6, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate occurrences of part-day event with EXDATES`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T160000
    DTEND;TZID=/Europe/Budapest:20200625T163000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2020, 7, 30)

        event.addExceptionDate(2)
        event.addExceptionDate(3)
        event.addExceptionDate(5)
        event.addExceptionDate(10)
        event.addExceptionDate(40) // non-existing occurrence

        val mapped = ICalUtilsImpl.expandOccurrencesWithSingleEdits(event, arrayListOf(), displayRangeTo, displayTimeZoneId)!!
        val filteredByExdates = mapped.filterOutOccurrencesByExdates(event, displayTimeZoneId)

        assertThat(filteredByExdates.size).isEqualTo(16)
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 2 }).isTrue()
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 3 }).isTrue()
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 5 }).isTrue()
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 10 }).isTrue()
    }

    @Test
    fun `generate occurrences of all-day event with EXDATES`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val displayRangeTo = LocalDate.of(2020, 7, 30)

        event.addExceptionDate(2)
        event.addExceptionDate(3)
        event.addExceptionDate(5)
        event.addExceptionDate(10)
        event.addExceptionDate(40) // non-existing occurrence

        val mapped = ICalUtilsImpl.expandOccurrencesWithSingleEdits(event, arrayListOf(), displayRangeTo, displayTimeZoneId)!!
        val filteredByExdates = mapped.filterOutOccurrencesByExdates(event, displayTimeZoneId)

        assertThat(filteredByExdates.size).isEqualTo(16)
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 2 }).isTrue()
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 3 }).isTrue()
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 5 }).isTrue()
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 10 }).isTrue()
    }

    @Test
    fun `filter out occurrences by RECURRENCE-ID`() {

        val iCals = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200710
    RECURRENCE-ID;VALUE=DATE:20200710
    SUMMARY:full day reccur\, single edit
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200711
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY;UNTIL=20200710
    SUMMARY:full day reccur
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200709
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR    
            """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20200707T190000
    DTEND;TZID=Europe/Vilnius:20200707T193000
    RRULE:FREQ=DAILY;UNTIL=20200711T205959Z
    SUMMARY:part-day\, Vilnius\, every day without stop
    UID:yb2puCFfwOqo40SypyVBBCUsnQ2I@proton.me
    DTSTAMP:20200706T154349Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()
        )

        val events = iCals.mapIndexed { index, iCal ->
            Event.from("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "email",
                "ownerEmail",
                "description",
                "",
                0,
                "addressId",
                "memberId",
                1,
                true,
                0,
                127,
                30,
                emptyList(),
                emptyList()
            ), ICalUtilsImpl.parseICalString(iCal)!!, 0, null)!!
        }

        val filtered = events.filterOccurencesByRecurrenceId()

        assertThat(filtered.size).isEqualTo(2)
        assertThat(filtered.map { it.summary }.containsAll(listOf("full day reccur, single edit", "part-day, Vilnius, every day without stop")))

    }

    @Test
    fun `filter and generate occurrences with single edits`() {

        val iCals = listOf( // original event on 3rd
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200803T120000
    DTEND;TZID=Europe/Zurich:20200803T123000
    RRULE:FREQ=DAILY
    EXDATE;TZID=Europe/Zurich:20200807T120000
    SEQUENCE:0
    SUMMARY:d2
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(), // event on 4th, changed only time
            """
        BEGIN:VCALENDAR
	    VERSION:2.0
	    BEGIN:VEVENT
	    DTSTART;TZID=Europe/Zurich:20200804T133000
	    DTEND;TZID=Europe/Zurich:20200804T140000
	    RECURRENCE-ID;TZID=Europe/Zurich:20200804T120000
	    SEQUENCE:1
	    SUMMARY:d2
	    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
	    DTSTAMP:20200803T150732Z
	    BEGIN:VALARM
	    TRIGGER:-PT15M
	    ACTION:DISPLAY
	    END:VALARM
	    END:VEVENT
	    END:VCALENDAR
            """.trimIndent(), // event on 6th, changed only summary, not time
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200806T120000
    DTEND;TZID=Europe/Zurich:20200806T123000
    RECURRENCE-ID;TZID=Europe/Zurich:20200806T120000
    SEQUENCE:1
    SUMMARY:d2 only summary edit
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(), // event on 8th moved to 9th on different time & edited summary
            """
        BEGIN:VCALENDAR
	    VERSION:2.0
	    BEGIN:VEVENT
	    DTSTART;TZID=Europe/Zurich:20200809T133000
	    DTEND;TZID=Europe/Zurich:20200809T140000
	    RECURRENCE-ID;TZID=Europe/Zurich:20200808T120000
	    SEQUENCE:1
	    SUMMARY:d2 moved from 8th to 9th
	    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
	    DTSTAMP:20200803T150732Z
	    BEGIN:VALARM
	    TRIGGER:-PT15M
	    ACTION:DISPLAY
	    END:VALARM
	    END:VEVENT
	    END:VCALENDAR
            """.trimIndent(), // event on 2nd moved from 3rd by "this" editing original event
            """
            BEGIN:VCALENDAR
		    VERSION:2.0
		    BEGIN:VEVENT
		    DTSTART;TZID=Europe/Zurich:20200802T120000
		    DTEND;TZID=Europe/Zurich:20200802T123000
		    RECURRENCE-ID;TZID=Europe/Zurich:20200803T120000
		    SEQUENCE:1
		    SUMMARY:d2
		    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
		    DTSTAMP:20200803T150732Z
		    BEGIN:VALARM
		    TRIGGER:-PT15M
		    ACTION:DISPLAY
		    END:VALARM
		    END:VEVENT
		    END:VCALENDAR
            """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200809T183000
    DTEND;TZID=Europe/Zurich:20200809T190000
    RECURRENCE-ID;TZID=Europe/Zurich:20200810T120000
    SEQUENCE:2
    SUMMARY:d2 moved from 10th to 9th
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()
        )

        val displayRangeTo = LocalDate.of(2020, 8, 9)
        val displayTimeZoneId = "UTC"

        val events = iCals.mapIndexed { index, iCal ->
            Event.from("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "email",
                "ownerEmail",
                "description",
                "",
                0,
                "addressId",
                "memberId",
                1,
                true,
                0,
                127,
                30,
                emptyList(),
                emptyList()
            ), ICalUtilsImpl.parseICalString(iCal)!!, 0, null)!!
        }

        // 2: 12:00-12:30 [occ 1]
        // 3: nothing visible, it was moved to 2nd
        // 4: 13:30-14:00  [occ 2]
        // 5: 12:00-12:30  [occ 3]
        // 6: 12:00-12:30 edited summary [occ 4]
        // 7: nothing visible because of exdate [occ 5]
        // 8: nothing visible, it was moved to 9th and changed time
        // 9: 12:00-12:30 [occ 7], 13:30-14:00 [occ 6], 18:30-19:00 [occ8]
        // last "ghost occurrence" is on 10th but it was moved to 9th

        val mappedOld = ICalUtilsImpl.expandOccurrencesWithSingleEdits(events.first(), events, displayRangeTo, displayTimeZoneId)!!

        // there are 7 occurrences until 2020-08-09 and one additional that was moved from 2020-08-10 to 2020-08-09
        assertThat(mappedOld.size).isEqualTo(8)

        val filteredByExdates = mappedOld.filterOutOccurrencesByExdates(events.first(), displayTimeZoneId)

        // one of the occurrences should be filtered out by exdate
        assertThat(filteredByExdates.size).isEqualTo(7)
        assertThat(filteredByExdates.find { it.occurrence!!.occurrenceNumber == 5 }).isNull()

        // new approach -- all visible in window spanning all events
        val mappedAll = ICalUtilsImpl.expandOccurrencesWithSingleEdits(events.first(), events, LocalDate.of(2020, 8, 2), displayRangeTo, displayTimeZoneId)!!
        val filteredByExdatesAll = mappedAll.filterOutOccurrencesByExdates(events.first(), displayTimeZoneId)

        assertThat(filteredByExdatesAll.size).isEqualTo(7)
        assertThat(filteredByExdatesAll.find { it.occurrence!!.occurrenceNumber == 5 }).isNull()

        // new approach -- nothing visible in window spanning no events
        val mappedNone = ICalUtilsImpl.expandOccurrencesWithSingleEdits(events.first(), events, LocalDate.of(2020, 8, 7), LocalDate.of(2020, 8, 8), displayTimeZoneId)!!
        val filteredByExdatesMappedNone = mappedNone.filterOutOccurrencesByExdates(events.first(), displayTimeZoneId)

        assertThat(filteredByExdatesMappedNone.size).isEqualTo(0)

        // new approach -- only events from given window visible
        val mappedSingleEditBeforeFirstOccurrence = ICalUtilsImpl.expandOccurrencesWithSingleEdits(events.first(), events, LocalDate.of(2020, 8, 2), LocalDate.of(2020, 8, 2), displayTimeZoneId)!!
        val filteredByExdatesmappedSingleEditBeforeFirstOccurrence = mappedSingleEditBeforeFirstOccurrence.filterOutOccurrencesByExdates(events.first(), displayTimeZoneId)

        assertThat(filteredByExdatesmappedSingleEditBeforeFirstOccurrence.size).isEqualTo(1)
        assertThat(filteredByExdatesmappedSingleEditBeforeFirstOccurrence[0].occurrence!!.occurrenceNumber == 1)

    }

    @Test
    fun `expandOccurrencesWithSingleEdits for an all-day single edit happening one day before original occurrence`() {

        val iCals = listOf( // main chain event starts on 9th, weekly
            """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210909
    DTEND;VALUE=DATE:20210910
    RRULE:FREQ=WEEKLY;BYDAY=TH
    DTSTAMP:20210907T080028Z
    UID:1deh57q4naj5bcd2eljvhr7joj@google.com
    X-MICROSOFT-CDO-OWNERAPPTID:1073665307
    CREATED:20210907T080028Z
    LAST-MODIFIED:20210907T080028Z
    LOCATION:
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Invite from google make SE
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
            """.trimIndent(),
            """
        BEGIN:VCALENDAR
        PRODID:-//Google Inc//Google Calendar 70.9054//EN
        VERSION:2.0
        CALSCALE:GREGORIAN
        METHOD:REQUEST
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20210915
        DTEND;VALUE=DATE:20210916
        DTSTAMP:20210907T091501Z
        UID:1deh57q4naj5bcd2eljvhr7joj@google.com
        X-MICROSOFT-CDO-OWNERAPPTID:868858272
        RECURRENCE-ID;VALUE=DATE:20210916
        CREATED:20210907T080028Z
        LAST-MODIFIED:20210907T091500Z
        LOCATION:
        SEQUENCE:1
        STATUS:CONFIRMED
        SUMMARY:SE: Invite from google make SE 
        TRANSP:TRANSPARENT
        END:VEVENT
        END:VCALENDAR
            """.trimIndent(), // original occurrence on 16th, changed day to 15th
        )

        val displayRangeTo = LocalDate.of(2021, 9, 15)
        val displayTimeZoneId = "Europe/Paris"

        val events = iCals.mapIndexed { index, iCal ->
            Event.from("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "email",
                "ownerEmail",
                "description",
                "",
                0,
                "addressId",
                "memberId",
                1,
                true,
                0,
                127,
                30,
                emptyList(),
                emptyList()
            ), ICalUtilsImpl.parseICalString(iCal)!!, 0, null)!!
        }

        val occurrencesWithSingleEdits = ICalUtilsImpl.expandOccurrencesWithSingleEdits(events.first(), events, LocalDate.of(2021, 9, 15), displayRangeTo, displayTimeZoneId)!!
        assertThat(occurrencesWithSingleEdits.size == 1)
        assertThat(occurrencesWithSingleEdits.first().isSingleEdit())
        assertThat(occurrencesWithSingleEdits.first().summary == "SE: Invite from google make SE")
    }

    @Test
    fun `filter out ex date from recurring event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:UTC
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;COUNT=7
    EXDATE;VALUE=DATE:20201116
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    UID:x6CIBatQ-BJuoKnwWMiJywzj7WPl@proton.me
    DTSTAMP:20201123T143518Z
    DTSTART;VALUE=DATE:20201115
    DTEND;VALUE=DATE:20201115
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayRangeTo = LocalDate.of(2020, 11, 21)
        val displayTimeZoneId = "UTC"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val mapped = ICalUtilsImpl.expandOccurrencesWithSingleEdits(event, arrayListOf(), displayRangeTo, displayTimeZoneId)!!

        val filteredByExdates = mapped.filterOutOccurrencesByExdates(event, displayTimeZoneId)
        assertThat(filteredByExdates.size).isEqualTo(6)
        assertThat(filteredByExdates.none { it.occurrence?.occurrenceNumber == 2 }).isTrue()
    }

    @Test
    fun `generate n-th occurrence of full-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence2 = event.generateOccurrence(2, displayTimeZoneId)
        val occurrence5 = event.generateOccurrence(5, displayTimeZoneId)
        val occurrence20 = event.generateOccurrence(20, displayTimeZoneId)
        val occurrence21 = event.generateOccurrence(21, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 26, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 27, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence2).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 27, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 28, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            2))

        assertThat(occurrence5).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 30, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 1, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            5))

        assertThat(occurrence20).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 7, 15, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 16, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            20))

        assertThat(occurrence21).isNull()

    }

    @Test
    fun `handle EXDATEs in partial-day event`() {

        val iCalEvent = ICalUtilsImpl.createNewVEvent()
        iCalEvent.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Vilnius")
        iCalEvent.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Vilnius")
        iCalEvent.setRecurrenceRule(Recurrence.Builder(Frequency.DAILY).interval(1).count(10).build())

        val calendar = iCalEvent.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Vilnius")
        calendar.setEndTimeZone("Europe/Vilnius")

        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), calendar, 0, null)!!

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0]).isEqualTo(ZonedDateTime.of(2020, 1, 20, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[1]).isEqualTo(ZonedDateTime.of(2020, 1, 22, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[2]).isEqualTo(ZonedDateTime.of(2020, 1, 29, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `handle EXDATEs in full-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200626
    DTEND;VALUE=DATE:20200627
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        TestsLogger.d("exception dates: $exceptionDates")
        TestsLogger.d("cal=${event.iCalendar.printToString()}")

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0].toLocalDate()).isEqualTo(LocalDate.of(2020, 6, 26))

    }

    @Test
    fun `filter out occurrences based on EXDATEs in partial-day event`() {

        val iCalEvent = ICalUtilsImpl.createNewVEvent()
        iCalEvent.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Vilnius")
        iCalEvent.setEnd(LocalDate.of(2020, 1, 20), LocalTime.of(11, 0), "Europe/Vilnius")
        iCalEvent.setRecurrenceRule(Recurrence.Builder(Frequency.DAILY).interval(1).count(10).build())

        val calendar = iCalEvent.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Vilnius")
        calendar.setEndTimeZone("Europe/Vilnius")

        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), calendar, 0, null)!!

        event.addExceptionDate(1)
        event.addExceptionDate(3)
        event.addExceptionDate(10)

        val exceptionDates = event.getExceptionDates()!!

        assertThat(exceptionDates.size).isEqualTo(3)
        assertThat(exceptionDates[0]).isEqualTo(ZonedDateTime.of(2020, 1, 20, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[1]).isEqualTo(ZonedDateTime.of(2020, 1, 22, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))
        assertThat(exceptionDates[2]).isEqualTo(ZonedDateTime.of(2020, 1, 29, 10, 0, 0, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `handle 'delete this and future' for part-day event with COUNT`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Vilnius:20200706T130000
    DTEND;TZID=/Europe/Vilnius:20200706T133000
    RRULE:FREQ=DAILY;COUNT=5
    SUMMARY:part-day every day 5 times\, Vilnius
    UID:b87agM1DYlWT_cNwG1DeMN90qxw5@proton.me
    DTSTAMP:20200706T100055Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.handleDeleteThisAndFuture(4)
        assertThat(event.iCalEvent.recurrenceRule.value.count).isEqualTo(3)

        event.handleDeleteThisAndFuture(2)
        assertThat(event.iCalEvent.recurrenceRule.value.count).isEqualTo(1)

    }

    @Test
    fun `handle 'delete this and future' for part-day event without COUNT & UNTIL`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Vilnius:20200707T190000
    DTEND;TZID=Europe/Vilnius:20200707T193000
    RRULE:FREQ=DAILY
    SUMMARY:part-day\, Vilnius\, every day without stop
    UID:yb2puCFfwOqo40SypyVBBCUsnQ2I@proton.me
    DTSTAMP:20200706T154349Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.handleDeleteThisAndFuture(6)

        // we delete "6th and future occurrences"
        // 6th occurrence happens on 2020-07-12
        // so we should set UNTIL to 1 second before midnight of the previous day

        assertThat(event.iCalEvent.recurrenceRule.value.frequency).isEqualTo(Frequency.DAILY)
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isTrue()
        assertThat(ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.of("Europe/Vilnius"))).isEqualTo(ZonedDateTime.of(2020, 7, 11, 23, 59, 59, 0, ZoneId.of("Europe/Vilnius")))

    }

    @Test
    fun `handle 'delete this and future' for all-day event without COUNT`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY;UNTIL=20200712
    SUMMARY:full day reccur
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200709
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.handleDeleteThisAndFuture(4)

        // we delete "4th and future occurrences"
        // 4th occurrence happens on 2020-07-11
        // so we should set UNTIL to previous day without TIME part

        TestsLogger.d("${ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()}")

        //RRULE:FREQ=DAILY;UNTIL=20200710

        assertThat(event.iCalEvent.recurrenceRule.value.frequency).isEqualTo(Frequency.DAILY)
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isFalse()
        assertThat(ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()).isEqualTo(LocalDate.of(2020, 7, 10))

    }

    @Test
    fun `handle 'delete this and future' for all-day event with UNTIL`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20200708
    RRULE:FREQ=DAILY;UNTIL=20200712
    SUMMARY:full day reccur
    UID:VrLeK2GFu96clzUzTLofDf0_hSDy@proton.me
    DTSTAMP:20200706T161209Z
    DTEND;VALUE=DATE:20200709
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        event.handleDeleteThisAndFuture(4)

        // we delete "4th and future occurrences"
        // 4th occurrence happens on 2020-07-11
        // so we should set UNTIL to previous day without TIME part

        TestsLogger.d("${ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()}")

        //RRULE:FREQ=DAILY;UNTIL=20200710

        assertThat(event.iCalEvent.recurrenceRule.value.frequency).isEqualTo(Frequency.DAILY)
        assertThat(event.iCalEvent.recurrenceRule.value.until.hasTime()).isFalse()
        assertThat(ZonedDateTime.ofInstant(event.iCalEvent.recurrenceRule.value.until.toInstant(), ZoneId.systemDefault()).toLocalDate()).isEqualTo(LocalDate.of(2020, 7, 10))

    }

    @Test
    fun `generate occurrences of partial-day event with UNTIL`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20201001T163000
    DTEND;TZID=Europe/Zurich:20201001T170000
    RRULE:FREQ=DAILY;UNTIL=20201004T215959Z
    UID:wK6IfGtwq4PX1x1qo9WY4dXxtDEU@proton.me
    DTSTAMP:20200928T141234Z
    SUMMARY:recur zh UNTIL Oct 4\, 16:30
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 1st, 2nd, 3rd and 4th in Zurich timezone

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Zurich"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 10)

        assertThat(occurrences!!.size).isEqualTo(4)

    }

    // TODO Fix test and enable it
    @Disabled
    @Test
    fun `generate occurrences of partial-day event with UNTIL, GMT+11`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Antarctica/Macquarie:20201002T020000
    DTEND;TZID=Antarctica/Macquarie:20201002T023000
    RRULE:FREQ=DAILY;UNTIL=20201004T125959Z
    UID:k8EOBVhcHvW73TBNBIbKxxbNf16T@proton.me
    DTSTAMP:20200928T145841Z
    SUMMARY:gmt+11 UNTIL Oct 4
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 1st, 2nd and 3rd in Vilnius timezone

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 10)!!

        assertThat(occurrences.first()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 1, 18, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 1, 18, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrences.last()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 3, 18, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 3, 18, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            3))

    }

    // TODO Fix test and enable it
    @Disabled
    @Test
    fun `generate occurrences of partial-day event with UNTIL, GMT+11, displayed in GMT+12`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Antarctica/Macquarie:20201002T020000
    DTEND;TZID=Antarctica/Macquarie:20201002T023000
    RRULE:FREQ=DAILY;UNTIL=20201004T125959Z
    UID:k8EOBVhcHvW73TBNBIbKxxbNf16T@proton.me
    DTSTAMP:20200928T145841Z
    SUMMARY:gmt+11 UNTIL Oct 4
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 2nd, 3rd and 4th in Pacific/Fiji timezone

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Pacific/Fiji"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 10)!!

        assertThat(occurrences.first()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 2, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 2, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrences.last()).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 4, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 4, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            3))

    }

    @Test
    fun `generate occurrences of all-day event with UNTIL, GMT+2, displayed in GMT+12`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Vilnius
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;UNTIL=20210109
    SEQUENCE:8
    SUMMARY:Daily all-day
    STATUS:CONFIRMED
    DTSTAMP:20201203T172430Z
    UID:nRjwqQ67EeB0AXahfOe-Yohnr-ZY_R20210107T133000@proton.me
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210107
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "UTC+12"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        TestsLogger.d("Timezone : ${ZoneId.systemDefault()}")
        val occurrences = event.generateOccurrences(displayTimeZoneId, LocalDate.of(2021, 1, 9), null, null)!!

        assertThat(occurrences.size).isEqualTo(3)
    }

    @Test
    fun `generate occurrences of all-day event with UNTIL, GMT-3, displayed in GMT-3`() {

        val iCalString = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Proton AG//web-calendar 5.0.43.6//EN
        BEGIN:VTIMEZONE
        TZID:America/Sao_Paulo
        END:VTIMEZONE
        BEGIN:VEVENT
        DTSTAMP:20250217T160959Z
        RRULE:FREQ=DAILY;UNTIL=20250210
        SEQUENCE:1
        SUMMARY:Daily full-day Feb 6 - 10
        UID:wRArtJZq-iG91uqKotDOCkx5wA8k@proton.me
        STATUS:CONFIRMED
        DTSTART;VALUE=DATE:20250206
        DTEND;VALUE=DATE:20250206
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "America/Sao_Paulo"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrencesUntil(LocalDate.of(2025, 2, 20), displayTimeZoneId)

        assertThat(occurrences!!.size).isEqualTo(5)
        occurrences.all {
            it.startDateTime.toLocalTime() == LocalTime.of(0, 0, 0)
        }
    }

    @Test
    fun `generate first occurrence SINCE of all-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Vilnius
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;UNTIL=20210520
    SEQUENCE:8
    STATUS:CONFIRMED
    DTSTAMP:20201203T172430Z
    UID:nRjwqQ67EeB0AXahfOe-Yohnr-ZY_R20210107T133000@proton.me
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210108
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "UTC+12"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val firstOccurrence = event.generateFirstOccurrenceSince(ZonedDateTime.of(LocalDate.of(2021, 1, 21), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))!!

        assertThat(firstOccurrence.occurrenceNumber).isEqualTo(15)
        assertThat(firstOccurrence.startDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2021, 1, 21), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))
        assertThat(firstOccurrence.endDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2021, 1, 22), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))
    }

    @Test
    fun `generate first occurrence SINCE with EXDATE of all-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Vilnius
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;UNTIL=20210520
    SEQUENCE:8
    STATUS:CONFIRMED
    EXDATE;VALUE=DATE:20210121
    EXDATE;VALUE=DATE:20210122
    EXDATE;VALUE=DATE:20210123
    DTSTAMP:20201203T172430Z
    UID:nRjwqQ67EeB0AXahfOe-Yohnr-ZY_R20210107T133000@proton.me
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210108
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "UTC+12"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val firstOccurrence = event.generateFirstRealOccurrenceSince(listOf(event), ZonedDateTime.of(LocalDate.of(2021, 1, 21), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))!!

        TestsLogger.d("${firstOccurrence}")

        assertThat(firstOccurrence.occurrenceNumber).isEqualTo(18)
        assertThat(firstOccurrence.startDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2021, 1, 24), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))
        assertThat(firstOccurrence.endDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2021, 1, 25), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))
    }

    @Test
    fun `generate first occurrence SINCE of part-day event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T100000
    DTEND;TZID=/Europe/Budapest:20200625T103000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Budapest"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val firstOccurrence = event.generateFirstOccurrenceSince(ZonedDateTime.of(LocalDate.of(2020, 7, 4), LocalTime.of(10, 0, 0), ZoneId.of(displayTimeZoneId)))!!

        assertThat(firstOccurrence.occurrenceNumber).isEqualTo(10)
        assertThat(firstOccurrence.startDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2020, 7, 4), LocalTime.of(10, 0, 0), ZoneId.of(displayTimeZoneId)))

        val firstOccurrenceVilnius = event.generateFirstOccurrenceSince(ZonedDateTime.of(LocalDate.of(2020, 7, 4), LocalTime.of(10, 0, 0), ZoneId.of("Europe/Vilnius")))!!

        assertThat(firstOccurrenceVilnius.occurrenceNumber).isEqualTo(10)
        assertThat(firstOccurrenceVilnius.startDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2020, 7, 4), LocalTime.of(11, 0, 0), ZoneId.of("Europe/Vilnius")))
    }

    @Test
    fun `generate occurrences of partial-day event with UNTIL, GMT-11`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Pacific/Pago_Pago:20201001T040000
    DTEND;TZID=Pacific/Pago_Pago:20201001T043000
    RRULE:FREQ=DAILY;UNTIL=20201005T105959Z
    UID:VU06MoYMt_mNWMlWuN5cfRuC3ZAl@proton.me
    DTSTAMP:20200928T145535Z
    SUMMARY:gmt-11 recur daily UNTIL Oct 4\, 8:30
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // this should be happening on 1st, 2nd, 3rd and 4th in Vilnius timezone

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 10)

        assertThat(occurrences!!.size).isEqualTo(4)

    }

    @Test
    fun `generate occurrences of all-day event spanning three weeks and over dst, display tz different from device tz`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Vilnius
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=MONTHLY;INTERVAL=4;BYDAY=18
    SEQUENCE:8
    STATUS:CONFIRMED
    DTSTAMP:20201203T172430Z
    UID:nRjwqQ67EeB0AXahfOe-Yohnr-ZY_R20210107T133000@proton.me
    DTSTART;VALUE=DATE:20230318
    DTEND;VALUE=DATE:20230407
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        // To properly test this, device running the test needs to be over UTC (ex: Europe/Zurich)

        val iCal = parseICalString(iCalString)!!
        val displayTimeZoneId = "America/Cayenne" // Tested with: UTC, America/Santiago, America/Cayenne
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 1)

        assertThat(occurrences!!.size).isEqualTo(1)
        assertThat(occurrences.first().startDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2023, 3, 18), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))
        assertThat(occurrences.first().endDateTime).isEqualTo(ZonedDateTime.of(LocalDate.of(2023, 4, 7), LocalTime.MIDNIGHT, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `generate n-th occurrence of partial-day weekly event, DST happening during event, display in different timezone that has no DST`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 1.0.3//EN
    BEGIN:VEVENT
    DTSTAMP:20220407T075929Z
    RRULE:FREQ=WEEKLY;BYDAY=SU
    SEQUENCE:0
    SUMMARY:Weekly on Sundays during DST change (with duration)
    TRANSP:OPAQUE
    UID:0qv22ol3j4ici7epqp11n7q78d2@google.com
    CREATED:20220407T075928Z
    LAST-MODIFIED:20220407T075928Z
    DTSTART;TZID=Europe/Vilnius:20220306T010000
    DTEND;TZID=Europe/Vilnius:20220306T110000
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Africa/Tripoli"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId) // Before DST
        val occurrence4 = event.generateOccurrence(4, displayTimeZoneId) // During DST
        val occurrence6 = event.generateOccurrence(6, displayTimeZoneId) // After DST

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 3, 6, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 3, 6, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence4).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 3, 27, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            4))

        assertThat(occurrence6).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 4, 10, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 4, 10, 10, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            6))

    }

    @Test
    fun `generate n-th occurrence of partial-day weekly event, DST happening during event`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton AG//AndroidCalendar 1.0.3//EN
    BEGIN:VEVENT
    DTSTAMP:20220407T075929Z
    RRULE:FREQ=WEEKLY;BYDAY=SU
    SEQUENCE:0
    SUMMARY:Weekly on Sundays during DST change (with duration)
    TRANSP:OPAQUE
    UID:0qv22ol3j4ici7epqp11n7q78d2@google.com
    CREATED:20220407T075928Z
    LAST-MODIFIED:20220407T075928Z
    DTSTART;TZID=Europe/Vilnius:20220306T010000
    DTEND;TZID=Europe/Vilnius:20220306T110000
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId) // Before DST (Winter time)
        val occurrence4 = event.generateOccurrence(4, displayTimeZoneId) // During DST
        val occurrence6 = event.generateOccurrence(6, displayTimeZoneId) // After DST (Summer time)
        val occurrence35 = event.generateOccurrence(35, displayTimeZoneId) // During DST
        val occurrence37 = event.generateOccurrence(37, displayTimeZoneId) // After DST (Winter time)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 3, 6, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 3, 6, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence4).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 3, 27, 12, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            4))

        assertThat(occurrence6).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 4, 10, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 4, 10, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            6))

        assertThat(occurrence35).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 10, 30, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 10, 30, 10, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            35))

        assertThat(occurrence37).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2022, 11, 13, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2022, 11, 13, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            37))

    }

    @Test
    fun `generate n-th occurrence of partial-day event, display in different timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=/Europe/Budapest:20200625T100000
    DTEND;TZID=/Europe/Budapest:20200625T103000
    RRULE:FREQ=DAILY;COUNT=20
    SUMMARY:recurring every day 20 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence5 = event.generateOccurrence(5, displayTimeZoneId)
        val occurrence21 = event.generateOccurrence(21, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 25, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 25, 11, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence5).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 6, 29, 11, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 6, 29, 11, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            5))

        assertThat(occurrence21).isNull()

    }

    @Test
    fun `generate n-th occurrence of partial-day recurring event with byday, display in a timezone that make it happen the next day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=America/Noronha:20201004T230000
    DTEND;TZID=America/Noronha:20201004T233000
    RRULE:FREQ=WEEKLY;BYDAY=SU
    SEQUENCE:1
    SUMMARY:Test event
    STATUS:CONFIRMED
    DTSTAMP:20201029T152822Z
    UID:ph5aTJJKGwKURIwnXh9CN0jW4isD@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT5H
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence3 = event.generateOccurrence(3, displayTimeZoneId)
        val occurrence4 = event.generateOccurrence(4, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 5, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 5, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence3).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 19, 3, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 19, 3, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            3))

        assertThat(occurrence4).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 10, 26, 2, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 10, 26, 2, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            4))
    }

    @Test
    fun `generate n-th occurrence of partial-day recurring event, display in a timezone that make it happen the next day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.19-prod.1//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Different timezone
    STATUS:CONFIRMED
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4
    DTSTART;TZID=Pacific/Niue:20210622T110000
    DTEND;TZID=Pacific/Niue:20210622T113000
    UID:aRpVeZ2WB-NBHPO_LLykafrJIYKr@proton.me
    SEQUENCE:0
    DTSTAMP:20210622T125255Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence2 = event.generateOccurrence(2, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2021, 6, 23, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2021, 6, 23, 0, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence2).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2021, 7, 28, 0, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2021, 7, 28, 0, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            2))
    }

    @Test
    fun `generate n-th occurrence of partial-day recurring event, display in a timezone that make it happen the previous day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.19-prod.1//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Different timezone
    STATUS:CONFIRMED
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4
    DTSTART;TZID=Asia/Anadyr:20210622T050000
    DTEND;TZID=Asia/Anadyr:20210622T053000
    UID:aRpVeZ2WB-NBHPO_LLykafrJIYKr@proton.me
    SEQUENCE:0
    DTSTAMP:20210622T125255Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Paris"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence2 = event.generateOccurrence(2, displayTimeZoneId)

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2021, 6, 21, 19, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2021, 6, 21, 19, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence2).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2021, 7, 26, 19, 0, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2021, 7, 26, 19, 30, 0, 0, ZoneId.of(displayTimeZoneId)),
            2))
    }

    @Test
    fun `generate n-th occurrence of partial-day multi-day event with BYDAY, display in different timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200708T154500
    DTEND;TZID=Europe/Zurich:20200709T161500
    RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=WE
    SUMMARY:recurring every week 5 times
    UID:EGVy407XddW2_ESpoOVn7oN1qc9V@proton.me
    DTSTAMP:20200625T143822Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrence1 = event.generateOccurrence(1, displayTimeZoneId)
        val occurrence2 = event.generateOccurrence(2, displayTimeZoneId)

        TestsLogger.d("$occurrence1")
        TestsLogger.d("$occurrence2")

        assertThat(occurrence1).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 7, 8, 16, 45, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 9, 17, 15, 0, 0, ZoneId.of(displayTimeZoneId)),
            1))

        assertThat(occurrence2).isEqualTo(Event.Occurrence(
            ZonedDateTime.of(2020, 7, 15, 16, 45, 0, 0, ZoneId.of(displayTimeZoneId)),
            ZonedDateTime.of(2020, 7, 16, 17, 15, 0, 0, ZoneId.of(displayTimeZoneId)),
            2))

    }

    @Test
    fun `generate entire Event model with datetime overwritten by n-th occurrence`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Budapest:20200708T140000
    DTEND;TZID=Europe/Budapest:20200709T143000
    RRULE:FREQ=WEEKLY
    SUMMARY:weekly\, 2-day part-time
    UID:iUsRIL4N3Wq6LN9MgvYdV42m2FIY@proton.me
    DTSTAMP:20200716T113118Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val eventWithOccurrence1 = Event.withOccurrence(event, 1, displayTimeZoneId)!!
        val eventWithOccurrence2 = Event.withOccurrence(event, 2, displayTimeZoneId)!!
        val eventWithOccurrence5 = Event.withOccurrence(event, 5, displayTimeZoneId)!!

        assertThat(event.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 8, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(event.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 9, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(eventWithOccurrence1.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 8, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence1.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 9, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence1.occurrence!!.occurrenceNumber).isEqualTo(1)

        assertThat(eventWithOccurrence2.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 15, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence2.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 7, 16, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence2.occurrence!!.occurrenceNumber).isEqualTo(2)

        assertThat(eventWithOccurrence5.getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 8, 5, 15, 0, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence5.getEnd(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2020, 8, 6, 15, 30, 0, 0, ZoneId.of(displayTimeZoneId)))
        assertThat(eventWithOccurrence5.occurrence!!.occurrenceNumber).isEqualTo(5)

    }

    @Test
    fun `correctly sanitise partial-day Event without DTEND`() {

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20), LocalTime.of(10, 0), "Europe/Zurich")

        assertThat(event.sanitise()).isTrue()

        assertThat(event.dateStart.value.hasTime()).isTrue()
        assertThat(event.dateEnd.value.hasTime()).isTrue()

        assertThat(event.getEnd("Europe/Zurich")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 20))
        assertThat(event.getEnd("Europe/Zurich")!!.toLocalTime()).isEqualTo(LocalTime.of(10, 0))

    }

    @Test
    fun `correctly sanitise all-day Event without DTEND`() {

        val event = ICalUtilsImpl.createNewVEvent()
        event.setStart(LocalDate.of(2020, 1, 20))

        assertThat(event.sanitise()).isTrue()

        assertThat(event.dateStart.value).isNotNull()
        assertThat(event.dateEnd.value).isNotNull()

        assertThat(event.getEnd("Europe/Zurich")!!.toLocalDate()).isEqualTo(LocalDate.of(2020, 1, 21))

    }

    @Test
    fun `clone iCalendar created from ical string`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.2.3//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200728T130000
    DTEND;TZID=Europe/Zurich:20200728T133000
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=-1
    SEQUENCE:0
    SUMMARY:monthly on last Tuesday
    STATUS:CONFIRMED
    DTSTAMP:20200728T103442Z
    UID:TaDoa1gh-CVheCqJbaIc86Up96cX@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT30M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val original = ICalUtilsImpl.parseICalString(iCalString)!!
        val cloned = original.clone()

        original.events.first().summary.value = "edited summary of original event"

        assertThat(cloned.events.first().summary.value).isEqualTo("monthly on last Tuesday")
        assertThat(cloned.events.first().alarms.size).isEqualTo(3)
        assertThat(cloned.timezoneInfo.getTimezone(cloned.events.first().dateStart).globalId).isEqualTo("Europe/Zurich")
        assertThat(cloned.events.first().dateStart.value.toInstant()).isEqualTo(ZonedDateTime.of(2020, 7, 28, 13, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant())

    }

    @Test
    fun `clone Event and modify the original afterwards`() {

        val eventTimeZoneId = "Europe/Zurich"

        val newICalendar = createNewVEvent().wrapInICalendar()
        val newVEvent = newICalendar.events.first()

        newVEvent.setStart(LocalDate.of(2021, 5, 18), LocalTime.of(18, 0), eventTimeZoneId)
        newICalendar.setStartTimeZone(eventTimeZoneId)
        newVEvent.setEnd(LocalDate.of(2021, 5, 18), LocalTime.of(19, 0), eventTimeZoneId)
        newICalendar.setEndTimeZone(eventTimeZoneId)
        newICalendar.setDefaultTimeZone(eventTimeZoneId)

        val event = Event.from("id", Calendar("", "", "", "", "", "",0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), newICalendar, 0)!!
        val eventCopy = Event.from(event)

        event.iCalEvent.setStart(LocalDate.of(2021, 1, 1), LocalTime.of(18, 0), "Europe/Vilnius")
        event.iCalEvent.setEnd(LocalDate.of(2021, 1, 1), LocalTime.of(19, 0), "Europe/Vilnius")

        assertThat(event.getStart("Europe/Zurich").toInstant()).isEqualTo(ZonedDateTime.of(2021, 1, 1, 18, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant())
        assertThat(event.getEnd("Europe/Zurich").toInstant()).isEqualTo(ZonedDateTime.of(2021, 1, 1, 19, 0, 0, 0, ZoneId.of("Europe/Vilnius")).toInstant())

        assertThat(eventCopy.getStart("Europe/Zurich").toInstant()).isEqualTo(ZonedDateTime.of(2021, 5, 18, 18, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant())
        assertThat(eventCopy.getEnd("Europe/Zurich").toInstant()).isEqualTo(ZonedDateTime.of(2021, 5, 18, 19, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant())
        assertThat(eventCopy.defaultTimeZone).isEqualTo("Europe/Zurich")
    }

    @Test
    fun `clone Event and check if timezone assignments were copied`() {

        val eventTimeZoneId = "Europe/Zurich"

        // RecurrenceID added for testing
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20200803T120000
    DTEND;TZID=Europe/Zurich:20200803T123000
    RRULE:FREQ=DAILY
    EXDATE;TZID=Europe/Vilnius:20200807T120000
    EXDATE;TZID=Europe/Vilnius:20200808T120000
    SEQUENCE:0
    RECURRENCE-ID;TZID=Europe/Berlin:20200809T120000
    UID:ahaBeeTYIPTisogZGV7ASf1htS0T@proton.me
    DTSTAMP:20200803T150732Z
    END:VEVENT
    END:VCALENDAR
        """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val eventCopy = Event.from(event)

        assertThat(eventCopy.getStart(eventTimeZoneId).toInstant()).isEqualTo(ZonedDateTime.of(2020, 8, 3, 12, 0, 0, 0, ZoneId.of(eventTimeZoneId)).toInstant())
        assertThat(eventCopy.iCalendar.timezoneInfo.getTimezone(eventCopy.iCalEvent.dateStart)?.timeZone?.id).isEqualTo(eventTimeZoneId)

        assertThat(eventCopy.getEnd(eventTimeZoneId).toInstant()).isEqualTo(ZonedDateTime.of(2020, 8, 3, 12, 30, 0, 0, ZoneId.of(eventTimeZoneId)).toInstant())
        assertThat(eventCopy.iCalendar.timezoneInfo.getTimezone(eventCopy.iCalEvent.dateEnd)?.timeZone?.id).isEqualTo(eventTimeZoneId)

        assertThat(eventCopy.defaultTimeZone).isNull()

        assertThat(eventCopy.iCalEvent.exceptionDates.size).isEqualTo(2)

        val exceptionICalDates = eventCopy.iCalEvent.exceptionDates.flatMap { it.values }
        assertThat(exceptionICalDates.find {
            it.toInstant() == ZonedDateTime.of(LocalDate.of(2020, 8, 7), LocalTime.of(12, 0, 0), ZoneId.of("Europe/Vilnius")).toInstant()
        }).isNotNull()
        assertThat(exceptionICalDates.find {
            it.toInstant() == ZonedDateTime.of(LocalDate.of(2020, 8, 8), LocalTime.of(12, 0, 0), ZoneId.of("Europe/Vilnius")).toInstant()
        }).isNotNull()

        assertThat(eventCopy.iCalendar.timezoneInfo.getTimezone(eventCopy.iCalEvent.exceptionDates[0])?.timeZone?.id).isEqualTo("Europe/Vilnius")
        assertThat(eventCopy.iCalendar.timezoneInfo.getTimezone(eventCopy.iCalEvent.exceptionDates[1])?.timeZone?.id).isEqualTo("Europe/Vilnius")

        assertThat(eventCopy.iCalendar.timezoneInfo.getTimezone(eventCopy.iCalEvent.recurrenceId)?.timeZone?.id).isEqualTo("Europe/Berlin")
    }

    @Test
    fun `recurring event ends after one occurrence`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTAMP:20201014T164542Z
    UID:MPguUggfUmij1uQgo5SHDWa0I492@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210103T190000
    DTEND;TZID=Europe/Paris:20210103T193000
    RRULE:FREQ=DAILY;COUNT=1
    SUMMARY:Single
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends after two occurrence but has one ex date`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201202T100000
    DTEND;TZID=UTC:20201202T103000
    RRULE:FREQ=DAILY;COUNT=2
    SEQUENCE:1
    EXDATE;TZID=UTC:20201203T100000
    STATUS:CONFIRMED
    DTSTAMP:20201222T095645Z
    UID:ZnMrGMJfKHacVbleN1Js9L9Q2vrR@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends after three occurrence and has duplicated ex date`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201202T100000
    DTEND;TZID=UTC:20201202T103000
    RRULE:FREQ=DAILY;COUNT=3
    SEQUENCE:1
    EXDATE;TZID=UTC:20201203T100000
    EXDATE;TZID=UTC:20201203T100000
    STATUS:CONFIRMED
    DTSTAMP:20201222T095645Z
    UID:ZnMrGMJfKHacVbleN1Js9L9Q2vrR@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isFalse()
    }

    @Test
    fun `recurring event ends after two occurrences`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTAMP:20201014T164542Z
    UID:MPguUggfUmij1uQgo5SHDWa0I492@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210103T190000
    DTEND;TZID=Europe/Paris:20210103T193000
    RRULE:FREQ=DAILY;COUNT=2
    SUMMARY:Single
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isFalse()
    }

    @Test
    fun `recurring event ends on the same day`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTAMP:20201014T164542Z
    UID:MPguUggfUmij1uQgo5SHDWa0I492@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210103T190000
    DTEND;TZID=Europe/Paris:20210103T193000
    RRULE:FREQ=DAILY;UNTIL=20210103T225959Z
    SUMMARY:Single
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends on the next day`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    SEQUENCE:1
    DTSTAMP:20201014T170339Z
    UID:oT8hagmb4v0xNIo8xC0Fk8LLllsN@proton.me
    STATUS:CONFIRMED
    RRULE:FREQ=DAILY;UNTIL=20210104T225959Z
    SUMMARY:Double
    DTEND;TZID=Europe/Paris:20210103T190000
    DTSTART;TZID=Europe/Paris:20210103T183000
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isFalse()
    }

    @Test
    fun `recurring event ends on the next day but has an ex date`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201202T100000
    DTEND;TZID=UTC:20201202T103000
    RRULE:FREQ=DAILY;UNTIL=20201203T235959Z
    SEQUENCE:1
    EXDATE;TZID=UTC:20201203T100000
    STATUS:CONFIRMED
    DTSTAMP:20201222T095843Z
    UID:SHxct-Ln7syB1-L2ORzMMN_91EKj@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends in two days and has a duplicated ex date`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201202T100000
    DTEND;TZID=UTC:20201202T103000
    RRULE:FREQ=DAILY;UNTIL=20201204T235959Z
    SEQUENCE:1
    EXDATE;TZID=UTC:20201203T100000
    EXDATE;TZID=UTC:20201203T100000
    STATUS:CONFIRMED
    DTSTAMP:20201222T095843Z
    UID:SHxct-Ln7syB1-L2ORzMMN_91EKj@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isFalse()
    }

    @Test
    fun `recurring event ends before next occurrence`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.13.0//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Vilnius
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=WEEKLY;UNTIL=20201117;BYDAY=SU
    SEQUENCE:2
    SUMMARY:Test rec
    STATUS:CONFIRMED
    DTSTAMP:20201111T165659Z
    UID:ebhr8v5Lu0B46spPaRXs_sGC1Y9I@proton.me
    DTSTART;VALUE=DATE:20201115
    DTEND;VALUE=DATE:20201115
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isTrue()
    }

    @Test
    fun `recurring event ends after next occurrence`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.13.0//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Vilnius
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=WEEKLY;UNTIL=20201123;BYDAY=SU
    SEQUENCE:3
    SUMMARY:Test rec
    STATUS:CONFIRMED
    DTSTAMP:20201111T170348Z
    UID:ebhr8v5Lu0B46spPaRXs_sGC1Y9I@proton.me
    DTSTART;VALUE=DATE:20201115
    DTEND;VALUE=DATE:20201115
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isSingleOccurrenceRecurring(timeZoneId)).isFalse()
    }

    @Test
    fun `check partial day single edit recurrence id date when original event was all day`() {
        val iCals = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201104
    DTEND;VALUE=DATE:20201105
    RRULE:FREQ=WEEKLY;COUNT=2;BYDAY=WE
    SEQUENCE:2
    SUMMARY:Weekly recurring
    STATUS:CONFIRMED
    DTSTAMP:20201105T135134Z
    UID:c2vDbiFQVRp847bbZRNQKLeZ6pK7@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20201104T135100
    DTEND;TZID=Europe/Paris:20201104T142100
    RECURRENCE-ID;VALUE=DATE:20201104
    SEQUENCE:3
    SUMMARY:Weekly recurring
    STATUS:CONFIRMED
    DTSTAMP:20201105T135151Z
    UID:c2vDbiFQVRp847bbZRNQKLeZ6pK7@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent())

        val displayTimeZoneId = "Europe/Paris"

        val events = iCals.mapIndexed { index, iCal ->
            Event.from("eventId-${index}", me.proton.android.calendar.domain.model.Calendar(
                "id",
                "calendar",
                "email",
                "ownerEmail",
                "description",
                "",
                0,
                "addressId",
                "memberId",
                1,
                true,
                0,
                127,
                30,
                emptyList(),
                emptyList()
            ), ICalUtilsImpl.parseICalString(iCal)!!, 0, null)!!
        }

        val displayRangeTo = LocalDate.of(2020, 12, 31)

        val originalEvent = events.first()!!
        val singleEdit = events[1]
        val mapped = ICalUtilsImpl.expandOccurrencesWithSingleEdits(originalEvent, events, displayRangeTo, displayTimeZoneId)!!

        assertThat(eventStartZonedDateTimeToDate(originalEvent.iCalEvent.getStart(displayTimeZoneId)!!, originalEvent.isAllDay())).isEqualTo(eventStartZonedDateTimeToDate(singleEdit.iCalEvent.getStart(displayTimeZoneId)!!, originalEvent.isAllDay()))
        assertThat(mapped[0].iCalEvent.getStart(displayTimeZoneId)).isEqualTo(singleEdit.iCalEvent.getStart(displayTimeZoneId))
    }

    @Test
    fun `reproduce event form all day event date formatted`() {
        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:UTC
    END:VTIMEZONE
    BEGIN:VEVENT
    SEQUENCE:0
    SUMMARY:All day
    UID:GAqLNyLWPaEIHKd-QYdNTUWlsxEZ@proton.me
    DTSTAMP:20201111T123938Z
    DTSTART;VALUE=DATE:20201117
    DTEND;VALUE=DATE:20201117
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val timeZoneId = "UTC"
        // Should return "Tuesday, November 17, 2020" if in English so we just check if day was calculated right
        val formattedStart = event.formatDateOrDateTimeProperty(event.iCalEvent.dateStart, timeZoneId, true, event.isAllDay())
        assertThat(formattedStart.first).isNotNull()
        assertThat(formattedStart.first!!.contains("17")).isTrue()
        assertThat(formattedStart.second).isNull()
    }

    @Test
    fun `transform between biweekly and java-time DayOfWeek`() {

        assertThat(biweekly.util.DayOfWeek.MONDAY.toDayOfWeek()).isEqualTo(java.time.DayOfWeek.MONDAY)
        assertThat(biweekly.util.DayOfWeek.TUESDAY.toDayOfWeek()).isEqualTo(java.time.DayOfWeek.TUESDAY)
        assertThat(biweekly.util.DayOfWeek.SATURDAY.toDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY)
        assertThat(biweekly.util.DayOfWeek.SUNDAY.toDayOfWeek()).isEqualTo(java.time.DayOfWeek.SUNDAY)

        assertThat(java.time.DayOfWeek.MONDAY.toBiweeklyDayOfWeek()).isEqualTo(biweekly.util.DayOfWeek.MONDAY)
        assertThat(java.time.DayOfWeek.TUESDAY.toBiweeklyDayOfWeek()).isEqualTo(biweekly.util.DayOfWeek.TUESDAY)
        assertThat(java.time.DayOfWeek.SATURDAY.toBiweeklyDayOfWeek()).isEqualTo(biweekly.util.DayOfWeek.SATURDAY)
        assertThat(java.time.DayOfWeek.SUNDAY.toBiweeklyDayOfWeek()).isEqualTo(biweekly.util.DayOfWeek.SUNDAY)

    }

    @Test
    fun `UID formatting`() {
        val normalUid = "7BtoMJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000@proton.me"
        assertThat(formatUidForICal(normalUid)).isEqualTo("7BtoMJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000@proton.me")

        val tooLongUid = "7BtoMjJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000@proton.me"
        assertThat(formatUidForICal(tooLongUid)).isEqualTo("7BtoMjJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000@proton.m\\r\\n e")

        val wayTooLongUid = "7BtoMjJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000_R20201208T000000_R20201208T000000_R20201208T000000_R20201208T000000_R20201208T000000@proton.me"
        assertThat(formatUidForICal(wayTooLongUid)).isEqualTo("7BtoMjJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000_R2020120\\r\\n 8T000000_R20201208T000000_R20201208T000000_R20201208T000000_R20201208T0000\\r\\n 00@proton.me")

        val alsoTooLongUid = "proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000_R20201207T163000@proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000"
        assertThat(formatUidForICal(alsoTooLongUid)).isEqualTo("proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000_R\\r\\n 20201207T163000@proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R2020\\r\\n 0905T163000")

        val exactLineLengthLimit = "proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000_R20201207T163000@proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R2020"
        assertThat(formatUidForICal(exactLineLengthLimit)).isEqualTo("proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000_R\\r\\n 20201207T163000@proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R2020")

        val hellishUid = "Lorizzle crazy check it out sizzle amizzle\\, check it out adipiscing elit. Nullam sapien velizzle\\, fizzle volutpizzle\\, sparkle funky fresh\\, its fo rizzle vizzle\\, arcu. Pellentesque eget tortor. Uhuh ... yih! erizzle. Fusce at owned dapibus turpis tempizzle sizzle. Mauris pellentesque nibh and crunk. Things bow wow wow tortizzle. Pellentesque eleifend crunk stuff. In gizzle habitasse rizzle dictumst. Ass dapibizzle. Away tellizzle away\\, its fo rizzle eu\\, mattizzle ac\\, eleifend break yo neck\\, yall\\, nunc. Mofo aroused. Integer boom shackalack velit boom shackalack i saw beyonces tizzles and my pizzle went crizzle."
        assertThat(formatUidForICal(hellishUid)).isEqualTo("Lorizzle crazy check it out sizzle amizzle\\, check it out adipiscing el\\r\\n it. Nullam sapien velizzle\\, fizzle volutpizzle\\, sparkle funky fresh\\, it\\r\\n s fo rizzle vizzle\\, arcu. Pellentesque eget tortor. Uhuh ... yih! erizzle\\r\\n . Fusce at owned dapibus turpis tempizzle sizzle. Mauris pellentesque nibh\\r\\n  and crunk. Things bow wow wow tortizzle. Pellentesque eleifend crunk stuf\\r\\n f. In gizzle habitasse rizzle dictumst. Ass dapibizzle. Away tellizzle awa\\r\\n y\\, its fo rizzle eu\\, mattizzle ac\\, eleifend break yo neck\\, yall\\, nunc\\r\\n . Mofo aroused. Integer boom shackalack velit boom shackalack i saw beyonc\\r\\n es tizzles and my pizzle went crizzle.")

        val uidWithDates = "7BtoMjJp56XF_1KHDP-4T1t5LuyS_R20201207T000000_R20201208T000000@proton.me"
        assertThat(generateProtonUid(uidWithDates, "20201209T193000")).isEqualTo("7BtoMjJp56XF_1KHDP-4T1t5LuyS_R20201209T193000@proton.me")

        val oldUid = "proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e"
        assertThat(generateProtonUid(oldUid, "20201209T193000")).isEqualTo("proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20201209T193000")

        // We just remove all _R{recurrenceId} and still keep everything after the last @ delimiter
        val badUid = "proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000_R20201207T163000@proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20200905T163000"
        assertThat(generateProtonUid(badUid, "20201209T193000")).isEqualTo("proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e_R20201209T193000@proton-calendar-350095ea-4368-26f0-4fc9-60a56015b02e")

        val noDateUid = "7u13-5hSesk_5rif9dkj3KHgtGAB@proton.me"
        assertThat(generateProtonUid(noDateUid, "20201209T193000")).isEqualTo("7u13-5hSesk_5rif9dkj3KHgtGAB_R20201209T193000@proton.me")

        val badCharacterUid = "7u13-5hSesk_5rif9dk@j3KHgtGAB@proton.me"
        assertThat(generateProtonUid(badCharacterUid, "20201209T193000")).isEqualTo("7u13-5hSesk_5rif9dk@j3KHgtGAB_R20201209T193000@proton.me")

        val importedUid = "7u13-5hSesk_5rif9dkj3KHgtGAB@google.com"
        assertThat(generateProtonUid(importedUid, "20201209T193000")).isEqualTo("7u13-5hSesk_5rif9dkj3KHgtGAB_R20201209T193000@google.com")
    }

    @Test
    fun `check if selected partial day event is first occurence when original event has been deleted`() {
        val originalEventICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:UTC
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY
    SEQUENCE:1
    EXDATE;TZID=UTC:20201211T103000
    SUMMARY:Xx
    STATUS:CONFIRMED
    DTSTAMP:20201222T100238Z
    UID:3BRpEzVDWGs3oG28rvAVMNU4GNYR@proton.me
    DTSTART;TZID=UTC:20201211T103000
    DTEND;TZID=UTC:20201211T110000
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:UTC
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY
    SEQUENCE:1
    EXDATE;TZID=UTC:20201211T103000
    SUMMARY:Xx
    STATUS:CONFIRMED
    DTSTAMP:20201222T100238Z
    UID:3BRpEzVDWGs3oG28rvAVMNU4GNYR@proton.me
    DTSTART;TZID=UTC:20201212T103000
    DTEND;TZID=UTC:20201212T110000
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val originalEventICal = ICalUtilsImpl.parseICalString(originalEventICalString)!!
        val originalEvent = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), originalEventICal, 0, null)!!

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isEventFirstOccurrence(originalEvent, timeZoneId)).isTrue()
    }

    @Test
    fun `check if selected all day event is first occurence when original event has been deleted`() {
        val originalEventICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;UNTIL=20201111
    SEQUENCE:1
    EXDATE;VALUE=DATE:20201108
    SUMMARY:Xx
    STATUS:CONFIRMED
    DTSTAMP:20201228T100331Z
    UID:T7v3-cIgOks12IJix_3zis93zu5f@proton.me
    DTSTART;VALUE=DATE:20201108
    DTEND;VALUE=DATE:20201108
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;UNTIL=20201111
    SEQUENCE:1
    EXDATE;VALUE=DATE:20201108
    STATUS:CONFIRMED
    DTSTAMP:20201228T100331Z
    UID:T7v3-cIgOks12IJix_3zis93zu5f@proton.me
    DTSTART;VALUE=DATE:20201109
    DTEND;VALUE=DATE:20201109
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"

        val originalEventICal = ICalUtilsImpl.parseICalString(originalEventICalString)!!
        val originalEvent = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), originalEventICal, 0, null)!!

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            0,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        assertThat(event.isEventFirstOccurrence(originalEvent, timeZoneId)).isTrue()
    }

    @Test
    fun `extract email`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20210105T120000
    DTEND;TZID=Europe/Zurich:20210105T123000
    ORGANIZER;CN=adamtst@protonmail.com:mailto:adamtst@protonmail.com
    SEQUENCE:0
    SUMMARY:Inviting BLT from adamtst
    STATUS:CONFIRMED
    DTSTAMP:20210105T101420Z
    UID:GOmbzP5Ok3Uo7QYgYyb7LCCijGzS@proton.me
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:james@example.com
    ATTENDEE;CN=james@pm.me;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james2@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=James;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;EMAIL=AmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=IAmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val eventIcal = ICalUtilsImpl.parseICalString(iCalString)!!
        val attendees = eventIcal.events.first().attendees

        assertThat(attendees.size).isEqualTo(10)

        for (i in 0..8) {
            assertThat(attendees[i].extractEmail()).isEqualTo("james@example.com")
        }

        assertThat(attendees[9].extractEmail()).isNull()

        assertThat(eventIcal.events.first().organizer.extractEmail()).isEqualTo("adamtst@protonmail.com")

    }

    @Test
    fun `getResponseIcs test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20210105T120000
    DTEND;TZID=Europe/Zurich:20210105T123000
    ORGANIZER;CN=adamtst@protonmail.com:mailto:adamtst@protonmail.com
    SEQUENCE:0
    SUMMARY:Inviting BLT from adamtst
    STATUS:CONFIRMED
    DTSTAMP:20210105T101420Z
    UID:GOmbzP5Ok3Uo7QYgYyb7LCCijGzS@proton.me
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:james@example.com
    ATTENDEE;CN=james@pm.me;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james2@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=James;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;EMAIL=AmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=IAmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val eventIcal = ICalUtilsImpl.parseICalString(iCalString)!!

        val ics = getResponseIcs(eventIcal, eventIcal.events.first().attendees.first(), ParticipationStatus.ACCEPTED, null, Date.from(Instant.now()), false)

        val responseICalendar = ICalUtilsImpl.parseICalString(ics)!!

        assertThat(responseICalendar.productId.value).isEqualTo(generateProtonProdId())
        assertThat(responseICalendar.version).isEqualTo(ICalVersion.V2_0)
        assertThat(responseICalendar.method.value).isEqualTo(Method.REPLY)
        assertThat(responseICalendar.calendarScale.value).isEqualTo(CalendarScale.GREGORIAN)
        assertThat(responseICalendar.events.first().alarms.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().attendees.size).isEqualTo(1)
        assertThat(responseICalendar.events.first().attendees.first().email).isEqualTo("james@example.com")
        assertThat(responseICalendar.events.first().attendees.first().participationStatus).isEqualTo(ParticipationStatus.ACCEPTED)
        assertThat(responseICalendar.events.first().exceptionDates.isNullOrEmpty()).isTrue()
    }

    @Test
    fun `getResponseIcs proton to proton test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20210105T120000
    DTEND;TZID=Europe/Zurich:20210105T123000
    ORGANIZER;CN=adamtst@protonmail.com:mailto:adamtst@protonmail.com
    SEQUENCE:0
    SUMMARY:Inviting BLT from adamtst
    STATUS:CONFIRMED
    DTSTAMP:20210105T101420Z
    UID:GOmbzP5Ok3Uo7QYgYyb7LCCijGzS@proton.me
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:james@example.com
    ATTENDEE;CN=james@pm.me;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james2@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=James;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;EMAIL=AmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=IAmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    X-PM-SHARED-EVENT-ID:sharedEventId
    X-PM-SESSION-KEY:sharedSessionKey
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val eventIcal = ICalUtilsImpl.parseICalString(iCalString)!!

        val ics = getResponseIcs(eventIcal, eventIcal.events.first().attendees.first(), ParticipationStatus.ACCEPTED, null, Date.from(Instant.now()), true, "sharedEventId", "sharedSessionKey")

        val responseICalendar = ICalUtilsImpl.parseICalString(ics)!!

        assertThat(responseICalendar.productId.value).isEqualTo(generateProtonProdId())
        assertThat(responseICalendar.version).isEqualTo(ICalVersion.V2_0)
        assertThat(responseICalendar.method.value).isEqualTo(Method.REPLY)
        assertThat(responseICalendar.calendarScale.value).isEqualTo(CalendarScale.GREGORIAN)
        assertThat(responseICalendar.events.first().alarms.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().attendees.size).isEqualTo(1)
        assertThat(responseICalendar.events.first().attendees.first().email).isEqualTo("james@example.com")
        assertThat(responseICalendar.events.first().attendees.first().participationStatus).isEqualTo(ParticipationStatus.ACCEPTED)
        assertThat(responseICalendar.events.first().exceptionDates.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().getExperimentalProperty(CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID)?.value?.equals("sharedEventId"))
        assertThat(responseICalendar.events.first().getExperimentalProperty(CustomICalPropertyParameter.X_PM_SESSION_KEY)?.value?.equals("sharedSessionKey"))
        assertThat(responseICalendar.events.first().getExperimentalProperty(CustomICalPropertyParameter.X_PM_PROTON_REPLY)?.value?.equals("TRUE"))
    }

    @Test
    fun `getInviteIcs test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20210105T120000
    DTEND;TZID=Europe/Zurich:20210105T123000
    ORGANIZER;CN=adamtst@protonmail.com:mailto:adamtst@protonmail.com
    SEQUENCE:0
    SUMMARY:Inviting BLT from adamtst
    STATUS:CONFIRMED
    DTSTAMP:20210105T101420Z
    UID:GOmbzP5Ok3Uo7QYgYyb7LCCijGzS@proton.me
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:james@example.com
    ATTENDEE;CN=james@pm.me;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james2@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=James;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;EMAIL=AmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=IAmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val eventIcal = ICalUtilsImpl.parseICalString(iCalString)!!

        val event = Event.from("eventId", Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), eventIcal, 0)!!
        event.iCalendar.setDefaultTimeZone("Europe/Paris")
        val ics = getInviteIcs(
            event,
            "sharedEventId",
            "sharedSessionKey"
        )

        // TODO: Provide complete VTIMEZONE in the ics. In the meantime, we remove it from the ICS
        assertThat(ics.contains("VTIMEZONE")).isFalse()

        val responseICalendar = ICalUtilsImpl.parseICalString(ics)!!

        assertThat(responseICalendar.productId.value).isEqualTo(generateProtonProdId())
        assertThat(responseICalendar.version).isEqualTo(ICalVersion.V2_0)
        assertThat(responseICalendar.method.value).isEqualTo(Method.REQUEST)
        assertThat(responseICalendar.calendarScale.value).isEqualTo(CalendarScale.GREGORIAN)
        assertThat(responseICalendar.events.first().alarms.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().attendees.size).isEqualTo(10)
        assertThat(responseICalendar.events.first().attendees.first().email).isEqualTo("james@example.com")
        assertThat(responseICalendar.events.first().attendees.first().participationStatus).isEqualTo(ParticipationStatus.NEEDS_ACTION)
        assertThat(responseICalendar.events.first().exceptionDates.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().getExperimentalProperty("X-PM-SESSION-KEY")?.value).isEqualTo("sharedSessionKey")
        assertThat(responseICalendar.events.first().getExperimentalProperty("X-PM-SHARED-EVENT-ID")?.value).isEqualTo("sharedEventId")
    }

    @Test
    fun `getCancelIcs test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Zurich:20210105T120000
    DTEND;TZID=Europe/Zurich:20210105T123000
    ORGANIZER;CN=adamtst@protonmail.com:mailto:adamtst@protonmail.com
    SEQUENCE:0
    SUMMARY:Inviting BLT from adamtst
    STATUS:CONFIRMED
    DTSTAMP:20210105T101420Z
    UID:GOmbzP5Ok3Uo7QYgYyb7LCCijGzS@proton.me
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:james@example.com
    ATTENDEE;CN=james@pm.me;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james2@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:mailto:james@example.com
    ATTENDEE;CN=james@pm.me;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=James;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=james@example.com;EMAIL=AmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    ATTENDEE;CN=IAmNotAnEmail;EMAIL=IAmNotAnEmail;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT:IAmNotAnEmail
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val eventIcal = ICalUtilsImpl.parseICalString(iCalString)!!

        val event = Event.from("eventId", Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), eventIcal, 0)!!
        event.iCalendar.setDefaultTimeZone("Europe/Paris")
        val ics = getCancelIcs(
            event,
            "sharedEventId"
        )

        // TODO: Provide complete VTIMEZONE in the ics. In the meantime, we remove it from the ICS
        assertThat(ics.contains("VTIMEZONE")).isFalse()

        val responseICalendar = ICalUtilsImpl.parseICalString(ics)!!

        assertThat(responseICalendar.productId.value).isEqualTo(generateProtonProdId())
        assertThat(responseICalendar.version).isEqualTo(ICalVersion.V2_0)
        assertThat(responseICalendar.method.value).isEqualTo(Method.CANCEL)
        assertThat(responseICalendar.calendarScale.value).isEqualTo(CalendarScale.GREGORIAN)
        assertThat(responseICalendar.events.first().status?.value.isNullOrEmpty())
        assertThat(responseICalendar.events.first().alarms.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().attendees.size).isEqualTo(10)
        assertThat(responseICalendar.events.first().attendees.first().email).isEqualTo("james@example.com")
        assertThat(responseICalendar.events.first().attendees.first().participationStatus).isEqualTo(ParticipationStatus.NEEDS_ACTION)
        assertThat(responseICalendar.events.first().exceptionDates.isNullOrEmpty()).isTrue()
        assertThat(responseICalendar.events.first().getExperimentalProperty("X-PM-SESSION-KEY")?.value.isNullOrEmpty())
        assertThat(responseICalendar.events.first().getExperimentalProperty("X-PM-SHARED-EVENT-ID")?.value).isEqualTo("sharedEventId")
    }

    @Test
    fun `explode Events day by day for Widget`() {

        val allDayMultiDayString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.24.2//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20210920T083644Z
    DTSTART;VALUE=DATE:20210920
    SEQUENCE:0
    SUMMARY:2-day all-day
    STATUS:CONFIRMED
    UID:l8ZA-04Xv8ukoH2DnuFpQDHEXb7Y@proton.me
    DTEND;VALUE=DATE:20210922
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
        """.trimIndent()

        val partDayMultiDayString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.24.2//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20210920T083552Z
    DTSTART;TZID=Europe/Zurich:20210920T110000
    DTEND;TZID=Europe/Zurich:20210922T120000
    SEQUENCE:0
    SUMMARY:3-day part-time\, 11:00 to 12:00
    STATUS:CONFIRMED
    UID:FUAdf8hlugTj5Aiuz7DkIEfpaf_-@proton.me
        """.trimIndent()

        val oneHourEvent = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.24.2//EN
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTAMP:20210920T131204Z
    DTSTART;TZID=Europe/Zurich:20210922T153000
    DTEND;TZID=Europe/Zurich:20210922T163000
    SEQUENCE:0
    SUMMARY:Regular 1-hour event
    STATUS:CONFIRMED
    UID:gWEfn3xdkmfX6rJ8w-q5IT3jnbJG@proton.me
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=START:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
        """.trimIndent()

        val displayTimeZoneId = "Europe/Zurich"

        val events = listOf(
            Event.from("event-all-day", Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), ICalUtilsImpl.parseICalString(allDayMultiDayString)!!, 0)!!,
            Event.from("event-part-day", Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), ICalUtilsImpl.parseICalString(partDayMultiDayString)!!, 0)!!,
            Event.from("event-1-hour", Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), ICalUtilsImpl.parseICalString(oneHourEvent)!!, 0)!!,
        )

        val explodedEvents = events.explodeEventDayByDay(LocalDate.of(2021, 9, 20), LocalDate.of(2021, 9, 22), displayTimeZoneId)

        // multi-day events start on first day of the window
        with(explodedEvents) {
            assertThat(this.flatMap { it.value }.size).isEqualTo(6)

            assertThat(this[LocalDate.of(2021, 9, 20)]?.find { it.summary == "2-day all-day" }).isNotNull()
            assertThat(this[LocalDate.of(2021, 9, 21)]?.find { it.summary == "2-day all-day" }).isNotNull()

            assertThat(this[LocalDate.of(2021, 9, 20)]?.find { it.summary == "3-day part-time, 11:00 to 12:00" }).isNotNull()
            assertThat(this[LocalDate.of(2021, 9, 21)]?.find { it.summary == "3-day part-time, 11:00 to 12:00" }).isNotNull()
            assertThat(this[LocalDate.of(2021, 9, 22)]?.find { it.summary == "3-day part-time, 11:00 to 12:00" }).isNotNull()

            assertThat(this[LocalDate.of(2021, 9, 22)]?.find { it.summary == "Regular 1-hour event" }).isNotNull()
        }

        val explodedEventsAlreadyHappening = events.explodeEventDayByDay(LocalDate.of(2021, 9, 21), LocalDate.of(2021, 9, 21), displayTimeZoneId)

        // multi-day events start before the window and end after it
        with(explodedEventsAlreadyHappening) {
            assertThat(this.flatMap { it.value }.size).isEqualTo(2)

            assertThat(this[LocalDate.of(2021, 9, 21)]?.find { it.summary == "2-day all-day" }).isNotNull()

            assertThat(this[LocalDate.of(2021, 9, 21)]?.find { it.summary == "3-day part-time, 11:00 to 12:00" }).isNotNull()
        }

    }

    @Test
    fun `filter out AlarmEntity duplicates`() {

        // this is from daily event that has alarms 1, 2 and 3 days before
        // on any given "alarm occurrence", we should show alarms for tomorrow's Event occurrence, occurrence in 2 days and occurrence in 3 days

        // they point to the same Event and have the same "alarm occurrence", but different triggers

        // last alarm here is actually duplicated twice (with different triggers), the rest should not be filtered out
        val alarmEntities = listOf(
            EventAlarmEntity(id="Proton-Android-App-Offline-Alarm-ID:943f6d2e-085d-404b-b92d-f7f8d3899279706235ff-5d53-442a-b037-aca8b48c19d5792bdc3e-74e4-4d90-8d5e-973e6de2bd3f", occurrence=1619547000, trigger="-P1D", action=2, eventId="rSJotpXXwG8IDXQ3iUhj41XtPCr_zNma3Me2geX7FcMmnA2WK25IEEHXOPDi27FSB_jKexR6e18T_4yXslROyQ==", memberId="TODO", calendarId="bIvmJ6uHkwB9knCpvOf-USe8DbfhWAligpAyz6oDynHCEJHlwwdzOp2uu-604zE6Y74QUgk6nhZDbXvlCpc21g=="),
            EventAlarmEntity(id="Proton-Android-App-Offline-Alarm-ID:61925d2f-e242-4fc6-a6b4-05d214abaeb57b94d8cb-7fc8-4816-a5fc-d01f61d48a74eab9ccf7-63b9-4bf9-9674-bcd80ce7485a", occurrence=1619547000, trigger="-P2D", action=2, eventId="rSJotpXXwG8IDXQ3iUhj41XtPCr_zNma3Me2geX7FcMmnA2WK25IEEHXOPDi27FSB_jKexR6e18T_4yXslROyQ==", memberId="TODO", calendarId="bIvmJ6uHkwB9knCpvOf-USe8DbfhWAligpAyz6oDynHCEJHlwwdzOp2uu-604zE6Y74QUgk6nhZDbXvlCpc21g=="),
            EventAlarmEntity(id="Proton-Android-App-Offline-Alarm-ID:85a58d2d-706b-449d-8d88-b7531addeac8f90bdcdd-dcf3-4aed-beaf-de0bfa1344cd5a8c8a0c-2ab1-4662-a255-1e2ed4ec94e8", occurrence=1619547000, trigger="-P3D", action=2, eventId="rSJotpXXwG8IDXQ3iUhj41XtPCr_zNma3Me2geX7FcMmnA2WK25IEEHXOPDi27FSB_jKexR6e18T_4yXslROyQ==", memberId="TODO", calendarId="bIvmJ6uHkwB9knCpvOf-USe8DbfhWAligpAyz6oDynHCEJHlwwdzOp2uu-604zE6Y74QUgk6nhZDbXvlCpc21g=="),
            EventAlarmEntity(id="Proton-Android-App-Offline-Alarm-ID:85a58d2d-706b-449d-8d88-b7531addeac8f90bdcdd-dcf3-4aed-beaf-de0bfa1344cd5a8c8a0c-2ab1-4662-a255-1e2ed4ec94e8", occurrence=1619547000, trigger="-P3D", action=2, eventId="rSJotpXXwG8IDXQ3iUhj41XtPCr_zNma3Me2geX7FcMmnA2WK25IEEHXOPDi27FSB_jKexR6e18T_4yXslROyQ==", memberId="TODO", calendarId="bIvmJ6uHkwB9knCpvOf-USe8DbfhWAligpAyz6oDynHCEJHlwwdzOp2uu-604zE6Y74QUgk6nhZDbXvlCpc21g=="),
            EventAlarmEntity(id="Proton-Android-App-Offline-Alarm-ID:85a58d2d-706b-449d-8d88-b7531addeac8f90bdcdd-dcf3-4aed-beaf-de0bfa1344cd5a8c8a0c-2ab1-4662-a255-1e2ed4ec94e8", occurrence=1619547000, trigger="-PT72H0M0S", action=2, eventId="rSJotpXXwG8IDXQ3iUhj41XtPCr_zNma3Me2geX7FcMmnA2WK25IEEHXOPDi27FSB_jKexR6e18T_4yXslROyQ==", memberId="TODO", calendarId="bIvmJ6uHkwB9knCpvOf-USe8DbfhWAligpAyz6oDynHCEJHlwwdzOp2uu-604zE6Y74QUgk6nhZDbXvlCpc21g==")
        )

        assertThat(alarmEntities.filterOutDuplicates().size).isEqualTo(3)

    }

    @Test
    fun `filter out Event duplicates in Subscribed Calendars`() {

        val ics1 = "BEGIN:VCALENDAR\r\nPRODID:-//Proton AG//ProtonCalendar 1.0.0//EN\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nDTSTAMP:20220404T104736Z\r\nUID:bobevent6950035@hibob.com\r\nDTSTART;VALUE=DATE:20220411\r\nDTEND;VALUE=DATE:20220415\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val ics2 = "BEGIN:VCALENDAR\r\nPRODID:-//Proton AG//ProtonCalendar 1.0.0//EN\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nDTSTAMP:20220404T104736Z\r\nUID:bobevent6950035@hibob.com\r\nDTSTART;VALUE=DATE:20220411\r\nDTEND;VALUE=DATE:20220415\r\nEND:VEVENT\r\nEND:VCALENDAR"

        val ics3DifferentDateStart = "BEGIN:VCALENDAR\r\nPRODID:-//Proton AG//ProtonCalendar 1.0.0//EN\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nDTSTAMP:20220404T104736Z\r\nUID:bobevent6950035@hibob.com\r\nDTSTART;VALUE=DATE:20220412\r\nDTEND;VALUE=DATE:20220415\r\nEND:VEVENT\r\nEND:VCALENDAR"

        val ics4DifferentUid = "BEGIN:VCALENDAR\r\nPRODID:-//Proton AG//ProtonCalendar 1.0.0//EN\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nDTSTAMP:20220404T104736Z\r\nUID:bobeventDifferentUid@hibob.com\r\nDTSTART;VALUE=DATE:20220411\r\nDTEND;VALUE=DATE:20220415\r\nEND:VEVENT\r\nEND:VCALENDAR"

        val calendarSubscribed = Calendar("calendar 1", "calendar 1", "email 1", "email 1", "description 1", "",0, "addressId", "memberId", 1, true, 1, 127, 30, emptyList(), emptyList())
        val calendarRegular = Calendar("calendar 2", "calendar 2", "email 2", "email 2", "description 2", "",0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList())

        val skeletonEntities = listOf<SkeletonEvent>(
            // original event
            SkeletonEvent.from(SkeletonEvent.dummyFrom(parseICalString(ics1)!!)!!, calendar = calendarSubscribed, id = "id1"),
            // actual duplicate
            SkeletonEvent.from(SkeletonEvent.dummyFrom(parseICalString(ics2)!!)!!, calendar = calendarSubscribed, id = "id2"),
            // the same UID but different date start
            SkeletonEvent.from(SkeletonEvent.dummyFrom(parseICalString(ics3DifferentDateStart)!!)!!, calendar = calendarSubscribed, id = "id3"),
            // different event
            SkeletonEvent.from(SkeletonEvent.dummyFrom(parseICalString(ics4DifferentUid)!!)!!, calendar = calendarSubscribed,  id = "id4"),

            // same event content but should be on the list because it's a non-subscribed calendar
            SkeletonEvent.from(SkeletonEvent.dummyFrom(parseICalString(ics1)!!)!!, calendar = calendarRegular, id = "id5"),
        )

        val filtered = skeletonEntities.filterOutDuplicatesInSubscribedCalendars().first

        assertThat(filtered.size).isEqualTo(4)

        val ics1Filtered = filtered.filter { it.uid == "bobevent6950035@hibob.com" }
        assertThat(ics1Filtered.size).isEqualTo(3)
        assertThat(ics1Filtered.count { it.calendar == calendarSubscribed }).isEqualTo(2)
        assertThat(ics1Filtered.count { it.calendar == calendarRegular }).isEqualTo(1)

    }

    @Test
    fun `filter out Event duplicates in Subscribed Calendars if there are different occurrences of the same event`() {

        val iCalString = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Proton AG//AndroidCalendar 0.30.3//EN
            BEGIN:VEVENT
            DTSTAMP:20220124T115649Z
            DTSTART;TZID=Europe/Vilnius:20220227T010000
            DTEND;TZID=Europe/Vilnius:20220227T050000
            RRULE:FREQ=MONTHLY
            SEQUENCE:0
            SUMMARY:01:00-05:00 every 27th
            STATUS:CONFIRMED
            UID:sch4mDryiYH6aDkw3VsklnzfhKoW@proton.me
            END:VEVENT
            END:VCALENDAR
    """.trimIndent()

        val iCal = ICalUtilsImpl.parseICalString(iCalString)!!
        val displayTimeZoneId = "Europe/Vilnius"
        val event = Event.from("id", me.proton.android.calendar.domain.model.Calendar(
            "id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            1,
            127,
            30,
            emptyList(),
            emptyList()
        ), iCal, 0, null)!!

        val occurrences = event.generateOccurrences(displayTimeZoneId, null, null, 3)!!

        val eventsWithOccurrences = occurrences.map { Event.withOccurrence(event, it) }

        val skeletonEntities = eventsWithOccurrences.map {
            listOf(
                // add each Event twice to get duplicates
                SkeletonEvent.from(SkeletonEvent.from(it, id = "1:${System.currentTimeMillis()}")),
                SkeletonEvent.from(SkeletonEvent.from(it, id = "2:${System.currentTimeMillis()}"))
            )
        }.flatten()

        val filtered = skeletonEntities.filterOutDuplicatesInSubscribedCalendars().first

        assertThat(filtered.size).isEqualTo(3)

        assertThat(filtered[0].getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2022, 2, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(filtered[1].getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

        assertThat(filtered[2].getStart(displayTimeZoneId)).isEqualTo(ZonedDateTime.of(2022, 4, 27, 1, 0, 0, 0, ZoneId.of(displayTimeZoneId)))

    }

    @Test
    fun `is Calendar change allowed for simple Event`() {

        val simpleEvent = EventMocks.provideEvent()
        val simpleInvitation = EventMocks.provideEvent(isAttendee = true, isOrganizer = true)

        val recurringEvent = EventMocks.provideEvent(isRecurring = true)
        val recurringInvitation = EventMocks.provideEvent(isRecurring = true, isAttendee = true, isOrganizer = true)

        assertThat(isCalendarChangeAllowed(simpleEvent, simpleEvent)).isEqualTo(CalendarFeatureFlag.ChangeCalendarSimpleEvent.fallbackValue)
        assertThat(isCalendarChangeAllowed(simpleEvent, simpleInvitation)).isEqualTo(false)

        assertThat(isCalendarChangeAllowed(simpleEvent, recurringEvent)).isEqualTo(CalendarFeatureFlag.ChangeCalendarSimpleEvent.fallbackValue)
        assertThat(isCalendarChangeAllowed(simpleEvent, recurringInvitation)).isEqualTo(false)

    }

    @Test
    fun `is Calendar change allowed for recurring Event`() {

        val simpleEvent = EventMocks.provideEvent()
        val recurringEvent = EventMocks.provideEvent(isRecurring = true)
        val singleEditEvent = EventMocks.provideEvent(isSingleEdit = true)

        assertThat(isCalendarChangeAllowed(recurringEvent, simpleEvent)).isFalse()
        assertThat(isCalendarChangeAllowed(recurringEvent, singleEditEvent)).isFalse()
        assertThat(isCalendarChangeAllowed(recurringEvent, recurringEvent)).isFalse()

    }

    @Test
    fun `is Calendar change allowed for single-edit Event`() {

        val simpleEvent = EventMocks.provideEvent()
        val recurringEvent = EventMocks.provideEvent(isRecurring = true)
        val singleEditEvent = EventMocks.provideEvent(isSingleEdit = true)

        assertThat(isCalendarChangeAllowed(singleEditEvent, simpleEvent)).isFalse()
        assertThat(isCalendarChangeAllowed(singleEditEvent, recurringEvent)).isFalse()
        assertThat(isCalendarChangeAllowed(singleEditEvent, singleEditEvent)).isFalse()

    }

    @Test
    fun `sort for month view`() {

        // List of event with the 5th as common date

        val timeZoneId = "Europe/Paris"
        val uiEvents = listOf(
            UiEvent().copy(
                dateStart = ZonedDateTime.of(2021, 10, 3, 0, 0, 0, 0, ZoneId.of(timeZoneId)),
                dateEnd = ZonedDateTime.of(2021, 10, 6, 0, 0, 0, 0, ZoneId.of(timeZoneId)),
                summary = "Multi day all day 3 - 5"
            ),
            UiEvent().copy(
                dateStart = ZonedDateTime.of(2021, 10, 5, 0, 0, 0, 0, ZoneId.of(timeZoneId)),
                dateEnd = ZonedDateTime.of(2021, 10, 8, 0, 0, 0, 0, ZoneId.of(timeZoneId)),
                summary = "Multi day all day 5 - 7"
            ),
            UiEvent().copy(
                dateStart = ZonedDateTime.of(2021, 10, 5, 11, 0, 0, 0, ZoneId.of(timeZoneId)),
                dateEnd = ZonedDateTime.of(2021, 10, 6, 13, 0, 0, 0, ZoneId.of(timeZoneId)),
                summary = "Multi day part day 5 - 6 11h - 13h"
            ),
            UiEvent().copy(
                dateStart = ZonedDateTime.of(2021, 10, 5, 11, 30, 0, 0, ZoneId.of(timeZoneId)),
                dateEnd = ZonedDateTime.of(2021, 10, 7, 13, 30, 0, 0, ZoneId.of(timeZoneId)),
                summary = "Multi day part day 5 - 7 11h30 - 13h30"
            ),
            UiEvent().copy(
                dateStart = ZonedDateTime.of(2021, 10, 5, 10, 0, 0, 0, ZoneId.of(timeZoneId)),
                dateEnd = ZonedDateTime.of(2021, 10, 5, 11, 0, 0, 0, ZoneId.of(timeZoneId)),
                summary = "Part day 5 10h - 11h"
            ),
            UiEvent().copy(
                dateStart = ZonedDateTime.of(2021, 10, 5, 10, 30, 0, 0, ZoneId.of(timeZoneId)),
                dateEnd = ZonedDateTime.of(2021, 10, 5, 11, 30, 0, 0, ZoneId.of(timeZoneId)),
                summary = "Part day 5 10h30 - 11h30"
            )
        )

        val sortedList = uiEvents.sortForMonthView()

        assertThat(sortedList[0].summary).isEqualTo("Multi day all day 3 - 5")
        assertThat(sortedList[1].summary).isEqualTo("Multi day all day 5 - 7")
        assertThat(sortedList[2].summary).isEqualTo("Multi day part day 5 - 6 11h - 13h")
        assertThat(sortedList[3].summary).isEqualTo("Multi day part day 5 - 7 11h30 - 13h30")
        assertThat(sortedList[4].summary).isEqualTo("Part day 5 10h - 11h")
        assertThat(sortedList[5].summary).isEqualTo("Part day 5 10h30 - 11h30")

    }

    @Test
    fun `VAlarm isTheSameAs comparison`() {

        val same1 = listOf(
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(1).build(), Related.START), "not used"),
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(2).build(), Related.START), "not used")
        )

        val same2 = listOf(
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(1).build(), Related.START), "not used"),
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(2).build(), Related.START), "not used")
        )

        val sameButDuplicates = listOf(
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(1).build(), Related.START), "not used"),
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(1).build(), Related.START), "not used"),
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(2).build(), Related.START), "not used"),
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(2).build(), Related.START), "not used")
        )

        val different = listOf(
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(3).build(), Related.START), "not used"),
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(4).build(), Related.START), "not used")
        )

        assertThat(same1.isTheSameAs(same2)).isTrue()
        assertThat(same2.isTheSameAs(same1)).isTrue()
        assertThat(same1.isTheSameAs(sameButDuplicates)).isTrue()
        assertThat(sameButDuplicates.isTheSameAs(same1)).isTrue()

        assertThat(same1.isTheSameAs(different)).isFalse()
        assertThat(different.isTheSameAs(same1)).isFalse()

        assertThat(different.isTheSameAs(sameButDuplicates)).isFalse()

    }

    private fun createEvent(startZonedDateTime: ZonedDateTime, endZonedDateTime: ZonedDateTime, hasTime: Boolean, timeZoneId: String, summary: String? = null): Event {
        val iCalendar = createNewVEvent().apply {
            setDateStart(Date.from(startZonedDateTime.toInstant()), hasTime)
            setDateEnd(Date.from(endZonedDateTime.toInstant()), hasTime)
            if (summary != null) setSummary(summary)
        }.wrapInICalendar()
        iCalendar.setStartTimeZone(timeZoneId)
        iCalendar.setEndTimeZone(timeZoneId)

        return Event.from("", Calendar("", "", "", "", "", "",0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()), iCalendar, 0)!!
    }
}
