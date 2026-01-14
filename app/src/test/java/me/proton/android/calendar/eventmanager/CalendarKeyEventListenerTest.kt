package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarKeyEventListener
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.EventId
import me.proton.core.eventmanager.domain.entity.EventMetadata
import me.proton.core.eventmanager.domain.entity.EventsResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarKeyEventListenerTest {

    private val db: AppDatabase = mockk()
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private lateinit var listener: CalendarKeyEventListener
    private val config = EventManagerConfig.Calendar(UserId("user_id"), calendarId)
    private val workManager: WorkManager = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarKeyEventListener(db, calendarsRepository, workManager, logger)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreateOrUpdate persists the keys if calendar is present`() {
        runBlocking {
            val entities = listOf(
                CalendarKeyEntity("key_id", 0, "", "", calendarId),
                CalendarKeyEntity("key_id_2", 0, "", "", calendarId),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = entities.count()) { calendarsRepository.persistCalendarKey(any()) }
        }
    }

    @Test
    fun `onCreateOrUpdate doesn't persist the keys if calendar is not present`() {
        coEvery { calendarsRepository.hasCalendar(any()) } returns false
        runBlocking {
            val entities = listOf(
                CalendarKeyEntity("key_id", 0, "", "", calendarId),
                CalendarKeyEntity("key_id_2", 0, "", "", calendarId),
            )

            listener.onCreateOrUpdate(config, entities)

            coVerify(exactly = 0) { calendarsRepository.persistCalendarKey(any()) }
            coVerify { logger.i(any()) }
        }
    }

    @Test
    fun `onDelete removes the keys`() {
        runBlocking {
            val ids = listOf("key_id", "key_id_2")

            listener.onDelete(config, ids)

            coVerify(exactly = ids.count()) { calendarsRepository.deleteCalendarKeyById(any()) }
        }
    }

}

private const val eventsResponseCreateCalendarKey = """
{
    "Code": 1000,
    "CalendarModelEventID": "q-aXxL2ncTtY-zGNxoc-oEfJC5Q577195AE3hx5hYdUtTzgZNjwgIeqY6fE9iiqrraPIFrzNfjJAhH3XFHb-Pg==",
    "Refresh": 0,
    "More": 0,
    "CalendarKeys": [
        {
            "ID": "key_id",
            "Action": 1,
             "Key": {
                "ID": "O5z62XQXs0fDt0Aj-Qho8R7VSbAwb1pz9RZncLxPxAwJg9KJNN9tgfbZGZ-ZJnwecjSzqzamx9V-UWVQ5_Sjow==",
                "PrivateKey": "-----BEGIN PGP PRIVATE KEY BLOCK-----\nVersion: ProtonMail\n\nxYYEYCqa/xYJKwYBBAHaRw8BAQdAy3aDY44P87WmvswGozb+raIXYeRGHF8w\nkm6WHj1uw2f+CQMIHxMesAW1+gRglqu3FyEXdXNZzZFjFSIJkJJISd9DxKTK\n2R6ohcqeNLlULLnoNakl9gEXrYJjI+o6XdDXW0jZ5HK3mBlAA8EojDmuaOkb\nPM0MQ2FsZW5kYXIga2V5wo8EEBYKACAFAmAqmv8GCwkHCAMCBBUICgIEFgIB\nAAIZAQIbAwIeAQAhCRCndAgBhQwQThYhBMJapVvf0uy7lBEZaqd0CAGFDBBO\n1scA/2PGiffPTQyq863HJrMdhuqlCfI2pmfT3wGmxM2QbCwJAQCkB9rvCNro\n1Px5IV77mx9rYmnZHeW0hh3u+eea+tlUCseLBGAqmv8SCisGAQQBl1UBBQEB\nB0BwRlw4dJ10F3dG/I8lT9sSJl7ONVOf4oafKyJgbo66awMBCAf+CQMIQf8j\ndZg/r/Rg1sETN7yXuwtlyIkedId3yGxAKK2BqKChW9T3kNarQ1xRu75i1aLd\nNsyJEv7ReKlzDjwDpC6/hV9e9YAaaLhGgpZpjfjIRsJ4BBgWCAAJBQJgKpr/\nAhsMACEJEKd0CAGFDBBOFiEEwlqlW9/S7LuUERlqp3QIAYUMEE6l4QEAhncd\nO8wUq3w5AMk/Prf9kwNLk+T4PM/wEajaPwoV7EUA/RjQPQ+YNk8KSxlpXW5y\nVrgH6KBvPpyYqrEirNj2pIYM\n=xMJu\n-----END PGP PRIVATE KEY BLOCK-----\n",
                "PassphraseID": "O5z62XQXs0fDt0Aj-Qho8R7VSbAwb1pz9RZncLxPxAwJg9KJNN9tgfbZGZ-ZJnwecjSzqzamx9V-UWVQ5_Sjow==",
                "Flags": 0,
                "CalendarID": "kvQPsAxpLGDWNiDxT2BIxf__u-88lAB4b6FPmPngETRM6oC1g9Bi0zBNVxn3yncjQutdJPnFbY_YomTRulFKQQ=="
             }
        }
    ]
}
"""
