package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.model.Event
import javax.inject.Inject

class IndexEventForSearchUseCase @Inject constructor(
    private val valueStoreProvider: ValueStoreProvider,
    private val searchDatabase: SearchDatabase,
    private val transformEventUseCase: TransformEventUseCase
) {

    suspend fun execute(userId: String, eventEntities: List<EventEntity>): UseCase.Result {

        // early return if indexing is turned off
        if ((valueStoreProvider.provideValueStore(userId).getBoolean(ValueKey.SEARCH_ENABLED) == true).not()) return UseCase.Result.Success<Unit>()

        // only index Entities that are newer than what we already have in DB
        //  this saves us unnecessary decryption
        val entitiesToIndex = eventEntities.filter { eventEntity ->
            !searchDatabase.searchDao().hasEventWithHigherModifyTime(userId, eventEntity.calendarId, eventEntity.id, eventEntity.modifyTime)
        }
        coroutineScope {
            val searchEvents = entitiesToIndex.map {
                async {
                    transformEventUseCase.execute(it)?.let { event ->
                        if (event.decryptionStatus == Event.DecryptionStatus.Success) {
                            SearchEventEntity.from(userId, event)
                        } else null
                    }
                }
            }.awaitAll().filterNotNull()

            searchDatabase.searchDao().insertSearchEvents(searchEvents)
        }

        return UseCase.Result.Success<Unit>()
    }

}
