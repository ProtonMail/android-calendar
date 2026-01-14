/*
 * Copyright (c) 2022 Proton Technologies AG
 * This file is part of Proton Technologies AG and Proton Mail.
 *
 * Proton Mail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Proton Mail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Proton Mail. If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.android.calendar.uitest.e2e.core.login

import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.uitest.BaseTest
import me.proton.android.calendar.uitest.robot.HomeRobot
import me.proton.core.auth.test.MinimalSignInInternalTests
import me.proton.core.auth.test.rule.AcceptExternalRule
import me.proton.core.auth.test.usecase.WaitForPrimaryAccount
import me.proton.core.network.domain.client.ExtraHeaderProvider
import org.junit.Rule
import javax.inject.Inject

@HiltAndroidTest
class LoginFlowTests : BaseTest(), MinimalSignInInternalTests {

    // TODO: rework tests to use ProtonRule - CP-8722.

    @get:Rule
    val acceptExternalRule = AcceptExternalRule { extraHeaderProvider }

    @Inject
    lateinit var extraHeaderProvider: ExtraHeaderProvider

    @Inject
    lateinit var waitForPrimaryAccount: WaitForPrimaryAccount

    override fun verifyAfter() {
        waitForPrimaryAccount()
        HomeRobot.splashAfterLoginIsDisplayed()
    }
}
