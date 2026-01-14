package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.android.calendar.common.utils.WorkerUtils.executeUseCase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.PostProcessEventsMetadataUseCase

@HiltWorker
class PostProcessEventsMetadataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val postProcessEventsMetadataUseCase: PostProcessEventsMetadataUseCase
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result = executeUseCase(logger) {
        logger.v("inside PostProcessEventsMetadataWorker doWork")
        postProcessEventsMetadataUseCase.execute()
    }
}
