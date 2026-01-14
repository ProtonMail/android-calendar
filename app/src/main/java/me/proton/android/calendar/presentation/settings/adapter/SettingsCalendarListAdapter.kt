package me.proton.android.calendar.presentation.settings.adapter

import android.content.res.ColorStateList
import android.graphics.Color
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
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionStatus
import me.proton.android.calendar.databinding.ItemBadgeBinding
import me.proton.android.calendar.databinding.ItemSettingsCalendarBinding
import me.proton.android.calendar.domain.model.Calendar

class SettingsCalendarListAdapter(
    val listener: (Calendar) -> Unit
) : ListAdapter<Calendar, SettingsCalendarListAdapter.ViewHolder>(CalendarDiffCallback()) {

    private var calendarSubscriptions: List<CalendarSubscriptionEntity> = emptyList()
    private var defaultCalendarId: String = ""

    class CalendarDiffCallback : DiffUtil.ItemCallback<Calendar>() {
        override fun areItemsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Calendar, newItem: Calendar): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemSettingsCalendarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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

    fun setDefaultCalendarId(defaultCalendarId: String?): Boolean {
        val dataSetChanged = this.defaultCalendarId != defaultCalendarId
        this.defaultCalendarId = defaultCalendarId ?: ""
        return dataSetChanged
    }

    inner class ViewHolder(itemBinding: ItemSettingsCalendarBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val calendarItemPress: View = itemBinding.itemSettingsCalendarPress
        private val calendarItemTitle: TextView = itemBinding.itemSettingsCalendarTitle
        private val calendarItemOwnerEmail: TextView = itemBinding.itemSettingsCalendarOwnerEmail
        private val calendarItemUserEmail: TextView = itemBinding.itemSettingsCalendarUserEmail
        private val calendarItemHelper: TextView = itemBinding.itemSettingsCalendarHelper
        private val calendarItemMenuIcon: ImageView = itemBinding.itemSettingsCalendarMenuIcon
        private val calendarItemIcon: ImageView = itemBinding.itemSettingsCalendarIcon
        private val calendarItemBadgeLayout: LinearLayout = itemBinding.itemSettingsCalendarBadgeLayout

        fun bind(calendar : Calendar) {
            val context = itemView.context

            // Calendar name
            calendarItemTitle.text = calendar.name

            // Calendar owner email
            val showCalendarOwnerEmail = calendar.isSharedWithMe && !calendar.ownerEmail.isNullOrEmpty()
            calendarItemOwnerEmail.visibleOrGone(showCalendarOwnerEmail)
            if (showCalendarOwnerEmail) {
                calendarItemOwnerEmail.text =  context.getString(R.string.settings_shared_calendars_owner, calendar.ownerEmail)
            }

            // Calendar user email
            calendarItemUserEmail.visibleOrGone(calendar.email.isNotEmpty())
            calendarItemUserEmail.text =
                if (!calendar.isHolidayCalendar && !calendar.allowEditEvents) {
                    context.getString(
                        R.string.settings_other_calendars_subtitle,
                        context.getString(R.string.settings_other_calendars_read_only),
                        calendar.email
                    )
                } else calendar.email

            // Set colored calendar dot tint
            calendarItemIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))

            // Clear badges
            calendarItemBadgeLayout.removeAllViews()

            // Display default badge
            if (calendar.id == defaultCalendarId && calendar.isDisabled.not()) {
                addBadge(
                    context.getString(R.string.settings_calendar_default),
                    context.getColorFromAttr(R.attr.brand_norm)
                )
            }

            // Display disabled badge
            if (calendar.isDisabled) addBadge(context.getString(R.string.settings_calendar_disabled), context.getColor(R.color.background_secondary), R.color.text_norm)

            calendarItemHelper.visibleOrGone(false)
            if (calendar.isSubscribed) {
                val calendarSubscription = calendarSubscriptions?.firstOrNull { it.calendarId == calendar.id }

                // Display not synced badge
                if (calendarSubscription?.isSynced == false) {
                    /*
                    Priority as followed:
                    - LastUpdateTime == 0 -> Syncing
                    - isLastSyncOld -> Not synced + helper message
                    - Status == 7 -> Syncing
                    - Status > 0 -> Not synced + helper message if existing
                     */
                    addBadge(
                        context.getString(
                            if (calendarSubscription.lastUpdateTime == 0 ||
                                (calendarSubscription.status == CalendarSubscriptionStatus.SYNCING.value && calendarSubscription.isLastSyncOld.not()))
                                R.string.settings_calendar_syncing
                            else R.string.settings_calendar_not_synced
                        ),
                        context.getColor(R.color.notification_warning)
                    )
                    val helperMessage =
                        if (calendarSubscription.isLastSyncOld)
                            context.getString(R.string.settings_calendar_subscribed_last_sync_old)
                        else {
                            when (calendarSubscription.status) {
                                CalendarSubscriptionStatus.INVALID_ICS.value -> {
                                    context.getString(R.string.settings_calendar_subscribed_invalid_ics)
                                }
                                CalendarSubscriptionStatus.SIZE_EXCEED_LIMIT.value -> {
                                    context.getString(R.string.settings_calendar_subscribed_too_big)
                                }
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_BAD_REQUEST.value,
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_UNAUTHORIZED.value,
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_FORBIDDEN.value,
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_NOT_FOUND.value,
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_TEST.value -> {
                                    context.getString(R.string.settings_calendar_subscribed_not_accessible)
                                }
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_GENERIC_ERROR.value,
                                CalendarSubscriptionStatus.HTTP_REQUEST_FAILED_INTERNAL_SERVER_ERROR.value -> {
                                    context.getString(R.string.settings_calendar_subscribed_tmp_not_accessible)
                                }
                                CalendarSubscriptionStatus.P2P_LINK_NOT_FOUND.value,
                                CalendarSubscriptionStatus.UNABLE_TO_DECRYPT.value -> {
                                    context.getString(R.string.settings_calendar_subscribed_not_decrypted)
                                }
                                else -> {
                                    null
                                }
                            }
                        }

                    calendarItemHelper.visibleOrGone(!helperMessage.isNullOrEmpty())
                    calendarItemHelper.text = helperMessage
                }
            }

            val calendarSettingsCanBeEdited = (calendar.isSubscribed.not() && calendar.isOwner) || // my own personal calendar
                    calendar.isSubscribed || // subscribed calendar
                    (calendar.isSharedWithMe && CalendarFeatureFlag.EditingSharedCalendars.fallbackValue) || // shared calendar
                    calendar.isHolidayCalendar

            // Only show menu icon when calendar can be edited
            calendarItemMenuIcon.visibleOrGone(calendarSettingsCanBeEdited)

            // Only allow item click when calendar can be edited
            calendarItemPress.visibleOrGone(calendarSettingsCanBeEdited)

            if (calendarSettingsCanBeEdited) {
                // On item click
                calendarItemPress.setOnSingleClickListener {
                    listener(calendar)
                }

                // On menu icon click
                calendarItemMenuIcon.setOnSingleClickListener {
                    listener(calendar)
                }
            }
        }

        private fun addBadge(text: String, color: Int, textColor: Int? = null) {
            val badgeViewBinding = ItemBadgeBinding.inflate(
                LayoutInflater.from(itemView.context),
                calendarItemBadgeLayout,
                false
            )
            val badgeTextView = badgeViewBinding.root
            badgeTextView.text = text
            textColor?.let {
                badgeTextView.setTextColor(ContextCompat.getColor(itemView.context, it))
            }
            badgeTextView.backgroundTintList = ColorStateList.valueOf(color)
            calendarItemBadgeLayout.addView(badgeTextView)
        }
    }
}
