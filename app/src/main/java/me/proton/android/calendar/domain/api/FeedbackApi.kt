package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarUserSettingsApiResponse
import me.proton.android.calendar.data.api.FeedbackApiRequest
import me.proton.android.calendar.data.api.StatusCodeApiResponse
import me.proton.android.calendar.data.api.UserSettingsApiResponse
import me.proton.core.domain.entity.UserId
import retrofit2.http.Body

interface FeedbackApi {
    suspend fun sendFeedback(userId: UserId, score: Int, feedback: String): ApiResponse<StatusCodeApiResponse>
}
