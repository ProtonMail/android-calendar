package me.proton.android.calendar.presentation.calendar.adapter

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.property.Attendee
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.getInitials
import me.proton.android.calendar.common.utils.AndroidUtils.highlightSearchTokens
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.databinding.ItemAddAttendeeBinding

class AddAttendeeListAdapter(
    private val searchList: Boolean = false,
    private val readOnly: Boolean = false,
    private val clickListener: (Attendee) -> Unit
) : ListAdapter<Attendee, AddAttendeeListAdapter.ViewHolder>(AddAttendeeDiffCallback()) {

    private var attendeeList: List<Attendee> = arrayListOf()
    private var query: String = ""
    private var organizerEmail: String = ""

    class AddAttendeeDiffCallback : DiffUtil.ItemCallback<Attendee>() {
        override fun areItemsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem.extractEmail() == newItem.extractEmail()
        }

        override fun areContentsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemAddAttendeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(itemBinding: ItemAddAttendeeBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val attendeeItemTextLayout: LinearLayout = itemBinding.itemAddAttendeeTextLayout
        private val attendeeItemTitle: TextView = itemBinding.itemAddAttendeeTitle
        private val attendeeItemDescription: TextView = itemBinding.itemAddAttendeeDescription
        private val attendeeItemInitials: TextView = itemBinding.itemAddAttendeeInitials
        private val attendeeItemIconDelete: ImageView = itemBinding.itemAddAttendeeDeleteIcon
        private val attendeeItemIconLoading: ProgressBar = itemBinding.itemAddAttendeeLoadingIcon
        private val attendeeItemIconCheck: ImageView = itemBinding.itemAddAttendeeCheckIcon
        private val attendeeItemPress: View = itemBinding.itemAddAttendeePress.root

        fun bind(attendee : Attendee, position : Int) {
            val context = itemView.context
            val isOrganizer = organizerEmail.equals(attendee.extractEmail(), true)
            // If has common name use it, else use email and hide description field
            val title =
                when {
                    !searchList && isOrganizer -> {
                        context.getString(R.string.event_current_user_organizer)
                    }
                    attendee.commonName.isNullOrEmpty() -> attendee.extractEmail() ?: ""
                    else -> attendee.commonName
                }
            val description =
                if (!isOrganizer && (attendee.commonName.isNullOrEmpty() ||
                            attendee.commonName.equals(attendee.extractEmail(), ignoreCase = true))) ""
                else attendee.extractEmail() ?: ""

            attendeeItemTitle.text = title
            attendeeItemTitle.highlightSearchTokens(listOf(query))

            attendeeItemDescription.visibleOrGone(description.isNotEmpty())
            val textLayoutParams = attendeeItemTextLayout.layoutParams as ConstraintLayout.LayoutParams
            if (description.isEmpty()) {
                // Update margins if description is hidden
                textLayoutParams.topMargin = context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin_large)
                textLayoutParams.bottomMargin = context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin_large)
            } else {
                // Update margins if description is visible
                textLayoutParams.topMargin = context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin)
                textLayoutParams.bottomMargin = context.resources.getDimensionPixelSize(R.dimen.attendee_item_vertical_margin)
            }
            attendeeItemTextLayout.layoutParams = textLayoutParams

            if (description.isNotEmpty()) {
                if (query.isNotEmpty() && description.contains(query, true)) {
                    val spannableStringBuilder = SpannableStringBuilder(description)
                    spannableStringBuilder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        description.indexOf(query, ignoreCase = true),
                        description.indexOf(query, ignoreCase = true) + query.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    attendeeItemDescription.text = spannableStringBuilder
                } else attendeeItemDescription.text = description
            }

            attendeeItemInitials.text =
                if (!searchList && isOrganizer) getInitials(attendee.commonName)
                else getInitials(title)

            val added = attendeeList.firstOrNull { it.extractEmail().equals(attendee.extractEmail(), true) && !isOrganizer } != null

            attendeeItemIconCheck.visibleOrGone(searchList && added)
            attendeeItemIconLoading.visibleOrGone(false)

            if (readOnly) {
                attendeeItemIconDelete.visibleOrGone(false)
                attendeeItemIconDelete.setOnSingleClickListener {
                    // Do nothing
                }
            } else {
                attendeeItemIconDelete.visibleOrGone(!searchList && !isOrganizer)
                attendeeItemIconDelete.setOnSingleClickListener {
                    if (!searchList) clickListener(attendee)
                }
            }

            attendeeItemPress.visibleOrGone(searchList && !added)
            attendeeItemPress.setOnSingleClickListener {
                if (searchList) {
                    attendeeItemIconLoading.visibleOrGone(true)
                    clickListener(attendee)
                }
            }
        }
    }

    fun setAttendeeList(attendeeList: List<Attendee>) {
        this.attendeeList = attendeeList
    }

    fun setQuery(query: String) {
        this.query = query
    }

    fun setOrganizerEmail(organizerEmail: String) {
        this.organizerEmail = organizerEmail
    }
}
