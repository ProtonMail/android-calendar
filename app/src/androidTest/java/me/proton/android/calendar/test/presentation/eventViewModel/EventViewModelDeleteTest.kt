package me.proton.android.calendar.test.presentation.eventViewModel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.util.Recurrence
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.test.shared.mocks.*
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.core.util.kotlin.toBoolean
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent

/**
 * EventViewModel delete flow tests
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Those tests have not been maintained. Update them following recent changes.")
@LargeTest
internal class EventViewModelDeleteTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * Single event
     */
    @Test
    fun deleteEventTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent()

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.THIS_EVENT, 0) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Single event error
     */
    @Test
    fun deleteEventErrorTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent()

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Error("error message")

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_error) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted_error)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.THIS_EVENT, 0) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_error) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnack(
                resourceProviderMock.provideString(R.string.snack_event_deleted_error)
            ))
        }
    }

    /**
     * Single occurrence recurring
     */
    @Test
    fun deleteSingleOccurrenceRecurringEventTest() {
        runBlocking {

            // Mock event
            val event = EventMocks.provideEvent(isRecurring = true)
            // Set recurrence count to 1
            event.iCalEvent.setRecurrenceRule(Recurrence.Builder(event.iCalEvent.recurrenceRule.value).count(1).build())
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.ALL_EVENTS, null) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Recurring from first occurrence with option "This"
     */
    @Test
    fun deleteRecurringEventOptionThisTest() {
        runBlocking {

            // Mock event
            val event = EventMocks.provideEvent(isRecurring = true)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(
                R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Select option this
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 0)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.THIS_EVENT, occurrenceNumber) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // This and future is hidden when first occurrence is selected
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Recurring from second occurrence with option "This & future"
     */
    @Test
    fun deleteRecurringEventOptionThisAndFutureTest() {
        runBlocking {

            // Mock event
            val event = EventMocks.provideEvent(isRecurring = true)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(
                R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 2
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Select option this and future
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 1)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.THIS_EVENT_AND_FUTURE, occurrenceNumber) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // This and future is hidden when first occurrence is selected
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Recurring from second occurrence with option "All"
     */
    @Test
    fun deleteRecurringEventOptionAllTest() {
        runBlocking {

            // Mock event
            val event = EventMocks.provideEvent(isRecurring = true)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns event

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(
                R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(
                R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 2
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Select option all
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 2)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.ALL_EVENTS, null) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // This and future is hidden when first occurrence is selected
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Recurring event in disabled calendar
     */
    @Test
    fun deleteDisabledCalendarRecurringEventTest() {
        runBlocking {

            // Mock recurring event with disabled calendar
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isRecurring = true,
                hasDisabledCalendar = true
            )

            // Handle delete use case
            coEvery { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) } returns UseCase.Result.Success<Unit>()

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDelete(userId, eventId, calendarId, EventEditDeleteOption.ALL_EVENTS, null) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * While changing answer
     */
    @Test
    fun deleteEventChangingAnswerTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(any())
            } else {
                transformEventUseCaseMock.execute(any())
            } } returns EventMocks.provideEvent()

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Simulate changing answer state
            eventViewModel.attendeeAnswerState.value = Pair(ParticipationStatus.ACCEPTED, true)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Success snack
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            // Handle delete use case
            coVerify(exactly = 0) { handleDeleteUseCaseMock.handleDelete(any(), any(), any(), any(), any()) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
        }
    }

    /**
     * Event with attendees (with user as organizer)
     */
    @Test
    fun deleteEventAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isOrganizer = true
            )

            // Handle delete use case with emailSent to true
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(true)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(
                    attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted_as_organizer)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(Pair(attendeeEmail, UserMocks.provideSendPreferences())),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer)
            ))
        }
    }

    /**
     * Event with attendees (with user as organizer) in a disabled calendar
     */
    @Test
    fun deleteEventDisabledCalendarAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer) and disabled calendar
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isOrganizer = true,
                hasDisabledCalendar = true
            )

            // Handle delete use case with emailSent to false (calendar disabled)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(false)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(
                    attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer_disabled) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event_as_organizer_disabled)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = true
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Recurring event with attendees (with user as organizer)
     */
    @Test
    fun deleteRecurringEventAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isOrganizer = true,
                isRecurring = true
            )

            // Handle delete use case with emailSent to true
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(true)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(
                    attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_recurring_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted_as_organizer)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId = userId,
                event = eventCopy,
                attendees = listOf(attendee),
                sendPreferences = mapOf(Pair(attendeeEmail, UserMocks.provideSendPreferences())),
                timeFormatIs24Hours = timeFormat.toBoolean(),
                isPartOfChain = true,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer)
            ))
        }
    }

    /**
     * Recurring event with attendees (with user as organizer) in a disabled calendar
     */
    @Test
    fun deleteRecurringEventDisabledCalendarAsAnOrganizerTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer) and disabled calendar
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isOrganizer = true,
                isRecurring = true,
                hasDisabledCalendar = true
            )

            // Handle delete use case with emailSent to false (disabled calendar)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(false)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(
                    attendeeEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_recurring_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer_disabled) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_recurring_event_as_organizer_disabled)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            val attendee = Attendee(attendeeName, attendeeEmail)
            attendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(attendee),
                mapOf(),
                timeFormat.toBoolean(),
                isPartOfChain = true,
                isCalendarDisabled = true
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_recurring_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_organizer_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Event with attendees (with user as organizer) with network error
     */
    @Test
    fun deleteEventAsAnOrganizerNetworkErrorTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isOrganizer = true
            )

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.NetworkError)
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_network_error) } returns protonCalendarApplication.getString(
                R.string.snack_network_error)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 0) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_network_error) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnack(
                resourceProviderMock.provideString(R.string.snack_network_error)
            ))
        }
    }

    /**
     * Event with attendees (with user as organizer) with all send preferences error
     */
    @Test
    fun deleteEventAsAnOrganizerAllSendPreferencesErrorTest() {
        runBlocking {

            // Mock event with attendees (user as the organizer)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isOrganizer = true
            )

            // Handle delete use case with emailSent to false (empty send preferences)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(false)

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled)
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns
                    protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            val emailError = protonCalendarApplication.getString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            )
            coEvery { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) } returns emailError

            // Send preferences dialog
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns
                    protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_none_message, emailError) } returns
                    protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_none_message, emailError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(
                R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(),
                mapOf(),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 2) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_attendees_send_prefs_error_none_message,
                emailError
            ) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    /**
     * Event with attendees (with user as organizer) with some send preferences error
     */
    @Test
    fun deleteEventAsAnOrganizerSomeSendPreferencesErrorTest() {
        runBlocking {

            // Mock event with two attendees (user as the organizer)
            val event = EventMocks.provideEvent(isOrganizer = true)
            val secondAttendee = Attendee(secondAttendeeName, secondAttendeeEmail)
            secondAttendee.participationStatus = ParticipationStatus.DECLINED
            event.iCalEvent.addAttendee(secondAttendee)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns event

            // Handle delete use case with emailSent to true
            coEvery { handleDeleteUseCaseMock.handleDeleteAsOrganizer(any(), any(), any(), any(), any(), any(), any()) } returns UseCase.Result.Success(true)

            // Get canonical and send preferences for attendees
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail, secondAttendeeEmail)) } returns mapOf(
                Pair(attendeeEmail, attendeeEmail), Pair(secondAttendeeEmail, secondAttendeeEmail)
            )
            coEvery { obtainSendPreferencesUseCaseMock.execute(
                userId, mapOf(
                    Pair(attendeeEmail, attendeeEmail),
                    Pair(secondAttendeeEmail, secondAttendeeEmail)
                )) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled),
                Pair(secondAttendeeEmail, ObtainSendPreferencesUseCase.Result.Success(sendPreferences = UserMocks.provideSendPreferences()))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_event_as_organizer)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns
                    protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            val emailWithError = protonCalendarApplication.getString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            )
            coEvery { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) } returns emailWithError

            // Send preferences dialog
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns protonCalendarApplication.getString(
                R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_some_message, emailWithError) } returns protonCalendarApplication.getString(
                R.string.event_attendees_send_prefs_error_some_message, emailWithError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(
                R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted_as_organizer)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsOrganizer(
                userId,
                eventCopy,
                listOf(secondAttendee),
                mapOf(Pair(secondAttendeeEmail, UserMocks.provideSendPreferences())),
                timeFormat.toBoolean(),
                isPartOfChain = false,
                isCalendarDisabled = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_event_as_organizer) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 2) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_some_message, emailWithError) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template, attendeeEmail, protonCalendarApplication.getString(
                    R.string.event_send_prefs_error_address_disabled)) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_organizer)
            ))
        }
    }

    /**
     * Event with attendees (with user as attendee)
     */
    @Test
    fun deleteEventAsAnAttendeeTest() {
        runBlocking {

            // Mock event with attendees (user as the attendee)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns
                    EventMocks.provideEvent(isAttendee = true, participationStatus = ParticipationStatus.ACCEPTED)

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf()

            // Handle delete use case with emailSent to false (empty send preferences)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsAttendee(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                    UseCase.Result.Success(true)

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(organizerEmail, ObtainSendPreferencesUseCase.Result.Success(UserMocks.provideSendPreferences()))
            )

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.dialog_title_delete_event) } returns protonCalendarApplication.getString(
                R.string.dialog_title_delete_event)
            coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_single_event_as_attendee) } returns protonCalendarApplication.getString(
                R.string.dialog_description_delete_single_event_as_attendee)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted_as_attendee) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted_as_attendee)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsAttendee(
                userId = userId,
                event = eventCopy,
                cancelledSingleEdits = emptyList(),
                userEmail = userEmail,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                hasNonCancelledSingleEdit = false,
                occurrenceNumber = occurrenceNumber,
                isOrphanSingleEdit = false,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean(),
                sendReply = true
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_title_delete_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_description_delete_single_event_as_attendee) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted_as_attendee) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted_as_attendee)
            ))
        }
    }

    /**
     * Event with attendees (with user as attendee) with some send preferences error
     */
    @Test
    fun deleteEventAsAnAttendeeSendPreferencesErrorTest() {
        runBlocking {

            // Mock event with attendees (user as the attendee)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns
                    EventMocks.provideEvent(isAttendee = true, participationStatus = ParticipationStatus.ACCEPTED)

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf()

            // Handle delete use case with emailSent to false (empty send preferences)
            coEvery { handleDeleteUseCaseMock.handleDeleteAsAttendee(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
                    UseCase.Result.Success(false)

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(organizerEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled)
            )

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns
                    protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            val emailError = protonCalendarApplication.getString(
                R.string.event_send_prefs_error_template,
                organizerEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            )
            coEvery { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                organizerEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) } returns emailError

            // Delete confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_organizer_send_prefs_error_title) } returns protonCalendarApplication.getString(
                R.string.event_organizer_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_delete_as_attendee_send_prefs_error_message, emailError) } returns protonCalendarApplication.getString(
                R.string.event_delete_as_attendee_send_prefs_error_message, emailError)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_delete) } returns protonCalendarApplication.getString(
                R.string.dialog_button_delete)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(
                R.string.dialog_button_cancel)

            // Delete success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_deleted) } returns protonCalendarApplication.getString(
                R.string.snack_event_deleted)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            withContext(Dispatchers.Default) {
                // Start delete
                eventViewModel.onDeleteClick(provideDisplayDialog(), occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle delete use case
            coVerify(exactly = 1) { handleDeleteUseCaseMock.handleDeleteAsAttendee(
                userId = userId,
                event = eventCopy,
                cancelledSingleEdits = emptyList(),
                userEmail = userEmail,
                sendPreferences = mapOf(),
                hasNonCancelledSingleEdit = false,
                occurrenceNumber = occurrenceNumber,
                isOrphanSingleEdit = false,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean(),
                sendReply = false
            ) }

            // Delete confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_organizer_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_delete_as_attendee_send_prefs_error_message,
                emailError
            ) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_delete) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                organizerEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_deleted) }

            assert(eventViewModel.eventDetailsState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(R.string.snack_event_deleted)
            ))
        }
    }

    @Test
    fun getDeleteAsAnAttendeeMessageTest() {

        val eventViewModel = getEventViewModel()

        var result = ""
        var message = ""
        var singleEditWarning = ""

        /**
         * Tests with displayWarning at false
         */
        var displayWarning = false

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_non_standalone_single_edit_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_non_standalone_single_edit_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        message = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) } returns message
        singleEditWarning = protonCalendarApplication.getString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee) } returns singleEditWarning
        result = protonCalendarApplication.getString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        )
        coEvery { resourceProviderMock.provideString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        ) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = true,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        message = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) } returns message
        singleEditWarning = protonCalendarApplication.getString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee) } returns singleEditWarning
        result = protonCalendarApplication.getString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        )
        coEvery { resourceProviderMock.provideString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        ) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = true,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = true,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = true,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = true,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = true,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_event)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_event) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = true,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        /**
         * Tests with displayWarning at true
         */
        displayWarning = true

        val emailWithErrors = "emailWithErrors"
        result = protonCalendarApplication.getString(R.string.event_delete_as_attendee_send_prefs_error_message, emailWithErrors)
        coEvery { resourceProviderMock.provideString(R.string.event_delete_as_attendee_send_prefs_error_message, emailWithErrors) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = true,
                emailsWithErrors = emailWithErrors,
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        message = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled) } returns message
        singleEditWarning = protonCalendarApplication.getString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee) } returns singleEditWarning
        result = protonCalendarApplication.getString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        )
        coEvery { resourceProviderMock.provideString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        ) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = true,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = false
            )
        )

        message = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled) } returns message
        singleEditWarning = protonCalendarApplication.getString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee) } returns singleEditWarning
        result = protonCalendarApplication.getString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        )
        coEvery { resourceProviderMock.provideString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        ) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = true,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = false
            )
        )

        message = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_attendee) } returns message
        singleEditWarning = protonCalendarApplication.getString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee) } returns singleEditWarning
        result = protonCalendarApplication.getString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        )
        coEvery { resourceProviderMock.provideString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        ) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = true,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        message = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_attendee) } returns message
        singleEditWarning = protonCalendarApplication.getString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee) } returns singleEditWarning
        result = protonCalendarApplication.getString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        )
        coEvery { resourceProviderMock.provideString(
            R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee,
            message,
            singleEditWarning
        ) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = true,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = false
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_recurring_event_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_recurring_event_as_attendee) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = true,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee_disabled)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee_disabled) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = false
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_single_event_as_attendee_disabled)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_single_event_as_attendee_disabled) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = true,
                emailsWithErrors = emailWithErrors,
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = false
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_single_event_as_attendee_disabled)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_single_event_as_attendee_disabled) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = false
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_single_event_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_single_event_as_attendee) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = true,
                isOrphanSingleEdit = true,
                isAddressAllowedToSend = true
            )
        )

        result = protonCalendarApplication.getString(R.string.dialog_description_delete_single_event_as_attendee)
        coEvery { resourceProviderMock.provideString(R.string.dialog_description_delete_single_event_as_attendee) } returns result
        assert(
            result == eventViewModel.getDeleteAsAnAttendeeMessage(
                displayWarning = displayWarning,
                isCalendarDisabled = false,
                sendPrefsFailed = false,
                emailsWithErrors = "",
                isRecurring = false,
                hasAnsweredSingleEdit = false,
                hasNonCancelledSingleEdit = false,
                isSingleEdit = false,
                isOrphanSingleEdit = false,
                isAddressAllowedToSend = true
            )
        )
    }

}
