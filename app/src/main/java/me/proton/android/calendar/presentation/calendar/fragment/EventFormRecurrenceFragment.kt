package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import biweekly.util.Frequency
import com.google.android.material.chip.Chip
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FormValidation
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.doAfterFilteredIntValueChanged
import me.proton.android.calendar.common.utils.AndroidUtils.formatMonthlyDayOfWeek
import me.proton.android.calendar.common.utils.AndroidUtils.getCheckedRadioButtonIndex
import me.proton.android.calendar.common.utils.AndroidUtils.setCustomOnCheckedChangeListener
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.showKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatWithDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLocaleForFormatting
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.databinding.FragmentEventFormRecurrenceBinding
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.calendar.customView.NoLayoutRadioGroup
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

class EventFormRecurrenceFragment : BaseDialogFragment<FragmentEventFormRecurrenceBinding>(), KoinComponent {

    override val TAG = "EventFormRecurrenceFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_recurrence

    override val navigateUp = false

    private lateinit var toolbarTitle: TextView

    private val navigationArguments: EventFormFragmentArgs by navArgs()

    private val logger: Logger by inject()
    private val eventViewModel: EventViewModel by activityViewModels() //inject()
    private lateinit var monthlyRecurrenceOnMap: HashMap<Int, EventViewModel.MonthlyRepeatOnOption>

    // index of the day of the week of Event start, used for forcing weekday picker to have it always picked
    //private val indexOfEventStartDay by lazy { (eventViewModel.eventLiveData.value!!.getStart(eventViewModel.initialTimeZoneId)!!.dayOfWeek.ordinal + if (eventViewModel.startWeekOnMonday) 0 else 1) % 7 }

    lateinit var customEndingRadioGroup: NoLayoutRadioGroup
    private var lastSelectedRadioButtonId: Int = -1

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentEventFormRecurrenceBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbarTitle = toolbar.findViewById(R.id.dialog_toolbar_title)
        toolbarTitle.text = getString(R.string.event_recurrence_title)

        attachActionHandlers()
        observeEventLiveData() // TODO maybe this could be removed if this form is not reactive

    }

    override fun onBackPressedCustom() {
        if (binding.eventFormRecurrenceRadioGroup.checkedRadioButtonId == R.id.event_form_recurrence_custom
            && binding.eventFormRecurrenceCustomLayout.root.isVisible) {
            //TODO set to last selected before clicking custom radio button ?
            if (lastSelectedRadioButtonId != -1) binding.eventFormRecurrenceRadioGroup.check(lastSelectedRadioButtonId)
            else binding.eventFormRecurrenceRadioGroup.clearCheck()
            binding.eventFormRecurrenceRadioGroup.visibleOrGone(true)
            binding.eventFormRecurrenceCustomLayout.root.visibleOrGone(false)
            toolbarTitle.text = getString(R.string.event_recurrence_title)
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
        when (binding.eventFormRecurrenceRadioGroup.checkedRadioButtonId) {
            R.id.event_form_recurrence_custom_edit -> {
                //Do nothing, we don't need to update recurrence and keep the custom one
            }
            R.id.event_form_recurrence_1 -> {
                eventViewModel.handleRecurrence(null, untilDate = false)
            }
            R.id.event_form_recurrence_2 -> {
                eventViewModel.handleRecurrence(Frequency.DAILY, untilDate = false)
            }
            R.id.event_form_recurrence_3 -> {
                eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDate = false)
            }
            R.id.event_form_recurrence_4 -> {
                eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDate = false)
            }
            R.id.event_form_recurrence_5 -> {
                eventViewModel.handleRecurrence(Frequency.YEARLY, untilDate = false)
            }
            R.id.event_form_recurrence_custom -> {

                var untilDateChecked = false
                var thisManyRepeats: Int? = null

                // "repeat until" options, apply to all recurrence periods
                when (customEndingRadioGroup.getCheckedRadioButtonId()) {
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd1.id -> {} // ends: never
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd2.id -> { // ends: on specific Date
                        untilDateChecked = true
                    }
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd3.id -> { // ends: after X occurrences
                        thisManyRepeats = binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.text.toString().toIntOrNull() ?: FormValidation.OCCURRENCE_COUNT_DEFAULT // TODO force when null?
                    }
                }

                // "repeat X times"
                when (getCheckedRadioButtonIndex(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup)) {
                    0 -> { // days
                        val interval = binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                        eventViewModel.handleRecurrence(Frequency.DAILY, untilDateChecked, interval, thisManyRepeats)
                    }
                    1 -> { // weeks
                        val interval = binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_WEEK_COUNT_DEFAULT

                        // weekdays for occurrence
                        val daysOfWeek = (binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.chipGroupDayOfWeekLayout as ViewGroup).children.mapIndexedNotNull { index, chip ->
                            if ((chip as Chip).isChecked) {
                                val weekStart = if (eventViewModel.userSettings.weekStart == 0) WeekFields.of(
                                    getLocaleForFormatting()
                                ).firstDayOfWeek.value else eventViewModel.userSettings.weekStart
                                biweekly.util.DayOfWeek.values()[(index + weekStart) % 7]
                            } else null
                        }.toList()

                        eventViewModel.handleRecurrence(Frequency.WEEKLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = daysOfWeek, customMonthly = false)
                    }
                    2 -> { // months
                        val interval = binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT

                        eventViewModel.handleRecurrence(Frequency.MONTHLY, untilDateChecked, interval, thisManyRepeats, daysOfWeek = null, customMonthly = true)
                    }
                    3 -> { // years
                        val interval = binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.text.toString().toIntOrNull()
                            ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT

                        eventViewModel.handleRecurrence(Frequency.YEARLY, untilDateChecked, interval, thisManyRepeats)
                    }
                }

            }
        }

        findNavController().navigateUp()
    }

    private fun attachActionHandlers() {

        // main recurrence type radio group
        binding.eventFormRecurrenceRadioGroup.setCustomOnCheckedChangeListener { radioGroup, index ->
            if (index == R.id.event_form_recurrence_custom) {
                binding.eventFormRecurrenceRadioGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.root.visibleOrGone(true)
                showCustomRecurrenceForms(getCheckedRadioButtonIndex(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup))
                toolbarTitle.text = getString(R.string.event_recurrence_custom_title)
            } else {
                lastSelectedRadioButtonId = index
            }
        }

        binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd1.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requireActivity().clearFocusAndHideKeyboard(view)
        }
        binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd2.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) requireActivity().clearFocusAndHideKeyboard(view)
        }

        // custom recurrence spinner ("repeats every X days/weeks/months...")
        setupCustomRecurrenceSpinnerComponent()

        // custom ending radio group ("ends on date/after X occurrences...")
        setupCustomEndingRadioGroup()

        updateChipHeight()
    }

    private fun setupCustomEndingRadioGroup() {
        customEndingRadioGroup = NoLayoutRadioGroup {
            // handle checks and focus
            if (it == R.id.custom_recurrence_end_3) {
                if (!binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.hasFocus()) binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.requestFocus()
            } else {
                binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.clearFocus()
            }

            val eventStartDate = eventViewModel.eventLiveData.value!!.getStart(eventViewModel.eventTimeZoneId)
                .toLocalDate()

            val currentRecurrenceUntilInstant = eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.until?.toZonedDateTime(eventViewModel.eventTimeZoneId)

            val untilDate = eventViewModel.tempRecurrenceUntilLocalDate
                ?: if (currentRecurrenceUntilInstant != null) {
                    currentRecurrenceUntilInstant.toLocalDate()
                } else eventStartDate

            // handle click on day picker
            if (it == R.id.custom_recurrence_end_2) {
                AndroidUtils.displayDatePicker(
                    requireContext(),
                    eventViewModel.userSettings.weekStartDayOfWeek(),
                    untilDate,
                    eventStartDate,
                    FormValidation.MAX_SUPPORTED_DATETIME.withZoneSameInstant(ZoneId.of(eventViewModel.eventTimeZoneId)).toLocalDate()
                ) { newDate ->
                    eventViewModel.handleRecurrenceUntilDate(newDate)
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd2.text = getString(
                        R.string.event_recurrence_ends_on_date,
                        newDate.formatWithDayOfWeek()
                    )
                }
            }
        }

        customEndingRadioGroup.add(
            requireView().findViewById(R.id.custom_recurrence_end_1),
            requireView().findViewById(R.id.custom_recurrence_end_2),
            requireView().findViewById(R.id.custom_recurrence_end_3)
        )

        customEndingRadioGroup.check(R.id.custom_recurrence_end_1)

        // "after X occurrences" radio button
        binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCountSuffix.setText(
            resources.getQuantityString(
                R.plurals.plural_time,
                FormValidation.OCCURRENCE_COUNT_DEFAULT
            )
        )

        // "after X occurrences" edit text
        binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.setText(FormValidation.OCCURRENCE_COUNT_DEFAULT.toString())
        binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.apply {
            doAfterFilteredIntValueChanged(
                FormValidation.OCCURRENCE_COUNT_DEFAULT,
                FormValidation.OCCURRENCE_COUNT_MIN,
                FormValidation.OCCURRENCE_COUNT_MAX
            ) {
                binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCountSuffix.setText(
                    resources.getQuantityString(
                        R.plurals.plural_time,
                        it
                    )
                )
            }
        }

        binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCountSuffix.setOnSingleClickListener {
            customEndingRadioGroup.check(R.id.custom_recurrence_end_3)
            binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.requestFocus()
        }

        binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                customEndingRadioGroup.check(R.id.custom_recurrence_end_3)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.setSelection(binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.length())
                requireContext().showKeyboard()
            }
        }

        binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd1.setOnCheckedChangeListener { button, isChecked ->
            if (!isChecked) button.jumpDrawablesToCurrentState()
            else requireActivity().clearFocusAndHideKeyboard(view)
        }
        binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd2.setOnCheckedChangeListener { button, isChecked ->
            if (!isChecked) button.jumpDrawablesToCurrentState()
            else requireActivity().clearFocusAndHideKeyboard(view)
        }
        binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd3.setOnCheckedChangeListener { button, isChecked ->
            if (!isChecked) button.jumpDrawablesToCurrentState()
            else requireActivity().clearFocusAndHideKeyboard(view)
        }
    }

    private fun setupCustomRecurrenceSpinnerComponent() {
        var lastSelectedIndex: Int? = null

        binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup.setCustomOnCheckedChangeListener { radioGroup, index ->
            requireActivity().clearFocusAndHideKeyboard(view)

            val position = getCheckedRadioButtonIndex(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup)
            showCustomRecurrenceForms(position)

            // prevent infinite loop when resetting adapters by EditText changes and Radio group selection
            if (lastSelectedIndex != null && lastSelectedIndex == position) {
                return@setCustomOnCheckedChangeListener
            }
            lastSelectedIndex = position

            when (position) {
                0 -> { // day
                    resetRecurrenceCountValidation(
                        FormValidation.INTERVAL_DAY_COUNT_DEFAULT,
                        FormValidation.INTERVAL_DAY_COUNT_MIN,
                        FormValidation.INTERVAL_DAY_COUNT_MAX
                    )
                }
                1 -> { // week
                    resetRecurrenceCountValidation(
                        FormValidation.INTERVAL_WEEK_COUNT_DEFAULT,
                        FormValidation.INTERVAL_WEEK_COUNT_MIN,
                        FormValidation.INTERVAL_WEEK_COUNT_MAX
                    )
                }
                2 -> { // month
                    resetRecurrenceCountValidation(
                        FormValidation.INTERVAL_MONTH_COUNT_DEFAULT,
                        FormValidation.INTERVAL_MONTH_COUNT_MIN,
                        FormValidation.INTERVAL_MONTH_COUNT_MAX
                    )
                }
                3 -> { // year
                    resetRecurrenceCountValidation(
                        FormValidation.INTERVAL_YEAR_COUNT_DEFAULT,
                        FormValidation.INTERVAL_YEAR_COUNT_MIN,
                        FormValidation.INTERVAL_YEAR_COUNT_MAX
                    )
                }
            }

        }

        // init with WEEK
        binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
        resetRecurrencePeriodText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
        binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup.check(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriod2.id)

        binding.eventFormRecurrenceCustomLayout.customRecurrenceCountLayout.setEndIconOnClickListener {
            when (getCheckedRadioButtonIndex(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup)) {
                // day
                0 -> binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText(FormValidation.INTERVAL_DAY_COUNT_DEFAULT.toString())
                // week
                1 -> binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT.toString())
                // month
                2 -> binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT.toString())
                // year
                3 -> binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT.toString())
            }
        }

        // TODO Check if can be improved

        // DayOfWeek of biweekly starts on Sunday (ordinal 0) and ends on Saturday (ordinal 6), we convert it to match java.time ordinals
        val byDayIndices =
            eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.byDay?.map { it.day.toDayOfWeek().ordinal } ?: emptyList()

        // DayOfWeek of java.time starts on Monday (ordinal 0) and ends on Sunday (ordinal 6)
        val indexOfEventStartDay =
            eventViewModel.eventLiveData.value!!.getStart(eventViewModel.eventTimeZoneId).dayOfWeek.ordinal
        val checkedDayIndices: List<Int> = byDayIndices + indexOfEventStartDay

        val weekDayLetters = (DayOfWeek.MONDAY.value .. DayOfWeek.SUNDAY.value).map { DayOfWeek.of(it).format(firstLetter = true) }

        // TODO cleanup below after removing hardcoded string-array with date names
        // We do minus 1 to match java.time DayOfWeek ordinals
        val weekStart = if (eventViewModel.userSettings.weekStart == 0) WeekFields.of(getLocaleForFormatting()).firstDayOfWeek.value - 1 else eventViewModel.userSettings.weekStart - 1
        val weekEnd = 7
        var stringArrayIndex = weekStart
        // Iterate from weekStart first
        weekDayLetters
            .slice(weekStart until weekEnd) // until excludes weekEnd value
            .forEachIndexed { index, dayName ->
                setChipItemContent(index, stringArrayIndex, indexOfEventStartDay, checkedDayIndices, dayName)
                stringArrayIndex++
            }
        if (weekStart != 0) {
            // If weekStart was not Monday, iterate from 0 to fill the rest of the chips
            stringArrayIndex = 0
            weekDayLetters
                .slice(0 until weekStart) // until excludes weekStart value
                .forEachIndexed { index, dayName ->
                    val customIndex = (weekEnd - weekStart) + index
                    setChipItemContent(customIndex, stringArrayIndex, indexOfEventStartDay, checkedDayIndices, dayName)
                    stringArrayIndex++
                }
        }

        // TODO get this from VM
        val eventStartDate =
            eventViewModel.eventLiveData.value!!.getStart(eventViewModel.eventTimeZoneId)
                .toLocalDate()

        // applies only to month
        binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.check(
            when (eventViewModel.tempMonthlyRepeatOption) {
                EventViewModel.MonthlyRepeatOnOption.ON_DAY_X -> binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime1.id
                EventViewModel.MonthlyRepeatOnOption.ON_X_WEEKDAY -> binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime2.id
                EventViewModel.MonthlyRepeatOnOption.ON_LAST_WEEKDAY -> binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime3.id
            }
        )

        val optionsRepeatOn = eventViewModel.calculateMonthlyRepeatOnOptions()
        monthlyRecurrenceOnMap = HashMap()
        optionsRepeatOn.forEach {
            when (it) {
                EventViewModel.MonthlyRepeatOnOption.ON_DAY_X -> {
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime1.visibleOrGone(true)
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime1.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                    monthlyRecurrenceOnMap[R.id.custom_recurrence_occurrence_time_1] = it
                }
                EventViewModel.MonthlyRepeatOnOption.ON_X_WEEKDAY -> {
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime2.visibleOrGone(true)
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime2.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                    monthlyRecurrenceOnMap[R.id.custom_recurrence_occurrence_time_2] = it
                }
                EventViewModel.MonthlyRepeatOnOption.ON_LAST_WEEKDAY -> {
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime3.visibleOrGone(true)
                    binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime3.text = mapMonthlyRecurrenceOnToString(eventStartDate, it)
                    monthlyRecurrenceOnMap[R.id.custom_recurrence_occurrence_time_3] = it
                }
            }
        }

        binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.setCustomOnCheckedChangeListener { _, checkedId ->
            requireActivity().clearFocusAndHideKeyboard(view)
            val monthlyRepeatOnOption = monthlyRecurrenceOnMap[checkedId]
            if (monthlyRepeatOnOption != null) eventViewModel.handleRecurrenceRepeatOn(monthlyRepeatOnOption)
        }
    }

    private fun setChipItemContent(index: Int, stringArrayIndex: Int, indexOfEventStartDay: Int, checkedDayIndices: List<Int>, dayName: String) {
        ((binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.chipGroupDayOfWeekLayout as ViewGroup).getChildAt(index) as? Chip)?.apply {
            text = dayName
            isClickable =
                (stringArrayIndex != indexOfEventStartDay) // we disable and check by default the day of event's start
            isChecked = (stringArrayIndex in checkedDayIndices)
        }
    }

    private fun mapMonthlyRecurrenceOnToString(eventStartDate: LocalDate, option: EventViewModel.MonthlyRepeatOnOption): String {
        return when(option) {
            EventViewModel.MonthlyRepeatOnOption.ON_DAY_X -> getString(
                R.string.event_recurrence_occurs_monthly_on_day,
                eventStartDate.dayOfMonth
            )
            EventViewModel.MonthlyRepeatOnOption.ON_X_WEEKDAY -> getString(
                R.string.event_recurrence_occurs_on_monthly_on_x_day_of_week,
                eventStartDate.formatMonthlyDayOfWeek(resources)
            )
            EventViewModel.MonthlyRepeatOnOption.ON_LAST_WEEKDAY -> getString(
                R.string.event_recurrence_occurs_on_monthly_on_x_day_of_week,
                eventStartDate.formatMonthlyDayOfWeek(resources, backwards = true)
            )
        }
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { nullableEvent ->

            val event = nullableEvent ?: return@Observer

            val radioButtonId = when (event.iCalEvent.recurrenceRule?.value?.frequency) {
                Frequency.DAILY -> R.id.event_form_recurrence_2
                Frequency.WEEKLY -> R.id.event_form_recurrence_3
                Frequency.MONTHLY -> R.id.event_form_recurrence_4
                Frequency.YEARLY -> R.id.event_form_recurrence_5
                else -> R.id.event_form_recurrence_1
            }

            if (event.isCustomRecurring()) {
                //Set custom edit radio button text
                binding.eventFormRecurrenceCustomEdit.visibleOrGone(true)
                binding.eventFormRecurrenceCustomEdit.text = AndroidUtils.formatRecurrence(requireContext().resources, event, eventViewModel.eventTimeZoneId) ?: resources.getString(R.string.event_recurrence_none)

                // handle custom recurrence rule
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.check(
                    when (eventViewModel.calculateMonthlyRepeatOnOptions()[eventViewModel.calculateMonthlyRepeatOnOptionIndex()]) {
                        EventViewModel.MonthlyRepeatOnOption.ON_DAY_X -> binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime1.id
                        EventViewModel.MonthlyRepeatOnOption.ON_X_WEEKDAY -> binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime2.id
                        EventViewModel.MonthlyRepeatOnOption.ON_LAST_WEEKDAY -> binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTime3.id
                    }
                )

                customEndingRadioGroup.check(R.id.custom_recurrence_end_1) // default

                eventViewModel.eventLiveData.value!!.iCalEvent.recurrenceRule?.value?.run {
                    // "ends" section
                    if (this.until != null) {
                        customEndingRadioGroup.check(R.id.custom_recurrence_end_2)
                        binding.eventFormRecurrenceCustomLayout.customRecurrenceEnd2.text = getString(
                            R.string.event_recurrence_ends_on_date,
                            this.until.toZonedDateTime(eventViewModel.eventTimeZoneId)
                                .formatDate(eventViewModel.eventTimeZoneId)
                        )
                    }
                    if (this.count != null) {
                        customEndingRadioGroup.check(R.id.custom_recurrence_end_3)
                        binding.eventFormRecurrenceCustomLayout.customRecurrenceEndCount.setText("${this.count}")
                    }

                    // "repeats every" section
                    when (this.frequency) {
                        Frequency.WEEKLY -> {
                            binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText("${this.interval ?: FormValidation.INTERVAL_WEEK_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_WEEK_COUNT_DEFAULT)
                            binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup.check(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriod2.id)
                        }
                        Frequency.MONTHLY -> {
                            binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText("${this.interval ?: FormValidation.INTERVAL_MONTH_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_MONTH_COUNT_DEFAULT)
                            binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup.check(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriod3.id)
                        }
                        Frequency.YEARLY -> {
                            binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText("${this.interval ?: FormValidation.INTERVAL_YEAR_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_YEAR_COUNT_DEFAULT)
                            binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup.check(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriod4.id)
                        }
                        else -> { // fallback to Frequency.DAILY
                            binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText("${this.interval ?: FormValidation.INTERVAL_DAY_COUNT_DEFAULT}")
                            resetRecurrencePeriodText(FormValidation.INTERVAL_DAY_COUNT_DEFAULT)
                            binding.eventFormRecurrenceCustomLayout.customRecurrencePeriodRadioGroup.check(binding.eventFormRecurrenceCustomLayout.customRecurrencePeriod1.id)
                        }
                    }
                }
            }

            binding.eventFormRecurrenceRadioGroup.check(
                if (event.isCustomRecurring()) R.id.event_form_recurrence_custom_edit
                else radioButtonId
            )
        })
    }

    /**
     * Applies correct pluralisation to dropdown items.
     */
    private fun resetRecurrencePeriodText(count: Int) {
        with(binding.eventFormRecurrenceCustomLayout) {
            customRecurrencePeriod1.text = resources.getQuantityString(R.plurals.plural_day, count, count)
            customRecurrencePeriod2.text = resources.getQuantityString(R.plurals.plural_week, count, count)
            customRecurrencePeriod3.text = resources.getQuantityString(R.plurals.plural_month, count, count)
            customRecurrencePeriod4.text = resources.getQuantityString(R.plurals.plural_year, count, count)
        }
    }

    // we keep track of TextWatcher so we can remove it when resetting recurrence validation
    var etRecurrenceTextWatcher: TextWatcher? = null

    private fun resetRecurrenceCountValidation(default: Int, min: Int, max: Int) {

        // remove current recurrence count text watcher
        etRecurrenceTextWatcher?.let { binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.removeTextChangedListener(it) }

        // set new text watcher with new config
        etRecurrenceTextWatcher =
            binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.doAfterFilteredIntValueChanged(default, min, max) {
                resetRecurrencePeriodText(it)
            }

        // set current value again because it might be outside of newly set limits
        binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.setText(binding.eventFormRecurrenceCustomLayout.customRecurrenceCount.text)
    }

    /**
     * Shows or hides custom components for different recurrence types.
     */
    private fun showCustomRecurrenceForms(recurrenceTypeIndex: Int) {
        when (recurrenceTypeIndex) {
            0 -> { // day

                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.root.visibleOrGone(false)
            }
            1 -> { // week
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.root.visibleOrGone(true)
            }
            2 -> { // month
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.visibleOrGone(true)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceGroup.visibleOrGone(true)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.root.visibleOrGone(false)
            }
            3 -> { // year
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceTimeRadioGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceOccurrenceGroup.visibleOrGone(false)
                binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.root.visibleOrGone(false)
            }
        }
    }

    /**
     * Updates week days chips height to match dynamic width measured by the system to keep the chips round
     */
    private fun updateChipHeight() {
        binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.root.viewTreeObserver.addOnGlobalLayoutListener {
            (binding.eventFormRecurrenceCustomLayout.customRecurrenceChipsLayout.chipGroupDayOfWeekLayout as ViewGroup).children.forEach { view ->
                if (view.measuredWidth == 0) return@addOnGlobalLayoutListener
                val currentLayoutParams = view.layoutParams
                val maxChipSizePixel = resources.getDimensionPixelSize(R.dimen.week_day_chip_max_size)
                val widthPixel = view.measuredWidth
                if (widthPixel > maxChipSizePixel) {
                    currentLayoutParams.width = maxChipSizePixel
                    currentLayoutParams.height = maxChipSizePixel
                    (currentLayoutParams as (LinearLayout.LayoutParams)).weight = 0.0f
                } else {
                    currentLayoutParams.height = view.measuredWidth
                }
                view.layoutParams = currentLayoutParams
            }
        }
    }
}
