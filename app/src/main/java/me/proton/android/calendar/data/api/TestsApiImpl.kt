package me.proton.android.calendar.data.api

import me.proton.android.calendar.common.PING_TIMEOUT_SECONDS
import me.proton.android.calendar.domain.api.TestsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.network.domain.TimeoutOverride
import retrofit2.http.GET
import retrofit2.http.Tag
import javax.inject.Inject

interface TestsApiService : BaseRetrofitApi {

    @GET("tests/ping")
    suspend fun pingServer(@Tag timeoutOverride: TimeoutOverride): StatusCodeApiResponse
}

class TestsApiImpl @Inject constructor(private val apiProvider: ApiProvider) : TestsApi {

    override suspend fun pingServer(userId: UserId): ApiResponse<StatusCodeApiResponse> =
        apiProvider.get<TestsApiService>(userId).invoke {
            pingServer(
                TimeoutOverride(PING_TIMEOUT_SECONDS, PING_TIMEOUT_SECONDS, PING_TIMEOUT_SECONDS)
            )
        }.toApiResponse()
}
