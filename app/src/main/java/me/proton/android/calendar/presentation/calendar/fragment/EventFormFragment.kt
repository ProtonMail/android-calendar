package me.proton.android.calendar.presentation.calendar.fragment

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import biweekly.property.Action
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FormValidation
import me.proton.android.calendar.common.FormValidation.ATTENDEE_MAX_CHIP_ALLOWED
import me.proton.android.calendar.common.FragmentArguments.DEFAULT_NOTIFICATIONS_TYPE_ARG
import me.proton.android.calendar.common.FragmentArguments.IS_ALL_DAY_ARG
import me.proton.android.calendar.common.FragmentArguments.READ_ONLY_ARG
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.allowedTimezoneIds
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.formattedTimeZoneToId
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.getDeviceContacts
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.sortFormattedTimeZoneIds
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.ColorUtils
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.firstDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTimeZoneId
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getTimeWithPadding
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.isBetween
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatEnd
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.databinding.DialogCheckboxBinding
import me.proton.android.calendar.databinding.FragmentEventFormBinding
import me.proton.android.calendar.databinding.ItemAlarmTextButtonBinding
import me.proton.android.calendar.databinding.ItemAttendeeChipBinding
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.MeetIntegrationType
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.FeatureFlagViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.presentation.utils.SnackType
import me.proton.core.presentation.utils.clearText
import me.proton.core.presentation.utils.snack
import me.proton.core.user.domain.extension.hasSubscriptionForMail
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

class EventFormFragment() : BaseDialogFragment<FragmentEventFormBinding>(), KoinComponent {

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val eventViewModel: EventViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val featureFlagViewModel: FeatureFlagViewModel by activityViewModels()

    override val TAG = "EventFormFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form
    override val isScrollable = false

    override val navigateUp = false

    private val logger: Logger by inject()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            // We navigate even though contacts permission is not granted
            navigateToAttendees()
        }

    override fun onBackPressedCustom() {
        val processingEvent = eventViewModel.eventFormState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                if (navigationArguments.eventId == null) jumpToMonthView()
                else navigateBackToDetails()
            }
        } else if (navigationArguments.eventId == null) jumpToMonthView()
        else navigateBackToDetails()
    }

    private fun navigateBackToDetails() {
        // Reinitialise event view model data
        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus =
                if (userId == null) EventViewModel.InitResult.Error.Default("user ID is null in EventDetailsFragment onViewCreated")
                else eventViewModel.initialise(
                    userId,
                    editMode = false,
                    meetIntegrations = featureFlagViewModel.enabledMeetIntegrations(),
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    null,
                    null
                )
            if (viewModeInitStatus == EventViewModel.InitResult.Success) {
                requireActivity().clearFocusAndHideKeyboard(view)
                findNavController().navigateUp()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.InitResult.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.InitResult.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.InitResult.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                    }
                    else -> Unit // TODO refactor and use one `when` expression
                }
                jumpToMonthView()
            }
        }
    }

    override fun onNavigationIconClicked(): Boolean {
        val processingEvent = eventViewModel.eventFormState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return true
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                jumpToMonthView()
            }
        } else {
            jumpToMonthView()
        }
        return true
    }

    private fun jumpToMonthView() {
        activity?.clearFocusAndHideKeyboard(view)
        if (!findNavController().popBackStack(R.id.nav_calendar, false)) {
            // TODO this is a workaround for navigating back to month view after opening EventForm from EventDetails
            //  that was opened from system notification
            //  R.id.nav_calendar is not in the hierarchy so popping backstack will fail and we need
            //  to navigate manually
            findNavController().navigate(Navigation.Deeplink.toMonth())
        }
        mainViewModel.triggerMainViewActions.update { true }
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.event_discard_changes_title)
            .setMessage(R.string.event_discard_changes_description)
            .setPositiveButton(R.string.event_discard_changes_confirm, callback)
            .setNegativeButton(R.string.event_discard_changes_cancel) { _, _ -> }
            .show()
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialogToolbarContent, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(R.string.action_save)
            setOnSingleClickListener {
                persistFormData()

                // Go back to main view if no changes have been made
                if (navigationArguments.eventId?.isNotEmpty() == true && eventViewModel.hasEventBeenEdited().not()) {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    findNavController().navigateUp()
                    return@setOnSingleClickListener
                }

                if (!mainViewModel.isConnectedToNetwork) {
                    view?.displaySnackBar(getString(R.string.snack_network_error))
                    return@setOnSingleClickListener
                }

                lifecycleScope.launch {
                    eventViewModel.onSaveClick(
                        provideDisplayDialog(),
                        navigationArguments.eventId,
                        navigationArguments.occurrenceNumber,
                        calendarViewModel.timeFormatIs24Hour(requireContext())
                    )
                }
            }
        }

        loadingAction = layoutInflater.inflate(R.layout.toolbar_action_loader, dialogToolbarContent, false)
        loadingAction.visibleOrGone(false)

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            addView(
                buttonSave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                loadingAction, layoutParams
            )
        }
    }

    // TODO remove when the issue with invalid sender Address is fixed
    private suspend fun invalidUserAddressLogoutHack() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, false)) {
            logger.e("EventFormFragment: hack was performed but we force logout again")
        }

        with (sharedPreferences.edit()) {
            putBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, true)
            apply()
        }

        logger.i("EventFormFragment: hack detected invalid user address, logging out")
        accountViewModel.logoutPrimary()
        calendarViewModel.shutdown()
    }

    private fun persistFormData() {
        eventViewModel.persistRecurrenceFormData(
            binding.eventFormTitle.text.toString().ifBlank { null },
            binding.eventFormLocation.text.toString().ifBlank { null },
            binding.eventFormDescription.text.toString().ifBlank { null }
        )

        // TODO CREATE EVENT WITHOUT SAVING BEFOREHAND? EXAMPLE CALL -> calendarViewModel.TEST_CREATE_EVENT_TODO()
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentEventFormBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {

            // TODO Fix transition so title hint doesn't blink on screen
            if (navigationArguments.eventId != null) binding.eventFormTitle.hint = ""

            // TODO maybe don't wait for init to be done, but show loading screen and maybe errors

            val prefill: Boolean = navigationArguments.prefill

            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus = withContext(Dispatchers.Main) {
                if (userId == null) EventViewModel.InitResult.Error.Default("user ID is null in EventDetailsFragment onViewCreated")
                else if (prefill) {
                    // Use arguments provided to prefill event form fields
                    val startMillis: Long = navigationArguments.startMillis
                    val endMillis: Long = navigationArguments.endMillis
                    val timeZoneId: String = Uri.decode(navigationArguments.timeZoneId)
                    val allDay: Boolean = navigationArguments.allDay
                    val title: String = Uri.decode(navigationArguments.title)
                    val description: String = Uri.decode(navigationArguments.description)
                    val location: String = Uri.decode(navigationArguments.location)
                    val rRule: String = Uri.decode(navigationArguments.rRule)

                    val startZonedDateTime =
                        if (startMillis == 0L) {
                            Instant.now().atZone(ZoneId.of(timeZoneId))
                        } else {
                            Instant.ofEpochMilli(startMillis).atZone(ZoneId.of(timeZoneId))
                        }
                    val endZonedDateTime =
                        if (endMillis == 0L) {
                            null // We use null here and let EventVM handle the end time with calendar default event duration
                        } else {
                            Instant.ofEpochMilli(endMillis).atZone(ZoneId.of(timeZoneId))
                        }
                    eventViewModel.initialise(
                        userId,
                        editMode = true,
                        meetIntegrations = featureFlagViewModel.enabledMeetIntegrations(),
                        null,
                        null,
                        startZonedDateTime.toLocalDate().toString(),
                        startZonedDateTime.toLocalTime().toString(),
                        endZonedDateTime?.toLocalDate()?.toString(),
                        endZonedDateTime?.toLocalTime()?.toString(),
                        allDay,
                        timeZoneId,
                        title,
                        description,
                        location,
                        rRule
                    )
                } else {
                    eventViewModel.initialise(
                        userId,
                        editMode = true,
                        meetIntegrations = featureFlagViewModel.enabledMeetIntegrations(),
                        navigationArguments.eventId,
                        if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                        navigationArguments.initStartDate,
                        navigationArguments.initStartTime
                    )
                }
            }

            if (viewModeInitStatus == EventViewModel.InitResult.Success) {
                if (navigationArguments.eventId == null) {
                    binding.eventFormTitle.requestFocus()
                    requireContext().showKeyboard()
                }
                launch {
                    eventViewModel.getSingleEditsInfo()
                }
                observeEventLiveData()
                observeEventSnackState(coroutineContext)
                attachActionHandlers()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.InitResult.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.InitResult.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.InitResult.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(
                            if (navigationArguments.eventId != null) getString(R.string.snack_event_opening_edit_error)
                            else {
                                when (viewModeInitStatus) {
                                    is EventViewModel.InitResult.Error.InitDefaultCalendarError -> getString(R.string.snack_create_event_no_active_calendar)
                                    is EventViewModel.InitResult.Error.Default -> getString(R.string.snack_event_init_error)
                                }
                            }
                        )
                    }
                    else -> Unit // TODO refactor and use one `when` expression
                }
                if (navigationArguments.eventId == null) jumpToMonthView()
                else onBackPressedCustom()
            }

            eventViewModel.eventFormState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventState ->
                val processingEvent = eventState is EventViewModel.EventState.Processing

                // Update action bar buttons visibility
                loadingAction.visibleOrGone(processingEvent)
                buttonSave.visibleOrGone(!processingEvent)

                // Disable/Enable all items linked to actions from our view
                binding.eventFormTitle.isEnabled = !processingEvent
                binding.eventFormLocation.isEnabled = !processingEvent
                binding.eventFormDescription.isEnabled = !processingEvent
                binding.eventFormAllDayPress.root.isEnabled = !processingEvent
                binding.eventFormAllDaySwitch.isClickable = !processingEvent
                binding.eventFormAllDaySwitch.isFocusable = !processingEvent
                binding.eventFormTimezonePress.root.isEnabled = !processingEvent
                binding.eventFormStartDatePress.isEnabled = !processingEvent
                binding.eventFormEndDatePress.isEnabled = !processingEvent
                binding.eventFormStartTimePress.isEnabled = !processingEvent
                binding.eventFormEndTimePress.isEnabled = !processingEvent
                binding.eventFormCalendarPress.root.isEnabled = !processingEvent
                binding.eventFormColorPress.root.isEnabled = !processingEvent
                binding.eventFormRecurrencePress.root.isEnabled = !processingEvent
                binding.eventFormAlarmPress.root.isEnabled = !processingEvent
                binding.eventFormConferenceRemove.isEnabled = !processingEvent

                for (i in 0 until binding.eventFormAlarmList.childCount) {
                    // Disable the delete buttons from inside alarm items views
                    binding.eventFormAlarmList.getChildAt(i)?.findViewById<View>(
                        R.id.item_simple_text_button_delete
                    )?.isEnabled = !processingEvent
                }

                binding.eventFormParticipantPress.root.isEnabled = !processingEvent
                for (i in 0 until binding.eventFormParticipantChipGroup.childCount) {
                    // Disable the chips from inside attendees items views
                    binding.eventFormParticipantChipGroup.getChildAt(i)?.isEnabled = !processingEvent
                }

                if (eventState is EventViewModel.EventState.UserAddressInvalidForEncryption) {
                    lifecycleScope.launch {
                        invalidUserAddressLogoutHack()
                    }
                }
            }
        }
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { nullableEvent: Event? ->

            val event = nullableEvent ?: return@Observer

            // TODO Fix transition so title hint doesn't blink on screen
            binding.eventFormTitle.hint = resources.getString(R.string.event_hint_title)
            event.summary?.let {
                if (it.isNotEmpty()) binding.eventFormTitle.setText(it)
            } ?: binding.eventFormTitle.clearText()
            event.location?.let { binding.eventFormLocation.setText(it) } ?: binding.eventFormLocation.clearText()
            event.description?.let { binding.eventFormDescription.setText(it) } ?: binding.eventFormDescription.clearText()

            binding.eventFormTitle.doAfterTextChanged { if (binding.eventFormTitle.hasFocus()) persistFormData() }
            binding.eventFormLocation.doAfterTextChanged { if (binding.eventFormLocation.hasFocus()) persistFormData() }
            binding.eventFormDescription.doAfterTextChanged { if (binding.eventFormDescription.hasFocus()) persistFormData() }

            val hasMeetConference = featureFlagViewModel.enabledMeetIntegrations().contains(event.meetType) && !event.meetUrl.isNullOrBlank()
            binding.eventFormConferenceLayout.visibleOrGone(hasMeetConference)
            if (hasMeetConference) {
                binding.eventFormConference.setText(when (event.meetType) {
                    MeetIntegrationType.ProtonMeet, null -> "Proton Meet" // TODO: Localize
                    MeetIntegrationType.Zoom -> getString(R.string.zoom_meeting_title)
                })
            }

            ImageViewCompat.setImageTintList(
                binding.eventFormLocationIcon,
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (binding.eventFormLocation.text.isEmpty()) R.color.icon_hint else R.color.icon_norm
                    )
                )
            )
            ImageViewCompat.setImageTintList(
                binding.eventFormDescriptionIcon,
                ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (binding.eventFormDescription.text.isEmpty()) R.color.icon_hint else R.color.icon_norm
                    )
                )
            )

            binding.eventFormAllDaySwitch.isChecked = event.isAllDay()
            binding.eventFormAllDaySwitch.jumpDrawablesToCurrentState()

            binding.eventFormPartialDayStart.visibleOrGone(!event.isAllDay())
            binding.eventFormPartialDayEnd.visibleOrGone(!event.isAllDay())
            binding.eventFormTimezoneLayout.visibleOrGone(!event.isAllDay())

            if (eventViewModel.validateDateTime()) {
                binding.eventFormStartDate.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
                binding.eventFormStartTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_norm))
            } else {
                binding.eventFormStartDate.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.textColorValidationError
                    )
                )
                binding.eventFormStartTime.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.textColorValidationError
                    )
                )
            }

            val formattedStart = event.formatStart(
                eventViewModel.eventTimeZoneId,
                eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            )
            binding.eventFormStartDate.text = formattedStart.first ?: ""
            binding.eventFormStartTime.text = formattedStart.second ?: ""

            val formattedEnd = event.formatEnd(
                eventViewModel.eventTimeZoneId,
                eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            )
            binding.eventFormEndDate.text = formattedEnd.first ?: ""
            binding.eventFormEndTime.text = formattedEnd.second ?: ""

            binding.eventFormTimezone.text = formatTimeZoneId(
                event.defaultTimeZone!!,
                eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toInstant()!!
            ) // TimeZone picked by user is saved in iCalendar's Default Timezone

            binding.eventFormCalendar.text = event.calendar.name
            binding.eventFormCalendarWithoutDot.text = event.calendar.name // TODO Delete once color per event is released

            val isColorPerEventEnabled = featureFlagViewModel.isColorPerEventEnabled()
            binding.eventFormCalendarIcon.visibleOrGone(isColorPerEventEnabled)
            binding.eventFormCalendarColorIcon.visibleOrGone(isColorPerEventEnabled)
            binding.eventFormCalendarDot.visibleOrGone(!isColorPerEventEnabled)
            binding.eventFormCalendar.visibleOrInvisible(isColorPerEventEnabled)
            binding.eventFormCalendarWithoutDot.visibleOrGone(!isColorPerEventEnabled) // TODO Delete once color per event is released
            ImageViewCompat.setImageTintList(
                if (!isColorPerEventEnabled) binding.eventFormCalendarDot
                else binding.eventFormCalendarColorIcon,
                ColorStateList.valueOf(Color.parseColor(event.calendar.color))
            )

            binding.eventFormColorLayout.visibleOrGone(isColorPerEventEnabled)
            if (isColorPerEventEnabled) {
                val isFreeUser = eventViewModel.user.hasSubscriptionForMail().not()
                binding.eventFormColorUpgrade.visibleOrGone(isFreeUser)
                binding.eventFormColor.text = ColorUtils.getColorNameForHex(
                    resources.getStringArray(R.array.colors_names),
                    resources.getStringArray(R.array.colors_values),
                    event.getDisplayColor(isFreeUser)
                ) ?: getString(R.string.undefined_color)
                binding.eventFormColorDefault.visibleOrGone(
                    event.getDisplayColor(isFreeUser) == event.calendar.color
                )

                ImageViewCompat.setImageTintList(
                    binding.eventFormColorIcon,
                    ColorStateList.valueOf(
                        Color.parseColor(
                            event.getDisplayColor(isFreeUser)
                        )
                    )
                )
            } else {
                binding.eventFormColorUpgrade.visibleOrGone(false)
            }

            binding.eventFormRecurrence.text =
                AndroidUtils.formatRecurrence(requireContext().resources, event, eventViewModel.eventTimeZoneId)
                    ?: resources.getString(R.string.event_recurrence_none)

            displayAlarms()

            binding.eventFormParticipantChipGroup.removeAllViews()

            lifecycleScope.launch {
                val protonContacts = ArrayList<ContactEmail>()
                accountViewModel.getPrimaryUserId()?.let {
                    protonContacts.addAll(mainViewModel.getProtonContacts(it) ?: emptyList())
                }

                withStarted {
                    ProtonUtilsImpl.matchAttendeesWithContacts(
                        event.iCalEvent.attendees,
                        requireContext().getDeviceContacts() ?: emptyList(),
                        protonContacts
                    ).take(ATTENDEE_MAX_CHIP_ALLOWED).forEach { attendee ->
                        val chipTitle = if (attendee.commonName.isNotEmpty()) attendee.commonName else attendee.extractEmail()
                        chipTitle?.let {
                            addAttendeeChip(chipTitle)
                        }
                    }
                }
            }

            if (!event.iCalEvent.attendees.isNullOrEmpty()) {
                addAttendeeChip(getString(R.string.event_current_user_organizer))

                if (event.iCalEvent.attendees.size > ATTENDEE_MAX_CHIP_ALLOWED) {
                    addAttendeeChip(
                        getString(
                            R.string.event_max_attendee_chip,
                            event.iCalEvent.attendees.size - ATTENDEE_MAX_CHIP_ALLOWED
                        )
                    )
                }
            }
            ImageViewCompat.setImageTintList(
                binding.eventFormParticipantIcon, ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (event.iCalEvent.attendees.isNullOrEmpty()) R.color.icon_hint
                        else R.color.icon_norm
                    )
                )
            )

            lifecycleScope.launch {
                binding.eventFormParticipantLayout.visibleOrGone(CalendarFeatureFlag.AddAttendees.fallbackValue &&
                        eventViewModel.allowSendForCalendarAddress() &&
                        event.hasProtonUid
                )

                binding.eventFormParticipantChipGroup.visibleOrGone(!event.iCalEvent.attendees.isNullOrEmpty())

                val isCreateEvent = navigationArguments.eventId.isNullOrEmpty()
                // We do not display any disclaimer if there is only one selectable calendar
                val selectableCalendarsCount = getSelectableCalendars()?.size ?: 0
                binding.eventFormCalendarDisclaimer.text =
                    if (selectableCalendarsCount > 1 && eventViewModel.isOriginalEventPartOfChain()) {
                        getString(R.string.change_calendar_recurring_disclaimer)
                    } else if (selectableCalendarsCount > 1 && eventViewModel.isEventAnInvitation() && !isCreateEvent) {
                        getString(R.string.invite_change_calendar_disclaimer)
                    } else ""
                binding.eventFormCalendarDisclaimer.visibleOrGone(binding.eventFormCalendarDisclaimer.text.isNotEmpty())
                binding.eventFormCalendarPress.root.isEnabled = binding.eventFormCalendarDisclaimer.text.isEmpty()
                if (calendarViewModel.getPersonalCalendarsCount() <= 1 && (event.iCalEvent.attendees?.isNotEmpty() == true || event.iCalEvent.organizer != null)) {
                    binding.eventFormCalendarPress.root.isEnabled = false
                }

                if (!event.calendar.isOwner) {
                    // Editing a shared calendar event
                    binding.eventFormParticipant.visibleOrGone(false)
                    binding.eventFormParticipantDisclaimer.visibleOrGone(true)
                    binding.eventFormParticipantDisclaimer.text = getString(R.string.invite_in_shared_calendar_disclaimer)
                    binding.eventFormParticipantPress.root.visibleOrGone(false)
                } else if (eventViewModel.hasCalendarBeenChanged()) {
                    // Changing calendar
                    binding.eventFormParticipant.visibleOrGone(false)
                    binding.eventFormParticipantDisclaimer.visibleOrGone(true)
                    binding.eventFormParticipantDisclaimer.text = getString(R.string.snack_event_edit_calendar_with_attendees_error)
                    binding.eventFormParticipantPress.root.visibleOrGone(false)
                } else {
                    // Default state
                    binding.eventFormParticipant.visibleOrGone(event.hasProtonUid && event.iCalEvent.attendees.isNullOrEmpty())
                    binding.eventFormParticipantDisclaimer.visibleOrGone(false)
                    binding.eventFormParticipantDisclaimer.text = ""
                    binding.eventFormParticipantPress.root.visibleOrGone(true)
                }
            }
        })
    }

    private fun observeEventSnackState(coroutineContext: CoroutineContext) {
        eventViewModel.eventFormSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventSnackState ->
            eventSnackState?.let {
                when (it) {
                    is EventViewModel.EventSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is EventViewModel.EventSnackState.DisplaySnackWithUriAction -> {
                        view?.snack(
                            message = it.message,
                            type = SnackType.Error,
                            action = it.action,
                            actionOnClick = {
                                val browserIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(it.uri)
                                )
                                startActivity(browserIntent)
                            },
                        )
                    }
                    is EventViewModel.EventSnackState.DisplaySnackReturnToMonth -> {
                        requireActivity().displaySnackBar(it.message)

                        lifecycleScope.launch {
                            val newSelectedDate = it.newSelectedDate
                            val newSelectedTime = it.newSelectedTime
                            val selectedDateTime = calendarViewModel.selectedDateTime.value
                            val selectedDate = selectedDateTime?.first
                            val selectedTime = selectedDateTime?.second
                            val isDifferentSelectedDate = newSelectedDate != null && selectedDate != newSelectedDate
                            val isDifferentSelectedTime = newSelectedTime != null && selectedTime != newSelectedTime
                            val isDayVisible =
                                when (calendarViewModel.viewMode.value) {
                                    ViewMode.AGENDA,
                                    ViewMode.DAY -> newSelectedDate == selectedDate
                                    ViewMode.MONTH -> {
                                        newSelectedDate?.year == selectedDate?.year && newSelectedDate?.month == selectedDate?.month
                                    }
                                    ViewMode.THREE_DAY -> {
                                        selectedDate?.let {
                                            newSelectedDate?.isBetween(selectedDate, selectedDate.plusDays(2))
                                        } ?: false
                                    }
                                    ViewMode.WEEK -> {
                                        val weekStart = calendarViewModel.getWeekStart()
                                        val firstDayOfWeek = selectedDate?.firstDayOfWeek(weekStart)
                                        if (selectedDate != null && newSelectedDate != null && firstDayOfWeek != null) {
                                            newSelectedDate.isBetween(firstDayOfWeek, firstDayOfWeek.plusDays(7))
                                        } else false
                                    }
                                    else -> false
                                }
                            if (newSelectedDate != null && isDifferentSelectedDate && !isDayVisible) {
                                calendarViewModel.handleDaySelected(newSelectedDate, newSelectedTime?.getTimeWithPadding())
                            } else if (selectedDate != null && isDayVisible && isDifferentSelectedTime) {
                                // Use currently selected date and new selected time
                                calendarViewModel.handleDaySelected(selectedDate, newSelectedTime?.getTimeWithPadding())
                            }

                            // Use jumpToMonthView to handle navigation when opening details from notification
                            jumpToMonthView()
                        }
                    }
                }
                eventViewModel.eventFormSnackState.value = null
            }
        }
    }

    private fun addAttendeeChip(title: String) {
        val chipViewBinding = ItemAttendeeChipBinding.inflate(layoutInflater, binding.eventFormParticipantChipGroup, false)
        val chip = chipViewBinding.root
        chip.text = title
        chip.setOnSingleClickListener {
            checkNavigationToAttendees()
        }
        binding.eventFormParticipantChipGroup.addView(chip)
    }

    private suspend fun getSelectableCalendars(): List<Calendar>? {
        // Return the calendars that can be selected by the user for that event
        return calendarViewModel.getUserCalendars()?.filter {
            if (eventViewModel.eventLiveData.value!!.iCalEvent.attendees.isNullOrEmpty()) {
                it.isActive && it.allowEditEvents
            } else {
                // We do not allow invitations to be saved in shared calendar as a member
                it.isActive && it.isOwner
            }
        }
    }

    private fun attachActionHandlers() {

        binding.eventFormLocation.setOnFocusChangeListener { _, hasFocus ->
            when {
                hasFocus ->
                    ImageViewCompat.setImageTintList(binding.eventFormLocationIcon, ColorStateList.valueOf(requireContext().getColorFromAttr(R.attr.proton_icon_accent)))
                binding.eventFormLocation.text.isNotEmpty() ->
                    ImageViewCompat.setImageTintList(binding.eventFormLocationIcon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm)))
                else ->
                    ImageViewCompat.setImageTintList(binding.eventFormLocationIcon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_hint)))
            }
        }
        binding.eventFormDescription.setOnFocusChangeListener { _, hasFocus ->
            when {
                hasFocus ->
                    ImageViewCompat.setImageTintList(binding.eventFormDescriptionIcon, ColorStateList.valueOf(requireContext().getColorFromAttr(R.attr.proton_icon_accent)))
                binding.eventFormDescription.text.isNotEmpty() ->
                    ImageViewCompat.setImageTintList(binding.eventFormDescriptionIcon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm)))
                else ->
                    ImageViewCompat.setImageTintList(binding.eventFormDescriptionIcon, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_hint)))
            }
        }

        binding.eventFormAllDayPress.root.setOnClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            binding.eventFormAllDaySwitch.performClick()
        }
        binding.eventFormAllDaySwitch.setOnCheckedChangeListener { _, checked ->
            eventViewModel.handleAllDaySwitch(checked)
        }

        binding.eventFormTimezonePress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            val forInstant = eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toInstant()!!
            val formattedTimeZoneIds = allowedTimezoneIds.map {
                formatTimeZoneId(it, forInstant)
            }.toTypedArray()
            formattedTimeZoneIds.sortFormattedTimeZoneIds()
            val defaultTimeZone = eventViewModel.eventLiveData.value?.defaultTimeZone
            val selectedIndex =
                if (defaultTimeZone == null) -1
                else formattedTimeZoneIds.indexOf(formatTimeZoneId(defaultTimeZone, forInstant))

            AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.settings_timezone_title), formattedTimeZoneIds, selectedIndex) {
                eventViewModel.handleTimeZone(formattedTimeZoneIds[it].formattedTimeZoneToId())
            }
        }

        binding.eventFormStartDatePress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val date = eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(
                context = requireContext(),
                firstDayOfWeek = eventViewModel.userSettings.weekStartDayOfWeek(),
                initialDate = date,
                minDate = FormValidation.MIN_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate(),
                maxDate = FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate()) {
                eventViewModel.handleStartDate(it)
            }
        }

        binding.eventFormEndDatePress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val date = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.eventTimeZoneId)?.toLocalDate()
            AndroidUtils.displayDatePicker(
                context = requireContext(),
                firstDayOfWeek = eventViewModel.userSettings.weekStartDayOfWeek(),
                initialDate = date,
                minDate = FormValidation.MIN_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate(),
                maxDate = FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate()
            ) {
                eventViewModel.handleEndDate(it)
            }
        }

        binding.eventFormStartTimePress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val is24Hour = eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            val time = eventViewModel.eventLiveData.value?.getStart(eventViewModel.eventTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleStartTime(it)
            }
        }

        binding.eventFormEndTimePress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val is24Hour = eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
            val time = eventViewModel.eventLiveData.value?.getEnd(eventViewModel.eventTimeZoneId)?.toLocalTime()
            AndroidUtils.displayTimePicker(requireContext(), time, is24Hour) {
                eventViewModel.handleEndTime(it)
            }
        }

        binding.eventFormCalendarPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            lifecycleScope.launch {
                val calendars = getSelectableCalendars()

                // TODO Save active calendars in calendar VM to avoid triggering click effect when not needed
                if (calendars == null || calendars.size <= 1) return@launch

                val selectedIndex = calendars.indexOfFirst { it.id == eventViewModel.eventLiveData.value!!.calendar.id }

                AndroidUtils.displayCalendarPicker(requireContext(), resources.getString(R.string.dialog_title_calendar_picker), calendars.toTypedArray(), selectedIndex) {

                    lifecycleScope.launch {
                        if (!eventViewModel.handleCalendar(calendars[it])) {
                            view?.displaySnackBar(getString(R.string.snack_event_calendar_error))
                        }
                    }

                }
            }
        }

        val isFreeUser = eventViewModel.user.hasSubscriptionForMail().not()
        binding.eventFormColorPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            lifecycleScope.launch {
                if (isFreeUser) {
                    view?.displaySnackBar(getString(R.string.snack_color_per_event_paid_feature))
                } else {
                    ColorUtils.displayColorPicker(
                        requireContext(),
                        resources.getString(R.string.dialog_title_event_color_picker),
                        resources.getStringArray(R.array.colors_names),
                        resources.getStringArray(R.array.colors_values),
                        eventViewModel.eventLiveData.value!!.getDisplayColor(isFreeUser),
                        eventViewModel.eventLiveData.value!!.calendar.color
                    ) {
                        eventViewModel.handleColor(it)
                    }
                }
            }
        }

        binding.eventFormRecurrencePress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForRecurrence()
            findNavController().navigate(R.id.nav_event_form_recurrence)
        }

        binding.eventFormParticipantPress.root.setOnSingleClickListener {
            if (eventViewModel.eventLiveData.value?.calendar?.isOwner == true) {
                checkNavigationToAttendees()
            }
        }

        binding.eventFormConferenceRemove.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.removeConferenceLink()
        }
    }

    private fun shouldShowContactsPermissionsDialog(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(SharedPreferencesKeys.SHOW_CONTACTS_PERMISSIONS_DIALOG, true)
    }

    private fun changeContactsPermissionsPreferences(showDialog: Boolean) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val editor = sharedPreferences.edit()
        editor.putBoolean(SharedPreferencesKeys.SHOW_CONTACTS_PERMISSIONS_DIALOG, showDialog)
        editor.apply()
    }

    private fun checkNavigationToAttendees() {
        requireActivity().clearFocusAndHideKeyboard(view)

        if (!eventViewModel.isChangingAttendeesAllowed()) {
            view?.displaySnackBar(getString(R.string.snack_event_edit_calendar_with_attendees_error))
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                navigateToAttendees()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)
                    && shouldShowContactsPermissionsDialog() -> {
                displayCustomPermissionDialog(true)
            }
            shouldShowContactsPermissionsDialog() -> {
                requestPermissionLauncher.launch(
                    Manifest.permission.READ_CONTACTS)
            }
            else -> {
                navigateToAttendees()
            }
        }
    }

    private fun navigateToAttendees() {
        lifecycleScope.launch {
            if (CalendarFeatureFlag.EditAttendeesAsOrganizer.fallbackValue) {
                findNavController().navigate(R.id.nav_event_form_attendees)
            } else {
                val event = eventViewModel.eventLiveData.value!!
                val readOnly = event.isAnInvitation && !navigationArguments.eventId.isNullOrEmpty()
                val bundle = Bundle().apply {
                    putBoolean(READ_ONLY_ARG, readOnly)
                }
                findNavController().navigate(R.id.nav_event_form_attendees, bundle)
            }
        }
    }

    private fun displayCustomPermissionDialog(openSettings: Boolean) {
        val dialogCheckboxBinding = DialogCheckboxBinding.inflate(layoutInflater, null, false)

        dialogCheckboxBinding.dialogCheckboxHeader.text = getString(R.string.contacts_permission_dialog_message)
        dialogCheckboxBinding.dialogCheckboxPress.root.setOnClickListener {
            dialogCheckboxBinding.dialogCheckbox.performClick()
        }
        val positiveButtonText =
            if (openSettings) R.string.contacts_permission_dialog_open_settings
            else R.string.contacts_permission_dialog_allow

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.contacts_permission_dialog_title)
            .setView(dialogCheckboxBinding.root)
            .setPositiveButton(positiveButtonText) { _, _ ->
                if (openSettings) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.data = Uri.fromParts("package", requireContext().packageName, null)
                    startActivity(intent)
                } else {
                    requestPermissionLauncher.launch(
                        Manifest.permission.READ_CONTACTS)
                }
            }
            .setNegativeButton(R.string.contacts_permission_dialog_cancel) { _, _ ->
                navigateToAttendees()
            }
            .setOnCancelListener {
                navigateToAttendees()
            }
            .setOnDismissListener {
                if (dialogCheckboxBinding.dialogCheckbox.isChecked) {
                    changeContactsPermissionsPreferences(false)
                }
            }
            .show()
    }

    private fun displayAlarms() {
        binding.eventFormAlarmList.removeAllViews()
        binding.eventFormAlarmIcon.visibleOrGone(true)

        val event = eventViewModel.eventLiveData.value!!

        event.alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

            val alarmViewBinding = ItemAlarmTextButtonBinding.inflate(layoutInflater, binding.eventFormAlarmList, false)
            alarmViewBinding.itemSimpleTextButtonTitle.apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext())), event.getStart(eventViewModel.eventTimeZoneId), alarm)
                isClickable = false
            }
            alarmViewBinding.itemSimpleTextButtonDelete.apply {
                setOnSingleClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    eventViewModel.handleAlarmDelete(alarm)
                }
                isClickable = true
            }
            if (index == 0) binding.eventFormAlarmIcon.visibleOrGone(false)
            binding.eventFormAlarmList.addView(alarmViewBinding.root)
        }

        // "add alarm" button
        binding.eventFormAlarmPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForAlarm()
            val bundle = Bundle().apply {
                putBoolean(IS_ALL_DAY_ARG, eventViewModel.eventLiveData.value!!.isAllDay())
                putInt(DEFAULT_NOTIFICATIONS_TYPE_ARG, EventFormAlarmFragment.DefaultNotificationsType.EVENT.value)
            }
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
        binding.eventFormAlarm.visibleOrGone(!eventViewModel.isAlarmLimitReached())
    }
}
