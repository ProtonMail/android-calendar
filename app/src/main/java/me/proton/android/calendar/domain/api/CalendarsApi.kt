package me.proton.android.calendar.domain.api

import me.proton.android.calendar.data.api.*
import me.proton.core.domain.entity.UserId

interface CalendarsApi {

    /**
     * Gets all user's calendars.
     */
    suspend fun getCalendars(userId: UserId): ApiResponse<CalendarsApiResponse>

    /**
     * Get calendar by id.
     */
    suspend fun getCalendar(userId: UserId, calendarId: String): ApiResponse<CalendarApiResponse>

    /**
     * Gets all events Metadata for given calendar, happening between timestamps in given timezone.
     */
    suspend fun getEventsMetadata(
        userId: UserId,
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String,
        type: Int,
        page: Int,
        pageSize: Int
    ): ApiResponse<EventsMetadataApiResponse>

    suspend fun getEventIdsForExport(
        userId: UserId,
        calendarId: String,
        limit: Int,
        afterId: String?
    ): ApiResponse<EventsExportIdsApiResponse>

    suspend fun getEventsForExport(
        userId: UserId,
        calendarId: String,
        pageSize: Int,
        beginId: String?
    ): ApiResponse<EventsExportApiResponse>

    suspend fun getEventsCount(
        userId: UserId,
        calendarId: String
    ): ApiResponse<EventsCountApiResponse>

    /**
     * Get single event.
     */
    suspend fun getEvent(userId: UserId, calendarId: String, eventId: String) : ApiResponse<EventApiResponse>

    /**
     * Get event attendees with comments.
     */
    suspend fun getEventAttendees(userId: UserId, calendarId: String, eventId: String, page: Int): ApiResponse<AttendeesInfoResponse>

    /**
     * Upgrades Event using AddressKeyPacket to use SharedKeyPacket (applicable for auto-added invites).
     */
    suspend fun upgradeEvent(userId: UserId, calendarId: String, eventId: String, body: UpgradeEventApiRequest): ApiResponse<UpgradeEventApiResponse>

    /**
     * Gets bootstrap for calendar setup.
     */
    suspend fun getBootstrap(userId: UserId, calendarId: String): ApiResponse<BootstrapApiResponse>

    /**
     * Deletes the Calendar.
     */
    suspend fun deleteCalendar(userId: UserId, calendarId: String): ApiResponse<StatusCodeApiResponse>

    /**
     * Deletes a calendar and creates a new one with the same name, description, color, and display.
     * Note: If calendar was shared, all members will be removed and will receive a deletion email.
     */
    suspend fun recreateCalendar(userId: UserId, calendarId: String): ApiResponse<RecreateCalendarApiResponse>

    /**
     * Gets all "active" (occuring in the future) alarms of type "DISPLAY" for given calendar.
     */
    suspend fun getAlarms(userId: UserId, calendarId: String, startTimestamp: Long, endTimestamp: Long, pageSize: Int) : ApiResponse<AlarmsApiResponse>

    /**
     * Gets all "active" (occuring in the future) alarms of type "DISPLAY" for given event.
     */
    suspend fun getEventAlarms(userId: UserId, calendarId: String, eventId: String) : ApiResponse<AlarmsApiResponse>

    suspend fun syncEvents(userId: UserId, calendarId: String, body: SyncEventsUpdateApiRequest) : ApiResponse<SyncEventsApiResponse>

    suspend fun getEventsByUid(userId: UserId, eventUid: String, page: Int, pageSize: Int) : ApiResponse<EventsByUidApiResponse>

    suspend fun updateCalendarDisplay(userId: UserId, calendarId: String, memberId: String, body: UpdateCalendarDisplayApiRequest): ApiResponse<MemberApiResponse>

    /**
     * Create calendar.
     */
    suspend fun createCalendar(userId: UserId, body: CreateCalendarApiRequest): ApiResponse<CalendarApiResponse>

    /**
     * Retrieve a list of all members associated with current user.
     */
    suspend fun getAllMembers(userId: UserId): ApiResponse<MemberListApiResponse>

    /**
     * Retrieve a list of members associated with this calendar and current user.
     */
    suspend fun getMemberList(userId: UserId, calendarId: String): ApiResponse<MemberListApiResponse>

    suspend fun updateMember(userId: UserId, calendarId: String, memberId: String, body: UpdateMemberApiRequest): ApiResponse<MemberApiResponse>

    /**
     * Sets a new calendar key and updates the encrypted passphrases for all existing members.
     */
    suspend fun setupKey(userId: UserId, calendarId: String, body: SetupKeyApiRequest): ApiResponse<SetupKeyApiResponse>

    /**
     * Retrieves all keys associated with the calendar, active or not. Available with admin permissions.
     */
    suspend fun getAllKeys(userId: UserId, calendarId: String): ApiResponse<KeysApiResponse>

    /**
     * Retrieves active keys associated with the calendar.
     */
    suspend fun getKeys(userId: UserId, calendarId: String): ApiResponse<KeysApiResponse>

    /**
     * Reenable a calendar key. Only available with admin permissions. Useful after a password reset.
     */
    suspend fun reenableKey(userId: UserId, calendarId: String, keyId: String, body: ReenableKeyApiRequest): ApiResponse<ReenableKeyApiResponse>

    /**
     * Retrieves all the calendars whose keys needs to be reset.
     */
    suspend fun getResetInfo(userId: UserId): ApiResponse<ResetInfoApiResponse>

    /**
     * Install a new key for each calendar that needs to be reset. For each calendar, the body has the same definition has the key setup bodies.
     */
    suspend fun resetCalendar(userId: UserId, body: ResetCalendarApiRequest): ApiResponse<ResetCalendarApiResponse>

    /**
     * Retrieve a list of passphrases associated with this calendar. Used for recovery. Available with admin permissions.
     */
    suspend fun getPassphrases(userId: UserId, calendarId: String): ApiResponse<PassphrasesApiResponse>

    /**
     * Retrieves the active passphrase.
     */
    suspend fun getActivePassphrase(userId: UserId, calendarId: String): ApiResponse<ActivePassphraseApiResponse>

    /**
     * Update the participation status of a given event attendee.
     */
    suspend fun updateParticipationStatus(userId: UserId, calendarId: String, eventId: String, attendeeId: String, status: Int, updateTime: Int? = null): ApiResponse<AttendeeApiResponse>

    /**
     * For an attendee to update event's personal part
     */
    suspend fun updateEventPersonalPart(userId: UserId, calendarId: String, eventId: String, body: UpdateEventPersonalPartApiRequest): ApiResponse<EventApiResponse>

    /**
     * Update calendar settings
     */
    suspend fun updateCalendarSettings(userId: UserId, calendarId: String, body: UpdateCalendarSettingsApiRequest): ApiResponse<UpdateCalendarSettingsApiResponse>

    /**
     * Get calendar settings
     */
    suspend fun getCalendarSettings(userId: UserId, calendarId: String): ApiResponse<GetCalendarSettingsApiResponse>

    /**
     * Get calendar subscription
     */
    suspend fun getCalendarSubscription(userId: UserId, calendarId: String): ApiResponse<GetCalendarSubscriptionApiResponse>

    /**
     * Get holiday calendars
     */
    suspend fun getManagedHolidayCalendars(userId: UserId): ApiResponse<GetHolidayCalendarsApiResponse>

    /**
     * Join a shared calendar
     */
    suspend fun joinCalendar(
        userId: UserId,
        calendarId: String,
        addressId: String,
        body: JoinCalendarApiRequest
    ): ApiResponse<JoinCalendarApiResponse>

    /**
     * Leave a shared calendar
     */
    suspend fun leaveSharedCalendar(
        userId: UserId,
        calendarId: String,
        memberId: String
    ): ApiResponse<StatusCodeApiResponse>

    /**
     * Leave a BE managed calendar
     */
    suspend fun leaveManagedCalendar(
        userId: UserId,
        calendarId: String
    ): ApiResponse<StatusCodeApiResponse>
}
