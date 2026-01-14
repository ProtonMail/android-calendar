package me.proton.android.calendar.domain.usecase

import android.util.Log
import io.mockk.*
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.logger.TestsLogger
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.test.shared.mocks.*
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEvent
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEventEntity
import me.proton.android.calendar.test.shared.mocks.EventMocks.provideEventResponse
import me.proton.android.calendar.test.shared.mocks.UserMocks.provideUserSettingsEntity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class HandleSaveUseCaseTest {

    private val calendarsRepositoryMock: CalendarsRepository = mockk()

    private val transformEventUseCaseMock: TransformEventUseCase = mockk()
    private val editCreateEventUseCaseMock: EditCreateEventUseCase = mockk()
    private val handleDeleteUseCaseMock: HandleDeleteUseCase = mockk()
    private val sendEmailUseCaseMock: SendEmailUseCase = mockk()
    private val eventDecryptorMock: EventDecryptor = mockk()

    private val testsLogger = TestsLogger

    @BeforeEach
    fun `before each`() {
        clearAllMocks()
        mockkStatic(Log::class)
        coEvery { Log.isLoggable(any(), any()) } returns true

        coEvery { handleDeleteUseCaseMock.handleDeleteSingleEdits(userId, any(), any()) } returns UseCase.Result.Success<Unit>()
        coEvery { editCreateEventUseCaseMock.execute(userId, any(), any(), any()) } returns UseCase.Result.Success(listOf(userId.id))
        coEvery { sendEmailUseCaseMock.sendInviteToAttendees(userId, any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()
    }

    private fun getHandleSaveUseCase(): HandleSaveUseCase {
        return HandleSaveUseCase(
            testsLogger,
            calendarsRepositoryMock,
            transformEventUseCaseMock,
            editCreateEventUseCaseMock,
            handleDeleteUseCaseMock,
            sendEmailUseCaseMock,
            eventDecryptorMock
        )
    }

    @Test
    fun `handleSave create single event test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns provideEventEntity()
            val event = provideEvent(isRecurring = false)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `handleSave create recurring event test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns provideEventEntity()
            val event = provideEvent(isRecurring = true)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `handleSave create single event with attendees test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns provideEventEntity()
            val event = provideEvent(isRecurring = false, isOrganizer = true)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(),
                    event = event,
                    originalDbEvent = null,
                    userSettings = provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }

            coVerify(exactly = 1) {
                sendEmailUseCaseMock.sendInviteToAttendees(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun `handleSave create recurring event with attendees test`() {
        runBlocking {

            coEvery { calendarsRepositoryMock.selectEventEntity(any()) } returns provideEventEntity()
            val event = provideEvent(isRecurring = true, isOrganizer = true)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } } returns event

            /* Create single event */
            assert(
                getHandleSaveUseCase().handleSave(
                    editOption = null,
                    occurrenceNumber = 1,
                    timeFormatIs24Hours = true,
                    sendPreferences = mapOf(), // TODO Mock send prefs
                    event = event,
                    originalDbEvent = null,
                    userSettings = provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                ) is UseCase.Result.Success<*>
            )

            coVerify(exactly = 1) {
                editCreateEventUseCaseMock.execute(any(), any(), any(), any())
            }

            coVerify(exactly = 1) {
                sendEmailUseCaseMock.sendInviteToAttendees(any(), any(), any(), any(), any(), any(), any())
            }
        }
    }
}
