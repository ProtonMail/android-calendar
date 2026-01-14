package me.proton.android.calendar.test.presentation.calendarFormViewModel

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.test.shared.mocks.*
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent

/**
 * CalendarFormViewModel create flow tests
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class CalendarFormViewModelCreateTest : KoinComponent, CalendarFormViewModelTestCommon() {

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun initViewModelCreateTest() = runBlocking {

        val calendarFormViewModel = getCalendarFormViewModel()


        coVerify(exactly = 1) {
            accountManagerMock.getPrimaryUserId()
        }

        coVerify(exactly = 1) {
            userManagerMock.getAddresses(userId)
        }

        coVerify(exactly = 1) {
            userManagerMock.getUser(userId)
        }

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        assert(calendarFormViewModel.calendarColor.value == calendarColor)

        assert(calendarFormViewModel.calendarEmail.value == userEmail)

        assert(calendarFormViewModel.userEmails?.size == 1)
        assert(calendarFormViewModel.userEmails?.equals(listOf(userEmail)) == true)

        assert(calendarFormViewModel.defaultEventDuration.value == CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first())

        assert(calendarFormViewModel.defaultAllDayAlarms.value?.size == 2)
        assert(calendarFormViewModel.defaultAllDayAlarms.value?.first() == CalendarForm.DEFAULT_ALL_DAY_ALARM)

        assert(calendarFormViewModel.defaultPartDayAlarms.value?.size == 2)
        assert(calendarFormViewModel.defaultPartDayAlarms.value?.first() == CalendarForm.DEFAULT_PART_DAY_ALARM)

    }

    @Test
    fun initViewModelCreateFailedToGetUserIdTest() = runBlocking {

        coEvery { accountManagerMock.getPrimaryUserId() } returns flowOf(null)
        coEvery { resourceProviderMock.provideString(R.string.snack_calendar_init_error) } returns
                protonCalendarApplication.getString(R.string.snack_calendar_init_error)

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initCreateCalendarForm(calendarColor)

        coVerify(exactly = 1) {
            accountManagerMock.getPrimaryUserId()
        }

        coVerify(exactly = 0) {
            userManagerMock.getAddresses(userId)
        }

        coVerify(exactly = 0) {
            userManagerMock.getUser(userId)
        }

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(
            protonCalendarApplication.getString(R.string.snack_calendar_init_error)
        ))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun createCalendarTest() = runBlocking {

        val customCalendarColor = "#abcdef"
        val customCalendarEmail = "custom@calendar.email.com"
        val customCalendarName = "custom calendar name"
        val customDefaultEventDuration = CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.last()
        val customAllDayAlarm =
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).hours(20).build(), Related.START), null)

        val customPartDayAlarm =
            VAlarm.display(Trigger(biweekly.util.Duration.builder().prior(true).minutes(10).build(), Related.START), null)

        val successSnackText = protonCalendarApplication.getString(
            R.string.snack_create_calendar_success)
        coEvery { resourceProviderMock.provideString(R.string.snack_create_calendar_success) } returns successSnackText
        val existingAlarmSnackText = protonCalendarApplication.getString(
            R.string.snack_notification_already_added)
        coEvery { resourceProviderMock.provideString(R.string.snack_notification_already_added) } returns existingAlarmSnackText

        coEvery { createCalendarsUseCaseMock.execute(userId, customCalendarName, "", customCalendarColor, 1, customCalendarEmail) } returns UseCase.Result.Success(calendarId)
        coEvery { updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM, customPartDayAlarm), listOf(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM, customAllDayAlarm)) } returns UseCase.Result.Success<Unit>()

        coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar())

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initCreateCalendarForm(calendarColor)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarColor(customCalendarColor)
        calendarFormViewModel.handleCalendarEmail(customCalendarEmail)
        calendarFormViewModel.handleCalendarName(customCalendarName)

        calendarFormViewModel.handleDefaultEventDuration(customDefaultEventDuration)

        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        // add custom alarms
        calendarFormViewModel.handleAlarmChange(customAllDayAlarm, true)
        calendarFormViewModel.handleAlarmChange(customPartDayAlarm, false)

        // Test add already existing alarms
        calendarFormViewModel.handleAlarmChange(customAllDayAlarm, true)
        calendarFormViewModel.handleAlarmChange(customPartDayAlarm, false)

        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnack(existingAlarmSnackText))

        // Reset calendarFormSnackState
        calendarFormViewModel.calendarFormSnackState.value = null

        assert(calendarFormViewModel.hasFormBeenEdited())
        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(successSnackText))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

        coVerify(exactly = 1) {
            createCalendarsUseCaseMock.execute(userId, name = customCalendarName, color = customCalendarColor, email = customCalendarEmail)
        }
        coVerify(exactly = 1) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM, customPartDayAlarm), listOf(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM, customAllDayAlarm))
        }

        // Test resetFormValues
        calendarFormViewModel.resetFormValues()
        assert(calendarFormViewModel.calendarName.value == "")
        assert(calendarFormViewModel.calendarColor.value == "")
        assert(calendarFormViewModel.calendarEmail.value == "")
        assert(calendarFormViewModel.defaultEventDuration.value == CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first())
        assert(calendarFormViewModel.defaultPartDayAlarms.value == arrayListOf<VAlarm>())
        assert(calendarFormViewModel.defaultAllDayAlarms.value == arrayListOf<VAlarm>())
        assert(calendarFormViewModel.calendarFormSnackState.value == null)
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)
        assert(calendarFormViewModel.userEmails == null)
        assert(!calendarFormViewModel.hasFormBeenEdited())
    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun createCalendarReturnToSettingsTest() = runBlocking {

        val successSnackText = protonCalendarApplication.getString(
            R.string.snack_create_calendar_success)
        coEvery { resourceProviderMock.provideString(R.string.snack_create_calendar_success) } returns successSnackText

        coEvery { createCalendarsUseCaseMock.execute(userId, calendarName, "", calendarColor, 1, userEmail) } returns UseCase.Result.Success(calendarId)
        coEvery { updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first(), listOf(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM), listOf(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM)) } returns UseCase.Result.Success<Unit>()

        coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar())

        val calendarFormViewModel = getCalendarFormViewModel()


        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarName(calendarName)

        // Remove alarms
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        assert(calendarFormViewModel.hasFormBeenEdited())
        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = true)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarSettingsSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(
            successSnackText
        ))
        assert(calendarFormViewModel.calendarFormSnackState.value == null)

        coVerify(exactly = 1) {
            createCalendarsUseCaseMock.execute(userId, name = calendarName, color = calendarColor, email = userEmail)
        }
        coVerify(exactly = 1) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first(), listOf(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM), listOf(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM))
        }
    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun createCalendarErrorTest() = runBlocking {

        coEvery { resourceProviderMock.provideString(R.string.snack_create_calendar_error) } returns
                protonCalendarApplication.getString(R.string.snack_create_calendar_error)

        coEvery { createCalendarsUseCaseMock.execute(userId, calendarName, "", calendarColor, 1, userEmail) } returns UseCase.Result.Error("Test")

        coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar())

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initCreateCalendarForm(calendarColor)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarName(calendarName)

        assert(calendarFormViewModel.hasFormBeenEdited() == true)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnack(
            protonCalendarApplication.getString(R.string.snack_create_calendar_error)
        ))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

        assert(calendarFormViewModel._calendarId == null)

        coVerify(exactly = 1) {
            createCalendarsUseCaseMock.execute(userId, name = calendarName, color = calendarColor, email = userEmail)
        }
        coVerify(exactly = 0) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, any(), any(), any())
        }
    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun createCalendarUpdateSettingsErrorTest() = runBlocking {

        coEvery { resourceProviderMock.provideString(R.string.snack_create_calendar_settings_error) } returns
                protonCalendarApplication.getString(R.string.snack_create_calendar_settings_error)

        coEvery { createCalendarsUseCaseMock.execute(userId, calendarName, "", calendarColor, 1, userEmail) } returns UseCase.Result.Success(calendarId)
        coEvery { updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first(), listOf(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM), listOf(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM)) } returns UseCase.Result.Error("Test")

        coEvery { calendarsRepositoryMock.selectActiveUserCalendars(userId.id) } returns listOf(CalendarMocks.provideCalendar())

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initCreateCalendarForm(calendarColor)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarName(calendarName)

        // Remove alarms
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        assert(calendarFormViewModel.hasFormBeenEdited() == true)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnack(
            protonCalendarApplication.getString(R.string.snack_create_calendar_settings_error)
        ))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

        assert(calendarFormViewModel._calendarId == calendarId)

        coVerify(exactly = 1) {
            createCalendarsUseCaseMock.execute(userId, name = calendarName, color = calendarColor, email = userEmail)
        }
        coVerify(exactly = 1) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first(), listOf(CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM), listOf(CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM))
        }
    }

}
