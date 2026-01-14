package me.proton.android.calendar.presentation.importAssistant.fragment

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.MAX_CALENDAR_FREE
import me.proton.android.calendar.common.MAX_CALENDAR_PAID
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.dpToPixel
import me.proton.android.calendar.common.utils.AndroidUtils.getColorFromAttr
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.common.utils.ColorUtils
import me.proton.android.calendar.databinding.FragmentImportAssistantBinding
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.ImportCalendarMapping
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.adapter.ImportCalendarMappingListAdapter
import me.proton.android.calendar.presentation.importAssistant.adapter.MergeCalendarListAdapter
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.presentation.ui.view.ProtonProgressButton
import org.koin.core.KoinComponent

@AndroidEntryPoint
class ImportAssistantFragment : BaseDialogFragment<FragmentImportAssistantBinding>(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant

    override val navigateUp = false
    override val isScrollable = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by activityViewModels()

    private val navigationArguments: ImportAssistantFragmentArgs by navArgs()

    private lateinit var importCalendarMappingListAdapter: ImportCalendarMappingListAdapter

    override fun onBackPressedCustom() {
        if (binding.fragmentImportAssistantImportButton.currentState == ProtonProgressButton.State.LOADING) {
            view?.displaySnackBar(getString(R.string.import_assistant_in_progress_snack))
            return
        }
        if (binding.fragmentImportAssistantCloseButton.visibility == View.VISIBLE) findNavController().navigateUp()
        else {
            displayDiscardChangesConfirmationDialog { _, _ ->
                findNavController().navigateUp()
            }
        }
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.import_assistant_discard_import_title)
            .setMessage(R.string.import_assistant_discard_import_message)
            .setPositiveButton(R.string.import_assistant_discard_import_positive, callback)
            .setNegativeButton(R.string.import_assistant_discard_import_negative) { _, _ -> }
            .show()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = "" // No Title
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentImportAssistantBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importAssistantViewModel.resetViewModel()

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
            userCalendars ?: return@observe

            if (this::importCalendarMappingListAdapter.isInitialized) {
                importCalendarMappingListAdapter.isOptionsEnabled(userCalendars.any { it.isActive && it.allowEditEvents })
            }

            checkCalendarLimit()
        }

        calendarViewModel.otherCalendars.observe(viewLifecycleOwner) { otherCalendars ->
            otherCalendars ?: return@observe

            checkCalendarLimit()
        }

        binding.fragmentImportAssistantSummaryCustomizeImportLayout.setOnSingleClickListener {
            binding.fragmentImportAssistantScrollView.smoothScrollTo(
                0,
                binding.fragmentImportAssistantSummaryCustomizeImportLayout.bottom + binding.fragmentImportAssistantIllustration.bottom + requireContext().dpToPixel(34)
            )
        }

        binding.fragmentImportAssistantImportButton.setOnSingleClickListener {
            if (binding.fragmentImportAssistantImportButton.currentState == ProtonProgressButton.State.LOADING) return@setOnSingleClickListener
            onStartImportClick()
        }

        // Show loader view by default
        showGatheringDataView()

        initExternalCalendarList()

        importAssistantViewModel.sourceEmail.observe(viewLifecycleOwner) { sourceEmail ->
            binding.fragmentImportAssistantSummaryEmail.text = sourceEmail
        }

        importAssistantViewModel.importCalendarMappingList.observe(viewLifecycleOwner) { importCalendarMappingList ->
            importCalendarMappingList ?: return@observe

            showImportSummaryView(importCalendarMappingList)

            checkCalendarLimit()
        }

        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            if (userId != null) {
                // Create importer and fetch external calendars
                if (!importAssistantViewModel.handleGoogleSignInRedirect(
                        userId,
                        navigationArguments.code,
                        resources.getStringArray(R.array.colors_values)
                    )) {
                    // Display error snack and navigate back
                    if (findNavController().previousBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                        importAssistantViewModel.importGuideSnackState.value =
                            ImportAssistantViewModel.ImportSnackState.DisplaySnack(getString(R.string.import_assistant_data_gathering_error))
                    } else {
                        requireActivity().displaySnackBar(getString(R.string.import_assistant_data_gathering_error))
                    }
                    findNavController().navigateUp()
                }
            } else {
                // Display error snack and navigate back
                if (findNavController().previousBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
                    importAssistantViewModel.importGuideSnackState.value =
                        ImportAssistantViewModel.ImportSnackState.DisplaySnack(getString(R.string.snack_network_error))
                } else {
                    requireActivity().displaySnackBar(getString(R.string.snack_network_error))
                }
                findNavController().navigateUp()
            }
        }
    }

    private fun initExternalCalendarList() {
        val externalCalendarListView = binding.fragmentImportAssistantSummaryList
        val externalCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        externalCalendarListView.layoutManager = externalCalendarLayoutManager
        importCalendarMappingListAdapter = ImportCalendarMappingListAdapter(
            importListener = { importCalendarMapping ->
                // On external calendar click
                val indexOfItem = importAssistantViewModel.setImportCalendar(importCalendarMapping, !importCalendarMapping.importCalendar)
                indexOfItem?.let {
                    importCalendarMappingListAdapter.notifyItemChanged(indexOfItem)
                }
            },
            optionsListener = { importCalendarMapping ->
                // On options click
                lifecycleScope.launch {
                    val activeWritableUserCalendars = calendarViewModel.getUserCalendars()?.filter { it.isActive && it.allowEditEvents }
                    showBottomSheetDialog(importCalendarMapping, activeWritableUserCalendars)
                }
            }
        )
        externalCalendarListView.adapter = importCalendarMappingListAdapter
    }

    private fun checkCalendarLimit() {
        lifecycleScope.launch {
            val calendarsCount = calendarViewModel.getCalendarsCount() ?: return@launch
            val importCalendarMappingList = importAssistantViewModel.importCalendarMappingList.value ?: return@launch
            val importCalendarsToCreateCount = importCalendarMappingList.filter { it.createDestinationCalendar && it.importCalendar }.size
            val importCalendarsToImportCount = importCalendarMappingList.filter { it.importCalendar }.size

            val isFreeUser = calendarViewModel.isFreeUser() ?: return@launch
            if (importCalendarsToCreateCount > 0 &&
                (isFreeUser && (calendarsCount + importCalendarsToCreateCount) > MAX_CALENDAR_FREE ||
                        !isFreeUser && (calendarsCount + importCalendarsToCreateCount) > MAX_CALENDAR_PAID)) {
                binding.fragmentImportAssistantSummaryHeaderLayout.visibleOrGone(false)
                binding.fragmentImportAssistantSummaryErrorLayout.visibleOrGone(true)
                val countCalendarsOverLimit =
                    if (isFreeUser) {
                        if (calendarsCount >= MAX_CALENDAR_FREE) importCalendarsToCreateCount
                        else calendarsCount + importCalendarsToCreateCount - MAX_CALENDAR_FREE
                    } else {
                        if (calendarsCount >= MAX_CALENDAR_PAID) importCalendarsToCreateCount
                        else calendarsCount + importCalendarsToCreateCount - MAX_CALENDAR_PAID
                    }

                val activeWritableUserCalendarsCount = calendarViewModel.userCalendars.value?.filter { it.isActive && it.allowEditEvents }?.size
                // Only show merge calendars disclaimer if we can merge
                val mergeCalendarsMessage = if (activeWritableUserCalendarsCount != null && activeWritableUserCalendarsCount > 0) {
                    resources.getQuantityString(
                        R.plurals.import_assistant_import_summary_error_merge,
                        countCalendarsOverLimit,
                        countCalendarsOverLimit
                    )
                } else ""
                binding.fragmentImportAssistantSummaryError.text = resources.getQuantityString(
                    R.plurals.import_assistant_import_summary_error,
                    countCalendarsOverLimit,
                    countCalendarsOverLimit
                ) + mergeCalendarsMessage

                importButtonIsEnabled(false)
                if (this@ImportAssistantFragment::importCalendarMappingListAdapter.isInitialized) {
                    importCalendarMappingListAdapter.setLimitReached(true)
                }
            } else {
                binding.fragmentImportAssistantSummaryHeaderLayout.visibleOrGone(true)
                binding.fragmentImportAssistantSummaryErrorLayout.visibleOrGone(false)
                if (importCalendarsToImportCount > 0) importButtonIsEnabled(true)
                if (this@ImportAssistantFragment::importCalendarMappingListAdapter.isInitialized) {
                    importCalendarMappingListAdapter.setLimitReached(false)
                }
            }
        }
    }

    // TODO Remove this method and only use isEnabled once core ProtonButton has been updated
    private fun importButtonIsEnabled(isEnabled: Boolean) {
        binding.fragmentImportAssistantImportButton.isEnabled = isEnabled

        // TODO Remove custom disabled style once core ProtonButton has been updated
        binding.fragmentImportAssistantImportButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_inverted))
        binding.fragmentImportAssistantImportButton.backgroundTintList = ColorStateList.valueOf(
            requireContext().getColorFromAttr(
                if (isEnabled) R.attr.proton_interaction_norm
                else R.attr.proton_interaction_norm_disabled
            )
        )
    }

    private fun onStartImportClick() {
        val calendarsToImport = importAssistantViewModel.importCalendarMappingList.value?.filter { it.importCalendar }
        if (calendarsToImport.isNullOrEmpty()) {
            view?.displaySnackBar(getString(R.string.import_assistant_start_import_empty_error))
            return
        }
        lifecycleScope.launch {
            // Display loading state on import button
            binding.fragmentImportAssistantImportButton.setLoading()

            if (calendarsToImport.any { it.createDestinationCalendar }) {
                // Create new calendars
                if (!createCalendars(calendarsToImport)) {
                    // Update list in VM
                    val updatedCalendarsList = ArrayList<ImportCalendarMapping>()
                    updatedCalendarsList.addAll(calendarsToImport)
                    updatedCalendarsList.addAll(importAssistantViewModel.importCalendarMappingList.value?.filter { it.importCalendar.not() } ?: arrayListOf())
                    importAssistantViewModel.setImportCalendarMappingList(updatedCalendarsList)

                    // If we fail to create one or more calendar, we display error to the user and display import summary view
                    showImportSummaryView(calendarsToImport)
                    view?.displaySnackBar(getString(R.string.import_assistant_create_calendar_error))
                    return@launch
                }
            }

            // Start the import
            val startImportResult = importAssistantViewModel.startImport(
                customCalendarMapping = calendarsToImport.any { it.mergeCalendar }, // If we're merging a calendar then user has custom mapping
                importCalendarMappingList = calendarsToImport
            )

            // Reset import button state
            binding.fragmentImportAssistantImportButton.setIdle()

            when (startImportResult) {
                is ImportAssistantViewModel.ImportResult.Error -> {
                    // Update list in VM
                    val updatedCalendarsList = ArrayList<ImportCalendarMapping>()
                    updatedCalendarsList.addAll(calendarsToImport)
                    updatedCalendarsList.addAll(importAssistantViewModel.importCalendarMappingList.value?.filter { it.importCalendar.not() } ?: arrayListOf())
                    importAssistantViewModel.setImportCalendarMappingList(updatedCalendarsList)
                    // We display error to the user and display import summary view
                    showImportSummaryView(updatedCalendarsList)
                    view?.displaySnackBar(
                        startImportResult.userErrorMessage ?: getString(R.string.import_assistant_start_import_error)
                    )
                }
                is ImportAssistantViewModel.ImportResult.Success -> {
                    showImportInProgressView()
                }
            }
        }
    }

    private suspend fun createCalendars(calendarsToImport: List<ImportCalendarMapping>): Boolean {
        // Create new calendars
        val calendarsToCreateCount = calendarsToImport.count { it.createDestinationCalendar }
        var calendarsCreatedCount = 0

        // Display creating new calendars loader view
        showCreatingCalendarsView(
            calendarsToCreateCount,
            calendarsCreatedCount,
            calendarsToImport.first { it.createDestinationCalendar }.destinationEmail
        )

        var allCalendarCreated = true
        calendarsToImport.forEachIndexed { index, calendarToImport ->
            if (calendarToImport.createDestinationCalendar && calendarToImport.destinationId == null) {
                // Create calendar
                val newCalendarId = importAssistantViewModel.createCalendar(
                    calendarToImport.destinationName,
                    calendarToImport.destinationDescription,
                    calendarToImport.destinationEmail,
                    calendarToImport.destinationColor
                )

                if (newCalendarId == null) allCalendarCreated = false

                // Set newly created calendar ID
                if (newCalendarId != null) {
                    calendarsToImport[index].destinationId = newCalendarId
                    calendarsToImport[index].createDestinationCalendar = false // We set this to false so that in case of error we already preselect the newly created calendar
                    calendarsCreatedCount++
                }

                // Update loader description text
                updateCalendarCreatedView(calendarsToCreateCount, calendarsCreatedCount, calendarToImport.destinationEmail)
            }
        }

        binding.fragmentImportAssistantLoaderDescription.text = getString(R.string.import_assistant_creating_calendars_finish)
        return allCalendarCreated
    }

    private fun showImportInProgressView() {
        // Display import in progress layout
        binding.fragmentImportAssistantLoaderLayout.visibleOrGone(false)
        binding.fragmentImportAssistantInProgressLayout.visibleOrGone(true)
        binding.fragmentImportAssistantSummaryLayout.visibleOrGone(false)

        val defaultUserEmail = importAssistantViewModel.defaultUserEmail.value ?: "" // TODO Handle null ?
        val sourceEmail = importAssistantViewModel.sourceEmail.value ?: "" // TODO Handle null ?
        binding.fragmentImportAssistantInProgressDescription.text = getString(
            R.string.import_assistant_in_progress_description,
            sourceEmail,
            defaultUserEmail
        )

        // Show close button
        binding.fragmentImportAssistantImportButton.visibleOrGone(false)
        binding.fragmentImportAssistantCloseButton.visibleOrGone(true)

        // Change illustration
        binding.fragmentImportAssistantInProgressIllustration.visibleOrGone(true)
        binding.fragmentImportAssistantIllustration.visibleOrGone(false)

        binding.fragmentImportAssistantCloseButton.setOnSingleClickListener {
            onNavigationIconClicked()
        }

        binding.fragmentImportAssistantInProgressRedirect.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_import_assistant_to_nav_import_assistant_status)
        }
    }

    private fun showGatheringDataView() {
        // Set loader title
        binding.fragmentImportAssistantLoaderTitle.text = getString(R.string.import_assistant_gathering_data_loader_title)

        // Display loader layout
        binding.fragmentImportAssistantLoaderLayout.visibleOrGone(true)
        binding.fragmentImportAssistantInProgressLayout.visibleOrGone(false)
        binding.fragmentImportAssistantSummaryLayout.visibleOrGone(false)

        // Hide the buttons
        binding.fragmentImportAssistantImportButton.visibleOrGone(false)
        binding.fragmentImportAssistantCloseButton.visibleOrGone(false)
    }

    private fun showImportSummaryView(importCalendarMappingList: List<ImportCalendarMapping>) {
        // We set the list of ImportCalendarMapping to be displayed
        if (this::importCalendarMappingListAdapter.isInitialized) {
            importCalendarMappingListAdapter.submitList(importCalendarMappingList)
            importCalendarMappingListAdapter.notifyDataSetChanged()
        }

        // Display summary layout
        binding.fragmentImportAssistantLoaderLayout.visibleOrGone(false)
        binding.fragmentImportAssistantInProgressLayout.visibleOrGone(false)
        binding.fragmentImportAssistantSummaryLayout.visibleOrGone(true)

        val calendarsToImport = importCalendarMappingList.filter { it.importCalendar }
        binding.fragmentImportAssistantSummaryCount.text = resources.getQuantityString(
            R.plurals.import_assistant_summary_count,
            importCalendarMappingList.size,
            calendarsToImport.size,
            importCalendarMappingList.size
        )
        binding.fragmentImportAssistantImportButton.text = resources.getQuantityString(
            R.plurals.import_assistant_import_button,
            calendarsToImport.size,
            calendarsToImport.size
        )

        // Disable button if list is empty
        importButtonIsEnabled(importCalendarMappingList.any { it.importCalendar })

        // Display calendars to create count
        val calendarsToCreate = importCalendarMappingList.filter { it.createDestinationCalendar && it.importCalendar }.size
        binding.fragmentImportAssistantSummaryCreateDetails.visibleOrGone(calendarsToCreate > 0)
        binding.fragmentImportAssistantSummaryCreateDetails.text = resources.getQuantityString(
            R.plurals.fragment_import_assistant_summary_create_details,
            calendarsToCreate,
            calendarsToCreate
        )

        // Display calendars to merge count
        val calendarsToMerge = importCalendarMappingList.filter { it.mergeCalendar && it.importCalendar }.size
        binding.fragmentImportAssistantSummaryMergeDetails.visibleOrGone(calendarsToMerge > 0)
        binding.fragmentImportAssistantSummaryMergeDetails.text = resources.getQuantityString(
            R.plurals.fragment_import_assistant_summary_merge_details,
            calendarsToMerge,
            calendarsToMerge
        )

        // Reset import button state
        binding.fragmentImportAssistantImportButton.setIdle()

        // Show the import button
        binding.fragmentImportAssistantImportButton.visibleOrGone(true)
        binding.fragmentImportAssistantCloseButton.visibleOrGone(false)
    }

    private fun showCreatingCalendarsView(calendarsToCreateCount: Int, calendarsCreatedCount: Int, destinationEmail: String) {
        // Set loader title
        binding.fragmentImportAssistantLoaderTitle.text = getString(R.string.import_assistant_creating_calendars)

        // Hide the buttons
        binding.fragmentImportAssistantImportButton.visibleOrGone(false)
        binding.fragmentImportAssistantCloseButton.visibleOrGone(false)

        // Display loader layout
        binding.fragmentImportAssistantLoaderLayout.visibleOrGone(true)
        binding.fragmentImportAssistantInProgressLayout.visibleOrGone(false)
        binding.fragmentImportAssistantSummaryLayout.visibleOrGone(false)

        // Set initial loader description text
        updateCalendarCreatedView(calendarsToCreateCount, calendarsCreatedCount,destinationEmail)

        // Display loader description
        binding.fragmentImportAssistantLoaderDescription.visibleOrGone(true)
    }

    private fun updateCalendarCreatedView(calendarsToCreateCount: Int, calendarsCreatedCount: Int, destinationEmail: String) {
        binding.fragmentImportAssistantLoaderDescription.text = resources.getQuantityString(
            R.plurals.import_assistant_creating_calendars_count,
            calendarsCreatedCount,
            calendarsCreatedCount,
            calendarsToCreateCount,
            destinationEmail
        )
    }

    private fun showBottomSheetDialog(calendarToImport: ImportCalendarMapping, activeWritableCalendars: List<Calendar>?) {
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

        bottomSheetDialog.setContentView(R.layout.dialog_calendar_import_mapping)

        val sourceCalendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_import_mapping_source_title)
        sourceCalendarName?.text = calendarToImport.sourceName

        val newCalendarIcon = bottomSheetDialog.findViewById<ImageView>(R.id.dialog_calendar_import_mapping_new_calendar_icon)
        val newCalendarColor =
            if (calendarToImport.createDestinationCalendar) calendarToImport.destinationColor
            else resources.getStringArray(R.array.colors_values).random()
        newCalendarIcon?.imageTintList = ColorStateList.valueOf(newCalendarColor.toColorInt())
        val newCalendarName = bottomSheetDialog.findViewById<TextView>(R.id.dialog_calendar_import_mapping_new_calendar_title)
        newCalendarName?.text = calendarToImport.sourceName

        val newCalendarPress = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_import_mapping_new_calendar_press)
        newCalendarPress?.setOnSingleClickListener {
            if (calendarToImport.createDestinationCalendar) {
                // Nothing to do here
                bottomSheetDialog.dismiss()
            }
            else {
                lifecycleScope.launch {
                    importAssistantViewModel.setCreateNewCalendar(calendarToImport, newCalendarColor)
                    bottomSheetDialog.dismiss()
                }
            }
        }

        val mergeCalendarLayout = bottomSheetDialog.findViewById<View>(R.id.dialog_calendar_import_mapping_merge_layout)
        mergeCalendarLayout?.visibleOrGone(!activeWritableCalendars.isNullOrEmpty())
        if (!activeWritableCalendars.isNullOrEmpty()) {
            val mergeCalendarListView = bottomSheetDialog.findViewById<RecyclerView>(R.id.dialog_calendar_import_mapping_merge_list)
            val mergeCalendarLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            mergeCalendarListView?.layoutManager = mergeCalendarLayoutManager
            val mergeCalendarListAdapter = MergeCalendarListAdapter {
                // On calendar click
                importAssistantViewModel.setMergeExistingCalendar(calendarToImport, it)
                bottomSheetDialog.dismiss()
            }
            mergeCalendarListView?.adapter = mergeCalendarListAdapter
            mergeCalendarListAdapter.submitList(activeWritableCalendars)
        }

        bottomSheetDialog.show()
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }
}
