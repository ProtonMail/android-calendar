package me.proton.android.calendar.common

import androidx.recyclerview.widget.DiffUtil
import me.proton.android.calendar.domain.model.BaseModel

class GenericDiffCallback<T : BaseModel> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
        return oldItem.equals(newItem) // TODO figure out generic way of determining if contents changed? timestamp?
    }
}
