package me.proton.android.calendar.presentation.calendar.fragment

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.FragmentArguments.DATE_ARG
import me.proton.android.calendar.common.FragmentArguments.POSITION_ARG
import me.proton.android.calendar.common.FragmentArguments.STARTING_POSITION_ARG
import me.proton.android.calendar.common.MAX_CALENDAR_INDICATORS
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.databinding.ItemMiniCalendarBinding
import me.proton.android.calendar.databinding.ItemMiniCalendarFragmentBinding
import me.proton.android.calendar.databinding.MiniCalendarDotBinding
import me.proton.android.calendar.databinding.MiniCalendarPlusBinding
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.ceil

@AndroidEntryPoint
class ItemMiniCalendarFragment : Fragment() {

    private var _binding: ItemMiniCalendarFragmentBinding? = null
    private val binding get() = _binding!!

    private var position: Int? = null
    private var startingPosition: Int? = null
    private var date: LocalDate? = null

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    @Inject
    lateinit var logger: Logger

    private var timeZoneId: String? = null
    private var weekStart: DayOfWeek? = null

    private var fullWeeksInMonth = 0

    private var selectedMiniCalendarItem: Int? = null

    private var indicators: Map<LocalDate, List<String>> = hashMapOf()

    private var currentMiniCalendarMonthList: List<MiniCalendarItem>? = null

    private val fetchingEventsScope = CoroutineScope(Dispatchers.IO)

    private var todayCurrentValue: LocalDate? = null

    data class MiniCalendarItem(
        val date: LocalDate,
        val isSelected: Boolean,
        /**
         * Item is an actual day indicator and not dummy helper
         */
        val isDay: Boolean,
        val indicatorColors: List<String>
    )

    companion object {
        fun newInstance(position: Int, startingPosition: Int, date: LocalDate): ItemMiniCalendarFragment {
            return ItemMiniCalendarFragment().apply {
                arguments = Bundle().apply {
                    putInt(POSITION_ARG, position)
                    putInt(STARTING_POSITION_ARG, startingPosition)
                    putSerializable(DATE_ARG, date)
                }
            }
        }

        /**
         * No need to measure the adapter view, we can calculate the height because item dimensions
         * are constant.
         */
        fun calculateAdapterHeight(
            context: Context,
            firstDayOfMonth: LocalDate,
            startWeekOn: DayOfWeek,
            isMonthView: Boolean
        ): Int {
            if (!isMonthView) return context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)

            val fullWeeksInMonth = calculateFullWeeksInMonth(firstDayOfMonth, startWeekOn)

            return context.resources.getDimensionPixelSize(R.dimen.calendar_item_header_height) +
                    fullWeeksInMonth * context.resources.getDimensionPixelSize(R.dimen.calendar_item_height) +
                    (if (fullWeeksInMonth == 1) context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing) * 2
                    else (fullWeeksInMonth) * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)) +
                    fullWeeksInMonth * 2 * context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_spacing) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_bottom_spacing) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height) +
                    context.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)
        }

        fun calculateFullWeeksInMonth(firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek): Int {
            val firstDayOfTheWeekNumber = firstDayOfMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheWeekOffset =
                if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber

            val dayCellsToShow = firstDayOfTheWeekOffset + firstDayOfMonth.lengthOfMonth()

            return ceil(dayCellsToShow / DAYS_IN_A_WEEK.toDouble()).toInt()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(POSITION_ARG)
            startingPosition = it.getInt(STARTING_POSITION_ARG)
            date = it.getSerializable(DATE_ARG) as? LocalDate?
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = ItemMiniCalendarFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val miniCalendarMediator = MediatorLiveData<Pair<String, DayOfWeek>>() // Month view value is not needed if week component is disabled
        miniCalendarMediator.addSource(calendarViewModel.timeZoneId) { value ->
            timeZoneId = value?.id

            if (timeZoneId != null && weekStart != null) {
                miniCalendarMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }
        miniCalendarMediator.addSource(calendarViewModel.weekStart) { value ->
            weekStart = value?.let { getWeekStartDayOfWeek(it) }

            if (timeZoneId != null && weekStart != null) {
                miniCalendarMediator.value = Pair(timeZoneId!!, weekStart!!)
            }
        }

        miniCalendarMediator.distinctUntilChanged().observe(viewLifecycleOwner) {
            it?.let {
                setupItemMiniCalendarContent(it.first, it.second)
            }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            binding.llWeeknumbers.visibleOrGone(displayWeekNumber)
        }
    }

    private fun setupItemMiniCalendarContent(timeZoneId: String, startWeekOn: DayOfWeek) {

        val immutableDate = date ?: return
        val immutablePosition = position ?: return
        val immutableStartingPosition = startingPosition ?: return

        val firstDayMonthView = immutableDate.plusMonths((immutablePosition - immutableStartingPosition).toLong())

        calendarViewModel.lifeCycleScope.launch {

            val firstDayOfTheMonth = firstDayMonthView.withDayOfMonth(1)
            val firstDayOfTheMonthWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
            val firstDayOfTheMonthWeekOffset =
                if (firstDayOfTheMonthWeekNumber < 0) firstDayOfTheMonthWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheMonthWeekNumber
            val lastDayOfTheMonth = firstDayMonthView.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

            val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheMonthWeekOffset.toLong())
            val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
                .plusDays(lastDayOfMonthOffset.toLong())

            calendarViewModel.fetchEvents(fromDate, toDate, timeZoneId, coroutineScope = fetchingEventsScope)

            initialiseMiniCalendarContent(
                firstDayMonthView,
                startWeekOn,
                timeZoneId
            )
        }
    }

    private fun initialiseMiniCalendarContent(forDate: LocalDate, startWeekOn: DayOfWeek, timeZoneId: String) {
        val firstDayOfTheMonth = forDate.withDayOfMonth(1)
        val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
        val firstDayOfTheWeekOffset =
            if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
        val lastDayOfTheMonth = forDate.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
        val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)

        val fromDate = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
        val toDate = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth())
            .plusDays(lastDayOfMonthOffset.toLong())

        val firstMiniCalendarDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())

        setupWeekNumbers(firstDayOfTheMonth, startWeekOn)

        val previousMonthDayItems = (0 until firstDayOfTheWeekOffset).map {
            val date = firstDayOfTheMonth.minusDays(it.toLong() + 1)
            MiniCalendarItem(date, false, true, emptyList())
        }.reversed()

        val dayItems = (0 until firstDayOfTheMonth.lengthOfMonth()).map {
            val date = firstDayOfTheMonth.plusDays(it.toLong())
            MiniCalendarItem(date, false, true, emptyList())
        }

        val upcomingMonthDayItems = (0 until lastDayOfMonthOffset).map {
            val date = firstDayOfTheMonth.withDayOfMonth(firstDayOfTheMonth.lengthOfMonth()).plusDays(it.toLong() + 1)
            MiniCalendarItem(date, false, true, emptyList())
        }

        // Submit month skeleton with only days and weekday names
        val skeletonList = AndroidUtils.concatenate(previousMonthDayItems, dayItems, upcomingMonthDayItems)
        setMiniCalendarSkeletonList(skeletonList, forDate, firstDayOfTheMonth, firstMiniCalendarDay, startWeekOn, timeZoneId)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                calendarViewModel.calendarIndicators(
                    fromDate,
                    toDate,
                    timeZoneId,
                ).collectLatest { indicators ->
                    view?.run {
                        this@ItemMiniCalendarFragment.indicators = indicators
                        applyMiniCalendarIndicators(indicators, firstMiniCalendarDay)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                calendarViewModel.selectedDateTime.observe(viewLifecycleOwner) { selectedDateTime ->
                    applySelectedDate(
                        selectedDateTime.first,
                        firstMiniCalendarDay,
                        firstDayOfTheMonth,
                        this@ItemMiniCalendarFragment.timeZoneId ?: timeZoneId
                    )
                }
            }
        }
    }

    private fun setMiniCalendarSkeletonList(
        skeletonList: List<MiniCalendarItem>,
        forDate: LocalDate,
        firstDay: LocalDate,
        firstMiniCalendarDay: LocalDate,
        startWeekOn: DayOfWeek,
        timeZoneId: String
    ) {
        if (currentMiniCalendarMonthList == skeletonList && todayCurrentValue == LocalDate.now(ZoneId.of(timeZoneId))) return
        todayCurrentValue = LocalDate.now(ZoneId.of(timeZoneId))
        currentMiniCalendarMonthList = skeletonList
        binding.glMiniCalendar.run {
            this.removeAllViews()
            selectedMiniCalendarItem = -1
            fullWeeksInMonth = calculateFullWeeksInMonth(firstDay, startWeekOn)
            this.rowCount = fullWeeksInMonth
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
            isRowOrderPreserved = false
            this.addMiniCalendarItemView(skeletonList, forDate)
        }

        val selectedDate = calendarViewModel.selectedDateTime.value?.first
        if (selectedDate != null) {
            applySelectedDate(selectedDate, firstMiniCalendarDay, firstDay, timeZoneId)
        }
    }

    private fun ViewGroup.addMiniCalendarItemView(skeletonList: List<MiniCalendarItem>, forDate: LocalDate) {
        skeletonList.forEach { item ->
            val miniCalendarItemViewBinding = ItemMiniCalendarBinding.inflate(layoutInflater, this, false)
            val viewContext = miniCalendarItemViewBinding.root.context

            if (this.childCount > DAYS_IN_A_WEEK) {
                val itemLayoutParams: GridLayout.LayoutParams =
                    miniCalendarItemViewBinding.root.layoutParams as GridLayout.LayoutParams
                itemLayoutParams.topMargin =
                    viewContext.resources.getDimensionPixelSize(R.dimen.calendar_item_day_vertical_spacing)
            }

            when {
                item.isSelected -> {
                    miniCalendarItemViewBinding.itemMiniCalendarText.setTextAppearance(
                        viewContext,
                        R.style.Text_DefaultSmall_Strong_Inverted
                    )
                    miniCalendarItemViewBinding.itemMiniCalendarText.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                }
                item.date == LocalDate.now(ZoneId.of(timeZoneId)) -> {
                    miniCalendarItemViewBinding.itemMiniCalendarText.setTextAppearance(
                        viewContext,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarItemViewBinding.itemMiniCalendarText.setTextColor(
                        requireContext().getColorFromAttr(
                            R.attr.proton_text_accent
                        )
                    )
                    miniCalendarItemViewBinding.itemMiniCalendarText.setBackgroundResource(R.drawable.ripple_mini_calendar_day_today)
                }
                item.date.month != forDate.month -> {
                    miniCalendarItemViewBinding.itemMiniCalendarText.setTextAppearance(
                        viewContext,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarItemViewBinding.itemMiniCalendarText.setTextColor(
                        ContextCompat.getColor(
                            viewContext,
                            R.color.text_hint
                        )
                    )
                    miniCalendarItemViewBinding.itemMiniCalendarText.setBackgroundResource(0)
                }
                else -> {
                    miniCalendarItemViewBinding.itemMiniCalendarText.setTextAppearance(
                        viewContext,
                        R.style.Text_DefaultSmall_Strong
                    )
                    miniCalendarItemViewBinding.itemMiniCalendarText.setBackgroundResource(0)
                }
            }

            miniCalendarItemViewBinding.itemMiniCalendarText.text = "${item.date.dayOfMonth}"

            miniCalendarItemViewBinding.llCalendarDots.visibleOrInvisible(true)
            miniCalendarItemViewBinding.llCalendarDots.apply {
                this.children.forEachIndexed { index, view ->
                    if (item.indicatorColors.size - 1 >= index) {
                        (view as ImageView).drawable.setTint(Color.parseColor(item.indicatorColors[index]))
                        view.visibleOrGone(true)
                    } else {
                        view.visibleOrGone(false)
                    }
                }
            }

            miniCalendarItemViewBinding.root.setOnClickListener {
                calendarViewModel.handleDaySelected(item.date)
            }

            this.addView(miniCalendarItemViewBinding.root)
        }
    }

    private fun applyMiniCalendarIndicators(indicators: Map<LocalDate, List<String>>, firstMiniCalendarDay: LocalDate) {
        binding.glMiniCalendar.children.forEach {
            it.findViewById<LinearLayout>(R.id.ll_calendar_dots).removeAllViews()
        }
        val processedViewIndexList = arrayListOf<Int>()
        indicators.forEach { (date, indicatorColors) ->
            val miniCalendarIndex = ChronoUnit.DAYS.between(firstMiniCalendarDay, date).toInt()
            binding.glMiniCalendar.getChildAt(miniCalendarIndex)?.let { itemView ->
                val llCalendarDotsView = itemView.findViewById<LinearLayout>(R.id.ll_calendar_dots)
                llCalendarDotsView.visibleOrInvisible(true)
                processedViewIndexList.add(miniCalendarIndex)
                indicatorColors.forEachIndexed { index, indicatorColor ->
                    if (index + 1 == MAX_CALENDAR_INDICATORS) {
                        val miniCalendarPlusViewBinding = MiniCalendarPlusBinding.inflate(
                            LayoutInflater.from(this.context),
                            llCalendarDotsView,
                            false
                        )
                        llCalendarDotsView.addView(miniCalendarPlusViewBinding.root)
                    } else {
                        val miniCalendarDotViewBinding = MiniCalendarDotBinding.inflate(
                            LayoutInflater.from(this.context),
                            llCalendarDotsView,
                            false
                        )
                        miniCalendarDotViewBinding.root.backgroundTintList = ColorStateList.valueOf(
                            Color.parseColor(indicatorColor)
                        )
                        llCalendarDotsView.addView(miniCalendarDotViewBinding.root)
                    }
                }
            }
        }
    }

    private fun applySelectedDate(
        selectedDate: LocalDate,
        firstMiniCalendarDay: LocalDate,
        firstDayOfTheMonth: LocalDate,
        timeZoneId: String
    ) {
        if (binding.glMiniCalendar.childCount <= 0) return

        val newSelectedMiniCalendarItem = ChronoUnit.DAYS.between(firstMiniCalendarDay, selectedDate).toInt()

        // Apply styles to month mini calendar
        if (selectedMiniCalendarItem != newSelectedMiniCalendarItem && firstDayOfTheMonth.month == selectedDate.month) {

            val selectedMiniCalendarItem = selectedMiniCalendarItem
            if (selectedMiniCalendarItem != null && selectedMiniCalendarItem >= 0) {

                binding.glMiniCalendar.getChildAt(selectedMiniCalendarItem)?.let { miniCalendarItemView ->
                    miniCalendarItemView.findViewById<TextView>(R.id.itemMiniCalendarText)?.let { miniCalendarText ->
                        if (firstMiniCalendarDay.plusDays(selectedMiniCalendarItem.toLong()) == LocalDate.now(ZoneId.of(timeZoneId))) {
                            // Apply today's style
                            miniCalendarText.setTextAppearance(
                                miniCalendarItemView.context,
                                R.style.Text_DefaultSmall_Strong
                            )
                            miniCalendarText.setTextColor(
                                requireContext().getColorFromAttr(
                                    R.attr.proton_text_accent
                                )
                            )
                            miniCalendarText.setBackgroundResource(R.drawable.ripple_mini_calendar_day_today)
                        } else if (selectedDate.month != firstMiniCalendarDay.plusDays(
                                selectedMiniCalendarItem.toLong()
                            ).month &&
                            firstDayOfTheMonth.month != firstMiniCalendarDay.plusDays(
                                selectedMiniCalendarItem.toLong()
                            ).month &&
                            newSelectedMiniCalendarItem >= 0 &&
                            newSelectedMiniCalendarItem < binding.glMiniCalendar.childCount
                        ) {
                            // Apply previous / upcoming month items style
                            miniCalendarText.setTextAppearance(
                                miniCalendarItemView.context,
                                R.style.Text_DefaultSmall_Strong
                            )
                            miniCalendarText.setTextColor(
                                ContextCompat.getColor(
                                    miniCalendarItemView.context,
                                    R.color.text_hint
                                )
                            )
                            miniCalendarText.setBackgroundResource(0)
                        } else {
                            // Apply default items style
                            miniCalendarText.setTextAppearance(
                                miniCalendarItemView.context,
                                R.style.Text_DefaultSmall_Strong
                            )
                            miniCalendarText.setBackgroundResource(0)
                        }
                    }
                }
            }

            this.selectedMiniCalendarItem = newSelectedMiniCalendarItem
            if (selectedDate.month == firstDayOfTheMonth.month) {
                // Only display selected date style for month currently displayed
                binding.glMiniCalendar.getChildAt(newSelectedMiniCalendarItem)?.let { miniCalendarItemView ->
                    miniCalendarItemView.findViewById<TextView>(R.id.itemMiniCalendarText)?.let { miniCalendarText ->
                        // Apply selected date item style
                        miniCalendarText.setTextAppearance(
                            miniCalendarItemView.context,
                            R.style.Text_DefaultSmall_Strong_Inverted
                        )
                        miniCalendarText.setBackgroundResource(R.drawable.ripple_mini_calendar_day_selected)
                    }
                }
            }
        }
    }

    private fun setupWeekNumbers(firstDay: LocalDate, startWeekOn: DayOfWeek) {
        // Setup week numbers
        binding.llWeeknumbers.run {
            this.removeAllViews()
            fullWeeksInMonth = calculateFullWeeksInMonth(firstDay, startWeekOn)
            for (i in 0 until fullWeeksInMonth) {
                val weekNumberView = LayoutInflater.from(this.context).inflate(
                    R.layout.item_mini_calendar_week_number,
                    this,
                    false
                )
                val textView = weekNumberView as TextView
                textView.text = "${firstDay.plusWeeks(i.toLong()).weekNumber(startWeekOn)}"
                if (i == 0) {
                    // Update top margin for first row
                    val layoutParams = textView.layoutParams as LinearLayout.LayoutParams
                    layoutParams.topMargin =
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_top_first)
                    textView.layoutParams = layoutParams
                }
                addView(weekNumberView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (fetchingEventsScope.isActive) fetchingEventsScope.cancel()
        _binding = null
    }
}
