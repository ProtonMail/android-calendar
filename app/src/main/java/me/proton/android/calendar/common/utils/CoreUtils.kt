package me.proton.android.calendar.common.utils

import me.proton.android.calendar.common.ApiResponseCode
import me.proton.android.calendar.common.HttpResponseCode
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.UserAddress
import okhttp3.internal.toHexString

fun ApiResponse.Error.isTimeout(): Boolean {
    // TODO hardcoded string because there is no dedicated code for timeout
    return this.httpCode == 0 && this.errorCode == 0 && this.error == "timeout"
}

/**
 * We shouldn't treat 404 as "resource not found" anymore, instead use 422 + 2501, but this code makes sure we still
 * maintain the old behavior until we do FU to new way.
 */
fun ApiResponse.Error.isNotFound(): Boolean {
    return this.httpCode == HttpResponseCode.NOT_FOUND ||
            (this.httpCode == HttpResponseCode.UNPROCESSABLE_ENTITY && this.errorCode == ApiResponseCode.DOES_NOT_EXIST)
}

suspend fun UserAddressManager.getAddressesOrNull(userId: UserId, refresh: Boolean = false): List<UserAddress>? {
    return kotlin.runCatching { getAddresses(userId, refresh) }.getOrElse {
        TimberLogger.i("UserManager.getAddressesOrNull error", it)
        null
    }
}

suspend fun UserAddressManager.getAddressOrNull(userId: UserId, addressId: String, refresh: Boolean = false): UserAddress? {
    return kotlin.runCatching { getAddress(userId, AddressId(addressId), refresh) }.getOrElse {
        TimberLogger.i("UserManager.getAddressOrNull error", it)
        null
    }
}

/** Create an hex color in the '#FFFFFF' format. */
fun Int.toHexColor(): String {
    return "#" + this.toHexString()
        .let { if (it.count() > 6) it.drop(it.count()-6) else it } // Remove alpha values for backend
}
