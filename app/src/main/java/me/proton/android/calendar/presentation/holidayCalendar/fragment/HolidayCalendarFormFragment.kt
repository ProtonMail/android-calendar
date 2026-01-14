package me.proton.android.calendar.presentation.holidayCalendar.fragment

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
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.ColorUtils
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.databinding.FragmentHolidayCalendarFormBinding
import me.proton.android.calendar.databinding.ItemAlarmTextButtonBinding
import me.proton.android.calendar.presentation.calendar.fragment.EventFormAlarmFragment
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.holidayCalendar.viewModel.HolidayCalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.presentation.utils.currentLocale
import org.koin.core.KoinComponent
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class HolidayCalendarFormFragment : BaseDialogFragment<FragmentHolidayCalendarFormBinding>(), KoinComponent {

    override val TAG: String
        get() = "HolidayCalendarFormFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_holiday_calendar_form

    override val navigateUp = false
    override val isScrollable = false

    private val navigationArguments: HolidayCalendarFormFragmentArgs by navArgs()

    private val mainViewModel: MainViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val holidayCalendarViewModel: HolidayCalendarViewModel by activityViewModels()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    private var calendarId: String? = null

    override fun onBackPressedCustom() {
        requireActivity().clearFocusAndHideKeyboard(view)

        // Display snack and return if we're saving the calendar changes
        val processingCalendar = holidayCalendarViewModel.holidayCalendarState.value is HolidayCalendarViewModel.HolidayCalendarState.Processing
        if (processingCalendar) {
            view?.displaySnackBar(getString(R.string.snack_calendar_saving))
            return
        }

        // Check if we need to display discard changes dialog
        if (holidayCalendarViewModel.hasBeenEdited()) {
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
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = getString(R.string.holiday_calendar_title)

        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialogToolbarContent, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(
                R.string.action_save
            )
            setOnSingleClickListener {

                requireActivity().clearFocusAndHideKeyboard(view)

                if (!mainViewModel.isConnectedToNetwork) {
                    view?.displaySnackBar(getString(R.string.snack_network_error))
                    return@setOnSingleClickListener
                }

                lifecycleScope.launch {
                    if (holidayCalendarViewModel.hasBeenEdited() || calendarId.isNullOrEmpty()) {
                        val selectedDate = calendarViewModel.selectedDateTime.value
                        val returnToSettings = findNavController().previousBackStackEntry?.destination?.id == R.id.nav_settings || calendarId != null
                        holidayCalendarViewModel.handleSaveHolidayCalendar(returnToSettings, calendarId.isNullOrEmpty(), selectedDate?.first)
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

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentHolidayCalendarFormBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        holidayCalendarViewModel.resetValues()

        calendarId = navigationArguments.calendarId

        lifecycleScope.launch {
            calendarId?.let {
                // Hide disclaimer based on time zone
                binding.holidayCalendarFormCountrySearchDisclaimer.visibleOrGone(false)
                // Init form for existing calendar
                holidayCalendarViewModel.initUpdateHolidayCalendar(it)
            } ?: run {
                // Use random color from array as calendar color
                // Init form for new calendar
                val returnToSettings = findNavController().previousBackStackEntry?.destination?.id == R.id.nav_settings
                val languageTag = requireContext().resources.configuration.currentLocale().toLanguageTag().lowercase()
                val countryCode = languageTag.substringAfter("-", "")
                holidayCalendarViewModel.initCreateHolidayCalendar(
                    resources.getStringArray(R.array.colors_values).random(),
                    requireContext().resources.configuration.currentLocale().language.lowercase(),
                    countryCode,
                    returnToSettings
                )
            }
        }

        initOnClickListeners()

        observeHolidayCalendarFormValues()

        observeHolidayCalendarSnackState(lifecycleScope.coroutineContext)
    }

    private fun observeHolidayCalendarFormValues() {

        holidayCalendarViewModel.country.observe(viewLifecycleOwner) { country ->
            // Hide the language field if country field is empty
            binding.holidayCalendarFormLanguageLayout.visibleOrGone(country.isNotEmpty())
            // Display placeholder if country field is empty
            binding.holidayCalendarFormCountryValue.visibleOrInvisible(country.isNotEmpty())
            binding.holidayCalendarFormCountryValuePlaceholder.visibleOrGone(country.isEmpty())
            binding.holidayCalendarFormCountryValue.text = country
            // Set the country flag
            val countryCode = holidayCalendarViewModel.holidayCalendars.value?.firstOrNull { it.country == country }?.countryCode
            binding.holidayCalendarFormCountryFlag.setImageResource(
                resources.getIdentifier(
                    "${requireContext().packageName}:drawable/flag_$countryCode",
                    "drawable",
                    requireContext().packageName
                )
            )
            // Disable the language field if there is only one option available
            binding.holidayCalendarFormLanguagePress.root.isEnabled = holidayCalendarViewModel.getLanguages().size > 1
        }

        holidayCalendarViewModel.language.observe(viewLifecycleOwner) { language ->
            if (language.isNullOrEmpty()) return@observe
            binding.holidayCalendarFormLanguageValue.text = language
        }

        holidayCalendarViewModel.calendarColor.observe(viewLifecycleOwner) { calendarColor ->
            if (calendarColor.isEmpty()) {
                // Value was reset. Set to background_norm to avoid seeing the color being refreshed
                binding.holidayCalendarFormColorIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.background_norm))
                return@observe
            }

            binding.holidayCalendarFormColorIcon.imageTintList = ColorStateList.valueOf(calendarColor.toColorInt())

            binding.holidayCalendarFormColor.text = ColorUtils.getColorNameForHex(
                resources.getStringArray(R.array.colors_names),
                resources.getStringArray(R.array.colors_values),
                calendarColor
            ) ?: getString(R.string.undefined_color)
        }

        holidayCalendarViewModel.defaultAllDayAlarms.observe(viewLifecycleOwner) { defaultAllDayAlarms ->
            binding.holidayCalendarFormDefaultAllDayEventNotifications.visibleOrGone(defaultAllDayAlarms.size < CalendarForm.DEFAULT_NOTIFICATIONS_COUNT_MAX)
            displayNotifications(
                defaultAllDayAlarms,
                binding.holidayCalendarFormDefaultAllDayEventNotificationsList,
                binding.holidayCalendarFormDefaultAllDayEventNotificationsIcon,
                binding.holidayCalendarFormDefaultAllDayEventNotificationsPress.root
            )
        }

        holidayCalendarViewModel.holidayCalendarState.asLiveData(lifecycleScope.coroutineContext).observe(viewLifecycleOwner) { holidayCalendarState ->
            val processingEvent = holidayCalendarState is HolidayCalendarViewModel.HolidayCalendarState.Processing
            val alreadyExists = holidayCalendarState is HolidayCalendarViewModel.HolidayCalendarState.AlreadyExists
            val pickBasedOnTimeZone = holidayCalendarState is HolidayCalendarViewModel.HolidayCalendarState.PickBasedOnTimeZone

            // Display disclaimer based on time zone
            binding.holidayCalendarFormCountrySearchDisclaimer.visibleOrGone(pickBasedOnTimeZone)

            // Update action bar buttons visibility
            loadingAction.visibleOrGone(processingEvent)
            buttonSave.visibleOrGone(!processingEvent)

            // Disable/Enable all items linked to actions from our view
            binding.holidayCalendarFormCountryValuePress.isEnabled = !processingEvent
            binding.holidayCalendarFormColorPress.root.isEnabled = !processingEvent

            binding.holidayCalendarFormDefaultAllDayEventNotificationsPress.root.isEnabled = !processingEvent
            for (i in 0 until binding.holidayCalendarFormDefaultAllDayEventNotificationsList.childCount) {
                // Disable the delete buttons from inside alarm items views
                binding.holidayCalendarFormDefaultAllDayEventNotificationsList.getChildAt(i)?.findViewById<View>(
                    R.id.item_simple_text_button_delete
                )?.isEnabled = !processingEvent
            }

            // Disable save when calendar already exists
            buttonSave.isEnabled = !alreadyExists
            val toolbarActionText = buttonSave.findViewById<TextView>(R.id.toolbar_action_text)
            toolbarActionText.setTextColor(
                requireContext().getColorFromAttr(
                    if (alreadyExists) R.attr.proton_interaction_norm_disabled
                    else R.attr.proton_text_accent
                )
            )

            // Hide time zone disclaimer if we show error
            if (alreadyExists) binding.holidayCalendarFormCountrySearchDisclaimer.visibleOrGone(false)
            // Display error subtext when calendar already exists
            binding.holidayCalendarFormCountrySearchError.visibleOrGone(alreadyExists)
            binding.holidayCalendarFormCountrySearchError.text =
                if (alreadyExists) getString(R.string.holiday_calendar_already_exists)
                else ""
        }
    }

    /**
     * Display notifications list and register listeners for add / remove notifications.
     */
    private fun displayNotifications(
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
                        true,
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
                        holidayCalendarViewModel.handleAlarmChange(alarm, isDelete = true)
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
                putBoolean(FragmentArguments.IS_ALL_DAY_ARG, true)
                putInt(FragmentArguments.DEFAULT_NOTIFICATIONS_TYPE_ARG, EventFormAlarmFragment.DefaultNotificationsType.HOLIDAY_CALENDAR.value)
            }
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
    }

    private fun initOnClickListeners() {

        // Country
        binding.holidayCalendarFormCountryValuePress.setOnSingleClickListener {
            if (!holidayCalendarViewModel.holidayCalendars.value.isNullOrEmpty()) {
                findNavController().navigate(R.id.action_nav_holiday_calendar_form_to_nav_holiday_calendar_search)
            }
        }

        // Calendar language
        binding.holidayCalendarFormLanguagePress.root.setOnSingleClickListener {
            val languages = holidayCalendarViewModel.getLanguages().sorted()
            if (languages.size <= 1) return@setOnSingleClickListener
            AndroidUtils.displayPickerDialog(
                requireContext(),
                null,
                languages.toTypedArray(),
                holidayCalendarViewModel.language.value?.let { language ->
                    languages.indexOf(language)
                } ?: 0
            ) {
                lifecycleScope.launch {
                    holidayCalendarViewModel.handleLanguage(languages[it])
                }
            }
        }

        // Calendar color
        binding.holidayCalendarFormColorPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)

            ColorUtils.displayColorPicker(
                requireContext(),
                resources.getString(R.string.dialog_title_calendar_color_picker),
                resources.getStringArray(R.array.colors_names),
                resources.getStringArray(R.array.colors_values),
                holidayCalendarViewModel.calendarColor.value!!
            ) {
                holidayCalendarViewModel.handleCalendarColor(it)
            }
        }
    }

    private fun observeHolidayCalendarSnackState(coroutineContext: CoroutineContext) {
        holidayCalendarViewModel.holidayCalendarSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { holidayCalendarSnackState ->
            holidayCalendarSnackState?.let {
                when (it) {
                    is HolidayCalendarViewModel.HolidayCalendarSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is HolidayCalendarViewModel.HolidayCalendarSnackState.DisplaySnackNavigateUp -> {
                        if (it.message.isNotEmpty()) requireActivity().displaySnackBar(it.message)

                        findNavController().navigateUp()
                    }
                }
                holidayCalendarViewModel.holidayCalendarSnackState.value = null
            }
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
