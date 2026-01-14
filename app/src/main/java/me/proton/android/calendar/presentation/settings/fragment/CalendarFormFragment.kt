package me.proton.android.calendar.presentation.settings.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.component.VAlarm
import biweekly.property.Action
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarForm
import me.proton.android.calendar.common.CalendarForm.CALENDAR_DESCRIPTION_CHARACTER_LIMIT
import me.proton.android.calendar.common.CalendarForm.CALENDAR_NAME_CHARACTER_LIMIT
import me.proton.android.calendar.common.CalendarForm.DEFAULT_NOTIFICATIONS_COUNT_MAX
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ColorUtils
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.databinding.FragmentCalendarFormBinding
import me.proton.android.calendar.databinding.ItemAlarmTextButtonBinding
import me.proton.android.calendar.presentation.calendar.fragment.EventFormAlarmFragment
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.core.presentation.utils.onTextChange
import org.koin.core.KoinComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class CalendarFormFragment : BaseDialogFragment<FragmentCalendarFormBinding>(), KoinComponent {

    private val navigationArguments: CalendarFormFragmentArgs by navArgs()

    override val TAG: String
        get() = "CalendarFormFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_calendar_form

    override val navigateUp = false

    private val calendarFormViewModel: CalendarFormViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private var calendarId: String? = null

    override fun onBackPressedCustom() {
        requireActivity().clearFocusAndHideKeyboard(view)

        // Display snack and return if we're saving the calendar changes
        val processingCalendar = calendarFormViewModel.calendarFormState.value is CalendarFormViewModel.CalendarFormState.Processing
        if (processingCalendar) {
            view?.displaySnackBar(getString(R.string.snack_calendar_saving))
            return
        }

        // Save calendar name in VM
        calendarFormViewModel.handleCalendarName(binding.calendarFormNameValue.text.toString())

        // Save calendar description in VM
        calendarFormViewModel.handleCalendarDescription(binding.calendarFormDescriptionValue.text.toString())

        // Check if we need to display discard changes dialog
        if (calendarFormViewModel.hasFormBeenEdited()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.event_discard_changes_title)
                .setMessage(R.string.event_discard_changes_description)
                .setPositiveButton(R.string.event_discard_changes_confirm) { _, _ ->
                    findNavController().navigateUp()
                }
                .setNegativeButton(R.string.event_discard_changes_cancel) { _, _ -> }
                .show()
        } else findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialogToolbarContent, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(
                R.string.calendar_form_save
            )
            setOnSingleClickListener {
                if (binding.calendarFormNameValue.text.toString().isBlank()) {
                    binding.calendarFormNameValue.setInputError(getString(R.string.calendar_form_name_required_error))
                    return@setOnSingleClickListener
                }

                requireActivity().clearFocusAndHideKeyboard(view)

                // Save calendar name in VM
                calendarFormViewModel.handleCalendarName(binding.calendarFormNameValue.text.toString().trim().replace("\n", ""))

                // Save calendar description in VM
                calendarFormViewModel.handleCalendarDescription(binding.calendarFormDescriptionValue.text.toString().trim())

                if (!mainViewModel.isConnectedToNetwork) {
                    view?.displaySnackBar(getString(R.string.snack_network_error))
                    return@setOnSingleClickListener
                }

                lifecycleScope.launch {
                    if (calendarFormViewModel.hasFormBeenEdited()) {
                        // Save new form values
                        val returnToSettings = findNavController().previousBackStackEntry?.destination?.id == R.id.nav_settings || calendarId != null
                        calendarFormViewModel.handleSaveCalendarForm(returnToSettings)
                    } else findNavController().navigateUp()
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

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentCalendarFormBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarFormViewModel.resetFormValues()

        calendarId = navigationArguments.calendarId

        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text =
            if (calendarId != null) getString(R.string.calendar_form_update_title)
            else getString(R.string.calendar_form_create_title)

        lifecycleScope.launch {
            calendarId?.let {
                // Init form for existing calendar
                calendarFormViewModel.initUpdateCalendarForm(it)

                // hide "default event duration" for Subscribed and Shared Calendars
                binding.calendarFormDefaultEventDurationLayout.visibleOrGone(!calendarFormViewModel.calendarIsSubscribed && !calendarFormViewModel.calendarIsSharedWithMe)
            } ?: run {
                // Init character limit text
                binding.calendarFormNameValue.helpText = resources.getQuantityString(
                    R.plurals.calendar_form_name_character_limit,
                    CALENDAR_NAME_CHARACTER_LIMIT,
                    0,
                    CALENDAR_NAME_CHARACTER_LIMIT
                )

                // Init character limit text
                binding.calendarFormDescriptionValue.helpText = resources.getQuantityString(
                    R.plurals.calendar_form_description_character_limit,
                    CALENDAR_DESCRIPTION_CHARACTER_LIMIT,
                    0,
                    CALENDAR_DESCRIPTION_CHARACTER_LIMIT
                )

                binding.calendarFormNameValue.requestFocus()
                requireContext().showKeyboard()

                // Use random color from array as calendar color
                // Init form for new calendar
                calendarFormViewModel.initCreateCalendarForm(
                    resources.getStringArray(R.array.colors_values).random()
                )
            }
        }

        initOnClickListeners(calendarId == null)

        binding.calendarFormNameValue.onTextChange {
            if (it.isNotEmpty()) binding.calendarFormNameValue.clearInputError()
            binding.calendarFormNameValue.helpText = resources.getQuantityString(
                R.plurals.calendar_form_name_character_limit,
                CALENDAR_NAME_CHARACTER_LIMIT,
                it.length,
                CALENDAR_NAME_CHARACTER_LIMIT
            )
        }

        binding.calendarFormDescriptionValue.onTextChange {
            if (it.isNotEmpty()) binding.calendarFormDescriptionValue.clearInputError()
            binding.calendarFormDescriptionValue.helpText = resources.getQuantityString(
                R.plurals.calendar_form_description_character_limit,
                CALENDAR_DESCRIPTION_CHARACTER_LIMIT,
                it.length,
                CALENDAR_DESCRIPTION_CHARACTER_LIMIT
            )
        }

        observeCalendarFormSnackState(lifecycleScope.coroutineContext)
        observeCalendarFormValues()
    }

    private fun observeCalendarFormValues() {

        calendarFormViewModel.calendarName.observe(viewLifecycleOwner) { calendarName ->
            binding.calendarFormNameValue.text = calendarName
            binding.calendarFormNameValue.helpText = resources.getQuantityString(
                R.plurals.calendar_form_name_character_limit,
                CALENDAR_NAME_CHARACTER_LIMIT,
                calendarName.length,
                CALENDAR_NAME_CHARACTER_LIMIT
            )
        }
        calendarFormViewModel.calendarDescription.observe(viewLifecycleOwner) { calendarDescription ->
            binding.calendarFormDescriptionValue.text = calendarDescription
            binding.calendarFormDescriptionValue.helpText = resources.getQuantityString(
                R.plurals.calendar_form_description_character_limit,
                CALENDAR_DESCRIPTION_CHARACTER_LIMIT,
                calendarDescription.length,
                CALENDAR_DESCRIPTION_CHARACTER_LIMIT
            )
        }
        calendarFormViewModel.calendarEmail.observe(viewLifecycleOwner) { calendarEmail ->
            binding.calendarFormDefaultEmailValue.text = calendarEmail
        }
        calendarFormViewModel.defaultEventDuration.observe(viewLifecycleOwner) { defaultEventDuration ->
            binding.calendarFormDefaultEventDurationValue.text = resources.getQuantityString(
                R.plurals.calendar_form_default_event_duration_value,
                defaultEventDuration,
                defaultEventDuration.toString()
            )
        }
        calendarFormViewModel.calendarColor.observe(viewLifecycleOwner) { calendarColor ->
            if (calendarColor.isEmpty()) {
                // Value was reset. Set to background_norm to avoid seeing the color being refreshed
                binding.calendarFormColorIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.background_norm))
                return@observe
            }

            binding.calendarFormColorIcon.imageTintList = ColorStateList.valueOf(calendarColor.toColorInt())

            binding.calendarFormColor.text = ColorUtils.getColorNameForHex(
                resources.getStringArray(R.array.colors_names),
                resources.getStringArray(R.array.colors_values),
                calendarColor
            ) ?: getString(R.string.undefined_color)
        }
        calendarFormViewModel.defaultPartDayAlarms.observe(viewLifecycleOwner) { defaultPartDayAlarms ->
            binding.calendarFormDefaultEventNotifications.visibleOrGone(defaultPartDayAlarms.size < DEFAULT_NOTIFICATIONS_COUNT_MAX)
            displayNotifications(
                allDay = false,
                defaultPartDayAlarms,
                binding.calendarFormDefaultEventNotificationsList,
                binding.calendarFormDefaultEventNotificationsIcon,
                binding.calendarFormDefaultEventNotificationsPress.root
            )
        }
        calendarFormViewModel.defaultAllDayAlarms.observe(viewLifecycleOwner) { defaultAllDayAlarms ->
            binding.calendarFormDefaultAllDayEventNotifications.visibleOrGone(defaultAllDayAlarms.size < DEFAULT_NOTIFICATIONS_COUNT_MAX)
            displayNotifications(
                allDay = true,
                defaultAllDayAlarms,
                binding.calendarFormDefaultAllDayEventNotificationsList,
                binding.calendarFormDefaultAllDayEventNotificationsIcon,
                binding.calendarFormDefaultAllDayEventNotificationsPress.root
            )
        }

        calendarFormViewModel.calendarFormState.asLiveData(lifecycleScope.coroutineContext).observe(viewLifecycleOwner) { eventState ->
            val processingEvent = eventState is CalendarFormViewModel.CalendarFormState.Processing

            // Update action bar buttons visibility
            loadingAction.visibleOrGone(processingEvent)
            buttonSave.visibleOrGone(!processingEvent)

            // Disable/Enable all items linked to actions from our view
            binding.calendarFormNameValue.isEnabled = !processingEvent
            binding.calendarFormDescriptionValue.isEnabled = !processingEvent
            binding.calendarFormColorPress.root.isEnabled = !processingEvent
            binding.calendarFormDefaultEventDurationPress.root.isEnabled = !processingEvent

            binding.calendarFormDefaultEventNotificationsPress.root.isEnabled = !processingEvent
            for (i in 0 until binding.calendarFormDefaultEventNotificationsList.childCount) {
                // Disable the delete buttons from inside alarm items views
                binding.calendarFormDefaultEventNotificationsList.getChildAt(i)?.findViewById<View>(
                    R.id.item_simple_text_button_delete
                )?.isEnabled = !processingEvent
            }

            binding.calendarFormDefaultAllDayEventNotificationsPress.root.isEnabled = !processingEvent
            for (i in 0 until binding.calendarFormDefaultAllDayEventNotificationsList.childCount) {
                // Disable the delete buttons from inside alarm items views
                binding.calendarFormDefaultAllDayEventNotificationsList.getChildAt(i).findViewById<View>(
                    R.id.item_simple_text_button_delete
                )?.isEnabled = !processingEvent
            }
        }
    }

    /**
     * Display notifications list and register listeners for add / remove notifications.
     */
    private fun displayNotifications(
        allDay: Boolean,
        alarms: List<VAlarm>,
        alarmsListView: ViewGroup,
        notificationIcon: View,
        itemViewPress: View
    ) {
        alarmsListView.removeAllViews()
        notificationIcon.visibleOrGone(true)

        lifecycleScope.launch {
            alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

                val alarmViewBinding = ItemAlarmTextButtonBinding.inflate(
                    layoutInflater,
                    alarmsListView,
                    false
                )
                alarmViewBinding.itemSimpleTextButtonTitle.apply {
                    text = AndroidUtils.formatAlarm(
                        resources,
                        allDay,
                        calendarViewModel.timeFormatIs24Hour(requireContext()),
                        LocalDate.now().toDate(ZoneId.systemDefault().id).toZonedDateTime(ZoneId.systemDefault().id, false), // TODO Simplify this
                        alarm
                    )
                    isClickable = false
                }
                alarmViewBinding.itemSimpleTextButtonDelete.apply {
                    // Remove notification listener
                    setOnSingleClickListener {
                        requireActivity().clearFocusAndHideKeyboard(view)
                        calendarFormViewModel.handleAlarmChange(alarm, allDay, isDelete = true)
                    }
                    isClickable = true
                }
                if (index == 0) notificationIcon.visibleOrGone(false)
                alarmsListView.addView(alarmViewBinding.root)
            }
        }

        // Add notification listener
        itemViewPress.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            val bundle = Bundle().apply {
                putBoolean(FragmentArguments.IS_ALL_DAY_ARG, allDay)
                putInt(FragmentArguments.DEFAULT_NOTIFICATIONS_TYPE_ARG, EventFormAlarmFragment.DefaultNotificationsType.NORMAL_CALENDAR.value)
            }
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
    }

    private fun initOnClickListeners(isCreate: Boolean) {

        // Calendar color
        binding.calendarFormColorPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            ColorUtils.displayColorPicker(
                requireContext(),
                resources.getString(R.string.dialog_title_calendar_color_picker),
                resources.getStringArray(R.array.colors_names),
                resources.getStringArray(R.array.colors_values),
                calendarFormViewModel.calendarColor.value!!
            ) {
                calendarFormViewModel.handleCalendarColor(it)
            }
        }

        // Calendar email (can only be changed for create calendar)
        binding.calendarFormDefaultEmailPress.root.visibleOrGone(isCreate)
        binding.calendarFormDefaultEmailPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            val userEmails = calendarFormViewModel.userEmails ?: return@setOnSingleClickListener
            AndroidUtils.displayPickerDialog(
                requireContext(),
                null,
                userEmails.toTypedArray(),
                calendarFormViewModel.calendarEmail.value?.let { userEmails.indexOf(it) } ?: 0
            ) {
                calendarFormViewModel.handleCalendarEmail(userEmails[it])
            }
        }

        // Default event duration
        binding.calendarFormDefaultEventDurationPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            AndroidUtils.displayPickerDialog(
                requireContext(),
                null,
                CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.map {
                    resources.getQuantityString(R.plurals.calendar_form_default_event_duration_value, it, it.toString())
                }.toTypedArray(),
                calendarFormViewModel.defaultEventDuration.value?.let { CalendarForm.EVENT_DEFAULT_DURATION_MINUTES.indexOf(it) } ?: 0
            ) {
                calendarFormViewModel.handleDefaultEventDuration(CalendarForm.EVENT_DEFAULT_DURATION_MINUTES[it])
            }
        }
    }

    private fun observeCalendarFormSnackState(coroutineContext: CoroutineContext) {
        calendarFormViewModel.calendarFormSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { calendarFormSnackState ->
            calendarFormSnackState?.let {
                when (it) {
                    is CalendarFormViewModel.CalendarFormSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp -> {
                        requireActivity().displaySnackBar(it.message)

                        findNavController().navigateUp()
                    }
                }
                calendarFormViewModel.calendarFormSnackState.value = null
            }
        }
    }
}
