package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.data.api.ServerEvent
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.ResetCalendarSearchUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UpdateEventOccurrencesUseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarEventListener
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.calendarKeyPacket
import me.proton.android.calendar.test.shared.mocks.eventUid
import me.proton.android.calendar.test.shared.mocks.sharedEventId
import me.proton.android.calendar.test.shared.mocks.sharedKeyPacket
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.EventId
import me.proton.core.eventmanager.domain.entity.EventMetadata
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class CalendarEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk()
    private val widgetRefresher: WidgetRefresher = mockk()
    private val updateAlarmsUseCase: UpdateAlarmsUseCase = mockk(relaxed = true)
    private val resetCalendarSearchUseCase: ResetCalendarSearchUseCase = mockk(relaxed = true)
    private val updateEventOccurrencesUseCaseMock: UpdateEventOccurrencesUseCase = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    private lateinit var listener: CalendarEventListener
    private val config = EventManagerConfig.Calendar(userId, calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarEventListener(
            db,
            calendarsRepository,
            resetCalendarSearchUseCase,
            logger,
            workManager,
            widgetRefresher,
            updateAlarmsUseCase,
            updateEventOccurrencesUseCaseMock
        )
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `deserializeEvents can deserialize a response`() {
        runBlocking {
            val result = listener.deserializeEvents(config, EventsResponse(eventsResponse))

            assertThat(result.isNullOrEmpty()).isFalse()
        }
    }

    @Test
    fun `onResetAll deletes all events and fetches recent events`() {
        runBlocking {
            coEvery { calendarsRepository.deleteEventsMetadataByCalendarId(any()) } returns Unit
            coEvery { calendarsRepository.deleteAllEvents(any()) } returns Unit

            listener.onResetAll(config)

            coVerify(exactly = 1) { calendarsRepository.deleteAllEvents(any()) }
        }
    }
}

fun createEventEntity(id: String, modifyTime: Long? = null, paramCalendarId: String? = null, paramSharedKeyPacket: String? = null) = EventEntity(
    id,
    paramCalendarId ?: calendarId,
    sharedEventId,
    calendarKeyPacket,
    0L,
    modifyTime ?: 0L,
    0,
    addressKeyPacket = null,
    addressId = null,
    paramSharedKeyPacket ?: sharedKeyPacket,
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    emptyList(),
    null
)

fun createEventMetadata(
    id: String,
    startTime: Long? = null,
    endTime: Long? = null,
    modifyTime: Long? = null,
    rRule: String? = null
) = EventEntityMetadata(
    id = id,
    calendarId = calendarId,
    sharedEventId = sharedEventId,
    addressId = null,
    startTime = startTime ?: Instant.now().plusSeconds(3600).epochSecond,
    startTimeZone = "GMT",
    endTime = endTime ?: Instant.now().plusSeconds(7200).epochSecond,
    endTimeZone = "GMT",
    fullDay = 0,
    uid = eventUid,
    recurrenceID = null,
    exDates = emptyList(),
    rRule = rRule,
    createTime = 0L,
    modifyTime = modifyTime ?: 0L,
    isOrganizer = 0,
    sharedKeyPacket = null,
    calendarKeyPacket = null,
    addressKeyPacket = null,
    isPersonalSingleEdit = false
)

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
                "IsOrganizer": 1,
                "AddressID": null,
                "SharedKeyPacket": null,
                "CalendarKeyPacket": null,
                "AddressKeyPacket": null,
                "IsPersonalSingleEdit": false
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

private const val eventsResponseWithCreateUpdateDelete = """
{
    "Code": 1000,
    "CalendarModelEventID": "q-aXxL2ncTtY-zGNxoc-oEfJC5Q577195AE3hx5hYdUtTzgZNjwgIeqY6fE9iiqrraPIFrzNfjJAhH3XFHb-Pg==",
    "Refresh": 0,
    "More": 0,
    "CalendarEvents": [
        {
            "ID": "id_1",
            "Action": 1,
            "Event": {
                "ID": "id_1",
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
        },
        {
            "ID": "id_2",
            "Action": 2,
            "Event": {
                "ID": "id_2",
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
        },
        {
            "ID": "id_3",
            "Action": 0
        }
    ]
}
"""

