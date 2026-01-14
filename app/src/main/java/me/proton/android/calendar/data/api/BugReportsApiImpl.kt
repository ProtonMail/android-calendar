package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.BugReportsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject

interface BugReportsApiService : BaseRetrofitApi {
    @POST("reports/bug")
    suspend fun sendReport(@Body body: BugReportsApiRequest): BugReportsApiResponse
}

class BugReportsApiImpl @Inject constructor(
    private val apiProvider: ApiProvider
) : BugReportsApi {

    override suspend fun sendBugReport(userId: UserId, body: BugReportsApiRequest): ApiResponse<BugReportsApiResponse> =
        apiProvider.get<BugReportsApiService>(userId).invoke {
            sendReport(body)
        }.toApiResponse()

}

@Serializable
data class BugReportsApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()

@Serializable
data class BugReportsApiRequest(
    @SerialName("OS")
    val osName: String,
    @SerialName("OSVersion")
    val osVersion: String,
    @SerialName("Client")
    val client: String,
    @SerialName("ClientVersion")
    val appVersionName: String,
    @SerialName("Title")
    val title: String,
    @SerialName("Description")
    val description: String,
    @SerialName("Username")
    val username: String,
    @SerialName("Email")
    val email: String
)
