package me.proton.android.calendar.presentation.holidayCalendar.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.databinding.ItemHolidayCalendarBinding
import me.proton.android.calendar.databinding.ItemHolidayCalendarHeaderBinding
import me.proton.android.calendar.domain.model.Holiday

class HolidayCalendarListAdapter(
    val listener: (Holiday) -> Unit
): ListAdapter<HolidayCalendarListAdapter.HolidayItem, HolidayCalendarListAdapter.ViewHolder>(HolidayDiffCallback()) {

    private var searchQuery: String = ""

    override fun getItemViewType(position: Int): Int = currentList[position].type.ordinal

    class HolidayDiffCallback : DiffUtil.ItemCallback<HolidayItem>() {
        override fun areItemsTheSame(oldItem: HolidayItem, newItem: HolidayItem): Boolean {
            return oldItem.type == newItem.type
        }

        override fun areContentsTheSame(oldItem: HolidayItem, newItem: HolidayItem): Boolean {
            return if (oldItem is HolidayItem.Header && newItem is HolidayItem.Header) {
                oldItem.letter == newItem.letter
            } else if (oldItem is HolidayItem.Value && newItem is HolidayItem.Value) {
                oldItem.holiday == newItem.holiday
            } else false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when(HolidayItemType.values()[viewType]) {
            HolidayItemType.Header -> HeaderViewHolder(ItemHolidayCalendarHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
            HolidayItemType.Value -> HolidayViewHolder(ItemHolidayCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val item = currentList[position]) {
            is HolidayItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HolidayItem.Value -> (holder as HolidayViewHolder).bind(item)
        }
    }

    fun setSearchQuery(searchQuery: String) {
        val queryChanged = this.searchQuery != searchQuery
        this.searchQuery = searchQuery
        if (queryChanged) notifyDataSetChanged()
    }

    enum class HolidayItemType {
        Header,
        Value
    }

    sealed class HolidayItem(val type: HolidayItemType) {
        data class Header(val letter: String, val basedOnTimeZone: Boolean): HolidayItem(HolidayItemType.Header)
        data class Value(val holiday: Holiday): HolidayItem(HolidayItemType.Value)
    }

    abstract class ViewHolder(itemBinding: ViewBinding): RecyclerView.ViewHolder(itemBinding.root)

    inner class HolidayViewHolder(itemBinding: ItemHolidayCalendarBinding): ViewHolder(itemBinding) {
        private val holidayFlag: ImageView = itemBinding.itemHolidayCalendarCountryFlag
        private val holidayCountryName: TextView = itemBinding.itemHolidayCalendarCountry
        private val holidayPress: View = itemBinding.itemHolidayCalendarPress.root

        fun bind(value: HolidayItem.Value) {
            val holiday = value.holiday

            holidayCountryName.text = holiday.country
            if (searchQuery.isNotBlank()) {
                holidayCountryName.highlightSearchTokens(searchQuery.split(" ", ignoreCase = true))
            }

            holidayFlag.setImageResource(holiday.flagDrawable)

            holidayPress.setOnSingleClickListener {
                listener(holiday)
            }
        }
    }

    inner class HeaderViewHolder(itemBinding: ItemHolidayCalendarHeaderBinding): ViewHolder(itemBinding) {
        private val holidayCalendarHeaderText: TextView = itemBinding.itemHolidayCalendarHeaderText

        fun bind(headerItem: HolidayItem.Header) {
            val context = itemView.context
            holidayCalendarHeaderText.text =
                if (headerItem.basedOnTimeZone) context.getString(R.string.holiday_calendar_time_zone_based)
                else headerItem.letter
        }
    }

}
