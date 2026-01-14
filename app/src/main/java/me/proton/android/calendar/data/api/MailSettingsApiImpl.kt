package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.domain.api.MailSettingsApi
import me.proton.android.calendar.domain.model.MailSettings
import me.proton.android.calendar.domain.model.PackageType
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.util.kotlin.toBoolean
import retrofit2.http.GET
import javax.inject.Inject

interface MailSettingsApiService : BaseRetrofitApi {

    @GET("mail/v4/settings")
    suspend fun getMailSettings(): MailSettingsApiResponse

}

class MailSettingsApiImpl @Inject constructor(private val apiProvider: ApiProvider) : MailSettingsApi {

    override suspend fun getMailSettings(userId: UserId): ApiResponse<MailSettingsApiResponse> =
        apiProvider.get<MailSettingsApiService>(userId).invoke {
            getMailSettings()
        }.toApiResponse()

}

@Serializable
data class MailSettingsApiResponse(
    @SerialName("MailSettings")
    val mailSettings: MailSettingsEntity
)

@Serializable
data class MailSettingsEntity(
    @SerialName("Sign")
    val sign: Int,
    @SerialName("PGPScheme")
    val pgpScheme: Int,
    @SerialName("AutoSaveContacts")
    val autoSaveContacts: Int,
    @SerialName("DraftMIMEType")
    val draftMimeType: String
) {
    fun toMailSettings(): MailSettings? {
        val packageType = PackageType.values().firstOrNull { it.type == this.pgpScheme }

        return packageType?.let {
            MailSettings(
                sign = this.sign.toBoolean(),
                pgpScheme = packageType,
                autoSaveContacts = this.autoSaveContacts.toBoolean(),
                draftMimeType = this.draftMimeType
            )
        }
    }
}
