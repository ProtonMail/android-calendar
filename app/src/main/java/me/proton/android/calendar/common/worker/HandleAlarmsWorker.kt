package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.CoroutineWorker
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending
import me.proton.android.calendar.common.utils.WorkerUtils.executeUseCase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId

@HiltWorker
class HandleAlarmsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
) : CoroutineWorker(context, workerParameters) {

    companion object {
        const val INPUT_USER_ID = "INPUT_USER_ID"
        const val INPUT_CALENDAR_ID = "INPUT_CALENDAR_ID"

        const val INPUT_ALARM_EPOCH_SECONDS = "INPUT_ALARM_EPOCH_SECONDS"

        fun enqueue(workManager: WorkManager, userId: String, alarmEpochSeconds: Long?): LiveData<Operation.State> {
            return workManager.enqueueAppending<HandleAlarmsWorker>(
                workDataOf(
                    INPUT_USER_ID to userId,
                    INPUT_ALARM_EPOCH_SECONDS to alarmEpochSeconds
                ),
                "HANDLE_ALARMS_$userId",
                requireNetwork = false,
            ).state
        }
    }

    override suspend fun doWork(): Result = executeUseCase(logger) {
        val userId = inputData.getString(INPUT_USER_ID)?.let { UserId(it) } ?: return@executeUseCase UseCase.Result.InvalidParams("Missing user ID")
        val alarmEpochSeconds = if (inputData.hasKeyWithValueOfType<Long>(INPUT_ALARM_EPOCH_SECONDS)) {
            inputData.getLong(INPUT_ALARM_EPOCH_SECONDS, 0)
        } else null
        handleAlarmsUseCase.execute(userId, alarmEpochSeconds)
    }
}
