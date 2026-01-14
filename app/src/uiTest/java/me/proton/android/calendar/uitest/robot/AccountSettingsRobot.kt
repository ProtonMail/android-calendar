package me.proton.android.calendar.uitest.robot

import androidx.annotation.StringRes
import me.proton.android.calendar.R
import me.proton.android.calendar.uitest.robot.AccountSettingsRobot.NavigationItem.PasswordManagement
import me.proton.android.calendar.uitest.robot.AccountSettingsRobot.NavigationItem.RecoveryEmail
import me.proton.test.fusion.Fusion.node

object AccountSettingsRobot : Robot {
    enum class NavigationItem(@StringRes val res: Int) {
        PasswordManagement(R.string.account_settings_list_item_password_header),
        RecoveryEmail(R.string.account_settings_list_item_recovery_header),
    }

    private fun clickNavigationItem(navigationItem: NavigationItem) =
        node.withText(navigationItem.res).click()

    fun clickPasswordManagement() = clickNavigationItem(PasswordManagement)
    fun clickRecoveryEmail() = clickNavigationItem(RecoveryEmail)
}
