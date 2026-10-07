package me.proton.android.calendar.domain.usecase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import assertk.assertThat
import assertk.assertions.containsExactly
import biweekly.util.Duration
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.BaseTest
import me.proton.android.calendar.common.getTimeFormat
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.CalendarUserSettingsDao
import me.proton.android.calendar.data.db.EventAlarmsDao
import me.proton.android.calendar.data.db.EventsDao
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal class ShowNotificationUseCaseTest : BaseTest() {

    private val context: Context = mockk(relaxed = true)
    private val database: AppDatabase = mockk()
    private val eventsDao: EventsDao = mockk()
    private val alarmsDao: EventAlarmsDao = mockk(relaxed = true)
    private val calendarUserSettingsDao: CalendarUserSettingsDao = mockk()
    private val eventDecryptor: EventDecryptor = mockk()
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase = mockk()
    private val userSettingsRepository: UserSettingsRepository = mockk()

    private val persistedAlarms = slot<List<EventAlarmEntity>>()

    private val firstStart = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES).plusMinutes(10)

    private fun sut() = ShowNotificationUseCase(
        logger = TestsLogger,
        context = context,
        transformEventUseCase = mockk(),
        eventDecryptor = eventDecryptor,
        database = database,
        safePersistEventAlarmUseCase = safePersistEventAlarmUseCase,
        userSettingsRepository = userSettingsRepository,
    )

    @BeforeEach
    fun beforeEach() {
        clearAllMocks()
        mockkStatic(Log::class)
        every { Log.isLoggable(any(), any()) } returns true
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()
        mockkObject(MainViewModel.Companion)
        every { MainViewModel.createMainIntentToShowEventDetails(any(), any(), any(), any()) } returns mockk()
        mockkConstructor(NotificationCompat.Builder::class)
        every { anyConstructed<NotificationCompat.Builder>().build() } returns mockk()
        mockkStatic("me.proton.android.calendar.common.CoreExtensionsKt")
        coEvery { userSettingsRepository.getTimeFormat(any(), any()) } returns 0

        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)
        every { database.eventsDao() } returns eventsDao
        every { database.eventAlarmsDao() } returns alarmsDao
        every { database.calendarUserSettingsDao() } returns calendarUserSettingsDao
        coEvery { calendarUserSettingsDao.select(USER_ID) } returns
            mockk<CalendarUserSettingsEntity> { every { primaryTimezone } returns "UTC" }
        coEvery { safePersistEventAlarmUseCase.invoke(capture(persistedAlarms)) } returns UseCase.Result.Success(Unit)
    }

    @AfterEach
    fun afterEach() {
        unmockkAll()
    }

    @Test
    fun `next reminder skips an occurrence deleted from the series`() = runTest {
        val series = weeklySeries(exdate = "EXDATE;TZID=UTC:${ics(firstStart.plusWeeks(1))}")
        givenChain(series)

        sut().execute(listOf(firedAlarmForFirstOccurrence()), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(firstStart.plusWeeks(2).minusMinutes(15).toEpochSecond())
    }

    @Test
    fun `next reminder skips an occurrence moved by a single edit`() = runTest {
        val series = weeklySeries()
        val singleEdit = eventForICalString(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART;TZID=UTC:${ics(firstStart.plusWeeks(1).plusHours(1))}
            DTEND;TZID=UTC:${ics(firstStart.plusWeeks(1).plusHours(2))}
            RECURRENCE-ID;TZID=UTC:${ics(firstStart.plusWeeks(1))}
            SUMMARY:weekly
            UID:$UID
            DTSTAMP:20260101T000000Z
            BEGIN:VALARM
            TRIGGER:-PT15M
            ACTION:DISPLAY
            END:VALARM
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
            eventId = "single-edit-id"
        )
        givenChain(series, singleEdit)

        sut().execute(listOf(firedAlarmForFirstOccurrence()), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(firstStart.plusWeeks(2).minusMinutes(15).toEpochSecond())
    }

    @Test
    fun `next reminder is the following occurrence of an unchanged series`() = runTest {
        givenChain(weeklySeries())

        sut().execute(listOf(firedAlarmForFirstOccurrence()), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(firstStart.plusWeeks(1).minusMinutes(15).toEpochSecond())
    }

    @Test
    fun `no next reminder after the last occurrence`() = runTest {
        givenChain(weeklySeries(count = 1))

        sut().execute(listOf(firedAlarmForFirstOccurrence()), USER_ID)

        coVerify(exactly = 0) { safePersistEventAlarmUseCase.invoke(any()) }
    }

    @Test
    fun `next all-day reminder handled late is the following day`() = runTest {
        val allDay = givenLateAllDayAlarm()
        givenChain(dailyAllDaySeries(allDay))

        sut().execute(listOf(allDay.firedAlarm), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(allDay.firedAt.plusDays(1).toEpochSecond())
    }

    @Test
    fun `next all-day reminder skips a deleted day`() = runTest {
        val allDay = givenLateAllDayAlarm()
        val deletedDay = allDay.occurrenceDate.plusDays(1)
        givenChain(dailyAllDaySeries(allDay, exdate = "EXDATE;VALUE=DATE:${deletedDay.format(DateTimeFormatter.BASIC_ISO_DATE)}"))

        sut().execute(listOf(allDay.firedAlarm), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(allDay.firedAt.plusDays(2).toEpochSecond())
    }

    @Test
    fun `next all-day reminder with display timezone west of device is the following day`() = runTest {
        val allDay = givenLateAllDayAlarm(ZoneId.of("Pacific/Pago_Pago"))
        givenChain(dailyAllDaySeries(allDay))

        sut().execute(listOf(allDay.firedAlarm), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(allDay.firedAt.plusDays(1).toEpochSecond())
        verify { MainViewModel.createMainIntentToShowEventDetails(any(), "event-id", "calendar-id", 3) }
    }

    @Test
    fun `next all-day reminder with display timezone east of device is the following day`() = runTest {
        val allDay = givenLateAllDayAlarm(ZoneId.of("Pacific/Kiritimati"))
        givenChain(dailyAllDaySeries(allDay))

        sut().execute(listOf(allDay.firedAlarm), USER_ID)

        assertThat(persistedAlarms.captured.map { it.occurrence })
            .containsExactly(allDay.firedAt.plusDays(1).toEpochSecond())
        verify { MainViewModel.createMainIntentToShowEventDetails(any(), "event-id", "calendar-id", 3) }
    }

    private class LateAllDayAlarm(val firedAt: ZonedDateTime, val occurrenceDate: LocalDate, val trigger: Duration) {
        val firedAlarm = EventAlarmEntity(
            "server-alarm-id",
            firedAt.toEpochSecond(),
            trigger.toString(),
            2,
            "event-id",
            "member-id",
            "calendar-id"
        )
    }

    private fun givenLateAllDayAlarm(zone: ZoneId = ZoneId.systemDefault()): LateAllDayAlarm {
        coEvery { calendarUserSettingsDao.select(USER_ID) } returns
            mockk<CalendarUserSettingsEntity> { every { primaryTimezone } returns zone.id }
        val firedAt = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES).minusMinutes(5)
        val occurrenceDate = firedAt.toLocalDate().plusDays(1)
        val minutesBefore = ChronoUnit.MINUTES.between(firedAt, occurrenceDate.atStartOfDay(zone))
        val trigger = Duration.builder().prior(true).hours((minutesBefore / 60).toInt()).minutes((minutesBefore % 60).toInt()).build()
        return LateAllDayAlarm(firedAt, occurrenceDate, trigger)
    }

    private fun dailyAllDaySeries(allDay: LateAllDayAlarm, exdate: String = "") = eventForICalString(
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:${allDay.occurrenceDate.minusDays(2).format(DateTimeFormatter.BASIC_ISO_DATE)}
        DTEND;VALUE=DATE:${allDay.occurrenceDate.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)}
        RRULE:FREQ=DAILY;COUNT=10
        $exdate
        SUMMARY:daily
        UID:$UID
        DTSTAMP:20260101T000000Z
        BEGIN:VALARM
        TRIGGER:${allDay.trigger}
        ACTION:DISPLAY
        END:VALARM
        END:VEVENT
        END:VCALENDAR
        """.trimIndent().lines().filter { it.isNotBlank() }.joinToString("\n")
    )

    private fun weeklySeries(exdate: String = "", count: Int = 5) = eventForICalString(
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        DTSTART;TZID=UTC:${ics(firstStart)}
        DTEND;TZID=UTC:${ics(firstStart.plusHours(1))}
        RRULE:FREQ=WEEKLY;COUNT=$count
        $exdate
        SUMMARY:weekly
        UID:$UID
        DTSTAMP:20260101T000000Z
        BEGIN:VALARM
        TRIGGER:-PT15M
        ACTION:DISPLAY
        END:VALARM
        END:VEVENT
        END:VCALENDAR
        """.trimIndent().lines().filter { it.isNotBlank() }.joinToString("\n")
    )

    private fun givenChain(series: Event, vararg others: Event) {
        val seriesEntity = mockk<EventEntity>()
        coEvery { eventsDao.selectEvent(series.id, series.calendar.id) } returns seriesEntity
        coEvery { eventDecryptor.decrypt(seriesEntity) } returns series
        val chainEntities = listOf(seriesEntity) + others.map { other ->
            mockk<EventEntity>().also { coEvery { eventDecryptor.decrypt(it) } returns other }
        }
        coEvery { eventsDao.selectByUidInCalendar(any(), any()) } returns chainEntities
    }

    private fun firedAlarmForFirstOccurrence() = EventAlarmEntity(
        "server-alarm-id",
        firstStart.minusMinutes(15).toEpochSecond(),
        "-PT15M",
        2,
        "event-id",
        "member-id",
        "calendar-id"
    )

    private fun ics(dateTime: ZonedDateTime) = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))

    private companion object {
        const val USER_ID = "user-id"
        const val UID = "weekly-uid@proton.me"
    }
}
