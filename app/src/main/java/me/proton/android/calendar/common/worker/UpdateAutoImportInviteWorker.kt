package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateAutoImportInviteWorker {

    fun enqueue(workManager: WorkManager, userId: String, autoImportInvite: Boolean) : LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_AUTO_IMPORT_INVITE,
            UseCaseWorker.INPUT_USER_ID to userId,
            UseCaseWorker.INPUT_AUTO_IMPORT_INVITE to autoImportInvite
        ), UseCaseWorker.UniqueWorkNames.UPDATE_AUTO_IMPORT_INVITE).state
    }
}
