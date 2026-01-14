package me.proton.android.calendar.domain.usecase

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.android.calendar.ProtonCalendarBroadcastReceiver
import me.proton.android.calendar.common.AlarmAction
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutDuplicates
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.domain.entity.UserId
import java.time.Instant
import javax.inject.Inject

class HandleAlarmsUseCase @Inject constructor(
    private val logger: Logger,
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val showNotificationUseCase: ShowNotificationUseCase,
    private val valueStoreProvider: ValueStoreProvider
) {

    suspend fun execute(userId: UserId, alarmEpochSeconds: Long? = null): UseCase.Result {
        val nowInstant = Instant.now()

        val lastHandledTimestamp =
            valueStoreProvider.provideValueStore(userId.id).getLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP)
                ?: ((alarmEpochSeconds
                    ?: nowInstant.epochSecond) - 1) // if no alarms were ever shown, let's pretend we've shown all until 1 second ago

        val alarmsToDisplayNow =
            database.eventAlarmsDao().selectAllBetweenInclusive(lastHandledTimestamp + 1, nowInstant.epochSecond)
                .filterOutDuplicates()
                .filter { it.action == AlarmAction.DISPLAY.value }

        showNotificationUseCase.execute(alarmsToDisplayNow, userId.id)

        val maxAlarmOccurrenceSeconds =
            alarmsToDisplayNow.maxByOrNull { it.occurrence }?.occurrence ?: nowInstant.epochSecond
        valueStoreProvider.provideValueStore(userId.id)
            .putLong(ValueKey.LAST_EVENT_ALARM_HANDLED_TIMESTAMP, maxAlarmOccurrenceSeconds)

        // get next event alarms after currently shown and set system alarm to fire at that timestamp
        val alarmsToDisplayNext = database.eventAlarmsDao().selectUpcomingInclusive(maxAlarmOccurrenceSeconds + 1)
        alarmsToDisplayNext.firstOrNull()?.let {
            rescheduleSystemAlarm(Instant.ofEpochSecond(it.occurrence))
        }

        return UseCase.Result.Success<Unit>()
    }

    private fun rescheduleSystemAlarm(atInstant: Instant) {

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ProtonCalendarBroadcastReceiver::class.java).apply {
            action = ProtonCalendarBroadcastReceiver.INTENT_ACTION_EVENT_ALARM
            putExtra(ProtonCalendarBroadcastReceiver.INTENT_EXTRA_EVENT_ALARM_TIMESTAMP_SECONDS, atInstant.epochSecond)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            logger.i("HandleAlarmsUseCase: can't schedule exact alarms")
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atInstant.toEpochMilli(), pendingIntent)
        }

    }

}
