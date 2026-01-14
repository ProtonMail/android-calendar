package me.proton.android.calendar.uitest.e2e.core.plan

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.init.MainInitializer
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.uitest.BaseTest.Companion.sharedPreferences
import me.proton.android.calendar.uitest.BaseTest.Companion.timeZone
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.SharedPreferencesRule
import me.proton.android.calendar.uitest.rule.TimeZoneRule
import me.proton.android.calendar.uitest.verify
import me.proton.core.plan.test.MinimalSubscriptionTests
import me.proton.core.plan.test.robot.SubscriptionRobot
import me.proton.core.test.rule.ProtonRule
import me.proton.core.test.rule.extension.protonActivityScenarioRule
import org.junit.Rule

@HiltAndroidTest
class SubscriptionTests : MinimalSubscriptionTests() {

    @get:Rule
    val protonRule: ProtonRule = protonActivityScenarioRule<MainActivity>(
        additionalRules = linkedSetOf(
            TimeZoneRule(timeZone),
            SharedPreferencesRule(sharedPreferences)
        ),
        afterHilt = { MainInitializer.init(it.targetContext) },
        logoutBefore = true
    )

    override fun startSubscription(): SubscriptionRobot {
        HomeRobot
            .verify { robotDisplayed() }
            .clickHamburgerButton()
            .clickSubscriptions()
        return SubscriptionRobot
    }
}
