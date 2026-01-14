package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.StatusCodeApiResponse
import me.proton.core.domain.entity.UserId

interface TestsApi {

    // TODO Temporary solution for server down banner
    suspend fun pingServer(userId: UserId): ApiResponse<StatusCodeApiResponse>
}