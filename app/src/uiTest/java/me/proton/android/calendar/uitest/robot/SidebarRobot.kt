package me.proton.android.calendar.uitest.robot

import androidx.annotation.IdRes
import me.proton.android.calendar.R
import me.proton.test.fusion.Fusion.view

object SidebarRobot : Robot {

    enum class NavigationItem(@IdRes val idRes: Int) {
        Agenda(R.id.nav_view_switcher_agenda_layout),
        Day(R.id.nav_view_switcher_day_layout),
        ThreeDays(R.id.nav_view_switcher_three_day_layout),
        Week(R.id.nav_view_switcher_week_layout),
        Month(R.id.nav_view_switcher_month_layout),
        Subscriptions(R.id.nav_view_more_subscription_layout),
        Settings(R.id.nav_view_more_settings_layout),
        LogOut(R.id.nav_view_more_logout_layout)
    }

    private fun  clickNavigationItem(navigationItem: NavigationItem) =
        view.withId(navigationItem.idRes).click()

    fun clickAgenda() = AgendaRobot.apply { clickNavigationItem(NavigationItem.Agenda) }
    fun clickDay() = HomeRobot.apply { clickNavigationItem(NavigationItem.Day) }
    fun clickThreeDays() = HomeRobot.apply { clickNavigationItem(NavigationItem.ThreeDays) }
    fun clickWeek() = HomeRobot.apply { clickNavigationItem(NavigationItem.Week) }
    fun clickMonth() = HomeRobot.apply { clickNavigationItem(NavigationItem.Month) }
    fun clickSubscriptions() = clickNavigationItem(NavigationItem.Subscriptions)
    fun clickSettings() = SettingsRobot.apply { clickNavigationItem(NavigationItem.Settings) }
    fun clickLogOut() {
        clickNavigationItem(NavigationItem.LogOut)
    }
}