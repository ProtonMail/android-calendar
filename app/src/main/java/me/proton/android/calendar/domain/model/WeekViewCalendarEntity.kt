package me.proton.android.calendar.domain.model

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import androidx.core.content.ContextCompat
import biweekly.parameter.ParticipationStatus
import com.alamkanak.weekview.WeekViewEntity
import com.alamkanak.weekview.setEndTime
import com.alamkanak.weekview.setStartTime
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

sealed class WeekViewCalendarEntity {

    data class Event(
        val id: String,
        val title: CharSequence,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val location: CharSequence,
        val color: Int,
        val isAllDay: Boolean,
        val strikeThroughTitle: Boolean,
        val isPastEvent: Boolean,
        val isUnanswered: Boolean,
        val calendarId: String,
        val decrypted: Boolean,
        val isRecurring: Boolean,
        val occurrenceNumber: Int? = null
    ) : WeekViewCalendarEntity()

    data class BlockedTimeSlot(
        val id: String,
        val startTime: LocalDateTime,
        val endTime: LocalDateTime
    ) : WeekViewCalendarEntity()
}

const val OCCURRENCE_NUMBER_SUFFIX = "&occurrenceNumber="
const val BIS_SUFFIX = "&bis"
const val CUSTOM_FONT = "sans-serif-medium"

fun WeekViewCalendarEntity.Event.getActualEventId(): String {
    val occurrenceNumberIndex = id.indexOf(OCCURRENCE_NUMBER_SUFFIX)
    val bisIndex = id.indexOf(BIS_SUFFIX)
    return id.substring(
        0,
        if (bisIndex >= 0) bisIndex
        else if (occurrenceNumberIndex >= 0) occurrenceNumberIndex
        else id.length
    )
}

fun UiEvent.toWeekViewCalendarEntityEvent(defaultEventTitle: String): List<WeekViewCalendarEntity.Event> {
    // We add the occurrence number as a suffix to the event id so that week view doesn't recycle events with the same id, and so that we easily find the event occurrence on click
    val occurrenceNumberSuffix = if (!this.isRecurring) "" else OCCURRENCE_NUMBER_SUFFIX + this.occurrenceNumber
    val weekViewEventId = this.id + occurrenceNumberSuffix
    val sameDay = this.dateEnd.toLocalDate().isEqual(this.dateStart.toLocalDate())
    val lastAtLeast24Hours = this.dateEnd.toInstant().toEpochMilli() - this.dateStart.toInstant().toEpochMilli() >= TimeUnit.HOURS.toMillis(24)
    return if (sameDay || lastAtLeast24Hours) {
        listOf(
            WeekViewCalendarEntity.Event(
                id = weekViewEventId,
                // Use empty title for failed to decrypt event state
                title = if (this.decryptionStatus is Event.DecryptionStatus.Failure) "" else this.summary ?: defaultEventTitle,
                location = "",
                startTime = this.dateStart.toLocalDateTime(),
                endTime = this.dateEnd.toLocalDateTime(),
                color = Color.parseColor(this.displayColor),
                isAllDay = this.isAllDay,
                strikeThroughTitle = this.isCancelled() || participationStatus == ParticipationStatus.DECLINED,
                isPastEvent = this.isInThePast(),
                isUnanswered = !this.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
                calendarId = this.calendarId,
                decrypted = this.decryptionStatus == Event.DecryptionStatus.Success,
                isRecurring = this.isRecurring,
                occurrenceNumber = if (this.occurrenceNumber == 0) null else this.occurrenceNumber
            )
        )
    } else {
        // If event last less than 24h but happens over two days, we split it in two
        val startEvent = WeekViewCalendarEntity.Event(
            id = weekViewEventId,
            // Use empty title for failed to decrypt event state
            title = if (this.decryptionStatus is Event.DecryptionStatus.Failure) "" else this.summary ?: defaultEventTitle,
            location = "",
            startTime = this.dateStart.toLocalDateTime(),
            endTime = this.dateStart.with(LocalTime.MAX).toLocalDateTime(),
            color = Color.parseColor(this.displayColor),
            isAllDay = this.isAllDay,
            strikeThroughTitle = this.isCancelled() || participationStatus == ParticipationStatus.DECLINED,
            isPastEvent = this.isInThePast(),
            isUnanswered = !this.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
            calendarId = this.calendarId,
            decrypted = this.decryptionStatus == Event.DecryptionStatus.Success,
            isRecurring = this.isRecurring,
            occurrenceNumber = if (this.occurrenceNumber == 0) null else this.occurrenceNumber
        )
        val weekViewEventBisId = this.id + BIS_SUFFIX + occurrenceNumberSuffix
        val endEvent = startEvent.copy(
            id = weekViewEventBisId,
            startTime = this.dateEnd.with(LocalTime.MIN).toLocalDateTime(),
            endTime = this.dateEnd.toLocalDateTime()
        )
        listOf(
            startEvent,
            endEvent
        )
    }
}

fun WeekViewCalendarEntity.toWeekViewEntity(context: Context): WeekViewEntity {
    return when (this) {
        is WeekViewCalendarEntity.Event -> toWeekViewEntity(context)
        is WeekViewCalendarEntity.BlockedTimeSlot -> toWeekViewEntity()
    }
}

fun WeekViewCalendarEntity.Event.toWeekViewEntity(context: Context): WeekViewEntity {
    val backgroundColor =
        if (isUnanswered || (strikeThroughTitle && decrypted)) ContextCompat.getColor(context, R.color.background_norm)
        else if (isPastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
        else color
    val textColor =
        if (isPastEvent) ContextCompat.getColor(context, R.color.text_weak)
        else if (isUnanswered || strikeThroughTitle) ContextCompat.getColor(context, R.color.text_norm)
        else ContextCompat.getColor(context, R.color.text_on_calendar_color)
    val borderWidthResId = if (!strikeThroughTitle) R.dimen.week_view_no_border_width else R.dimen.week_view_border_width
    val borderColor = Color.parseColor(
        AndroidUtils.darkenCalendarColor(
            "#${Integer.toHexString(color and 0x00ffffff)}"
        )
    )

    val styleBuilder = WeekViewEntity.Style.Builder()
        .setTextColor(textColor)
        .setBackgroundColor(backgroundColor)
        .setBorderWidthResource(borderWidthResId)
        .setBorderColor(borderColor)

    if (isUnanswered) {
        styleBuilder.setStripesColorResource(
            if (isPastEvent) ContextCompat.getColor(context, R.color.interaction_weak_norm)
            else Color.parseColor(
                AndroidUtils.brightenCalendarColor(
                    "#${Integer.toHexString(color and 0x00ffffff)}",
                    MonthView.MonthViewSettings.UNANSWERED_STRIPES_BRIGHTEN_COLOR_BY
                )
            )
        )
    }

    val style = styleBuilder.build()

    val title = SpannableStringBuilder(title).apply {
        val titleSpan = TypefaceSpan(CUSTOM_FONT)
        setSpan(titleSpan, 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (strikeThroughTitle) {
            setSpan(StrikethroughSpan(), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    val subtitle = SpannableStringBuilder(location).apply {
        if (strikeThroughTitle) {
            setSpan(StrikethroughSpan(), 0, location.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    return WeekViewEntity.Event.Builder(this)
        .setId(id)
        .setTitle(title)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setSubtitle(subtitle)
        .setAllDay(isAllDay)
        .setStyle(style)
        .build()
}

fun WeekViewCalendarEntity.BlockedTimeSlot.toWeekViewEntity(): WeekViewEntity {
    val style = WeekViewEntity.Style.Builder()
        .setBackgroundColorResource(R.color.background_secondary)
        .setCornerRadius(0)
        .build()

    return WeekViewEntity.BlockedTime.Builder()
        .setId(id)
        .setStartTime(startTime)
        .setEndTime(endTime)
        .setStyle(style)
        .build()
}

