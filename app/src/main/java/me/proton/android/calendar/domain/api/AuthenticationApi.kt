package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*
import me.proton.core.network.domain.session.SessionId

interface AuthenticationApi {
    suspend fun refreshAccessToken(sessionId: SessionId, refreshToken: String): ApiResponse<RefreshAccessTokenApiResponse>
    suspend fun fetchLoginInfo(username: String): ApiResponse<LoginInfoApiResponse>
    suspend fun login(
        username: String,
        srpSession: String,
        clientEphemeral: String,
        clientProof: String
    ): ApiResponse<LoginApiResponse>
}
