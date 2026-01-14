package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.PERIODIC_CALENDAR_WORKER_REFRESH_PERIOD
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.HandleAlarmsUseCase
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getAccounts
import me.proton.core.featureflag.domain.FeatureFlagManager
import java.util.concurrent.TimeUnit

@HiltWorker
class PeriodicCalendarWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val accountManager: AccountManager,
    private val widgetRefresher: WidgetRefresher,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val featureFlagManager: FeatureFlagManager
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {

        forceShowLateAlarms()

        forceRefreshWidget()

        return Result.success()
    }

    private fun forceRefreshWidget() {
        widgetRefresher.broadcastRefresh()
    }

    private suspend fun forceShowLateAlarms() {

        try {
            accountManager.getAccounts(AccountState.Ready).first().forEach {
                handleAlarmsUseCase.execute(it.userId)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                logger.e("exception in forceShowLateAlarms()", e)
            }
        }

    }

    companion object {
        const val UNIQUE_WORK_NAME = "PERIODIC_CALENDAR_WORKER"

        fun setup(workManager: WorkManager, logger: Logger) {

            try {
                val constraints = Constraints.Builder().build()

                val work = PeriodicWorkRequestBuilder<PeriodicCalendarWorker>(PERIODIC_CALENDAR_WORKER_REFRESH_PERIOD)
                    .setConstraints(constraints)
                    .setInitialDelay(5, TimeUnit.MINUTES)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                    work
                )
            } catch (e: Exception) {
                logger.e("exception in PeriodicCalendarWorker setup", e)
            }
        }
    }

}
