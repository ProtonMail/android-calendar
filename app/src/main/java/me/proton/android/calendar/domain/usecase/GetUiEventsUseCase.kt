package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.filterVisibleCalendars
import me.proton.core.domain.entity.UserId
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GetUiEventsUseCase @Inject constructor(
    private val database: AppDatabase,
    private val eventDecryptor: EventDecryptor,
    private val calendarsRepository: CalendarsRepository,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val loadingStateUseCase: LoadingStateUseCase,
) : UseCase {

    // Ensures parallel invocations await behind a mutex during their decrypt+expand stage, to allow the cache to get populated and avoid thread starvation
    private val mutex = Mutex()

    fun execute(
        userId: UserId,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
        onlyVisibleCalendars: Boolean = true
    ): Flow<CalendarsRepository.GetEventsResult<UiEvent>> {
        val eventsWindow = CalendarsRepository.EventsWindow(fromDate, toDate, timeZoneId)
        Timber.d("getUiEventsUseCase: $eventsWindow")

        val zoneId = ZoneId.of(timeZoneId)
        val fromEpochZdt = fromDate.atStartOfDay(zoneId)
        val toEpochZdt = toDate.plusDays(1).atStartOfDay(zoneId)
        val fromEpochSec = fromEpochZdt.toEpochSecond()
        val toEpochSec = toEpochZdt.toEpochSecond()

        return calendarsRepository.flowAllCalendars(userId.id)
            .map { if (onlyVisibleCalendars) it.filterVisibleCalendars() else it }
            .onEach { eventDecryptor.setCalendars(it) }
            .distinctUntilChanged()
            .debounceExceptFirst(1.seconds)
            .flatMapLatest { calendars ->
                val calendarIds = calendars.map { it.id }
                combine(
                    getUserInfoUseCase().debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // trivial case, only 1 row for each event, start + end times are well defined
                    database.eventOccurrencesDao().selectNonRecurringBetweenInclusive(
                        userId.id,
                        calendarIds,
                        fromEpochSec,
                        toEpochSec
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // we know first and last occurrence time, it can be selected like non-recurring above
                    database.eventOccurrencesDao().selectFiniteRecurring(
                        userId.id,
                        calendarIds,
                        fromEpochSec,
                        toEpochSec
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    // we don't know when the last occurrence happens, so we have to select all events
                    // except for the ones that start after our window
                    database.eventOccurrencesDao().selectInfiniteRecurring(
                        userId.id,
                        calendarIds,
                        toEpochSec
                    ).debounceExceptFirst(1.seconds).distinctUntilChanged()
                ) { userInfo, nonRecurring, finiteRecurring, infiniteRecurring ->
                    Timber.d("for $eventsWindow nonRecurring: ${nonRecurring.size}, finiteRecurring: ${finiteRecurring.size}, infiniteRecurring: ${infiniteRecurring.size}")
                    withContext(Dispatchers.Default) {
                        // potential optimization: we have rrule, we can generate occurrences quickly without decrypting the event
                        // to filter out even more events here, before decryption takes place

                        // potential optimizations, but debatable if we're not dealing with huge amount of infinitely recurring events:
                        //  - select from by windowStart and windowEnd, only the rows that overlap with eventsWindow (problem: false negatives if the window was never generated for those events)
                        //  - groupBy events before performing discarding below and early-return for each event that for sure happens or doesn't happen in the window (problem: higher memory usage)

                        // infinite recurring that don't occur in <from,to>
                        val notOccurring = infiniteRecurring.filter { occurrence ->
                            val windowPair = fromEpochSec to toEpochSec
                            val occPair = occurrence.windowStartTime to occurrence.windowEndTime
                            val isSelectedWindowFullyInOccurrenceWindow = isFullyBetween(windowPair, occPair)
                            val isEventNotHappeningInOccurrenceWindow = occurrence.startTime == null
                            val isFirstEventOccurenceAfterSelectedWindow = (occurrence.startTime ?: 0) > toEpochSec

                            isSelectedWindowFullyInOccurrenceWindow && (isEventNotHappeningInOccurrenceWindow || isFirstEventOccurenceAfterSelectedWindow)
                        }

                        val filteredInfiniteRecurring = infiniteRecurring.filterNot { recurring ->
                            notOccurring.any {
                                recurring.userId == it.userId &&
                                        recurring.calendarId == it.calendarId &&
                                        recurring.eventId == it.eventId
                            }
                        }

                        val neededUids: Set<String> =
                            (finiteRecurring.asSequence().map { it.eventUid } +
                                    filteredInfiniteRecurring.asSequence().map { it.eventUid })
                                .distinct()
                                .toSet()

                        val byUidMap = calendarsRepository.selectEventEntitiesByUids(neededUids)

                        suspend fun fetchEventFor(occ: EventOccurrenceEntity): Event? =
                            (eventDecryptor.getFromCache(occ.eventId, occ.calendarId, occ.modifyTime)
                                ?: database.eventsDao().selectById(occ.eventId)?.let { eventDecryptor.decrypt(it) })
                                ?.takeIf { it.decryptionStatus != Event.DecryptionStatus.Failure.NoAddressKey }

                        mutex.withLock {
                            val transformedNonRecurring =
                                nonRecurring.parallelMap { occ ->
                                    fetchEventFor(occ)?.toUiEvent(
                                        userEmails = userInfo.emails,
                                        timeZoneId = timeZoneId,
                                        isFreeUser = userInfo.hasSubscriptionForMail.not()
                                    )
                                }

                            val transformedFiniteRecurring =
                                finiteRecurring.parallelMap { occ ->
                                    fetchEventFor(occ)?.let { decr ->
                                        val eventsSharingUid = byUidMap[occ.eventUid]?.mapNotNull {
                                            eventDecryptor.decrypt(it)
                                        }.orEmpty()

                                        calendarsRepository
                                            .expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                                                originalEvent = decr,
                                                eventsSharingUid = eventsSharingUid,
                                                fromDate = eventsWindow.fromDate,
                                                toDate = eventsWindow.toDate,
                                                timeZoneId = eventsWindow.timeZoneId,
                                                userEmails = userInfo.emails,
                                                isFreeUser = userInfo.hasSubscriptionForMail.not()
                                            )
                                    }
                                }.flatten()

                            val transformedInfiniteRecurring =
                                filteredInfiniteRecurring.parallelMap { occ ->
                                        fetchEventFor(occ)?.let { decr ->
                                            val eventsSharingUid = byUidMap[occ.eventUid]?.mapNotNull {
                                                eventDecryptor.decrypt(it)
                                            }.orEmpty()

                                            calendarsRepository
                                                .expandOccurrencesWithSingleEditsAndExDatesToUiEvents(
                                                    originalEvent = decr,
                                                    eventsSharingUid = eventsSharingUid,
                                                    fromDate = eventsWindow.fromDate,
                                                    toDate = eventsWindow.toDate,
                                                    timeZoneId = eventsWindow.timeZoneId,
                                                    userEmails = userInfo.emails,
                                                    isFreeUser = userInfo.hasSubscriptionForMail.not()
                                                )
                                        }
                                    }.flatten()
                            (transformedNonRecurring + transformedFiniteRecurring + transformedInfiniteRecurring).distinct()
                        }
                    }
                }
            }
            .combine(loadingStateUseCase.invoke().map { it.inProgress() }
                .distinctUntilChanged()) { events, inProgress ->
                CalendarsRepository.GetEventsResult.Success(events, fullyLoaded = inProgress.not())
            }
    }

    /**
     * If [fromEpoch] -- [toEpoch] is fully between [windowStartTime] -- [windowEndTime]
     */
    private fun isFullyBetween(smallerWindow: Pair<Long, Long>, largerWindow: Pair<Long, Long>): Boolean {
        return smallerWindow.first >= largerWindow.first && smallerWindow.second <= largerWindow.second
    }

    private fun defaultConcurrency() = when (val cores = Runtime.getRuntime().availableProcessors()) {
        1, 2, 3, 4 -> cores
        // likely a big.LITTLE cluster of 2 big + N small.
        // In practice it works better to constrain this, for different reasons -
        // i.e. we need to await the first "long" call, which may "land" on a little core, and also the cluster shares resources amongst cores
        else -> cores / 2
    }.coerceAtLeast(1)

    private suspend fun <T, R> List<T>.parallelMap(
        concurrency: Int = defaultConcurrency(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(concurrency),
        transform: suspend (T) -> R?,
    ): List<R> = coroutineScope {
        if (this@parallelMap.isEmpty()) return@coroutineScope emptyList()
        val chunkSize = (size + concurrency - 1) / concurrency
        chunked(chunkSize).map { chunk ->
            async(dispatcher) { chunk.mapNotNull { transform(it) } }
        }.awaitAll().flatten()
    }
}
