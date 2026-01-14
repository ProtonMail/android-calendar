package me.proton.android.calendar.uitest.e2e.core.accountrecovery

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import me.proton.android.calendar.uitest.BaseTest
import me.proton.android.calendar.uitest.rule.NotificationPermissionRule
import me.proton.core.accountmanager.data.AccountStateHandler
import me.proton.core.accountrecovery.dagger.CoreAccountRecoveryFeaturesModule
import me.proton.core.accountrecovery.domain.IsAccountRecoveryEnabled
import me.proton.core.accountrecovery.domain.IsAccountRecoveryResetEnabled
import me.proton.core.accountrecovery.test.MinimalAccountRecoveryNotificationTest
import me.proton.core.auth.test.usecase.WaitForPrimaryAccount
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.eventmanager.domain.repository.EventMetadataRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.notification.dagger.CoreNotificationFeaturesModule
import me.proton.core.notification.domain.repository.NotificationRepository
import me.proton.core.notification.domain.usecase.IsNotificationsEnabled
import me.proton.test.fusion.FusionConfig
import org.junit.Rule
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(
    CoreAccountRecoveryFeaturesModule::class,
    CoreNotificationFeaturesModule::class,
)
open class AccountRecoveryFlowTest : BaseTest(), MinimalAccountRecoveryNotificationTest {

    @get:Rule(order = Rule.DEFAULT_ORDER - 2)
    val grantPermissionRule = NotificationPermissionRule()

    @get:Rule(order = Rule.DEFAULT_ORDER - 1)
    val composeTestRule: ComposeTestRule = createEmptyComposeRule()

    @BindValue
    internal val isAccountRecoveryEnabled = object : IsAccountRecoveryEnabled {
        override fun invoke(userId: UserId?): Boolean = true
        override fun isLocalEnabled(): Boolean = true
        override fun isRemoteEnabled(userId: UserId?): Boolean = true
    }

    @BindValue
    internal val isAccountRecoveryResetEnabled = object : IsAccountRecoveryResetEnabled {
        override fun invoke(userId: UserId?): Boolean = true
        override fun isLocalEnabled(): Boolean = true
        override fun isRemoteEnabled(userId: UserId?): Boolean = true
    }

    @BindValue
    internal val isNotificationsEnabled = IsNotificationsEnabled { true }

    /** AccountStateHandler is already started by `ProtonCalendarApplication` and `MainInitializer`.
     * This test doesn't need to start it manually.
     **/
    override val accountStateHandler: AccountStateHandler? = null

    @Inject
    override lateinit var apiProvider: ApiProvider

    @Inject
    override lateinit var eventManagerProvider: EventManagerProvider

    @Inject
    override lateinit var eventMetadataRepository: EventMetadataRepository

    @Inject
    override lateinit var notificationRepository: NotificationRepository

    @Inject
    override lateinit var waitForPrimaryAccount: WaitForPrimaryAccount

    init {
        FusionConfig.Compose.testRule.set(composeTestRule)
        FusionConfig.Compose.useUnmergedTree.set(true)
    }

    override fun verifyAfterLogin() {
        waitForPrimaryAccount()
    }
}
