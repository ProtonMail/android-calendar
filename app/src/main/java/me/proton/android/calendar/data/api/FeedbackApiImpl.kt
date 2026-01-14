package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.FeedbackApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject

interface FeedbackApiService : BaseRetrofitApi {

    @POST("core/v4/feedback")
    suspend fun sendFeedback(@Body body: FeedbackApiRequest): StatusCodeApiResponse

}

class FeedbackApiImpl @Inject constructor(private val apiProvider: ApiProvider) : FeedbackApi {

    override suspend fun sendFeedback(
        userId: UserId,
        score: Int,
        feedback: String
    ): ApiResponse<StatusCodeApiResponse> =
        apiProvider.get<FeedbackApiService>(userId).invoke {
            sendFeedback(FeedbackApiRequest("calendar_android_launch", score, feedback))
        }.toApiResponse()

}

@Serializable
data class FeedbackApiRequest(
    @SerialName("FeedbackType")
    val feedbackType: String,
    @SerialName("Score")
    val score: Int,
    @SerialName("Feedback")
    val feedback: String
)
