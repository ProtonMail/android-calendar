package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import biweekly.util.ICalDate
import me.proton.android.calendar.common.IcsParsingValidation.DESCRIPTION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.LOCATION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.SUMMARY_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.UID_MAX_LENGTH
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanDtStart
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanIcs
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRecurrenceId
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date


internal class InviteIcsSurgeryUtilsTest {

    @Test
    fun `clean ics success test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    ATTENDEE;CN=breakingcalendar+alias@pm.me;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PAR
     TSTAT=NEEDS-ACTION;X-PM-TOKEN=1980a5594f21b76eb27cc969081cf2b85c14895b:mail
     to:breakingcalendar+alias@pm.me
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    ORGANIZER;CN=benjaminlovesdebugging@pm.me:mailto:benjaminlovesdebugging@pm.
     me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
        }
    }

    @Test
    fun `drop unsupported properties in ics test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:TENTATIVE
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    ATTENDEE;CN=breakingcalendar+alias@pm.me;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PAR
     TSTAT=NEEDS-ACTION;X-PM-TOKEN=1980a5594f21b76eb27cc969081cf2b85c14895b:mail
     to:breakingcalendar+alias@pm.me
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    ORGANIZER;CN=benjaminlovesdebugging@pm.me:mailto:benjaminlovesdebugging@pm.
     me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            // TENTATIVE is valid, but unsupported -- we drop entire property regardless of value anyway
            assertThat(event.status).isNull()
        }
    }

    @Test
    fun `cleanRawIcs all day event with time test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;VALUE=DATE:20200101T120000Z
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.dateStart.value.hasTime()).isFalse()
        }
    }

    @Test
    fun `All day event with missing VALUE=DATE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART:20200101
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!
            assertThat(event.dateStart.value).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2020, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()
                    ).toInstant()
                )
            )
            assertThat(event.dateEnd.value).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2020, 1, 2, 0, 0, 0, 0, ZoneId.systemDefault()
                    ).toInstant()
                )
            )
            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20200101")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20200102")).isEqualTo(true)
        }
    }

    @Test
    fun `cleanRawIcs part day floating time event with no X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART:20200101T120000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanICalString = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanICalString is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateOrDateTimeProperty).isTrue()
    }

    @Test
    fun `cleanTimezones part day floating time event with X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:Europe/Vilnius
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART:20200102T133000
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!
            assertThat(event.dateStart.value).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2020, 1, 2, 13, 30, 0, 0, ZoneId.of(
                            "Europe/Vilnius"
                        )
                    ).toInstant()
                )
            )
            assertThat(iCalendar.timezoneInfo.getTimezone(event.dateStart).globalId).isEqualTo("Europe/Vilnius")
            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Vilnius:20200102T133000")).isEqualTo(true)
        }
    }

    @Test
    fun `cleanTimezones part day zulu time event with X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:Europe/Vilnius
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART:20200102T133000Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.cleanDtStart()).isTrue()
            assertThat(event.dateStart.value).isEqualTo(
                ICalDate.from(
                    ZonedDateTime.of(
                        2020, 1, 2, 15, 30, 0, 0, ZoneId.of(
                            "Europe/Vilnius"
                        )
                    ).toInstant()
                )
            )
            assertThat(iCalendar.timezoneInfo.getTimezone(event.dateStart).globalId).isEqualTo("Europe/Vilnius")
            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Vilnius:20200102T153000")).isEqualTo(true)
        }
    }

    @Test
    fun `cleanTimezones non-supported timezone test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/KazluRuda:20200102T133000Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateOrDateTimeProperty).isTrue()
    }

    @Test
    fun `cleanTimezones convert to supported timezone test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Bratislava:20210302T130000
    DTEND;TZID=Europe/Bratislava:20210302T133000
    UID:hwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().dateStart).timeZone.id).isEqualTo("Europe/Prague")
            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Prague:20210302T130000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Prague:20210302T133000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 2, 13, 0, 0, 0, ZoneId.of("Europe/Prague")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 2, 13, 30, 0, 0, ZoneId.of("Europe/Prague")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanVersion missing VERSION test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("VERSION:2.0")).isEqualTo(true)
        }
    }

    @Test
    fun `cleanVersion invalid value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:1.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult).isInstanceOf(IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful::class)

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("VERSION:2.0")).isEqualTo(true)
        }
    }

    @Test
    fun `cleanCalscale missing CALSCALE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.calendarScale).isEqualTo(null)
        }
    }

    @Test
    fun `cleanCalscale invalid value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:JULIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.CalScale).isTrue()
    }

    @Test
    fun `cleanXWrTimezone unsupported X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:Europe/Bratislava
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:hwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.getExperimentalProperty("X-WR-TIMEZONE")?.value).isEqualTo("Europe/Prague")
        }
    }

    @Test
    fun `cleanXWrTimezone invalid X-WR-TIMEZONE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-WR-TIMEZONE:InvalidTimezoneId
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:hwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.getExperimentalProperty("X-WR-TIMEZONE")).isNull()
        }
    }

    @Test
    fun `cleanUid too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    UID:gKdD4Slj5bSH31aLOhwGUegKdD4Slj5bSH31aLOhwGUengaLzrngaLzrgKdD4Slj5bSH31aLOhwGUengaLzrgKdD4Slj5bSH31aLOhwGUengaLzrgKdD4Slj5bSH31aLOhgKdD4Slj5bSH31aLOhwGUengaLzrwGUengaLzrgKdD4Slj5bSH31aLOhwGUengaLzr@proton.me
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.uid.value.length).isEqualTo(UID_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanUid missing UID test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.7//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SUMMARY:Single 02-03
    STATUS:CONFIRMED
    DTSTART;TZID=Europe/Paris:20210302T130000
    DTEND;TZID=Europe/Paris:20210302T133000
    SEQUENCE:0
    DTSTAMP:20210302T115550Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.MissingUid).isTrue()
    }

    @Test
    fun `cleanDtstamp missing DTSTAMP for Invite test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.MissingDateTimeStamp).isTrue()
    }

    @Test
    fun `cleanDtStart missing DTSTART test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateStart).isTrue()

    }

    @Test
    fun `cleanDtStart value before MIN_DATE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:19681212
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateStart).isTrue()
    }

    @Test
    fun `cleanDtStart value after MAX_DATE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20380102
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateStart).isTrue()
    }

    @Test
    fun `cleanDtStart different value type for DTSTART and DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200102
    DTEND;TZID=Europe/Paris:20210302T133000
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.DateStart).isTrue()
    }

    @Test
    fun `cleanDuration with DURATION and no DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20220102
    DURATION:PT15M
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20220102")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20220103")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 1, 3, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with one hour DURATION and no DTEND taking DST + 1 into account test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Zurich:20220327T010000
    DURATION:PT1H
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Zurich:20220327T010000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Zurich:20220327T030000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 3, 27, 3, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with three hour DURATION and no DTEND taking DST + 1 into account test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Zurich:20220327T010000
    DURATION:PT3H
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Zurich:20220327T010000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Zurich:20220327T050000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 3, 27, 5, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with one day DURATION and no DTEND taking DST + 1 into account test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Zurich:20220327T010000
    DURATION:PT1D
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Zurich:20220327T010000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Zurich:20220328T020000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 3, 27, 1, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 3, 28, 2, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with one hour DURATION and no DTEND taking DST - 1 into account test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Zurich:20221030T030000
    DURATION:PT1H
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Zurich:20221030T030000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Zurich:20221030T040000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 30, 3, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 30, 4, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with three hours DURATION and no DTEND taking DST - 1 into account test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Zurich:20221030T010000
    DURATION:PT3H
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Zurich:20221030T010000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Zurich:20221030T030000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 30, 1, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 30, 3, 0, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with one day DURATION and no DTEND taking DST - 1 into account test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Zurich:20221030T013000
    DURATION:PT1D
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Zurich:20221030T013000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Zurich:20221031T003000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 30, 1, 30, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2022, 10, 31, 0, 30, 0, 0, ZoneId.of("Europe/Zurich")).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration with 36h DURATION and no DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    CALSCALE:GREGORIAN
    PRODID://Yahoo//Calendar//EN
    METHOD:REQUEST
    X-PUBLISHED-TTL:PT1H
    BEGIN:VEVENT
    UID:5467891011121454563
    SUMMARY:36HourFullDayEvent2
    DTSTAMP:20210321T062500Z
    DTSTART;VALUE=DATE:20211005
    DURATION:PT36H
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20211005")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20211007")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 10, 5, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 10, 7, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration Event starting in one month winter time and ending in another summer time, 32 days with DURATION no DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20230301
    DURATION:P32D
    RRULE:FREQ=MONTHLY;INTERVAL=4;BYMONTHDAY=1
    DTSTAMP:20220407T081949Z
    UID:3c8isbf74qqpa679ns3lifb39a21@google.com
    X-MICROSOFT-CDO-OWNERAPPTID:1096650366
    CREATED:20220407T081948Z
    LAST-MODIFIED:20220407T081948Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Event starting in one month and ending in another, 32 days with duration
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20230301")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20230402")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 3, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 4, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration Event starting in one month summer time and ending in another winter time, 32 days with DURATION no DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20231015
    DURATION:P32D
    RRULE:FREQ=MONTHLY;INTERVAL=4;BYMONTHDAY=15
    DTSTAMP:20220407T081949Z
    UID:3c8isbf74qqpa679ns3lifb39a21@google.com
    X-MICROSOFT-CDO-OWNERAPPTID:1096650366
    CREATED:20220407T081948Z
    LAST-MODIFIED:20220407T081948Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Event starting in one month and ending in another, 32 days with duration
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20231015")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20231116")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 10, 15, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 11, 16, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration Event starting in one month and ending in another, 3 weeks with DURATION no DTEND, starts on 18 test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20230318
    DURATION:P3W
    RRULE:FREQ=MONTHLY;INTERVAL=4;BYMONTHDAY=18
    DTSTAMP:20220407T081949Z
    UID:3c8isbf74qqpa679ns3lifb39a21124518@google.com
    X-MICROSOFT-CDO-OWNERAPPTID:1096650366
    CREATED:20220407T081948Z
    LAST-MODIFIED:20220407T081948Z
    LOCATION:Every 4 months
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Event starting in one month and ending in another, 3 weeks with duration, starts on 18
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20230318")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20230408")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 3, 18, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 4, 8, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDuration Event starting in one month and ending in another, 6 weeks with DURATION no DTEND test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20230301
    DURATION:P4W
    RRULE:FREQ=MONTHLY;INTERVAL=4;BYMONTHDAY=1
    DTSTAMP:20220407T081949Z
    UID:3c8isbf74qqpa679ns3lifb39a2112@google.com
    X-MICROSOFT-CDO-OWNERAPPTID:1096650366
    CREATED:20220407T081948Z
    LAST-MODIFIED:20220407T081948Z
    LOCATION:Every 4 months
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Event starting in one month and ending in another, 6 weeks with duration
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20230301")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20230329")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 3, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2023, 3, 29, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.duration).isNull()
        }
    }

    @Test
    fun `cleanDtEnd DTEND value before DTSTART test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20210102
    DTEND;VALUE=DATE:20210101
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
            //  set to avoid NPE in the app. We remove it when sending the ICS to BE.
            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20210102")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20210103")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 1, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 1, 3, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanDtEnd no DTEND partial day test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;TZID=Europe/Paris:20210302T130000
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
            //  set to avoid NPE in the app. We remove it when sending the ICS to BE.
            assertThat(iCalendar.printToString().contains("DTSTART;TZID=Europe/Paris:20210302T130000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;TZID=Europe/Paris:20210302T130000")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 2, 13, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 2, 13, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanDtEnd no DTEND full day test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20210102
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
            //  set to avoid NPE in the app. We remove it when sending the ICS to BE.
            assertThat(iCalendar.printToString().contains("DTSTART;VALUE=DATE:20210102")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("DTEND;VALUE=DATE:20210103")).isEqualTo(true)
            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 1, 2, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 1, 3, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanDescription DESCRIPTION too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200506
    DESCRIPTION:iWJZBZJzu8qZpa1EwfN43fFepSCfklnua9L1xDqJFneRBF4cChhWTTOBYt1Es8mx5wiRa4izKeheC9BAlAw1ss1EJpEHEpRxUyeNTdi5jneUqa3LhfCechW1dz2Ar44orITzw1hLOQ2p0ergwPe9rQch2OzdwpcWFXkKDb9gk45lccy0uz8mqCpG5yYQIBmSkW3mO7SrgtrCM7fKVdDKekEFfJzS4IMvyeckdiwlo6RyQoLTNMKqAuw6IfYzPAKbKj3TiN0iJEHCb6KmzSmCafT8iGI5UydvWZU8dHkCdViITZ7f8G3Ns47fGJ7giv9oZR902LZXVRU0uyThpjGwGy5GHLRTuKL4TF1IkKJk6Wvh3NT9dfsiY0P3uhXE26OtZmImgCCld255clSOGtPe3dWX8hrTrnX0KIAGLxHB0jOGDI6Av0QeRCAiNY12Bh9OVCbP0OU27ZBIXffq6LRHI8VCj4tNs95PDeaBU39E8wfbn55niiCSwuxWHF8DxPnIL0EUOhZoU2FiqGc58yXLGqRBZpKRk2oiBwQbd7HoF7pfNhMF902jBkO2iGF4Gh3DUTzxy5Nc7O2UyH2XxSiggSNYzkPAWZyecitvxQ1KQtyhEjfiNkQZgCxGUAvkyQGJ9ZJaKUujG4Nbpc9z3xhnMqsXRnEYxJvopurfwodM0i2AYGpfKtJaFOy4GINDhbozC9IWZxmYxVsEPbhv3lnQP07iNEh1YKsVaNr6EPOtrNjUNSG9euEkfJZni6TX4i8ggEsGEco8hgphrBBrS2JhkWrDWH2f5CIgfgmkGMumqEA8WLNyeo30eZKPGeW58S0UljxNckLlI9agxdevmdhjCZxOlv51awUw4AhSn0FkvtMuPKvd8leFf5tma8UbVZvnw5w8wcFYlFsQN56qGbH2U7bpYs1QH2JMWp92f2KYVBreyv4UbbUmr1Ct3A3oqrl5QPnlrW55FZPLhAauk5fjvMTXdkICfRMeeoeEBJkBxYhveUXCuIaSkiD3TQrwqdAMFzw0OtKbYcTrEhxg6VfASDboGUzAG6N9SHYWCl7Xmwff1Ow4oY57tiHB4TZk8J5KaMKp6OqIqxRcOFUJ1aIZ5INS8gB6G08vU7ypygsET6tP1A67e3NCWUcpW3JH7yNlVIKoiOKZutnAhM6qEzHdp4e1w9tZzXb100HfAQB82phfQBAqiRkIYLOJVLEj2YnJN7Xf3v87FKHznPFQfbaUBrKAz3EY8Rzj8QTF9d6GofCivv9Orliy5pc9fh3x8dbyfjKSZpPGhygojz31dAWoDvrz7mNcB2v8YI4whvcpRXIO3uUlLSGBdkkbMXESu7IjPdWsnMSeGz09ALrAJigWuHmmDmY1d3XXNNrfcb5X53ym6wGJLARkKCt6FnvP8B2PcXkMyIToImAOfGyDruRYSeMVBudxiEdJfaVqxA2iHxeY6Tw4D78QTN81rAJGTsTMkO5Ow4ybj1m9FUO3nHP27LQGfczOzqmVs8qgqRpYy2IsCdnpRFQLStahSTFTfKv4loh1UJqM5V7esn6fmzhNx8omsvTtawyka5oKcK5VBPnygSN02sBEf2kVkDomxW9I3eFV9XYpceRETGdUH9MMBHEYYxzAnEVMDvVL78D0AoREIpQMUFPiWd7lGc9nJiZW21KMcCbdeTUuU9Y5eoZPrJ9I3szCcej2uPt8l0TmWo4HMxjgVtiMz2ywMalPcVdWkpLmeoCv3xqTzT0rRBfVoAgETuI0RSORHELMQ73FJ9mYDI4YHRWOmivRkRvijf0V2oRJDM2kJ8QbYzyFqRgohRWwx58anTLXNsZHIYrm7K7KXp7Znf6JFccerRW7Oq0UaCoT6mTwj9wSXpQuBw07H8AFxd5be2nDFvF29UMqcZvjPxkbIkaoRe2ceW7gILrBcqbwwOm82fvyHDuhn0utVJpUL5Dwlf6TVn5W2cy5bOShkGVttXQ45QtNYpcgq7Jf65EKhse3AxRa8J3FkfwJa4UoHqlhNsLTTtoZKf0i8iXBrySWUzeQmAoXrQ5LQDXD64Tfjofc52UdGAiucG5PJgKLECbeykR3f8Laz3IjJ5ES8hXcqCgdVkFYz5hm9F5YzjgBGVmf2D9KguRQlMujOgI9VKjhSoJZRADOoOTLRZnBPGzj7HwyKnRexQtgU0xwftylxI7Igi6d42mGdKQrqDpruyDPd78qnNFHSJBTCQoihnuyJ0LcvVbG2UREDwz9QVg9HrlfcJcutEqKzgpApkzfy0pFZbTH8V4ooPMYoFfyseBZZaSOzxvSdNmMednwVPTqboHwgqgt1Cay2LsW5SOWWtDt3Uvy3sjWoSBzPUx9Jgm1wK0OOvteTnFKrSKepz1wuGBXnjhdaKopvCWOL9B6whPP6PBLLYu1uFM4fyySVJgMc2YvB7mnpUmzCbisuD5Th8jvrg5q8Dt6KLU2C1eJoSpiOzJooCJlx9EIfy1UPXcelJDM1c2qzNT1GxHhIAgaTgIntr1bDX0rwMl29cQUnYZhXNvYQuz3uXfszRC441R0yokiyisSupNloIuMWkjsUux1HJEZzaq9gWRYqCZxdPzCTj81FRph2wvG9k9ZoFjVJVAAtP71dbWwRPkH1xGVgq1AHEhu2J6OQ6FDtl1wLZZPxJPd37l1n0wxWMoQOLdrSGcdiQEco7QPnX3XyhukoSMTuDP2kE1QkqJtXnCEMhC480FTjlqNyOWm5EJ6aBqn2btLngUq9EMcAaQ2dbIwgNLiGzc7j39oaDggVQotBqWcsELfsetrrJD7ZMSdlQIDxIWQlPFYDIrIYJdyptXqafvDl1jcXVC7mhGiXL8gna27e0xQoP24oTKdCN9WEJxxE8AnZAyxkiXxvLVY82rj4HaJafAbCCwQoLvEDy8QpKpWGp55m4B9VqkCjeJvX46x0WEv80vv1erWshIMmujgAFsdsXdktDQ7DOpK1tyKLLb0hgJKMFp1zy7NM5YAm1ssLrpPyqChV
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.description.value.length).isEqualTo(DESCRIPTION_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanDescription LOCATION too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200506
    LOCATION:iWJZBZJzu8qZpa1EwfN43fFepSCfklnua9L1xDqJFneRBF4cChhWTTOBYt1Es8mx5wiRa4izKeheC9BAlAw1ss1EJpEHEpRxUyeNTdi5jneUqa3LhfCechW1dz2Ar44orITzw1hLOQ2p0ergwPe9rQch2OzdwpcWFXkKDb9gk45lccy0uz8mqCpG5yYQIBmSkW3mO7SrgtrCM7fKVdDKekEFfJzS4IMvyeckdiwlo6RyQoLTNMKqAuw6IfYzPAKbKj3T
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.location.value.length).isEqualTo(LOCATION_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanDescription SUMMARY too long test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20200506
    SUMMARY:iWJZBZJzu8qZpa1EwfN43fFepSCfklnua9L1xDqJFneRBF4cChhWTTOBYt1Es8mx5wiRa4izKeheC9BAlAw1ss1EJpEHEpRxUyeNTdi5jneUqa3LhfCechW1dz2Ar44orITzw1hLOQ2p0ergwPe9rQch2OzdwpcWFXkKDb9gk45lccy0uz8mqCpG5yYQIBmSkW3mO7SrgtrCM7fKVdDKekEFfJzS4IMvyeckdiwlo6RyQoLTNMKqAuw6IfYzPAKbKj3T
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.summary.value.length).isEqualTo(SUMMARY_MAX_LENGTH)
        }
    }

    @Test
    fun `cleanRRule do not generate DTSTART as occurrence test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210327T225959Z;BYDAY=SU
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule recurring with ex date on only occurrence test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210306T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210306T160000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule DAILY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=DAILY;INTERVAL=1000
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule WEEKLY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;INTERVAL=5000
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule MONTHLY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=MONTHLY;INTERVAL=1000
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule YEARLY interval over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;INTERVAL=100
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule COUNT over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;COUNT=500
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule UNTIL over max allowed value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=YEARLY;UNTIL=20380102T120000Z
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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule UNTIL is DATETIME for all day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210329
    DTEND;VALUE=DATE:20210330
    RRULE:FREQ=DAILY;UNTIL=20210331T120000Z
    SEQUENCE:1
    STATUS:CONFIRMED
    DTSTAMP:20210329T094605Z
    UID:002bTy1gdPpekrxdL-hc99N76RF3@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.recurrenceRule.value.until.hasTime()).isFalse()
            assertThat(iCalendar.printToString().contains("UNTIL=20210331")).isEqualTo(true)
            assertThat(event.recurrenceRule.value.until).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 31, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRRule UNTIL is DATE for part day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210329T120000
    DTEND;TZID=Europe/Paris:20210329T123000
    RRULE:FREQ=DAILY;UNTIL=20210331
    SEQUENCE:0
    STATUS:CONFIRMED
    DTSTAMP:20210329T094841Z
    UID:B6CCcxfX8f3TfV0WVYTEcdSNz2aa@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("UNTIL=20210331T215959Z")).isEqualTo(true)
            assertThat(event.recurrenceRule.value.until).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 31, 23, 59, 59, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRRule UNTIL is before DTSTART for part day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210329T120000
    DTEND;TZID=Europe/Paris:20210329T123000
    RRULE:FREQ=DAILY;UNTIL=20210328T215959Z
    SEQUENCE:0
    STATUS:CONFIRMED
    DTSTAMP:20210329T094841Z
    UID:B6CCcxfX8f3TfV0WVYTEcdSNz2aa@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("UNTIL=20210329T100000Z")).isEqualTo(true)
            assertThat(event.recurrenceRule.value.until).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 29, 12, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRRule UNTIL is before DTSTART for all day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210329
    DTEND;VALUE=DATE:20210329
    RRULE:FREQ=DAILY;UNTIL=20210328
    SEQUENCE:0
    STATUS:CONFIRMED
    DTSTAMP:20210329T094841Z
    UID:B6CCcxfX8f3TfV0WVYTEcdSNz2aa@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("UNTIL=20210329")).isEqualTo(true)
            assertThat(event.recurrenceRule.value.until).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 29, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRRule YEARLY with BYMONTHDAY but no BYMONTH test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210322
    DTEND;VALUE=DATE:20210323
    RRULE:FREQ=YEARLY;BYMONTHDAY=22
    SEQUENCE:0
    STATUS:CONFIRMED
    DTSTAMP:20210329T094841Z
    UID:B6CCcxfX8f3TfV0WVYTEcdSNz2aa@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RRule).isTrue()
    }

    @Test
    fun `cleanRRule event with both RECURRENCE-ID and RRULE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.10//EN
    METHOD:REPLY
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210329
    DTEND;VALUE=DATE:20210330
    RRULE:FREQ=DAILY;UNTIL=20210331
    RECURRENCE-ID;VALUE=DATE:20210329
    SEQUENCE:1
    STATUS:CONFIRMED
    DTSTAMP:20210329T094605Z
    UID:002bTy1gdPpekrxdL-hc99N76RF3@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.recurrenceRule).isEqualTo(null)
        }
    }

    @Test
    fun `cleanRRule all day event with DATETIME UNTIL in RRULE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:Taipei Standard Time
    BEGIN:STANDARD
    DTSTART:16010101T000000
    TZOFFSETFROM:+0800
    TZOFFSETTO:+0800
    END:STANDARD
    BEGIN:DAYLIGHT
    DTSTART:16010101T000000
    TZOFFSETFROM:+0800
    TZOFFSETTO:+0800
    END:DAYLIGHT
    END:VTIMEZONE
    BEGIN:VEVENT
    ORGANIZER;CN=test:mailto:test@protonmail.com
    ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=breakingcalendar@pm.me:mailto:breakingcalendar@pm.me
    DESCRIPTION;LANGUAGE=en-US:\n
    RRULE:FREQ=DAILY;UNTIL=20210522T160000Z;INTERVAL=1
    UID:android_until_outlook
    SUMMARY;LANGUAGE=en-US:android, until
    DTSTART;VALUE=DATE:20210519
    DTEND;VALUE=DATE:20210519
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20201112T025741Z
    TRANSP:TRANSPARENT
    STATUS:CONFIRMED
    SEQUENCE:0
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("UNTIL=20210523")).isEqualTo(true)
            assertThat(event.recurrenceRule.value.until).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 5, 23, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRRule part day event with tz that make occurrences happen on the next day test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.19-prod.1//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VTIMEZONE
    TZID:Pacific/Niue
    LAST-MODIFIED:20210410T122212Z
    X-LIC-LOCATION:Pacific/Niue
    BEGIN:STANDARD
    TZNAME:-11
    TZOFFSETFROM:-1100
    TZOFFSETTO:-1100
    DTSTART:19700101T000000
    END:STANDARD
    END:VTIMEZONE
    BEGIN:VEVENT
    SUMMARY:Different timezone
    STATUS:CONFIRMED
    RRULE:FREQ=MONTHLY;BYDAY=TU;BYSETPOS=4
    DTSTART;TZID=Pacific/Niue:20210622T110000
    DTEND;TZID=Pacific/Niue:20210622T113000
    ATTENDEE;X-PM-TOKEN=fd5a754f0f3f9b89f44f251bc39d4342901a2b29;RSVP=TRUE;ROLE
    =REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=breakingcalendar@protonmail.com:
    mailto:breakingcalendar@protonmail.com
    UID:aRpVeZ2WB-NBHPO_LLykafrJIYKr@proton.me
    ORGANIZER;CN=benjaminlovesdebugging@pm.me:mailto:benjaminlovesdebugging@pm.
    me
    SEQUENCE:0
    DTSTAMP:20210622T125255Z
    X-PM-SHARED-EVENT-ID:s6zweKVpKPlwW3e_59EIPL-zOBgPl3cGdP1VRTEGzB6Nyx2I21hcnA
    nuQ97-9pMdmUGc7qgcvjQexclIZMpBgXKkL9e7xyS1zWtg_a_RbW8=
    X-PM-SESSION-KEY:e6Rybl4XlTgNlG4vyI8FggwIwMogBbafdoMcf150IdM=
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 23, 0, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 23, 0, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))

            val dummyEventForOccurrences = Event.dummyFrom(iCalendar)!!
            val firstOccurrence = dummyEventForOccurrences.generateOccurrence(1, "Pacific/Niue")!!
            assertThat(firstOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 6, 22, 11, 0, 0, 0, ZoneId.of("Pacific/Niue"))
            )
            assertThat(firstOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 6, 22, 11, 30, 0, 0, ZoneId.of("Pacific/Niue"))
            )
            val secondOccurrence = dummyEventForOccurrences.generateOccurrence(2, "Pacific/Niue")!!
            assertThat(secondOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 27, 11, 0, 0, 0, ZoneId.of("Pacific/Niue"))
            )
            assertThat(secondOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 27, 11, 30, 0, 0, ZoneId.of("Pacific/Niue"))
            )
        }
    }

    @Test
    fun `cleanRRule part day event with tz that make occurrences happen on the previous day test`() {

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
    ATTENDEE;X-PM-TOKEN=fd5a754f0f3f9b89f44f251bc39d4342901a2b29;RSVP=TRUE;ROLE
    =REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=breakingcalendar@protonmail.com:
    mailto:breakingcalendar@protonmail.com
    UID:aRpVeZ2WB-NBHPO_LLykafrJIYKr@proton.me
    ORGANIZER;CN=benjaminlovesdebugging@pm.me:mailto:benjaminlovesdebugging@pm.
    me
    SEQUENCE:0
    DTSTAMP:20210622T125255Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.dateStart.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 21, 19, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.dateEnd.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 6, 21, 19, 30, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))

            val dummyEventForOccurrences = Event.dummyFrom(iCalendar)!!
            val firstOccurrence = dummyEventForOccurrences.generateOccurrence(1, "Asia/Anadyr")!!
            assertThat(firstOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 6, 22, 5, 0, 0, 0, ZoneId.of("Asia/Anadyr"))
            )
            assertThat(firstOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 6, 22, 5, 30, 0, 0, ZoneId.of("Asia/Anadyr"))
            )
            val secondOccurrence = dummyEventForOccurrences.generateOccurrence(2, "Asia/Anadyr")!!
            assertThat(secondOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 27, 5, 0, 0, 0, ZoneId.of("Asia/Anadyr"))
            )
            assertThat(secondOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 27, 5, 30, 0, 0, ZoneId.of("Asia/Anadyr"))
            )
        }
    }

    @Test
    fun `cleanRRule part day monthly event with bysetpos and same month until value`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.19.6//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20210708T191427Z
    DTSTART;TZID=Europe/Vilnius:20210708T223000
    DTEND;TZID=Europe/Vilnius:20210708T230000
    RRULE:FREQ=MONTHLY;UNTIL=20210731T205959Z;BYDAY=TH;BYSETPOS=2
    SEQUENCE:0
    SUMMARY:Custom monthly until same month
    STATUS:CONFIRMED
    UID:tPk35J26Cn-Hz6mL937t41_QtDE2@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            val dummyEventForOccurrences = Event.dummyFrom(iCalendar)!!
            val firstOccurrence = dummyEventForOccurrences.generateOccurrence(1, "Europe/Vilnius")!!
            assertThat(firstOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 8, 22, 30, 0, 0, ZoneId.of("Europe/Vilnius"))
            )
            assertThat(firstOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 8, 23, 0, 0, 0, ZoneId.of("Europe/Vilnius"))
            )
            val secondOccurrence = dummyEventForOccurrences.generateOccurrence(2, "Europe/Vilnius")
            assertThat(secondOccurrence).isEqualTo(null)
        }
    }

    @Test
    fun `cleanRRule part day monthly event with byday scheduled in different timezone`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VTIMEZONE
    TZID:Pacific/Honolulu
    X-LIC-LOCATION:Pacific/Honolulu
    BEGIN:STANDARD
    TZOFFSETFROM:-1000
    TZOFFSETTO:-1000
    TZNAME:HST
    DTSTART:19700101T000000
    END:STANDARD
    END:VTIMEZONE
    BEGIN:VEVENT
    DTSTART;TZID=Pacific/Honolulu:20210713T210000
    DTEND;TZID=Pacific/Honolulu:20210713T220000
    RRULE:FREQ=MONTHLY;UNTIL=20220902T095959Z;BYDAY=2TU
    DTSTAMP:20210713T172841Z
    ORGANIZER;CN=With single edits:mailto:1huhifrk6kvjplpgoglieacf7g@group.cale
     ndar.google.com
    UID:5kvolm820qa18mhnghp4dj3aeb@google.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=OPT-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=breakingcalendar@protonmail.com;X-NUM-GUESTS=0:mailto:breakingcalen
     dar@protonmail.com
    CREATED:20210713T172840Z
    DESCRIPTION:Test
    LAST-MODIFIED:20210713T172840Z
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:Event with participants
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            val dummyEventForOccurrences = Event.dummyFrom(iCalendar)!!
            val firstOccurrence = dummyEventForOccurrences.generateOccurrence(1, "Pacific/Honolulu")!!
            assertThat(firstOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 13, 21, 0, 0, 0, ZoneId.of("Pacific/Honolulu"))
            )
            assertThat(firstOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 7, 13, 22, 0, 0, 0, ZoneId.of("Pacific/Honolulu"))
            )
            val secondOccurrence = dummyEventForOccurrences.generateOccurrence(2, "Pacific/Honolulu")!!
            assertThat(secondOccurrence.startDateTime).isEqualTo(
                ZonedDateTime.of(2021, 8, 10, 21, 0, 0, 0, ZoneId.of("Pacific/Honolulu"))
            )
            assertThat(secondOccurrence.endDateTime).isEqualTo(
                ZonedDateTime.of(2021, 8, 10, 22, 0, 0, 0, ZoneId.of("Pacific/Honolulu"))
            )
        }
    }

    @Test
    fun `cleanRRule part day daily with custom RRULE with COUNT at 52`() {

        val iCalString = """
    BEGIN:VCALENDAR
    METHOD:REQUEST
    PRODID:Microsoft Exchange Server 2010
    VERSION:2.0
    BEGIN:VTIMEZONE
    TZID:FLE Standard Time
    BEGIN:STANDARD
    DTSTART:16010101T040000
    TZOFFSETFROM:+0300
    TZOFFSETTO:+0200
    RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10
    END:STANDARD
    BEGIN:DAYLIGHT
    DTSTART:16010101T030000
    TZOFFSETFROM:+0200
    TZOFFSETTO:+0300
    RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3
    END:DAYLIGHT
    END:VTIMEZONE
    BEGIN:VEVENT
    ORGANIZER;CN=Juste SavSavSav:mailto:Iamblueuser@outlook.com
    ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=Short Name
     :mailto:calendaruser@pm.me
    DESCRIPTION;LANGUAGE=en-GB:\n
    RRULE:FREQ=DAILY;INTERVAL=1;COUNT=52
    UID:040000008200E00074C5B7101A82E0080000000009486FEAD877D701000000000000000
     010000000440129523F8F744AB3C83A6C75E9E7BB123456789
    SUMMARY;LANGUAGE=en-GB:Custom rrule
    DTSTART;TZID=FLE Standard Time:20210713T150000
    DTEND;TZID=FLE Standard Time:20210713T153000
    CLASS:PUBLIC
    PRIORITY:5
    DTSTAMP:20210713T111916Z
    TRANSP:OPAQUE
    STATUS:CONFIRMED
    SEQUENCE:0
    LOCATION;LANGUAGE=en-GB:
    X-MICROSOFT-CDO-APPT-SEQUENCE:0
    X-MICROSOFT-CDO-OWNERAPPTID:2119657692
    X-MICROSOFT-CDO-BUSYSTATUS:TENTATIVE
    X-MICROSOFT-CDO-INTENDEDSTATUS:BUSY
    X-MICROSOFT-CDO-ALLDAYEVENT:FALSE
    X-MICROSOFT-CDO-IMPORTANCE:1
    X-MICROSOFT-CDO-INSTTYPE:1
    X-MICROSOFT-DONOTFORWARDMEETING:FALSE
    X-MICROSOFT-DISALLOW-COUNTER:FALSE
    X-MICROSOFT-LOCATIONS:[]
    BEGIN:VALARM
    DESCRIPTION:REMINDER
    TRIGGER;RELATED=START:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(event.recurrenceRule.value.count).isEqualTo(52)
        }
    }

    @Test
    fun `cleanRecurrenceId RECURRENCE-ID with RRULE test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210320T120000
    DTEND;TZID=Europe/Paris:20210320T123000
    RECURRENCE-ID;TZID=Europe/Paris:20210320T120000
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=SU
    SEQUENCE:0
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.RecurrenceId).isTrue()
    }

    @Test
    fun `cleanRecurrenceId datetime type RECURRENCE-ID for all day event with TZID test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210320
    DTEND;VALUE=DATE:20210320
    RRULE:FREQ=DAILY
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210420
    DTEND;VALUE=DATE:20210420
    RECURRENCE-ID;TZID=Europe/Paris:20210420T120000
    SEQUENCE:1
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105558Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.cleanRecurrenceId(parentICal)).isTrue()
            assertThat(iCalendar.printToString().contains("RECURRENCE-ID;VALUE=DATE:20210420")).isEqualTo(true)
            assertThat(iCalendar.events.first().recurrenceId.value.hasTime()).isFalse()
            assertThat(event.recurrenceId.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 4, 20, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRecurrenceId datetime type RECURRENCE-ID for all day event without TZID test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REQUEST
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210704
    DTEND;VALUE=DATE:20210705
    RRULE:FREQ=DAILY;COUNT=7
    DTSTAMP:20210628T135523Z
    ORGANIZER;CN=calendarregression@gmail.com:mailto:calendarregression@gmail.c
     om
    UID:5ju5dd05gteb97ei0iaknapt03@google.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=calendaruser@pm.me;X-NUM-GUESTS=0:mailto:calendaruser@pm.me
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE
     ;CN=calendarregression@gmail.com;X-NUM-GUESTS=0:mailto:calendarregression@g
     mail.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=adamtst@protonmail.com;X-NUM-GUESTS=0:mailto:adamtst@protonmail.com
    X-MICROSOFT-CDO-OWNERAPPTID:-272203753
    CREATED:20210628T135522Z
    DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~::~:~::-\nDo not edit this section of the description.\n\nV
     iew your event at https://calendar.google.com/calendar/event?action=VIEW&ei
     d=NWp1NWRkMDVndGViOTdlaTBpYWtuYXB0MDMgY2FsZW5kYXJ1c2VyQHBtLm1l&tok=MjgjY2Fs
     ZW5kYXJyZWdyZXNzaW9uQGdtYWlsLmNvbTMxNzgwYmUyNmUyODYwZDJkM2RkY2IyNjI2MDQ4YzV
     kNmVhYzkxY2I&ctz=Europe%2FVilnius&hl=en_GB&es=1.\n-::~:~::~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~::~:~::-
    LAST-MODIFIED:20210628T135523Z
    LOCATION:
    SEQUENCE:0
    STATUS:CONFIRMED
    SUMMARY:SeriesFull
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:CANCEL
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210705
    DTEND;VALUE=DATE:20210706
    DTSTAMP:20210628T135535Z
    ORGANIZER;CN=calendarregression@gmail.com:mailto:calendarregression@gmail.c
     om
    UID:5ju5dd05gteb97ei0iaknapt03@google.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;CN=calend
     arregression@gmail.com;X-NUM-GUESTS=0:mailto:calendarregression@gmail.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=ca
     lendaruser@pm.me;X-NUM-GUESTS=0:mailto:calendaruser@pm.me
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=adamtst@protonmail.com;X-NUM-GUESTS=0:mailto:adamtst@protonmail.com
    RECURRENCE-ID;VALUE=DATE:20210705
    CREATED:20210628T135522Z
    DESCRIPTION:
    LAST-MODIFIED:20210628T135535Z
    LOCATION:
    SEQUENCE:1
    STATUS:CANCELLED
    SUMMARY:SeriesFull
    TRANSP:TRANSPARENT
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.cleanRecurrenceId(parentICal)).isTrue()
            assertThat(iCalendar.printToString().contains("RECURRENCE-ID;VALUE=DATE:20210705")).isEqualTo(true)
            assertThat(iCalendar.events.first().recurrenceId.value.hasTime()).isFalse()
            assertThat(event.recurrenceId.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 7, 5, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRecurrenceId date type RECURRENCE-ID for part day event test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REPLY
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210320T120000
    DTEND;TZID=Europe/Paris:20210320T123000
    RRULE:FREQ=DAILY
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REPLY
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210420T120000
    DTEND;TZID=Europe/Paris:20210420T123000
    RECURRENCE-ID;VALUE=DATE:20210420
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=SU
    SEQUENCE:0
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.cleanRecurrenceId(parentICal)).isFalse()
        }
    }

    @Test
    fun `cleanRecurrenceId RECURRENCE-ID timezone different from the parent DTSTART test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Athens:20210320T120000
    DTEND;TZID=Europe/Athens:20210320T123000
    RRULE:FREQ=DAILY
    SEQUENCE:0
    SUMMARY:Recurring
    STATUS:CONFIRMED
    DTSTAMP:20210317T105458Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210420
    DTEND;VALUE=DATE:20210420
    RECURRENCE-ID;TZID=Europe/Paris:20210420T120000
    SEQUENCE:1
    SUMMARY:Recurring with SE 1
    STATUS:CONFIRMED
    DTSTAMP:20210317T105558Z
    UID:lOIY56JOStapy1PUGuN4WiN67oBQ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.cleanRecurrenceId(parentICal)).isTrue()
            assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().recurrenceId).timeZone.id).isEqualTo("Europe/Athens")
            assertThat(iCalendar.printToString().contains("RECURRENCE-ID;TZID=Europe/Athens:20210420T130000")).isEqualTo(true)
            assertThat(event.recurrenceId.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 4, 20, 13, 0, 0, 0, ZoneId.of("Europe/Athens")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanRecurrenceId RECURRENCE-ID timezone (with TZ definition) same as the parent DTSTART test`() {

        val parentICalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REPLY
    BEGIN:VEVENT
    DTSTAMP:20210601T134616Z
    DTSTART;TZID=America/Noronha:20210601T100000
    DTEND;TZID=America/Noronha:20210601T103000
    RRULE:FREQ=WEEKLY;UNTIL=20210901T225959Z;BYDAY=FR,SA,TH,TU,WE
    ORGANIZER;CN=iamblueuser@gmail.com:mailto:iamblueuser@gmail.com
    SEQUENCE:0
    DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~::~:~::-\nPlease do not edit this section of the descripti
     on.\n\nView your event at https://calendar.google.com/calendar/event?actio
     n=VIEW&eid=MHFndW1sbmFraGg3dTliYmZkcDNvbjk5b2YgY2FsZW5kYXJhdXRvbWF0aW9uOTk
     5QHByb3Rvbm1haWwuY29t&tok=MjEjaWFtYmx1ZXVzZXJAZ21haWwuY29tMDRjOWNmNTJhNzQ1
     YTAyYTJiODM4NTE4NzljNTU2YjY5OTM4YThjNw&ctz=Europe%2FVilnius&hl=en_GB&es=1.
     \n-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:
     ~:~:~:~::~:~::-
    SUMMARY:Timezone +1
    UID:0qgumlnakhh7u9bbfdp3on99ofadam@google.com
    ATTENDEE;X-PM-TOKEN=bd25aa978853c40eec974a60a8b1911a1c0ec567;RSVP=TRUE;ROLE
     =REQ-PARTICIPANT;PARTSTAT=TENTATIVE;CN=adamtst@protonmail.com:mailto:adamt
     st@protonmail.com
    BEGIN:VALARM
    ACTION:DISPLAY
    TRIGGER:-PT15M
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Google Inc//Google Calendar 70.9054//EN
    VERSION:2.0
    CALSCALE:GREGORIAN
    METHOD:REPLY
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210729
    DTEND;VALUE=DATE:20210730
    DTSTAMP:20210601T135021Z
    ORGANIZER;CN=iamblueuser@gmail.com:mailto:iamblueuser@gmail.com
    UID:0qgumlnakhh7u9bbfdp3on99ofadam2@google.com
    ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
     TRUE;CN=adamtst@protonmail.com;X-NUM-GUESTS=0:mailto:adamtst
     @protonmail.com
    X-MICROSOFT-CDO-OWNERAPPTID:-462541747
    RECURRENCE-ID;TZID=America/Noronha:20210729T100000
    CREATED:20210601T134615Z
    DESCRIPTION:-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~::~:~::-\nPlease do not edit this section of the descriptio
     n.\n\nView your event at https://calendar.google.com/calendar/event?action=
     VIEW&eid=MHFndW1sbmFraGg3dTliYmZkcDNvbjk5b2ZfMjAyMTA3MjlUMDkwMDAwWiBjYWxlbm
     RhcmF1dG9tYXRpb245OTlAcHJvdG9ubWFpbC5jb20&tok=MjEjaWFtYmx1ZXVzZXJAZ21haWwuY
     29tOGQwZTZjNTc5ZWY1NDgyN2UzODc0ZDU5ZjQ3NmRiMTA5YzFhOTdkOQ&ctz=Europe%2FViln
     ius&hl=en_GB&es=0.\n-::~:~::~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~:~
     :~:~:~:~:~:~:~:~:~:~:~:~::~:~::-
    LAST-MODIFIED:20210601T135020Z
    LOCATION:
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Timezone To full day
    TRANSP:OPAQUE
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val parentICal = ICalUtilsImpl.parseICalString(parentICalString)

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.cleanRecurrenceId(parentICal)).isTrue()
            assertThat(iCalendar.timezoneInfo.getTimezone(iCalendar.events.first().recurrenceId).timeZone.id).isEqualTo("America/Noronha")
            assertThat(iCalendar.printToString().contains("RECURRENCE-ID;TZID=America/Noronha:20210729T100000")).isEqualTo(true)
            assertThat(event.recurrenceId.value).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 7, 29, 10, 0, 0, 0, ZoneId.of("America/Noronha")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanExDate EXDATE is of type DATE-TIME for an all-day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;VALUE=DATE:20210307
    DTEND;VALUE=DATE:20210308
    RRULE:FREQ=WEEKLY;UNTIL=20210328;BYDAY=SU
    SEQUENCE:0
    EXDATE;TZID=Europe/Paris:20210314T160000
    EXDATE;TZID=Europe/Paris:20210321T160000
    SUMMARY:Recurring all day with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T152217Z
    UID:tJdI3clfxULR9iZ5oUkKhvqUsB6d@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("EXDATE;VALUE=DATE:20210314")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("EXDATE;VALUE=DATE:20210321")).isEqualTo(true)
            assertThat(event.exceptionDates[0].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 14, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
            assertThat(event.exceptionDates[1].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 21, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()
            ))
        }
    }

    @Test
    fun `cleanExDate EXDATE of type DATE for part day event test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210327T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;VALUE=DATE:20210313
    EXDATE;VALUE=DATE:20210320
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.ExDate).isTrue()
    }

    @Test
    fun `cleanExDate EXDATE has timezone different from DTSTART test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=DAILY;UNTIL=20210327T225959Z
    SEQUENCE:0
    EXDATE;TZID=Europe/Vilnius:20210307T170000
    EXDATE;TZID=Europe/Vilnius:20210320T170000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("EXDATE;TZID=Europe/Paris:20210307T160000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("EXDATE;TZID=Europe/Paris:20210320T160000")).isEqualTo(true)
            assertThat(event.exceptionDates[0].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 7, 16, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.exceptionDates[1].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 20, 16, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanExDate EXDATE has multiple values that needs to be split test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.8//EN
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210306T160000
    DTEND;TZID=Europe/Paris:20210306T163000
    RRULE:FREQ=WEEKLY;UNTIL=20210327T225959Z;BYDAY=SA
    SEQUENCE:0
    EXDATE;TZID=Europe/Vilnius:20210307T170000,20210313T170000,20210320T170000
    EXDATE;TZID=Europe/Vilnius:20210327T170000
    SUMMARY:Recurring with exdates
    STATUS:CONFIRMED
    DTSTAMP:20210311T145808Z
    UID:35fdx2qMv8RPjvIFecqY1qTMIooJ@proton.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("EXDATE;TZID=Europe/Paris:20210307T160000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("EXDATE;TZID=Europe/Paris:20210313T160000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("EXDATE;TZID=Europe/Paris:20210320T160000")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("EXDATE;TZID=Europe/Paris:20210327T160000")).isEqualTo(true)
            assertThat(event.exceptionDates[0].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 7, 16, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.exceptionDates[1].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 13, 16, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.exceptionDates[2].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 20, 16, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
            assertThat(event.exceptionDates[3].values.first()).isEqualTo(Date.from(
                ZonedDateTime.of(2021, 3, 27, 16, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant()
            ))
        }
    }

    @Test
    fun `cleanAttendees duplicated ATTENDEE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.8//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Test
    UID:FKQKTlAcHr6irit-E7iuR3elGxFf@proton.me
    DTSTART;TZID=Europe/Paris:20210326T130000
    DTEND;TZID=Europe/Paris:20210326T140000
    ORGANIZER;CN=test@protonmail.com:mailto:test@proton
     mail.com
    ATTENDEE;CN=testattendee@protonmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PA
     RTSTAT=NEEDS-ACTION:mailto:testattendee@protonmail.com
    ATTENDEE;CN=testattendee@protonmail.com;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PA
     RTSTAT=NEEDS-ACTION:mailto:testattendee@protonmail.com
    DTSTAMP:20210324T090044Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees).isTrue()
    }

    @Test
    fun `cleanAttendees ATTENDEE with no email`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.8//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Test
    UID:FKQKTlAcHr6irit-E7iuR3elGxFf@proton.me
    DTSTART;TZID=Europe/Paris:20210326T130000
    DTEND;TZID=Europe/Paris:20210326T140000
    ORGANIZER;CN=test@protonmail.com:mailto:test@proton
     mail.com
    ATTENDEE;CN=testattendee;ROLE=REQ-PARTICIPANT;RSVP=TRUE;PARTSTAT=NEEDS-AC
     TION:mailto:testattendee
    DTSTAMP:20210324T090044Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            // We allow any values for attendee email during the surgery, but we check the email validity in HandleIcsUseCase
            //  if we are in organizerMode, as there we require the attendee email to be canonicalizable to generate the token
        }
    }

    @Test
    fun `cleanAttendees ATTENDEE with Apple formatting`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonCalendar 4.1.8//EN
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    SEQUENCE:1
    STATUS:CONFIRMED
    SUMMARY:Test
    UID:FKQKTlAcHr6irit-E7iuR3elGxFf@proton.me
    DTSTART;TZID=Europe/Paris:20210326T130000
    DTEND;TZID=Europe/Paris:20210326T140000
    ORGANIZER;CN=test@protonmail.com:mailto:test@proton
     mail.com
    ATTENDEE;CN=test1@pm.me;CUTYPE=INDIVIDUAL;EMAIL=test1@pm.me;RSVP=TRUE;PARTST
     AT=NEEDS-ACTION:/1234567zMDQ4Mjk2MDIzMIZjbeHD-pCEmJU6loV23jx6n2nXhXA9yXmtoE
     412345/principal/
    ATTENDEE;CN=test2@protonmail.com;CUTYPE=INDIVIDUAL;EMAIL=test2@protonmail.co
     m;RSVP=TRUE;PARTSTAT=NEEDS-ACTION:/1234567zMDQ4Mjk2MDIzMIZjbeHD-pCEmJU6loV2
     3jx6n2nXhXA9yXmtoE412345/principal/
    ATTENDEE;CN=test31@example.com;EMAIL=test32@example.com;PARTSTAT=NEEDS
     -ACTION;ROLE=REQ-PARTICIPANT:test33
    ATTENDEE;CN=test41;EMAIL=test42@example.com;PARTSTAT=NEEDS-ACTION;ROLE
     =REQ-PARTICIPANT:test43
    ATTENDEE;CN=test51;EMAIL=test52@example.com;PARTSTAT=NEEDS-ACTION;ROLE
     =REQ-PARTICIPANT:test53
    ATTENDEE;CN=test61@example.com;EMAIL=test62;PARTSTAT=NEEDS-ACTION;ROLE
     =REQ-PARTICIPANT:test63
    DTSTAMP:20210324T090044Z
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;PARTSTAT=NEEDS-ACTION;CN=test1@pm.me:m\r\n ailto:test1@pm.me")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;PARTSTAT=NEEDS-ACTION;CN=test2@protonm\r\n ail.com:mailto:test2@protonmail.com")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=test31@example.com:m\r\n ailto:test32@example.com")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=test41:mailto:test42\r\n @example.com")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=test51:mailto:test52\r\n @example.com")).isEqualTo(true)
            assertThat(iCalendar.printToString().contains("ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=test61@example.com:m\r\n ailto:test61@example.com")).isEqualTo(true)

            event.attendees.forEach {
                assertThat(it.uri?.contains("/principal/") == true).isFalse()
                assertThat(it.uri?.contains("test") == true).isFalse()
            }
            assertThat(event.attendees[0].email).isEqualTo("test1@pm.me")
            assertThat(event.attendees[1].email).isEqualTo("test2@protonmail.com")
            assertThat(event.attendees[2].email).isEqualTo("test32@example.com")
            assertThat(event.attendees[3].email).isEqualTo("test42@example.com")
            assertThat(event.attendees[4].email).isEqualTo("test52@example.com")
            assertThat(event.attendees[5].email).isEqualTo("test61@example.com")
        }
    }

    @Test
    fun `cleanAttendees REPLY with multiple ATTENDEE`() {

        val iCalString = """
    BEGIN:VCALENDAR
    PRODID:-//Proton Technologies//ProtonMail 4.1.48//EN
    VERSION:2.0
    METHOD:REPLY
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    UID:gq5SSswmHEVRZsuySZFNjg6_V6QW@proton.me
    DTSTART;TZID=Europe/Paris:20210614T160000
    DTEND;TZID=Europe/Paris:20210614T163000
    SEQUENCE:0
    ORGANIZER;CN=breakingcalendar@protonmail.com:mailto:breakingcalendar@proton
     mail.com
    SUMMARY:Create Proton to proton 1
    X-PM-SHARED-EVENT-ID:NdGVn4ks6nlYxlieUeuah9J-Oeuw5-V8qHCZcPR6QHvyM6LzRfVZe7
     twRYW7Dk3_q__om5Rm9RHJOEL7XKIKSObOXZXJHS_0aEki2-C-d18=
    X-PM-SESSION-KEY:ZkVFqPfOknRQWnLsKN/unJAbRyitjYncBDdy0g/A2Ww=
    DTSTAMP:20210614T134325Z
    ATTENDEE;PARTSTAT=ACCEPTED:mailto:benjaminlovestesting@pm.me
    ATTENDEE;PARTSTAT=DECLINED:mailto:adamtst@pm.me
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error.Invalid.Attendees).isTrue()
    }

    @Test
    fun `cleanSequence SEQUENCE negative value test`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    BEGIN:VEVENT
    DTSTAMP:20190719T130854Z
    CREATED:20200505T173304Z
    LAST-MODIFIED:20200505T173304Z
    UID:6c4ad96a-632f-4b24-bdee-f5e048f70a0c@proton.test
    DTSTART;VALUE=DATE:20210102
    SEQUENCE:-2
    STATUS:CONFIRMED
    SUMMARY:API deploy
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar!!
            val event = iCalendar.events?.first()!!

            assertThat(iCalendar.printToString().contains("SEQUENCE:0")).isEqualTo(true)
            assertThat(event.sequence.value).isEqualTo(0)
        }
    }

    @Test
    fun `test invite with UID outside of VEVENT`() {

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

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
            assertThat(iCalendar?.printToString()?.contains("UID:0flbaqgv2ghu1fgo2oeeaqe1pb123@google.com")).isEqualTo(true)
            assertThat(iCalendar?.events?.first()?.uid?.value).isEqualTo("0flbaqgv2ghu1fgo2oeeaqe1pb123@google.com")
        }
    }

    @Test
    fun `test your VALID ics here`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.18.7//EN
    METHOD:REQUEST
    CALSCALE:GREGORIAN
    X-PM-SESSION-KEY:GOdCw1W1in43El2X+uVgMXrorJW87D6WN/6ao5nkrSI=
    X-PM-SHARED-EVENT-ID:_dVrW1d2kRgxU-pfRi5gZrZzmlEPKRHVrq5gA1UdzOU1RB8CN3nzLp
     ULi04uPDv9Nys3XJjTnVTiY0ApsQyKyM58Jt7hHBogG05YJrRgrH0=
    BEGIN:VEVENT
    DTSTART;TZID=Europe/Paris:20210310T200000
    DTEND;TZID=Europe/Paris:20210310T203000
    SEQUENCE:0
    ORGANIZER;CN=breakingcalendar@protonmail.com:mailto:breakingcalendar@proton
     mail.com
    SUMMARY:Test android 5
    STATUS:CONFIRMED
    DTSTAMP:20210310T183328Z
    UID:n3l-g25tlL00MzsgzqlVAKJB8RRs@proton.me
    ATTENDEE;X-PM-TOKEN=d7dff916bc6a007718f7e7bcb637821aee5279bb;RSVP=TRUE;ROLE
     =REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=:mailto:benjaminlovestesting@pro
     tonmail.com
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()

        if (cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful) {
            val iCalendar = cleanIcsResult.iCalendar

            assertThat(iCalendar).isNotNull()
        }
    }

    @Disabled
    @Test
    fun `ics error folder test`() {
        val files = File("./src/test/resources/ics/errors").listFiles()

        assertThat(files).isNotNull()

        files?.forEach {
            val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream("ics/errors/" + it.name)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val iCalString = bufferedReader.use { it.readText() }

            val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true, allowMultipleEvents = true)

            print("File tested: ${it.name}\n")
            assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error).isTrue()
        }
    }

    @Disabled
    @Test
    fun `ics valid folder test`() {
        val files = File("./src/test/resources/ics/valid").listFiles()

        assertThat(files).isNotNull()

        files?.forEach {
            val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream("ics/valid/" + it.name)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val iCalString = bufferedReader.use { it.readText() }

            val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true, allowMultipleEvents = true)

            print("File tested: ${it.name}\n")
            assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.ParsingSuccessful).isTrue()
        }
    }

    @Disabled
    @Test
    fun `ics error file test`() {
        val inputStream: InputStream? = this.javaClass.classLoader?.getResourceAsStream("ics/errors/RecurringRuleInconsistent.ics")
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val iCalString = bufferedReader.use { it.readText() }

        val cleanIcsResult = cleanIcs(iCalString, isOpeningFromProtonMail = true, allowMultipleEvents = true)

        assertThat(cleanIcsResult is IcsSurgeryUtils.HandleIcsResult.Error).isTrue()
    }
}
