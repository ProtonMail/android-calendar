package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object HandleAlarmsWithMissingEventWorker {

    fun enqueue(workManager: WorkManager, userId: String, calendarId: String, eventId: String) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.HANDLE_ALARMS_WITH_MISSING_EVENT,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId,
                UseCaseWorker.INPUT_EVENT_ID to eventId
            ),
            UseCaseWorker.UniqueWorkNames.HANDLE_ALARMS_WITH_MISSING_EVENT,
        )
    }
}
