package me.proton.android.calendar.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import org.junit.jupiter.api.Test
import java.time.LocalDate


internal class EventUtilsTest {

    @Test
    fun `spans single day for part-time 2-hour ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210916T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isTrue()

    }

    @Test
    fun `spans single day for part-time zero-duration at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T000000
    DTEND;TZID=Europe/Zurich:20210915T000000
    SEQUENCE:0
    SUMMARY:Zero-duration start/ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isTrue()

    }

    @Test
    fun `spans single day for part-time 2-day ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210917T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight, 2-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isFalse()

    }

    @Test
    fun `spans single day for 1-day all-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210108
    SEQUENCE:0
    SUMMARY:1-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isTrue()

    }

    @Test
    fun `spans single day for 2-day all-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210109
    SEQUENCE:0
    SUMMARY:2-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.spansSingleDay(timeZoneId = timeZoneId)).isFalse()

    }

    @Test
    fun `calculate full day counter for part-time 2-hour ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210916T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 15), timeZoneId)).isEqualTo(Pair(1, 1))

    }

    @Test
    fun `calculate full day counter for part-time 2-day ending at Midnight`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T220000
    DTEND;TZID=Europe/Zurich:20210917T000000
    SEQUENCE:0
    SUMMARY:Ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 15), timeZoneId)).isEqualTo(Pair(1, 2))
        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 16), timeZoneId)).isEqualTo(Pair(2, 2))

    }

    @Test
    fun `calculate full day counter for part-time zero-duration`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;TZID=Europe/Zurich:20210915T000000
    DTEND;TZID=Europe/Zurich:20210915T000000
    SEQUENCE:0
    SUMMARY:Zero-duration starts/ends at Midnight
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 9, 15), timeZoneId)).isEqualTo(Pair(1, 1))

    }

    @Test
    fun `calculate full day counter for all-day 1-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210108
    SEQUENCE:0
    SUMMARY:1-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 1, 7), timeZoneId)).isEqualTo(Pair(1, 1))

    }

    @Test
    fun `calculate full day counter for all-day 2-day`() {

        val iCalString = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.4//EN
    BEGIN:VEVENT
    DTSTAMP:20210915T195051Z
    DTSTART;VALUE=DATE:20210107
    DTEND;VALUE=DATE:20210109
    SEQUENCE:0
    SUMMARY:2-day
    STATUS:CONFIRMED
    UID:v1uZ9ssGTcP5Lc28MTWj-VjQSPv3@proton.me
    END:VEVENT
    END:VCALENDAR
            """.trimIndent()

        val timeZoneId = "Europe/Zurich"
        val event = Event.from(
            "id",
            Calendar("id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()),
            ICalUtilsImpl.parseICalString(iCalString)!!,
            0
        )!!

        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 1, 7), timeZoneId)).isEqualTo(Pair(1, 2))
        assertThat(event.calculateFullDayCounter(LocalDate.of(2021, 1, 8), timeZoneId)).isEqualTo(Pair(2, 2))

    }

}
