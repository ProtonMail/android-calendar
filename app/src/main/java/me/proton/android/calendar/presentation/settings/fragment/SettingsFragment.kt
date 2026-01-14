package me.proton.android.calendar.presentation.settings.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FragmentArguments.CALENDAR_ID_ARG
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayFreeUserCalendarLimitReached
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayFreeUserMandatoryPersonalCalendarLimitReached
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayPaidUserCalendarLimitReached
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.displayPaidUserMandatoryPersonalCalendarLimitReached
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.sortPersonalCalendars
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.databinding.FragmentSettingsBinding
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.usecase.DeleteCalendarUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.holidayCalendar.viewModel.HolidayCalendarViewModel
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.FeatureFlagViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.android.calendar.presentation.settings.adapter.SettingsCalendarListAdapter
import me.proton.android.calendar.presentation.settings.viewModel.CalendarFormViewModel
import me.proton.core.accountmanager.presentation.compose.AccountSettingsItem
import me.proton.core.compose.component.ProtonSettingsItem
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.devicemigration.presentation.settings.SignInToAnotherDeviceItem
import org.koin.core.KoinComponent
import org.koin.core.inject

@AndroidEntryPoint
class SettingsFragment : BaseDialogFragment<FragmentSettingsBinding>(), KoinComponent {

    override val TAG: String
        get() = "SettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_settings

    override val navigateUp = true

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val calendarFormViewModel: CalendarFormViewModel by activityViewModels()
    private val holidayCalendarViewModel: HolidayCalendarViewModel by activityViewModels()
    private val featureFlagViewModel: FeatureFlagViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val resourceProvider: ResourceProvider by inject()

    private lateinit var settingsUserCalendarListAdapter: SettingsCalendarListAdapter
    private lateinit var settingsOtherCalendarListAdapter: SettingsCalendarListAdapter

    private val otherCalendarsMediator = MediatorLiveData<Pair<List<Calendar>, List<CalendarSubscriptionEntity>>>()
    private var otherCalendars: List<Calendar>? = null
    private var calendarSubscriptions: List<CalendarSubscriptionEntity>? = null

    private var defaultCalendarId: String? = null

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
        mainViewModel.triggerMainViewActions.update { true }
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_settings)
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentSettingsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.settingsAccountItem.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ProtonTheme {
                    Column {
                        AccountSettingsItem(
                            onClick = { findNavController().navigate(R.id.action_nav_settings_to_nav_account_settings) }
                        )
                        SignInToAnotherDeviceItem(
                            content = { label: String, onClick: () -> Unit ->
                                ProtonSettingsItem(
                                    name = label,
                                    onClick = onClick
                                )
                            }
                        )
                    }
                }
            }
        }

        binding.settingsGeneralPress.root.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_general_settings)
        }

        lifecycleScope.launch {
            val displayImport = calendarViewModel.displayImport()
            binding.settingsImport.visibleOrGone(displayImport)
            binding.settingsImportSeparator.visibleOrGone(displayImport)
        }
        binding.settingsImportPress.root.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_settings_to_nav_import_assistant_guide)
        }

        // Build the general settings description
        var generalSettingsDescription = getString(
            R.string.settings_general_info_separator,
            getString(R.string.settings_general_info_time_zone),
            getString(R.string.settings_general_info_calendar_layout)
        )
        if (CalendarFeatureFlag.ChangeLanguage.fallbackValue) {
            generalSettingsDescription = getString(
                R.string.settings_general_info_separator,
                getString(R.string.settings_general_info_language),
                generalSettingsDescription
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            generalSettingsDescription = getString(
                R.string.settings_general_info_separator,
                getString(R.string.settings_general_info_theme),
                generalSettingsDescription
            )
        }
        binding.settingsGeneralInfo.text = getString(R.string.settings_general_info, generalSettingsDescription).replaceFirstChar {
            it.titlecase(DateTimeUtilsImpl.getLocaleForFormatting())
        }

        binding.settingsCalendarsTitleAdd.setOnSingleClickListener {
            showCalendarsOptionsDialog()
        }

        binding.settingsOtherCalendarsTitleAdd.setOnSingleClickListener {
            showCalendarsOptionsDialog()
        }

        val settingsCalendarListView = binding.settingsCalendarsList
        val settingsCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsCalendarListView.layoutManager = settingsCalendarLayoutManager
        settingsUserCalendarListAdapter = SettingsCalendarListAdapter() { calendar ->
            //On Calendar click event
            showBottomSheetDialog(calendar)
        }
        (settingsCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        settingsCalendarListView.adapter = settingsUserCalendarListAdapter

        calendarViewModel.userPersonalCalendars.observe(viewLifecycleOwner) { userPersonalCalendars ->
            userPersonalCalendars ?: return@observe

            refreshUserPersonalCalendarList(userPersonalCalendars.filter { it.isActive || it.isDisabled })
        }

        val settingsOtherCalendarListView = binding.settingsOtherCalendarsList
        val settingsOtherCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        settingsOtherCalendarListView.layoutManager = settingsOtherCalendarLayoutManager
        settingsOtherCalendarListAdapter = SettingsCalendarListAdapter() { calendar ->
            //On Calendar click event
            showBottomSheetDialog(calendar)
        }
        (settingsOtherCalendarListView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        settingsOtherCalendarListView.adapter = settingsOtherCalendarListAdapter

        otherCalendarsMediator.addSource(calendarViewModel.otherCalendars) { value ->
            otherCalendars = value

            if (otherCalendars != null && calendarSubscriptions != null) {
                otherCalendarsMediator.value = Pair(otherCalendars!!, calendarSubscriptions!!)
            }
        }
        otherCalendarsMediator.addSource(calendarViewModel.calendarSubscriptions) { value ->
            calendarSubscriptions = value

            if (otherCalendars != null && calendarSubscriptions != null) {
                otherCalendarsMediator.value = Pair(otherCalendars!!, calendarSubscriptions!!)
            }
        }
        otherCalendarsMediator.observe(viewLifecycleOwner) {
            it?.let {
                lifecycleScope.launch {
                    val otherCalendars = ProtonUtilsImpl.sortOtherCalendars(it.first)
                    val calendarSubscriptions = it.second

                    val calendarSubscriptionsChanged =
                        settingsOtherCalendarListAdapter.setCalendarSubscriptions(calendarSubscriptions)

                    settingsOtherCalendarListAdapter.submitList(otherCalendars)
                    if (calendarSubscriptionsChanged) settingsOtherCalendarListAdapter.notifyDataSetChanged()
                    binding.settingsOtherCalendars.visibleOrGone(otherCalendars.isNotEmpty())
                    val userPersonalCalendars = calendarViewModel.getUserPersonalCalendars() ?: emptyList()
                    binding.settingsCalendars.visibleOrGone(userPersonalCalendars.isNotEmpty() || (userPersonalCalendars.isEmpty() && otherCalendars.isEmpty()))
                    binding.settingsOtherCalendarsTitleAdd.visibleOrGone(userPersonalCalendars.isEmpty() && otherCalendars.isNotEmpty())
                    binding.settingsCalendarsSubtitle.visibleOrGone(userPersonalCalendars.isEmpty() && otherCalendars.isEmpty())
                }
            }
        }

        calendarViewModel.defaultCalendarId.observe(viewLifecycleOwner) { defaultCalendarId ->

            if (this@SettingsFragment.defaultCalendarId != defaultCalendarId) {
                lifecycleScope.launch {
                    calendarViewModel.getUserPersonalCalendars()?.let { userPersonalCalendars ->
                        refreshUserPersonalCalendarList(userPersonalCalendars.filter { it.isActive || it.isDisabled })
                    }
                }
            }
        }

        calendarFormViewModel.calendarSettingsSnackState.asLiveData(lifecycleScope.coroutineContext).observe(viewLifecycleOwner) { calendarSettingsSnackState ->
            calendarSettingsSnackState?.let {
                when (it) {
                    is CalendarFormViewModel.CalendarFormSnackState.DisplaySnackNavigateUp -> {
                        view?.displaySnackBar(it.message)

                        findNavController().navigateUp()
                    }
                    else -> { } // We do not use the other values
                }
                calendarFormViewModel.calendarSettingsSnackState.value = null
            }
        }

        holidayCalendarViewModel.calendarSettingsSnackState.asLiveData(lifecycleScope.coroutineContext).observe(viewLifecycleOwner) { calendarSettingsSnackState ->
            calendarSettingsSnackState?.let {
                when (it) {
                    is HolidayCalendarViewModel.HolidayCalendarSnackState.DisplaySnackNavigateUp -> {
                        if (it.message.isNotEmpty()) view?.displaySnackBar(it.message)

                        findNavController().navigateUp()
                    }
                    else -> { } // We do not use the other values
                }
                holidayCalendarViewModel.calendarSettingsSnackState.value = null
            }
        }
    }

    /**
     * @param userCalendars updated user personal calendar list
     * Refreshes the user personal calendar list with the new set of data. Get the emails linked to each calendar and
     * get the current default calendar id. Sort the list by following order: default / active / disabled.
     */
    private fun refreshUserPersonalCalendarList(userPersonalCalendars: List<Calendar>) {
        lifecycleScope.launch {
            val otherCalendars = calendarViewModel.getOtherCalendars() ?: emptyList()
            binding.settingsCalendars.visibleOrGone(userPersonalCalendars.isNotEmpty() || (userPersonalCalendars.isEmpty() && otherCalendars.isEmpty()))
            binding.settingsOtherCalendarsTitleAdd.visibleOrGone(userPersonalCalendars.isEmpty() && otherCalendars.isNotEmpty())
            binding.settingsCalendarsSubtitle.visibleOrGone(userPersonalCalendars.isEmpty() && otherCalendars.isEmpty())
            lifecycleScope.launch {
                val defaultCalendarId = calendarViewModel.getDefaultCalendarIdWithFallback(allowShared = false)
                this@SettingsFragment.defaultCalendarId = defaultCalendarId
                val dataSetChanged: Boolean = settingsUserCalendarListAdapter.setDefaultCalendarId(defaultCalendarId)
                settingsUserCalendarListAdapter.submitList(sortPersonalCalendars(userPersonalCalendars, defaultCalendarId))
                if (dataSetChanged) settingsUserCalendarListAdapter.notifyDataSetChanged()
            }
        }
    }

    /**
     * Displays the bottom sheet dialog with the calendar name as a header, and the following button as a content:
     * Edit, Mark as default, Delete.
     * Buttons visibility varies with the calendar type and status.
     */
    private fun showBottomSheetDialog(calendar: Calendar) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window?.navigationBarColor = requireContext().getColorFromAttr(
                R.attr.proton_background_norm
            )
        }

        bottomSheetDialog.setContentView(R.layout.dialog_calendar_settings)

        val calendarIcon = bottomSheetDialog.findViewById<ImageView>(R.id.dialog_calendar_settings_calendar_icon)
        calendarIcon?.imageTintList = ColorStateList.valueOf(Color.parseColor(calendar.color))

        val calendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_settings_calendar_title)
        calendarName?.text = calendar.name

        val deleteTitle = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_settings_delete_title)
        deleteTitle?.text = getString(
            if (calendar.isHolidayCalendar) R.string.action_remove
            else if (calendar.isSharedWithMe) R.string.action_leave
            else R.string.action_delete
        )

        val editPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_edit_press)
        val markDefaultPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_default_press)
        val deletePress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_delete_press)
        val recreatePress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_settings_recreate_press)

        editPress?.setOnSingleClickListener {
            val bundle = Bundle().apply {
                putString(CALENDAR_ID_ARG, calendar.id)
            }
            if (calendar.isHolidayCalendar) {
                findNavController().navigate(R.id.action_nav_settings_to_nav_holiday_calendar_form, bundle)
            } else {
                findNavController().navigate(R.id.action_nav_settings_to_nav_calendar_form, bundle)
            }
            bottomSheetDialog.dismiss()
        }

        markDefaultPress?.setOnSingleClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                bottomSheetDialog.dismiss()
                view?.displaySnackBar(requireContext().getString(R.string.snack_network_error))
                return@setOnSingleClickListener
            }

            lifecycleScope.launch {
                val updateDefaultCalendarId = calendarViewModel.updateDefaultCalendarId(calendar.id)
                if (updateDefaultCalendarId) {
                    calendarViewModel.getUserPersonalCalendars()?.let { userPersonalCalendars ->
                        refreshUserPersonalCalendarList(userPersonalCalendars.filter { it.isActive || it.isDisabled })
                    }
                    view?.displaySnackBar(requireContext().getString(R.string.snack_update_default_calendar))
                } else {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_update_default_calendar_error))
                }
            }
            bottomSheetDialog.dismiss()
        }

        deletePress?.setOnSingleClickListener {
            lifecycleScope.launch {

                if (calendar.isHolidayCalendar) {
                    bottomSheetDialog.dismiss()
                    with (MaterialAlertDialogBuilder(requireContext())) {
                        setTitle(resourceProvider.provideString(R.string.remove_calendar_dialog_title))
                        setMessage(resourceProvider.provideString(R.string.remove_calendar_dialog_message))
                        setPositiveButton(R.string.action_remove) { _, _ ->
                            lifecycleScope.launch {
                                when (calendarViewModel.leaveHolidayCalendar(calendar.id)) {
                                    is UseCase.Result.Error -> view?.displaySnackBar(resourceProvider.provideString(R.string.remove_calendar_snack_error))
                                    is UseCase.Result.InvalidParams -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error_password_confirmation))
                                    is UseCase.Result.Success<*> -> view?.displaySnackBar(resourceProvider.provideString(R.string.remove_calendar_snack_removed))
                                }
                                bottomSheetDialog.dismiss()
                            }
                        }
                        setNegativeButton(R.string.dialog_button_cancel, null)
                    }.create().show()
                } else if (calendar.isSharedWithMe) {
                    bottomSheetDialog.dismiss()
                    with (MaterialAlertDialogBuilder(requireContext())) {
                        setTitle(resourceProvider.provideString(R.string.leave_calendar_dialog_title))
                        setMessage(resourceProvider.provideString(R.string.leave_calendar_dialog_message))
                        setPositiveButton(R.string.action_leave) { _, _ ->
                            lifecycleScope.launch {
                                when (calendarViewModel.leaveSharedCalendar(calendar.id, calendar.memberId)) {
                                    is UseCase.Result.Error -> view?.displaySnackBar(resourceProvider.provideString(R.string.leave_calendar_snack_error))
                                    is UseCase.Result.InvalidParams -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error_password_confirmation))
                                    is UseCase.Result.Success<*> -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_deleted))
                                }
                                bottomSheetDialog.dismiss()
                            }
                        }
                        setNegativeButton(R.string.dialog_button_cancel, null)
                    }.create().show()
                } else {

                    val prepareOption = calendarViewModel.prepareDeleteCalendar(calendar.id)

                    val dialogMessage = when (prepareOption) {
                        is DeleteCalendarUseCase.DeleteCalendarOption.Delete.DefaultLastActive -> resourceProvider.provideString(R.string.delete_calendar_dialog_message)
                        is DeleteCalendarUseCase.DeleteCalendarOption.Delete.DefaultNextActive -> resourceProvider.provideString(R.string.delete_default_calendar_dialog_message, prepareOption.nextDefaultName)
                        is DeleteCalendarUseCase.DeleteCalendarOption.Error -> null
                        is DeleteCalendarUseCase.DeleteCalendarOption.Delete.NonDefault -> resourceProvider.provideString(R.string.delete_calendar_dialog_message)
                    }

                    if (prepareOption is DeleteCalendarUseCase.DeleteCalendarOption.Error) {
                        bottomSheetDialog.dismiss()
                        view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error))
                    } else {
                        bottomSheetDialog.dismiss()
                        with (MaterialAlertDialogBuilder(requireContext())) {
                            setTitle(resourceProvider.provideString(R.string.delete_calendar_dialog_title))
                            setMessage(dialogMessage)
                            setPositiveButton(R.string.dialog_button_delete) { _, _ ->
                                lifecycleScope.launch {
                                    when (calendarViewModel.deleteCalendar(prepareOption)) {
                                        is UseCase.Result.Error -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error))
                                        is UseCase.Result.InvalidParams -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error_password_confirmation))
                                        is UseCase.Result.Success<*> -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_deleted))
                                    }
                                    bottomSheetDialog.dismiss()
                                }
                            }
                            setNegativeButton(R.string.dialog_button_cancel, null)
                        }.create().show()
                    }
                }
            }
        }

        recreatePress?.setOnSingleClickListener {
            lifecycleScope.launch {
                bottomSheetDialog.dismiss()
                with (MaterialAlertDialogBuilder(requireContext())) {
                    setTitle(resourceProvider.provideString(R.string.recreate_calendar_dialog_title))
                    setMessage(resourceProvider.provideString(R.string.recreate_calendar_dialog_message))
                    setPositiveButton(R.string.action_recreate) { _, _ ->
                        lifecycleScope.launch {
                            val snackBar = view?.displaySnackBar(getString(R.string.recreate_calendar_snack_clearing), Snackbar.LENGTH_INDEFINITE)
                            when (calendarViewModel.recreateCalendar(calendar.id)) {
                                is UseCase.Result.Error -> view?.displaySnackBar(resourceProvider.provideString(R.string.recreate_calendar_snack_error))
                                is UseCase.Result.InvalidParams -> view?.displaySnackBar(resourceProvider.provideString(R.string.delete_calendar_snack_error_password_confirmation))
                                is UseCase.Result.Success<*> -> view?.displaySnackBar(resourceProvider.provideString(R.string.recreate_calendar_snack_deleted))
                            }
                            snackBar?.dismiss()
                            bottomSheetDialog.dismiss()
                        }
                    }
                    setNegativeButton(R.string.dialog_button_cancel, null)
                }.create().show()
            }
        }

        lifecycleScope.launch {
            val editLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_edit)
            editLayout?.visibleOrGone(
                (calendar.isSubscribed.not() && calendar.isOwner) || // my own personal calendar
                        calendar.isSubscribed || // subscribed calendar
                        (calendar.isSharedWithMe && CalendarFeatureFlag.EditingSharedCalendars.fallbackValue) || // shared calendar
                        calendar.isHolidayCalendar // holiday calendar
            )

            val deleteLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_delete)
            deleteLayout?.visibleOrGone(CalendarFeatureFlag.DeleteCalendar.fallbackValue)

            val recreateLayout = bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_recreate)
            recreateLayout?.visibleOrGone(
                CalendarFeatureFlag.ClearCalendar.fallbackValue &&
                        calendar.isSubscribed.not() &&
                        calendar.isSharedWithMe.not() &&
                        calendar.isHolidayCalendar.not() &&
                        calendar.isActive
            )

            val markAsDefaultLayout =
                bottomSheetDialog.findViewById<ConstraintLayout>(R.id.dialog_calendar_settings_default)
            markAsDefaultLayout?.visibleOrGone(
                calendar.id != defaultCalendarId &&
                        calendar.isActive &&
                        calendar.isSubscribed.not() &&
                        calendar.isSharedWithMe.not() &&
                        calendar.isHolidayCalendar.not()
            )

            bottomSheetDialog.show()
        }
    }

    private fun showCalendarsOptionsDialog() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())

        // Workaround to make sure we have the correct navigation bar color.
        // TODO update once we change splash screen and how we handle navigation bar colors
        val window = bottomSheetDialog.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val navigationBarBackgroundColor = R.color.background_norm
            window?.navigationBarColor = resources.getColor(navigationBarBackgroundColor, null)
        } else {
            window?.navigationBarColor = requireContext().getColorFromAttr(
                R.attr.proton_background_norm
            )
        }

        bottomSheetDialog.setContentView(R.layout.dialog_calendars_create_import)

        val createCalendarPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendars_create_press)
        val addHolidayCalendarPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendars_holiday_calendar_press)
        val importFromGooglePress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendars_import_press)

        createCalendarPress?.setOnSingleClickListener {
            onClickCreateCalendar(Calendar.CalendarType.NORMAL)
            bottomSheetDialog.dismiss()
        }

        addHolidayCalendarPress?.setOnSingleClickListener {
            onClickCreateCalendar(Calendar.CalendarType.HOLIDAY)
            bottomSheetDialog.dismiss()
        }

        importFromGooglePress?.setOnSingleClickListener {
            if (!mainViewModel.isConnectedToNetwork) {
                bottomSheetDialog.dismiss()
                view?.displaySnackBar(this.getString(R.string.snack_network_error), Snackbar.LENGTH_LONG)
                return@setOnSingleClickListener
            }

            (requireActivity() as MainActivity).showImportGoogleAuthDialog()
            bottomSheetDialog.dismiss()
        }

        lifecycleScope.launch {

            if (!calendarViewModel.displayImport()) {
                val importFromGoogleLayout = bottomSheetDialog.findViewById<View>(R.id.dialog_calendars_import)
                importFromGoogleLayout?.visibleOrGone(false)
            }

            bottomSheetDialog.show()
        }
    }

    private fun onClickCreateCalendar(calendarType: Calendar.CalendarType) {
        lifecycleScope.launch {
            // Check if calendar limit was reached
            when (calendarViewModel.isCalendarLimitReached(calendarType)) {
                CalendarViewModel.CalendarLimit.ERROR -> {
                    view?.displaySnackBar(this@SettingsFragment.getString(R.string.snack_create_calendar_error))
                }
                CalendarViewModel.CalendarLimit.NOT_REACHED -> {
                    // If limit has not been reached, open calendar form
                    when (calendarType) {
                        Calendar.CalendarType.NORMAL -> findNavController().navigate(R.id.action_nav_settings_to_nav_calendar_form)
                        Calendar.CalendarType.HOLIDAY -> findNavController().navigate(R.id.action_nav_settings_to_nav_holiday_calendar_form)
                        Calendar.CalendarType.SUBSCRIBED -> {} // Creating subscribed calendar has not yet been implemented
                    }
                }
                CalendarViewModel.CalendarLimit.FREE_REACHED -> {
                    // Display limit reached for free user dialog
                    requireContext().displayFreeUserCalendarLimitReached()
                }
                CalendarViewModel.CalendarLimit.FREE_MANDATORY_PERSONAL_REACHED -> {
                    // Display mandatory personal calendar limit reached for free user dialog
                    requireContext().displayFreeUserMandatoryPersonalCalendarLimitReached()
                }
                CalendarViewModel.CalendarLimit.PAID_REACHED -> {
                    // Display limit reached for paid user dialog
                    requireContext().displayPaidUserCalendarLimitReached()
                }
                CalendarViewModel.CalendarLimit.PAID_MANDATORY_PERSONAL_REACHED -> {
                    // Display mandatory personal calendar limit reached for paid user dialog
                    requireContext().displayPaidUserMandatoryPersonalCalendarLimitReached()
                }
            }
        }
    }
}
