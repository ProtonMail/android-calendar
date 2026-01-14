package me.proton.android.calendar.domain.usecase

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
            eventEntityMetadata.modifyTime
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
            logger.e("UpdateEventOccurrencesUseCase failed for cal=${eventEntityMetadata.calendarId} (event likely does not exist)", it)
        }
    }
}
