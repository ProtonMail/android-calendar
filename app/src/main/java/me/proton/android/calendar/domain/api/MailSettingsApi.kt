package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.MailSettingsApiResponse
import me.proton.core.domain.entity.UserId

interface MailSettingsApi {
    suspend fun getMailSettings(userId: UserId): ApiResponse<MailSettingsApiResponse>
}

