package me.proton.android.calendar.uitest.e2e.calendar.event

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.AuthenticatedTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.robot.NotificationRobot.Notification.AtTimeOfEvent
import me.proton.android.calendar.uitest.verify
import me.proton.core.test.performance.annotation.Measure
import me.proton.core.test.performance.measurement.DurationMeasurement
import me.proton.core.test.rule.annotation.PrepareUser
import me.proton.core.test.rule.annotation.TestUserData
import org.junit.Test

@HiltAndroidTest
class PerformanceMetricsTests : AuthenticatedTest() {

    @Test
    @Measure
    @PrepareUser(loginBefore = true, userData = TestUserData(name = "pro", password = "pro"))
    fun performanceMetricsTest() {
        val profile = measurementContext
            .setWorkflow("android_calendar")
            .setServiceLevelIndicator("test_of_performance_metrics")
            .addMeasurement(DurationMeasurement())

        profile.measure {
            val eventName = "Event made from PerformanceMetricsTests"
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
                    eventIsDisplayed(eventName, null)
                    robotDisplayed()
                }
        }
    }
}