package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object FixCalendarsWorker {

    fun enqueue(workManager: WorkManager, userId: String): LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.FIX_CALENDARS,
            UseCaseWorker.INPUT_USER_ID to userId
        ), UseCaseWorker.UniqueWorkNames.FIX_CALENDARS).state
    }
}