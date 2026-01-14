package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.CalendarImport.PRODUCT_CALENDAR
import me.proton.android.calendar.common.CalendarImport.REDIRECT_URI
import me.proton.android.calendar.common.CalendarImport.SOURCE
import me.proton.android.calendar.domain.api.ImporterApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.util.kotlin.toInt
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import javax.inject.Inject


interface ImporterApiService : BaseRetrofitApi {
    @GET("core/v4/system/config")
    suspend fun getGoogleClientId(): GoogleClientIdApiResponse

    @POST("importer/v1/tokens")
    suspend fun createAccessToken(@Body body: CreateAccessTokenApiRequest): CreateAccessTokenApiResponse

    @POST("importer/v1/importers")
    suspend fun createImporter(@Body body: CreateImporterApiRequest): CreateImporterApiResponse

    @GET("importer/v1/calendar/importers/{importerId}")
    suspend fun getCalendarImportMappingInfo(@Path("importerId") importerId: String): CalendarImportMappingInfoApiResponse

    @POST("importer/v1/importers/start")
    suspend fun startImporter(@Body body: StartImporterApiRequest): StartImporterApiResponse

    @PUT("importer/v1/importers/{importerId}")
    suspend fun updateImporter(@Path("importerId") importerId: String, @Body body: UpdateImporterApiRequest): UpdateImporterApiResponse

    @GET("importer/v1/importers")
    suspend fun getImporters(): ImportersApiResponse

    @GET("importer/v1/importers/{importerId}")
    suspend fun getImporter(@Path("importerId") importerId: String): ImporterApiResponse

    @GET("importer/v1/reports")
    suspend fun getReports(): ReportsApiResponse

    @PUT("importer/v1/importers/cancel")
    suspend fun cancelImport(@Body body: CancelImportApiRequest): CancelImportApiResponse

    @PUT("importer/v1/importers/resume")
    suspend fun resumeImport(@Body body: ResumeImportApiRequest): ResumeImportApiResponse

    @DELETE("importer/v1/calendar/importers/reports/{reportId}")
    suspend fun deleteReport(@Path("reportId") reportId: String): DeleteReportApiResponse
}

class ImporterApiImpl @Inject constructor(private val apiProvider: ApiProvider) : ImporterApi {

    override suspend fun getGoogleClientId(userId: UserId): ApiResponse<GoogleClientIdApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getGoogleClientId()
        }.toApiResponse()

    override suspend fun createAccessToken(userId: UserId, code: String): ApiResponse<CreateAccessTokenApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            createAccessToken(
                CreateAccessTokenApiRequest(
                    code = code,
                    redirectUri = REDIRECT_URI,
                    provider = 1,
                    products = listOf(PRODUCT_CALENDAR)
                )
            )
        }.toApiResponse()

    override suspend fun createCalendarImporter(userId: UserId, tokenId: String): ApiResponse<CreateImporterApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            createImporter(
                CreateImporterApiRequest(
                    tokenId = tokenId,
                    calendar = 1,
                    source = SOURCE
                )
            )
        }.toApiResponse()

    override suspend fun updateCalendarImporter(userId: UserId, importerId: String, tokenId: String): ApiResponse<UpdateImporterApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            updateImporter(
                importerId,
                UpdateImporterApiRequest(
                    tokenId = tokenId
                )
            )
        }.toApiResponse()

    override suspend fun getCalendarImportMappingInfo(userId: UserId, importerId: String): ApiResponse<CalendarImportMappingInfoApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getCalendarImportMappingInfo(
                importerId
            )
        }.toApiResponse()

    override suspend fun startImporter(userId: UserId, importerId: String, customCalendarMapping: Boolean, calendarMapping: List<CalendarMappingEntity>): ApiResponse<StartImporterApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            startImporter(
                StartImporterApiRequest(
                    importerId = importerId,
                    calendar = CustomCalendarMappingEntity(
                        customCalendarMapping = customCalendarMapping.toInt(),
                        mapping = calendarMapping
                    )
                )
            )
        }.toApiResponse()

    override suspend fun getImporters(userId: UserId): ApiResponse<ImportersApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getImporters()
        }.toApiResponse()

    override suspend fun getImporter(userId: UserId, importerId: String): ApiResponse<ImporterApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getImporter(importerId)
        }.toApiResponse()

    override suspend fun getReports(userId: UserId): ApiResponse<ReportsApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            getReports()
        }.toApiResponse()

    override suspend fun cancelImport(userId: UserId, importerId: String): ApiResponse<CancelImportApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            cancelImport(
                CancelImportApiRequest(
                    importerId,
                    listOf("Calendar")
                )
            )
        }.toApiResponse()

    override suspend fun resumeImport(userId: UserId, importerId: String): ApiResponse<ResumeImportApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            resumeImport(
                ResumeImportApiRequest(
                    importerId,
                    listOf("Calendar")
                )
            )
        }.toApiResponse()

    override suspend fun deleteReport(userId: UserId, reportId: String): ApiResponse<DeleteReportApiResponse> =
        apiProvider.get<ImporterApiService>(userId).invoke {
            deleteReport(
                reportId
            )
        }.toApiResponse()
}

@Serializable
data class GoogleClientIdApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Config")
    val config: ConfigEntity
): BaseApiResponse()

@Serializable
data class ConfigEntity(
    @SerialName("importer.google.client_id")
    val googleClientId: String
)

@Serializable
data class CreateAccessTokenApiRequest(
    @SerialName("Code")
    val code: String,
    @SerialName("RedirectUri")
    val redirectUri: String,
    @SerialName("Provider")
    val provider: Int,
    @SerialName("Products")
    val products: List<String>
)

@Serializable
data class CreateAccessTokenApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Token")
    val token: TokenEntity
): BaseApiResponse()

@Serializable
data class TokenEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("Account")
    val account: String,
    @SerialName("Provider")
    val provider: Int,
    @SerialName("Products")
    val products: List<String>
)

@Serializable
data class CreateImporterApiRequest(
    @SerialName("TokenID")
    val tokenId: String,
    @SerialName("Calendar")
    val calendar: Int,
    @SerialName("Source")
    val source: String
)

@Serializable
data class CreateImporterApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("ImporterID")
    val importerID: String
): BaseApiResponse()

@Serializable
data class CalendarImportMappingInfoApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Calendars")
    val calendars: List<ExternalCalendarEntity>
): BaseApiResponse()

@Serializable
data class ExternalCalendarEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("Source")
    val source: String,
    @SerialName("Description")
    val description: String
)

@Serializable
data class StartImporterApiRequest(
    @SerialName("ImporterID")
    val importerId: String,
    @SerialName("Calendar")
    val calendar: CustomCalendarMappingEntity
)

@Serializable
data class CustomCalendarMappingEntity(
    @SerialName("CustomCalendarMapping")
    val customCalendarMapping: Int,
    @SerialName("Mapping")
    val mapping: List<CalendarMappingEntity>
)

@Serializable
data class CalendarMappingEntity(
    @SerialName("Source")
    val source: String,
    @SerialName("Destination")
    val destination: String
)

@Serializable
data class StartImporterApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()

@Serializable
data class ImportersApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Importers")
    val importers: List<ImporterEntity>
): BaseApiResponse()

@Serializable
data class ImporterApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Importer")
    val importer: ImporterEntity
): BaseApiResponse()

@Serializable
data class ImporterEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("Account")
    val account: String,
    @SerialName("Product")
    val product: List<String>, // "Mail", "Contacts", "Calendar"
    @SerialName("TokenID")
    val tokenID: String?,
    @SerialName("Active")
    val active: ActiveImporterEntity? = null // The Active field is present if there is an ongoing import.
)

@Serializable
data class ActiveImporterEntity(
    @SerialName("Calendar")
    val calendar: ActiveCalendarImporterEntity? = null
)

@Serializable
data class ActiveCalendarImporterEntity(
    @SerialName("CreateTime")
    val createTime: Int,
    @SerialName("State")
    val state: Int, // 0: QUEUED, 1: RUNNING, 2: DONE, 3: FAILED, 4: PAUSED, 5: CANCELED
    @SerialName("ErrorCode")
    val errorCode: Int, // 1: Lost connection, 2: Storage limit reached
)

@Serializable
data class ActiveImporterMappingEntity(
    @SerialName("Source")
    val source: String,
    @SerialName("Destination")
    val destination: String,
    @SerialName("Processed")
    val processed: Int,
    @SerialName("State")
    val state: Int // 0: QUEUED, 1: RUNNING, 2: DONE, 3: FAILED, 4: PAUSED, 5: CANCELED
)

@Serializable
data class ReportsApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Reports")
    val reports: List<ReportEntity>
): BaseApiResponse()

@Serializable
data class ReportEntity(
    @SerialName("ID")
    val id: String,
    @SerialName("Provider")
    val provider: Int,
    @SerialName("Account")
    val account: String,
    @SerialName("State")
    val state: Int,
    @SerialName("CreateTime")
    val createTime: Int,
    @SerialName("EndTime")
    val endTime: Int,
    @SerialName("TotalSize")
    val totalSize: Int,
    @SerialName("Summary")
    val summary: ReportSummaryEntity
)

@Serializable
data class ReportSummaryEntity(
    @SerialName("Calendar")
    val calendar: ReportCalendarSummaryEntity? = null
)

@Serializable
data class ReportCalendarSummaryEntity(
    @SerialName("State")
    val state: Int, // 0: QUEUED, 1: RUNNING, 2: DONE, 3: FAILED, 4: PAUSED, 5: CANCELED
    @SerialName("NumEvents")
    val numEvents: Int,
    @SerialName("TotalSize")
    val totalSize: Int
)

@Serializable
data class CancelImportApiRequest(
    @SerialName("ImporterID")
    val importerId: String,
    @SerialName("Products")
    val products: List<String>
)

@Serializable
data class CancelImportApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()

@Serializable
data class ResumeImportApiRequest(
    @SerialName("ImporterID")
    val importerId: String,
    @SerialName("Products")
    val products: List<String>
)

@Serializable
data class ResumeImportApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()

@Serializable
data class DeleteReportApiResponse(
    @SerialName("Code")
    override val code: Int
): BaseApiResponse()

@Serializable
data class UpdateImporterApiRequest(
    @SerialName("TokenID")
    val tokenId: String
)

@Serializable
data class UpdateImporterApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Importer")
    val importer: ImporterEntity
): BaseApiResponse()
