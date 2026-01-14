package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarImportMappingInfoApiResponse
import me.proton.android.calendar.data.api.CalendarMappingEntity
import me.proton.android.calendar.data.api.CancelImportApiResponse
import me.proton.android.calendar.data.api.CreateAccessTokenApiResponse
import me.proton.android.calendar.data.api.CreateImporterApiResponse
import me.proton.android.calendar.data.api.DeleteReportApiResponse
import me.proton.android.calendar.data.api.GoogleClientIdApiResponse
import me.proton.android.calendar.data.api.ImporterApiResponse
import me.proton.android.calendar.data.api.ImportersApiResponse
import me.proton.android.calendar.data.api.ReportsApiResponse
import me.proton.android.calendar.data.api.ResumeImportApiResponse
import me.proton.android.calendar.data.api.StartImporterApiResponse
import me.proton.android.calendar.data.api.UpdateImporterApiResponse
import me.proton.core.domain.entity.UserId

interface ImporterApi {

    /**
     *  Return the google ClientID among all the API system config
     */
    suspend fun getGoogleClientId(userId: UserId): ApiResponse<GoogleClientIdApiResponse>

    /**
     *  This route uses the given Token code and redirect URI to get an access token.
     */
    suspend fun createAccessToken(userId: UserId, code: String): ApiResponse<CreateAccessTokenApiResponse>

    /**
     *  Create the Calendar Importer
     */
    suspend fun createCalendarImporter(userId: UserId, tokenId: String): ApiResponse<CreateImporterApiResponse>

    /**
     *  Update the Calendar Importer
     */
    suspend fun updateCalendarImporter(userId: UserId, importerId: String, tokenId: String): ApiResponse<UpdateImporterApiResponse>

    /**
     *  This route looks up the calendars from the remote provider.
     */
    suspend fun getCalendarImportMappingInfo(userId: UserId, importerId: String): ApiResponse<CalendarImportMappingInfoApiResponse>

    /**
     *  This route launches the import in a background task.
     */
    suspend fun startImporter(userId: UserId, importerId: String, customCalendarMapping: Boolean, calendarMapping: List<CalendarMappingEntity>): ApiResponse<StartImporterApiResponse>

    /**
     *  Get all the importers. The Active field is present if there is an ongoing import.
     */
    suspend fun getImporters(userId: UserId): ApiResponse<ImportersApiResponse>

    /**
     *  Get one importer.
     */
    suspend fun getImporter(userId: UserId, importerId: String): ApiResponse<ImporterApiResponse>

    /**
     *  This route returns a history of the finished imports.
     */
    suspend fun getReports(userId: UserId): ApiResponse<ReportsApiResponse>

    /**
     *  This route cancels an ongoing import.
     */
    suspend fun cancelImport(userId: UserId, importerId: String): ApiResponse<CancelImportApiResponse>

    /**
     *  This route resumes a paused import.
     */
    suspend fun resumeImport(userId: UserId, importerId: String): ApiResponse<ResumeImportApiResponse>

    /**
     *  This route deletes the given import report.
     */
    suspend fun deleteReport(userId: UserId, reportId: String): ApiResponse<DeleteReportApiResponse>
}
