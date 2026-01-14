package me.proton.android.calendar.uitest.rule

import androidx.preference.PreferenceManager
import me.proton.core.util.android.sharedpreferences.set
import me.proton.test.fusion.FusionConfig.targetContext
import org.junit.rules.ExternalResource

class SharedPreferencesRule(private val sharedPreferences: Array<Pair<String, Any>>) : ExternalResource() {
    override fun before() {
        sharedPreferences.forEach {
            PreferenceManager.getDefaultSharedPreferences(targetContext)[it.first] = it.second
        }
    }
}
