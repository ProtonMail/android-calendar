package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.calculateWeekNumberBetween
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getFullyOverlappingWindow
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLastWeekOfMonthOffset
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getTimezoneOffsetDifferenceSeconds
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal class DateTimeUtilsTest {

    @Test
    fun `check if EventsWindow fully overlaps with collection`() {

        val timeZone = "Europe/Zurich"

        val eventsWindows = listOf(
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 6, 1), LocalDate.of(2021, 6, 30), timeZone),
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 8, 1), LocalDate.of(2021, 8, 31), timeZone)
        )

        val windowBefore =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 5, 1), LocalDate.of(2021, 5, 31), timeZone)
        val windowOverlappingStart =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 5, 1), LocalDate.of(2021, 6, 1), timeZone)
        val windowInside =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 6, 10), LocalDate.of(2021, 6, 10), timeZone)
        val windowBetween =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 7, 1), LocalDate.of(2021, 7, 31), timeZone)
        val windowOverlappingEnd =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 8, 31), LocalDate.of(2021, 9, 1), timeZone)
        val windowAfter =
            CalendarsRepository.EventsWindow(LocalDate.of(2021, 10, 1), LocalDate.of(2021, 10, 1), timeZone)

        assertThat(eventsWindows.getFullyOverlappingWindow(windowBefore)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowOverlappingStart)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowInside)).isNotNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowBetween)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowOverlappingEnd)).isNull()
        assertThat(eventsWindows.getFullyOverlappingWindow(windowAfter)).isNull()

    }

    @Test
    fun `Calculate weeks between dates`() {
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2021, 7, 15),
            DayOfWeek.MONDAY))
            .isEqualTo(6)
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2021, 4, 20),
            DayOfWeek.MONDAY))
            .isEqualTo(-6)
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2023, 4, 6),
            DayOfWeek.MONDAY))
            .isEqualTo(96)
        // End date's week number is previous year's last week number
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2023, 1, 1),
            DayOfWeek.MONDAY))
            .isEqualTo(82)
        // Start date's week number is previous year's last week number
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2021, 6, 1),
            DayOfWeek.MONDAY))
            .isEqualTo(-82)
        assertThat(calculateWeekNumberBetween(
            LocalDate.of(2021, 6, 1),
            LocalDate.of(2019, 9, 18),
            DayOfWeek.MONDAY))
            .isEqualTo(-89)
    }

    @Test
    fun `Calculate last week of the month offset`() {
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.MONDAY,
            LocalDate.of(2021, 6, 30)
        )).isEqualTo(4)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SATURDAY,
            LocalDate.of(2021, 6, 30)
        )).isEqualTo(2)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SUNDAY,
            LocalDate.of(2021, 6, 30)
        )).isEqualTo(3)

        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SUNDAY,
            LocalDate.of(2021, 7, 31)
        )).isEqualTo(0)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.SATURDAY,
            LocalDate.of(2021, 7, 31)
        )).isEqualTo(6)
        assertThat(getLastWeekOfMonthOffset(
            DayOfWeek.MONDAY,
            LocalDate.of(2021, 7, 31)
        )).isEqualTo(1)
    }

    @Test
    fun `Check timezone offset difference between two Instants`() {
        var timezone = "Europe/Berlin"
        assertThat(
            getTimezoneOffsetDifferenceSeconds(
                ZonedDateTime.of(
                    2021, 10, 28, 0, 0, 0, 0, ZoneId.of(timezone)
                ).toInstant(), // GMT+2
                ZonedDateTime.of(
                    2021, 11, 2, 0, 0, 0, 0, ZoneId.of(timezone)
                ).toInstant(), // GMT+1
                timezone
            )
        ).isEqualTo(-3600)

        timezone = "Pacific/Auckland"
        assertThat(
            getTimezoneOffsetDifferenceSeconds(
                ZonedDateTime.of(
                    2021, 9, 25, 0, 0, 0, 0, ZoneId.of(timezone)
                ).toInstant(), // GMT+12
                ZonedDateTime.of(
                    2021, 9, 27, 0, 0, 0, 0, ZoneId.of(timezone)
                ).toInstant(), // GMT+13
                timezone
            )
        ).isEqualTo(3600)
    }

    @Test
    fun `Fix dateStart and dateEnd parsing of Event starting during CET`() {

        val iCalString = """
            BEGIN:VCALENDAR
            METHOD:REQUEST
            PRODID:Microsoft Exchange Server 2010
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:16010101T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10
            END:STANDARD
            BEGIN:DAYLIGHT
            DTSTART:16010101T020000
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3
            END:DAYLIGHT
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:040000008
            SUMMARY;LANGUAGE=en-US:test
            DTSTART;TZID=Europe/Berlin:20211102T130000
            DTEND;TZID=Europe/Berlin:20211102T140000
            DTSTAMP:20211029T072337Z
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Berlin"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR, 0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        // the actual dateTimes should always be the same, no matter when Biweekly parsed the ICS (during standard
        //  or daylight saving time)

        // TODO This is not applied anymore during ICS surgery, uncomment if this test starts to fail someday
        // event.iCalEvent.dateStart.value.getBiweeklyDstParsingFix(event.iCalendar.timezoneInfo.getTimezone(event.iCalEvent.dateStart).timeZone.id)?.let {
        //     event.iCalEvent.dateStart.value = it
        // }
        // event.iCalEvent.dateEnd.value.getBiweeklyDstParsingFix(event.iCalendar.timezoneInfo.getTimezone(event.iCalEvent.dateEnd).timeZone.id)?.let {
        //     event.iCalEvent.dateEnd.value = it
        // }

        assertThat(event.getStart(timeZoneId)).isEqualTo(ZonedDateTime.of(
            LocalDate.of(2021, 11, 2),
            LocalTime.of(13, 0, 0),
            ZoneId.of(timeZoneId)
        ))

        assertThat(event.getEnd(timeZoneId)).isEqualTo(ZonedDateTime.of(
            LocalDate.of(2021, 11, 2),
            LocalTime.of(14, 0, 0),
            ZoneId.of(timeZoneId)
        ))

    }

    @Test
    fun `Fix dateStart and dateEnd parsing of Event starting during CEST`() {

        val iCalString = """
            BEGIN:VCALENDAR
            METHOD:REQUEST
            PRODID:Microsoft Exchange Server 2010
            VERSION:2.0
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:16010101T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10
            END:STANDARD
            BEGIN:DAYLIGHT
            DTSTART:16010101T020000
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3
            END:DAYLIGHT
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:040000008
            SUMMARY;LANGUAGE=en-US:test
            DTSTART;TZID=Europe/Berlin:20220402T130000
            DTEND;TZID=Europe/Berlin:20220402T140000
            DTSTAMP:20211029T072337Z
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Berlin"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        // TODO This is not applied anymore during ICS surgery, uncomment if this test starts to fail someday
        // event.iCalEvent.dateStart.value.getBiweeklyDstParsingFix(event.iCalendar.timezoneInfo.getTimezone(event.iCalEvent.dateStart).timeZone.id)?.let {
        //     event.iCalEvent.dateStart.value = it
        // }
        // event.iCalEvent.dateEnd.value.getBiweeklyDstParsingFix(event.iCalendar.timezoneInfo.getTimezone(event.iCalEvent.dateEnd).timeZone.id)?.let {
        //     event.iCalEvent.dateEnd.value = it
        // }

        // the actual dateTimes should always be the same, no matter when Biweekly parsed the ICS (during standard
        //  or daylight saving time)

        assertThat(event.getStart(timeZoneId)).isEqualTo(ZonedDateTime.of(
            LocalDate.of(2022, 4, 2),
            LocalTime.of(13, 0, 0),
            ZoneId.of(timeZoneId)
        ))

        assertThat(event.getEnd(timeZoneId)).isEqualTo(ZonedDateTime.of(
            LocalDate.of(2022, 4, 2),
            LocalTime.of(14, 0, 0),
            ZoneId.of(timeZoneId)
        ))
    }

}
