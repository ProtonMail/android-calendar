package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CanonicalEmailsApiResponse
import me.proton.core.domain.entity.UserId

interface AddressesApi {
    suspend fun getCanonicalEmails(userId: UserId, emails: List<String>): ApiResponse<CanonicalEmailsApiResponse>
}
