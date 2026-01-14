package com.alamkanak.weekview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.util.Calendar

class DateExtensionsTest {

    @Test
    fun `returns correct day of week`() {
        val date = firstDayOfYear().withYear(2019)
        val expected = Calendar.TUESDAY
        val result = date.dayOfWeek
        assertEquals(expected, result)
    }

    @Test
    fun `does correct equality check`() {
        val first = firstDayOfYear().withYear(2019)
        val second = firstDayOfYear().withYear(2019)
        assertEquals(first, second)

        val newSecond = second.plusMillis(1)
        assertNotEquals(first, newSecond)
    }

    @Test
    fun `adds days correctly`() {
        val date = firstDayOfYear().withYear(2019)
        val result = date.plusDays(2)
        assertEquals(3, result.dayOfMonth)

        val secondResult = date.plusDays(31)
        assertEquals(1, secondResult.dayOfMonth)
        assertEquals(Calendar.FEBRUARY, secondResult.month)
    }

    @Test
    fun `toLocalDate() returns correct date`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 1995)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 24)
        }
        val localDate = LocalDate.of(1995, Month.AUGUST, 24)
        assertEquals(localDate, calendar.toLocalDate())
    }

    @Test
    fun `toLocalDateTime() returns correct date and time`() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 1995)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val localDateTime = LocalDateTime.of(1995, Month.AUGUST, 24, 6, 30, 0, 0)
        assertEquals(localDateTime, calendar.toLocalDateTime())
    }

    @Test
    fun `LocalDate toCalendar() returns correct date and time`() {
        val localDateTime = LocalDateTime.of(1995, Month.AUGUST, 24, 6, 30, 0, 0)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 1995)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(calendar, localDateTime.toCalendar())
    }

    @Test
    fun `LocalDateTime toCalendar() returns correct date and time`() {
        val localDateTime = LocalDateTime.of(1995, Month.AUGUST, 24, 6, 30, 0, 0)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, 1995)
            set(Calendar.MONTH, Calendar.AUGUST)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(calendar, localDateTime.toCalendar())
    }
}
