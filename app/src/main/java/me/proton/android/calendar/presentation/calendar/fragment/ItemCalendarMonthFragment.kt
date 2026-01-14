package me.proton.android.calendar.presentation.calendar.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.databinding.ItemCalendarMonthFragmentBinding
import me.proton.android.calendar.databinding.ItemMonthViewGridBinding
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.CalendarsRepository.EventsWindow
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.COLUMNS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.MONTH_GRID_ITEMS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.ROWS_MAX
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class ItemCalendarMonthFragment : Fragment(), KoinComponent {

    private var _binding: ItemCalendarMonthFragmentBinding? = null
    private val binding get() = _binding!!

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val fetchingEventsScope = CoroutineScope(Dispatchers.IO)

    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null

    private var skeletonList: List<LocalDate> = listOf()

    private lateinit var monthView: MonthView
    private var monthViewMaxEventCount = 0

    private var events: List<UiEvent>? = null

    companion object {
        fun newInstance(position: Int, startingPosition: Int, date: LocalDate): ItemCalendarMonthFragment {
            return ItemCalendarMonthFragment().apply {
                arguments = Bundle().apply {
                    putInt(FragmentArguments.POSITION_ARG, position)
                    putInt(FragmentArguments.STARTING_POSITION_ARG, startingPosition)
                    putSerializable(FragmentArguments.DATE_ARG, date)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(FragmentArguments.POSITION_ARG)
            startingPosition = it.getInt(FragmentArguments.STARTING_POSITION_ARG)
            date = it.getSerializable(FragmentArguments.DATE_ARG) as? LocalDate?
        }
    }

    override fun onResume() {
        super.onResume()
        timeZoneId?.let {
            // Set the grid selectable items
            setMonthGridClickableItems(skeletonList, it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ItemCalendarMonthFragmentBinding.inflate(inflater, container, false)
        monthView = binding.monthFragmentMonthView
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return
        val firstDayMonthView = immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            binding.monthFragmentWeekNumberLayout.visibleOrGone(displayWeekNumber)
            monthView.setShowWeekNumbers(displayWeekNumber)
        }

        val monthViewMediator = MediatorLiveData<Pair<String, DayOfWeek>>()
        monthViewMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }
        monthViewMediator.addSource(calendarViewModel.weekStart) { value ->
            val newWeekStart = value?.let { AndroidUtils.getWeekStartDayOfWeek(it) }

            weekStart = newWeekStart

            if (timeZoneId != null && weekStart != null) {
                monthViewMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }

        monthViewMediator.observe(viewLifecycleOwner) {
            it?.let {
                setupMonthViewGrid(firstDayMonthView, it.first, it.second, immutablePosition)
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                currentRange.filterNotNull().flatMapLatest { range ->
                    calendarViewModel.getUiEventsLookupFlow(range.fromDate, range.toDate, range.timeZoneId).map {
                        range to it
                    }
                }.collectLatest { (range, events) ->
                    updateUiEvents(events, range)
                }
            }
        }
    }

    private fun setWeekDaysHeader(weekStart: DayOfWeek, timeZoneId: String, firstDayMonthView: LocalDate) {
        val weekDays = DayOfWeek.entries.toMutableList()
        Collections.rotate(
            weekDays,
            CalendarSettings.DAYS_IN_A_WEEK - (weekStart.value - 1)
        )
        weekDays.forEachIndexed { index, dayOfWeek ->
            (binding.monthFragmentWeekDaysLayout.getChildAt(index) as? TextView)?.let { textView ->
                val firstLetterDayOfWeek = dayOfWeek.format(firstLetter = true)
                textView.text = firstLetterDayOfWeek
                val currentDate = LocalDate.now(ZoneId.of(timeZoneId))
                if (dayOfWeek == currentDate.dayOfWeek && currentDate.month == firstDayMonthView.month && currentDate.year == firstDayMonthView.year) {
                    textView.setTextColor(requireContext().getColorFromAttr(R.attr.proton_text_accent))
                } else {
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_weak))
                }
            }
        }
    }

    private fun setupMonthViewGrid(forDate: LocalDate, timeZoneId: String, startWeekOn: DayOfWeek, position: Int) {
        // Set week days header
        setWeekDaysHeader(startWeekOn, timeZoneId, forDate)

        // Prepare the list of dates we'll display in the month view
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset =
            if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + CalendarSettings.DAYS_IN_A_WEEK
            else firstDayOfTheWeekNumber

        setupWeekNumbers(firstDayOfTheMonth, startWeekOn)

        val previousMonthDayItems = (0 until firstDayOfTheWeekOffset).map {
            firstDayOfTheMonth.minusDays(it.toLong() + 1)
        }.reversed()

        val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
            firstDayOfTheMonth.plusDays(it.toLong())
        }

        val lastDayOfMonthOffset = MONTH_GRID_ITEMS_MAX - (previousMonthDayItems.size + dayItems.size)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            .plusDays(lastDayOfMonthOffset.toLong())

        val upcomingMonthDayItems = (0 until lastDayOfMonthOffset).map {
            firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(it.toLong() + 1)
        }

        skeletonList = AndroidUtils.concatenate(previousMonthDayItems, dayItems, upcomingMonthDayItems)

        // Save the skeleton list in the month view so that it can be drawn along with the events
        monthView.setTimeZoneId(timeZoneId)
        monthView.prepareMonthGrid(skeletonList, forDate.month)
        monthViewMaxEventCount = monthView.calculateMaxEventCount()

        requestEvents(fromDate!!, toDate!!, timeZoneId)

        // Check if we load the events now or if we need to wait
        if (this.isResumed) {
            // Set the grid selectable items
            setMonthGridClickableItems(skeletonList, timeZoneId)
        }
    }

    private fun setMonthGridClickableItems(skeletonList: List<LocalDate>, timeZoneId: String) {
        // Set the clickable grid items
        binding.monthFragmentGridLayout.run {
            this.removeAllViews()

            // Set the row and column counts
            this.rowCount = ROWS_MAX
            this.columnCount = COLUMNS_MAX

            var skeletonListIndex = 0
            for (rowIndex in 0 until ROWS_MAX) {
                for (columnIndex in 0 until COLUMNS_MAX) {

                    val dayItemViewBinding = ItemMonthViewGridBinding.inflate(
                        LayoutInflater.from(this.context),
                        this,
                        false
                    )

                    // Set the view layout params so that the grid is split evenly
                    val row = GridLayout.spec(rowIndex, GridLayout.FILL, 1f)
                    val column = GridLayout.spec(columnIndex, GridLayout.FILL, 1f)
                    val layoutParams = GridLayout.LayoutParams(row, column)
                    dayItemViewBinding.root.layoutParams = layoutParams

                    if (skeletonListIndex <= skeletonList.lastIndex) {
                        // Make sure we don't go out of bound
                        val date = skeletonList[skeletonListIndex]

                        dayItemViewBinding.root.setOnSingleClickListener {
                            val selectedDateEvents = events?.filter {
                                it.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate() == date && it.spansSingleDay(true)
                            }
                            if (!selectedDateEvents.isNullOrEmpty()) {
                                // Save the time of the first event of the selected day in order for the day view to be
                                //  initialized on that position without waiting for the events loading to finish
                                calendarViewModel.firstEventOfTheDayTime =
                                    Collections.min(
                                        selectedDateEvents.map {
                                            it.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime()
                                        }
                                    )
                            } else {
                                calendarViewModel.firstEventOfTheDayTime = null
                            }

                            // Save the current month in order to know where to return if user presses back from day view
                            calendarViewModel.monthViewDate = date

                            // Set the selected date value and switch to day view
                            calendarViewModel.handleDaySelected(date)
                            calendarViewModel.viewMode.postValue(ViewMode.DAY)
                            mainViewModel.setLastViewMode(ViewMode.DAY)
                        }
                    }

                    this.addView(dayItemViewBinding.root)

                    skeletonListIndex++
                }
            }
        }
    }

    private fun requestEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String) {
        lifecycleScope.launch {
            calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId, coroutineScope = fetchingEventsScope)
            currentRange.value = EventsWindow(
                fromDate = fromDate,
                toDate = toDate,
                timeZoneId = timeZoneId,
            )
        }
    }

    private val currentRange = MutableStateFlow<EventsWindow?>(null)

    private fun updateUiEvents(newEvents: CalendarsRepository.GetEventsResult<UiEvent>, range: EventsWindow) {
        when (newEvents) {
            CalendarsRepository.GetEventsResult.InProgress -> {
                // Progress is shown through the skeleton events
                binding.monthFragmentLoader.isVisible = true
            }
            is CalendarsRepository.GetEventsResult.Success -> {
                val alphabeticallySortedEvents = newEvents.events.sortedBy { event -> event?.summary }
                events = alphabeticallySortedEvents

                // Display the UI events in the month view
                displayMonthViewUiEvents(alphabeticallySortedEvents, range.fromDate)

                binding.monthFragmentLoader.isVisible = false
            }
            is CalendarsRepository.GetEventsResult.Exception -> {
                binding.monthFragmentLoader.isVisible = false
            }
        }
    }

    private fun displayMonthViewUiEvents(events: List<UiEvent>, fromDate:LocalDate) {
        val newMaxEventCount = monthView.calculateMaxEventCount()
        if (newMaxEventCount > monthViewMaxEventCount) monthViewMaxEventCount = newMaxEventCount

        // Get the map of MonthViewEvent indexed by day
        val monthViewEventsMap = calendarViewModel.getMonthViewEventsMap(
            events,
            fromDate,
            monthViewMaxEventCount,
            false
        )

        // Set the month view events so that they can be drawn
        monthView.setMonthViewEvents(monthViewEventsMap, calendarViewModel.displayWeekNumber.value ?: false, monthViewMaxEventCount)
    }

    private fun setupWeekNumbers(firstDay: LocalDate, startWeekOn: DayOfWeek) {
        // Setup week numbers
        binding.monthFragmentWeekNumberLayout.run {
            for (i in 0 until 6) {
                val weekNumberTextView = (this.getChildAt(i) as? LinearLayout)?.getChildAt(0) as? TextView
                weekNumberTextView?.text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (fetchingEventsScope.isActive) fetchingEventsScope.cancel()
        _binding = null
    }
}
