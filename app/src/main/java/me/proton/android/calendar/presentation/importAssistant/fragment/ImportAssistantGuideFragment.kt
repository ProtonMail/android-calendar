package me.proton.android.calendar.presentation.importAssistant.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarImport.PRODUCT_CALENDAR
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.data.api.ReportEntity
import me.proton.android.calendar.databinding.FragmentImportAssistantBinding
import me.proton.android.calendar.databinding.FragmentImportAssistantGuideBinding
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class ImportAssistantGuideFragment : BaseDialogFragment<FragmentImportAssistantGuideBinding>(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantGuideFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant_guide

    override val navigateUp = true
    override val isScrollable = false

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by activityViewModels()

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = "" // No Title
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentImportAssistantGuideBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importAssistantViewModel.resetViewModel()

        lifecycleScope.launch {
            importAssistantViewModel.getImporters()
            importAssistantViewModel.getReports()
        }

        // Hidden by default
        binding.importAssistantStatusGuideImportsSubtitle.visibleOrGone(false)

        importAssistantViewModel.importerList.observe(viewLifecycleOwner) { importerList ->
            importerList ?: return@observe

            val reportList = importAssistantViewModel.reportList.value
            refreshOngoingImportText(importerList, reportList)
        }

        importAssistantViewModel.reportList.observe(viewLifecycleOwner) { reportList ->
            reportList ?: return@observe

            val importerList = importAssistantViewModel.importerList.value
            refreshOngoingImportText(importerList, reportList)
        }

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) { userCalendars ->
        }

        observeImportGuideSnackState(coroutineContext)

        binding.importAssistantStatusGuideNewImportButton.setOnSingleClickListener {
            (requireActivity() as MainActivity).showImportGoogleAuthDialog()
        }

        binding.importAssistantStatusGuideImportsPress.setOnSingleClickListener {
            findNavController().navigate(R.id.action_nav_import_assistant_guide_to_nav_import_assistant_status)
        }
    }

    override fun onResume() {
        super.onResume()

        val importerList = importAssistantViewModel.importerList.value
        val reportList = importAssistantViewModel.reportList.value
        refreshOngoingImportText(importerList, reportList)
    }

    private fun refreshOngoingImportText(importerList: List<ImporterEntity>?, reportList: List<ReportEntity>?) {
        val ongoingImports = importerList?.count { it.product.contains(PRODUCT_CALENDAR) && it.active?.calendar != null }
        binding.importAssistantStatusGuideImportsLayout.visibleOrGone((ongoingImports != null && ongoingImports > 0) || reportList.isNullOrEmpty().not())
        binding.importAssistantStatusGuideImportsSubtitle.visibleOrGone(ongoingImports != null && ongoingImports > 0)
        if (ongoingImports != null && ongoingImports > 0) {
            binding.importAssistantStatusGuideImportsSubtitle.text = resources.getQuantityString(
                R.plurals.import_assistant_ongoing_import,
                ongoingImports,
                ongoingImports
            )
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }

    private fun observeImportGuideSnackState(coroutineContext: CoroutineContext) {
        importAssistantViewModel.importGuideSnackState.asLiveData(coroutineContext).observe(viewLifecycleOwner) { importGuideSnackState ->
            importGuideSnackState?.let {
                when (it) {
                    is ImportAssistantViewModel.ImportSnackState.DisplaySnack -> {
                        view?.displaySnackBar(it.message)
                    }
                }
                importAssistantViewModel.importGuideSnackState.value = null
            }
        }
    }
}

