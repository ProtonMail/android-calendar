package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlinx.serialization.json.Json
import me.proton.android.calendar.BaseTest
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.test.shared.mocks.CalendarMocks.provideCalendarSettingsEntity
import org.junit.jupiter.api.Test
import java.time.*

internal class AlarmsTest : BaseTest() {

    @Test
    fun `calculate alarms for regular part-day event`() {

        val event = eventForICalString(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;TZID=UTC:20201126T200000
    DTEND;TZID=UTC:20201126T203000
    SEQUENCE:1
    SUMMARY:20:00
    STATUS:CONFIRMED
    UID:1FkJarw0ZGxECCG955eRylOdsdVN@proton.me
    DTSTAMP:20201126T165438Z
    BEGIN:VALARM
    TRIGGER:PT0S
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT30M
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT60M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()
        )

        // alarms for part-day event fire at the same point-in-time no matter the timezone

        val alarmsUtc = ICalUtilsImpl.calculateAlarmEntities(event, "UTC", "member-id")

        assertThat(alarmsUtc.size).isEqualTo(4)

        assertThat(alarmsUtc[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[0].action).isEqualTo(2)
        assertThat(alarmsUtc[0].trigger).isEqualTo("PT0S")

        assertThat(alarmsUtc[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(19, 45, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[1].action).isEqualTo(2)
        assertThat(alarmsUtc[1].trigger).isEqualTo("-PT15M")

        assertThat(alarmsUtc[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(19, 30, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[2].action).isEqualTo(2)
        assertThat(alarmsUtc[2].trigger).isEqualTo("-PT30M")

        assertThat(alarmsUtc[3].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(19, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarmsUtc[3].action).isEqualTo(2)
        assertThat(alarmsUtc[3].trigger).isEqualTo("-PT60M")

        val alarmsZurich = ICalUtilsImpl.calculateAlarmEntities(event, "Europe/Zurich", "member-id")

        assertThat(alarmsZurich.size).isEqualTo(4)

        assertThat(alarmsZurich[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(21, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[0].action).isEqualTo(2)
        assertThat(alarmsZurich[0].trigger).isEqualTo("PT0S")

        assertThat(alarmsZurich[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 45, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[1].action).isEqualTo(2)
        assertThat(alarmsZurich[1].trigger).isEqualTo("-PT15M")

        assertThat(alarmsZurich[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 30, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[2].action).isEqualTo(2)
        assertThat(alarmsZurich[2].trigger).isEqualTo("-PT30M")

        assertThat(alarmsZurich[3].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 26),
                LocalTime.of(20, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[3].action).isEqualTo(2)
        assertThat(alarmsZurich[3].trigger).isEqualTo("-PT60M")

    }

    @Test
    fun `calculate alarms for regular all-day event`() {

        val event = eventForICalString(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201130
    SEQUENCE:1
    SUMMARY:all-day 3 notifs
    STATUS:CONFIRMED
    UID:JeEXmSg8RbKD8VWKitbcvdHp7UFD@proton.me
    DTSTAMP:20201126T180559Z
    DTEND;VALUE=DATE:20201130
    BEGIN:VALARM
    TRIGGER:PT9H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT15H30M
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-P2DT16H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()
        )

        // on the same day at 9:00
        // 1 day before at 8:30
        // 3 days before at 8:00

        // in different timezones alarms should fire at the same local time of the same day

        val alarms = ICalUtilsImpl.calculateAlarmEntities(event, "UTC", "member-id")

        assertThat(alarms.size).isEqualTo(3)

        TestsLogger.d("${Instant.ofEpochSecond(alarms[0].occurrence)}")

        assertThat(alarms[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 30),
                LocalTime.of(9, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarms[0].action).isEqualTo(2)
        assertThat(alarms[0].trigger).isEqualTo("PT9H")

        assertThat(alarms[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 29),
                LocalTime.of(8, 30, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarms[1].action).isEqualTo(2)
        assertThat(alarms[1].trigger).isEqualTo("-PT15H30M")

        assertThat(alarms[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 27),
                LocalTime.of(8, 0, 0),
                ZoneId.of("UTC")
            ).toEpochSecond()
        )
        assertThat(alarms[2].action).isEqualTo(2)
        assertThat(alarms[2].trigger).isEqualTo("-P2DT16H")

        val alarmsZurich = ICalUtilsImpl.calculateAlarmEntities(event, "Europe/Zurich", "member-id")

        assertThat(alarms.size).isEqualTo(3)

        TestsLogger.d("${Instant.ofEpochSecond(alarmsZurich[0].occurrence)}")

        assertThat(alarmsZurich[0].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 30),
                LocalTime.of(9, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[0].action).isEqualTo(2)
        assertThat(alarmsZurich[0].trigger).isEqualTo("PT9H")

        assertThat(alarmsZurich[1].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 29),
                LocalTime.of(8, 30, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[1].action).isEqualTo(2)
        assertThat(alarmsZurich[1].trigger).isEqualTo("-PT15H30M")

        assertThat(alarmsZurich[2].occurrence).isEqualTo(
            ZonedDateTime.of(
                LocalDate.of(2020, 11, 27),
                LocalTime.of(8, 0, 0),
                ZoneId.of("Europe/Zurich")
            ).toEpochSecond()
        )
        assertThat(alarmsZurich[2].action).isEqualTo(2)
        assertThat(alarmsZurich[2].trigger).isEqualTo("-P2DT16H")
    }

    val allDay1 = eventForICalString(
        """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    RRULE:FREQ=DAILY;UNTIL=20201212
    EXDATE;VALUE=DATE:20201208
    SEQUENCE:0
    SUMMARY:All-day daily\, alarm 1 day before at 9 and on the day at 9
    STATUS:CONFIRMED
    UID:Z3nFwzbyM-VdOYquZ2sVk5H9LrHJ@proton.me
    DTSTAMP:20201207T093547Z
    DTSTART;VALUE=DATE:20201207
    DTEND;VALUE=DATE:20201207
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:PT9H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent(), "event1")

    val allDay2 = eventForICalString(
        """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201209
    RECURRENCE-ID;VALUE=DATE:20201209
    SEQUENCE:0
    SUMMARY:All-day daily\, single edit
    STATUS:CONFIRMED
    UID:Z3nFwzbyM-VdOYquZ2sVk5H9LrHJ@proton.me
    DTSTAMP:20201207T093547Z
    DTEND;VALUE=DATE:20201209
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:PT9H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent(), "event1-single-edit-1")

    val allDay3 = eventForICalString(
        """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201211
    RECURRENCE-ID;VALUE=DATE:20201211
    SEQUENCE:0
    SUMMARY:All-day daily\, single edit with alarms at 10 instead of 9
    STATUS:CONFIRMED
    UID:Z3nFwzbyM-VdOYquZ2sVk5H9LrHJ@proton.me
    DTSTAMP:20201207T093547Z
    DTEND;VALUE=DATE:20201211
    BEGIN:VALARM
    TRIGGER:-PT14H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:PT10H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent(), "event1-single-edit-2")

    val allDay4NoAlarms = eventForICalString(
        """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Europe/Zurich
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20201211
    SEQUENCE:0
    SUMMARY:Unrelated single event on 11th\, alarm on the same day at 12:00
    STATUS:CONFIRMED
    UID:xl2GkVJxpLvn_Si_eBxtQAxBO_Ei@proton.me
    DTSTAMP:20201207T173100Z
    DTEND;VALUE=DATE:20201211
    END:VEVENT
    END:VCALENDAR
    """.trimIndent(), "event2")

    @Test
    fun `calculate all alarms for chain of all-day events`() {

        // daily event happening from 7th to 12th
        // deleted on 8th
        // single edit on 9th and 11th
        val sameUidEvents = listOf(allDay1, allDay2, allDay3, allDay4NoAlarms)

        val until = LocalDate.of(2020, 12, 12)
        val timeZoneId = "Pacific/Apia"
        val zoneId = ZoneId.of(timeZoneId)
        val expandedEvents = ICalUtilsImpl.expandOccurrencesWithSingleEdits(allDay1, sameUidEvents, until, timeZoneId)
        val exDateFiltered = expandedEvents!!.filterOutOccurrencesByExdates(allDay1, timeZoneId)
        val withOccurrences = exDateFiltered.map {
            if (it.occurrence != null) {
                Event.withOccurrence(it, it.occurrence!!)
            } else it
        }

        val alarms = withOccurrences.flatMap {
            ICalUtilsImpl.calculateAlarmEntities(it, timeZoneId, "TODO")
        }

        alarms.forEach {
            TestsLogger.d("${Instant.ofEpochSecond(it.occurrence).atZone(zoneId)} -> ${it.eventId}")
        }

        assertThat(alarms.size).isEqualTo(10)

        assertThat(Instant.ofEpochSecond(alarms[0].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 6, 9, 0, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms[1].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 7, 9, 0, 0, 0, zoneId)
        )

        assertThat(Instant.ofEpochSecond(alarms[2].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 8, 9, 0, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms[3].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 9, 9, 0, 0, 0, zoneId)
        )

        assertThat(Instant.ofEpochSecond(alarms[4].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 9, 9, 0, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms[5].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 10, 9, 0, 0, 0, zoneId)
        )

        assertThat(Instant.ofEpochSecond(alarms[6].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 10, 10, 0, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms[7].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 11, 10, 0, 0, 0, zoneId)
        )

        assertThat(Instant.ofEpochSecond(alarms[8].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 11, 9, 0, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms[9].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 12, 9, 0, 0, 0, zoneId)
        )

    }

    @Test
    fun `calculate upcoming alarms for chain of all-day events`() {

        // daily event happening from 7th to 12th
        // deleted on 8th
        // single edit on 9th and 11th
        val sameUidEvents = listOf(allDay1, allDay2, allDay3, allDay4NoAlarms)

        val timeZoneId = "Asia/Tokyo"
        val zoneId = ZoneId.of(timeZoneId)

        val now1 = ZonedDateTime.of(2020, 12, 5, 9, 0, 0, 0, zoneId)
        val alarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(sameUidEvents, now1, "TODO")

        alarms.forEach {
            TestsLogger.d("${Instant.ofEpochSecond(it.occurrence).atZone(zoneId)} alarm for ${it.eventId}")
        }

        assertThat(alarms.size).isEqualTo(6)

        // first occurrence
        assertThat(Instant.ofEpochSecond(alarms[0].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 6, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[0].eventId).isEqualTo("event1")
        assertThat(Instant.ofEpochSecond(alarms[1].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 7, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[1].eventId).isEqualTo("event1")

        // single edit on 9th
        assertThat(Instant.ofEpochSecond(alarms[2].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 8, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[2].eventId).isEqualTo("event1-single-edit-1")
        assertThat(Instant.ofEpochSecond(alarms[3].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 9, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[3].eventId).isEqualTo("event1-single-edit-1")

        // single edit on 11th
        assertThat(Instant.ofEpochSecond(alarms[4].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 10, 10, 0, 0, 0, zoneId)
        )
        assertThat(alarms[4].eventId).isEqualTo("event1-single-edit-2")
        assertThat(Instant.ofEpochSecond(alarms[5].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 11, 10, 0, 0, 0, zoneId)
        )
        assertThat(alarms[5].eventId).isEqualTo("event1-single-edit-2")

    }

    @Test
    fun `calculate upcoming alarms for chain of all-day events with EXDATE`() {

        // TODO we're not returning recurring event occurrence so we're not 100% sure we return alarms for correct
        //  occurrences -- this is checked manually in calculateUpcomingAlarmEntities

        // daily event happening from 7th to 12th
        // deleted on 8th
        // single edit on 9th and 11th
        val sameUidEvents = listOf(allDay1, allDay2, allDay3, allDay4NoAlarms)

        val timeZoneId = "Asia/Tokyo"
        val zoneId = ZoneId.of(timeZoneId)

        val now2 = ZonedDateTime.of(2020, 12, 8, 12, 0, 0, 0, zoneId)
        val alarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(sameUidEvents, now2, "TODO")

        alarms.forEach {
            TestsLogger.d("${Instant.ofEpochSecond(it.occurrence).atZone(zoneId)} alarm for ${it.eventId}")
        }

        // 2 alarms for occurrence on 10th
        // 1 alarm for single edit on 9th
        // 2 alarms at 10:00 for single edit on 11th

        assertThat(alarms.size).isEqualTo(5)

        assertThat(Instant.ofEpochSecond(alarms[0].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 9, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[0].eventId).isEqualTo("event1")
        assertThat(Instant.ofEpochSecond(alarms[1].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 10, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[1].eventId).isEqualTo("event1")

        // single edit on 9th, but only 1 alarm, because the other has passed
        assertThat(Instant.ofEpochSecond(alarms[2].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 9, 9, 0, 0, 0, zoneId)
        )
        assertThat(alarms[2].eventId).isEqualTo("event1-single-edit-1")

        assertThat(Instant.ofEpochSecond(alarms[3].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 10, 10, 0, 0, 0, zoneId)
        )
        assertThat(alarms[3].eventId).isEqualTo("event1-single-edit-2")
        assertThat(Instant.ofEpochSecond(alarms[4].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 11, 10, 0, 0, 0, zoneId)
        )
        assertThat(alarms[4].eventId).isEqualTo("event1-single-edit-2")

        // no more alarms for event:

        val afterAllEvents = ZonedDateTime.of(2020, 12, 12, 12, 0, 0, 0, zoneId)
        val noMoreAlarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(sameUidEvents, afterAllEvents, "TODO")

        assertThat(noMoreAlarms.isEmpty()).isTrue()

    }

    val partDay1 = eventForICalString(
        """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART;TZID=Europe/Zurich:20201214T160000
            DTEND;TZID=Europe/Zurich:20201214T170000
            RRULE:FREQ=WEEKLY;WKST=MO;INTERVAL=2;BYDAY=MO
            DTSTAMP:20201207T152413Z
            UID:122qfbpnpghcrfqfqv94bg3hn0@google.com
            CREATED:20201207T151852Z
            DESCRIPTION:ah ah
            LAST-MODIFIED:20201207T152412Z
            LOCATION:
            SEQUENCE:1
            STATUS:CONFIRMED
            SUMMARY:Event with interesting reminders
            TRANSP:OPAQUE
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:This is an event reminder
            TRIGGER:-P0DT0H10M0S
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:This is an event reminder
            TRIGGER:-P14D
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent(), "partDay1")

    @Test
    fun `calculate upcoming alarms for part-day event with WKST, BYDAY`() {

        val events = listOf(partDay1)

        val timeZoneId = "Europe/Zurich"
        val zoneId = ZoneId.of(timeZoneId)

        val now = ZonedDateTime.of(2020, 12, 7, 9, 0, 0, 0, zoneId)
        val alarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(events, now, "TODO")

        alarms.forEach {
            TestsLogger.d("${Instant.ofEpochSecond(it.occurrence).atZone(zoneId)} alarm for ${it.eventId}")
        }

        // we should get 2 alarms, but for occurrence number n and n+1
        assertThat(alarms.size).isEqualTo(2)

        assertThat(Instant.ofEpochSecond(alarms[0].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 14, 15, 50, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms[1].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 14, 16, 0, 0, 0, zoneId)
        )

        // timestamp after last alarm for occurrence X
        val now2 = ZonedDateTime.of(2020, 12, 14, 15, 55, 0, 0, zoneId)
        val alarms2 = ICalUtilsImpl.calculateUpcomingAlarmEntities(events, now2, "TODO")

        alarms2.forEach {
            TestsLogger.d("${Instant.ofEpochSecond(it.occurrence).atZone(zoneId)} alarm for ${it.eventId}")
        }

        // we should get 2 alarms for the same occurrence
        assertThat(alarms2.size).isEqualTo(2)

        assertThat(Instant.ofEpochSecond(alarms2[0].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 28, 15, 50, 0, 0, zoneId)
        )
        assertThat(Instant.ofEpochSecond(alarms2[1].occurrence).atZone(zoneId)).isEqualTo(
            ZonedDateTime.of(2020, 12, 14, 16, 0, 0, 0, zoneId)
        )

    }

}
