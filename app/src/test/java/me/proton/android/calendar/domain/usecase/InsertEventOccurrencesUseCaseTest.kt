package me.proton.android.calendar.domain.usecase

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.EventOccurrencesDao
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.domain.Logger
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsertEventOccurrencesUseCaseTest {

    private val occurrencesDao = mockk<EventOccurrencesDao>()
    private val database = mockk<AppDatabase> {
        every { eventOccurrencesDao() } returns occurrencesDao
        coEvery { inTransaction(any<suspend () -> Any?>()) } coAnswers {
            firstArg<suspend () -> Any?>().invoke()
        }
    }
    private val logger = mockk<Logger>(relaxed = true)

    private val useCase = InsertEventOccurrencesUseCase(logger, database)

    @Test
    fun `needsRefresh lets rows without a startTimeZone regenerate`() = runTest {
        // untouched events never bump modifyTime, so it can't be the only freshness key
        stubFreshRows(exist = true, complete = false)

        assertFalse(useCase.needsRefresh("u1", metadata(startTimeZone = "Europe/Zurich")))
    }

    @Test
    fun `needsRefresh keeps fresh complete rows`() = runTest {
        stubFreshRows(exist = true, complete = true)

        assertTrue(useCase.needsRefresh("u1", metadata(startTimeZone = "Europe/Zurich")))
    }

    @Test
    fun `needsRefresh keeps fresh rows when the metadata has no startTimeZone either`() = runTest {
        // otherwise blank-tz metadata would regenerate on every fetch
        stubFreshRows(exist = true, complete = false)

        assertTrue(useCase.needsRefresh("u1", metadata(startTimeZone = "")))
    }

    @Test
    fun `needsRefresh is false when no rows are fresh`() = runTest {
        stubFreshRows(exist = false, complete = false)

        assertFalse(useCase.needsRefresh("u1", metadata(startTimeZone = "Europe/Zurich")))
    }

    @Test
    fun `needsRefresh lets inflated rows of a zero-duration recurring event regenerate`() = runTest {
        // rows written while zero duration was bumped to a day never refresh via modifyTime alone
        coEvery {
            occurrencesDao.hasOccurrenceWithEqualOrHigherModifyTime(
                "u1", "cal1", "e1", 1L, requireStartTimeZone = true, requireZeroDuration = true,
            )
        } returns false

        assertFalse(useCase.needsRefresh("u1", metadata(startTimeZone = "Europe/Zurich", durationSeconds = 0)))
    }

    @Test
    fun `needsRefresh keeps fresh rows of a zero-duration recurring event`() = runTest {
        coEvery {
            occurrencesDao.hasOccurrenceWithEqualOrHigherModifyTime(
                "u1", "cal1", "e1", 1L, requireStartTimeZone = true, requireZeroDuration = true,
            )
        } returns true

        assertTrue(useCase.needsRefresh("u1", metadata(startTimeZone = "Europe/Zurich", durationSeconds = 0)))
    }

    // fresh = modifyTime >= the metadata's, complete = has a startTimeZone
    private fun stubFreshRows(exist: Boolean, complete: Boolean) {
        coEvery {
            occurrencesDao.hasOccurrenceWithEqualOrHigherModifyTime(
                "u1", "cal1", "e1", 1L, requireStartTimeZone = true, requireZeroDuration = false,
            )
        } returns (exist && complete)
        coEvery {
            occurrencesDao.hasOccurrenceWithEqualOrHigherModifyTime(
                "u1", "cal1", "e1", 1L, requireStartTimeZone = false, requireZeroDuration = false,
            )
        } returns exist
    }

    private fun metadata(startTimeZone: String, durationSeconds: Long = 3_600L) = EventEntityMetadata(
        id = "e1",
        calendarId = "cal1",
        sharedEventId = "s1",
        addressId = null,
        startTime = 1_750_000_000L,
        startTimeZone = startTimeZone,
        endTime = 1_750_000_000L + durationSeconds,
        endTimeZone = startTimeZone,
        fullDay = 0,
        uid = "UID-1",
        recurrenceID = null,
        exDates = emptyList(),
        rRule = "FREQ=WEEKLY",
        createTime = 0,
        modifyTime = 1,
        isOrganizer = 1,
        sharedKeyPacket = null,
        calendarKeyPacket = null,
        addressKeyPacket = null,
        isPersonalSingleEdit = false,
    )
}
