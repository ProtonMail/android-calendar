package me.proton.android.calendar.presentation.calendar.viewModel

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.text.Spanned
import android.text.format.DateFormat
import android.view.LayoutInflater
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.work.Operation
import androidx.work.WorkManager
import biweekly.parameter.ParticipationStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import me.proton.android.calendar.R
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.MAX_CALENDAR_FREE
import me.proton.android.calendar.common.MAX_CALENDAR_INDICATORS
import me.proton.android.calendar.common.MAX_CALENDAR_PAID
import me.proton.android.calendar.common.SIGNATURE_VERIFICATION_API_TIMEOUT
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.getTimeFormat
import me.proton.android.calendar.common.getTimeFormatFlow
import me.proton.android.calendar.common.getUserOrNull
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.getWeekStartFlow
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.areTimeZoneOffsetsDifferent
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.ICalUtilsImpl.explodeEventDayByDay
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutEventsBySearchTerm
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sortForMonthView
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.worker.BugReportWorker
import me.proton.android.calendar.common.worker.FixCalendarsWorker
import me.proton.android.calendar.common.worker.MigrateEventMetadataToOccurrencesWorker
import me.proton.android.calendar.common.worker.UpdateAutoDetectPrimaryTimezoneWorker
import me.proton.android.calendar.common.worker.UpdateAutoImportInviteWorker
import me.proton.android.calendar.common.worker.UpdateCalendarListWorker
import me.proton.android.calendar.common.worker.UpdateDisplayWeekNumberWorker
import me.proton.android.calendar.common.worker.UpdatePrimaryTimezoneWorker
import me.proton.android.calendar.common.worker.UpdateTimeFormatWorker
import me.proton.android.calendar.common.worker.UpdateWeekStartWorker
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.databinding.DialogCheckboxBinding
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.DeleteCalendarUseCase
import me.proton.android.calendar.domain.usecase.GetCanonicalEmailsUseCase
import me.proton.android.calendar.domain.usecase.GetUiEventsUseCase
import me.proton.android.calendar.domain.usecase.HandleDeleteUseCase
import me.proton.android.calendar.domain.usecase.LeaveManagedCalendarUseCase
import me.proton.android.calendar.domain.usecase.LeaveSharedCalendarUseCase
import me.proton.android.calendar.domain.usecase.ManualRefreshUseCase
import me.proton.android.calendar.domain.usecase.ReactivateCalendarKeyUseCase
import me.proton.android.calendar.domain.usecase.RecreateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.android.calendar.presentation.calendar.adapter.TimelineEventAdapter
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.Delinquent
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.user.domain.extension.hasSubscriptionForMail
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.nullIfBlank
import me.proton.core.util.kotlin.toBoolean
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    application: Application,
    private val accountManager: AccountManager,
    private val userManager: UserManager,
    private val userAddressManager: UserAddressManager,
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val handleDeleteUseCase: HandleDeleteUseCase,
    private val reactivateCalendarKeyUseCase: ReactivateCalendarKeyUseCase,
    private val deleteCalendarUseCase: DeleteCalendarUseCase,
    private val recreateCalendarUseCase: RecreateCalendarUseCase,
    private val leaveSharedCalendarUseCase: LeaveSharedCalendarUseCase,
    private val leaveManagedCalendarUseCase: LeaveManagedCalendarUseCase,
    private val logger: Logger,
    private val getCanonicalEmailsUseCase: GetCanonicalEmailsUseCase,
    private val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase,
    private val resourceProvider: ResourceProvider,
    private val database: AppDatabase,
    private val getUiEventsUseCase: GetUiEventsUseCase,
    private val workManager: WorkManager,
    private val manualRefreshUseCase: ManualRefreshUseCase,
) : AndroidViewModel(application) {

    val initialised = MutableLiveData(false)

    private val _userId: MutableLiveData<UserId> = MutableLiveData()
    val userId: LiveData<UserId> = _userId

    var updateSelectedLocalDate: LocalDate? = null

    private val _selectedDateTime: MutableLiveData<Pair<LocalDate, LocalTime?>> = MutableLiveData()
    val selectedDateTime: LiveData<Pair<LocalDate, LocalTime?>> = _selectedDateTime

    // userCalendars contains all non-subscribed calendars regardless of their flags
    var visibleCalendarIds: LiveData<List<String>> = MutableLiveData()
    var userCalendars: LiveData<List<Calendar>> = MutableLiveData() // Calendars with type 0
    var userPersonalCalendars: LiveData<List<Calendar>> = MutableLiveData() // Calendars with type 0 where user is owner
    var otherCalendars: LiveData<List<Calendar>> = MutableLiveData() // Calendars with type 0 where user is member, type 1 and type 2
    var calendarSubscriptions: LiveData<List<CalendarSubscriptionEntity>> = MutableLiveData()

    var timeZoneId: LiveData<ZoneId> = MutableLiveData()
    var timeFormat: LiveData<Int> = MutableLiveData()
    var defaultCalendarId: LiveData<String?> = MutableLiveData()
    var autoDetectPrimaryTimezone: LiveData<Boolean> = MutableLiveData()
    var weekStart: LiveData<Int> = MutableLiveData()
    var displayWeekNumber: LiveData<Boolean> = MutableLiveData()
    var autoImportInvite: LiveData<Boolean> = MutableLiveData()

    var viewMode: MutableLiveData<ViewMode> = MutableLiveData(ViewMode.AGENDA)

    // monthView: true means that mini calendar is fully expanded, false means that it's collapsed
    var monthView: MutableLiveData<Boolean> = MutableLiveData(false)

    var loading: MutableLiveData<Boolean> = MutableLiveData(false)

    // Pair with position of the resumed fragment and loading status for the view
    //  so that we know when to load and display the events for a fragment without having multiple process running
    val monthViewLoading = MutableLiveData<Pair<Int, Boolean>>()

    // Position of the currently resumed month view fragment. We use to start loading the next view only after the swipe is finished.
    val resumedMonthViewPosition = MutableLiveData<Int>()

    var currentLoadingProcesses: Int = 0 // Amount of currently loading processes
    var viewPagerFragmentsLoadingState: HashMap<Int, Boolean> = hashMapOf() // Map of fragment position in the view pager and their loading states

    val initialToday: LocalDate = LocalDate.now()

    val lifeCycleScope: CoroutineScope = this.viewModelScope

    val fetchingEvents: MutableLiveData<String> = MutableLiveData(null)

    private var updateTimeZoneDialogLastShown: LocalDate? = null
    var updatingCalendarPassphrase: Boolean = false

    var showAutoDetectPrimaryTimezone = true
    var initialAutoDetectPrimaryTimezoneValue: Boolean? = null

    // Used to save the month view currently displayed month
    var monthViewDate: LocalDate? = null
    // Time of the first event of the day, used to adjust the day view scroll position
    var firstEventOfTheDayTime: LocalTime? = null

    // TODO Rename
    // This is used to store the current month mini calendar height when in month mode
    var currentPosDesiredMonthHeight = 0

    suspend fun selectCalendars() {
        val userId = userId.value?.id ?: accountManager.getPrimaryUserId().firstOrNull()?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel selectDisabledCalendars")
            return
        }
        visibleCalendarIds = calendarsRepository.flowVisibleCalendarIds(userId).asLiveData(Dispatchers.Default)
        userCalendars = calendarsRepository.flowUserCalendars(userId).asLiveData(Dispatchers.Default)
        userPersonalCalendars = calendarsRepository.flowUserPersonalCalendars(userId).asLiveData(Dispatchers.Default)
        otherCalendars = combine(
            calendarsRepository.flowSharedCalendars(userId).distinctUntilChanged(),
            calendarsRepository.flowSubscribedCalendars(userId).distinctUntilChanged(),
            calendarsRepository.flowHolidayCalendars(userId).distinctUntilChanged()
        ) { sharedCalendars, subscribedCalendars, holidayCalendars ->
            sharedCalendars + subscribedCalendars + holidayCalendars
        }.asLiveData(Dispatchers.Default)
        calendarSubscriptions = calendarsRepository.flowCalendarSubscriptions().asLiveData(Dispatchers.Default)
    }

    suspend fun selectUser(): User? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel selectUser")
            return null
        }
        return userManager.getUserOrNull(userId, logger)
    }

    fun refreshNow() = viewModelScope.launch {
        setLoading(true)
        val result = manualRefreshUseCase.execute()
        if (result !is UseCase.Result.Success<*>) {
            logger.e("Manual refresh failed: $result")
        }
        calendarsRepository.fetchingState
            .firstOrNull { it == CalendarsRepository.FetchingState.Finished }
        setLoading(false)
    }

    // TODO go back to UserId as String
    suspend fun initForUser(userId: UserId): Flow<CalendarsRepository.InitingState> {
        return flow {
            _userId.postValue(userId)

            // TODO make this prettier
            val calendarUserSettings = calendarsRepository.selectCalendarUserSettings(userId.id)
            if (calendarUserSettings == null) logger.e("CalendarViewModel initForUser: calendarUserSettings was null")
            val timeZone = calendarUserSettings?.primaryTimezone
            if (timeZone == null) {
                logger.e("CalendarViewModel initForUser: timeZone was null. Emitting error state and going back to login screen.")
                emit(CalendarsRepository.InitingState.Error)
                return@flow
            }

            timeZoneId = calendarsRepository.flowCalendarUserSettingsPrimaryTimezone(userId.id).map {
                if (it != null) {
                    ZoneId.of(it)
                } else {
                    ZoneId.of(timeZone)
                }
            }.asLiveData(Dispatchers.Default)

            autoDetectPrimaryTimezone = calendarsRepository.flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId.id).map {
                it?.toBoolean() ?: true // Show week numbers by default
            }.asLiveData(Dispatchers.Default)

            displayWeekNumber = calendarsRepository.flowCalendarUserSettingsDisplayWeekNumber(userId.id).map {
                it?.toBoolean() ?: true // Show week numbers by default
            }.asLiveData(Dispatchers.Default)

            autoImportInvite = calendarsRepository.flowCalendarUserSettingsAutoImportInvite(userId.id).map {
                it?.toBoolean() ?: false
            }.asLiveData(Dispatchers.Default)

            defaultCalendarId = calendarsRepository.flowCalendarUserDefaultCalendarId(userId.id).map {
                it
            }.asLiveData(Dispatchers.Default)

            timeFormat = userSettingsRepository.getTimeFormatFlow(userId, database).asLiveData(Dispatchers.Default)

            weekStart = userSettingsRepository.getWeekStartFlow(userId, database).asLiveData(Dispatchers.Default)

            calendarsRepository.initForUser(userId.id, ZoneId.of(timeZone)).collect {
                when (it) {
                    CalendarsRepository.InitingState.Initing -> {
                        emit(it)
                        logger.v("initing calendars repo")
                    }
                    CalendarsRepository.InitingState.Finished -> {
                        logger.v("finished initing calendars repo")
                        initialised.postValue(true)
                        emit(it)
                    }
                    CalendarsRepository.InitingState.Error -> {
                        logger.e("error in CalendarViewModel initForUser")
                        emit(it)
                    }
                }
            }

        }
    }

    suspend fun shutdown() {
        initialised.postValue(false)
        calendarsRepository.shutdown()
    }

    fun handleInitialDaySelection(date: LocalDate) {
        // Select specific date if it has been provided instead of default init value.
        // Lets us handle selected date when navigating back from event details / form if it was opened from a notification
        val immutableUpdateSelectedLocalDate = updateSelectedLocalDate
        if (immutableUpdateSelectedLocalDate != null) {
            handleDaySelected(immutableUpdateSelectedLocalDate)
            updateSelectedLocalDate = null
        } else handleDaySelected(date)
    }

    fun handleDaySelected(date: LocalDate, time: LocalTime? = null, fromMonthPagerCallback: Boolean = false) {
        // prevent mini-calendar scroll from overriding selected date
        _selectedDateTime.value?.let { selectedDateTime ->
            val selectedDate = selectedDateTime.first
            if (fromMonthPagerCallback && selectedDate.month == date.month && selectedDate.year == date.year) {
                return
            }
            weekStart.value?.let { weekStart ->
                val startWeekOn = AndroidUtils.getWeekStartDayOfWeek(weekStart)
                if (fromMonthPagerCallback &&
                    monthView.value == false &&
                    selectedDate.weekNumber(startWeekOn) == date.weekNumber(startWeekOn) &&
                    selectedDate.year == date.year) {
                    // TODO is this early return logic really needed for week view here ?
                    return
                }
            }
        }

        _selectedDateTime.value = Pair(date, time)
    }

    private fun calculateCalendarIndicators(
        events: List<UiEvent>,
        timeZoneId: String,
    ): Map<LocalDate, List<String>> {

        val indicators = mutableMapOf<LocalDate, MutableList<String>>().withDefault { mutableListOf() }

        events.forEach { event ->
            val partTimeEndsOnMidnight = (!event.isAllDay && event.dateEnd.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime() == LocalTime.MIDNIGHT)
            var start = event.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate()
            val end = event.dateEnd.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate()

            // Use !start.isAfter(end) to iterate inclusive
            while (!start.isAfter(end)) {
                val current = indicators.getValue(start)
                current.add(event.displayColor)
                indicators[start] = current
                start = start.plusDays(1)

                // All day events end on next day 00:00 so we need to break loop to exclude end day
                if (start == end && (event.isAllDay || partTimeEndsOnMidnight)) break
            }
        }

        return indicators.mapValues { it.value.toList().sorted().take(MAX_CALENDAR_INDICATORS) }
    }

    /**
     * Creates a Flow with Events matching the search term.
     */
    fun getTimelineEvents(userId: String, searchTerm: String, userEmails: List<String>, is24Hour: Boolean, timeZoneId: String): Flow<List<TimelineEventAdapter.TimelineItem>?> {

        val fromDate = LocalDate.now().minusYears(3)
        val toDate = LocalDate.now().plusYears(3)

        return calendarsRepository.getSearchEvents(userId, searchTerm).transform<CalendarsRepository.GetEventsResult<Event>, List<TimelineEventAdapter.TimelineItem>?> { eventResult ->

            when (eventResult) {
                is CalendarsRepository.GetEventsResult.Exception -> {
                    logger.e("Exception in getTimelineEvents", eventResult.throwable)
                    emit(null)
                }
                CalendarsRepository.GetEventsResult.InProgress -> { }
                is CalendarsRepository.GetEventsResult.Success -> {

                    // expand events until given date in the future
                    val expandedEvents = eventResult.events.flatMap {
                        calendarsRepository.expandDbEvent(it, eventResult.events, toDate.atStartOfDay(ZoneId.of(timeZoneId))).filterOutEventsBySearchTerm(searchTerm)
                    }

                    // explode events for UI
                    val timelineEvents = mutableListOf<TimelineEventAdapter.TimelineEvent>()
                    expandedEvents.explodeEventDayByDay(fromDate, toDate, timeZoneId).toSortedMap().forEach { entry ->

                        yield() // support coroutine cancellation

                        val sortedEvents = entry.value.sortedBy {
                            "${!it.isAllDay()}${
                                it.getStart(timeZoneId).toEpochSecond()
                            }${it.summary}"
                        }

                        val today = LocalDate.now()

                        sortedEvents.forEachIndexed { index, event ->
                            // show date column only in the first Event on a given day
                            val timelineEvent = event.toTimelineEvent(
                                resourceProvider,
                                entry.key,
                                timeZoneId,
                                index == 0,
                                entry.key == today,
                                index == sortedEvents.size - 1,
                                userEmails,
                                is24Hour,
                                isFreeUser() ?: true,
                                searchTerm
                            )
                            timelineEvents.add(timelineEvent)
                        }
                    }

                    val grouped = timelineEvents.groupBy {
                        it.happensOn.year
                    }.toSortedMap().flatMap {
                        listOf(TimelineEventAdapter.TimelineItem.Header(it.key)) + it.value.map {
                            TimelineEventAdapter.TimelineItem.Event(it)
                        }
                    }

                    emit(grouped)

                }
            }

        }.flowOn(Dispatchers.IO).cancellable()
    }

    fun calendarIndicators(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): Flow<Map<LocalDate, List<String>>> {
        // TODO we could optimize this by operating on EventOccurrenceEntity only, not full UiEvents
        return getUiEventsLookupFlow(fromDate, toDate, timeZoneId).map { eventsResult ->
            when (eventsResult) {
                CalendarsRepository.GetEventsResult.InProgress -> {
                    emptyMap()
                }
                is CalendarsRepository.GetEventsResult.Success -> calculateCalendarIndicators(
                    eventsResult.events,
                    timeZoneId
                )
                is CalendarsRepository.GetEventsResult.Exception -> {
                    logger.e("exception getting skeletonEventsLiveData", eventsResult.throwable)
                    emptyMap()
                }
            }
        }
    }

    val fetchingState: Flow<CalendarsRepository.FetchingState> = calendarsRepository.fetchingState

    suspend fun fetchEvents(fromDate: LocalDate,
                            toDate: LocalDate,
                            timeZoneId: String,
                            coroutineScope: CoroutineScope) {

        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel fetchEvents")
            return
        }
        coroutineScope.launch {
            calendarsRepository.fetchEvents(userId, fromDate, toDate, timeZoneId)
        }
    }

    fun getUiEventsLookupFlow(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): Flow<CalendarsRepository.GetEventsResult<UiEvent>> = flow {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getUiEventsLookup")
            return@flow
        }
        emitAll(getUiEventsUseCase.execute(userId, fromDate, toDate, timeZoneId))
    }.distinctUntilChanged()
        .onStart { emit(CalendarsRepository.GetEventsResult.InProgress) }

    suspend fun handleDeleteEvent(eventId: String,
                                  calendarId: String,
                                  deleteOption: EventEditDeleteOption,
                                  occurrenceNumber: Int? = null): UseCase.Result {

        // TODO ÜBER IMPORTANT -- FIXME, PUT INTO WORKER!!!!!!!
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel handleDeleteEvent")
            return UseCase.Result.Error("User ID was null in CalendarViewModel handleDeleteEvent")
        }

        return viewModelScope.async {
            withContext(Dispatchers.IO) {
                handleDeleteUseCase.handleDelete(userId, eventId, calendarId, deleteOption, occurrenceNumber) // TODO UserId
            }
        }.await()
    }

    suspend fun updateCalendarVisibility(calendarId: String, display: Boolean) {
        withContext(Dispatchers.IO) {
            calendarsRepository.updateCalendarDisplay(calendarId, display)
        }
    }

    suspend fun prepareDeleteCalendar(calendarId: String): DeleteCalendarUseCase.DeleteCalendarOption {
        val userId = userId.value?.id ?: accountManager.getPrimaryUserId().firstOrNull()?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel prepareDeleteCalendar")
            return DeleteCalendarUseCase.DeleteCalendarOption.Error("userID == null in prepareDeleteCalendar")
        }
        return deleteCalendarUseCase.prepare(UserId(userId), calendarId)
    }

    suspend fun deleteCalendar(deleteOption: DeleteCalendarUseCase.DeleteCalendarOption): UseCase.Result {
        val userId = userId.value?.id ?: accountManager.getPrimaryUserId().firstOrNull()?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel deleteCalendar")
            return UseCase.Result.Error("userID == null in deleteCalendar")
        }
        return deleteCalendarUseCase.execute(UserId(userId), deleteOption)
    }

    suspend fun recreateCalendar(calendarId: String): UseCase.Result {
        val userId = userId.value?.id ?: accountManager.getPrimaryUserId().firstOrNull()?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel recreateCalendar")
            return UseCase.Result.Error("userID == null in recreateCalendar")
        }
        return recreateCalendarUseCase.execute(UserId(userId), calendarId)
    }

    suspend fun leaveSharedCalendar(calendarId: String, memberId: String?): UseCase.Result {
        val userId = userId.value?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel leaveCalendar")
            return UseCase.Result.Error("userID == null in leaveCalendar")
        }
        return leaveSharedCalendarUseCase.execute(UserId(userId), calendarId, memberId)
    }

    suspend fun leaveHolidayCalendar(calendarId: String): UseCase.Result {
        val userId = userId.value?.id
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel leaveHolidayCalendar")
            return UseCase.Result.Error("userID == null in leaveHolidayCalendar")
        }
        return leaveManagedCalendarUseCase.execute(UserId(userId), calendarId)
    }

    fun updatePrimaryTimezone(primaryTimezone: String) : LiveData<Operation.State> {
        return UpdatePrimaryTimezoneWorker.enqueue(workManager, currUserId(), primaryTimezone = primaryTimezone)
    }

    fun updateAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone: Boolean) : LiveData<Operation.State> {
        return UpdateAutoDetectPrimaryTimezoneWorker.enqueue(workManager, userId = currUserId(), autoDetectPrimaryTimezone)
    }

    fun updateDisplayWeekNumber(displayWeekNumber: Boolean) : LiveData<Operation.State> {
        return UpdateDisplayWeekNumberWorker.enqueue(workManager, userId = currUserId(), displayWeekNumber)
    }

    fun updateAutoImportInvite(autoImportInvite: Boolean) : LiveData<Operation.State> {
        return UpdateAutoImportInviteWorker.enqueue(workManager, userId = currUserId(), autoImportInvite)
    }

    suspend fun updateDefaultCalendarId(defaultCalendarId: String): Boolean {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel updateDefaultCalendarId")
            return false
        }
        val updateCalendarUserSettingsUseCaseResult = updateCalendarUserSettingsUseCase.executeDefaultCalendarId(
            userId,
            defaultCalendarId
        )

        return updateCalendarUserSettingsUseCaseResult is UseCase.Result.Success<*>
    }

    fun updateTimeFormat(timeFormat: Int) : LiveData<Operation.State> {
        return UpdateTimeFormatWorker.enqueue(workManager, userId = currUserId(), timeFormat)
    }

    fun updateWeekStart(weekStart: Int) : LiveData<Operation.State> {
        return UpdateWeekStartWorker.enqueue(workManager, userId = currUserId(), weekStart)
    }

    fun updateServerCalendarListDisplay() : LiveData<Operation.State> {
        return UpdateCalendarListWorker.enqueue(workManager, currUserId())
    }

    private fun currUserId(): String {
        val value = userId.value?.id
        if (value == null) logger.e("User ID unexpectedly null")
        return value.orEmpty()
    }

    fun sendBugReport(
        osName: String,
        osVersion: String,
        client: String,
        appVersionName: String,
        title: String,
        description: String,
        username: String,
        email: String
    ) : LiveData<Operation.State> {
        return BugReportWorker.enqueue(
            workManager,
            userId = currUserId(),
            osName = osName,
            osVersion = osVersion,
            client = client,
            appVersionName = appVersionName,
            title = title,
            description = description,
            username = username,
            email = email,
        )
    }

    suspend fun updateInactiveCalendarsPassphrase() {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel updateInactiveCalendarsPassphrase")
            updatingCalendarPassphrase = false
            return
        }

        calendarsRepository.selectInactiveUserCalendars(userId.id).forEach { calendar ->
            if (calendar.hasUpdatePassphrase) {
                // Handle flag UPDATE_PASSPHRASE
                val reactivateCalendarKeyResult = reactivateCalendarKeyUseCase.execute(userId, calendar.id)
                reactivateCalendarKeyResult.ifSuccessAndLogErrors(logger) { }
            }
        }

        val refreshResult = calendarsRepository.refreshCalendars(userId)
        updatingCalendarPassphrase = false
    }

    suspend fun fetchCalendars(): List<Calendar>? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel fetchCalendars")
            return null
        }
        return calendarsRepository.fetchCalendars(userId)
    }

    suspend fun refreshCalendars(calendarIds: List<String>) {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel refreshCalendars")
            return
        }
        calendarsRepository.refreshCalendars(userId, calendarIds)
    }

    suspend fun refreshMember(calendarId: String): Boolean {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel refreshMember")
            return false
        }
        val memberEntity = calendarsRepository.fetchMembers(userId, calendarId)?.firstOrNull() ?: run {
            logger.e("MemberEntity was null in CalendarViewModel refreshMember")
            return false
        }
        calendarsRepository.persistMember(memberEntity)
        return true
    }

    suspend fun getCalendarUserSettingsPrimaryTimezone(): String? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getCalendarUserSettingsPrimaryTimezone")
            return null
        }
        return calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)
    }

    private suspend fun checkLocalTimezone(context: Context) {
        if (LocalDate.now() == updateTimeZoneDialogLastShown) return

        updateTimeZoneDialogLastShown = LocalDate.now()
        getTimeZoneId()?.let { timeZoneId ->
            val systemTimeZone = fallbackTimeZone(TimeZone.getDefault().id, fallbackToDefault = true)!!
            if (areTimeZoneOffsetsDifferent(timeZoneId.id, systemTimeZone) == true) {
                // We add tags to the timezone string argument directly because it is not supported otherwise
                val dialogMessage: Spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(
                        context.getString(R.string.update_timezone_dialog_message, "<b>${systemTimeZone}</b>"),
                        Html.FROM_HTML_MODE_COMPACT
                    )
                } else {
                    Html.fromHtml(context.getString(R.string.update_timezone_dialog_message, "<b>${systemTimeZone}</b>"))
                }

                val viewBinding = DialogCheckboxBinding.inflate(LayoutInflater.from(context), null, false)

                viewBinding.dialogCheckboxHeader.text = dialogMessage
                viewBinding.dialogCheckboxPress.root.setOnClickListener {
                    viewBinding.dialogCheckbox.performClick()
                }

                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.update_timezone_dialog_title)
                    .setView(viewBinding.root)
                    .setPositiveButton(R.string.update_timezone_dialog_confirmation) { _, _ ->
                        updatePrimaryTimezone(systemTimeZone)
                    }
                    .setNegativeButton(R.string.update_timezone_dialog_cancel) { _, _ -> }
                    .show().setOnDismissListener {
                        if (viewBinding.dialogCheckbox.isChecked) {
                            updateAutoDetectPrimaryTimezone(false)
                        }
                    }
            }
        }
    }

    suspend fun handleAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone: Boolean, context: Context) {
        // Use initial value to avoid showing dialog when user changes it in app settings. We only show the dialog when opening the app.
        if (initialAutoDetectPrimaryTimezoneValue == null) initialAutoDetectPrimaryTimezoneValue =
            autoDetectPrimaryTimezone
        if (showAutoDetectPrimaryTimezone && autoDetectPrimaryTimezone && initialAutoDetectPrimaryTimezoneValue == true) {
            checkLocalTimezone(context)
            showAutoDetectPrimaryTimezone = false
        }
    }

    suspend fun timeFormatIs24Hour(context: Context): Boolean {
        return when (getTimeFormat()) {
            1 -> true
            2 -> false
            else -> DateFormat.is24HourFormat(context)
        }
    }

    fun timeFormatIs24Hour(timeFormat: Int, context: Context): Boolean {
        return when (timeFormat) {
            1 -> true
            2 -> false
            else -> DateFormat.is24HourFormat(context)
        }
    }

    suspend fun getCanonicalEmails(emails: List<String>): Map<String, String?>? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getCanonicalEmails")
            return null
        }
        return getCanonicalEmailsUseCase.invoke(userId, emails)
    }

    suspend fun getUserEmails(): List<String>? {
        return getUserAddresses()?.map { it.email }
    }

    suspend fun getCanonicalUserEmails(forceCanonicalization: Boolean = false): List<String>? {
        return getUserAddresses()?.map { ProtonUtilsImpl.canonicalizeProtonEmail(it.email, forceCanonicalization) }
    }

    suspend fun transformEventAllowingApiCall(eventId: String, calendarId: String): Event? {
        return withTimeoutOrNull(SIGNATURE_VERIFICATION_API_TIMEOUT) {
            calendarsRepository.transformAllowingApiCall(eventId, calendarId)
        }
    }

    /**
     * @param loading define the loading state
     * @param position fragment position in the view pager
     */
    fun setLoading(loading: Boolean, position: Int? = null) {
        if (loading) {
            currentLoadingProcesses++
            if (position != null) viewPagerFragmentsLoadingState[position] = true
            this.loading.value = true
        } else if (position != null) {
            if (viewPagerFragmentsLoadingState[position] == true && currentLoadingProcesses > 0) currentLoadingProcesses--
            viewPagerFragmentsLoadingState.remove(position)
        } else {
            if (currentLoadingProcesses > 0) currentLoadingProcesses--
        }
        if (currentLoadingProcesses == 0) this.loading.value = false
    }

    suspend fun isFreeUser(): Boolean? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel isFreeUser")
            return null
        }
        val user = userManager.getUserOrNull(userId, logger)
        return user?.hasSubscriptionForMail() == false
    }

    suspend fun displayImport(): Boolean {
        return CalendarFeatureFlag.ImportAssistant.fallbackValue && isDelinquentUser() == false
    }

    suspend fun isDelinquentUser(): Boolean? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel isDelinquentUser")
            return null
        }
        val user = userManager.getUserOrNull(userId, logger)
        val delinquent = user?.delinquent?.value
        return delinquent != null && delinquent >= Delinquent.InvoiceDelinquent.value // We consider a user delinquent on the calendar side when the state is at least 3 (InvoiceDelinquent)
    }

    enum class CalendarLimit {
        ERROR,
        NOT_REACHED,
        FREE_REACHED,
        FREE_MANDATORY_PERSONAL_REACHED,
        PAID_REACHED,
        PAID_MANDATORY_PERSONAL_REACHED
    }

    suspend fun getCalendarsCount(): Int {
        return calendarsRepository.countCalendars()
    }

    suspend fun getPersonalCalendarsCount(): Int {
        return getUserPersonalCalendars()?.count() ?: 0
    }

    suspend fun isCalendarLimitReached(calendarType: Calendar.CalendarType): CalendarLimit {
        val calendarsCount = getCalendarsCount()
        val personalCalendarsCounts = getPersonalCalendarsCount()

        val isFreeUser = isFreeUser() ?: return CalendarLimit.ERROR

        // User can't have only subscribed / shared / holiday calendars.
        val blockForMandatoryPersonalCalendar =
            personalCalendarsCounts == 0 &&
                    calendarType == Calendar.CalendarType.HOLIDAY &&
                    calendarsCount >= (if (isFreeUser) MAX_CALENDAR_FREE else MAX_CALENDAR_PAID) - 1

        // Check free user limit
        if (isFreeUser && calendarsCount >= MAX_CALENDAR_FREE) {
            return CalendarLimit.FREE_REACHED
        } else if (isFreeUser && blockForMandatoryPersonalCalendar) {
            return CalendarLimit.FREE_MANDATORY_PERSONAL_REACHED
        }
        // Check paid user limit
        if (!isFreeUser && calendarsCount >= MAX_CALENDAR_PAID) {
            return CalendarLimit.PAID_REACHED
        } else if (!isFreeUser && blockForMandatoryPersonalCalendar) {
            return CalendarLimit.PAID_MANDATORY_PERSONAL_REACHED
        }
        return CalendarLimit.NOT_REACHED
    }

    /**
     * Getters for LiveData
     */

    suspend fun getUserCalendars(): List<Calendar>? {
        return userCalendars.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getUserCalendars")
                return null
            }
            calendarsRepository.selectUserCalendars(userId.id)
        }
    }

    suspend fun getUserPersonalCalendars(): List<Calendar>? {
        return userPersonalCalendars.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getUserCalendars")
                return null
            }
            calendarsRepository.selectUserPersonalCalendars(userId.id)
        }
    }

    suspend fun getOtherCalendars(): List<Calendar>? {
        return otherCalendars.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getUserCalendars")
                return null
            }
            calendarsRepository.selectOtherCalendars(userId.id)
        }
    }

    suspend fun allowDeleteEvent(calendarId: String): Boolean {
        return calendarsRepository.selectCalendar(calendarId)?.allowEditEvents ?: false
    }

    suspend fun getUserAddresses(): List<UserAddress>? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getUserAddresses")
            return null
        }
        return userAddressManager.getAddressesOrNull(userId)
    }

    suspend fun observeUserAddresses(): LiveData<List<UserAddress>>? {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel getUserAddresses")
            return null
        }
        return kotlin.runCatching {
            userAddressManager.observeAddresses(userId).asLiveData(Dispatchers.Default)
        }.getOrElse {
            logger.e("CalendarViewModel observeUserAddresses threw exception ${it.message}", it)
            null
        }
    }

    suspend fun getTimeZoneId(): ZoneId? {
        return timeZoneId.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getTimeZoneId")
                return null
            }
            calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)?.let {
                ZoneId.of(it)
            }
        }
    }

    suspend fun getTimeFormat(): Int? {
        return timeFormat.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getTimeFormat")
                return null
            }
            userSettingsRepository.getTimeFormat(userId, database)
        }
    }

    suspend fun getDefaultCalendarIdWithFallback(allowShared: Boolean): String? {
        return defaultCalendarId.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getDefaultCalendarId")
                return null
            }
            calendarsRepository.getDefaultCalendarIdWithFallback(userId.id, allowShared)
        }
    }

    suspend fun getAutoDetectPrimaryTimezone(): Boolean? {
        return autoDetectPrimaryTimezone.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getAutoDetectPrimaryTimezone")
                return null
            }
            calendarsRepository.selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId.id)?.toBoolean()
        }
    }

    suspend fun getDisplayWeekNumber(): Boolean? {
        return displayWeekNumber.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getDisplayWeekNumber")
                return null
            }
            calendarsRepository.selectCalendarUserSettingsDisplayWeekNumber(userId.id)?.toBoolean()
        }
    }

    suspend fun getWeekStart(): Int? {
        return weekStart.value ?: run {
            val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
            if (userId == null) {
                logger.e("User ID was null in CalendarViewModel getWeekStart")
                return null
            }
            userSettingsRepository.getWeekStart(userId, database)
        }
    }

    // TODO Reduce code duplication with getMonthViewEventsMap
    fun getMonthViewSkeletonEventsMap(
        events: List<SkeletonEvent>,
        fromDate:LocalDate,
        maxEventCount: Int,
        timeZoneId: String
    ): Map<Int, List<MonthView.MonthViewEvent>> {

        val monthGridMap = mutableMapOf<Int, ArrayList<Event>>()
        // Split the events for each day of the month
        events.forEach { skeletonEvent ->
            val partTimeEndsOnMidnight = (!skeletonEvent.isAllDay() && skeletonEvent.getOccurrenceEnd(timeZoneId) .toLocalTime() == LocalTime.MIDNIGHT)
            var start = skeletonEvent.getOccurrenceStart(timeZoneId).toLocalDate()
            val end = skeletonEvent.getOccurrenceEnd(timeZoneId).toLocalDate()

            // Use !start.isAfter(end) to iterate inclusive
            while (!start.isAfter(end)) {
                val dayIndex = ChronoUnit.DAYS.between(fromDate, start).toInt()
                val current = monthGridMap[dayIndex]
                if (current?.contains(skeletonEvent) == false) {
                    current.add(skeletonEvent)
                }
                monthGridMap[dayIndex] = current ?: arrayListOf(skeletonEvent)
                start = start.plusDays(1)

                // All day events end on next day 00:00 so we need to break loop to exclude end day
                if (start == end && (skeletonEvent.isAllDay() || partTimeEndsOnMidnight)) break
            }
        }

        val monthViewEventsMap = hashMapOf<Int, List<MonthView.MonthViewEvent>>()

        monthGridMap.forEach {
            // Filter out the events spanning multiple days if it is not the first day
            val filteredList = it.value.filterNot { event ->
                it.key != 0 &&
                        !event.spansSingleDay(timeZoneId = timeZoneId) &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(it.key.toLong()),
                            timeZoneId
                        ).first > 1
            }
            // Sort the list for the month view
            val sortedList: MutableList<Event> = filteredList.sortForMonthView(timeZoneId).toMutableList()
            it.value.clear()
            it.value.addAll(sortedList)
        }

        val rootMap: MutableMap<Int, Map<Int, Event>> = mutableMapOf()
        for (key in 0 until MonthView.MonthViewSettings.MONTH_GRID_ITEMS_MAX) {
            val eventList = monthGridMap[key]

            val childMap = mutableMapOf<Int, Event>()
            if (key > 0) {
                // Insert the events spanning multiple days depending on the previous day list, in order to extend the multi day event on this day with the same index
                val previousChildMap = rootMap[key - 1]
                previousChildMap?.forEach { (index, event) ->
                    if (!event.spansSingleDay(timeZoneId = timeZoneId) &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(key.toLong()),
                            timeZoneId
                        ).first > 1) {
                        childMap[index] = event
                    }
                }
            }
            var nextAvailableMapIndex = 0
            // Fill the remaining indexes with the sorted events list
            eventList?.forEach { event ->
                while (childMap.containsKey(nextAvailableMapIndex)) nextAvailableMapIndex++
                if (nextAvailableMapIndex >= maxEventCount + MonthView.MonthViewSettings.MINI_EVENTS_MAX + 1) return@forEach
                childMap[nextAvailableMapIndex] = event
                nextAvailableMapIndex++
            }

            rootMap[key] = childMap
        }

        // Transform the child map of events to a list of MonthViewEvent
        rootMap.forEach { (dayIndex, childMap) ->

            val monthViewEvents = arrayListOf<MonthView.MonthViewEvent>()

            childMap.forEach { (indexInDay, event) ->

                val fullDayCounter = event.calculateFullDayCounter(
                    fromDate.plusDays(dayIndex.toLong()),
                    timeZoneId
                )

                monthViewEvents.add(
                    MonthView.MonthViewEvent(
                        indexInDay = indexInDay,
                        daySpanCount = fullDayCounter.second,
                        daySpanIndex = fullDayCounter.first,
                        calendarColor = resourceProvider.provideColor(R.color.interaction_weak_norm),
                        pastEvent = event.isInThePast(timeZoneId),
                        isUnanswered = false,
                        strikeThroughTitle = false,
                        decryptionFailed = false,
                        eventTitle = null
                    )
                )
            }
            monthViewEventsMap[dayIndex] = monthViewEvents
        }

        return monthViewEventsMap
    }

    // TODO Reduce code duplication with getMonthViewSkeletonEventsMap
    fun getMonthViewEventsMap(
        events: List<UiEvent>,
        fromDate: LocalDate,
        maxEventCount: Int,
        isSkeletonEvent: Boolean
    ): Map<Int, List<MonthView.MonthViewEvent>> {

        val monthGridMap = mutableMapOf<Int, ArrayList<UiEvent>>()
        // Split the events for each day of the month
        events.forEach { skeletonEvent ->
            val partTimeEndsOnMidnight = (!skeletonEvent.isAllDay && skeletonEvent.dateEnd.toLocalTime() == LocalTime.MIDNIGHT)
            var start = skeletonEvent.dateStart.toLocalDate()
            val end = skeletonEvent.dateEnd.toLocalDate()

            // Use !start.isAfter(end) to iterate inclusive
            while (!start.isAfter(end)) {
                val dayIndex = ChronoUnit.DAYS.between(fromDate, start).toInt()
                val current = monthGridMap[dayIndex]
                if (current?.contains(skeletonEvent) == false) {
                    current.add(skeletonEvent)
                }
                monthGridMap[dayIndex] = current ?: arrayListOf(skeletonEvent)
                start = start.plusDays(1)

                // All day events end on next day 00:00 so we need to break loop to exclude end day
                if (start == end && (skeletonEvent.isAllDay || partTimeEndsOnMidnight)) break
            }
        }

        val monthViewEventsMap = hashMapOf<Int, List<MonthView.MonthViewEvent>>()

        monthGridMap.forEach {
            // Filter out the events spanning multiple days if it is not the first day
            val filteredList = it.value.filterNot { event ->
                it.key != 0 &&
                        !event.spansSingleDay() &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(it.key.toLong())
                        ).first > 1
            }
            // Sort the list for the month view
            val sortedList: MutableList<UiEvent> = filteredList.sortForMonthView().toMutableList()
            it.value.clear()
            it.value.addAll(sortedList)
        }

        val rootMap: MutableMap<Int, Map<Int, UiEvent>> = mutableMapOf()
        for (key in 0 until MonthView.MonthViewSettings.MONTH_GRID_ITEMS_MAX) {
            val eventList = monthGridMap[key]

            val childMap = mutableMapOf<Int, UiEvent>()
            if (key > 0) {
                // Insert the events spanning multiple days depending on the previous day list, in order to extend the multi day event on this day with the same index
                val previousChildMap = rootMap[key - 1]
                previousChildMap?.forEach { (index, event) ->
                    if (!event.spansSingleDay() &&
                        event.calculateFullDayCounter(
                            fromDate.plusDays(key.toLong())
                        ).first > 1) {
                        childMap[index] = event
                    }
                }
            }
            var nextAvailableMapIndex = 0
            // Fill the remaining indexes with the sorted events list
            eventList?.forEach { event ->
                while (childMap.containsKey(nextAvailableMapIndex)) nextAvailableMapIndex++
                if (nextAvailableMapIndex >= maxEventCount + MonthView.MonthViewSettings.MINI_EVENTS_MAX + 1) return@forEach
                childMap[nextAvailableMapIndex] = event
                nextAvailableMapIndex++
            }

            rootMap[key] = childMap
        }

        // Transform the child map of events to a list of MonthViewEvent
        rootMap.forEach { (dayIndex, childMap) ->

            val monthViewEvents = arrayListOf<MonthView.MonthViewEvent>()

            childMap.forEach { (indexInDay, event) ->

                val fullDayCounter = event.calculateFullDayCounter(
                    fromDate.plusDays(dayIndex.toLong())
                )

                monthViewEvents.add(
                    MonthView.MonthViewEvent(
                        indexInDay = indexInDay,
                        daySpanCount = fullDayCounter.second,
                        daySpanIndex = fullDayCounter.first,
                        calendarColor = if (isSkeletonEvent) resourceProvider.provideColor(R.color.interaction_weak_norm)
                        else Color.parseColor(event.displayColor),
                        pastEvent = event.isInThePast(),
                        isUnanswered = !event.isCancelled() && event.participationStatus == ParticipationStatus.NEEDS_ACTION,
                        strikeThroughTitle = event.isCancelled() || event.participationStatus == ParticipationStatus.DECLINED,
                        decryptionFailed = event.decryptionStatus is Event.DecryptionStatus.Failure,
                        eventTitle = if (isSkeletonEvent) null
                        else event.summary?.nullIfBlank() ?: resourceProvider.provideString(R.string.default_event_summary)
                    )
                )
            }
            monthViewEventsMap[dayIndex] = monthViewEvents
        }

        return monthViewEventsMap
    }

    suspend fun initManagedHolidayCalendar(userId: UserId) {
        calendarsRepository.refreshManagedHolidayCalendars(userId)
    }

    suspend fun hasManagedHolidayCalendarListInDb(): Boolean {
        return database.managedHolidayCalendarDao().hasCalendar()
    }

    suspend fun hasHolidayCalendars(): Boolean {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        if (userId == null) {
            logger.e("User ID was null in CalendarViewModel hasHolidayCalendars")
            return false
        }
        return calendarsRepository.hasHolidayCalendars(userId.id)
    }

    suspend fun fixCalendars(): LiveData<Operation.State> {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        return FixCalendarsWorker.enqueue(workManager, userId?.id.orEmpty())
    }

    suspend fun migrateEventMetadataToOccurrences(): LiveData<Operation.State> {
        val userId = userId.value ?: accountManager.getPrimaryUserId().firstOrNull()
        return MigrateEventMetadataToOccurrencesWorker.enqueue(workManager, userId = userId?.id.orEmpty())
    }

    fun shouldDisplayServerDownBanner(): LiveData<Boolean> {
        return calendarsRepository.getDisplayServerDownBannerFlow().asLiveData(Dispatchers.Default)
    }

    fun hideServerDownBanner() {
        return calendarsRepository.hideServerDownBanner()
    }
}
