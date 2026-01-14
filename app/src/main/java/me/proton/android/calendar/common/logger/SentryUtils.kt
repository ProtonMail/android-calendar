package me.proton.android.calendar.common.logger

import android.content.SharedPreferences
import androidx.core.content.edit
import me.proton.android.calendar.common.SharedPreferencesKeys
import java.util.UUID

object SentryUtils {

    @JvmStatic
    fun getInstallationId(sharedPreferences: SharedPreferences): String =
        sharedPreferences.getString(SharedPreferencesKeys.APP_INSTALLATION_ID, null)
            ?: UUID.randomUUID().toString().also {
                sharedPreferences.edit(commit = true) { putString(SharedPreferencesKeys.APP_INSTALLATION_ID, it) }
            }
}
