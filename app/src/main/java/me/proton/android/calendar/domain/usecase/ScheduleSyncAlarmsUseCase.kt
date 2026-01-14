package me.proton.android.calendar.domain.usecase

import androidx.work.Operation
import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.SyncAlarmsWorker
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import java.time.Duration
import javax.inject.Inject

class ScheduleSyncAlarmsUseCase @Inject constructor(
    private val logger: Logger,
    private val workManager: WorkManager
) : UseCase {

    fun execute(
        userId: UserId,
        initialDelay: Duration = Duration.ofSeconds(0),
        force: Boolean = false
    ): Operation {
        logger.v("executing ScheduleSyncAlarmsUseCase")
        return SyncAlarmsWorker.enqueue(workManager, userId.id, force, initialDelay.toMillis())
    }
}
