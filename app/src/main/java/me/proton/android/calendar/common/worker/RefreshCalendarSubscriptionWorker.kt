package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object RefreshCalendarSubscriptionWorker {

    fun enqueue(workManager: WorkManager, userId: String, calendarId: String) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.REFRESH_CALENDAR_SUBSCRIPTION,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId
            ),
            UseCaseWorker.UniqueWorkNames.REFRESH_CALENDAR_SUBSCRIPTION,
        )
    }
}
