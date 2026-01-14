package me.proton.android.calendar.data.db

import androidx.room.*
import me.proton.android.calendar.data.entity.EventEntity
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.entity.SkeletonEventEntity

@Dao
abstract class EventsDao : BaseDao<EventEntity> {

//    lateinit var userId: String

//    @Query("SELECT * FROM events WHERE calendarId IN (:calendarIds) AND (sharedEvents LIKE '%' || :sharedEventsFieldSubstring || '%')")

    @Query("SELECT * FROM events")
    abstract suspend fun selectEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId")
    abstract fun selectEventsByCalendar(calendarId: String): List<EventEntity>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :eventId AND calendarId = :calendarId")
    abstract suspend fun selectEvent(eventId: String, calendarId: String): EventEntity?

    @Query("SELECT * FROM events WHERE id IN (:uids)")
    abstract suspend fun selectByUidIn(uids: Set<String>): List<EventEntity>

    @Query("SELECT * FROM events")
    abstract fun selectEventsFlow(): Flow<List<EventEntity>>

    @Query("SELECT ID, CalendarID, SharedEvents, ModifyTime, AddressID, Color FROM events")
    abstract fun selectSkeletonEventsFlow(): Flow<List<SkeletonEventEntity>>

    @Query("SELECT ID, CalendarID, SharedEvents, ModifyTime, AddressID, Color FROM events WHERE id IN (:ids) ORDER BY ID")
    abstract fun selectSkeletonEventsById(ids: List<String>): List<SkeletonEventEntity>

    @Query("SELECT ID, CalendarID, SharedEvents, ModifyTime, AddressID, Color FROM events WHERE calendarId = :calendarId")
    abstract fun getSkeletonEventsInCalendarFlow(calendarId: String): Flow<List<SkeletonEventEntity>>

    @Query("SELECT ID, CalendarID, SharedEvents, ModifyTime, AddressID, Color FROM events ORDER BY ID ASC LIMIT :limit OFFSET :offset")
    abstract fun selectSkeletonEventsPaginated(limit: Int, offset: Int): List<SkeletonEventEntity>

    @Query("SELECT ID, CalendarID, SharedEvents, ModifyTime, AddressID, Color FROM events WHERE calendarId = :calendarId ORDER BY ID ASC LIMIT :limit OFFSET :offset")
    abstract fun selectSkeletonEventsInCalendarPaginated(calendarId: String, limit: Int, offset: Int): List<SkeletonEventEntity>

    @Query("SELECT ID, CalendarID, SharedEvents, ModifyTime, AddressID, Color FROM events WHERE calendarId = :calendarId")
    abstract fun selectSkeletonEvents(calendarId: String): List<SkeletonEventEntity>

    @Query("SELECT COUNT(ID) FROM events")
    abstract fun skeletonEventCountFlow(): Flow<Int>

    @Query("SELECT * FROM events WHERE calendarId IN (:calendarIds)")
    abstract fun flowEvents(calendarIds: List<String>): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE calendarId IN (:calendarIds)")
    abstract suspend fun selectEvents(calendarIds: List<String>): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    abstract fun selectByIdFlow(id: String): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    abstract suspend fun selectById(id: String): EventEntity?

    @Query("SELECT * FROM events WHERE id IN (:eventIds)")
    abstract suspend fun selectAllById(eventIds: List<String>): List<EventEntity>

    @Query("SELECT * FROM events WHERE sharedEvents LIKE '%DTSTART;VALUE=DATE:%'")
    abstract suspend fun selectAllDayOnly(): List<EventEntity>

    @Query("SELECT * FROM events WHERE sharedEvents LIKE '%DTSTART;VALUE=DATE:%' AND calendarId = :calendarId")
    abstract suspend fun selectAllDayOnly(calendarId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE sharedEvents NOT LIKE '%DTSTART;VALUE=DATE:%' AND calendarId = :calendarId")
    abstract suspend fun selectPartDayOnly(calendarId: String): List<EventEntity>

    @Deprecated("Format UID with formatUidForICal")
    @Query("SELECT * FROM events WHERE sharedEvents LIKE '%UID:' || :uid || '%'")
    abstract suspend fun selectByUid(uid: String): List<EventEntity>

    @Deprecated("Format UID with formatUidForICal")
    @Query("SELECT COUNT(id) FROM events WHERE sharedEvents LIKE '%UID:' || :uid || '%'")
    abstract suspend fun countByUid(uid: String): Int

    @Transaction
    @Query("SELECT EXISTS(SELECT * FROM events WHERE id = :eventId AND calendarId = :calendarId)")
    abstract suspend fun hasEvent(eventId: String, calendarId: String): Boolean

    @Query("SELECT EXISTS(SELECT * FROM events WHERE id = :eventId AND calendarId = :calendarId AND modifyTime = :modifyTime)")
    abstract suspend fun hasEvent(eventId: String, calendarId: String, modifyTime: Long): Boolean

    @Query("SELECT EXISTS(SELECT * FROM events WHERE id = :eventId AND calendarId = :calendarId AND modifyTime > :modifyTime)")
    abstract suspend fun hasEventWithHigherModifyTime(eventId: String, calendarId: String, modifyTime: Long): Boolean

    @Query("SELECT EXISTS(SELECT * FROM events WHERE id = :eventId AND calendarId = :calendarId AND modifyTime >= :modifyTime)")
    abstract suspend fun hasEventWithHigherOrEqualModifyTime(eventId: String, calendarId: String, modifyTime: Long): Boolean

    @Query("SELECT COUNT(id) FROM events WHERE calendarId = :calendarId")
    abstract suspend fun count(calendarId: String): Int

    @Query("DELETE FROM events WHERE id IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM events WHERE calendarId = :calendarId")
    abstract suspend fun deleteAll(calendarId: String)

    @Query("DELETE FROM events")
    abstract suspend fun deleteAll()

    // TODO select for given timespan

}
