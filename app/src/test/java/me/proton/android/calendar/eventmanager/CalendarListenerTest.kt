package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isNull
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.DEFAULT_CALENDAR_COLOR
import me.proton.android.calendar.common.utils.toHexColor
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.usecase.BootstrapCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.eventmanager.listeners.core.CalendarListener
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CalendarListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk()
    private val logger: Logger = mockk(relaxed = true)
    private val json: Json = mockk()
    private val workManager: WorkManager = mockk(relaxed = true)

    private lateinit var listener: CalendarListener
    private val config = EventManagerConfig.Core(UserId("user_id"))

    @BeforeEach
    fun setup() {
        clearAllMocks()

        listener = CalendarListener(db, calendarsRepository, workManager, logger)

        coEvery { calendarsRepository.selectCalendarUserSettings(any()) } returns null
        coEvery { calendarsRepository.persistCalendar(any(), any()) } returns Unit
        coEvery { calendarsRepository.deleteCalendarById(any()) } returns Unit
    }

    @Test
    fun `Can deserialize valid response`() {
        runBlocking {
            val response = EventsResponse(validResponse)

            val events = listener.deserializeEvents(config, response)

            assertThat(events.isNullOrEmpty()).isFalse()
        }
    }

    @Test
    fun `Cannot deserialize invalid response`() {
        runBlocking {
            val response = EventsResponse(brokenResponse)

            assertThrows<MissingFieldException> {
                listener.deserializeEvents(config, response)
            }
        }
    }

    @Test
    fun `Cannot deserialize empty response`() {
        runBlocking {
            val response = EventsResponse(emptyResponse)

            val events = listener.deserializeEvents(config, response)

            assertThat(events).isNull()
        }
    }

    @Test
    fun `onCreate persist CalendarEntity`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity(
                    "calendar_id",
                    owner = null
                ),
            )

            listener.onCreate(config, entities)

            coVerify(exactly = 1) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `onUpdate just persists the calendar`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity(
                    "calendar_id",
                    owner = null
                ),
            )

            val calendarSettingsEntity: CalendarSettingsEntity = mockk()

            coEvery { calendarSettingsEntity.defaultEventDuration } returns 30
            coEvery { calendarSettingsEntity.defaultPartDayNotifications } returns emptyList()
            coEvery { calendarSettingsEntity.defaultFullDayNotifications } returns emptyList()

            coEvery { calendarsRepository.selectCalendar(any()) } returns Calendar.from(
                CalendarEntity(
                    "calendar_id",
                    owner = null
                ),
                MemberEntity("member_id", MemberEntity.Permission.ADMIN.value, "address_id", "member email", "calendar_id", "fff", 1, 1,  "Name", "Description", 0),
                calendarSettingsEntity,
                json
            )

            listener.onUpdate(config, entities)

            coVerify(exactly = 1) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `onUpdate calendar doesn't exist yet in db do persist`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity(
                    "calendar_id",
                    owner = null
                ),
            )

            coEvery { calendarsRepository.selectCalendar(any()) } returns null

            listener.onUpdate(config, entities)

            coVerify(exactly = 1) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `onUpdate calendar doesn't exist yet in db bootstrap failed persist calendar`() {
        runBlocking {
            val entities = listOf(
                CalendarEntity(
                    "calendar_id",
                    owner = null
                ),
            )

            coEvery { calendarsRepository.selectCalendar(any()) } returns null

            listener.onUpdate(config, entities)

            coVerify(exactly = 1) { calendarsRepository.persistCalendar(any(), any()) }
        }
    }

    @Test
    fun `onDelete normal calendar`() {
        runBlocking {
            val ids = listOf("calendar_id")

            coEvery { calendarsRepository.selectCalendar(any()) } returns Calendar(
                "id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 127, 30, emptyList(), emptyList()
            )

            listener.onDelete(config, ids)

            coVerify(exactly = 1) { calendarsRepository.deleteCalendarById(any()) }
        }
    }

    @Test
    fun `onDelete holiday calendar`() {
        runBlocking {
            val ids = listOf("calendar_id")

            coEvery { calendarsRepository.selectCalendar(any()) } returns Calendar(
                "id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 2, 127, 30, emptyList(), emptyList()
            )

            listener.onDelete(config, ids)

            coVerify(exactly = 0) { calendarsRepository.deleteCalendarById(any()) }
        }
    }

    @Test
    fun `onDelete shared calendar`() {
        runBlocking {
            val ids = listOf("calendar_id")

            coEvery { calendarsRepository.selectCalendar(any()) } returns Calendar(
                "id", "name", "email", "ownerEmail", "description", DEFAULT_CALENDAR_COLOR,0, "addressId", "memberId", 1, true, 0, 32, 30, emptyList(), emptyList()
            )

            listener.onDelete(config, ids)

            coVerify(exactly = 0) { calendarsRepository.deleteCalendarById(any()) }
        }
    }

}

private const val validResponse = """
{
    "Code": 1000,
    "EventID": "scQb2qVo6ZNziFBmsRggXrCzjrvAZEz8XL6nCSiSj9DtfGJI8f6LIUKcvw5kIOjhsKBowzOMTDNQfiIjTE24XQ==",
    "Refresh": 0,
    "More": 0,
    "Calendars": [
        {
            "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
            "Action": 1,
            "Calendar": {
                "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Name": "ASDADADSASD",
                "Description": "",
                "Type": 0,
                "Flags": 1,
                "Color": "#9DB99F",
                "Display": 1,
                "CalendarID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Owner": {
                    "Email": "owner@pm.me"
                }
            }
        }
    ],
    "CalendarMembers": [
        {
            "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
            "Action": 1,
            "Member": {
                "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Permissions": 127,
                "Email": "pro@burbank.proton.black",
                "AddressID": "p5DPgsgSOQhwxfZmy4A-vVIxHd40lH8xRVg_4ulz69pz7Ox7ibSa2QXEbMg151clLRB-CQQTCRNteaIBHL_iUg==",
                "CalendarID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Flags": 1,
                "Color": "#9DB99F",
                "Display": 1,
                "Name": "ASDADADSASD",
                "Description": "",
                "Priority": 0
            }
        }
    ],
    "Notices": []
}
"""

private const val brokenResponse = """
        {
    "Code": 1000,
    "EventID": "scQb2qVo6ZNziFBmsRggXrCzjrvAZEz8XL6nCSiSj9DtfGJI8f6LIUKcvw5kIOjhsKBowzOMTDNQfiIjTE24XQ==",
    "Refresh": 0,
    "More": 0,
    "Calendars": [
        {
            "ID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
            "Action": 1,
            "Calendar": {
                "ID_malformed_property": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw==",
                "Type": 0,
                "Flags": 1,
                "CalendarID": "xZLizr66ZlJwAfcVTwiH5ewAQ3a5h6IptTBHdtP-mpuv4Sqqy5B3S8KfD-7_W8i0jxBd976glUl8q5eMAo4JCw=="
            }
        }
    ],
    "Notices": []
}
"""

private const val emptyResponse = """
{
    "Code": 1000,
    "EventID": "scQb2qVo6ZNziFBmsRggXrCzjrvAZEz8XL6nCSiSj9DtfGJI8f6LIUKcvw5kIOjhsKBowzOMTDNQfiIjTE24XQ==",
    "Refresh": 0,
    "More": 0,
    "Notices": []
}
"""
