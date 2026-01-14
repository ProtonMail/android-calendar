package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object BootstrapCalendarsWorker {

    fun enqueue(workManager: WorkManager, userId: String, calendarsToBootstrap: Set<String>) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.BOOTSTRAP_CALENDARS,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_CALENDAR_IDS to calendarsToBootstrap.toTypedArray()
            ),
            UseCaseWorker.UniqueWorkNames.BOOTSTRAP_CALENDARS,
        )
    }
}
