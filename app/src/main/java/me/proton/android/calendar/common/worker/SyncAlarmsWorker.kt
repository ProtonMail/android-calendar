package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending
import me.proton.android.calendar.common.utils.WorkerUtils.executeUseCase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.SyncAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId

@HiltWorker
class SyncAlarmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val syncAlarmsUseCase: SyncAlarmsUseCase,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        const val INPUT_USER_ID = "INPUT_USER_ID"
        const val INPUT_CALENDAR_ID = "INPUT_CALENDAR_ID"

        const val INPUT_FORCE_SYNC_ALARMS = "INPUT_FORCE_SYNC_ALARMS"

        fun enqueue(workManager: WorkManager, userId: String, force: Boolean, initialDelayMs: Long): Operation {
            return workManager.enqueueAppending<SyncAlarmsWorker>(
                workDataOf(
                    INPUT_USER_ID to userId,
                    INPUT_FORCE_SYNC_ALARMS to force,
                ),
                "SYNC_ALARMS_$userId",
                initialDelayMs = initialDelayMs,
            )
        }
    }

    override suspend fun doWork(): Result = executeUseCase(logger) {
        val userId = inputData.getString(INPUT_USER_ID)?.let { UserId(it) } ?: return@executeUseCase UseCase.Result.InvalidParams("Missing user ID")
        val forceSyncAlarms = inputData.getBoolean(INPUT_FORCE_SYNC_ALARMS, false)
        syncAlarmsUseCase.execute(userId, forceSyncAlarms)
    }
}
