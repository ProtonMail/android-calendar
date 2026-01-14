package me.proton.android.calendar.test.presentation.calendarFormViewModel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUserSettingsUseCase
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.android.calendar.test.shared.mocks.UserMocks
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import org.junit.Before
import org.junit.Rule
import org.koin.core.KoinComponent

open class CalendarFormViewModelTestCommon: KoinComponent {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    lateinit var appDatabaseMock: AppDatabase
    lateinit var protonCalendarApplication: Application

    val calendarsRepositoryMock: CalendarsRepository = mockk()

    val userManagerMock: UserManager = mockk()
    val userAddressManagerMock: UserAddressManager = mockk()

    val updateCalendarUseCaseMock: UpdateCalendarUseCase = mockk()
    val updateCalendarSettingsUseCaseMock: UpdateCalendarSettingsUseCase = mockk()
    val updateCalendarUserSettingsUseCaseMock: UpdateCalendarUserSettingsUseCase = mockk()
    val createCalendarsUseCaseMock: CreateCalendarUseCase = mockk()
    val accountManagerMock: AccountManager = mockk()


    private val testsLogger = TestsLogger

    val resourceProviderMock: ResourceProvider = mockk()

    @Before
    fun beforeEach() {
        clearAllMocks()
        appDatabaseMock = mockk()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        protonCalendarApplication = application

        coEvery { userManagerMock.getUser(userId) } returns UserMocks.provideUser()
        coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())
        coEvery { accountManagerMock.getPrimaryUserId() } returns flowOf(userId)
    }

    /**
     * Utils private methods
     */
    fun getCalendarFormViewModel(): CalendarFormViewModel {
        return CalendarFormViewModel(
            application = protonCalendarApplication,
            calendarsRepository = calendarsRepositoryMock,
            userManager = userManagerMock,
            userAddressManager = userAddressManagerMock,
            logger = testsLogger,
            updateCalendarUseCase = updateCalendarUseCaseMock,
            resourceProvider = resourceProviderMock,
            accountManager = accountManagerMock,
            createCalendarUseCase = createCalendarsUseCaseMock,
            updateCalendarSettingsUseCase = updateCalendarSettingsUseCaseMock,
            updateCalendarUserSettingsUseCase = updateCalendarUserSettingsUseCaseMock
        )
    }

}
