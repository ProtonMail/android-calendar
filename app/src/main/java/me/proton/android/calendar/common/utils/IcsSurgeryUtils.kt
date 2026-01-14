package me.proton.android.calendar.common.utils

import biweekly.Biweekly
import biweekly.ICalVersion
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.io.TimezoneAssignment
import biweekly.parameter.ParticipationLevel
import biweekly.parameter.ParticipationStatus
import biweekly.property.Action
import biweekly.property.Action.AUDIO
import biweekly.property.Action.DISPLAY
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.DateTimeProperty
import biweekly.property.ExceptionDates
import biweekly.property.ICalProperty
import biweekly.property.Method
import biweekly.util.DateTimeComponents
import biweekly.util.Duration
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_TOKEN
import me.proton.android.calendar.common.FormValidation.ALARM_COUNT_MAX
import me.proton.android.calendar.common.IcsParsingValidation.CONTACT_NAME_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.DESCRIPTION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.LOCATION_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.MAX_ATTENDEES
import me.proton.android.calendar.common.IcsParsingValidation.MAX_COUNT
import me.proton.android.calendar.common.IcsParsingValidation.MAX_COUNT_INVITATION
import me.proton.android.calendar.common.IcsParsingValidation.MAX_DAILY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MAX_DATE
import me.proton.android.calendar.common.IcsParsingValidation.MAX_VCALENDAR_COUNT
import me.proton.android.calendar.common.IcsParsingValidation.MAX_MONTHLY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MAX_VEVENT_COUNT
import me.proton.android.calendar.common.IcsParsingValidation.MAX_WEEKLY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MAX_YEARLY_INTERVAL
import me.proton.android.calendar.common.IcsParsingValidation.MIN_DATE
import me.proton.android.calendar.common.IcsParsingValidation.SUMMARY_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.TZID
import me.proton.android.calendar.common.IcsParsingValidation.TZID_PARAMETER
import me.proton.android.calendar.common.IcsParsingValidation.UID_MAX_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.UTC_TIME_ZONE_ID
import me.proton.android.calendar.common.IcsParsingValidation.X_PM_TOKEN_LENGTH
import me.proton.android.calendar.common.IcsParsingValidation.X_WR_TIMEZONE
import me.proton.android.calendar.common.aliasesTimezonesMap
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.dateTimeToDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.dateToDateTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.ICalUtilsImpl.clone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.filterOutEventOccurrencesByExdates
import me.proton.android.calendar.common.utils.ICalUtilsImpl.generateProtonUidForImport
import me.proton.android.calendar.common.utils.ICalUtilsImpl.iCalTimeZone
import me.proton.android.calendar.common.windowsTimeZoneMap
import me.proton.android.calendar.domain.model.Event
import me.proton.core.util.kotlin.takeIfNotBlank
import me.proton.core.util.kotlin.toBoolean
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object IcsSurgeryUtils {

    enum class HandleIcsAction {
        OPEN_EVENT,
        UPDATE_EVENT,
        CREATE_EVENT
    }

    sealed class HandleIcsResult {
        data class Success(
            val eventId: String,
            val action: HandleIcsAction,
            val newAttendeeStatus: Pair<String, ParticipationStatus>? = null,
            val isRecurring: Boolean? = false
        ): HandleIcsResult()

        data class ParsingSuccessful(
            val iCalendar: ICalendar? = null
        ): HandleIcsResult()

        data class RawParsingSuccessful(
            val cleanICalString: String
        ): HandleIcsResult()

        sealed class Error: HandleIcsResult() {
            object DefaultError: Error()
            object EventNotFound: Error()
            object NetworkError: Error()
            object EventDeleted: Error()
            object ParsingFailed: Error()
            object PartyCrasher: Error()
            object MissingUid: Error()
            object NoDefaultCalendarFound: Error()
            object NoDefaultPersonalCalendarFound: Error()
            object DurationNotSupported: Error()
            object TooManyEvents: Error()
            object NoEvents: Error()

            data class EditCreateEventError(val userErrorMessage: String? = null): Error()
            data class ReplyPartyCrasher(val eventId: String? = null): Error()
            data class DecryptionFailed(val eventId: String? = null, val calendarId: String? = null, val isRecurring: Boolean? = null): Error()
            data class DisabledCalendar(val eventId: String? = null): Error()
            data class Method(val eventId: String? = null): Error()

            sealed class Unsupported: Error() {
                object Method: Unsupported()
                object Add: Unsupported()
                object Refresh: Unsupported()
                object Counter: Unsupported()
                object Publish: Unsupported()
                object SingleEditReply: Unsupported()
            }

            sealed class Invalid: Error() {
                object CalScale: Invalid()
                object DateOrDateTimeProperty: Invalid()
                object DateStart: Invalid()
                object DateEnd: Invalid()
                object Description: Invalid()
                object Location: Invalid()
                object Summary: Invalid()
                object RRule: Invalid()
                object RecurrenceId: Invalid()
                object ExDate: Invalid()
                object Sequence: Invalid()
                object Attendees: Invalid()
                object MissingDateTimeStamp: Invalid()
                object Method: Invalid()
                object MissingOrganizer: Invalid()
            }
        }
    }

    fun cleanIcs(iCalString: String, timeZoneId: String? = null, allowMultipleEvents: Boolean = false, isOpeningFromProtonMail: Boolean = false): HandleIcsResult {
        // Clean the iCal String first to check for invalid Date or DateTime Properties
        val cleanRawIcsResult = iCalString.cleanRawIcs()

        if (cleanRawIcsResult !is HandleIcsResult.RawParsingSuccessful) return cleanRawIcsResult

        val iCalendar = try {
            val importedICalendars = Biweekly.parse(cleanRawIcsResult.cleanICalString).all() ?: return HandleIcsResult.Error.ParsingFailed

            if (importedICalendars.size > MAX_VCALENDAR_COUNT) return HandleIcsResult.Error.TooManyEvents

            if (importedICalendars.isEmpty()) return HandleIcsResult.Error.NoEvents

            (importedICalendars.firstOrNull() ?: return HandleIcsResult.Error.ParsingFailed).also {
                ICalUtilsImpl.normaliseICalendar(
                    it
                )
            }
        } catch (e: Exception) {
            TimberLogger.e("IcsSurgeryUtils: error parsing iCalendar", e)
            return HandleIcsResult.Error.ParsingFailed
        }

        if (iCalendar.events.isEmpty()) return HandleIcsResult.Error.NoEvents

        if (iCalendar.events.size > MAX_VEVENT_COUNT && !allowMultipleEvents) {
            // TODO If is an invitation we take first event only
            return HandleIcsResult.Error.TooManyEvents
        }

        /* Calendar properties */

        // VERSION: The ICS we produce should always have version 2.0
        if (iCalendar.version != ICalVersion.V2_0) {
            iCalendar.version = ICalVersion.V2_0
        }

        if (!iCalendar.cleanCalscale()) return HandleIcsResult.Error.Invalid.CalScale

        iCalendar.cleanXWrTimezone()

        // If method is not provided, set to PUBLISH by default
        if (iCalendar.method == null) iCalendar.method = Method.publish()

        val isImport = iCalendar.method?.isPublish == true

        if (!iCalendar.cleanTimezones(isImport || !isOpeningFromProtonMail, timeZoneId)) return HandleIcsResult.Error.Invalid.DateOrDateTimeProperty

        /* Event properties */

        // TODO Once we handle multiple events, allow them to fail separately
        iCalendar.events.forEach { event ->

            if (isImport) {
                event.handleImport(iCalendar, iCalString, isOpeningFromProtonMail)

                event.cleanAlarms()
            } else if (!isOpeningFromProtonMail) {
                // Importing an invitation (Method != PUBLISH) from outside of Proton Mail.
                // Drop organizer and attendees
                event.attendees.clear()
                event.organizer = null

                // The UID must be always overwritten, and re-generated. The reason to do that here is that we don't want to have UID collisions in case the user is later invited to the event.
                event.moveUid(iCalendar)
                event.setUid(generateProtonUidForImport(event.uid?.value, iCalString))

                // If present, RECURRENCE-ID should be dropped. This is because a recurrence id without the original UID is useless
                event.recurrenceId = null

                // We drop alarms when importing invitations as this would not be the user's. We must set the default calendar alarms instead (done later in the use case).
                event.alarms.clear()
            }

            // TODO Might be useless now, but keep it in case the issue comes back and we need to revert.
            // event.applyBiweeklyDstParsingFix(iCalendar)

            event.dropUnsupportedProperties()

            if (!event.cleanDtStamp(iCalendar, isImport || !isOpeningFromProtonMail)) return HandleIcsResult.Error.Invalid.MissingDateTimeStamp

            if (!event.cleanUid(iCalendar)) return HandleIcsResult.Error.MissingUid

            if (!event.cleanDtStart()) return HandleIcsResult.Error.Invalid.DateStart

            if (!event.cleanDuration(iCalendar)) return HandleIcsResult.Error.DurationNotSupported

            if (!event.cleanDtEnd(iCalendar)) return HandleIcsResult.Error.Invalid.DateEnd

            if (!event.cleanDescription()) return HandleIcsResult.Error.Invalid.Description

            if (!event.cleanLocation()) return HandleIcsResult.Error.Invalid.Location

            if (!event.cleanSummary()) return HandleIcsResult.Error.Invalid.Summary

            // RECURRENCE-ID: If the event contains both a RECURRENCE-ID and an RRULE, it is rejected (as unsupported) unless it's an invitation with REPLY method.
            if (iCalendar.method?.isReply == false && event.recurrenceId?.value != null && event.recurrenceRule?.value != null) return HandleIcsResult.Error.Invalid.RecurrenceId

            if (!event.cleanRRule(iCalendar, isImport, isOpeningFromProtonMail)) return HandleIcsResult.Error.Invalid.RRule

            if (!event.cleanExDate(iCalendar)) return HandleIcsResult.Error.Invalid.ExDate

            if (!event.cleanSequence()) return HandleIcsResult.Error.Invalid.Sequence

            if (!event.cleanAttendees(iCalendar.method)) return HandleIcsResult.Error.Invalid.Attendees

            if ((iCalendar.method?.isReply == true || iCalendar.method?.isRequest == true || iCalendar.method?.isCancel == true) && !event.alarms.isNullOrEmpty()) {
                // We drop alarms for invites as those would be the personal alarms of the organizer
                event.alarms.clear()
            }
        }

        return HandleIcsResult.ParsingSuccessful(iCalendar)
    }

    private fun VEvent.handleImport(iCalendar: ICalendar, iCalString: String, isOpeningFromProtonMail: Boolean) {
        // Drop organizer and attendees
        this.attendees.clear()
        this.organizer = null

        this.moveUid(iCalendar)

        if (isOpeningFromProtonMail) {
            // For import of invitations, the UID must be always overwritten, and re-generated
            this.setUid(generateProtonUidForImport(this.uid?.value, iCalString))

            // If present, RECURRENCE-ID should be dropped. This is because a recurrence id without the original UID is useless
            this.recurrenceId = null

            // We drop alarms when importing invitations as this would not be the user's. We must set the default calendar alarms instead (done later in the use case).
            this.alarms.clear()
        } else if (this.uid?.value.isNullOrBlank()) {
            // For other imports, we keep the UID if present, and generate a new one if not
            this.setUid(generateProtonUidForImport(null, iCalString))
        }
    }

    /**
     * Apply the fixed ICalDate values for [dateStart], [dateEnd] and [recurrenceId] if Biweekly made it incorrect due to DST
     * [exceptionDates] are not fixed in this method, but are fixed later in [cleanExDates].
     */
    fun VEvent.applyBiweeklyDstParsingFix(iCalendar: ICalendar) {

        // Fix dateStart
        iCalendar.timezoneInfo?.getTimezone(this.dateStart)?.timeZone?.id?.let { timeZone ->
            this.dateStart?.value?.getBiweeklyDstParsingFix(timeZone)?.let {
                this.dateStart.value = it
            }
        }

        // Fix dateEnd
        iCalendar.timezoneInfo?.getTimezone(this.dateEnd)?.timeZone?.id?.let { timeZone ->
            this.dateEnd?.value?.getBiweeklyDstParsingFix(timeZone)?.let {
                this.dateEnd.value = it
            }
        }

        // Fix recurrenceId
        iCalendar.timezoneInfo?.getTimezone(this.recurrenceId)?.timeZone?.id?.let { timeZone ->
            this.recurrenceId?.value?.getBiweeklyDstParsingFix(timeZone)?.let {
                this.recurrenceId.value = it
            }
        }

        // Fix ExDates in cleanExDates

    }

    private fun VEvent.dropUnsupportedProperties() {

        // Only supported status is CONFIRMED but it's also default if empty
        this.status = null
    }

    /**
     * Fixes the ICalDate value if Biweekly made it incorrect due to DST
     * @return ICalDate if fix is needed. null if nothing needs to be fixed.
     */
    // TODO Might be useless now, but keep it in case the issue comes back and we need to revert.
    fun ICalDate.getBiweeklyDstParsingFix(timezone: String): ICalDate? {
        // Ex: Device is in GMT+1 (No DST), ICalDate is in GMT+2 (DST): will return ICalDate minus 1 hour
        // Ex: Device is in GMT+2 (DST), ICalDate is in GMT+1 (No DST): will return ICalDate plus 1 hour
        // Ex: Device is in UTC-8 (No DST), ICalDate is in UTC-7 (DST): will return ICalDate minus 1 hour
        // Ex: Device is in UTC-7 (DST), ICalDate is in UTC-8 (No DST): will return ICalDate plus 1 hour
        if (!this.hasTime()) return null

        val raw = this.rawComponents ?: run {
            TimberLogger.e("IcsSurgeryUtils: getBiweeklyDstParsingFix rawComponents was null")
            return null
        }
        val rawLocalTime = raw.toDate().toZonedDateTime(timezone, false).toLocalDateTime()
        val parsedLocalTime = this.toZonedDateTime(timezone).toLocalDateTime()
        val diff = ChronoUnit.MILLIS.between(parsedLocalTime, rawLocalTime)
        val date = this.clone() as ICalDate
        date.time += diff
        return date
    }

    fun String.cleanRawIcs(): HandleIcsResult {
        var cleanICalString = this

        // Start by checking number of events
        val multipleEventsRegex = Regex("BEGIN:VEVENT")
        val multipleEventsIterator = multipleEventsRegex.findAll(cleanICalString).iterator()
        var eventCount = 0
        while (multipleEventsIterator.hasNext()) {
            eventCount++
            if (eventCount > MAX_VEVENT_COUNT) return HandleIcsResult.Error.TooManyEvents
            multipleEventsIterator.next()
        }

        cleanICalString = fixDateOrDateTimeFormat(cleanICalString)

        cleanICalString = replaceUnsupportedTimeZoneId(cleanICalString, aliasesTimezonesMap)
        cleanICalString = replaceUnsupportedTimeZoneId(cleanICalString, windowsTimeZoneMap)

        return HandleIcsResult.RawParsingSuccessful(cleanICalString)
    }

    private fun removeWhitespacesInProperties(iCalString: String): String {
        var cleanICalString = iCalString
        // Remove invalid whitespaces in properties
        val whiteSpaceRegex = Regex("(DTSTAMP|DTSTART|DTEND|RECURRENCE-ID|CREATED|LAST-MODIFIED)(;([^:]*))?:([^\\r?\\n]*)")
        whiteSpaceRegex.findAll(cleanICalString).iterator().forEach {
            val cleanDate = it.value.replace(it.groupValues.last(), it.groupValues.last().replace(" ", ""))
            cleanICalString = cleanICalString.replace(it.value, cleanDate)
        }
        return cleanICalString
    }

    private fun capitalizeTimeMarkers(iCalString: String): String {
        var cleanICalString = iCalString
        // Capitalize Time markers. ex: DTSTART:20230209t140000Z
        val lowerCaseTimeMarker = Regex(":\\d{8}t\\d{6}[zZ]?\\r?\\n")
        lowerCaseTimeMarker.findAll(cleanICalString).iterator().forEach {
            cleanICalString = cleanICalString.replace(it.value, it.value.uppercase())
        }
        return cleanICalString
    }

    private fun capitalizeZuluMarkers(iCalString: String): String {
        var cleanICalString = iCalString
        // Capitalize Zulu markers. ex: DTSTART:20230209T140000z
        val lowerCaseZuluMarker = Regex(":\\d{8}T\\d{6}z?\\r?\\n")
        lowerCaseZuluMarker.findAll(cleanICalString).iterator().forEach {
            cleanICalString = cleanICalString.replace(it.value, it.value.uppercase())
        }
        return cleanICalString
    }

    private fun addMissingSecondsToDateTimeProperties(iCalString: String): String {
        var cleanICalString = iCalString
        // Add missing seconds to DATETIME properties. ex: DTSTART:20230209T1400Z / DTSTART:20230209T1400
        val missingSecondsRegex = Regex(":\\d{8}T\\d{4}Z?\\r?\\n")
        missingSecondsRegex.findAll(cleanICalString).iterator().forEach {
            cleanICalString = cleanICalString.replace(
                it.value,
                if (it.value.endsWith("Z\r\n")) it.value.replace("Z\r\n", "00Z\r\n")
                else if (it.value.endsWith("Z\n")) it.value.replace("Z\n", "00Z\n")
                else if (it.value.endsWith("\r\n")) it.value.replace("\r\n", "00\r\n")
                else it.value.replace("\n", "00\n")
            )
        }
        return cleanICalString
    }

    private fun convertIsoDateTime(iCalString: String): String {
        var cleanICalString = iCalString
        // Convert following ISO date times (2022-10-24T11:30:00.000Z)
        val isoDateRegex = Regex(":\\d{4}-\\d{2}-\\d{2}T\\d{2}[.:]\\d{2}[.:](\\d{2}[.:])?\\d{3}Z\\r?\\n")
        isoDateRegex.findAll(cleanICalString).iterator().forEach {
            var cleanDate = it.value.replace("-", "")
            cleanDate = cleanDate.replace(Regex("[.:]\\d{3}Z"), "Z")
            cleanDate = cleanDate.replace(Regex("[.:]"), "")
            cleanICalString = cleanICalString.replace(
                it.value,
                ":$cleanDate" // Add back the first ':' since we removed it along with the others using replace
            )
        }
        return cleanICalString
    }

    private fun fixDateOrDateTimeFormat(iCalString: String): String {
        // DATETIME or DATE properties
        var cleanICalString = iCalString

        // The order is important here
        cleanICalString = removeWhitespacesInProperties(cleanICalString)
        cleanICalString = capitalizeTimeMarkers(cleanICalString)
        cleanICalString = capitalizeZuluMarkers(cleanICalString)
        cleanICalString = convertIsoDateTime(cleanICalString)
        cleanICalString = addMissingSecondsToDateTimeProperties(cleanICalString)

        // For all day events
        // We need to check in iCal string for all day dates with bad format because Biweekly doesn't properly save VALUE parameter

        // If the type DATE is specified, drop time information in case it could be there.
        cleanICalString = cleanICalString.replace(Regex("(?<=;VALUE=DATE:\\d{8})T\\d{6}Z?"), "")

        return cleanICalString
    }

    private fun replaceUnsupportedTimeZoneId(iCalString: String, supportedTimeZoneMap: Map<String, String>): String {
        var cleanICalString = iCalString
        supportedTimeZoneMap.forEach {
            var index = cleanICalString.indexOf("$TZID_PARAMETER${it.key}:", ignoreCase = true)
            if (index >= 0) {
                cleanICalString = cleanICalString.replaceRange(index, index + "$TZID_PARAMETER${it.key}:".length, "$TZID_PARAMETER${it.value}:")
            }
            while (index >= 0) {
                index = cleanICalString.indexOf("$TZID_PARAMETER${it.key}:", startIndex = index, ignoreCase = true)
                if (index >= 0) {
                    cleanICalString = cleanICalString.replaceRange(index, index + "$TZID_PARAMETER${it.key}:".length, "$TZID_PARAMETER${it.value}:")
                }
            }
        }
        return cleanICalString
    }

    fun ICalendar.cleanCalscale(): Boolean {
        // CALSCALE: The calendar scale must be either 'Gregorian' or empty.
        return this.calendarScale?.value.isNullOrEmpty() || this.calendarScale.isGregorian
    }

    fun ICalendar.cleanXWrTimezone(): Boolean {
        // X-WR-TIMEZONE: This one is not an official iCal property, but if present, we should try to convert it into a supported timezone, and use it to localize some UTC dates in the ICS.
        val xWrTimezone = this.getExperimentalProperty(X_WR_TIMEZONE)
        if (xWrTimezone != null) {
            val timezoneId = fallbackTimeZone(xWrTimezone.value, fallbackToDefault = false)
            if (timezoneId != null) {
                this.setExperimentalProperty(X_WR_TIMEZONE, timezoneId)
            } else {
                this.removeExperimentalProperties(X_WR_TIMEZONE)
            }
        }
        return true
    }

    private fun ICalendar.getXWrTimezone(): String? {
        return this.getExperimentalProperty(X_WR_TIMEZONE)?.value
    }

    fun VEvent.cleanAlarms() {
        // Remove duplicate alarms
        val alarms = this.alarms.distinctBy {
            "${it.trigger?.duration?.toMillis()} ${it.action}"
        }.take(ALARM_COUNT_MAX) // Keep at max the first 10
        this.alarms.clear()
        this.alarms.addAll(alarms)

        val alarmsIterator = this.alarms.iterator()
        while (alarmsIterator.hasNext()) {
            val alarm = alarmsIterator.next()

            // Alarms with RELATED=END are not supported.
            // TODO Can we spot this parameter with some other method ? Trigger doesn't seem to be parsed by biweekly
            if (alarm.toString().contains("RELATED=[END]")) {
                alarmsIterator.remove()
                continue
            }

            // Alarms without trigger are discarded
            if (alarm.trigger == null) {
                alarmsIterator.remove()
                continue
            }

            // Alarms with positive triggers (i.e. alarms that are triggered after the event started) are not supported
            if (alarm.trigger?.duration != null && alarm.trigger.duration.toMillis() > 0) {
                alarmsIterator.remove()
                continue
            }

            // Action is mandatory according to RFC.
            if (alarm.action == null) {
                alarm.action = Action(DISPLAY)
            }

            // We also support 'AUDIO' action but it should be considered as a DISPLAY action.
            if (alarm.action == Action(AUDIO)) {
                alarm.action = Action(DISPLAY)
            }

            // A trigger for partial-day event cannot contain more than one-time component
            if (this.dateStart.value.hasTime() && alarm?.trigger?.duration != null) {
                val hasWeeks = alarm.trigger.duration.weeks?.let { 1 } ?: 0
                val hasDays = alarm.trigger.duration.days?.let { 1 } ?: 0
                val hasHours = alarm.trigger.duration.hours?.let { 1 } ?: 0
                val hasMinutes = alarm.trigger.duration.minutes?.let { 1 } ?: 0
                val hasSeconds = alarm.trigger.duration.seconds?.let { 1 } ?: 0
                if (hasWeeks + hasDays + hasHours + hasMinutes + hasSeconds > 1) {
                    val durationInMs = alarm.trigger.duration.toMillis()
                    // Use smallest unit for the duration
                    val simplifiedDuration =
                        if (hasSeconds.toBoolean()) {
                            Duration.builder().seconds(TimeUnit.MILLISECONDS.toSeconds(durationInMs).toInt()).build()
                        } else if (hasMinutes.toBoolean()) {
                            Duration.builder().minutes(TimeUnit.MILLISECONDS.toMinutes(durationInMs).toInt()).build()
                        } else if (hasHours.toBoolean()) {
                            Duration.builder().hours(TimeUnit.MILLISECONDS.toHours(durationInMs).toInt()).build()
                        } else if (hasDays.toBoolean()) {
                            Duration.builder().days(TimeUnit.MILLISECONDS.toDays(durationInMs).toInt()).build()
                        } else {
                            Duration.builder().weeks(TimeUnit.MILLISECONDS.toDays(durationInMs).div(7).toInt()).build()
                        }
                    alarm.trigger.setDuration(simplifiedDuration, alarm.trigger.related)
                }
            }
        }
    }

    fun VEvent.cleanDtStamp(iCalendar: ICalendar, isImport: Boolean): Boolean {
        // DTSTAMP: mandatory field as per RFC.
        // For Imports: if not present, use the current time timestamp.
        if (isImport && this.dateTimeStamp?.value == null) {
            val date = Date.from(Instant.now())
            val rawComponents = DateTimeComponents(date)
            this.setDateTimeStamp(ICalDate(date, rawComponents, true))
            return true
        }

        // TODO For Invites: if not present, use the email timestamp. We need Mail to give us that data. For now consider as invalid.
        if (!isImport && this.dateTimeStamp?.value == null) return false

        // If TZID is empty, remove it
        if (this.dateTimeStamp?.value != null && this.dateTimeStamp.getParameter(TZID)?.isEmpty() == true) this.dateTimeStamp.removeParameter(TZID)

        // If it's an all-day event
        if (!(this.dateTimeStamp?.value as ICalDate).hasTime()) {
            val timeZoneId =
                if (this.dateTimeStamp.getParameter(TZID).isNullOrEmpty()) {
                    // If it's an all-day event, assume 0 hours, 0 minutes, 0 seconds in UTC to convert to timestamp
                    UTC_TIME_ZONE_ID
                } else {
                    // If it's an all-day event with TZID, assume 0 hours, 0 minutes, 0 seconds in the indicated time zone, then convert to UTC
                    fallbackTimeZone(this.dateTimeStamp.getParameter(TZID), fallbackToDefault = false) ?: return false
                }
            val instant = this.dateTimeStamp?.value?.toZonedDateTime(ZoneId.systemDefault().id, false)?.withZoneSameLocal(ZoneId.of(timeZoneId))?.toInstant()
            val date = Date.from(instant)
            val rawComponents = DateTimeComponents.parse(date.toInstant().toString()) // Use parse here since the date may use a different timezone than device timezone
            this.setDateTimeStamp(ICalDate(date, rawComponents, true))
        }

        // If it's a floating date (i.e. no TZID present, e.g. DTSTART:20200101T120000), assume TZID=UTC.
        if ((this.dateTimeStamp?.value as ICalDate).rawComponents?.toString()?.contains("Z") == false && iCalendar.timezoneInfo.getTimezone(this.dateTimeStamp) == null) {
            this.dateTimeStamp.localizeDateToTimezone(UTC_TIME_ZONE_ID)
            iCalendar.timezoneInfo.setFloating(this.dateTimeStamp, false)
            this.dateTimeStamp.removeParameter(TZID) // TZID parameter is not needed anymore
            return true
        }

        // If both the Zulu marker and a TZID are present in the property, it is to be interpreted as a Zulu one
        if (!this.dateTimeStamp.getParameter(TZID).isNullOrEmpty() && (this.dateTimeStamp?.value as ICalDate).rawComponents?.toString()?.contains("Z") == true) {
            this.dateTimeStamp.removeParameter(TZID)
            return true
        }

        // Extract TZID parameter to timezoneInfo if dtstamp has one and Biweekly didn't process it during parsing
        if (!this.dateTimeStamp.getParameter(TZID).isNullOrEmpty() && (this.dateTimeStamp?.value as ICalDate).rawComponents?.toString()?.contains("Z") == false) {
            val supportedTzid = fallbackTimeZone(this.dateTimeStamp.getParameter(TZID), fallbackToDefault = false) ?: return false
            iCalendar.timezoneInfo.setTimezone(this.dateTimeStamp, TimezoneAssignment(TimeZone.getTimeZone(supportedTzid), supportedTzid))
        }

        // If a TZID is present, we try to convert it into a supported timezone. If not possible, reject (as unsupported) the event. Otherwise localize it to the supported timezone.
        if (!iCalendar.convertToSupportedTimezone(this.dateTimeStamp)) return false

        return true
    }

    private fun VEvent.moveUid(iCalendar: ICalendar) {
        if (this.uid?.value.isNullOrBlank() && !iCalendar.uid?.value.isNullOrBlank()) {
            // If UID was set in VCalendar instead of VEvent, move it
            // TODO Once we handle importing multiple VEvent per VCalendar, make sure this does not apply
            this.uid = iCalendar.uid
            iCalendar.uid = null
        }
    }

    private fun VEvent.cleanUid(iCalendar: ICalendar): Boolean {
        this.moveUid(iCalendar)

        // UID: As per RFC, we require it to be present. Also, there's a BE limit of 191 characters.
        //  If we need to crop, we keep the last 191 characters of the uid.
        val uid = this.uid?.value ?: return false
        when {
            uid.isBlank() -> return false
            uid.length > UID_MAX_LENGTH -> this.setUid(this.uid.value.takeLast(UID_MAX_LENGTH))
        }
        return true
    }

    fun VEvent.cleanDtStart(): Boolean {
        // DTSTART: As per RFC, we require that it be present and in bounds.
        if (this.dateStart?.value == null || this.dateStart.value.toInstant().isBefore(MIN_DATE.toInstant()) ||
            this.dateStart.value.toInstant().isAfter(MAX_DATE.toInstant())) return false

        // DTSTART & DTEND: They should both use the same value type: either: date-time (default) or date. If one is a date and the other a date-time, the API will reject the request.
        if (this.dateEnd?.value != null && this.dateStart.value.hasTime() != this.dateEnd.value.hasTime()) return false

        return true
    }

    private fun VEvent.cleanDuration(iCalendar: ICalendar): Boolean {
        // DURATION property should be transformed into the corresponding DTEND
        val dateEnd = this.dateStart.value.clone() as ICalDate
        val dateStartTimeZone = iCalendar.timezoneInfo.getTimezone(this.dateStart)
        if (this.duration?.value != null && this.dateEnd?.value == null) {
            if (this.dateStart.value.hasTime()) {
                // Part day
                val durationInMillis = this.duration.value.toMillis()
                dateEnd.time += durationInMillis
                this.setDateEnd(dateEnd)
                // Set DTEND timezone
                if (dateStartTimeZone != null) iCalendar.timezoneInfo.setTimezone(this.dateEnd, dateStartTimeZone)
            } else {
                // All day
                val durationInMsDouble = this.duration.value.toMillis().toDouble()
                val oneDayAsMsDouble = TimeUnit.DAYS.toMillis(1).toDouble()
                val durationInDaysDouble = durationInMsDouble.div(oneDayAsMsDouble)
                // Round up
                val durationInDaysRoundedUp = ceil(durationInDaysDouble).toLong()
                dateEnd.time = this.dateStart.value.toZonedDateTime(ZoneId.systemDefault().id).plusDays(
                    if (durationInDaysRoundedUp == 0L) 1 // DTEND must always be at least DTSTART + 1 day for all day event
                    else durationInDaysRoundedUp
                ).toInstant().toEpochMilli()
                this.setDateEnd(dateEnd)
                // Set DTEND timezone
                if (dateStartTimeZone != null) iCalendar.timezoneInfo.setTimezone(this.dateEnd, dateStartTimeZone)
            }
            // Remove duration once DTEND has been set
            this.removeProperty(this.duration)
        }
        return true
    }

    fun VEvent.cleanDtEnd(iCalendar: ICalendar): Boolean {
        // DTEND: If not present, we don't add it either. If present, the standard DATETIME/DATE sanitization operations must be performed.
        if (this.dateEnd?.value != null && (
                    this.dateEnd.value.before(this.dateStart.value) ||
                            (!this.dateEnd.value.hasTime() && this.dateEnd.value.equals(this.dateStart.value))
                    )) {
            // If DTEND happens before DTSTART, or if event is all day and DTSTART is equal to DTEND we drop it
            this.dateEnd.value = null
        }
        if (this.dateEnd?.value == null) {
            // DTEND can be omitted
            val dateStartTimeZone = iCalendar.timezoneInfo.getTimezone(this.dateStart)
            if (this.dateStart.value.hasTime()) {
                // For partial day, the DTEND is by default set to the DTSTART value
                //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
                //  set to avoid NPE in the app. We remove it when sending the ICS to BE.
                this.setDateEnd(this.dateStart.value)
                // Set DTEND timezone
                if (dateStartTimeZone != null) iCalendar.timezoneInfo.setTimezone(this.dateEnd, dateStartTimeZone)
            } else {
                // For full day, the DTEND is by default set to the day after DTSTART such that the event is one day long
                val dateEnd = this.dateStart.value.clone() as ICalDate
                dateEnd.time += TimeUnit.DAYS.toMillis(1)
                this.setDateEnd(dateEnd)
                // Set DTEND timezone
                if (dateStartTimeZone != null) iCalendar.timezoneInfo.setTimezone(this.dateEnd, dateStartTimeZone)
            }
        } else {
            // DTEND: If present check if in bounds
            if (this.dateEnd.value.toInstant().isBefore(MIN_DATE.toInstant()) ||
                this.dateEnd.value.toInstant().isAfter(MAX_DATE.toInstant())) return false
        }

        return true
    }

    private fun String.ellipsizeField(maxLength: Int): String {
        return this.substring(0, maxLength - 1).plus("…")
    }

    fun VEvent.cleanDescription(): Boolean {
        // DESCRIPTION: This field is limited to 3k characters.
        if (this.description?.value != null && this.description.value.length > DESCRIPTION_MAX_LENGTH) {
            this.description.value = this.description.value.ellipsizeField(DESCRIPTION_MAX_LENGTH)
        }
        return this.description?.value == null || this.description.value.length <= DESCRIPTION_MAX_LENGTH
    }

    fun VEvent.cleanLocation(): Boolean {
        // LOCATION: This field is limited to 255 characters.
        if (this.location?.value != null && this.location.value.length > LOCATION_MAX_LENGTH) {
            this.location.value = this.location.value.ellipsizeField(LOCATION_MAX_LENGTH)
        }
        return this.location?.value == null || this.location.value.length <= LOCATION_MAX_LENGTH
    }

    fun VEvent.cleanSummary(): Boolean {
        // SUMMARY: This field is limited to 255 characters.
        if (this.summary?.value != null && this.summary.value.length > SUMMARY_MAX_LENGTH) {
            this.summary.value = this.summary.value.ellipsizeField(SUMMARY_MAX_LENGTH)
        }
        return this.summary?.value == null || this.summary.value.length <= SUMMARY_MAX_LENGTH
    }

    fun VEvent.cleanRRule(iCalendar: ICalendar, isImport: Boolean, isOpeningFromProtonMail: Boolean): Boolean {

        if (recurrenceRule?.value == null) return true

        // If the event contains both a RECURRENCE-ID and an RRULE and the method is REPLY, simply ignore the RRULE (the external provider forgot to remove it when adding the RECURRENCE-ID).
        if (iCalendar.method?.isReply == true && this.recurrenceRule?.value != null && this.recurrenceId?.value != null) {
            this.recurrenceRule = null
            return true
        }

        if (isImport || !isOpeningFromProtonMail) {
            // We only support certain types of RRULE on Import, basically the ones that can be created from ProtonCalendar

            // Frequency: DAILY, WEEKLY, MONTHLY, YEARLY
            if (recurrenceRule.value.frequency != Frequency.DAILY &&
                recurrenceRule.value.frequency != Frequency.WEEKLY &&
                recurrenceRule.value.frequency != Frequency.MONTHLY &&
                recurrenceRule.value.frequency != Frequency.YEARLY) return false

            if (!recurrenceRule.value.checkCustomYearlyRRule()) return false
            if (!recurrenceRule.value.checkCustomMonthlyRRule()) return false
            if (!recurrenceRule.value.checkCustomWeeklyRRule()) return false
            if (!recurrenceRule.value.checkCustomDailyRRule()) return false
        }

        // We do not accept events with a rrule below daily
        if (recurrenceRule.value.frequency == Frequency.HOURLY || recurrenceRule.value.frequency == Frequency.MINUTELY || recurrenceRule.value.frequency == Frequency.SECONDLY) return false

        // The interval is limited to the following max values:
        if (recurrenceRule.value.frequency != null && recurrenceRule.value.interval != null) {
            when (recurrenceRule.value.frequency) {
                Frequency.DAILY -> if (recurrenceRule.value.interval > MAX_DAILY_INTERVAL) return false
                Frequency.WEEKLY -> if (recurrenceRule.value.interval > MAX_WEEKLY_INTERVAL) return false
                Frequency.MONTHLY -> if (recurrenceRule.value.interval > MAX_MONTHLY_INTERVAL) return false
                Frequency.YEARLY -> if (recurrenceRule.value.interval > MAX_YEARLY_INTERVAL) return false
                else -> Unit
            }
        }

        // COUNT: Because this is pretty expensive BE side, the maximum count value is lower on imported events (included).
        if (recurrenceRule.value.count != null && recurrenceRule.value.count > if (iCalendar.isInvitation() && isOpeningFromProtonMail) MAX_COUNT_INVITATION else MAX_COUNT) return false

        // UNTIL: The maximum value is currently 01/01/2038 @ 00:00:00 UTC (soon to become 01/01/2200 @ 12:00am (UTC)
        if (recurrenceRule.value.until != null && recurrenceRule.value.until.toInstant().isAfter(MAX_DATE.toInstant())) return false

        // UNTIL: we should use UTC dates if and only if the event is not all-day.
        if (!this.dateStart.value.hasTime() && this.recurrenceRule.value.until?.hasTime() == true) {
            val timeZone = iCalendar.timezoneInfo.timezones.firstOrNull()?.timeZone
            val supportedTimeZone = if (timeZone != null) fallbackTimeZone(timeZone.id, false) ?: UTC_TIME_ZONE_ID else UTC_TIME_ZONE_ID
            val untilDate = this.recurrenceRule.value.until
            this.recurrenceRule.value = this.recurrenceRule.value.clone(until = dateTimeToDate(untilDate, supportedTimeZone, setRawComponents = true))
        }

        // UNTIL: we should transform a DATE into the UTC DATETIME that corresponds to the end of the day in the DTSTART timezone
        if (this.dateStart.value.hasTime() && this.recurrenceRule.value.until?.hasTime() == false) {
            iCalendar.timezoneInfo.getTimezone(this.dateStart)?.timeZone?.id?.let { timezone -> // We make sure we have a timezone for part day dateStart earlier in the process
                val newUntil = dateToDateTime(this.recurrenceRule.value.until.toZonedDateTime(timezone), timezone, setRawComponents = true)
                this.recurrenceRule.value = this.recurrenceRule.value.clone(until = newUntil)
            }
        }

        // UNTIL: if an UNTIL < DTSTART is received, it means to actually have one occurrence. We should therefore set UNTIL = DTSTART (equality in the timestamp sense, the UNTIL format should always be UTC DATETIME).
        if (this.recurrenceRule.value.until != null && this.recurrenceRule.value.until < this.dateStart.value) {
            val newUntil = this.dateStart.value
            this.recurrenceRule.value = this.recurrenceRule.value.clone(until = newUntil)
        }

        // We reject as invalid RRULEs that:
        val eventTimezone = (if (dateStart.value.hasTime()) iCalendar.iCalTimeZone(dateStart) else TimeZone.getDefault()) ?: return false
        val dummyEventForOccurrences = Event.dummyFrom(iCalendar) ?: return false

        // Do not generate any occurrence. We check if we can generate at least DTSTART as first occurrence since it's mandatory as per RFC.
        val firstOccurrence = dummyEventForOccurrences.generateOccurrence(1, eventTimezone.id) ?: return false
        val firstExDatedOccurrence = (listOf(firstOccurrence)).filterOutEventOccurrencesByExdates(dummyEventForOccurrences, eventTimezone.id).firstOrNull() ?: return false

        // Do not generate DTSTART as occurrence (which is mandatory as per RFC).
        if (firstExDatedOccurrence.startDateTime != dummyEventForOccurrences.getStart(eventTimezone.id) || firstExDatedOccurrence.endDateTime != dummyEventForOccurrences.getEnd(eventTimezone.id)) return false

        // Special case: YEARLY with BYMONTHDAY but no BYMONTH
        if (recurrenceRule.value.frequency == Frequency.YEARLY && !recurrenceRule.value.byMonthDay.isNullOrEmpty() && recurrenceRule.value.byMonth.isNullOrEmpty()) return false

        // If event is recurring with DAILY, WEEKLY, MONTHLY and has BYYEARDAY parameter - reject invite as invalid
        if ((recurrenceRule.value.frequency == Frequency.DAILY || recurrenceRule.value.frequency == Frequency.WEEKLY || recurrenceRule.value.frequency == Frequency.MONTHLY)
            && !recurrenceRule.value.byYearDay.isNullOrEmpty()) return false

        return true
    }

    private fun Recurrence.checkCustomYearlyRRule(): Boolean {
        if (this.frequency == Frequency.YEARLY) {
            // No selectors allowed
            if (!this.bySecond.isNullOrEmpty() ||
                !this.byMinute.isNullOrEmpty() ||
                !this.byHour.isNullOrEmpty() ||
                !this.byMonthDay.isNullOrEmpty() ||
                !this.byYearDay.isNullOrEmpty() ||
                !this.byWeekNo.isNullOrEmpty() ||
                !this.byMonth.isNullOrEmpty() ||
                !this.bySetPos.isNullOrEmpty() ||
                !this.byDay.isNullOrEmpty()
            ) return false
        }
        return true
    }

    private fun Recurrence.checkCustomMonthlyRRule(): Boolean {
        if (this.frequency == Frequency.MONTHLY) {
            // Allow BYDAY and BYSETPOS
            if (!this.bySecond.isNullOrEmpty() ||
                !this.byMinute.isNullOrEmpty() ||
                !this.byHour.isNullOrEmpty() ||
                !this.byMonthDay.isNullOrEmpty() ||
                !this.byYearDay.isNullOrEmpty() ||
                !this.byWeekNo.isNullOrEmpty() ||
                !this.byMonth.isNullOrEmpty()
            ) return false
            // BYSETPOS can be first (1), second (2), third (3), forth (4) or last (-1)
            if (!this.bySetPos.isNullOrEmpty() && this.bySetPos.any { it != 1 && it != 2 && it != 3 && it != 4 && it != -1 }) return false
        }
        return true
    }

    private fun Recurrence.checkCustomWeeklyRRule(): Boolean {
        if (this.frequency == Frequency.WEEKLY) {
            // Allow BYDAY and allow multiple days (BYDAY=MO,TU,SA,SU)
            if (!this.bySecond.isNullOrEmpty() ||
                !this.byMinute.isNullOrEmpty() ||
                !this.byHour.isNullOrEmpty() ||
                !this.byMonthDay.isNullOrEmpty() ||
                !this.byYearDay.isNullOrEmpty() ||
                !this.byWeekNo.isNullOrEmpty() ||
                !this.byMonth.isNullOrEmpty() ||
                !this.bySetPos.isNullOrEmpty()
            ) return false
        }
        return true
    }

    private fun Recurrence.checkCustomDailyRRule(): Boolean {
        if (this.frequency == Frequency.DAILY) {
            // No selectors allowed
            if (!this.bySecond.isNullOrEmpty() ||
                !this.byMinute.isNullOrEmpty() ||
                !this.byHour.isNullOrEmpty() ||
                !this.byMonthDay.isNullOrEmpty() ||
                !this.byYearDay.isNullOrEmpty() ||
                !this.byWeekNo.isNullOrEmpty() ||
                !this.byMonth.isNullOrEmpty() ||
                !this.bySetPos.isNullOrEmpty() ||
                !this.byDay.isNullOrEmpty()
            ) return false
        }
        return true
    }

    fun ICalendar.cleanRecurrenceId(parentCalendar: ICalendar? = null): Boolean {
        val event = this.events.firstOrNull() ?: return false

        if (event.recurrenceId?.value == null) return true

        // Allow standalone single edits
        val parentEvent = parentCalendar?.events?.firstOrNull() ?: return true

        // If RECURRENCE-ID is of type DATE-TIME for a parent all-day event, convert to type DATE by keeping just the date part.
        if (event.recurrenceId.value.hasTime() && parentEvent.dateStart?.value?.hasTime() == false) {
            val rawComponents = try {
                DateTimeComponents.parse(
                    event.recurrenceId.value.rawComponents.toString(false, false)
                )
            } catch (e: IllegalArgumentException) {
                TimberLogger.e("cleanRecurrenceId failed to parse DateTimeComponents")
                null
            }
            event.recurrenceId.value =
                if (rawComponents != null) {
                    ICalDate(
                        event.recurrenceId.value,
                        rawComponents,
                        false
                    )
                } else {
                    ICalDate(
                        event.recurrenceId.value,
                        false
                    )
                }
        }

        // If RECURRENCE-ID is of type DATE for a parent part-day event then we cannot recover and reject (as invalid).
        if (!event.recurrenceId.value.hasTime() && parentEvent.dateStart?.value?.hasTime() == true) {
            return false
        }

        if (event.recurrenceId.value.hasTime()) {
            // If RECURRENCE-ID has a timezone different from the parent DTSTART one, re-localize in the parent DTSTART timezone.
            val eventRecurrenceIdTimezone = this.timezoneInfo?.getTimezone(event.recurrenceId)?.timeZone?.id
            val parentRecurrenceIdTimezone = parentCalendar.timezoneInfo?.getTimezone(parentEvent.dateStart)?.timeZone?.id
            if (event.recurrenceId.value != null && eventRecurrenceIdTimezone != parentRecurrenceIdTimezone) {
                this.timezoneInfo.setTimezone(event.recurrenceId, parentCalendar.timezoneInfo?.getTimezone(parentEvent.dateStart))
            }
        }

        return true
    }

    fun VEvent.cleanExDate(iCalendar: ICalendar): Boolean {
        // EXDATE: If the event contains an EXDATE, but not an RRULE, reject (as invalid).
        if (!this.exceptionDates.isNullOrEmpty() && this.recurrenceRule?.value == null) return false

        // Otherwise we apply the same operations as for RECURRENCE-ID to each of the dates contained in the property.
        // The re-localization of the timezone here is easier and simpler as the reference timezone is the DTSTART timezone of the same event.

        val exceptionDatesIterator: Iterator<ExceptionDates> = this.exceptionDates.iterator()
        while (exceptionDatesIterator.hasNext()) {
            val exceptionDates = exceptionDatesIterator.next()

            val exceptionDatesValuesIterator: Iterator<ICalDate> = exceptionDates.values.iterator()
            while (exceptionDatesValuesIterator.hasNext()) {
                val exceptionDateValue = exceptionDatesValuesIterator.next()

                // If EXDATE is of type DATE for a part-day event then we cannot recover and reject (as invalid).
                if (!exceptionDateValue.hasTime() && this.dateStart.value.hasTime()) {
                    return false
                }

                // If EXDATE is of type DATE-TIME for an all-day event, convert to type DATE by keeping just the date part.
                if (exceptionDateValue.hasTime() && !this.dateStart.value.hasTime()) {
                    val rawComponents = try {
                        DateTimeComponents.parse(
                            exceptionDateValue.rawComponents.toString(false, false)
                        )
                    } catch (e: IllegalArgumentException) {
                        TimberLogger.e("cleanExDate failed to parse DateTimeComponents")
                        null
                    }
                    exceptionDates.values[exceptionDates.values.indexOf(exceptionDateValue)] =
                        if (rawComponents != null) {
                            ICalDate(
                                exceptionDateValue,
                                rawComponents,
                                false
                            )
                        } else {
                            ICalDate(
                                exceptionDateValue,
                                false
                            )
                        }
                }

                // TODO Might be useless now, but keep it in case the issue comes back and we need to revert.
                // Fix Biweekly DST parsing on ExDates values
                // if (exceptionDateValue.hasTime() && this.dateStart.value.hasTime()) {
                //     iCalendar.timezoneInfo?.getTimezone(exceptionDates)?.timeZone?.id?.let { timeZone ->
                //         exceptionDateValue.getBiweeklyDstParsingFix(timeZone)?.let {
                //             exceptionDates.values[exceptionDates.values.indexOf(exceptionDateValue)] = it
                //         }
                //     }
                // }
            }
        }

        // We only allow one EXDATE value per row
        val exceptionDatesSplit = arrayListOf<ExceptionDates>()
        this.exceptionDates.forEach {
            if (it.values.size > 1) {
                val exceptionDateParametersCopy = it.parameters
                it.values.forEach { iCalDate ->
                    val timeZone = iCalendar.timezoneInfo.getTimezone(it)
                    val exceptionDate = ExceptionDates()
                    exceptionDate.parameters = exceptionDateParametersCopy
                    exceptionDate.values.add(iCalDate)
                    exceptionDatesSplit.add(exceptionDate)
                    iCalendar.timezoneInfo.setTimezone(exceptionDate, timeZone)
                }
                iCalendar.timezoneInfo.setTimezone(it, null)
            } else exceptionDatesSplit.add(it)
        }
        this.exceptionDates.removeAll(this.exceptionDates)
        this.exceptionDates.addAll(exceptionDatesSplit)

        return true
    }

    fun VEvent.cleanSequence(): Boolean {
        // If SEQUENCE was saved as an experimental property then the value was over an Integer's limit, so we remove it and set its value to value % Int.MAX_VALUE.
        // If value was negative, set to 0.
        if (this.sequence?.value == null && this.getExperimentalProperty("SEQUENCE") != null) {
            val currentSequence = this.getExperimentalProperty("SEQUENCE").value
            this.removeExperimentalProperties("SEQUENCE")
            if (currentSequence.toLong() < 0) this.setSequence(0)
            else this.setSequence(currentSequence.toLong().mod(Int.MAX_VALUE.toLong() + 1).toInt())
        }
        // SEQUENCE: If not present, assume it's zero. If present, make sure it's a non-negative integer or convert it to zero otherwise.
        if (this.sequence?.value == null || this.sequence.value < 0) this.setSequence(0)
        return true
    }

    fun VEvent.cleanAttendees(method: Method): Boolean {
        // If there are more than 100 attendees, reject invitation as unsupported.
        if (this.attendees != null && this.attendees.size > MAX_ATTENDEES) return false

        // REPLY ics should only contain one attendee
        if (method.isReply && this.attendees.size > 1) return false

        val attendeesEmail = mutableListOf<String>()
        this.attendees?.forEach { attendee ->
            // We allow any values for attendee email during the surgery, but we check the email validity in HandleIcsUseCase
            //  if we are in organizerMode, as there we require the attendee email to be canonicalizable to generate the token
            val email = attendee.extractEmail() ?:
            attendee.email?.takeIfNotBlank() ?:
            attendee.uri?.substringAfter("mailto:")?.takeIfNotBlank() ?:
            attendee.commonName?.takeIfNotBlank() ?: return false

            // Remove URI parameter if it's clearly not an email
            if (attendee.uri?.contains("@") == false) {
                attendee.uri = null
            }

            // Overwrite email field with extracted email value
            attendee.email = email

            // CN: This field is limited to 190 characters, which is the limit we impose on contact names.
            if (attendee.commonName?.isNotBlank() == true && attendee.commonName.length > CONTACT_NAME_MAX_LENGTH) {
                attendee.commonName = attendee.commonName.ellipsizeField(CONTACT_NAME_MAX_LENGTH)
            }

            // ROLE: We only admit OPTIONAL or REQUIRED as values. If the value is any other, ignore the parameter.
            if (attendee.participationLevel != ParticipationLevel.OPTIONAL && attendee.participationLevel != ParticipationLevel.REQUIRED) {
                attendee.participationLevel = null
            }

            // RSVP: We only admit TRUE as value. Ignore other values.
            if (attendee.rsvp != true) {
                attendee.rsvp = null
            }

            // PARTSTAT: We only admit NEEDS_ACTION, ACCEPTED, DECLINED, TENTATIVE or DELEGATED as values, if different fall back to NEEDS-ACTION
            if (attendee.participationStatus != ParticipationStatus.NEEDS_ACTION &&
                attendee.participationStatus != ParticipationStatus.ACCEPTED &&
                attendee.participationStatus != ParticipationStatus.DECLINED &&
                attendee.participationStatus != ParticipationStatus.TENTATIVE &&
                attendee.participationStatus != ParticipationStatus.DELEGATED) {
                attendee.participationStatus = ParticipationStatus.NEEDS_ACTION
            }

            if (attendee.getParameter(X_PM_TOKEN) != null) {
                val token = attendee.getParameter(X_PM_TOKEN)
                // X-PM-TOKEN: Make a quick check that it's a valid Proton token (check length plus proton.me domain). If invalid, reject the event as invalid.
                if (token.length != X_PM_TOKEN_LENGTH) return false
            }

            // In case some attendee emails are repeated, we reject (as unsupported) the invite
            if (attendeesEmail.contains(email)) return false
            email?.let { attendeesEmail.add(email) }
        }

        return true
    }

    private fun ICalendar.cleanTimezones(isImport: Boolean = false, timeZoneId: String? = null): Boolean {

        this.events.forEach {

            // DTSTART, DTEND, RECURRENCE-ID:

            // If TZID is empty, remove it
            if (it.dateStart?.value != null && it.dateStart.getParameter(TZID)?.isEmpty() == true) it.dateStart.removeParameter(TZID)
            if (it.dateEnd?.value != null && it.dateEnd.getParameter(TZID)?.isEmpty() == true) it.dateEnd.removeParameter(TZID)
            if (it.recurrenceId?.value != null && it.recurrenceId.getParameter(TZID)?.isEmpty() == true) it.recurrenceId.removeParameter(TZID)

            // Extract TZID parameter to timezoneInfo if Biweekly didn't process it during parsing
            if (!this.extractTzid(it.dateStart)) return false
            if (!this.extractTzid(it.dateEnd)) return false
            if (!this.extractTzid(it.recurrenceId)) return false

            // If a TZID is present, we try to convert it into a supported timezone. If not possible, reject (as unsupported) the event. Otherwise localize it to the supported timezone.
            if (!this.convertToSupportedTimezone(it.dateStart)) return false
            if (!this.convertToSupportedTimezone(it.dateEnd)) return false
            if (!this.convertToSupportedTimezone(it.recurrenceId)) return false

            // If it's a floating date (i.e. no TZID present, e.g. DTSTART:20200101T120000), localize it to the x-wr-timezone if supported.
            // If no x-wr-timezone is present, we check if there's a single vtimezone in the ICS string
            if (it.dateStart?.localizeFloatingDate(this, isImport, timeZoneId) == false) return false
            if (it.dateEnd?.localizeFloatingDate(this, isImport, timeZoneId) == false) return false
            if (it.recurrenceId?.localizeFloatingDate(this, isImport, timeZoneId) == false) return false

            // If it's a Zulu time, the event is non-recurring and x-wr-timezone is present and supported, localize the date-time to the supported timezone.
            it.dateStart?.localizeZuluTimeDate(this, it)
            it.dateEnd?.localizeZuluTimeDate(this, it)
            it.recurrenceId?.localizeZuluTimeDate(this, it)

            // EXDATE
            it.exceptionDates.forEach { exDate ->

                // If EXDATE has a timezone different from the DTSTART, re-localize in the DTSTART timezone.
                if (this.timezoneInfo.getTimezone(exDate) != this.timezoneInfo.getTimezone(this.events.first().dateStart)) {
                    this.timezoneInfo.setTimezone(exDate, this.timezoneInfo.getTimezone(this.events.first().dateStart))
                }

                // If a TZID is present, we try to convert it into a supported timezone. If not possible, reject (as unsupported) the event. Otherwise localize it to the supported timezone.
                if (!this.convertToSupportedTimezone(exDate)) return false
            }
        }

        return true
    }

    private fun ICalendar.extractTzid(date: DateOrDateTimeProperty?): Boolean {
        // Extract TZID parameter to timezoneInfo if Biweekly didn't process it during parsing
        if (date?.value != null && !date.getParameter(TZID).isNullOrEmpty()) {
            val supportedTzid = fallbackTimeZone(date.getParameter(TZID), fallbackToDefault = false) ?: return false
            this.timezoneInfo.setTimezone(date, TimezoneAssignment(TimeZone.getTimeZone(supportedTzid), supportedTzid))
        }
        return true
    }

    private fun ICalendar.convertToSupportedTimezone(date: ICalProperty?): Boolean {
        // TODO This shouldn't be needed anymore as long as we do the replace unsupported tzid in cleanRawIcs. We keep it for now if biweekly parses a timezone we haven't converted.
        // If a TZID is present, we try to convert it into a supported timezone. If not possible, reject (as unsupported) the event. Otherwise localize it to the supported timezone.
        this.timezoneInfo.getTimezone(date)?.let { timezoneAssignment ->
            val supportedTzid = fallbackTimeZone(timezoneAssignment.timeZone.id, fallbackToDefault = false) ?: return false
            if (supportedTzid != timezoneAssignment.timeZone.id) this.timezoneInfo.setTimezone(date, TimezoneAssignment(TimeZone.getTimeZone(supportedTzid), supportedTzid))
        }
        return true
    }

    private fun DateOrDateTimeProperty.localizeFloatingDate(iCalendar: ICalendar, isImport: Boolean, timeZoneId: String?): Boolean {
        // If it's a floating date (i.e. no TZID present, e.g. DTSTART:20200101T120000), localize it to the x-wr-timezone if supported.
        val xWrTimezone = iCalendar.getXWrTimezone()
        if (this.value.hasTime() && iCalendar.timezoneInfo.getTimezone(this) == null && !this.value.rawComponents.toString().contains("Z")) {
            if (xWrTimezone != null) {
                // Remove floating timezone property and localize
                iCalendar.timezoneInfo.setFloating(this, false)
                iCalendar.timezoneInfo.setTimezone(
                    this,
                    TimezoneAssignment(TimeZone.getTimeZone(xWrTimezone), xWrTimezone)
                )
                this.localizeDateToTimezone(xWrTimezone)
            } else if (!iCalendar.timezoneInfo.timezones.isNullOrEmpty() &&
                (iCalendar.timezoneInfo.timezones.size == 1 || iCalendar.timezoneInfo.timezones.map { it.timeZone?.id }.all { it == iCalendar.timezoneInfo.timezones?.firstOrNull()?.timeZone?.id }) &&
                !iCalendar.timezoneInfo.timezones?.firstOrNull()?.timeZone?.id.isNullOrEmpty()) {
                // If no x-wr-timezone is present, we check if there's a single VTIMEZONE to use in the ICS string (Exclude those with same id)
                val fallbackTimeZoneId = fallbackTimeZone(iCalendar.timezoneInfo.timezones?.firstOrNull()?.timeZone?.id ?: return false, fallbackToDefault = false) ?: return false
                // Remove floating timezone property and localize
                iCalendar.timezoneInfo.setFloating(this, false)
                iCalendar.timezoneInfo.setTimezone(
                    this,
                    TimezoneAssignment(TimeZone.getTimeZone(fallbackTimeZoneId), fallbackTimeZoneId)
                )
                this.localizeDateToTimezone(fallbackTimeZoneId)
            } else if (isImport && timeZoneId != null) {
                // Remove floating timezone property and localize
                iCalendar.timezoneInfo.setFloating(this, false)
                iCalendar.timezoneInfo.setTimezone(
                    this,
                    TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), timeZoneId)
                )
                this.localizeDateToTimezone(timeZoneId)
            } else return false
        }
        return true
    }

    private fun DateOrDateTimeProperty.localizeZuluTimeDate(iCalendar: ICalendar, event: VEvent) {
        // If it's a Zulu time, the event is non-recurring and x-wr-timezone is present and supported, localize the date-time to the supported timezone.
        val xWrTimezone = iCalendar.getXWrTimezone()
        if (event.recurrenceRule?.value == null && this.value.hasTime() && iCalendar.timezoneInfo.getTimezone(this) == null && this.value.rawComponents.toString().contains("Z") && xWrTimezone != null) {
            iCalendar.timezoneInfo.setTimezone(this, TimezoneAssignment(TimeZone.getTimeZone(xWrTimezone), xWrTimezone))
            this.setParameter(TZID, xWrTimezone)
        }
    }

    private fun DateOrDateTimeProperty.localizeDateToTimezone(timezone: String) {
        val date = Date.from(
            this.value.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
                .toInstant()
        )
        this.value = ICalDate(
            date,
            DateTimeComponents(date),
            true
        )
        this.setParameter(TZID, timezone)
    }

    private fun DateTimeProperty.localizeDateToTimezone(timezone: String) {
        val date = Date.from(
            this.value.toInstant().atZone(ZoneId.systemDefault()).withZoneSameLocal(ZoneId.of(timezone))
                .toInstant()
        )
        this.value = ICalDate(
            date,
            DateTimeComponents(date),
            true
        )
        this.setParameter(TZID, timezone)
    }

    private fun ICalendar.isInvitation(): Boolean {
        return this.method?.isReply == true || this.method?.isCounter == true || this.method?.isRefresh == true ||
                this.method?.isRequest == true || this.method?.isCancel == true || this.method?.isAdd == true ||
                this.method?.isDeclineCounter == true
    }
}
