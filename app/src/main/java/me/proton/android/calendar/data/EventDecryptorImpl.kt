package me.proton.android.calendar.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class EventDecryptorImpl @Inject constructor(
    private val transformEventUseCase: TransformEventUseCase,
    private val database: AppDatabase,
    private val json: Json
): EventDecryptor {

    private data class CacheKey(
        val eventId: String,
        val calendarId: String,
        val modifyTime: Long,
    )

    private val cache = ConcurrentHashMap<CacheKey, Event>()
    private val eventsMutex = Mutex()

    private val cachedCalendars = ConcurrentHashMap<String, Calendar>()

    override suspend fun setCalendars(calendars: List<Calendar>) {
        cachedCalendars.putAll(calendars.associateBy { it.id })
    }

    private suspend fun findCalendar(id: String): Calendar? =
        cachedCalendars[id] ?: database.calendarsDao()
            .selectById(id)?.joinToCalendar(database, json)?.also {
                cachedCalendars.putIfAbsent(id, it)
            }

    override suspend fun decrypt(eventEntity: EventEntity): Event? {
        val cal = findCalendar(eventEntity.calendarId) ?: return null
        val key = CacheKey(eventEntity.id, eventEntity.calendarId, eventEntity.modifyTime)

        cache.computeIfPresent(key) { _, ev ->
            if (ev.calendar == cal) ev else Event.from(ev, calendar = cal)
        }?.let { return it }

        val dec = transformEventUseCase.execute(eventEntity) ?: return null
        return cache.putIfAbsent(key, dec) ?: dec
    }

    override suspend fun decryptAllowingApiCall(eventEntity: EventEntity): Event? {
        // we don't care about cache value and force decrypting again
        val decryptedEvent = transformEventUseCase.execute(eventEntity, allowApiCall = true)

        eventsMutex.withLock {
            return if (decryptedEvent != null) {
                val cacheKey = CacheKey(eventEntity.id, eventEntity.calendarId, eventEntity.modifyTime)
                cache[cacheKey] = decryptedEvent
                decryptedEvent
            } else null
        }
    }

    override suspend fun clearCache() = eventsMutex.withLock {
        cache.clear()
    }

    override suspend fun getFromCache(eventId: String, calendarId: String, modifyTime: Long): Event? {
        val cal = findCalendar(calendarId) ?: return null
        val key = CacheKey(eventId, calendarId, modifyTime)
        return cache.compute(key) { _, cur ->
            cur ?: return@compute null
            if (cur.calendar == cal) cur else Event.from(cur, calendar = cal)
        }
    }
}
