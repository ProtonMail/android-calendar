package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.CoroutineWorker
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending
import me.proton.android.calendar.common.utils.WorkerUtils.executeUseCase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.SendBugReportUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId

@HiltWorker
class BugReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val sendBugReportUseCase: SendBugReportUseCase,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        const val INPUT_USER_ID = "INPUT_USER_ID"
        const val INPUT_OS_NAME = "INPUT_OS_NAME"
        const val INPUT_OS_VERSION = "INPUT_OS_VERSION"
        const val INPUT_CLIENT = "INPUT_CLIENT"
        const val INPUT_APP_VERSION_NAME = "INPUT_APP_VERSION_NAME"
        const val INPUT_TITLE = "INPUT_TITLE"
        const val INPUT_DESCRIPTION = "INPUT_DESCRIPTION"
        const val INPUT_USERNAME = "INPUT_USERNAME"
        const val INPUT_EMAIL = "INPUT_EMAIL"

        fun enqueue(workManager: WorkManager, userId: String, osName: String, osVersion: String, client: String,
                    appVersionName: String, title: String, description: String, username: String,
                    email: String,
        ): LiveData<Operation.State> {
            return workManager.enqueueAppending<BugReportWorker>(
                workDataOf(
                    INPUT_USER_ID to userId,
                    INPUT_OS_NAME to osName,
                    INPUT_OS_VERSION to osVersion,
                    INPUT_CLIENT to client,
                    INPUT_APP_VERSION_NAME to appVersionName,
                    INPUT_TITLE to title,
                    INPUT_DESCRIPTION to description,
                    INPUT_USERNAME to username,
                    INPUT_EMAIL to email,

                ),
                "BUG_REPORT_$userId",
            ).state
        }
    }

    override suspend fun doWork(): Result = executeUseCase(logger) {
        val userId = inputData.getString(INPUT_USER_ID)?.let { UserId(it) } ?: return@executeUseCase UseCase.Result.InvalidParams("Missing user ID")

        sendBugReportUseCase.execute(
            userId = userId,
            osName = inputData.getString(INPUT_OS_NAME) ?: return@executeUseCase UseCase.Result.InvalidParams("No OS_NAME provided"),
            osVersion = inputData.getString(INPUT_OS_VERSION) ?: return@executeUseCase UseCase.Result.InvalidParams("No OS_VERSION provided"),
            client = inputData.getString(INPUT_CLIENT) ?: return@executeUseCase UseCase.Result.InvalidParams("No CLIENT provided"),
            appVersionName = inputData.getString(INPUT_APP_VERSION_NAME) ?: return@executeUseCase UseCase.Result.InvalidParams("No APP_VERSION_NAME provided"),
            title = inputData.getString(INPUT_TITLE) ?: return@executeUseCase UseCase.Result.InvalidParams("No TITLE provided"),
            description = inputData.getString(INPUT_DESCRIPTION) ?: return@executeUseCase UseCase.Result.InvalidParams("No DESCRIPTION provided"),
            username = inputData.getString(INPUT_USERNAME) ?: return@executeUseCase UseCase.Result.InvalidParams("No USERNAME provided"),
            email = inputData.getString(INPUT_EMAIL) ?: return@executeUseCase UseCase.Result.InvalidParams("No EMAIL provided"),
        )
    }
}
