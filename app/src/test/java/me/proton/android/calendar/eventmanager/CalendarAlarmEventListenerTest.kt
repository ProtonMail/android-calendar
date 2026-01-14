package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import assertk.assertThat
import assertk.assertions.isFalse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.SafePersistEventAlarmUseCase
import me.proton.android.calendar.domain.usecase.ScheduleSyncAlarmsUseCase
import me.proton.android.calendar.domain.usecase.SyncAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarAlarmEventListener
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.eventId
import me.proton.android.calendar.test.shared.mocks.memberId
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.EventId
import me.proton.core.eventmanager.domain.entity.EventMetadata
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class CalendarAlarmEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase = mockk(relaxed = true)
    private val handleAlarmsUseCase: HandleAlarmsUseCase = mockk(relaxed = true)
    private val syncAlarmsUseCase: SyncAlarmsUseCase = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private val scheduleSyncAlarmsUseCase: ScheduleSyncAlarmsUseCase = mockk(relaxed = true)

    private lateinit var listener: CalendarAlarmEventListener
    private val config = EventManagerConfig.Calendar(userId, calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarAlarmEventListener(
            db,
            calendarsRepository,
            safePersistEventAlarmUseCase,
            workManager,
            logger,
            scheduleSyncAlarmsUseCase
        )
    }

    @Test
    fun `deserializeEvents can deserialize a response`() {
        runBlocking {
            val result = listener.deserializeEvents(config, EventsResponse(eventsResponse))

            assertThat(result.isNullOrEmpty()).isFalse()
        }
    }

    @Test
    fun `onCreateOrUpdate persists the alarms`() {
        runBlocking {
            val alarms = listOf(createAlarmEntity("alarm_id_1"), createAlarmEntity("alarm_id_2"))

            listener.onCreateOrUpdate(config, alarms)

            coVerify(exactly = 1) { safePersistEventAlarmUseCase.invoke(any()) }
        }
    }

    @Test
    fun `onDelete removes the alarms`() {
        runBlocking {
            val alarms = listOf("alarm_id_1", "alarm_id_2")

            listener.onDelete(config, alarms)

            coVerify(exactly = alarms.count()) { calendarsRepository.deleteEventAlarmById(any()) }
        }
    }

    @Test
    fun `onResetAll deletes all alarms`() {
        runBlocking {
            listener.onResetAll(config)

            coVerify { calendarsRepository.deleteAllEventAlarmsByCalendar(any()) }
        }
    }

    @Test
    fun `onResetAll schedules alarms sync with force=true`() {
        runBlocking {
            listener.onResetAll(config)

            coVerify { scheduleSyncAlarmsUseCase.execute(
                userId = userId,
                force = true
            ) }
        }
    }

    @Test
    fun `onFailure schedules alarms sync with force=true`() {
        runBlocking {
            listener.onFailure(config)

            coVerify { scheduleSyncAlarmsUseCase.execute(
                userId = userId,
                force = true
            ) }
        }
    }

    private fun createAlarmEntity(id: String, occurrence: Instant = Instant.now()) = EventAlarmEntity(
        id,
        occurrence.epochSecond,
        "",
        0,
        eventId,
        memberId,
        calendarId
    )

}

private const val eventsResponse = """
{
    "Code": 1000,
    "CalendarModelEventID": "q-aXxL2ncTtY-zGNxoc-oEfJC5Q577195AE3hx5hYdUtTzgZNjwgIeqY6fE9iiqrraPIFrzNfjJAhH3XFHb-Pg==",
    "Refresh": 0,
    "More": 0,
    "CalendarEvents": [
        {
            "ID": "q6fRrEIn0nyJBE_-YSIiVf80M2VZhOuUHW5In4heCyOdV_nGibV38tK76fPKm7lTHQLcDiZtEblk0t55wbuw4w==",
            "Action": 2,
            "Event": {
                "ID": "q6fRrEIn0nyJBE_-YSIiVf80M2VZhOuUHW5In4heCyOdV_nGibV38tK76fPKm7lTHQLcDiZtEblk0t55wbuw4w==",
                "CalendarID": "q6PiQMq2FU0RnxzC2ly5cRQ7AjB1A-3IvKOmKUT4vwmPxGqJuXUvshsnor2q59pgXUISYBjycLu7WcNj_gt7-A==",
                "SharedEventID": "9WO6jlhdJQbw46gBKjMR6yXpTC-H8LtLsCad65bcKmsg7NCeDSi7O-QlnrBce0T15VkjiVlcNnScJ61RN44BWLq9VbLYBgI0Xg-jripj1lE=",
                "StartTime": 1637933400,
                "StartTimezone": "Europe/Madrid",
                "EndTime": 1637935200,
                "EndTimezone": "Europe/Madrid",
                "FullDay": 0,
                "UID": "nEa_hXjoeJiOgoyBKWzqxPSU-Zda@proton.me",
                "RecurrenceID": null,
                "Exdates": [],
                "RRule": null,
                "CreateTime": 1637683433,
                "ModifyTime": 1637927851,
                "IsOrganizer": 1
            }
        }
    ],
    "CalendarAlarms": [
        {
            "ID": "FV6FNtAc6lgP6R7dM7YB5N3LzHuoBMyivBSqjPcDfIbuMf5MdvXlCmMMcBY57-wdG-hQk4qhFb5lDEJyxjySpQ==",
            "Action": 1,
            "Alarm": {
                "ID": "FV6FNtAc6lgP6R7dM7YB5N3LzHuoBMyivBSqjPcDfIbuMf5MdvXlCmMMcBY57-wdG-hQk4qhFb5lDEJyxjySpQ==",
                "EventID": "q6fRrEIn0nyJBE_-YSIiVf80M2VZhOuUHW5In4heCyOdV_nGibV38tK76fPKm7lTHQLcDiZtEblk0t55wbuw4w==",
                "CalendarID": "q6PiQMq2FU0RnxzC2ly5cRQ7AjB1A-3IvKOmKUT4vwmPxGqJuXUvshsnor2q59pgXUISYBjycLu7WcNj_gt7-A==",
                "MemberID": "q6PiQMq2FU0RnxzC2ly5cRQ7AjB1A-3IvKOmKUT4vwmPxGqJuXUvshsnor2q59pgXUISYBjycLu7WcNj_gt7-A==",
                "Occurrence": 1637932500,
                "Trigger": "-PT15M",
                "Action": 2
            }
        }
    ]
}
"""
