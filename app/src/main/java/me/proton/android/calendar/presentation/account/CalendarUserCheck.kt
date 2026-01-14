package me.proton.android.calendar.presentation.account

import android.content.Context
import me.proton.android.calendar.R
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.auth.domain.usecase.UserCheckAction
import me.proton.core.auth.presentation.DefaultUserCheck
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.Delinquent
import me.proton.core.user.domain.entity.User

/**
 * Check [User] succeed if:
 * - [User.delinquent] is not [Delinquent.InvoiceDelinquent] or [Delinquent.InvoiceMailDisabled].
 * - [User.hasSubscription] is true or all existing [Account] in [AccountState.Ready] have a subscription.
 * - [User.usedSpace] is lower than [User.maxSpace].
 */
class CalendarUserCheck(
    private val context: Context,
    accountManager: AccountManager,
    userManager: UserManager
) : DefaultUserCheck(context, accountManager, userManager) {

    private fun errorStoreQuota() = PostLoginAccountSetup.UserCheckResult.Error(
        localizedMessage = context.getString(R.string.bootstrap_error_store_quota_reached_message),
        action = UserCheckAction.OpenUrl(
            name = context.getString(R.string.bootstrap_error_store_quota_reached_learn_more),
            url = "https://proton.me/support/increase-storage-space"
        )
    )

    override suspend fun invoke(user: User): PostLoginAccountSetup.UserCheckResult = when {
        // user.usedSpace >= user.maxSpace -> errorStoreQuota()
        else -> super.invoke(user)
    }
}
