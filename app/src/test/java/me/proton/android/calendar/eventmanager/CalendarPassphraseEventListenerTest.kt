package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.CacheCalendarPassphraseUseCase
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarPassphraseEventListener
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.EventId
import me.proton.core.eventmanager.domain.entity.EventMetadata
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarPassphraseEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)
    private lateinit var listener: CalendarPassphraseEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = spyk(CalendarPassphraseEventListener(db, calendarsRepository, workManager, cacheCalendarPassphraseUseCase, logger))
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the passphrases if calendar exists`() {
        runBlocking {
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarPassphrase(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the passphrases if calendar doesn't exist`() {
        runBlocking {
            coEvery { calendarsRepository.hasCalendar(any()) } returns false
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistCalendarPassphrase(any()) }
            coVerify { logger.i(any()) }
        }
    }

    @Test
    fun `onUpdate persists the passphrases`() {
        runBlocking {
            val entities = listOf(
                PassphraseEntity("passphrase_id", 0, emptyList(), calendarId),
                PassphraseEntity("passphrase_id_2", 0, emptyList(), calendarId)
            )

            listener.onUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarPassphrase(any()) }
        }
    }

    @Test
    fun `onDelete removes the passphrases`() {
        runBlocking {
            val entities = listOf("passphrase_id", "passphrase_id_2")

            listener.onDelete(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.deleteCalendarPassphraseById(any()) }
        }
    }

    @Test
    fun `onComplete caches the created or update passphrases if present`() {
        runBlocking {

            listener.notifyComplete(
                config,
                EventMetadata(
                    userId = userId,
                    eventId = EventId("eventId"),
                    config = config,
                    createdAt = 0L
                ),
                response = EventsResponse(eventsResponseWithPassphrases),
            )

            coVerify(exactly = 1) { cacheCalendarPassphraseUseCase.execute(any(), any()) }
        }
    }
}

private const val eventsResponseWithPassphrases = """
{
    "Code": 1000,
    "CalendarModelEventID": "q-aXxL2ncTtY-zGNxoc-oEfJC5Q577195AE3hx5hYdUtTzgZNjwgIeqY6fE9iiqrraPIFrzNfjJAhH3XFHb-Pg==",
    "Refresh": 0,
    "More": 0,
    "CalendarPassphrases": [
        {
            "ID": "passphrase_id",
            "Action": 1,
             "Key": {
                "ID": "passphrase_id",
                "MemberPassphrases": [],
                "Flags": 0,
                "CalendarID": "$calendarId"
             }
        },
        {
            "ID": "passphrase_id_2",
            "Action": 1,
             "Key": {
                "ID": "passphrase_id_2",
                "MemberPassphrases": [],
                "Flags": 0,
                "CalendarID": "$calendarId"
             }
        }
    ]
}
"""
