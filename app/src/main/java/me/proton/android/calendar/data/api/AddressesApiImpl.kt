package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.AddressesApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET
import retrofit2.http.Query
import javax.inject.Inject

interface AddressesApiService : BaseRetrofitApi {

    @GET("addresses/canonical")
    suspend fun getCanonicalEmails(@Query("Emails[]") emails: List<String>): CanonicalEmailsApiResponse
}

class AddressesApiImpl @Inject constructor(private val apiProvider: ApiProvider) : AddressesApi {

    override suspend fun getCanonicalEmails(userId: UserId, emails: List<String>): ApiResponse<CanonicalEmailsApiResponse> =
        apiProvider.get<AddressesApiService>(userId).invoke {
            getCanonicalEmails(emails)
        }.toApiResponse()
}

@Serializable
data class CanonicalEmailsApiResponse(
    @SerialName("Responses")
    val canonicalEmailsResponses: List<CanonicalEmailsResponses>
)

@Serializable
data class CanonicalEmailsResponses(
    @SerialName("Email")
    val email: String,
    @SerialName("Response")
    val canonicalEmailResponse: CanonicalEmailResponse
)

@Serializable
data class CanonicalEmailResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("CanonicalEmail")
    val canonicalEmail: String? = null,
): BaseApiResponse()
