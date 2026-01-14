package com.alamkanak.weekview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class WeekView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val viewState: ViewState by lazy {
        ViewStateFactory.create(context, attrs)
    }

    private val eventsCacheProvider: EventsCacheProvider = { adapter?.eventsCache }
    private val eventChipsCacheProvider: EventChipsCacheProvider = { adapter?.eventChipsCache }

    private val touchHandler = WeekViewTouchHandler(viewState)

    private val navigationListener = object : Navigator.NavigationListener {
        override fun onHorizontalScrollPositionChanged() {
            invalidate()
        }

        override fun onHorizontalScrollingFinished() {
            notifyRangeChangedListener()
        }

        override fun onVerticalScrollPositionChanged(distance: Float) {
            notifyVerticalScrollChanged(distance)
            invalidate()
        }

        override fun onVerticalScrollingFinished() {
            notifyVerticalScrollFinished()
        }

        override fun requestInvalidation() {
            ViewCompat.postInvalidateOnAnimation(this@WeekView)
        }

        override fun onZoomFinished() {
            notifyHourHeightChanged()
        }
    }

    private val navigator = Navigator(viewState = viewState, listener = navigationListener)

    private val dragHandler = DragHandler(
        viewState = viewState,
        touchHandler = touchHandler,
        navigator = navigator,
        dragListener = { id -> adapter?.handleDragAndDrop(id) },
        eventsCacheProvider = eventsCacheProvider,
        eventsProcessorProvider = { adapter?.eventsProcessor },
    )

    private val gestureHandler = WeekViewGestureHandler(
        context = context,
        viewState = viewState,
        touchHandler = touchHandler,
        navigator = navigator,
        dragHandler = dragHandler,
    )

    private var accessibilityTouchHelper = WeekViewAccessibilityTouchHelper(
        view = this,
        viewState = viewState,
        touchHandler = touchHandler,
        eventChipsCacheProvider = eventChipsCacheProvider
    )

    private val renderers: List<Renderer> = listOf(
        TimeColumnRenderer(viewState),
        CalendarRenderer(viewState, eventChipsCacheProvider),
        HeaderRenderer(context, viewState, eventChipsCacheProvider, onHeaderHeightChanged = this::invalidate)
    )

    // We use width and height instead of view.isLaidOut(), because the latter seems to
    // struggle when used in a ViewPager.
    // See: https://github.com/thellmund/Android-Week-View/issues/214
    private val isWaitingToBeLaidOut: Boolean
        get() = width <= 0 || height <= 0

    init {
        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        val isAccessibilityEnabled = accessibilityManager.isEnabled
        val isExploreByTouchEnabled = accessibilityManager.isTouchExplorationEnabled

        val isAccessibilityHelperActive = isAccessibilityEnabled && isExploreByTouchEnabled
        if (isAccessibilityHelperActive) {
            ViewCompat.setAccessibilityDelegate(this, accessibilityTouchHelper)
        }

        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        viewState.onConfigurationChanged(newConfig)
        super.onConfigurationChanged(newConfig)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        performPendingScroll()
        updateViewState()
        refreshEvents()
        performRendering(canvas)
    }

    private fun performPendingScroll() {
        val pendingScroll = viewState.pendingScroll
        viewState.pendingScroll = null
        pendingScroll?.let { dateTime ->
            scrollToDateTime(dateTime)
        }
    }

    private fun updateViewState() {
        viewState.update(navigationListener = navigationListener)
    }

    private fun refreshEvents() {
        if (isInEditMode) {
            return
        }

        val pagingAdapter = adapter as? PagingAdapter
        pagingAdapter?.dispatchLoadRequest()
    }

    private fun performRendering(canvas: Canvas) {
        for (renderer in renderers) {
            renderer.render(canvas)
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        return superState?.let {
            SavedState(it, viewState.numberOfVisibleDays, viewState.firstVisibleDate)
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        val savedState = state as SavedState
        super.onRestoreInstanceState(savedState.superState)

        if (viewState.restoreNumberOfVisibleDays) {
            viewState.numberOfVisibleDays = savedState.numberOfVisibleDays
        }

        scrollToDate(savedState.firstVisibleDate)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        viewState.onSizeChanged(width, height)
        renderers.forEach { it.onSizeChanged(width, height) }
    }

    private fun updateDateRange(): List<Calendar> {
        val daysScrolled = viewState.currentOrigin.x / viewState.dayWidth
        val delta = daysScrolled.roundToInt() * (-1)

        val firstVisibleDate = if (viewState.isLtr) {
            today().plusDays(delta)
        } else {
            today().minusDays(delta)
        }

        val dateRange = viewState.createDateRange(firstVisibleDate)
        return dateRange.validate(viewState = viewState)
    }

    private fun notifyRangeChangedListener() {
        val currentFirstVisibleDate = viewState.firstVisibleDate
        val newDateRange = updateDateRange()
        val newFirstVisibleDate = newDateRange.first()

        val didFirstVisibleDateChange = !currentFirstVisibleDate.isSameDate(newFirstVisibleDate)
        viewState.firstVisibleDate = newFirstVisibleDate

        if (didFirstVisibleDateChange) {
            val newLastVisibleDate = newDateRange.last()
            adapter?.onRangeChanged(
                firstVisibleDate = newFirstVisibleDate,
                lastVisibleDate = newLastVisibleDate
            )
        }
    }

    private fun notifyHourHeightChanged() {
        adapter?.onHourHeightChanged(viewState.hourHeight)
    }

    private fun notifyVerticalScrollChanged(distance: Float) {
        if (abs(distance) >= 1f) {
            adapter?.onVerticalScrollPositionChanged(
                currentOffset = verticalScrollOffset,
                distance = distance
            )
        }
    }

    private fun notifyVerticalScrollFinished() {
        adapter?.onVerticalScrollFinished(currentOffset = verticalScrollOffset)
    }

    /*
     ***********************************************************************************************
     *
     *   Calendar configuration
     *
     ***********************************************************************************************
     */

    /**
     * Returns the number of visible days.
     */
    @PublicApi
    var numberOfVisibleDays: Int
        get() = viewState.numberOfVisibleDays
        set(value) {
            val currentFirstVisibleDate = viewState.firstVisibleDate
            viewState.numberOfVisibleDays = value

            renderers.filterIsInstance(DateFormatterDependent::class.java).forEach {
                it.onDateFormatterChanged(viewState.dateFormatter)
            }

            val newOrigin = viewState.getXOriginForDate(currentFirstVisibleDate)
            viewState.currentOrigin.x = newOrigin
            invalidate()
        }

    /**
     * Returns whether the first day of the week should be displayed at the start position
     * when WeekView is rendered for the first time.
     */
    @Deprecated(
        message = "Use stickToWeekInWeekView instead.",
        replaceWith = ReplaceWith(expression = "stickToWeekInWeekView"),
        level = DeprecationLevel.ERROR,
    )
    @PublicApi
    var showFirstDayOfWeekFirst: Boolean
        get() = viewState.showFirstDayOfWeekFirst
        set(value) {
            viewState.showFirstDayOfWeekFirst = value
        }

    /**
     * Returns whether weeks should always be scrolled as a whole when showing a week view, meaning
     * that the [firstVisibleDate] will actually be the first date of the currently displayed week
     * (as per [Calendar.getFirstDayOfWeek]). If not, any day of the week can be the
     * [firstVisibleDate].
     */
    @PublicApi
    var stickToActualWeek: Boolean
        get() = viewState.stickToActualWeek
        set(value) {
            viewState.stickToActualWeek = value
        }

    /**
     * Returns whether all-day events are arranged vertically. If false, all-day events are shown
     * in a horizontal arrangement, occupying only a single row.
     */
    @PublicApi
    var arrangeAllDayEventsVertically: Boolean
        get() = viewState.arrangeAllDayEventsVertically
        set(value) {
            viewState.arrangeAllDayEventsVertically = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Header bottom line
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether a horizontal line should be displayed at the bottom of the header row.
     */
    @PublicApi
    var showHeaderBottomLine: Boolean
        get() = viewState.showHeaderBottomLine
        set(value) {
            viewState.showHeaderBottomLine = value
            invalidate()
        }

    /**
     * Returns the color of the horizontal line at the bottom of the header row.
     */
    @PublicApi
    var headerBottomLineColor: Int
        get() = viewState.headerBottomLinePaint.color
        set(value) {
            viewState.headerBottomLinePaint.color = value
            invalidate()
        }

    /**
     * Returns the stroke width of the horizontal line at the bottom of the header row.
     */
    @PublicApi
    var headerBottomLineWidth: Int
        get() = viewState.headerBottomLinePaint.strokeWidth.toInt()
        set(value) {
            viewState.headerBottomLinePaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Header bottom shadow
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether a shadow should be displayed at the bottom of the header row.
     */
    @PublicApi
    var showHeaderBottomShadow: Boolean
        get() = viewState.showHeaderBottomShadow
        set(value) {
            viewState.showHeaderBottomShadow = value
            invalidate()
        }

    /**
     * Returns the color of the shadow at the bottom of the header row.
     */
    @PublicApi
    var headerBottomShadowColor: Int
        @RequiresApi(api = 29)
        get() = viewState.headerBackgroundWithShadowPaint.shadowLayerColor
        @RequiresApi(api = 29)
        set(value) {
            val paint = viewState.headerBackgroundWithShadowPaint
            paint.setShadowLayer(headerBottomShadowRadius.toFloat(), 0f, 0f, value)
            invalidate()
        }

    /**
     * Returns the radius of the shadow at the bottom of the header row.
     */
    @PublicApi
    var headerBottomShadowRadius: Int
        @RequiresApi(api = 29)
        get() = viewState.headerBackgroundWithShadowPaint.shadowLayerRadius.roundToInt()
        @RequiresApi(api = 29)
        set(value) {
            val paint = viewState.headerBackgroundWithShadowPaint
            paint.setShadowLayer(value.toFloat(), 0f, 0f, headerBottomShadowColor)
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time column
     *
     ***********************************************************************************************
     */

    /**
     * Returns the padding in the time column to the left and right side of the time label.
     */
    @PublicApi
    var timeColumnPadding: Int
        get() = viewState.timeColumnPadding
        set(value) {
            viewState.timeColumnPadding = value
            invalidate()
        }

    /**
     * Returns the text color of the labels in the time column.
     */
    @PublicApi
    var timeColumnTextColor: Int
        get() = viewState.timeColumnTextPaint.color
        set(value) {
            viewState.timeColumnTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the background color of the time column.
     */
    @PublicApi
    var timeColumnBackgroundColor: Int
        get() = viewState.timeColumnBackgroundPaint.color
        set(value) {
            viewState.timeColumnBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the text size of the labels in the time column.
     */
    @PublicApi
    var timeColumnTextSize: Int
        get() = viewState.timeColumnTextPaint.textSize.roundToInt()
        set(value) {
            viewState.timeColumnTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether a horizontal line is displayed between each hour in the time column.
     */
    @PublicApi
    var showTimeColumnHourSeparators: Boolean
        get() = viewState.showTimeColumnHourSeparators
        set(value) {
            viewState.showTimeColumnHourSeparators = value
            invalidate()
        }

    /**
     * Returns whether a horizontal line is displayed on each hour in the time column.
     */
    @PublicApi
    var showCustomTimeColumnHourSeparators: Boolean
        get() = viewState.showCustomTimeColumnHourSeparators
        set(value) {
            viewState.showCustomTimeColumnHourSeparators = value
            invalidate()
        }

    /**
     * Returns the hour separator width
     */
    @PublicApi
    var customTimeColumnHourSeparatorWidth: Float
        get() = viewState.customTimeColumnHourSeparatorWidth
        set(value) {
            viewState.customTimeColumnHourSeparatorWidth = value
            invalidate()
        }

    /**
     * Returns the interval in which time labels are displayed in the time column.
     */
    @PublicApi
    var timeColumnHoursInterval: Int
        get() = viewState.timeColumnHoursInterval
        set(value) {
            viewState.timeColumnHoursInterval = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time column separator
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether a vertical line is displayed between the time column and the calendar grid.
     */
    @PublicApi
    var showTimeColumnSeparator: Boolean
        get() = viewState.showTimeColumnSeparator
        set(value) {
            viewState.showTimeColumnSeparator = value
            invalidate()
        }

    /**
     * Returns the color of the time column separator.
     */
    @PublicApi
    var timeColumnSeparatorColor: Int
        get() = viewState.timeColumnSeparatorPaint.color
        set(value) {
            viewState.timeColumnSeparatorPaint.color = value
            invalidate()
        }

    /**
     * Returns the stroke width of the time column separator.
     */
    @PublicApi
    var timeColumnSeparatorWidth: Int
        get() = viewState.timeColumnSeparatorPaint.strokeWidth.roundToInt()
        set(value) {
            viewState.timeColumnSeparatorPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Header row
     *
     ***********************************************************************************************
     */

    /**
     * Returns the header row padding, which is applied above and below the all-day event chips.
     */
    @PublicApi
    var headerPadding: Int
        get() = viewState.headerPadding.roundToInt()
        set(value) {
            viewState.headerPadding = value.toFloat()
            invalidate()
        }

    /**
     * Returns the header row background color.
     */
    @PublicApi
    var headerBackgroundColor: Int
        get() = viewState.headerBackgroundPaint.color
        set(value) {
            viewState.headerBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the text color used for all date labels except today.
     */
    @PublicApi
    var headerTextColor: Int
        get() = viewState.headerTextPaint.color
        set(value) {
            viewState.headerTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the text color used for today's date label.
     */
    @PublicApi
    var todayHeaderTextColor: Int
        get() = viewState.todayHeaderTextPaint.color
        set(value) {
            viewState.todayHeaderTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the text color used for weekend date labels.
     */
    @PublicApi
    var weekendHeaderTextColor: Int
        get() = viewState.weekendHeaderTextPaint.color
        set(value) {
            viewState.weekendHeaderTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the text size of all date labels.
     */
    @PublicApi
    var headerTextSize: Int
        get() = viewState.headerTextPaint.textSize.roundToInt()
        set(value) {
            viewState.headerTextPaint.textSize = value.toFloat()
            viewState.todayHeaderTextPaint.textSize = value.toFloat()
            viewState.weekendHeaderTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether we show arrow down in header.
     */
    @PublicApi
    var showHeaderDownArrow: Boolean
        get() = viewState.showHeaderDownArrow
        set(value) {
            viewState.showHeaderDownArrow = value
            invalidate()
        }

    /**
     * Returns whether we show the loading events text in header.
     */
    @PublicApi
    var showLoadingEvents: Boolean
        get() = viewState.showLoadingEvents
        set(value) {
            viewState.showLoadingEvents = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Week number
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether the current week number is displayed in the header.
     */
    @PublicApi
    var weekNumber: Int
        get() = viewState.weekNumber
        set(value) {
            viewState.weekNumber = value
            invalidate()
        }

    /**
     * Returns whether the current week number is displayed in the header.
     */
    @PublicApi
    var showWeekNumber: Boolean
        get() = viewState.showWeekNumber
        set(value) {
            viewState.showWeekNumber = value
            invalidate()
        }

    /**
     * Returns the text color of the week number.
     */
    @PublicApi
    var weekNumberTextColor: Int
        get() = viewState.weekNumberTextPaint.color
        set(value) {
            viewState.weekNumberTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the text size of the week number.
     */
    @PublicApi
    var weekNumberTextSize: Int
        get() = viewState.weekNumberTextPaint.textSize.toInt()
        set(value) {
            viewState.weekNumberTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns the color of the week number's background.
     */
    @PublicApi
    var weekNumberBackgroundColor: Int
        get() = viewState.weekNumberBackgroundPaint.color
        set(value) {
            viewState.weekNumberTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the corner radius of the week number's background.
     */
    @PublicApi
    var weekNumberBackgroundCornerRadius: Int
        get() = viewState.weekNumberBackgroundCornerRadius.roundToInt()
        set(value) {
            viewState.weekNumberBackgroundCornerRadius = value.toFloat()
            invalidate()
        }

    /**
     * Returns the size the week number's background.
     */
    @PublicApi
    var weekNumberBackgroundSize: Int
        get() = viewState.weekNumberBackgroundSize.roundToInt()
        set(value) {
            viewState.weekNumberBackgroundSize = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Event chips
     *
     ***********************************************************************************************
     */

    /**
     * Returns the corner radius of an [EventChip].
     */
    @PublicApi
    var eventCornerRadius: Int
        get() = viewState.eventCornerRadius
        set(value) {
            viewState.eventCornerRadius = value
            invalidate()
        }

    /**
     * Returns the text size of a single-event [EventChip].
     */
    @PublicApi
    var eventTextSize: Int
        get() = viewState.eventTextPaint.textSize.roundToInt()
        set(value) {
            viewState.eventTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether the text size of the [EventChip] is adapting to the [EventChip] height.
     */
    @PublicApi
    var isAdaptiveEventTextSize: Boolean
        get() = viewState.adaptiveEventTextSize
        set(value) {
            viewState.adaptiveEventTextSize = value
            invalidate()
        }

    /**
     * Returns the text size of an all-day [EventChip].
     */
    @PublicApi
    var allDayEventTextSize: Int
        get() = viewState.allDayEventTextPaint.textSize.roundToInt()
        set(value) {
            viewState.allDayEventTextPaint.textSize = value.toFloat()
            invalidate()
        }

    /**
     * Returns the default text color of an [EventChip].
     */
    @PublicApi
    var defaultEventTextColor: Int
        get() = viewState.eventTextPaint.color
        set(value) {
            viewState.eventTextPaint.color = value
            invalidate()
        }

    /**
     * Returns the horizontal padding within an [EventChip].
     */
    @PublicApi
    var eventPaddingHorizontal: Int
        get() = viewState.eventPaddingHorizontal
        set(value) {
            viewState.eventPaddingHorizontal = value
            invalidate()
        }

    /**
     * Returns the vertical padding within an [EventChip].
     */
    @PublicApi
    var eventPaddingVertical: Int
        get() = viewState.eventPaddingVertical
        set(value) {
            viewState.eventPaddingVertical = value
            invalidate()
        }

    /**
     * Returns the default background color of an [EventChip].
     */
    @PublicApi
    var defaultEventColor: Int
        get() = viewState.defaultEventColor
        set(value) {
            viewState.defaultEventColor = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Event margins
     *
     ***********************************************************************************************
     */

    /**
     * Returns the column gap at the end of each day.
     */
    @PublicApi
    var columnGap: Int
        get() = viewState.columnGap
        set(value) {
            viewState.columnGap = value
            invalidate()
        }

    /**
     * Returns the horizontal gap between overlapping [EventChip]s.
     */
    @PublicApi
    var overlappingEventGap: Int
        get() = viewState.overlappingEventGap
        set(value) {
            viewState.overlappingEventGap = value
            invalidate()
        }

    /**
     * Returns the vertical margin of an [EventChip].
     */
    @PublicApi
    var eventMarginVertical: Int
        get() = viewState.eventMarginVertical
        set(value) {
            viewState.eventMarginVertical = value
            invalidate()
        }

    /**
     * Returns the horizontal padding used in single-day view.
     */
    @PublicApi
    var singleDayHorizontalPadding: Int
        get() = viewState.singleDayHorizontalPadding
        set(value) {
            viewState.singleDayHorizontalPadding = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Colors
     *
     ***********************************************************************************************
     */

    /**
     * Returns the background color of a day.
     */
    @PublicApi
    var dayBackgroundColor: Int
        get() = viewState.dayBackgroundPaint.color
        set(value) {
            viewState.dayBackgroundPaint.color = value
            invalidate()
        }

    /**
     * Returns the background color of the current date. If not explicitly set, WeekView will use
     * [dayBackgroundColor].
     */
    @PublicApi
    var todayBackgroundColor: Int
        get() = viewState.todayBackgroundPaint?.color ?: dayBackgroundColor
        set(value) {
            viewState.todayBackgroundPaint = value.toPaint()
            invalidate()
        }

    /**
     * Returns the background color for past dates. If not explicitly set, WeekView will use
     * [dayBackgroundColor].
     */
    @PublicApi
    var pastBackgroundColor: Int
        get() = viewState.pastBackgroundPaint?.color ?: dayBackgroundColor
        set(value) {
            viewState.pastBackgroundPaint = value.toPaint()
            invalidate()
        }

    /**
     * Returns the background color for past weekend dates. If not explicitly set, WeekView will use
     * [dayBackgroundColor].
     */
    @PublicApi
    var pastWeekendBackgroundColor: Int
        get() = viewState.pastWeekendBackgroundPaint?.color ?: dayBackgroundColor
        set(value) {
            viewState.pastWeekendBackgroundPaint = value.toPaint()
            invalidate()
        }

    /**
     * Returns the background color for future dates. If not explicitly set, WeekView will use
     * [dayBackgroundColor].
     */
    @PublicApi
    var futureBackgroundColor: Int
        get() = viewState.futureBackgroundPaint?.color ?: dayBackgroundColor
        set(value) {
            viewState.futureBackgroundPaint = value.toPaint()
            invalidate()
        }

    /**
     * Returns the background color for future weekend dates. If not explicitly set, WeekView will
     * use [dayBackgroundColor].
     */
    @PublicApi
    var futureWeekendBackgroundColor: Int
        get() = viewState.futureWeekendBackgroundPaint?.color ?: dayBackgroundColor
        set(value) {
            viewState.futureWeekendBackgroundPaint = value.toPaint()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Hour height
     *
     ***********************************************************************************************
     */

    /**
     * Returns the current height of an hour.
     */
    @PublicApi
    var hourHeight: Int
        get() = viewState.hourHeight.roundToInt()
        set(value) {
            viewState.newHourHeight = value.toFloat()
            invalidate()
        }

    /**
     * Returns the minimum height of an hour.
     */
    @PublicApi
    var minHourHeight: Int
        get() = viewState.minHourHeight.roundToInt()
        set(value) {
            viewState.minHourHeight = value.toFloat()
            invalidate()
        }

    /**
     * Returns the maximum height of an hour.
     */
    @PublicApi
    var maxHourHeight: Int
        get() = viewState.maxHourHeight.roundToInt()
        set(value) {
            viewState.maxHourHeight = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether the complete day should be shown, in which case [hourHeight] automatically
     * adjusts to accommodate all hours between [minHour] and [maxHour].
     */
    @PublicApi
    var showCompleteDay: Boolean
        get() = viewState.showCompleteDay
        set(value) {
            viewState.showCompleteDay = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Now line
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether a horizontal line should be displayed at the current time.
     */
    @PublicApi
    var showNowLine: Boolean
        get() = viewState.showNowLine
        set(value) {
            viewState.showNowLine = value
            invalidate()
        }

    /**
     * Returns the color of the horizontal "now" line.
     */
    @PublicApi
    var nowLineColor: Int
        get() = viewState.nowLinePaint.color
        set(value) {
            viewState.nowLinePaint.color = value
            invalidate()
        }

    /**
     * Returns the stroke width of the horizontal "now" line.
     */
    @PublicApi
    var nowLineStrokeWidth: Int
        get() = viewState.nowLinePaint.strokeWidth.roundToInt()
        set(value) {
            viewState.nowLinePaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /**
     * Returns whether a dot at the start of the "now" line is displayed. The dot is only displayed
     * if [showNowLine] is set to true.
     */
    @PublicApi
    var showNowLineDot: Boolean
        get() = viewState.showNowLineDot
        set(value) {
            viewState.showNowLineDot = value
            invalidate()
        }

    /**
     * Returns the color of the dot at the start of the "now" line.
     */
    @PublicApi
    var nowLineDotColor: Int
        get() = viewState.nowDotPaint.color
        set(value) {
            viewState.nowDotPaint.color = value
            invalidate()
        }

    /**
     * Returns the radius of the dot at the start of the "now" line.
     */
    @PublicApi
    var nowLineDotRadius: Int
        get() = viewState.nowDotPaint.strokeWidth.roundToInt()
        set(value) {
            viewState.nowDotPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Hour separators
     *
     ***********************************************************************************************
     */

    @PublicApi
    var showHourSeparators: Boolean
        get() = viewState.showHourSeparators
        set(value) {
            viewState.showHourSeparators = value
            invalidate()
        }

    @PublicApi
    var hourSeparatorColor: Int
        get() = viewState.hourSeparatorPaint.color
        set(value) {
            viewState.hourSeparatorPaint.color = value
            invalidate()
        }

    @PublicApi
    var hourSeparatorStrokeWidth: Int
        get() = viewState.hourSeparatorPaint.strokeWidth.roundToInt()
        set(value) {
            viewState.hourSeparatorPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Day separators
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether vertical lines are displayed as separators between dates.
     */
    @PublicApi
    var showDaySeparators: Boolean
        get() = viewState.showDaySeparators
        set(value) {
            viewState.showDaySeparators = value
            invalidate()
        }

    /**
     * Returns the color of the separators between dates.
     */
    @PublicApi
    var daySeparatorColor: Int
        get() = viewState.daySeparatorPaint.color
        set(value) {
            viewState.daySeparatorPaint.color = value
            invalidate()
        }

    /**
     * Returns the stroke color of the separators between dates.
     */
    @PublicApi
    var daySeparatorStrokeWidth: Int
        get() = viewState.daySeparatorPaint.strokeWidth.roundToInt()
        set(value) {
            viewState.daySeparatorPaint.strokeWidth = value.toFloat()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time zone
     *
     ***********************************************************************************************
     */

    /**
     * Returns the time zone that [WeekView] will be displayed in
     */
    @PublicApi
    var customTimeZone: TimeZone
        get() = viewState.customTimeZone
        set(value) {
            viewState.customTimeZone = value
            viewState.renderCurrentTime()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time format
     *
     ***********************************************************************************************
     */

    /**
     * Returns the time format that [WeekView] will be displayed in
     */
    @PublicApi
    var timeFormatIs24Hour: Boolean
        get() = viewState.timeFormatIs24Hour
        set(value) {
            viewState.timeFormatIs24Hour = value
            viewState.renderCurrentTime()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Date range
     *
     ***********************************************************************************************
     */

    /**
     * Returns the minimum date that [WeekView] will display, or null if none is set. Events before
     * this date will not be shown.
     */
    @PublicApi
    var minDate: Calendar?
        get() = viewState.minDate?.copy()
        set(value) {
            val maxDate = viewState.maxDate
            if (maxDate != null && value != null && value.isAfter(maxDate)) {
                throw IllegalArgumentException("Can't set a minDate that's after maxDate")
            }

            viewState.minDate = value?.copy()
            invalidate()
        }

    /**
     * Returns the maximum date that [WeekView] will display, or null if none is set. Events after
     * this date will not be shown.
     */
    @PublicApi
    var maxDate: Calendar?
        get() = viewState.maxDate?.copy()
        set(value) {
            val minDate = viewState.minDate
            if (minDate != null && value != null && value.isBefore(minDate)) {
                throw IllegalArgumentException("Can't set a maxDate that's before minDate")
            }

            viewState.maxDate = value?.copy()
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Time range
     *
     ***********************************************************************************************
     */

    /**
     * Returns the minimum hour that [WeekView] will display. Events before this time will not be
     * shown.
     */
    @PublicApi
    var minHour: Int
        get() = viewState.minHour
        set(value) {
            if (value < 0 || value > viewState.maxHour) {
                throw IllegalArgumentException("minHour must be between 0 and maxHour.")
            }

            viewState.minHour = value
            invalidate()
        }

    /**
     * Returns the maximum hour that [WeekView] will display. Events before this time will not be
     * shown.
     */
    @PublicApi
    var maxHour: Int
        get() = viewState.maxHour
        set(value) {
            if (value > 24 || value < viewState.minHour) {
                throw IllegalArgumentException("maxHour must be between minHour and 24.")
            }

            viewState.maxHour = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Scrolling
     *
     ***********************************************************************************************
     */

    /**
     * Returns whether WeekView can scroll horizontally.
     */
    @PublicApi
    var isHorizontalScrollingEnabled: Boolean
        get() = viewState.horizontalScrollingEnabled
        set(value) {
            viewState.horizontalScrollingEnabled = value
        }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestureHandler.onTouchEvent(event)

    /*
     ***********************************************************************************************
     *
     *   Date methods
     *
     ***********************************************************************************************
     */

    /**
     * Returns the first visible date.
     */
    @PublicApi
    val firstVisibleDate: Calendar
        get() = viewState.dateRange.first().copy()

    /**
     * Returns the last visible date.
     */
    @PublicApi
    val lastVisibleDate: Calendar
        get() = viewState.dateRange.last().copy()

    /**
     * Set the specified date. Any provided [Calendar] that falls outside the range of
     * [minDate] and [maxDate] will be adjusted to fit into this range.
     *
     * @param date A [Calendar] representing the date to scroll to.
     */
    @PublicApi
    fun setDate(date: Calendar) {
        internalScrollToDate(date.withLocalTimeZone(), animate = false)
    }

    /**
     * Set the specified date time. Any provided [Calendar] that falls outside the range of
     * [minDate] and [maxDate], or [minHour] and [maxHour], will be adjusted to fit into these
     * ranges.
     *
     * @param dateTime A [Calendar] representing the date time to scroll to.
     */
    @PublicApi
    fun setDateTime(dateTime: Calendar) {
        internalScrollToDate(dateTime.withLocalTimeZone(), animate = false) {
            scrollToTime(hour = it.hour, minute = it.minute)
        }
    }

    /**
     * Scrolls to the specified date. Any provided [Calendar] that falls outside the range of
     * [minDate] and [maxDate] will be adjusted to fit into this range.
     *
     * @param date A [Calendar] representing the date to scroll to.
     */
    @PublicApi
    fun scrollToDate(date: Calendar) {
        internalScrollToDate(date.withLocalTimeZone())
    }

    /**
     * Scrolls to the specified date time. Any provided [Calendar] that falls outside the range of
     * [minDate] and [maxDate], or [minHour] and [maxHour], will be adjusted to fit into these
     * ranges.
     *
     * @param dateTime A [Calendar] representing the date time to scroll to.
     */
    @PublicApi
    fun scrollToDateTime(dateTime: Calendar) {
        val localeDate = dateTime.withLocalTimeZone()
        internalScrollToDate(localeDate) {
            scrollToTime(hour = it.hour, minute = it.minute)
        }
    }

    /**
     * Scrolls to the specified time. Any provided time that falls outside the range of [minHour]
     * and [maxHour] will be adjusted to fit into these ranges.
     *
     * @param hour The hour to scroll to.
     * @param minute The minute to scroll to.
     */
    @PublicApi
    fun scrollToTime(hour: Int, minute: Int) {
        if (isWaitingToBeLaidOut) {
            // If the view's dimensions have just changed or if it hasn't been laid out yet, we
            // postpone the action until onDraw() is called the next time.
            viewState.pendingScroll = viewState.firstVisibleDate.withTime(hour, minute)
            return
        }

        val sanitizedHour = hour.coerceIn(minimumValue = minHour, maximumValue = maxHour)
        val desired = nowAtTimezone(viewState.customTimeZone).withTime(hour = sanitizedHour, minutes = minute)

        // the time passed to scrollToTime already has the padding
//        if (desired.hour > minHour) {
//            // Add some padding above the current time (and thus: the now line)
//            desired.subtractHours(1)
//        } else {
//            desired.subtractMinutes(desired.minute)
//        }

        val fraction = desired.minute / 60f
        val verticalOffset = hourHeight * (desired.hour + fraction)

        // We make sure that WeekView doesn't "over-scroll" by limiting the offset to the total day
        // height minus the height of WeekView, which would result in scrolling all the way to the
        // bottom.
        val maxOffset = viewState.dayHeight - height
        val finalOffset = min(maxOffset, verticalOffset) * (-1)

        if (verticalScrollOffset >= finalOffset * -1 - 5 && verticalScrollOffset <= finalOffset * -1 + 5) {
            // To avoid micro adjustments due to calculating minutes from offset
            return
        }
        navigator.scrollVerticallyTo(offset = finalOffset)
    }

    private fun internalScrollToDate(date: Calendar, animate: Boolean = true, onComplete: (Calendar) -> Unit = {}) {
        val adjustedDate = viewState.getStartDateInAllowedRange(date)
        if (adjustedDate.toEpochDays() == viewState.firstVisibleDate.toEpochDays()) {
            onComplete(adjustedDate)
            return
        }

        gestureHandler.forceScrollFinished()

        if (isWaitingToBeLaidOut) {
            // If the view's dimensions have just changed or if it hasn't been laid out yet, we
            // postpone the action until onDraw() is called the next time.
            viewState.pendingScroll = adjustedDate
            return
        }

        navigator.scrollHorizontallyTo(date = adjustedDate, animate = animate) {
            onComplete(adjustedDate)
        }
    }

    /**
     * Returns the first hour that is visible on the screen.
     */
    @PublicApi
    val firstVisibleHour: Int
        get() = viewState.firstVisibleHour

    /**
     * Returns the first hour that is fully visible on the screen.
     */
    @PublicApi
    val firstFullyVisibleHour: Int
        get() = viewState.firstFullyVisibleHour

    /**
     * Returns the last hour that is visible on the screen.
     */
    @PublicApi
    val lastVisibleHour: Int
        get() = viewState.lastVisibleHour

    /**
     * Returns the last hour that is fully visible on the screen.
     */
    @PublicApi
    val lastFullyVisibleHour: Int
        get() = viewState.lastFullyVisibleHour

    /**
     * Returns the current vertical offset (in pixels) from the top. This is 0 if WeekView is
     * scrolled up all the way to [minHour].
     */
    @PublicApi
    val verticalScrollOffset: Float
        get() {
            // Invert the current origin, as it feels more natural
            // to have a positive verticalScrollOffset.
            return viewState.currentOrigin.y * -1
        }

    /*
     ***********************************************************************************************
     *
     *   Typeface
     *
     ***********************************************************************************************
     */

    /**
     * Returns the typeface used for events, time labels and date labels.
     */
    @PublicApi
    var typeface: Typeface
        get() = viewState.typeface
        set(value) {
            viewState.typeface = value
            invalidate()
        }

    /*
     ***********************************************************************************************
     *
     *   Adapter
     *
     ***********************************************************************************************
     */

    private var internalAdapter: Adapter<*>? = null

    var adapter: Adapter<*>?
        get() = internalAdapter
        set(value) {
            setAdapterInternal(value)
        }

    private fun setAdapterInternal(adapter: Adapter<*>?) {
        internalAdapter = adapter
        touchHandler.adapter = adapter
        adapter?.registerObserver(this)
        invalidate()
    }

    @PublicApi
    fun setDateFormatter(formatter: DateFormatter) {
        viewState.dateFormatter = formatter
        renderers.filterIsInstance(DateFormatterDependent::class.java).forEach {
            it.onDateFormatterChanged(formatter)
        }
        invalidate()
    }

    @PublicApi
    fun setWeekDayFormatter(formatter: DateFormatter) {
        viewState.weekDayFormatter = formatter
        renderers.filterIsInstance(DateFormatterDependent::class.java).forEach {
            it.onDateFormatterChanged(formatter)
        }
        invalidate()
    }

    @PublicApi
    fun setTimeFormatter(formatter: TimeFormatter) {
        viewState.timeFormatter = formatter
        renderers.filterIsInstance(TimeFormatterDependent::class.java).forEach {
            it.onTimeFormatterChanged(formatter)
        }
        invalidate()
    }

    @PublicApi
    fun getTimeFormatter(): TimeFormatter {
        return viewState.timeFormatter
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityTouchHelper.dispatchHoverEvent(event)) {
            return true
        }
        return super.dispatchHoverEvent(event)
    }

    /**
     * An abstract base class for an adapter used with [WeekView].
     *
     * An adapter allows interaction with [WeekView]. It provides optional methods for being
     * notified about clicks, long-clicks and scrolling events.
     *
     * @param T The type of elements that are displayed in the corresponding [WeekView].
     */
    abstract class Adapter<T> {

        internal abstract val eventsCache: EventsCache
        internal val eventChipsCache: EventChipsCache by lazy { EventChipsCache() }
        private val eventChipsFactory: EventChipsFactory by lazy { EventChipsFactory() }

        internal val eventsProcessor: EventsProcessor by lazy {
            EventsProcessor(
                context = context,
                eventsCache = eventsCache,
                eventChipsCache = eventChipsCache,
                eventChipsFactory = eventChipsFactory
            )
        }

        internal var weekView: WeekView? = null
            private set

        /**
         * Provides the [Context] that the adapter's [WeekView] is running in.
         */
        val context: Context
            get() = checkNotNull(weekView).context

        internal fun handleClick(x: Float, y: Float, headerClicked: Boolean, dateClicked: Calendar): Boolean {
            val eventChip = findHitEvent(x, y, headerClicked, dateClicked) ?: return false
            val data = findEventData(id = eventChip.eventId) ?: return false
            onEventClick(data, eventChip.bounds)
            return true
        }

        internal fun handleLongClick(x: Float, y: Float, headerClicked: Boolean, dateClicked: Calendar): LongClickResult? {
            val eventChip = findHitEvent(x, y, headerClicked, dateClicked) ?: return null
            val data = findEventData(id = eventChip.eventId) ?: return null
            val handled = onEventLongClick(data, eventChip.bounds)
            return LongClickResult(eventChip = eventChip, handled = handled)
        }

        private fun findHitEvent(x: Float, y: Float, headerClicked: Boolean, dateClicked: Calendar): EventChip? {
            val candidates = eventChipsCache.allEventChips.filter {
                it.isHit(x, y) && dateClicked.isSameDate(it.startTime) &&
                        (headerClicked == it.event.isAllDay || headerClicked == it.event.isMultiDay)
            }
            return when {
                candidates.isEmpty() -> null
                // Two events hit. This is most likely because an all-day event was clicked, but a
                // single event is rendered underneath it. We return the all-day event.
                candidates.size == 2 -> {
                    candidates.firstOrNull { it.event.isAllDay || it.event.isMultiDay }?.takeUnless { it.isHidden }
                        ?: candidates.first().takeUnless { it.isHidden }
                }
                else -> candidates.first().takeUnless { it.isHidden }
            }
        }

        internal fun registerObserver(weekView: WeekView) {
            this.weekView = weekView
        }

        internal fun updateObserver() {
            weekView?.invalidate()
        }

        internal fun onEventClick(id: String, bounds: RectF) {
            val data = findEventData(id) ?: return
            onEventClick(data, bounds)
        }

        internal fun handleLongClick(id: String, bounds: RectF) {
            val data = findEventData(id) ?: return
            onEventLongClick(data, bounds)
        }

        @Suppress("UNCHECKED_CAST")
        private fun findEventData(id: String): T? {
            val match = eventsCache[id]
            return (match as? ResolvedWeekViewEntity.Event<T>)?.data
        }

        /**
         * Called for each element of type [T] that was submitted to this adapter. This method must
         * return a [WeekViewEntity] that will be rendered in the [WeekView] that is associated with
         * this adapter.
         *
         * @param item The item of type [T] that was submitted to [WeekView]
         * @return A [WeekViewEntity] that will be rendered in [WeekView]
         */
        abstract fun onCreateEntity(item: T): WeekViewEntity

        /**
         * Returns the data of the [WeekViewEntity.Event] that the user clicked on.
         *
         * @param data The data of the [WeekViewEntity.Event]
         */
        open fun onEventClick(data: T) = Unit

        /**
         * Returns the data of the [WeekViewEntity.Event] that the user clicked on as well as the
         * bounds of the [EventChip] in which it is displayed.
         *
         * @param data The data of the [WeekViewEntity.Event]
         * @param bounds The [RectF] representing the bounds of the event's [EventChip]
         */
        open fun onEventClick(data: T, bounds: RectF) = Unit

        /**
         * Returns the data of the [WeekViewEntity.Event] that the user long-clicked on.
         *
         * @param data The data of the [WeekViewEntity.Event]
         */
        open fun onEventLongClick(data: T) = Unit

        /**
         * Returns the data of the [WeekViewEntity.Event] that the user long-clicked on as well as
         * the bounds of the [EventChip] in which it is displayed.
         *
         * @param data The data of the [WeekViewEntity.Event]
         * @param bounds The [RectF] representing the bounds of the event's [EventChip]
         * @return Whether the long click has been handled. If false, this will trigger drag-&-drop
         *         to activate.
         */
        open fun onEventLongClick(data: T, bounds: RectF): Boolean = false

        internal fun handleDragAndDrop(id: String) {
            val data = findEventData(id) ?: return
            val match = eventsCache[id] ?: return

            onDragAndDropFinished(
                data = data,
                newStartTime = match.startTime,
                newEndTime = match.endTime,
            )
        }

        /**
         * Called when a drag-&-drop gesture has finished to inform the caller of the dragged event's
         * new start and end time.
         *
         * @param data The [T] entity that is associated with the dragged event
         * @param newStartTime The new start time that the event was dragged to
         * @param newEndTime THe new end time that the event was dragged to
         */
        open fun onDragAndDropFinished(data: T, newStartTime: Calendar, newEndTime: Calendar) = Unit

        /**
         * Returns the date of the day header that the user clicked on.
         *
         * @param time A [Calendar] with the date and time
         */
        open fun onDateHeaderClick(time: Calendar) = Unit

        /**
         * Returns the date and time of the location that the user clicked on.
         *
         * @param time A [Calendar] with the date and time
         * @param isAllDay A [Boolean] to define whether we clicked on all day header
         */
        open fun onEmptyViewClick(time: Calendar, isAllDay: Boolean) = Unit

        /**
         * Returns the date and time of the location that the user long-clicked on.
         *
         * @param time A [Calendar] with the date and time
         */
        open fun onEmptyViewLongClick(time: Calendar) = Unit

        /**
         * Called whenever the range of dates visible in [WeekView] changes. The list of dates is
         * typically as long as [numberOfVisibleDays], though it might contain an additional date
         * if [WeekView] is currently scrolling.
         *
         * @param firstVisibleDate A [Calendar] representing the first visible date
         * @param lastVisibleDate A [Calendar] representing the last visible date
         */
        open fun onRangeChanged(firstVisibleDate: Calendar, lastVisibleDate: Calendar) = Unit

        /**
         * Called whenever the vertical scroll position in [WeekView] changes. A [distance] > 0
         * indicates that the user is scrolling down towards later hours; a [distance] < 0 that the
         * user is scrolling up towards earlier hours.
         *
         * @param currentOffset The current vertical offset.
         * @param distance The distance that the user scrolled vertically.
         */
        open fun onVerticalScrollPositionChanged(currentOffset: Float, distance: Float) = Unit

        /**
         * Called when the vertical scrolling in [WeekView] finished.
         *
         * @param currentOffset The current vertical offset.
         */
        open fun onVerticalScrollFinished(currentOffset: Float) = Unit

        /**
         * Called when the hour height changes after zooming in or out.
         *
         * @param newHourHeight The new hour height.
         */
        open fun onHourHeightChanged(newHourHeight: Float) = Unit
    }

    /**
     * An implementation of [WeekView.Adapter] that allows to submit a list of new elements to
     * [WeekView].
     *
     * Newly submitted events are processed on a background thread and then presented in [WeekView].
     * Previously submitted events are replaced completely. If you require a paginated approach, you
     * might want to use [WeekView.PagingAdapter].
     *
     * @param T The type of elements that are displayed in the corresponding [WeekView].
     */
    abstract class SimpleAdapter<T> : Adapter<T>() {

        override val eventsCache = SimpleEventsCache()

        /**
         * Submits a new list of elements to the adapter. These events are processed on a background
         * thread and then presented in [WeekView]. Previously submitted events are replaced
         * completely.
         *
         * @param elements The elements of type [T] that are to be displayed in [WeekView]
         */
        @PublicApi
        fun submitList(elements: List<T>) {
            val viewState = weekView?.viewState ?: return
            val entities = elements.map(this::onCreateEntity)
            eventsProcessor.submit(entities, viewState, onFinished = this::updateObserver)
        }
    }

    /**
     * An implementation of [WeekView.Adapter] that allows to submit a list of new elements to
     * [WeekView] in a paginated way.
     *
     * This adapter keeps a cache of the submitted elements grouped by month. Whenever the user
     * scrolls to a different month, this adapter will check whether that month's events are present
     * in the cache. If not, it will dispatch a callback to [onLoadMore] with the start and end
     * dates of the months that need to be fetched.
     *
     * Newly submitted events are processed on a background thread and then presented in [WeekView].
     * To clear the cache and thus refresh all events, you can call [refresh].
     *
     * @param T The type of elements that are displayed in the corresponding [WeekView].
     */
    abstract class PagingAdapter<T> : Adapter<T>() {

        override val eventsCache = PaginatedEventsCache()

        /**
         * Submits a new list of elements of type [T] to the adapter. These events are processed on
         * a background thread and then presented in [WeekView].
         *
         * @param elements The elements of type [T] that are to be displayed in [WeekView]
         */
        @PublicApi
        fun submitList(elements: List<T>) {
            val viewState = weekView?.viewState ?: return
            val entities = elements.map(this::onCreateEntity)
            eventsProcessor.submit(entities, viewState, onFinished = this::updateObserver)
        }

        /**
         * Called whenever [WeekView] needs to fetch new elements of a given month in order to allow
         * for a smooth scrolling experience.
         *
         * This adapter caches submitted elements of the current month as well as its previous and
         * next month. If [WeekView] scrolls to a new month, that month as well as its surrounding
         * months need to potentially be fetched.
         *
         * @param startDate A [Calendar] of the first date of the month that needs to be fetched
         * @param endDate A [Calendar] of the last date of the month that needs to be fetched
         */
        @PublicApi
        open fun onLoadMore(startDate: Calendar, endDate: Calendar) = Unit

        /**
         * Refreshes the elements presented by this adapter. All cached elements will be removed and
         * a call to [onLoadMore] will be triggered.
         */
        @PublicApi
        fun refresh() {
            eventsCache.clear()
            eventChipsCache.clear()
            weekView?.invalidate()
        }

        internal fun dispatchLoadRequest() {
            val firstVisibleDate = weekView?.viewState?.firstVisibleDate ?: return
            val fetchRange = FetchRange.create(firstVisibleDate)
            if (fetchRange in eventsCache) {
                return
            }

            val periodsToFetch = eventsCache.determinePeriodsToFetch(fetchRange)
            if (periodsToFetch.isNotEmpty()) {
                fetchPeriods(periodsToFetch)
            }
        }

        private fun fetchPeriods(periods: List<Period>) {
            val grouped = periods.groupConsecutivePeriods()

            for (period in periods) {
                // We add an empty list for the periods to avoid multiple fetch attempts.
                eventsCache.reserve(period)
            }

            for (group in grouped) {
                val first = group.first()
                val last = group.last()
                onLoadMore(first.startDate, last.endDate)
            }
        }

        private fun List<Period>.groupConsecutivePeriods(): List<List<Period>> {
            val emptyList = mutableListOf<MutableList<Period>>()
            return fold(emptyList) { accumulator, period ->
                val lastPeriodInList = accumulator.lastOrNull()?.last()
                val isConsecutive = lastPeriodInList?.next == period
                if (accumulator.isEmpty() || !isConsecutive) {
                    accumulator.add(mutableListOf(period))
                } else {
                    accumulator.last().add(period)
                }
                accumulator
            }
        }
    }
}
