package com.alamkanak.weekview

import android.graphics.RectF

internal class EventChipBoundsCalculator(
    private val viewState: ViewState
) {

    fun calculateSingleEvent(
        eventChip: EventChip,
        startPixel: Float
    ): RectF {
        val drawableWidth = when (eventChip.event) {
            is ResolvedWeekViewEntity.Event<*> -> viewState.drawableDayWidth
            is ResolvedWeekViewEntity.BlockedTime -> viewState.dayWidth
        }

        val isBlockedTime = eventChip.event is ResolvedWeekViewEntity.BlockedTime
        val leftOffset = if (viewState.isLtr || isBlockedTime) 0 else viewState.columnGap

        val minutesFromStart = eventChip.minutesFromStartHour
        val top = calculateDistanceFromTop(minutesFromStart)

        val bottomMinutesFromStart = minutesFromStart + eventChip.durationInMinutes
        var bottom = calculateDistanceFromTop(bottomMinutesFromStart)

        val partialEventEndsAtEndOfDay = eventChip.endTime.isAtEndOfPeriod(hour = viewState.maxHour)
        val fullEventContinuesOnNextDay = eventChip.endsOnLaterDay

        if (!(partialEventEndsAtEndOfDay && fullEventContinuesOnNextDay) && !isBlockedTime) {
            // There's only one case where we don't render a vertical margin: The partial event ends
            // at midnight, but the full event continues continues on the next day.
            bottom -= viewState.eventMarginVertical
        }

        var left = startPixel + leftOffset + eventChip.relativeStart * drawableWidth
        var right = left + eventChip.relativeWidth * drawableWidth

        if (left > startPixel) {
            left += viewState.overlappingEventGap / 2
        }

        if (right < startPixel + drawableWidth) {
            right -= viewState.overlappingEventGap / 2
        }

        val hasNoOverlaps = (right == startPixel + drawableWidth)
        if (viewState.isSingleDay && hasNoOverlaps) {
            right -= viewState.singleDayHorizontalPadding * 2
        }

        val isBeingDragged = eventChip.eventId == viewState.dragState?.eventId
        if (isBeingDragged) {
            left = startPixel + leftOffset
            right = left + drawableWidth
        }

        return RectF(left, top, right, bottom)
    }

    private fun calculateDistanceFromTop(
        minutesFromStart: Int
    ): Float = with(viewState) {
        val portionOfDay = minutesFromStart.toFloat() / minutesPerDay
        val pixelsFromTop = hourHeight * hoursPerDay * portionOfDay
        return pixelsFromTop + currentOrigin.y + headerHeight
    }

    fun calculateAllDayEvent(
        eventChip: EventChip,
        startPixel: Float,
        firstPixelForStartDate: Float?,
        lastPixelForEndDate: Float?,
        topPixel: Float?
    ): RectF {
        val padding = viewState.headerPadding
        val dayWidth = viewState.drawableDayWidth
        val leftTextOffset = if (viewState.isLtr) 0 else viewState.columnGap

        val dateLabelHeight = if (viewState.numberOfVisibleDays > 1) {
            padding + viewState.dateLabelHeight + padding
        } else {
            0f
        }

        val chipHeight = viewState.allDayEventTextPaint.textSize + viewState.eventPaddingVertical * 2

        val top = if (viewState.arrangeAllDayEventsVertically) {
            if (eventChip.event.isMultiDay && topPixel != null) {
                // Use top pixel info so that it stays vertically aligned with previous child of multi day event
                topPixel
            } else {
                val previousChipsEndY = eventChip.verticalIndex * (eventChip.bounds.height() + viewState.eventMarginVertical)
                val fixedPreviousChipsHeight =
                    if (previousChipsEndY > 0 && previousChipsEndY < chipHeight) eventChip.verticalIndex * (chipHeight + viewState.eventMarginVertical)
                    else previousChipsEndY
                val dayViewMarginTop =
                    if (viewState.isSingleDay) viewState.headerPadding / 2f
                    else 0f
                dateLabelHeight + fixedPreviousChipsHeight + dayViewMarginTop
            }
        } else {
            dateLabelHeight
        }

        var left = if (viewState.arrangeAllDayEventsVertically) {

            if (viewState.isSingleDay) {
                startPixel + leftTextOffset
            } else {
                val computeLeft = if (eventChip.event.isMultiDay && startPixel < viewState.timeColumnWidth && eventChip.isNotLastIndex) {
                    // startPixel < viewState.timeColumnWidth -> Scrolling right
                    viewState.timeColumnWidth
                } else if (eventChip.event.isMultiDay && startPixel > viewState.timeColumnWidth && eventChip.index != 0) {
                    // startPixel > viewState.timeColumnWidth -> Scrolling left
                    viewState.timeColumnWidth
                } else {
                    startPixel + leftTextOffset
                }

                // Make sure we don't draw too far left when scrolling back
                if (firstPixelForStartDate != null && computeLeft < firstPixelForStartDate) firstPixelForStartDate
                else computeLeft
            }
        } else {
            startPixel + leftTextOffset + eventChip.relativeStart * dayWidth
        }

        var right = if (viewState.arrangeAllDayEventsVertically) {
            if (viewState.isSingleDay || eventChip.event.isSingleDay) {
                left + dayWidth
            } else {
                lastPixelForEndDate?.let {it - viewState.columnGap} ?: (left + dayWidth)
            }
        } else {
            left + eventChip.relativeWidth * dayWidth
        }

        val isLeftMostColumn = left == startPixel
        val isRightMostColumn = right == startPixel + dayWidth

        val endOfView = viewState.viewWidth.toFloat() == startPixel + viewState.dayWidth

        if (!isLeftMostColumn) {
            left += viewState.overlappingEventGap / 2f
        }

        if (!isRightMostColumn) {
            right -= viewState.overlappingEventGap / 2f
        }

        val bottom = top + chipHeight

        if (viewState.isSingleDay && isRightMostColumn) {
            right -= viewState.singleDayHorizontalPadding * 2
        }

        return RectF(left, top, right, bottom)
    }
}
