package me.proton.android.calendar.presentation.calendar.fragment

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.whenStarted
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Operation
import com.alamkanak.weekview.firstVisibleDateAsLocalDate
import com.alamkanak.weekview.scrollToDate
import com.alamkanak.weekview.scrollToDateTime
import com.alamkanak.weekview.scrollToTime
import com.alamkanak.weekview.setDate
import com.alamkanak.weekview.setDateFormatter
import com.alamkanak.weekview.setDateTime
import com.alamkanak.weekview.setWeekDayFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.DAY_VIEW_DAYS_COUNT
import me.proton.android.calendar.common.EventEditDeleteOption
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.THREE_DAYS_VIEW_DAYS_COUNT
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.WEEK_VIEW_DATE_FORMATTER_PATTERN
import me.proton.android.calendar.common.WEEK_VIEW_DAYS_COUNT
import me.proton.android.calendar.common.WEEK_VIEW_FUTURE_DAYS_TO_LOAD
import me.proton.android.calendar.common.WEEK_VIEW_PAST_DAYS_TO_LOAD
import me.proton.android.calendar.common.WEEK_VIEW_WEEKDAY_FORMATTER_PATTERN
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.animateGuidelineHeightChange
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.firstDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatMonth
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLocaleForFormatting
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getTimeWithPadding
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekNumber
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayEventDecryptionErrorDialog
import me.proton.android.calendar.databinding.FragmentMonthBinding
import me.proton.android.calendar.databinding.ItemMiniCalendarHeaderBinding
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.CalendarsRepository.EventsWindow
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.WeekViewCalendarEntity
import me.proton.android.calendar.domain.model.getActualEventId
import me.proton.android.calendar.domain.model.toWeekViewCalendarEntityEvent
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.adapter.WeekViewAdapter
import me.proton.android.calendar.presentation.calendar.customView.MonthLayoutGestureListener
import me.proton.android.calendar.presentation.calendar.fragment.ItemMiniCalendarFragment.Companion.calculateAdapterHeight
import me.proton.android.calendar.presentation.calendar.pagerAdapter.AgendaPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.MiniCalendarPagerAdapter
import me.proton.android.calendar.presentation.calendar.pagerAdapter.MonthPagerAdapter
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseFragment
import me.proton.android.calendar.presentation.main.viewModel.FeatureFlagViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.TimeZone
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class MonthFragment : BaseFragment<FragmentMonthBinding>() {

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val featureFlagViewModel: FeatureFlagViewModel by activityViewModels()

    @Inject
    lateinit var handleAlarmsUseCase: HandleAlarmsUseCase
    @Inject
    lateinit var logger: Logger

    private lateinit var miniCalendarPagerAdapter: MiniCalendarPagerAdapter
    private lateinit var agendaPagerAdapter: AgendaPagerAdapter
    private lateinit var monthPagerAdapter: MonthPagerAdapter

    private val navigationArguments: MonthFragmentArgs by navArgs()

    private lateinit var toolbarTitle: TextView

    private val mainViewModel: MainViewModel by viewModels()

    override val TAG: String
        get() = "CalendarFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_month

    private lateinit var buttonSearch: View
    private lateinit var buttonCreate: View
    private lateinit var buttonToday: View

    private lateinit var miniCalendarPageChangeCallback: ViewPager2.OnPageChangeCallback

    private var startWeekOn: DayOfWeek? = null
    private var timeZoneId: String? = null

    private var currentViewMode: ViewMode? = null

    // Week view values
    private var currentFromDate: LocalDate? = null
    private var currentToDate: LocalDate? = null
    private var currentTimeZoneId: String? = null
    private lateinit var weekViewAdapter: WeekViewAdapter
    private var initWeekView = false // Use it to ignore the first range change callback in week view mode (due to week view sticking to week start)

    private val currentRange = MutableStateFlow<EventsWindow?>(null)

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSearch = layoutInflater.inflate(R.layout.toolbar_action_button, fragmentToolbarContent, false)
        with (buttonSearch) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_proton_magnifier))
        }
        buttonCreate = layoutInflater.inflate(R.layout.toolbar_action_button, fragmentToolbarContent, false)
        with (buttonCreate) {
            val drawableRes = R.drawable.ic_proton_plus
            tag = drawableRes.toString()
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, drawableRes))
        }
        buttonToday = layoutInflater.inflate(R.layout.toolbar_action_button, fragmentToolbarContent, false)
        with (buttonToday) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_calendar_today_indicator))
        }

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.fragment_toolbar_content)) {
            addView(buttonSearch, resources.getDimensionPixelSize(
                R.dimen.action_clickable_size
            ), resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            buttonSearch.visibleOrGone(
                featureFlagViewModel.isEventSearchEnabled()
            )

            addView(
                buttonToday, resources.getDimensionPixelSize(
                    R.dimen.action_clickable_size
                ), resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )

            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size)
            )
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                buttonCreate, layoutParams
            )
        }

        toolbarTitle = toolbar.findViewById(R.id.fragment_toolbar_title)
    }

    private fun setToolbarListeners(timeZoneId: ZoneId) {
        buttonSearch.setOnSingleClickListener {
            requireActivity().findNavController(R.id.nav_host_fragment_container_view).navigate(R.id.nav_search)
        }

        buttonCreate.setOnSingleClickListener {
            lifecycleScope.launch {
                val hasWritableActiveCalendars = calendarViewModel.getUserCalendars()?.any { it.isActive && it.allowEditEvents }
                if (hasWritableActiveCalendars == true) {
                    // Each item in the adapter is one day
                    calendarViewModel.selectedDateTime.value?.let { currentDateTime ->
                        val currentDate = currentDateTime.first
                        val initStartDateTime =
                            if (currentViewMode == ViewMode.MONTH) {
                                if (currentDate.month == LocalDate.now(timeZoneId).month && currentDate.year == LocalDate.now(timeZoneId).year) {
                                    ICalUtilsImpl.generateEventStart(timeZoneId)
                                } else ZonedDateTime.of(currentDate.withDayOfMonth(1), LocalTime.of(8, 0), timeZoneId).toLocalDateTime()
                            } else if (currentDate == LocalDate.now(timeZoneId)) {
                                ICalUtilsImpl.generateEventStart(timeZoneId, currentDate)
                            } else {
                                ZonedDateTime.of(currentDate, LocalTime.of(8, 0), timeZoneId).toLocalDateTime()
                            }
                        requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                            .navigate(
                                Navigation.Deeplink.toEventCreate(
                                    initStartDate = initStartDateTime.toLocalDate(),
                                    initStartTime = initStartDateTime.toLocalTime()
                                )
                            )
                    }
                } else {
                    requireActivity().displaySnackBar(resources.getString(R.string.snack_create_event_no_active_calendar))
                }
            }
        }

        with(buttonToday) {
            // Display current day number
            val textField = (findViewById<TextView>(R.id.toolbarActionSecondaryText))
            textField.text = LocalDate.now(timeZoneId).dayOfMonth.toString()
            textField.visibility = View.VISIBLE
        }
        buttonToday.setOnSingleClickListener {
            if (currentViewMode == ViewMode.DAY || currentViewMode == ViewMode.THREE_DAY || currentViewMode == ViewMode.WEEK) {

                updateWeekView(LocalDate.now(timeZoneId), LocalTime.now(timeZoneId).getTimeWithPadding())
                calendarViewModel.handleDaySelected(LocalDate.now(timeZoneId), LocalTime.now(timeZoneId).getTimeWithPadding())
            } else {
                calendarViewModel.handleDaySelected(LocalDate.now(timeZoneId))
            }
        }
    }

    private fun setMiniCalendarPageChangeCallback(startWeekOn: DayOfWeek) {
        miniCalendarPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                val monthStartingPosition = miniCalendarPagerAdapter.startingPosition
                val monthStartingDate = miniCalendarPagerAdapter.firstDayOfMonth
                val firstDay = monthStartingDate.plusMonths((position - monthStartingPosition).toLong())

                calendarViewModel.handleDaySelected(firstDay, fromMonthPagerCallback = true)

                setToolbarMonthYearTitle(firstDay, binding.miniCalendarPager.currentItem)

                adjustMiniCalendarView(firstDay.withDayOfMonth(1), startWeekOn)

                timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
            }
        }
    }

    private val agendaPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            // Agenda view pager is hidden when displaying other views
            if (currentViewMode == ViewMode.AGENDA) {
                val currentDate =
                    calendarViewModel.initialToday.plusDays((binding.agendaPager.currentItem - agendaPagerAdapter.startingPosition).toLong())
                calendarViewModel.handleDaySelected(currentDate)
            }
        }
    }

    private fun updateMiniCalendarHeight(
        startWeekOn: DayOfWeek,
        isMonthView: Boolean,
    ) {
        val firstDayOfMonth = calendarViewModel.selectedDateTime.value?.first?.withDayOfMonth(1) ?: return // We will retry this once calendarViewModel.selectedDate has been set

        // Calculate current month's desired height for both mini calendar mode (month / week)
        val desiredHeight = calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )
        if (isMonthView) {
            calendarViewModel.currentPosDesiredMonthHeight = desiredHeight
        } else {
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        if (!isResumed) return // TODO ViewBinding NPE
            binding.viewPagerTopGuideline.animateGuidelineHeightChange(
                desiredHeight,
                calendarViewModel.currentPosDesiredMonthHeight
            ) {
            }
            binding.viewPagerSliderGuideline.animateGuidelineHeightChange(
                desiredHeight - requireContext().resources.getDimensionPixelSize(
                    R.dimen.calendar_slider_height
                ), calendarViewModel.currentPosDesiredMonthHeight
            ) { }
    }

    override fun onStart() {
        super.onStart()

        calendarViewModel.showAutoDetectPrimaryTimezone = true
        calendarViewModel.initialAutoDetectPrimaryTimezoneValue = null
        calendarViewModel.autoDetectPrimaryTimezone.observe(viewLifecycleOwner) { autoDetectPrimaryTimezone ->
            autoDetectPrimaryTimezone ?: return@observe
            lifecycleScope.launch {
                calendarViewModel.handleAutoDetectPrimaryTimezone(autoDetectPrimaryTimezone, requireContext())
            }
        }
    }

    private fun adjustMiniCalendarView(firstDayOfMonth: LocalDate, startWeekOn: DayOfWeek) {

        // Mini calendar is hidden when displaying Month view
        if (currentViewMode == ViewMode.MONTH) return

        // ViewPager will adjust its height to the largest item it contains and display empty space for
        // smaller items, like months with fewer week lines. That's why we need to resize it every time we
        // display a month

        val isMonthView = calendarViewModel.monthView.value ?: true

        // Calculate current month's desired height for both mini calendar mode (month / week)
        val desiredHeight = calculateAdapterHeight(
            requireContext(),
            firstDayOfMonth,
            startWeekOn,
            isMonthView
        )
        if (isMonthView) {
            calendarViewModel.currentPosDesiredMonthHeight = desiredHeight
        } else {
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        binding.viewPagerTopGuideline.animateGuidelineHeightChange(desiredHeight, null) {
        }
        binding.viewPagerSliderGuideline.animateGuidelineHeightChange(
            desiredHeight - requireContext().resources.getDimensionPixelSize(
                R.dimen.calendar_slider_height
            ), null
        ) { }
    }

    override fun onDestroyView() {
        if (this::miniCalendarPageChangeCallback.isInitialized) binding.miniCalendarPager.unregisterOnPageChangeCallback(
            miniCalendarPageChangeCallback
        )
        binding.agendaPager.unregisterOnPageChangeCallback(agendaPageChangeCallback)
        super.onDestroyView()
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentMonthBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        miniCalendarPagerAdapter =
            MiniCalendarPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))
        monthPagerAdapter =
            MonthPagerAdapter(requireActivity(), calendarViewModel.initialToday.withDayOfMonth(1))

        val monthStartingPosition = miniCalendarPagerAdapter.startingPosition

        binding.miniCalendarPager.apply {
            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1
            setCurrentItem(monthStartingPosition, false)
        }

        agendaPagerAdapter = AgendaPagerAdapter(requireActivity(), calendarViewModel.initialToday)

        calendarViewModel.viewMode.value?.let { viewMode ->
            when (viewMode) {
                ViewMode.DAY -> binding.weekView.numberOfVisibleDays = DAY_VIEW_DAYS_COUNT
                ViewMode.THREE_DAY -> binding.weekView.numberOfVisibleDays = THREE_DAYS_VIEW_DAYS_COUNT
                ViewMode.WEEK -> binding.weekView.numberOfVisibleDays = WEEK_VIEW_DAYS_COUNT
                else -> {}
            }
            binding.fragmentMonthLayout.viewMode = viewMode
            initAgendaPager(viewMode)
        }

        calendarViewModel.loading.observe(viewLifecycleOwner) { loading ->
            fragmentProgressBar.visibleOrInvisible(loading)
        }

        // Switch between Agenda and Day views
        calendarViewModel.viewMode.observe(viewLifecycleOwner) { viewMode ->
            when (viewMode) {
                ViewMode.DAY -> binding.weekView.numberOfVisibleDays = DAY_VIEW_DAYS_COUNT
                ViewMode.THREE_DAY -> binding.weekView.numberOfVisibleDays = THREE_DAYS_VIEW_DAYS_COUNT
                ViewMode.WEEK -> binding.weekView.numberOfVisibleDays = WEEK_VIEW_DAYS_COUNT
                else -> {}
            }
            binding.fragmentMonthLayout.viewMode = viewMode
            initAgendaPager(viewMode)
        }

        binding.agendaPager.registerOnPageChangeCallback(agendaPageChangeCallback)

        // Init selected date
        val navArgsDate = navigationArguments.date
        val navigationDate = if (navArgsDate == null) {
            null
        } else {
            try {
                LocalDate.parse(
                    navArgsDate,
                    DateTimeFormatter.ISO_LOCAL_DATE
                )
            } catch (e: DateTimeParseException) {
                null
            }
        }
        calendarViewModel.handleInitialDaySelection(navigationDate ?: calendarViewModel.initialToday)

        calendarViewModel.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
            binding.weekView.timeFormatIs24Hour = calendarViewModel.timeFormatIs24Hour(timeFormat, requireContext())
            // Time formatter is already taking timeFormatIs24Hour value into account, we just need to trigger onTimeFormatterChanged
            binding.weekView.setTimeFormatter(binding.weekView.getTimeFormatter())
            calendarViewModel.selectedDateTime.value?.let {
                binding.weekView.setDate(it.first)
            }
        }

        calendarViewModel.selectedDateTime.observe(viewLifecycleOwner) { selectedDateTime ->
            val selectedDate = selectedDateTime.first
            setToolbarMonthYearTitle(selectedDate, binding.miniCalendarPager.currentItem)

            // Only update those when we are in week views
            if (calendarViewModel.viewMode.value == ViewMode.WEEK ||
                calendarViewModel.viewMode.value == ViewMode.THREE_DAY ||
                calendarViewModel.viewMode.value == ViewMode.DAY) {
                updateWeekView(selectedDate, selectedDateTime.second)
            }

            lifecycleScope.launch {
                val firstDayOfMonth = selectedDate.withDayOfMonth(1)
                val weekStart = calendarViewModel.getWeekStart() ?: return@launch
                val startWeekOn = getWeekStartDayOfWeek(weekStart)
                if (calendarViewModel.currentPosDesiredMonthHeight != calculateAdapterHeight(
                        requireContext(),
                        firstDayOfMonth,
                        startWeekOn,
                        true
                    )
                ) {
                    whenStarted { // TODO ViewBinding NPE
                        adjustMiniCalendarView(firstDayOfMonth, startWeekOn)
                    }
                }
            }

            // Handle selected day change in miniCalendarPager (mini calendar / month views)
            if (binding?.miniCalendarPager?.adapter != null) {
                val monthStartingDate = calendarViewModel.initialToday.withDayOfMonth(1)
                val offset = ChronoUnit.MONTHS.between(monthStartingDate, selectedDate.withDayOfMonth(1)).toInt()
                val miniCalendarIndex = monthStartingPosition + offset
                if (binding?.miniCalendarPager?.currentItem != miniCalendarIndex) {
                    // smooth-scroll only when switching between adjacent months
                    binding?.miniCalendarPager?.post {
                        if (isResumed) { // TODO Temporary fix for NPE in getViewBinding
                            val currentItem = binding?.miniCalendarPager?.currentItem
                            binding?.miniCalendarPager?.setCurrentItem(
                                miniCalendarIndex,
                                if (currentItem != null) Math.abs(currentItem - miniCalendarIndex) == 1 else false
                            )
                        }
                    }
                }
            }

            // Handle selected day change in agendaPager (agenda / day views)
            if (binding?.agendaPager?.adapter != null) {
                val startingDate = agendaPagerAdapter.startingDate
                val startingPosition = agendaPagerAdapter.startingPosition
                val selectedDayOffset = ChronoUnit.DAYS.between(startingDate, selectedDate).toInt()
                val agendaIndex = startingPosition + selectedDayOffset
                if (binding?.agendaPager?.currentItem != agendaIndex) {
                    binding?.agendaPager?.post {
                        if (isResumed) { // TODO Temporary fix for NPE in getViewBinding
                            binding?.agendaPager?.setCurrentItem(agendaIndex, false)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            calendarViewModel.fetchingState.collect {
                when (it) {
                    CalendarsRepository.FetchingState.NotNeeded -> {
                        calendarViewModel.setLoading(false)
                    }
                    CalendarsRepository.FetchingState.Fetching -> {
                        calendarViewModel.setLoading(true)
                    }
                    CalendarsRepository.FetchingState.Finished -> {
                        calendarViewModel.setLoading(false)
                    }
                }
            }
        }

        lifecycleScope.launchWhenStarted {

            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {

                // TODO schedule this from some global periodic scheduler
                withContext(Dispatchers.Default) {
                    handleAlarmsUseCase.execute(userId)
                }

                // TODO schedule repeating worker job
                mainViewModel.syncAlarms(userId).observe(viewLifecycleOwner) {
                    if (it is Operation.State.IN_PROGRESS) {
                        calendarViewModel.setLoading(true)
                    } else {
                        calendarViewModel.setLoading(false)
                    }
                }

                mainViewModel.fetchUserSettings(userId = userId)
            }

        }

        val headerDaysMediator = MediatorLiveData<Pair<DayOfWeek, String>>()
        // Setup header with week's days
        headerDaysMediator.addSource(calendarViewModel.timeZoneId) { zoneId ->
            timeZoneId = zoneId?.id

            setToolbarListeners(zoneId)

            // Update week view time zone if necessary
            if (binding.weekView.customTimeZone != TimeZone.getTimeZone(zoneId)) {
                binding.weekView.customTimeZone = TimeZone.getTimeZone(zoneId)
                lifecycleScope.launch {
                    val weekStart = calendarViewModel.getWeekStart()
                    val firstDayOfWeek = calendarViewModel.selectedDateTime.value?.first?.firstDayOfWeek(weekStart) ?: return@launch
                    val fromDate = firstDayOfWeek.minusDays(WEEK_VIEW_PAST_DAYS_TO_LOAD)
                    val toDate = firstDayOfWeek.plusDays(WEEK_VIEW_FUTURE_DAYS_TO_LOAD)

                    getEvents(fromDate, toDate, zoneId?.id ?: return@launch)
                }
            }

            if (timeZoneId != null && startWeekOn != null) {
                headerDaysMediator.value = Pair(startWeekOn!!, timeZoneId!!)
            }
        }
        headerDaysMediator.addSource(calendarViewModel.weekStart) { weekStart ->

            weekStart?.let {
                setupMonthLayoutGestures(weekStart)
                calendarViewModel.selectedDateTime.value?.let {
                    updateWeekView(it.first)
                }
            }

            if (startWeekOn == null && weekStart != null) {
                if (calendarViewModel.viewMode.value == ViewMode.AGENDA) {
                    calendarViewModel.monthView.value = true
                    simulateExpandWithScroll(getWeekStartDayOfWeek(weekStart))
                } else {
                    calendarViewModel.monthView.value = false
                    simulateCollapseWithScroll(getWeekStartDayOfWeek(weekStart))
                }
            }

            startWeekOn = weekStart?.let { getWeekStartDayOfWeek(it) }

            startWeekOn?.let { startWeekOn ->
                calendarViewModel.selectedDateTime.value?.first?.weekNumber(startWeekOn)?.let { weekNumber ->
                    binding.weekView.weekNumber = weekNumber
                }
            }

            if (timeZoneId != null && startWeekOn != null) {
                headerDaysMediator.value = Pair(startWeekOn!!, timeZoneId!!)
            }
        }
        headerDaysMediator.observe(viewLifecycleOwner) {
            it?.let { setHeaderDaysContent(it.first, it.second) }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            val layoutParams = binding.miniCalendarDaysHeaderLayout.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.marginStart = requireContext().resources.getDimensionPixelSize(R.dimen.mini_calendar_margin) +
                    if (displayWeekNumber) {
                        requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_width) +
                                requireContext().resources.getDimensionPixelSize(R.dimen.calendar_week_number_spacing_start)
                    } else 0
            binding.miniCalendarDaysHeaderLayout.layoutParams = layoutParams

            binding.weekView.showWeekNumber = displayWeekNumber
        }

        calendarViewModel.monthView.observe(viewLifecycleOwner) { monthView ->

            // Addresses CALAND-2905, once we're confident it does not introduce side effects, remove the FF
            // and update the logic.
            if (featureFlagViewModel.isSplitViewVerticalScrollingEnabled()) {
                binding.fragmentMonthLayout.allowScrolling = true
                binding.weekView.isHorizontalScrollingEnabled = !monthView
            } else {
                binding.fragmentMonthLayout.allowScrolling = !monthView ||
                        calendarViewModel.viewMode.value == ViewMode.AGENDA ||
                        calendarViewModel.viewMode.value == ViewMode.MONTH
            }
        }

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
            val hasActiveWritableCalendars = userCalendars.any { it.isActive && it.allowEditEvents }
            val createImageButton = buttonCreate.findViewById<ImageButton>(R.id.imageButton)
            if (hasActiveWritableCalendars) {
                createImageButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_norm))
                createImageButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_button_oval)
            } else {
                createImageButton.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.icon_disabled))
                createImageButton.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_action_button_disabled_oval)
            }
        }

        featureFlagViewModel.eventSearchFeatureFlag.observe(viewLifecycleOwner) { eventSearchFeatureFlag ->
            eventSearchFeatureFlag ?: return@observe
            buttonSearch.visibleOrGone(eventSearchFeatureFlag)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                currentRange.filterNotNull().flatMapLatest { range ->
                    calendarViewModel.getUiEventsLookupFlow(range.fromDate, range.toDate, range.timeZoneId)
                }.collectLatest {
                    updateUiEvents(it)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                weekViewEvents.collectLatest { weekViewCalendarEntities ->
                    weekViewAdapter.submitList(weekViewCalendarEntities)
                }
            }
        }

        weekViewAdapter = WeekViewAdapter(
            dragHandler = { _, _, _ ->
                // TODO DRAG
            },
            loadMoreHandler = { _ ->
            },
            rangeChangedHandler = { firstVisibleDate, _ ->
                if (calendarViewModel.viewMode.value == ViewMode.WEEK) {
                    // For week view we need to use set date as first day of the week so that we stick to user week start choice
                    lifecycleScope.launch {
                        val selectedDate = calendarViewModel.selectedDateTime.value?.first
                        calendarViewModel.getWeekStart()?.let { weekStart ->
                            val firstDayOfWeek = selectedDate?.firstDayOfWeek(weekStart)
                            if (firstDayOfWeek != firstVisibleDate && !initWeekView) {
                                val verticalScrollOffset = binding.weekView.verticalScrollOffset
                                val hourHeight = binding.weekView.hourHeight
                                if (verticalScrollOffset != null && hourHeight != null) {
                                    val hour = (verticalScrollOffset / hourHeight).toInt()
                                    val minute = (((verticalScrollOffset / hourHeight) - hour) * 60).toInt()
                                    calendarViewModel.handleDaySelected(firstVisibleDate, LocalTime.of(
                                        if (hour < 0) 0 else if (hour > 23) 23 else hour,
                                        if (minute < 0) 0 else if (minute > 59) 59 else minute)
                                    )
                                } else {
                                    // If week view was null and we failed to get verticalScrollOffset & hourHeight
                                    //  we still set selected day so that week view and mini calendar are in sync
                                    //  but we ignore setting the LocalTime
                                    calendarViewModel.handleDaySelected(
                                        firstVisibleDate,
                                        calendarViewModel.selectedDateTime.value?.second
                                    )
                                }
                            }
                            initWeekView = false
                        }
                    }
                } else {
                    if (firstVisibleDate != calendarViewModel.selectedDateTime.value?.first) {
                        val verticalScrollOffset = if (isResumed) binding.weekView.verticalScrollOffset else null // TODO ViewBinding NPE
                        val hourHeight = if (isResumed) binding.weekView.hourHeight else null // TODO ViewBinding NPE
                        if (verticalScrollOffset != null && hourHeight != null) {
                            val hour = (verticalScrollOffset / hourHeight).toInt()
                            val minute = ceil((((verticalScrollOffset / hourHeight) - hour) * 60)).toInt()
                            calendarViewModel.handleDaySelected(firstVisibleDate, LocalTime.of(
                                if (hour < 0) 0 else if (hour > 23) 23 else hour,
                                if (minute < 0) 0 else if (minute > 59) 59 else minute)
                            )
                        } else {
                            // If week view was null and we failed to get verticalScrollOffset & hourHeight
                            //  we still set selected day so that week view and mini calendar are in sync
                            //  but we ignore setting the LocalTime
                            calendarViewModel.handleDaySelected(
                                firstVisibleDate,
                                calendarViewModel.selectedDateTime.value?.second
                            )
                        }
                    }
                }
            },
            viewClickHandler = { startTime, isAllDay ->
                openCreateEventForm(isAllDay, startTime)
            },
            eventClickHandler = { weekViewEventClicked ->
                onWeekViewEventClick(weekViewEventClicked)
            },
            dateHeaderClickHandler = { dateClicked ->
                calendarViewModel.viewMode.value = ViewMode.DAY
                binding.weekView.scrollToDate(dateClicked)
            },
            onHourHeightChangedHandler = { newHourHeight ->
                mainViewModel.setWeekViewHourHeight(newHourHeight)
            }
        )
        binding.weekView.adapter = weekViewAdapter

        binding.weekView.hourHeight = mainViewModel.getWeekViewHourHeight(resources.getDimensionPixelSize(R.dimen.default_week_view_hour_height).toFloat()).roundToInt()

        binding.weekView.setWeekDayFormatter { date: LocalDate ->
            val weekdayFormatter = DateTimeFormatter.ofPattern(WEEK_VIEW_WEEKDAY_FORMATTER_PATTERN, getLocaleForFormatting())
            weekdayFormatter.format(date)
        }
        val dateFormatter = DateTimeFormatter.ofPattern(WEEK_VIEW_DATE_FORMATTER_PATTERN, getLocaleForFormatting())
        binding.weekView.setDateFormatter { date: LocalDate ->
            dateFormatter.format(date)
        }
    }

    private val rawUiEvents = MutableStateFlow<List<UiEvent>?>(null)
    private val weekViewEvents = rawUiEvents.filterNotNull().map { events ->
        events.flatMap { event ->
            event.toWeekViewCalendarEntityEvent(getString(R.string.default_event_summary))
        }
    }.distinctUntilChanged()

    private fun updateWeekView(selectedDate: LocalDate, selectedTime: LocalTime? = null, animate: Boolean = true) {
        lifecycleScope.launch {
            val weekStart = calendarViewModel.getWeekStart()
            val firstDayOfWeek = selectedDate.firstDayOfWeek(weekStart)
            firstDayOfWeek?.let {
                val fromDate = firstDayOfWeek.minusDays(WEEK_VIEW_PAST_DAYS_TO_LOAD)
                val toDate = firstDayOfWeek.plusDays(WEEK_VIEW_FUTURE_DAYS_TO_LOAD)
                val rangeChanged = (currentFromDate?.firstDayOfWeek(weekStart) != firstDayOfWeek && currentFromDate != fromDate && currentToDate != toDate)
                if (rangeChanged) {
                    val timeZoneId = calendarViewModel.getTimeZoneId()?.id
                    getEvents(fromDate, toDate, timeZoneId ?: return@launch)
                }
            }

            whenStarted { // TODO ViewBinding NPE
                if (calendarViewModel.viewMode.value == ViewMode.WEEK) {
                    // For week view we need to use set date as first day of the week so that we stick to user week start choice
                    lifecycleScope.launch {
                        weekStart?.let { weekStart ->
                            selectedDate.firstDayOfWeek(weekStart)?.let {
                                if (selectedTime != null && animate) binding.weekView.scrollToDateTime(it.atTime(selectedTime))
                                else if (selectedTime != null) binding.weekView.setDateTime(it.atTime(selectedTime))
                                else if (animate) binding.weekView.scrollToDate(it)
                                else binding.weekView.setDate(it)
                            }
                        }
                    }
                } else if (binding.weekView.firstVisibleDateAsLocalDate != selectedDate) {
                    if (selectedTime != null && animate) binding.weekView.scrollToDateTime(LocalDateTime.of(selectedDate, selectedTime))
                    else if (selectedTime != null) binding.weekView.setDateTime(LocalDateTime.of(selectedDate, selectedTime))
                    else if (animate) binding.weekView.scrollToDate(selectedDate)
                    else binding.weekView.setDate(selectedDate)
                } else if (selectedTime != null) {
                    binding.weekView.scrollToTime(selectedTime)
                } else {
                    // TODO Remove empty else when removing encapsulating whenStarted
                }
            }

            weekStart?.let {
                whenStarted { // TODO ViewBinding NPE
                    val startWeekOn = getWeekStartDayOfWeek(weekStart)
                    val weekNumber = selectedDate.weekNumber(startWeekOn)
                    if (weekNumber != binding.weekView.weekNumber) {
                        binding.weekView.weekNumber = selectedDate.weekNumber(startWeekOn)
                    }
                }
            }
        }
    }

    private fun getEvents(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String) {
        // Get and display decrypted events
        currentFromDate = fromDate
        currentToDate = toDate
        currentTimeZoneId = timeZoneId
        currentRange.update { EventsWindow(fromDate, toDate, timeZoneId) }
    }

    private fun updateUiEvents(events: CalendarsRepository.GetEventsResult<UiEvent>) {
        when (events) {
            CalendarsRepository.GetEventsResult.InProgress -> {
                binding.weekView.showLoadingEvents = true
                binding.calendarProgress.isVisible = true
            }

            is CalendarsRepository.GetEventsResult.Success -> {
                rawUiEvents.value = events.events

                binding.weekView.showLoadingEvents = events.events.isEmpty() && events.fullyLoaded.not()
                binding.calendarProgress.isVisible = events.fullyLoaded.not()
            }

            is CalendarsRepository.GetEventsResult.Exception -> {
                binding.weekView.showLoadingEvents = false
                binding.calendarProgress.isVisible = false
            }
        }
    }

    private fun openCreateEventForm(isAllDay: Boolean, startTime: LocalDateTime) {
        lifecycleScope.launch {
            val hasActiveWritableCalendars = calendarViewModel.getUserCalendars()?.any { it.isActive && it.allowEditEvents }
            if (hasActiveWritableCalendars == true) {
                val truncatedStartTime =
                    if (!isAllDay) LocalTime.of(startTime.hour, if (startTime.minute >= 30) 30 else 0)
                    else null
                requireActivity().findNavController(R.id.nav_host_fragment_container_view)
                    .navigate(
                        Navigation.Deeplink.toEventCreate(
                            startTime.toLocalDate(),
                            truncatedStartTime
                        )
                    )
            } else {
                requireActivity().displaySnackBar(resources.getString(R.string.snack_create_event_no_active_calendar))
            }
        }
    }

    private fun onWeekViewEventClick(weekViewEvent: WeekViewCalendarEntity.Event) {
        if (weekViewEvent.decrypted) {
            findNavController().navigate(
                Navigation.Deeplink.toEventDetails(
                    weekViewEvent.getActualEventId(),
                    weekViewEvent.occurrenceNumber ?: 0
                )
            )
        } else {
            lifecycleScope.launch {
                requireContext().displayEventDecryptionErrorDialog(
                    calendarViewModel.allowDeleteEvent(weekViewEvent.calendarId),
                    weekViewEvent.isRecurring
                ) { _, _ ->
                    lifecycleScope.launch {
                        val deleteResult = withContext(Dispatchers.Default) {
                            calendarViewModel.handleDeleteEvent(
                                weekViewEvent.getActualEventId(),
                                weekViewEvent.calendarId,
                                EventEditDeleteOption.ALL_EVENTS
                            )
                        }
                        if (deleteResult is UseCase.Result.Success<*>) {
                            requireActivity().displaySnackBar(getString(R.string.snack_event_deleted))
                        } else {
                            var userErrorMessage: String? = null
                            if (deleteResult is UseCase.Result.Error) {
                                logger.e("Error deleting event: ${deleteResult.message}")
                                userErrorMessage = deleteResult.userErrorMessage
                            } else if (deleteResult is UseCase.Result.InvalidParams) {
                                logger.e("InvalidParams deleting event: ${deleteResult.message}")
                                userErrorMessage = deleteResult.userErrorMessage
                            }
                            requireActivity().displaySnackBar(
                                if (userErrorMessage.isNullOrEmpty()) getString(R.string.snack_event_deleted_error)
                                else userErrorMessage
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupMonthLayoutGestures(weekStart: Int) {
        if (this::miniCalendarPageChangeCallback.isInitialized) binding.miniCalendarPager.unregisterOnPageChangeCallback(
            miniCalendarPageChangeCallback
        )

        val startWeekOn = getWeekStartDayOfWeek(weekStart)
        setMiniCalendarPageChangeCallback(startWeekOn)

        fragmentToolbarTitleLayout.setOnSingleClickListener {
            if (calendarViewModel.monthView.value == false) {
                simulateExpandWithScroll(startWeekOn)
            } else {
                simulateCollapseWithScroll(startWeekOn)
            }
        }

        // Calculate current month's desired height for both mini calendar mode (month / week)
        calendarViewModel.selectedDateTime.value?.first?.withDayOfMonth(1)?.let { firstDayOfMonth ->
            calendarViewModel.currentPosDesiredMonthHeight = calculateAdapterHeight(
                requireContext(),
                firstDayOfMonth,
                startWeekOn,
                true
            )
        }

        // Set custom listener for mini calendar gestures
        val onTouchListener = MonthLayoutGestureListener(
            requireContext(),
            calendarViewModel,
            binding.viewPagerTopGuideline,
            binding.viewPagerSliderGuideline,
            binding.weekView,
            object : MonthLayoutGestureListener.MonthLayoutOnFinishMoveListener {
                override fun fullyExpand(animationEndListener: () -> Unit) {
                    if (calendarViewModel.monthView.value == false) {
                        // Apply the changes for expanded state
                        calendarViewModel.monthView.value = true
                        if (currentViewMode != ViewMode.MONTH) AndroidUtils.rotateArrowUpward(miniCalendarChevron)
                        updateMiniCalendarHeight(
                            startWeekOn,
                            isMonthView = true,
                        )
                        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                    } else {
                        // Animate mini calendar to go back to expanded state
                        binding.viewPagerTopGuideline.animateGuidelineHeightChange(
                            calendarViewModel.currentPosDesiredMonthHeight,
                            calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                        binding.viewPagerSliderGuideline.animateGuidelineHeightChange(
                            calendarViewModel.currentPosDesiredMonthHeight - requireContext().resources.getDimensionPixelSize(
                                R.dimen.calendar_slider_height
                            ), calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                    }
                }

                override fun fullyCollapse(animationEndListener: () -> Unit) {
                    if (calendarViewModel.monthView.value == true) {
                        // Apply the changes for collapsed state
                        calendarViewModel.monthView.value = false
                        if (currentViewMode != ViewMode.MONTH) AndroidUtils.rotateArrowDownward(miniCalendarChevron)
                        updateMiniCalendarHeight(
                            startWeekOn,
                            isMonthView = false,
                        )
                        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                    } else {
                        // Animate mini calendar to go back to collapsed state
                        binding.viewPagerTopGuideline.animateGuidelineHeightChange(
                            resources.getDimensionPixelSize(R.dimen.calendar_slider_height),
                            calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                        binding.viewPagerSliderGuideline.animateGuidelineHeightChange(
                            resources.getDimensionPixelSize(R.dimen.calendar_slider_height) - requireContext().resources.getDimensionPixelSize(
                                R.dimen.calendar_slider_height
                            ), calendarViewModel.currentPosDesiredMonthHeight
                        ) {
                        }
                    }
                }

                override fun simulateCollapse(animationEndListener: () -> Unit) {
                    simulateCollapseWithScroll(startWeekOn)
                }

                override fun simulateExpand(animationEndListener: () -> Unit) {
                    simulateExpandWithScroll(startWeekOn)
                }

            }
        )

        binding.fragmentMonthLayout.agendaPager = binding.agendaPager
        binding.fragmentMonthLayout.weekView = binding.weekView

        binding.miniCalendarPager.registerOnPageChangeCallback(miniCalendarPageChangeCallback)

        // Addresses CALAND-2905, once we're confident it does not introduce side effects, remove the FF
        // and update the logic.
        featureFlagViewModel.splitViewVerticalScrollingFlag.observe(viewLifecycleOwner) { isSplitViewScrollingEnabled ->
            val listener = if (isSplitViewScrollingEnabled) null else onTouchListener
            binding.fragmentMonthLayout.setOnTouchListener(listener)
        }
    }

    private fun initAgendaPager(viewMode: ViewMode) {
        // Skip initAgendaPager if it was already done for specified viewMode
        if (viewMode == currentViewMode) return
        val previousViewMode = currentViewMode
        currentViewMode = viewMode

        if (viewMode == ViewMode.WEEK) {
            val selectedDate = calendarViewModel.selectedDateTime.value?.first
            val weekStart = calendarViewModel.weekStart.value
            initWeekView = true
            selectedDate?.firstDayOfWeek(weekStart)?.let {
                if (selectedDate == calendarViewModel.initialToday) {
                    calendarViewModel.handleDaySelected(selectedDate)
                }else {
                    calendarViewModel.handleDaySelected(it)
                }
                binding.weekView.scrollToDate(it)
            }
        } else if (previousViewMode == ViewMode.WEEK) {
            // We need to adjust the view when coming from week view because it sticks to week start
            val selectedDate = calendarViewModel.selectedDateTime.value?.first
            selectedDate?.let {
                calendarViewModel.handleDaySelected(it)
                binding.weekView.scrollToDate(it)
            }
        }

        if (binding.weekView.isVisible && (viewMode == ViewMode.DAY || viewMode == ViewMode.THREE_DAY || viewMode == ViewMode.WEEK)){
            miniCalendarChevron.clearAnimation()
            miniCalendarChevron.visibleOrGone(true)
            return
        }

        binding.fragmentMonthLayout.allowScrolling = viewMode == ViewMode.AGENDA

        if (viewMode == ViewMode.MONTH) {
            displayMonthCalendarPager()
        } else if (previousViewMode == null || previousViewMode == ViewMode.MONTH) {
            displayMiniCalendarPager()
        }

        when (viewMode) {
            ViewMode.MONTH -> {
                // Hide the agenda
                binding.agendaPager.visibleOrGone(false)
                binding.agendaPager.adapter = null
                binding.weekView.visibleOrGone(false)
                binding.miniCalendarDaysHeaderLayout.visibleOrGone(false)
                miniCalendarChevron.clearAnimation()
                miniCalendarChevron.visibleOrGone(false)

                // Update constraints so that month view can occupy entire space
                updateMiniCalendarConstraints(viewMode)
            }
            ViewMode.AGENDA -> {
                // Update the pager to use agenda adapter
                lifecycleScope.launch {
                    val weekStart = calendarViewModel.getWeekStart()
                    if (weekStart != null) {
                        whenStarted { // TODO ViewBinding NPE
                            calendarViewModel.monthView.value = true
                            simulateExpandWithScroll(getWeekStartDayOfWeek(weekStart))
                        }
                    }
                }
                binding.agendaPager.apply {
                    val currentItem =
                        calendarViewModel.selectedDateTime.value?.let { selectedDateTime ->
                            val startingDate = agendaPagerAdapter.startingDate
                            val startingPosition = agendaPagerAdapter.startingPosition
                            val selectedDayOffset = ChronoUnit.DAYS.between(startingDate, selectedDateTime.first).toInt()
                            startingPosition + selectedDayOffset
                        } ?: agendaPagerAdapter.startingPosition

                    adapter = agendaPagerAdapter
                    val item = if (currentItem > 0) currentItem else agendaPagerAdapter.startingPosition
                    setCurrentItem(item, false)
                    offscreenPageLimit = 1
                }
                // TODO Try and see if this is still needed
                // (binding.agendaPager.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = false
                // (binding.agendaPager.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(0) // Make sure we only keep 3 childs in cache

                // Display the agenda
                binding.agendaPager.visibleOrGone(true)
                binding.weekView.visibleOrGone(false)
                binding.miniCalendarDaysHeaderLayout.visibleOrGone(true)
                miniCalendarChevron.clearAnimation()
                miniCalendarChevron.visibleOrGone(true)

                // Update constraints so that the view is split between mini calendar and agenda view
                updateMiniCalendarConstraints(viewMode)
            }
            else -> {
                lifecycleScope.launch {
                    val weekStart = calendarViewModel.getWeekStart()
                    if (weekStart != null) {
                        calendarViewModel.monthView.value = false
                        simulateCollapseWithScroll(getWeekStartDayOfWeek(weekStart))
                    }

                    val timeZoneId = calendarViewModel.getTimeZoneId()
                    val selectedDate = calendarViewModel.selectedDateTime.value?.first
                    if (timeZoneId != null && (selectedDate == LocalDate.now() || (viewMode == ViewMode.WEEK && selectedDate?.firstDayOfWeek(weekStart) == LocalDate.now().firstDayOfWeek(weekStart)))) {
                        updateWeekView(LocalDate.now(timeZoneId), LocalTime.now(timeZoneId).getTimeWithPadding(), animate = false)
                        calendarViewModel.handleDaySelected(LocalDate.now(timeZoneId), LocalTime.now(timeZoneId).getTimeWithPadding())
                    } else if (selectedDate != null) {
                        val firstEventOfTheDayTime = calendarViewModel.firstEventOfTheDayTime
                        if (firstEventOfTheDayTime != null) {
                            updateWeekView(selectedDate, firstEventOfTheDayTime.getTimeWithPadding(), animate = false)
                            calendarViewModel.firstEventOfTheDayTime = null
                            calendarViewModel.handleDaySelected(selectedDate, firstEventOfTheDayTime.getTimeWithPadding())
                        } else {
                            updateWeekView(selectedDate, animate = false)
                        }
                    }
                }

                binding.agendaPager.visibleOrGone(false)
                binding.agendaPager.adapter = null
                binding.weekView.visibleOrGone(true)
                binding.miniCalendarDaysHeaderLayout.visibleOrGone(true)
                miniCalendarChevron.clearAnimation()
                miniCalendarChevron.visibleOrGone(true)

                // Update constraints so that the view is split between mini calendar and agenda / day views
                updateMiniCalendarConstraints(viewMode)
            }
        }
    }

    private fun updateMiniCalendarConstraints(viewMode: ViewMode) {
        if (viewMode == ViewMode.MONTH) {
            (binding.miniCalendarLayout.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarLayoutLayoutParams ->
                miniCalendarLayoutLayoutParams.height = ViewPager.LayoutParams.MATCH_PARENT
                binding.miniCalendarLayout.layoutParams = miniCalendarLayoutLayoutParams
            }
            (binding.miniCalendarPagerLayout.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutLayoutParams ->
                miniCalendarPagerLayoutLayoutParams.height = ViewPager.LayoutParams.MATCH_PARENT
                binding.miniCalendarPagerLayout.layoutParams = miniCalendarPagerLayoutLayoutParams
            }
            (binding.miniCalendarPager.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutParams ->
                miniCalendarPagerLayoutParams.height = ViewPager.LayoutParams.MATCH_PARENT
                binding.miniCalendarPager.layoutParams = miniCalendarPagerLayoutParams
            }
        } else {
            (binding.miniCalendarLayout.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarLayoutLayoutParams ->
                miniCalendarLayoutLayoutParams.height = ViewPager.LayoutParams.WRAP_CONTENT
                binding.miniCalendarLayout.layoutParams = miniCalendarLayoutLayoutParams
            }
            (binding.miniCalendarPagerLayout.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutLayoutParams ->
                miniCalendarPagerLayoutLayoutParams.height = 0
                binding.miniCalendarPagerLayout.layoutParams = miniCalendarPagerLayoutLayoutParams
            }
            (binding.miniCalendarPager.layoutParams as? ConstraintLayout.LayoutParams)?.let { miniCalendarPagerLayoutParams ->
                miniCalendarPagerLayoutParams.height = 0
                binding.miniCalendarPager.layoutParams = miniCalendarPagerLayoutParams
            }
        }
    }

    private fun displayMonthCalendarPager() {
        // Update the pager to use month adapter
        binding.miniCalendarPager.apply {
            val currentItem =
                calendarViewModel.selectedDateTime.value?.let { selectedDateTime ->
                    val startingDate = monthPagerAdapter.firstDayOfMonth
                    val startingPosition = monthPagerAdapter.startingPosition
                    val selectedDayOffset = ChronoUnit.MONTHS.between(startingDate, selectedDateTime.first.withDayOfMonth(1)).toInt()
                    startingPosition + selectedDayOffset
                } ?: monthPagerAdapter.startingPosition

            adapter = monthPagerAdapter
            offscreenPageLimit = 1

            val item = if (currentItem > 0) currentItem else monthPagerAdapter.startingPosition
            setCurrentItem(item, false)
        }
        // TODO Try and see if this is still needed
        (binding.miniCalendarPager.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = false
        (binding.miniCalendarPager.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(1) // We keep one view cached
    }

    private fun displayMiniCalendarPager() {
        // Update the pager to use mini calendar adapter
        binding.miniCalendarPager.apply {
            val currentItem =
                calendarViewModel.selectedDateTime.value?.let { selectedDateTime ->
                    val startingDate = miniCalendarPagerAdapter.firstDayOfMonth
                    val startingPosition = miniCalendarPagerAdapter.startingPosition
                    val selectedDayOffset = ChronoUnit.MONTHS.between(startingDate, selectedDateTime.first.withDayOfMonth(1)).toInt()
                    startingPosition + selectedDayOffset
                } ?: miniCalendarPagerAdapter.startingPosition

            adapter = miniCalendarPagerAdapter
            offscreenPageLimit = 1

            val item = if (currentItem > 0) currentItem else miniCalendarPagerAdapter.startingPosition
            setCurrentItem(item, false)
        }
        // TODO Try and see if this is still needed
        (binding.miniCalendarPager.getChildAt(0) as? RecyclerView)?.layoutManager?.isItemPrefetchEnabled = false
        (binding.miniCalendarPager.getChildAt(0) as? RecyclerView)?.setItemViewCacheSize(0) // Make sure we only keep 3 childs in cache
    }

    private fun setHeaderDaysContent(startWeekOn: DayOfWeek, timeZoneId: String) {

        // Mini calendar is hidden when displaying Month view
        if (currentViewMode == ViewMode.MONTH) return

        // Setup mini calendar week days header
        binding.miniCalendarDaysHeaderLayout.run {

            val today = LocalDate.now(ZoneId.of(timeZoneId))
            val selectedDate = calendarViewModel.selectedDateTime.value?.first
            var dayToHighlight: DayOfWeek? = null
            selectedDate?.let {
                // Calculate what day to highlight, depending on mini calendar mode
                val firstDayOfTheMonth = selectedDate.withDayOfMonth(1)
                val firstDayOfTheWeekNumber = firstDayOfTheMonth.dayOfWeek.value - startWeekOn.value
                val firstDayOfTheWeekOffset =
                    if (firstDayOfTheWeekNumber < 0) firstDayOfTheWeekNumber + DAYS_IN_A_WEEK else firstDayOfTheWeekNumber
                val miniCalendarFirstDay = firstDayOfTheMonth.minusDays(firstDayOfTheWeekOffset.toLong())
                val lastDayOfTheMonth = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth())
                val lastDayOfMonthOffset = DateTimeUtilsImpl.getLastWeekOfMonthOffset(startWeekOn, lastDayOfTheMonth)
                val miniCalendarLastDay = lastDayOfTheMonth.plusDays(lastDayOfMonthOffset.toLong())
                dayToHighlight =
                    if ((calendarViewModel.monthView.value == true && (selectedDate.month == today.month || (today.isAfter(
                            miniCalendarFirstDay
                        ) && today.isBefore(miniCalendarLastDay)))) ||
                        (calendarViewModel.monthView.value == false && selectedDate.weekNumber(startWeekOn) == today.weekNumber(
                            startWeekOn
                        ))
                    ) today.dayOfWeek
                    else null
            }

            this.removeAllViews()

            val weekDays = DayOfWeek.values().toList()
            Collections.rotate(
                weekDays,
                DAYS_IN_A_WEEK - (startWeekOn.value - 1)
            )
            weekDays.forEach {
                setDayHeaderItem(this, it, dayToHighlight == it)
            }
        }
    }

    private fun setDayHeaderItem(headerLayout: LinearLayout, dayOfWeek: DayOfWeek, highlight: Boolean) {
        val weekDayHeaderViewBinding = ItemMiniCalendarHeaderBinding.inflate(
            LayoutInflater.from(this.context),
            headerLayout,
            false
        )
        val textView = weekDayHeaderViewBinding.root
        textView.text = dayOfWeek.format(firstLetter = true)
        if (highlight) textView.setTextColor(requireContext().getColorFromAttr(R.attr.proton_text_accent))
        else textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_weak))
        headerLayout.addView(weekDayHeaderViewBinding.root)
    }

    private fun setToolbarMonthYearTitle(localDate: LocalDate, position: Int) {
        val month = SpannableString(localDate.formatMonth(true))
        val year = SpannableString(localDate.year.toString())
        if (LocalDate.now().year == localDate.year) toolbarTitle.text = "$month"
        else toolbarTitle.text = "$month $year"
    }

    private fun simulateExpandWithScroll(startWeekOn: DayOfWeek) {

        if (!isResumed) return // TODO ViewBinding NPE

        val desiredHeight = calendarViewModel.currentPosDesiredMonthHeight

        // Set mini calendar to expanded state
        calendarViewModel.monthView.value = true
        updateMiniCalendarHeight(
            startWeekOn,
            isMonthView = true,
        )
        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
        if (currentViewMode != ViewMode.MONTH) AndroidUtils.rotateArrowUpward(miniCalendarChevron)

        // Animate the guidelines to desired height
        binding.viewPagerTopGuideline.animateGuidelineHeightChange(
            desiredHeight,
            object : AndroidUtils.AnimateGuidelineListener {
                override fun onHeightChange(animatedValue: Int) {
                    if (!isResumed) return // TODO ViewBinding NPE
                    val viewPagerSliderGuidelineLayoutParams =
                        (binding.viewPagerSliderGuideline.layoutParams as? ConstraintLayout.LayoutParams)
                    if (viewPagerSliderGuidelineLayoutParams?.guideBegin == 0) {
                        // Update slider guide begin in case it was skipped somehow
                        viewPagerSliderGuidelineLayoutParams.guideBegin =
                            calendarViewModel.currentPosDesiredMonthHeight - requireContext().resources.getDimensionPixelSize(
                                R.dimen.calendar_slider_height
                            )
                        binding.viewPagerSliderGuideline.layoutParams = viewPagerSliderGuidelineLayoutParams
                    }
                }

                override fun onAnimationEnd() {
                }
            })
    }

    private fun simulateCollapseWithScroll(startWeekOn: DayOfWeek) {
        val desiredHeight = resources.getDimensionPixelSize(R.dimen.calendar_slider_height)

        if (calendarViewModel.monthView.value == false) {
            // Mini calendar should already be collapsed, skip animation
            updateMiniCalendarHeight(
                startWeekOn,
                isMonthView = false,
            )
            timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
        } else {
            if (currentViewMode != ViewMode.MONTH) AndroidUtils.rotateArrowDownward(miniCalendarChevron)

            // Animate mini calendar collapse
            binding.viewPagerTopGuideline.animateGuidelineHeightChange(
                desiredHeight,
                object : AndroidUtils.AnimateGuidelineListener {
                    override fun onHeightChange(animatedValue: Int) {
                    }

                    override fun onAnimationEnd() {
                        // Set mini calendar to collapsed state
                        calendarViewModel.monthView.value = false
                        updateMiniCalendarHeight(
                            startWeekOn,
                            isMonthView = false,
                        )
                        timeZoneId?.let { setHeaderDaysContent(startWeekOn, it) }
                    }
                })
        }
    }
}
