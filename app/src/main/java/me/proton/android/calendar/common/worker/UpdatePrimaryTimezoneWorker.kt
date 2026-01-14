package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdatePrimaryTimezoneWorker {

    fun enqueue(workManager: WorkManager, userId: String, primaryTimezone: String): LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_PRIMARY_TIMEZONE,
            UseCaseWorker.INPUT_USER_ID to userId,
            UseCaseWorker.INPUT_PRIMARY_TIMEZONE to primaryTimezone
        ),
            uniqueWorkName = UseCaseWorker.UniqueWorkNames.UPDATE_PRIMARY_TIMEZONE + "_$userId"
        ).state
    }
}
