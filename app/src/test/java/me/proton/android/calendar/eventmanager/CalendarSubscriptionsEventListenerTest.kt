package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarSettingsEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarSubscriptionsEventListener
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.calendarSettingsId
import me.proton.android.calendar.test.shared.mocks.defaultEventDuration
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarSubscriptionsEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private lateinit var listener: CalendarSubscriptionsEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarSubscriptionsEventListener(db, calendarsRepository, workManager, logger)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the calendar settings if calendar is present`() {
        runBlocking {
            val entities = listOf(
                CalendarSubscriptionEntity(calendarId, 0, 0, 0, ""),
                CalendarSubscriptionEntity(calendarId, 0, 0, 0, ""),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarSubscription(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the calendar settings if calendar is not present`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val entities = listOf(
                CalendarSubscriptionEntity(calendarId, 0, 0, 0, ""),
                CalendarSubscriptionEntity(calendarId, 0, 0, 0, ""),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistCalendarSubscription(any()) }
            coVerify { logger.i(any()) }
        }
    }

    @Test
    fun `onUpdate persists the calendar settings`() {
        runBlocking {
            val entities = listOf(
                CalendarSubscriptionEntity(calendarId, 0, 0, 0, ""),
                CalendarSubscriptionEntity(calendarId, 0, 0, 0, ""),
            )

            listener.onUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarSubscription(any()) }
        }
    }

}
