package me.proton.android.calendar.domain.usecase

import androidx.work.WorkManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.common.worker.GetMinimalCalendarEventsWorker
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.ServerEventsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.UserAddress
import javax.inject.Inject

/**
 * Sets up all the user's calendars, call this only once after successful login.
 */
class BootstrapCalendarUseCase @Inject constructor( // TODO TEST
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase,
    private val serverEventsApi: ServerEventsApi,
    private val valueStoreProvider: ValueStoreProvider,
    private val workManager: WorkManager
): UseCase {

    companion object {
        const val BOOTSTRAP_CALENDARS = "BOOTSTRAP_CALENDARS"
        const val BOOTSTRAP_ALL_CALENDARS = "BOOTSTRAP_ALL_CALENDARS"
    }

    suspend fun executeBootstrap(
        calendarEntity: CalendarEntity,
        userId: UserId,
        addresses: List<UserAddress>? = null
    ): UseCase.Result {
        when (val bootstrapResponse = calendarsApi.getBootstrap(userId, calendarEntity.id)) {
            is ApiResponse.Success -> {
                logger.v("got successful bootstrap response for calendar ${calendarEntity.id}")
                calendarsRepository.apply {
                    persistCalendar(userId.id, calendarEntity)
                    persistCalendarSettings(bootstrapResponse.data.calendarSettings)
                    if (calendarEntity.isSubscribed && bootstrapResponse.data.calendarSubscriptionEntity != null) {
                        persistCalendarSubscription(bootstrapResponse.data.calendarSubscriptionEntity)
                    } else if (calendarEntity.isSubscribed && bootstrapResponse.data.calendarSubscriptionEntity == null) {
                        logger.e("BootstrapCalendarsUseCase: calendarSubscriptionEntity was null")
                    }
                    persistCalendarPassphrase(bootstrapResponse.data.passphrase)
                    bootstrapResponse.data.keys.forEach { persistCalendarKey(it) }
                    bootstrapResponse.data.members.forEach { persistMember(it) }
                }

                // TODO cached calendar passphrase will be invalidated when I reset my password and I was the only member of this calendar
                // when calendar is shared it might get invalidated while I'm still logged in -- you can have only 1 ACTIVE calendar passphrase
                // for this calendar at the same time, this will be sent in the event loop automatically

                // extract passphrase for just saved Calendar
                when (val cachePassphraseResult = cacheCalendarPassphraseUseCase.execute(userId, calendarEntity.id)) {
                    is UseCase.Result.Success<*> -> {

                        // fetch events
                        val calendarMembers = bootstrapResponse.data.members.filter { it.calendarId == calendarEntity.id }
                        val userMember = addresses?.let {
                            calendarsRepository.getUserMember(
                                it,
                                calendarMembers
                            )
                        } ?: calendarMembers.first()
                        if (userMember.display == 1) {
                            // Launch worker to fetch minimal events for calendar
                            GetMinimalCalendarEventsWorker.enqueue(workManager, userId = userId.id, calendarId = calendarEntity.id)
                        }

                        // for each bootstrapped calendar, get its latest Event ID
                        val valueStore = valueStoreProvider.provideValueStore(userId.id)
                        val latestEventIdResponse = serverEventsApi.getLatestServerCalendarEvent(userId, calendarEntity.id)
                        if (latestEventIdResponse is ApiResponse.Success) {
                            valueStore.putStringInSet(ValueSet.LAST_SERVER_CALENDAR_EVENT_ID, calendarEntity.id, latestEventIdResponse.data.calendarEventId)
                        } else {
                            logger.e("could not get latest calendar server event ID response in BootstrapCalendarsUseCase")
                        }

                        return UseCase.Result.Success<Unit>()
                    }
                    is UseCase.Result.InvalidParams -> {
                        return UseCase.Result.InvalidParams("BootstrapCalendarsUseCase: cachePassphraseResult invalid params: ${cachePassphraseResult.message}")
                    }
                    is UseCase.Result.Error -> {
                        return UseCase.Result.Error("BootstrapCalendarsUseCase: cachePassphraseResult error: ${cachePassphraseResult.message}")
                    }
                }
            }
            is ApiResponse.Error -> {
                return UseCase.Result.Error("BootstrapCalendarsUseCase: api error getting calendar bootstrap: $bootstrapResponse")
            }
            is ApiResponse.Exception -> {
                return UseCase.Result.Error("BootstrapCalendarsUseCase: api exception getting calendar bootstrap: $bootstrapResponse")
            }
        }
    }

    suspend fun executeCalendarsBootstrap(userId: UserId, calendarIds: List<String>): UseCase.Result {
        val failedBootstraps = arrayListOf<String>()
        coroutineScope {
            calendarIds.map {
                async {
                    calendarsRepository.selectCalendarEntity(it)?.let { calendarEntity ->
                        val executeBootstrapResult = executeBootstrap(calendarEntity, userId)
                        // We don't want the result to be blocking
                        executeBootstrapResult.ifSuccessAndLogErrors(logger) { }
                        if (executeBootstrapResult !is UseCase.Result.Success<*>) failedBootstraps.add(it)
                    } ?: run {
                        logger.i("BootstrapCalendarUseCase failed to select calendar to bootstrap from DB")
                        failedBootstraps.add(it)
                    }
                }
            }.awaitAll()
        }

        return if (failedBootstraps.isEmpty()) {
            UseCase.Result.Success<Unit>()
        } else {
            logger.i("BootstrapCalendarUseCase executeCalendarsBootstrap failed to bootstrap some calendars")
            UseCase.Result.Error("BootstrapCalendarUseCase executeCalendarsBootstrap failed to bootstrap some calendars")
        }
    }

    suspend fun executeAllCalendarsBootstrap(userId: UserId): UseCase.Result {
        val allCalendarEntities = calendarsRepository.fetchCalendarEntities(userId)
            ?: return UseCase.Result.Error("BootstrapCalendarsUseCase: error getting Calendar Entities from API")

        val failedBootstraps = arrayListOf<String>()
        coroutineScope {
            allCalendarEntities.map { calendarEntity ->
                async {
                    // TODO Test this without fetchEvents to see if we somehow already refresh current views events
                    val executeBootstrapResult = executeBootstrap(calendarEntity, userId)
                    // We don't want the result to be blocking
                    executeBootstrapResult.ifSuccessAndLogErrors(logger) { }
                    if (executeBootstrapResult !is UseCase.Result.Success<*>) failedBootstraps.add(calendarEntity.id)
                }
            }.awaitAll()
        }

        return if (failedBootstraps.isEmpty()) {
            UseCase.Result.Success<Unit>()
        } else {
            UseCase.Result.Error("BootstrapCalendarUseCase executeAllCalendarsBootstrap failed to bootstrap some calendars")
        }
    }
}
