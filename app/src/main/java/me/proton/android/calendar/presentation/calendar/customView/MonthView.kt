package me.proton.android.calendar.presentation.calendar.customView

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.COLUMNS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.DECRYPTION_FAILED_BRIGHTEN_COLOR_BY
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.DECRYPTION_FAILED_PAST_EVENT_BRIGHTEN_COLOR_BY
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.MINI_EVENTS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.MONTH_VIEW_FONT_PATH
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.ROWS_MAX
import me.proton.android.calendar.presentation.calendar.customView.MonthView.MonthViewSettings.UNANSWERED_STRIPES_BRIGHTEN_COLOR_BY
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos

class MonthView : ViewGroup {

    object MonthViewSettings {
        const val ROWS_MAX = 6
        const val COLUMNS_MAX = 7
        const val MINI_EVENTS_MAX = 2
        const val MONTH_GRID_ITEMS_MAX = ROWS_MAX * COLUMNS_MAX
        const val MONTH_VIEW_CACHE = 2L // Cached views on each side of current view

        const val MONTH_VIEW_FONT_PATH = "fonts/Roboto-Medium.ttf"

        const val DECRYPTION_FAILED_PAST_EVENT_BRIGHTEN_COLOR_BY = 0.04f
        const val DECRYPTION_FAILED_BRIGHTEN_COLOR_BY = 0.18f

        const val UNANSWERED_STRIPES_BRIGHTEN_COLOR_BY = 0.18f
    }

    // Map of grid index and list of events for each index
    private var monthViewEventsMap: HashMap<Int, List<MonthViewEvent>> = hashMapOf()

    private var maxEventCount = 0

    private var gridItemWidth = 0F
    private var gridItemHeight = 0F
    private var parentWidth = 0F
    private var parentHeight = 0F

    private var eventRadius: Float = 0F
    private var highlightDayTitleRectRadius: Float = 0F

    private var highlightDayTitleRectWidth: Float = 0F
    private var highlightDayTitleRectHeight: Float = 0F

    private var highlightDayTitleRectTopMargin: Float = 0F

    private var basicTitlePaint: TextPaint

    private var dayTitlePaint: TextPaint
    private var offsetDayTitlePaint: TextPaint
    private var highlightDayTitlePaint: TextPaint

    private var highlightDayTitleRectPaint: Paint
    private var gridItemSeparatorPaint: Paint

    private var dayList: List<LocalDate>? = null
    private var month: Month? = null

    private var showWeekNumbers: Boolean = false
    private var timeZoneId: String = ""

    private val res = context.resources

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        setWillNotDraw(false)

        val res = context.resources

        eventRadius = res.getDimension(R.dimen.month_view_event_corner_radius)
        highlightDayTitleRectRadius = res.getDimension(R.dimen.month_view_event_title_highlight_radius)

        highlightDayTitleRectWidth = res.getDimension(R.dimen.month_view_event_title_highlight_width)
        highlightDayTitleRectHeight = res.getDimension(R.dimen.month_view_event_title_highlight_height)

        highlightDayTitleRectTopMargin = res.getDimension(R.dimen.month_view_event_title_highlight_top_margin)

        val robotoMediumTypeface = Typeface.createFromAsset(context.assets, MONTH_VIEW_FONT_PATH)

        basicTitlePaint = TextPaint().apply {
            style = Paint.Style.FILL
            textSize = res.getDimension(R.dimen.month_view_event_title_text_size)
            typeface = robotoMediumTypeface
            color = ContextCompat.getColor(context, R.color.text_on_calendar_color)
            isAntiAlias = true
        }

        dayTitlePaint = TextPaint().apply {
            style = Paint.Style.FILL
            textSize = res.getDimension(R.dimen.month_view_day_number_text_size)
            typeface = robotoMediumTypeface
            color = ContextCompat.getColor(context, R.color.text_norm)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        highlightDayTitlePaint = TextPaint(dayTitlePaint).apply {
            color = context.getColorFromAttr(
                R.attr.proton_text_accent
            )
        }

        highlightDayTitleRectPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = context.getColorFromAttr(
                R.attr.proton_icon_accent
            )
            strokeWidth = res.getDimension(R.dimen.month_view_event_title_highlight_stroke)
            isAntiAlias = true
        }

        offsetDayTitlePaint = TextPaint(dayTitlePaint).apply {
            color = ContextCompat.getColor(context, R.color.text_hint)
        }

        gridItemSeparatorPaint = Paint().apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(context, R.color.separator_norm)
            strokeWidth = res.getDimension(R.dimen.month_view_grid_separator_width)

            isAntiAlias = true
        }
    }

    constructor(context: Context, attrs: AttributeSet) : this(context, attrs, 0) { }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawMonthGrid(canvas)

        drawEvents(canvas)
    }

    private fun drawMonthGrid(canvas: Canvas) {

        val dayList = this.dayList
        val month = this.month
        if (dayList == null || month == null) return

        val topMargin = res.getDimension(R.dimen.month_view_day_number_text_baseline_margin_top)

        var listIndex = 0
        for (rowIndex in 0 until ROWS_MAX) {
            for (columnIndex in 0 until COLUMNS_MAX) {
                val date = dayList[listIndex]
                val text = date.dayOfMonth.toString()

                val gridItemStart = (columnIndex * gridItemWidth)
                val gridItemTop = (rowIndex * gridItemHeight) + topMargin

                canvas?.drawText(
                    text,
                    0,
                    text.length,
                    gridItemStart + (gridItemWidth / 2),
                    gridItemTop,
                    when {
                        date == LocalDate.now(ZoneId.of(timeZoneId)) -> highlightDayTitlePaint
                        date.month != month -> offsetDayTitlePaint
                        else -> dayTitlePaint
                    }
                )

                if (date == LocalDate.now(ZoneId.of(timeZoneId))) {
                    val fontMetrics = highlightDayTitlePaint.fontMetrics
                    val textHeight = fontMetrics.bottom - fontMetrics.top + fontMetrics.leading
                    val stroke = res.getDimension(R.dimen.month_view_event_title_highlight_stroke)

                    val start = gridItemStart + (gridItemWidth / 2) - (highlightDayTitleRectWidth / 2)
                    val top = gridItemTop - (textHeight / 4) - (highlightDayTitleRectHeight / 2) - (stroke / 2)
                    canvas?.drawRoundRect(
                        RectF(
                            start,
                            top,
                            start + highlightDayTitleRectWidth,
                            top + highlightDayTitleRectHeight
                        ),
                        highlightDayTitleRectRadius,
                        highlightDayTitleRectRadius,
                        highlightDayTitleRectPaint
                    )
                }

                listIndex++
            }
        }

        // Draw lines to split grid items
        val gridSeparatorList = arrayListOf<Float>()
        for (rowIndex in 0 until ROWS_MAX) {
            gridSeparatorList.add(0F) // startX
            gridSeparatorList.add(rowIndex * gridItemHeight) // startY
            gridSeparatorList.add(parentWidth) // stopX
            gridSeparatorList.add(rowIndex * gridItemHeight) // stopY
        }

        // 0 will draw an outer divider to the left of the grid
        val columnStart = if (showWeekNumbers) 0 else 1
        // MAX + 1 will draw an outer divider to the right of the grid
        val columnEnd = if (showWeekNumbers) COLUMNS_MAX + 1 else COLUMNS_MAX

        for (columnIndex in columnStart until columnEnd) {
            gridSeparatorList.add(columnIndex * gridItemWidth) // startX
            gridSeparatorList.add(0F) // startY
            gridSeparatorList.add(columnIndex * gridItemWidth) // stopX
            gridSeparatorList.add(parentHeight) // stopY
        }
        canvas?.drawLines(
            // startX, startY, stopX, stopY
            gridSeparatorList.toFloatArray(),
            gridItemSeparatorPaint
        )
    }

    private fun drawEvents(canvas: Canvas?) {
        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                if (monthViewEvent.isPlusIcon) drawPlusIcon(canvas, monthViewEvent)
                else drawEventRect(canvas, monthViewEvent)
            }
        }

        this.monthViewEventsMap.forEach {
            it.value.forEach { monthViewEvent ->
                monthViewEvent.eventTitle?.let { eventTitle ->
                    drawEventTitle(canvas, monthViewEvent, eventTitle)
                }
            }
        }
    }

    /**
     * Draws the event rects with side strip
     */
    private fun drawEventRect(canvas: Canvas?, monthViewEvent: MonthViewEvent) {

        if (!monthViewEvent.drawRect) return // For multi day events, we only draw one blob per row, skip the others

        // Draw main rect containing text
        canvas?.drawRoundRect(
            monthViewEvent.eventRect,
            eventRadius,
            eventRadius,
            monthViewEvent.eventRectPaint
        )

        if (monthViewEvent.strikeThroughTitle && !monthViewEvent.decryptionFailed && !monthViewEvent.isMiniEvent && !monthViewEvent.isPlusIcon) {
            // Draw the main rect stroke if event is cancelled or declined
            canvas?.drawRoundRect(
                monthViewEvent.eventRect,
                eventRadius,
                eventRadius,
                monthViewEvent.eventRectStrokePaint
            )
        }

        // Draw side strip after to cover the left side of original rect
        canvas?.drawRoundRect(
            monthViewEvent.eventLeftSideStripRect,
            eventRadius,
            eventRadius,
            monthViewEvent.eventLeftSideStripPaint
        )

        // Draw line to cover right half of side strip rect
        canvas?.drawLines(
            monthViewEvent.eventStripSeparationLine,
            monthViewEvent.eventStripSeparationPaint
        )

        if (monthViewEvent.isUnanswered) {
            // Draw unanswered style stripped lines
            canvas?.drawLines(
                monthViewEvent.unansweredStripes,
                monthViewEvent.unansweredStripesPaint
            )
        }

        if (monthViewEvent.decryptionFailed && !monthViewEvent.isMiniEvent) {
            // Draw round square that replaces the title for event that failed to be decrypted
            canvas?.drawRoundRect(
                monthViewEvent.decryptionFailedRect,
                eventRadius,
                eventRadius,
                monthViewEvent.decryptionFailedPaint
            )
        }
    }

    /**
     * Draws the event title
     */
    private fun drawEventTitle(canvas: Canvas?, monthViewEvent: MonthViewEvent, eventTitle: String) {
        // Draw text for event title
        if (eventTitle.isNotBlank() && monthViewEvent.ellipsizedTitle.isNotBlank()) {
            canvas?.drawText(
                eventTitle,
                0,
                monthViewEvent.ellipsizedTitle.length,
                monthViewEvent.titleX,
                monthViewEvent.titleY,
                monthViewEvent.titlePaint
            )
        }
    }

    /**
     * Draws the plus icon for more events
     */
    private fun drawPlusIcon(canvas: Canvas?, monthViewEvent: MonthViewEvent) {

        // Draw the '+' vertical line
        canvas?.drawRect(
            monthViewEvent.plusIconVerticalRect,
            monthViewEvent.plusIconPaint
        )

        // Draw the '+' horizontal line
        canvas?.drawRect(
            monthViewEvent.plusIconHorizontalRect,
            monthViewEvent.plusIconPaint
        )

    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Nothing to do here
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Width will change when showing / hiding week numbers. Check if view needs to be refreshed so that event blobs have the correct width.
        val heightChanged = (parentWidth != 0F && parentWidth != measuredWidth.toFloat()) ||
                (parentHeight != 0F && parentHeight != measuredHeight.toFloat())
        val refreshView = heightChanged && this.monthViewEventsMap.isNotEmpty()

        // Save the view's width and height
        parentWidth = measuredWidth.toFloat()
        parentHeight = measuredHeight.toFloat()

        // Calculate the grid items width and height
        gridItemWidth = parentWidth / COLUMNS_MAX
        gridItemHeight = parentHeight / ROWS_MAX

        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)

        // Calculate the event blobs width, height, positions and trigger view's onDraw
        if (refreshView) {
            prepareAndDrawMonthViewEvents()
        }
    }

    /**
     * Store the list of dates to display, as well as the month value for the month we display.
     */
    fun prepareMonthGrid(skeletonList: List<LocalDate>, forMonth: Month) {
        dayList = skeletonList
        month = forMonth
    }

    /**
     * Calculate how many events can fit in a grid item.
     */
    fun calculateMaxEventCount(): Int {
        val headerHeight = res.getDimension(R.dimen.month_view_day_header_height)
        val eventHeight = res.getDimension(R.dimen.month_view_event_height) +
                res.getDimension(R.dimen.month_view_event_top_margin)
        val miniEventsHeight = res.getDimension(R.dimen.month_view_mini_event_height)
        return ((gridItemHeight - headerHeight - miniEventsHeight -
                (res.getDimension(R.dimen.month_view_grid_item_top_margin) +
                        res.getDimension(R.dimen.month_view_grid_item_bottom_margin))
                ) / eventHeight).toInt()
    }

    /**
     * Store the events to display.
     */
    fun setMonthViewEvents(monthViewEventsMap: Map<Int, List<MonthViewEvent>>, showWeekNumbers: Boolean, maxEventCount: Int) {

        this.showWeekNumbers = showWeekNumbers

        // We set max event count here so that it matches the value used to calculate monthViewEventsMap
        this.maxEventCount = maxEventCount

        this.monthViewEventsMap.clear()

        this.monthViewEventsMap.putAll(monthViewEventsMap)

        prepareAndDrawMonthViewEvents()
    }

    /**
     * Set whether we display week numbers or not.
     */
    fun setShowWeekNumbers(showWeekNumbers: Boolean) {

        this.showWeekNumbers = showWeekNumbers
    }

    /**
     * Set Time Zone Id in order to highlight today's day number.
     */
    fun setTimeZoneId(timeZoneId: String) {

        this.timeZoneId = timeZoneId
    }

    /**
     * Iterate through the event list and prepare the elements to draw onto the view.
     */
    private fun prepareAndDrawMonthViewEvents() {
        this.monthViewEventsMap.forEach {

            val dayIndex = it.key
            val row = dayIndex / COLUMNS_MAX
            val column = dayIndex - (row * COLUMNS_MAX)

            var miniEventIndex = 0 // Workaround for monthViewEvent.indexInDay skipping some indexes because of previous day multi day events

            val maxIndex = it.value.maxOfOrNull { it.indexInDay }
            it.value.forEach { monthViewEvent ->
                if (monthViewEvent.indexInDay >= maxEventCount) {
                    val miniEventCount = it.value.size - maxEventCount
                    if (miniEventIndex > MINI_EVENTS_MAX - 1) { // Extract max number of mini events
                        monthViewEvent.preparePlusIcon(
                            context,
                            context.resources,
                            gridItemWidth,
                            gridItemHeight,
                            column,
                            row,
                            maxEventCount
                        )
                    } else {
                        monthViewEvent.prepareMiniEventBlob(
                            context,
                            context.resources,
                            gridItemWidth,
                            gridItemHeight,
                            column,
                            row,
                            maxEventCount,
                            if (maxIndex != null && maxIndex >= maxEventCount + miniEventCount) miniEventCount + 1
                            else miniEventCount,
                            miniEventIndex
                        )
                    }
                    miniEventIndex++
                } else {
                    monthViewEvent.prepareEventBlob(
                        context,
                        context.resources,
                        basicTitlePaint,
                        gridItemWidth,
                        gridItemHeight,
                        column,
                        row
                    )
                }
            }
        }

        invalidate()
    }

    /**
     * MonthViewEvent contains all the data needed to draw the event onto the view.
     */
    data class MonthViewEvent(
        val indexInDay: Int,
        val daySpanCount: Int,
        val daySpanIndex: Int,
        val calendarColor: Int,
        val pastEvent: Boolean,
        val isUnanswered: Boolean,
        val strikeThroughTitle: Boolean,
        val decryptionFailed: Boolean,
        val eventTitle: String?
    ) {

        lateinit var eventRect: RectF
        lateinit var eventRectPaint: Paint
        lateinit var eventRectStrokePaint: Paint

        lateinit var eventStripSeparationPaint: Paint
        lateinit var eventStripSeparationLine: FloatArray

        lateinit var eventLeftSideStripRect: RectF
        lateinit var eventLeftSideStripPaint: Paint

        lateinit var titlePaint: TextPaint

        var ellipsizedTitle: CharSequence = ""

        var titleX: Float = 0F
        var titleY: Float = 0F

        lateinit var plusIconHorizontalRect: RectF
        lateinit var plusIconVerticalRect: RectF

        lateinit var plusIconPaint: Paint

        lateinit var decryptionFailedRect: RectF
        lateinit var decryptionFailedPaint: Paint

        lateinit var unansweredStripes: FloatArray

        lateinit var unansweredStripesPaint: Paint

        var drawRect = true
        var isMiniEvent = false
        var isPlusIcon = false

        fun prepareEventBlob(context: Context, res: Resources, basicTitlePaint: TextPaint, gridItemWidth: Float, gridItemHeight: Float, column: Int, row: Int) {

            // For multi day events, we only draw one blob per row, skip the others
            if (daySpanCount > 1 && daySpanIndex > 1 && column > 0) {
                drawRect = false
                return
            }

            // Event rect start
            val start = (column * gridItemWidth + res.getDimension(R.dimen.month_view_event_start_margin))

            val headerHeight = res.getDimension(R.dimen.month_view_day_header_height)
            val eventHeight = res.getDimension(R.dimen.month_view_event_height)

            // Event rect top
            val top = ((row * gridItemHeight) +
                    headerHeight +
                    (indexInDay * eventHeight) +
                    (indexInDay * res.getDimension(R.dimen.month_view_event_top_margin)))

            val columnLastIndex = COLUMNS_MAX - 1
            val gridItemExtensionCount =
                if (daySpanIndex == 1 && column + daySpanCount > columnLastIndex) columnLastIndex - column
                else if (daySpanIndex > 1 && daySpanCount - daySpanIndex > columnLastIndex) columnLastIndex
                else daySpanCount - daySpanIndex

            // Event rect end
            val end = ((column * gridItemWidth) +
                    (gridItemExtensionCount * gridItemWidth) + gridItemWidth -
                    res.getDimension(R.dimen.month_view_event_end_margin))

            // Event rect bottom
            val bottom = top + eventHeight

            val sideStripWidth = res.getDimension(R.dimen.month_view_event_side_strip_width)
            val textStart = start + sideStripWidth + res.getDimension(R.dimen.month_view_event_title_start_margin)
            val eventRectAdjustmentEndMargin = res.getDimension(R.dimen.month_view_event_adjustment_end_margin)
            val textEndMargin = res.getDimension(R.dimen.month_view_event_title_end_margin)

            // Prepare the event left strip with darkened color
            eventLeftSideStripPaint = Paint().apply {
                style = Paint.Style.FILL
                color = Color.parseColor(
                    AndroidUtils.darkenCalendarColor(
                        "#${Integer.toHexString(calendarColor and 0x00ffffff)}"
                    )
                )
                isAntiAlias = true
            }
            eventLeftSideStripRect = RectF(
                start,
                top,
                start + (sideStripWidth * 2), // We double the width to have a proper rounded top & bottom
                bottom
            )

            // Prepare the event main rect
            eventRectPaint = Paint().apply {
                style = Paint.Style.FILL
                color =
                    if (isUnanswered || (strikeThroughTitle && !decryptionFailed)) ContextCompat.getColor(context, R.color.background_norm)
                    else if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else calendarColor
                isAntiAlias = true
            }
            if (strikeThroughTitle && !decryptionFailed) {
                // Prepare the event main rect stroke paint if event is cancelled or declined
                eventRectStrokePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    color =
                        when {
                            isUnanswered -> ContextCompat.getColor(context, R.color.background_norm)
                            pastEvent -> ContextCompat.getColor(context, R.color.interaction_weak_norm)
                            else -> calendarColor
                        }
                    isAntiAlias = true
                }
            }
            eventRect = RectF(
                start,
                top,
                end - eventRectAdjustmentEndMargin,
                bottom
            )

            // The event strip separation is used to cover the right half of the event strip round rectangle
            eventStripSeparationPaint = Paint(eventRectPaint).apply {
                strokeWidth = sideStripWidth + eventRectAdjustmentEndMargin
                style = Paint.Style.FILL
                if (strikeThroughTitle && !decryptionFailed) color = ContextCompat.getColor(context, R.color.background_norm)
            }
            eventStripSeparationLine = floatArrayOf(
                eventLeftSideStripRect.centerX() + (sideStripWidth / 2), top, eventLeftSideStripRect.centerX() + (sideStripWidth / 2), bottom
            )

            // Prepare the event title if needed
            if (eventTitle != null && !decryptionFailed) {
                titlePaint = TextPaint(basicTitlePaint).apply {
                    color =
                        if (pastEvent) ContextCompat.getColor(context, R.color.text_weak)
                        else if (isUnanswered || strikeThroughTitle) ContextCompat.getColor(context, R.color.text_norm)
                        else ContextCompat.getColor(context, R.color.text_on_calendar_color)
                    isStrikeThruText = strikeThroughTitle
                }

                titleX = textStart
                titleY = eventRect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2

                val titleWidth = eventRect.width() - textEndMargin - sideStripWidth

                // Use ellipsize to calculate the max length we can draw
                ellipsizedTitle = TextUtils.ellipsize(
                    eventTitle,
                    titlePaint,
                    titleWidth,
                    TextUtils.TruncateAt.END
                )
            }

            // Prepare failed to decrypt style
            if (decryptionFailed) {
                decryptionFailedPaint = Paint().apply {
                    val colorToBrighten =
                        if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                        else calendarColor
                    color = Color.parseColor(
                        AndroidUtils.brightenCalendarColor(
                            "#${Integer.toHexString(colorToBrighten and 0x00ffffff)}",
                            if (pastEvent) DECRYPTION_FAILED_PAST_EVENT_BRIGHTEN_COLOR_BY
                            else DECRYPTION_FAILED_BRIGHTEN_COLOR_BY
                        )
                    )
                    isAntiAlias = true
                }

                decryptionFailedRect = RectF(
                    start + res.getDimension(R.dimen.month_view_event_decryption_failed_margin),
                    top + res.getDimension(R.dimen.month_view_event_decryption_failed_margin),
                    end - res.getDimension(R.dimen.month_view_event_decryption_failed_margin),
                    bottom - res.getDimension(R.dimen.month_view_event_decryption_failed_margin)
                )
            }

            // Prepare unanswered stripped lines
            if (isUnanswered) {
                prepareStripes(context, res, sideStripWidth)
            }
        }

        fun prepareMiniEventBlob(context: Context, res: Resources, gridItemWidth: Float, gridItemHeight: Float, column: Int, row: Int, maxEventCount: Int, miniEventCount: Int, miniEventIndex: Int) {

            isMiniEvent = true

            val headerHeight = res.getDimension(R.dimen.month_view_day_header_height)
            val eventHeight = res.getDimension(R.dimen.month_view_event_height)
            val miniEventHeight = res.getDimension(R.dimen.month_view_mini_event_height)
            val eventRectAdjustmentEndMargin = res.getDimension(R.dimen.month_view_event_adjustment_end_margin)

            // Prepare the mini event blob rects

            val start: Float
            val end: Float

            // Mini event rect top
            val top = row * gridItemHeight +
                    headerHeight +
                    maxEventCount * eventHeight +
                    maxEventCount * res.getDimension(R.dimen.month_view_event_top_margin) +
                    res.getDimension(R.dimen.month_view_mini_event_top_margin)

            // Mini event rect bottom
            val bottom = top + miniEventHeight

            val columnStart = (column * gridItemWidth) + res.getDimension(R.dimen.month_view_grid_separator_width)
            // Calculate mini event rect start and end
            if (miniEventCount == 1) {
                // Display only one mini blob
                start = columnStart
                end = ((column * gridItemWidth) + gridItemWidth - res.getDimension(R.dimen.month_view_mini_event_end_margin))
            } else if (miniEventCount > MINI_EVENTS_MAX) {
                // Display '+' icon
                val plusIconWidth = res.getDimension(R.dimen.month_view_plus_icon_width) + res.getDimension(R.dimen.month_view_mini_event_end_margin)
                val miniEventWidth = (gridItemWidth -
                        plusIconWidth -
                        (MINI_EVENTS_MAX * res.getDimension(R.dimen.month_view_mini_event_end_margin))) /
                        MINI_EVENTS_MAX

                start = columnStart + (miniEventWidth * miniEventIndex)
                end = start + miniEventWidth - res.getDimension(R.dimen.month_view_mini_event_end_margin)
            } else {
                // Split width to display MINI_EVENTS_MAX number of mini event
                val endMargin =
                    if (miniEventCount - 1 == miniEventIndex) 0F
                    else res.getDimension(R.dimen.month_view_mini_event_end_margin)
                val miniEventWidth = (gridItemWidth -
                        (res.getDimension(R.dimen.month_view_mini_event_end_margin) + res.getDimension(R.dimen.month_view_grid_separator_width)) -
                        ((MINI_EVENTS_MAX - 1) * endMargin)) /
                        MINI_EVENTS_MAX

                start = columnStart + (miniEventWidth * miniEventIndex)
                end = start + miniEventWidth - endMargin
            }

            // Prepare the mini event left strip with darkened color
            eventLeftSideStripPaint = Paint().apply {
                color = Color.parseColor(AndroidUtils.darkenCalendarColor(
                    "#${Integer.toHexString(calendarColor and 0x00ffffff)}")
                )
                isAntiAlias = true
            }
            val sideStripWidth = res.getDimension(R.dimen.month_view_event_side_strip_width)
            eventLeftSideStripRect = RectF(
                start,
                top,
                start + (sideStripWidth * 2), // We double the width to have a proper rounded top & bottom
                bottom
            )

            // Prepare the mini event main rect
            eventRectPaint = Paint().apply {
                color =
                    if (isUnanswered) ContextCompat.getColor(context, R.color.background_norm)
                    else if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else calendarColor
                style =
                    if (strikeThroughTitle && !decryptionFailed) Paint.Style.STROKE
                    else Paint.Style.FILL
                isAntiAlias = true
            }
            eventRect = RectF(
                start,
                top,
                end - eventRectAdjustmentEndMargin,
                bottom
            )

            // The event strip separation is used to cover the right half of the event strip round rectangle
            eventStripSeparationPaint = Paint(eventRectPaint).apply {
                if (strikeThroughTitle && !decryptionFailed) color = ContextCompat.getColor(context, R.color.background_norm)
                strokeWidth = sideStripWidth + eventRectAdjustmentEndMargin
            }
            eventStripSeparationLine = floatArrayOf(
                eventLeftSideStripRect.centerX() + (sideStripWidth / 2), top, eventLeftSideStripRect.centerX() + (sideStripWidth / 2), bottom
            )

            // Prepare unanswered stripped lines
            if (isUnanswered) {
                prepareStripes(context, res, sideStripWidth)
            }
        }

        private fun prepareStripes(context: Context, res: Resources, sideStripWidth: Float) {
            val lineWidth = res.getDimension(R.dimen.month_view_event_unanswered_stripes_width)

            // Set the paint for the stripes
            unansweredStripesPaint = Paint().apply {
                color =
                    if (pastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
                    else Color.parseColor(
                        AndroidUtils.brightenCalendarColor(
                            "#${Integer.toHexString(calendarColor and 0x00ffffff)}",
                            UNANSWERED_STRIPES_BRIGHTEN_COLOR_BY
                        )
                    )
                strokeWidth = lineWidth
                isAntiAlias = true
            }

            // Define the gap between each stripe
            val lineGap = res.getDimension(R.dimen.month_view_event_unanswered_stripes_gap)

            val eventWidth = eventRect.width() - sideStripWidth
            val totalDistance = eventWidth + eventRect.height()

            // Array list containing sets of 4 floats that represents the x and y coordinates for the start and stop points of the line
            val stripesArrayList = arrayListOf<Float>()
            var distance = 0.0
            val rectStartX = eventRect.left + sideStripWidth
            while (distance < totalDistance) {

                val startX =
                    if (distance < eventWidth) rectStartX + distance.toFloat()
                    else rectStartX + eventWidth
                val startY =
                    if (distance < eventWidth) eventRect.top
                    else eventRect.top + (distance.toFloat() - eventWidth)
                val stopX =
                    if (distance < eventRect.height()) rectStartX
                    else rectStartX + (distance.toFloat() - eventRect.height())
                val stopY =
                    if (distance < eventRect.height()) eventRect.top + distance.toFloat()
                    else eventRect.top + eventRect.height()

                stripesArrayList.add(startX) // The x-coordinate of the start point of the line
                stripesArrayList.add(startY) // The y-coordinate of the start point of the line
                stripesArrayList.add(stopX) // The x-coordinate of the end point of the line
                stripesArrayList.add(stopY) // The y-coordinate of the end point of the line

                distance += ((lineGap + lineWidth) / cos(PI / 4))

            }
            unansweredStripes = stripesArrayList.toFloatArray()
        }

        fun preparePlusIcon(context: Context, res: Resources, gridItemWidth: Float, gridItemHeight: Float, column: Int, row: Int, maxEventCount: Int) {
            isPlusIcon = true

            val headerHeight = res.getDimension(R.dimen.month_view_day_header_height)
            val eventHeight = res.getDimension(R.dimen.month_view_event_height)
            val miniEventHeight = res.getDimension(R.dimen.month_view_mini_event_height)

            // Plus icon top
            val top = ((row * gridItemHeight) +
                    headerHeight +
                    (maxEventCount * eventHeight) +
                    (maxEventCount * res.getDimension(R.dimen.month_view_event_top_margin)) +
                    res.getDimension(R.dimen.month_view_mini_event_top_margin))

            // Plus icon end
            val end = (column * gridItemWidth) +
                    res.getDimension(R.dimen.month_view_grid_separator_width) +
                    gridItemWidth -
                    res.getDimension(R.dimen.month_view_plus_icon_end_margin)

            // Plus icon start
            val start = end - res.getDimension(R.dimen.month_view_plus_icon_width)

            // Plus icon bottom
            val bottom = top + miniEventHeight

            val horizontalHeight = ((bottom - top) / 2) - res.getDimension(R.dimen.month_view_plus_icon_line_width)
            // Prepare the plus icon horizontal line
            plusIconHorizontalRect = RectF(
                start,
                top + horizontalHeight,
                end,
                bottom - horizontalHeight
            )

            val verticalWidth = ((end - start) / 2) - res.getDimension(R.dimen.month_view_plus_icon_line_width)
            // Prepare the plus icon vertical line
            plusIconVerticalRect = RectF(
                start + verticalWidth,
                top,
                end - verticalWidth,
                bottom
            )

            // Prepare the plus icon paint
            plusIconPaint = Paint().apply {
                color =
                    if (eventTitle == null) {
                        val color = ContextCompat.getColor(context, R.color.interaction_weak_norm)
                        Color.parseColor(AndroidUtils.darkenCalendarColor(
                            "#${Integer.toHexString(color and 0x00ffffff)}")
                        )
                    } else ContextCompat.getColor(context, R.color.icon_weak)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
        }
    }
}
