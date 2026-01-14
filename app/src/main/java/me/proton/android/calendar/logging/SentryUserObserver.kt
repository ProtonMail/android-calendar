package me.proton.android.calendar.logging

import io.sentry.Sentry
import io.sentry.protocol.User
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import me.proton.android.calendar.common.logger.SentryUtils
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.util.kotlin.CoroutineScopeProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentryUserObserver @Inject constructor(
    private val scopeProvider: CoroutineScopeProvider,
    private val defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider,
    private val accountManager: AccountManager
) {

    fun start() = accountManager.getPrimaryUserId()
        .map { userId ->
            val user = User().apply {
                id = userId?.id
                    ?: SentryUtils.getInstallationId(defaultSharedPreferencesProvider.sharedPreferences)
            }

            Sentry.setUser(user)
        }
        .launchIn(scopeProvider.GlobalDefaultSupervisedScope)
}
