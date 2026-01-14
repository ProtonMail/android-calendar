package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateAlarmsWorker {

    fun enqueue(workManager: WorkManager, userId: String, calendarId: String, updateAllDayEventsAlarms: Set<String>, updatePartDayEventsAlarms: Set<String>) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_ALARMS,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId,
                UseCaseWorker.INPUT_UPDATE_ALL_DAY_ALARMS to updateAllDayEventsAlarms.contains(calendarId),
                UseCaseWorker.INPUT_UPDATE_PART_DAY_ALARMS to updatePartDayEventsAlarms.contains(calendarId)
            ),
            UseCaseWorker.UniqueWorkNames.UPDATE_ALARMS,
        )
    }
}
