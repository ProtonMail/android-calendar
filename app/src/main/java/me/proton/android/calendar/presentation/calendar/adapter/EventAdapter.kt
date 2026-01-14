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
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import biweekly.parameter.ParticipationStatus
import me.proton.android.calendar.R
import me.proton.android.calendar.common.GenericDiffCallback
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.setStripedBackground
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatFullDayCounter
import me.proton.android.calendar.databinding.ItemAgendaEventAllDayBinding
import me.proton.android.calendar.databinding.ItemAgendaEventHeaderBinding
import me.proton.android.calendar.databinding.ItemAgendaEventPartialDayBinding
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.presentation.calendar.adapter.EventAdapter.EventViewHolder.HeaderViewHolder
import me.proton.core.util.kotlin.nullIfBlank
import java.time.LocalDate
import java.time.ZoneId

class EventAdapter(
    private val clickListener: ((UiEvent) -> Unit)?
) : ListAdapter<UiEvent, EventAdapter.EventViewHolder>(GenericDiffCallback()) {

    private var timeZoneId: String? = null
    private var is24Hour: Boolean? = null
    private var date: LocalDate? = null

    fun setDate(date: LocalDate) {
        this.date = date
    }

    fun setTimeZoneId(timeZoneId: String) {
        this.timeZoneId = timeZoneId
    }

    fun setTimeFormatIs24Hour(is24Hour: Boolean) {
        this.is24Hour = is24Hour
    }

    sealed class EventViewHolder(itemBinding: ViewBinding) : RecyclerView.ViewHolder(itemBinding.root) {

        class HeaderViewHolder(itemBinding: ItemAgendaEventHeaderBinding) : EventViewHolder(itemBinding) {
            private val textHeader: TextView = itemBinding.textHeader
            fun bind(date: LocalDate, timeZoneId: String) {
                val context = itemView.context
                if (date == LocalDate.now(ZoneId.of(timeZoneId))) {
                    textHeader.setTextColor(context.getColorFromAttr(R.attr.proton_text_accent))
                } else {
                    textHeader.setTextColor(ContextCompat.getColor(context, R.color.text_norm))
                }
                textHeader.text = context.getString(R.string.agenda_header_date, date.formatDayOfWeek(), date.dayOfMonth)
            }
        }

        class PartialDayEventViewHolder(itemBinding: ItemAgendaEventPartialDayBinding) : EventViewHolder(itemBinding) {

            private val imageViewIcon: View = itemBinding.imageIcon
            private val textViewHeader: TextView = itemBinding.textHeader
            private val textViewSubheader: TextView = itemBinding.textSubheader
            private val textViewSubheaderSide: TextView = itemBinding.textSubheaderSide

            private val decryptionErrorIcon: ImageView = itemBinding.decryptionErrorIcon
            private val decryptionErrorView: View = itemBinding.decryptionErrorView

            fun bind(event: UiEvent, timeZoneId: String, is24Hour: Boolean, date: LocalDate, clickListener: ((UiEvent) -> Unit)?) {
                val context = itemView.context
                val participationStatus = event.participationStatus

                if (!event.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION) {
                    imageViewIcon.setBackgroundResource(R.drawable.ic_calendar_bar_unanswered)
                } else {
                    imageViewIcon.setBackgroundResource(R.drawable.shape_calendar_bar)
                }
                imageViewIcon.background.setTint(Color.parseColor(event.displayColor))

                textViewHeader.text =
                    "${(event.dateStart.withZoneSameInstant(
                        ZoneId.of(timeZoneId)
                    ))?.formatTime(timeZoneId, is24Hour)} ‐ ${(event.dateEnd.withZoneSameInstant(
                        ZoneId.of(timeZoneId)
                    ))?.formatTime(timeZoneId, is24Hour)}"

                textViewSubheader.text = event.summary?.nullIfBlank() ?: context.resources.getString(R.string.default_event_summary)

                if (event.spansSingleDay()) {
                    textViewSubheaderSide.visibleOrGone(false)
                } else {
                    textViewSubheaderSide.text = event.formatFullDayCounter(date)
                    textViewSubheaderSide.visibleOrGone(true)
                }

                if (event.decryptionStatus is Event.DecryptionStatus.Failure) {
                    decryptionErrorIcon.visibleOrGone(true)
                    decryptionErrorView.visibleOrGone(true)
                    textViewSubheader.visibleOrGone(false)
                } else {
                    decryptionErrorIcon.visibleOrGone(false)
                    decryptionErrorView.visibleOrGone(false)
                    textViewSubheader.visibleOrGone(true)
                }

                if (event.isInThePast()) {
                    textViewHeader.setTextAppearance(context, R.style.Text_DefaultSmall_Weak)
                    textViewSubheader.setTextAppearance(context, R.style.Text_Default_Weak)
                    textViewSubheaderSide.setTextAppearance(context, R.style.Text_Default_Weak)
                    ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_weak)))
                } else {
                    textViewHeader.setTextAppearance(context, R.style.Text_DefaultSmall)
                    textViewSubheader.setTextAppearance(context, R.style.Text_Default)
                    textViewSubheaderSide.setTextAppearance(context, R.style.Text_Default)
                }

                if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                itemView.setOnSingleClickListener { clickListener?.invoke(event) }
            }
        }

        // TODO this viewholder can actually is used also for partial-day events, that span more than one day
        class AllDayEventViewHolder(itemBinding: ItemAgendaEventAllDayBinding) : EventViewHolder(itemBinding) {

            private val viewBackground: LayerDrawable = itemBinding.viewBackground.background as LayerDrawable
            private val viewMainSurface: Drawable = viewBackground.findDrawableByLayerId(R.id.main_surface)
            private val viewSideStrip: Drawable = viewBackground.findDrawableByLayerId(R.id.side_strip)

            private val textViewHeader: TextView = itemBinding.textHeader
            private val textViewSubheader: TextView = itemBinding.textSubheader
            private val textViewSubheaderSide: TextView = itemBinding.textSubheaderSide

            private val viewBackgroundStripedLayout: CardView = itemBinding.viewBackgroundStripedLayout
            private val viewBackgroundStriped: View = itemBinding.viewBackgroundStriped

            private val decryptionErrorIcon: ImageView = itemBinding.decryptionErrorIcon
            private val decryptionErrorView: View = itemBinding.decryptionErrorView

            // TODO consider databinding
            fun bind(event: UiEvent, timeZoneId: String, is24Hour: Boolean, date: LocalDate, clickListener: ((UiEvent) -> Unit)?) {
                val context = itemView.context
                val participationStatus = event.participationStatus
                viewBackgroundStripedLayout.visibleOrGone(!event.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION)

                if (!event.isAllDay && !event.spansSingleDay()) {
                    val fullDayCounter = event.calculateFullDayCounter(date)
                    if (fullDayCounter.first == 1) { // this is the first day of an ongoing event
                        textViewHeader.visibleOrGone(true)
                        textViewHeader.text = "${(event.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)))?.formatTime(timeZoneId, is24Hour)}"
                    } else { // this is second or later day of an ongoing event
                        textViewHeader.visibleOrGone(false)
                    }
                } else {
                    textViewHeader.visibleOrGone(false)
                }

                textViewSubheader.text = event.summary?.nullIfBlank() ?: context.resources.getString(R.string.default_event_summary)

                if (event.spansSingleDay()) {
                    textViewSubheaderSide.visibleOrGone(false)
                } else {
                    textViewSubheaderSide.text = event.formatFullDayCounter(date)
                    textViewSubheaderSide.visibleOrGone(true)
                }

                if (event.decryptionStatus is Event.DecryptionStatus.Failure) {
                    decryptionErrorIcon.visibleOrGone(true)
                    decryptionErrorView.visibleOrGone(true)
                    textViewHeader.visibleOrGone(false)
                    textViewSubheader.visibleOrGone(false)
                } else {
                    decryptionErrorIcon.visibleOrGone(false)
                    decryptionErrorView.visibleOrGone(false)
                    textViewSubheader.visibleOrGone(true)
                }

                viewSideStrip.setTint(Color.parseColor(AndroidUtils.darkenCalendarColor(event.displayColor)))

                if (event.isInThePast()) {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall_Weak)
                    textViewSubheader.setTextAppearance(R.style.Text_Default_Weak)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default_Weak)
                    ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.icon_weak)))

                    if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                        viewMainSurface.setTint(ContextCompat.getColor(context, R.color.background_norm))
                    } else if (event.decryptionStatus == Event.DecryptionStatus.Success && participationStatus == ParticipationStatus.NEEDS_ACTION) {
                        viewMainSurface.setTint(ContextCompat.getColor(context, R.color.background_norm))
                        setStripedBackground(
                            viewBackgroundStriped,
                            context,
                            ContextCompat.getColor(context, R.color.shade_60)
                        ) // striped background with 20% opacity for unanswered all day events
                    } else {
                        viewMainSurface.setTint(ContextCompat.getColor(context, R.color.background_secondary))
                        decryptionErrorView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_norm))
                        decryptionErrorView.alpha = 0.1f
                    }
                } else {
                    textViewHeader.setTextAppearance(R.style.Text_DefaultSmall)
                    textViewSubheader.setTextAppearance(R.style.Text_Default)
                    textViewSubheaderSide.setTextAppearance(R.style.Text_Default)

                    if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                        viewMainSurface.setTint(ContextCompat.getColor(context, R.color.background_norm))
                    } else if (event.decryptionStatus == Event.DecryptionStatus.Success && participationStatus == ParticipationStatus.NEEDS_ACTION) {
                        viewMainSurface.setTint(ContextCompat.getColor(context, R.color.background_norm))
                        textViewHeader.setTextColor(ContextCompat.getColor(context, R.color.text_norm))
                        textViewSubheader.setTextColor(ContextCompat.getColor(context, R.color.text_norm))
                        textViewSubheaderSide.setTextColor(ContextCompat.getColor(context, R.color.text_norm))
                        setStripedBackground(
                            viewBackgroundStriped,
                            context,
                            Color.parseColor(event.displayColor)
                        ) // striped background with 20% opacity for unanswered all day events
                    } else {
                        viewMainSurface.setTint(Color.parseColor(event.displayColor))
                        textViewHeader.setTextColor(ContextCompat.getColor(context, R.color.text_on_calendar_color))
                        textViewSubheader.setTextColor(ContextCompat.getColor(context, R.color.text_on_calendar_color))
                        textViewSubheaderSide.setTextColor(ContextCompat.getColor(context, R.color.text_on_calendar_color))
                        ImageViewCompat.setImageTintList(decryptionErrorIcon, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_on_calendar_color)))
                        decryptionErrorView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_on_calendar_color))
                        decryptionErrorView.alpha = 0.2f
                    }
                }

                if (event.decryptionStatus == Event.DecryptionStatus.Success && (event.isCancelled() || participationStatus == ParticipationStatus.DECLINED)) {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    textViewHeader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    textViewSubheader.paintFlags = textViewSubheader.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }

                itemView.setOnSingleClickListener { clickListener?.invoke(event) }
            }
        }
    }

    private val ITEM_TYPE_HEADER = 0
    private val ITEM_TYPE_EVENT_PARTIAL_DAY = 1
    private val ITEM_TYPE_EVENT_ALL_DAY = 2

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            ITEM_TYPE_HEADER
        } else if (getItem(position).isAllDay || !getItem(position).spansSingleDay()) {
            ITEM_TYPE_EVENT_ALL_DAY
        } else {
            ITEM_TYPE_EVENT_PARTIAL_DAY
        }
    }

    // accommodating header view
//    override fun getItemCount(): Int {
//        return super.getItemCount() + 1
//    }
//
//    override fun getItem(position: Int): Event {
//        return super.getItem(position - 1)
//    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int /*later when we have more view types*/
    ): EventViewHolder {
        return if (viewType == ITEM_TYPE_HEADER) {
            val itemBinding = ItemAgendaEventHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderViewHolder(itemBinding)
        } else if (viewType == ITEM_TYPE_EVENT_PARTIAL_DAY) {
            val itemBinding = ItemAgendaEventPartialDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            EventViewHolder.PartialDayEventViewHolder(itemBinding)
        } else {
            val itemBinding = ItemAgendaEventAllDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            EventViewHolder.AllDayEventViewHolder(itemBinding)
        }
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {

        val immutableTimeZoneId = timeZoneId
        val immutableTimeFormatIs24Hour = is24Hour
        val immutableDate = date
        if (immutableTimeZoneId == null || immutableTimeFormatIs24Hour == null || immutableDate == null) return

        when (holder) {
            is EventViewHolder.HeaderViewHolder -> holder.bind(
                immutableDate,
                immutableTimeZoneId
            )
            is EventViewHolder.PartialDayEventViewHolder -> holder.bind(
                getItem(position),
                immutableTimeZoneId,
                immutableTimeFormatIs24Hour,
                immutableDate,
                clickListener
            )
            is EventViewHolder.AllDayEventViewHolder -> holder.bind(
                getItem(position),
                immutableTimeZoneId,
                immutableTimeFormatIs24Hour,
                immutableDate,
                clickListener
            )
        }

    }
}

