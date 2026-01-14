package me.proton.android.calendar.test.presentation.eventViewModel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.util.Frequency
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.test.shared.mocks.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

@RunWith(AndroidJUnit4::class)
@LargeTest
open class EventViewModelTest: KoinComponent, EventViewModelTestCommon() {

    /**
     * EventViewModel.initialise tests
     */

    /**
     * Initialise EventVM for creating an all day event
     */
    @Test
    fun initialiseCreateAllDayEventTest() {
        runBlocking {

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            val startDate = ZonedDateTime.of(
                LocalDate.now(),
                LocalTime.of(0, 0),
                ZoneId.of(defaultTimezone)
            )

            assert(eventViewModel.eventLiveData.value?.getStart(defaultTimezone) == startDate)
            assert(eventViewModel.eventLiveData.value?.getStart(defaultTimezone) == startDate)
            assert(eventViewModel.eventLiveData.value?.getEnd(defaultTimezone) == startDate)

            assert(eventViewModel.eventLiveData.value?.isAllDay() == true)

        }
    }

    /**
     * Initialise EventVM for creating a part day event
     */
    @Test
    fun initialiseCreatePartDayEventTest() {
        runBlocking {

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = 0,
                initStartDate = "2021-10-01",
                initStartTime = "12:15:28.054"
            )

            val startDate = LocalDate.of(2021, 10, 1)
            val startTime = LocalTime.of(12, 15)
            val endTime = LocalTime.of(12, 45)

            assert(eventViewModel.eventLiveData.value?.getStart(defaultTimezone)?.toLocalDate() == startDate)
            assert(eventViewModel.eventLiveData.value?.getStart(defaultTimezone)?.toLocalTime() == startTime)
            assert(eventViewModel.eventLiveData.value?.getEnd(defaultTimezone)?.toLocalTime() == endTime)

            assert(eventViewModel.eventLiveData.value?.isAllDay() == false)

        }
    }

    /**
     * Initialise EventVM for opening details of existing event
     */
    @Test
    fun initialiseViewEventDetailsTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent()

            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )
        }
    }

    /**
     * Initialise EventVM for editing an event
     */
    @Test
    fun initialiseEditEventTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent()

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = eventId,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )
        }
    }

    /**
     * Initialise EventVM for editing an event with no default calendar
     */
    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun initialiseCreateEventNoDefaultCalendarTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent()

            // No default calendar
            coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns null

            // Fallback calendar
            val fallbackCalendarId = "fallbackCalendarId"
            coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar(id = fallbackCalendarId))

            // Fallback calendar settings
            coEvery { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) } returns CalendarMocks.provideCalendarSettingsEntity(id = fallbackCalendarId)

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) }

            assert(eventViewModel.calendarSettings.calendarId == fallbackCalendarId)
        }
    }

    /**
     * Initialise EventVM for editing an event with disabled default calendar
     */
    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun initialiseCreateEventDisabledDefaultCalendarTest() {
        runBlocking {

            // Mock event
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent()

            // No default calendar
            val calendar = CalendarMocks.provideCalendar(isDisabled = true)
            coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns calendar

            // Fallback calendar
            val fallbackCalendarId = "fallbackCalendarId"
            coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar(id = fallbackCalendarId))

            // Fallback calendar settings
            coEvery { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) } returns CalendarMocks.provideCalendarSettingsEntity(id = fallbackCalendarId)

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = null,
                occurrenceNumber = 0,
                initStartDate = null,
                initStartTime = null
            )

            coVerify(exactly = 1) { calendarsRepositoryMock.selectCalendarSettings(fallbackCalendarId) }

            assert(eventViewModel.calendarSettings.calendarId == fallbackCalendarId)
        }
    }

    /**
     * Initialise EventVM for editing a single edit
     */
    @Test
    fun initialiseEditSingleEditTest() {
        runBlocking {

            // Mock single edit event
            val singleEditEventEntity = EventMocks.provideEventResponse(isSingleEdit = true)
            val singleEditEvent = EventMocks.provideEvent(isSingleEdit = true)
            coEvery { calendarsRepositoryMock.selectEventEntity(singleEditEventId) } returns singleEditEventEntity.toEventEntity()
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(singleEditEventEntity.toEventEntity())
            } else {
                transformEventUseCaseMock.execute(singleEditEventEntity.toEventEntity())
            } } returns singleEditEvent

            // Mock root event
            val rootEventEntity = EventMocks.provideEventResponse()
            val rootEvent = EventMocks.provideEvent(isRecurring = true)
            coEvery { calendarsRepositoryMock.selectRootEventEntity(eventUid) } returns rootEventEntity.toEventEntity()
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(rootEventEntity.toEventEntity())
            } else {
                transformEventUseCaseMock.execute(rootEventEntity.toEventEntity())
            } } returns rootEvent

            val eventViewModel = getInitialisedEventViewModel(
                editMode = true,
                eventId = singleEditEventId,
                occurrenceNumber = 2,
                initStartDate = null,
                initStartTime = null
            )

            // Test: Clone RRule from original event in DB if we are in edit mode
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value != null)
            assert(eventViewModel.eventLiveData.value?.iCalEvent?.recurrenceRule?.value?.frequency == Frequency.DAILY)

            assert(eventViewModel.eventLiveData.value?.id == singleEditEventId)

        }
    }

    /**
     * EventViewModel.getSingleEditsInfo tests
     */

    /**
     * Get single edits info for event with one declined future non cancelled single edit
     */
    @Test
    fun getSingleEditsInfoTest() {
        runBlocking {

            // Mock event with attendee (user as organizer)
            coEvery { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptorMock.decrypt(EventMocks.provideEventResponse().toEventEntity())
            } else {
                transformEventUseCaseMock.execute(EventMocks.provideEventResponse().toEventEntity())
            } } returns EventMocks.provideEvent(isRecurring = true, isOrganizer = true)

            // Mock single edit with attendee (user as organizer)
            coEvery { calendarsRepositoryMock.getSingleEdits(userId, eventUid, null, null) } returns listOf(
                EventMocks.provideEvent(isOrganizer = true, isSingleEdit = true)
            )

            val eventViewModel = getInitialisedEventViewModel(
                editMode = false,
                eventId = eventId,
                occurrenceNumber = 1,
                initStartDate = null,
                initStartTime = null
            )

            val singleEditsInfo = eventViewModel.getSingleEditsInfo(listOf(attendeeEmail))

            assert(singleEditsInfo?.singleEdits?.size == 1)
            assert(singleEditsInfo?.hasSingleEdit == true)
            assert(singleEditsInfo?.hasAnsweredSingleEdit == true)
            assert(singleEditsInfo?.hasFutureSingleEdit == true)
            assert(singleEditsInfo?.hasNonCancelledSingleEdit == true)
        }
    }
}
