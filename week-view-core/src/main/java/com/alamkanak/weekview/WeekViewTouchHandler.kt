package com.alamkanak.weekview

import java.util.Calendar

internal data class LongClickResult(
    val eventChip: EventChip,
    val handled: Boolean,
)

internal class WeekViewTouchHandler(
    private val viewState: ViewState
) {

    var adapter: WeekView.Adapter<*>? = null

    private fun getDateClicked(x: Float): Calendar {
        return if (viewState.isSingleDay) viewState.firstVisibleDate
        else {
            val gridWidth = viewState.headerBounds.right - viewState.headerBounds.left
            val dayWidth = gridWidth / viewState.numberOfVisibleDays
            var dayClicked = 0
            for (i in 0 until viewState.numberOfVisibleDays) {
                if (x >= viewState.headerBounds.left + i * dayWidth && x <= viewState.headerBounds.left + i * dayWidth + dayWidth) {
                    dayClicked = i
                    break
                }
            }
            val dateRange = viewState.createDateRange(viewState.firstVisibleDate).validate(viewState).toMutableList()
            dateRange[dayClicked]
        }
    }

    fun handleClick(x: Float, y: Float) {
        val inCalendarArea = x > viewState.timeColumnWidth
        val inAllDayEventsToggleArea = viewState.toggleAllDayEventsAreaBounds.contains(x, y)

        val triggeredAllDayEventsToggleArea = inAllDayEventsToggleArea && (viewState.showHeaderDownArrow || viewState.allDayEventsExpanded)
        val triggeredAllDayMoreToggleArea = viewState.isInAllDayMoreBounds(x, y) && viewState.allDayEventsExpanded.not()
        if (viewState.showAllDayEventsToggleArrow && (triggeredAllDayEventsToggleArea || triggeredAllDayMoreToggleArea)) {
            viewState.allDayEventsExpanded = !viewState.allDayEventsExpanded
            adapter?.updateObserver()
            return
        }

        // Check what date was clicked
        val dateClicked = getDateClicked(x)

        // First check if user clicked on header date to open day view
        if (viewState.numberOfVisibleDays > 1 && viewState.headerBounds.contains(x, y)) {
            if (y < viewState.headerDateLabelHeight) {
                // Handle click to open day view
                adapter?.onDateHeaderClick(dateClicked)
                return
            }
        }

        // Check if user clicked on event
        val handled = adapter?.handleClick(x, y, viewState.headerBounds.contains(x, y), dateClicked) ?: false

        // Check if user clicked on empty space in header to open all day event form
        if (viewState.headerBounds.contains(x, y) && !handled) {
            // Handle click in empty header
            adapter?.onEmptyViewClick(dateClicked, true)
            return
        }

        if (!inCalendarArea) {
            return
        }

        if (!handled && y > viewState.headerHeight) {
            val time = calculateTimeFromPoint(x, y) ?: return
            adapter?.onEmptyViewClick(time, false)
        }
    }

    fun handleLongClick(x: Float, y: Float): LongClickResult? {
        val isInTimeColumn = x <= viewState.timeColumnWidth
        val isInCalendarArea = x > viewState.timeColumnWidth && y > viewState.headerHeight

        if (isInTimeColumn) {
            return null
        }

        // Check what date was clicked
        val dateClicked = getDateClicked(x)

        val result = adapter?.handleLongClick(x, y, viewState.headerBounds.contains(x, y), dateClicked)

        if (result == null && isInCalendarArea) {
            val time = calculateTimeFromPoint(x, y) ?: return null
            adapter?.onEmptyViewLongClick(time)
        }

        return result
    }

    /**
     * Returns the date and time that the user clicked on.
     *
     * @param touchX The x coordinate of the touch event.
     * @param touchY The y coordinate of the touch event.
     * @return The [Calendar] of the clicked position, or null if none was found.
     */
    internal fun calculateTimeFromPoint(
        touchX: Float,
        touchY: Float
    ): Calendar? {
        val dateRange = viewState.dateRangeWithStartPixels

        for ((date, startPixel) in dateRange) {
            val endPixel = startPixel + viewState.dayWidth
            val isWithinDay = touchX in startPixel..endPixel

            if (isWithinDay) {
                val hourHeight = viewState.hourHeight
                val pixelsFromMidnight = touchY - viewState.currentOrigin.y - viewState.headerHeight
                val hour = (pixelsFromMidnight / hourHeight).toInt()

                val pixelsFromFullHour = pixelsFromMidnight - hour * hourHeight
                val minutes = ((pixelsFromFullHour / hourHeight) * 60).toInt()

                return date.withTime(viewState.minHour + hour, minutes)
            }
        }

        return null
    }
}
