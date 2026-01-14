package me.proton.android.calendar.uitest

import androidx.test.espresso.NoMatchingViewException
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import org.junit.Before

@HiltAndroidTest
open class AuthenticatedTest : BaseTest() {

    @Before
    fun waitForLogin() {
        try {
            HomeRobot.verify {
                robotDisplayed()
            }
        } catch (ex: NoMatchingViewException) {
            error("Could not log in before test")
        }
    }
}