package me.proton.android.calendar.domain

import biweekly.property.RecurrenceId
import kotlinx.coroutines.flow.Flow
import me.proton.android.calendar.data.api.AlarmsApiResponse
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.UserAddress
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Manages all Calendars, Events, Members, Passphrases etc.
 */
// TODO move to separate package?
interface CalendarsRepository {

    data class EventsWindow(
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val timeZoneId: String
    )

    suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<InitingState>

    suspend fun shutdown()

    suspend fun clearSearchDatabase()

    // calendars
    suspend fun countCalendars(): Int

    suspend fun hasHolidayCalendars(userId: String): Boolean

    suspend fun selectCalendarEntity(calendarId: String): CalendarEntity?

    suspend fun selectCalendar(calendarId: String): Calendar?

    suspend fun selectCalendarEntities(userId: String): List<CalendarEntity>

    suspend fun selectUserCalendars(userId: String): List<Calendar>

    suspend fun selectUserPersonalCalendars(userId: String): List<Calendar>

    suspend fun selectOtherCalendars(userId: String): List<Calendar>

    suspend fun selectAllCalendars(userId: String): List<Calendar>

    suspend fun selectActiveUserCalendars(userId: String): List<Calendar>

    suspend fun selectDisabledUserCalendars(userId: String): List<Calendar>

    suspend fun selectInactiveUserCalendars(userId: String): List<Calendar>

    suspend fun selectSubscribedCalendars(userId: String): List<Calendar>

    fun flowVisibleCalendarIds(userId: String): Flow<List<String>>

    fun flowActiveUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowDisabledUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowInactiveUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowAllCalendars(userId: String): Flow<List<Calendar>>

    fun flowUserCalendars(userId: String): Flow<List<Calendar>>

    fun flowUserPersonalCalendars(userId: String): Flow<List<Calendar>>

    fun flowSharedCalendars(userId: String): Flow<List<Calendar>>

    fun flowSubscribedCalendars(userId: String): Flow<List<Calendar>>

    fun flowHolidayCalendars(userId: String): Flow<List<Calendar>>

    suspend fun persistCalendar(userId: String, calendar: CalendarEntity)

    suspend fun deleteCalendars(userId: String)

    suspend fun deleteCalendarById(id: String)

    suspend fun refreshCalendars(userId: UserId): Boolean

    suspend fun refreshCalendars(userId: UserId, calendarIds: List<String>)

    suspend fun fetchCalendars(userId: UserId): List<Calendar>?

    suspend fun fetchCalendarEntities(userId: UserId): List<CalendarEntity>?

    /**
     * Fetches and combines MemberEntity with supplied CalendarEntities
     */
    suspend fun fetchMembersToCalendarEntities(userId: UserId, calendarEntities: List<CalendarEntity>): List<Calendar>

    suspend fun fetchMembers(userId: UserId, calendarId: String): List<MemberEntity>?

    suspend fun fetchCalendar(userId: UserId, calendarId: String): Calendar?

    suspend fun fetchCalendarEntity(userId: UserId, calendarId: String): CalendarEntity?

    suspend fun fetchManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>?

    suspend fun getManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>?

    suspend fun getManagedHolidayCalendarById(userId: UserId, calendarId: String): ManagedHolidayCalendarEntity?

    suspend fun refreshManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>?

    suspend fun refreshVisibleManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>?

    suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean

    suspend fun updateCalendarDisplay(calendarId: String, display: Boolean)

    /**
     * Request Events to be pushed to observers and also fetched from API if possible.
     */
    suspend fun fetchEvents(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        force: Boolean = false,
    )

    sealed class GetEventsResult<out T> {
        object InProgress: GetEventsResult<Nothing>()
        data class Success<T>(val events: List<T>, val fullyLoaded: Boolean): GetEventsResult<T>()
        data class Exception(val throwable: Throwable): GetEventsResult<Nothing>()
    }

    suspend fun transformAllowingApiCall(eventId: String, calendarId: String): Event?

    suspend fun expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
        originalEvent: Event,
        eventsSharingUid: List<Event>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        userEmails: List<String>,
        isFreeUser: Boolean
    ): List<UiEvent>?

    fun getSearchEvents(
        userId: String,
        searchTerm: String
    ): Flow<GetEventsResult<Event>>

    suspend fun deleteAllSearchEvents(userId: String)

    suspend fun deleteAllSearchEventsInCalendar(userId: String, calendarId: String)

    suspend fun deleteSearchEventsForEvents(userId: String, calendarId: String, eventIds: List<String>)

    suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean

    suspend fun eventExistsOnServer(userId: UserId, eventId: String, calendarId: String): Boolean?

    suspend fun shouldFetchEvent(userId: UserId, metadata: EventEntityMetadata): Boolean

    suspend fun hasCalendar(calendarId: String, ): Boolean

    fun expandDbEvent(event: Event, allEvents: List<Event>, toDateTime: ZonedDateTime): List<Event>

    suspend fun selectEventEntity(eventId: String): EventEntity?

    /**
     * Root Event is the original recurring event for single-edited event with RECURRENCE-ID. May be the event itself
     * if there is only one event with this UID.
     */
    suspend fun selectRootEventEntity(eventUid: String): EventEntity?

    suspend fun hasSingleEdits(userId: UserId, eventUid: String): Boolean?

    suspend fun getSingleEdits(userId: UserId, eventUid: String, stopAfter: ZonedDateTime? = null,  timeZoneId: String? = null): List<Event>?

    suspend fun isOrphanSingleEdit(userId: UserId, eventUid: String): Boolean?

    suspend fun isStandaloneSingleEdit(userId: UserId, eventUid: String, eventRecurrenceId: RecurrenceId, timeZoneId: String): Boolean?

    suspend fun deleteEventsMetadataByEventIds(eventIds: List<String>)

    suspend fun deleteEventsMetadataByCalendarId(calendarId: String)

    suspend fun persistEvents(vararg events: EventEntity)

    suspend fun persistEventsMetadata(vararg eventsMetadata: EventEntityMetadata)

    suspend fun deleteEventsById(calendarId: String, ids: List<String>)

    suspend fun deleteAllEvents()

    suspend fun deleteAllEvents(calendarId: String)

    suspend fun getEventsByUid(userId: UserId, eventUid: String): ApiResponse<EventsByUidApiResponse>

    suspend fun selectEventEntitiesByUids(uids: Collection<String>): Map<String, List<EventEntity>>

    suspend fun fetchEventById(userId: UserId, calendarId: String, eventId: String): ApiResponse<EventApiResponse>

    // calendar keys
    suspend fun selectCalendarKeys(calendarId: String): List<CalendarKeyEntity>

    suspend fun persistCalendarKey(calendarKey: CalendarKeyEntity) // calendarId is already there

    suspend fun deleteAllCalendars()

    suspend fun deleteCalendarKeyById(id: String)

    suspend fun deleteCalendarKeyByCalendarId(calendarId: String)

    // passphrases
    suspend fun selectCalendarPassphrases(calendarId: String): List<PassphraseEntity>

    suspend fun persistCalendarPassphrase(calendarPassphrase: PassphraseEntity)

    suspend fun deleteCalendarPassphraseById(id: String)

    suspend fun deleteCalendarPassphrases(calendarId: String)

    // members
    suspend fun selectCalendarMembers(calendarId: String): List<MemberEntity>

    suspend fun selectCalendarUserMember(calendarId: String): MemberEntity?

    suspend fun selectMemberById(memberId: String): MemberEntity?

    suspend fun persistMember(member: MemberEntity) // calendarId is already there

    suspend fun deleteMemberById(id: String)

    // calendar settings
    suspend fun selectCalendarSettings(calendarId: String): CalendarSettingsEntity?

    suspend fun persistCalendarSettings(calendarSettings: CalendarSettingsEntity) // calendarId is already there

    suspend fun updateCalendarSettings(calendarSettings: CalendarSettingsEntity) // calendarId is already there

    suspend fun deleteCalendarSettingsById(id: String)

    suspend fun deleteCalendarSettingsByCalendarId(calendarId: String)

    // calendar subscription
    suspend fun selectCalendarSubscription(calendarId: String): CalendarSubscriptionEntity?

    suspend fun selectCalendarSubscriptions(calendarId: String): List<CalendarSubscriptionEntity>

    fun flowCalendarSubscriptions(): Flow<List<CalendarSubscriptionEntity>>

    suspend fun persistCalendarSubscription(calendarSubscription: CalendarSubscriptionEntity) // calendarId is already there

    suspend fun deleteCalendarSubscriptionByCalendarId(calendarId: String)

    // calendar user settings
    suspend fun selectCalendarUserSettings(userId: String): CalendarUserSettingsEntity?

    suspend fun updateCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String, autoDetectPrimaryTimezone: Int)

    suspend fun selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Int?

    fun flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Flow<Int?>

    suspend fun updateCalendarUserSettingsDisplayWeekNumber(userId: String, displayWeekNumber: Int)

    suspend fun selectCalendarUserSettingsDisplayWeekNumber(userId: String): Int?

    fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int?>

    fun flowCalendarUserSettingsAutoImportInvite(userId: String): Flow<Int?>

    suspend fun updateCalendarUserDefaultCalendarId(userId: String, defaultCalendarId: String)

    suspend fun updateCalendarUserAutoImportInvite(userId: String, autoImportInvite: Boolean)

    fun flowCalendarUserDefaultCalendarId(userId: String): Flow<String?>

    suspend fun selectCalendarUserSettingsPrimaryTimezone(userId: String): String?

    fun flowCalendarUserSettingsPrimaryTimezone(userId: String): Flow<String?>

    suspend fun persistCalendarUserSettings(userId: String, calendarUserSettings: CalendarUserSettingsEntity)

    suspend fun deleteCalendarUserSettingsByUserId(userId: String)

    suspend fun getDefaultCalendarIdWithFallback(userId: String, allowShared: Boolean): String?

    suspend fun getDefaultCalendarId(userId: String): String?

    suspend fun fetchEventAlarms(userId: UserId, calendarId: String, eventId: String): ApiResponse<AlarmsApiResponse>

    suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>>

    // event alarms
    suspend fun selectEventAlarm(eventAlarmId: String): EventAlarmEntity?

    /**
     * Selects upcoming EventAlarms that should be shown at [timestampSeconds] or the nearest possible timestamp.
     */
    suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity>

    /**
     * Selects EventAlarms that should be shown between [timestampSecondsFrom] and [timestampSecondsTo] inclusive.
     */
    suspend fun selectAllEventAlarmsBetween(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity>

    suspend fun deleteEventAlarmById(id: String)

    suspend fun deleteEventAlarmsForEvent(eventId: String)

    suspend fun deleteEventAlarmsByEventIdAndOccurrence(eventId: String, occurrence: Long)

    suspend fun deleteAllEventAlarmsByCalendar(calendarId: String)

    suspend fun getAddressForMember(
        userId: UserId,
        addressId: String?,
        memberId: String,
        canonicalEmail: String,
        addresses: List<UserAddress>? = null
    ): UserAddress?

    fun getUserMember(userAddresses: List<UserAddress>, members: List<MemberEntity>): MemberEntity?

    suspend fun <T : Any> ApiResponse<T>.pingServerIfNeeded(userId: UserId): ApiResponse<T>
    suspend fun pingServer(userId: UserId)

    fun getDisplayServerDownBannerFlow(): Flow<Boolean>
    fun hideServerDownBanner()

    val fetchingState: Flow<FetchingState>

    sealed class FetchingState {
        object NotNeeded : FetchingState()
        object Fetching : FetchingState()
        object Finished : FetchingState()
    }

    sealed class InitingState {
        object Initing : InitingState()
        object Finished : InitingState()
        object Error : InitingState()
    }
}
