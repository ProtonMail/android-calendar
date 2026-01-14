package me.proton.android.calendar.data.api

import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.common.API_VERSION_CALENDAR
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarKeyEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.CalendarSubscriptionEntity
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.data.entity.PassphraseEntity
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Event
import me.proton.core.domain.entity.UserId
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import javax.inject.Inject

interface CalendarsApiService : BaseRetrofitApi {

    @GET("calendar/$API_VERSION_CALENDAR")
    suspend fun getCalendars(@Query("Page") page: Int = 0, @Query("PageSize") pageSize: Int = 100): CalendarsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun getCalendar(@Path("calendarId") calendarId: String): CalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events")
    suspend fun getEvents(
        @Path("calendarId") calendarId: String,
        @Query("Start") startTimestamp: Long,
        @Query("End") endTimestamp: Long,
        @Query("Timezone") timezone: String,
        @Query("Type") type: Int,
        @Query("Page") page: Int,
        @Query("PageSize") pageSize: Int
    ): EventsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events")
    suspend fun getEventsMetadata(
        @Path("calendarId") calendarId: String,
        @Query("Start") startTimestamp: Long,
        @Query("End") endTimestamp: Long,
        @Query("Timezone") timezone: String,
        @Query("Type") type: Int,
        @Query("Page") page: Int,
        @Query("PageSize") pageSize: Int,
        @Query("MetaDataOnly") metaDataOnly: Int = 1
    ): EventsMetadataApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/ids")
    suspend fun getEventIdsForExport(
        @Path("calendarId") calendarId: String,
        @Query("Limit") limit: Int,
        @Query("AfterID") afterId: String?
    ): EventsExportIdsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events")
    suspend fun getEventsForExport(
        @Path("calendarId") calendarId: String,
        @Query("PageSize") pageSize: Int,
        @Query("BeginID") beginId: String?
    ): EventsExportApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/count")
    suspend fun getEventsCount(
        @Path("calendarId") calendarId: String
    ): EventsCountApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}")
    suspend fun getEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : EventApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/attendees")
    suspend fun getEventAttendees(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String, @Query("Page") page: Int) : AttendeesInfoResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/upgrade")
    suspend fun upgradeEvent(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String, @Body body: UpgradeEventApiRequest): UpgradeEventApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/bootstrap")
    suspend fun getBootstrap(@Path("calendarId") calendarId: String): BootstrapApiResponse

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}")
    suspend fun deleteCalendar(@Path("calendarId") calendarId: String): StatusCodeApiResponse

    @POST("calendar/$API_VERSION_CALENDAR/{calendarId}/recreate")
    suspend fun recreateCalendar(@Path("calendarId") calendarId: String): RecreateCalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/alarms")
    suspend fun getAlarms(@Path("calendarId") calendarId: String, @Query("Start") startTimestamp: Long, @Query("End") endTimestamp: Long, @Query("PageSize") pageSize: Int) : AlarmsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/alarms")
    suspend fun getEventAlarms(@Path("calendarId") calendarId: String, @Path("eventId") eventId: String) : AlarmsApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/sync")
    suspend fun syncEvents(@Path("calendarId") calendarId: String, @Body body: SyncEventsUpdateApiRequest) : SyncEventsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/events")
    suspend fun getEventsByUid(@Query("UID") eventUid: String, @Query("Page") page: Int, @Query("PageSize") pageSize: Int) : EventsByUidApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/members/{memberId}")
    suspend fun updateCalendarDisplay(@Path("calendarId") calendarId: String, @Path("memberId") memberId: String, @Body body: UpdateCalendarDisplayApiRequest) : MemberApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/members/{memberId}")
    suspend fun updateMember(@Path("calendarId") calendarId: String, @Path("memberId") memberId: String, @Body body: UpdateMemberApiRequest) : MemberApiResponse

    @POST("calendar/$API_VERSION_CALENDAR")
    suspend fun createCalendar(@Body body: CreateCalendarApiRequest) : CalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/members")
    suspend fun getAllMembers() : MemberListApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/members")
    suspend fun getMemberList(@Path("calendarId") calendarId: String) : MemberListApiResponse

    @POST("calendar/$API_VERSION_CALENDAR/{calendarId}/keys")
    suspend fun setupKey(@Path("calendarId") calendarId: String, @Body body: SetupKeyApiRequest) : SetupKeyApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/keys/all")
    suspend fun getAllKeys(@Path("calendarId") calendarId: String) : KeysApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/keys")
    suspend fun getKeys(@Path("calendarId") calendarId: String) : KeysApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/keys/reset")
    suspend fun getResetInfo() : ResetInfoApiResponse

    @POST("calendar/$API_VERSION_CALENDAR/keys/reset")
    suspend fun resetCalendar(@Body body: ResetCalendarApiRequest) : ResetCalendarApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/passphrases")
    suspend fun getPassphrases(@Path("calendarId") calendarId: String) : PassphrasesApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/passphrase")
    suspend fun getActivePassphrase(@Path("calendarId") calendarId: String) : ActivePassphraseApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/keys/{keyId}")
    suspend fun reenableKey(@Path("calendarId") calendarId: String, @Path("keyId") keyId: String, @Body body: ReenableKeyApiRequest) : ReenableKeyApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/attendees/{attendeeId}")
    suspend fun updateParticipationStatus(@Path("calendarId") calendarId: String,
                                          @Path("eventId") eventId: String,
                                          @Path("attendeeId") attendeeId: String,
                                          @Body body: UpdateParticipationStatusApiRequest) : AttendeeApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/events/{eventId}/personal")
    suspend fun updateEventPersonalPart(@Path("calendarId") calendarId: String,
                                        @Path("eventId") eventId: String,
                                        @Body body: UpdateEventPersonalPartApiRequest) : EventApiResponse

    @PUT("calendar/$API_VERSION_CALENDAR/{calendarId}/settings")
    suspend fun updateCalendarSettings(
        @Path("calendarId") calendarId: String,
        @Body body: UpdateCalendarSettingsApiRequest
    ) : UpdateCalendarSettingsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/settings")
    suspend fun getCalendarSettings(
        @Path("calendarId") calendarId: String
    ) : GetCalendarSettingsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/subscription")
    suspend fun getCalendarSubscription(
        @Path("calendarId") calendarId: String
    ) : GetCalendarSubscriptionApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/directory?Type=2") // The type ensures that only holiday calendars are returned.
    suspend fun getManagedHolidayCalendars() : GetHolidayCalendarsApiResponse

    @POST("calendar/$API_VERSION_CALENDAR/{calendarId}/invitations/{addressId}/join")
    suspend fun joinCalendar(
        @Path("calendarId") calendarId: String,
        @Path("addressId") addressId: String,
        @Body body: JoinCalendarApiRequest
    ) : JoinCalendarApiResponse

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}/members/{memberId}")
    suspend fun leaveSharedCalendar(
        @Path("calendarId") calendarId: String,
        @Path("memberId") memberId: String
    ) : StatusCodeApiResponse

    @DELETE("calendar/$API_VERSION_CALENDAR/{calendarId}/managed")
    suspend fun leaveManagedCalendar(
        @Path("calendarId") calendarId: String
    ) : StatusCodeApiResponse
}

class CalendarsApiImpl @Inject constructor(private val apiProvider: ApiProvider) : CalendarsApi {

    override suspend fun getCalendars(userId: UserId): ApiResponse<CalendarsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getCalendars()
        }.toApiResponse()

    override suspend fun getCalendar(userId: UserId, calendarId: String): ApiResponse<CalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getCalendar(calendarId)
        }.toApiResponse()

    override suspend fun getEventsMetadata(
        userId: UserId,
        calendarId: String,
        startTimestamp: Long,
        endTimestamp: Long,
        timezone: String,
        type: Int,
        page: Int,
        pageSize: Int
    ): ApiResponse<EventsMetadataApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEventsMetadata(
            calendarId,
            startTimestamp,
            endTimestamp,
            timezone,
            type,
            page,
            pageSize
        )
    }.toApiResponse()

    override suspend fun getEventIdsForExport(
        userId: UserId,
        calendarId: String,
        limit: Int,
        afterId: String?
    ): ApiResponse<EventsExportIdsApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEventIdsForExport(
            calendarId,
            limit,
            afterId
        )
    }.toApiResponse()

    override suspend fun getEventsForExport(
        userId: UserId,
        calendarId: String,
        pageSize: Int,
        beginId: String?
    ): ApiResponse<EventsExportApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEventsForExport(
            calendarId,
            pageSize,
            beginId
        )
    }.toApiResponse()

    override suspend fun getEventsCount(userId: UserId, calendarId: String): ApiResponse<EventsCountApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getEventsCount(
                calendarId
            )
        }.toApiResponse()

    override suspend fun getEvent(
        userId: UserId,
        calendarId: String,
        eventId: String
    ): ApiResponse<EventApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEvent(calendarId, eventId)
    }.toApiResponse()

    override suspend fun getEventAttendees(
        userId: UserId,
        calendarId: String,
        eventId: String,
        page: Int
    ): ApiResponse<AttendeesInfoResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getEventAttendees(calendarId, eventId, page)
    }.toApiResponse()

    override suspend fun upgradeEvent(
        userId: UserId,
        calendarId: String,
        eventId: String,
        body: UpgradeEventApiRequest
    ): ApiResponse<UpgradeEventApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        upgradeEvent(calendarId, eventId, body)
    }.toApiResponse()

    override suspend fun getBootstrap(userId: UserId, calendarId: String): ApiResponse<BootstrapApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getBootstrap(calendarId)
        }.toApiResponse()

    override suspend fun deleteCalendar(userId: UserId, calendarId: String): ApiResponse<StatusCodeApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            deleteCalendar(calendarId)
        }.toApiResponse()

    override suspend fun recreateCalendar(userId: UserId, calendarId: String): ApiResponse<RecreateCalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            recreateCalendar(calendarId)
        }.toApiResponse()

    override suspend fun getAlarms(userId: UserId, calendarId: String, startTimestamp: Long, endTimestamp: Long, pageSize: Int): ApiResponse<AlarmsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getAlarms(calendarId, startTimestamp, endTimestamp, pageSize)
        }.toApiResponse()

    override suspend fun getEventAlarms(
        userId: UserId,
        calendarId: String,
        eventId: String
    ): ApiResponse<AlarmsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getEventAlarms(calendarId, eventId)
        }.toApiResponse()

    override suspend fun syncEvents(userId: UserId, calendarId: String, body: SyncEventsUpdateApiRequest): ApiResponse<SyncEventsApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            syncEvents(calendarId, body)
        }.toApiResponse()

    override suspend fun getEventsByUid(userId: UserId, eventUid: String, page: Int, pageSize: Int): ApiResponse<EventsByUidApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getEventsByUid(eventUid, page, pageSize)
        }.toApiResponse()

    override suspend fun updateCalendarDisplay(userId: UserId, calendarId: String, memberId: String, body: UpdateCalendarDisplayApiRequest): ApiResponse<MemberApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            updateCalendarDisplay(calendarId, memberId, body)
        }.toApiResponse()

    override suspend fun createCalendar(userId: UserId, body: CreateCalendarApiRequest): ApiResponse<CalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            createCalendar(body)
        }.toApiResponse()

    override suspend fun getAllMembers(userId: UserId): ApiResponse<MemberListApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getAllMembers()
        }.toApiResponse()

    override suspend fun getMemberList(userId: UserId, calendarId: String): ApiResponse<MemberListApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getMemberList(calendarId)
        }.toApiResponse()

    override suspend fun setupKey(userId: UserId, calendarId: String, body: SetupKeyApiRequest): ApiResponse<SetupKeyApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            setupKey(calendarId, body)
        }.toApiResponse()

    override suspend fun updateMember(userId: UserId, calendarId: String, memberId: String, body: UpdateMemberApiRequest): ApiResponse<MemberApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            updateMember(calendarId, memberId, body)
        }.toApiResponse()

    override suspend fun getAllKeys(userId: UserId, calendarId: String): ApiResponse<KeysApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getAllKeys(calendarId)
        }.toApiResponse()

    override suspend fun getKeys(userId: UserId, calendarId: String): ApiResponse<KeysApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getKeys(calendarId)
        }.toApiResponse()

    override suspend fun reenableKey(userId: UserId, calendarId: String, keyId: String, body: ReenableKeyApiRequest): ApiResponse<ReenableKeyApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            reenableKey(calendarId, keyId, body)
        }.toApiResponse()

    override suspend fun getResetInfo(userId: UserId): ApiResponse<ResetInfoApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getResetInfo()
        }.toApiResponse()

    override suspend fun resetCalendar(userId: UserId, body: ResetCalendarApiRequest): ApiResponse<ResetCalendarApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            resetCalendar(body)
        }.toApiResponse()

    override suspend fun getPassphrases(userId: UserId, calendarId: String): ApiResponse<PassphrasesApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getPassphrases(calendarId)
        }.toApiResponse()

    override suspend fun getActivePassphrase(userId: UserId, calendarId: String): ApiResponse<ActivePassphraseApiResponse> =
        apiProvider.get<CalendarsApiService>(userId).invoke {
            getActivePassphrase(calendarId)
        }.toApiResponse()

    override suspend fun updateParticipationStatus(
        userId: UserId,
        calendarId: String,
        eventId: String,
        attendeeId: String,
        status: Int,
        updateTime: Int?
    ): ApiResponse<AttendeeApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        updateParticipationStatus(calendarId, eventId, attendeeId, UpdateParticipationStatusApiRequest(
            status, updateTime ?: Instant.now().epochSecond.toInt())
        )
    }.toApiResponse()

    override suspend fun updateEventPersonalPart(
        userId: UserId,
        calendarId: String,
        eventId: String,
        body: UpdateEventPersonalPartApiRequest
    ): ApiResponse<EventApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        updateEventPersonalPart(calendarId, eventId, body)
    }.toApiResponse()

    override suspend fun updateCalendarSettings(
        userId: UserId,
        calendarId: String,
        body: UpdateCalendarSettingsApiRequest
    ): ApiResponse<UpdateCalendarSettingsApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        updateCalendarSettings(calendarId, body)
    }.toApiResponse()

    override suspend fun getCalendarSettings(
        userId: UserId,
        calendarId: String
    ): ApiResponse<GetCalendarSettingsApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getCalendarSettings(calendarId)
    }.toApiResponse()

    override suspend fun getCalendarSubscription(
        userId: UserId,
        calendarId: String
    ): ApiResponse<GetCalendarSubscriptionApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getCalendarSubscription(calendarId)
    }.toApiResponse()

    override suspend fun getManagedHolidayCalendars(
        userId: UserId
    ): ApiResponse<GetHolidayCalendarsApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        getManagedHolidayCalendars()
    }.toApiResponse()

    override suspend fun joinCalendar(
        userId: UserId,
        calendarId: String,
        addressId: String,
        body: JoinCalendarApiRequest
    ): ApiResponse<JoinCalendarApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        joinCalendar(calendarId, addressId, body)
    }.toApiResponse()

    override suspend fun leaveSharedCalendar(
        userId: UserId,
        calendarId: String,
        memberId: String
    ): ApiResponse<StatusCodeApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        leaveSharedCalendar(calendarId, memberId)
    }.toApiResponse()

    override suspend fun leaveManagedCalendar(
        userId: UserId,
        calendarId: String
    ): ApiResponse<StatusCodeApiResponse> = apiProvider.get<CalendarsApiService>(userId).invoke {
        leaveManagedCalendar(calendarId)
    }.toApiResponse()
}

@Serializable
data class CalendarsApiResponse(
    @SerialName("Calendars")
    val calendars: List<CalendarEntity>
)

@Serializable
data class EventsApiResponse(
    @SerialName("Events")
    val events: List<EventResponse>,
    @SerialName("More")
    val more: Int
)

@Serializable
data class EventsMetadataApiResponse(
    @SerialName("Events")
    val events: List<EventEntityMetadata>,
    @SerialName("More")
    val more: Int
)

@Serializable
data class EventsExportIdsApiResponse(
    @SerialName("IDs")
    val events: List<String>
)

@Serializable
data class EventsExportApiResponse(
    @SerialName("Events")
    val events: List<EventResponse>,
    @SerialName("Total")
    val total: Int
)

@Serializable
data class EventsCountApiResponse(
    @SerialName("Total")
    val total: Int
)

@Serializable
data class EventApiResponse(
    @SerialName("Event")
    val event: EventResponse
)

@Serializable
data class SyncEventsUpdateApiRequest(
    @SerialName("IsImport")
    val isImport: Int = 0,
    @SerialName("Events")
    val events: List<SyncEventContainer>
)

@Serializable
data class UpgradeEventApiRequest(
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String
)

@Serializable
data class UpgradeEventApiResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Event")
    val event: EventResponse
): BaseApiResponse()

@Serializable
data class UpdateMemberApiRequest(
    @SerialName("Color")
    val color: String? = null,
    @SerialName("Display")
    val display: Int? = null,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("Description")
    val description: String? = null
)

@Serializable
data class UpdateCalendarDisplayApiRequest(
    @SerialName("Display")
    val display: Int
)

@Serializable
data class CreateCalendarApiRequest(
    @SerialName("Name")
    val name: String,
    @SerialName("Description")
    val description: String,
    @SerialName("AddressID")
    val addressId: String,
    @SerialName("Color")
    val color: String,
    @SerialName("Display")
    val display: Int
)

@Serializable
data class CalendarApiResponse(
    @SerialName("Calendar")
    val calendar: CalendarEntity
)

@Serializable
data class MemberApiResponse(
    @SerialName("Member")
    val member: MemberEntity
)

@Serializable
data class MemberListApiResponse(
    @SerialName("Members")
    val members: List<MemberEntity>
)

@Serializable
data class SetupKeyApiRequest(
    @SerialName("PrivateKey")
    val privateKey: String,
    @SerialName("Signature")
    val signature: String,
    @SerialName("AddressID")
    val addressId: String,
    @SerialName("Passphrase")
    val passphrase: PassphraseApiRequest,
)

@Serializable
data class PassphraseApiRequest(
    @SerialName("DataPacket")
    val dataPacket: String,
    @SerialName("KeyPackets")
    val keyPackets: Map<String, String>
)

@Serializable
data class SetupKeyApiResponse(
    @SerialName("Key")
    val calendarKey: CalendarKeyEntity
)

@Serializable
data class ResetInfoApiResponse(
    @SerialName("Calendars")
    val calendars: List<ResetInfoCalendar>
)

@Serializable
data class ResetInfoCalendar(
    @SerialName("ID")
    val id: String,
    @SerialName("Members")
    val members: Map<String, String>
)

@Serializable
data class ResetCalendarApiRequest(
    @SerialName("CalendarKeys")
    val calendarKeys: Map<String, SetupKeyApiRequest>
)


// TODO container for CREATE LINKED by adding SharedEventID and UID

@Serializable
sealed class SyncEventContainer

@Serializable
data class SyncEventCreateContainer(
    @SerialName("Event")
    val event: SyncEvent,
    @SerialName("Overwrite")
    val overwrite: Int = 0
) : SyncEventContainer()

@Serializable
data class SyncEventUpdateContainer(
    @SerialName("ID")
    val id: String,
    @SerialName("Event")
    val event: SyncEvent
) : SyncEventContainer()

@Serializable
data class SyncEventDeleteContainer(
    @SerialName("ID")
    val id: String,
    @SerialName("DeletionReason")
    val deletionReason: Int
) : SyncEventContainer()

@Serializable
data class SyncEvent(
    @SerialName("Permissions")
    val permissions: Int? = null,
    @SerialName("IsOrganizer")
    val isOrganizer: Int, // Default value is 1
    @SerialName("CalendarKeyPacket")
    val calendarKeyPacket: String? = null,
    @SerialName("CalendarEventContent")
    val calendarEventContent: List<Event.EventPart.Calendar>? = null,
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String? = null,
    @SerialName("SharedEventContent")
    val sharedEventContent: List<Event.EventPart.Shared>? = null,
    @SerialName("AttendeesEventContent")
    val attendeesEventContent: List<Event.EventPart.Attendee>? = null,
    @SerialName("Attendees")
    val attendees: List<Event.AttendeeStatusEvent>? = null,
    @SerialName("AddedProtonAttendees")
    val addedProtonAttendees: List<Event.AddedAttendee>? = null,
    @SerialName("SharedEventID")
    val sharedEventId: String? = null,
    @SerialName("UID")
    val uid: String? = null,
    @SerialName("SourceCalendarID") // original Calendar ID when creating new Event for "change calendar"
    val sourceCalendarId: String? = null,
    @SerialName("Notifications") // notifications that used to be in the PersonalEventContent
    val notifications: List<NotificationEntity>? = null,
    @SerialName("Color")
    val color: String? = null
)

@Serializable
data class BootstrapApiResponse(
    @SerialName("Keys")
    val keys: List<CalendarKeyEntity>,
    @SerialName("Passphrase")
    val passphrase: PassphraseEntity,
    @SerialName("Members")
    val members: List<MemberEntity>,
    @SerialName("CalendarSettings")
    val calendarSettings: CalendarSettingsEntity, // settings specific to calendar, not user
    @SerialName("CalendarSubscription")
    val calendarSubscriptionEntity: CalendarSubscriptionEntity? = null // contains extra properties for subscribed calendars
)

@Serializable
data class SyncEventsApiResponse(
    @SerialName("Responses")
    val responses: List<SyncResponseWrapper>
)

@Serializable
data class SyncResponseWrapper(
    @SerialName("Index")
    val index: Int,
    @SerialName("Response")
    val response: SyncResponse
    // TODO errors and other types of payload
)

@Serializable
data class SyncResponse(
    @SerialName("Code")
    override val code: Int,
    @SerialName("Event")
    val event: EventResponse? = null
    // TODO errors and other types of payload
) : BaseApiResponse()

@Serializable
data class AlarmsApiResponse(
    @SerialName("Alarms")
    val alarms: List<EventAlarmEntity>
)

@Serializable
data class EventsByUidApiResponse(
    @SerialName("Events")
    val events: List<EventResponse>
)

@Serializable
data class ResetCalendarApiResponse(
    @SerialName("Code")
    override val code: Int
) : BaseApiResponse()

@Serializable
data class KeysApiResponse(
    @SerialName("Keys")
    val keys: List<CalendarKeyEntity>,
)

@Serializable
data class PassphrasesApiResponse(
    @SerialName("Passphrases")
    val passphrases: List<PassphraseEntity>,
)

@Serializable
data class ActivePassphraseApiResponse(
    @SerialName("Passphrase")
    val passphrase: PassphraseEntity,
)

@Serializable
data class ReenableKeyApiResponse(
    @SerialName("Key")
    val calendarKey: CalendarKeyEntity
)

@Serializable
data class ReenableKeyApiRequest(
    @SerialName("PrivateKey")
    val privateKey: String
)

@Serializable
data class AttendeeApiResponse(
    @SerialName("Event")
    val event: EventResponse
)

@Serializable
data class UpdateParticipationStatusApiRequest(
    @SerialName("Status")
    val status: Int, // 0: Unanswered, 1: Maybe, 2: No, 3: Yes
    @SerialName("UpdateTime")
    val updateTime: Int? = null
)

@Serializable
data class UpdateEventPersonalPartApiRequest(
    @SerialName("Notifications")
    val notifications: List<NotificationEntity>?,
    @SerialName("Color")
    val color: String? = null,
)

@Serializable
data class UpdateCalendarSettingsApiRequest(
    @SerialName("DefaultEventDuration")
    val defaultEventDuration: Int? = null,
    @SerialName("DefaultPartDayNotifications")
    val defaultPartDayNotifications: List<NotificationEntity>? = null,
    @SerialName("DefaultFullDayNotifications")
    val defaultFullDayNotifications: List<NotificationEntity>? = null
)

@Serializable
data class UpdateCalendarSettingsApiResponse(
    @SerialName("CalendarSettings")
    val calendarSettings: CalendarSettingsEntity
)

@Serializable
data class GetCalendarSettingsApiResponse(
    @SerialName("CalendarSettings")
    val calendarSettings: CalendarSettingsEntity
)

@Serializable
data class GetCalendarSubscriptionApiResponse(
    @SerialName("CalendarSubscription")
    val calendarSubscription: CalendarSubscriptionEntity
)

@Serializable
data class RecreateCalendarApiResponse(
    @SerialName("Calendar")
    val calendar: CalendarEntity
)

@Serializable
data class GetHolidayCalendarsApiResponse(
    @SerialName("Calendars")
    val calendars: List<ManagedHolidayCalendarEntity>
)

@Serializable
data class JoinCalendarApiRequest(
    @SerialName("Signature")
    val signature: String,
    @SerialName("PassphraseKeyPacket")
    val passphraseKeyPacket: String,
    @SerialName("Color")
    val color: String,
    @SerialName("DefaultFullDayNotifications")
    val defaultFullDayNotifications: List<NotificationEntity>? = null,
    @SerialName("Priority")
    val priority: Int? = null
)

@Serializable
data class JoinCalendarApiResponse(
    @SerialName("Calendar")
    val calendar: CalendarEntity,
    @SerialName("Keys")
    val keys: List<CalendarKeyEntity>,
    @SerialName("Passphrase")
    val passphrase: PassphraseEntity,
    @SerialName("Members")
    val members: List<MemberEntity>,
    @SerialName("CalendarSettings")
    val calendarSettings: CalendarSettingsEntity, // settings specific to calendar, not user
)

@Serializable
data class AttendeesInfoResponse(
    @SerialName("Attendees")
    val attendees: List<JsonElement>,
    @SerialName("MoreAttendees")
    val moreAttendees: Int,
)

@Serializable
data class EventResponse(
    @SerialName("ID")
    @PrimaryKey
    val id: String,
    @SerialName("CalendarID")
    val calendarId: String,
    @SerialName("SharedEventID")
    val sharedEventId: String?,
    @SerialName("CalendarKeyPacket")
    val calendarKeyPacket: String?, // keypackets used to decrypt Type 3 CalendarEventData, to be armored with Data packets, base64
    @SerialName("CreateTime")
    val createTime: Long, // unix timestamps
    @SerialName("ModifyTime")
    val modifyTime: Long,
    @SerialName("Permissions")
    val permissions: Int, // Permissions of the attendees (bitmap)
    // 1 (number) - Can invite
    //2 (number) - Can modify event
    //4 (number) - Can see attendees list
    @SerialName("AddressKeyPacket")
    val addressKeyPacket: String?, // shared session key encrypted with the Address Key
    @SerialName("AddressID")
    val addressId: String?, // which Address contains the Address Key ^
    @SerialName("SharedKeyPacket")
    val sharedKeyPacket: String?, // base64, shared session key encrypted with Calendar Key
    @SerialName("SharedEvents")
    val sharedEvents: List<JsonElement>, // shared between all calendars
    @SerialName("CalendarEvents")
    val calendarEvents: List<JsonElement>, // specific to a calendar, shared between all calendar’s members, The data linked with the current calendar
    @SerialName("AttendeesEvents")
    val attendeesEvents: List<JsonElement>, // shared between all calendars
    @SerialName("Attendees")
    val attendees: List<JsonElement>? = emptyList(), // deprecated in favor of attendeesInfo
    @SerialName("AttendeesInfo")
    val attendeesInfo: AttendeesInfoResponse? = null,
    @SerialName("IsProtonProtonInvite")
    val isProtonProtonInvite: Int?, // 1 if is proton to proton invite,
    @SerialName("Notifications")
    val notifications: List<JsonElement>? = null,
    @SerialName("Color")
    val color: String? = null,
    @SerialName("StartTime")
    val startTime: Long, // Epoch seconds
    @SerialName("StartTimezone")
    val startTimeZone: String,
    @SerialName("EndTime")
    val endTime: Long, // Epoch seconds
    @SerialName("EndTimezone")
    val endTimeZone: String,
    @SerialName("FullDay")
    val fullDay: Int,
    @SerialName("UID")
    val uid: String,
    @SerialName("RecurrenceID")
    val recurrenceID: Long?,
    @SerialName("Exdates")
    val exDates: List<Long>,
    @SerialName("RRule")
    val rRule: String?,
    @SerialName("IsOrganizer")
    val isOrganizer: Int,
    @SerialName("IsPersonalSingleEdit")
    val isPersonalSingleEdit: Boolean
)
