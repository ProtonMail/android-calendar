package me.proton.android.calendar.test.presentation.eventViewModel

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.serialization.json.Json
import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.common.getUserSettingsEntity
import me.proton.android.calendar.common.getUserSettingsEntityFlow
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.MeetIntegrationType
import me.proton.android.calendar.domain.usecase.GetCanonicalEmailsUseCase
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.HandleDeleteUseCase
import me.proton.android.calendar.domain.usecase.HandleSaveUseCase
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.SendEmailUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateParticipationStatusUseCase
import me.proton.android.calendar.domain.usecase.UpdatePersonalPartUseCase
import me.proton.android.calendar.domain.usecase.UpgradeEventUseCase
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.test.shared.mocks.CalendarMocks
import me.proton.android.calendar.test.shared.mocks.EventMocks
import me.proton.android.calendar.test.shared.mocks.UserMocks
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.eventId
import me.proton.android.calendar.test.shared.mocks.singleEditEventId
import me.proton.android.calendar.test.shared.mocks.userId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.junit.Before
import org.junit.Rule
import org.koin.core.KoinComponent

open class EventViewModelTestCommon: KoinComponent {

    @get:Rule
    var rule = InstantTaskExecutorRule()

    lateinit var appDatabaseMock: AppDatabase
    lateinit var protonCalendarApplication: Application

    val calendarsRepositoryMock: CalendarsRepository = mockk()
    val userSettingsRepositoryMock: UserSettingsRepository = mockk()
    val eventDecryptorMock: EventDecryptor = mockk()

    val userManagerMock: UserManager = mockk()
    val userAddressManagerMock: UserAddressManager = mockk()

    val transformEventUseCaseMock: TransformEventUseCase = mockk()
    val updateParticipationStatusUseCaseMock: UpdateParticipationStatusUseCase = mockk()
    val sendEmailUseCaseMock: SendEmailUseCase = mockk()
    val getCanonicalEmailsUseCaseMock: GetCanonicalEmailsUseCase = mockk()
    val obtainSendPreferencesUseCaseMock: ObtainSendPreferencesUseCase = mockk()
    val handleSaveUseCaseMock: HandleSaveUseCase = mockk()
    val handleDeleteUseCaseMock: HandleDeleteUseCase = mockk()
    val updateCalendarUseCaseMock: UpdateCalendarUseCase = mockk()
    val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    val calendarWidgetRefresherMock: CalendarWidgetRefresher = mockk()
    val upgradeEventUseCaseMock: UpgradeEventUseCase = mockk()
    val workManagerMock: WorkManager = mockk()
    val updatePersonalPartUseCase: UpdatePersonalPartUseCase = mockk()

    private val testsLogger = TestsLogger
    private val json = Json { this.ignoreUnknownKeys = true }

    val resourceProviderMock: ResourceProvider = mockk()

    @Before
    fun beforeEach() {
        clearAllMocks()
        appDatabaseMock = buildMultiThreaded()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        protonCalendarApplication = application

        coEvery { calendarsRepositoryMock.getDefaultCalendarIdWithFallback(userId.id, true) } returns calendarId
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        // TODO Test fallback to first active user calendar when default calendar is null
        coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar())
        coEvery { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) } returns CalendarMocks.provideCalendarUserSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.provideCalendarSettingsEntity()
        coEvery { userSettingsRepositoryMock.getUserSettingsEntity(userId, appDatabaseMock) } returns UserMocks.provideUserSettingsEntity()
        coEvery { userSettingsRepositoryMock.getUserSettingsEntityFlow(userId, appDatabaseMock) } returns MutableLiveData(UserMocks.provideUserSettingsEntity()).asFlow()
        coEvery { userManagerMock.getUser(userId) } returns UserMocks.provideUser()
        coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

        coEvery { calendarsRepositoryMock.selectEventEntity(eventId) } returns EventMocks.provideEventResponse().toEventEntity()

        coEvery { calendarWidgetRefresherMock.refreshEventList() } just Runs
    }

    /**
     * Utils private methods
     */

    private fun buildMultiThreaded(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
    }

    fun getEventViewModel(): EventViewModel {
        return EventViewModel(
            application = protonCalendarApplication,
            userManager = userManagerMock,
            userAddressManager = userAddressManagerMock,
            calendarsRepository = calendarsRepositoryMock,
            userSettingsRepository = userSettingsRepositoryMock,
            transformEventUseCase = transformEventUseCaseMock,
            updateParticipationStatusUseCase = updateParticipationStatusUseCaseMock,
            sendEmailUseCase = sendEmailUseCaseMock,
            logger = testsLogger,
            json = json,
            getCanonicalEmailsUseCase = getCanonicalEmailsUseCaseMock,
            obtainSendPreferencesUseCase = obtainSendPreferencesUseCaseMock,
            handleSaveUseCase = handleSaveUseCaseMock,
            handleDeleteUseCase = handleDeleteUseCaseMock,
            updateCalendarUseCase = updateCalendarUseCaseMock,
            resourceProvider = resourceProviderMock,
            widgetRefresher = calendarWidgetRefresherMock,
            handleAlarmsUseCase = handleAlarmsUseCaseMock,
            eventDecryptor = eventDecryptorMock,
            database = appDatabaseMock,
            upgradeEventUseCase = upgradeEventUseCaseMock,
            workManager = workManagerMock,
            updatePersonalPartUseCase = updatePersonalPartUseCase
        )
    }

    suspend fun getInitialisedEventViewModel(
        editMode: Boolean,
        eventId: String?,
        occurrenceNumber: Int?,
        initStartDate: String?,
        initStartTime: String?
    ): EventViewModel {
        val eventViewModel = getEventViewModel()
        eventViewModel.initialise(
            userId,
            editMode,
            setOf(MeetIntegrationType.Zoom, MeetIntegrationType.ProtonMeet),
            eventId,
            occurrenceNumber,
            initStartDate,
            initStartTime
        )

        if (editMode) {
            if (eventId == null) {
                coVerify(exactly = 1) { calendarsRepositoryMock.getDefaultCalendarIdWithFallback(any(), any()) }
                coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendar(any()) }
            }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(any()) }
        }

        coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(any()) }
        coVerify(exactly = 1) { userSettingsRepositoryMock.getUserSettingsEntity(userId, appDatabaseMock) }
        coVerify(exactly = 1) { userManagerMock.getUser(any()) }

        if (eventId != null && eventId == singleEditEventId) {
            // If we edit existing single edit event
            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            if (editMode) {
                coVerify(exactly = 1) { calendarsRepositoryMock.selectRootEventEntity(any()) }
                coVerify(exactly = 2) { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptorMock.decrypt(any())
                } else {
                    transformEventUseCaseMock.execute(any())
                } }
            } else {
                coVerify(exactly = 1) { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptorMock.decrypt(any())
                } else {
                    transformEventUseCaseMock.execute(any())
                } }
            }
        } else if (eventId != null) {
            // If we edit existing event
            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } }
        }

        return eventViewModel
    }

    fun provideDisplayDialog(selectedItem: Int = 0, selectPositive: Boolean = true): BaseDialogFragment.DisplayDialog {
        return object: BaseDialogFragment.DisplayDialog {
            override fun alertDialog(
                title: String,
                message: String,
                positiveButton: String,
                negativeButton: String,
                dialogListener: BaseDialogFragment.DialogListener?
            ) {
                if (selectPositive) dialogListener?.onPositive()
            }

            override fun pickerDialog(
                title: String,
                items: Array<String>,
                defaultSelectedItem: Int,
                positiveButton: String,
                negativeButton: String,
                dialogListener: BaseDialogFragment.DialogListener?
            ) {
                if (selectPositive) dialogListener?.onPositive(selectedItem)
            }
        }
    }
}
