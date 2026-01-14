package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateAutoDetectPrimaryTimezoneWorker {

    fun enqueue(workManager: WorkManager, userId: String, autoDetectPrimaryTimezone: Boolean) : LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE,
            UseCaseWorker.INPUT_USER_ID to userId,
            UseCaseWorker.INPUT_AUTO_DETECT_PRIMARY_TIMEZONE to autoDetectPrimaryTimezone
        ), UseCaseWorker.UniqueWorkNames.UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE).state
    }
}
