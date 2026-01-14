package me.proton.android.calendar.domain.usecase

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateEventOccurrencesUseCaseTest {

    @MockK(relaxed = true) lateinit var logger: Logger
    @MockK(relaxed = true) lateinit var insertEventOccurrencesUseCase: InsertEventOccurrencesUseCase

    private val capturedEntities = slot<List<EventOccurrenceEntity>>()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        coEvery { insertEventOccurrencesUseCase.needsRefresh(any(), any()) } returns false
        coEvery { insertEventOccurrencesUseCase.execute(any(), any(), capture(capturedEntities)) } returns Result.success(
            Unit)
    }

    @AfterEach
    fun tearDown() {
        capturedEntities.clear()
    }

    @Test
    fun `monthly second Saturday in BuenosAires generates first occurrence on expected day`() = runBlocking {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        // 2nd Saturday in Nov 2025 = 2025-11-08
        val startLocal = ZonedDateTime.of(2025, 11, 8, 21, 30, 0, 0, zone)
        val endLocal = startLocal.plusHours(1)

        val meta = createMetadata(startLocal, endLocal)

        execute(metadata = meta)

        coVerify { insertEventOccurrencesUseCase.execute("u1", meta, any()) }
        val list = capturedEntities.captured
        assertTrue(list.isNotEmpty())

        val firstWithStart = list.firstOrNull { it.startTime != null }
        assertNotNull(firstWithStart)

        val expectedStart = startLocal.toInstant().epochSecond
        assertEquals(expectedStart, firstWithStart!!.firstOccurrenceStartTime)

        // ensure one stored window actually contains the first instance
        val windowHit = list.any {
            it.startTime == expectedStart &&
                    it.windowStartTime <= expectedStart && expectedStart <= it.windowEndTime
        }
        assertTrue(windowHit)
    }

    @Test
    fun `overnight end is adjusted and duration propagates`() = runBlocking {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val startLocal = ZonedDateTime.of(2025, 11, 8, 21, 30, 0, 0, zone)
        // bad end (same day 20:00) should roll to next day 20:00
        val badEndSameDay = ZonedDateTime.of(2025, 11, 8, 20, 0, 0, 0, zone)

        val meta = createMetadata(startLocal, badEndSameDay)

        execute(metadata = meta)

        coVerify { insertEventOccurrencesUseCase.execute("u1", meta, any()) }
        val list = capturedEntities.captured
        val occ = list.first { it.startTime != null && it.endTime != null }
        val duration = occ.endTime!! - occ.startTime!!
        val expected = (22 * 3600L) + (30 * 60L) // 22h30m
        assertEquals(expected, duration)
    }

    @Test
    fun `weekly MO,WE in Buenos Aires generates first occurrence and hits window`() = runBlocking {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        // Monday 2025-11-03 09:00 (-03)
        val startLocal = ZonedDateTime.of(2025, 11, 3, 9, 0, 0, 0, zone)
        val endLocal = startLocal.plusHours(1)
        val meta = createMetadata(
            startLocal,
            endLocal,
            rrule = "FREQ=WEEKLY;BYDAY=MO,WE"
        )

        execute(metadata = meta)

        coVerify { insertEventOccurrencesUseCase.execute("u1", meta, any()) }
        val list = capturedEntities.captured
        assertTrue(list.isNotEmpty())

        val firstWithStart = list.firstOrNull { it.startTime != null }
        assertNotNull(firstWithStart)
        assertEquals(startLocal.toInstant().epochSecond, firstWithStart!!.firstOccurrenceStartTime)

        // the month window of Nov 2025 should contain the Monday 3rd occurrence
        val expectedStart = startLocal.toInstant().epochSecond
        val windowHit = list.any {
            it.startTime == expectedStart &&
                    it.windowStartTime <= expectedStart && expectedStart <= it.windowEndTime
        }
        assertTrue(windowHit)
    }

    @Test
    fun `monthly by month day 31 has non occurring April window with null Start and End`() = runBlocking {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        // start on a 31st so BYMONTHDAY=31 has months without an occurrence (e.g., April)
        val startLocal = ZonedDateTime.of(2025, 1, 31, 10, 0, 0, 0, zone)
        val endLocal = startLocal.plusHours(1)
        val meta = createMetadata(
            startLocal,
            endLocal,
            rrule = "FREQ=MONTHLY;BYMONTHDAY=31"
        )

        execute(metadata = meta)

        val list = capturedEntities.captured
        assertTrue(list.isNotEmpty())

        // find April 2025 window start (local start-of-day converted to epoch)
        val aprilStart = ZonedDateTime.of(2025, 4, 1, 0, 0, 0, 0, zone).toInstant().epochSecond
        val aprilWindow = list.firstOrNull { it.windowStartTime == aprilStart }
        assertNotNull(aprilWindow)

        // no 31st in April - should record a non-occurring window (null start/end)
        assertEquals(null, aprilWindow!!.startTime)
        assertEquals(null, aprilWindow.endTime)
    }

    @Test
    fun `monthly second Saturday with until stops after last occurrence`() = runBlocking {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        // 2nd Saturday November 2025 at 21:30
        val startDate = ZonedDateTime.of(2025, 11, 8, 21, 30, 0, 0, zone)
        val meta = createMetadata(
            start = startDate,
            end = startDate.plusHours(1),
            // stop around February 2026's 2nd Saturday
            rrule = "FREQ=MONTHLY;BYDAY=SA;BYSETPOS=2;UNTIL=20260228T235959Z"
        )

        execute(metadata = meta)

        val list = capturedEntities.captured
        assertTrue(list.isNotEmpty())

        // last occurrence not later than Feb 2026
        val maxWindowStart = list.maxOf { it.windowStartTime }
        // Mar 1, 2026 00:00 local as an exclusive bound
        val marchStart = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, zone).toInstant().epochSecond
        assertTrue(maxWindowStart < marchStart, "windows should not extend past Feb 2026")

        // for any entity stored, lastOccurrenceEndTime should be set (finite series)
        val anyEntity = list.first()
        assertNotNull(anyEntity.lastOccurrenceEndTime, "finite series should have lastOccurrenceEndTime")
        // we have a concrete start for Nov 2025
        val expectedStart = startDate.toInstant().epochSecond
        assertTrue(list.any { it.startTime == expectedStart })
    }

    private suspend fun execute(metadata: EventEntityMetadata) = UpdateEventOccurrencesUseCase(logger, insertEventOccurrencesUseCase)
        .execute(userId = "u1", eventEntityMetadata = metadata)

    private fun createMetadata(
        start: ZonedDateTime,
        end: ZonedDateTime,
        rrule: String = "FREQ=MONTHLY;BYDAY=SA;BYSETPOS=2",
        fullDay: Int = 0,
    ) = EventEntityMetadata(
        id = "e1",
        calendarId = "cal1",
        sharedEventId = "s1",
        addressId = null,
        startTime = start.toInstant().epochSecond,
        startTimeZone = start.zone.id,
        endTime = end.toInstant().epochSecond,
        endTimeZone = end.zone.id,
        fullDay = fullDay,
        uid = "UID-1",
        recurrenceID = null,
        exDates = emptyList(),
        rRule = rrule,
        createTime = 0,
        modifyTime = 1,
        isOrganizer = 1,
        sharedKeyPacket = null,
        calendarKeyPacket = null,
        addressKeyPacket = null,
        isPersonalSingleEdit = false,
    )
}
