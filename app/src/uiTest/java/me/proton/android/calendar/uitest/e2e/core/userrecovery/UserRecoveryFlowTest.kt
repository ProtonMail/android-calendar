package me.proton.android.calendar.uitest.e2e.core.userrecovery

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import me.proton.android.calendar.uitest.BaseTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.android.calendar.uitest.rule.NotificationPermissionRule
import me.proton.core.auth.test.usecase.WaitForPrimaryAccount
import me.proton.core.domain.entity.UserId
import me.proton.core.userrecovery.dagger.CoreDeviceRecoveryFeaturesModule
import me.proton.core.userrecovery.domain.IsDeviceRecoveryEnabled
import me.proton.core.userrecovery.domain.repository.DeviceRecoveryRepository
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryHandler
import me.proton.core.userrecovery.presentation.compose.DeviceRecoveryNotificationSetup
import me.proton.core.userrecovery.test.MinimalUserRecoveryTest
import me.proton.core.util.android.sentry.TimberLogger
import me.proton.core.util.kotlin.CoreLogger
import me.proton.test.fusion.FusionConfig
import org.junit.Rule
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(CoreDeviceRecoveryFeaturesModule::class)
class UserRecoveryFlowTest : BaseTest(), MinimalUserRecoveryTest {
    @get:Rule(order = Rule.DEFAULT_ORDER - 2)
    val grantPermissionRule = NotificationPermissionRule()

    @get:Rule(order = Rule.DEFAULT_ORDER - 1)
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    @Inject
    override lateinit var deviceRecoveryHandler: DeviceRecoveryHandler

    @Inject
    override lateinit var deviceRecoveryNotificationSetup: DeviceRecoveryNotificationSetup

    @Inject
    override lateinit var deviceRecoveryRepository: DeviceRecoveryRepository

    @Inject
    override lateinit var waitForPrimaryAccount: WaitForPrimaryAccount

    @BindValue
    internal val isDeviceRecoveryEnabled = object : IsDeviceRecoveryEnabled {
        override fun invoke(userId: UserId?): Boolean = true
        override fun isLocalEnabled(): Boolean = true
        override fun isRemoteEnabled(userId: UserId?): Boolean = true
    }

    init {
        FusionConfig.Compose.testRule.set(composeTestRule)
        FusionConfig.Compose.useUnmergedTree.set(true)
        CoreLogger.set(TimberLogger)
        Timber.plant(Timber.DebugTree())
    }

    override fun signOut() {
        HomeRobot
            .clickHamburgerButton()
            .clickLogOut()
    }
}
