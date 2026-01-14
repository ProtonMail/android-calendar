package me.proton.android.calendar.presentation.calendar.viewModel

import android.app.Application
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import androidx.work.WorkManager
import biweekly.component.VAlarm
import biweekly.parameter.ParticipationLevel
import biweekly.parameter.ParticipationStatus
import biweekly.parameter.Related
import biweekly.property.Attendee
import biweekly.property.Organizer
import biweekly.property.Trigger
import biweekly.util.DayOfWeek
import biweekly.util.Duration
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import me.proton.android.calendar.R
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.FormValidation
import me.proton.android.calendar.common.getUserOrNull
import me.proton.android.calendar.common.getUserSettingsEntity
import me.proton.android.calendar.common.timezoneApiOverrides
import me.proton.android.calendar.common.utils.AndroidUtils.formatSendPreferencesError
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.tryCast
import me.proton.android.calendar.common.utils.AndroidUtils.tryCastOrNull
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.isLastDayOfWeekInMonth
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toBiweeklyDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekInMonth
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.utils.EventUtilsImpl.getSingleEditOriginalOccurrenceNumber
import me.proton.android.calendar.common.utils.EventUtilsImpl.updateParticipationStatus
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.adjustRRuleToStartDate
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isCalendarChangeAllowed
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.parseICalString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.printToString
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStartTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.wrapInICalendar
import me.proton.android.calendar.common.utils.IcsSurgeryUtils.cleanRRule
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.isShortDomainAddress
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.worker.UpdateParticipationStatusSingleEditWorker
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.MeetIntegrationType
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.android.calendar.domain.usecase.GetCanonicalEmailsUseCase
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.HandleDeleteUseCase
import me.proton.android.calendar.domain.usecase.HandleSaveUseCase
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.android.calendar.domain.usecase.SendEmailUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateParticipationStatusUseCase
import me.proton.android.calendar.domain.usecase.UpdatePersonalPartUseCase
import me.proton.android.calendar.domain.usecase.UpgradeEventUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.configuration.EnvironmentConfigurationDefaults
import me.proton.core.domain.entity.UserId
import me.proton.core.mailmessage.domain.entity.Email
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.user.domain.extension.hasSubscriptionForMail
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.filterNullValues
import me.proton.core.util.kotlin.removeFirst
import me.proton.core.util.kotlin.toBoolean
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class EventViewModel @Inject constructor(
    application: Application,
    private val userManager: UserManager,
    private val userAddressManager: UserAddressManager,
    private val calendarsRepository: CalendarsRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val transformEventUseCase: TransformEventUseCase,
    private val eventDecryptor: EventDecryptor,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val sendEmailUseCase: SendEmailUseCase,
    private val logger: Logger,
    private val json: Json,
    private val getCanonicalEmailsUseCase: GetCanonicalEmailsUseCase,
    private val obtainSendPreferencesUseCase: ObtainSendPreferencesUseCase,
    private val handleSaveUseCase: HandleSaveUseCase,
    private val handleDeleteUseCase: HandleDeleteUseCase,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val resourceProvider: ResourceProvider,
    private val widgetRefresher: WidgetRefresher,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val database: AppDatabase,
    private val upgradeEventUseCase: UpgradeEventUseCase,
    private val workManager: WorkManager,
    private val updatePersonalPartUseCase: UpdatePersonalPartUseCase
) : AndroidViewModel(application) {

    sealed class InitResult {
        object Success : InitResult()
        object OccurrenceDoesNotExist : InitResult()
        object EventDoesNotExist : InitResult()
        class InitEventSuccess(val event: Event) : InitResult()
        class InitDefaultCalendarSuccess(val calendar: Calendar?) : InitResult()
        sealed class Error(val message: String): InitResult() {
            class Default(val errorMessage: String) : Error(errorMessage)
            class InitDefaultCalendarError(val errorMessage: String) : Error(errorMessage)
        }
    }

    sealed class EventDetailsActionType {
        object Edit : EventDetailsActionType()
        object Delete : EventDetailsActionType()
    }

    private var viewModelJob = Job() // TODO extract this to superclass
    private var coroutineScope = CoroutineScope(Dispatchers.Default)
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private lateinit var userId: UserId

    private var timeStartBackup: LocalTime? = null
    private var timeEndBackup: LocalTime? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var eventEdited = false
    private var editMode = false
    private var isCreate = false
    private var meetIntegrations = emptySet<MeetIntegrationType>()

    private lateinit var event: Event

    // Original event from database, from before it has been edited
    private var dbEvent: Event? = null

    private var eventCustomPartialDayAlarmsSave: ArrayList<VAlarm>? = null
    private var eventCustomAllDayAlarmsSave: ArrayList<VAlarm>? = null

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    lateinit var calendarSettings: CalendarSettingsEntity

    private var originalDbEvent: Event? = null

    private val _event = MutableLiveData<Event?>() // TODO see if there's less ugly way
    val eventLiveData: LiveData<Event?> = _event

    // TimeZone used when displaying event is taken from settings
    lateinit var displayTimeZoneId: String

    // TimeZone for editing event is always event's own timezone, or default
    lateinit var eventTimeZoneId: String

    lateinit var calendarUserSettings: CalendarUserSettingsEntity
    lateinit var userSettings: UserSettingsEntity
    lateinit var user: User

    var rruleManuallyEdited: Boolean = false
    private var singleEditsInfo: SingleEditsInfo? = null

    val eventDetailsState: MutableStateFlow<EventState> = MutableStateFlow(EventState.Idle)
    val eventFormState: MutableStateFlow<EventState> = MutableStateFlow(EventState.Idle)

    val eventDetailsSnackState: MutableStateFlow<EventSnackState?> = MutableStateFlow(null)
    val eventFormSnackState: MutableStateFlow<EventSnackState?> = MutableStateFlow(null)

    val attendeeAnswerState: MutableStateFlow<Pair<ParticipationStatus, Boolean>?> = MutableStateFlow(null)

    private var currentParticipationStatus: ParticipationStatus = ParticipationStatus.NEEDS_ACTION

    // TODO Remove once we allow creating events with email notifications
    var hasEmailNotifications: Boolean = false

    // Recurrence temp values
    var tempRecurrenceUntilLocalDate: LocalDate? = null
    var tempMonthlyRepeatOption: MonthlyRepeatOnOption = MonthlyRepeatOnOption.ON_DAY_X

    sealed class EventState {

        // TODO
        object Idle: EventState()
        object UserAddressInvalidForEncryption: EventState()

        sealed class Processing: EventState() {
            object Saving: Processing()
            object Deleting: Processing()
            object EditLoading: Processing()
        }
    }

    sealed class EventSnackState {

        data class DisplaySnack(
            val message: String
        ): EventSnackState()

        data class DisplaySnackWithUriAction(
            val message: String,
            val action: String,
            val uri: String
        ): EventSnackState()

        data class DisplaySnackReturnToMonth(
            val message: String,
            val newSelectedDate: LocalDate? = null,
            val newSelectedTime: LocalTime? = null
        ): EventSnackState()
    }

    enum class ChangeAnswerRecurringDialogType {
        OVERWRITE,
        SINGLE_EDIT,
        DEFAULT
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    /**
     * Resets the EventViewModel data with its default values
     */
    private fun resetEventViewModelValues(editMode: Boolean) {
        if (editMode) {
            eventFormState.value = EventState.Idle
            eventFormSnackState.value = null
        } else {
            eventDetailsState.value = EventState.Idle
            eventDetailsSnackState.value = null
        }

        attendeeAnswerState.value = null

        // reset backup values
        timeStartBackup = null
        timeEndBackup = null

        eventEdited = false
        eventCustomPartialDayAlarmsSave = null
        eventCustomAllDayAlarmsSave = null
        dbEvent = null
        originalDbEvent = null
        rruleManuallyEdited = false
        singleEditsInfo = null
        tempRecurrenceUntilLocalDate = null
        hasEmailNotifications = false

        _event.postValue(null)
    }

    /**
     * Calling this method first is mandatory before using the EventViewModel as it initialises it with all the necessary values.
     */
    suspend fun initialise(
        userId: UserId,
        editMode: Boolean,
        meetIntegrations: Set<MeetIntegrationType>,
        eventId: String?,
        occurrenceNumber: Int?,
        initStartDate: String?,
        initStartTime: String?,
        initEndDate: String? = null,
        initEndTime: String? = null,
        isAllDay: Boolean? = null,
        timeZoneId: String? = null,
        title: String? = null,
        description: String? = null,
        location: String? = null,
        rRule: String? = null
    ): InitResult {

        resetEventViewModelValues(editMode)

        this.editMode = editMode
        this.userId = userId
        this.isCreate = eventId == null
        this.meetIntegrations = meetIntegrations

        // Default calendar and its settings is only needed in create mode
        val defaultCalendar: Calendar? =
            if (isCreate) {
                // Get default calendar and its settings if we are in create mode
                val initializeDefaultCalendarResult = initializeDefaultCalendar()
                if (initializeDefaultCalendarResult !is InitResult.InitDefaultCalendarSuccess) {
                    // Handle initialisation error
                    return initializeDefaultCalendarResult
                } else {
                    // Use default calendar
                    initializeDefaultCalendarResult.calendar
                }
            } else null

        // Default calendar is mandatory on event creation
        if (eventId == null && defaultCalendar == null) return InitResult.Error.InitDefaultCalendarError("EventViewModel: could not get default calendar")

        calendarUserSettings = calendarsRepository.selectCalendarUserSettings(userId.id)
            ?: return InitResult.Error.Default("EventViewModel: could not get Calendar User Settings")

        userSettings = userSettingsRepository.getUserSettingsEntity(userId, database)

        user = userManager.getUserOrNull(userId, logger) ?: return InitResult.Error.Default("EventViewModel: could not get User")

        displayTimeZoneId = calendarUserSettings.primaryTimezone

        event = if (eventId == null) {

            val initialiseCreateEventResult = initialiseNewEvent(
                defaultCalendar!!,
                initStartDate,
                initStartTime,
                initEndDate,
                initEndTime,
                isAllDay,
                timeZoneId,
                title,
                description,
                location,
                rRule
            )
            if (initialiseCreateEventResult !is InitResult.InitEventSuccess) {
                // Handle initialisation error
                return initialiseCreateEventResult
            } else {
                // Use initialised event
                initialiseCreateEventResult.event
            }

        } else {

            val initialiseEditEventResult = initialiseExistingEvent(
                eventId,
                occurrenceNumber
            )
            if (initialiseEditEventResult !is InitResult.InitEventSuccess) {
                // Handle initialisation error
                return initialiseEditEventResult
            } else {
                // Use initialised event
                initialiseEditEventResult.event
            }
        }

        hasEmailNotifications = event.hasEmailNotifications
        if (editMode && !isCreate) saveUserEditedAlarms()

        _event.postValue(event)

        return InitResult.Success
    }

    /**
     * @returns Default calendar (and load calendar settings to be stored in calendarSettings) or InitResult error.
     */
    private suspend fun initializeDefaultCalendar(): InitResult {
        // Get default calendar
        val defaultCalendar = calendarsRepository.getDefaultCalendarIdWithFallback(userId.id, allowShared = true)?.let {
            calendarsRepository.selectCalendar(it) ?: return InitResult.Error.InitDefaultCalendarError("EventViewModel: failed to select default calendar")
        } ?: return InitResult.Error.InitDefaultCalendarError("EventViewModel: no active calendars for user")

        // Load settings for given calendar id and stores them in calendarSettings
        if (!loadSettingsForCalendar(defaultCalendar.id)) return InitResult.Error.Default("EventViewModel: could not get CalendarSettings")

        return InitResult.InitDefaultCalendarSuccess(defaultCalendar)
    }

    /**
     * @returns Initialised new event or InitResult error
     */
    private fun initialiseNewEvent(
        defaultCalendar: Calendar,
        initStartDate: String?,
        initStartTime: String?,
        initEndDate: String?,
        initEndTime: String?,
        isAllDay: Boolean?,
        timeZoneId: String?,
        title: String?,
        description: String?,
        location: String?,
        rRule: String?
    ): InitResult {

        eventTimeZoneId = timeZoneId ?: displayTimeZoneId

        val newICalendar =
            if (!rRule.isNullOrEmpty()) {
                val newEventToPrefill = ICalUtilsImpl.createNewVEvent().wrapInICalendar()
                // Create a temporary RRULE to replace
                newEventToPrefill.events.first().setRecurrenceRule(Recurrence.Builder(Frequency.DAILY).build())
                val newEventIcs = newEventToPrefill.printToString()
                // Replace temporary RRULE with the one provided in args
                val rRuleRegex = Regex("RRULE:.*\\r?\\n")
                val prefilledNewEventIcs = newEventIcs.replace(rRuleRegex, "RRULE:$rRule\r\n")
                // Parse prefilled event ics
                parseICalString(prefilledNewEventIcs) ?: return InitResult.Error.Default("could not parse Event using parseICalString method")
            } else {
                ICalUtilsImpl.createNewVEvent().wrapInICalendar()
            }
        val newVEvent = newICalendar.events.first()

        // If there is no requested start date, we take today
        val startDate =
            if (initStartDate != null) LocalDate.parse(initStartDate)
            else ZonedDateTime.now(ZoneId.of(eventTimeZoneId)).toLocalDate()

        // If there is no requested start time, we calculate it according to "now"
        val startTime =
            if (initStartTime != null) LocalTime.parse(initStartTime)
            else ZonedDateTime.of(startDate, LocalTime.of(8, 0), ZoneId.of(eventTimeZoneId)).toLocalTime()

        var endDate =
            if (initEndDate != null) LocalDate.parse(initEndDate)
            else startDate
        val endTime =
            if (initEndTime != null) LocalTime.parse(initEndTime)
            else {
                if (startTime.toSecondOfDay() + this.calendarSettings.defaultEventDuration.times(60) > LocalTime.MAX.toSecondOfDay()) {
                    endDate = endDate.plusDays(1)
                }
                startTime.plusMinutes(this.calendarSettings.defaultEventDuration.toLong())
            }

        timeStartBackup = startTime
        timeEndBackup = endTime // this time can be before timeStartBackup at this point

        // TODO GUI takes timezone from iCalendar's "default timezone", maybe this should be moved to "Event" model?
        newICalendar.setDefaultTimeZone(eventTimeZoneId)

        if (initStartTime == null) { // create new all-day event

            newVEvent.setStart(startDate)

            // event end time goes over midnight
            if (endDate.dayOfYear != startDate.dayOfYear) {
                newVEvent.setEnd(endDate.minusDays(1))
                timeEndBackup = timeStartBackup // workaround TODO we could force-change date-end to next-day
            } else {
                newVEvent.setEnd(endDate)
            }

        } else if (isAllDay == true) {
            // create new all-day event
            newVEvent.setStart(startDate)
            newVEvent.setEnd(endDate)
        } else { // create new part-day event
            newVEvent.setStart(startDate, startTime, eventTimeZoneId)
            newVEvent.setEnd(endDate, endTime, eventTimeZoneId)
            newICalendar.setStartTimeZone(eventTimeZoneId)
            newICalendar.setEndTimeZone(eventTimeZoneId)
        }

        if (!newVEvent.cleanRRule(newICalendar, isImport = true, isOpeningFromProtonMail = false)) {
            return InitResult.Error.Default("invalid RRULE in prefilled event")
        }

        val newEvent = Event.from(
            ICalUtilsImpl.generateOfflineEventId(), Calendar(
                defaultCalendar.id,
                defaultCalendar.name,
                defaultCalendar.email,
                defaultCalendar.ownerEmail,
                defaultCalendar.description,
                defaultCalendar.color,
                defaultCalendar.priority,
                defaultCalendar.addressId,
                defaultCalendar.memberId,
                defaultCalendar.flags,
                defaultCalendar.display,
                defaultCalendar.type,
                defaultCalendar.permissions,
                defaultCalendar.defaultEventDuration,
                defaultCalendar.defaultPartDayNotifications,
                defaultCalendar.defaultFullDayNotifications
            ), newICalendar, 0
        ) ?: return InitResult.Error.Default("could not create Event using factory method")

        newEvent.setDefaultAlarms()

        // Prefill
        title?.let { newEvent.iCalEvent.setSummary(title) }
        description?.let { newEvent.iCalEvent.setDescription(description) }
        location?.let { newEvent.iCalEvent.setLocation(location) }

        return InitResult.InitEventSuccess(newEvent)
    }

    /**
     * @returns Initialised existing event or InitResult error
     */
    private suspend fun initialiseExistingEvent(
        eventId: String,
        occurrenceNumber: Int?
    ): InitResult {

        val dbEventEntity = calendarsRepository.selectEventEntity(eventId)
        dbEvent = if (dbEventEntity != null) {
            if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                eventDecryptor.decrypt(dbEventEntity)
            } else {
                transformEventUseCase.execute(dbEventEntity)
            }
        } else null

        if (dbEvent == null) return InitResult.EventDoesNotExist

        if (meetIntegrations.isNotEmpty()) {
            dbEvent?.removeConferenceDescription()
        }

        val eventStartTimeZone =
            dbEvent?.iCalendar?.timezoneInfo?.getTimezone(dbEvent?.iCalEvent?.dateStart)?.timeZone?.id
                ?: displayTimeZoneId

        eventTimeZoneId = eventStartTimeZone

        val timeZoneForOccurrence =
            if (editMode) eventTimeZoneId
            else displayTimeZoneId

        // we have to generate occurrence in event's timezone, because otherwise we will overwrite it with default calendar's timezone
        val dbEventWithOccurrence = occurrenceNumber?.let {
            dbEvent?.let {
                Event.withOccurrence(
                    it,
                    occurrenceNumber,
                    timeZoneForOccurrence
                )
            }
        }
        // return error only if event dbEvent is recurring, if it's a single edit it's okay that occurrence can't be generated
        if (occurrenceNumber != null && (dbEvent?.isRecurring() == true) && dbEventWithOccurrence == null) return InitResult.OccurrenceDoesNotExist

        if (editMode) {
            // Load the settings for the event's calendar
            val event = dbEventWithOccurrence ?: dbEvent ?: return InitResult.Error.Default("EventViewModel: event was null when loading settings for calendar in EventViewModel")
            loadSettingsForCalendar(event.calendar.id)
        }

        val adjustedEvent =
            (dbEventWithOccurrence ?: Event.from(dbEvent!!)).apply {

                if (this.isAllDay()) { // adjust endDate to -1 day if event has no time
                    this.iCalEvent.setEnd(this.getEnd(timeZoneForOccurrence).toLocalDate().minusDays(1))
                }

                // default timezone in iCalendar is used for GUI
                this.iCalendar.setDefaultTimeZone(timeZoneForOccurrence)

                if (editMode) {
                    // Setup event time backup values
                    if (this.isAllDay()) {
                        val startTime = ICalUtilsImpl.generateEventStart(ZoneId.of(eventTimeZoneId)).toLocalTime()
                        timeStartBackup = startTime
                        timeEndBackup =
                            startTime.plusMinutes(this@EventViewModel.calendarSettings.defaultEventDuration.toLong())
                    } else {
                        timeStartBackup = this.getStart(timeZoneForOccurrence).toLocalTime()
                        timeEndBackup = this.getEnd(timeZoneForOccurrence).toLocalTime()
                    }

                    // Clone RRule from original event in DB if we are in edit mode
                    val eventUid = dbEvent?.uid
                    if (dbEvent?.isSingleEdit() == true && eventUid != null) {
                        // We store reference to originalDbEvent for later use
                        originalDbEvent = calendarsRepository.selectRootEventEntity(eventUid)
                            ?.let { if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                                eventDecryptor.decrypt(it)
                            } else {
                                transformEventUseCase.execute(it)
                            } }
                        this.iCalEvent.recurrenceRule = originalDbEvent?.iCalEvent?.recurrenceRule
                    }
                }

            }

        return if (adjustedEvent != null) {

            if (dbEvent != null &&
                listOf(adjustedEvent).filterOutOccurrencesByExdates(dbEvent!!, timeZoneForOccurrence).isEmpty()
            ) {
                return InitResult.OccurrenceDoesNotExist
            }

            InitResult.InitEventSuccess(adjustedEvent)

        } else InitResult.Error.Default("EventViewModel: could not generate event with occurrence in EventViewModel")
    }

    /**
     * Loads calendar settings for given calendar id and stores them locally if it exists
     */
    private suspend fun loadSettingsForCalendar(calendarId: String): Boolean {
        val settings = calendarsRepository.selectCalendarSettings(calendarId)
        if (settings != null) calendarSettings = settings
        return settings != null
    }

    data class SingleEditsInfo(
        val singleEdits: List<Event>?,
        val hasSingleEdit: Boolean,
        val hasFutureSingleEdit: Boolean?, // Set to null if not checked or not applicable
        val hasAnsweredSingleEdit: Boolean?, // Set to null if not checked or not applicable
        val hasNonCancelledSingleEdit: Boolean? // Set to null if not checked or not applicable
    )

    /**
     * @returns SingleEditsInfo object if it exists, or fetches data and creates it
     */
    suspend fun getSingleEditsInfo(userEmails: List<String>? = null): SingleEditsInfo? {

        if (singleEditsInfo == null) {

            val event = _event.value ?: return null
            val dbEvent = dbEvent ?: return null

            val occurrenceStart = event.getOccurrenceStart(eventTimeZoneId)
            val occurrence = event.occurrence
            val allowShowThisAndFuture =
                occurrence?.occurrenceNumber != null &&
                        occurrence.occurrenceNumber > 1 &&
                        !event.isEventFirstOccurrence(dbEvent, eventTimeZoneId)

            // We check for single edits only once and in initialise because it may require API calls
            singleEditsInfo =
                if (occurrence?.occurrenceNumber == 1 && !allowShowThisAndFuture &&
                    (editMode || !event.isAnInvitation && userEmails != null)) {

                    // We don't have option "this and future" when updating first event in chain
                    // TODO Decide behavior if API call was an error and method returns null
                    val hasSingleEdit = dbEvent.isRecurring() && calendarsRepository.hasSingleEdits(userId, dbEvent.uid) == true

                    SingleEditsInfo(
                        singleEdits = null,
                        hasSingleEdit = hasSingleEdit,
                        hasFutureSingleEdit = null,
                        hasAnsweredSingleEdit = null,
                        hasNonCancelledSingleEdit = null
                    )
                } else {

                    // TODO Decide behavior if API call was an error and method returns null
                    val singleEdits = calendarsRepository.getSingleEdits(
                        userId,
                        dbEvent.uid,
                        if (editMode || !event.isAnInvitation && userEmails != null) occurrenceStart
                        else null, // Fetch all SE when event has attendees in order to check for hasAnsweredSingleEdit
                        if (editMode || !event.isAnInvitation && userEmails != null) eventTimeZoneId
                        else null
                    )

                    val hasSingleEdit = !singleEdits.isNullOrEmpty()
                    val hasFutureSingleEdit = singleEdits?.any { it.getStart(eventTimeZoneId).isAfter(occurrenceStart) } ?: false
                    val hasAnsweredSingleEdit = singleEdits?.any {
                        // We only need hasAnsweredSingleEdit for change answer in event details view (if event has attendees)
                        if (!editMode && event.isAnInvitation && userEmails != null && !it.isCancelled()) {
                            // The only values we need are Accepted, Declined and Tentative
                            val participationStatus = it.getParticipationStatus(userEmails)
                            participationStatus == ParticipationStatus.ACCEPTED ||
                                    participationStatus == ParticipationStatus.DECLINED ||
                                    participationStatus == ParticipationStatus.TENTATIVE
                        } else false
                    } ?: false
                    val hasNonCancelledSingleEdit = singleEdits?.any { it.isCancelled().not() } ?: true

                    SingleEditsInfo(
                        singleEdits = singleEdits,
                        hasSingleEdit = hasSingleEdit,
                        hasFutureSingleEdit = hasFutureSingleEdit,
                        hasAnsweredSingleEdit = hasAnsweredSingleEdit,
                        hasNonCancelledSingleEdit = hasNonCancelledSingleEdit
                    )
                }
        }

        return singleEditsInfo
    }

    /**
     * Resets temporary values for Recurrence
     */
    fun initialiseForRecurrence() {
        this.tempMonthlyRepeatOption = MonthlyRepeatOnOption.ON_DAY_X
        val until = event.iCalEvent.recurrenceRule?.value?.until?.toZonedDateTime(eventTimeZoneId) ?: return
        this.tempRecurrenceUntilLocalDate = until.toLocalDate()
    }

    /**
     * Saves form data in iCalendar, but doesn't emit new LiveData
     * because the changes are already there in user interface.
     */
    fun persistRecurrenceFormData(summary: String?, location: String?, description: String?) {

        if (!this::event.isInitialized) return

        if (event.iCalEvent.summary?.value != summary ||
            event.iCalEvent.location?.value != location ||
            event.iCalEvent.description?.value != description
        ) {
            markEventAsEdited()
        }

        event.iCalEvent.setSummary(summary)
        event.iCalEvent.setLocation(location)
        event.iCalEvent.setDescription(description)
    }

    /**
     * Checks if end date/time is not before start date/time
     */
    fun validateDateTime(): Boolean = if (this::event.isInitialized) {
        !(event.getEnd(eventTimeZoneId).isBefore(event.getStart(eventTimeZoneId)))
    } else false

    // alarm temp values
    private var tempAlarmSendByOption: SendByOption = SendByOption.NOTIFICATION
    var tempAlarmTime: LocalTime = LocalTime.of(9, 0)

    fun isAlarmLimitReached() = this.event.alarms.size >= FormValidation.ALARM_COUNT_MAX

    fun isCalendarChangeAllowed(): Boolean {
        return dbEvent?.let {
            isCalendarChangeAllowed(it, this.event)
        } ?: true
    }

    fun isOriginalEventPartOfChain(): Boolean {
        return dbEvent?.isPartOfChain() == true
    }

    fun isEventAnInvitation(): Boolean {
        return dbEvent?.let { fromEvent ->
            val toEvent = this.event
            val isFromEventAnInvitation =
                fromEvent.iCalEvent.attendees?.isNotEmpty() == true || fromEvent.iCalEvent.organizer != null

            val isCurrentEventAnInvitation =
                toEvent.iCalEvent.attendees?.isNotEmpty() == true || toEvent.iCalEvent.organizer != null

            return isFromEventAnInvitation || isCurrentEventAnInvitation
        } ?: false
    }

    fun hasCalendarBeenChanged() = dbEvent?.calendar?.id != null && dbEvent?.calendar?.id != event.calendar.id

    fun isChangingAttendeesAllowed() = !hasCalendarBeenChanged()

    /**
     * Resets temporary values for Alarm.
     */
    fun initialiseForAlarm() {
        this.tempAlarmSendByOption = SendByOption.NOTIFICATION
        this.tempAlarmTime = LocalTime.of(9, 0)
    }

    suspend fun handleCalendar(calendar: Calendar): Boolean {
        val isCalendarBeingChanged = dbEvent?.calendar?.id != null && dbEvent?.calendar?.id != calendar.id

        // If user choice has been saved then we don't set calendar's default alarms
        val alarmsEdited = (event.isAllDay() && eventCustomAllDayAlarmsSave != null) ||
                (!event.isAllDay() && eventCustomPartialDayAlarmsSave != null)
        return if (loadSettingsForCalendar(calendar.id)) {
            markEventAsEdited()
            if (event.iCalEvent.organizer != null) {
                val organizerEmail = calendar.email
                event.iCalEvent.organizer = Organizer(organizerEmail, organizerEmail)
            }
            event = Event.from(
                event,
                calendar = Calendar(
                    calendar.id,
                    calendar.name,
                    calendar.email,
                    calendar.ownerEmail,
                    calendar.description,
                    calendar.color,
                    calendar.priority,
                    calendar.addressId,
                    calendar.memberId,
                    calendar.flags,
                    calendar.display,
                    calendar.type,
                    calendar.permissions,
                    calendar.defaultEventDuration,
                    calendar.defaultPartDayNotifications,
                    calendar.defaultFullDayNotifications
                ),
                color = if (event.color == event.calendar.color) calendar.color
                else event.color
            )

            // when changing calendar, don't apply its default alarms
            if (!alarmsEdited && (!isCalendarBeingChanged || dbEvent?.isAllDay() != event.isAllDay())) {
                event.setDefaultAlarms()
            }
            _event.postValue(event)
            true
        } else {
            false
        }
    }

    fun handleColor(colorHex: String) {
        markEventAsEdited()
        event = Event.from(
            event,
            color = colorHex
        )
        _event.postValue(event)
    }

    fun handleTimeZone(orgTimeZoneId: String) {
        val timeZoneId = timezoneApiOverrides[orgTimeZoneId] ?: orgTimeZoneId
        markEventAsEdited()
        val old = event.getStart(eventTimeZoneId)
        event.iCalendar.setDefaultTimeZone(timeZoneId)
        eventTimeZoneId = timeZoneId
        event.iCalendar.adjustRRuleToStartDate(old)
        _event.postValue(event)
    }

    fun handleStartDate(newDate: LocalDate) {
        markEventAsEdited()
        val old = event.getStart(eventTimeZoneId)
        val endDate = event.getEnd(eventTimeZoneId)
        val unit = ChronoUnit.DAYS
        val diff = unit.between(old, endDate)
        when {
            diff > 0 -> handleEndDate(newDate.plusDays(diff))
            diff < 0 -> handleEndDate(newDate.minusDays(diff))
            else -> handleEndDate(newDate)
        }
        if (event.isAllDay()) {
            event.iCalEvent.setStart(newDate)
        } else {
            event.iCalEvent.setStart(newDate, old.toLocalTime(), eventTimeZoneId)
        }
        event.iCalendar.adjustRRuleToStartDate(old)
        _event.postValue(event)
    }

    fun handleEndDate(newDate: LocalDate) {
        markEventAsEdited()
        val old = event.getEnd(eventTimeZoneId)
        if (event.isAllDay()) {
            event.iCalEvent.setEnd(newDate)
        } else {
            event.iCalEvent.setEnd(newDate, old.toLocalTime(), eventTimeZoneId)
        }
        _event.postValue(event)
    }

    fun handleStartTime(newTime: LocalTime) {
        markEventAsEdited()
        val old = event.getStart(eventTimeZoneId)
        val endTime = event.getEnd(eventTimeZoneId)
        val newDate = LocalDateTime.of(old.toLocalDate(), newTime.truncatedTo(ChronoUnit.MINUTES))
        val unit = ChronoUnit.MINUTES
        val diff = unit.between(old, endTime)
        val newEndTime = newDate.plusMinutes(diff)
        if (newEndTime.dayOfWeek != endTime.dayOfWeek)
            handleEndDate(newEndTime.toLocalDate())
        handleEndTime(newEndTime.toLocalTime())
        event.iCalEvent.setStart(old.toLocalDate(), newTime, eventTimeZoneId)
        timeStartBackup = newTime
        _event.postValue(event)
    }

    fun handleEndTime(newTime: LocalTime) {
        markEventAsEdited()
        val old = event.getEnd(eventTimeZoneId)
        event.iCalEvent.setEnd(old.toLocalDate(), newTime, eventTimeZoneId)
        timeEndBackup = newTime
        _event.postValue(event)
    }

    fun handleAllDaySwitch(isAllDay: Boolean) {
        if (event.isAllDay() == isAllDay) return
        val timeStart = timeStartBackup
        val timeEnd = timeEndBackup
        if (timeStart == null || timeEnd == null) return
        markEventAsEdited()
        if (isAllDay) {
            // remove time part and timezone from start/end
            event.iCalEvent.setStart(event.getStart(eventTimeZoneId).toLocalDate())
            event.iCalEvent.setEnd(event.getEnd(eventTimeZoneId).toLocalDate())
        } else {
            // get times & timezone from backup, but date from current event date
            event.iCalEvent.setStart(
                event.getStart(eventTimeZoneId).toLocalDate(),
                timeStart,
                eventTimeZoneId
            )

            event.iCalEvent.setEnd(
                event.getEnd(eventTimeZoneId).toLocalDate(),
                timeEnd,
                eventTimeZoneId
            )
        }

        // If user choice has been saved then we don't set calendar's default alarms
        // if calendar has been changed during this editing, set its default alarms
        if ((isAllDay && eventCustomAllDayAlarmsSave == null && (!hasCalendarBeenChanged() || isAllDay != dbEvent?.isAllDay())) ||
            (!isAllDay && eventCustomPartialDayAlarmsSave == null && (!hasCalendarBeenChanged() || isAllDay != dbEvent?.isAllDay()))) {
            event.setDefaultAlarms()
        } else {
            event.clearAlarms()
            // If user choice has been saved then use it even if alarm list is empty
            val savedAlarms =
                if (isAllDay) eventCustomAllDayAlarmsSave?.toList() else eventCustomPartialDayAlarmsSave?.toList()
            savedAlarms?.let { event.addAlarms(it) }
        }

        event.iCalendar.adjustRRuleToStartDate()

        _event.postValue(event)
    }

    /**
     * Handles simple Recurrence Rule like weekly or monthly.
     *
     * @param frequency if null, removes entire recurrence rule
     */
    fun handleRecurrence(
        frequency: Frequency?,
        untilDate: Boolean,
        interval: Int? = null,
        count: Int? = null,
        daysOfWeek: List<DayOfWeek>? = null,
        customMonthly: Boolean = false
    ) {
        val builder = Recurrence.Builder(frequency)

        if (frequency != null) {
            interval?.let {
                if (it > 1) {
                    builder.interval(it)
                }
            }
            count?.let {
                builder.count(it)
            }
            if (untilDate && tempRecurrenceUntilLocalDate != null) {
                val until = if (event.isAllDay()) {
                    ICalDate(tempRecurrenceUntilLocalDate!!.toDate(ZoneId.systemDefault().id), false)
                } else {
                    ICalDate(
                        Date.from(
                            ZonedDateTime.of(
                                tempRecurrenceUntilLocalDate!!,
                                LocalTime.of(23, 59, 59),
                                ZoneId.of(eventTimeZoneId)
                            ).withZoneSameInstant(ZoneId.of(eventTimeZoneId)).toInstant()
                        ), true
                    )
                }
                builder.until(until)
            }
            daysOfWeek?.let {
                builder.byDay(daysOfWeek)
            }
            if (customMonthly) {
                val eventStartDate = event.getStart(eventTimeZoneId).toLocalDate()
                val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()
                val weekInMonth = eventStartDate.weekInMonth()

                when (tempMonthlyRepeatOption) {
                    MonthlyRepeatOnOption.ON_DAY_X -> {
                    }
                    MonthlyRepeatOnOption.ON_X_WEEKDAY -> {
                        builder.byDay(iCalDayOfWeek) // TODO be careful about using .byDay(ByDay(int, weekday)) because it uses different notation
                        builder.bySetPos(weekInMonth)
                    }
                    MonthlyRepeatOnOption.ON_LAST_WEEKDAY -> {
                        builder.byDay(iCalDayOfWeek)
                        builder.bySetPos(-1)
                    }
                }
            }
        }

        val recurrence = if (frequency != null) builder.build() else null
        if (event.iCalEvent.recurrenceRule?.value == recurrence) return

        markEventAsEdited()
        rruleManuallyEdited = true
        event.iCalEvent.setRecurrenceRule(recurrence)
        _event.postValue(event)
    }

    private fun markEventAsEdited() {
        eventEdited = true
    }

    fun hasEventBeenEdited(): Boolean {
        return eventEdited
    }

    fun handleRecurrenceUntilDate(untilLocalDate: LocalDate?) {
        markEventAsEdited()
        tempRecurrenceUntilLocalDate = untilLocalDate
    }

    enum class MonthlyRepeatOnOption {
        ON_DAY_X,
        ON_X_WEEKDAY,
        ON_LAST_WEEKDAY
    }

    /**
     * Complicated logic for displaying monthly recurrence options is calculated by ViewModel.
     */
    fun calculateMonthlyRepeatOnOptions(): List<MonthlyRepeatOnOption> {
        val eventStartDate = event.getStart(eventTimeZoneId).toLocalDate()

        val options = mutableListOf(MonthlyRepeatOnOption.ON_DAY_X)
        if (eventStartDate.weekInMonth() <= 4) options.add(MonthlyRepeatOnOption.ON_X_WEEKDAY)
        if (eventStartDate.isLastDayOfWeekInMonth()) options.add(MonthlyRepeatOnOption.ON_LAST_WEEKDAY)

        return options
    }

    /**
     * Complicated logic for determining selected recurrence option is calculated by ViewModel.
     */
    fun calculateMonthlyRepeatOnOptionIndex(): Int {

        val repeatOptions = calculateMonthlyRepeatOnOptions()

        event.iCalEvent.recurrenceRule?.value?.run {

            if (this.frequency != Frequency.MONTHLY) return 0

            val eventStartDate = event.getStart(eventTimeZoneId).toLocalDate()

            val iCalDayOfWeek = eventStartDate.dayOfWeek.toBiweeklyDayOfWeek()

            val eventDaySetPos = this.bySetPos.getOrNull(this.byDay.indexOfFirst { it.day == iCalDayOfWeek })

            if (eventDaySetPos != null) {
                if (eventDaySetPos in 1..4) {
                    return 1
                } else if (eventDaySetPos == -1) {
                    return repeatOptions.lastIndex
                }
            }
        }

        return 0 // default: Recurrence Rule never ends
    }

    fun handleRecurrenceRepeatOn(monthlyRepeatOnOption: MonthlyRepeatOnOption) {
        markEventAsEdited()
        this.tempMonthlyRepeatOption = monthlyRepeatOnOption
    }

    enum class SendByOption {
        NOTIFICATION,
        EMAIL
    }

    fun handleAlarmSendBy(option: SendByOption) {
        markEventAsEdited()
        this.tempAlarmSendByOption = option
    }

    fun handleAlarmTime(time: LocalTime) {
        markEventAsEdited()
        this.tempAlarmTime = time
    }

    fun handleAlarm(alarmTypeOption: Int, count: Int? = null, countTypeOption: Int? = null, isAllDay: Boolean): VAlarm? {
        val duration = if (isAllDay) {
            when (alarmTypeOption) {
                0 -> Duration.builder().prior(false).hours(9).build() // on the day at 9:00
                1 -> Duration.builder().prior(true).hours(6).build() // day before at 18:00
                2 -> Duration.builder().prior(true).days(6).hours(15).build() // 1 week before at 9:00, -P6DT15H
                3 -> Duration.builder().prior(true).weeks(2).days(6).hours(15)
                    .build() // 3 weeks before at 9:00, -P2W6DT15H
                4 -> null // all-day alarms have 1 fewer option
                5 -> { // custom

                    // -P6DT15H 1 week before at 9
                    // -P6DT23H59M 1 week before at 00:01

                    if (count != null && countTypeOption != null) {
                        Duration.builder().apply {

                            val alarmAtMidnight = tempAlarmTime == LocalTime.MIDNIGHT

                            // We hide minutes and hours buttons and use same radio group so days and weeks have id 2 & 3
                            when (countTypeOption) {
                                2 -> {
                                    prior(true)
                                    val adjustedDays = count - 1 + (if (alarmAtMidnight) 1 else 0)
                                    if (adjustedDays > 0) days(adjustedDays)
                                    if (count == 0) days(count)

                                    val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong())
                                        .minusMinutes(tempAlarmTime.minute.toLong())

                                    if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                    if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
                                }
                                3 -> {
                                    prior(true)
                                    val adjustedWeeks = count - 1 + (if (alarmAtMidnight) 1 else 0)
                                    if (adjustedWeeks > 0) weeks(adjustedWeeks)

                                    if (!alarmAtMidnight) {
                                        days(7 - 1)
                                    }

                                    val negativeTimeOfDay = LocalTime.of(0, 0).minusHours(tempAlarmTime.hour.toLong())
                                        .minusMinutes(tempAlarmTime.minute.toLong())

                                    if (negativeTimeOfDay.hour > 0) hours(negativeTimeOfDay.hour)
                                    if (negativeTimeOfDay.minute > 0) minutes(negativeTimeOfDay.minute)
                                }
                                // On the day at x
                                4 -> {
                                    prior(false)
                                    val positiveTimeOfDay = LocalTime.of(0, 0).plusHours(tempAlarmTime.hour.toLong())
                                        .plusMinutes(tempAlarmTime.minute.toLong())

                                    if (positiveTimeOfDay.hour > 0) hours(positiveTimeOfDay.hour)
                                    if (positiveTimeOfDay.minute > 0) minutes(positiveTimeOfDay.minute)

                                    // on the same day at 00:00 which means "at the time of the event"
                                    if (tempAlarmTime == LocalTime.MIDNIGHT) seconds(0)
                                }
                            }
                        }.build()
                    } else null
                }
                else -> null
            }
        } else { // partial-day trigger can contain only one component
            when (alarmTypeOption) {
                0 -> Duration.builder().prior(false).seconds(0).build() // at the time of event
                1 -> Duration.builder().prior(true).minutes(10).build()
                2 -> Duration.builder().prior(true).minutes(30).build()
                3 -> Duration.builder().prior(true).hours(1).build()
                4 -> Duration.builder().prior(true).weeks(1).build()
                5 -> { // custom
                    Duration.builder().apply {
                        prior(true)
                        when (countTypeOption) {
                            0 -> minutes(count)
                            1 -> hours(count)
                            2 -> days(count)
                            3 -> weeks(count)
                        }
                    }.build()
                }
                else -> null
            }
        }

        duration?.apply {
            markEventAsEdited()

            val alarm = when (tempAlarmSendByOption) {
                SendByOption.NOTIFICATION -> VAlarm.display(Trigger(duration, Related.START), null)
                SendByOption.EMAIL -> VAlarm.email(Trigger(duration, Related.START), null, null, emptyList())
            }

            return alarm
        }

        return null
    }

    fun saveAlarm(alarm: VAlarm) {
        if (event.alarms.any { it.isTheSameAs(alarm) }) {
            eventFormSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_notification_already_added)
            )
            return
        }
        event.addAlarms(listOf(alarm))
        saveUserEditedAlarms()
        _event.postValue(event)
    }

    fun handleAlarmDelete(alarm: VAlarm) {
        markEventAsEdited()
        event.removeAlarm(alarm)
        saveUserEditedAlarms()
        _event.postValue(event)
    }

    private fun saveUserEditedAlarms() {
        // If an action is done on alarms we go into edited alarm mode and save the user choice over default alarms
        if (event.isAllDay()) eventCustomAllDayAlarmsSave = ArrayList(event.alarms)
        else eventCustomPartialDayAlarmsSave = ArrayList(event.alarms)
    }

    fun hasExDates(afterSelectedEvent: Boolean = false): Boolean {
        val immutableOriginalEvent =
            if (event.isSingleEdit()) originalDbEvent
            else dbEvent

        immutableOriginalEvent?.let { dbEvent ->
            return if (afterSelectedEvent) {
                val exZonedDateTimes =
                    dbEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                        exDates.values.map { exDate ->
                            exDate.toZonedDateTime(eventTimeZoneId)
                        }
                    }
                exZonedDateTimes.firstOrNull {
                    it.isAfter(event.getStart(eventTimeZoneId))
                } != null
            } else {
                dbEvent.isRecurring() && !dbEvent.iCalEvent.exceptionDates.isNullOrEmpty()
            }
        }
        return false
    }

    fun hasRecurrenceRuleBeenEdited(): Boolean {
        val immutableOriginalEvent =
            if (event.isSingleEdit()) originalDbEvent
            else dbEvent
        return immutableOriginalEvent?.iCalEvent?.recurrenceRule != event.iCalEvent.recurrenceRule
    }

    suspend fun allowSendForCalendarAddress(): Boolean {
        val user =
            if (this::user.isInitialized) user
            else {
                logger.i("EventViewModel: User was null in allowSend")
                return false
            }
        val email = event.calendar.email
        return user.hasSubscriptionForMail() || !isShortDomainAddress(email)
    }

    private suspend fun updateCalendarDisplay(calendar: Calendar, display: Boolean) {
        // 1. Update in DB
        calendarsRepository.updateCalendarDisplay(calendar.id, display)
        // 2. Update on Server
        updateCalendarUseCase.executeUpdateDisplayFromDb(userId, calendar.id)
    }

    fun removeConferenceLink() {
        markEventAsEdited()
        event.removeConference()
        _event.postValue(event)
    }

    fun handleAttendee(attendee: Attendee, canonicalEmail: String = "", addAttendee: Boolean = true) {
        markEventAsEdited()
        if (addAttendee) {
            attendee.commonName = ""
            attendee.rsvp = true
            attendee.participationLevel = ParticipationLevel.REQUIRED
            attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
            val token = ICalUtilsImpl.generateXPmToken(canonicalEmail, event.uid)
            attendee.addParameter(X_PM_TOKEN, token)
            event.iCalEvent.addAttendee(
                attendee
            )
            if (event.iCalEvent.organizer == null) {
                val organizerEmail = event.calendar.email
                event.iCalEvent.organizer = Organizer(organizerEmail, organizerEmail)
            }
        } else {
            event.iCalEvent.attendees.removeFirst { it.extractEmail()?.equalsNoCase(attendee.extractEmail()) == true }
        }
        _event.postValue(event)
    }

    data class SendPreferencesResults(
        val sendPreferences: Map<Email, SendPreferences>,
        val emailErrors: Map<String, ObtainSendPreferencesUseCase.Result.Error>
    )

    private suspend fun getSendPreferences(emails: List<String>): SendPreferencesResults {
        // get send preferences and check if attendees have disabled email addresses
        val canonicalEmails = getCanonicalEmailsUseCase.invoke(userId, emails)

        val sendPreferencesResults = obtainSendPreferencesUseCase.execute(userId, canonicalEmails.filterNullValues())

        val emailErrors = hashMapOf<String, ObtainSendPreferencesUseCase.Result.Error>()
        val sendPreferences = sendPreferencesResults.mapValues {
            when (val result = it.value) {
                is ObtainSendPreferencesUseCase.Result.Success -> result.sendPreferences
                ObtainSendPreferencesUseCase.Result.Error.AddressDisabled -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.AddressDisabled
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.GettingContactPreferences -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.GettingContactPreferences
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.NetworkError -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.NetworkError
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.TrustedKeysInvalid -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.TrustedKeysInvalid
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.PublicKeysInvalid -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.PublicKeysInvalid
                    null
                }
                ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys -> {
                    emailErrors[it.key] = ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys
                    null
                }
            }
        }.filterNullValues()

        return SendPreferencesResults(sendPreferences, emailErrors)
    }

    private fun formatSendPreferencesEmailsWithError(sendPreferencesResults: SendPreferencesResults): String {
        return TextUtils.join("\n• ", sendPreferencesResults.emailErrors.map { entry ->
            resourceProvider.provideString(
                R.string.event_send_prefs_error_template,
                entry.key,
                resourceProvider.provideString(entry.value.formatSendPreferencesError())
            )
        })
    }

    enum class SaveResult {
        SUCCESS,
        CREATE_ERROR_SEND_MAIL,
        EDIT_ERROR_SEND_MAIL,
        USER_ADDRESS_INVALID_FOR_ENCRYPTION, // TODO remove the hack when UserAddress problem is solved
        LOST_ZOOM_ACCESS,
        ZOOM_MEETING_DOES_NOT_EXIST,
        ERROR
    }

    /**
     * This method starts the edit / create flow
     */
    suspend fun onSaveClick(
        displayDialog: BaseDialogFragment.DisplayDialog,
        eventId: String?,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean
    ) {

        if (validateDateTime()) {

            // Allow saving with no edition if creating an event
            if (eventId.isNullOrEmpty() || hasEventBeenEdited()) {

                // Update Event Form state
                eventFormState.value = EventState.Processing.Saving

                val originalOccurrenceNumber =
                    if (event.isSingleEdit() && originalDbEvent != null && occurrenceNumber == 0) {
                        // If event is a single edit and occurrence number is 0, check that we are using the correct original event occurrence number
                        event.getSingleEditOriginalOccurrenceNumber(originalDbEvent!!, eventTimeZoneId) ?: occurrenceNumber
                    } else occurrenceNumber

                if (eventLiveData.value?.iCalEvent?.attendees.isNullOrEmpty().not()
                    || dbEvent?.iCalEvent?.attendees.isNullOrEmpty().not()) {

                    // Handle edit / create for event with attendees
                    saveEventWithAttendees(
                        displayDialog,
                        eventId,
                        originalOccurrenceNumber,
                        timeFormatIs24Hour
                    )
                } else {

                    // Handle edit / create for event without attendees
                    saveEvent(
                        displayDialog,
                        mapOf(),
                        originalOccurrenceNumber,
                        timeFormatIs24Hour
                    )
                }
            }
        } else {

            // Invalid date / time error
            eventFormSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.event_alert_invalid_start_end_date)
            )
        }
    }

    /**
     * @returns show the save option picker dialog (This / This and future / All)
     */
    private fun showSaveOptionPicker(event: Event?): Boolean {
        return !isEventNew() && (event?.isRecurring() == true || event?.isPartOfChain() == true) && !event.isSingleOccurrenceRecurring(displayTimeZoneId)
    }

    private fun isEventNew() = !event.isSyncedWithApi()

    private fun removedAttendees() = dbEvent?.iCalEvent?.attendees?.filter { dbEventAttendee ->
        !event.iCalEvent.attendees.map { it.extractEmail() }.contains(dbEventAttendee.extractEmail())
    } ?: emptyList()

    private fun addedAttendees() = event.iCalEvent.attendees.filter { eventAttendee ->
        dbEvent?.iCalEvent?.attendees?.map { it.extractEmail() }?.contains(eventAttendee.extractEmail()) == false
    }

    private fun needSendAnEmailUpdate(
        occurrenceNumber: Int
    ): Boolean {
        val eventTimeZone = event.defaultTimeZone ?: eventTimeZoneId
        val dbEventTimeZone = dbEvent?.defaultTimeZone ?: eventTimeZoneId
        // TODO SAME FOR SINGLE EDITS ?
        val dateChanged =
            if (dbEvent?.isRecurring() == true) {
                // For recurring DB event we need to generate corresponding occurrence
                val occurrence = dbEvent?.generateOccurrence(occurrenceNumber, dbEventTimeZone)
                val adjustAllDayEndDate = if (dbEvent?.isAllDay() == true) 1L else 0L
                occurrence?.startDateTime != event.getStart(eventTimeZone) ||
                        occurrence.endDateTime.minusDays(adjustAllDayEndDate) != event.getEnd(eventTimeZone)
            } else {
                val adjustAllDayEndDate = if (dbEvent?.isAllDay() == true) 1L else 0L
                dbEvent?.getStart(dbEventTimeZone) != event.getStart(eventTimeZone) ||
                        dbEvent?.getEnd(dbEventTimeZone)?.minusDays(adjustAllDayEndDate) != event.getEnd(eventTimeZone)
            }
        return dbEvent?.summary != event.summary
                || dbEvent?.description != event.description
                || dbEvent?.location != event.location
                || dateChanged
                || dbEvent?.iCalEvent?.recurrenceRule != event.iCalEvent.recurrenceRule
    }

    /**
     * Show dialog notifying the user that an invitation will be sent to attendees
     */
    private suspend fun saveEventWithAttendees(
        displayDialog: BaseDialogFragment.DisplayDialog,
        eventId: String?,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean
    ) {
        val isAddParticipantsToRecurring = showSaveOptionPicker(dbEvent)
        if (dbEvent == null || dbEvent?.iCalEvent?.attendees.isNullOrEmpty()) {
            // Create an event with attendees / add attendees to an event
            if (isAddParticipantsToRecurring && !eventId.isNullOrEmpty()) {
                // Create a recurring event with attendees / add attendees to a recurring event
                val singleEditsInfo = getSingleEditsInfo()
                // Display Add Participants Dialog (adding attendees to an existing event)
                uiScope.launch {
                    displayDialog.alertDialog(
                        resourceProvider.provideString(R.string.event_add_participants_dialog_title),
                        resourceProvider.provideString(
                            if (hasExDates() || singleEditsInfo?.hasSingleEdit == true) R.string.recurring_event_add_participants_overwrite_dialog_description
                            else R.string.recurring_event_add_participants_dialog_description
                        ),
                        resourceProvider.provideString(R.string.event_add_participants_dialog_confirm),
                        resourceProvider.provideString(R.string.event_add_participants_dialog_cancel),
                        object: BaseDialogFragment.DialogListener {
                            override fun onPositive(selectedItem: Int) {
                                coroutineScope.launch {
                                    saveEventWithAttendeesSendPreferences(
                                        displayDialog,
                                        isAddParticipantsToRecurring,
                                        occurrenceNumber,
                                        timeFormatIs24Hour
                                    )
                                }
                            }
                            override fun onNegative() { eventFormState.value = EventState.Idle
                            }
                            override fun onCancel() { eventFormState.value = EventState.Idle
                            }
                            override fun onDismiss() {}
                        }
                    )
                }
            } else {
                // Create a single event with attendees / add attendees to a single event
                // Display Send Invitation Dialog
                uiScope.launch {
                    displayDialog.alertDialog(
                        resourceProvider.provideString(R.string.event_send_invite_dialog_title),
                        resourceProvider.provideString(R.string.event_send_invite_dialog_description),
                        resourceProvider.provideString(R.string.event_send_invite_dialog_confirm),
                        resourceProvider.provideString(R.string.event_send_invite_dialog_cancel),
                        object: BaseDialogFragment.DialogListener {
                            override fun onPositive(selectedItem: Int) {
                                coroutineScope.launch {
                                    saveEventWithAttendeesSendPreferences(
                                        displayDialog,
                                        isAddParticipantsToRecurring,
                                        occurrenceNumber,
                                        timeFormatIs24Hour
                                    )
                                }
                            }
                            override fun onNegative() { eventFormState.value = EventState.Idle
                            }
                            override fun onCancel() { eventFormState.value = EventState.Idle
                            }
                            override fun onDismiss() {}
                        }
                    )
                }
            }
        } else {
            // Update existing invitation
            // Changes not requiring email update
            //  Start / end or time zone if it doesn't change UTC time
            //  Notifications
            //  Calendar

            // Changes requiring email update
            //  Title
            //  Description
            //  Location
            //  Start / end or time zone if it changes UTC time
            //  Recurrence rule
            val addedAttendees = addedAttendees().isNotEmpty()
            val removedAttendees = removedAttendees().isNotEmpty()
            if (needSendAnEmailUpdate(occurrenceNumber)) {
                // Send an email update
                if (event.isRecurring()) {
                    if (removedAttendees || addedAttendees) {
                        // Display participants changes dialog
                        val message =
                            if (removedAttendees && addedAttendees) {
                                // Add and remove participants
                                resourceProvider.provideString(R.string.recurring_event_update_add_and_remove_participants_dialog_description)
                            } else if (addedAttendees) {
                                // Add participants
                                resourceProvider.provideString(R.string.recurring_event_update_add_participants_dialog_description)
                            } else if (event.iCalEvent.attendees.isNullOrEmpty()) {
                                // Remove all participants
                                resourceProvider.provideString(R.string.recurring_event_update_remove_all_participants_dialog_description)
                            } else {
                                // Remove participants
                                resourceProvider.provideString(R.string.recurring_event_update_remove_participants_dialog_description)
                            }
                        uiScope.launch {
                            displayDialog.alertDialog(
                                resourceProvider.provideString(R.string.event_save_changes),
                                message,
                                resourceProvider.provideString(R.string.action_save),
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object: BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            // Check send preferences for existing + added / removed attendees
                                            saveEventWithAttendeesSendPreferences(
                                                displayDialog,
                                                isAddParticipantsToRecurring,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate = true,
                                                notifyChangedAttendees = true
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    } else {
                        // Display update all events and send invitation Dialog
                        uiScope.launch {
                            displayDialog.alertDialog(
                                resourceProvider.provideString(R.string.update_recurring_event),
                                resourceProvider.provideString(R.string.update_recurring_invite_send_email),
                                resourceProvider.provideString(R.string.dialog_button_update),
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object : BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            saveEventWithAttendeesSendPreferences(
                                                displayDialog,
                                                isAddParticipantsToRecurring,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate = true
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    }
                } else {
                    if (removedAttendees || addedAttendees) {
                        // Display participants changes dialog
                        val message =
                            if (removedAttendees && addedAttendees) {
                                // Add and remove participants
                                resourceProvider.provideString(R.string.event_update_add_and_remove_participants_dialog_description)
                            } else if (addedAttendees) {
                                // Add participants
                                resourceProvider.provideString(R.string.event_update_add_participants_dialog_description)
                            } else if (event.iCalEvent.attendees.isNullOrEmpty()) {
                                // Remove all participants
                                resourceProvider.provideString(R.string.event_update_remove_all_participants_dialog_description)
                            } else {
                                // Remove participants
                                resourceProvider.provideString(R.string.event_update_remove_participants_dialog_description)
                            }
                        uiScope.launch {
                            displayDialog.alertDialog(
                                resourceProvider.provideString(R.string.event_save_changes),
                                message,
                                resourceProvider.provideString(R.string.action_save),
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object: BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            // Check send preferences for existing + added / removed attendees
                                            saveEventWithAttendeesSendPreferences(
                                                displayDialog,
                                                isAddParticipantsToRecurring,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate = true,
                                                notifyChangedAttendees = true
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    } else {
                        // Display send update invitation Dialog
                        uiScope.launch {
                            displayDialog.alertDialog(
                                resourceProvider.provideString(R.string.update_event),
                                resourceProvider.provideString(R.string.update_invite_send_email),
                                resourceProvider.provideString(R.string.dialog_button_update),
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object : BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            saveEventWithAttendeesSendPreferences(
                                                displayDialog,
                                                isAddParticipantsToRecurring,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate = true
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    }
                }
            } else {
                // Do not send an email update
                if (event.isRecurring()) {
                    if (removedAttendees || addedAttendees) {
                        // Display participants changes dialog
                        var title = ""
                        var message = ""
                        var positiveButton = ""
                        if (removedAttendees && addedAttendees) {
                            // Add and remove participants
                            title = resourceProvider.provideString(R.string.event_save_changes)
                            message = resourceProvider.provideString(R.string.recurring_event_add_and_remove_participants_dialog_description)
                            positiveButton = resourceProvider.provideString(R.string.action_save)
                        } else if (addedAttendees) {
                            // Add participants
                            title = resourceProvider.provideString(R.string.event_add_participants_dialog_title)
                            message = resourceProvider.provideString(R.string.recurring_event_add_participants_dialog_description)
                            positiveButton = resourceProvider.provideString(R.string.action_add)
                        } else {
                            // Remove participants
                            title = resourceProvider.provideString(R.string.event_remove_participants_dialog_title)
                            message = resourceProvider.provideString(R.string.recurring_event_remove_participants_dialog_description)
                            positiveButton = resourceProvider.provideString(R.string.action_remove)
                        }
                        uiScope.launch {
                            displayDialog.alertDialog(
                                title,
                                message,
                                positiveButton,
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object: BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            // Check send preferences for added / removed attendees
                                            saveEventWithAttendeesSendPreferences(
                                                displayDialog,
                                                isAddParticipantsToRecurring,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate = false,
                                                notifyChangedAttendees = true
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    } else {
                        // Display edit all events dialog
                        uiScope.launch {
                            displayDialog.alertDialog(
                                resourceProvider.provideString(R.string.update_recurring_event),
                                resourceProvider.provideString(R.string.update_recurring_all_events),
                                resourceProvider.provideString(R.string.dialog_button_update),
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object : BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            // Save event
                                            handleSave(
                                                EventEditDeleteOption.ALL_EVENTS,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                mapOf(),
                                                sendEmailUpdate = false
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    }
                } else {
                    if (removedAttendees || addedAttendees) {
                        // Display participants changes dialog
                        var title = ""
                        var message = ""
                        var positiveButton = ""
                        if (removedAttendees && addedAttendees) {
                            // Add and remove participants
                            title = resourceProvider.provideString(R.string.event_save_changes)
                            message = resourceProvider.provideString(R.string.event_add_and_remove_participants_dialog_description)
                            positiveButton = resourceProvider.provideString(R.string.action_save)
                        } else if (addedAttendees) {
                            // Add participants
                            title = resourceProvider.provideString(R.string.event_add_participants_dialog_title)
                            message = resourceProvider.provideString(R.string.event_add_participants_dialog_description)
                            positiveButton = resourceProvider.provideString(R.string.action_add)
                        } else if (removedAttendees) {
                            // Remove participants
                            title = resourceProvider.provideString(R.string.event_remove_participants_dialog_title)
                            message = resourceProvider.provideString(R.string.event_remove_participants_dialog_description)
                            positiveButton = resourceProvider.provideString(R.string.action_remove)
                        }
                        uiScope.launch {
                            displayDialog.alertDialog(
                                title,
                                message,
                                positiveButton,
                                resourceProvider.provideString(R.string.dialog_button_cancel),
                                object: BaseDialogFragment.DialogListener {
                                    override fun onPositive(selectedItem: Int) {
                                        coroutineScope.launch {
                                            // Check send preferences for added / removed attendees
                                            saveEventWithAttendeesSendPreferences(
                                                displayDialog,
                                                isAddParticipantsToRecurring,
                                                occurrenceNumber = 1,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate = false,
                                                notifyChangedAttendees = true
                                            )
                                        }
                                    }
                                    override fun onNegative() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onCancel() { eventFormState.value = EventState.Idle
                                    }
                                    override fun onDismiss() {}
                                }
                            )
                        }
                    } else {
                        // Save event
                        handleSave(
                            null,
                            occurrenceNumber = 1,
                            timeFormatIs24Hour,
                            mapOf(),
                            sendEmailUpdate = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Get send preferences for the attendees and display dialog showing which attendees can't be notified if it had send preferences errors.
     */
    private suspend fun saveEventWithAttendeesSendPreferences(
        displayDialog: BaseDialogFragment.DisplayDialog,
        isAddParticipantsToRecurring: Boolean,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean,
        sendEmailUpdate: Boolean? = null,
        notifyChangedAttendees: Boolean? = null
    ) {

        val attendeesEmails =
            if (sendEmailUpdate == true && notifyChangedAttendees == true) {
                // We get send preferences for existing, added and removed attendees
                eventLiveData.value?.iCalEvent?.attendees?.mapNotNull { it.extractEmail() }?.plus(
                    removedAttendees().mapNotNull { it.extractEmail() }
                )
            } else if (sendEmailUpdate == false && notifyChangedAttendees == true) {
                // We get send preferences for added and removed attendees
                eventLiveData.value?.iCalEvent?.attendees?.mapNotNull { it.extractEmail() }?.plus(
                    removedAttendees().mapNotNull { it.extractEmail() }
                )
            } else {
                // We get send preferences for event attendees
                eventLiveData.value?.iCalEvent?.attendees?.mapNotNull { it.extractEmail() }
            }
        if (!attendeesEmails.isNullOrEmpty()) {

            val sendPreferencesResults = getSendPreferences(attendeesEmails)
            if (sendPreferencesResults.emailErrors.isNotEmpty()) {
                // Handle send preferences errors

                if (sendPreferencesResults.emailErrors.any { it.value == ObtainSendPreferencesUseCase.Result.Error.NetworkError }) {

                    // Reset event form state, display network error snack and leave save event flow.
                    eventFormState.value = EventState.Idle
                    eventFormSnackState.value =
                        EventSnackState.DisplaySnack(resourceProvider.provideString(R.string.snack_network_error))
                    return

                } else {

                    // Get formatted list of emails with error
                    val emailsWithErrors = formatSendPreferencesEmailsWithError(sendPreferencesResults)

                    // Display send preferences error(s) dialog
                    uiScope.launch {
                        displayDialog.alertDialog(
                            resourceProvider.provideString(R.string.event_attendees_send_prefs_error_title),
                            resourceProvider.provideString(
                                if (sendPreferencesResults.sendPreferences.isEmpty()) R.string.event_attendees_send_prefs_error_none_message
                                else R.string.event_attendees_send_prefs_error_some_message,
                                emailsWithErrors
                            ),
                            resourceProvider.provideString(R.string.event_attendees_send_prefs_error_confirm),
                            resourceProvider.provideString(R.string.event_attendees_send_prefs_error_cancel),
                            object: BaseDialogFragment.DialogListener {
                                override fun onPositive(selectedItem: Int) {
                                    coroutineScope.launch {

                                        if (sendEmailUpdate == true) {
                                            // In case of edit of an invitation:
                                            // - Existing participants that have errors in send preferences are not removed from the event.
                                            // - New participants that have errors in send preferences are removed from the event.
                                            val addedAttendees = addedAttendees()
                                            eventLiveData.value?.iCalEvent?.attendees?.removeAll(
                                                addedAttendees.filter { attendee ->
                                                    sendPreferencesResults.emailErrors.any { emailError ->
                                                        attendee.extractEmail() == emailError.key
                                                    }
                                                }
                                            )
                                        } else {
                                            // Remove attendees whom emails were invalid
                                            eventLiveData.value?.iCalEvent?.attendees?.removeIf { attendee ->
                                                sendPreferencesResults.emailErrors.any { emailError ->
                                                    attendee.extractEmail() == emailError.key
                                                }
                                            }
                                        }

                                        // We continue the save flow without the invalid attendees
                                        if (isAddParticipantsToRecurring) {
                                            // Adding attendees to an existing recurring event
                                            handleSave(
                                                EventEditDeleteOption.ALL_EVENTS,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendPreferencesResults.sendPreferences,
                                                sendEmailUpdate
                                            )
                                        } else {
                                            // Create an event with attendees / Add attendees to a single event
                                            saveEvent(
                                                displayDialog,
                                                sendPreferencesResults.sendPreferences,
                                                occurrenceNumber,
                                                timeFormatIs24Hour,
                                                sendEmailUpdate
                                            )
                                        }
                                    }
                                }
                                override fun onNegative() { eventFormState.value = EventState.Idle
                                }
                                override fun onCancel() { eventFormState.value = EventState.Idle
                                }
                                override fun onDismiss() {}
                            }
                        )
                    }
                }
            } else {

                // No send preferences errors, we continue the save flow
                if (isAddParticipantsToRecurring) {
                    // Adding attendees to an existing recurring event
                    handleSave(
                        EventEditDeleteOption.ALL_EVENTS,
                        occurrenceNumber,
                        timeFormatIs24Hour,
                        sendPreferencesResults.sendPreferences,
                        sendEmailUpdate
                    )
                } else {
                    // Create an event with attendees / Add attendees to a single event
                    saveEvent(
                        displayDialog,
                        sendPreferencesResults.sendPreferences,
                        occurrenceNumber,
                        timeFormatIs24Hour,
                        sendEmailUpdate
                    )
                }
            }
        } else {

            // Fallback to saving an event without attendees (should not happen as we check attendees email when adding them)
            saveEvent(displayDialog, mapOf(), occurrenceNumber, timeFormatIs24Hour)
        }
    }

    /**
     * Check if we need to display the save with options dialog or finish the save event flow
     */
    private suspend fun saveEvent(
        displayDialog: BaseDialogFragment.DisplayDialog,
        sendPreferences: Map<Email, SendPreferences>,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean,
        sendEmailUpdate: Boolean? = null
    ) {
        val dbEvent = dbEvent // Immutable dbEvent
        val showSaveOptionPicker = showSaveOptionPicker(dbEvent)
        if (showSaveOptionPicker) {

            // Display save event options dialog
            saveEventWithOption(
                displayDialog,
                sendPreferences,
                occurrenceNumber,
                timeFormatIs24Hour
            )

        } else {

            val editOption =
                if (dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == true) EventEditDeleteOption.ALL_EVENTS
                else null

            // Finish the save event flow
            handleSave(
                editOption,
                occurrenceNumber = 1,
                timeFormatIs24Hour,
                sendPreferences,
                sendEmailUpdate
            )
        }
    }

    /**
     * Display save event with options dialog (This / This and future / All) and continue the flow
     */
    private suspend fun saveEventWithOption(
        displayDialog: BaseDialogFragment.DisplayDialog,
        sendPreferences: Map<Email, SendPreferences>,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean
    ) {
        val singleEditsInfo = getSingleEditsInfo()

        val dbEvent = dbEvent // Immutable dbEvent

        // Check if we show This and future option in dialog
        val showThisAndFuture = occurrenceNumber > 1 &&
                (dbEvent != null && eventLiveData.value?.isEventFirstOccurrence(
                    dbEvent,
                    displayTimeZoneId
                ) == false)

        val hasSingleEdit = singleEditsInfo?.hasSingleEdit == true
        val hasFutureSingleEdit = singleEditsInfo?.hasFutureSingleEdit == true

        // Display save recurring event options dialog
        uiScope.launch {
            displayDialog.pickerDialog(
                resourceProvider.provideString(R.string.event_text_edit_event),
                listOfNotNull(
                    resourceProvider.provideString(R.string.event_recurring_edit_this),
                    if (showThisAndFuture) resourceProvider.provideString(R.string.event_recurring_edit_this_and_future) else null,
                    resourceProvider.provideString(R.string.event_recurring_edit_all_events)
                ).toTypedArray(),
                defaultSelectedItem = 0,
                resourceProvider.provideString(R.string.dialog_button_ok),
                resourceProvider.provideString(R.string.dialog_button_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {

                            // Save option selected by user
                            val eventEditDeleteOption =
                                if (selectedItem == 0) {
                                    EventEditDeleteOption.THIS_EVENT
                                } else if (selectedItem == 1) {
                                    if (showThisAndFuture) {
                                        EventEditDeleteOption.THIS_EVENT_AND_FUTURE
                                    } else {
                                        EventEditDeleteOption.ALL_EVENTS
                                    }
                                } else { // selectedItem == 2
                                    EventEditDeleteOption.ALL_EVENTS
                                }

                            val message =
                                if (eventEditDeleteOption == EventEditDeleteOption.THIS_EVENT && rruleManuallyEdited && hasRecurrenceRuleBeenEdited()) {
                                    // Display warning dialog for this event option if recurrence rule has been edited
                                    resourceProvider.provideString(R.string.event_recurring_update_this_description)
                                } else if (eventEditDeleteOption == EventEditDeleteOption.ALL_EVENTS && (hasExDates() || hasSingleEdit)) {
                                    // Display warning dialog for all events option if has ex dates or single edits
                                    resourceProvider.provideString(R.string.event_recurring_update_all_description)
                                } else if (eventEditDeleteOption == EventEditDeleteOption.THIS_EVENT_AND_FUTURE && (hasExDates(true) || hasFutureSingleEdit)) {
                                    // Display warning dialog for all events option if has ex dates or single edits
                                    resourceProvider.provideString(R.string.event_recurring_update_all_description)
                                } else ""

                            if (message.isNotEmpty()) {

                                // Display recurring event warning dialog
                                saveEventRecurringWarningDialog(
                                    displayDialog,
                                    message,
                                    eventEditDeleteOption,
                                    sendPreferences,
                                    occurrenceNumber,
                                    timeFormatIs24Hour
                                )
                            } else {

                                // Finish save event flow
                                handleSave(
                                    eventEditDeleteOption,
                                    occurrenceNumber,
                                    timeFormatIs24Hour,
                                    sendPreferences
                                )
                            }
                        }
                    }
                    override fun onNegative() { eventFormState.value = EventState.Idle
                    }
                    override fun onCancel() { eventFormState.value = EventState.Idle
                    }
                    override fun onDismiss() {}
                }
            )
        }
    }

    /**
     * Display dialog warning the user depending on save option: displayUpdateRecurringEventDialog
     * - This: RRule changes will be lost
     * - This and future / All: previous changes will be lost if it had exception dates or single edits
     */
    private suspend fun saveEventRecurringWarningDialog(
        displayDialog: BaseDialogFragment.DisplayDialog,
        message: String,
        eventEditDeleteOption: EventEditDeleteOption,
        sendPreferences: Map<Email, SendPreferences>,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean
    ) {

        // Display recurring event warning dialog
        uiScope.launch {
            displayDialog.alertDialog(
                resourceProvider.provideString(R.string.event_recurring_update_this_title),
                message,
                resourceProvider.provideString(R.string.event_recurring_update_this_confirm),
                resourceProvider.provideString(R.string.event_recurring_update_this_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {

                            // Finish save event flow
                            handleSave(
                                eventEditDeleteOption,
                                occurrenceNumber,
                                timeFormatIs24Hour,
                                sendPreferences
                            )
                        }
                    }
                    override fun onNegative() { eventFormState.value = EventState.Idle
                    }
                    override fun onCancel() { eventFormState.value = EventState.Idle
                    }
                    override fun onDismiss() {}
                }
            )
        }
    }

    /**
     * Final step of save event flow: call HandleSaveUseCase and handle the result
     */
    private suspend fun handleSave(
        editOption: EventEditDeleteOption? = null,
        occurrenceNumber: Int,
        timeFormatIs24Hours: Boolean,
        sendPreferences: Map<Email, SendPreferences>,
        sendEmailUpdate: Boolean? = null
    ) {

        // Post saving event value to true to trigger loading state
        eventFormState.value = EventState.Processing.Saving

        // Add back the Meet/Zoom description
        if (meetIntegrations.isNotEmpty()
            && !event.meetUrl.isNullOrBlank()
            && !event.containsMeetDescription()
            && meetIntegrations.contains(event.meetType)
        ) {
            event.addMeetDescription(resourceProvider)
        }

        val eventCopy = Event.from(event)

        val handleSaveResult = handleSaveUseCase.handleSave(
            editOption,
            occurrenceNumber,
            timeFormatIs24Hours,
            sendPreferences,
            eventCopy,
            dbEvent, // TODO If Recurring, send specific occurrence or root event ?
            userSettings,
            eventTimeZoneId,
            userId,
            rruleManuallyEdited,
            isCreate,
            sendEmailUpdate
        )

        handleSaveResult.ifSuccessAndLogErrors(logger) {}

        if (handleSaveResult !is UseCase.Result.Success<*> && meetIntegrations.isNotEmpty()) {
            event.removeConferenceDescription()
        }

        // Schedule alarms if any
        handleAlarmsUseCase.execute(userId)

        var userErrorMessage: String? = null
        val saveResult =
            when (handleSaveResult) {
                is UseCase.Result.Error -> {
                    when (handleSaveResult.error) {
                        UseCase.Error.HandleSave.EditSendEmail -> SaveResult.EDIT_ERROR_SEND_MAIL
                        UseCase.Error.HandleSave.CreateSendEmail -> SaveResult.CREATE_ERROR_SEND_MAIL
                        UseCase.Error.Crypto.UserAddressInvalidForEncryption -> SaveResult.USER_ADDRESS_INVALID_FOR_ENCRYPTION
                        UseCase.Error.Sync.LostZoomAccess -> SaveResult.LOST_ZOOM_ACCESS
                        UseCase.Error.Sync.ZoomLinkDoesNotExist -> SaveResult.ZOOM_MEETING_DOES_NOT_EXIST
                        else -> {
                            userErrorMessage = handleSaveResult.userErrorMessage
                            SaveResult.ERROR
                        }
                    }
                }
                is UseCase.Result.InvalidParams -> {
                    userErrorMessage = handleSaveResult.userErrorMessage
                    SaveResult.ERROR
                }
                else -> {
                    SaveResult.SUCCESS
                }
            }

        // Handle save result
        handleSaveResult(saveResult, occurrenceNumber, userErrorMessage)
    }

    suspend fun onSavePersonalClick(
        displayDialog: BaseDialogFragment.DisplayDialog,
        eventId: String
    ) {
        // Update Event Form state
        eventFormState.value = EventState.Processing.Saving

        if (event.isRecurring() && !event.isSingleEdit()) {
            // Display edit all events dialog
            uiScope.launch {
                displayDialog.alertDialog(
                    resourceProvider.provideString(R.string.update_recurring_event),
                    resourceProvider.provideString(R.string.update_recurring_all_events),
                    resourceProvider.provideString(R.string.dialog_button_update),
                    resourceProvider.provideString(R.string.dialog_button_cancel),
                    object : BaseDialogFragment.DialogListener {
                        override fun onPositive(selectedItem: Int) {
                            coroutineScope.launch {
                                // Save event
                                handleSavePersonal(eventId)
                            }
                        }
                        override fun onNegative() {
                            eventFormState.value = EventState.Idle
                        }
                        override fun onCancel() {
                            eventFormState.value = EventState.Idle
                        }
                        override fun onDismiss() {}
                    }
                )
            }
        } else {
            // Save event
            handleSavePersonal(eventId)
        }
    }

    private suspend fun handleSavePersonal(eventId: String) {

        // Make sure to upgrade the event first
        val upgradedEventEntity = (upgradeEventUseCase.execute(
            userId,
            eventId
        ) as? UseCase.Result.Success<*>)?.returnValue.tryCastOrNull<EventEntity>()
        if (upgradedEventEntity == null) {
            logger.e("handleSavePersonal could not upgrade Event. Failed to cast upgrade result to EventEntity")
            // Reset event form state
            eventFormState.value = EventState.Idle
            // Display error updating event snack
            eventFormSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_event_updated_error)
            )
        }

        val updatePersonalPartUseCaseUseCaseResult = updatePersonalPartUseCase.execute(
            userId,
            event.calendar.id,
            eventId,
            event.notifications.notifications,
            event.color
        )

        // Handle save result
        if (updatePersonalPartUseCaseUseCaseResult is UseCase.Result.Success<*>) {
            // Display event updated snack and return to month view with focus on the event's start date
            eventFormSnackState.value = EventSnackState.DisplaySnackReturnToMonth(
                resourceProvider.provideString(R.string.snack_event_updated),
                eventLiveData.value?.getStart(displayTimeZoneId)?.toLocalDate(),
                if (eventLiveData.value?.isAllDay() == false) eventLiveData.value?.getStart(displayTimeZoneId)?.toLocalTime()
                else null
            )
        } else {
            // Reset event form state
            eventFormState.value = EventState.Idle
            // Display error updating event snack
            eventFormSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_event_updated_error)
            )
        }
    }

    /**
     * Handle save event result
     */
    private suspend fun handleSaveResult(saveResult: SaveResult, occurrenceNumber: Int, userErrorMessage: String? = null) {
        if (eventLiveData.value?.isSyncedWithApi() == true) {

            // Save result for edit existing event
            when (saveResult) {
                SaveResult.SUCCESS -> {

                    // Refresh the widget on success
                    widgetRefresher.refreshEventList()

                    // Display the event's calendar if it was hidden
                    if (!event.calendar.display) updateCalendarDisplay(event.calendar, true)

                    // Reset event form state
                    eventFormState.value = EventState.Idle

                    val hasAttendees = !event.iCalEvent.attendees.isNullOrEmpty()
                    val addedAttendees = addedAttendees().isNotEmpty()
                    val removedAttendees = removedAttendees().isNotEmpty()
                    val successMessage =
                        if (hasAttendees || addedAttendees || removedAttendees) {
                            val sentEmailUpdate = needSendAnEmailUpdate(occurrenceNumber)
                            val isRecurring = event.isRecurring()
                            val messageResId =
                                if (addedAttendees && removedAttendees) {
                                    // Add and remove attendees
                                    if (isRecurring) R.string.success_snack_email_update_recurring
                                    else R.string.success_snack_email_update
                                } else if (addedAttendees) {
                                    // Add attendees
                                    if (isRecurring) {
                                        if (sentEmailUpdate) R.string.success_snack_email_update_recurring
                                        else R.string.success_snack_add_attendees_recurring
                                    } else {
                                        if (sentEmailUpdate) R.string.success_snack_email_update
                                        else R.string.success_snack_add_attendees
                                    }
                                } else if (removedAttendees) {
                                    // Remove attendees
                                    if (isRecurring) {
                                        if (sentEmailUpdate) R.string.success_snack_email_update_recurring
                                        else R.string.success_snack_remove_attendees_recurring
                                    } else {
                                        if (sentEmailUpdate) R.string.success_snack_email_update
                                        else R.string.success_snack_remove_attendees
                                    }
                                } else {
                                    // No change in attendees
                                    if (sentEmailUpdate) {
                                        if (isRecurring) R.string.success_snack_email_update_recurring
                                        else R.string.success_snack_email_update
                                    } else {
                                        if (isRecurring) R.string.snack_event_updated_recurring
                                        else R.string.snack_event_updated
                                    }
                                }
                            resourceProvider.provideString(messageResId)
                        } else {
                            // Normal event update success message
                            resourceProvider.provideString(R.string.snack_event_updated)
                        }

                    // Display event updated snack and return to month view with focus on the event's start date
                    eventFormSnackState.value = EventSnackState.DisplaySnackReturnToMonth(
                        successMessage,
                        eventLiveData.value?.getStart(displayTimeZoneId)?.toLocalDate(),
                        if (eventLiveData.value?.isAllDay() == false) eventLiveData.value?.getStart(displayTimeZoneId)?.toLocalTime()
                        else null
                    )

                }
                SaveResult.EDIT_ERROR_SEND_MAIL -> {

                    // Reset event form state
                    eventFormState.value = EventState.Idle

                    // Display invitation failed to be sent snack
                    eventFormSnackState.value = EventSnackState.DisplaySnack(
                        resourceProvider.provideString(
                            R.string.snack_update_event_error_failed_mail
                        )
                    )
                }
                SaveResult.USER_ADDRESS_INVALID_FOR_ENCRYPTION -> {

                    // Update event form state to handle invalid sender address issue
                    eventFormState.value = EventState.UserAddressInvalidForEncryption
                }
                SaveResult.LOST_ZOOM_ACCESS -> {

                    // Reset event form state
                    eventFormState.value = EventState.Idle

                    // Display snack
                    eventFormSnackState.value = EventSnackState.DisplaySnackWithUriAction(
                        resourceProvider.provideString(
                            R.string.snack_lost_zoom_access
                        ),
                        resourceProvider.provideString(
                            R.string.snack_lost_zoom_access_acion
                        ),
                        "https://account.${EnvironmentConfigurationDefaults.host}/calendar/security#third-party"
                    )
                }
                SaveResult.ZOOM_MEETING_DOES_NOT_EXIST -> {

                    // Reset event form state
                    eventFormState.value = EventState.Idle

                    // Display snack
                    eventFormSnackState.value = EventSnackState.DisplaySnack(
                        resourceProvider.provideString(
                            R.string.snack_zoom_meeting_does_not_exist
                        )
                    )
                }
                else -> {

                    // Reset event form state
                    eventFormState.value = EventState.Idle

                    // Display error updating event snack
                    eventFormSnackState.value = EventSnackState.DisplaySnack(
                        if (userErrorMessage.isNullOrEmpty()) {
                            resourceProvider.provideString(
                                R.string.snack_event_updated_error
                            )
                        } else {
                            userErrorMessage
                        }
                    )
                }
            }
        } else {

            // Save result for create new event
            if (saveResult == SaveResult.SUCCESS || saveResult == SaveResult.CREATE_ERROR_SEND_MAIL) {

                // Refresh the widget on success
                widgetRefresher.refreshEventList()

                // Display the event's calendar if it was hidden
                if (!event.calendar.display) updateCalendarDisplay(event.calendar, true)

                // Reset event form state
                eventFormState.value = EventState.Idle

                // Display event created snack and return to month view with focus on the event's start date
                eventFormSnackState.value = EventSnackState.DisplaySnackReturnToMonth(
                    resourceProvider.provideString(
                        if (saveResult == SaveResult.SUCCESS) R.string.snack_event_created
                        else R.string.snack_event_created_failed_mail
                    ), // Display event created but invitation failed to be sent snack
                    eventLiveData.value?.getStart(displayTimeZoneId)?.toLocalDate(),
                    if (eventLiveData.value?.isAllDay() == false) eventLiveData.value?.getStart(displayTimeZoneId)?.toLocalTime()
                    else null
                )
            } else if (saveResult == SaveResult.USER_ADDRESS_INVALID_FOR_ENCRYPTION) {

                // Update event form state to handle invalid sender address issue
                eventFormState.value = EventState.UserAddressInvalidForEncryption
            } else {

                // Reset event form state
                eventFormState.value = EventState.Idle

                // Display error creating event snack
                eventFormSnackState.value = EventSnackState.DisplaySnack(
                    if (userErrorMessage.isNullOrEmpty()) {
                        resourceProvider.provideString(
                            R.string.snack_event_created_error
                        )
                    } else {
                        userErrorMessage
                    }
                )
            }
        }
    }

    suspend fun onEditClick(
        navigateToEditForm: () -> Unit,
        navigateToEditFormPersonal: () -> Unit
    ) {
        // Post edit loading event value to true to display loading state
        eventDetailsState.value = EventState.Processing.EditLoading

        if (isRecurringInvitationWithSingleOccurrenceChanges(EventDetailsActionType.Edit)) {
            navigateToEditFormPersonal()
        } else if (event.isAnInvitation) {
            val canonicalUserEmails = userAddressManager.getAddressesOrNull(userId)?.map { address ->
                ProtonUtilsImpl.canonicalizeProtonEmail(address.email, forceCanonicalization = true)
            }
            if (!event.isUserOrganizer(canonicalUserEmails)) {
                navigateToEditFormPersonal()
            } else navigateToEditForm()
        } else if (!event.calendar.allowEditEvents) navigateToEditFormPersonal()
        else navigateToEditForm()
        eventDetailsState.value = EventState.Idle
    }

    private suspend fun isRecurringInvitationWithSingleOccurrenceChanges(
        actionType: EventDetailsActionType,
        isOrganizer: Boolean = false,
        showSnackBar: Boolean = false,
    ): Boolean {
        val event = eventLiveData.value!!
        if (event.isAnInvitation && (event.isRecurring() || event.isSingleEdit())) {
            var errorResId: Int? = null
            if (event.isSingleEdit()) {
                errorResId = when (actionType) {
                    EventDetailsActionType.Edit -> R.string.snack_event_recurring_invitation_only_supported_on_web_message
                    EventDetailsActionType.Delete -> R.string.snack_event_recurring_invitation_only_supported_on_web_message
                }
            } else {
                val immutableSingleEditsInfo = singleEditsInfo ?: getSingleEditsInfo()
                if (immutableSingleEditsInfo == null) {
                    errorResId = when (actionType) {
                        EventDetailsActionType.Edit -> R.string.snack_event_opening_edit_error
                        EventDetailsActionType.Delete -> R.string.snack_event_deleted_error
                    }
                } else if (immutableSingleEditsInfo.hasSingleEdit) {
                    errorResId = when (actionType) {
                        EventDetailsActionType.Edit -> R.string.snack_event_recurring_invitation_only_supported_on_web_message
                        EventDetailsActionType.Delete -> R.string.snack_event_recurring_invitation_only_supported_on_web_message
                    }
                } else if (hasExDates()) {
                    errorResId = when (actionType) {
                        EventDetailsActionType.Edit -> R.string.snack_event_recurring_invitation_only_supported_on_web_message
                        EventDetailsActionType.Delete -> {
                            if (!isOrganizer)
                                R.string.snack_event_recurring_invitation_only_supported_on_web_message
                            else null
                        }
                    }
                }
            }

            if (errorResId != null) {
                if (showSnackBar) {
                    eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                        resourceProvider.provideString(errorResId)
                    )
                }
                return true
            }
        }

        return false
    }

    /**
     * This method starts the delete flow
     */
    suspend fun onDeleteClick(
        displayDialog: BaseDialogFragment.DisplayDialog,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean
    ) {
        if (attendeeAnswerState.value?.second == true) {
            // Block delete event when changing answer
            return
        }

        val userAddresses = userAddressManager.getAddressesOrNull(userId)
        if (userAddresses == null) {
            logger.e("EventViewModel onDeleteClick, userAddresses == null")
            return
        }

        // Post deleting event value to true to display loading state
        eventDetailsState.value = EventState.Processing.Deleting

        val canonicalUserEmails = userAddresses.map { address ->
            ProtonUtilsImpl.canonicalizeProtonEmail(address.email, forceCanonicalization = true)
        }
        val deleteAsAnOrganizer = event.isUserOrganizer(canonicalUserEmails)
        val deleteAsAnAttendee = event.isUserAttendee(canonicalUserEmails)

        val event = eventLiveData.value!!
        val dbEvent = this.dbEvent

        if (isRecurringInvitationWithSingleOccurrenceChanges(EventDetailsActionType.Delete, deleteAsAnOrganizer, true)) {
            eventDetailsState.value = EventState.Idle
            return
        }

        val originalOccurrenceNumber =
            if (event.isSingleEdit() && occurrenceNumber == 0) {
                // If event is a single edit and occurrence number is 0, check that we are using the correct original event occurrence number
                val eventUid = dbEvent?.uid
                if (originalDbEvent == null && eventUid != null) {
                    // We only set originalDbEvent in editMode, but we do delete from details so we need to get it here
                    originalDbEvent = calendarsRepository.selectRootEventEntity(eventUid)?.let {
                        if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) eventDecryptor.decrypt(it)
                        else transformEventUseCase.execute(it)
                    }
                }
                originalDbEvent?.let {
                    event.getSingleEditOriginalOccurrenceNumber(it, eventTimeZoneId) ?: occurrenceNumber
                } ?: occurrenceNumber
            } else occurrenceNumber

        if (deleteAsAnOrganizer) { // Check deleteAsAnOrganizer before deleteAsAnAttendee because user can be both organizer and attendee

            deleteEventAsAnOrganizer(displayDialog, timeFormatIs24Hour)

        } else if (deleteAsAnAttendee) { // Check deleteAsAnAttendee after deleteAsAnOrganizer because user can be both organizer and attendee

            deleteEventAsAnAttendeeSendPreferences(displayDialog, userAddresses, event, originalOccurrenceNumber, timeFormatIs24Hour)

        } else if (event.isPartOfChain() && dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == false && event.calendar.isActive) {

            deleteRecurringEvent(displayDialog, dbEvent, originalOccurrenceNumber)

        } else {
            // TODO Check if we need to handle inactive calendars the same way
            val disabledCalendarRecurringEvent = event.calendar.isDisabled &&
                    event.isPartOfChain() &&
                    dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == false

            if (disabledCalendarRecurringEvent) {

                deleteDisabledCalendarRecurringEvent(displayDialog)
            }
            else {

                deleteEvent(displayDialog, originalOccurrenceNumber)
            }
        }
    }

    /**
     * Starts the delete flow as an organizer.
     * Display dialog notifying the user whether a cancellation email will be sent to the attendees or not, depending on its calendar status (address disabled)
     */
    private suspend fun deleteEventAsAnOrganizer(
        displayDialog: BaseDialogFragment.DisplayDialog,
        timeFormatIs24Hour: Boolean
    ) {
        val isPartOfChain = event.isPartOfChain()
        val isCalendarDisabled = event.calendar.isDisabled
        uiScope.launch {
            displayDialog.alertDialog(
                resourceProvider.provideString(
                    if (isPartOfChain) R.string.dialog_title_delete_recurring_event
                    else R.string.dialog_title_delete_event
                ),
                resourceProvider.provideString(
                    if (isPartOfChain && isCalendarDisabled) R.string.dialog_description_delete_recurring_event_as_organizer_disabled
                    else if (isPartOfChain) R.string.dialog_description_delete_recurring_event_as_organizer
                    else if (isCalendarDisabled) R.string.dialog_description_delete_event_as_organizer_disabled
                    else R.string.dialog_description_delete_event_as_organizer
                ),
                resourceProvider.provideString(R.string.dialog_button_delete),
                resourceProvider.provideString(R.string.dialog_button_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {
                            // Get send preferences for attendees
                            deleteEventAsAnOrganizerSendPreferences(
                                displayDialog,
                                isPartOfChain,
                                isCalendarDisabled,
                                timeFormatIs24Hour
                            )
                        }
                    }
                    override fun onNegative() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onCancel() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onDismiss() {}
                }
            )
        }
    }

    /**
     * Get send preferences for attendees and display error dialog if there were any.
     */
    private suspend fun deleteEventAsAnOrganizerSendPreferences(
        displayDialog: BaseDialogFragment.DisplayDialog,
        isPartOfChain: Boolean,
        isCalendarDisabled: Boolean,
        timeFormatIs24Hour: Boolean
    ) {
        val attendees = eventLiveData.value?.iCalEvent?.attendees
        val attendeesEmails = attendees?.mapNotNull { it.extractEmail() }
        if (attendeesEmails.isNullOrEmpty()) {
            // Handle error in case attendees email list is somehow empty
            eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_event_deleted_error)
            )
            eventDetailsState.value = EventState.Idle
            return
        }

        if (isCalendarDisabled) {
            // Skip send preferences and finish delete flow if calendar is disabled since we won't be sending the email
            handleDeleteEventAsAnOrganizer(
                attendees,
                emptyMap(),
                timeFormatIs24Hour,
                isPartOfChain,
                isCalendarDisabled
            )
        } else {

            val sendPreferencesResults = getSendPreferences(attendeesEmails)
            if (sendPreferencesResults.emailErrors.isNotEmpty()) {

                if (sendPreferencesResults.emailErrors.any { it.value == ObtainSendPreferencesUseCase.Result.Error.NetworkError }) {
                    // Handle network error
                    eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                        resourceProvider.provideString(R.string.snack_network_error)
                    )
                    eventDetailsState.value = EventState.Idle
                    return
                } else {
                    // Display send preferences error dialog
                    val emailsWithErrors = formatSendPreferencesEmailsWithError(sendPreferencesResults)
                    uiScope.launch {
                        displayDialog.alertDialog(
                            resourceProvider.provideString(R.string.event_attendees_send_prefs_error_title),
                            if (sendPreferencesResults.sendPreferences.isEmpty()) {
                                resourceProvider.provideString(
                                    R.string.event_attendees_send_prefs_error_none_message,
                                    emailsWithErrors
                                )
                            } else {
                                resourceProvider.provideString(
                                    R.string.event_attendees_send_prefs_error_some_message,
                                    emailsWithErrors
                                )
                            },
                            resourceProvider.provideString(R.string.event_attendees_send_prefs_error_confirm),
                            resourceProvider.provideString(R.string.dialog_button_cancel),
                            object: BaseDialogFragment.DialogListener {
                                override fun onPositive(selectedItem: Int) {
                                    coroutineScope.launch {
                                        // Remove attendees whom emails were invalid
                                        val filteredAttendees = eventLiveData.value?.iCalEvent?.attendees?.filter { attendee ->
                                            sendPreferencesResults.emailErrors.any { emailError ->
                                                attendee.extractEmail() == emailError.key
                                            }.not()
                                        } ?: listOf()

                                        // Finish delete flow
                                        handleDeleteEventAsAnOrganizer(
                                            filteredAttendees,
                                            sendPreferencesResults.sendPreferences,
                                            timeFormatIs24Hour,
                                            isPartOfChain,
                                            isCalendarDisabled
                                        )
                                    }
                                }
                                override fun onNegative() { eventDetailsState.value = EventState.Idle
                                }
                                override fun onCancel() { eventDetailsState.value = EventState.Idle
                                }
                                override fun onDismiss() {}
                            }
                        )
                    }
                }
            } else {
                // Finish delete flow
                handleDeleteEventAsAnOrganizer(
                    attendees,
                    sendPreferencesResults.sendPreferences,
                    timeFormatIs24Hour,
                    isPartOfChain,
                    isCalendarDisabled
                )
            }
        }
    }

    /**
     * Final step for delete as an organizer. Call handle delete use case.
     */
    private suspend fun handleDeleteEventAsAnOrganizer(
        attendees: List<Attendee>,
        sendPreferences: Map<Email, SendPreferences>,
        timeFormatIs24Hours: Boolean,
        isPartOfChain: Boolean,
        isCalendarDisabled: Boolean
    ) {

        val deleteResult =
            handleDeleteUseCase.handleDeleteAsOrganizer(
                userId,
                Event.from(event),
                attendees,
                sendPreferences,
                timeFormatIs24Hours,
                isPartOfChain,
                isCalendarDisabled
            )

        var emailSent = false
        if (deleteResult is UseCase.Result.Success<*>) {
            deleteResult.returnValue.tryCast<Boolean> { emailSent = this }
        }

        handleDeleteResult(deleteResult, DeleteType.AS_AN_ORGANIZER, emailSent)
    }

    /**
     * Starts the delete flow as an attendee.
     * Get send preferences for organizer and display error dialog if there were any.
     * Display delete confirmation dialog if there were none.
     */
    private suspend fun deleteEventAsAnAttendeeSendPreferences(
        displayDialog: BaseDialogFragment.DisplayDialog,
        userAddresses: List<UserAddress>,
        event: Event,
        occurrenceNumber: Int,
        timeFormatIs24Hour: Boolean
    ) {
        val organizerEmail = event.iCalEvent.organizer.extractEmail()
        if (organizerEmail.isNullOrEmpty()) {
            // Handle error if organizer email was somehow empty
            eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(R.string.snack_event_deleted_error)
            )
            eventDetailsState.value = EventState.Idle
            return
        }

        val sendPreferencesResults = getSendPreferences(listOf(organizerEmail))
        if (sendPreferencesResults.emailErrors.isNotEmpty() && sendPreferencesResults.emailErrors.any {
                it.value == ObtainSendPreferencesUseCase.Result.Error.NetworkError
            }) {
            // Handle network error
            eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                resourceProvider.provideString(
                    R.string.snack_network_error
                )
            )
            eventDetailsState.value = EventState.Idle
            return
        } else {
            val attendeeEmails = event.iCalEvent.attendees.mapNotNull { it.extractEmail() }
            val userAddress = userAddresses.find { userAddress ->
                attendeeEmails.find { attendeeEmail ->
                    ProtonUtilsImpl.canonicalizeProtonEmail(userAddress.email, forceCanonicalization = true) ==
                            ProtonUtilsImpl.canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true)
                } != null
            }

            if (userAddress == null) {
                // Handle error if we were unable to get the user address
                logger.e("Error deleting event: userAddress was null in handleDeleteEventAsAttendee")
                eventDetailsSnackState.value =
                    EventSnackState.DisplaySnack(resourceProvider.provideString(R.string.snack_event_deleted_error))
                eventDetailsState.value = EventState.Idle
                return
            }

            val userEmail = userAddress.email

            val isOrphanSingleEdit = if (event.isSingleEdit()) calendarsRepository.isOrphanSingleEdit(
                userId,
                event.uid
            ) ?: false
            else false

            val isRecurring = event.isRecurring()
            val isSingleEdit = event.isSingleEdit()
            val isCalendarDisabled = event.calendar.isDisabled
            val isEventCanceled = event.isCancelled()
            val isAddressAllowedToSend = userAddress.enabled && userAddress.canSend
            val currentParticipationStatus = event.getParticipationStatus(listOf(userEmail)) ?: ParticipationStatus.NEEDS_ACTION

            val hasNonCancelledSingleEdit = getSingleEditsInfo(listOf(userEmail))?.hasSingleEdit ?: false &&
                    getSingleEditsInfo(listOf(userEmail))?.hasNonCancelledSingleEdit == true

            val hasAnsweredSingleEdit = getSingleEditsInfo(listOf(userEmail))?.singleEdits?.any {
                val participationStatus = it.getParticipationStatus(listOf(userEmail))
                participationStatus == ParticipationStatus.ACCEPTED ||
                        participationStatus == ParticipationStatus.TENTATIVE
            } ?: false // We only care about single edits answered with YES or MAYBE

            val emailsWithErrors = formatSendPreferencesEmailsWithError(sendPreferencesResults)
            val sendPrefsFailed = emailsWithErrors.isNotEmpty()

            val displayWarning = (currentParticipationStatus == ParticipationStatus.ACCEPTED ||
                    currentParticipationStatus == ParticipationStatus.TENTATIVE) &&
                    isEventCanceled.not() &&
                    !isCalendarDisabled

            // Display delete as an attendee confirmation dialog
            uiScope.launch {
                displayDialog.alertDialog(
                    resourceProvider.provideString(
                        if (sendPrefsFailed && displayWarning) R.string.event_organizer_send_prefs_error_title
                        else if (isRecurring || (isSingleEdit && isOrphanSingleEdit.not())) R.string.dialog_title_delete_recurring_event
                        else R.string.dialog_title_delete_event
                    ),
                    getDeleteAsAnAttendeeMessage(
                        displayWarning,
                        isCalendarDisabled,
                        sendPrefsFailed,
                        emailsWithErrors,
                        isRecurring,
                        hasAnsweredSingleEdit,
                        hasNonCancelledSingleEdit,
                        isSingleEdit,
                        isOrphanSingleEdit,
                        isAddressAllowedToSend
                    ),
                    resourceProvider.provideString(R.string.dialog_button_delete),
                    resourceProvider.provideString(R.string.dialog_button_cancel),
                    object: BaseDialogFragment.DialogListener {
                        override fun onPositive(selectedItem: Int) {
                            coroutineScope.launch {
                                // Finish delete flow
                                handleDeleteEventAsAnAttendee(
                                    userEmail,
                                    sendPreferencesResults.sendPreferences,
                                    hasNonCancelledSingleEdit,
                                    hasAnsweredSingleEdit,
                                    occurrenceNumber,
                                    isOrphanSingleEdit,
                                    timeFormatIs24Hour,
                                    displayWarning && isAddressAllowedToSend && !sendPrefsFailed
                                )
                            }
                        }
                        override fun onNegative() { eventDetailsState.value = EventState.Idle
                        }
                        override fun onCancel() { eventDetailsState.value = EventState.Idle
                        }
                        override fun onDismiss() {}
                    }
                )
            }
        }
    }

    /**
     * @returns the message to be displayed in delete as an attendee confirmation dialog
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun getDeleteAsAnAttendeeMessage(
        displayWarning: Boolean,
        isCalendarDisabled: Boolean,
        sendPrefsFailed: Boolean,
        emailsWithErrors: String,
        isRecurring: Boolean,
        hasAnsweredSingleEdit: Boolean,
        hasNonCancelledSingleEdit: Boolean,
        isSingleEdit: Boolean,
        isOrphanSingleEdit: Boolean,
        isAddressAllowedToSend: Boolean
    ): String {
        return if (displayWarning.not()) {
            // Display basic delete event message
            if (isSingleEdit && isOrphanSingleEdit.not() && isCalendarDisabled.not()) resourceProvider.provideString(R.string.dialog_description_delete_non_standalone_single_edit_event)
            else if (isRecurring || (isSingleEdit && isOrphanSingleEdit.not() && isCalendarDisabled)) {
                val message = resourceProvider.provideString(R.string.dialog_description_delete_recurring_event)

                val singleEditWarning = if (!isCalendarDisabled && hasAnsweredSingleEdit) resourceProvider.provideString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee)
                else if (!isCalendarDisabled && hasNonCancelledSingleEdit) resourceProvider.provideString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee)
                else ""

                // Add single edit warning if needed
                if (singleEditWarning.isNotEmpty()) resourceProvider.provideString(R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee, message, singleEditWarning)
                else message
            }
            else resourceProvider.provideString(R.string.dialog_description_delete_event)
        } else {
            // Display extended dialog message if event has ACCEPTED / TENTATIVE answer, is not canceled, and calendar is enabled
            // Dialog priority order: 1- Address disabled warning. 2- Send prefs dialog. 3- Others.
            if (sendPrefsFailed && isAddressAllowedToSend) {
                resourceProvider.provideString(R.string.event_delete_as_attendee_send_prefs_error_message, emailsWithErrors)
            } else {
                if (isRecurring) {
                    val message = if (!isAddressAllowedToSend) resourceProvider.provideString(R.string.dialog_description_delete_recurring_event_as_attendee_disabled)
                    else resourceProvider.provideString(R.string.dialog_description_delete_recurring_event_as_attendee)

                    val singleEditWarning = if (hasAnsweredSingleEdit) resourceProvider.provideString(R.string.dialog_description_warning_delete_recurring_with_answered_single_edit_as_attendee)
                    else if (hasNonCancelledSingleEdit) resourceProvider.provideString(R.string.dialog_description_warning_delete_recurring_with_unanswered_single_edit_as_attendee)
                    else ""

                    // Add single edit warning if needed
                    if (singleEditWarning.isNotEmpty()) resourceProvider.provideString(R.string.dialog_description_warning_delete_recurring_with_single_edit_as_attendee, message, singleEditWarning)
                    else message
                }
                else if (isSingleEdit && isOrphanSingleEdit.not() && !isAddressAllowedToSend) resourceProvider.provideString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee_disabled)
                else if (isSingleEdit && isOrphanSingleEdit.not()) resourceProvider.provideString(R.string.dialog_description_delete_non_standalone_single_edit_event_as_attendee)
                else if (!isAddressAllowedToSend) resourceProvider.provideString(R.string.dialog_description_delete_single_event_as_attendee_disabled)
                else resourceProvider.provideString(R.string.dialog_description_delete_single_event_as_attendee)
            }
        }
    }

    /**
     * Final step for delete as an attendee. Call handle delete use case.
     */
    private suspend fun handleDeleteEventAsAnAttendee(
        userEmail: String,
        sendPreferences: Map<Email, SendPreferences>,
        hasNonCancelledSingleEdit: Boolean,
        hasAnsweredSingleEdit: Boolean,
        occurrenceNumber: Int,
        isOrphanSingleEdit: Boolean,
        timeFormatIs24Hours: Boolean,
        sendReply: Boolean
    ) {

        val cancelledSingleEdits = getSingleEditsInfo(listOf(userEmail))?.singleEdits?.filter { it.isCancelled() }
        val deleteResult = handleDeleteUseCase.handleDeleteAsAttendee(
            userId,
            Event.from(event),
            cancelledSingleEdits,
            userEmail,
            sendPreferences,
            hasNonCancelledSingleEdit,
            occurrenceNumber,
            isOrphanSingleEdit,
            event.defaultTimeZone!!,
            timeFormatIs24Hours,
            sendReply
        )

        if (deleteResult is UseCase.Result.Success<*> && event.isRecurring() && hasAnsweredSingleEdit) {
            // If chain has single edits, update their part stat to NEEDS_ACTION
            // By passing DECLINED as the last parameter here we make it so that single edits with DECLINED status are not reset
            clearSingleEditsParticipationStatus(event.calendar.id, event.uid, listOf(userEmail), ParticipationStatus.DECLINED)
        }

        var emailSent = false
        if (deleteResult is UseCase.Result.Success<*>) {
            deleteResult.returnValue.tryCast<Boolean> { emailSent = this }
        }

        handleDeleteResult(deleteResult, DeleteType.AS_AN_ATTENDEE, emailSent)
    }

    /**
     * Starts delete flow for recurring event.
     * Display picker dialog with options This / This and future / All.
     */
    private suspend fun deleteRecurringEvent(
        displayDialog: BaseDialogFragment.DisplayDialog,
        dbEvent: Event,
        occurrenceNumber: Int
    ) {
        val showThisAndFuture = occurrenceNumber > 1 &&
                !event.isEventFirstOccurrence(dbEvent, displayTimeZoneId)

        // Display delete recurring event option dialog (This / This and future / All)
        uiScope.launch {
            displayDialog.pickerDialog(
                resourceProvider.provideString(R.string.dialog_title_delete_recurring_event),
                listOfNotNull(
                    resourceProvider.provideString(R.string.event_recurring_edit_this),
                    if (showThisAndFuture) resourceProvider.provideString(R.string.event_recurring_edit_this_and_future) else null,
                    resourceProvider.provideString(R.string.event_recurring_edit_all_events)
                ).toTypedArray(),
                defaultSelectedItem = 0,
                resourceProvider.provideString(R.string.dialog_button_ok),
                resourceProvider.provideString(R.string.dialog_button_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {
                            // Finish delete recurring flow
                            handleDeleteRecurring(
                                occurrenceNumber,
                                selectedItem,
                                showThisAndFuture
                            )
                        }
                    }
                    override fun onNegative() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onCancel() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onDismiss() {}
                }
            )
        }
    }

    /**
     * Final step for delete recurring event. Call handle delete use case.
     */
    private suspend fun handleDeleteRecurring(occurrenceNumber: Int, selectedIndex: Int, showThisAndFuture: Boolean) {
        val deleteOption =
            if (selectedIndex == 0) EventEditDeleteOption.THIS_EVENT
            else if (selectedIndex == 1) {
                if (showThisAndFuture) EventEditDeleteOption.THIS_EVENT_AND_FUTURE
                else EventEditDeleteOption.ALL_EVENTS
            } else EventEditDeleteOption.ALL_EVENTS

        val deleteResult = handleDeleteUseCase.handleDelete(
            userId,
            event.id,
            event.calendar.id,
            deleteOption,
            if (deleteOption == EventEditDeleteOption.ALL_EVENTS) null else occurrenceNumber
        )

        handleDeleteResult(deleteResult, DeleteType.NO_PARTICIPANTS)
    }

    /**
     * Display confirmation dialog notifying user all events will be deleted for this series.
     * Call handle delete use case.
     */
    private suspend fun deleteDisabledCalendarRecurringEvent(
        displayDialog: BaseDialogFragment.DisplayDialog
    ) {
        uiScope.launch {
            displayDialog.alertDialog(
                resourceProvider.provideString(R.string.dialog_title_delete_recurring_event),
                resourceProvider.provideString(R.string.dialog_description_delete_recurring_event),
                resourceProvider.provideString(R.string.dialog_button_delete),
                resourceProvider.provideString(R.string.dialog_button_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {
                            // Delete all events from chain
                            val deleteResult = handleDeleteUseCase.handleDelete(userId, event.id, event.calendar.id,
                                EventEditDeleteOption.ALL_EVENTS, null)

                            handleDeleteResult(deleteResult, DeleteType.NO_PARTICIPANTS)
                        }
                    }
                    override fun onNegative() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onCancel() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onDismiss() {}
                }
            )
        }
    }

    /**
     * Display delete confirmation dialog for single event
     * Call handle delete use case.
     */
    private suspend fun deleteEvent(
        displayDialog: BaseDialogFragment.DisplayDialog,
        occurrenceNumber: Int
    ) {
        uiScope.launch {
            displayDialog.alertDialog(
                resourceProvider.provideString(R.string.dialog_title_delete_event),
                resourceProvider.provideString(R.string.dialog_description_delete_event),
                resourceProvider.provideString(R.string.dialog_button_delete),
                resourceProvider.provideString(R.string.dialog_button_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {

                            val deleteResult =
                                if (dbEvent?.isSingleOccurrenceRecurring(displayTimeZoneId) == true) {
                                    handleDeleteUseCase.handleDelete(userId, event.id, event.calendar.id,
                                        EventEditDeleteOption.ALL_EVENTS, null)
                                } else {
                                    handleDeleteUseCase.handleDelete(userId, event.id, event.calendar.id,
                                        EventEditDeleteOption.THIS_EVENT, occurrenceNumber)
                                }

                            handleDeleteResult(deleteResult, DeleteType.NO_PARTICIPANTS)
                        }
                    }
                    override fun onNegative() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onCancel() { eventDetailsState.value = EventState.Idle
                    }
                    override fun onDismiss() {}
                }
            )
        }
    }

    enum class DeleteType {
        AS_AN_ORGANIZER,
        AS_AN_ATTENDEE,
        NO_PARTICIPANTS
    }

    /**
     * Handle delete event result and display snack
     */
    private fun handleDeleteResult(deleteResult: UseCase.Result, deleteType: DeleteType, mailSent: Boolean? = null) {
        // Post deleting event value to false to stop loading state
        eventDetailsState.value = EventState.Idle

        if (deleteResult is UseCase.Result.Success<*>) {
            widgetRefresher.refreshEventList()
            eventDetailsSnackState.value = EventSnackState.DisplaySnackReturnToMonth(
                if (deleteType == DeleteType.AS_AN_ORGANIZER && mailSent == true) resourceProvider.provideString(R.string.snack_event_deleted_as_organizer)
                else if (deleteType == DeleteType.AS_AN_ATTENDEE && mailSent == true) resourceProvider.provideString(R.string.snack_event_deleted_as_attendee)
                else resourceProvider.provideString(R.string.snack_event_deleted)
            )
        } else {

            var userErrorMessage: String? = null
            if (deleteResult is UseCase.Result.Error) {
                logger.e("Error deleting event: ${deleteResult.message}")
                userErrorMessage = deleteResult.userErrorMessage
            } else if (deleteResult is UseCase.Result.InvalidParams) {
                logger.e("InvalidParams deleting event: ${deleteResult.message}")
                userErrorMessage = deleteResult.userErrorMessage
            }

            eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                if (userErrorMessage.isNullOrEmpty()) {
                    resourceProvider.provideString(R.string.snack_event_deleted_error)
                } else {
                    userErrorMessage
                }
            )
        }
    }

    /**
     * Display change answer error snack, reset attendee answer to current participation status and clear loading state
     */
    private fun handleChangeAnswerError() {
        eventDetailsSnackState.value =
            EventSnackState.DisplaySnack(resourceProvider.provideString(R.string.snack_change_attendee_answer_error))
        // Reset change answer buttons to previous state and remove loading state
        attendeeAnswerState.value = Pair(currentParticipationStatus, false)
    }

    /**
     * Starts the change answer flow.
     */
    suspend fun onChangeAnswerClick(
        displayDialog: BaseDialogFragment.DisplayDialog,
        newParticipationStatus: ParticipationStatus,
        timeFormatIs24Hours: Boolean
    ) {

        if (eventDetailsState.value is EventState.Processing || attendeeAnswerState.value?.second == true) return

        val userEmails = userAddressManager.getAddressesOrNull(userId)?.map { address ->
            address.email
        }

        if (userEmails == null) {
            logger.e("EventViewModel onChangeAnswerClick userEmails == null")
            return
        }

        val currentParticipationStatus = event.getParticipationStatus(userEmails) ?: ParticipationStatus.NEEDS_ACTION
        this.currentParticipationStatus = currentParticipationStatus
        if (currentParticipationStatus == newParticipationStatus) return

        // Display Processing State
        attendeeAnswerState.value = Pair(newParticipationStatus, true)

        if (event.isPartOfChain()) {
            // Handle recurring events and single edits
            val isSingleEdit = event.isSingleEdit()
            val isOrphanSingleEdit = if (isSingleEdit) calendarsRepository.isOrphanSingleEdit(
                userId,
                event.uid
            ) else false

            if (isOrphanSingleEdit == true) {
                changeAnswerSendPreferences(
                    displayDialog,
                    newParticipationStatus,
                    timeFormatIs24Hours
                )
            } else {
                changeAnswerRecurring(
                    displayDialog,
                    newParticipationStatus,
                    timeFormatIs24Hours,
                    userEmails,
                    isSingleEdit
                )
            }
        } else {
            // Handle single events
            changeAnswerSendPreferences(
                displayDialog,
                newParticipationStatus,
                timeFormatIs24Hours
            )
        }
    }

    /**
     * Display change answer confirmation dialog for recurring events and single edits
     */
    private suspend fun changeAnswerRecurring(
        displayDialog: BaseDialogFragment.DisplayDialog,
        newParticipationStatus: ParticipationStatus,
        timeFormatIs24Hours: Boolean,
        userEmails: List<String>,
        isSingleEdit: Boolean
    ) {
        val hasAnsweredSingleEdit = getSingleEditsInfo(userEmails)?.hasAnsweredSingleEdit ?: false
        val hasAnsweredSingleEditToOverwrite =
            if (hasAnsweredSingleEdit) {
                // Only check if it has any answered single edits
                getSingleEditsInfo(userEmails)?.singleEdits?.any {
                    val singleEditParticipationStatus = it.getParticipationStatus(userEmails)
                    singleEditParticipationStatus != newParticipationStatus && (
                            singleEditParticipationStatus == ParticipationStatus.ACCEPTED ||
                                    singleEditParticipationStatus == ParticipationStatus.DECLINED ||
                                    singleEditParticipationStatus == ParticipationStatus.TENTATIVE)
                } ?: false
            } else false

        // Check if we need to overwrite any answer single edit with the new participation status
        val overwrite =
            if (isSingleEdit) false
            else hasAnsweredSingleEdit && hasAnsweredSingleEditToOverwrite

        // Display Confirmation Dialog
        val dialogType = when {
            overwrite -> ChangeAnswerRecurringDialogType.OVERWRITE
            isSingleEdit -> ChangeAnswerRecurringDialogType.SINGLE_EDIT
            else -> ChangeAnswerRecurringDialogType.DEFAULT
        }
        uiScope.launch {
            displayDialog.alertDialog(
                resourceProvider.provideString(R.string.event_change_answer_recurring_title),
                resourceProvider.provideString(
                    when(dialogType) {
                        ChangeAnswerRecurringDialogType.OVERWRITE -> R.string.event_change_answer_recurring_overwrite_description
                        ChangeAnswerRecurringDialogType.SINGLE_EDIT -> R.string.event_change_answer_recurring_single_edit_description
                        else -> R.string.event_change_answer_recurring_description
                    }
                ),
                resourceProvider.provideString(R.string.event_change_answer_recurring_confirm),
                resourceProvider.provideString(R.string.dialog_button_cancel),
                object: BaseDialogFragment.DialogListener {
                    override fun onPositive(selectedItem: Int) {
                        coroutineScope.launch {
                            changeAnswerSendPreferences(
                                displayDialog,
                                newParticipationStatus,
                                timeFormatIs24Hours
                            )
                        }
                    }
                    override fun onNegative() { attendeeAnswerState.value = Pair(currentParticipationStatus, false) }
                    override fun onCancel() { attendeeAnswerState.value = Pair(currentParticipationStatus, false) }
                    override fun onDismiss() {}
                }
            )
        }
    }

    /**
     * Get send preferences for the organizer. Display dialog if there were any error and end change answer flow.
     */
    private suspend fun changeAnswerSendPreferences(
        displayDialog: BaseDialogFragment.DisplayDialog,
        newParticipationStatus: ParticipationStatus,
        timeFormatIs24Hours: Boolean
    ) {

        val organizerEmail = event.iCalEvent.organizer.extractEmail()
        if (organizerEmail == null) {
            handleChangeAnswerError()
            return
        }

        val sendPreferencesResults = getSendPreferences(listOf(organizerEmail))
        if (sendPreferencesResults.emailErrors.isNotEmpty()) {

            val emailError = sendPreferencesResults.emailErrors.values.first()

            // Reset change answer buttons to previous state and remove loading state
            attendeeAnswerState.value = Pair(currentParticipationStatus, false)

            val errorMessage = resourceProvider.provideString(when (newParticipationStatus) {
                ParticipationStatus.ACCEPTED -> R.string.event_organizer_send_prefs_message_accepted_title
                ParticipationStatus.DECLINED -> R.string.event_organizer_send_prefs_message_declined_title
                ParticipationStatus.TENTATIVE -> R.string.event_organizer_send_prefs_message_tentative_title
                else -> R.string.event_organizer_send_prefs_message_default_title
            })

            uiScope.launch {
                // Display send preferences error dialog
                displayDialog.alertDialog(
                    resourceProvider.provideString(R.string.event_organizer_send_prefs_error_title),
                    resourceProvider.provideString(
                        R.string.event_send_prefs_error_template,
                        errorMessage,
                        resourceProvider.provideString(emailError.formatSendPreferencesError())
                    ),
                    resourceProvider.provideString(R.string.event_organizer_send_prefs_button_title),
                    resourceProvider.provideString(R.string.dialog_button_cancel),
                    null
                )

                if (emailError is ObtainSendPreferencesUseCase.Result.Error.NetworkError) {
                    // Display snack if it was a network error
                    eventDetailsSnackState.value = EventSnackState.DisplaySnack(
                        resourceProvider.provideString(
                            R.string.snack_change_attendee_answer_error
                        )
                    )
                }
            }
        } else {
            // Continue change answer flow
            changeAnswer(
                newParticipationStatus,
                sendPreferencesResults.sendPreferences,
                timeFormatIs24Hours
            )
        }
    }

    /**
     * Prepare data needed for change answer use case and handle error if we failed to prepare anything mandatory.
     */
    private suspend fun changeAnswer(
        participationStatus: ParticipationStatus,
        sendPreferences: Map<Email, SendPreferences>,
        timeFormatIs24Hours: Boolean
    ) {
        val status = participationStatus.toInt()

        val userEmails = userAddressManager.getAddressesOrNull(userId)?.map { address ->
            address.email
        }

        if (userEmails == null) {
            logger.e("EventViewModel changeAnswer userEmails == null")
            return
        }

        val userAttendee = event.iCalEvent.attendees.find { attendee ->
            userEmails.firstOrNull { userEmail ->
                val attendeeEmail = attendee.extractEmail()
                attendeeEmail != null && ProtonUtilsImpl.canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true)
                    .equals(ProtonUtilsImpl.canonicalizeProtonEmail(userEmail, forceCanonicalization = true), ignoreCase = true)
            } != null
        }
        if (userAttendee == null) {
            handleChangeAnswerError()
            return
        }
        val userParticipationStatus = userAttendee.participationStatus

        val eventCopy = Event.from(event)
        val (personalPartICalString, notifications) =
            if (participationStatus == ParticipationStatus.DECLINED && event.alarms.isNotEmpty()
            ) {
                // if changes to NO, remove all notifications if there are any
                eventCopy.clearAlarms()
                "" to emptyList<Notification>()
            } else if ((userParticipationStatus == ParticipationStatus.DECLINED ||
                        userParticipationStatus == ParticipationStatus.NEEDS_ACTION) &&
                (participationStatus == ParticipationStatus.ACCEPTED || participationStatus == ParticipationStatus.TENTATIVE) &&
                event.alarms.isEmpty()
            ) {
                // if changes from NO to YES/MAYBE add default calendar notifications
                if (loadSettingsForCalendar(event.calendar.id)) {
                    // we don't need to load settings here anymore, but let's keep it in case of side effects
                    eventCopy.setDefaultAlarms()
                    val calendarSplit = ICalUtilsImpl.splitICalendarIntoParts(eventCopy.iCalendar)
                    calendarSplit.personalPart?.printToString() to null // null means default calendar notifications in "revamped system"
                } else null to null
            } else {
                // else keep notifications as it is
                null to null
            }

        val eventEntity = if (event.isProtonProtonInvite == null || event.isProtonProtonInvite == true) {
            val event = calendarsRepository.fetchEventById(userId, event.calendar.id, event.id).valueOrNullAndLogErrors(logger)?.event?.toEventEntity()
            if (event == null) {
                handleChangeAnswerError()
                return
            }
            event
        } else null

        val isProtonProtonInvite = event.isProtonProtonInvite ?: eventEntity?.isProtonProtonInvite?.toBoolean()

        val attendeeId = event.currentUserAttendeeId
        if (attendeeId.isNullOrEmpty()) {
            handleChangeAnswerError()
            return
        }

        if (isProtonProtonInvite == true) {
            if (eventEntity == null) {
                handleChangeAnswerError()
                return
            }

            // Finish change answer flow for proton to proton invite
            handleChangeAnswerProtonToProton(
                sendPreferences = sendPreferences,
                eventCopy = eventCopy,
                eventEntity = eventEntity,
                userAttendee = userAttendee,
                participationStatus = participationStatus,
                status = status,
                personalPartICalString = personalPartICalString,
                notifications = notifications,
                timeFormatIs24Hours = timeFormatIs24Hours,
                attendeeId = attendeeId,
                userEmails = userEmails
            )
        } else {
            // Finish change answer flow for external invite
            handleChangeAnswerExternal(
                sendPreferences = sendPreferences,
                eventCopy = eventCopy,
                userAttendee = userAttendee,
                participationStatus = participationStatus,
                status = status,
                personalPartICalString = personalPartICalString,
                notifications = notifications,
                timeFormatIs24Hours = timeFormatIs24Hours,
                attendeeId = attendeeId,
                userEmails = userEmails
            )
        }
    }

    /**
     * Finish change answer flow for external invite. We first send the reply to the organizer
     *  and then update the participation status on server if we succeeded (sending the reply is mandatory for external)
     */
    private suspend fun handleChangeAnswerExternal(
        sendPreferences: Map<Email, SendPreferences>,
        eventCopy: Event,
        userAttendee: Attendee,
        participationStatus: ParticipationStatus,
        status: Int,
        personalPartICalString: String?,
        notifications: List<Notification>?,
        timeFormatIs24Hours: Boolean,
        attendeeId: String,
        userEmails: List<String>
    ) {
        val updateTime = Instant.now()

        // We first send the reply to the organizer
        val sendEmailUseCaseResult = if (sendPreferences.isNotEmpty()) {
            val organizerEmail = event.iCalEvent.organizer.extractEmail() ?: run {
                handleChangeAnswerError()
                return
            }

            val preferredReplyEmail =
                userEmails.firstOrNull { ProtonUtilsImpl.isProtonDomain(it) } ?: userEmails.first()

            val protonAttendeeForReply = eventCopy.iCalEvent.attendees.firstOrNull { a ->
                val attendeeEmail = a.extractEmail()
                attendeeEmail != null && ProtonUtilsImpl.canonicalizeProtonEmail(attendeeEmail, forceCanonicalization = true)
                    .equals(
                        ProtonUtilsImpl.canonicalizeProtonEmail(preferredReplyEmail, forceCanonicalization = true),
                        ignoreCase = true
                    )
            }
            val attendeeForReply = protonAttendeeForReply ?: userAttendee

            sendEmailUseCase.sendReplyToOrganizer(
                userId = userId,
                event = eventCopy,
                originalTimeZoneInfo = dbEvent?.iCalendar?.timezoneInfo,
                userAttendee = attendeeForReply.copy(),
                organizerEmail = organizerEmail,
                participationStatus = participationStatus,
                sendPreferences = sendPreferences,
                dtStamp = Date.from(updateTime),
                eventEntity = null,
                isProtonProtonInvite = false,
                defaultTimeZone = event.defaultTimeZone!!,
                timeFormatIs24Hours = timeFormatIs24Hours
            )
        } else UseCase.Result.Success<Unit>()
        sendEmailUseCaseResult.ifSuccessAndLogErrors(logger) { }

        if (sendEmailUseCaseResult is UseCase.Result.Error && sendEmailUseCaseResult.error == UseCase.Error.Crypto.UserAddressInvalidForEncryption) {
            // We don't need to display snack and update attendee answer state because this state will force logout the user anyway
            eventDetailsState.value = EventState.UserAddressInvalidForEncryption
            return
        }

        if (sendEmailUseCaseResult !is UseCase.Result.Success<*>) {
            // Handle change answer error and stop flow
            handleChangeAnswerResult(
                sendEmailUseCaseResult,
                userEmails,
                participationStatus,
                personalPartICalString
            )
            return
        }

        // We then update participation status on server
        val updateParticipationStatusUseCaseResult = updateParticipationStatusUseCase.execute(
            userId,
            event.calendar.id,
            event.id,
            attendeeId,
            status,
            personalPartICalString,
            notifications,
            updateTime.epochSecond.toInt()
        )
        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }

        // Handle change answer result
        handleChangeAnswerResult(
            updateParticipationStatusUseCaseResult,
            userEmails,
            participationStatus,
            personalPartICalString
        )
    }

    /**
     * Finish change answer flow for proton to proton invite. We first update the participation status on server
     *  and then send the reply to the organizer if we succeeded (sending the reply is optional for proton to proton)
     */
    private suspend fun handleChangeAnswerProtonToProton(
        sendPreferences: Map<Email, SendPreferences>,
        eventCopy: Event,
        eventEntity: EventEntity,
        userAttendee: Attendee,
        participationStatus: ParticipationStatus,
        status: Int,
        personalPartICalString: String?,
        notifications: List<Notification>?,
        timeFormatIs24Hours: Boolean,
        attendeeId: String,
        userEmails: List<String>
    ) {

        val updateTime = Instant.now()

        val upgradedEventEntity = (upgradeEventUseCase.execute(userId, eventEntity.id) as? UseCase.Result.Success<*>)?.returnValue.tryCastOrNull<EventEntity>() ?: logger.e("handleChangeAnswerProtonToProton failed to cast upgrade result to EventEntity")

        // For proton to proton we first update the participation status on BE
        val updateParticipationStatusUseCaseResult = if (upgradedEventEntity != null) {
            updateParticipationStatusUseCase.execute(
                userId,
                event.calendar.id,
                event.id,
                attendeeId,
                status,
                personalPartICalString,
                notifications,
                updateTime.epochSecond.toInt()
            )
        } else UseCase.Result.Error("handleChangeAnswerProtonToProton could not upgrade Event: ${upgradedEventEntity}")
        updateParticipationStatusUseCaseResult.ifSuccessAndLogErrors(logger) { }

        val organizerEmail = event.iCalEvent.organizer.extractEmail()
        if (updateParticipationStatusUseCaseResult is UseCase.Result.Success<*> && sendPreferences.isNotEmpty() && organizerEmail != null) {
            // If we updated the participation status on BE, we send the reply to the organizer. We consider sending the reply to be optional.
            val sendEmailUseCaseResult = sendEmailUseCase.sendReplyToOrganizer(
                userId,
                eventCopy,
                dbEvent?.iCalendar?.timezoneInfo,
                userAttendee.copy(),
                organizerEmail,
                participationStatus,
                sendPreferences,
                Date.from(updateTime), // Use same updateTime as for Update part stat BE call
                eventEntity,
                true,
                event.defaultTimeZone!!,
                timeFormatIs24Hours
            )
            sendEmailUseCaseResult.ifSuccessAndLogErrors(logger) { }
            // Do not return use case result. Sending the email is optional for proton to proton so we don't care if it failed
        }

        handleChangeAnswerResult(
            updateParticipationStatusUseCaseResult,
            userEmails,
            participationStatus,
            personalPartICalString
        )
    }

    /**
     * Handle change answer use case result.
     */
    private suspend fun handleChangeAnswerResult(
        changeAnswerResult: UseCase.Result,
        userEmails: List<String>,
        participationStatus: ParticipationStatus,
        personalPartICalString: String?
    ) {

        // Handle error
        if (changeAnswerResult !is UseCase.Result.Success<*>) {
            handleChangeAnswerError()
            return
        }

        if (!event.isSingleEdit() && singleEditsInfo?.hasSingleEdit == true) {
            // If chain has single edits, update their part stat to NEEDS_ACTION
            clearSingleEditsParticipationStatus(event.calendar.id, event.uid, userEmails, participationStatus)
        }

        // Apply alarms modifications
        if (personalPartICalString?.isEmpty() == true) {
            // clear alarms
            event.clearAlarms()
        } else if (personalPartICalString?.isNotEmpty() == true) {
            // add default alarms
            event.setDefaultAlarms()
        }

        // Update the event participation status to reflect changes in view
        event.updateParticipationStatus(userEmails, participationStatus)

        if (!event.calendar.display) updateCalendarDisplay(event.calendar, true)

        _event.postValue(event)

        // Update attendee answer state to reflect change on buttons
        attendeeAnswerState.value = Pair(participationStatus, false)

        // Force the Widget to refresh, because we just changed the Event answer
        widgetRefresher.refreshEventList()

        // Display part stat updated snack and return to month view with focus on the event's start date
        eventDetailsSnackState.value = EventSnackState.DisplaySnackReturnToMonth(
            resourceProvider.provideString(R.string.snack_event_part_stat_updated)
        )
    }

    private fun clearSingleEditsParticipationStatus(
        calendarId: String,
        eventUid: String,
        userEmails: List<String>,
        mainChainParticipationStatus: ParticipationStatus
    ): LiveData<Operation.State> {
        return UpdateParticipationStatusSingleEditWorker.enqueue(workManager, userId = userId.id,
            calendarId = calendarId,
            eventUid = eventUid,
            userEmails = userEmails,
            mainChainParticipationStatus = mainChainParticipationStatus,
        )
    }

    sealed class EventLinkResult {
        class Success(val occurrenceNumber: Int) : EventLinkResult()
        class DecryptionFailed(val event: Event) : EventLinkResult()
        object EventDoesNotExist : EventLinkResult()
        object OccurrenceDoesNotExist : EventLinkResult()
        object Error : EventLinkResult()
    }

    suspend fun handleEventLink(userId: UserId, eventId: String, calendarId: String, recurrenceIdTimestamp: String): EventLinkResult {
        this.userId = userId
        var eventEntity = calendarsRepository.selectEventEntity(eventId)
        if (eventEntity == null) {
            eventEntity = calendarsRepository.fetchEventById(userId, eventId, calendarId).valueOrNullAndLogErrors(logger)?.event?.toEventEntity()
                ?: return EventLinkResult.EventDoesNotExist
        }
        val event = (if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
            eventDecryptor.decrypt(eventEntity)
        } else {
            transformEventUseCase.execute(eventEntity)
        }) ?: return EventLinkResult.Error
        if (event.decryptionStatus is Event.DecryptionStatus.Failure) return EventLinkResult.DecryptionFailed(event)
        if (!event.calendar.display) updateCalendarDisplay(event.calendar, true)
        return if (event.isRecurring()) {
            val calendarUserSettings =
                calendarsRepository.selectCalendarUserSettings(userId.id) ?: return EventLinkResult.Error
            val timeZoneId = event.iCalendar.timezoneInfo?.getTimezone(event.iCalEvent.dateStart)?.timeZone?.id
                ?: calendarUserSettings.primaryTimezone
            val occurrences = event.generateOccurrencesUntil(
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(recurrenceIdTimestamp.toLong()), ZoneId.of(timeZoneId))
                    .toLocalDate(),
                timeZoneId
            )
            if (occurrences.isNullOrEmpty()) EventLinkResult.OccurrenceDoesNotExist
            else EventLinkResult.Success(occurrences.lastIndex + 1)
        } else EventLinkResult.Success(0)
    }
}
