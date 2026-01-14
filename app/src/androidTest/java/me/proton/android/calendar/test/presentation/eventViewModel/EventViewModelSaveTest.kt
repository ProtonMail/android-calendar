package me.proton.android.calendar.test.presentation.eventViewModel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.property.Attendee
import biweekly.util.Frequency
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.test.shared.mocks.*
import me.proton.core.util.kotlin.toBoolean
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent

/**
 * EventViewModel save flow tests
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Those tests have not been maintained. Update them following recent changes.")
@LargeTest
internal class EventViewModelSaveTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * Create an all day event (with hidden calendar)
     */
    @Test
    fun createAllDayEventTest() {
        runBlocking {

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Mock hidden default calendar
            coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_created) } returns protonCalendarApplication.getString(
                R.string.snack_event_created)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, null, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = null,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                )
            }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_created) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_created
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * TO REMOVE ONCE EDITING INVITE IS ALLOWED
     * Error trying to edit event with attendees
     */
    @Test
    fun editInviteErrorTest() { // TODO To remove once editing invite is allowed
        runBlocking {
            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isOrganizer = true)

            // Mock fetchEventById with event so that isApiEventAnInvitation returns true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse(hasAttendees = true)
                )
            )

            // Error snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_edit_with_attendees_error) } returns protonCalendarApplication.getString(
                R.string.snack_event_edit_with_attendees_error)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Force eventEdited value
            eventViewModel.eventEdited = true

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_edit_with_attendees_error) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnack(
                resourceProviderMock.provideString(R.string.snack_event_edit_with_attendees_error)
            ))
        }
    }

    /**
     * Edit a recurring event (with hidden calendar)
     */
    @Test
    fun editRecurringEventTest() {
        runBlocking {

            // Mock event with hidden calendar
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true, hasHiddenCalendar = true)

            // Mock fetchEventById so that isApiEventAnInvitation returns false
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse()
                )
            )

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, any(), any()) } returns listOf()

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Edit option picker dialog
            coEvery { resourceProviderMock.provideString(R.string.event_text_edit_event) } returns protonCalendarApplication.getString(R.string.event_text_edit_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_updated) } returns protonCalendarApplication.getString(
                R.string.snack_event_updated)

            val occurrenceNumber = 2
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Force eventEdited value
            eventViewModel.eventEdited = true

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            // Mock selecting option edit This and future
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 1)

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = EventEditDeleteOption.THIS_EVENT_AND_FUTURE,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = false,
                    sendEmailUpdate = null
                )
            }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            // Edit option picker dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_text_edit_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_updated) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_updated
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Edit a recurring event that has a single edit with option All
     */
    @Test
    fun editRecurringEventWithSingleEditTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true)

            // Mock fetchEventById so that isApiEventAnInvitation returns false
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse()
                )
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Mock single edits info (one single edit)
            coEvery { calendarsRepositoryMock.hasSingleEdits(userId, eventUid) } returns true

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Edit option picker dialog
            coEvery { resourceProviderMock.provideString(R.string.event_text_edit_event) } returns protonCalendarApplication.getString(R.string.event_text_edit_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Warning dialog
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_title) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_title)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_all_description) } returns protonCalendarApplication.getString(R.string.event_recurring_update_all_description)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_confirm) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_cancel) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_updated) } returns protonCalendarApplication.getString(
                R.string.snack_event_updated)

            // Edit first occurrence
            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Force eventEdited value
            eventViewModel.eventEdited = true

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            // Mock selecting option edit All (edit first occurrence so option All is index 2)
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 2)

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = EventEditDeleteOption.ALL_EVENTS,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = false,
                    sendEmailUpdate = null
                )
            }

            // Edit option picker dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_text_edit_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // Edit first occurrence so option This and future is hidden
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Warning dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_all_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_updated) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_updated
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Edit a recurring event that has an ex date with option This and future
     */
    @Test
    fun editRecurringEventWithExDateTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true, hasExDate = true)

            // Mock fetchEventById so that isApiEventAnInvitation returns false
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse()
                )
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, any(), any()) } returns listOf()

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Edit option picker dialog
            coEvery { resourceProviderMock.provideString(R.string.event_text_edit_event) } returns protonCalendarApplication.getString(R.string.event_text_edit_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Warning dialog
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_title) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_title)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_all_description) } returns protonCalendarApplication.getString(R.string.event_recurring_update_all_description)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_confirm) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_cancel) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_updated) } returns protonCalendarApplication.getString(
                R.string.snack_event_updated)

            // Edit third occurrence
            val occurrenceNumber = 2
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Force eventEdited value
            eventViewModel.eventEdited = true

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            // Mock selecting option edit This and future
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 1)

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = EventEditDeleteOption.THIS_EVENT_AND_FUTURE,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = false,
                    sendEmailUpdate = null
                )
            }

            // Edit option picker dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_text_edit_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Warning dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_all_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_updated) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_updated
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Edit the recurrence rule of a recurring event with option This
     */
    @Test
    fun editRRuleRecurringEventWithOptionThisTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true)

            // Mock fetchEventById so that isApiEventAnInvitation returns false
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse()
                )
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Mock single edits info (one single edit)
            coEvery { calendarsRepositoryMock.hasSingleEdits(userId, eventUid) } returns true

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Edit option picker dialog
            coEvery { resourceProviderMock.provideString(R.string.event_text_edit_event) } returns protonCalendarApplication.getString(R.string.event_text_edit_event)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_this_and_future)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) } returns protonCalendarApplication.getString(R.string.event_recurring_edit_all_events)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_ok) } returns protonCalendarApplication.getString(R.string.dialog_button_ok)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            // Warning dialog
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_title) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_title)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_description) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_description)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_confirm) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_recurring_update_this_cancel) } returns protonCalendarApplication.getString(R.string.event_recurring_update_this_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_updated) } returns protonCalendarApplication.getString(
                R.string.snack_event_updated)

            // Edit first occurrence
            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Set RRule manually edited
            eventViewModel.rruleManuallyEdited = true

            // Update recurrence rule count (from 10 to 20)
            eventViewModel.handleRecurrence(
                frequency = Frequency.DAILY,
                untilDate = false,
                interval = null,
                count = 20,
                daysOfWeek = null,
                customMonthly = false
            )

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            // Mock selecting option edit This
            val provideDisplayDialog = provideDisplayDialog(selectedItem = 0)

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = EventEditDeleteOption.THIS_EVENT,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = true,
                    isCreate = false,
                    sendEmailUpdate = null
                )
            }

            // Edit option picker dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_text_edit_event) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_this) }
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.event_recurring_edit_this_and_future) } // Edit first occurrence so option This and future is hidden
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_edit_all_events) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_ok) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            // Warning dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_recurring_update_this_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_updated) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_updated
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Create an event with attendees
     */
    @Test
    fun createEventWithAttendeesTest() {
        runBlocking {

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(
                    attendeeEmail,
                    ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    )
                )
            )

            // Mock calendar member
            coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Send invitation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_title) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_title)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_description) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_description)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_confirm) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_cancel) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_created) } returns protonCalendarApplication.getString(
                R.string.snack_event_created)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Add an attendee
            eventViewModel.handleAttendee(Attendee(attendeeName, attendeeEmail), attendeeEmail, addAttendee = true)

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, null, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = null,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(Pair(attendeeEmail, UserMocks.provideSendPreferences())),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                )
            }

            // Send invitation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_created) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_created
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Edit a recurring event by adding an attendee
     */
    @Test
    fun editRecurringEventAddAttendeeTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true)

            // Mock fetchEventById so that isApiEventAnInvitation returns false
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse()
                )
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Mock single edits info (no single edit)
            coEvery { calendarsRepositoryMock.hasSingleEdits(userId, eventUid) } returns false

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(
                    attendeeEmail,
                    ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    )
                )
            )

            // Mock calendar member
            coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Send invitation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_add_participants_dialog_title) } returns protonCalendarApplication.getString(R.string.event_add_participants_dialog_title)
            coEvery { resourceProviderMock.provideString(R.string.recurring_event_add_participants_dialog_description) } returns protonCalendarApplication.getString(R.string.recurring_event_add_participants_dialog_description)
            coEvery { resourceProviderMock.provideString(R.string.event_add_participants_dialog_confirm) } returns protonCalendarApplication.getString(R.string.event_add_participants_dialog_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_add_participants_dialog_cancel) } returns protonCalendarApplication.getString(R.string.event_add_participants_dialog_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_updated) } returns protonCalendarApplication.getString(
                R.string.snack_event_updated)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Add an attendee
            eventViewModel.handleAttendee(Attendee(attendeeName, attendeeEmail), attendeeEmail, addAttendee = true)

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            // Mock selecting option edit This and future
            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = EventEditDeleteOption.ALL_EVENTS,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(Pair(attendeeEmail, UserMocks.provideSendPreferences())),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = false,
                    sendEmailUpdate = null
                )
            }

            // Send invitation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_add_participants_dialog_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.recurring_event_add_participants_dialog_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_add_participants_dialog_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_add_participants_dialog_cancel) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_updated) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_updated
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Edit a recurring event (with an ex date) by adding an attendee, fail to get send preferences
     */
    @Test
    fun editRecurringEventWithExDateAddAttendeeSendPrefsAllErrorTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true, hasExDate = true)

            // Mock fetchEventById so that isApiEventAnInvitation returns false
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    EventMocks.provideEventResponse()
                )
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Mock single edits info (no single edit)
            coEvery { calendarsRepositoryMock.hasSingleEdits(userId, eventUid) } returns false

            // Get canonical and send preferences error for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(Pair(
                attendeeEmail, attendeeEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(attendeeEmail, attendeeEmail))) } returns mapOf(
                Pair(attendeeEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled)
            )

            // Mock calendar member
            coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

            // Handle save use case call with error (empty send prefs so failed to send mail)
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Error("Failed to send mail", UseCase.Error.HandleSave.EditSendEmail)

            // Send invitation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_add_participants_dialog_title) } returns protonCalendarApplication.getString(R.string.event_add_participants_dialog_title)
            coEvery { resourceProviderMock.provideString(R.string.recurring_event_add_participants_overwrite_dialog_description) } returns protonCalendarApplication.getString(R.string.recurring_event_add_participants_overwrite_dialog_description)
            coEvery { resourceProviderMock.provideString(R.string.event_add_participants_dialog_confirm) } returns protonCalendarApplication.getString(R.string.event_add_participants_dialog_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_add_participants_dialog_cancel) } returns protonCalendarApplication.getString(R.string.event_add_participants_dialog_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
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
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_none_message, emailError) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_none_message, emailError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_cancel) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_updated_error_failed_mail) } returns protonCalendarApplication.getString(
                R.string.snack_event_updated_error_failed_mail)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Add an attendee
            eventViewModel.handleAttendee(Attendee(attendeeName, attendeeEmail), attendeeEmail, addAttendee = true)

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            // Mock selecting option edit This and future
            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, eventId, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = EventEditDeleteOption.ALL_EVENTS,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = false,
                    sendEmailUpdate = null
                )
            }

            // Send invitation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_add_participants_dialog_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.recurring_event_add_participants_overwrite_dialog_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_add_participants_dialog_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_add_participants_dialog_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_attendees_send_prefs_error_none_message,
                emailError
            ) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_cancel) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_updated_error_failed_mail) }

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnack(
                resourceProviderMock.provideString(
                    R.string.snack_event_updated_error_failed_mail
                )
            ))
        }
    }

    /**
     * Create an event with multiple attendees, fail to get send preferences for one
     */
    @Test
    fun createEventWithAttendeesSendPrefsSomeErrorTest() {
        runBlocking {

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail, organizerEmail)) } returns mapOf(
                Pair(attendeeEmail, attendeeEmail), Pair(organizerEmail, organizerEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(
                Pair(attendeeEmail, attendeeEmail), Pair(organizerEmail, organizerEmail)
            )) } returns mapOf(
                Pair(
                    organizerEmail,
                    ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    )
                ),
                Pair(
                    attendeeEmail,
                    ObtainSendPreferencesUseCase.Result.Error.AddressDisabled
                )
            )

            // Mock calendar member
            coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

            // Handle save use case call
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Success<Unit>()

            // Send invitation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_title) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_title)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_description) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_description)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_confirm) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_cancel) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
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
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_some_message, emailError) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_some_message, emailError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_cancel) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_created) } returns protonCalendarApplication.getString(
                R.string.snack_event_created)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Add two attendees
            eventViewModel.handleAttendee(Attendee(attendeeName, attendeeEmail), attendeeEmail, addAttendee = true)
            eventViewModel.handleAttendee(Attendee(organizerName, organizerEmail), organizerEmail, addAttendee = true)

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, null, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = null,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                )
            }

            // Send invitation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_attendees_send_prefs_error_some_message,
                emailError
            ) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_cancel) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_created) }

            // Make sure only one attendee is left, we remove those that have failed send prefs
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.attendees?.size == 1)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.attendees?.first()?.extractEmail() == organizerEmail)

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_created
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

    /**
     * Create an event with one attendee, fail to get send preferences
     */
    @Test
    fun createEventWithAttendeesSendPrefsAllErrorTest() {
        runBlocking {

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Schedule alarms if any
            coEvery { handleAlarmsUseCaseMock.execute(userId) } returns UseCase.Result.Success<Unit>()

            // Get canonical and send preferences for attendee
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(attendeeEmail)) } returns mapOf(
                Pair(attendeeEmail, attendeeEmail))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(
                Pair(attendeeEmail, attendeeEmail)
            )) } returns mapOf(
                Pair(
                    attendeeEmail,
                    ObtainSendPreferencesUseCase.Result.Error.AddressDisabled
                )
            )

            // Mock calendar member
            coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

            // Handle save use case call with error (empty send prefs so failed to send mail)
            coEvery {
                handleSaveUseCaseMock.handleSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns UseCase.Result.Error("Failed to send mail", UseCase.Error.HandleSave.CreateSendEmail)

            // Send invitation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_title) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_title)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_description) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_description)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_confirm) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_send_invite_dialog_cancel) } returns protonCalendarApplication.getString(R.string.event_send_invite_dialog_cancel)

            // Email with error mock
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
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
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_none_message, emailError) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_none_message, emailError)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_confirm)
            coEvery { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_cancel) } returns protonCalendarApplication.getString(R.string.event_attendees_send_prefs_error_cancel)

            // Success snack
            coEvery { resourceProviderMock.provideString(R.string.snack_event_created_failed_mail) } returns protonCalendarApplication.getString(
                R.string.snack_event_created_failed_mail)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            // Add two attendees
            eventViewModel.handleAttendee(Attendee(attendeeName, attendeeEmail), attendeeEmail, addAttendee = true)

            // Store initialised event
            val event = eventViewModel.eventLiveData.value

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start save
                eventViewModel.onSaveClick(provideDisplayDialog, null, occurrenceNumber, timeFormat.toBoolean())
            }

            // Handle save use case
            coVerify(exactly = 1) {
                handleSaveUseCaseMock.handleSave(
                    editOption = null,
                    occurrenceNumber = occurrenceNumber,
                    timeFormatIs24Hours = timeFormat.toBoolean(),
                    sendPreferences = mapOf(),
                    event = event!!,
                    originalDbEvent = null,
                    userSettings = UserMocks.provideUserSettingsEntity(),
                    eventTimeZoneId = defaultTimezone,
                    userId = userId,
                    rruleManuallyEdited = false,
                    isCreate = true,
                    sendEmailUpdate = null
                )
            }

            // Send invitation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_invite_dialog_cancel) }

            // Send preferences dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_attendees_send_prefs_error_none_message,
                emailError
            ) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_attendees_send_prefs_error_cancel) }

            // Send preferences dialog content
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) }
            verify(exactly = 1) { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                attendeeEmail,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) }

            // Success snack
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.snack_event_created_failed_mail) }

            // Make sure no attendee is left, we remove those that have failed send prefs
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.attendees?.size == 0)

            assert(eventViewModel.eventFormState.value == EventViewModel.EventState.Idle)
            assert(eventViewModel.eventFormSnackState.value == EventViewModel.EventSnackState.DisplaySnackReturnToMonth(
                resourceProviderMock.provideString(
                    R.string.snack_event_created_failed_mail
                ),
                event!!.getStart(defaultTimezone).toLocalDate()
            ))
        }
    }

}
