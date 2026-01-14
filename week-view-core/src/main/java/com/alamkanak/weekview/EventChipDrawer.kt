package com.alamkanak.weekview

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.StaticLayout
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

internal class EventChipDrawer(
    private val viewState: ViewState
) {

    private val dragShadow: Int by lazy {
        Color.parseColor("#757575")
    }

    private val backgroundPaint = Paint()
    private val borderPaint = Paint()

    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    internal fun draw(
        eventChip: EventChip,
        canvas: Canvas,
        textLayout: StaticLayout?
    ) = with(canvas) {

        // Don't draw event if it is supposed be hidden
        if (eventChip.isHidden) return@with

        val entity = eventChip.event
        val bounds = eventChip.bounds
        val cornerRadius = (entity.style.cornerRadius ?: viewState.eventCornerRadius).toFloat()

        val isBeingDragged = entity.id == viewState.dragState?.eventId
        updateBackgroundPaint(entity, isBeingDragged, backgroundPaint)
        drawRoundRect(bounds, cornerRadius, cornerRadius, backgroundPaint)

        updateBorderPaint(entity, borderPaint)

        // Draw the event side strip
        val sideStripBounds = RectF(bounds.left, bounds.top, bounds.left + viewState.eventSideStripWidth * 2, bounds.bottom)
        drawRoundRect(sideStripBounds, cornerRadius, cornerRadius, Paint().apply {
            style = Paint.Style.FILL
            color = borderPaint.color
            isAntiAlias = true
        })

        // Draw a line to hide the right side of the event side strip rectangle
        val eventStripSeparationPaint = Paint(backgroundPaint).apply {
            strokeWidth = viewState.eventSideStripWidth + 2f // Adjustment to make sure the lines cover the whole right side of the event stripe
            style = Paint.Style.FILL
        }
        val xPos = sideStripBounds.centerX() + (viewState.eventSideStripWidth / 2) + 2f
        val eventStripSeparationLine = floatArrayOf(
            xPos, sideStripBounds.top, xPos, sideStripBounds.bottom
        )
        drawLines(
            eventStripSeparationLine,
            eventStripSeparationPaint
        )

        val unansweredStripesColor = entity.style.stripesColor
        if (unansweredStripesColor != null) {
            val lineWidth = viewState.unansweredStripesWidth

            // Set the paint for the stripes
            val unansweredStripesPaint = Paint().apply {
                color = unansweredStripesColor
                strokeWidth = lineWidth
                isAntiAlias = true
            }

            // Define the gap between each stripe
            val lineGap = viewState.unansweredStripesGap

            val sideStripWidth = sideStripBounds.width() / 2
            val eventWidth = bounds.width() - sideStripWidth
            val totalDistance = eventWidth + bounds.height()

            // Array list containing sets of 4 floats that represents the x and y coordinates for the start and stop points of the line
            val stripesArrayList = arrayListOf<Float>()
            var distance = 0.0
            val rectStartX = bounds.left + sideStripWidth
            while (distance < totalDistance) {

                val startX =
                    if (distance < eventWidth) rectStartX + distance.toFloat()
                    else rectStartX + eventWidth
                val startY =
                    if (distance < eventWidth) bounds.top
                    else bounds.top + (distance.toFloat() - eventWidth)
                val stopX =
                    if (distance < bounds.height()) rectStartX
                    else rectStartX + (distance.toFloat() - bounds.height())
                val stopY =
                    if (distance < bounds.height()) bounds.top + distance.toFloat()
                    else bounds.top + bounds.height()

                stripesArrayList.add(startX) // The x-coordinate of the start point of the line
                stripesArrayList.add(startY) // The y-coordinate of the start point of the line
                stripesArrayList.add(stopX) // The x-coordinate of the end point of the line
                stripesArrayList.add(stopY) // The y-coordinate of the end point of the line

                distance += ((lineGap + lineWidth) / cos(PI / 4))

            }
            val unansweredStripes = stripesArrayList.toFloatArray()

            drawLines(
                unansweredStripes,
                unansweredStripesPaint
            )
        }

        val pattern = entity.style.pattern
        if (pattern != null) {
            drawPattern(
                pattern = pattern,
                bounds = eventChip.bounds,
                isLtr = viewState.isLtr,
                paint = patternPaint
            )
        }

        val borderWidth = entity.style.borderWidth
        if (borderWidth != null && borderWidth > 0) {
            val borderBounds = bounds.insetBy(borderWidth / 2f)
            drawRoundRect(borderBounds, cornerRadius, cornerRadius, borderPaint)
        }

        // Draw event decryption failed blur rectangle
        if (textLayout?.text?.isEmpty() == true) {
            val pastEvent = eventChip.event.endTime.withTimeZone(viewState.customTimeZone).isBefore(nowAtTimezone(viewState.customTimeZone))
            val colorToBrighten = "#${Integer.toHexString(backgroundPaint.color and 0x00ffffff)}"

            val outHSL = FloatArray(3)
            ColorUtils.colorToHSL(Integer.valueOf(colorToBrighten.substringAfter("#"), 16), outHSL)

            val increaseBy =
                if (pastEvent) 0.04f
                else 0.18f
            val brightenedColor = ColorUtils.HSLToColor(
                floatArrayOf(outHSL[0], outHSL[1], kotlin.math.max(0f, kotlin.math.min(outHSL[2] + increaseBy, 1.0f)))
            )
            val brightenedColorHex = "#${Integer.toHexString(brightenedColor)}"

            val decryptionFailedPaint = Paint().apply {
                color = Color.parseColor(brightenedColorHex)
                isAntiAlias = true
            }

            val decryptionFailedRect = RectF(
                eventChip.bounds.left + viewState.decryptionFailedRectMargin,
                eventChip.bounds.top + viewState.decryptionFailedRectMargin,
                eventChip.bounds.right - viewState.decryptionFailedRectMargin,
                eventChip.bounds.top + viewState.decryptionFailedRectMargin + viewState.decryptionFailedRectHeight
            )
            val decryptionFailedRadius = viewState.eventCornerRadius.toFloat()
            drawRoundRect(decryptionFailedRect, decryptionFailedRadius, decryptionFailedRadius, decryptionFailedPaint)
        }

        if (textLayout != null) { // textLayout will be null if event chip is too small to display title
            drawEventTitle(eventChip, textLayout)
        }
    }

    private fun Canvas.drawEventTitle(
        eventChip: EventChip,
        textLayout: StaticLayout
    ) {
        val bounds = eventChip.bounds

        val multiDayTimeText =
            if (eventChip.event.isMultiDay && eventChip.event.isNotAllDay && viewState.isSingleDay && eventChip.index == 0) {
                val hour = eventChip.event.startTime.hour
                val minutes = eventChip.event.startTime.minute
                viewState.timeFormatter(hour, minutes)
            } else ""

        val horizontalOffset = if (viewState.isLtr) {
            bounds.left + viewState.eventPaddingHorizontal + viewState.eventSideStripWidth / 2
        } else {
            bounds.right - viewState.eventPaddingHorizontal
        }

        val verticalOffset = if (eventChip.event.isAllDay || eventChip.event.isMultiDay) {
            (bounds.height() - textLayout.height) / 2f
        } else {
            if (eventChip.eventTextDoesNotFit) {
                // If title doesn't fit, reduce vertical padding
                viewState.eventPaddingVertical.toFloat() - eventChip.verticalPaddingReduction / 2
            } else viewState.eventPaddingVertical.toFloat()
        }

        val eventCountText =
            if (viewState.isSingleDay && eventChip.event.isMultiDay) "(${eventChip.index + 1}/${eventChip.event.daysCount})"
            else ""
        val eventCountTextWidth = textLayout.paint.measureText(eventCountText) + viewState.columnGap
        val eventCountMargin = if (eventCountTextWidth > 0) viewState.eventPaddingHorizontal * 2 else 0
        val titleWidth = bounds.width() - viewState.eventPaddingHorizontal - viewState.eventSideStripWidth / 2 - eventCountTextWidth - eventCountMargin
        // Use ellipsize to calculate the max length we can draw
        val ellipsizedTitle =
            if (eventChip.event.isAllDay || eventChip.event.isMultiDay) {
                TextUtils.ellipsize(
                    if (multiDayTimeText.isBlank()) textLayout.text
                    else "$multiDayTimeText ${textLayout.text}",
                    textLayout.paint,
                    titleWidth,
                    TextUtils.TruncateAt.END
                )
            } else {
                textLayout.text
            }

        if (eventCountText.isNotBlank()) {
            withTranslation(
                x = bounds.right - eventCountTextWidth - viewState.eventPaddingHorizontal,
                y = bounds.top + verticalOffset
            ) {
                draw(
                    eventCountText.semibold().toTextLayout(
                        textPaint = textLayout.paint,
                        width = eventCountTextWidth.toInt(),
                        alignment = textLayout.alignment,
                        spacingMultiplier = textLayout.spacingMultiplier,
                        spacingExtra = textLayout.spacingAdd
                    )
                )
            }
        }

        withTranslation(
            x = horizontalOffset,
            y = bounds.top + verticalOffset
        ) {
            draw(
                ellipsizedTitle.semibold().toTextLayout(
                    textPaint = textLayout.paint,
                    width = (bounds.width().roundToInt() - viewState.eventPaddingHorizontal).coerceAtLeast(0),
                    alignment = textLayout.alignment,
                    spacingMultiplier = textLayout.spacingMultiplier,
                    spacingExtra = textLayout.spacingAdd
                )
            )
        }
    }

    private fun updateBackgroundPaint(
        entity: ResolvedWeekViewEntity,
        isBeingDragged: Boolean,
        paint: Paint
    ) = with(paint) {
        color = entity.style.backgroundColor ?: viewState.defaultEventColor
        isAntiAlias = true
        strokeWidth = 0f
        style = Paint.Style.FILL

        if (isBeingDragged) {
            setShadowLayer(12f, 0f, 0f, dragShadow)
        } else {
            clearShadowLayer()
        }
    }

    private fun updateBorderPaint(
        entity: ResolvedWeekViewEntity,
        paint: Paint
    ) = with(paint) {
        color = entity.style.borderColor ?: viewState.defaultEventColor
        isAntiAlias = true
        strokeWidth = entity.style.borderWidth?.toFloat() ?: 0f
        style = Paint.Style.STROKE
    }
}
