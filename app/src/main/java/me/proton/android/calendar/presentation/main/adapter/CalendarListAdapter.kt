package me.proton.android.calendar.presentation.main.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.databinding.ItemDrawerCalendarBinding
import me.proton.android.calendar.domain.model.Calendar

class CalendarListAdapter(
    val listener: (Calendar) -> Unit
) : ListAdapter<Calendar, CalendarListAdapter.ViewHolder>(CalendarDiffCallback()) {

    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null

    class CalendarDiffCallback : DiffUtil.ItemCallback<Calendar>() {
        override fun areItemsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemDrawerCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    fun setCalendarSubscriptions(calendarSubscriptions: List<CalendarSubscriptionEntity>): Boolean {
        val dataSetChanged = this.calendarSubscriptions != calendarSubscriptions
        this.calendarSubscriptions = calendarSubscriptions
        return dataSetChanged
    }

    inner class ViewHolder(itemBinding: ItemDrawerCalendarBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val calendarItemOverlay: View = itemBinding.itemDrawerCalendarPress
        private val calendarItemTitle: TextView = itemBinding.itemDrawerCalendarTitle
        private val calendarItemCheckBox: CheckBox = itemBinding.itemDrawerCalendarCheckbox

        fun bind(calendar : Calendar) {
            val context = itemView.context
            if (calendar.isDisabled) {
                // For subscribed calendars we prioritize displaying disabled label over not synced
                calendarItemTitle.text = context.getString(R.string.nav_view_disabled_calendars, calendar.name)
                calendarItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.sidebar_text_weak))
            } else if (calendar.isSubscribed) {
                val calendarSubscription = calendarSubscriptions?.firstOrNull { it.calendarId == calendar.id }

                if (calendarSubscription?.lastUpdateTime == 0 || calendarSubscription?.isSyncing == true) {
                    // Calendar is syncing
                    calendarItemTitle.text = context.getString(R.string.nav_view_syncing_calendars, calendar.name)
                    calendarItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.sidebar_text_weak))
                } else if (calendarSubscription?.isSynced == true) {
                    // Synced
                    calendarItemTitle.text = calendar.name
                    calendarItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.sidebar_text_norm))
                } else {
                    // Not synced
                    calendarItemTitle.text = context.getString(R.string.nav_view_not_synced_calendars, calendar.name)
                    calendarItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.sidebar_text_weak))
                }
            } else {
                calendarItemTitle.text = calendar.name
                calendarItemTitle.setTextColor(ContextCompat.getColor(itemView.context, R.color.sidebar_text_norm))
            }
            calendarItemCheckBox.isChecked = calendar.display
            calendarItemCheckBox.buttonTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))
            setCheckboxStyle(calendarItemCheckBox, calendar)

            calendarItemCheckBox.setOnSingleClickListener {
                setCheckboxStyle(calendarItemCheckBox, calendar)
                listener(
                    calendar.copy(
                        display = calendarItemCheckBox.isChecked
                    )
                )
            }

            calendarItemOverlay.setOnSingleClickListener {
                calendarItemCheckBox.performClick()
            }
        }

        private fun setCheckboxStyle(checkBox: CheckBox, calendar: Calendar) {
            if (checkBox.isChecked) {
                checkBox.background =
                    ContextCompat.getDrawable(itemView.context, R.drawable.shape_checkbox_nav_drawer)
                checkBox.backgroundTintList = null
            } else {
                checkBox.background =
                    ContextCompat.getDrawable(itemView.context, R.drawable.ic_checkbox_off)
                checkBox.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor(calendar.color))
            }
        }
    }
}
