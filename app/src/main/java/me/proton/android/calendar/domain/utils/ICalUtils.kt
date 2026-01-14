package me.proton.android.calendar.domain.utils

import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.io.TimezoneInfo
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.property.ICalProperty
import biweekly.property.Organizer
import biweekly.property.RecurrenceRule
import biweekly.util.DayOfWeek
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.common.utils.CalendarSplit
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.model.UiEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

interface ICalUtils {
    fun parseICalString(iCalendar: String): ICalendar?
    fun normaliseICalendar(calendar: ICalendar)

    /**
     * Takes iCalendar parts split according to "the matrix" and returns one iCalendar object.
     */
    fun mergeCalendarPartsIntoICalendar(calendarStrings: List<String>): ICalendar?

    /**
     * Clones the ICalendar copying timezones.
     */
    fun ICalendar.clone(): ICalendar

    /**
     * @return true if Event is valid
     */
    fun VEvent.sanitise(): Boolean

    /**
     * Sanitise the event before sending it to BE or by email so that it matches RFC.
     */
    fun VEvent.sanitiseForExternal()

    /**
     * This methods clones Recurrence and overwrites only parameters supplied.
     */
    fun Recurrence.clone(
        byDay: List<DayOfWeek>? = null,
        bySetPos: List<Int>? = null,
        until: ICalDate? = null,
        workweekStarts: DayOfWeek? = null
    ): Recurrence

    fun ICalendar.adjustRRuleToStartDate(oldDateTime: ZonedDateTime? = null)
    fun RecurrenceRule.adjustToWeekStart(settingsWeekStart: java.time.DayOfWeek)
    fun VEvent.isDateTimeTheSame(that: VEvent?): Boolean
    fun ICalendar.isDateTimeTheSame(that: ICalendar?): Boolean
    fun ICalendar.iCalTimeZone(property: ICalProperty): TimeZone

    /**
     * Takes one iCalendar object and splits it according to "the matrix".
     */
    fun splitICalendarIntoParts(originalCalendar: ICalendar): CalendarSplit

    /**
     * The token is calculated by doing SHA1(EventUID + canonicalAttendeeAddress)
     */
    fun generateXPmToken(email: String, uid: String): String

    /**
     * This function merges only first top-level component of type VEvent.
     *
     * In the future we can extend this to support VTodo and custom components.
     */
    fun mergeICalendars(left: ICalendar, right: ICalendar) : ICalendar
    fun createNewVEvent(): VEvent
    fun generateEventStart(timeZoneId: ZoneId, selectedDate: LocalDate? = null): LocalDateTime

    /**
     * Generates Proton UID for new ICalendar components.
     */
    fun generateProtonUid(): String

    /**
     * Generates UID in the form of "original UID prefix + recurrenceId + original UID postfix (after @ symbol)".
     */
    fun generateProtonUid(originalUid: String, recurrenceId: String): String

    /**
     * Generates Proton UID for an imported event.
     */
    fun generateProtonUidForImport(originalEventUid: String?, ics: String): String

    /**
     * Generates Proton Product Identifier.
     */
    fun generateProtonProdId(): String

    /**
     * Generates offline CalendarID to use before it's successfully sent to server.
     */
    fun generateOfflineEventId(): String

    /**
     * Generates offline AlarmID for offline alarms calculated locally.
     */
    fun generateOfflineAlarmId(): String

    /**
     * Returns iCal Events with Occurrence, but does not overwrite the DTSTART/DTEND. See [withOccurrence]
     * Takes single edits into account.
     *
     * @param events all single edits selected by UID
     */
    fun expandOccurrencesWithSingleEdits(originalEvent: Event, events: List<Event>, toDate: LocalDate, timeZoneId: String): List<Event>?

    /**
     * Generated Event objects contain distinct Occurrence properties, but they point to the same ICalendar object!
     */
    fun expandOccurrencesWithSingleEdits(
        originalEvent: Event,
        eventsSharingUid: List<Event>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): List<Event>?

    /**
     * Creates ICalendar using only plaintext shared event part.
     */
    fun toICalendarFromPlaintextSharedPart(json: Json, sharedEvents: List<JsonElement>): ICalendar?

    /**
     * Given original Event, filter out all occurrences that are excluded by EXDATE
     */
    fun List<Event>.filterOutOccurrencesByExdates(originalEvent: Event, timeZoneId: String): List<Event>
    fun List<Event.Occurrence>.filterOutEventOccurrencesByExdates(originalEvent: Event, timeZoneId: String): List<Event.Occurrence>
    fun List<EventAlarmEntity>.filterOutDuplicates(): List<EventAlarmEntity>

    fun List<Event>.filterOutEventsBySearchTerm(searchTerm: String): List<Event>
    fun List<SearchEventEntity>.filterOutBySearchTerm(searchTerm: String): List<SearchEventEntity>

    /**
     * TODO this is a hack for hiding duplicated Events in Subscribed Calendars
     *
     * Events are considered duplicated if they have the same UID, RecurrenceID, CalendarID and Calendar is of type "subscribed" (1).
     *
     * Out of all duplicates, we return the one with highest "modifyTime" value.
     *
     * @return Pair<Unique Events, Duplicated Events>
     */
    fun List<SkeletonEvent>.filterOutDuplicatesInSubscribedCalendars(): Pair<List<SkeletonEvent>, List<SkeletonEvent>>

    /**
     * TODO this is a hack for hiding duplicated Events in Subscribed Calendars
     *
     * Events are considered duplicated if they have the same UID, RecurrenceID, CalendarID and Calendar is of type "subscribed" (1).
     *
     * Out of all duplicates, we return the one with highest "modifyTime" value.
     *
     * @return Pair<Unique Events, Duplicated Events>
     */
    fun List<EventEntityMetadata>.filterOutDuplicatesInSubscribedCalendars(
        calendars: List<Calendar>
    ): Pair<List<EventEntityMetadata>, List<EventEntityMetadata>>

    /**
     * Returns event ZonedDateTime on Date format
     * Converts it to default timezone when event is all day
     */
    fun eventStartZonedDateTimeToDate(startDate: ZonedDateTime, isAllDay: Boolean): Date
    fun calculateAlarmEntity(event: Event, vAlarm: VAlarm, timeZoneId: String, memberId: String): EventAlarmEntity

    /**
     * Calculates all Alarm Entities for any given Event occurrence in the format used in API.
     *
     * ID is generated locally.
     */
    fun calculateAlarmEntities(event: Event, timeZoneId: String, memberId: String): List<EventAlarmEntity>

    /**
     * Calculates upcoming Alarms (triggering starting from [now]) for the upcoming occurrences of all the events
     * supplied, filtered by exdates and single edits if they are in [events].
     *
     * If you need to refresh all alarms for an Event, it's best to supply here all the events in chain
     * (sharing the same UID).
     */
    fun calculateUpcomingAlarmEntities(events: List<Event>, now: ZonedDateTime, memberId: String
    ): List<EventAlarmEntity>

    /**
     * Filters out all Alarm Entities that are not of type DISPLAY.
     */
    fun List<EventAlarmEntity>.onlyDisplayType(): List<EventAlarmEntity>

    fun isCalendarChangeAllowed(from: Event, to: Event): Boolean

    /**
     * Creates new ICalendar object and sets this VEvent as only event.
     */
    fun VEvent.wrapInICalendar(): ICalendar

    // TODO we strip out "global timezone forward slash" manually, because for some requests server refuses to accept it
    fun ICalendar.printToString() : String
    fun VEvent.setStart(date: LocalDate)
    fun VEvent.setEnd(date: LocalDate)
    fun VEvent.setStart(date: LocalDate, time: LocalTime, timeZoneId: String? = "UTC")
    fun VEvent.setEnd(date: LocalDate, time: LocalTime, timeZoneId: String? = "UTC")
    fun VEvent.setStart(time: LocalTime, timeZoneId: String? = "UTC")
    fun VEvent.setEnd(time: LocalTime, timeZoneId: String? = "UTC")

    /**
     * Sets or clears TimeZone for Date Start.
     */
    fun ICalendar.setStartTimeZone(timeZoneId: String?)

    /**
     * Sets or clears TimeZone for Date End.
     */
    fun ICalendar.setEndTimeZone(timeZoneId: String?)
    fun ICalendar.setDefaultTimeZone(timeZoneId: String?)

    /**
     * Sets start and end timezones, preserving the original local datetimes.
     */
    fun ICalendar.adjustStartEndTimeZones(currentDateTimeTimezoneId: String, timeZoneId: String)

    /**
     * Removes Timezone Assignments and sets correct DTEND according to standard, not GUI form.
     *
     * @param timeZoneId needed to correctly interpret Dates if we are about to remove timezone info
     */
    fun ICalendar.adjustOutgoingAllDayEvent(timeZoneId: String)
    fun ICalendar.adjustIncomingAllDayEvent()
    fun VEvent.getStart(timeZoneId: String): ZonedDateTime?
    fun VEvent.getEnd(timeZoneId: String): ZonedDateTime?
    fun Attendee.extractEmail(): String?
    fun Organizer.extractEmail(): String?

    /**
     * Groups all-day and spanning multiple days Events first.
     */
    fun List<Event>.sortForAgendaView(timeZoneId: String): List<Event>

    fun List<UiEvent>.sortUiEventsForAgendaView(timeZoneId: String): List<UiEvent>

    /**
     * Sorts events with following order:
     * 1- All day spanning multiple days
     * 2- All day
     * 3- Partial day spanning multiple days
     * 4- Partial day
     */
    fun List<Event>.sortForMonthView(timeZoneId: String): List<Event>

    /**
     * Sorts events with following order:
     * 1- All day spanning multiple days
     * 2- All day
     * 3- Partial day spanning multiple days
     * 4- Partial day
     */
    fun List<UiEvent>.sortForMonthView(): List<UiEvent>

    /**
     * Copies all multi-day events across each day in the range, so we can display them day by day.
     */
    fun List<Event>.explodeEventDayByDay(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Map<LocalDate, List<Event>>

    fun List<UiEvent>.explodeDayByDay(fromDate: LocalDate, toDate: LocalDate, timeZoneId: String): Map<LocalDate, List<UiEvent>>

    /**
     * Filters out original Events that have occurrences with RECURRENCE-ID pointing to
     * that original Event.
     */
    fun List<Event>.filterOccurencesByRecurrenceId(): List<Event>
    fun formatUidForICal(eventUid: String): String
    fun getResponseIcs(
        responseICalendar: ICalendar,
        userAttendee: Attendee,
        participationStatus: ParticipationStatus,
        originalTimeZoneInfo: TimezoneInfo?,
        dtStamp: Date,
        isProtonProtonInvite: Boolean,
        sharedEventId: String? = null,
        sharedSessionKey: String? = null
    ): String

    fun getInviteIcs(
        newEvent: Event,
        sharedEventId: String,
        sharedSessionKey: String,
    ): String

    fun getCancelIcs(
        event: Event,
        sharedEventId: String
    ): String

    fun VAlarm.isTheSameAs(
        alarm: VAlarm
    ): Boolean

    fun Notification.isTheSameAs(
        notification: Notification
    ): Boolean

    fun List<VAlarm>.isTheSameAs(
        alarms: List<VAlarm>
    ): Boolean
}
