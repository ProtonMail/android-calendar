package me.proton.android.calendar.data

import android.database.sqlite.SQLiteConstraintException
import androidx.annotation.VisibleForTesting
import biweekly.property.RecurrenceId
import biweekly.property.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.PING_INTERVAL_SECONDS
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstRealOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.utils.EventUtilsImpl.overlapsWithFullDayRange
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutBySearchTerm
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutDuplicatesInSubscribedCalendars
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutOccurrencesByExdates
import me.proton.android.calendar.common.utils.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.canonicalizeProtonEmail
import me.proton.android.calendar.common.utils.ProtonUtilsImpl.sortPersonalCalendars
import me.proton.android.calendar.common.utils.getAddressOrNull
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.data.api.AlarmsApiResponse
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.data.api.EventsByUidApiResponse
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.SearchDatabase
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
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.data.entity.SkeletonEventEntity
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toSkeletonEvent
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.TestsApi
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.FetchEventsUseCase
import me.proton.android.calendar.domain.usecase.GetEventWithCommentsUseCase
import me.proton.android.calendar.domain.usecase.GetFetchedEventWindowsValidity
import me.proton.android.calendar.domain.usecase.IndexEventForSearchUseCase
import me.proton.android.calendar.domain.usecase.RefreshDeletedEventsUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UpdateEventOccurrencesUseCase
import me.proton.android.calendar.domain.usecase.UpdateFetchedEventsMetadataUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.NetworkManager
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.toBoolean
import me.proton.core.util.kotlin.toInt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@FlowPreview
@ExperimentalCoroutinesApi
class CalendarsRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val transformEventUseCase: TransformEventUseCase,
    private val logger: Logger,
    private val fetchEventsUseCase: FetchEventsUseCase,
    private val getEventWithCommentsUseCase: GetEventWithCommentsUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val calendarsApi: CalendarsApi,
    private val testsApi: TestsApi,
    private val json: Json,
    private val widgetRefresher: WidgetRefresher,
    private val eventDecryptor: EventDecryptor,
    private val searchDatabase: SearchDatabase,
    private val indexEventForSearchUseCase: IndexEventForSearchUseCase,
    private val userAddressManager: UserAddressManager,
    private val accountManager: AccountManager,
    private val networkManager: NetworkManager,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase,
    private val updateFetchedEventsMetadataUseCase: UpdateFetchedEventsMetadataUseCase,
    private val getFetchedEventWindowsValidity: GetFetchedEventWindowsValidity,
    private val refreshDeletedEventsUseCase: RefreshDeletedEventsUseCase,
) : CalendarsRepository {

    override val fetchingState =
        MutableStateFlow<CalendarsRepository.FetchingState>(CalendarsRepository.FetchingState.NotNeeded)

    private val fetchedWindowTimes = ConcurrentHashMap<FetchWindow, Instant>()
    private var fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /**
     * Min and Max FetchWindow requested in the lifetime of Repository. This means user has visited the views
     * corresponding to these windows, but not necessarily fetched events from backend.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var minRequestedWindowToFetch: FetchWindow? = null
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var maxRequestedWindowToFetch: FetchWindow? = null

    private var scopeEventFetching = CoroutineScope(Dispatchers.Default)

    private val displayServerDownBannerFlow = MutableStateFlow(false)
    private var lastPingMs: Long = 0L

    private suspend fun FetchWindow.needsRefresh() = fetchedWindowTimes[this]
        ?.let { getFetchedEventWindowsValidity.isWindowFetchValid(this, it).not() }
        ?: true

    override suspend fun initForUser(userId: String, timeZoneId: ZoneId): Flow<CalendarsRepository.InitingState> {
        val flow = MutableStateFlow<CalendarsRepository.InitingState>(CalendarsRepository.InitingState.Initing)

        // TODO make sure we also migrate the calendar fetching for new event decryption
        scopeEventFetching.launch {
            fetchEventsChannel.consumeEach { fetchWindow ->

                minRequestedWindowToFetch = if (minRequestedWindowToFetch != null) {
                    listOf(fetchWindow, minRequestedWindowToFetch).minByOrNull { it!!.fromDate }
                } else fetchWindow

                maxRequestedWindowToFetch = if (maxRequestedWindowToFetch != null) {
                    listOf(fetchWindow, maxRequestedWindowToFetch).maxByOrNull { it!!.toDate }
                } else fetchWindow

                logger.v("consuming: $fetchWindow")
                fetchEventsInWindow(fetchWindow)
            }
        }

        flow.value = CalendarsRepository.InitingState.Finished
        return flow
    }

    private suspend fun fetchEventsInWindow(fetchWindow: FetchWindow) {
        val shouldUseFetchedEventsMetadata = getFetchedEventWindowsValidity.shouldUseFetchedEventsMetadata(fetchWindow) && !fetchWindow.force

        // only fetch calendars that have not been fetched before
        val calendarIdsToFetch = if (shouldUseFetchedEventsMetadata) fetchWindow.calendarIds.filter {
            updateFetchedEventsMetadataUseCase.shouldFetch(fetchWindow.userId.id,
                it,
                fetchWindow.fromDate,
                fetchWindow.toDate,
                fetchWindow.timeZoneId)
        } else fetchWindow.calendarIds

        if (calendarIdsToFetch.isEmpty()) return

        val needsToFetch = fetchWindow.force || fetchWindow.needsRefresh()
        if (!needsToFetch) {
            logger.v("no need to fetch events: ${fetchWindow.fromDate} = ${fetchWindow.toDate}")
            return
        }

        logger.d("fetching events: ${fetchWindow.fromDate} = ${fetchWindow.toDate}")

        fetchingState.value = CalendarsRepository.FetchingState.Fetching

        // fetch from API
        val (fetchEventsResult, eventsAndMetadatas) = fetchEventsUseCase.splitFetchEvents(
            fetchWindow.userId,
            calendarIdsToFetch,
            fetchWindow.fromDate,
            fetchWindow.toDate,
            fetchWindow.timeZoneId
        )
        
        // delete any events missed by the event loop
        if (fetchWindow.force && eventsAndMetadatas != null) {
            refreshDeletedEventsUseCase.execute(
                userId = fetchWindow.userId,
                calendarIdsToFetch = calendarIdsToFetch,
                fromDate = fetchWindow.fromDate,
                toDate = fetchWindow.toDate,
                timeZoneId = fetchWindow.timeZoneId,
                eventsAndMetadatas = eventsAndMetadatas,
            )
        }

        if (fetchEventsResult is UseCase.Result.Success<*>) {
            if (eventsAndMetadatas == null) {
                logger.e("fetchEventsResult: null event list when Success")
            }

            eventsAndMetadatas?.let { eventsAndMetadatas ->
                logger.v("fetchEventsResult success: ${eventsAndMetadatas.size}")

                // TODO persist events where we download fresh ones, not here

                val eventEntities = eventsAndMetadatas.map { it.first }
                persistEvents(*(eventEntities).toTypedArray())
                eventsAndMetadatas.forEach { (_, eventMetadata) ->
                    updateEventOccurrencesUseCase.execute(fetchWindow.userId.id, eventMetadata, forceReload = fetchWindow.force)
                }
                fetchingState.value = CalendarsRepository.FetchingState.Finished // Events have been fetched and persisted in DB

                updateAlarmsUseCase.execute(fetchWindow.userId.id, eventEntities)
                fetchedWindowTimes[fetchWindow] = Instant.now()
            }
        } else {
            // If this failed, we make sure servers are up with a ping
            pingServer(fetchWindow.userId)
        }

        fetchingState.value = CalendarsRepository.FetchingState.Finished
    }

    override suspend fun shutdown() {
        // cancel any ongoing Event fetching
        scopeEventFetching.cancel()
        scopeEventFetching = CoroutineScope(Dispatchers.Default)

        minRequestedWindowToFetch = null
        maxRequestedWindowToFetch = null

        fetchedWindowTimes.clear()
        fetchEventsChannel = Channel<FetchWindow>(capacity = 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        fetchingState.value = CalendarsRepository.FetchingState.Finished
    }

    override suspend fun countCalendars(): Int {
        return database.calendarsDao().countCalendars()
    }

    override suspend fun hasHolidayCalendars(userId: String): Boolean {
        return database.calendarsDao().selectHolidayCalendars(userId).isNotEmpty()
    }

    override suspend fun clearSearchDatabase() {
        withContext(Dispatchers.IO) {
            searchDatabase.searchDao().deleteAllSearchEvents()
        }
    }

    override suspend fun selectCalendarEntity(calendarId: String): CalendarEntity? {
        return database.calendarsDao().selectById(calendarId)
    }

    override suspend fun selectCalendar(calendarId: String): Calendar? {
        return database.calendarsDao().selectById(calendarId)?.joinToCalendar(database, json)
    }

    override suspend fun selectCalendarEntities(userId: String): List<CalendarEntity> {
        return database.calendarsDao().selectCalendars(userId)
    }

    override suspend fun selectUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json)
    }

    override suspend fun selectUserPersonalCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter {
            it.isOwner
        }
    }

    override suspend fun selectOtherCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectCalendars(userId).joinToCalendars(database, json).filter {
            !it.isOwner
        }
    }

    override suspend fun selectAllCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectCalendars(userId).joinToCalendars(database, json)
    }

    override suspend fun selectActiveUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter { it.isActive }
    }

    override suspend fun selectDisabledUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter { it.isDisabled }
    }

    override suspend fun selectInactiveUserCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectUserCalendars(userId).joinToCalendars(database, json).filter { it.isInactive }
    }

    override suspend fun selectSubscribedCalendars(userId: String): List<Calendar> {
        return database.calendarsDao().selectSubscribedCalendars(userId).joinToCalendars(database, json)
    }

    override fun flowVisibleCalendarIds(userId: String): Flow<List<String>> {
        return combine(
            database.calendarsDao().flowCalendars(userId).distinctUntilChanged(),
            database.membersDao().flowMembers().distinctUntilChanged()
        ) { calendars, members ->
            if (calendars.isNotEmpty()) {
                // Get user addresses so we can find the calendar member for current user
                val userAddresses = database.addressDao().getByUserId(UserId(calendars.first().fkUserId))
                calendars.mapNotNull { calendarEntity ->
                    // Find the member that belongs to the current user
                    val userMember = members.filter { it.calendarId == calendarEntity.id }.getUserMember(userAddresses)
                    // Keep visible calendar ids
                    userMember?.let {
                        if (it.display.toBoolean()) calendarEntity.id
                        else null
                    }
                }
            } else emptyList()
        }.distinctUntilChanged()
    }

    override fun flowActiveUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).transform<List<Calendar>, List<Calendar>> { it.filter { it.isActive } }.distinctUntilChanged()
    }

    override fun flowDisabledUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).transform<List<Calendar>, List<Calendar>> { it.filter { it.isDisabled } }.distinctUntilChanged()
    }

    override fun flowInactiveUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).transform<List<Calendar>, List<Calendar>> { it.filter { it.isInactive } }.distinctUntilChanged()
    }

    override fun flowAllCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowCalendars(userId).joinToCalendars(database, json)
    }

    override fun flowUserCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).distinctUntilChanged()
    }

    override fun flowUserPersonalCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).map { userCalendars ->
            userCalendars.filter { it.isOwner }
        }.distinctUntilChanged()
    }

    override fun flowSharedCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowUserCalendars(userId).joinToCalendars(database, json).map { userCalendars ->
            userCalendars.filter { !it.isOwner }
        }.distinctUntilChanged()
    }

    override fun flowSubscribedCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowSubscribedCalendars(userId).joinToCalendars(database, json).distinctUntilChanged()
    }

    override fun flowHolidayCalendars(userId: String): Flow<List<Calendar>> {
        return database.calendarsDao().flowHolidayCalendars(userId).joinToCalendars(database, json).distinctUntilChanged()
    }

    override suspend fun persistCalendar(userId: String, calendar: CalendarEntity) {
        //  TODO make sure we have "flags" set!!!!!
        database.calendarsDao().updateOrInsert(calendar.copy(fkUserId = userId))
    }

    override suspend fun deleteCalendars(userId: String) {
        database.calendarsDao().deleteCalendars(userId)
    }

    override suspend fun deleteCalendarById(id: String) {
        database.calendarsDao().deleteById(id)

        // TODO this will never succeed, we just deleted this Calendar from DB
        //  but search is not ON at the moment
        database.calendarsDao().selectCalendarUserId(id)?.let {
            deleteAllSearchEventsInCalendar(it, id)
        }
    }

    override suspend fun refreshCalendars(userId: UserId): Boolean {
        val calendarsResponse = calendarsApi.getCalendars(userId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)
        return if (calendarsResponse != null) {
            calendarsResponse.calendars.forEach {
                // TODO do boostrap for subscribed calendars to get calendar subscription extra properties
                persistCalendar(userId.id, it)
            }
            true
        } else false
    }

    override suspend fun refreshCalendars(userId: UserId, calendarIds: List<String>) {
        val calendars = arrayListOf<CalendarEntity>()
        val members = hashMapOf<String, MemberEntity>()
        calendarIds.forEach {
            val calendarResponse = calendarsApi.getCalendar(userId, it).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)
            if (calendarResponse != null) {
                calendars.add(calendarResponse.calendar)
                // Get members for calendar
                val memberListResponse = calendarsApi.getMemberList(userId, it).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)
                if (memberListResponse != null) {
                    memberListResponse.members.firstOrNull()?. let { memberEntity ->
                        members[it] = memberEntity
                    }
                }
            }
        }
        calendars.forEach {
            // TODO do boostrap for subscribed calendars to get calendar subscription extra properties
            persistCalendar(userId.id, it)
            members[it.id]?.let { memberEntity ->
                persistMember(memberEntity)
            }
        }
    }

    override suspend fun fetchCalendars(userId: UserId): List<Calendar>? {
        return fetchCalendarEntities(userId)?.map {
            Calendar.from(
                it,
                fetchMembers(userId, it.id)?.firstOrNull() ?: return null,
                fetchCalendarSettings(userId, it.id) ?: return null,
                json)
        }
    }

    override suspend fun fetchCalendarEntities(userId: UserId): List<CalendarEntity>? =
        calendarsApi.getCalendars(userId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.calendars

    override suspend fun fetchMembersToCalendarEntities(
        userId: UserId,
        calendarEntities: List<CalendarEntity>
    ): List<Calendar> {
        val calendars = arrayListOf<Calendar>()
        coroutineScope {
            calendarEntities.map {
                async {
                    val members = fetchMembers(userId, it.id)?.firstOrNull() ?: run {
                        logger.e("fetchMembersToCalendarEntities: Failed to fetch members")
                        return@async null
                    }
                    val calendarSettings = fetchCalendarSettings(userId, it.id) ?: run {
                        logger.e("fetchMembersToCalendarEntities: Failed to fetch calendar settings")
                        return@async null
                    }
                    val calendar = Calendar.from(
                        it,
                        members,
                        calendarSettings,
                        json
                    )
                    calendars.add(calendar)
                }
            }.awaitAll()
        }
        return calendars
    }

    override suspend fun fetchMembers(userId: UserId, calendarId: String): List<MemberEntity>? {
        return calendarsApi.getMemberList(userId, calendarId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.members
    }

    private suspend fun fetchCalendarSettings(userId: UserId, calendarId: String): CalendarSettingsEntity? {
        return calendarsApi.getCalendarSettings(userId, calendarId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.calendarSettings
    }

    override suspend fun fetchCalendar(userId: UserId, calendarId: String): Calendar? {

        val fetchedCalendar = calendarsApi.getCalendar(userId, calendarId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.calendar ?: return null
        val fetchedMember = calendarsApi.getMemberList(userId, calendarId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.members?.firstOrNull() ?: return null
        val fetchedCalendarSettings = calendarsApi.getCalendarSettings(userId, calendarId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.calendarSettings ?: return null

        return Calendar.from(fetchedCalendar, fetchedMember, fetchedCalendarSettings, json)
    }

    override suspend fun fetchCalendarEntity(userId: UserId, calendarId: String): CalendarEntity? =
        calendarsApi.getCalendar(userId, calendarId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.calendar

    override suspend fun fetchManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? =
        calendarsApi.getManagedHolidayCalendars(userId).pingServerIfNeeded(userId).valueOrNullAndLogErrors(logger)?.calendars

    override suspend fun getManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? =
        database.managedHolidayCalendarDao().selectAll()

    override suspend fun getManagedHolidayCalendarById(userId: UserId, calendarId: String): ManagedHolidayCalendarEntity? =
        database.managedHolidayCalendarDao().selectById(calendarId)

    /**
     * Fetches the managed holiday calendar list from BE, persists it in DB if non null.
     * @return the fetched list of managed holiday calendar.
     */
    override suspend fun refreshManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? {
        val managedHolidayCalendars = fetchManagedHolidayCalendars(userId)
        managedHolidayCalendars?.forEach {
            database.managedHolidayCalendarDao().updateOrInsert(it.copy(fkUserId = userId.id))
        }
        return managedHolidayCalendars
    }

    /**
     * Fetches the managed holiday calendar list from BE, persists it in DB if non null.
     * @return the fetched list of visible managed holiday calendar.
     */
    override suspend fun refreshVisibleManagedHolidayCalendars(userId: UserId): List<ManagedHolidayCalendarEntity>? {
        return refreshManagedHolidayCalendars(userId)?.filter { it.hidden == false }
    }

    override suspend fun isCalendarDisplayUpToDate(calendarId: String, newDisplay: Int): Boolean {
        val member = selectCalendarUserMember(calendarId)
        return if (member != null) member.display == newDisplay else false
    }

    override suspend fun updateCalendarDisplay(calendarId: String, display: Boolean) {
        selectCalendarUserMember(calendarId)?.let {
            database.membersDao().updateDisplay(calendarId, display.toInt())
            widgetRefresher.refreshEventList()
        }
    }

    /**
     * Combines expanding, including single edits and filtering by exdates.
     */
    override suspend fun expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
        originalEvent: Event,
        eventsSharingUid: List<Event>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        userEmails: List<String>,
        isFreeUser: Boolean
    ): List<UiEvent>? {
        val tz = ZoneId.of(timeZoneId)

        val fromStartInstant = fromDate.atStartOfDay(tz).toInstant()
        val toEndInstant = toDate.plusDays(1).atStartOfDay(tz).toInstant()

        val dtStart = originalEvent.getStart(timeZoneId)
        val untilInstant = originalEvent.iCalEvent.recurrenceRule.value.until?.toInstant()

        val potentialOccurrences = when {
            dtStart.toInstant() >= toEndInstant -> emptyList()
            untilInstant != null && untilInstant.isBefore(fromStartInstant) -> emptyList()
            else -> originalEvent.generateOccurrencesUntil(toDate, timeZoneId) ?: return null
        }

        val exInstants = originalEvent.iCalEvent.exceptionDates
            .flatMap { it.values }
            .map { it.toZonedDateTime(timeZoneId).toInstant() }
            .toSet()

        val singleEditRecurrenceInstants = eventsSharingUid
            .filter { it.isSingleEdit() }
            .mapNotNull { it.getRecurrenceId(timeZoneId)?.toInstant() }
            .toSet()

        val displayColor = originalEvent.getDisplayColor(isFreeUser)
        val decryptStatus = originalEvent.decryptionStatus ?: Event.DecryptionStatus.Failure.Generic
        val participation = originalEvent.getParticipationStatus(userEmails)
        val status = originalEvent.status ?: Status.confirmed()

        val originalUiEvents = potentialOccurrences.mapNotNull { occ ->
            val sInst = occ.startDateTime.toInstant()
            if (sInst in exInstants || sInst in singleEditRecurrenceInstants) return@mapNotNull null

            val eInst = occ.endDateTime.toInstant()
            val overlaps = eInst.isAfter(fromStartInstant) && sInst.isBefore(toEndInstant)
            if (!overlaps) return@mapNotNull null

            UiEvent(
                id = originalEvent.id,
                calendarId = originalEvent.calendar.id,
                uid = originalEvent.uid,
                summary = originalEvent.summary,
                location = originalEvent.location,
                description = originalEvent.description,
                dateStart = occ.startDateTime,
                dateEnd = occ.endDateTime,
                isAllDay = originalEvent.isAllDay(),
                occurrenceNumber = occ.occurrenceNumber,
                displayColor = displayColor,
                decryptionStatus = decryptStatus,
                participationStatus = participation,
                status = status,
            )
        }

        val singleEditUiEvents = eventsSharingUid.mapNotNull { se ->
            if (!se.isSingleEdit()) return@mapNotNull null
            val s = se.getStart(timeZoneId)
            val e = se.getEnd(timeZoneId)
            val sInst = s.toInstant()
            val eInst = e.toInstant()
            if (eInst <= fromStartInstant || sInst >= toEndInstant) return@mapNotNull null
            UiEvent(
                id = se.id,
                calendarId = se.calendar.id,
                uid = se.uid,
                summary = se.summary,
                location = se.location,
                description = se.description,
                dateStart = s,
                dateEnd = e,
                isAllDay = se.isAllDay(),
                occurrenceNumber = 0,
                displayColor = se.getDisplayColor(isFreeUser),
                decryptionStatus = se.decryptionStatus ?: Event.DecryptionStatus.Failure.Generic,
                participationStatus = se.getParticipationStatus(userEmails),
                status = se.status ?: Status.confirmed(),
            )
        }
        return if (singleEditUiEvents.isEmpty()) {
            originalUiEvents
        } else {
            originalUiEvents + singleEditUiEvents
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    data class FetchWindow(
        val userId: UserId,
        val calendarIds: List<String>,
        val fromDate: LocalDate,
        val toDate: LocalDate,
        val timeZoneId: String,
        val force: Boolean = false,
    )

    override suspend fun fetchEvents(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        force: Boolean,
    ) {
        val calendarIds = database.calendarsDao().selectCalendars(userId.id).map { it.id }
        fetchEventsChannel.send(FetchWindow(userId, calendarIds, fromDate, toDate, timeZoneId, force))
    }

    override suspend fun transformAllowingApiCall(eventId: String, calendarId: String): Event? {
        return database.eventsDao().selectEvent(eventId, calendarId)?.let { eventDecryptor.decryptAllowingApiCall(it) }
    }

    override fun getSearchEvents(userId: String, searchTerm: String): Flow<CalendarsRepository.GetEventsResult<Event>> =
        searchDatabase.searchDao().flowSearchEvents(userId).distinctUntilChanged().transform<List<SearchEventEntity>, CalendarsRepository.GetEventsResult<Event>> { searchEventEntities ->
            val containSearchTerm = searchEventEntities.filterOutBySearchTerm(searchTerm)

            val deduplicated = containSearchTerm.mapNotNull { searchEventEntity ->
                database.eventsDao().selectEvent(searchEventEntity.eventId, searchEventEntity.calendarId)?.let { eventDecryptor.decrypt(it) }
            }.filterOutDuplicatesInSubscribedCalendars().first

            emit(CalendarsRepository.GetEventsResult.Success(deduplicated, fullyLoaded = true))
        }.onStart {
            emit(CalendarsRepository.GetEventsResult.InProgress)
        }.catch {
            emit(CalendarsRepository.GetEventsResult.Exception(it))
        }.distinctUntilChanged().cancellable()

    override suspend fun deleteAllSearchEvents(userId: String) {
        searchDatabase.searchDao().deleteAll(userId)
    }

    override suspend fun deleteAllSearchEventsInCalendar(userId: String, calendarId: String) {
        searchDatabase.searchDao().deleteAllInCalendar(userId, calendarId)
    }

    override suspend fun deleteSearchEventsForEvents(userId: String, calendarId: String, eventIds: List<String>) {
        searchDatabase.searchDao().deleteSearchEventsForEvents(userId, calendarId, eventIds)
    }

    override suspend fun hasEvent(eventId: String, calendarId: String, ): Boolean =
        database.eventsDao().hasEvent(eventId, calendarId)

    override suspend fun eventExistsOnServer(userId: UserId, eventId: String, calendarId: String): Boolean? {
        return when (val result = calendarsApi.getEvent(userId, calendarId, eventId)) {
            is ApiResponse.Success-> true
            is ApiResponse.Error -> {
                if (result.isNotFound()) return false
                else null
            }
            is ApiResponse.Exception -> null
        }
    }

    override suspend fun shouldFetchEvent(userId: UserId, metadata: EventEntityMetadata): Boolean {
        val dbEventEntity = database.eventsDao().selectById(metadata.id)
        val isDbEventUpToDate = dbEventEntity?.modifyTime == metadata.modifyTime
        if (isDbEventUpToDate) return false

        if (metadata.rRule == null) { // non-recurring event
            val now = Instant.now()
            val startInstant = Instant.ofEpochSecond(metadata.startTime)
            val endInstant = Instant.ofEpochSecond(metadata.endTime)

            val isInsideFetchedEventsMetadata = updateFetchedEventsMetadataUseCase.isWindowFullyFetched(
                userId.id,
                metadata.calendarId,
                startInstant,
                endInstant,
            )

            val isEventRecent = when {
                now.minus(60, ChronoUnit.DAYS).isAfter(endInstant) -> false
                now.plus(120, ChronoUnit.DAYS).isBefore(startInstant) -> false
                else -> true
            }

            val minWindowStart = minRequestedWindowToFetch?.fromDate?.atStartOfDay(ZoneId.of(minRequestedWindowToFetch?.timeZoneId))
            val maxWindowEnd = maxRequestedWindowToFetch?.toDate?.plusDays(1)?.atStartOfDay(ZoneId.of(maxRequestedWindowToFetch?.timeZoneId))

            // event is within all requested FetchWindows, it means that user would have displayed it in one of the views
            val isInsideRequestedFetchingWindows = when {
                minWindowStart?.toInstant()?.isAfter(endInstant) == true -> false
                maxWindowEnd?.toInstant()?.isBefore(startInstant) == true -> false
                else -> minWindowStart != null && maxWindowEnd != null
            }

            return isEventRecent || isInsideRequestedFetchingWindows || isInsideFetchedEventsMetadata
        } else { // recurring event, we always assume "should fetch" for simplicity
            return true
        }

    }

    override suspend fun hasCalendar(calendarId: String, ): Boolean = database.calendarsDao().hasCalendar(calendarId)

    /**
     * Calling this method requires obtained lock on 'allEvents'!
     */
    override fun expandDbEvent(event: Event, allEvents: List<Event>, toDateTime: ZonedDateTime): List<Event> {
        return if (event.isRecurring()) {
            val expandedOccurrences = ICalUtilsImpl.expandOccurrencesWithSingleEdits(
                event,
                allEvents.filter { it.uid == event.uid },
                toDateTime.toLocalDate(),
                toDateTime.zone.id
            ) ?: run {
                logger.e("expandDbEvent: expandOccurrencesWithSingleEdits result was null")
                return emptyList()
            }
            val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event, toDateTime.zone.id)

            filteredByExdates
        } else if (event.isSingleEdit()) {
            // single edits are already generated when expanding above ^
            //  however, orphaned single edits (without original recurring event)
            //  have to be added to the list manually
            if (allEvents.find { it.uid == event.uid && it.isRecurring() } != null) {
                emptyList()
            } else {
                listOf(event)
            }
        } else {
            listOf(event)
        }
    }

    private fun expandSkeletonEventsAndFilterInWindow(event: Event, allEvents: List<Event>, eventsWindow: CalendarsRepository.EventsWindow): List<Event> {

        return if (event.isRecurring()) {
            // occurrences are already filtered for time window
            val expandedOccurrences = ICalUtilsImpl.expandOccurrencesWithSingleEdits(
                event,
                allEvents.filter { it.uid == event.uid },
                eventsWindow.fromDate,
                eventsWindow.toDate,
                eventsWindow.timeZoneId
            ) ?: run {
                logger.e("expandSkeletonEventsAndFilterInWindow: expandOccurrencesWithSingleEdits result was null")
                return emptyList()
            }
            val filteredByExdates = expandedOccurrences.filterOutOccurrencesByExdates(event, eventsWindow.timeZoneId)

            filteredByExdates
        } else if (event.isSingleEdit()) {
            // single edits are already generated when expanding above ^
            //  however, orphaned single edits (without original recurring event)
            //  have to be added to the list manually
            if (allEvents.find { it.uid == event.uid && it.isRecurring() } != null) {
                emptyList()
            } else {
                if (event.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId)) listOf(event) else emptyList()
            }
        } else {
            if (event.overlapsWithFullDayRange(eventsWindow.fromDate, eventsWindow.toDate, eventsWindow.timeZoneId)) listOf(event) else emptyList()
        }
    }

    override suspend fun selectEventEntity(eventId: String): EventEntity? =
        database.eventsDao().selectById(eventId)

    override suspend fun selectRootEventEntity(eventUid: String): EventEntity? {
        val formattedUid = formatUidForICal(eventUid)
        return database.eventsDao().selectByUid(formattedUid).find { eventEntity ->
            eventEntity.sharedEvents.any {
                try {
                    // only root event contains RRULE
                    it.jsonObject.get("Data")?.jsonPrimitive?.content?.contains("RRULE:") == true
                } catch (e: IllegalArgumentException) {
                    false
                }
            }
        }
    }

    override suspend fun hasSingleEdits(userId: UserId, eventUid: String): Boolean? {
        // Return null for failed API calls
        val formattedUid = formatUidForICal(eventUid)
        val hasSingleEditsInDb = database.eventsDao().countByUid(formattedUid) > 1
        if (hasSingleEditsInDb) return true
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {
            eventsSharingUidResponse.data.events.map { it.toEventEntity() }.forEach {
                val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(it)
                } else {
                    transformEventUseCase.execute(it)
                }
                if (event?.iCalEvent?.recurrenceId != null) return true
            }
            false
        } else null
    }

    override suspend fun getSingleEdits(userId: UserId, eventUid: String, stopAfter: ZonedDateTime?, timeZoneId: String?): List<Event>? {
        // Return null for failed API calls
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {
            val events = arrayListOf<Event>()
            eventsSharingUidResponse.data.events.map { it.toEventEntity() }.forEach {
                val event = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(it)
                } else {
                    transformEventUseCase.execute(it)
                }
                if (stopAfter != null && timeZoneId != null && event?.getStart(timeZoneId)?.isAfter(stopAfter) == true) {
                    events.add(event)
                    return events
                }
                if (event?.iCalEvent?.recurrenceId != null) events.add(event)
            }
            events
        } else return null
    }

    /**
     * User is invited to only one occurrence of a recurring event
     */
    override suspend fun isOrphanSingleEdit(userId: UserId, eventUid: String): Boolean? {
        // Check if single edit is the only occurrence of a recurring event
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {
            // If an event with the same UID has no recurrenceId then we have occurrence(s) of the main series
            eventsSharingUidResponse.data.events.none { eventResponse ->
                val sharedEvents = eventResponse.sharedEvents.map {
                    json.decodeFromJsonElement<Event.EventPart.Shared>(it)
                }
                val iCal = ICalUtilsImpl.parseICalString(sharedEvents.first { !it.isEncrypted }.data)
                iCal?.events?.firstOrNull()?.recurrenceId == null
            }
        } else null
    }

    /**
     * Main chain has no other occurrences left and event is the only single edit
     */
    override suspend fun isStandaloneSingleEdit(userId: UserId, eventUid: String, eventRecurrenceId: RecurrenceId, timeZoneId: String): Boolean? {
        // Check if single edit is the only occurrence of a recurring event
        val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100) // TODO paging
        return if (eventsSharingUidResponse is ApiResponse.Success) {

            val skeletonEvents = eventsSharingUidResponse.data.events.mapNotNull { eventResponse ->
                val skeletonEventEntity = SkeletonEventEntity(eventResponse.id, eventResponse.calendarId, eventResponse.sharedEvents, eventResponse.modifyTime, eventResponse.addressId)
                skeletonEventEntity.toSkeletonEvent(json)
            }

            val singleEdits = skeletonEvents.filter { skeletonEvent ->
                skeletonEvent.iCalEvent.recurrenceId != null
            }
            if (singleEdits.size > 1) return false

            val rootEvent = skeletonEvents.firstOrNull { it.iCalEvent.recurrenceId == null } ?: return false // If it has no root event then it is an orphan single edit

            val isStandaloneSingleEdit = rootEvent.generateFirstRealOccurrenceSince(skeletonEvents, rootEvent.getStart(timeZoneId)) == null &&
                    (singleEdits.isNullOrEmpty() || singleEdits.firstOrNull()?.iCalEvent?.recurrenceId == eventRecurrenceId)
            isStandaloneSingleEdit
        } else null
    }

    override suspend fun getEventsByUid(userId: UserId, eventUid: String): ApiResponse<EventsByUidApiResponse> {
        return calendarsApi.getEventsByUid(userId, eventUid, 0, 100)
    }

    override suspend fun selectEventEntitiesByUids(
        uids: Collection<String>,
    ): Map<String, List<EventEntity>> {
        if (uids.isEmpty()) return emptyMap()
        return uids.associateWith {
            eventUid -> database.eventsDao().selectByUid(formatUidForICal(eventUid))
        }
    }

    override suspend fun fetchEventById(userId: UserId, calendarId: String, eventId: String): ApiResponse<EventApiResponse> {
        return getEventWithCommentsUseCase.execute(userId, calendarId, eventId)
    }

    override suspend fun deleteEventsMetadataByEventIds(eventIds: List<String>) {
        database.eventsMetadataDao().deleteByEventIds(eventIds)
    }

    override suspend fun deleteEventsMetadataByCalendarId(calendarId: String) {
        database.eventsMetadataDao().deleteByCalendarId(calendarId)
    }

    override suspend fun persistEvents(vararg events: EventEntity) {
        val eventsByCalendar = events.groupBy { it.calendarId }
        database.inTransaction {
            eventsByCalendar.forEach { (calendarId, eventsForCalendar) ->
                val calendarUserId = database.calendarsDao().selectCalendarUserId(calendarId)
                if (calendarUserId != null /* Calendar exists */) {
                    try {
                        eventsForCalendar.forEach {
                            // don't overwrite Event it we already have the same or newer one in DB
                            if (!database.eventsDao().hasEventWithHigherOrEqualModifyTime(it.id, it.calendarId, it.modifyTime)) {
                                database.eventsDao().updateOrInsert(it)
                                indexEventForSearchUseCase.execute(calendarUserId, listOf(it))
                            }
                        }
                    } catch (e: SQLiteConstraintException) {
                        // hack for different SQLite implementations formatting message differently
                        if (e.message?.contains("787") == true
                            && e.message?.contains("foreign", ignoreCase = true) == true
                            && e.message?.contains("constraint", ignoreCase = true) == true
                        ) {
                            // ignore, it means this Event's Calendar doesn't exist
                            logger.e("persistEvents couldn't insert because ${e.message}, calendar ID: $calendarId", e)
                        } else throw e
                    }
                } else {
                    logger.e("persistEvents couldn't insert because calendar $calendarId doesn't exist")
                }
            }
        }
    }

    override suspend fun persistEventsMetadata(vararg eventsMetadata: EventEntityMetadata) {
        database.eventsMetadataDao().updateOrInsert(*eventsMetadata)
    }

    override suspend fun deleteEventsById(calendarId: String, ids: List<String>) {
        database.eventsDao().deleteByIds(ids)

        database.calendarsDao().selectCalendarUserId(calendarId)?.let {
            deleteSearchEventsForEvents(it, calendarId, ids)
        }
    }

    override suspend fun deleteAllEvents() {
        database.eventsDao().deleteAll()
    }

    override suspend fun deleteAllEvents(calendarId: String) {
        database.eventsDao().deleteAll(calendarId)

        database.calendarsDao().selectCalendarUserId(calendarId)?.let {
            deleteAllSearchEventsInCalendar(it, calendarId)
        }
    }

    override suspend fun selectCalendarKeys(calendarId: String): List<CalendarKeyEntity> {
        return database.calendarKeysDao().select(calendarId)
    }

    override suspend fun persistCalendarKey(calendarKey: CalendarKeyEntity) {
        database.calendarKeysDao().updateOrInsert(calendarKey)
    }

    override suspend fun deleteAllCalendars() {
        database.calendarsDao().deleteAll()
        database.calendarUserSettingsDao().deleteAll()
        database.managedHolidayCalendarDao().deleteAll()
    }

    override suspend fun deleteCalendarKeyById(id: String) {
        database.calendarKeysDao().deleteById(id)
    }

    override suspend fun deleteCalendarKeyByCalendarId(calendarId: String) {
        database.calendarKeysDao().deleteByCalendarId(calendarId)
    }

    override suspend fun selectCalendarPassphrases(calendarId: String): List<PassphraseEntity> {
        return database.passphrasesDao().select(calendarId)
    }

    override suspend fun persistCalendarPassphrase(calendarPassphrase: PassphraseEntity) {
        database.passphrasesDao().insert(calendarPassphrase)
    }

    override suspend fun deleteCalendarPassphraseById(id: String) {
        database.passphrasesDao().deleteById(id)
    }

    override suspend fun deleteCalendarPassphrases(calendarId: String) {
        database.passphrasesDao().deleteByCalendarId(calendarId)
    }

    override suspend fun selectCalendarMembers(calendarId: String): List<MemberEntity> {
        return database.membersDao().selectCalendarMembers(calendarId)
    }

    override suspend fun selectCalendarUserMember(calendarId: String): MemberEntity? {
        // Get user addresses so we can find the calendar member for current user
        val userAddresses = accountManager.getPrimaryUserId().firstOrNull()?.let { userAddressManager.getAddressesOrNull(it) } ?: run {
            logger.e("selectCalendarUserMember userAddresses were null")
            return null
        }
        // Get all members for that calendar
        val calendarMembers = database.membersDao().selectCalendarMembers(calendarId)
        // Find the member that belongs to the current user
        return getUserMember(userAddresses, calendarMembers)
    }

    override suspend fun selectMemberById(memberId: String): MemberEntity? {
        return database.membersDao().selectById(memberId)
    }

    override suspend fun persistMember(member: MemberEntity) {
        database.membersDao().updateOrInsert(member)
    }

    override suspend fun deleteMemberById(id: String) {
        database.membersDao().deleteById(id)
    }

    override suspend fun selectCalendarSettings(calendarId: String): CalendarSettingsEntity? {
        return database.calendarSettingsDao().select(calendarId)
    }

    override suspend fun persistCalendarSettings(calendarSettings: CalendarSettingsEntity) {
        database.calendarSettingsDao().insert(calendarSettings)
    }

    override suspend fun updateCalendarSettings(calendarSettings: CalendarSettingsEntity) {
        database.calendarSettingsDao().updateOrInsert(calendarSettings)
    }

    override suspend fun deleteCalendarSettingsById(id: String) {
        database.calendarSettingsDao().deleteById(id)
    }

    override suspend fun deleteCalendarSettingsByCalendarId(calendarId: String) {
        database.calendarSettingsDao().deleteByCalendarId(calendarId)
    }

    override suspend fun selectCalendarSubscription(calendarId: String): CalendarSubscriptionEntity? {
        return database.calendarSubscriptionDao().select(calendarId)
    }

    override suspend fun selectCalendarSubscriptions(calendarId: String): List<CalendarSubscriptionEntity> {
        return database.calendarSubscriptionDao().selectCalendarSubscriptions()
    }

    override fun flowCalendarSubscriptions(): Flow<List<CalendarSubscriptionEntity>> {
        return database.calendarSubscriptionDao().flowCalendarSubscriptions().distinctUntilChanged()
    }

    override suspend fun persistCalendarSubscription(calendarSubscription: CalendarSubscriptionEntity) {
        database.calendarSubscriptionDao().updateOrInsert(calendarSubscription)
    }

    override suspend fun deleteCalendarSubscriptionByCalendarId(calendarId: String) {
        database.calendarSubscriptionDao().deleteByCalendarId(calendarId)
    }

    override suspend fun selectCalendarUserSettings(userId: String): CalendarUserSettingsEntity? {
        return database.calendarUserSettingsDao().select(userId)
    }

    override suspend fun updateCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String, autoDetectPrimaryTimezone: Int) {
        return database.calendarUserSettingsDao().updateAutoDetectPrimaryTimezone(userId, autoDetectPrimaryTimezone)
    }

    override suspend fun selectCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Int? {
        return database.calendarUserSettingsDao().selectAutoDetectPrimaryTimezone(userId)
    }

    override fun flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsAutoDetectPrimaryTimezone(userId).distinctUntilChanged()
    }

    override suspend fun updateCalendarUserSettingsDisplayWeekNumber(userId: String, displayWeekNumber: Int) {
        return database.calendarUserSettingsDao().updateDisplayWeekNumber(userId, displayWeekNumber)
    }

    override suspend fun selectCalendarUserSettingsDisplayWeekNumber(userId: String): Int? {
        return database.calendarUserSettingsDao().selectCalendarUserSettingsDisplayWeekNumber(userId)
    }

    override fun flowCalendarUserSettingsDisplayWeekNumber(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsDisplayWeekNumber(userId).distinctUntilChanged()
    }

    override fun flowCalendarUserSettingsAutoImportInvite(userId: String): Flow<Int?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsAutoImportInvite(userId).distinctUntilChanged()
    }

    override suspend fun updateCalendarUserDefaultCalendarId(userId: String, defaultCalendarId: String) {
        return database.calendarUserSettingsDao().updateDefaultCalendarId(userId, defaultCalendarId)
    }

    override suspend fun updateCalendarUserAutoImportInvite(userId: String, autoImportInvite: Boolean) {
        return database.calendarUserSettingsDao().updateAutoImportInvite(userId, autoImportInvite.toInt())
    }

    override fun flowCalendarUserDefaultCalendarId(userId: String): Flow<String?> {
        return database.calendarUserSettingsDao().flowCalendarUserDefaultCalendarId(userId).distinctUntilChanged()
    }

    override suspend fun selectCalendarUserSettingsPrimaryTimezone(userId: String): String? {
        return database.calendarUserSettingsDao().selectCalendarUserSettingsPrimaryTimezone(userId)
    }

    override fun flowCalendarUserSettingsPrimaryTimezone(userId: String): Flow<String?> {
        return database.calendarUserSettingsDao().flowCalendarUserSettingsPrimaryTimezone(userId).distinctUntilChanged()
    }

    override suspend fun persistCalendarUserSettings(userId: String, calendarUserSettings: CalendarUserSettingsEntity) {
        database.calendarUserSettingsDao().updateOrInsert(calendarUserSettings.copy(fkUserId = userId))
    }

    override suspend fun deleteCalendarUserSettingsByUserId(userId: String) {
        database.calendarUserSettingsDao().deleteByUserId(userId)
    }

    override suspend fun getDefaultCalendarIdWithFallback(userId: String, allowShared: Boolean): String? {
        // Get user calendar settings default calendar id or fallback to first sorted personal active user calendar id
        val defaultCalendarId = getDefaultCalendarId(userId)
        val calendar = defaultCalendarId?.let {
            selectCalendar(defaultCalendarId)
        }
        return if (calendar != null && calendar.isActive && (allowShared || calendar.isOwner)) {
            defaultCalendarId
        } else {
            if (allowShared) {
                val sortedActiveUserCalendars = sortPersonalCalendars(selectActiveUserCalendars(userId), null)
                sortedActiveUserCalendars.firstOrNull {
                    it.isOwner // First try to get a personal calendar
                }?.id ?: sortedActiveUserCalendars.firstOrNull {
                    it.allowEditEvents // Fallback to a writable calendar
                }?.id
            } else {
                sortPersonalCalendars(selectActiveUserCalendars(userId), null).firstOrNull {
                    it.isOwner
                }?.id
            }
        }
    }

    override suspend fun getDefaultCalendarId(userId: String): String? {
        return database.calendarUserSettingsDao().selectCalendarUserDefaultCalendarId(userId)
    }

    override suspend fun fetchEventAlarms(userId: UserId, calendarId: String, eventId: String): ApiResponse<AlarmsApiResponse> {
        return calendarsApi.getEventAlarms(userId, calendarId, eventId)
    }

    override suspend fun selectEventAlarms(eventId: String): Flow<List<EventAlarmEntity>> {
        return database.eventAlarmsDao().selectByEventId(eventId)
    }

    override suspend fun selectEventAlarm(eventAlarmId: String): EventAlarmEntity? {
        return database.eventAlarmsDao().select(eventAlarmId)
    }

    override suspend fun deleteAllEventAlarmsByCalendar(calendarId: String) {
        database.eventAlarmsDao().deleteAllByCalendar(calendarId)
    }

    override suspend fun selectUpcomingEventAlarms(timestampSeconds: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectUpcomingInclusive(timestampSeconds)
    }

    override suspend fun selectAllEventAlarmsBetween(timestampSecondsFrom: Long, timestampSecondsTo: Long): List<EventAlarmEntity> {
        return database.eventAlarmsDao().selectAllBetweenInclusive(timestampSecondsFrom, timestampSecondsTo)
    }

    override suspend fun deleteEventAlarmById(id: String) {
        database.eventAlarmsDao().deleteById(id)
    }

    override suspend fun deleteEventAlarmsForEvent(eventId: String) {
        database.eventAlarmsDao().deleteAllByEventId(eventId)
    }

    override suspend fun deleteEventAlarmsByEventIdAndOccurrence(eventId: String, occurrence: Long) {
        database.eventAlarmsDao().deleteAllByEventIdAndOccurrence(eventId, occurrence)
    }

    override suspend fun getAddressForMember(
        userId: UserId,
        addressId: String?,
        memberId: String,
        canonicalEmail: String,
        addresses: List<UserAddress>?
    ): UserAddress? {
        val address =
            if (!addressId.isNullOrEmpty() && !addresses.isNullOrEmpty()) {
                addresses.firstOrNull { it.addressId.id.equalsNoCase(addressId) }
            } else if (!addressId.isNullOrEmpty()) {
                userAddressManager.getAddressOrNull(userId, addressId)
            } else if (!addresses.isNullOrEmpty()) {
                addresses.firstOrNull {
                    canonicalizeProtonEmail(it.email, forceCanonicalization = true).equalsNoCase(canonicalEmail)
                }
            } else {
                userAddressManager.getAddressesOrNull(userId)?.firstOrNull {
                    canonicalizeProtonEmail(it.email, forceCanonicalization = true).equalsNoCase(canonicalEmail)
                }
            }

        if (address != null && addressId.isNullOrEmpty()) {
            // Update AddressId in member if a match was found and field was not already persisted
            database.membersDao().updateMemberAddressId(memberId, address.addressId.id)
        }
        return address
    }

    /**
     * Find member that belongs to user
     */
    override fun getUserMember(userAddresses: List<UserAddress>, members: List<MemberEntity>): MemberEntity? {
        return members.firstOrNull { member ->
            userAddresses.any { userAddress ->
                if (!member.addressId.isNullOrEmpty()) {
                    member.addressId.equalsNoCase(userAddress.addressId.id)
                } else {
                    member.canonicalEmail.equalsNoCase(
                        canonicalizeProtonEmail(userAddress.email, forceCanonicalization = true)
                    )
                }
            }
        }
    }

    override suspend fun <T : Any> ApiResponse<T>.pingServerIfNeeded(userId: UserId): ApiResponse<T> {
        if (this is ApiResponse.Error
            && this.httpCode == 503
            && networkManager.isConnectedToNetwork()
            && System.currentTimeMillis().minus(lastPingMs) >= TimeUnit.SECONDS.toMillis(PING_INTERVAL_SECONDS)) {
            displayServerDownBannerFlow.value = testsApi.pingServer(userId) !is ApiResponse.Success
            lastPingMs = System.currentTimeMillis()
        }

        return this
    }

    override suspend fun pingServer(userId: UserId) {
        if (networkManager.isConnectedToNetwork()
            && System.currentTimeMillis().minus(lastPingMs) >= TimeUnit.SECONDS.toMillis(PING_INTERVAL_SECONDS)) {
            displayServerDownBannerFlow.value = testsApi.pingServer(userId) !is ApiResponse.Success
            lastPingMs = System.currentTimeMillis()
        }
    }

    override fun getDisplayServerDownBannerFlow(): Flow<Boolean> {
        return displayServerDownBannerFlow
    }

    override fun hideServerDownBanner() {
        displayServerDownBannerFlow.value = false
    }
}

/**
 * Joins [CalendarEntity] with [MemberEntity] to [Calendar] object.
 */
fun Flow<List<CalendarEntity>>.joinToCalendars(database: AppDatabase, json: Json): Flow<List<Calendar>> {
    return combine(
        this.distinctUntilChanged(),
        database.membersDao().flowMembers().distinctUntilChanged(),
        database.calendarSettingsDao().flowCalendarSettings().distinctUntilChanged()
    ) { calendars, members, calendarSettingsList ->
        if (calendars.isNotEmpty()) {
            // Get user addresses so we can find the calendar member for current user
            val userAddresses = database.addressDao().getByUserId(UserId(calendars.first().fkUserId))
            calendars.mapNotNull { calendarEntity ->
                // Find the calendar settings for that calendar
                val calendarSettings = calendarSettingsList.firstOrNull { it.calendarId == calendarEntity.id } ?: return@mapNotNull null
                // Find the member that belongs to the current user
                val userMember = members.filter { it.calendarId == calendarEntity.id }.getUserMember(userAddresses)
                // Map to Calendar
                userMember?.let { Calendar.from(calendarEntity, it, calendarSettings, json) }
            }
        } else emptyList()
    }.distinctUntilChanged()
}

/**
 * Joins [CalendarEntity] with [MemberEntity] to [Calendar] object.
 */
suspend fun List<CalendarEntity>.joinToCalendars(database: AppDatabase, json: Json): List<Calendar> {
    if (this.isEmpty()) return emptyList()
    // Get user addresses so we can find the calendar member for current user
    val userAddresses = database.addressDao().getByUserId(UserId(this.first().fkUserId))
    return this.mapNotNull { calendarEntity ->
        val calendarSettings = database.calendarSettingsDao().select(calendarEntity.id) ?: return@mapNotNull null
        // Get all members for that calendar
        val calendarMembers = database.membersDao().selectCalendarMembers(calendarEntity.id)
        // Find the member that belongs to the current user
        val userMember = calendarMembers.getUserMember(userAddresses)
        // Map to Calendar
        userMember?.let { Calendar.from(calendarEntity, it, calendarSettings, json) }
    }
}

/**
 * Joins [CalendarEntity] with [MemberEntity] to [Calendar] object.
 */
suspend fun CalendarEntity?.joinToCalendar(database: AppDatabase, json: Json): Calendar? {
    return if (this == null) {
        null
    } else {
        // Get user addresses so we can find the calendar member for current user
        val userAddresses = database.addressDao().getByUserId(UserId(this.fkUserId))
        val calendarSettings = database.calendarSettingsDao().select(this.id) ?: return null
        // Get all members for that calendar
        val calendarMembers = database.membersDao().selectCalendarMembers(this.id)
        // Find the member that belongs to the current user
        val userMember = calendarMembers.getUserMember(userAddresses)
        // Map to Calendar
        userMember?.let { Calendar.from(this, it, calendarSettings, json) }
    }
}

/**
 * Find member that belongs to user
 */
private fun List<MemberEntity>.getUserMember(userAddresses: List<AddressEntity>): MemberEntity? {
    return this.firstOrNull { member ->
        userAddresses.any { userAddress ->
            if (!member.addressId.isNullOrEmpty()) {
                member.addressId.equalsNoCase(userAddress.addressId.id)
            } else {
                member.canonicalEmail.equalsNoCase(
                    canonicalizeProtonEmail(userAddress.email, forceCanonicalization = true)
                )
            }
        }
    }
}
