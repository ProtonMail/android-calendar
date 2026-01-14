package me.proton.android.calendar.eventmanager.listeners

import me.proton.core.data.room.db.Database
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfig

/**
 * Base EventListener class, with some helpers to avoid writing boilerplate in every EventListener class
 */
abstract class CalendarBaseEventListener<Key: Any, Type: Any>(
    protected val db: Database,
): EventListener<Key, Type>() {

    /** This method will be called when either [onCreate] or [onUpdate] are called */
    open suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<Type>) {}

    override suspend fun onCreate(config: EventManagerConfig, entities: List<Type>) {
        onCreateOrUpdate(config, entities)
    }

    override suspend fun onUpdate(config: EventManagerConfig, entities: List<Type>) {
        onCreateOrUpdate(config, entities)
    }

    /** Common inTransaction method for all EventListeners */
    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.inTransaction(block)
}
