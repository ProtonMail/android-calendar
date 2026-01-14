package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*
import me.proton.core.domain.entity.UserId

interface BugReportsApi {
    suspend fun sendBugReport(userId: UserId, body: BugReportsApiRequest): ApiResponse<BugReportsApiResponse>
}
