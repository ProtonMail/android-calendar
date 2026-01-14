package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.CalendarSettingsChangedUseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarSettingsEventListener
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.calendarSettingsId
import me.proton.android.calendar.test.shared.mocks.defaultEventDuration
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarSettingsEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private lateinit var listener: CalendarSettingsEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarSettingsEventListener(db, calendarsRepository, logger, workManager)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate calls calendarSettingsChangedUseCase if calendars are present`() {
        runBlocking {
            val entities = listOf(
                CalendarSettingsEntity(calendarSettingsId, calendarId, defaultEventDuration, emptyList(), emptyList()),
                CalendarSettingsEntity(calendarSettingsId, calendarId, defaultEventDuration, emptyList(), emptyList()),
            )

            listener.onCreateOrUpdate(config, entities)
        }
    }

}
