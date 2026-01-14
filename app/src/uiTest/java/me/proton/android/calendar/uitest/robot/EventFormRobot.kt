package me.proton.android.calendar.uitest.robot

import androidx.appcompat.widget.AppCompatImageButton
import me.proton.android.calendar.R
import me.proton.core.util.kotlin.EMPTY_STRING
import me.proton.test.fusion.Fusion.view
import kotlin.time.Duration.Companion.seconds

object EventFormRobot : Robot {
    private val eventFormTitleField = view.withId(R.id.event_form_title)
    private val eventFormAllDaySwitch = view.withId(R.id.event_form_all_day_switch)
    private val eventFormAllDayTitle = view.withId(R.id.event_form_all_day_title)
    private val eventFormStartDateLabel = view.withId(R.id.event_form_start_date)
    private val eventFormStartTimeLabel = view.withId(R.id.event_form_start_time)
    private val eventFormEndDateLabel = view.withId(R.id.event_form_end_date)
    private val eventFormEndTimeLabel = view.withId(R.id.event_form_end_time)
    private val eventFormTimeZoneLabel = view.withId(R.id.event_form_timezone)
    private val eventFormRecurrence = view.withId(R.id.event_form_recurrence)
    private val eventFormLocation = view.withId(R.id.event_form_location)
    private val eventFormCalendar = view.withId(R.id.event_form_calendar)
    private val eventFormParticipants = view.withId(R.id.event_form_participant)
    private val eventFormAlarmList = view.withId(R.id.event_form_alarm_list)
    private val eventFormDescription = view.withId(R.id.event_form_description)
    private val saveButton = view.withId(R.id.toolbar_action_text).withText(R.string.calendar_form_save)
    private val addNotification = view.withText(R.string.event_text_add_alarm)
    private val closeButton =
        view
            .instanceOf(AppCompatImageButton::class.java)
            .hasParent(view.withId(R.id.dialog_toolbar))

    private fun notificationInList(notification: NotificationRobot.Notification, formatString: String = EMPTY_STRING) =
        view
            .containsTextIgnoringCase(resourceString(notification.text, formatString))
            .hasAncestor(eventFormAlarmList)

    fun clickSave() = saveButton.clickTo(HomeRobot)
    fun clickClose() = closeButton.clickTo(HomeRobot)
    fun typeTitle(text: String) = apply { eventFormTitleField.scrollTo().typeText(text) }
    fun clickAllDaySwitch() = apply { eventFormAllDaySwitch.scrollTo().click() }
    fun clickAllDayTitle() = apply { eventFormAllDayTitle.scrollTo().click() }
    fun clickEventFormStartDate() = apply { eventFormStartDateLabel.scrollTo().click() }
    fun clickEventFormStartTime() = apply { eventFormStartTimeLabel.scrollTo().click() }
    fun clickEventFormEndDate() = apply { eventFormEndDateLabel.scrollTo().click() }
    fun clickEventFormEndTime() = apply { eventFormEndTimeLabel.scrollTo().click() }
    fun clickEventFormTimezone() = apply { eventFormTimeZoneLabel.scrollTo().click() }
    fun clickEventFormRecurrence() = apply { eventFormRecurrence.scrollTo().click() }
    fun typeEventFormLocation(text: String) = apply { eventFormLocation.scrollTo().typeText(text) }
    fun clickEventFormCalendar() = apply { eventFormCalendar.scrollTo().click() }
    fun clickParticipants() = apply { eventFormParticipants.scrollTo().click() }
    fun typeDescription(text: String) = apply { eventFormDescription.scrollTo().typeText(text) }

    fun clickAddNotification() = addNotification.scrollTo().clickTo(NotificationRobot)

    fun closeKeyboard() = apply { eventFormTitleField.closeKeyboard() }

    fun notificationIsDisplayed(notification: NotificationRobot.Notification) =
        notificationInList(notification).await(90.seconds) { checkIsDisplayed() }
}
