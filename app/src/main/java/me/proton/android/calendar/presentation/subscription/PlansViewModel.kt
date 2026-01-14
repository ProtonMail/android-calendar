package me.proton.android.calendar.presentation.subscription

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.payment.domain.PaymentManager
import me.proton.core.payment.presentation.PaymentsOrchestrator
import me.proton.core.plan.presentation.PlansOrchestrator
import me.proton.core.plan.presentation.onUpgradeResult
import me.proton.core.presentation.utils.showToast
import javax.inject.Inject

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val plansOrchestrator: PlansOrchestrator,
    private val paymentsOrchestrator: PaymentsOrchestrator,
    private val paymentManager: PaymentManager
) : ViewModel() {

    fun register(context: ComponentActivity) {
        plansOrchestrator.register(context)
        paymentsOrchestrator.register(context)
    }

    private fun getPrimaryUserId() = accountManager.getPrimaryUserId()

    fun isSubscriptionFlowAvailable(): Flow<Boolean> = getPrimaryUserId().map {
        it != null && paymentManager.isSubscriptionAvailable(it)
    }

    fun onPlansUpgradeClicked(context: ComponentActivity) {
        viewModelScope.launch {
            getPrimaryUserId().first()?.let {
                val account = accountManager.getAccount(it).first() ?: return@launch
                with(plansOrchestrator) {
                    onUpgradeResult { upgradeResult ->
                        // do something with the upgrade result
                        if (upgradeResult != null) {
                            context.showToast(
                                "Upgrade result: ${upgradeResult.billingResult.token} " +
                                        "for plan id: ${upgradeResult.planId}"
                            )
                        }
                    }

                    plansOrchestrator.startUpgradeWorkflow(account.userId)
                }
            }
        }
    }

    fun onCurrentPlanClicked(context: ComponentActivity) {
        viewModelScope.launch {
            getPrimaryUserId().first()?.let {
                val account = accountManager.getAccount(it).first() ?: return@launch
                with(plansOrchestrator) {
                    onUpgradeResult { upgradeResult ->
                        // do something with the upgrade result
                        if (upgradeResult != null) {
                            context.showToast(
                                "Upgrade result: ${upgradeResult.billingResult.token} " +
                                        "for planId: ${upgradeResult.planId}"
                            )
                        }
                    }

                    plansOrchestrator.showCurrentPlanWorkflow(account.userId)
                }
            }
        }
    }
}
