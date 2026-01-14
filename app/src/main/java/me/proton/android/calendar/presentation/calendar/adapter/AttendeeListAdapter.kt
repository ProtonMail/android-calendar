package me.proton.android.calendar.presentation.calendar.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import biweekly.parameter.ParticipationLevel
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.getInitials
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.databinding.ItemAttendeeBinding
import me.proton.android.calendar.domain.model.Event

class AttendeeListAdapter(
    val canonicalUserEmails: List<String>?,
    val attendeeComments: Map<String, Pair<Event.SignatureVerification, String?>>,
    val rsvpCommentsEnabled: Boolean
)
    : ListAdapter<Attendee, AttendeeListAdapter.ViewHolder>(AttendeeDiffCallback()) {

    class AttendeeDiffCallback : DiffUtil.ItemCallback<Attendee>() {
        override fun areItemsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem.extractEmail() == newItem.extractEmail()
        }

        override fun areContentsTheSame(oldItem: Attendee, newItem: Attendee): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val itemBinding = ItemAttendeeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position)
    }

    inner class ViewHolder(itemBinding: ItemAttendeeBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        private val attendeeItemTitle: TextView = itemBinding.itemAttendeeTitle
        private val attendeeItemDescription: TextView = itemBinding.itemAttendeeDescription
        private val attendeeItemInitials: TextView = itemBinding.itemAttendeeInitials
        private val attendeeItemStatus: ImageView = itemBinding.itemAttendeeStatus
        private val attendeeItemOptional: TextView = itemBinding.itemAttendeeOptional
        private val attendeeCommentIcon: ImageView = itemBinding.itemAttendeeCommentIcon
        private val attendeeComment: TextView = itemBinding.itemAttendeeComment
        private val attendeeCommentFailedVerificationIcon: ImageView = itemBinding.itemAttendeeFailedVerificationIcon

        fun bind(attendee : Attendee, position : Int) {
            val context = itemView.context
            // If has common name use it, else use email and hide description field
            val attendeeEmail = attendee.extractEmail()
            val title =
                if (attendee.commonName.isNullOrEmpty()) attendeeEmail ?: ""
                else attendee.commonName
            val description =
                if (attendee.commonName.isNullOrEmpty() ||
                    attendee.commonName.equals(attendeeEmail, ignoreCase = true)) ""
                else attendeeEmail ?: ""

            val attendeeIsCurrentUser = attendeeEmail?.let {
                canonicalUserEmails?.contains(
                    ProtonUtilsImpl.canonicalizeProtonEmail(
                        attendeeEmail,
                        forceCanonicalization = true
                    )
                ) == true
            } ?: false
            attendeeItemTitle.text =
                if (attendeeIsCurrentUser) context.getString(R.string.event_attendee_is_current_user)
                else title
            attendeeItemDescription.visibleOrGone(description.isNotEmpty() || attendeeIsCurrentUser)
            if (description.isNotEmpty() || attendeeIsCurrentUser) {
                attendeeItemDescription.text =
                    if (attendeeIsCurrentUser) attendeeEmail
                    else description
            }

            attendeeItemInitials.text = getInitials(title)

            if (attendee.participationLevel != null && attendee.participationLevel == ParticipationLevel.OPTIONAL) {
                if (attendeeItemDescription.visibility != View.VISIBLE) {
                    // Use description to display optional label if we only have the title,
                    //  in order to keep the correct alignment
                    attendeeItemOptional.visibleOrGone(false)
                    attendeeItemDescription.visibleOrGone(true)
                    attendeeItemDescription.text = context.getString(R.string.event_attendee_optional)
                } else {
                    attendeeItemOptional.visibleOrGone(true)
                }
            } else {
                attendeeItemOptional.visibleOrGone(false)
            }

            initAttendeeComment(attendee, attendeeCommentIcon, attendeeComment, attendeeCommentFailedVerificationIcon, attendeeComments, rsvpCommentsEnabled)
            initAttendeeStatus(attendeeItemStatus, attendee.participationStatus ?: ParticipationStatus.NEEDS_ACTION, context)
        }
    }
}

fun initAttendeeComment(
    attendee: Attendee?,
    commentIcon: ImageView,
    comment: TextView,
    failedVerificationIcon: ImageView,
    attendeeComments: Map<String, Pair<Event.SignatureVerification, String?>>,
    rsvpCommentsEnabled: Boolean
) {
    if (!rsvpCommentsEnabled) {
        commentIcon.visibleOrGone(false)
        comment.visibleOrGone(false)
        failedVerificationIcon.visibleOrGone(false)
        return
    }
    val attendeeCommentText = attendee?.extractEmail()?.let { attendeeComments[it]?.second }
    if (attendeeCommentText?.isBlank() == false) {
        commentIcon.visibleOrGone(true)
        comment.visibleOrGone(true)
        comment.text = attendeeCommentText
        // TODO bring back warning icon when we get comment signatures
//        val failedVerification = attendee.extractEmail()?.let { attendeeComments[it]?.first }
//        if (failedVerification != Event.SignatureVerification.SUCCESS) {
//            failedVerificationIcon.visibleOrGone(true)
//        }
    } else {
        commentIcon.visibleOrGone(false)
        comment.visibleOrGone(false)
        failedVerificationIcon.visibleOrGone(false)
    }
}

fun initAttendeeStatus(attendeeItemStatus: ImageView, participationStatus: ParticipationStatus, context: Context) {
    attendeeItemStatus.visibleOrGone(true)
    when (participationStatus) {
        ParticipationStatus.ACCEPTED -> {
            attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_check_circle_filled))
        }
        ParticipationStatus.DECLINED -> {
            attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_clear_circle_filled))
        }
        ParticipationStatus.TENTATIVE -> {
            attendeeItemStatus.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_question_circle_filled))
        }
        else ->  attendeeItemStatus.visibleOrGone(false)
    }
}
