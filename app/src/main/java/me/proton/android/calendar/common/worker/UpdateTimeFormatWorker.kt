package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateTimeFormatWorker {

    fun enqueue(workManager: WorkManager, userId: String, timeFormat: Int) : LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_TIME_FORMAT,
            UseCaseWorker.INPUT_USER_ID to userId,
            UseCaseWorker.INPUT_TIME_FORMAT to timeFormat
        ), UseCaseWorker.UniqueWorkNames.UPDATE_TIME_FORMAT).state
    }
}
