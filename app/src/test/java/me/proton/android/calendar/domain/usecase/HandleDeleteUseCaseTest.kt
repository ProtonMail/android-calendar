package me.proton.android.calendar.domain.usecase

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.ApiResponseCode
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.test.shared.mocks.*
import me.proton.android.calendar.test.shared.mocks.CalendarMocks.provideCalendarUserSettingsEntity
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEvent
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEventEntity
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEventResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


internal class HandleDeleteUseCaseTest {

    private lateinit var appDatabaseMock: AppDatabase

    private val calendarsApiMock: CalendarsApi = mockk()

    private val calendarsRepositoryMock: CalendarsRepository = mockk()

    private val handleAlarmsUseCaseMock: HandleAlarmsUseCase = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val sendEmailUseCaseMock: SendEmailUseCase = mockk()
    private val updateParticipationStatusUseCaseMock: UpdateParticipationStatusUseCase = mockk()
    private val eventDecryptorMock: EventDecryptor = mockk()

    private val testsLogger = TestsLogger

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true

        appDatabaseMock = mockk()

        coEvery { editCreateEventUseCaseMock.execute(userId, any(), any(), any()) } returns UseCase.Result.Success(listOf(userId.id))

        coEvery { sendEmailUseCaseMock.sendInviteToAttendees(userId, any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

        coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

        coEvery { calendarsApiMock.syncEvents(userId, any(), any()) } returns ApiResponse.Success(
            getSyncEventsApiResponse()
        )
        coEvery { calendarsApiMock.getEventsByUid(userId, any(), any(), any()) } returns ApiResponse.Success(
            EventsByUidApiResponse(
                listOf(
                    provideEventResponse()
                )
            )
        )

        coEvery { appDatabaseMock.membersDao().selectCalendarMembers(any()) } returns listOf(
            MemberEntity(
                id = "id",
                permissions = 64,
                addressId = addressId.id,
                email = "email@pm.me",
                calendarId = "calendarId",
                color = calendarColor,
                display = calendarDisplay,
                flags = calendarFlags,
                name = calendarName,
                description = calendarDescription,
                priority = 0
            )
        )

        coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns provideEventEntity() // We do not care about this EventEntity since it is just used as a parameter for transformEventUseCase and that is mocked above to return event
        coEvery { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) } returns provideCalendarUserSettingsEntity()
        coEvery { calendarsRepositoryMock.selectRootEventEntity(any()) } returns provideEventEntity()
        coEvery { calendarsRepositoryMock.deleteEventsById(any(), any()) } just Runs
        coEvery { calendarsRepositoryMock.deleteEventsMetadataByEventIds(any()) } just Runs
        coEvery { calendarsRepositoryMock.fetchEventById(userId, any(), any()) } returns ApiResponse.Success(
            EventApiResponse(
                event = provideEventResponse()
            )
        )

        coEvery { sendEmailUseCaseMock.sendCancellationToAttendees(userId, any(), any(), any(), any(), any())
        } returns UseCase.Result.Success<Unit>()
        coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(userId, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns UseCase.Result.Success<Unit>()

        coEvery { updateParticipationStatusUseCaseMock.execute(userId, any(), any(), any(), any(), any(), any())
        } returns UseCase.Result.Success<Unit>()
    }

    private fun getHandleDeleteUseCase(): HandleDeleteUseCase {
        return HandleDeleteUseCase(
            testsLogger,
            handleAlarmsUseCaseMock,
            calendarsApiMock,
            appDatabaseMock,
            editCreateEventUseCaseMock,
            transformEventUseCaseMock,
            calendarsRepositoryMock,
            sendEmailUseCaseMock,
            updateParticipationStatusUseCaseMock,
            eventDecryptorMock
        )
    }

    enum class SyncEventErrorType {
        ERROR_EVENT_DOES_NOT_EXIST,
        ERROR_DELETING_ON_SERVER,
        NO_ERROR
    }

    private fun getSyncEventsApiResponse(syncEventErrorType: SyncEventErrorType = SyncEventErrorType.NO_ERROR): SyncEventsApiResponse {
        val syncResponseWrapperList = when (syncEventErrorType) {
            SyncEventErrorType.ERROR_EVENT_DOES_NOT_EXIST -> listOf(
                SyncResponseWrapper(
                    index = 0,
                    response = SyncResponse(
                        code = ApiResponseCode.DOES_NOT_EXIST,
                        event = provideEventResponse()
                    )
                )
            )
            SyncEventErrorType.ERROR_DELETING_ON_SERVER -> listOf(
                SyncResponseWrapper(
                    index = 0,
                    response = SyncResponse(
                        code = 0,
                        event = provideEventResponse()
                    )
                )
            )
            SyncEventErrorType.NO_ERROR -> listOf()
        }

        return SyncEventsApiResponse(
            responses = syncResponseWrapperList
        )
    }

    private fun prepareEvent(isRecurring: Boolean = false): Event {

        // TODO Handle Single edits by preparing second set of EventEntity & Event and mocking transformEventUseCaseMock parameter to match either

        val event = provideEvent(isRecurring = isRecurring)

        coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
            eventDecryptorMock.decrypt(any())
        } else {
            transformEventUseCaseMock.execute(any())
        } } returns event

        return event
    }

    @Test
    fun `handleDelete delete single event test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false)

            /* Delete single event */
            assert(
                getHandleDeleteUseCase().handleDelete(
                    userId,
                    event.id,
                    event.calendar.id,
                    EventEditDeleteOption.THIS_EVENT,
                    0
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } }
            coVerify(exactly = 0) { appDatabaseMock.membersDao().selectCalendarMembers(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) }
            coVerify(exactly = 0) { editCreateEventUseCaseMock.execute(userId, any(), any(), any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.deleteEventsById(event.calendar.id, listOf(event.id)) }
            coVerify(exactly = 1) { calendarsRepositoryMock.deleteEventsMetadataByEventIds(listOf(event.id)) }
            coVerify(exactly = 1) { handleAlarmsUseCaseMock.execute(userId) }
        }
    }

    @Test
    fun `handleDelete single event that doesn't exist on server anymore test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false)

            /* Delete single event that doesn't exist on server anymore */
            coEvery { calendarsApiMock.syncEvents(userId, any(), any()) } returns ApiResponse.Success(
                getSyncEventsApiResponse(SyncEventErrorType.ERROR_EVENT_DOES_NOT_EXIST)
            )

            assert(
                getHandleDeleteUseCase().handleDelete(
                    userId,
                    event.id,
                    event.calendar.id,
                    EventEditDeleteOption.THIS_EVENT,
                    0
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } }
            coVerify(exactly = 0) { appDatabaseMock.membersDao().selectCalendarMembers(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) }
            coVerify(exactly = 0) { editCreateEventUseCaseMock.execute(userId, any(), any(), any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.deleteEventsById(event.calendar.id, listOf(event.id)) }
            coVerify(exactly = 1) { calendarsRepositoryMock.deleteEventsMetadataByEventIds(listOf(event.id)) }
            coVerify(exactly = 1) { handleAlarmsUseCaseMock.execute(userId) }
        }
    }

    @Test
    fun `handleDelete ERROR delete single event test`() {
        runBlocking {

            // Make sure to call prepare method
            val event = prepareEvent(isRecurring = false)

            coEvery { calendarsApiMock.syncEvents(userId, any(), any()) } returns ApiResponse.Success(
                getSyncEventsApiResponse(SyncEventErrorType.ERROR_DELETING_ON_SERVER)
            )

            /* Error delete single event */
            assert(
                getHandleDeleteUseCase().handleDelete(
                    userId,
                    event.id,
                    event.calendar.id,
                    EventEditDeleteOption.THIS_EVENT,
                    0
                ) is UseCase.Result.Error
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectEventEntity(any()) }
            coVerify(exactly = 1) { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } }
            coVerify(exactly = 0) { appDatabaseMock.membersDao().selectCalendarMembers(any()) }
            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarUserSettings(userId.id) }
            coVerify(exactly = 0) { editCreateEventUseCaseMock.execute(userId, any(), any(), any()) }
            coVerify(exactly = 0) { calendarsRepositoryMock.deleteEventsById(event.calendar.id, listOf(event.id)) }
            coVerify(exactly = 0) { handleAlarmsUseCaseMock.execute(userId) }
        }
    }
}

