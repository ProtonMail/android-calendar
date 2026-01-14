package me.proton.android.calendar.test.presentation.calendarFormViewModel

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.Duration
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.android.calendar.test.shared.mocks.CalendarMocks
import me.proton.android.calendar.test.shared.mocks.calendarColor
import me.proton.android.calendar.test.shared.mocks.calendarId
import me.proton.android.calendar.test.shared.mocks.calendarName
import me.proton.android.calendar.test.shared.mocks.userEmail
import me.proton.android.calendar.test.shared.mocks.userId
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinComponent


/**
 * CalendarFormViewModel update flow tests
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
internal class CalendarFormViewModelUpdateTest : KoinComponent, CalendarFormViewModelTestCommon() {

    @Test
    fun initViewModelUpdateTest() = runBlocking {

        // Mock calendar
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.provideCalendarSettingsEntity()

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        coVerify(exactly = 1) {
            accountManagerMock.getPrimaryUserId()
        }

        coVerify(exactly = 1) {
            calendarsRepositoryMock.selectCalendar(calendarId)
        }

        coVerify(exactly = 1) {
            calendarsRepositoryMock.selectCalendarSettings(calendarId)
        }

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        assert(calendarFormViewModel.calendarName.value == calendarName)

        assert(calendarFormViewModel.calendarColor.value == calendarColor)

        assert(calendarFormViewModel.calendarEmail.value == userEmail)

        assert(calendarFormViewModel.defaultEventDuration.value == CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.first())

        assert(calendarFormViewModel.defaultAllDayAlarms.value?.size == 1)
        assert(calendarFormViewModel.defaultAllDayAlarms.value?.first() == CalendarForm.DEFAULT_ALL_DAY_ALARM)

        assert(calendarFormViewModel.defaultPartDayAlarms.value?.size == 1)
        assert(calendarFormViewModel.defaultPartDayAlarms.value?.first() == CalendarForm.DEFAULT_PART_DAY_ALARM)

    }

    @Test
    fun initViewModelUpdateFailedToGetUserIdTest() = runBlocking {

        coEvery { accountManagerMock.getPrimaryUserId() } returns flowOf(null)
        coEvery { resourceProviderMock.provideString(R.string.snack_calendar_init_error) } returns
                protonCalendarApplication.getString(R.string.snack_calendar_init_error)

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        coVerify(exactly = 1) {
            accountManagerMock.getPrimaryUserId()
        }

        coVerify(exactly = 0) {
            calendarsRepositoryMock.selectCalendar(calendarId)
        }

        coVerify(exactly = 0) {
            calendarsRepositoryMock.selectCalendarSettings(calendarId)
        }

        coVerify(exactly = 0) {
            calendarsRepositoryMock.selectCalendarMembers(calendarId)
        }

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarSettingsSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(
            protonCalendarApplication.getString(R.string.snack_calendar_init_error)
        ))
        assert(calendarFormViewModel.calendarFormSnackState.value == null)

    }

    @Test
    fun initViewModelUpdateFailedToGetCalendarTest() = runBlocking {

        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns null
        coEvery { resourceProviderMock.provideString(R.string.snack_calendar_init_error) } returns
                protonCalendarApplication.getString(R.string.snack_calendar_init_error)

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        coVerify(exactly = 1) {
            accountManagerMock.getPrimaryUserId()
        }

        coVerify(exactly = 1) {
            calendarsRepositoryMock.selectCalendar(calendarId)
        }

        coVerify(exactly = 0) {
            calendarsRepositoryMock.selectCalendarSettings(calendarId)
        }

        coVerify(exactly = 0) {
            calendarsRepositoryMock.selectCalendarMembers(calendarId)
        }

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarSettingsSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(
            protonCalendarApplication.getString(R.string.snack_calendar_init_error)
        ))
        assert(calendarFormViewModel.calendarFormSnackState.value == null)

    }

    @Test
    fun initViewModelUpdateFailedToGetCalendarSettingsTest() = runBlocking {

        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns null
        coEvery { resourceProviderMock.provideString(R.string.snack_calendar_init_error) } returns
                protonCalendarApplication.getString(R.string.snack_calendar_init_error)

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        coVerify(exactly = 1) {
            accountManagerMock.getPrimaryUserId()
        }

        coVerify(exactly = 1) {
            calendarsRepositoryMock.selectCalendar(calendarId)
        }

        coVerify(exactly = 1) {
            calendarsRepositoryMock.selectCalendarSettings(calendarId)
        }

        coVerify(exactly = 0) {
            calendarsRepositoryMock.selectCalendarMembers(calendarId)
        }

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarSettingsSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(
            protonCalendarApplication.getString(R.string.snack_calendar_init_error)
        ))
        assert(calendarFormViewModel.calendarFormSnackState.value == null)

    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun updateCalendarTest() = runBlocking {

        val customCalendarColor = "#abcdef"
        val customCalendarName = "custom calendar name"
        val customDefaultEventDuration = CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.last()
        val customAllDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).hours(20).build(), Related.START), null)

        val customPartDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).minutes(10).build(), Related.START), null)

        val successSnackText = protonCalendarApplication.getString(
            R.string.snack_update_calendar_success)
        coEvery { resourceProviderMock.provideString(R.string.snack_update_calendar_success) } returns successSnackText

        // Mock calendar
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.provideCalendarSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

        coEvery { updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = customCalendarName, color = customCalendarColor) } returns UseCase.Result.Success<Unit>()
        coEvery { updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm)) } returns UseCase.Result.Success<Unit>()

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarColor(customCalendarColor)
        calendarFormViewModel.handleCalendarName(customCalendarName)

        calendarFormViewModel.handleDefaultEventDuration(customDefaultEventDuration)

        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        // add custom alarms
        calendarFormViewModel.handleAlarmChange(customAllDayAlarm, true)
        calendarFormViewModel.handleAlarmChange(customPartDayAlarm, false)

        assert(calendarFormViewModel.hasFormBeenEdited())
        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarSettingsSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp(successSnackText))
        assert(calendarFormViewModel.calendarFormSnackState.value == null)

        coVerify(exactly = 1) {
            updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = customCalendarName, color = customCalendarColor)
        }
        coVerify(exactly = 1) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm))
        }
    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun updateCalendarErrorTest() = runBlocking {

        val customCalendarColor = "#abcdef"
        val customCalendarName = "custom calendar name"
        val customDefaultEventDuration = CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.last()
        val customAllDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).hours(20).build(), Related.START), null)

        val customPartDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).minutes(10).build(), Related.START), null)

        val errorSnackText = protonCalendarApplication.getString(
            R.string.snack_update_calendar_error)
        coEvery { resourceProviderMock.provideString(R.string.snack_update_calendar_error) } returns errorSnackText

        // Mock calendar
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.provideCalendarSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

        coEvery { updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = customCalendarName, color = customCalendarColor) } returns UseCase.Result.Error("Test")

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarColor(customCalendarColor)
        calendarFormViewModel.handleCalendarName(customCalendarName)

        calendarFormViewModel.handleDefaultEventDuration(customDefaultEventDuration)

        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        // add custom alarms
        calendarFormViewModel.handleAlarmChange(customAllDayAlarm, true)
        calendarFormViewModel.handleAlarmChange(customPartDayAlarm, false)

        assert(calendarFormViewModel.hasFormBeenEdited())
        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnack(errorSnackText))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

        coVerify(exactly = 1) {
            updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = customCalendarName, color = customCalendarColor)
        }
        coVerify(exactly = 0) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm))
        }
    }

    @Ignore("This test has not been maintained. Update them following recent changes.")
    @Test
    fun updateCalendarSettingsErrorTest() = runBlocking {

        val customCalendarColor = "#abcdef"
        val customCalendarName = "custom calendar name"
        val customDefaultEventDuration = CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.last()
        val customAllDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).hours(20).build(), Related.START), null)

        val customPartDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).minutes(10).build(), Related.START), null)

        val errorSnackText = protonCalendarApplication.getString(
            R.string.snack_update_calendar_settings_error)
        coEvery { resourceProviderMock.provideString(R.string.snack_update_calendar_settings_error) } returns errorSnackText

        // Mock calendar
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.provideCalendarSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

        coEvery { updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = customCalendarName, color = customCalendarColor) } returns UseCase.Result.Success<Unit>()
        coEvery { updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm)) } returns UseCase.Result.Error("Test")

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleCalendarColor(customCalendarColor)
        calendarFormViewModel.handleCalendarName(customCalendarName)

        calendarFormViewModel.handleDefaultEventDuration(customDefaultEventDuration)

        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        // add custom alarms
        calendarFormViewModel.handleAlarmChange(customAllDayAlarm, true)
        calendarFormViewModel.handleAlarmChange(customPartDayAlarm, false)

        assert(calendarFormViewModel.hasFormBeenEdited())
        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnack(errorSnackText))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

        coVerify(exactly = 1) {
            updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = customCalendarName, color = customCalendarColor)
        }
        coVerify(exactly = 1) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm))
        }
    }

    @Test
    fun updateOnlyCalendarSettingsErrorTest() = runBlocking {

        val customDefaultEventDuration = CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.last()
        val customAllDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).hours(20).build(), Related.START), null)

        val customPartDayAlarm =
            VAlarm.display(Trigger(Duration.builder().prior(true).minutes(10).build(), Related.START), null)

        val errorSnackText = protonCalendarApplication.getString(
            R.string.snack_update_calendar_error)
        coEvery { resourceProviderMock.provideString(R.string.snack_update_calendar_error) } returns errorSnackText

        // Mock calendar
        coEvery { calendarsRepositoryMock.selectCalendar(calendarId) } returns CalendarMocks.provideCalendar()
        coEvery { calendarsRepositoryMock.selectCalendarSettings(calendarId) } returns CalendarMocks.provideCalendarSettingsEntity()
        coEvery { calendarsRepositoryMock.selectCalendarMembers(calendarId) } returns listOf(CalendarMocks.provideMemberEntity())

        coEvery { updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm)) } returns UseCase.Result.Error("Test")

        val calendarFormViewModel = getCalendarFormViewModel()

        calendarFormViewModel.initUpdateCalendarForm(calendarId)

        assert(calendarFormViewModel.hasFormBeenEdited() == false)

        calendarFormViewModel.handleDefaultEventDuration(customDefaultEventDuration)

        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_ALL_DAY_ALARM, true, true)
        calendarFormViewModel.handleAlarmChange(CalendarForm.DEFAULT_PART_DAY_ALARM, false, true)

        // add custom alarms
        calendarFormViewModel.handleAlarmChange(customAllDayAlarm, true)
        calendarFormViewModel.handleAlarmChange(customPartDayAlarm, false)

        assert(calendarFormViewModel.hasFormBeenEdited())
        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)

        calendarFormViewModel.handleSaveCalendarForm(returnToSettings = false)

        assert(calendarFormViewModel.calendarFormState.value == CalendarFormViewModel.CalendarFormState.Idle)
        assert(calendarFormViewModel.calendarFormSnackState.value == CalendarFormViewModel.CalendarFormSnackState.DisplaySnack(errorSnackText))
        assert(calendarFormViewModel.calendarSettingsSnackState.value == null)

        coVerify(exactly = 0) {
            updateCalendarUseCaseMock.executeUpdate(userId, calendarId, name = calendarName, color = calendarColor)
        }
        coVerify(exactly = 1) {
            updateCalendarSettingsUseCaseMock.updateCalendarSettings(userId, calendarId, customDefaultEventDuration, listOf(customPartDayAlarm), listOf(customAllDayAlarm))
        }
    }


}

