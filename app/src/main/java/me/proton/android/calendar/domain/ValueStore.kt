package me.proton.android.calendar.domain

// TODO move this to a dedicated package

/**
 * The actual implementation has to be scoped per User ID!
 */
interface ValueStore {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun putLong(key: String, value: Long)
    fun getLong(key: String): Long?
    fun putBoolean(key: String, value: Boolean)
    fun getBoolean(key: String): Boolean?
    fun putStringInSet(setName: String, key: String, value: String)
    fun getStringFromSet(setName: String, key: String): String?
    fun putLongInSet(setName: String, key: String, value: Long)
    fun getLongFromSet(setName: String, key: String): Long?
    fun clearAll()
    fun removeKey(key: String)
    fun removeKeySet(setName: String)
    fun removeKeyFromSet(setName: String, key: String)
}

interface ValueStoreProvider {
    fun provideValueStore(userId: String): ValueStore

    companion object {
        // used for sharing global fake UserID so we don't need to scope ValueStore per actual UserID
        const val GLOBAL_VALUE_STORE_USER_ID = "GLOBAL_VALUE_STORE_USER_ID"
    }
}

object ValueKey {
    const val USER_PASSPHRASE = "USER_PASSPHRASE" // TODO deprecated!
    const val LAST_EVENT_ALARM_HANDLED_TIMESTAMP = "LAST_EVENT_ALARM_HANDLED_TIMESTAMP"
    const val LAST_SERVER_EVENT_ID = "LAST_SERVER_EVENT_ID"
    const val SEARCH_DATABASE_PASSPHRASE_BASE_64 = "SEARCH_DATABASE_PASSPHRASE_BASE_64"
    const val SEARCH_ENABLED = "SEARCH_ENABLED"
}

object ValueSet {
    const val CALENDAR_PASSPHRASE = "CALENDAR_PASSPHRASE"
    const val LAST_CALENDAR_ALARM_SYNC_SUCCESS_TIMESTAMP = "LAST_CALENDAR_ALARM_SYNC_SUCCESS_TIMESTAMP"
    const val LAST_SERVER_CALENDAR_EVENT_ID = "LAST_SERVER_CALENDAR_EVENT_ID"
    // when fetching entire Calendar, we fetched everything up to this EventID
    const val FETCHING_CALENDAR_LAST_EVENT_ID = "FETCHING_CALENDAR_LAST_EVENT_ID"
    const val FETCHING_CALENDAR_TOTAL_DOWNLOADED_COUNT = "FETCHING_CALENDAR_TOTAL_DOWNLOADED_COUNT"
}

