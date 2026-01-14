package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.CalendarUserSettingsApiResponse
import me.proton.android.calendar.data.api.UserSettingsApiResponse
import me.proton.core.domain.entity.UserId

interface SettingsApi {
    suspend fun getCalendarUserSettings(userId: UserId): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun updateCalendarUserPrimaryTimezone(userId: UserId, primaryTimezone: String): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun updateCalendarUserAutoDetectTimezone(userId: UserId, autoDetectPrimaryTimezone: Int): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun updateCalendarUserDisplayWeekNumber(userId: UserId, displayWeekNumber: Int): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun updateCalendarUserDefaultCalendarId(userId: UserId, defaultCalendarId: String): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun updateCalendarUserAutoImportInvite(userId: UserId, autoImportInvite: Boolean): ApiResponse<CalendarUserSettingsApiResponse>
    suspend fun updateUserTimeFormat(userId: UserId, timeFormat: Int): ApiResponse<UserSettingsApiResponse>
    suspend fun updateUserWeekStart(userId: UserId, weekStart: Int): ApiResponse<UserSettingsApiResponse>
}
