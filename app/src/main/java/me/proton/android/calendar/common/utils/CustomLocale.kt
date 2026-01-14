package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.SharedPreferencesKeys
import java.util.Locale

object CustomLocale {

    fun applyCurrent(context: Context): Context {
        val currentSelectedLocaleCode = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(SharedPreferencesKeys.APP_SETTINGS_LANGUAGE, null)
        return currentSelectedLocaleCode?.let {
            val locale = createLocaleFromCode(it)
            if (getSelectedLocale() == null) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
                setAppDefaultNightMode(context)
            }
            // This is needed because of ResourceProvider's usage. It's advised to remove it ASAP.
            val configuration = context.resources.configuration
            configuration.setLocale(locale)

            setConfigurationUiMode(context, configuration)
        } ?: run {
            setAppDefaultNightMode(context)
            // This is needed because of ResourceProvider's usage. It's advised to remove it ASAP.
            val configuration = context.resources.configuration

            setConfigurationUiMode(context, configuration)
        }
    }

    private fun setConfigurationUiMode(context: Context, configuration: Configuration): Context {
        // Make sure we also set the app theme in Configuration
        when (AppTheme.values()[PreferenceManager.getDefaultSharedPreferences(context).getInt(SharedPreferencesKeys.THEME, AppTheme.SYSTEM_DEFAULT.value)]) {
            AppTheme.LIGHT -> configuration.uiMode = Configuration.UI_MODE_NIGHT_NO
            AppTheme.DARK -> configuration.uiMode = Configuration.UI_MODE_NIGHT_YES
            else -> configuration.uiMode = Configuration.UI_MODE_NIGHT_UNDEFINED
        }
        return context.createConfigurationContext(configuration)
    }

    fun apply(context: Context, localeCode: String?) {
        val localesToSet: LocaleListCompat = when {
            !CalendarFeatureFlag.ChangeLanguage.fallbackValue -> LocaleListCompat.create(Locale("en", "US"))
            localeCode.isNullOrBlank() -> {
                // If settings are in Auto Detect, use System language if supported, or fallback to en-US
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .remove(SharedPreferencesKeys.APP_SETTINGS_LANGUAGE)
                    .commit()
                LocaleListCompat.getEmptyLocaleList()
            }
            else -> {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(SharedPreferencesKeys.APP_SETTINGS_LANGUAGE, localeCode)
                    .commit()
                val locale = createLocaleFromCode(localeCode)
                LocaleListCompat.create(locale)
            }
        }

        setAppDefaultNightMode(context)
        AppCompatDelegate.setApplicationLocales(localesToSet)
    }

    private fun setAppDefaultNightMode(context: Context) {
        // Make sure we also set the app theme in Configuration
        val appTheme = when (AppTheme.values()[PreferenceManager.getDefaultSharedPreferences(context).getInt(SharedPreferencesKeys.THEME, AppTheme.SYSTEM_DEFAULT.value)]) {
            AppTheme.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppTheme.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(appTheme)
    }

    private fun createLocaleFromCode(localeCode: String): Locale {
        val languageToSet = localeCode.substringBefore("-")
        val countryToSet = localeCode.substringAfter("-", "")
        // Create custom Locale
        return Locale(preAndroid15LanguageCode(languageToSet), preAndroid15LanguageCode(countryToSet))
    }

    fun preAndroid15LanguageCode(code: String): String  {
        if (Build.VERSION.SDK_INT < 35) {
            when (code) {
                "he" -> return "iw"
                "id" -> return "in"
                "yi" -> return "ji"
            }
        }
        return code
    }

    /** Gets either a custom selected Locale for the app or null. */
    fun getSelectedLocale(): Locale? = AppCompatDelegate.getApplicationLocales().firstOrNull()
}

private fun LocaleListCompat.firstOrNull() = if (isEmpty) null else this[0]
