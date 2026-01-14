package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import me.proton.core.util.kotlin.toInt
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import javax.inject.Inject

interface SettingsApiService : BaseRetrofitApi {
    @GET("settings/calendar")
    suspend fun getCalendarUserSettings(): CalendarUserSettingsApiResponse

    @PUT("settings/calendar")
    suspend fun updateCalendarUserPrimaryTimezone(@Body body: UpdateCalendarUserPrimaryTimezoneApiRequest): CalendarUserSettingsApiResponse

    @PUT("settings/calendar")
    suspend fun updateCalendarUserAutoDetectTimezone(@Body body: UpdateCalendarUserAutoDetectTimezoneApiRequest): CalendarUserSettingsApiResponse

    @PUT("settings/calendar")
    suspend fun updateCalendarUserDisplayWeekNumber(@Body body: UpdateCalendarUserDisplayWeekNumberApiRequest): CalendarUserSettingsApiResponse

    @PUT("settings/calendar")
    suspend fun updateCalendarUserDefaultCalendarId(@Body body: UpdateCalendarUserDefaultCalendarIdApiRequest): CalendarUserSettingsApiResponse

    @PUT("settings/calendar")
    suspend fun updateCalendarUserAutoImportInvite(@Body body: UpdateCalendarUserAutoImportInviteApiRequest): CalendarUserSettingsApiResponse

    @GET("settings")
    suspend fun getUserSettings(): UserSettingsApiResponse

    @PUT("settings/timeformat")
    suspend fun updateUserTimeFormat(@Body body: UpdateUserTimeFormatApiRequest): UserSettingsApiResponse

    @PUT("settings/weekstart")
    suspend fun updateUserWeekStart(@Body body: UpdateUserWeekStartApiRequest): UserSettingsApiResponse

}

class SettingsApiImpl @Inject constructor(private val apiProvider: ApiProvider) : SettingsApi {

    override suspend fun getCalendarUserSettings(userId: UserId): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            getCalendarUserSettings()
        }.toApiResponse()

    override suspend fun updateCalendarUserPrimaryTimezone(userId: UserId, primaryTimezone: String): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateCalendarUserPrimaryTimezone(
                UpdateCalendarUserPrimaryTimezoneApiRequest(primaryTimezone)
            )
        }.toApiResponse()

    override suspend fun updateCalendarUserAutoDetectTimezone(userId: UserId, autoDetectPrimaryTimezone: Int): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateCalendarUserAutoDetectTimezone(
                UpdateCalendarUserAutoDetectTimezoneApiRequest(autoDetectPrimaryTimezone)
            )
        }.toApiResponse()

    override suspend fun updateCalendarUserDisplayWeekNumber(userId: UserId, displayWeekNumber: Int): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateCalendarUserDisplayWeekNumber(
                UpdateCalendarUserDisplayWeekNumberApiRequest(displayWeekNumber)
            )
        }.toApiResponse()

    override suspend fun updateCalendarUserDefaultCalendarId(userId: UserId, defaultCalendarId: String): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateCalendarUserDefaultCalendarId(
                UpdateCalendarUserDefaultCalendarIdApiRequest(defaultCalendarId)
            )
        }.toApiResponse()

    override suspend fun updateCalendarUserAutoImportInvite(
        userId: UserId,
        autoImportInvite: Boolean
    ): ApiResponse<CalendarUserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateCalendarUserAutoImportInvite(
                UpdateCalendarUserAutoImportInviteApiRequest(autoImportInvite.toInt())
            )
        }.toApiResponse()

    override suspend fun updateUserTimeFormat(userId: UserId, timeFormat: Int): ApiResponse<UserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateUserTimeFormat(
                UpdateUserTimeFormatApiRequest(timeFormat)
            )
        }.toApiResponse()

    override suspend fun updateUserWeekStart(userId: UserId, weekStart: Int): ApiResponse<UserSettingsApiResponse> =
        apiProvider.get<SettingsApiService>(userId).invoke {
            updateUserWeekStart(
                UpdateUserWeekStartApiRequest(weekStart)
            )
        }.toApiResponse()
}

@Serializable
data class CalendarUserSettingsApiResponse(
    @SerialName("CalendarUserSettings")
    val calendarUserSettings: CalendarUserSettingsEntity
)

@Serializable
data class UserSettingsApiResponse(
    @SerialName("UserSettings")
    val userSettings: UserSettingsEntity
)

@Serializable
data class UpdateCalendarUserPrimaryTimezoneApiRequest(
    @SerialName("PrimaryTimezone")
    val primaryTimezone: String
)

@Serializable
data class UpdateCalendarUserAutoDetectTimezoneApiRequest(
    @SerialName("AutoDetectPrimaryTimezone")
    val autoDetectPrimaryTimezone: Int
)

@Serializable
data class UpdateCalendarUserDisplayWeekNumberApiRequest(
    @SerialName("DisplayWeekNumber")
    val displayWeekNumber: Int
)

@Serializable
data class UpdateCalendarUserDefaultCalendarIdApiRequest(
    @SerialName("DefaultCalendarID")
    val defaultCalendarId: String
)

@Serializable
data class UpdateCalendarUserAutoImportInviteApiRequest(
    @SerialName("AutoImportInvite")
    val autoImportInvite: Int
)

@Serializable
data class UpdateUserTimeFormatApiRequest(
    @SerialName("TimeFormat")
    val timeFormat: Int
)

@Serializable
data class UpdateUserWeekStartApiRequest(
    @SerialName("WeekStart")
    val weekStart: Int
)
