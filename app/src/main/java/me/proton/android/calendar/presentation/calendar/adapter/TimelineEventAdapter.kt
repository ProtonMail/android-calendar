package me.proton.android.calendar.presentation.calendar.adapter

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrInvisible
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatShort
import me.proton.android.calendar.databinding.ItemTimelineBinding
import me.proton.android.calendar.databinding.ItemTimelineHeaderBinding
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TimelineEventAdapter(
    private val clickListener: (TimelineEvent) -> Unit
) : ListAdapter<TimelineEventAdapter.TimelineItem, TimelineEventAdapter.ViewHolder>(TimelineEventDiffCallback()) {

    override fun getItemViewType(position: Int): Int = getCurrentList()[position].type.ordinal

    class TimelineEventDiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return if (oldItem is TimelineItem.Header && newItem is TimelineItem.Header) {
                oldItem.year == newItem.year
            } else if (oldItem is TimelineItem.Event && newItem is TimelineItem.Event) {
                oldItem.event == newItem.event
            } else false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(TimelineItemType.values()[viewType]) {
            TimelineItemType.Header -> HeaderViewHolder(ItemTimelineHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            TimelineItemType.Event -> EventViewHolder(ItemTimelineBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = getCurrentList()[position]) {
            is TimelineItem.Header -> (holder as HeaderViewHolder).bind(item)
            is TimelineItem.Event -> (holder as EventViewHolder).bind(item)
        }
    }

    enum class TimelineItemType {
        Header,
        Event
    }

    sealed class TimelineItem(val type: TimelineItemType) {
        data class Header(val year: Int): TimelineItem(TimelineItemType.Header)
        data class Event(val event: TimelineEvent): TimelineItem(TimelineItemType.Event)
    }

    abstract class ViewHolder(private val itemBinding: ViewBinding): RecyclerView.ViewHolder(itemBinding.root)

    inner class EventViewHolder(private val itemBinding: ItemTimelineBinding): ViewHolder(itemBinding) {
        fun bind(eventItem: TimelineItem.Event) {
            val context = itemBinding.root.context
            val event = eventItem.event
            with(itemBinding) {
                // show or hide date column
                rlEventDayContainer.visibleOrInvisible(event.showDateColumn)
                tvEventDateHeader.text = if (event.showDateColumn) event.happensOn.month.formatShort() else ""
                tvEventDateText.text = if (event.showDateColumn) "${event.happensOn.dayOfMonth}" else ""

                // highlight date column
                if (event.highlightDateColumn) {
                    tvEventDateHeader.setTextColor(context.getColorFromAttr(R.attr.proton_text_accent))
                    tvEventDateText.setTextColor(context.getColorFromAttr(R.attr.proton_text_accent))
                } else {
                    tvEventDateHeader.setTextColor(context.getColorFromAttr(R.attr.proton_text_norm))
                    tvEventDateText.setTextColor(context.getColorFromAttr(R.attr.proton_text_norm))
                }

                // strikethrough if event is cancelled
                if (event.isCancelledOrDeclined) {
                    tvEventHeader.paintFlags = tvEventHeader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvEventSubheader.paintFlags = tvEventSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    tvEventHeader.paintFlags = tvEventHeader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvEventSubheader.paintFlags = tvEventSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                // set summary and time
                tvEventHeader.text = event.summary
                tvEventHeaderDayIndicator.text = if (event.fullDayCounter != null) " " + event.fullDayCounter else ""
                tvEventSubheader.text = event.dateContent
                tvEventSubheaderSideText.text = event.location

                if (event.searchTerm.isNotBlank()) {
                    tvEventHeader.highlightSearchTokens(event.searchTerm.split(" ", ignoreCase = true))
                    tvEventSubheaderSideText.highlightSearchTokens(event.searchTerm.split(" ", ignoreCase = true))
                }

                // clear summary and show views for event that failed decryption
                if (event.failedToDecrypt) tvEventHeader.text = ""
                decryptionErrorView.visibleOrInvisible(event.failedToDecrypt)
                decryptionErrorIcon.visibleOrInvisible(event.failedToDecrypt)

                // add spacing after last event in a day
                vEventSpacing.visibleOrGone(event.showBottomSpacing)

                // set style of calendar bar
                if (event.needsAction) {
                    ivCalendarBar.setBackgroundResource(R.drawable.ic_calendar_bar_unanswered)
                } else {
                    ivCalendarBar.setBackgroundResource(R.drawable.shape_calendar_bar)
                }

                // tint calendar bar
                ivCalendarBar.background.setTint(Color.parseColor(event.color))

            }

            itemBinding.rlEvent.setOnSingleClickListener {
                clickListener(eventItem.event)
            }
        }
    }

    inner class HeaderViewHolder(private val itemBinding: ItemTimelineHeaderBinding): ViewHolder(itemBinding){
        fun bind(headerItem: TimelineItem.Header) {
            itemBinding.textHeader.text = headerItem.year.toString()
        }
    }

    data class TimelineEvent(
        val id: String,
        val summary: String,
        val dateContent: String,
        val location: String,
        val isCancelledOrDeclined: Boolean,
        val needsAction: Boolean,
        val failedToDecrypt: Boolean,
        // LocalDate that this Event spans, not necessarily the same as dateStart
        val happensOn: LocalDate,
        val showDateColumn: Boolean,
        val highlightDateColumn: Boolean,
        val showBottomSpacing: Boolean,
        val fullDayCounter: String?,
        val occurrenceNumber: Int,
        val color: String,
        val searchTerm: String
    )

}

/**
 * Finds index of first element happening today.
 * @param [zoneId] used to determine what "today" is
 */
fun List<TimelineEventAdapter.TimelineItem>.findIndexToScrollTo(zoneId: ZoneId): Int {

    // we need to adjust list position relative to "today"
    val today = LocalDate.now(zoneId)

    val binaryIndex = this.binarySearchBy(0) {
        when (it) {
            is TimelineEventAdapter.TimelineItem.Event -> ChronoUnit.DAYS.between(today, it.event.happensOn).toInt()
            is TimelineEventAdapter.TimelineItem.Header -> ChronoUnit.DAYS.between(today, LocalDate.of(it.year, 1, 1)).toInt()
        }
    }
    val nonNegativeBinaryIndex = if (binaryIndex < 0) {
        (binaryIndex + 1) * -1
    } else binaryIndex

    var positionToScrollTo = if (nonNegativeBinaryIndex > this.size - 1) this.size - 1 else nonNegativeBinaryIndex

    val binaryItemToScrollTo = if (positionToScrollTo >= 0 && positionToScrollTo <= this.lastIndex) this[positionToScrollTo] else null
    val binaryItemToScrollToLocalDate = if (binaryItemToScrollTo is TimelineEventAdapter.TimelineItem.Event) {
        binaryItemToScrollTo.event.happensOn
    } else if (binaryItemToScrollTo is TimelineEventAdapter.TimelineItem.Header) {
        LocalDate.of(binaryItemToScrollTo.year, 1, 1)
    } else {
        today // can only happen if list was empty, not valid anyway
    }

    // item on the list found with binary search might not be the first today, we need to traverse up,
    // until we find the first item on that day
    while (positionToScrollTo > 0) {
        val listItem = this[positionToScrollTo]
        if (listItem is TimelineEventAdapter.TimelineItem.Event) {
            if (listItem.event.happensOn.isBefore(binaryItemToScrollToLocalDate)) {
                positionToScrollTo++
                break
            } else {
                positionToScrollTo--
            }
        } else break
    }

    return positionToScrollTo
}
