package me.proton.android.calendar.presentation.importAssistant.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.databinding.ItemCalendarImportMappingBinding
import me.proton.android.calendar.domain.model.Calendar

class MergeCalendarListAdapter(
    val listener: (Calendar) -> Unit
): ListAdapter<Calendar, MergeCalendarListAdapter.ViewHolder>(CalendarDiffCallback()) {

    class CalendarDiffCallback : DiffUtil.ItemCallback<Calendar>() {
        override fun areItemsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemCalendarImportMappingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemBinding: ItemCalendarImportMappingBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val calendarIcon: ImageView = itemBinding.itemCalendarImportMappingIcon
        private val calendarName: TextView = itemBinding.itemCalendarImportMappingName
        private val calendarLayout: LinearLayout = itemBinding.itemCalendarImportMappingLayout

        fun bind(calendar : Calendar) {

            calendarName.text = calendar.name
            calendarIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))

            calendarLayout.setOnSingleClickListener {
                listener(calendar)
            }
        }

    }
}
