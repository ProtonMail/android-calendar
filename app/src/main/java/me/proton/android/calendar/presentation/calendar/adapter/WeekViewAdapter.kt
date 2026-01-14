package me.proton.android.calendar.presentation.calendar.adapter

import android.graphics.RectF
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.WeekViewPagingAdapterJsr310
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.model.WeekViewCalendarEntity
import me.proton.android.calendar.domain.model.toWeekViewEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class WeekViewAdapter(
    private val dragHandler: (String, LocalDateTime, LocalDateTime) -> Unit,
    private val loadMoreHandler: (List<YearMonth>) -> Unit,
    private val rangeChangedHandler: (LocalDate, LocalDate) -> Unit,
    private val viewClickHandler: (LocalDateTime, Boolean) -> Unit,
    private val eventClickHandler: (WeekViewCalendarEntity.Event) -> Unit,
    private val dateHeaderClickHandler: (LocalDate) -> Unit,
    private val onHourHeightChangedHandler: (Float) -> Unit
) : WeekViewPagingAdapterJsr310<WeekViewCalendarEntity>() {

    override fun onCreateEntity(item: WeekViewCalendarEntity): WeekViewEntity = item.toWeekViewEntity(context)

    override fun onEventClick(data: WeekViewCalendarEntity, bounds: RectF) {
        if (data is WeekViewCalendarEntity.Event) {
            eventClickHandler(data)
        }
    }

    override fun onRangeChanged(firstVisibleDate: LocalDate, lastVisibleDate: LocalDate) {
        super.onRangeChanged(firstVisibleDate, lastVisibleDate)
        rangeChangedHandler(firstVisibleDate, lastVisibleDate)
    }

    override fun onEmptyViewClick(time: LocalDateTime, isAllDay: Boolean) {
        viewClickHandler(time, isAllDay)
    }

    override fun onDateHeaderClick(time: LocalDate) {
        dateHeaderClickHandler(time)
    }

    override fun onDragAndDropFinished(data: WeekViewCalendarEntity, newStartTime: LocalDateTime, newEndTime: LocalDateTime) {
        if (data is WeekViewCalendarEntity.Event) {
            dragHandler(data.id, newStartTime, newEndTime)
        }
    }

    override fun onEmptyViewLongClick(time: LocalDateTime) {
    }

    override fun onLoadMore(startDate: LocalDate, endDate: LocalDate) {
        loadMoreHandler(yearMonthsBetween(startDate, endDate))
    }

    override fun onVerticalScrollPositionChanged(currentOffset: Float, distance: Float) {
    }

    override fun onVerticalScrollFinished(currentOffset: Float) {
    }

    override fun onHourHeightChanged(newHourHeight: Float) {
        onHourHeightChangedHandler(newHourHeight)
    }

    override fun onEventLongClick(data: WeekViewCalendarEntity, bounds: RectF): Boolean {
        return !CalendarFeatureFlag.DragAndDrop.fallbackValue // Return true to disable drag and drop
    }

    private fun yearMonthsBetween(startDate: LocalDate, endDate: LocalDate): List<YearMonth> {
        val yearMonths = mutableListOf<YearMonth>()
        val maxYearMonth = endDate.yearMonth
        var currentYearMonth = startDate.yearMonth

        while (currentYearMonth <= maxYearMonth) {
            yearMonths += currentYearMonth
            currentYearMonth = currentYearMonth.plusMonths(1)
        }

        return yearMonths
    }

    private val LocalDate.yearMonth: YearMonth
        get() = YearMonth.of(year, month)
}

