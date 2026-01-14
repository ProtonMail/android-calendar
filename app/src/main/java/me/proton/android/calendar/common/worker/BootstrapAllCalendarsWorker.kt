package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object BootstrapAllCalendarsWorker {

    fun enqueue(workManager: WorkManager, userId: String) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.BOOTSTRAP_ALL_CALENDARS,
                UseCaseWorker.INPUT_USER_ID to userId
            ),
            UseCaseWorker.UniqueWorkNames.BOOTSTRAP_ALL_CALENDARS,
        )
    }
}
