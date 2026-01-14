package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object RefreshMemberFlagsWorker {

    fun enqueue(workManager: WorkManager, userId: String) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.REFRESH_MEMBERS_FLAGS,
                UseCaseWorker.INPUT_USER_ID to userId
            ),
            UseCaseWorker.UniqueWorkNames.REFRESH_MEMBERS_FLAGS,
        )
    }
}