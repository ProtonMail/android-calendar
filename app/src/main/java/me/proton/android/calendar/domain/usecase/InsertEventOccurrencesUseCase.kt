package me.proton.android.calendar.domain.usecase

import android.database.sqlite.SQLiteConstraintException
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.Logger
import javax.inject.Inject

class InsertEventOccurrencesUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase
) : UseCase {

    suspend fun needsRefresh(
        userId: String,
        eventEntityMetadata: EventEntityMetadata,
    ) = database.inTransaction {
        database.eventOccurrencesDao().hasOccurrenceWithEqualOrHigherModifyTime(
            userId,
            eventEntityMetadata.calendarId,
            eventEntityMetadata.id,
            eventEntityMetadata.modifyTime,
            // rows without a startTimeZone predate the column and never refresh via modifyTime alone
            requireStartTimeZone = eventEntityMetadata.startTimeZone.isNotBlank(),
            // rows written while a zero-duration end was bumped to the next day never refresh via modifyTime alone
            requireZeroDuration = eventEntityMetadata.fullDay == 0 &&
                eventEntityMetadata.endTime == eventEntityMetadata.startTime,
        )
    }

    suspend fun execute(
        userId: String,
        eventEntityMetadata: EventEntityMetadata,
        eventOccurrenceEntities: List<EventOccurrenceEntity>,
    ): Result<Unit> {
        return runCatching {
            database.inTransaction {
                database.eventOccurrencesDao()
                    .deleteAllForEvent(userId, eventEntityMetadata.calendarId, eventEntityMetadata.id)
                database.eventOccurrencesDao().insert(*eventOccurrenceEntities.toTypedArray())
            }
        }.onFailure {
            // a FK violation is the expected race when the parent event was deleted concurrently
            val message = "UpdateEventOccurrencesUseCase failed for cal=${eventEntityMetadata.calendarId} (event likely does not exist)"
            if (it is SQLiteConstraintException) logger.w(message, it) else logger.e(message, it)
        }
    }
}
