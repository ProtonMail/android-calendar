package me.proton.android.calendar.presentation.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.databinding.FragmentAccountSettingsBinding
import me.proton.android.calendar.presentation.main.fragment.BaseDialogFragment
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.compose.AccountSettingsList
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.usersettings.presentation.UserSettingsOrchestrator
import org.koin.core.KoinComponent
import javax.inject.Inject

@AndroidEntryPoint
class AccountSettingsFragment :
    BaseDialogFragment<FragmentAccountSettingsBinding>(),
    KoinComponent {

    @Inject
    lateinit var accountManager: AccountManager

    @Inject
    lateinit var userSettingsOrchestrator: UserSettingsOrchestrator

    override val TAG: String
        get() = "AccountSettingsFragment"
    override val layoutResourceId: Int
        get() = R.layout.fragment_account_settings

    override val navigateUp = true
    override val isScrollable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userSettingsOrchestrator.register(this)
    }

    override fun onDestroy() {
        userSettingsOrchestrator.unregister()
        super.onDestroy()
    }

    override fun onBackPressedCustom() {
        findNavController().navigateUp()
    }

    override fun onNavigationIconClicked(): Boolean {
        onBackPressedCustom()
        return true
    }

    override fun onToolbarCreated(toolbar: Toolbar) {
        toolbar.findViewById<TextView>(R.id.dialog_toolbar_title).text =
            resources.getString(R.string.account_settings_header)
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAccountSettingsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.settingsAccountSettingsList.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ProtonTheme {
                    AccountSettingsList(
                        onPasswordManagementClick = { startPasswordManagement() },
                        onRecoveryEmailClick = { startUpdateRecoveryEmail() },
                        onSecurityKeysClick = { startSecurityKeys() },
                        divider = {}
                    )
                }
            }
        }
    }

    private fun startPasswordManagement() {
        viewLifecycleOwner.lifecycleScope.launch {
            accountManager.getPrimaryUserId().firstOrNull()?.let {
                userSettingsOrchestrator.startPasswordManagementWorkflow(it)
            }
        }
    }

    private fun startUpdateRecoveryEmail() {
        viewLifecycleOwner.lifecycleScope.launch {
            accountManager.getPrimaryUserId().firstOrNull()?.let {
                userSettingsOrchestrator.startUpdateRecoveryEmailWorkflow(it)
            }
        }
    }

    private fun startSecurityKeys() {
        viewLifecycleOwner.lifecycleScope.launch {
            accountManager.getPrimaryUserId().firstOrNull()?.let {
                userSettingsOrchestrator.startSecurityKeysWorkflow(it)
            }
        }
    }
}
