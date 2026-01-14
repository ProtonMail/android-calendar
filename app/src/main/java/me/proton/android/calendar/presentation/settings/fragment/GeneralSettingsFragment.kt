package me.proton.android.calendar.presentation.settings.fragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.ProtonCalendarApplication
import me.proton.android.calendar.R
import me.proton.android.calendar.common.AppTheme
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.allowedTimezoneIds
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.formattedTimeZoneToId
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.sortFormattedTimeZoneIds
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.CustomLocale
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.databinding.FragmentGeneralSettingsBinding
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.SearchViewModel
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.FeatureFlagViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.util.kotlin.equalsNoCase
import org.koin.core.KoinComponent
import java.time.DayOfWeek
import java.time.Instant

@AndroidEntryPoint
class GeneralSettingsFragment : BaseDialogFragment<FragmentGeneralSettingsBinding>(), KoinComponent {

    override val TAG: String
        get() = "GeneralSettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_general_settings

    override val navigateUp = true

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val searchViewModel: SearchViewModel by activityViewModels()
    private val featureFlagViewModel: FeatureFlagViewModel by activityViewModels()
    private val application: ProtonCalendarApplication by lazy {
        requireContext().applicationContext as ProtonCalendarApplication
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_more_general_settings)
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentGeneralSettingsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // refresh CalendarUserSettings, for users who were logged in before auto-added invites switch,
        //  the toggle value might be out of sync
        lifecycleScope.launchWhenStarted {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {
                mainViewModel.refreshCalendarUserSettings(userId = userId)
            }
        }

        binding.settingsWeekNumbersPress.root.setOnClickListener {
            binding.settingsWeekNumbersSwitch.performClick()
        }
        binding.settingsWeekNumbersSwitch.setOnClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                displayNetworkError()
                binding.settingsWeekNumbersSwitch.isChecked = !binding.settingsWeekNumbersSwitch.isChecked
                return@setOnClickListener
            }
            lifecycleScope.launch {
                calendarViewModel.updateDisplayWeekNumber(binding.settingsWeekNumbersSwitch.isChecked)
            }
        }

        binding.settingsAutoInvitesSeparator.visibleOrGone(CalendarFeatureFlag.AutoInvitesSetting.fallbackValue)
        binding.settingsAutoInvites.visibleOrGone(CalendarFeatureFlag.AutoInvitesSetting.fallbackValue)

        binding.settingsAutoInvitesPress.root.setOnClickListener {
            binding.settingsAutoInvitesSwitch.performClick()
        }
        binding.settingsAutoInvitesSwitch.setOnClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                displayNetworkError()
                binding.settingsAutoInvitesSwitch.isChecked = !binding.settingsAutoInvitesSwitch.isChecked
                return@setOnClickListener
            }
            lifecycleScope.launch {
                calendarViewModel.updateAutoImportInvite(binding.settingsAutoInvitesSwitch.isChecked)
            }
        }

        binding.settingsUpdateTimezonePress.root.setOnClickListener {
            binding.settingsUpdateTimezoneSwitch.performClick()
        }
        binding.settingsUpdateTimezoneSwitch.setOnClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                displayNetworkError()
                binding.settingsUpdateTimezoneSwitch.isChecked = !binding.settingsUpdateTimezoneSwitch.isChecked
                return@setOnClickListener
            }
            lifecycleScope.launch {
                calendarViewModel.updateAutoDetectPrimaryTimezone(binding.settingsUpdateTimezoneSwitch.isChecked)
            }
        }

        binding.settingsTimezonePress.root.setOnSingleClickListener {
            val forInstant = Instant.now()
            val formattedTimeZoneIds = allowedTimezoneIds.map {
                DateTimeUtilsImpl.formatTimeZoneId(it, forInstant)
            }.toTypedArray()
            formattedTimeZoneIds.sortFormattedTimeZoneIds()
            lifecycleScope.launch {
                val defaultTimeZone = calendarViewModel.getTimeZoneId()?.id
                val selectedIndex =
                    if (defaultTimeZone == null) -1
                    else formattedTimeZoneIds.indexOf(DateTimeUtilsImpl.formatTimeZoneId(defaultTimeZone, forInstant))

                AndroidUtils.displaySingleChoicePicker(requireContext(), getString(R.string.settings_timezone_title), formattedTimeZoneIds, selectedIndex) {
                    if (!mainViewModel.isConnectedToNetwork) {
                        displayNetworkError()
                        return@displaySingleChoicePicker
                    }
                    calendarViewModel.updatePrimaryTimezone(formattedTimeZoneIds[it].formattedTimeZoneToId())
                }
            }
        }

        val appThemes = resources.getStringArray(R.array.app_themes)
        binding.settingsThemeValue.text = appThemes[application.getAppTheme().value]

        // TODO Handle themes for Android P and below
        binding.settingsTheme.visibleOrGone(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.settingsThemePress.root.setOnSingleClickListener {
                AndroidUtils.displaySingleChoicePicker(
                    requireContext(),
                    getString(R.string.settings_theme_title),
                    appThemes,
                    AppTheme.values().indexOf(application.getAppTheme())
                ) { index ->
                    binding.settingsThemeValue.text = appThemes[index]
                    application.changeAppTheme(AppTheme.values()[index])
                }
            }
        }

        val viewModes = arrayOf(
            getString(R.string.settings_default_view_default),
            getString(R.string.nav_view_switcher_agenda),
            getString(R.string.nav_view_switcher_day),
            getString(R.string.nav_view_switcher_three_day),
            getString(R.string.nav_view_switcher_week),
            getString(R.string.nav_view_switcher_month)
        )
        binding.settingsDefaultViewValue.text =
            if (mainViewModel.useDefaultViewMode()) viewModes[mainViewModel.getLastViewMode().value + 1]
            else viewModes[0]

        binding.settingsDefaultViewPress.root.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_default_view_title),
                viewModes,
                if (!mainViewModel.useDefaultViewMode()) 0
                else mainViewModel.getLastViewMode().value + 1
            ) { index ->
                binding.settingsDefaultViewValue.text = viewModes[index]
                mainViewModel.setUseDefaultViewMode(index != 0)
                if (index != 0) {
                    mainViewModel.setViewMode(ViewMode.values()[index - 1])
                } else {
                    calendarViewModel.viewMode.value?.let {
                        mainViewModel.setViewMode(it)
                    }
                }
            }
        }

        val appLanguagesLabels = resources.getStringArray(R.array.custom_language_labels)
        val appLanguagesValues = resources.getStringArray(R.array.custom_language_values)
        val selectedLanguageTagValue = CustomLocale.getSelectedLocale()?.toLanguageTag()
        val selectedLanguageValue = CustomLocale.getSelectedLocale()?.language
        val selectedLanguageIndex = run {
            val indexOfTag = appLanguagesValues.indexOfFirst { it.equalsNoCase(selectedLanguageTagValue) }
            if (indexOfTag == -1) appLanguagesValues.indexOfFirst { it.equalsNoCase(selectedLanguageValue) }
            else indexOfTag
        }
        val systemDefaultLabel = resources.getString(R.string.settings_language_default)
        binding.settingsLanguageValue.text = if (selectedLanguageIndex == -1) systemDefaultLabel else appLanguagesLabels[selectedLanguageIndex]

        val appLanguageDialogLabels = appLanguagesLabels.toMutableList()
        appLanguageDialogLabels.add(0, systemDefaultLabel)

        binding.settingsLanguage.visibleOrGone(CalendarFeatureFlag.ChangeLanguage.fallbackValue)
        binding.settingsLanguagePress.root.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_language_title),
                appLanguageDialogLabels.toTypedArray(),
                run {
                    val selectedLanguageTag = CustomLocale.getSelectedLocale()?.toLanguageTag()
                    val selectedLanguage = CustomLocale.getSelectedLocale()?.language
                    val selectedLanguageDialogIndex = run {
                        val indexOfTag = appLanguagesValues.indexOfFirst { it.equalsNoCase(selectedLanguageTag) }
                        if (indexOfTag == -1) appLanguagesValues.indexOfFirst { it.equalsNoCase(selectedLanguage) }
                        else indexOfTag
                    }
                    if (selectedLanguageDialogIndex == -1) 0 else selectedLanguageDialogIndex + 1
                }
            ) { index ->
                if (index == 0) {
                    binding.settingsLanguageValue.text = systemDefaultLabel
                    (activity as? MainActivity)?.changeAppLanguage(null) // Use empty string for System default
                } else {
                    binding.settingsLanguageValue.text = appLanguagesLabels[index - 1]
                    (activity as? MainActivity)?.changeAppLanguage(appLanguagesValues[index - 1])
                }
            }
        }

        val timeFormats = resources.getStringArray(R.array.time_formats)
        binding.settingsTimeFormatPress.root.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_time_format_title),
                timeFormats,
                timeFormats.indexOf(binding.settingsTimeFormatValue.text)
            ) { index ->
                if (!mainViewModel.isConnectedToNetwork) {
                    displayNetworkError()
                    return@displaySingleChoicePicker
                }
                binding.settingsTimeFormatValue.text = timeFormats[index]
                calendarViewModel.updateTimeFormat(index)
            }
        }

        val weekStartValues = resources.getStringArray(R.array.week_start)
        binding.settingsWeekStartPress.root.setOnSingleClickListener {
            AndroidUtils.displaySingleChoicePicker(
                requireContext(),
                getString(R.string.settings_week_start_title),
                weekStartValues,
                weekStartValues.indexOf(binding.settingsWeekStartValue.text)) { index ->
                if (!mainViewModel.isConnectedToNetwork) {
                    displayNetworkError()
                    return@displaySingleChoicePicker
                }
                val weekStart = when (index) {
                    2 -> DayOfWeek.SATURDAY.value // 6 is value for Saturday and index 2 in available days string array
                    3 -> DayOfWeek.SUNDAY.value // 7 is value for Sunday and index 3 in available days string array
                    else -> index
                }
                calendarViewModel.updateWeekStart(weekStart)
            }
        }

        binding.settingsSearch.visibleOrGone(
            featureFlagViewModel.isEventSearchEnabled()
        )

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.settingsSearchSwitch.isChecked = searchViewModel.isCalendarDownloadEnabled()
            }
        }

        binding.settingsSearchPress.root.setOnClickListener {
            binding.settingsSearchSwitch.performClick()
        }
        binding.settingsSearchSwitch.setOnClickListener {

            val builder = MaterialAlertDialogBuilder(requireContext())

            if (binding.settingsSearchSwitch.isChecked) { // turning ON
                builder.setTitle(R.string.search_settings_dialog_toggle_on_title)
                builder.setMessage(R.string.search_settings_dialog_toggle_on_text)
                builder.setPositiveButton(R.string.dialog_button_download) { _, _ ->
                    searchViewModel.enableCalendarDownload()
                }
            } else { // turning OFF
                builder.setTitle(R.string.search_settings_dialog_toggle_off_title)
                builder.setMessage(R.string.search_settings_dialog_toggle_off_text)
                builder.setPositiveButton(R.string.dialog_button_remove) { _, _ ->
                    searchViewModel.disableCalendarDownload()
                    searchViewModel.clearDownloadingState()
                }
            }

            builder.setNegativeButton(R.string.dialog_button_cancel, null)
            builder.setOnDismissListener {
                // bring back the correct toggle value in case user changed their mind
                lifecycleScope.launch {
                    withContext(Dispatchers.Main) {
                        binding.settingsSearchSwitch.isChecked = searchViewModel.isCalendarDownloadEnabled()
                    }
                }
            }
            builder.create().show()
        }

        val isAlternativeRoutingEnabled = mainViewModel.isAlternativeRoutingEnabled()
        binding.settingsAlternativeRoutingSwitch.isChecked = isAlternativeRoutingEnabled
        binding.settingsAlternativeRoutingPress.root.setOnClickListener {
            binding.settingsAlternativeRoutingSwitch.performClick()
        }
        binding.settingsAlternativeRoutingSwitch.setOnClickListener {
            mainViewModel.setAlternativeRoutingEnabled(binding.settingsAlternativeRoutingSwitch.isChecked)
        }

        // Observers

        calendarViewModel.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
            binding.settingsTimeFormatValue.text = timeFormats[timeFormat]
        }

        calendarViewModel.timeZoneId.observe(viewLifecycleOwner) { zoneId ->
            binding.settingsTimezoneValue.text = zoneId.id?.let {
                DateTimeUtilsImpl.formatTimeZoneId(it, Instant.now())
            } ?: getString(R.string.settings_value_placeholder)
        }

        calendarViewModel.weekStart.observe(viewLifecycleOwner) { weekStart ->
            binding.settingsWeekStartValue.text = when (weekStart) {
                DayOfWeek.SATURDAY.value -> weekStartValues[2] // 6 is value for Saturday and index 2 in available days string array
                DayOfWeek.SUNDAY.value -> weekStartValues[3] // 7 is value for Sunday and index 3 in available days string array
                else -> weekStartValues[weekStart]
            }
        }

        calendarViewModel.displayWeekNumber.observe(viewLifecycleOwner) { displayWeekNumber ->
            binding.settingsWeekNumbersSwitch.isChecked = displayWeekNumber
            binding.settingsWeekNumbersSwitch.jumpDrawablesToCurrentState()
        }

        calendarViewModel.autoImportInvite.observe(viewLifecycleOwner) { autoImportInvite ->
            binding.settingsAutoInvitesSwitch.isChecked = autoImportInvite
            binding.settingsAutoInvitesSwitch.jumpDrawablesToCurrentState()
        }

        calendarViewModel.autoDetectPrimaryTimezone.observe(viewLifecycleOwner) { autoDetectPrimaryTimezone ->
            binding.settingsUpdateTimezoneSwitch.isChecked = autoDetectPrimaryTimezone
            binding.settingsUpdateTimezoneSwitch.jumpDrawablesToCurrentState()
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}

