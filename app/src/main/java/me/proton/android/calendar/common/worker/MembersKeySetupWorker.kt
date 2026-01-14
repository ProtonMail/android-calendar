package me.proton.android.calendar.common.worker

import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending

object MembersKeySetupWorker {

    fun enqueue(workManager: WorkManager, userId: String, membersWithIncompleteKeySetup: Set<String>) {
        workManager.enqueueAppending<UseCaseWorker>(
            workDataOf(
                UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.MEMBERS_KEY_SETUP,
                UseCaseWorker.INPUT_USER_ID to userId,
                UseCaseWorker.INPUT_MEMBER_IDS to membersWithIncompleteKeySetup.toTypedArray()
            ),
            UseCaseWorker.UniqueWorkNames.MEMBERS_KEY_SETUP,
        )
    }
}
