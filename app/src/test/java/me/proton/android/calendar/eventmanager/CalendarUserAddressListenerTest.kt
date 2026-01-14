package me.proton.android.calendar.eventmanager

import androidx.work.WorkManager
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.core.CalendarUserAddressListener
import me.proton.android.calendar.test.shared.mocks.addressId
import me.proton.android.calendar.test.shared.mocks.userEmail
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.key.data.api.response.AddressResponse
import me.proton.core.user.domain.repository.UserAddressRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CalendarUserAddressListenerTest {

    private val db: AppDatabase = mockk()
    private val userAddressRepository: UserAddressRepository = mockk(relaxed = true)
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private lateinit var listener: CalendarUserAddressListener
    private val config = EventManagerConfig.Core(UserId("user_id"))
    private val workManager: WorkManager = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    @BeforeEach
    fun setup() {
        clearAllMocks()
        listener = CalendarUserAddressListener(db, userAddressRepository, logger, workManager)
        coEvery { calendarsRepository.hasCalendar(any()) } returns true
    }

    @Test
    fun `onCreate persists the user addresses`() {
        runBlocking {
            val addressResponse = createAddressResponse(addressId.id)

            listener.onCreate(config, listOf(addressResponse))

            coVerify { userAddressRepository.updateAddresses(any()) }
        }
    }

    @Test
    fun `onUpdate persists the user addresses`() {
        runBlocking {
            val addressResponse = createAddressResponse(addressId.id)

            listener.onUpdate(config, listOf(addressResponse))

            coVerify { userAddressRepository.updateAddresses(any()) }
        }
    }

    @Test
    fun `onUpdate persists the user addresses and calls refreshCalendarsFlagsForAddress`() {
        runBlocking {
            val addressResponse = createAddressResponse(addressId.id)

            listener.onUpdate(config, listOf(addressResponse))

            coVerify { userAddressRepository.updateAddresses(any()) }
        }
    }

    @Test
    fun `onDelete removes the user addresses`() {
        runBlocking {
            val addressIds = listOf(addressId.id)

            listener.onDelete(config, addressIds)

            coVerify { userAddressRepository.deleteAddresses(any()) }
        }
    }

    @Test
    fun `onResetAll refresh all the user addresses`() {
        runBlocking {
            listener.onResetAll(config)

            coVerify { userAddressRepository.getAddresses(any(), true) }
        }
    }
}

fun createAddressResponse(id: String) = AddressResponse(
    id = id,
    email = userEmail,
    send = 1,
    receive = 1,
    status = 0,
    type = 0,
    order = 0,
    hasKeys = 1
)
