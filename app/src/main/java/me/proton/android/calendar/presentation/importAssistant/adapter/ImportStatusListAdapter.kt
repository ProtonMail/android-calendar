package me.proton.android.calendar.presentation.importAssistant.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.humanReadableByteCountBin
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.databinding.ItemImportStatusBinding
import me.proton.android.calendar.domain.model.Import
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class ImportStatusListAdapter(
    val is24Hour: Boolean,
    val listener: (Import, Action) -> Unit
): ListAdapter<Import, ImportStatusListAdapter.ViewHolder>(ImportDiffCallback()) {

    enum class Action {
        CANCEL,
        RESUME,
        DELETE
    }

    class ImportDiffCallback : DiffUtil.ItemCallback<Import>() {
        override fun areItemsTheSame(oldItem: Import, newItem: Import): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Import, newItem: Import): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemImportStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(itemBinding: ItemImportStatusBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val account: TextView = itemBinding.itemImportStatusAccount
        private val details: TextView = itemBinding.itemImportStatusDetails
        private val badge: View = itemBinding.itemImportStatusBadge.root
        private val icon: ImageView = itemBinding.itemImportStatusIcon
        private val warningLayout: LinearLayout = itemBinding.itemImportStatusWarning
        private val warningTitle: TextView = itemBinding.itemImportStatusWarningTitle
        private val warningDescription: TextView = itemBinding.itemImportStatusWarningDescription
        private val warningButton: TextView = itemBinding.itemImportStatusWarningButton

        fun bind(import : Import) {
            val context = itemView.context
            account.text = import.account
            val importSize = humanReadableByteCountBin(import.size?.toLong() ?: 0L)
            val date = import.dateTime?.toLocalDate()?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            val time = import.dateTime?.toLocalTime()?.formatTime(is24Hour, short = false)
            val dateTime = context.getString(
                R.string.import_assistant_report_details_date_time,
                date,
                time
            )
            details.text =
                if (import.size != null) {
                    context.getString(
                        R.string.import_assistant_report_details,
                        importSize,
                        dateTime
                    )
                } else dateTime

            icon.visibleOrGone(import.state != null)
            badge.visibleOrGone(import.state != null)
            warningLayout.visibleOrGone(import.state != null)
            if (import.state != null) {
                (badge as TextView).text = context.getString(
                    when (import.state) {
                        Import.ImportState.QUEUED -> R.string.import_assistant_status_queued
                        Import.ImportState.RUNNING -> R.string.import_assistant_status_in_progress
                        Import.ImportState.DONE -> R.string.import_assistant_status_completed
                        Import.ImportState.FAILED -> R.string.import_assistant_status_failed
                        Import.ImportState.PAUSED -> R.string.import_assistant_status_paused
                        Import.ImportState.CANCELED -> R.string.import_assistant_status_canceled
                        Import.ImportState.CANCELING -> R.string.import_assistant_status_canceling
                    }
                )
                badge.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        when (import.state) {
                            Import.ImportState.QUEUED,
                            Import.ImportState.RUNNING -> R.color.text_norm
                            Import.ImportState.DONE,
                            Import.ImportState.PAUSED,
                            Import.ImportState.FAILED,
                            Import.ImportState.CANCELED,
                            Import.ImportState.CANCELING -> R.color.text_inverted
                        }
                    )
                )
                badge.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        itemView.context,
                        when (import.state) {
                            Import.ImportState.QUEUED,
                            Import.ImportState.RUNNING -> R.color.background_secondary
                            Import.ImportState.DONE -> R.color.notification_success
                            Import.ImportState.FAILED,
                            Import.ImportState.CANCELED,
                            Import.ImportState.CANCELING -> R.color.notification_error
                            Import.ImportState.PAUSED -> R.color.notification_warning
                        }
                    )
                )

                if (import.state == Import.ImportState.CANCELING) {
                    // When cancelling, we hide the icon
                    icon.visibleOrGone(false)
                } else {
                    icon.visibleOrGone(true)
                    icon.setImageDrawable(
                        ContextCompat.getDrawable(
                            itemView.context,
                            when (import.state) {
                                Import.ImportState.QUEUED,
                                Import.ImportState.RUNNING,
                                Import.ImportState.CANCELING -> R.drawable.ic_proton_cross
                                Import.ImportState.PAUSED -> {
                                    if (import.lostConnection || import.storageFull) {
                                        // Icon displayed is the cross and action is cancel for those two cases, as the resume button is in the view below
                                        R.drawable.ic_proton_cross
                                    } else R.drawable.ic_proton_play
                                }
                                Import.ImportState.DONE,
                                Import.ImportState.FAILED,
                                Import.ImportState.CANCELED -> R.drawable.ic_proton_trash
                            }
                        )
                    )
                }

                icon.setOnSingleClickListener {
                    when (import.state) {
                        Import.ImportState.QUEUED,
                        Import.ImportState.RUNNING -> listener(import, Action.CANCEL)
                        Import.ImportState.PAUSED -> {
                            if (import.lostConnection || import.storageFull) {
                                // Icon displayed is the cross and action is cancel for those two cases, as the resume button is in the view below
                                listener(import, Action.CANCEL)
                            } else listener(import, Action.RESUME)
                        }
                        Import.ImportState.DONE,
                        Import.ImportState.FAILED,
                        Import.ImportState.CANCELED -> listener(import, Action.DELETE)
                        Import.ImportState.CANCELING -> {} // Do nothing as icon should be hidden anyway
                    }
                }

                if (import.lostConnection) {
                    warningLayout.visibleOrGone(true)
                    warningTitle.text =
                        context.getString(R.string.import_assistant_status_paused_lost_connection_title)
                    warningDescription.text =
                        context.getString(R.string.import_assistant_status_paused_lost_connection_description)
                    warningButton.text =
                        context.getString(R.string.import_assistant_status_paused_lost_connection_button)
                    warningButton.setOnSingleClickListener {
                        listener(import, Action.RESUME)
                    }
                } else if (import.storageFull) {
                    warningLayout.visibleOrGone(true)
                    warningTitle.text =
                        context.getString(R.string.import_assistant_status_paused_storage_full_title)
                    warningDescription.text =
                        context.getString(R.string.import_assistant_status_paused_storage_full_description)
                    warningButton.text =
                        context.getString(R.string.import_assistant_status_paused_storage_full_button)
                    warningButton.setOnSingleClickListener {
                        listener(import, Action.RESUME)
                    }
                } else {
                    warningLayout.visibleOrGone(false)
                }
            }
        }
    }
}
