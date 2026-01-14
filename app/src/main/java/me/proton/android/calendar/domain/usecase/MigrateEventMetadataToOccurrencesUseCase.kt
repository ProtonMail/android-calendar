package me.proton.android.calendar.domain.usecase

import android.database.SQLException
import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.core.accountmanager.domain.AccountManager
import javax.inject.Inject

class MigrateEventMetadataToOccurrencesUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val accountManager: AccountManager
) {

    companion object {
        const val WORKER_ID = "MIGRATE_EVENT_METADATA_TO_OCCURRENCES"
    }

    suspend fun execute(): UseCase.Result {
        val userId =
            accountManager.getPrimaryUserId().firstOrNull() ?: return UseCase.Result.Error("Could not obtain UserId")

        database.eventsMetadataDao().selectEventsMetadata().forEach {
            try {
                updateEventOccurrencesUseCase.execute(userId.id, it)
            } catch (e: SQLException) {
                // we don't want to interrupt entire migration process if only one update fails -- Event for this
                //  EventMetadata doesn't exist locally so we wouldn't show it in the app anyway -- it has to be
                //  fetched by one of the views
                logger.e("MigrateEventMetadataToOccurrencesUseCase: Error updating event occurrences", e)
            }
        }

        // when we run this UseCase next time, there will be no MetaData in DB so there's no need for additional checks
        //  if we should run it or not
        database.eventsMetadataDao().deleteAll()
        
        return UseCase.Result.Success<Unit>()
    }

}
