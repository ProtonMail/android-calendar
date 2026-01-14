package me.proton.android.calendar.common.utils

import android.app.Activity
import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CALENDAR_PROVIDER_VERSION_CODE
import me.proton.android.calendar.common.COLOR_PER_EVENT_VERSION_CODE
import me.proton.android.calendar.common.EASY_SWITCH_VERSION_CODE
import me.proton.android.calendar.common.HOLIDAY_CALENDAR_VERSION_CODE
import me.proton.android.calendar.common.IMPORT_VERSION_CODE
import me.proton.android.calendar.common.MONTH_VIEW_VERSION_CODE
import me.proton.android.calendar.common.REBRANDING_VERSION_CODE
import me.proton.android.calendar.common.SEARCH_VERSION_CODE
import me.proton.android.calendar.common.SPOTLIGHT_VERSION_CODES
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.WEEK_VIEW_VERSION_CODE
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.databinding.DialogSpotlightBinding
import me.proton.android.calendar.databinding.DialogSpotlightV5Binding
import me.proton.android.calendar.presentation.main.MainActivity

object SpotlightUtils {

    private fun Context.getLastSpotlightShown(): Int {
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(SharedPreferencesKeys.LAST_SPOTLIGHT_SHOWN, 0)
    }

    private fun Context.setLastSpotlightShown(versionCode: Int) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val editor = sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.LAST_SPOTLIGHT_SHOWN, versionCode)
        editor.apply()
    }

    private fun getMonthViewDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_month_view_title,
            R.string.spotlight_dialog_month_view_description
        )
    }

    private fun getRebrandingDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_v5_dialog_rebranding_title,
            R.string.spotlight_v5_dialog_rebranding_description
        )
    }

    private fun getAutoAddedInvitesDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_auto_invites_title,
            R.string.spotlight_dialog_auto_invites_description
        )
    }

    private fun getEasySwitchDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.import_from_google_title,
            R.string.spotlight_dialog_easy_switch_description
        )
    }

    private fun getWeekViewDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_week_view_title,
            R.string.spotlight_dialog_week_view_description
        )
    }

    private fun getImportDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_import_title,
            R.string.spotlight_dialog_import_description
        )
    }

    private fun getSearchDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_search_title,
            R.string.spotlight_dialog_search_description
        )
    }

    private fun getCalendarProviderDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_calendar_provider_title,
            R.string.spotlight_dialog_calendar_provider_description
        )
    }

    private fun getHolidayCalendarDialogContent(limitReached: Boolean): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_holiday_calendar_title,
            if (limitReached) R.string.spotlight_dialog_holiday_calendar_limit_reached_description
            else R.string.spotlight_dialog_holiday_calendar_add_description
        )
    }

    private fun getColorPerEventDialogContent(): Pair<Int, Int> {
        return Pair(
            R.string.spotlight_dialog_color_per_event_title,
            R.string.spotlight_dialog_color_per_event_description
        )
    }

    fun Activity.showLastSpotlightDialog(
        isFreeUser: Boolean,
        isColorPerEventEnabled: Boolean,
        isEventSearchEnabled: Boolean,
        hasHolidayCalendar: Boolean,
        calendarLimitReached: Boolean,
        positiveCallback: ((lastSpotlightVersionCode: Int) -> Unit)? = null
    ): Boolean {
        val lastSpotlightShown = this.getLastSpotlightShown()
        val lastSpotlightVersionCode = SPOTLIGHT_VERSION_CODES.maxOrNull() ?: 0 // Should never be null

        // Return if we have already shown the last spotlight dialog
        if (lastSpotlightShown >= lastSpotlightVersionCode) return false

        return when (lastSpotlightVersionCode) {
            MONTH_VIEW_VERSION_CODE -> {
                // Month view
                val monthViewContent = getMonthViewDialogContent()
                this.displaySpotlightDialog(
                    monthViewContent.first,
                    monthViewContent.second
                )
                true
            }
            REBRANDING_VERSION_CODE -> {
                // Rebranding
                val rebrandingContent = getRebrandingDialogContent()
                this.displayV5SpotlightDialog(
                    rebrandingContent.first,
                    rebrandingContent.second
                )
                true
            }
            EASY_SWITCH_VERSION_CODE -> {
                // Display auto added invites dialog, followed by easy switch dialog
                val autoAddedInvitesContent = getAutoAddedInvitesDialogContent()
                this.displaySpotlightDialog(
                    autoAddedInvitesContent.first,
                    autoAddedInvitesContent.second,
                    materialPositiveButtonText = R.string.spotlight_dialog_auto_invites_positive_button,
                    customOnDismissCallback = {
                        // Easy switch
                        val easySwitchContent = getEasySwitchDialogContent()
                        val positiveButtonCallback = View.OnClickListener {
                            // Open import from google view
                            (this as MainActivity).showImportGoogleAuthDialog()
                        }
                        this.displaySpotlightDialog(
                            easySwitchContent.first,
                            easySwitchContent.second,
                            customPositiveButtonText = R.string.spotlight_dialog_easy_switch_positive_button,
                            customNegativeButtonText = R.string.spotlight_dialog_easy_switch_negative_button,
                            customPositiveButtonCallback = positiveButtonCallback
                        )
                    }
                )
                true
            }
            WEEK_VIEW_VERSION_CODE -> {
                // Week view
                val weekViewContent = getWeekViewDialogContent()
                this.displaySpotlightDialog(
                    weekViewContent.first,
                    weekViewContent.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button
                )
                true
            }
            IMPORT_VERSION_CODE -> {
                // Import
                val weekViewContent = getImportDialogContent()
                this.displaySpotlightDialog(
                    weekViewContent.first,
                    weekViewContent.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button
                )
                true
            }
            CALENDAR_PROVIDER_VERSION_CODE -> {
                // Calendar provider, default view setting, shared calendar write permissions, shared calendar edit setting, new languages
                val content = getCalendarProviderDialogContent()
                this.displaySpotlightDialog(
                    content.first,
                    content.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button
                )
                true
            }
            HOLIDAY_CALENDAR_VERSION_CODE -> {
                if (hasHolidayCalendar) {
                    setLastSpotlightShown(BuildConfig.VERSION_CODE)
                    return false
                }
                // Holiday calendar
                val content = getHolidayCalendarDialogContent(calendarLimitReached)
                this.displaySpotlightDialog(
                    content.first,
                    content.second,
                    materialPositiveButtonText =
                    if (calendarLimitReached) R.string.create_calendar_limit_reached_manage
                    else R.string.add_calendar_button,
                    materialNegativeButtonText = R.string.spotlight_dialog_skip,
                    customPositiveButtonCallback = {
                        positiveCallback?.invoke(lastSpotlightVersionCode)
                    }
                )
                true
            }
            COLOR_PER_EVENT_VERSION_CODE -> {
                if (!isColorPerEventEnabled || isFreeUser) return false // Color per event is a paid feature
                val content = getColorPerEventDialogContent()
                this.displaySpotlightDialog(
                    content.first,
                    content.second,
                    materialPositiveButtonText = R.string.spotlight_v5_dialog_got_it_button,
                    customPositiveButtonCallback = {
                        positiveCallback?.invoke(lastSpotlightVersionCode)
                    }
                )
                true
            }
            SEARCH_VERSION_CODE -> {
                if (!isEventSearchEnabled) return false
                val content = getSearchDialogContent()
                this.displaySpotlightDialog(
                    content.first,
                    content.second,
                    materialPositiveButtonText = R.string.spotlight_dialog_search_positive,
                    customPositiveButtonCallback = {
                        positiveCallback?.invoke(lastSpotlightVersionCode)
                    },
                    materialNegativeButtonText = R.string.spotlight_dialog_search_negative
                )
                true
            }
            else -> {
                // Do nothing if we don't have any dialog to show for that version code
                false
            }
        }
    }

    private fun Context.displaySpotlightDialog(
        title: Int,
        description: Int,
        materialPositiveButtonText: Int? = null,
        materialNegativeButtonText: Int? = null,
        customPositiveButtonText: Int? = null,
        customNegativeButtonText: Int? = null,
        customPositiveButtonCallback: View.OnClickListener? = null,
        customOnDismissCallback: View.OnClickListener? = null
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)

        val viewBinding = DialogSpotlightBinding.inflate(LayoutInflater.from(this), null, false)

        materialDialogBuilder.setOnDismissListener {
            customOnDismissCallback?.onClick(viewBinding.root)
            // Set current version name as last spotlight shown
            this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
        }

        // Support link in text with getText
        viewBinding.dialogSpotlightTitle.text = getText(title)
        viewBinding.dialogSpotlightDescription.text = getText(description)
        // Support click on link in text
        viewBinding.dialogSpotlightDescription.movementMethod = LinkMovementMethod.getInstance()

        var dialog: AlertDialog? = null

        viewBinding.dialogSpotlightCustomPositiveButton.visibleOrGone(customPositiveButtonText != null)
        if (customPositiveButtonText != null) {
            // Use custom positive button if text is provided
            viewBinding.dialogSpotlightCustomPositiveButton.text = getText(customPositiveButtonText)
            viewBinding.dialogSpotlightCustomPositiveButton.setOnSingleClickListener {
                dialog?.dismiss()
                customPositiveButtonCallback?.onClick(it)
            }
        } else {
            materialDialogBuilder.setPositiveButton(materialPositiveButtonText ?: R.string.spotlight_dialog_confirmation_button) { _, _ ->
                // Trigger callback if provided
                customPositiveButtonCallback?.onClick(viewBinding.root)
            }
        }

        viewBinding.dialogSpotlightCustomNegativeButton.visibleOrGone(customNegativeButtonText != null)
        if (customNegativeButtonText != null) {
            // Use custom negative button if text is provided
            viewBinding.dialogSpotlightCustomNegativeButton.text = getText(customNegativeButtonText)
            viewBinding.dialogSpotlightCustomNegativeButton.setOnSingleClickListener {
                dialog?.dismiss()
            }
        } else if (materialNegativeButtonText != null) {
            materialDialogBuilder.setNegativeButton(materialNegativeButtonText) { _, _ ->
                // Nothing to do here
            }
        }

        materialDialogBuilder.setView(viewBinding.root)
        dialog = materialDialogBuilder.show()
    }

    private fun Context.displayV5SpotlightDialog(
        title: Int,
        description: Int
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setOnDismissListener {
                // Set current version name as last spotlight shown
                this.setLastSpotlightShown(BuildConfig.VERSION_CODE)
            }

        val viewBinding = DialogSpotlightV5Binding.inflate(LayoutInflater.from(this), null, false)

        // Support link in text with getText
        viewBinding.dialogSpotlightV5Title.text = getString(title)
        viewBinding.dialogSpotlightV5Description.text = getText(description)
        // Support click on link in text
        viewBinding.dialogSpotlightV5Description.movementMethod = LinkMovementMethod.getInstance()

        var dialog: AlertDialog? = null
        viewBinding.dialogSpotlightV5GotItButton.setOnSingleClickListener {
            dialog?.dismiss()
        }

        materialDialogBuilder.setView(viewBinding.root)
        dialog = materialDialogBuilder.show()
    }
}
