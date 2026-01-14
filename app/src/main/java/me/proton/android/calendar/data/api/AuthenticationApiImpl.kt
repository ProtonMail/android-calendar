package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.AuthenticationApi
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.network.domain.session.SessionId
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthenticationApiService: BaseRetrofitApi {

    @POST("auth")
    suspend fun login(@Body requestBody: RequestBody): LoginApiResponse

    @POST("auth/info")
    suspend fun fetchLoginInfo(@Body requestBody: RequestBody): LoginInfoApiResponse

    @POST("auth/refresh")
    suspend fun refreshAccessToken(@Body body: RefreshAccessTokenApiBody): RefreshAccessTokenApiResponse

}

class AuthenticationApiImpl(private val apiProvider: ApiProvider) : AuthenticationApi {

    @Deprecated("needs fixing")
    override suspend fun refreshAccessToken(sessionId: SessionId, refreshToken: String): ApiResponse<RefreshAccessTokenApiResponse> =
        apiProvider.get<AuthenticationApiService>(sessionId).invoke {
            refreshAccessToken(RefreshAccessTokenApiBody(refreshToken)) // TODO FIX THIS
        }.toApiResponse()

    override suspend fun fetchLoginInfo(username: String): ApiResponse<LoginInfoApiResponse> =
        apiProvider.get<AuthenticationApiService>().invoke {
            fetchLoginInfo(LoginInfoApiBody(username).toRetrofitRequestBody())
        }.toApiResponse()

    override suspend fun login(username: String, srpSession: String, clientEphemeral: String, clientProof: String): ApiResponse<LoginApiResponse> =
        apiProvider.get<AuthenticationApiService>().invoke {
            login(LoginApiBody(username, srpSession, clientEphemeral, clientProof).toRetrofitRequestBody())
        }.toApiResponse()
}

data class RefreshAccessTokenApiBody(
    val refreshToken: String
) {
    fun toJson(): String {
        return """{
            "RefreshToken": "$refreshToken",
            "ResponseType": "token",
            "GrantType": "refresh_token",
            "RedirectUri": "http://protonmail.ch"
        }""".trimIndent()
    }
}

data class RefreshAccessTokenApiResponse(
    val accessToken: String
//    "ExpiresIn": 360000,
//    "TokenType": "Bearer",
//"Scope": "full other_scopes",
//"RefreshToken": "b894b4c4f20003f12d486900d8b88c7d68e67235"
)

data class LoginInfoApiBody(
    val username: String
) {
    fun toRetrofitRequestBody(): RequestBody {
        return """{"Username": "$username"}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }
}

@Serializable
data class LoginInfoApiResponse(
    @SerialName("Modulus")
    val modulus: String,
    @SerialName("ServerEphemeral")
    val serverEphemeral: String,
    @SerialName("Version")
    val version: Int,
    @SerialName("Salt")
    val salt: String,
    @SerialName("SRPSession")
    val srpSession: String
)

data class LoginApiBody(
    val username: String,
    val srpSession: String,
    val clientEphemeral: String,
    val clientProof: String,
    val twoFactorCode: String? = null

) {
    fun toRetrofitRequestBody(): RequestBody {
        return """{
            "Username": "$username",
            "SRPSession": "$srpSession",
            "ClientEphemeral": "$clientEphemeral",
            "ClientProof": "$clientProof",
            "TwoFactorCode": "${twoFactorCode ?: ""}"
        }""".trimIndent().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }
}

@Serializable
data class LoginApiResponse(
    @SerialName("AccessToken")
    val accessToken: String,
    @SerialName("UID")
    val uid: String,
    @SerialName("UserID")
    val userId: String,
    @SerialName("RefreshToken")
    val refreshToken: String,
    @SerialName("EventID")
    val eventID: String
)
