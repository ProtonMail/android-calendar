package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.logger.SentryUtils
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.BugReportsApiRequest
import me.proton.android.calendar.domain.api.BugReportsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class SendBugReportUseCase @Inject constructor(
    private val defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider,
    private val bugReportsApi: BugReportsApi
) {

    suspend fun execute(
        userId: UserId,
        osName: String,
        osVersion: String,
        client: String,
        appVersionName: String,
        title: String,
        description: String,
        username: String,
        email: String
    ): UseCase.Result {

        val installationId = SentryUtils.getInstallationId(defaultSharedPreferencesProvider.sharedPreferences)

        val bugReportsApiRequest = BugReportsApiRequest(
            osName,
            osVersion,
            client,
            appVersionName,
            title,
            "$description\n\nSentry user ID: $installationId",
            username,
            email)

        return when (val reportsApiResponse = bugReportsApi.sendBugReport(userId, bugReportsApiRequest)) {
            is ApiResponse.Success -> {
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> UseCase.Result.Error(reportsApiResponse.error)
            is ApiResponse.Exception -> UseCase.Result.Error(reportsApiResponse.exception.message ?: "(no exception message)")
        }
    }
}
