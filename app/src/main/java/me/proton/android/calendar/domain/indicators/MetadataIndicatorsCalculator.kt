package me.proton.android.calendar.domain.indicators

import biweekly.property.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.proton.android.calendar.common.MAX_CALENDAR_INDICATORS
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.KotlinUtilsImpl.debounceExceptFirst
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.EventKey
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.model.filterVisibleCalendars
import me.proton.android.calendar.domain.usecase.GetUserInfoUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Computes mini-calendar indicator dots/skeleton from server-plaintext data
 * (no decryption for near-immediate UI feedback)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MetadataIndicatorsCalculator @Inject constructor(
    private val database: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val getUserInfoUseCase: GetUserInfoUseCase,
    private val logger: Logger,
) {

    // one occurrence of an event resolved from metadata (no decryption)
    private data class SkeletonOccurrence(
        val eventId: String,
        val calendarId: String,
        val uid: String,
        val color: String,
        val fullDay: Boolean,
        val startInstant: Instant,
        val endInstant: Instant,
        val occurrenceNumber: Int,
    )

    // mirrors CalendarViewModel.calendarIndicators but skips decryption
    fun compute(
        userId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): Flow<Map<LocalDate, List<String>>> =
        observeSkeletonOccurrences(userId, fromDate, toDate, timeZoneId)
            .map { occurrences -> withContext(Dispatchers.Default) { occurrencesToIndicators(occurrences, timeZoneId) } }
            .distinctUntilChanged()

    // mirrors GetUiEventsUseCase.execute but skips decryption
    suspend fun computeSkeletonUiEvents(
        userId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): List<UiEvent> = withContext(Dispatchers.Default) {
        val zone = ZoneId.of(timeZoneId)
        observeSkeletonOccurrences(userId, fromDate, toDate, timeZoneId).first().map { occ ->
            // all-day is anchored at UTC midnight; read in UTC so the day doesn't shift with display tz
            val occZone = if (occ.fullDay) ZoneOffset.UTC else zone
            UiEvent(
                id = occ.eventId,
                calendarId = occ.calendarId,
                uid = occ.uid,
                summary = null,
                location = null,
                description = null,
                dateStart = occ.startInstant.atZone(occZone),
                dateEnd = occ.endInstant.atZone(occZone),
                isAllDay = occ.fullDay,
                occurrenceNumber = occ.occurrenceNumber,
                displayColor = occ.color,
                decryptionStatus = Event.DecryptionStatus.Success,
                participationStatus = null,
                status = Status.confirmed(),
                isSkeleton = true,
            )
        }.distinct() // mirror GetUiEventsUseCase's .distinct() so skeleton rows align with the real ones
    }

    private fun occurrencesToIndicators(
        occurrences: List<SkeletonOccurrence>,
        timeZoneId: String,
    ): Map<LocalDate, List<String>> {
        val indicators = mutableMapOf<LocalDate, MutableList<String>>()
        for (occ in occurrences) {
            addSpannedDays(
                startInstant = occ.startInstant,
                endInstant = occ.endInstant,
                isAllDay = occ.fullDay,
                timeZoneId = timeZoneId,
                color = occ.color,
                indicators = indicators,
            )
        }
        return indicators.mapValues { it.value.toList().sorted().take(MAX_CALENDAR_INDICATORS) }
    }

    private fun observeSkeletonOccurrences(
        userId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): Flow<List<SkeletonOccurrence>> {
        val (fromEpoch, toEpoch) = windowEpochs(fromDate, toDate, timeZoneId)
        return calendarsRepository.flowAllCalendars(userId)
            .map { it.filterVisibleCalendars() }
            .distinctUntilChanged()
            .debounceExceptFirst(1.seconds)
            .flatMapLatest { visibleCalendars ->
                if (visibleCalendars.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val calendarIds = visibleCalendars.map { it.id }
                    combine(
                        database.eventOccurrencesDao()
                            .selectNonRecurringBetweenInclusive(userId, calendarIds, fromEpoch, toEpoch)
                            .debounceExceptFirst(1.seconds).distinctUntilChanged(),
                        database.eventOccurrencesDao()
                            .selectFiniteRecurring(userId, calendarIds, fromEpoch, toEpoch)
                            .debounceExceptFirst(1.seconds).distinctUntilChanged(),
                        database.eventOccurrencesDao()
                            .selectInfiniteRecurring(userId, calendarIds, toEpoch)
                            .debounceExceptFirst(1.seconds).distinctUntilChanged(),
                        database.eventOccurrencesDao()
                            .selectSingleEdits(userId, calendarIds)
                            .debounceExceptFirst(1.seconds).distinctUntilChanged(),
                        getUserInfoUseCase()
                            .debounceExceptFirst(1.seconds).distinctUntilChanged(),
                    ) { nonRecurring, finiteRecurring, infiniteRecurringRaw, allSingleEdits, userInfo ->
                        withContext(Dispatchers.Default) {
                            buildOccurrences(
                                visibleCalendars = visibleCalendars,
                                nonRecurring = nonRecurring,
                                finiteRecurring = finiteRecurring,
                                infiniteRecurringRaw = infiniteRecurringRaw,
                                allSingleEdits = allSingleEdits,
                                isFreeUser = !userInfo.hasSubscriptionForMail,
                                ownAddressIds = userInfo.addresses.map { it.addressId.id }.toSet(),
                                fromDate = fromDate,
                                toDate = toDate,
                                timeZoneId = timeZoneId,
                            )
                        }
                    }
                }
            }
    }

    private fun windowEpochs(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Pair<Long, Long> {
        val zone = ZoneId.of(timeZoneId)
        val fromEpoch = fromDate.atStartOfDay(zone).toInstant().epochSecond
        val toEpoch = toDate.plusDays(1).atStartOfDay(zone).toInstant().epochSecond
        return fromEpoch to toEpoch
    }

    // mirrors GetUiEventsUseCase for row filter and expansion
    private fun buildOccurrences(
        visibleCalendars: List<Calendar>,
        nonRecurring: List<EventOccurrenceEntity>,
        finiteRecurring: List<EventOccurrenceEntity>,
        infiniteRecurringRaw: List<EventOccurrenceEntity>,
        allSingleEdits: List<EventOccurrenceEntity>,
        isFreeUser: Boolean,
        ownAddressIds: Set<String>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String,
    ): List<SkeletonOccurrence> {
        val zone = ZoneId.of(timeZoneId)
        val fromInstant = fromDate.atStartOfDay(zone).toInstant()
        val toEndInstant = toDate.plusDays(1).atStartOfDay(zone).toInstant()
        val fromEpoch = fromInstant.epochSecond
        val toEpoch = toEndInstant.epochSecond

        val colorByCalendarId = visibleCalendars.associate { it.id to it.color }

        val notOccurring = infiniteRecurringRaw.filter { occ ->
            val isSelectedWindowFullyInOccurrenceWindow =
                fromEpoch >= occ.windowStartTime && toEpoch <= occ.windowEndTime
            val isEventNotHappeningInOccurrenceWindow = occ.startTime == null
            val isFirstEventOccurrenceAfterSelectedWindow = (occ.startTime ?: 0) > toEpoch
            isSelectedWindowFullyInOccurrenceWindow &&
                (isEventNotHappeningInOccurrenceWindow || isFirstEventOccurrenceAfterSelectedWindow)
        }
        val infiniteRecurring = infiniteRecurringRaw.filterNot { recurring ->
            notOccurring.any {
                recurring.userId == it.userId &&
                    recurring.calendarId == it.calendarId &&
                    recurring.eventId == it.eventId
            }
        }

        val allRows = nonRecurring + finiteRecurring + infiniteRecurring
        val byUid = allRows.groupBy { it.eventUid }
        val singleEditsByUid = allSingleEdits.groupBy { it.eventUid }

        // per-event color overrides
        val eventColorByKey: Map<EventKey, String?> = if (isFreeUser) {
            emptyMap()
        } else {
            val eventIds = allRows.map { it.eventId }.distinct()
            if (eventIds.isEmpty()) emptyMap()
            else database.eventsDao().selectEventColors(eventIds)
                .associate { EventKey(it.id, it.calendarId) to it.color }
        }

        // an event locked to an address key we don't hold never decrypts, so it must not get a dot either
        val unreadableKeys: Set<EventKey> = allRows.map { it.eventId }.distinct()
            .let { if (it.isEmpty()) emptyList() else database.eventsDao().selectEventAddressKeys(it) }
            .filter { it.isUnreadableBy(ownAddressIds) }
            .map { EventKey(it.id, it.calendarId) }
            .toSet()

        fun resolveColor(eventId: String, calendarId: String): String? {
            val calColor = colorByCalendarId[calendarId] ?: return null
            if (isFreeUser) return calColor
            return eventColorByKey[EventKey(eventId, calendarId)] ?: calColor
        }

        val result = mutableListOf<SkeletonOccurrence>()

        for ((uid, group) in byUid) {
            val parents = group.filter { it.recurrenceID == null }
            val overrides = group.filter { it.recurrenceID != null }
            // mask with all single edits of this uid, not just the windowed ones, like the decrypt path does
            val overrideRecurrenceInstants = (overrides + singleEditsByUid[uid].orEmpty())
                .mapNotNull { it.recurrenceID }
                .map { Instant.ofEpochSecond(it) }
                .toSet()

            val parentByEvent = parents.groupBy { it.eventId to it.calendarId }
            for ((_, rows) in parentByEvent) {
                val rep = rows.firstOrNull() ?: continue
                if (EventKey(rep.eventId, rep.calendarId) in unreadableKeys) continue
                val color = resolveColor(rep.eventId, rep.calendarId) ?: continue

                if (rep.rRule == null) {
                    // non-recurring parent: use the row's start/end directly
                    val startSec = rep.startTime ?: continue
                    val endSec = rep.endTime ?: continue
                    result.add(
                        SkeletonOccurrence(
                            eventId = rep.eventId,
                            calendarId = rep.calendarId,
                            uid = rep.eventUid,
                            color = color,
                            fullDay = rep.fullDay != 0,
                            startInstant = Instant.ofEpochSecond(startSec),
                            endInstant = Instant.ofEpochSecond(endSec),
                            occurrenceNumber = 0,
                        )
                    )
                } else {
                    // recurring parent: expand RRULE in the event's ORIGINAL timezone, filter by EXDATE and override RECURRENCE-IDs
                    val rrule = rep.rRule
                    // all-day is anchored at UTC midnight; derive its date in UTC so it can't shift a day
                    val expansionTz = if (rep.fullDay != 0) "UTC" else rep.startTimeZone.ifBlank { timeZoneId }

                    val sample = rows.firstOrNull { it.startTime != null && it.endTime != null }
                        ?: continue
                    val durationSec = sample.endTime!! - sample.startTime!!

                    val dtStart = Instant.ofEpochSecond(rep.firstOccurrenceStartTime)
                    val dtEnd = Instant.ofEpochSecond(rep.firstOccurrenceStartTime + durationSec)

                    val exDateInstants = rep.exDates
                        .map { Instant.ofEpochSecond(it) }
                        .toSet()

                    val occurrences = expandRecurring(
                        rrule = rrule,
                        dtStartInstant = dtStart,
                        dtEndInstant = dtEnd,
                        fullDay = rep.fullDay != 0,
                        expansionTimeZoneId = expansionTz,
                        // arithmetic generators / generateOccurrencesUntil localize to displayTz, which is what we want
                        formatTimeZoneId = timeZoneId,
                        fromDate = fromDate,
                        toDate = toDate,
                    ) ?: continue

                    for (occ in occurrences) {
                        val sInst = occ.start
                        if (sInst in exDateInstants) continue
                        if (sInst in overrideRecurrenceInstants) continue
                        val eInst = occ.end
                        if (!eInst.isAfter(fromInstant) || !sInst.isBefore(toEndInstant)) continue

                        result.add(
                            SkeletonOccurrence(
                                eventId = rep.eventId,
                                calendarId = rep.calendarId,
                                uid = rep.eventUid,
                                color = color,
                                fullDay = rep.fullDay != 0,
                                startInstant = sInst,
                                endInstant = eInst,
                                // absolute, DTSTART-relative number (matches the real expansion path)
                                occurrenceNumber = occ.number,
                            )
                        )
                    }
                }
            }

            // override rows (single edits)
            for (override in overrides) {
                if (EventKey(override.eventId, override.calendarId) in unreadableKeys) continue
                val color = resolveColor(override.eventId, override.calendarId) ?: continue
                val startSec = override.startTime ?: continue
                val endSec = override.endTime ?: continue
                result.add(
                    SkeletonOccurrence(
                        eventId = override.eventId,
                        calendarId = override.calendarId,
                        uid = override.eventUid,
                        color = color,
                        fullDay = override.fullDay != 0,
                        startInstant = Instant.ofEpochSecond(startSec),
                        endInstant = Instant.ofEpochSecond(endSec),
                        occurrenceNumber = 0,
                    )
                )
            }
        }

        return result
    }

    // same rule as UiEvent.spansSingleDay
    private fun addSpannedDays(
        startInstant: Instant,
        endInstant: Instant,
        isAllDay: Boolean,
        timeZoneId: String,
        color: String,
        indicators: MutableMap<LocalDate, MutableList<String>>,
    ) {
        // all-day events are stored at UTC midnight; read their day in UTC so the display tz can't shift it
        val zone = if (isAllDay) ZoneOffset.UTC else ZoneId.of(timeZoneId)
        val startLocal = startInstant.atZone(zone)
        val endLocal = endInstant.atZone(zone)
        val partTimeEndsOnMidnight = !isAllDay && endLocal.toLocalTime() == LocalTime.MIDNIGHT

        var day = startLocal.toLocalDate()
        val endDay = endLocal.toLocalDate()
        while (!day.isAfter(endDay)) {
            indicators.getOrPut(day) { mutableListOf() }.add(color)
            day = day.plusDays(1)
            if (day == endDay && (isAllDay || partTimeEndsOnMidnight)) break
        }
    }

    // mirrors expandOccurrencesWithSingleEditsAndExDatesToUiEvents with a fake VEVENT
    private fun expandRecurring(
        rrule: String,
        dtStartInstant: Instant,
        dtEndInstant: Instant,
        fullDay: Boolean,
        expansionTimeZoneId: String,
        formatTimeZoneId: String,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<ExpandedOccurrence>? {
        val recurrence = ICalUtilsImpl.parseRecurrence(rrule) ?: return null
        val expansionZone = ZoneId.of(expansionTimeZoneId)
        val displayZone = ZoneId.of(formatTimeZoneId)
        val durationSec = dtEndInstant.epochSecond - dtStartInstant.epochSecond

        // the common repeating events (plain daily and weekly-on-weekdays) take the fast date-math path
        val winStart = fromDate.atStartOfDay(displayZone).toInstant()
        val winEnd = toDate.plusDays(1).atStartOfDay(displayZone).toInstant()
        FastWindowExpansion.occurrencesInWindow(
            recurrence, dtStartInstant, durationSec, fullDay, expansionZone, winStart, winEnd,
        )?.let { return it }

        // everything else (monthly, yearly, anything with COUNT/UNTIL or fancier BY* rules) is rarer and cheaper:
        // build a throwaway event and let biweekly expand it. the caller trims the result to the window.
        val startLocal = dtStartInstant.atZone(expansionZone).toLocalDateTime()
        val endLocalRaw = dtEndInstant.atZone(expansionZone).toLocalDateTime()
        val endLocal = if (!fullDay && endLocalRaw.isBefore(startLocal)) {
            endLocalRaw.plusDays(1)
        } else endLocalRaw

        val dummy = generateDummyEvent(startLocal, endLocal, rrule, expansionZone, fullDay) ?: return null
        val occurrences = dummy.generateOccurrencesUntil(toDate, formatTimeZoneId) ?: return null
        return occurrences.map { occ ->
            if (fullDay) {
                // all-day occurrences come back at display-tz midnight; re-anchor to UTC midnight like the stored rows
                ExpandedOccurrence(
                    occ.startDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                    occ.endDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                    occ.occurrenceNumber,
                )
            } else {
                ExpandedOccurrence(occ.startDateTime.toInstant(), occ.endDateTime.toInstant(), occ.occurrenceNumber)
            }
        }
    }

    private fun generateDummyEvent(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        rrule: String,
        zoneId: ZoneId,
        fullDay: Boolean,
    ): Event? {
        val iCalString = if (fullDay) {
            val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            // VALUE=DATE -> isAllDay()=true
            """
                BEGIN:VCALENDAR
                PRODID:0
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART;VALUE=DATE:${startLocal.toLocalDate().format(dateFormatter)}
                DTEND;VALUE=DATE:${endLocal.toLocalDate().format(dateFormatter)}
                RRULE:$rrule
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()
        } else {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
            """
                BEGIN:VCALENDAR
                PRODID:0
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART;TZID=${zoneId.id}:${startLocal.format(formatter)}
                DTEND;TZID=${zoneId.id}:${endLocal.format(formatter)}
                RRULE:$rrule
                END:VEVENT
                END:VCALENDAR
            """.trimIndent()
        }
        return try {
            Event.dummyFrom(ICalUtilsImpl.parseICalString(iCalString)!!)
        } catch (t: Throwable) {
            logger.e("MetadataIndicatorsCalculator: failed to build dummy iCal for RRULE='$rrule'", t)
            null
        }
    }
}
