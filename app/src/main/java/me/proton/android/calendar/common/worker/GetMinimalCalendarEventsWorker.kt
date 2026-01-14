package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object GetMinimalCalendarEventsWorker {

    fun enqueue(workManager: WorkManager, userId: String, calendarId: String): LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.GET_MINIMAL_CALENDAR_EVENTS,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId
            ), "GET_MINIMAL_CALENDAR_EVENTS_${userId}",
        ).state
    }
}
