package me.proton.android.calendar.presentation.importAssistant.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.databinding.ItemImportCalendarBinding
import me.proton.android.calendar.domain.model.ImportCalendarMapping

class ImportCalendarMappingListAdapter(
    val importListener: (ImportCalendarMapping) -> Unit,
    val optionsListener: (ImportCalendarMapping) -> Unit
) : ListAdapter<ImportCalendarMapping, ImportCalendarMappingListAdapter.ViewHolder>(ExternalCalendarEntityDiffCallback()) {

    private var limitReached = false
    private var optionsEnabled = false

    fun setLimitReached(limitReached: Boolean) {
        val valueChanged = this.limitReached != limitReached
        this.limitReached = limitReached
        if (valueChanged) notifyDataSetChanged()
    }

    fun isOptionsEnabled(optionsEnabled: Boolean) {
        val valueChanged = this.optionsEnabled != optionsEnabled
        this.optionsEnabled = optionsEnabled
        if (valueChanged) notifyDataSetChanged()
    }

    class ExternalCalendarEntityDiffCallback : DiffUtil.ItemCallback<ImportCalendarMapping>() {
        override fun areItemsTheSame(oldItem: ImportCalendarMapping, newItem: ImportCalendarMapping): Boolean {
            return oldItem.sourceId == newItem.sourceId && oldItem.destinationId == newItem.destinationId
        }

        override fun areContentsTheSame(oldItem: ImportCalendarMapping, newItem: ImportCalendarMapping): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemImportCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemBinding: ItemImportCalendarBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val sourceTitle: TextView = itemBinding.itemImportCalendarSourceTitle
        private val sourceEmail: TextView = itemBinding.itemImportCalendarSourceEmail
        private val importCheckBox: CheckBox = itemBinding.itemImportCalendarCheckbox
        private val primaryPressOverlay: View = itemBinding.itemImportCalendarPress.root
        private val secondaryPressOverlay: View = itemBinding.itemImportCalendarSecondaryPress.root
        private val destinationLayout: ConstraintLayout = itemBinding.itemImportCalendarDestinationLayout
        private val destinationTitle: TextView = itemBinding.itemImportCalendarDestinationTitle
        private val destinationEmail: TextView = itemBinding.itemImportCalendarDestinationEmail
        private val destinationBadgeView: View = itemBinding.itemImportCalendarDestinationBadge.root
        private val destinationPressOverlay: View = itemBinding.itemImportCalendarDestinationPress
        private val destinationOptionsButton: ImageView = itemBinding.itemImportCalendarDestinationOptions

        fun bind(importCalendarMapping : ImportCalendarMapping) {
            sourceTitle.setTextColor(
                ContextCompat.getColor(itemView.context,
                    if (importCalendarMapping.importCalendar) R.color.text_norm
                    else R.color.text_weak
                )
            )
            sourceTitle.text = importCalendarMapping.sourceName
            sourceEmail.text = importCalendarMapping.sourceEmail

            destinationLayout.visibleOrGone(importCalendarMapping.importCalendar)
            destinationTitle.text = importCalendarMapping.destinationName
            destinationEmail.text = importCalendarMapping.destinationEmail
            (destinationBadgeView as TextView).text = itemView.context.getString(
                if (importCalendarMapping.createDestinationCalendar) R.string.import_assistant_new_calendar_badge
                else R.string.import_assistant_merge_calendar_badge
            )

            importCheckBox.isChecked = importCalendarMapping.importCalendar
            importCheckBox.setOnSingleClickListener {
                importListener(importCalendarMapping)
            }

            primaryPressOverlay.visibleOrGone(!importCalendarMapping.importCalendar)
            primaryPressOverlay.setOnSingleClickListener {
                importCheckBox.performClick()
            }
            secondaryPressOverlay.visibleOrGone(importCalendarMapping.importCalendar)
            secondaryPressOverlay.setOnSingleClickListener {
                importCheckBox.performClick()
            }

            destinationOptionsButton.visibleOrGone(optionsEnabled)
            destinationPressOverlay.setOnSingleClickListener {
                if (optionsEnabled) optionsListener(importCalendarMapping)
            }

            destinationOptionsButton.setOnSingleClickListener {
                if (optionsEnabled) optionsListener(importCalendarMapping)
            }

            if (limitReached && importCalendarMapping.createDestinationCalendar) {
                destinationPressOverlay.background = ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.shape_background_secondary_rounded_error
                )
            } else {
                destinationPressOverlay.background = ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.shape_background_secondary_rounded
                )
            }
        }

    }
}
