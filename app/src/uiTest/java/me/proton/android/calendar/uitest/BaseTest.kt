/*
 * Copyright (c) 2021 Proton Technologies AG
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

package me.proton.android.calendar.uitest

import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidTest
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.init.MainInitializer
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.uitest.robot.Robot
import me.proton.android.calendar.uitest.rule.NotificationPermissionRule
import me.proton.android.calendar.uitest.rule.SharedPreferencesRule
import me.proton.android.calendar.uitest.rule.TimeZoneRule
import me.proton.android.calendar.BuildConfig
import me.proton.core.test.performance.MeasurementConfig
import me.proton.core.test.performance.MeasurementRule
import me.proton.core.test.quark.Quark
import me.proton.core.test.quark.data.User.Users
import me.proton.core.test.rule.extension.protonActivityScenarioRule
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.deserializeList
import me.proton.test.fusion.FusionConfig.targetContext
import org.junit.Rule
import java.util.TimeZone

@HiltAndroidTest
open class BaseTest {

    val measurementConfig = MeasurementConfig
        .setEnvironment(BuildConfig.DYNAMIC_DOMAIN)
        .setLokiEndpoint(BuildConfig.LOKI_ENDPOINT)
        .setLokiPrivateKey(BuildConfig.LOKI_PRIVATE_KEY)
        .setLokiCertificate(BuildConfig.LOKI_CERTIFICATE)

    private val measurementRule = MeasurementRule()

    val measurementContext = measurementRule.measurementContext(measurementConfig)

    @get:Rule
    open val protonRule = protonActivityScenarioRule<MainActivity>(
        afterHilt = {
            MainInitializer.init(targetContext)
        },
        additionalRules = linkedSetOf(
            TimeZoneRule(timeZone),
            SharedPreferencesRule(sharedPreferences),
            NotificationPermissionRule(),
            measurementRule
        ),
        logoutBefore = true
    )

    val users = Users(
        InstrumentationRegistry.getInstrumentation().context
            .assets
            .open("users.json")
            .bufferedReader()
            .use { it.readText() }
            .deserializeList())

    val quark
        get() = Quark(
            host = protonRule.testConfig.envConfig!!.host,
            proxyToken = protonRule.testConfig.envConfig?.proxyToken,
            InstrumentationRegistry.getInstrumentation().context
                .assets
                .open("internal_api.json")
                .bufferedReader()
                .use { it.readText() }
                .deserialize())

    companion object {
        internal val sharedPreferences = arrayOf(
            SharedPreferencesKeys.LAST_SPOTLIGHT_SHOWN to Int.MAX_VALUE as Any
        )
        internal val timeZone: TimeZone get() = TimeZone.getTimeZone("GMT+2")
    }
}

fun <T : Robot> T.verify(block: T.() -> Any): T =
    apply { block() }
