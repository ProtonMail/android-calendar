package me.proton.android.calendar.presentation.bugReport

import android.content.DialogInterface
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Operation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.R
import me.proton.android.calendar.common.utils.AndroidUtils.clearFocusAndHideKeyboard
import me.proton.android.calendar.common.utils.AndroidUtils.displaySnackBar
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener
import me.proton.android.calendar.common.utils.AndroidUtils.visibleOrGone
import me.proton.android.calendar.databinding.FragmentBugReportBinding
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import org.koin.core.KoinComponent

class BugReportFragment : BaseDialogFragment<FragmentBugReportBinding>(), KoinComponent {

    override val TAG: String
        get() = "BugReportFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_bug_report

    override val navigateUp = false

    private lateinit var buttonSend: View
    private lateinit var loadingAction: View

    private val calendarViewModel: CalendarViewModel by activityViewModels()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) = FragmentBugReportBinding.inflate(inflater, container, false)

    override fun onBackPressedCustom() {
        if (binding.bugReportTitle.text.isNotEmpty() || binding.bugReportDescription.text.isNotEmpty()) {
            displayDiscardChangesConfirmationDialog { _, _ ->
                // Reinitialise event view model data when user chooses to discard modifications
                lifecycleScope.launch {
                    findNavController().navigateUp()
                }
            }
        } else findNavController().navigateUp()
    }

    private fun displayDiscardChangesConfirmationDialog(callback: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bug_report_discard_changes_title)
            .setMessage(R.string.bug_report_discard_changes_description)
            .setPositiveButton(R.string.bug_report_discard_changes_confirm, callback)
            .setNegativeButton(R.string.bug_report_discard_changes_cancel) { _, _ -> }
            .show()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        buttonSend = layoutInflater.inflate(R.layout.toolbar_action_button, dialogToolbarContent, false)
        with (buttonSend) {
            (findViewById<ImageButton>(R.id.imageButton)).setImageDrawable(ContextCompat.getDrawable(this.context, R.drawable.ic_proton_paper_plane))
            setOnSingleClickListener {
                requireActivity().clearFocusAndHideKeyboard(view)

                if (binding.bugReportDescription.text.isEmpty()) {
                    view?.displaySnackBar(requireContext().getString(R.string.snack_send_bug_report_description_empty))
                    return@setOnSingleClickListener
                }

                displayLoading(true)
                lifecycleScope.launch {
                    val user = withContext(Dispatchers.Default) {
                        calendarViewModel.selectUser()
                    }
                    val osName = "Android"
                    val osVersion = "" + Build.VERSION.SDK_INT
                    val client = "AndroidCalendar"
                    val appVersionName = getString(
                        R.string.nav_view_version_info,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    )
                    val title: String = binding.bugReportTitle.text.toString()
                    val description: String = binding.bugReportDescription.text.toString()
                    val username: String = user?.name ?: ""
                    val email: String = user?.email ?: ""

                    calendarViewModel.sendBugReport(
                        osName,
                        osVersion,
                        client,
                        appVersionName,
                        title,
                        description,
                        username,
                        email
                    ).observe(viewLifecycleOwner) {
                        displayLoading(false)
                        if (it is Operation.State.SUCCESS) {
                            requireActivity().displaySnackBar(requireContext().getString(R.string.snack_send_bug_report_success))
                            findNavController().navigateUp()
                        } else if (it is Operation.State.FAILURE) {
                            view?.displaySnackBar(requireContext().getString(R.string.snack_send_bug_report_error))
                        }
                    }
                }
            }
        }

        loadingAction = layoutInflater.inflate(R.layout.toolbar_action_loader, dialogToolbarContent, false)
        loadingAction.visibleOrGone(false)

        with(toolbar.findViewById<ViewGroup>(R.id.dialog_toolbar_content)) {
            val layoutParams = LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.action_clickable_size),
                resources.getDimensionPixelSize(R.dimen.action_clickable_size))
            layoutParams.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            addView(
                buttonSend, layoutParams
            )
            addView(
                loadingAction, layoutParams
            )
        }

        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text = resources.getString(R.string.nav_view_more_bug)
    }

    private fun displayLoading(display: Boolean) {
        // Update action bar buttons visibility
        loadingAction.visibleOrGone(display)
        buttonSend.visibleOrGone(!display)
    }
}
