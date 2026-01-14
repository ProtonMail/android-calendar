package me.proton.android.calendar.uitest.e2e.calendar.event

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.AuthenticatedTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.robot.NotificationRobot.Notification.AtTimeOfEvent
import me.proton.android.calendar.uitest.verify
import me.proton.core.test.rule.annotation.PrepareUser
import org.junit.Test

@HiltAndroidTest
class CreateEventTests : AuthenticatedTest() {

    @Test
    @PrepareUser(loginBefore = true)
    fun createPartDayEventWithAddedSameTimeNotification() {
        val eventName = "Event"
        HomeRobot
            .clickAddEvent()
            .closeKeyboard()
            .clickAddNotification()
            .clickDone()
            .verify {
                notificationIsDisplayed(AtTimeOfEvent)
            }
            .typeTitle(eventName)
            .clickSave()
            .clickHamburgerButton()
            .clickAgenda()
            .verify {
                eventIsDisplayed(eventName, 1)
                robotDisplayed()
            }
    }
}