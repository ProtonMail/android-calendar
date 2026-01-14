package me.proton.android.calendar.domain

import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event

interface EventDecryptor {

    /**
     * Sets all available Calendars for Decryptor to use for Event caching.
     */
    suspend fun setCalendars(calendars: List<Calendar>)

    suspend fun decrypt(eventEntity: EventEntity): Event?

    /**
     * Decrypts EventEntity and potentially connects to API, call only from appropriate coroutine contexts.
     */
    suspend fun decryptAllowingApiCall(eventEntity: EventEntity): Event?

    suspend fun clearCache()

    suspend fun getFromCache(eventId: String, calendarId: String, modifyTime: Long): Event?

}
