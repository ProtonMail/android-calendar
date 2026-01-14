package me.proton.android.calendar.domain.usecase

import android.util.Log
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.EventAlarmsDao
import me.proton.android.calendar.data.db.EventOccurrencesDao
import me.proton.android.calendar.data.db.EventsDao
import me.proton.android.calendar.data.db.EventsMetadataDao
import me.proton.android.calendar.data.db.SearchDao
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RefreshDeletedEventsUseCaseTest {

    private val testsLogger = TestsLogger

    private val featureFlagManager: FeatureFlagManager = mockk()
    private val database: AppDatabase = mockk()
    private val searchDatabase: SearchDatabase = mockk()

    private val occurrencesDao: EventOccurrencesDao = mockk()
    private val eventsDao: EventsDao = mockk()
    private val alarmsDao: EventAlarmsDao = mockk()
    private val metadataDao: EventsMetadataDao = mockk()
    private val searchDao: SearchDao = mockk()

    private fun sut() = RefreshDeletedEventsUseCase(
        logger = testsLogger,
        featureFlagManager = featureFlagManager,
        database = database,
        searchDatabase = searchDatabase,
    )

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true

        every { database.eventOccurrencesDao() } returns occurrencesDao
        every { database.eventsDao() } returns eventsDao
        every { database.eventAlarmsDao() } returns alarmsDao
        every { database.eventsMetadataDao() } returns metadataDao

        every { searchDatabase.searchDao() } returns searchDao

        coEvery { database.inTransaction(any<suspend () -> Unit>()) } coAnswers {
            (arg<suspend () -> Unit>(0)).invoke()
        }
    }

    @Test
    fun `no local overlaps - no deletions`() = runBlocking {
        // Given
        flagEnabled(true)
        coEvery {
            occurrencesDao.selectEventKeysOverlapping(
                any(),
                any(),
                any(),
                any(),
            )
        } returns emptyList()
        // When
        val res = sut().execute(
            userId = UserId("u1"),
            calendarIdsToFetch = listOf("c1"),
            fromDate = LocalDate.of(2025, 1, 1),
            toDate = LocalDate.of(2025, 1, 2),
            timeZoneId = "UTC",
            eventsAndMetadatas = listOf(event("e2", "c1") to metadata("e2", "c1"))
        )
        // Then
        coVerify(exactly = 1) {
            occurrencesDao.selectEventKeysOverlapping(
                "u1",
                listOf("c1"),
                any(),
                any(),
            )
        }
        coVerify(exactly = 0) { eventsDao.deleteByIds(any()) }
        coVerify(exactly = 0) { alarmsDao.deleteAllByEventId(any()) }
        coVerify(exactly = 0) { metadataDao.deleteByEventIds(any()) }
        coVerify(exactly = 0) { searchDao.deleteSearchEventsForEvents(any(), any(), any()) }
        assert(res is UseCase.Result.Success<*>)
    }

    @Test
    fun `stale local events - deletes occurrences, alarms, events, search, metadata`() =
        runBlocking {
            // Given
            flagEnabled(true)

            // locally we have e1 in window; but we fetch only e2 - e1 should be deleted
            coEvery {
                occurrencesDao.selectEventKeysOverlapping("u1", listOf("c1"), any(), any())
            } returns listOf(EventOccurrencesDao.EventKey(eventId = "e1", calendarId = "c1"))

            coEvery { alarmsDao.deleteAllByEventId(any()) } just Runs
            coEvery { occurrencesDao.deleteAllForEvent(any(), any(), any()) } just Runs
            coEvery { eventsDao.deleteByIds(any()) } just Runs
            coEvery { metadataDao.deleteByEventIds(any()) } just Runs
            coEvery { searchDao.deleteSearchEventsForEvents(any(), any(), any()) } returns -1
            // When
            val res = sut().execute(
                userId = UserId("u1"),
                calendarIdsToFetch = listOf("c1"),
                fromDate = LocalDate.of(2025, 1, 1),
                toDate = LocalDate.of(2025, 1, 2),
                timeZoneId = "UTC",
                eventsAndMetadatas = listOf(event("e2", "c1") to metadata("e2", "c1")),
            )
            // Then
            coVerify { occurrencesDao.selectEventKeysOverlapping("u1", listOf("c1"), any(), any()) }
            coVerify { occurrencesDao.deleteAllForEvent("u1", "c1", "e1") }
            coVerify { alarmsDao.deleteAllByEventId("e1") }
            coVerify { eventsDao.deleteByIds(match { it == listOf("e1") }) }
            coVerify {
                searchDao.deleteSearchEventsForEvents(
                    userId = "u1",
                    calendarId = "c1",
                    eventIds = match { it == listOf("e1") },
                )
            }
            coVerify { metadataDao.deleteByEventIds(match { it == listOf("e1") }) }
            assert(res is UseCase.Result.Success<*>)
        }

    private fun flagEnabled(value: Boolean) {
        val flag = mockk<FeatureFlag> {
            every { this@mockk.value } returns value
        }
        coEvery { featureFlagManager.get(any(), any()) } returns flag
    }

    private fun metadata(id: String, cal: String) = EventEntityMetadata(
        id = id,
        calendarId = cal,
        sharedEventId = "sh$id",
        addressId = null,
        startTime = 1L,
        startTimeZone = "UTC",
        endTime = 2L,
        endTimeZone = "UTC",
        fullDay = 0,
        uid = "uid-$id",
        recurrenceID = null,
        exDates = emptyList(),
        rRule = null,
        createTime = 0L,
        modifyTime = 10L,
        isOrganizer = 1,
        sharedKeyPacket = null,
        calendarKeyPacket = null,
        addressKeyPacket = null,
        isPersonalSingleEdit = false,
    )

    private fun event(id: String, cal: String) = EventEntity(
        id = id,
        calendarId = cal,
        sharedEventId = "sh$id",
        calendarKeyPacket = null,
        createTime = 0L,
        modifyTime = 10L,
        permissions = 0,
        addressKeyPacket = null,
        addressId = null,
        sharedKeyPacket = null,
        sharedEvents = emptyList(),
        calendarEvents = emptyList(),
        attendeesEvents = emptyList(),
        attendees = emptyList(),
        attendeesInfo = emptyList(),
        isProtonProtonInvite = null,
        notifications = null,
        color = null,
    )
}