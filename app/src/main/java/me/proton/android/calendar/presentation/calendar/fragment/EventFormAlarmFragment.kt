package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FormValidation
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.doAfterFilteredIntValueChanged
import me.proton.android.calendar.common.utils.AndroidUtils.getCheckedRadioButtonIndex
import me.proton.android.calendar.common.utils.AndroidUtils.setCustomOnCheckedChangeListener
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.databinding.FragmentEventFormAlarmBinding
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.holidayCalendar.viewModel.HolidayCalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import org.koin.core.KoinComponent
import java.time.LocalTime

class EventFormAlarmFragment() : BaseDialogFragment<FragmentEventFormAlarmBinding>(), KoinComponent {

    private val navigationArguments: EventFormAlarmFragmentArgs by navArgs()

    override val TAG = "EventFormAlarmFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_alarm

    private lateinit var toolbarTitle: TextView

    private val eventViewModel: EventViewModel by activityViewModels()
    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val calendarFormViewModel: CalendarFormViewModel by activityViewModels()
    private val holidayCalendarViewModel: HolidayCalendarViewModel by activityViewModels()

    private var isAllDay: Boolean = false
    private var defaultNotificationsType: DefaultNotificationsType = DefaultNotificationsType.EVENT

    enum class DefaultNotificationsType(val value: Int) {
        EVENT(0),
        NORMAL_CALENDAR(1),
        HOLIDAY_CALENDAR(2)
    }

    private var lastSelectedRadioButtonId: Int = -1

    override fun onBackPressedCustom() {
        if (binding.eventFormAlarmRadioGroup.checkedRadioButtonId == R.id.event_form_alarm_custom
            && binding.eventFormAlarmCustomLayout.root.isVisible) {
            //Change view
            //TODO set to last selected before clicking custom radio button ?
            if (lastSelectedRadioButtonId != -1) binding.eventFormAlarmRadioGroup.check(lastSelectedRadioButtonId)
            else binding.eventFormAlarmRadioGroup.clearCheck()
            binding.eventFormAlarmRadioGroup.visibleOrGone(true)
            binding.eventFormAlarmCustomLayout.root.visibleOrGone(false)
            toolbarTitle.text = getString(R.string.event_alarms_title)
        } else {
            findNavController().navigateUp()
        }
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        val buttonDone = layoutInflater.inflate(R.layout.toolbar_action_text, dialogToolbarContent, false)
        with (buttonDone) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(R.string.action_done)
            setOnSingleClickListener {
                onDoneClick()
            }
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            addView(
                buttonDone, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun onDoneClick() {
        val alarmTypeOption = getCheckedRadioButtonIndex(binding.eventFormAlarmRadioGroup)

        val option =
            if (getCheckedRadioButtonIndex(binding.eventFormAlarmActionRadioGroup) == 0)
                EventViewModel.SendByOption.NOTIFICATION
            else EventViewModel.SendByOption.EMAIL
        eventViewModel.handleAlarmSendBy(option) // 0 -- notification (default), 1 -- email

        val countTypeOption = getCheckedRadioButtonIndex(binding.eventFormAlarmCustomLayout.customAlarmRadioGroup)
        // countTypeOption with value at 4 is used for "on the day" option
        eventViewModel.handleAlarm(
            alarmTypeOption = alarmTypeOption,
            count = binding.eventFormAlarmCustomLayout.customAlarmField.text.toString().toIntOrNull() ?:
            if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
            else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
            countTypeOption = if (countTypeOption == -1 && isAllDay) 4 else countTypeOption,
            isAllDay = isAllDay
        )?.let { alarm ->
            when (defaultNotificationsType) {
                DefaultNotificationsType.EVENT -> eventViewModel.saveAlarm(alarm)
                DefaultNotificationsType.NORMAL_CALENDAR -> calendarFormViewModel.handleAlarmChange(alarm, isAllDay)
                DefaultNotificationsType.HOLIDAY_CALENDAR -> holidayCalendarViewModel.handleAlarmChange(alarm)
            }
        }

        findNavController().navigateUp()

        // TODO
        // copy all values edited here to VM, before this they should be ephemeral, but we should keep in memory edited-not-saved
        // notifications when switching between custom and canned ones
    }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetAlarmCustomText(count: Int) {
        val selectedIndex = getCheckedRadioButtonIndex(binding.eventFormAlarmCustomLayout.customAlarmRadioGroup)
        val label = R.string.event_alarm_label
        val labelSelected = R.string.event_alarm_label_before

        binding.eventFormAlarmCustomLayout.customAlarm3.text = getString(if (selectedIndex == 2) labelSelected else label, resources.getQuantityString(R.plurals.plural_day, count, count))
        binding.eventFormAlarmCustomLayout.customAlarm4.text = getString(if (selectedIndex == 3) labelSelected else label, resources.getQuantityString(R.plurals.plural_week, count, count))
        if (!isAllDay) {
            binding.eventFormAlarmCustomLayout.customAlarm1.text = getString(if (selectedIndex == 0) labelSelected else label, resources.getQuantityString(R.plurals.plural_minute, count, count))
            binding.eventFormAlarmCustomLayout.customAlarm2.text = getString(if (selectedIndex == 1) labelSelected else label, resources.getQuantityString(R.plurals.plural_hour, count, count))
        }
    }

    private fun resetAlarmText(selectedIndex: Int) {
        lifecycleScope.launch {
            val is24Hour =
                when (defaultNotificationsType) {
                    DefaultNotificationsType.EVENT -> eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
                    DefaultNotificationsType.NORMAL_CALENDAR,
                    DefaultNotificationsType.HOLIDAY_CALENDAR -> calendarViewModel.timeFormatIs24Hour(requireContext())
                }

            if (isAllDay) { // TODO refactor and extract common formatting code to helpers -- pass timezone, locale and am/pm setting for later
                binding.eventFormAlarm1.text = getString(R.string.event_alarm_all_day_1, LocalTime.of(9, 0).formatTime(is24Hour))
                binding.eventFormAlarm2.text = getString(R.string.event_alarm_all_day_2, LocalTime.of(18, 0).formatTime(is24Hour))
                binding.eventFormAlarm3.text = getString(R.string.event_alarm_all_day_3, LocalTime.of(9, 0).formatTime(is24Hour))
                binding.eventFormAlarm4.text = getString(R.string.event_alarm_all_day_4, LocalTime.of(9, 0).formatTime(is24Hour))
            } else {
                binding.eventFormAlarm1.text = getString(R.string.event_alarm_partial_day_1)
                binding.eventFormAlarm2.text = getString(R.string.event_alarm_partial_day_2)
                binding.eventFormAlarm3.text = getString(R.string.event_alarm_partial_day_3)
                binding.eventFormAlarm4.text = getString(R.string.event_alarm_partial_day_4)
                binding.eventFormAlarm5.text = getString(R.string.event_alarm_partial_day_5)
                if (selectedIndex != -1 && selectedIndex != R.id.event_form_alarm_1) {
                    val radioButton = binding.eventFormAlarmRadioGroup.findViewById<RadioButton>(selectedIndex)
                    radioButton.text = getString(R.string.event_alarm_label_before, radioButton.text)
                }
            }
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentEventFormAlarmBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarTitle = toolbar.findViewById(R.id.dialog_toolbar_title)
        toolbarTitle.text = getString(R.string.event_alarms_title)
        toolbar.setNavigationIcon(R.drawable.ic_proton_cross)

        isAllDay = navigationArguments.isAllDay
        defaultNotificationsType = DefaultNotificationsType.values()[navigationArguments.defaultNotificationsType]

        binding.eventFormAlarmSendByLayout.visibleOrGone(true)

        binding.eventFormAlarm5.visibleOrGone(!isAllDay)
        resetAlarmText(-1)

        binding.eventFormAlarmRadioGroup.setCustomOnCheckedChangeListener { _, index ->
            when (index) {
                R.id.event_form_alarm_custom -> {
                    toolbarTitle.text = getString(R.string.event_custom_alarms_title)

                    //Change view
                    binding.eventFormAlarmRadioGroup.visibleOrGone(false)
                    binding.eventFormAlarmCustomLayout.root.visibleOrGone(true)
                }
                else -> {
                    resetAlarmText(index)
                    lastSelectedRadioButtonId = index
                }
            }
        }
        binding.eventFormAlarmActionRadioGroup.setCustomOnCheckedChangeListener { radioGroup, index ->
            requireActivity().clearFocusAndHideKeyboard(view)
        }

        binding.eventFormAlarmRadioGroup.check(binding.eventFormAlarm1.id) // TODO read from event alarm
        binding.eventFormAlarmActionRadioGroup.check(binding.eventFormAlarmActionNotification.id) // TODO read from event alarm

        var lastSelectedIndex: Int? = null

        binding.eventFormAlarmCustomLayout.customAlarmRadioGroup.setCustomOnCheckedChangeListener { radioGroup, index ->

            // prevent infinite loop when resetting adapters by EditText changes and Spinner selection
            if (lastSelectedIndex != null && lastSelectedIndex == index) {
                return@setCustomOnCheckedChangeListener
            }
            lastSelectedIndex = index

            if (index != -1) binding.eventFormAlarmCustomLayout.customAlarmSameDay.isChecked = false

            requireActivity().clearFocusAndHideKeyboard(view)

            when (getCheckedRadioButtonIndex(binding.eventFormAlarmCustomLayout.customAlarmRadioGroup)) {
                0 -> { // minute
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_MINUTES
                    )
                }
                1 -> { // hour
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_HOURS
                    )
                }
                2 -> { // day
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_DAYS
                    )
                }
                3 -> { // week
                    resetAlarmCountValidation(
                        if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT,
                        FormValidation.ALARM_PERIOD_COUNT_MIN,
                        FormValidation.ALARM_PERIOD_MAX_WEEKS
                    )
                }
                // Hide before label if no button is checked
                else ->
                    resetAlarmCustomText(
                        if (binding.eventFormAlarmCustomLayout.customAlarmField.text.isNullOrEmpty()) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT
                        else binding.eventFormAlarmCustomLayout.customAlarmField.text.toString().toInt()
                    )
            }
        }

        binding.eventFormAlarmCustomLayout.customAlarmSameDay.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) binding.eventFormAlarmCustomLayout.customAlarmRadioGroup.clearCheck()
            else button.jumpDrawablesToCurrentState()
        }

        lifecycleScope.launch {
            val is24Hour =
                when (defaultNotificationsType) {
                    DefaultNotificationsType.EVENT -> eventViewModel.userSettings.timeFormatIs24Hour(DateFormat.is24HourFormat(requireContext()))
                    DefaultNotificationsType.NORMAL_CALENDAR,
                    DefaultNotificationsType.HOLIDAY_CALENDAR -> calendarViewModel.timeFormatIs24Hour(requireContext())
                }

            // init
            binding.eventFormAlarmCustomLayout.customAlarm1.visibleOrGone(!isAllDay)
            binding.eventFormAlarmCustomLayout.customAlarm2.visibleOrGone(!isAllDay)
            binding.eventFormAlarmCustomLayout.customAlarmTimeLayout.visibleOrGone(isAllDay)
            binding.eventFormAlarmCustomLayout.customAlarmSameDayLayout.visibleOrGone(isAllDay)
            if (isAllDay) {
                binding.eventFormAlarmCustomLayout.customAlarmField.setText("1")
                binding.eventFormAlarmCustomLayout.customAlarmRadioGroup.check(binding.eventFormAlarmCustomLayout.customAlarm3.id)
                resetAlarmCustomText(1)
                binding.eventFormAlarmCustomLayout.customAlarmTime.text =
                    getString(R.string.event_alarm_at_time, eventViewModel.tempAlarmTime.formatTime(is24Hour))
            } else {
                binding.eventFormAlarmCustomLayout.customAlarmField.setText("15")
                binding.eventFormAlarmCustomLayout.customAlarmRadioGroup.check(binding.eventFormAlarmCustomLayout.customAlarm1.id)
                resetAlarmCustomText(15)
            }
            binding.eventFormAlarmCustomLayout.customAlarmFieldLayout.setEndIconOnClickListener {
                binding.eventFormAlarmCustomLayout.customAlarmField.setText(
                    if (isAllDay) FormValidation.ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT.toString()
                    else FormValidation.ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT.toString()
                )
            }

            binding.eventFormAlarmCustomLayout.customAlarmTimePress.root.setOnSingleClickListener {
                requireActivity().clearFocusAndHideKeyboard(view)

                AndroidUtils.displayTimePicker(requireContext(), LocalTime.now(), is24Hour) {
                    eventViewModel.handleAlarmTime(it) // TODO Handle defaultNotificationsType
                    binding.eventFormAlarmCustomLayout.customAlarmTime.text = getString(R.string.event_alarm_at_time, it.formatTime(is24Hour))
                }
            }
        }
    }

    // we keep track of TextWatcher so we can remove it when resetting alarm validation
    var alarmCustomFieldTextWatcher: TextWatcher? = null

    private fun resetAlarmCountValidation(default: Int, min: Int, max: Int) {
        // remove current alarm count text watcher
        alarmCustomFieldTextWatcher?.let { binding.eventFormAlarmCustomLayout.customAlarmField.removeTextChangedListener(it) }

        // set new text watcher with new config
        alarmCustomFieldTextWatcher =
            binding.eventFormAlarmCustomLayout.customAlarmField.doAfterFilteredIntValueChanged(default, min, max) {
                resetAlarmCustomText(it)
            }

        // set current value again because it might be outside of newly set limits
        binding.eventFormAlarmCustomLayout.customAlarmField.setText(binding.eventFormAlarmCustomLayout.customAlarmField.text)
    }
}
