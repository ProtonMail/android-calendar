package me.proton.android.calendar.uitest.robot

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageButton
import me.proton.android.calendar.R
import me.proton.core.util.kotlin.EMPTY_STRING
import me.proton.test.fusion.Fusion.view
import me.proton.test.fusion.ui.espresso.builders.OnView

object NotificationRobot : Robot {

    private val doneButton get() = view.withId(R.id.toolbar_action_text).withText(R.string.action_done)
    private val closeButton
        get() =
            view
                .instanceOf(AppCompatImageButton::class.java)
                .hasParent(view.withId(R.id.dialog_toolbar))
    private val sendByNotification = view.withId(R.id.event_form_alarm_action_notification)
    private val sendByEmail = view.withId(R.id.event_form_alarm_action_email)

    fun clickDone() = doneButton.clickTo(EventFormRobot)

    fun clickClose() = closeButton.clickTo(EventFormRobot)

    private fun notification(notification: Notification, formatString: String = EMPTY_STRING): OnView =
        view.withId(notification.id).withText(resourceString(notification.text, formatString))

    fun clickNotification(notification: Notification, formatString: String = EMPTY_STRING) = apply {
        notification(notification, formatString).click()
    }

    enum class Notification(@IdRes val id: Int, @StringRes val text: Int) {
        /** All day reminders **/
        SameDayAt(R.id.event_form_alarm_1, R.string.event_alarm_all_day_1),
        OneDayBeforeAt(R.id.event_form_alarm_2, R.string.event_alarm_all_day_2),
        OneWeekBeforeAt(R.id.event_form_alarm_3, R.string.event_alarm_all_day_3),
        ThreeWeeksBeforeAt(R.id.event_form_alarm_4, R.string.event_alarm_all_day_4),

        /** Same day reminders **/
        AtTimeOfEvent(R.id.event_form_alarm_1, R.string.event_alarm_partial_day_1),
        TenMinutes(R.id.event_form_alarm_2, R.string.event_alarm_partial_day_2),
        ThirtyMinutes(R.id.event_form_alarm_3, R.string.event_alarm_partial_day_3),
        OneHour(R.id.event_form_alarm_4, R.string.event_alarm_partial_day_4),
        OneWeek(R.id.event_form_alarm_5, R.string.event_alarm_partial_day_5)
    }
}