package me.proton.android.calendar.presentation.importAssistant.fragment

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarImport
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.data.api.ImporterEntity
import me.proton.android.calendar.data.api.ReportEntity
import me.proton.android.calendar.databinding.FragmentImportAssistantGuideBinding
import me.proton.android.calendar.databinding.FragmentImportAssistantStatusBinding
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Import
import me.proton.android.calendar.presentation.account.AccountViewModel
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.importAssistant.adapter.ImportStatusListAdapter
import me.proton.android.calendar.presentation.importAssistant.viewModel.ImportAssistantViewModel
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import org.koin.core.KoinComponent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class ImportAssistantStatusFragment : BaseDialogFragment<FragmentImportAssistantStatusBinding>(), KoinComponent {

    override val TAG: String
        get() = "ImportAssistantStatusFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_import_assistant_status

    override val navigateUp = true
    override val isScrollable = false

    @Inject
    lateinit var logger: Logger

    private val calendarViewModel: CalendarViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val importAssistantViewModel: ImportAssistantViewModel by activityViewModels()

    private lateinit var importStatusListAdapter: ImportStatusListAdapter

    private var importerList: List<ImporterEntity>? = null
    private var reportList: List<ReportEntity>? = null
    private var zoneId: ZoneId? = null

    private var resumeImportId: String? = null
    private var resumeImportAccount: String? = null
    private var googleSignInClient: GoogleSignInClient? = null

    override fun onBackPressedCustom() {
        if (findNavController().previousBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
            findNavController().navigateUp()
        } else {
            if (!findNavController().popBackStack(R.id.nav_calendar, false)) {
                // TODO this is a workaround for navigating back to month view after opening EventForm from EventDetails
                //  that was opened from system notification
                //  R.id.nav_calendar is not in the hierarchy so popping backstack will fail and we need
                //  to navigate manually
                findNavController().navigate(Navigation.Deeplink.toMonth())
            }
        }
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = getString(R.string.import_assistant_previous_import)
        if (findNavController().previousBackStackEntry?.destination?.id == R.id.nav_import_assistant_guide) {
            toolbar.setNavigationIcon(R.drawable.ic_proton_arrow_left)
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_proton_cross)
        }
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentImportAssistantStatusBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importAssistantViewModel.resetViewModel()

        initImportList()

        calendarViewModel.userCalendars.observe(viewLifecycleOwner) {
        }

        binding.fragmentImportAssistantStatusRefresh.isRefreshing = true
        refreshList()

        val importListMediator = MediatorLiveData<Triple<List<ImporterEntity>, List<ReportEntity>, ZoneId>>()
        importListMediator.addSource(importAssistantViewModel.importerList) { importerList ->
            this.importerList = importerList
            if (importerList != null && reportList != null && zoneId != null) {
                importListMediator.value = Triple(importerList, reportList!!, zoneId!!)
            }
        }
        importListMediator.addSource(importAssistantViewModel.reportList) { reportList ->
            this.reportList = reportList
            if (importerList != null && reportList != null && zoneId != null) {
                importListMediator.value = Triple(importerList!!, reportList, zoneId!!)
            }
        }
        importListMediator.addSource(calendarViewModel.timeZoneId) { zoneId ->
            this.zoneId = zoneId
            if (importerList != null && reportList != null && zoneId != null) {
                importListMediator.value = Triple(importerList!!, reportList!!, zoneId)
            }
        }
        importListMediator.observe(viewLifecycleOwner) {
            it ?: return@observe

            binding.fragmentImportAssistantStatusRefresh.isRefreshing = false

            val importerList = it.first
            val reportList = it.second
            val zoneId = it.third
            val importList = arrayListOf<Import>()
            importerList.filter { importerEntity ->
                importerEntity.product.contains(CalendarImport.PRODUCT_CALENDAR) && importerEntity.active?.calendar != null
            }.forEach { importerEntity ->
                // Importers
                importList.add(
                    Import(
                        importerEntity.id,
                        importerEntity.account,
                        null,
                        importerEntity.active?.calendar?.createTime?.let { createTime ->
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(createTime.toLong()), zoneId)
                        },
                        importerEntity.active?.calendar?.state?.let { state ->
                            // Canceling is a special case because it has the same state value as canceled but only exists for active importers
                            if (state == Import.ImportState.CANCELED.value) Import.ImportState.CANCELING
                            else Import.ImportState.values()[state]
                        },
                        importerEntity.active?.calendar?.errorCode
                    )
                )
            }
            reportList.filter { reporterEntity ->
                reporterEntity.summary.calendar != null
            }.forEach { reporterEntity ->
                // Reports
                importList.add(
                    Import(
                        reporterEntity.id,
                        reporterEntity.account,
                        reporterEntity.summary.calendar?.totalSize,
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(reporterEntity.endTime.toLong()), zoneId),
                        reporterEntity.summary.calendar?.state?.let { state ->
                            Import.ImportState.values()[state]
                        }
                    )
                )
            }
            importStatusListAdapter.submitList(
                importList.sortedByDescending { it.dateTime }
            )
        }
    }

    private fun initImportList() {
        val importListView = binding.fragmentImportAssistantStatusList
        val importLayoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
        importListView.layoutManager = importLayoutManager
        val timeFormatIs24Hour = calendarViewModel.timeFormat.value?.let { calendarViewModel.timeFormatIs24Hour(it, requireContext()) } ?: true
        importStatusListAdapter = ImportStatusListAdapter(timeFormatIs24Hour) { import, action ->
            when (action) {
                ImportStatusListAdapter.Action.CANCEL -> {
                    showConfirmationDialog(
                        R.string.import_assistant_cancel_import_title,
                        R.string.import_assistant_cancel_import_message,
                        R.string.import_assistant_cancel_import_positive_button,
                        R.string.import_assistant_cancel_import_negative_button
                    ) { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch {
                            if (!mainViewModel.isConnectedToNetwork) {
                                displayNetworkError()
                                return@launch
                            }
                            if (importAssistantViewModel.cancelImport(import.id)) refreshList()
                            else view?.displaySnackBar(getString(R.string.import_assistant_cancel_import_error))
                        }
                    }
                }
                ImportStatusListAdapter.Action.RESUME -> {
                    lifecycleScope.launch {
                        if (!mainViewModel.isConnectedToNetwork) {
                            displayNetworkError()
                            return@launch
                        }
                        // No confirmation dialog for resume
                        if (import.errorCode == Import.ErrorCode.LOST_CONNECTION.value) {
                            // Lost connection, we need to sign in to Google and create a new token to update importer
                            // Get Google Sign In Options with Calendar scope
                            importAssistantViewModel.getGoogleSignInOptions()?.let { googleSignInOptions ->
                                // Get Google Sign In Client and store the value so we can disconnect user
                                googleSignInClient = GoogleSignIn.getClient(activity as MainActivity, googleSignInOptions)
                                googleSignInClient?.let { googleSignInClient ->
                                    // Start Google Sign In
                                    resumeImportId = import.id
                                    resumeImportAccount = import.account
                                    resumeImportResultLauncher.launch(googleSignInClient.signInIntent)
                                } ?: run {
                                    view?.displaySnackBar(getString(R.string.import_assistant_resume_import_error))
                                }
                            } ?: run {
                                view?.displaySnackBar(getString(R.string.import_assistant_resume_import_error))
                            }
                        } else {
                            when (val resumeImportResult = importAssistantViewModel.resumeImport(import.id)) {
                                is ImportAssistantViewModel.ImportResult.Error -> {
                                    view?.displaySnackBar(
                                        resumeImportResult.userErrorMessage ?: getString(R.string.import_assistant_resume_import_error)
                                    )
                                }
                                is ImportAssistantViewModel.ImportResult.Success -> {
                                    refreshList()
                                }
                            }
                        }
                    }
                }
                ImportStatusListAdapter.Action.DELETE -> {
                    showConfirmationDialog(
                        R.string.import_assistant_delete_report_title,
                        R.string.import_assistant_delete_report_message,
                        R.string.import_assistant_delete_report_positive_button,
                        R.string.import_assistant_delete_report_negative_button
                    ) { dialog, _ ->
                        dialog.dismiss()
                        lifecycleScope.launch {
                            if (!mainViewModel.isConnectedToNetwork) {
                                displayNetworkError()
                                return@launch
                            }
                            // Import.id is the reportId since we mapped both reports and active importers to the Import object
                            if (!importAssistantViewModel.deleteReport(import.id)) {
                                view?.displaySnackBar(getString(R.string.import_assistant_delete_import_error))
                            } else {
                                view?.displaySnackBar(getString(R.string.import_assistant_delete_import_success))
                            }
                        }
                    }
                }
            }
        }
        importListView.adapter = importStatusListAdapter

        binding.fragmentImportAssistantStatusRefresh.setOnRefreshListener {
            refreshList()
        }
    }

    private var resumeImportResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data

            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)

                // Signed in successfully, show authenticated UI.
                val authCode = account.serverAuthCode
                val accountEmail = account.email
                googleSignInClient?.signOut()
                if (authCode != null && accountEmail != null) {
                    // Resume import
                    resumeImport(authCode, accountEmail)
                } else {
                    view?.displaySnackBar(getString(R.string.import_assistant_resume_import_error))
                }
            } catch (e: ApiException) {
                // The ApiException status code indicates the detailed failure reason.
                // Please refer to the GoogleSignInStatusCodes class reference for more information.
                logger.i("Resume Import signInResult:failed code= ${e.statusCode}") // TODO This might flood Sentry ?
                view?.displaySnackBar(getString(R.string.import_assistant_resume_import_error))
                googleSignInClient?.signOut()
            }
        }
    }

    private fun resumeImport(authCode: String, accountEmail: String) {
        if (accountEmail != this.resumeImportAccount) {
            view?.displaySnackBar(getString(R.string.import_assistant_update_import_wrong_account_error))
            return
        }

        lifecycleScope.launch {
            val userId = accountViewModel.getPrimaryUserId()
            val importerId = this@ImportAssistantStatusFragment.resumeImportId
            if (userId == null || importerId == null) {
                view?.displaySnackBar(getString(R.string.import_assistant_update_import_error))
                return@launch
            }

            // Update Importer to resume
            if (!importAssistantViewModel.handleGoogleSignInRedirect(
                    userId,
                    authCode,
                    resources.getStringArray(R.array.colors_values),
                    importerId
                )
            ) {
                view?.displaySnackBar(getString(R.string.import_assistant_update_import_error))
            }
        }
    }

    private fun refreshList() {
        lifecycleScope.launch {
            if (!mainViewModel.isConnectedToNetwork) {
                displayNetworkError()
                binding.fragmentImportAssistantStatusRefresh.isRefreshing = false
                return@launch
            }
            importAssistantViewModel.getReports()
            importAssistantViewModel.getImporters()
            // TODO Handle error and cancel loading animation
        }
    }

    private fun displayNetworkError() {
        view?.displaySnackBar(getString(R.string.snack_network_error))
    }

    private fun showConfirmationDialog(
        title: Int,
        message: Int,
        positiveButtonText: Int,
        negativeButtonText: Int,
        listener: DialogInterface.OnClickListener
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setCancelable(true)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText, listener)
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                dialog.dismiss()
            }

        materialDialogBuilder.show()
    }
}
