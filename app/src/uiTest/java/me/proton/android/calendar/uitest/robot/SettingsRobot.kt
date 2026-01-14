package me.proton.android.calendar.uitest.robot

import androidx.annotation.IdRes
import me.proton.android.calendar.R
import me.proton.test.fusion.Fusion.view

object SettingsRobot : Robot {

    enum class NavigationItem(@IdRes val idRes: Int) {
        Account(R.id.settings_account_item),
        // General(R.id.settings_general_press),
    }

    private fun clickNavigationItem(navigationItem: NavigationItem) =
        view.withId(navigationItem.idRes).click()

    fun clickAccount() = AccountSettingsRobot.apply { clickNavigationItem(NavigationItem.Account) }

    // fun clickGeneral() = clickNavigationItem(NavigationItem.General)
}
