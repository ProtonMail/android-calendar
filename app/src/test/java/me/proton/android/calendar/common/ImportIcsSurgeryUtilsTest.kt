package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isNullOrEmpty
import assertk.assertions.isTrue
import biweekly.Biweekly
import biweekly.property.Action
import biweekly.util.Frequency
import biweekly.util.ICalDate
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanAlarms
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanDtStamp
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRRule
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRawIcs
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

internal class ImportIcsSurgeryUtilsTest {

    @Test
    fun `cleanDtstamp missing DTSTAMP for PUBLISH test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-// calendar.com //NONSGML Version 1//EN
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:475i5djdmpjt342igfvfc7bo0c@google.com
    BEGIN:VEVENT
    SUMMARY:test
    DTSTART:20210115T130000Z
    DTEND:20210115T140000Z
    ATTENDEE;CN=test.test@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test.test@pro
     tonmail.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:te
     st.test@protonmail.com
    DESCRIPTION:\n
    ORGANIZER;CN=test@gmail.com:mailto:test@gmail.com
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains(Regex("DTSTAMP:\\d{8}T\\d{6}[Z]"))).isEqualTo(true)
        }
    }

    @Test
    fun `cleanDtstamp DTSTAMP no timezone for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-// calendar.com //NONSGML Version 1//EN
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:475i5djdmpjt342igfvfc7bo0c@google.com
    BEGIN:VEVENT
    SUMMARY:test
    DTSTART:20210115T130000Z
    DTEND:20210115T140000Z
    ATTENDEE;CN=test.test@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test.test@pro
     tonmail.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:te
     st.test@protonmail.com
    DESCRIPTION:\n
    ORGANIZER;CN=test@gmail.com:mailto:test@gmail.com
    DTSTAMP:20210302T115550
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStamp(iCalendar, isImport = true)).isTrue()
            assertThat(iCalendar?.printToString()?.contains(Regex("DTSTAMP:20210302T115550Z"))).isEqualTo(true)
            assertThat(event.dateTimeStamp.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 2, 11, 55, 50,  0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanDtstamp DTSTAMP date no time for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-// calendar.com //NONSGML Version 1//EN
    SEQUENCE:0
    STATUS:CONFIRMED
    UID:475i5djdmpjt342igfvfc7bo0c@google.com
    BEGIN:VEVENT
    SUMMARY:test
    DTSTART:20210115T130000Z
    DTEND:20210115T140000Z
    ATTENDEE;CN=test.test@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test.test@pro
     tonmail.com;PARTSTAT=NEEDS-ACTION;ROLE=REQ-PARTICIPANT;RSVP=TRUE:mailto:te
     st.test@protonmail.com
    DESCRIPTION:\n
    ORGANIZER;CN=test@gmail.com:mailto:test@gmail.com
    DTSTAMP;VALUE=DATE:20210302
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.cleanDtStamp(iCalendar, isImport = true)).isTrue()
            assertThat(iCalendar?.printToString()?.contains(Regex("DTSTAMP:20210302T000000Z"))).isEqualTo(true)
            assertThat(event.dateTimeStamp.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 2, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanTimezones date no timezone for Import test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//DigitalU//NONSGML 32030344//EN
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    SUMMARY:Retrait de votre commande CoursesU.com
    CLASS:PUBLIC
    STATUS:CONFIRMED
    UID:1649423783427@www.coursesu.com
    DTSTART:20220409T103000
    DTEND:20220409T113000
    DTSTAMP:20220408T125428
    LOCATION:Super U LOISIN,RD 1206,74140,LOISIN
    BEGIN:VALARM
    TRIGGER:-PT1H
    DESCRIPTION:Retrait de votre commande CoursesU.com
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")
        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()
        val iCalendar = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar

        assertThat(iCalendar).isNotNull()
        iCalendar?.events?.forEach { event ->
            assertThat(iCalendar?.printToString()?.contains(Regex("DTSTART;TZID=Europe/Paris:20220409T103000"))).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains(Regex("DTEND;TZID=Europe/Paris:20220409T113000"))).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains(Regex("DTSTAMP:20220408T125428Z"))).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 4, 9, 10, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 4, 9, 11, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.dateTimeStamp.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 4, 8, 12, 54, 28, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanAlarms VALARM with RELATED END`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER;RELATED=END:-P2D    
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(2)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(1)
        }
    }

    @Test
    fun `cleanAlarms VALARM with ACTION AUDIO`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:AUDIO
    TRIGGER:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(1)
            assertThat(event.alarms.first().action).isEqualTo(Action(Action.AUDIO))
            event.cleanAlarms()
            assertThat(event.alarms.first().action).isEqualTo(Action(Action.DISPLAY))
        }
    }

    @Test
    fun `cleanAlarms VALARM with duplicates`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(3)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(1)
            assertThat(event.alarms.first().trigger).isNotNull()
        }
    }

    @Test
    fun `cleanAlarms VALARM with null TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(1)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(0)
        }
    }

    @Test
    fun `cleanAlarms VALARM with one duplicate and more than 10`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT1M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT1M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT2M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT3M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT4M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT5M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT6M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT7M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT8M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT9M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT10M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT11M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT12M
    END:VALARM
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT13M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(14)
            event.cleanAlarms()
            assertThat(event.alarms.first().trigger?.duration?.toMillis()).isEqualTo(Duration.ofMinutes(1).toMillis() * -1)
            assertThat(event.alarms.last().trigger?.duration?.toMillis()).isEqualTo(Duration.ofMinutes(10).toMillis() * -1)
            assertThat(event.alarms.size).isEqualTo(10)
        }
    }

    @Test
    fun `cleanAlarms VALARM with positive TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:PT1M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.size).isEqualTo(1)
            event.cleanAlarms()
            assertThat(event.alarms.size).isEqualTo(0)
        }
    }

    @Test
    fun `cleanAlarms part day event VALARM with mixed components TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;TZID=Europe/Paris:20210223T090000
    DTEND;TZID=Europe/Paris:20210223T094000
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-P1W3DT4H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("-P1W3DT4H")
            event.cleanAlarms()
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("PT-244H")
        }
    }

    @Test
    fun `cleanAlarms all day event VALARM with mixed components TRIGGER`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:icalendar
    CALSCALE:GREGORIAN
    METHOD:PUBLISH
    BEGIN:VEVENT
    DTSTAMP:20210204T130756Z
    DTSTART;VALUE=DATE:20210223
    DTEND;VALUE=DATE:20210223
    CLASS:PRIVATE
    PRIORITY:2
    SUMMARY:Test alarms
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-P1W3DT4H
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()
        assertThat(cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).isTrue()
        val cleanICalString = (cleanRawIcsResult as IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful).cleanICalString

        val iCalendar = Biweekly.parse(cleanICalString).first()
        assertThat(iCalendar).isNotNull()
        iCalendar.events.forEach { event ->
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("-P1W3DT4H")
            event.cleanAlarms()
            assertThat(event.alarms.first().trigger.duration.toString()).isEqualTo("-P1W3DT4H")
        }
    }

    @Test
    fun `test handle import invitation`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Recruitee//Recruitee Events//EN
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTART:20201125T130000Z
    DTEND:20201125T140000Z
    CREATED:20201124T170307Z
    DTSTAMP:20201124T170310Z
    RECURRENCE-ID:20201125T130000Z
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=WE
    ORGANIZER;CN=Test:MAILTO:testOrga@proton.me
    ATTENDEE;CN=Test:MAILTO:testAttendee@proton.me
    SUMMARY:Test this out
    DESCRIPTION:Test this description
    LOCATION:Test this location
    UID:123456970bluemnday@recruitee.com
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val expectedUid = "original-uid-123456970bluemnday@recruitee.com-sha1-uid-2f56b753fd19967006672eaa365fec86821c8e74"
        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.firstOrNull()?.recurrenceId).isNull()
            assertThat(iCalendar?.events?.firstOrNull()?.alarms).isNullOrEmpty()
            assertThat(iCalendar?.events?.firstOrNull()?.uid?.value).isEqualTo(expectedUid)
        }
    }

    @Test
    fun `ics import from file generate UID`() {
        val file = File("./src/test/resources/importWithNoUid.ics")

        assertThat(file).isNotNull()

        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream(file.name)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val event = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar?.events?.firstOrNull()
        assertThat(event?.uid?.value).isEqualTo("sha1-uid-45bcf24f9032a3f0865fa55492876b770e96e5ab")
    }

    @Test
    fun `ics import from file generate UID with short original UID`() {
        val file = File("./src/test/resources/importWithShortUid.ics")

        assertThat(file).isNotNull()

        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream(file.name)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val event = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar?.events?.firstOrNull()
        assertThat(event?.uid?.value).isEqualTo("original-uid-123456970bluemnday@recruitee.com-sha1-uid-db2828aaf1b1084fbf004530e2008f945fb796fd")
    }

    @Test
    fun `ics import from file generate UID with long original UID`() {
        val file = File("./src/test/resources/importWithLongUid.ics")

        assertThat(file).isNotNull()

        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream(file.name)
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris", isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        val event = (cleanIcsResult as IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).iCalendar?.events?.firstOrNull()
        assertThat(event?.uid?.value).isEqualTo("original-uid-FmcWKJ0q0eeNWIN4OLZ8yJnSDdC8DT9CndSxOnnPC47VWjQHu0psXB25lZuCt4EWsWAtgmCPWe1Wa0AIL0y8rlPn0qbB05u3WuyOst8XYkJNWz6gYx@recruitee.com-sha1-uid-a058b52a132530144c9fa59597036c8aac8ae550")
    }

    @Test
    fun `test all day date without VALUE=DATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Calendar Labs//Calendar 1.0//EN
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    X-WR-CALNAME:US Holidays
    X-WR-TIMEZONE:Etc/GMT
    BEGIN:VEVENT
    DTSTART:20220101
    DTEND:20220102
    UID:636a37c4d8b361667905476@calendarlabs.com
    DTSTAMP:20221108T110436Z
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;VALUE=DATE:20220101")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;VALUE=DATE:20220102")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Disabled // TODO For now we ignore this test because biweekly doesn't parse DTSTART with VALUE=DATE-TIME as a DATE-TIME, we need to parse using regex before biweekly to ICalendar
    @Test
    fun `test Date time formatted as Date`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Calendar Labs//Calendar 1.0//EN
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    X-WR-CALNAME:US Holidays
    X-WR-TIMEZONE:Etc/GMT
    BEGIN:VEVENT
    DTSTART;VALUE=DATE-TIME:20220101
    DTEND;VALUE=DATE-TIME:20220102
    UID:636a37c4d8b361667905476@calendarlabs.com
    DTSTAMP:20221108T110436Z
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;VALUE=DATE-TIME:20220101T000000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;VALUE=DATE-TIME:20220101T000000Z")).isEqualTo(true)
        }
    }

    @Test
    fun `test DTEND before DTSTART`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART:20211130T080000Z
    DTEND:20211030T090000Z
    DTSTAMP:20211129T130415Z
    UID:1o2b18ap2lqbcgckgugthjic1de1@google.com
    CREATED:20211129T130414Z
    DESCRIPTION:Lorem ipsum dolor sit amet\, consectetur adipiscing elit\, sed
     do eiusmod tempor incididunt ut labore et dolore magna aliqua.Lorem ipsum d
    LAST-MODIFIED:20211129T130414Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:End date is earlier2
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
            //  set to avoid NPE in the app. We remove it when sending the ICS to BE.
            assertThat(iCalendar?.printToString()?.contains("DTEND:20211130T080000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 11, 30, 9, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(iCalendar?.events?.first()?.dateStart?.value)
        }
    }

    @Test
    fun `test DTSTAMP with VALUE=DATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;VALUE=DATE:20200131
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T000000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2020, 1, 31, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `test DTSTAMP with floating date time`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP:20200131T151111
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T151111Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2020, 1, 31, 15, 11, 11, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `test DTSTAMP with TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;TZID=America/Montevideo:20200131T151111
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T181111Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2020, 1, 31, 18, 11, 11, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `test DTSTAMP with TZID and VALUE=DATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;TZID=America/Montevideo;VALUE=DATE:20200131
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20200131T030000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2020, 1, 31, 3, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `test DTSTAMP with TZID and Zulu marker`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;TZID=America/New_York:20221012T171500
    DTEND;TZID=America/New_York:20221012T182500
    DTSTAMP;TZID=Europe/Brussels:20221026T063929Z
    UID:11353R6@voltigeursbourget.com
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221026T063929Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 26, 6, 39, 29, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `test empty TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    TZID:Europe/Zurich
    METHOD:REQUEST
    BEGIN:VEVENT
    UID:random
    DTSTART;TZID=:20221027T103000Z
    DTEND;TZID=:20221027T113000Z
    DTSTAMP;TZID=:20221027T022510Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20221027T103000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND:20221027T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221027T022510Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 27, 12, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 27, 13, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 27, 2, 25, 10, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `test floating Date time with no X-WR-TIMEZONE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:-//xxxx//NONSGML agenda//FR
    VERSION:2.0
    X-WR-CALNAME: xxxx
    BEGIN:VTIMEZONE
    TZID:Europe/Paris
    BEGIN:DAYLIGHT
    TZOFFSETFROM:+0100
    TZOFFSETTO:+0200
    TZNAME:CEST
    DTSTART:19700329T020000
    RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3
    END:DAYLIGHT
    BEGIN:STANDARD
    TZOFFSETFROM:+0200
    TZOFFSETTO:+0100
    TZNAME:CET
    DTSTART:19701025T030000
    RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10
    END:STANDARD
    END:VTIMEZONE
    BEGIN:VEVENT
    UID:hwkhwkhteke
    DTSTAMP:20210920T080000Z
    DTSTART:20210920T080000
    DTEND:20210920T120000
    SUMMARY:TD AN0501
    DESCRIPTION:xxxx
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Europe/Paris:20210920T080000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Europe/Paris:20210920T120000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 9, 20, 8, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 9, 20, 12, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `test floating Date time with X-WR-TIMEZONE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID;X-RICAL-TZSOURCE=TZINFO:-//com.denhaven2/NONSGML ri_cal gem//EN
    CALSCALE:GREGORIAN
    VERSION:2.0
    X-PUBLISHED-TTL:PT10M
    X-WR-TIMEZONE:Asia/Taipei
    BEGIN:VEVENT
    DTSTART;VALUE=DATE-TIME:20210824T100000
    DTEND;VALUE=DATE-TIME:20210824T145000
    DTSTAMP:20230109T080000Z
    UID:1132139459
    URL:xxxx
    SUMMARY:yyyy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Asia/Taipei:20210824T100000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Asia/Taipei:20210824T145000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 8, 24, 10, 0, 0, 0, ZoneId.of("Asia/Taipei")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 8, 24, 14, 50, 0, 0, ZoneId.of("Asia/Taipei")).toInstant()
            ))
        }
    }

    @Test
    fun `test floating time PM with X-WR-TIMEZONE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID;X-RICAL-TZSOURCE=TZINFO:-//com.denhaven2/NONSGML ri_cal gem//EN
    CALSCALE:GREGORIAN
    VERSION:2.0
    X-PUBLISHED-TTL:PT10M
    X-WR-TIMEZONE:America/Los_Angeles
    BEGIN:VEVENT
    DTSTART;VALUE=DATE-TIME:20210824T130000
    DTEND;VALUE=DATE-TIME:20210824T155000
    DTSTAMP:20210920T080000Z
    UID:1132139459
    URL:xxxx
    SUMMARY:yyyy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=America/Los_Angeles:20210824T130000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=America/Los_Angeles:20210824T155000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 8, 24, 13, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 8, 24, 15, 50, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant()
            ))
        }
    }

    @Test
    fun `test floating time with ZULU marker and X-WR-TIMEZONE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID;X-RICAL-TZSOURCE=TZINFO:-//com.denhaven2/NONSGML ri_cal gem//EN
    CALSCALE:GREGORIAN
    VERSION:2.0
    X-PUBLISHED-TTL:PT10M
    X-WR-TIMEZONE:Asia/Taipei
    BEGIN:VEVENT
    DTSTART;VALUE=DATE-TIME:20210824T100000Z
    DTEND;VALUE=DATE-TIME:20210824T205000
    DTSTAMP:20210920T080000Z
    UID:1132139459
    URL:xxxx
    SUMMARY:yyyy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Asia/Taipei:20210824T180000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Asia/Taipei:20210824T205000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 8, 24, 18, 0, 0, 0, ZoneId.of("Asia/Taipei")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 8, 24, 20, 50, 0, 0, ZoneId.of("Asia/Taipei")).toInstant()
            ))
        }
    }

    @Test
    fun `test format all day event EXDATE with time`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    PRODID:-//ddaysoftware.com//NONSGML DDay.iCal 1.0//EN
    X-WR-CALNAME:yyyy
    BEGIN:VEVENT
    DTSTAMP:20210902T041500Z
    UID:event-assignment-652662
    DTSTART;VALUE=DATE:20220914
    DTEND;VALUE=DATE:20220915
    EXDATE;VALUE=DATE-TIME:20220916T080000Z
    RRULE:FREQ=DAILY
    CLASS:PUBLIC
    SEQUENCE:0
    SUMMARY: test summary
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("EXDATE;VALUE=DATE:20220916")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.exceptionDates?.first()?.values?.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 9, 16, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `test format all day event with time`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    PRODID:-//ddaysoftware.com//NONSGML DDay.iCal 1.0//EN
    X-WR-CALNAME:yyyy
    BEGIN:VEVENT
    DTSTAMP:20210902T041500Z
    UID:event-assignment-652662
    DTSTART;VALUE=DATE:20210901T000000
    DTEND;VALUE=DATE:20210902T000000
    CLASS:PUBLIC
    SEQUENCE:0
    SUMMARY: test summary
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;VALUE=DATE:20210901")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;VALUE=DATE:20210902")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 9, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 9, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `test long event DESCRIPTION`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    X-WR-CALNAME:Full day
    BEGIN:VEVENT
    DESCRIPTION:abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890
    UID:040000008200E00074C5B7101A82E0080000000014E7703F955ED701000000000000000
     0100000002FCAA4D4BCE40047A639A52FC42692FF
    SUMMARY:Edit1
    DTSTART;VALUE=DATE:20210621
    DTEND;VALUE=DATE:20210622
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20210705T093423Z
    TRANSP:TRANSPARENT
    STATUS:CONFIRMED
    SEQUENCE:0
    LOCATION:
    X-MICROSOFT-CDO-APPT-SEQUENCE:0
    X-MICROSOFT-CDO-BUSYSTATUS:FREE
    X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY
    X-MICROSOFT-CDO-ALLDAYEVENT:TRUE
    X-MICROSOFT-CDO-IMPORTANCE:1
    X-MICROSOFT-CDO-INSTTYPE:3
    X-MICROSOFT-DONOTFORWARDMEETING:FALSE
    X-MICROSOFT-DISALLOW-COUNTER:FALSE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.description?.value?.length).isEqualTo(3000)
        }
    }

    @Test
    fun `test long event DESCRIPTION with special chars`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    X-WR-CALNAME:Full day
    BEGIN:VEVENT
    DESCRIPTION:úÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁíúÞóðóÁí
    UID:040000008200E00074C5B7101A82E0080000000014E7703F955ED701000000000000000
     0100000002FCAA4D4BCE40047A639A52FC42692BB
    SUMMARY:Edit1
    DTSTART;VALUE=DATE:20210621
    DTEND;VALUE=DATE:20210622
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20210705T093423Z
    TRANSP:TRANSPARENT
    STATUS:CONFIRMED
    SEQUENCE:0
    LOCATION:
    X-MICROSOFT-CDO-APPT-SEQUENCE:0
    X-MICROSOFT-CDO-BUSYSTATUS:FREE
    X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY
    X-MICROSOFT-CDO-ALLDAYEVENT:TRUE
    X-MICROSOFT-CDO-IMPORTANCE:1
    X-MICROSOFT-CDO-INSTTYPE:3
    X-MICROSOFT-DONOTFORWARDMEETING:FALSE
    X-MICROSOFT-DISALLOW-COUNTER:FALSE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.description?.value?.length).isEqualTo(3000)
        }
    }

    @Test
    fun `test microsoft TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VTIMEZONE
    TZID:mountain time (us & canada)
    X-LIC-LOCATION:mountain time (us & canada)
    BEGIN:DAYLIGHT
    TZOFFSETFROM:-0700
    TZOFFSETTO:-0600
    TZNAME:MDT
    DTSTART:19700308T020000
    RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=2SU
    END:DAYLIGHT
    BEGIN:STANDARD
    TZOFFSETFROM:-0600
    TZOFFSETTO:-0700
    TZNAME:MST
    DTSTART:19701101T020000
    RRULE:FREQ=YEARLY;BYMONTH=11;BYDAY=1SU
    END:STANDARD
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;TZID=mountain time (us & canada):20220101T123000
    DTEND;TZID=mountain time (us & canada):20220101T133000
    RRULE:FREQ=DAILY;UNTIL=20220202T065959Z
    DTSTAMP:20211206T100422Z
    UID:4tn4qih3pa3n6a00s8h0numd7d2@google.com
    X-MICROSOFT-CDO-OWNERAPPTID:-548145106
    CREATED:20211206T100422Z
    DESCRIPTION:mountain time (us & canada) > America/Denver; DTSTART 12:30
    LAST-MODIFIED:20211206T100422Z
    SEQUENCE:0
    SUMMARY:Timezones: mountain time (us & canada) > America/Denver
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=America/Denver:20220101T123000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=America/Denver:20220101T133000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 1, 12, 30, 0, 0, ZoneId.of("America/Denver")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 1, 13, 30, 0, 0, ZoneId.of("America/Denver")).toInstant()
            ))
        }
    }

    @Test
    fun `test event with DTSTART in CEST`() {

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
        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = timeZoneId)

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Europe/Berlin:20220402T130000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Europe/Berlin:20220402T140000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 4, 2, 13, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 4, 2, 14, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant()
            ))
        }
    }

    @Test
    fun `test event with DTSTART in CET`() {

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
        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = timeZoneId)

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Europe/Berlin:20211102T130000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Europe/Berlin:20211102T140000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 11, 2, 13, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 11, 2, 14, 0, 0, 0, ZoneId.of("Europe/Berlin")).toInstant()
            ))
        }
    }

    @Test
    fun `test missing DTSTAMP for REQUEST`() {

        val iCalString = """
    BEGIN:VCALENDAR
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    VERSION:2.0
    PRODID:-//ddaysoftware.com//NONSGML DDay.iCal 1.0//EN
    X-WR-CALNAME:yyyy
    BEGIN:VEVENT
    DESCRIPTION:
    DTEND;VALUE=DATE:20210610
    DTSTART;VALUE=DATE:20210609
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:xxx
    UID:lekt_1048130
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains(Regex("DTSTAMP:\\d{8}T\\d{6}[Z]"))).isEqualTo(true)
        }
    }

    @Test
    fun `test missing SEQUENCE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    X-WR-CALNAME:google
    X-WR-TIMEZONE:Europe/Prague
    BEGIN:VEVENT
    DTSTART:20210622T144500Z
    DTEND:20210622T151500Z
    DTSTAMP:20210903T144702Z
    UID:144B00A6-8BAD-4224-AB45-B4AC33891EFA
    ORGANIZER:mailto:aaaa@group.calendar.google.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;CN=Adrien
      Blanc;X-NUM-GUESTS=0:mailto:adrien@gmail.com
    SUMMARY:Busy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("SEQUENCE:0")).isEqualTo(true)
        }
    }

    @Test
    fun `test multiple EXDATE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    X-WR-CALNAME:Calendar
    BEGIN:VEVENT
    DESCRIPTION:Event 1
    RRULE:FREQ=WEEKLY;UNTIL=20221118T193000Z;INTERVAL=1;BYDAY=FR;WKST=MO
    EXDATE;TZID=Eastern Standard Time:20201225T143000,20210101,20210305T
     143000,20210507T143000,20210528,20210702T143000,20210903,202
     11008T143000,20211126,20211224T143000,20211231T143000
    UID:wnnvwhowjsngwjgljljwljl
    SUMMARY:Event 1
    DTSTART;VALUE=DATE;TZID=Eastern Standard Time:20201204
    DTEND;VALUE=DATE;TZID=Eastern Standard Time:20201205
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20211122T163549Z
    TRANSP:OPAQUE
    STATUS:CONFIRMED
    SEQUENCE:2
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"
        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = timeZoneId)

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            val expectedExDates = arrayListOf<Date>(
                // 20201225
                ICalDate.from(ZonedDateTime.of(2020, 12, 25, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20210101
                ICalDate.from(ZonedDateTime.of(2021, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20210305
                ICalDate.from(ZonedDateTime.of(2021, 3, 5, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20210507
                ICalDate.from(ZonedDateTime.of(2021, 5, 7, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20210528
                ICalDate.from(ZonedDateTime.of(2021, 5, 28, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20210702
                ICalDate.from(ZonedDateTime.of(2021, 7, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20210903
                ICalDate.from(ZonedDateTime.of(2021, 9, 3, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20211008
                ICalDate.from(ZonedDateTime.of(2021, 10, 8, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20211126
                ICalDate.from(ZonedDateTime.of(2021, 11, 26, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20211224
                ICalDate.from(ZonedDateTime.of(2021, 12, 24, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()),
                // 20211231
                ICalDate.from(ZonedDateTime.of(2021, 12, 31, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant())
            )
            assertThat(iCalendar?.events?.first()?.exceptionDates?.map { it.values.first() }?.forEachIndexed { index, exceptionDate ->
                assertThat(exceptionDate).isEqualTo(expectedExDates[index])
            })
        }
    }

    @Test
    fun `test multiple EXDATE with Microsoft TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    X-WR-CALNAME:Calendar
    BEGIN:VEVENT
    DESCRIPTION:Event 1
    RRULE:FREQ=WEEKLY;UNTIL=20221118T193000Z;INTERVAL=1;BYDAY=FR;WKST=MO
    EXDATE;TZID=Eastern Standard Time:20201225T143000,20210101T143000,20210305T
     143000,20210507T143000,20210528T143000,20210702T143000,20210903T143000,202
     11008T143000,20211126T143000,20211224T143000,20211231T143000
    UID:wnnvwhowjsngwjgljljwljl
    SUMMARY:Event 1
    DTSTART;TZID=Eastern Standard Time:20201204T143000
    DTEND;TZID=Eastern Standard Time:20201204T163000
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20211122T163549Z
    TRANSP:OPAQUE
    STATUS:CONFIRMED
    SEQUENCE:2
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val timeZoneId = "Europe/Paris"
        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = timeZoneId)

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            val exDatesTimeZoneId = ZoneId.of("America/New_York")
            val expectedExDates = arrayListOf<Date>(
                //20201225T143000
                ICalDate.from(ZonedDateTime.of(2020, 12, 25, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20210101T143000
                ICalDate.from(ZonedDateTime.of(2021, 1, 1, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20210305T143000
                ICalDate.from(ZonedDateTime.of(2021, 3, 5, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20210507T143000
                ICalDate.from(ZonedDateTime.of(2021, 5, 7, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20210528T143000
                ICalDate.from(ZonedDateTime.of(2021, 5, 28, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20210702T143000
                ICalDate.from(ZonedDateTime.of(2021, 7, 2, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20210903T143000
                ICalDate.from(ZonedDateTime.of(2021, 9, 3, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20211008T143000
                ICalDate.from(ZonedDateTime.of(2021, 10, 8, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20211126T143000
                ICalDate.from(ZonedDateTime.of(2021, 11, 26, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20211224T143000
                ICalDate.from(ZonedDateTime.of(2021, 12, 24, 14, 30, 0, 0, exDatesTimeZoneId).toInstant()),
                //20211231T143000
                ICalDate.from(ZonedDateTime.of(2021, 12, 31, 14, 30, 0, 0, exDatesTimeZoneId).toInstant())
            )
            assertThat(iCalendar?.events?.first()?.exceptionDates?.map { it.values.first() }?.forEachIndexed { index, exceptionDate ->
                assertThat(exceptionDate).isEqualTo(expectedExDates[index])
                assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events?.first()?.exceptionDates?.get(index))?.timeZone?.id).isEqualTo("America/New_York")
            })
        }
    }

    @Test
    fun `test with Windows TZID`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VTIMEZONE
    TZID:nuku'alofa
    BEGIN:STANDARD
    TZOFFSETFROM:+1300
    TZOFFSETTO:+1300
    TZNAME:+13
    DTSTART:19700101T000000
    END:STANDARD
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;TZID=nuku'alofa:20220111T223000
    DTEND;TZID=nuku'alofa:20220111T233000
    RRULE:FREQ=MONTHLY;UNTIL=20230101T105959Z;BYDAY=2TU
    DTSTAMP:20220111T162244Z
    ORGANIZER;CN=Email Notifications:mailto:pn5gf1n83ukmudv1r30avpp4qo@group.ca
     lendar.google.com
    UID:4hkvne86or8s6u9ap1puq615db2@google.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=calendaruser@pm.me;X-NUM-GUESTS=0:mailto:calendaruser@pm.me
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=calendar-user-pentest1@protonmail.com;X-NUM-GUESTS=0:mailto:calenda
     r-user-pentest1@protonmail.com
    X-MICROSOFT-CDO-OWNERAPPTID:-1255746708
    CREATED:20220111T162243Z
    DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~::~:~::-\nDo not edit this section of the description.\n\nV
     iew your event at https://calendar.google.com/calendar/event?action=VIEW&ei
     d=NGhrdm5lODZvcjhzNnU5YXAxcHVxNjE1ZGIgY2FsZW5kYXItdXNlci1wZW50ZXN0MUBwcm90b
     25tYWlsLmNvbQ&tok=NTIjcG41Z2YxbjgzdWttdWR2MXIzMGF2cHA0cW9AZ3JvdXAuY2FsZW5kY
     XIuZ29vZ2xlLmNvbWVkODkwY2YzMTYyN2E2NzM1MmI2ZDAxYWZiMGUzMTVhMmYzZTdmMWI&ctz=
     Europe%2FVilnius&hl=en_GB&es=1.\n-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:
     ~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~::~:~::-
    LAST-MODIFIED:20220111T162243Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Tongatapu nuku'alofa
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Pacific/Tongatapu:20220111T223000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Pacific/Tongatapu:20220111T233000")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 11, 22, 30, 0, 0, ZoneId.of("Pacific/Tongatapu")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 11, 23, 30, 0, 0, ZoneId.of("Pacific/Tongatapu")).toInstant()
            ))
        }
    }

    @Test
    fun `test UID outside of VEVENT`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    UID:0flbaqgv2ghu1fgo2oeeaqe1pb123@google.com
    BEGIN:VEVENT
    DTSTART:20211129T100000Z
    DTEND:20211129T110000Z
    DTSTAMP:20211129T092446Z
    ORGANIZER;CN=iamblueuser@gmail.com:mailto:iamblueuser@gmail.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=calendar-user-pentest1@protonmail.com;X-NUM-GUESTS=0:mailto:calenda
     r-user-pentest1@protonmail.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=1;X-NUM-GUESTS=0:mailto:calendarUser@protonmail.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE
     ;CN=iamblueuser@gmail.com;X-NUM-GUESTS=0:mailto:iamblueuser@gmail.com
    X-MICROSOFT-CDO-OWNERAPPTID:527559693
    CREATED:20211129T092445Z
    DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~::~:~::-\nDo not edit this section of the description.\n\nV
     iew your event at https://calendar.google.com/calendar/event?action=VIEW&ei
     d=MGZsYmFxZ3YyZ2h1MWZnbzJvZWVhcWUxcGIgY2FsZW5kYXItdXNlci1wZW50ZXN0MUBwcm90b
     25tYWlsLmNvbQ&tok=MjEjaWFtYmx1ZXVzZXJAZ21haWwuY29tNjJkNTRmNmIxMmEzNWQ0YmI5N
     jYzM2RlMzAxNzIzMTU0Mzk5OTJmNg&ctz=Europe%2FVilnius&hl=en_GB&es=1.\n-::~:~::
     ~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~::~:
     ~::-
    LAST-MODIFIED:20211129T092445Z
    LOCATION:
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:UID outside VEVENT
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("UID:original-uid-0flbaqgv2ghu1fgo2oeeaqe1pb123@google.com-sha1-uid-e120d75c\r\n 32f3d2bd23cd5a3601f0169a3ed64056")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.uid?.value).isEqualTo("original-uid-0flbaqgv2ghu1fgo2oeeaqe1pb123@google.com-sha1-uid-e120d75c32f3d2bd23cd5a3601f0169a3ed64056")
            assertThat(iCalendar?.uid).isEqualTo(null)
        }
    }

    @Test
    fun `test status TENTATIVE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    VERSION:2.0
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    X-WR-CALNAME:yyyy
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210609
    DTEND;VALUE=DATE:20210610
    DTSTAMP:20211129T092446Z
    SEQUENCE:0
    STATUS:TENTATIVE
    SUMMARY:xxx
    UID:wtf
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("STATUS:TENTATIVE")).isEqualTo(false)
            assertThat(iCalendar?.events?.first()?.status).isEqualTo(null)
        }
    }

    @Test
    fun `test all day event with DTSTART equal to DTEND`() {

        val iCalString = """
    BEGIN:VCALENDAR
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    VERSION:2.0
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    X-WR-CALNAME:yyyy
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210609
    DTEND;VALUE=DATE:20210609
    DTSTAMP:20211129T092446Z
    SEQUENCE:0
    STATUS:TENTATIVE
    SUMMARY:xxx
    UID:wtf
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;VALUE=DATE:20210609")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;VALUE=DATE:20210610")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 9, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 10, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `test part day event with DTSTART equal to DTEND`() {

        val iCalString = """
    BEGIN:VCALENDAR
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    VERSION:2.0
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    X-WR-CALNAME:yyyy
    BEGIN:VEVENT
    DTSTART:20210609T120000Z
    DTEND:20210609T120000Z
    DTSTAMP:20211129T092446Z
    SEQUENCE:0
    STATUS:TENTATIVE
    SUMMARY:xxx
    UID:wtf
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20210609T120000Z")).isEqualTo(true)
            //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
            //  set to avoid NPE in the app. We remove it when sending the ICS to BE.
            assertThat(iCalendar?.printToString()?.contains("DTEND:20210609T120000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 9, 14, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 9, 14, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRRule COUNT over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;COUNT=50
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210313T160000
    EXDATE;TZID=Europe/Paris:20210320T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule::class)

    }

    @Test
    fun `Multiple DATETIME with Time and Zulu markers in lower case test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART:20221024t090000z
    DTSTART:20221024t090000
    DTSTART;TZID=Atlantic/Azores:20221024t090000
    DTSTART;VALUE=DATE-TIME:20221024t090000z
    DTSTART;VALUE=DATE-TIME;TZID=Atlantic/Azores:20221024t090000z
    DTEND:20221024t113000z
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanRawIcsResult = iCalString.cleanRawIcs()

        assertThat(cleanRawIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful::class)

        if (cleanRawIcsResult is IcsSurgeryUtils.HandleIcsResult.RawParsingSuccessful) {
            val cleanICalString = cleanRawIcsResult.cleanICalString

            // This lets us test the regex that turns Time and Zulu markers to uppercase.
            // Use a TZID that contains both a t and a z
            assertThat(cleanICalString.contains("DTSTART:20221024T090000Z")).isEqualTo(true)
            assertThat(cleanICalString.contains("DTSTART:20221024T090000")).isEqualTo(true)
            assertThat(cleanICalString.contains("DTSTART;TZID=Atlantic/Azores:20221024T090000")).isEqualTo(true)
            assertThat(cleanICalString.contains("DTSTART;VALUE=DATE-TIME:20221024T090000Z")).isEqualTo(true)
            assertThat(cleanICalString.contains("DTSTART;VALUE=DATE-TIME;TZID=Atlantic/Azores:20221024T090000Z")).isEqualTo(true)
            assertThat(cleanICalString.contains("DTEND:20221024T113000Z")).isEqualTo(true)
            assertThat(cleanICalString.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
        }
    }

    @Test
    fun `DATETIME with Time and Zulu markers in lower case test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART:20221024t090000z
    DTEND:20221024T113000z
    DTSTAMP:20221024t113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20221024T090000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `DATETIME missing seconds test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART:20221024T0900
    DTEND:20221024T113000
    DTSTAMP:20221024T1130Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            // Missing seconds
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=Europe/Paris:20221024T090000")).isEqualTo(true)
            // No fix applied
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=Europe/Paris:20221024T113000")).isEqualTo(true)
            // Missing seconds but has Zulu marker
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 9, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `DATETIME in ISO format test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART:2022-10-24T09:00:00.000Z
    DTEND:2022-10-24T11.30.000Z
    DTSTAMP:2022-10-24T11:30:00.000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20221024T090000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `DATETIME with double Zulu marker test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART:20221024T090000ZZ
    DTEND:20221024T113000ZZ
    DTSTAMP:20221024T113000ZZ
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20221024T090000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `DATETIME with whitespace in property test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART: 20221024T090000Z
    DTEND: 20221024T113000Z
    DTSTAMP: 20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART:20221024T090000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 9, 0, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `DATETIME with Microsoft TZID and with whitespace in property test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;TZID=eastern time (us & canada): 20221024T090000
    DTEND;TZID=eastern time (us & canada): 20221024T113000
    DTSTAMP: 20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;TZID=America/New_York:20221024T090000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;TZID=America/New_York:20221024T113000")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 9, 0, 0, 0, ZoneId.of("America/New_York")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("America/New_York")).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `DATE missing VALUE=DATE and DTSTART == DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART:20221024
    DTEND:20221024
    DTSTAMP: 20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("DTSTART;VALUE=DATE:20221024")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTEND;VALUE=DATE:20221025")).isEqualTo(true)
            assertThat(iCalendar?.printToString()?.contains("DTSTAMP:20221024T113000Z")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.dateStart?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateEnd?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 25, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(iCalendar?.events?.first()?.dateTimeStamp?.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 24, 11, 30, 0, 0, ZoneId.of("UTC")).toInstant()
            ))
        }
    }

    @Test
    fun `Basic YEARLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=YEARLY;INTERVAL=1
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.recurrenceRule?.value?.frequency).isEqualTo(Frequency.YEARLY)
        }
    }

    @Test
    fun `Custom YEARLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=1SU;BYMONTH=11
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `Basic DAILY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=DAILY;INTERVAL=1
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.recurrenceRule?.value?.frequency).isEqualTo(Frequency.DAILY)
        }
    }

    @Test
    fun `Custom DAILY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=DAILY;INTERVAL=1;BYDAY=1SU
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `Basic WEEKLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,TH
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.recurrenceRule?.value?.frequency).isEqualTo(Frequency.WEEKLY)
        }
    }

    @Test
    fun `Custom WEEKLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=1SU;BYMONTH=1
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `Basic MONTHLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=MO,FR
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.recurrenceRule?.value?.frequency).isEqualTo(Frequency.MONTHLY)
        }
    }

    @Test
    fun `MONTHLY BYSETPOS invalid RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=MO,FR;BYSETPOS=2
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        // this RRULE does not create DTSTART as 1st occurrence so we treat it as invalid
        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule::class)
    }

    @Test
    fun `BYDAY and BYSETPOS combined MONTHLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221012
    DTEND;VALUE=DATE:20221012
    RRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=2WE
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.recurrenceRule?.value?.frequency).isEqualTo(Frequency.MONTHLY)
        }
    }

    @Test
    fun `Custom MONTHLY RRule test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221024
    DTEND;VALUE=DATE:20221024
    RRULE:FREQ=MONTHLY;INTERVAL=1;BYDAY=MO,FR;BYSETPOS=2;BYMONTH=1
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:3
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `SEQUENCE with value over Int limit test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221012
    DTEND;VALUE=DATE:20221012
    RRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=2WE
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:2205082007
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.sequence?.value).isEqualTo((2205082007 % 2147483648).toInt())
            assertThat(Regex("SEQUENCE").findAll(iCalendar?.printToString()!!).count()).isEqualTo(1)
            assertThat(iCalendar.printToString().contains("SEQUENCE:57598359")).isEqualTo(true)
        }
    }

    @Test
    fun `SEQUENCE with value negative Int limit test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221012
    DTEND;VALUE=DATE:20221012
    RRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=2WE
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:-2147483648
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.sequence?.value).isEqualTo(0)
            assertThat(Regex("SEQUENCE").findAll(iCalendar?.printToString()!!).count()).isEqualTo(1)
            assertThat(iCalendar.printToString().contains("SEQUENCE:0")).isEqualTo(true)
        }
    }

    @Test
    fun `SEQUENCE with value positive Int limit test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:PUBLISH
    BEGIN:VEVENT
    CLASS:PUBLIC
    SUMMARY:blalbla
    DESCRIPTION:blalbla
    DTSTART;VALUE=DATE:20221012
    DTEND;VALUE=DATE:20221012
    RRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=2WE
    DTSTAMP:20221024T113000Z
    TRANSP:OPAQUE
    SEQUENCE:2147483647
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = IcsSurgeryUtils.cleanIcs(iCalString, timeZoneId = "Europe/Paris")

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.events?.first()?.sequence?.value).isEqualTo(2147483647)
            assertThat(Regex("SEQUENCE").findAll(iCalendar?.printToString()!!).count()).isEqualTo(1)
            assertThat(iCalendar.printToString().contains("SEQUENCE:2147483647")).isEqualTo(true)
        }
    }
}