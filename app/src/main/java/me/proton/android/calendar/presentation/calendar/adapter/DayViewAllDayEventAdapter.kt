package me.proton.android.calendar.presentation.calendar.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationStatus
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.databinding.ItemDayViewEventAllDayBinding
import me.proton.android.calendar.domain.model.Event
import java.time.LocalDate

class DayViewAllDayEventAdapter(
    private val clickListener: (Event) -> Unit
) : ListAdapter<Event, DayViewAllDayEventAdapter.ViewHolder>(EventDiffCallback()) {

    private var userEmails: List<String>? = null
    private var timeZoneId: String? = null
    private var date: LocalDate? = null
    private var timeFormatIs24Hour: Boolean? = null

    fun setUserEmails(userEmails: List<String>) {
        this.userEmails = userEmails
    }

    fun setTimeZoneId(timeZoneId: String) {
        this.timeZoneId = timeZoneId
    }

    fun setTimeFormatIs24Hour(timeFormatIs24Hour: Boolean) {
        this.timeFormatIs24Hour = timeFormatIs24Hour
    }

    fun setDate(date: LocalDate) {
        this.date = date
    }

    class EventDiffCallback : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemDayViewEventAllDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(itemBinding: ItemDayViewEventAllDayBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val eventItemTitle: TextView = itemBinding.textTitle
        private val eventItemTitleSide: TextView = itemBinding.textTitleSide

        private val viewBackground: LayerDrawable = itemBinding.viewBackground.background as LayerDrawable
        private val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
        private val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)

        private val viewBackgroundStripedLayout: CardView = itemBinding.viewBackgroundStripedLayout
        private val viewBackgroundStriped: View = itemBinding.viewBackgroundStriped

        private val decryptionErrorIcon: ImageView = itemBinding.decryptionErrorIcon
        private val decryptionErrorView: View = itemBinding.decryptionErrorView

        fun bind(event : Event, position : Int) {
            val context = itemView.context
            val immutableTimeZoneId = timeZoneId
            val immutableTimeFormatIs24Hour = timeFormatIs24Hour
            val immutableDate = date
            if (immutableTimeZoneId == null || immutableTimeFormatIs24Hour == null || immutableDate == null) return

            val participationStatus = event.getParticipationStatus(userEmails ?: arrayListOf())
            viewBackgroundStripedLayout.visibleOrGone(!event.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION)

            eventItemTitle.text = if (event.summary.isNullOrEmpty()) context.getString(R.string.default_event_summary) else event.summary
            if (!event.spansSingleDay(timeZoneId = immutableTimeZoneId)) {
                if (event.getStart(immutableTimeZoneId).toLocalDate() == date && !event.isAllDay()) {
                    eventItemTitle.text = context.getString(
                        R.string.multiple_days_event_summary,
                        event.getStart(immutableTimeZoneId).formatTime(immutableTimeZoneId, immutableTimeFormatIs24Hour),
                        eventItemTitle.text
                    )
                }
                eventItemTitleSide.text = event.formatFullDayCounter(immutableDate, immutableTimeZoneId)
                eventItemTitleSide.visibleOrGone(true)
            } else eventItemTitleSide.visibleOrGone(false)

            if (event.decryptionStatus is Event.DecryptionStatus.Failure) {
                decryptionErrorIcon.visibleOrGone(true)
                decryptionErrorView.visibleOrGone(true)
                eventItemTitle.visibleOrGone(false)
            } else {
                decryptionErrorIcon.visibleOrGone(false)
                decryptionErrorView.visibleOrGone(false)
            }

            viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.calendar.color)))

            if (event.isInThePast(immutableTimeZoneId)) {
                eventItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_weak))
                eventItemTitleSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_weak))
                ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.icon_weak)))

                if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                    viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                } else if (event.decryptionStatus == Event.DecryptionStatus.Success && participationStatus == ParticipationStatus.NEEDS_ACTION) {
                    viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                    AndroidUtils.setStripedBackground(
                        viewBackgroundStriped,
                        itemView.context,
                        ContextCompat.getColor(itemView.context, R.color.shade_60)
                    ) // striped background with 20% opacity for unanswered all day events
                } else {
                    viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_secondary))
                    decryptionErrorView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_norm))
                    decryptionErrorView.alpha = 0.1f
                }
            } else {
                if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                    eventItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                    eventItemTitleSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                    viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                } else if (event.decryptionStatus == Event.DecryptionStatus.Success && participationStatus == ParticipationStatus.NEEDS_ACTION) {
                    viewMainSurface.setTint(ContextCompat.getColor(itemView.context, R.color.background_norm))
                    eventItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                    eventItemTitleSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_norm))
                    AndroidUtils.setStripedBackground(
                        viewBackgroundStriped,
                        itemView.context,
                        Color.parseColor(event.calendar.color)
                    ) // striped background with 20% opacity for unanswered all day events
                } else {
                    viewMainSurface.setTint(Color.parseColor(event.calendar.color))
                    eventItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                    eventItemTitleSide.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                    ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color)))
                    decryptionErrorView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.text_on_calendar_color))
                    decryptionErrorView.alpha = 0.2f
                }
            }

            if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                eventItemTitle.paintFlags = eventItemTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                eventItemTitle.paintFlags = eventItemTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            itemView.setOnSingleClickListener { clickListener(event) }
        }
    }
}
