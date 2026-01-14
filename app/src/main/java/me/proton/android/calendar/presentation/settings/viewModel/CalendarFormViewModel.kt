package me.proton.android.calendar.presentation.settings.viewModel

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import biweekly.component.VAlarm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarForm.DEFAULT_ALL_DAY_ALARM
import me.proton.android.calendar.common.CalendarForm.DEFAULT_ALL_DAY_EMAIL_ALARM
import me.proton.android.calendar.common.CalendarForm.DEFAULT_PART_DAY_ALARM
import me.proton.android.calendar.common.CalendarForm.DEFAULT_PART_DAY_EMAIL_ALARM
import me.proton.android.calendar.common.CalendarForm.EVENT_DEFAULT_DURATION_MINUTES
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.usecase.CreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.isExternal
import javax.inject.Inject

@HiltViewModel
class CalendarFormViewModel @Inject constructor(
    application: Application,
    private val resourceProvider: ResourceProvider,
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val updateCalendarSettingsUseCase: UpdateCalendarSettingsUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val userManager: UserManager,
    private val userAddressManager: UserAddressManager,
    private val accountManager: AccountManager,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase
) : AndroidViewModel(application) {

    sealed class CalendarFormSnackState {

        data class DisplaySnack(
            val message: String,
        ): CalendarFormSnackState()

        data class DisplaySnackNavigateUp(
            val message: String,
        ): CalendarFormSnackState()
    }

    val calendarFormSnackState: MutableStateFlow<CalendarFormSnackState?> = MutableStateFlow(null)
    val calendarSettingsSnackState: MutableStateFlow<CalendarFormSnackState?> = MutableStateFlow(null)

    sealed class CalendarFormState {

        object Idle: CalendarFormState()

        sealed class Processing: CalendarFormState() {
            object Saving: Processing()
        }
    }

    val calendarFormState: MutableStateFlow<CalendarFormState> = MutableStateFlow(CalendarFormState.Idle)

    var calendarIsSubscribed: Boolean = false
        private set

    var calendarIsSharedWithMe: Boolean = false
        private set

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    private val _calendarName = MutableLiveData("")
    val calendarName: LiveData<String> = _calendarName

    private val _calendarDescription = MutableLiveData("")
    val calendarDescription: LiveData<String> = _calendarDescription

    private val _calendarEmail = MutableLiveData<String>()
    val calendarEmail: LiveData<String> = _calendarEmail

    private val _calendarColor = MutableLiveData<String>()
    val calendarColor: LiveData<String> = _calendarColor

    private val _defaultEventDuration = MutableLiveData<Int>()
    val defaultEventDuration: LiveData<Int> = _defaultEventDuration

    private val _defaultPartDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultPartDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultPartDayAlarms

    private val _defaultAllDayAlarms = MutableLiveData(arrayListOf<VAlarm>())
    val defaultAllDayAlarms: LiveData<ArrayList<VAlarm>> = _defaultAllDayAlarms

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var _calendarId: String? = null

    private var calendarEdited = false
    private var calendarSettingsEdited = false

    var userEmails: List<String>? = null

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun resetFormValues() {
        _calendarName.value = ""
        _calendarDescription.value = ""
        _calendarColor.value = ""
        _calendarEmail.value = ""
        _defaultEventDuration.value = EVENT_DEFAULT_DURATION_MINUTES.first()
        _defaultPartDayAlarms.value = arrayListOf()
        _defaultAllDayAlarms.value = arrayListOf()
        _calendarId = null
        calendarFormSnackState.value = null
        calendarSettingsSnackState.value = null
        calendarEdited = false
        calendarSettingsEdited = false
        userEmails = null
    }

    suspend fun initUpdateCalendarForm(calendarId: String) {

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in CalendarFormViewModel initUpdateCalendarForm")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        _calendarId = calendarId

        val calendar = getCalendar(calendarId) ?: run {
            logger.e("Calendar was null in initUpdateCalendarForm")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }

        val calendarSettings = getCalendarSettings(calendarId) ?: run {
            logger.e("CalendarSettings was null in initUpdateCalendarForm")
            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }

        calendarIsSubscribed = calendar.isSubscribed

        calendarIsSharedWithMe = calendar.isSharedWithMe

        // Calendar name
        _calendarName.value = calendar.name

        // Calendar description
        _calendarDescription.value = calendar.description

        // Calendar default email (can't be updated for existing calendar)
        _calendarEmail.value = calendar.email

        // Calendar color
        _calendarColor.value = calendar.color

        // Default event duration
        _defaultEventDuration.value = calendarSettings.defaultEventDuration

        // Default part day event notifications
        setDefaultAlarms(calendar.defaultPartDayNotifications, isAllDay = false)

        // Default all day event notifications
        setDefaultAlarms(calendar.defaultFullDayNotifications, isAllDay = true)
    }

    suspend fun initCreateCalendarForm(calendarColor: String) {
        // Set default calendar color (picked randomly from the colors array)
        _calendarColor.value = calendarColor

        // Set default event duration
        _defaultEventDuration.value = EVENT_DEFAULT_DURATION_MINUTES.first()

        // Set default part day event notifications (15 minutes before)
        val defaultPartDayAlarms = arrayListOf(DEFAULT_PART_DAY_ALARM)
        defaultPartDayAlarms.add(DEFAULT_PART_DAY_EMAIL_ALARM)
        _defaultPartDayAlarms.value = defaultPartDayAlarms

        // Set default all day event notifications (1 day before at 9am)
        val defaultAllDayAlarms = arrayListOf(DEFAULT_ALL_DAY_ALARM)
        defaultAllDayAlarms.add(DEFAULT_ALL_DAY_EMAIL_ALARM)
        _defaultAllDayAlarms.value = defaultAllDayAlarms

        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: run {
            logger.e("UserId was null in CalendarFormViewModel initCreateCalendarForm")
            calendarFormSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_calendar_init_error)
            )
            return
        }
        _userId.value = userId

        // Save user emails for calendar email picker dialog
        val userAddresses = userAddressManager.getAddressesOrNull(userId).orEmpty()
        userEmails = userAddresses.filter { it.enabled && it.canSend && it.canReceive && it.isExternal().not() }.sortedBy { it.order }.map { it.email }

        val defaultUserEmail = userManager.getUser(userId).email
        val defaultUserAddress =
            if (defaultUserEmail != null) {
                userAddresses.find {
                    canonicalizeProtonEmail(it.email, forceCanonicalization = true) == canonicalizeProtonEmail(
                        defaultUserEmail,
                        forceCanonicalization = true
                    )
                }
            } else null
        if (defaultUserEmail != null && defaultUserAddress != null && defaultUserAddress.enabled && defaultUserAddress.canReceive && defaultUserAddress.canSend) {
            _calendarEmail.value = defaultUserEmail!!
        } else {
            _calendarEmail.value = userEmails?.firstOrNull() ?: run {
                logger.e("userEmails was null in CalendarFormViewModel initCreateCalendarForm")
                calendarFormSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_calendar_init_error)
                )
                return
            }
        }
    }

    fun hasFormBeenEdited(): Boolean {
        return calendarEdited || calendarSettingsEdited
    }

    private fun setDefaultAlarms(defaultNotifications: List<Notification>, isAllDay: Boolean) {
        val alarms = arrayListOf<VAlarm>().apply { addAll(defaultNotifications.map { it.toVAlarm() }) }

        if (isAllDay) _defaultAllDayAlarms.value = alarms
        else _defaultPartDayAlarms.value = alarms
    }

    fun handleAlarmChange(alarm: VAlarm, isAllDay: Boolean, isDelete: Boolean = false) {
        calendarSettingsEdited = true
        if (isAllDay) {
            val tmpDefaultAllDayAlarms = _defaultAllDayAlarms.value

            if (isDelete) tmpDefaultAllDayAlarms?.remove(alarm) // Remove the alarm from the list
            else {
                // Check if alarm already exist in the list
                if (tmpDefaultAllDayAlarms?.contains(alarm) == true || tmpDefaultAllDayAlarms?.any { it.isTheSameAs(alarm) } == true) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_notification_already_added)
                    )
                    return
                }
                // Add the alarm to the list
                tmpDefaultAllDayAlarms?.add(alarm)
            }
            // Update LiveData value to trigger changes in view
            _defaultAllDayAlarms.value = tmpDefaultAllDayAlarms

        } else {
            val tmpDefaultPartDayAlarms = _defaultPartDayAlarms.value

            if (isDelete) tmpDefaultPartDayAlarms?.remove(alarm) // Remove the alarm from the list
            else {
                // Check if alarm already exist in the list
                if (tmpDefaultPartDayAlarms?.contains(alarm) == true || tmpDefaultPartDayAlarms?.any { it.isTheSameAs(alarm) } == true) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_notification_already_added)
                    )
                    return
                }
                // Add the alarm to the list
                tmpDefaultPartDayAlarms?.add(alarm)
            }
            // Update LiveData value to trigger changes in view
            _defaultPartDayAlarms.value = tmpDefaultPartDayAlarms
        }
    }

    fun handleCalendarName(calendarName: String) {
        if (_calendarName.value == calendarName) return
        calendarEdited = true
        _calendarName.value = calendarName
    }

    fun handleCalendarDescription(calendarDescription: String) {
        if (_calendarDescription.value == calendarDescription) return
        calendarEdited = true
        _calendarDescription.value = calendarDescription
    }

    fun handleCalendarColor(calendarColor: String) {
        if (_calendarColor.value == calendarColor) return
        calendarEdited = true
        _calendarColor.value = calendarColor
    }

    fun handleDefaultEventDuration(defaultEventDuration: Int) {
        if (_defaultEventDuration.value == defaultEventDuration) return
        calendarSettingsEdited = true
        _defaultEventDuration.value = defaultEventDuration
    }

    fun handleCalendarEmail(calendarEmail: String) {
        if (_calendarEmail.value == calendarEmail) return
        calendarEdited = true
        _calendarEmail.value = calendarEmail
    }

    suspend fun handleSaveCalendarForm(returnToSettings: Boolean) {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarFormViewModel handleSaveCalendarForm")
            return
        }
        _calendarId?.let { calendarId ->
            // Update existing calendar

            // Set loading state
            calendarFormState.value = CalendarFormState.Processing.Saving

            if (calendarEdited) {
                // Update calendar entity
                val updateCalendarUseCaseResult = updateCalendarUseCase.executeUpdate(
                    userId,
                    calendarId,
                    description = _calendarDescription.value,
                    name = _calendarName.value,
                    color = _calendarColor.value
                )
                if (updateCalendarUseCaseResult !is UseCase.Result.Success<*>) {
                    updateCalendarUseCaseResult.ifSuccessAndLogErrors(logger) {}
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_update_calendar_error)
                    )
                    // Clear loading state
                    calendarFormState.value = CalendarFormState.Idle
                    return
                }
            }

            if (calendarSettingsEdited) {
                // Update calendar settings
                val updateCalendarSettingsUseCaseResult = updateCalendarSettingsUseCase.updateCalendarSettings(
                    userId,
                    calendarId,
                    _defaultEventDuration.value,
                    _defaultPartDayAlarms.value,
                    _defaultAllDayAlarms.value
                )
                if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*>) {
                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(
                            if (calendarEdited) R.string.snack_update_calendar_settings_error
                            else R.string.snack_update_calendar_error
                        )
                    )
                    // Clear loading state
                    calendarFormState.value = CalendarFormState.Idle
                    return
                }
            }

            // Reset calendar id
            _calendarId = null

            // Clear loading state
            calendarFormState.value = CalendarFormState.Idle

            // Use settings snack state here to display snack in calendar settings view
            calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                resourceProvider.provideString(R.string.snack_update_calendar_success)
            )
        } ?: run {
            // Create new calendar

            if (_calendarEmail.value.isNullOrEmpty()) {
                calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_create_calendar_error)
                )
                return
            }

            // Set loading state
            calendarFormState.value = CalendarFormState.Processing.Saving

            // Get current active user calendars to check if new one needs to be set to default
            val activeUserCalendars = calendarsRepository.selectActiveUserCalendars(userId.id)
            val setNewCalendarAsDefault = activeUserCalendars.isNullOrEmpty()

            // Create calendar
            val createCalendarResult = createCalendarUseCase.execute(
                userId = userId,
                name = _calendarName.value!!,
                description = _calendarDescription.value!!,
                color = _calendarColor.value!!,
                email = _calendarEmail.value!!
            )
            if (createCalendarResult !is UseCase.Result.Success<*>) {
                createCalendarResult.ifSuccessAndLogErrors(logger) {}
                calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                    resourceProvider.provideString(R.string.snack_create_calendar_error)
                )
                // Clear loading state
                calendarFormState.value = CalendarFormState.Idle
                return
            }

            createCalendarResult.returnValue.tryCast<String> {

                // Update newly created calendar settings
                val updateCalendarSettingsUseCaseResult = updateCalendarSettingsUseCase.updateCalendarSettings(
                    userId,
                    this,
                    _defaultEventDuration.value,
                    _defaultPartDayAlarms.value,
                    _defaultAllDayAlarms.value
                )
                if (updateCalendarSettingsUseCaseResult !is UseCase.Result.Success<*>) {
                    // Set the newly created calendar id in case we fail to update calendar settings
                    _calendarId = this

                    calendarFormSnackState.value = CalendarFormSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_create_calendar_settings_error)
                    )
                    // Clear loading state
                    calendarFormState.value = CalendarFormState.Idle
                    return
                }

                // Set newly created calendar as default if needed
                if (setNewCalendarAsDefault) {
                    updateCalendarUserSettingsUseCase.executeDefaultCalendarId(
                        userId,
                        this
                    )
                }
            }

            // Reset calendar id
            _calendarId = null

            // Clear loading state
            calendarFormState.value = CalendarFormState.Idle

            if (returnToSettings) {
                // Use settings snack state here to display snack in calendar settings view
                calendarSettingsSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_create_calendar_success)
                )
            } else {
                // Use form snack state here to display snack in month view
                calendarFormSnackState.value = CalendarFormSnackState.DisplaySnackNavigateUp(
                    resourceProvider.provideString(R.string.snack_create_calendar_success)
                )
            }
        }
    }

    private suspend fun getCalendar(calendarId: String): Calendar? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarFormViewModel getCalendar")
            return null
        }
        return calendarsRepository.selectCalendar(calendarId)
    }

    private suspend fun getCalendarSettings(calendarId: String): CalendarSettingsEntity? {
        val userId = userId.value
        if (userId == null) {
            logger.e("User ID was null in CalendarFormViewModel getDefaultCalendarSettings")
            return null
        }
        return calendarsRepository.selectCalendarSettings(calendarId)
    }
}
