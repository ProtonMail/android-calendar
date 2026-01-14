package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.fail
import java.text.SimpleDateFormat
import java.util.TimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal class ICalTest {

    val iCal = ICalUtilsImpl

    @Test
    fun `merge calendars for all-day event`() {

        val calendarParts = listOf(
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-02a20ccd-b335-4e4a-2f10-48bc45503993
    DTSTAMP:20200228T170551Z
    SUMMARY:Event after successful connection between database\, usecase and ap
     i
    BEGIN:VALARM
    TRIGGER:-PT20H
    ACTION:DISPLAY
    END:VALARM    
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-02a20ccd-b335-4e4a-2f10-48bc45503993
    DTSTAMP:20200228T170551Z
    DTSTART;VALUE=DATE:20200228
    DTEND;VALUE=DATE:20200229
    BEGIN:VALARM
    TRIGGER:-PT20H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 1.0//EN
    BEGIN:VEVENT
    UID:proton-calendar-02a20ccd-b335-4e4a-2f10-48bc45503993
    DTSTAMP:20200228T170551Z
    BEGIN:VALARM
    TRIGGER:-PT15H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT30H
    ACTION:DISPLAY
    END:VALARM
    BEGIN:VALARM
    TRIGGER:-PT45H
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:kkeK2lGET6ZyVpnHrOAJoXLAWwSO@proton.me
    ATTENDEE;CN=calendarsingle8@protonmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-TOKEN=fc2ea86750f1d0ce99c2350dd37066f995397ddd:mailto:calendarsingle8@protonmail.com
    ATTENDEE;CN=benjaminlovestesting@pm.me;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-TOKEN=0a1d59494c1192807b52008cf4f2ae8ae6e62a25:mailto:benjaminlovestesting@pm.me
    END:VEVENT
    END:VCALENDAR""".trimIndent()
        )

        val mergedCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts) ?: fail("calendar merging failed")
        val mergedEvent = mergedCalendar?.events?.first() ?: fail("calendar merging failed")

        assertThat(mergedEvent.dateStart.value.time).isEqualTo(ZonedDateTime.of(LocalDate.of(2020, 2, 28), LocalTime.MIDNIGHT, ZoneId.systemDefault()).toEpochSecond() * 1000)
        assertThat(mergedEvent.dateEnd.value.time).isEqualTo(ZonedDateTime.of(LocalDate.of(2020, 2, 29), LocalTime.MIDNIGHT, ZoneId.systemDefault()).toEpochSecond() * 1000)
        assertThat(mergedEvent.dateTimeStamp.value.toInstant()).isEqualTo(Instant.ofEpochSecond(1582909551)) // 20200228T170551Z
        assertThat(mergedEvent.summary.value).isEqualTo("Event after successful connection between database, usecase and api")
        assertThat(mergedEvent.alarms.first().action.value).isEqualTo("DISPLAY")
        assertThat(mergedEvent.alarms).hasSize(5)
        assertThat(mergedCalendar.events).hasSize(1)
        assertThat(mergedCalendar.productId.value).isEqualTo("-//Proton Technologies//AndroidCalendar 1.0//EN")
        assertThat(mergedCalendar.events.first().attendees).hasSize(2)
        assertThat(mergedCalendar.events.first().attendees[0].email).isEqualTo("calendarsingle8@protonmail.com")
        assertThat(mergedCalendar.events.first().attendees[1].email).isEqualTo("benjaminlovestesting@pm.me")

    }

    @Test
    fun `merge calendars preserving timezone info`() { // TODO FIXME add RECURRENCE-ID & EXDATES

        val calendarParts = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    DTSTAMP:20200330T155311Z
    SUMMARY:Start on 31st March 10:00\, end on 1st April 18:00
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
    """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    DTSTAMP:20200330T155311Z
    DTSTART;TZID=Europe/Budapest:20200331T100000
    DTEND;TZID=Europe/Budapest:20200401T180000
    END:VEVENT
    END:VCALENDAR""".trimIndent()
        )

        val mergedCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts) ?: fail("calendar merging failed")
        val mergedEvent = mergedCalendar?.events?.first() ?: fail("calendar merging failed")

        assertThat(mergedCalendar.timezoneInfo.getTimezone(mergedEvent.dateStart).timeZone.id).isEqualTo("Europe/Budapest")
        assertThat(mergedCalendar.timezoneInfo.getTimezone(mergedEvent.dateEnd).timeZone.id).isEqualTo("Europe/Budapest")

    }

    @Test
    fun `merge calendars with empty DTSTAMP`() {

        val calendarParts = listOf(
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    SUMMARY:Start on 31st March 10:00\, end on 1st April 18:00
    END:VEVENT
    END:VCALENDAR""".trimIndent(),
            """
    BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-5a1e31ee-0ba7-7094-93d8-26dd58ca8b37
    DTSTAMP:20200330T155311Z
    DTSTART;TZID=Europe/Budapest:20200331T100000
    DTEND;TZID=Europe/Budapest:20200401T180000
    END:VEVENT
    END:VCALENDAR""".trimIndent()
        )

        val mergedCalendar = iCal.mergeCalendarPartsIntoICalendar(calendarParts) ?: fail("calendar merging failed")
        val mergedEvent = mergedCalendar?.events?.first() ?: fail("calendar merging failed")

        val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

        assertThat(mergedEvent.dateTimeStamp.value).isEqualTo(dateFormat.parse("20200330T155311Z"))

    }

    @Test
    fun `properly parse event with empty fields`() {

        val testVCal = """
                BEGIN:VCALENDAR
                PRODID:-//Proton Technologies//ProtonCalendar Beta//EN
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART:20200514T080000Z
                DTEND:20200514T083000Z
                DTSTAMP:20200511T123232Z
                UID:7peg3ed7bodi8shj243ikiaeqi@google.com
                CREATED:20200511T123226Z
                DESCRIPTION:no title
                LAST-MODIFIED:20200511T123226Z
                LOCATION:
                SEQUENCE:0
                STATUS:CONFIRMED
                SUMMARY:
                TRANSP:OPAQUE
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()

        assertThat(ICalUtilsImpl.parseICalString(testVCal)).isNotNull()

    }


}
