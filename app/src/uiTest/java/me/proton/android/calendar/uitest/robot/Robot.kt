package me.proton.android.calendar.uitest.robot

import androidx.annotation.StringRes
import me.proton.test.fusion.FusionConfig
import me.proton.test.fusion.ui.espresso.builders.OnView
import me.proton.test.fusion.ui.espresso.wrappers.EspressoActions

interface Robot {
    fun <T : Robot> EspressoActions.clickTo(goesTo: T): T = goesTo.apply { click() }

    fun resourceString(@StringRes stringRes: Int, formatString: String) =
        FusionConfig.targetContext.resources.getString(stringRes, formatString)
}