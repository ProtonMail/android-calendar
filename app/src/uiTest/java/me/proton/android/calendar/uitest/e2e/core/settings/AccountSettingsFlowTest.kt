package me.proton.android.calendar.uitest.e2e.core.settings

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import me.proton.android.calendar.init.MainInitializer
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.uitest.BaseTest.Companion.sharedPreferences
import me.proton.android.calendar.uitest.BaseTest.Companion.timeZone
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.NotificationPermissionRule
import me.proton.android.calendar.uitest.rule.SharedPreferencesRule
import me.proton.android.calendar.uitest.rule.TimeZoneRule
import me.proton.android.calendar.uitest.verify
import me.proton.core.accountrecovery.dagger.CoreAccountRecoveryFeaturesModule
import me.proton.core.accountrecovery.domain.IsAccountRecoveryEnabled
import me.proton.core.accountrecovery.domain.IsAccountRecoveryResetEnabled
import me.proton.core.test.rule.ProtonRule
import me.proton.core.test.rule.extension.protonActivityScenarioRule
import me.proton.core.usersettings.test.MinimalUserSettingsTest
import me.proton.test.fusion.FusionConfig
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.RuleChain

@HiltAndroidTest
@UninstallModules(CoreAccountRecoveryFeaturesModule::class)
class AccountSettingsFlowTest : MinimalUserSettingsTest {

    override val protonRule: ProtonRule = protonActivityScenarioRule<MainActivity>(
        additionalRules = linkedSetOf(
            TimeZoneRule(timeZone),
            SharedPreferencesRule(sharedPreferences)
        ),
        afterHilt = { MainInitializer.init(it.targetContext) },
        logoutBefore = true
    )

    private val grantPermissionRule = NotificationPermissionRule()

    private val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(grantPermissionRule)
        .around(protonRule)
        .around(composeTestRule)

    @BindValue
    internal val isAccountRecoveryEnabled = mockk<IsAccountRecoveryEnabled> {
        every { this@mockk.invoke(any()) } returns true
    }

    @BindValue
    internal val isAccountRecoveryResetEnabled = mockk<IsAccountRecoveryResetEnabled> {
        every { this@mockk.invoke(any()) } returns true
    }

    init {
        FusionConfig.Compose.testRule.set(composeTestRule)
    }

    private fun startAccountSettings() = HomeRobot
        .verify { robotDisplayed() }
        .clickHamburgerButton()
        .clickSettings()
        .clickAccount()

    override fun startPasswordManagement() {
        startAccountSettings().clickPasswordManagement()
    }

    override fun startRecoveryEmail() {
        startAccountSettings().clickRecoveryEmail()
    }
}
