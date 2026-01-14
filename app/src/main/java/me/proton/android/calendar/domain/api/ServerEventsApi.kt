package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*
import me.proton.core.domain.entity.UserId

interface ServerEventsApi {
    /**
     * Gets latest event ID, only use with empty cache to bootstrap event stream.
     */
    suspend fun getLatestServerCoreEvent(userId: UserId): ApiResponse<LatestServerCoreEventApiResponse>

    suspend fun getLatestServerCalendarEvent(userId: UserId, calendarId: String): ApiResponse<LatestServerCalendarEventApiResponse>

    /**
     * Gets core events since last event ID.
     */
    suspend fun getServerCoreEventsSince(userId: UserId, serverEventId: String): ApiResponse<ServerEventsApiResponse>

    /**
     * Gets calendar events since last event ID.
     */
    suspend fun getServerCalendarEventsSince(userId: UserId, serverEventId: String, calendarId: String): ApiResponse<ServerEventsApiResponse>
}
