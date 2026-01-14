package me.proton.android.calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.worker.HandleAlarmsWorker
import me.proton.android.calendar.common.worker.PeriodicCalendarWorker
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.MigrateEventMetadataToOccurrencesUseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId

@DelicateCoroutinesApi
class ProtonCalendarBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun logger(): Logger
        fun accountManager(): AccountManager
        fun workManager(): WorkManager
        fun migrateEventMetadataToOccurrencesUseCase(): MigrateEventMetadataToOccurrencesUseCase
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val appContext = context?.applicationContext ?: return
        val entryPoint = try {
            EntryPointAccessors.fromApplication(appContext, ReceiverEntryPoint::class.java)
        } catch (_: IllegalStateException) {
            return
        }

        val logger = entryPoint.logger()
        val accountManager = entryPoint.accountManager()

        val workManager = entryPoint.workManager()
        fun handleAlarms(
            userId: UserId,
            alarmEpochSeconds: Long? = null
        ): LiveData<Operation.State> {
            return HandleAlarmsWorker.enqueue(workManager, userId = userId.id, alarmEpochSeconds)
        }

        logger.v("intent in ProtonCalendarBroadcastReceiver: $intent")
        try {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> {
                    PeriodicCalendarWorker.setup(workManager, logger)
                    GlobalScope.launch(Dispatchers.IO) {
                        val userId = accountManager.getPrimaryUserId().firstOrNull()
                        if (userId != null) {
                            handleAlarms(userId)
                        }
                    }
                }

                Intent.ACTION_MY_PACKAGE_REPLACED -> GlobalScope.launch(Dispatchers.IO) {
                    entryPoint.migrateEventMetadataToOccurrencesUseCase().execute()
                }

                INTENT_ACTION_EVENT_ALARM -> GlobalScope.launch(Dispatchers.IO) {
                    val alarmTimestamp =
                        if (intent.hasExtra(INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS)) intent.getLongExtra(
                            INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS,
                            0
                        ) else null

                    val userId = accountManager.getPrimaryUserId().firstOrNull()
                    if (userId != null) {
                        handleAlarms(userId, alarmTimestamp)
                    }
                }

                else -> {
                    logger.e("ProtonCalendarBroadcastReceiver unknown intent action: ${intent.action}")
                }
            }
        } catch (e: Exception) {
            logger.e("ProtonCalendarBroadcastReceiver, error for ${intent.action}", e)
        }
    }

    companion object {
        const val INTENT_ACTION_EVENT_ALARM = "INTENT_ACTION_EVENT_ALARM"
        const val INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS = "INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS"
    }
}
