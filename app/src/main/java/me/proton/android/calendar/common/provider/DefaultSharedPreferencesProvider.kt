package me.proton.android.calendar.common.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class DefaultSharedPreferencesProvider(private val context: Context) {

    val sharedPreferences: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(context)

}
