package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.overlaps
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.EventOccurrenceEntity
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * This needs to be called when EventEntity is already saved in DB, otherwise foreign key will fail!
 */
class UpdateEventOccurrencesUseCase @Inject constructor(
    private val logger: Logger,
    private val insertEventOccurrencesUseCase: InsertEventOccurrencesUseCase,
) : UseCase {

    private val generatedWindowsCount = 24L

    suspend fun execute(
        userId: String,
        eventEntityMetadata: EventEntityMetadata,
        forceReload: Boolean = false,
    ) {
        val isNonRecurring = eventEntityMetadata.rRule == null
        val forceReloadRecurring = !isNonRecurring && forceReload
        // early return if there is no need to update the Occurrences
        if (!forceReloadRecurring && insertEventOccurrencesUseCase.needsRefresh(userId, eventEntityMetadata)) return

        val eventOccurrenceEntities = if (isNonRecurring) { // normal event or single edit
            listOf(
                EventOccurrenceEntity(
                    userId = userId,
                    calendarId = eventEntityMetadata.calendarId,
                    eventId = eventEntityMetadata.id,
                    eventUid = eventEntityMetadata.uid,
                    fullDay = eventEntityMetadata.fullDay,
                    startTime = eventEntityMetadata.startTime,
                    endTime = eventEntityMetadata.endTime,
                    windowStartTime = eventEntityMetadata.startTime,
                    windowEndTime = eventEntityMetadata.endTime,
                    modifyTime = eventEntityMetadata.modifyTime,
                    firstOccurrenceStartTime = eventEntityMetadata.startTime
                )
            )
        } else { // recurring event
            val zoneId = ZoneId.of(eventEntityMetadata.startTimeZone)
            val startLocal = Instant.ofEpochSecond(eventEntityMetadata.startTime).atZone(zoneId)
                .toLocalDateTime()
            val endLocalRaw =
                Instant.ofEpochSecond(eventEntityMetadata.endTime).atZone(zoneId).toLocalDateTime()
            // handle overnight ranges (end <= start -> next day)
            val endLocal =
                if (eventEntityMetadata.fullDay == 0 && !endLocalRaw.isAfter(startLocal)) {
                    endLocalRaw.plusDays(1)
                } else endLocalRaw

            val dummyEventForOccurrences =
                generateDummyEventForOccurrences(
                    startLocal = startLocal,
                    endLocal = endLocal,
                    rrule = eventEntityMetadata.rRule,
                    zoneId = zoneId,
                )

            val startOfEventsFirstMonth = startLocal.withDayOfMonth(1).toLocalDate()

            val occurrences = dummyEventForOccurrences.generateOccurrencesUntil(
                startOfEventsFirstMonth.plusMonths(generatedWindowsCount + 1).minusDays(1),
                zoneId.id
            )

            val firstOccurrenceAfterWindows = dummyEventForOccurrences.generateFirstOccurrenceSince(
                startOfEventsFirstMonth.plusMonths(generatedWindowsCount + 1).atStartOfDay(zoneId)
            )

            val firstOccurrenceStartTime = occurrences?.firstOrNull()?.startDateTime?.toEpochSecond() ?: run {
                logger.e("UpdateEventOccurrencesUseCase.execute() failed to get first occurrence, RRULE: ${eventEntityMetadata.rRule}")
                return
            }

            val lastOccurrenceEndTime = if (firstOccurrenceAfterWindows == null) {
                occurrences.lastOrNull()?.endDateTime?.toEpochSecond()
            } else null

            val eventOccurrenceEntities = mutableListOf<EventOccurrenceEntity>()

            for (i in 0..generatedWindowsCount) {
                val windowStart = startOfEventsFirstMonth.plusMonths(i).atStartOfDay(zoneId)
                val windowEnd = windowStart.toLocalDate().plusMonths(1).atStartOfDay(zoneId).minusSeconds(1)

                // first generated occurrence should be happening in the first window for sure

                // this can be one of many occurrences in a given window but we only care about hit inside window, not particular start/end-times
                val occurrenceInThisWindow = occurrences.find {
                    Pair(it.startDateTime, it.endDateTime).overlaps(Pair(windowStart, windowEnd))
                }

                // don't save unnecessary occurrences if their window starts after last occurrence
                val isWindowAfterLastOccurrence =
                    lastOccurrenceEndTime != null && windowStart.toEpochSecond() > lastOccurrenceEndTime

                if (occurrenceInThisWindow != null || isWindowAfterLastOccurrence.not()) {
                    eventOccurrenceEntities.add(
                        // if there is no occurrence in this window, startTime and endTime will be null -- this is a useful information for lookup
                        EventOccurrenceEntity(
                            userId = userId,
                            calendarId = eventEntityMetadata.calendarId,
                            eventId = eventEntityMetadata.id,
                            eventUid = eventEntityMetadata.uid,
                            fullDay = eventEntityMetadata.fullDay,
                            startTime = occurrenceInThisWindow?.startDateTime?.toEpochSecond(),
                            endTime = occurrenceInThisWindow?.endDateTime?.toEpochSecond(),
                            rRule = eventEntityMetadata.rRule,
                            windowStartTime = windowStart.toEpochSecond(),
                            windowEndTime = windowEnd.toEpochSecond(),
                            firstOccurrenceStartTime = firstOccurrenceStartTime,
                            lastOccurrenceEndTime = lastOccurrenceEndTime,
                            modifyTime = eventEntityMetadata.modifyTime
                        )
                    )
                }
            }
            eventOccurrenceEntities
        }
        insertEventOccurrencesUseCase.execute(userId, eventEntityMetadata, eventOccurrenceEntities)
    }

    private fun generateDummyEventForOccurrences(
        startLocal: LocalDateTime,
        endLocal: LocalDateTime,
        rrule: String,
        zoneId: ZoneId,
    ): Event {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

        val iCalString = """
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

        return Event.dummyFrom(ICalUtilsImpl.parseICalString(iCalString)!!)!!
    }
}
