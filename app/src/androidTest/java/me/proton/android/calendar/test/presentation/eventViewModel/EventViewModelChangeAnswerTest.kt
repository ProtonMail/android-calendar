package me.proton.android.calendar.test.presentation.eventViewModel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.component.VAlarm
import biweekly.parameter.ParticipationStatus
import biweekly.parameter.Related
import biweekly.property.Attendee
import biweekly.property.Trigger
import biweekly.util.Duration
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
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
 * EventViewModel change answer flow tests
 */
@Ignore("Those tests have not been maintained. Update them following recent changes.")
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class EventViewModelChangeAnswerTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * For external invite
     */
    @Test
    fun changeAnswerExternalEventTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isAttendee = true,
                participationStatus = ParticipationStatus.DECLINED,
                hasDefaultAlarms = false
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse()
                )
            )

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.iCalEvent.addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START), null))

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.ACCEPTED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = null,
                isProtonProtonInvite = false,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(eventCopy.iCalendar)
            val personalPartICalString = calendarSplit.personalPart?.printToString()
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = eventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.ACCEPTED.toInt(),
                personalPartICalString = personalPartICalString,
                updateTime = any(), // updateTime = Instant.now()
                notifications = null
            ) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.alarms?.size != 0) // Check that alarms were added
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.ACCEPTED, false))
        }
    }

    /**
     * For external invite, failed to send mail
     */
    @Test
    fun changeAnswerExternalEventSendMailErrorTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isAttendee = true,
                participationStatus = ParticipationStatus.DECLINED,
                hasDefaultAlarms = false
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Error("Failed to send mail")

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse()
                )
            )

            // Error snack
            coEvery { resourceProviderMock.provideString(R.string.snack_change_attendee_answer_error) } returns protonCalendarApplication.getString(R.string.snack_change_attendee_answer_error)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.iCalEvent.addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START), null))

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.ACCEPTED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = null,
                isProtonProtonInvite = false,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }

            // Part stat is not updated for external if mail failed to be sent
            coVerify(exactly = 0) { updateParticipationStatusUseCaseMock.execute(any(), any(), any(), any(), any(), any(), any()) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.DECLINED, false))
            assert(eventViewModel.eventDetailsSnackState.value == EventViewModel.EventSnackState.DisplaySnack(
                resourceProviderMock.provideString(R.string.snack_change_attendee_answer_error)
            ))
        }
    }

    /**
     * For external invite, with send prefs error
     */
    @Test
    fun changeAnswerExternalEventSendPrefsErrorTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isAttendee = true,
                participationStatus = ParticipationStatus.DECLINED,
                hasDefaultAlarms = false
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(organizerEmail, ObtainSendPreferencesUseCase.Result.Error.AddressDisabled))

            // Email with error mock
            val errorMessage = protonCalendarApplication.getString(R.string.event_organizer_send_prefs_message_accepted_title)
            coEvery { resourceProviderMock.provideString(R.string.event_organizer_send_prefs_message_accepted_title) } returns errorMessage
            coEvery { resourceProviderMock.provideString(R.string.event_send_prefs_error_address_disabled) } returns protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            val emailError = protonCalendarApplication.getString(
                R.string.event_send_prefs_error_template,
                errorMessage,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            )

            // Send preferences dialog
            coEvery { resourceProviderMock.provideString(R.string.event_organizer_send_prefs_error_title) } returns protonCalendarApplication.getString(R.string.event_organizer_send_prefs_error_title)
            coEvery { resourceProviderMock.provideString(
                R.string.event_send_prefs_error_template,
                errorMessage,
                protonCalendarApplication.getString(R.string.event_send_prefs_error_address_disabled)
            ) } returns emailError
            coEvery { resourceProviderMock.provideString(R.string.event_organizer_send_prefs_button_title) } returns protonCalendarApplication.getString(R.string.event_organizer_send_prefs_button_title)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            coVerify(exactly = 0) { sendEmailUseCaseMock.sendReplyToOrganizer(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { updateParticipationStatusUseCaseMock.execute(any(), any(), any(), any(), any(), any(), any()) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.DECLINED, false))
        }
    }

    /**
     * For proton to proton invite
     */
    @Test
    fun changeAnswerProtonToProtonEventTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isAttendee = true,
                isProtonProtonInvite = true,
                hasHiddenCalendar = true,
                participationStatus = ParticipationStatus.DECLINED,
                hasDefaultAlarms = false
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse()
                )
            )

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.iCalEvent.addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START), null))

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.ACCEPTED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = EventMocks.provideEventResponse().toEventEntity(),
                isProtonProtonInvite = true,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(eventCopy.iCalendar)
            val personalPartICalString = calendarSplit.personalPart?.printToString()
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = eventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.ACCEPTED.toInt(),
                personalPartICalString = personalPartICalString,
                updateTime = any(), // updateTime = Instant.now()
                notifications = null
            ) }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.alarms?.size != 0) // Check that alarms were added
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.ACCEPTED, false))
        }
    }

    /**
     * For proton to proton recurring invite
     */
    @Test
    fun changeAnswerProtonToProtonRecurringEventTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isRecurring = true,
                isAttendee = true,
                isProtonProtonInvite = true,
                hasHiddenCalendar = true,
                participationStatus = ParticipationStatus.DECLINED,
                hasDefaultAlarms = false
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse()
                )
            )

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf()

            // Confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_title)
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_description) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_description)
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.iCalEvent.addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START), null))

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.ACCEPTED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = EventMocks.provideEventResponse().toEventEntity(),
                isProtonProtonInvite = true,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(eventCopy.iCalendar)
            val personalPartICalString = calendarSplit.personalPart?.printToString()
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = eventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.ACCEPTED.toInt(),
                personalPartICalString = personalPartICalString,
                updateTime = any(), // updateTime = Instant.now()
                notifications = null
            ) }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            // Confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.alarms?.size != 0) // Check that alarms were added
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.ACCEPTED, false))
        }
    }

    /**
     * For proton to proton recurring invite with a single edit
     */
    @Test
    fun changeAnswerProtonToProtonRecurringEventWithSingleEditTest() {
        runBlocking {

            // Mock event with user as attendee
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(
                isRecurring = true,
                isAttendee = true,
                isProtonProtonInvite = true,
                hasHiddenCalendar = true,
                participationStatus = ParticipationStatus.DECLINED,
                hasDefaultAlarms = false
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, eventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse()
                )
            )

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf(
                EventMocks.provideEvent(isSingleEdit = true, isAttendee = true, participationStatus = ParticipationStatus.TENTATIVE, isProtonProtonInvite = true, hasHiddenCalendar = true)
            )

            // Confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_title)
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_overwrite_description) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_overwrite_description)
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            val occurrenceNumber = 1
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.ACCEPTED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.iCalEvent.addAlarm(VAlarm.display(Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START), null))

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.DECLINED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.ACCEPTED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = EventMocks.provideEventResponse().toEventEntity(),
                isProtonProtonInvite = true,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(eventCopy.iCalendar)
            val personalPartICalString = calendarSplit.personalPart?.printToString()
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = eventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.ACCEPTED.toInt(),
                personalPartICalString = personalPartICalString,
                updateTime = any(), // updateTime = Instant.now()
                notifications = null
            ) }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            // Confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_overwrite_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.alarms?.size != 0) // Check that alarms were added
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.ACCEPTED, false))
        }
    }

    /**
     * For proton to proton single edit invite
     */
    @Test
    fun changeAnswerProtonToProtonSingleEditEventTest() {
        runBlocking {

            // Mock single edit event with user as attendee
            coEvery { calendarsRepositoryMock.selectEventEntity(singleEditEventId) } returns EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity()
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity())
            } } returns EventMocks.provideEvent(
                isSingleEdit = true,
                isAttendee = true,
                isProtonProtonInvite = true,
                hasHiddenCalendar = true,
                participationStatus = ParticipationStatus.ACCEPTED,
                hasDefaultAlarms = true
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, singleEditEventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse(isSingleEdit = true)
                )
            )

            // Mock single edits info (no single edits)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf()

            coEvery { calendarsRepositoryMock.isOrphanSingleEdit(userId, eventUid) } returns false

            // Confirmation dialog
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_title)
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_single_edit_description) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_single_edit_description)
            coEvery { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) } returns protonCalendarApplication.getString(R.string.event_change_answer_recurring_confirm)
            coEvery { resourceProviderMock.provideString(R.string.dialog_button_cancel) } returns protonCalendarApplication.getString(R.string.dialog_button_cancel)

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = singleEditEventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.DECLINED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.clearAlarms()

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.ACCEPTED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.DECLINED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity(),
                isProtonProtonInvite = true,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = singleEditEventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.DECLINED.toInt(),
                personalPartICalString = "",
                updateTime = any(), // updateTime = Instant.now()
                notifications = null
            ) }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            // Confirmation dialog
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_single_edit_description) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) }
            verify(exactly = 1) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.alarms?.size == 0) // Check that alarms were cleared
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.DECLINED, false))
        }
    }

    /**
     * For proton to proton orphan single edit invite
     */
    @Test
    fun changeAnswerProtonToProtonOrphanSingleEditEventTest() {
        runBlocking {

            // Mock single edit event with user as attendee
            coEvery { calendarsRepositoryMock.selectEventEntity(singleEditEventId) } returns EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity()
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity())
            } } returns EventMocks.provideEvent(
                isSingleEdit = true,
                isAttendee = true,
                isProtonProtonInvite = true,
                hasHiddenCalendar = true,
                participationStatus = ParticipationStatus.ACCEPTED,
                hasDefaultAlarms = true
            )

            // Get address for current user
            coEvery { userManagerMock.getAddresses(userId) } returns listOf(UserMocks.provideUserAddress())

            // Get canonical and send preferences for organizer
            coEvery { getCanonicalEmailsUseCaseMock.invoke(userId, listOf(organizerEmail)) } returns mapOf(Pair(
                organizerEmail, organizerEmail
            ))
            coEvery { obtainSendPreferencesUseCaseMock.execute(userId, mapOf(Pair(organizerEmail, organizerEmail))) } returns mapOf(
                Pair(
                    organizerEmail, ObtainSendPreferencesUseCase.Result.Success(
                        sendPreferences = UserMocks.provideSendPreferences()
                    ))
            )

            // Send reply email
            coEvery { sendEmailUseCaseMock.sendReplyToOrganizer(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Set participation status on server
            coEvery { updateParticipationStatusUseCaseMock.execute(
                any(), any(), any(), any(), any(), any(), any()
            ) } returns UseCase.Result.Success<Unit>()

            // Display calendar if it was hidden
            coEvery { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) } just Runs
            coEvery { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) } returns UseCase.Result.Success<Unit>()

            // Fetch event by id if event.isProtonProtonInvite == null || event.isProtonProtonInvite == true
            coEvery { calendarsRepositoryMock.fetchEventById(userId, calendarId, singleEditEventId) } returns ApiResponse.Success(
                EventApiResponse(
                    event = EventMocks.provideEventResponse(isSingleEdit = true)
                )
            )

            // Mock orphan single edit
            coEvery { calendarsRepositoryMock.isOrphanSingleEdit(userId, eventUid) } returns true

            val occurrenceNumber = 0
            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = singleEditEventId,
                occurrenceNumber = occurrenceNumber,
                initStartDate = null,
                initStartTime = null
            )

            val eventCopy = Event.from(eventViewModel.eventLiveData.value!!)

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.ACCEPTED)

            val provideDisplayDialog = provideDisplayDialog()

            withContext(Dispatchers.Default) {
                // Start change answer
                eventViewModel.onChangeAnswerClick(provideDisplayDialog, ParticipationStatus.DECLINED, timeFormat.toBoolean())
            }

            // Default alarms will be added when changing answer from Declined to Accepted. Need to add it to the copy to match the event in sendReplyToOrganizer.
            eventCopy.clearAlarms()

            val userAttendee = Attendee(userName, userEmail)
            userAttendee.participationStatus = ParticipationStatus.ACCEPTED
            coVerify(exactly = 1) { sendEmailUseCaseMock.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = any(), // TODO
                userAttendee = userAttendee,
                organizerEmail = organizerEmail,
                participationStatus = ParticipationStatus.DECLINED,
                sendPreferences = mapOf(Pair(organizerEmail, UserMocks.provideSendPreferences())),
                dtStamp = any(), // updateTime = Instant.now()
                eventEntity = EventMocks.provideEventResponse(isSingleEdit = true).toEventEntity(),
                isProtonProtonInvite = true,
                defaultTimeZone = defaultTimezone,
                timeFormatIs24Hours = timeFormat.toBoolean()
            ) }
            coVerify(exactly = 1) { updateParticipationStatusUseCaseMock.execute(
                userId = userId,
                calendarId = calendarId,
                eventId = singleEditEventId,
                attendeeId = attendeeId,
                status = ParticipationStatus.DECLINED.toInt(),
                personalPartICalString = "",
                updateTime = any(), // updateTime = Instant.now()
                notifications = null
            ) }

            // Update calendar display
            coVerify(exactly = 1) { calendarsRepositoryMock.updateCalendarDisplay(calendarId, true) }
            coVerify(exactly = 1) { updateCalendarUseCaseMock.executeUpdateDisplayFromDb(userId, calendarId) }

            // Make sure we skip confirmation dialog
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_title) }
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.event_change_answer_recurring_confirm) }
            verify(exactly = 0) { resourceProviderMock.provideString(R.string.dialog_button_cancel) }

            assert(eventViewModel.eventLiveData.value?.getParticipationStatus(listOf(userEmail)) == ParticipationStatus.DECLINED)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.alarms?.size == 0) // Check that alarms were cleared
            assert(eventViewModel.attendeeAnswerState.value == Pair(ParticipationStatus.DECLINED, false))
        }
    }
}
