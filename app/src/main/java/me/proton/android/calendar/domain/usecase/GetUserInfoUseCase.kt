package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import me.proton.android.calendar.domain.model.UserInfo
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.extension.hasSubscriptionForMail
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class GetUserInfoUseCase @Inject constructor(
    private val userManager: UserManager,
    private val userAddressManager: UserAddressManager,
    private val accountManager: AccountManager,
) : UseCase {

    operator fun invoke(): Flow<UserInfo> =
        accountManager.getPrimaryUserId().filterNotNull().distinctUntilChanged().flatMapConcat { userId ->
            combine(
                userManager.observeUser(SessionUserId(userId.id)).filterNotNull(),
                userAddressManager.observeAddresses(SessionUserId(userId.id))
            ) { user, addresses ->
                UserInfo(
                    userId = userId,
                    addresses = addresses,
                    hasSubscriptionForMail = user.hasSubscriptionForMail()
                )
            }
        }

}
