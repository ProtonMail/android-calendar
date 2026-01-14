package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateWeekStartWorker {

    fun enqueue(workManager: WorkManager, userId: String, weekStart: Int) : LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_WEEK_START,
            UseCaseWorker.INPUT_USER_ID to userId,
            UseCaseWorker.INPUT_WEEK_START to weekStart
        ), UseCaseWorker.UniqueWorkNames.UPDATE_WEEK_START).state
    }
}
