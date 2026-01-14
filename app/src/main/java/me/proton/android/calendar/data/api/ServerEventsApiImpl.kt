package me.proton.android.calendar.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.common.API_VERSION_CALENDAR
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.key.data.api.response.AddressResponse
import me.proton.core.key.data.api.response.UserResponse
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.protonApi.BaseRetrofitApi
import retrofit2.http.GET
import retrofit2.http.Path
import javax.inject.Inject

// ServerEvent is an Event happening in Event Loop

interface ServerEventsApiService : BaseRetrofitApi {

    @GET("events/latest")
    suspend fun getLatestServerEvent(): LatestServerCoreEventApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/modelevents/latest")
    suspend fun getLatestServerEvent(@Path("calendarId") calendarId: String): LatestServerCalendarEventApiResponse

    @GET("events/{eventId}")
    suspend fun getServerCoreEventsSince(@Path("eventId") serverEventId: String): ServerCoreEventsApiResponse

    @GET("calendar/$API_VERSION_CALENDAR/{calendarId}/modelevents/{eventId}")
    suspend fun getServerCalendarEventsSince(@Path("eventId") serverEventId: String, @Path("calendarId") calendarId: String): ServerCalendarEventsApiResponse

}

class ServerEventsApiImpl @Inject constructor(private val apiProvider: ApiProvider) : ServerEventsApi {

    override suspend fun getLatestServerCoreEvent(userId: UserId): ApiResponse<LatestServerCoreEventApiResponse> =
        apiProvider.get<ServerEventsApiService>(userId).invoke {
            getLatestServerEvent()
        }.toApiResponse()

    override suspend fun getLatestServerCalendarEvent(
        userId: UserId,
        calendarId: String
    ): ApiResponse<LatestServerCalendarEventApiResponse> =
        apiProvider.get<ServerEventsApiService>(userId).invoke {
            getLatestServerEvent(calendarId)
        }.toApiResponse()

    override suspend fun getServerCoreEventsSince(userId: UserId, serverEventId: String): ApiResponse<ServerEventsApiResponse> =
        apiProvider.get<ServerEventsApiService>(userId).invoke {
            getServerCoreEventsSince(serverEventId).toServerEventsApiResponse()
        }.toApiResponse()

    override suspend fun getServerCalendarEventsSince(
        userId: UserId,
        serverEventId: String,
        calendarId: String
    ): ApiResponse<ServerEventsApiResponse> =
        apiProvider.get<ServerEventsApiService>(userId).invoke {
            getServerCalendarEventsSince(serverEventId, calendarId).toServerEventsApiResponse()
        }.toApiResponse()

}

@Serializable
data class LatestServerCoreEventApiResponse(
    @SerialName("EventID")
    val eventId: String
)

@Serializable
data class LatestServerCalendarEventApiResponse(
    @SerialName("CalendarModelEventID")
    val calendarEventId: String
)

data class ServerEventsApiResponse(
    val eventId: String, // new eventId to send with next request
    val refresh: Int, // bitmap, 255 means throw out client cache and reload everything from server, 1 is mail, 2 is contacts
    val more: Int, // 0 or 1 if more events exist and should be fetched
    val user: UserResponse? = null, // doesn't contain "Action", it's always "update"
    val userSettings: UserSettingsEntity? = null,
    val addresses: List<ServerEvent.AddressesApiResponse>? = null,
    val calendars: List<ServerEvent.CalendarsApiResponse>? = null,
    val calendarKeys: List<ServerEvent.CalendarKeysApiResponse>? = null,
    val calendarPassphrases: List<ServerEvent.PassphrasesApiResponse>? = null,
    val calendarMembers: List<ServerEvent.MembersApiResponse>? = null,
    val calendarEvents: List<ServerEvent.EventsApiResponse>? = null,
    val calendarSettings: List<ServerEvent.CalendarSettingsApiResponse>? = null,
    val calendarAlarms: List<ServerEvent.AlarmsApiResponse>? = null,
    val calendarUserSettings: CalendarUserSettingsEntity? = null,
    val calendarSubscriptions: List<ServerEvent.CalendarSubscriptionsApiResponse>? = null,
)

fun ServerCoreEventsApiResponse.toServerEventsApiResponse() = ServerEventsApiResponse(
    eventId = this.eventId,
    refresh = this.refresh,
    more = this.more,
    user = this.user,
    userSettings = this.userSettings,
    addresses = this.addresses,
    calendars = this.calendars,
    calendarMembers = this.calendarMembers,
    calendarUserSettings = this.calendarUserSettings
)

@Serializable
data class ServerCoreEventsApiResponse(
    @SerialName("EventID")
    val eventId: String, // new eventId to send with next request
    @SerialName("Refresh")
    val refresh: Int, // bitmap, 255 means throw out client cache and reload everything from server, 1 is mail, 2 is contacts
    @SerialName("More")
    val more: Int, // 0 or 1 if more events exist and should be fetched
    @SerialName("User")
    val user: UserResponse? = null, // doesn't contain "Action", it's always "update"
    @SerialName("UserSettings")
    val userSettings: UserSettingsEntity? = null,
    @SerialName("Addresses")
    val addresses: List<ServerEvent.AddressesApiResponse>? = null,
    @SerialName("Calendars")
    val calendars: List<ServerEvent.CalendarsApiResponse>? = null,
    @SerialName("CalendarMembers")
    val calendarMembers: List<ServerEvent.MembersApiResponse>? = null,
    @SerialName("CalendarUserSettings")
    val calendarUserSettings: CalendarUserSettingsEntity? = null
)

@Serializable
data class CalendarUserSettingsEvents(
    @SerialName("CalendarUserSettings")
    val calendarUserSettings: CalendarUserSettingsEntity? = null
)

@Serializable
data class CalendarsEvents(
    @SerialName("Calendars")
    val calendars: List<ServerEvent.CalendarsApiResponse>? = null
)

@Serializable
data class CalendarMembersEvents(
    @SerialName("CalendarMembers")
    val calendarMembers: List<ServerEvent.MembersApiResponse>? = null
)

fun ServerCalendarEventsApiResponse.toServerEventsApiResponse() = ServerEventsApiResponse(
    eventId = this.calendarModelEventId,
    refresh = this.refresh,
    more = this.more,
    calendarKeys = this.calendarKeys,
    calendarPassphrases = this.calendarPassphrases,
    calendarEvents = this.calendarEvents,
    calendarSettings = this.calendarSettings,
    calendarAlarms = this.calendarAlarms,
    calendarSubscriptions = this.calendarSubscriptions
)

@Serializable
data class ServerCalendarEventsApiResponse(
    @SerialName("CalendarModelEventID")
    val calendarModelEventId: String, // new eventId to send with next request
    @SerialName("Refresh")
    val refresh: Int, // bitmap, 255 means throw out client cache and reload everything from server, 1 is mail, 2 is contacts
    @SerialName("More")
    val more: Int, // 0 or 1 if more events exist and should be fetched
    @SerialName("CalendarKeys")
    val calendarKeys: List<ServerEvent.CalendarKeysApiResponse>? = null,
    @SerialName("CalendarPassphrases")
    val calendarPassphrases: List<ServerEvent.PassphrasesApiResponse>? = null,
    @SerialName("CalendarEvents")
    val calendarEvents: List<ServerEvent.EventsApiResponse>? = null,
    @SerialName("CalendarSettings")
    val calendarSettings: List<ServerEvent.CalendarSettingsApiResponse>? = null,
    @SerialName("CalendarAlarms")
    val calendarAlarms: List<ServerEvent.AlarmsApiResponse>? = null,
    @SerialName("CalendarSubscriptions")
    val calendarSubscriptions: List<ServerEvent.CalendarSubscriptionsApiResponse>? = null,
)
@Serializable
data class CalendarEventsServerEvents(
    @SerialName("CalendarEvents")
    val calendarEvents: List<ServerEvent.EventsApiResponse>? = null
)

@Serializable
data class CalendarAlarmsEvents(
    @SerialName("CalendarAlarms")
    val calendarAlarms: List<ServerEvent.AlarmsApiResponse>? = null
)

@Serializable
data class CalendarKeysEvents(
    @SerialName("CalendarKeys")
    val calendarKeys: List<ServerEvent.CalendarKeysApiResponse>? = null
)

@Serializable
data class CalendarPassphrasesEvents(
    @SerialName("CalendarPassphrases")
    val calendarPassphrases: List<ServerEvent.PassphrasesApiResponse>? = null
)

@Serializable
data class CalendarSettingsEvents(
    @SerialName("CalendarSettings")
    val calendarSettings: List<ServerEvent.CalendarSettingsApiResponse>? = null
)

@Serializable
data class CalendarSubscriptionsEvents(
    @SerialName("CalendarSubscriptions")
    val calendarSubscriptions: List<ServerEvent.CalendarSubscriptionsApiResponse>? = null
)


// TODO HANDLE ACTIONS AND CREATE TESTS FOR THAT!!!!!!!!!!!!!!!!!!

class ServerEvent {

    // TODO BaseApiEntity? BaseEventApiEntity?
    abstract class BaseServerEventApiResponse {
        abstract val id: String
        abstract val action: Int // when action is 0 = delete, we get no payload, that's why all payloads are nullable
    }

    enum class Action(val value: Int) {
//        @SerializedName("0")
        DELETE(0),
        CREATE(1),
        UPDATE(2);
        //UPDATE_FLAGS(3) we don't use this for now, but will need to at one point, to only update metadata and not get entire blob from API

        companion object {
            fun valueOf(value: Int) = values().find { it.value == value }
        }
    }



    class ApiEnum<E : Enum<E>>(val value: Enum<E>) {

//        interface Const<E : Enum<E>> {
//            val action: Action2<E>
//        }
    }

    @Serializable
    data class AddressesApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Address")
        val address: AddressResponse? = null // all the payloads here are nullable, because action = 0 (delete) sends no payload
    ) : BaseServerEventApiResponse()

    @Serializable
    data class CalendarsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Calendar")
        val calendar: CalendarEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class CalendarKeysApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Key")
        val key: CalendarKeyEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class PassphrasesApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Passphrase")
        val passphrase: PassphraseEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class MembersApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Member")
        val member: MemberEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class EventsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Event")
        val event: EventEntityMetadata? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    class CalendarSettingsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("CalendarSettings")
        val calendarSettings: CalendarSettingsEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    class AlarmsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("Alarm")
        val alarm: EventAlarmEntity? = null
    ) : BaseServerEventApiResponse()

    @Serializable
    data class CalendarSubscriptionsApiResponse(
        @SerialName("ID")
        override val id: String,
        @SerialName("Action")
        override val action: Int,
        @SerialName("CalendarSubscription")
        val calendarSubscriptionEntity: CalendarSubscriptionEntity? = null
    ) : BaseServerEventApiResponse()

}

