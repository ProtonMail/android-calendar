package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import biweekly.parameter.ParticipationStatus
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object UpdateParticipationStatusSingleEditWorker {

    fun enqueue(
        workManager: WorkManager,
        userId: String,
        calendarId: String,
        eventUid: String,
        userEmails: List<String>,
        mainChainParticipationStatus: ParticipationStatus
    ): LiveData<Operation.State> {
        val status = mainChainParticipationStatus.toInt()
        return workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_CALENDAR_ID to calendarId,
                UseCaseWorker.INPUT_EVENT_UID to eventUid,
                UseCaseWorker.INPUT_USER_EMAILS to userEmails.toTypedArray(),
                UseCaseWorker.INPUT_PARTICIPATION_STATUS to status
            ),
            UseCaseWorker.UniqueWorkNames.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT,
        ).state
    }
}
