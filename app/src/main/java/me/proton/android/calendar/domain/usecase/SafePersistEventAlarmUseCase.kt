package me.proton.android.calendar.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.Logger
import javax.inject.Inject

class SafePersistEventAlarmUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
) {

    suspend operator fun invoke(eventAlarms: List<EventAlarmEntity>): UseCase.Result {
        database.inTransaction {
            eventAlarms.groupBy { Pair(it.eventId, it.calendarId) }.forEach { groupedEventAlarms ->
                if (database.eventsDao().hasEvent(groupedEventAlarms.key.first, groupedEventAlarms.key.second)) {
                    groupedEventAlarms.value.forEach { eventAlarm ->
                        try {
                            database.eventAlarmsDao().updateOrInsert(eventAlarm)
                        } catch (e: SQLiteConstraintException) {
                            // hack for different SQLite implementations formatting message differently
                            if (e.message?.contains("787") == true
                                && e.message?.contains("foreign", ignoreCase = true) == true
                                && e.message?.contains("constraint", ignoreCase = true) == true
                            ) {
                                // ignore, it means this EventAlarms' Event doesn't exist
                                logger.e("persistEventAlarm couldn't insert because ${e.message}", e)
                            } else throw e
                        }
                    }
                } else {
                    logger.i("persistEventAlarm, event doesn't exist")
                }
            }
        }
        return UseCase.Result.Success<Unit>()
    }

}
