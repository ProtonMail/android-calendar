package me.proton.android.calendar.domain.usecase

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.fail
import biweekly.Biweekly
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.parameter.Related
import biweekly.property.Attendee
import biweekly.property.Organizer
import biweekly.property.Status
import biweekly.property.Transparency
import biweekly.property.Trigger
import biweekly.util.Duration
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_CREATOR
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_CREATOR_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_HOST
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_HOST_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PASSWORD
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PASSWORD_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PROVIDER
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PROVIDER_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_CONFERENCE_ID
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_CONFERENCE_URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStartTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.wrapInICalendar
import org.junit.jupiter.api.Test

internal class EditCreateEventUseCaseTest {

    @Test
    fun `split iCalendar according to the matrix`() {

        val event = VEvent().apply {
            setUid(ICalUtilsImpl.generateProtonUid())
            setCreated(Date())
            setLastModified(getCreated().value)
            setDateStart(Date.from(ZonedDateTime.of(LocalDate.of(2020, 4, 2), LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant()), false)
            setDateEnd(Date.from(ZonedDateTime.of(LocalDate.of(2020, 4, 3), LocalTime.MIDNIGHT, ZoneId.systemDefault()).toInstant()), false)
            setDateTimeStamp(Date.from(Instant.ofEpochSecond(666)))
            setSummary("All-day event on 2nd April")
            setDescription("2 alarms, 30 minutes (display) and 2 hours (email) before")
            setLocation("Zurich")
            addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(30).build(), Related.START), null))
            addAlarm(VAlarm.email(Trigger(Duration.builder().prior(true).hours(2).build(), Related.START), null, null))
            setStatus(Status.tentative())
            addComment("Comment no 1")
            addComment("Comment no 2")
            setTransparency(true)

            val attendee = Attendee("John Doe", "john.doe@pm.me")
            attendee.addParameter(X_PM_TOKEN, "X-PM-TOKEN")
            addAttendee(attendee)

            setOrganizer(Organizer("organizer@pm.me", "organizer@pm.me"))

            setExperimentalProperty(X_PM_CONFERENCE_ID, "77896951805").run {
                this.setParameter(PARAMETER_CONFERENCE_PROVIDER, "1")
                this.setParameter(PARAMETER_CONFERENCE_CREATOR, "WStPKICb69ynQxIR5GXm6OuK4okqkADvrnC-K4D8VfZ4f0fbSnqNDek6XrfygCqAvRdZX43YAWG8M-xjF8IDDQ==")
            }

            setExperimentalProperty(X_PM_CONFERENCE_URL, "https://us06web.zoom.us/j/89317403034?pwd=mv5ztORtJOHVQkl8KUc9mFLsClS3te.1").run {
                this.setParameter(PARAMETER_CONFERENCE_PASSWORD, "356224")
                this.setParameter(PARAMETER_CONFERENCE_HOST, "proton662@urey.proton.black")
            }
        }

        TestsLogger.d("original event: ${event.wrapInICalendar().printToString()}")

        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(event.wrapInICalendar())

        TestsLogger.d("raw shared split:\n${Biweekly.write(calendarSplit.sharedPart).go()}")
        TestsLogger.d("raw shared encrypted split:\n${Biweekly.write(calendarSplit.sharedPartToEncrypt).go()}")

        TestsLogger.d("raw personal split:\n${Biweekly.write(calendarSplit.personalPart).go()}")

        with (calendarSplit.sharedPart.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.created).isEqualTo(event.created)
            assertThat(this.lastModified).isEqualTo(event.lastModified)
            assertThat(this.dateStart.value.time).isEqualTo(ZonedDateTime.of(LocalDate.of(2020, 4, 2), LocalTime.MIDNIGHT, ZoneId.systemDefault()).toEpochSecond() * 1000)
            assertThat(this.dateStart.value.hasTime()).isFalse()
            assertThat(calendarSplit.sharedPart.timezoneInfo.getTimezone(this.dateStart)).isNull()
            assertThat(this.dateEnd.value.time).isEqualTo(ZonedDateTime.of(LocalDate.of(2020, 4, 3), LocalTime.MIDNIGHT, ZoneId.systemDefault()).toEpochSecond() * 1000)
            assertThat(this.dateEnd.value.hasTime()).isFalse()
            assertThat(calendarSplit.sharedPart.timezoneInfo.getTimezone(this.dateEnd)).isNull()
            // TODO rrule, recurrence-id, sequence, exdate
            assertThat(this.dateTimeStamp.value).isEqualTo(Date.from(Instant.ofEpochSecond(666)))
            assertThat(this.summary).isNull()
            assertThat(this.description).isNull()
            assertThat(this.status).isNull()
            assertThat(this.alarms).isEmpty()
            assertThat(this.comments).isEmpty()
            assertThat(calendarSplit.sharedPart.timezoneInfo.timezones).isEmpty()
            assertThat(this.organizer).isEqualTo(Organizer("organizer@pm.me", "organizer@pm.me"))
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_ID).value).isEqualTo("77896951805")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_ID).getParameter(PARAMETER_CONFERENCE_PROVIDER)).isEqualTo("1")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_ID).getParameter(PARAMETER_CONFERENCE_CREATOR)).isEqualTo("WStPKICb69ynQxIR5GXm6OuK4okqkADvrnC-K4D8VfZ4f0fbSnqNDek6XrfygCqAvRdZX43YAWG8M-xjF8IDDQ==")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL)).isNull()
        }

        with (calendarSplit.sharedPartToEncrypt.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.dateStart).isNull()
            assertThat(this.dateEnd).isNull()
            assertThat(this.description.value.toString()).isEqualTo("2 alarms, 30 minutes (display) and 2 hours (email) before")
            assertThat(this.summary.value.toString()).isEqualTo("All-day event on 2nd April")
            assertThat(this.location.value.toString()).isEqualTo("Zurich")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL).value).isEqualTo("https://us06web.zoom.us/j/89317403034?pwd=mv5ztORtJOHVQkl8KUc9mFLsClS3te.1")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL).getParameter(PARAMETER_CONFERENCE_PASSWORD)).isEqualTo("356224")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL).getParameter(PARAMETER_CONFERENCE_HOST)).isEqualTo("proton662@urey.proton.black")
        }

        with (calendarSplit.calendarPart!!.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.status).isEqualTo(Status.tentative())
            assertThat(this.transparency).isEqualTo(Transparency.transparent())
        }

        with (calendarSplit.calendarPartToEncrypt!!.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.comments.size).isEqualTo(2)
            assertThat(this.comments[0].value).isEqualTo("Comment no 1")
            assertThat(this.comments[1].value).isEqualTo("Comment no 2")
            // TODO all the other properties not mentioned in matrix should be here
        }

        with (calendarSplit.personalPart!!.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.alarms.size).isEqualTo(2)
            assertThat(this.alarms[0].action.isDisplay).isTrue()
            assertThat(this.alarms[0].trigger.duration.minutes).isEqualTo(30)
            assertThat(this.alarms[1].action.isEmail).isTrue()
            assertThat(this.alarms[1].trigger.duration.hours).isEqualTo(2)
        }

        with (calendarSplit.attendeesPart!!.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.attendees.size).isEqualTo(1)
            assertThat(this.attendees[0].email).isEqualTo("john.doe@pm.me")
            assertThat(this.attendees[0].commonName).isEqualTo("John Doe")
            assertThat(this.attendees[0].getParameter("X-PM-TOKEN")).isEqualTo("X-PM-TOKEN")
        }
        // TODO Attendees Part

        // TODO sequence
        // recurrence id
        // RRULE



        // By default, biweekly writes all date/time values in UTC time

        // formatting dates under a timezone
        // TimeZone javaTz = TimeZone.getTimeZone("America/New_York");
        //TimezoneAssignment newYork = TimezoneAssignment.download(javaTz, false);
        //
        //ICalendar ical = ...
        //ical.getTimezoneInfo().setDefaultTimezone(newYork);

        // Recurrence recur = new Recurrence.Builder(Frequency.WEEKLY).interval(2).build();
        //RecurrenceRule rrule = new RecurrenceRule(recur);
        //WriteContext context = new WriteContext(ICalVersion.V2_0, new TimezoneInfo(), null);
        //RecurrenceRuleScribe scribe = new RecurrenceRuleScribe();
        //System.out.println(scribe.writeText(rrule, context));
        ////prints: FREQ=WEEKLY;INTERVAL=2

    }

    @Test
    fun `split iCalendar according to the matrix and migrate legacy Zoom properties`() {

        val event = VEvent().apply {
            setUid(ICalUtilsImpl.generateProtonUid())

            setExperimentalProperty(X_PM_CONFERENCE_ID, "77896951805").run {
                this.setParameter(PARAMETER_CONFERENCE_PROVIDER_READONLY, "1")
                this.setParameter(PARAMETER_CONFERENCE_CREATOR_READONLY, "WStPKICb69ynQxIR5GXm6OuK4okqkADvrnC-K4D8VfZ4f0fbSnqNDek6XrfygCqAvRdZX43YAWG8M-xjF8IDDQ==")
            }
            setExperimentalProperty(X_PM_CONFERENCE_URL, "https://us06web.zoom.us/j/89317403034?pwd=mv5ztORtJOHVQkl8KUc9mFLsClS3te.1").run {
                this.setParameter(PARAMETER_CONFERENCE_PASSWORD_READONLY, "356224")
                this.setParameter(PARAMETER_CONFERENCE_HOST_READONLY, "proton662@urey.proton.black")
            }
        }

        TestsLogger.d("original event: ${event.wrapInICalendar().printToString()}")

        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(event.wrapInICalendar())

        TestsLogger.d("raw shared split:\n${Biweekly.write(calendarSplit.sharedPart).go()}")
        TestsLogger.d("raw shared encrypted split:\n${Biweekly.write(calendarSplit.sharedPartToEncrypt).go()}")

        with (calendarSplit.sharedPart.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_ID).value).isEqualTo("77896951805")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_ID).getParameter(PARAMETER_CONFERENCE_PROVIDER)).isEqualTo("1")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_ID).getParameter(PARAMETER_CONFERENCE_CREATOR)).isEqualTo("WStPKICb69ynQxIR5GXm6OuK4okqkADvrnC-K4D8VfZ4f0fbSnqNDek6XrfygCqAvRdZX43YAWG8M-xjF8IDDQ==")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL)).isNull()
        }

        with (calendarSplit.sharedPartToEncrypt.events[0]) {
            assertThat(this.uid).isEqualTo(event.uid)
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL).value).isEqualTo("https://us06web.zoom.us/j/89317403034?pwd=mv5ztORtJOHVQkl8KUc9mFLsClS3te.1")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL).getParameter(PARAMETER_CONFERENCE_PASSWORD)).isEqualTo("356224")
            assertThat(this.getExperimentalProperty(X_PM_CONFERENCE_URL).getParameter(PARAMETER_CONFERENCE_HOST)).isEqualTo("proton662@urey.proton.black")
        }
    }

    @Test
    fun `VTIMEZONE is empty and date start & end have correct TZID timezones assigned after splitting`() {

        val event = VEvent().apply {
            setUid(ICalUtilsImpl.generateProtonUid())
            setStart(LocalDate.of(2020, 4, 2), LocalTime.of(15, 5, 20), "Europe/Zurich")
            setEnd(LocalDate.of(2020, 4, 3), LocalTime.of(17, 10, 30), "Europe/Zurich")
        }

        val calendar = event.wrapInICalendar()
        calendar.setStartTimeZone("Europe/Zurich")
        calendar.setEndTimeZone("Europe/Zurich")

        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(calendar)

        assertThat(calendarSplit.sharedPart.timezoneInfo.getTimezone(calendarSplit.sharedPart.events.first().dateStart).timeZone.id).isEqualTo("Europe/Zurich")
        assertThat(calendarSplit.sharedPart.timezoneInfo.getTimezone(calendarSplit.sharedPart.events.first().dateEnd).timeZone.id).isEqualTo("Europe/Zurich")

        assertThat(calendarSplit.sharedPart.timezoneInfo.timezones).isEmpty()

    }

    @Test
    fun `non-required parts are empty after splitting minimal event`() {

        val event = VEvent().apply {
            setUid(ICalUtilsImpl.generateProtonUid())
            setCreated(Date())
            setLastModified(getCreated().value)
            setDateStart(Date.from(LocalDate.of(2020, 4, 2).atStartOfDay(ZoneId.systemDefault()).toInstant()), false)
            setDateEnd(Date.from(LocalDate.of(2020, 4, 3).atStartOfDay(ZoneId.systemDefault()).toInstant()), false)
            setSummary("Test")
        }

        val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(event.wrapInICalendar())

        assertThat(calendarSplit.calendarPart).isNull()
        assertThat(calendarSplit.calendarPartToEncrypt).isNull()
        assertThat(calendarSplit.personalPart).isNull()
        // TODO attendees part

    }

    fun `encrypt and sign split iCalendar`() {
        fail("TODO")
    }




    val icalendarString = """
        not-decrypted shared event: BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-b9e255c8-2b3d-9785-7381-29fece3c3105
    DTSTAMP:20200316T083628Z
    RRULE:FREQ=WEEKLY
    DTSTART;TZID=Europe/Budapest:20200330T100000
    DTEND;TZID=Europe/Budapest:20200330T110000
    END:VEVENT
    END:VCALENDAR
V/TimberLogger: decrypted shared event: BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-b9e255c8-2b3d-9785-7381-29fece3c3105
    DTSTAMP:20200316T083628Z
    SUMMARY:Event with all fields filled out\, every week repeat\, notif 15 min
     s before
    LOCATION:Zuricco
    DESCRIPTION:Lorem phat for sure yo mamma amet\, dang adipiscing tellivizzle
     . Fo shizzle my nizzle yo\, shut the shizzle up volutpizzle\, pot quis\, cr
     ackalackin vel\, yippiyo. Pellentesque fizzle phat. Shizzlin dizzle erizzle
     . Gangster izzle doggy dapibizzle fo shizzle my
    END:VEVENT
    END:VCALENDAR
V/TimberLogger: signature ok for personal event: true
V/TimberLogger: not-decrypted personal event: BEGIN:VCALENDAR
    VERSION:2.0
    BEGIN:VEVENT
    UID:proton-calendar-b9e255c8-2b3d-9785-7381-29fece3c3105
    DTSTAMP:20200316T083628Z
    BEGIN:VALARM
    TRIGGER:-PT15M
    ACTION:DISPLAY
    END:VALARM
    END:VEVENT
    END:VCALENDAR
    """.trimIndent()

}
