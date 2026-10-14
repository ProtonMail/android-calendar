package me.proton.android.calendar.presentation.calendar.viewModel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import biweekly.util.Frequency
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import me.proton.android.calendar.common.getUserOrNull
import me.proton.android.calendar.common.getUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.test.shared.mocks.CalendarMocks
import me.proton.android.calendar.test.shared.mocks.UserMocks
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.user.domain.UserManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class EventViewModelPrefillTest {

    private val userManager: UserManager = mockk(relaxed = true)
    private val calendarsRepository: CalendarsRepository = mockk(relaxed = true)
    private val userSettingsRepository: UserSettingsRepository = mockk(relaxed = true)

    private val viewModel = EventViewModel(
        application = mockk(relaxed = true),
        userManager = userManager,
        userAddressManager = mockk(relaxed = true),
        calendarsRepository = calendarsRepository,
        userSettingsRepository = userSettingsRepository,
        transformEventUseCase = mockk(relaxed = true),
        eventDecryptor = mockk(relaxed = true),
        updateParticipationStatusUseCase = mockk(relaxed = true),
        sendEmailUseCase = mockk(relaxed = true),
        logger = mockk(relaxed = true),
        getCanonicalEmailsUseCase = mockk(relaxed = true),
        obtainSendPreferencesUseCase = mockk(relaxed = true),
        handleSaveUseCase = mockk(relaxed = true),
        handleDeleteUseCase = mockk(relaxed = true),
        updateCalendarUseCase = mockk(relaxed = true),
        resourceProvider = mockk(relaxed = true),
        widgetRefresher = mockk(relaxed = true),
        handleAlarmsUseCase = mockk(relaxed = true),
        database = mockk(relaxed = true),
        upgradeEventUseCase = mockk(relaxed = true),
        workManager = mockk(relaxed = true),
        updatePersonalPartUseCase = mockk(relaxed = true),
        generateProtonMeetUrlUseCase = mockk(relaxed = true),
        protonMeetCrypto = mockk(relaxed = true),
        getProtonMeetDetailsUseCase = mockk(relaxed = true),
        isProtonMeetAddEnabled = mockk(relaxed = true),
        scopeProvider = mockk(relaxed = true),
    )

    @BeforeEach
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        mockkStatic("me.proton.android.calendar.common.CoreExtensionsKt")
        coEvery { userManager.getUserOrNull(any(), any()) } returns UserMocks.provideUser()
        coEvery { userSettingsRepository.getUserSettingsEntity(any(), any()) } returns
            UserMocks.provideUserSettingsEntity()
        coEvery { calendarsRepository.getDefaultCalendarIdWithFallback(any(), any()) } returns calendarId
        coEvery { calendarsRepository.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepository.selectCalendarSettings(calendarId) } returns
            CalendarMocks.provideCalendarSettingsEntity()
        coEvery { calendarsRepository.selectCalendarUserSettings(any()) } returns
            CalendarMocks.provideCalendarUserSettingsEntity()
    }

    @AfterEach
    fun tearDown() {
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    @Test
    fun `prefilled rrule is applied to the new event`() = runTest {
        val result = initialiseWithRRule("FREQ=WEEKLY;BYDAY=TH")

        assertThat(result).isEqualTo(EventViewModel.InitResult.Success)
        val recurrence = viewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value
        assertThat(recurrence?.frequency).isEqualTo(Frequency.WEEKLY)
    }

    @Test
    fun `prefilled rrule with a dollar sign is rejected instead of crashing`() = runTest {
        val result = initialiseWithRRule("$")

        assertThat(result).isInstanceOf(EventViewModel.InitResult.Error::class)
    }

    @Test
    fun `prefilled rrule with a group reference is rejected instead of crashing`() = runTest {
        val result = initialiseWithRRule("FREQ=DAILY$1")

        assertThat(result).isInstanceOf(EventViewModel.InitResult.Error::class)
    }

    private suspend fun initialiseWithRRule(rRule: String) = viewModel.initialise(
        userId = userId,
        editMode = true,
        meetIntegrations = emptySet(),
        eventId = null,
        calendarId = null,
        occurrenceNumber = null,
        initStartDate = "2026-10-08",
        initStartTime = "10:00",
        timeZoneId = "Europe/Paris",
        rRule = rRule,
    )
}
