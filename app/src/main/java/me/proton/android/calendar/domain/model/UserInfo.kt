package me.proton.android.calendar.domain.model

import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.UserAddress

data class UserInfo(
    val userId: UserId,
    val addresses: List<UserAddress>,
    val hasSubscriptionForMail: Boolean
) {
    val emails: List<String>
        get() = addresses.map { it.email }
}