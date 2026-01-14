package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateDisplayWeekNumberWorker {

    fun enqueue(workManager: WorkManager, userId: String, displayWeekNumber: Boolean) : LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_DISPLAY_WEEK_NUMBER,
            UseCaseWorker.INPUT_USER_ID to userId,
            UseCaseWorker.INPUT_DISPLAY_WEEK_NUMBER to displayWeekNumber
        ), UseCaseWorker.UniqueWorkNames.UPDATE_DISPLAY_WEEK_NUMBER).state
    }
}
