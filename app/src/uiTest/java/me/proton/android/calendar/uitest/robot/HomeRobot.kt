/*
 * Copyright (c) 2023 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.calendar.uitest.robot

import me.proton.android.calendar.R
import me.proton.test.fusion.Fusion.view
import kotlin.time.Duration.Companion.seconds

object HomeRobot : Robot {
    private val splashLogo = view.withId(R.id.root_splash_logo)
    private val plusButton = view.withTag(R.drawable.ic_proton_plus.toString())
    private val hamburgerButton = view.withContentDesc(R.string.hamburger_button)

    fun clickAddEvent() = plusButton.clickTo(EventFormRobot)

    fun clickHamburgerButton() = hamburgerButton.clickTo(SidebarRobot)

    fun splashAfterLoginIsDisplayed() = splashLogo.checkIsDisplayed()

    fun robotDisplayed() = let {
        plusButton.await(60.seconds) { checkIsDisplayed() }
        hamburgerButton.await(60.seconds) { checkIsDisplayed() }
    }
}
