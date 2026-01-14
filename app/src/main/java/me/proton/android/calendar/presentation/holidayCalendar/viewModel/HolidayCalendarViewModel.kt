package me.proton.android.calendar.presentation.holidayCalendar.viewModel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import androidx.work.WorkManager
import biweekly.component.VAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.getMatchingDefaultHolidayCalendar
import me.proton.android.calendar.common.worker.FetchCachedViewsEventsWorker
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.usecase.JoinCalendarUseCase
import me.proton.android.calendar.domain.usecase.LeaveManagedCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class HolidayCalendarViewModel @Inject constructor(
    application: Application,
    private val resourceProvider: ResourceProvider,
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val accountManager: AccountManager,
    private val joinCalendarUseCase: JoinCalendarUseCase,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val leaveManagedCalendarUseCase: LeaveManagedCalendarUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val workManager: WorkManager
) : AndroidViewModel(application) {

    sealed class HolidayCalendarSnackState {

        data class DisplaySnack(
            val message: String,
        ): HolidayCalendarSnackState()

        data class DisplaySnackNavigateUp(
            val message: String,
        ): HolidayCalendarSnackState()
    }

    val holidayCalendarSnackState: MutableStateFlow<HolidayCalendarSnackState?> = MutableStateFlow(null)
    val calendarSettingsSnackState: MutableStateFlow<HolidayCalendarSnackState?> = MutableStateFlow(null)

    sealed class HolidayCalendarState {

        object Idle: HolidayCalendarState()
        object AlreadyExists: HolidayCalendarState()
        object PickBasedOnTimeZone: HolidayCalendarState()

        sealed class Processing: HolidayCalendarState() {
            object Saving: Processing()
        }
    }

    val holidayCalendarState: MutableStateFlow<HolidayCalendarState> = MutableStateFlow(HolidayCalendarState.Idle)

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private val _country = MutableLiveData("")
    val country: LiveData<String> = _country

    private val _calendarColor = MutableLiveData<String>()
    val calendarColor: LiveData<String> = _calendarColor

    private val _language = MutableLiveData<String>()
    val language: LiveData<String> = _language

    private val _defaultAllDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultAllDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultAllDayAlarms

    private val _holidayCalendars: MutableLiveData<List<ManagedHolidayCalendarEntity>> = MutableLiveData()
    val holidayCalendars: LiveData<List<ManagedHolidayCalendarEntity>> = _holidayCalendars

    var fetchedHolidayCalendars = false

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var _calendar: Calendar? = null

    private var calendarEdited = false

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun resetValues() {
        _country.value = ""
        _language.value = ""
        _calendarColor.value = ""
        _defaultAllDayAlarms.value = arrayListOf()
        _calendar = null
        calendarEdited = false
        holidayCalendarSnackState.value = null
        calendarSettingsSnackState.value = null
        fetchedHolidayCalendars = false
        holidayCalendarState.value = HolidayCalendarState.Idle
    }

    suspend fun initUpdateHolidayCalendar(calendarId: String) {

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in HolidayCalendarViewModel initUpdateHolidayCalendar")
            displayInitErrorSnack(true)
            return
        }
        _userId.value = userId

        val calendar = calendarsRepository.selectCalendar(calendarId) ?: run {
            logger.e("Calendar was null in initUpdateHolidayCalendar")
            displayInitErrorSnack(true)
            return
        }

        _calendar = calendar

        // Init managed holiday calendars with DB data
        getManagedHolidayCalendars(showHidden = true)?.let { holidayCalendars ->
            _holidayCalendars.value = holidayCalendars

            val managedHolidayCalendar = holidayCalendars.firstOrNull { it.calendarId == calendarId } ?: run {
                // If we failed to find a match with the DB list, refresh the list from remote and retry
                calendarsRepository.refreshManagedHolidayCalendars(userId)?.let {
                    _holidayCalendars.value = it
                    fetchedHolidayCalendars = true
                    it.firstOrNull { holidayCalendar -> holidayCalendar.calendarId == calendarId }
                }
            } ?: run {
                // If we still failed to find a match, display error and close the view
                logger.e("ManagedHolidayCalendar was null in initUpdateHolidayCalendar")
                displayInitErrorSnack(true)
                return
            }

            // Calendar name
            _country.value = managedHolidayCalendar.country

            // Calendar language
            _language.value = managedHolidayCalendar.language

            // Calendar color
            _calendarColor.value = calendar.color

            // Default all day event notifications
            setDefaultAlarms(calendar.defaultFullDayNotifications)
        } ?: run {
            logger.e("ManagedHolidayCalendars were null in initUpdateHolidayCalendar")
            displayInitErrorSnack(true)
            return
        }

        if (!fetchedHolidayCalendars) {
            calendarsRepository.refreshManagedHolidayCalendars(userId)?.let {
                _holidayCalendars.value = it
                fetchedHolidayCalendars = true
            }
        }
    }

    suspend fun initCreateHolidayCalendar(
        calendarColor: String,
        defaultLanguageCode: String,
        defaultCountryCode: String,
        returnToSettings: Boolean
    ) {

        // Set default calendar color (picked randomly from the colors array)
        _calendarColor.value = calendarColor

        // Set default all day event notifications (1 day before at 9am)
        _defaultAllDayAlarms.value = arrayListOf()

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in HolidayCalendarViewModel initCreateHolidayCalendar")
            displayInitErrorSnack(returnToSettings)
            return
        }
        _userId.value = userId

        val primaryTimezone = calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id) ?: ZoneId.systemDefault().id

        // Init managed holiday calendars with DB data
        getManagedHolidayCalendars(showHidden = false)?.let { holidayCalendars ->
            if (holidayCalendars.isEmpty()) {
                logger.e("ManagedHolidayCalendars were empty in HolidayCalendarViewModel initCreateHolidayCalendar")
                displayInitErrorSnack(returnToSettings)
                return
            }

            _holidayCalendars.value = holidayCalendars

            if (!autoDetectHolidayCalendar(userId, holidayCalendars, primaryTimezone, defaultLanguageCode, defaultCountryCode)) {
                // If we failed to find a match with the DB list, refresh the list from remote and retry
                calendarsRepository.refreshVisibleManagedHolidayCalendars(userId)?.let {
                    _holidayCalendars.value = it
                    fetchedHolidayCalendars = true
                    autoDetectHolidayCalendar(userId, it, primaryTimezone, defaultLanguageCode, defaultCountryCode)
                    // If we still fail to find a match, leave holiday calendar form in its default state.
                }
            }
        } ?: run {
            logger.e("ManagedHolidayCalendars were null in HolidayCalendarViewModel initCreateHolidayCalendar")
            displayInitErrorSnack(returnToSettings)
            return
        }

        if (!fetchedHolidayCalendars) {
            calendarsRepository.refreshVisibleManagedHolidayCalendars(userId)?.let {
                _holidayCalendars.value = it
                fetchedHolidayCalendars = true
            }
        }
    }

    private suspend fun autoDetectHolidayCalendar(
        userId: UserId,
        holidayCalendars: List<ManagedHolidayCalendarEntity>,
        primaryTimezone: String,
        defaultLanguageCode: String,
        defaultCountryCode: String
    ): Boolean {
        // Get calendars matching the default time zone
        val matchingDefaultHolidayCalendar = getMatchingDefaultHolidayCalendar(
            holidayCalendars,
            primaryTimezone,
            defaultLanguageCode,
            defaultCountryCode
        )

        // If holiday calendar already exists, leave the fields empty
        matchingDefaultHolidayCalendar?.let {
            val holidayCalendarAlreadyExists = calendarsRepository.selectCalendarEntities(userId.id).firstOrNull {
                it.id == matchingDefaultHolidayCalendar.calendarId
            } != null
            if (holidayCalendarAlreadyExists) return true
        } ?: return false
        // Change state so we display based on time zone disclaimer
        holidayCalendarState.value = HolidayCalendarState.PickBasedOnTimeZone
        _country.value = matchingDefaultHolidayCalendar.country
        _language.value = matchingDefaultHolidayCalendar.language
        return true
    }

    private fun displayInitErrorSnack(returnToSettings: Boolean) {
        if (returnToSettings) {
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_holiday_calendar_init_error)
            )
        } else {
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_holiday_calendar_init_error)
            )
        }
    }

    fun getLanguages(): List<String> {
        val languages = _holidayCalendars.value?.filter {
            it.country == _country.value && it.hidden == false
        }?.map {
            it.language
        } ?: emptyList()
        return language.value?.let {
            // Add already selected language for cases where user edit hidden calendar
            languages.plus(it).distinct()
        } ?: languages.distinct()
    }

    fun hasBeenEdited(): Boolean {
        return calendarEdited
    }

    private fun setDefaultAlarms(defaultNotifications: List<Notification>) {
        val alarms = arrayListOf<VAlarm>().apply { addAll(defaultNotifications.map { it.toVAlarm() }) }

        _defaultAllDayAlarms.value = alarms
    }

    fun handleAlarmChange(alarm: VAlarm, isDelete: Boolean = false) {
        val tmpDefaultAllDayAlarms = _defaultAllDayAlarms.value
        calendarEdited = true

        if (isDelete) tmpDefaultAllDayAlarms?.remove(alarm) // Remove the alarm from the list
        else {
            // Check if alarm already exist in the list
            if (tmpDefaultAllDayAlarms?.contains(alarm) == true || tmpDefaultAllDayAlarms?.any { it.isTheSameAs(alarm) } == true) {
                holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_notification_already_added)
                )
                return
            }
            // Add the alarm to the list
            tmpDefaultAllDayAlarms?.add(alarm)
        }
        // Update LiveData value to trigger changes in view
        _defaultAllDayAlarms.value = tmpDefaultAllDayAlarms
    }

    suspend fun handleCountry(country: String, defaultLanguageCode: String) {
        if (_country.value == country) return
        calendarEdited = true
        _country.value = country

        if (holidayCalendarState.value == HolidayCalendarState.PickBasedOnTimeZone) {
            // Clear based on time zone state
            holidayCalendarState.value = HolidayCalendarState.Idle
        }

        // Get the calendar matching the default language
        val matchingCountries = holidayCalendars.value?.filter { it.country == country }
        val matchingDefaultHolidayCalendar = matchingCountries?.firstOrNull {
            it.languageCode.equals(defaultLanguageCode, ignoreCase = true)
        } ?: matchingCountries?.firstOrNull()
        _language.value = matchingDefaultHolidayCalendar?.language ?: ""

        checkExistingHolidayCalendar()
    }

    suspend fun handleLanguage(language: String) {
        if (_language.value == language) return
        calendarEdited = true
        _language.value = language
        checkExistingHolidayCalendar()
    }

    fun handleCalendarColor(calendarColor: String) {
        if (_calendarColor.value == calendarColor) return
        calendarEdited = true
        _calendarColor.value = calendarColor
    }

    private suspend fun checkExistingHolidayCalendar() {
        val holidayCalendarId = holidayCalendars.value?.firstOrNull {
            it.country == _country.value && it.language == _language.value
        }?.calendarId
        if (_calendar?.id == holidayCalendarId) {
            if (holidayCalendarState.value == HolidayCalendarState.AlreadyExists) {
                // Clear already exists state if needed
                holidayCalendarState.value = HolidayCalendarState.Idle
            }
            return
        }
        val holidayCalendarAlreadyExists =
            userId.value?.id?.let { userId ->
                calendarsRepository.selectCalendarEntities(userId).firstOrNull {
                    it.id == holidayCalendarId
                } != null
            } ?: false
        if (holidayCalendarAlreadyExists) {
            // Set already exists state
            holidayCalendarState.value = HolidayCalendarState.AlreadyExists
        } else if (holidayCalendarState.value == HolidayCalendarState.AlreadyExists) {
            // Clear already exists state
            holidayCalendarState.value = HolidayCalendarState.Idle
        }
    }

    suspend fun handleSaveHolidayCalendar(returnToSettings: Boolean, isCreate: Boolean, selectedDate: LocalDate?) {
        val userId = userId.value

        val holidayCalendar = holidayCalendars.value?.firstOrNull {
            it.country == _country.value && it.language == _language.value
        }

        val calendarColor = _calendarColor.value

        if (userId == null || holidayCalendar == null || calendarColor == null) {
            userId ?: logger.e("User ID was null in HolidayCalendarViewModel handleSaveHolidayCalendar")
            holidayCalendar ?: logger.e("Holiday calendar was null in HolidayCalendarViewModel handleSaveHolidayCalendar")
            calendarColor ?: logger.e("Calendar color was null in HolidayCalendarViewModel handleSaveHolidayCalendar")
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                resourceProvider.provideString(
                    if (isCreate) R.string.snack_add_calendar_error
                    else R.string.snack_update_calendar_error
                )
            )
            return
        }

        // Set loading state
        holidayCalendarState.value = HolidayCalendarState.Processing.Saving

        if (isCreate) {
            handleCreateHolidayCalendar(returnToSettings, userId, holidayCalendar, calendarColor, selectedDate)
        } else {
            handleUpdateHolidayCalendar(userId, holidayCalendar, calendarColor, selectedDate)
        }
    }

    private suspend fun handleUpdateHolidayCalendar(
        userId: UserId,
        holidayCalendar: ManagedHolidayCalendarEntity,
        calendarColor: String,
        selectedDate: LocalDate?
    ) {
        val calendar = _calendar
        if (calendar == null) {
            logger.e("Calendar was null for update in HolidayCalendarViewModel handleSaveHolidayCalendar")
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_update_calendar_error)
            )
            // Clear loading state
            holidayCalendarState.value = HolidayCalendarState.Idle
            return
        }
        val calendarPriority = calendar.priority
        val currentDefaultAllDayNotifications = calendar.defaultFullDayNotifications.map { it.toVAlarm() }
        val notificationsChanged = currentDefaultAllDayNotifications != _defaultAllDayAlarms.value
        val colorChanged = calendar.color != calendarColor
        val calendarChanged = calendar.id != holidayCalendar.calendarId
        if (calendarChanged) {
            // Check that holiday calendar doesn't already exist
            if (handleExistingHolidayCalendar(userId, holidayCalendar)) {
                // Clear loading state
                holidayCalendarState.value = HolidayCalendarState.Idle
                return
            }

            // Leave current holiday calendar
            val leaveCalendarUseCaseResult = leaveManagedCalendarUseCase.execute(userId, calendar.id)
            if (leaveCalendarUseCaseResult !is UseCase.Result.Success<*>) {
                holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_update_calendar_error)
                )
                // Clear loading state
                holidayCalendarState.value = HolidayCalendarState.Idle
                return
            }

            val displayTimeZoneId = calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)
                ?: ZoneId.systemDefault().id

            // Join new holiday calendar
            val joinCalendarResult = joinCalendarUseCase.joinHolidayCalendar(
                userId,
                holidayCalendar,
                calendarColor,
                _defaultAllDayAlarms.value,
                calendarPriority
            )

            // Clear loading state
            holidayCalendarState.value = HolidayCalendarState.Idle

            if (joinCalendarResult !is UseCase.Result.Success<*>) {
                logger.e("Failed to join holiday calendar on update")
                holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_update_calendar_error)
                )
                return
            } else {
                joinCalendarResult.returnValue.tryCast<String> {
                    val calendarId = this
                    fetchCachedViewsEvents(
                        userId,
                        calendarId,
                        selectedDate ?: LocalDate.now(ZoneId.of(displayTimeZoneId)),
                        displayTimeZoneId
                    )
                }
                // Use settings snack state here to display snack in calendar settings view
                calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_update_calendar_success)
                )
            }
        } else if (notificationsChanged || colorChanged) {
            // Update calendar settings
            val updateCalendarSettingsUseCaseResult = if (notificationsChanged) {
                updateCalendarSettingsUseCase.updateCalendarSettings(
                    userId,
                    calendar.id,
                    defaultFullDayNotifications = _defaultAllDayAlarms.value
                )
            } else UseCase.Result.Success<Unit>()

            // Update color in member
            val updateMemberUseCaseResult = if (colorChanged) {
                updateCalendarUseCase.executeUpdate(
                    userId,
                    calendar.id,
                    color = calendarColor
                )
            } else UseCase.Result.Success<Unit>()

            // Clear loading state
            holidayCalendarState.value = HolidayCalendarState.Idle

            if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*> &&
                updateMemberUseCaseResult !is UseCase.Result.Success<*>) {
                // Both update calls failed
                holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_update_calendar_error)
                )
                return
            } else if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*> ||
                updateMemberUseCaseResult !is UseCase.Result.Success<*>) {
                // Only one update call failed
                holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_update_calendar_settings_error)
                )
            } else {
                // Use settings snack state here to display snack in calendar settings view
                calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_update_calendar_success)
                )
            }
        } else {
            // Nothing changed
            // Use settings snack state with empty message here to navigate back up to calendar settings view
            calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp("")
        }
    }

    private suspend fun handleExistingHolidayCalendar(userId: UserId, holidayCalendar: ManagedHolidayCalendarEntity): Boolean {
        val holidayCalendarAlreadyExists = calendarsRepository.selectCalendarEntities(userId.id).firstOrNull {
            it.id == holidayCalendar.calendarId
        } != null
        if (holidayCalendarAlreadyExists) {
            // Clear loading state
            holidayCalendarState.value = HolidayCalendarState.Idle

            // Use holiday calendar snack state here to display snack in month view
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.holiday_calendar_already_exists)
            )
            return true
        }
        return false
    }

    private suspend fun handleCreateHolidayCalendar(
        returnToSettings: Boolean,
        userId: UserId,
        holidayCalendar: ManagedHolidayCalendarEntity,
        calendarColor: String,
        selectedDate: LocalDate?
    ) {
        // Check that holiday calendar doesn't already exist
        if (handleExistingHolidayCalendar(userId, holidayCalendar)) {
            // Clear loading state
            holidayCalendarState.value = HolidayCalendarState.Idle
            return
        }

        val displayTimeZoneId = calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)
            ?: ZoneId.systemDefault().id

        val joinCalendarResult = joinCalendarUseCase.joinHolidayCalendar(
            userId,
            holidayCalendar,
            calendarColor,
            _defaultAllDayAlarms.value
        )

        // Clear loading state
        holidayCalendarState.value = HolidayCalendarState.Idle

        if (joinCalendarResult !is UseCase.Result.Success<*>) {
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_add_calendar_error)
            )
            return
        }

        joinCalendarResult.returnValue.tryCast<String> {
            val calendarId = this
            fetchCachedViewsEvents(
                userId,
                calendarId,
                selectedDate ?: LocalDate.now(ZoneId.of(displayTimeZoneId)),
                displayTimeZoneId
            )
        }

        if (returnToSettings) {
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_add_calendar_success)
            )
        } else {
            // Use holiday calendar snack state here to display snack in month view
            holidayCalendarSnackState.value = HolidayCalendarSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_add_calendar_success)
            )
        }
    }

    private suspend fun getManagedHolidayCalendars(showHidden: Boolean): List<ManagedHolidayCalendarEntity>? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in HolidayCalendarViewModel getCalendar")
            return null
        }
        val dbManagedHolidayCalendars = calendarsRepository.getManagedHolidayCalendars(userId)?.let {
            if (showHidden) it
            else it.filter { it.hidden == false }
        }
        return if (dbManagedHolidayCalendars.isNullOrEmpty()) {
            fetchedHolidayCalendars = true
            calendarsRepository.refreshManagedHolidayCalendars(userId)?.let {
                if (showHidden) it
                else it.filter { it.hidden == false }
            }
        } else dbManagedHolidayCalendars
    }

    private fun fetchCachedViewsEvents(
        userId: UserId,
        calendarId: String,
        selectedDate: LocalDate,
        displayTimeZoneId: String
    ) : LiveData<Operation.State> {
        return FetchCachedViewsEventsWorker.enqueue(workManager,
            userId = userId,
            calendarId = calendarId,
            selectedDate = selectedDate,
            displayTimeZoneId = displayTimeZoneId
        )
    }
}
