package me.proton.android.calendar.domain.usecase

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.EventOccurrencesDao
import me.proton.android.calendar.data.db.EventsDao
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.UserInfo
import me.proton.core.domain.entity.UserId
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetUiEventsUseCaseTest {

    private val occurrencesDao = mockk<EventOccurrencesDao>()
    private val eventsDao = mockk<EventsDao>()
    private val database = mockk<AppDatabase>() {
        every { this@mockk.eventsDao() } returns eventsDao
        every { this@mockk.eventOccurrencesDao() } returns occurrencesDao
    }
    private val decryptor = mockk<EventDecryptor>(relaxed = true)

    private val calendarId = "cal1"
    private val userId = UserId("u1")
    private val getUserInfo = mockk<GetUserInfoUseCase> {
        every { this@mockk.invoke() } returns flowOf(UserInfo(userId = userId, emptyList(), true))
    }
    private val loadingState = mockk<LoadingStateUseCase> {
        every { this@mockk.invoke() } returns flowOf(mockLoadingState(inProgress = false))
    }

    private val tz = "Europe/Zurich"
    private val zone = ZoneId.of(tz)

    private val from = LocalDate.of(2025, 1, 1)
    private val to = LocalDate.of(2025, 2, 2)
    private val fromSec = from.atStartOfDay(zone).toInstant().epochSecond
    private val toSec = to.plusDays(1).atStartOfDay(zone).toInstant().epochSecond

    private val calendarsRepo = mockk<CalendarsRepository> {
        coEvery {
            this@mockk.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                any(), any(), any(), any(), any(), any(), any()
            )
        } answers {
            val e: Event = arg(0)
            val tzArg: String = arg(4)
            val emails: List<String> = arg(5)
            val isFree: Boolean = arg(6)
            listOf(buildUiEventFrom(e, tzArg, emails, isFree))
        }
        every { this@mockk.flowAllCalendars(userId.id) } returns flowOf(listOf(mockCalendar(calendarId)))
    }

    @Test
    fun `correctly aggregates`() = runTest {
        // Given
        val cachedEvents = mutableMapOf<String, Event>()
        fun seedEventsAndOccurrences(prefix: String, count: Int, startHourBase: Int) = (0 until count).map { i ->
            val id = "$prefix-e$i"
            val uid = "uid-$prefix-$i"
            val start = from.atStartOfDay(zone).plusHours((startHourBase + i).toLong())
            val end = start.plusHours(1)
            cachedEvents[id] = createEvent(
                id = id,
                uid = uid,
                tz = tz,
                start = start,
                end = end,
                isSingleEdit = false,
            )
            mockOccurrence(
                userId = userId.id,
                calendarId = calendarId,
                eventId = id,
                eventUid = uid,
                modifyTime = (i + 1).toLong(),
                windowStart = fromSec - ONE_DAY_SEC,
                windowEnd = toSec + ONE_DAY_SEC,
                startTime = start.toInstant().epochSecond,
            )
        }
        val countPerGroup = 100
        val nonRecurring = seedEventsAndOccurrences("n", countPerGroup, 2)
        val finite = seedEventsAndOccurrences("f", countPerGroup, 4)
        val infinite = seedEventsAndOccurrences("i", countPerGroup, 6)

        coEvery { decryptor.getFromCache(any(), calendarId, any()) } answers { cachedEvents[arg(0)]!! }
        every { occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any()) } returns flowOf(nonRecurring)
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns flowOf(finite)
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(infinite)

        coEvery { calendarsRepo.selectEventEntitiesByUids(any()) } returns emptyMap()

        execute(from, to).test {
            // When / Then
            val first = awaitItem()
            require(first is CalendarsRepository.GetEventsResult.Success)

            val events = first.events
            val nonCount = events.count { it.summary!!.startsWith("Summary-n-") }
            val finiteCount = events.count { it.summary!!.startsWith("Summary-f-") }
            val infiniteCount = events.count { it.summary!!.startsWith("Summary-i-") }

            assertEquals(countPerGroup, nonCount, "non-recurring count")
            assertEquals(countPerGroup, finiteCount, "finite-recurring count")
            assertEquals(countPerGroup, infiniteCount, "infinite-recurring count")
            assertEquals(3 * countPerGroup, events.size, "total events")
            assertTrue(first.fullyLoaded)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `decrypts when cache miss`() = runTest {
        // Given
        val start = from.atStartOfDay(zone).plusHours(9)
        val end = start.plusHours(1)

        val occ = mockOccurrence(
            userId = userId.id,
            calendarId = calendarId,
            eventId = "e1",
            eventUid = "uid1",
            modifyTime = 1L,
            windowStart = fromSec,
            windowEnd = toSec,
            startTime = start.toInstant().epochSecond
        )

        coEvery { decryptor.getFromCache("e1", calendarId, any()) } returns null
        coEvery { eventsDao.selectById("e1") } returns mockk(relaxed = true)
        coEvery { decryptor.decrypt(any()) } returns createEvent("e1", "uid1", tz, start, end, isSingleEdit = false)

        every {
            occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any())
        } returns flowOf(listOf(occ))
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(emptyList())

        coEvery { calendarsRepo.selectEventEntitiesByUids(any()) } returns emptyMap()

        execute(from, to).test {
            // When / Then
            awaitItem()
            coVerifySequence {
                decryptor.setCalendars(any())
                decryptor.getFromCache("e1", calendarId, any())
                eventsDao.selectById("e1")
                decryptor.decrypt(any())
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters events with NoAddressKey`() = runTest {
        // Given
        val start = from.atStartOfDay(zone).plusHours(8)
        val end = start.plusHours(1)

        val failedEvent = createEvent("e2","uid2",tz, start, end, isSingleEdit = false,
            decryptionStatus = Event.DecryptionStatus.Failure.NoAddressKey)

        val occ = mockOccurrence(
            userId = userId.id,
            calendarId = calendarId,
            eventId = "e2",
            eventUid = "uid2",
            modifyTime = 1L,
            windowStart = fromSec,
            windowEnd = toSec,
            startTime = start.toInstant().epochSecond
        )

        coEvery { decryptor.getFromCache("e2", calendarId, any()) } returns failedEvent

        every {
            occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any())
        } returns flowOf(listOf(occ))
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(emptyList())

        coEvery { calendarsRepo.selectEventEntitiesByUids(any()) } returns emptyMap()

        execute(from, to).test {
            // When / Then
            val res = awaitItem() as CalendarsRepository.GetEventsResult.Success
            assertTrue(res.events.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `expands recurring with single edits`() = runTest {
        // Given
        val start = from.atStartOfDay(zone).plusHours(10)
        val end = start.plusHours(1)

        val base = createEvent("eR", "uidR", tz, start, end, isSingleEdit = false)
        val occ = mockOccurrence(
            userId = userId.id,
            calendarId = calendarId,
            eventId = "eR",
            eventUid = "uidR",
            modifyTime = 1L,
            windowStart = fromSec,
            windowEnd = toSec,
            startTime = start.toInstant().epochSecond
        )

        coEvery { decryptor.getFromCache("eR", calendarId, any()) } returns base
        val ee1 = mockk<EventEntity>(relaxed = true)
        val ee2 = mockk<EventEntity>(relaxed = true)
        coEvery { calendarsRepo.selectEventEntitiesByUids(setOf("uidR")) } returns mapOf("uidR" to listOf(ee1, ee2))
        coEvery { decryptor.decrypt(ee1) } returns createEvent("eR-edit1", "uidR", tz, start.plusDays(1), end.plusDays(1), isSingleEdit = true)
        coEvery { decryptor.decrypt(ee2) } returns createEvent("eR-edit2", "uidR", tz, start.plusDays(2), end.plusDays(2), isSingleEdit = true)
        coEvery {
            calendarsRepo.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            val original: Event = arg(0)
            val edits: List<Event> = arg(1)
            val tzArg: String = arg(4)
            val emails: List<String> = arg(5)
            val isFree: Boolean = arg(6)
            assertEquals(2, edits.size, "should pass decrypted single-edits to expansion")
            listOf(buildUiEventFrom(original, tzArg, emails, isFree))
        }

        every { occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns flowOf(listOf(occ))
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(emptyList())

        execute(from, to).test {
            // When / Then
            awaitItem()
            coVerify {
                calendarsRepo.expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                    base, any(), from, to, tz, any(), any()
                )
            }
            coVerify { decryptor.decrypt(ee1) }
            coVerify { decryptor.decrypt(ee2) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filters out infinite recurring not occurring in window`() = runTest {
        // Given
        // Window fully inside occurrence window, but first occurrence after our window
        val occ = mockOccurrence(
            userId = userId.id,
            calendarId = calendarId,
            eventId = "eInf",
            eventUid = "uidInf",
            modifyTime = 1L,
            windowStart = fromSec - ONE_DAY_SEC, // covers before
            windowEnd = toSec + ONE_DAY_SEC, // covers after
            startTime = toSec + ONE_HOUR_SEC, // first occurrence after window
        )

        every { occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(listOf(occ))

        coEvery { calendarsRepo.selectEventEntitiesByUids(any()) } returns emptyMap()

        execute(from, to).test {
            // When / Then
            val res = awaitItem() as CalendarsRepository.GetEventsResult.Success
            assertTrue(res.events.isEmpty(), "infinite recurring after window should be skipped")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deduplicates identical UiEvents`() = runTest {
        // Given
        val start = from.atStartOfDay(zone).plusHours(12)
        val end = start.plusHours(1)

        val sameEvent = createEvent("dup", "uidD", tz, start, end, isSingleEdit = false)

        val nonOcc = mockOccurrence(
            userId = userId.id,
            calendarId = calendarId,
            eventId = "dup",
            eventUid = "uidD",
            modifyTime = 1L,
            windowStart = fromSec,
            windowEnd = toSec,
            startTime = start.toInstant().epochSecond,
        )
        val finOcc = mockOccurrence(
            userId = userId.id,
            calendarId = calendarId,
            eventId = "dup",
            eventUid = "uidD",
            modifyTime = 1L,
            windowStart = fromSec,
            windowEnd = toSec,
            startTime = start.toInstant().epochSecond,
        )

        coEvery { decryptor.getFromCache("dup", calendarId, any()) } returns sameEvent

        every {
            occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any())
        } returns flowOf(listOf(nonOcc))
        every {
            occurrencesDao.selectFiniteRecurring(any(), any(), any(), any())
        } returns flowOf(listOf(finOcc))
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(emptyList())

        coEvery { calendarsRepo.selectEventEntitiesByUids(any()) } returns emptyMap()

        execute(from, to).test {
            // When / Then
            val res = awaitItem() as CalendarsRepository.GetEventsResult.Success
            assertEquals(1, res.events.size, "duplicate UiEvents should be deduped")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `includes event that spills past end boundary`() = runTest {
        // Given
        val start = to.atStartOfDay(zone).minusMinutes(30) // 23:30 on 'to' day
        val end = to.plusDays(1).atStartOfDay(zone).plusMinutes(30) // 00:30 next day

        coEvery {
            decryptor.getFromCache("cross", calendarId, any())
        } returns createEvent("cross", "uidCross", tz, start, end, isSingleEdit = false)

        every {
            occurrencesDao.selectNonRecurringBetweenInclusive(any(), any(), any(), any())
        } returns flowOf(listOf(
            mockOccurrence(
                userId = userId.id,
                calendarId = calendarId,
                eventId = "cross",
                eventUid = "uidCross",
                modifyTime = 1L,
                windowStart = fromSec,
                windowEnd = toSec,
                startTime = start.toInstant().epochSecond,
            )
        ))
        every { occurrencesDao.selectFiniteRecurring(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { occurrencesDao.selectInfiniteRecurring(any(), any(), any()) } returns flowOf(emptyList())

        coEvery { calendarsRepo.selectEventEntitiesByUids(any()) } returns emptyMap()

        execute(from, to).test {
            // When / Then
            val res = awaitItem() as CalendarsRepository.GetEventsResult.Success
            assertEquals(1, res.events.size, "event crossing midnight should be included")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun execute(from: LocalDate, to: LocalDate) = GetUiEventsUseCase(
        database = database,
        eventDecryptor = decryptor,
        calendarsRepository = calendarsRepo,
        getUserInfoUseCase = getUserInfo,
        loadingStateUseCase = loadingState,
    ).execute(userId, from, to, tz, false)

    private fun mockCalendar(id: String): Calendar =
        mockk(relaxed = true) { every { this@mockk.id } returns id }

    private fun mockLoadingState(inProgress: Boolean): LoadingStateUseCase.LoadingState =
        mockk { every { this@mockk.inProgress() } returns inProgress }

    private fun mockOccurrence(
        userId: String,
        calendarId: String,
        eventId: String,
        eventUid: String,
        modifyTime: Long,
        windowStart: Long,
        windowEnd: Long,
        startTime: Long?,
    ): EventOccurrenceEntity = EventOccurrenceEntity(
        userId = userId,
        calendarId = calendarId,
        eventId = eventId,
        eventUid = eventUid,
        modifyTime = modifyTime,
        windowStartTime = windowStart,
        windowEndTime = windowEnd,
        startTime = startTime,
        fullDay = 0,
        firstOccurrenceStartTime = windowStart,
    )

    private fun createEvent(
        id: String,
        uid: String,
        tz: String,
        start: ZonedDateTime,
        end: ZonedDateTime,
        isSingleEdit: Boolean,
        decryptionStatus: Event.DecryptionStatus = Event.DecryptionStatus.Success,
    ): Event {
        val vEv = biweekly.component.VEvent().apply {
            setUid(uid)
            setSummary("Summary-$id")
            me.proton.android.calendar.common.utils.ICalUtilsImpl.apply {
                setStart(start.toLocalDate(), start.toLocalTime(), tz)
                setEnd(end.toLocalDate(), end.toLocalTime(), tz)
            }
            if (isSingleEdit) {
                setRecurrenceId(Date.from(start.toInstant()))
            }
        }
        val ical = biweekly.ICalendar().apply { addEvent(vEv) }.apply {
            with(me.proton.android.calendar.common.utils.ICalUtilsImpl) {
                setStartTimeZone(tz)
                setEndTimeZone(tz)
                setDefaultTimeZone(tz)
            }
        }
        return Event.from(
            id = id,
            calendar = mockk<Calendar>(relaxed = true),
            iCalendar = ical,
            modifyTime = 1L,
            decryptionStatus = decryptionStatus,
            color = "0xFF0000",
        )!!
    }

    private fun buildUiEventFrom(
        e: Event,
        tz: String,
        emails: List<String>,
        isFree: Boolean,
    ): UiEvent = UiEvent(
        id = e.id,
        calendarId = e.calendar.id,
        uid = e.uid,
        summary = e.summary,
        location = e.location,
        description = e.description,
        dateStart = e.getStart(tz),
        dateEnd = e.getEnd(tz),
        isAllDay = e.isAllDay(),
        occurrenceNumber = 0,
        displayColor = e.getDisplayColor(isFree),
        decryptionStatus = e.decryptionStatus ?: Event.DecryptionStatus.Failure.Generic,
        participationStatus = e.getParticipationStatus(emails),
        status = e.status ?: biweekly.property.Status.confirmed(),
    )
}

private const val ONE_DAY_SEC = 86_400
private const val ONE_HOUR_SEC = 3600
