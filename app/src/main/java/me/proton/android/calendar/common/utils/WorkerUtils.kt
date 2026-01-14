package me.proton.android.calendar.common.utils

import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import me.proton.android.calendar.common.WORKER_MAX_RETRY_COUNT
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.UseCase
import java.util.concurrent.TimeUnit

object WorkerUtils {

    inline fun <reified T: ListenableWorker> WorkManager.enqueueAppending(
        workData: Data,
        uniqueWorkName: String,
        requireNetwork: Boolean = true,
        initialDelayMs: Long? = null,
    ): Operation {
        val work = OneTimeWorkRequestBuilder<T>()
            .let {
                if (requireNetwork) {
                    it.setConstraints(Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                } else {
                    it
                }
            }
            .let {
                if (initialDelayMs != null) {
                  it.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                } else {
                    it
                }
            }
            .setInputData(workData)
            .build()

        return enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }

    suspend fun CoroutineWorker.executeUseCase(logger: Logger, block: suspend () -> UseCase.Result): ListenableWorker.Result {
        val logTag = this.javaClass.simpleName
        logger.v("inside $logTag doWork()")

        val useCaseResult = block()

        return when (useCaseResult) {
            is UseCase.Result.Success<*> -> {
                logger.v("Worker $logTag success")
                ListenableWorker.Result.success()
            }
            is UseCase.Result.InvalidParams -> {
                logger.i("Worker $logTag failure, reason: ${useCaseResult.message}")
                ListenableWorker.Result.failure()
            }
            is UseCase.Result.Error -> {
                if (this.runAttemptCount >= WORKER_MAX_RETRY_COUNT) {
                    logger.e("Worker $logTag error, reason: ${useCaseResult.message}, max retry exceeded")
                    ListenableWorker.Result.failure()
                } else {
                    logger.i("Worker $logTag error, reason: ${useCaseResult.message}, retrying")
                    ListenableWorker.Result.retry()
                }
            }
        }
    }
}
