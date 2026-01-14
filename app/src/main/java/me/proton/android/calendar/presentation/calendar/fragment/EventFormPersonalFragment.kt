package me.proton.android.calendar.presentation.calendar.fragment

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.preference.PreferenceManager
import biweekly.property.Action
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.FragmentArguments
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.utils.AndroidUtils
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ColorUtils
import me.proton.android.calendar.databinding.FragmentEventFormPersonalBinding
import me.proton.android.calendar.databinding.ItemAlarmTextButtonBinding
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.EventViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.FeatureFlagViewModel
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.user.domain.extension.hasSubscriptionForMail
import org.koin.android.ext.android.inject
import org.koin.core.KoinComponent
import kotlin.coroutines.CoroutineContext

class EventFormPersonalFragment() : BaseDialogFragment<FragmentEventFormPersonalBinding>(), KoinComponent {

    private val navigationArguments: EventFormPersonalFragmentArgs by navArgs()

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val eventViewModel: EventViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val featureFlagViewModel: FeatureFlagViewModel by activityViewModels()

    override val TAG = "EventFormPersonalFragment" // TODO
    override val layoutResourceId = R.layout.fragment_event_form_personal
    override val isScrollable = false

    override val navigateUp = false

    private val logger: Logger by inject()

    private lateinit var loadingAction: View
    private lateinit var buttonSave: View

    override fun onBackPressedCustom() {
        val processingEvent = eventViewModel.eventFormState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                navigateBackToDetails()
            }
        } else navigateBackToDetails()
    }

    override fun onNavigationIconClicked(): Boolean {
        val processingEvent = eventViewModel.eventFormState.value is EventViewModel.EventState.Processing
        if (processingEvent) {
            view?.displaySnackBar(getString(R.string.snack_event_saving))
            return true
        }
        if (eventViewModel.hasEventBeenEdited()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                jumpToMonthView()
            }
        } else jumpToMonthView()
        return true
    }

    private fun jumpToMonthView() {
        activity?.clearFocusAndHideKeyboard(view)
        if (!findNavController().popBackStack(R.id.nav_calendar, false)) {
            // TODO this is a workaround for navigating back to month view after opening EventForm from EventDetails
            //  that was opened from system notification
            //  R.id.nav_calendar is not in the hierarchy so popping backstack will fail and we need
            //  to navigate manually
            findNavController().navigate(Navigation.Deeplink.toMonth())
        }
        mainViewModel.triggerMainViewActions.update { true }
    }

    private fun navigateBackToDetails() {
        // Reinitialise event view model data
        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus =
                if (userId == null) EventViewModel.InitResult.Error.Default("user ID is null in EventDetailsFragment onViewCreated")
                else eventViewModel.initialise(
                    userId,
                    editMode = false,
                    meetIntegrations = featureFlagViewModel.enabledMeetIntegrations(),
                    navigationArguments.eventId,
                    if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                    null,
                    null
                )
            if (viewModeInitStatus == EventViewModel.InitResult.Success) {
                requireActivity().clearFocusAndHideKeyboard(view)
                findNavController().navigateUp()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.InitResult.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.InitResult.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.InitResult.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(getString(R.string.snack_event_opening_error))
                    }
                    else -> Unit // TODO refactor and use one `when` expression
                }
                jumpToMonthView()
            }
        }
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.event_discard_changes_title)
            .setMessage(R.string.event_discard_changes_description)
            .setPositiveButton(R.string.event_discard_changes_confirm, callback)
            .setNegativeButton(R.string.event_discard_changes_cancel) { _, _ -> }
            .show()
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSave = layoutInflater.inflate(R.layout.toolbar_action_text, dialogToolbarContent, false)
        with (buttonSave) {
            (findViewById<TextView>(R.id.toolbar_action_text)).text = getString(R.string.action_save)
            setOnSingleClickListener {
                // Go back to main view if no changes have been made
                if (eventViewModel.hasEventBeenEdited().not()) {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    findNavController().navigateUp()
                    return@setOnSingleClickListener
                }

                if (!mainViewModel.isConnectedToNetwork) {
                    view?.displaySnackBar(getString(R.string.snack_network_error))
                    return@setOnSingleClickListener
                }

                lifecycleScope.launch {
                    eventViewModel.onSavePersonalClick(
                        provideDisplayDialog(),
                        navigationArguments.eventId!!
                    )
                }
            }
        }

        loadingAction = layoutInflater.inflate(R.layout.toolbar_action_loader, dialogToolbarContent, false)
        loadingAction.visibleOrGone(false)

        // TODO extract somewhere to remove boilerplate
        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            addView(
                buttonSave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            )
            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                loadingAction, layoutParams
            )
        }
    }
    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentEventFormPersonalBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {

            val userId = accountViewModel.getPrimaryUserId()
            val viewModeInitStatus = withContext(Dispatchers.Main) {
                if (userId == null) EventViewModel.InitResult.Error.Default("user ID is null in EventDetailsFragment onViewCreated")
                else {
                    eventViewModel.initialise(
                        userId,
                        editMode = true,
                        meetIntegrations = featureFlagViewModel.enabledMeetIntegrations(),
                        navigationArguments.eventId,
                        if (navigationArguments.occurrenceNumber == 0) null else navigationArguments.occurrenceNumber,
                        null,
                        null
                    )
                }
            }

            if (viewModeInitStatus == EventViewModel.InitResult.Success) {
                launch {
                    eventViewModel.getSingleEditsInfo()
                }
                observeEventLiveData()
                observeEventSnackState(coroutineContext)
                attachActionHandlers()
            } else {
                when (viewModeInitStatus) {
                    EventViewModel.InitResult.OccurrenceDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_occurrence_does_not_exist)
                        )
                    }
                    EventViewModel.InitResult.EventDoesNotExist -> {
                        AndroidUtils.displaySimpleOkAlert(
                            requireContext(),
                            getString(R.string.error_event_does_not_exist)
                        )
                    }
                    is EventViewModel.InitResult.Error -> {
                        logger.e(viewModeInitStatus.message)
                        requireActivity().displaySnackBar(
                            if (navigationArguments.eventId != null) getString(R.string.snack_event_opening_edit_error)
                            else {
                                when (viewModeInitStatus) {
                                    is EventViewModel.InitResult.Error.InitDefaultCalendarError -> getString(R.string.snack_create_event_no_active_calendar)
                                    is EventViewModel.InitResult.Error.Default -> getString(R.string.snack_event_init_error)
                                }
                            }
                        )
                    }
                    else -> Unit
                }
                if (navigationArguments.eventId == null) jumpToMonthView()
                else onBackPressedCustom()
            }

            eventViewModel.eventFormState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventState ->
                val processingEvent = eventState is EventViewModel.EventState.Processing

                // Update action bar buttons visibility
                loadingAction.visibleOrGone(processingEvent)
                buttonSave.visibleOrGone(!processingEvent)

                // Disable/Enable all items linked to actions from our view
                binding.eventFormPersonalAlarmPress.root.isEnabled = !processingEvent
                binding.eventFormPersonalColorPress.root.isEnabled = !processingEvent

                for (i in 0 until binding.eventFormPersonalAlarmList.childCount) {
                    // Disable the delete buttons from inside alarm items views
                    binding.eventFormPersonalAlarmList.getChildAt(i)
                        .findViewById<View>(R.id.item_simple_text_button_delete).isEnabled = !processingEvent
                }

                if (eventState is EventViewModel.EventState.UserAddressInvalidForEncryption) {
                    lifecycleScope.launch {
                        invalidUserAddressLogoutHack()
                    }
                }
            }
        }
    }

    // TODO remove when the issue with invalid sender Address is fixed
    private suspend fun invalidUserAddressLogoutHack() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (sharedPreferences.getBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, false)) {
            logger.e("EventFormPersonalFragment: hack was performed but we force logout again")
        }

        with (sharedPreferences.edit()) {
            putBoolean(SharedPreferencesKeys.HACK_USER_ADDRESS_INVALID_FOR_SENDING, true)
            apply()
        }

        logger.i("EventFormPersonalFragment: hack detected invalid user address, logging out")
        accountViewModel.logoutPrimary()
        calendarViewModel.shutdown()
    }

    private fun observeEventLiveData() {
        eventViewModel.eventLiveData.observe(viewLifecycleOwner, Observer { nullableEvent: Event? ->

            val event = nullableEvent ?: return@Observer

            binding.eventFormPersonalTitle.text = event.summary

            displayAlarms()

            val isColorPerEventEnabled = featureFlagViewModel.isColorPerEventEnabled()
            binding.eventFormPersonalColorLayout.visibleOrGone(isColorPerEventEnabled)
            if (isColorPerEventEnabled) {
                val isFreeUser = eventViewModel.user.hasSubscriptionForMail().not()
                binding.eventFormPersonalColor.text = ColorUtils.getColorNameForHex(
                    resources.getStringArray(R.array.colors_names),
                    resources.getStringArray(R.array.colors_values),
                    event.getDisplayColor(isFreeUser)
                ) ?: getString(R.string.undefined_color)
                binding.eventFormPersonalColorDefault.visibleOrGone(event.getDisplayColor(isFreeUser) == event.calendar.color)

                ImageViewCompat.setImageTintList(
                    binding.eventFormPersonalColorIcon,
                    ColorStateList.valueOf(Color.parseColor(event.getDisplayColor(isFreeUser)))
                )
            }
        })
    }

    private fun displayAlarms() {
        binding.eventFormPersonalAlarmList.removeAllViews()
        binding.eventFormPersonalAlarmIcon.visibleOrGone(true)

        val event = eventViewModel.eventLiveData.value!!

        event.alarms.filter { it.action == Action.display() || it.action == Action.email() }.forEachIndexed { index, alarm ->

            val alarmViewBinding = ItemAlarmTextButtonBinding.inflate(layoutInflater, binding.eventFormPersonalAlarmList, false)
            alarmViewBinding.itemSimpleTextButtonTitle.apply {
                text = AndroidUtils.formatAlarm(resources, event.isAllDay(), eventViewModel.userSettings.timeFormatIs24Hour(
                    DateFormat.is24HourFormat(requireContext())), event.getStart(eventViewModel.eventTimeZoneId), alarm)
                isClickable = false
            }
            alarmViewBinding.itemSimpleTextButtonDelete.apply {
                setOnSingleClickListener {
                    requireActivity().clearFocusAndHideKeyboard(view)
                    eventViewModel.handleAlarmDelete(alarm)
                }
                isClickable = true
            }
            if (index == 0) binding.eventFormPersonalAlarmIcon.visibleOrGone(false)
            binding.eventFormPersonalAlarmList.addView(alarmViewBinding.root)
        }

        // "add alarm" button
        binding.eventFormPersonalAlarmPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            eventViewModel.initialiseForAlarm()
            val bundle = Bundle().apply {
                putBoolean(FragmentArguments.IS_ALL_DAY_ARG, eventViewModel.eventLiveData.value!!.isAllDay())
                putInt(FragmentArguments.DEFAULT_NOTIFICATIONS_TYPE_ARG, EventFormAlarmFragment.DefaultNotificationsType.EVENT.value)
            }
            findNavController().navigate(R.id.nav_event_form_alarm, bundle)
        }
        binding.eventFormPersonalAlarm.visibleOrGone(!eventViewModel.isAlarmLimitReached())
    }

    private fun attachActionHandlers() {
        val isFreeUser = eventViewModel.user.hasSubscriptionForMail().not()
        binding.eventFormPersonalColorPress.root.setOnSingleClickListener {
            requireActivity().clearFocusAndHideKeyboard(view)
            lifecycleScope.launch {
                if (isFreeUser) {
                    view?.displaySnackBar(getString(R.string.snack_color_per_event_paid_feature))
                } else {
                    ColorUtils.displayColorPicker(
                        requireContext(),
                        resources.getString(R.string.dialog_title_event_color_picker),
                        resources.getStringArray(R.array.colors_names),
                        resources.getStringArray(R.array.colors_values),
                        eventViewModel.eventLiveData.value!!.getDisplayColor(isFreeUser),
                        eventViewModel.eventLiveData.value!!.calendar.color
                    ) {
                        eventViewModel.handleColor(it)
                    }
                }
            }
        }
    }

    private fun observeEventSnackState(coroutineContext: CoroutineContext) {
        eventViewModel.eventFormSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { eventSnackState ->
            eventSnackState?.let {
                when (it) {
                    is EventViewModel.EventSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                    is EventViewModel.EventSnackState.DisplaySnackReturnToMonth -> {
                        // Should not happen
                        requireActivity().displaySnackBar(it.message)
                        // Use jumpToMonthView to handle navigation when opening details from notification
                        jumpToMonthView()
                    }

                    else -> {}
                }
                eventViewModel.eventFormSnackState.value = null
            }
        }
    }
}
