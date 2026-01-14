package me.proton.android.calendar.common.utils

import biweekly.Biweekly
import biweekly.ICalDataType
import biweekly.ICalVersion
import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.component.VTimezone
import biweekly.io.TimezoneAssignment
import biweekly.io.TimezoneInfo
import biweekly.parameter.ParticipationStatus
import biweekly.property.*
import biweekly.util.Duration
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import com.google.crypto.tink.subtle.Hex
import com.google.crypto.tink.subtle.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.proton.android.calendar.BuildConfig
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_CREATOR
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_CREATOR_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_HOST
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_HOST_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PASSWORD
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PASSWORD_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PROVIDER
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PROVIDER_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_CONFERENCE_ID
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_CONFERENCE_URL
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_PROTON_REPLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_SESSION_KEY
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID
import me.proton.android.calendar.common.MessageDigestHashType.SHA1
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.dateToDateTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.isBetween
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.isLastDayOfWeekInMonth
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.startEndOverlapsWithFullDayRange
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toBiweeklyDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekInMonth
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstRealOccurrenceSince
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.data.entity.EventEntityMetadata
import me.proton.android.calendar.data.entity.SearchEventEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.Notification
import me.proton.android.calendar.domain.model.SkeletonEvent
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.utils.ICalUtils
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.security.MessageDigest
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

object ICalUtilsImpl : ICalUtils {

    override fun parseICalString(iCalendar: String): ICalendar? {
        return try {
            Biweekly.parse(iCalendar).first().also { normaliseICalendar(it) }
        } catch (e: Exception) {
            TimberLogger.e("error parsing iCalendar", e)
            null
        }
    }

    override fun normaliseICalendar(calendar: ICalendar) {
        // TODO replace this with global validation of all properties from biweekly
        fun String.sanitizeICalParam(): String =
            this.replace("\r", "").replace("\n", "").replace("\"", "")

        calendar.events?.forEach { vEvent ->
            vEvent?.attendees?.forEach { att ->
                att.commonName = att.commonName?.sanitizeICalParam()
            }
            vEvent?.organizer?.let { org ->
                org.commonName = org.commonName?.sanitizeICalParam()
            }
        }
    }

    /**
     * Takes iCalendar parts split according to "the matrix" and returns one iCalendar object.
     */
    override fun mergeCalendarPartsIntoICalendar(calendarStrings: List<String>): ICalendar? {
        return calendarStrings.mapNotNull { parseICalString(it) }.reduce { sum, element -> mergeICalendars(sum, element) }
    }

    /**
     * Clones the ICalendar copying timezones.
     */
    override fun ICalendar.clone(): ICalendar {
        val defaultTimezoneId = this.timezoneInfo?.defaultTimezone?.timeZone?.id
        return parseICalString(this.printToString())!!.apply { setDefaultTimeZone(defaultTimezoneId) }
    }

    /**
     * @return true if Event is valid
     */
    override fun VEvent.sanitise(): Boolean {

        if (this.dateStart == null) return false

        // TODO events with DTSTART == DTEND should not come from the API and we can't simply
        //  force this change, because we rely on this in some places in the code

        // set DTEND to be RFC compliant for all-day event with DTSTART == DTEND
        /*if (this.dateStart?.value == this.dateEnd?.value && this.dateStart?.value?.hasTime() == false) {
            val endLocalDate = this.getStart(ZoneId.systemDefault().id)!!.toLocalDate().plusDays(1)
            this.setDateEnd(endLocalDate.toDate(), false)
        }*/

        // add DTEND if not present
        if (this.dateEnd == null) {
            if (this.dateStart.value.hasTime()) {
                this.setDateEnd(this.dateStart.value)
            } else {
                val endLocalDate = this.getStart(ZoneId.systemDefault().id)!!.toLocalDate().plusDays(1)
                this.setDateEnd(endLocalDate.toDate(), false)
            }
        }

        return true
    }

    /**
     * Sanitise the event before sending it to BE or by email so that it matches RFC.
     */
    override fun VEvent.sanitiseForExternal() {

        //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
        //  set to avoid NPE in the app. We remove it when sending the ICS to BE or by mail.
        if (this.dateStart?.value == this.dateEnd?.value) {
            this.dateEnd = null
        }
    }

    /**
     * This methods clones Recurrence and overwrites only parameters supplied.
     */
    override fun Recurrence.clone(
        byDay: List<biweekly.util.DayOfWeek>?,
        bySetPos: List<Int>?,
        until: ICalDate?,
        workweekStarts: biweekly.util.DayOfWeek?
    ): Recurrence {

        val builder = Recurrence.Builder(this.frequency)

        builder.bySecond(this.bySecond)
        builder.byMinute(this.byMinute)
        builder.byHour(this.byHour)
        builder.byDay(byDay ?: this.byDay.map { it.day })
        builder.byMonthDay(this.byMonthDay)
        builder.byYearDay(this.byYearDay)
        builder.byWeekNo(this.byWeekNo)
        builder.byMonth(this.byMonth)
        builder.bySetPos(bySetPos ?: this.bySetPos)

        builder.interval(this.interval)
        builder.count(this.count)
        builder.until(until ?: this.until)
        builder.workweekStarts(workweekStarts ?: this.workweekStarts)

        return builder.build()
    }

    override fun ICalendar.adjustRRuleToStartDate(oldDateTime: ZonedDateTime?) {

        val iCalEvent = this.events.first()

        if (iCalEvent.recurrenceRule == null) return

        // TODO when editing, we keep the chosen timezone as default timezone, this is an ugly hack
        //  so this.iCalTimeZone doesn't return default timezone, but should be fixed
        val startTimeZone = this.timezoneInfo.defaultTimezone?.timeZone ?: this.iCalTimeZone(iCalEvent.dateStart)
        val startDate = iCalEvent.getStart(startTimeZone.id)!!
        val startWeekday = (iCalEvent.getStart(startTimeZone.id)!!.dayOfWeek.toBiweeklyDayOfWeek())
        val startDayWeekInMonth = iCalEvent.getStart(startTimeZone.id)!!.toLocalDate().weekInMonth()

        when (iCalEvent.recurrenceRule.value.frequency) {
            Frequency.WEEKLY -> {
                // Remove ByDay rule corresponding to old start date day and add new start date day
                val oldByDayOfWeek = ArrayList(iCalEvent.recurrenceRule.value.byDay.map { it.day })
                if (oldDateTime != null) oldByDayOfWeek.remove(oldDateTime.dayOfWeek.toBiweeklyDayOfWeek())
                if (!oldByDayOfWeek.contains(startWeekday)) oldByDayOfWeek.add(startWeekday)
                // Don't set BYDAY if happens WEEKLY on a single day
                if (oldByDayOfWeek.size > 1) iCalEvent.recurrenceRule.value = iCalEvent.recurrenceRule.value.clone(byDay = oldByDayOfWeek)
            }
            Frequency.MONTHLY -> {
                val rrule = iCalEvent.recurrenceRule.value

                if (rrule.byDay.isNotEmpty() && rrule.bySetPos.isNotEmpty()) {

                    val setPos = if (iCalEvent.getStart(startTimeZone.id)!!.toLocalDate().isLastDayOfWeekInMonth()) {
                        -1
                    } else {
                        startDayWeekInMonth
                    }

                    iCalEvent.recurrenceRule.value = iCalEvent.recurrenceRule.value.clone(
                        byDay = listOf(startWeekday),
                        bySetPos = listOf(setPos)
                    )
                }
            }
            else -> Unit
        }

        iCalEvent.recurrenceRule.value.until?.let {
            val untilDate = it.toZonedDateTime(oldDateTime?.zone?.id ?: startTimeZone.id)

            val newUntilDate = if (startDate.isAfter(untilDate)) startDate else untilDate

            val until : ICalDate = if (iCalEvent.dateStart.value.hasTime()) {
                dateToDateTime(newUntilDate, startTimeZone.id)
            } else {
                ICalDate(newUntilDate.toLocalDate().toDate(ZoneId.systemDefault().id), false)
            }

            iCalEvent.recurrenceRule.value = iCalEvent.recurrenceRule.value.clone(
                until = until
            )
        }

    }

    override fun RecurrenceRule.adjustToWeekStart(settingsWeekStart: DayOfWeek) {

        val addWkst = when (this.value.frequency) {
            Frequency.WEEKLY -> {
                this.value.interval != null && this.value.interval > 1 && (this.value.byDay?.isNotEmpty() == true)
            }
            Frequency.YEARLY -> {
                this.value.byWeekNo?.isNotEmpty() == true
            }
            else -> false
        }

        if (addWkst) {
            this.value = this.value.clone(workweekStarts = settingsWeekStart.toBiweeklyDayOfWeek())
        }

    }

    override fun VEvent.isDateTimeTheSame(that: VEvent?): Boolean {

        if (that == null) return false

        return (this.dateStart.value.toInstant() == that.dateStart.value.toInstant()) && (this.dateEnd.value.toInstant() == that.dateEnd.value.toInstant())
    }

    override fun ICalendar.isDateTimeTheSame(that: ICalendar?): Boolean {

        if (that == null) return false

        return (this.timezoneInfo.getTimezone(this.events.first().dateStart)?.timeZone?.id == that.timezoneInfo.getTimezone(that.events.first().dateStart)?.timeZone?.id) &&
                (this.timezoneInfo.getTimezone(this.events.first().dateEnd)?.timeZone?.id == that.timezoneInfo.getTimezone(that.events.first().dateEnd)?.timeZone?.id) &&
                (this.events.first().isDateTimeTheSame(that.events.first()))
    }

    override fun ICalendar.iCalTimeZone(property: ICalProperty): TimeZone {
        return if (this.timezoneInfo.isFloating(property)) {
            TimeZone.getDefault()
        } else {
            val timezone = this.timezoneInfo.getTimezone(property)
            if (timezone == null) TimeZone.getTimeZone("UTC") else timezone.timeZone
        }
    }

    /**
     * Takes one iCalendar object and splits it according to "the matrix".
     */
    override fun splitICalendarIntoParts(originalCalendar: ICalendar): CalendarSplit {

        // TODO Attendees Part

        val originalEvent = originalCalendar.events.first()

        return CalendarSplit(
            sharedPart = VEvent().run {

                val newCalendar = wrapInICalendar()

                setUid(originalEvent.uid)
                setCreated(originalEvent.created)
                setLastModified(originalEvent.lastModified)
                setDateTimeStamp(originalEvent.dateTimeStamp)
                setDateStart(originalEvent.dateStart)
                setDateEnd(originalEvent.dateEnd)
                setRecurrenceRule(originalEvent.recurrenceRule)
                setRecurrenceId(originalEvent.recurrenceId)
                setSequence(originalEvent.sequence)
                originalEvent.exceptionDates.forEachIndexed { index, exceptionDate ->
                    addExceptionDates(exceptionDate)

                    // copy timezone assignments for EXDATEs
                    val timezoneAssignment = originalCalendar.timezoneInfo.getTimezone(exceptionDate) ?: originalCalendar.timezoneInfo.defaultTimezone
                    if (timezoneAssignment != null) {
                        newCalendar.timezoneInfo.setTimezone(exceptionDates[index], timezoneAssignment)
                    }
                }
                setOrganizer(originalEvent.organizer)

                originalEvent.getExperimentalProperty(X_PM_CONFERENCE_ID)?.let { originalConferenceIdProperty ->
                    setExperimentalProperty(
                        X_PM_CONFERENCE_ID,
                        originalConferenceIdProperty.value
                    ).run {
                        val originalProvider = originalConferenceIdProperty.parameters.get(PARAMETER_CONFERENCE_PROVIDER)?.takeIfNotEmpty()
                            ?: originalConferenceIdProperty.parameters.get(PARAMETER_CONFERENCE_PROVIDER_READONLY)
                        if (!originalProvider.isNullOrEmpty()) this.setParameter(PARAMETER_CONFERENCE_PROVIDER, originalProvider)

                        val originalCreator = originalConferenceIdProperty.parameters.get(PARAMETER_CONFERENCE_CREATOR).takeIfNotEmpty()
                            ?: originalConferenceIdProperty.parameters.get(PARAMETER_CONFERENCE_CREATOR_READONLY)
                        if (!originalCreator.isNullOrEmpty()) this.setParameter(PARAMETER_CONFERENCE_CREATOR, originalCreator)
                    }
                }

                // copy timezone assignments
                newCalendar.timezoneInfo.setTimezone(this.dateStart, originalCalendar.timezoneInfo.getTimezone(originalEvent.dateStart) ?: originalCalendar.timezoneInfo.defaultTimezone)
                newCalendar.timezoneInfo.setTimezone(this.dateEnd, originalCalendar.timezoneInfo.getTimezone(originalEvent.dateEnd) ?: originalCalendar.timezoneInfo.defaultTimezone)
                newCalendar.timezoneInfo.setTimezone(this.recurrenceId, originalCalendar.timezoneInfo.getTimezone(originalEvent.recurrenceId) ?: originalCalendar.timezoneInfo.defaultTimezone)

                // delete timezone info created automatically when setting timezones
                newCalendar.timezoneInfo.timezones.clear()

                newCalendar
            },
            sharedPartToEncrypt = VEvent().run {
                setUid(originalEvent.uid)
                setDateTimeStamp(originalEvent.dateTimeStamp)
                setDescription(originalEvent.description) // TODO force substring to be max VALIDATION_EVENT_DESCRIPTION_MAX_LENGTH long?
                setSummary(originalEvent.summary) // TODO force substring to be max VALIDATION_EVENT_SUMMARY_MAX_LENGTH long?
                setLocation(originalEvent.location) // TODO force substring to be max VALIDATION_EVENT_LOCATION_MAX_LENGTH long?

                originalEvent.getExperimentalProperty(X_PM_CONFERENCE_URL)?.let { originalConferenceUrlProperty ->
                    setExperimentalProperty(
                        X_PM_CONFERENCE_URL,
                        originalConferenceUrlProperty.value
                    ).run {
                        val originalPassword = originalConferenceUrlProperty.parameters.get(PARAMETER_CONFERENCE_PASSWORD).takeIfNotEmpty()
                            ?: originalConferenceUrlProperty.parameters.get(PARAMETER_CONFERENCE_PASSWORD_READONLY)
                        if (!originalPassword.isNullOrEmpty()) this.setParameter(PARAMETER_CONFERENCE_PASSWORD, originalPassword)

                        val originalHost = originalConferenceUrlProperty.parameters.get(PARAMETER_CONFERENCE_HOST).takeIfNotEmpty()
                            ?: originalConferenceUrlProperty.parameters.get(PARAMETER_CONFERENCE_HOST_READONLY)
                        if (!originalHost.isNullOrEmpty()) this.setParameter(PARAMETER_CONFERENCE_HOST, originalHost)
                    }
                }

                wrapInICalendar()
            },
            calendarPart = if (originalEvent.status != null || originalEvent.transparency != null) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setDateTimeStamp(originalEvent.dateTimeStamp)
                    setStatus(originalEvent.status)
                    setTransparency(originalEvent.transparency)
                    wrapInICalendar()
                }
            } else null,
            calendarPartToEncrypt = if (originalEvent.comments.isNotEmpty()) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setDateTimeStamp(originalEvent.dateTimeStamp)
                    originalEvent.comments.forEach {
                        addComment(it)
                    }
                    // TODO here should be inserted "all the rest" of the properties
                    wrapInICalendar()
                }
            } else null,
            personalPart = if (originalEvent.alarms.isNotEmpty()) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setDateTimeStamp(originalEvent.dateTimeStamp)
                    originalEvent.alarms.forEach {
                        addAlarm(it)
                    }
                    wrapInICalendar()
                }
            } else null,
            attendeesPart = if (originalEvent.attendees.isNotEmpty()) {
                VEvent().run {
                    setUid(originalEvent.uid)
                    setDateTimeStamp(originalEvent.dateTimeStamp)
                    originalEvent.attendees.forEach {
                        addAttendee(it)
                    }
                    wrapInICalendar()
                }
            } else null
        )
    }

    /**
     * The token is calculated by doing SHA1(EventUID + canonicalAttendeeAddress)
     */
    override fun generateXPmToken(email: String, uid: String): String {
        val messageDigest = MessageDigest.getInstance(SHA1)
        messageDigest.update((uid + email).toByteArray())
        val token = messageDigest.digest()
        return Hex.encode(token)
    }

    /**
     * This function merges only first top-level component of type VEvent.
     *
     * In the future we can extend this to support VTodo and custom components.
     */
    override fun mergeICalendars(left: ICalendar, right: ICalendar) : ICalendar {

        // copy components and properties from the only event there is
        right.events.first().components.forEach { components ->
            components.value.forEach { iCalComponent ->
                if (iCalComponent is VAlarm) {
                    left.events.first().addComponent(iCalComponent)
                } else {
                    if (iCalComponent !in left.events.first().components.values()) {
                        left.events.first().addComponent(iCalComponent)
                    }
                }
            }
        }
        right.events.first().properties.forEach { properties ->
            properties.value.forEach { iCalProperty ->
                // We use setProperty to avoid having duplicates, but we need to use addProperty for Attendees
                // to properly add multiple ones
                if (iCalProperty::class == Attendee::class) left.events.first().addProperty(iCalProperty)
                else if (iCalProperty::class == RawProperty::class) left.events.first().addProperty(iCalProperty)
                else if (iCalProperty::class == ExceptionDates::class) left.events.first().addProperty(iCalProperty)
                else if (iCalProperty::class == DateTimeStamp::class) {
                    // Take latest DateTimeStamp
                    val leftDateTimeStamp = left.events.first().getProperty(DateTimeStamp::class.java)?.value
                    if (leftDateTimeStamp == null || (iCalProperty as DateTimeStamp).value.after(leftDateTimeStamp))
                        left.events.first().setProperty(iCalProperty)
                } else left.events.first().setProperty(iCalProperty)
                left.timezoneInfo.setTimezone(iCalProperty, right.timezoneInfo.getTimezone(iCalProperty))
            }
        }

        // copy additional metadata that is not yet set
        if (left.productId == null) {
            left.productId = right.productId
        }

        return left
    }

    override fun createNewVEvent() = VEvent().apply {
        setUid(generateProtonUid())
        setStatus(Status(Status.CONFIRMED)) // TODO set this as default if imported event has this field empty
        setSequence(0)
    }

    override fun generateEventStart(timeZoneId: ZoneId, selectedDate: LocalDate?): LocalDateTime {
        val startDate = selectedDate?.let {
            ZonedDateTime.of(selectedDate, LocalTime.now(timeZoneId), timeZoneId)
        } ?: ZonedDateTime.now(timeZoneId)
        return if (startDate.toLocalTime().toSecondOfDay() > TimeUnit.HOURS.toSeconds(23) + TimeUnit.MINUTES.toSeconds(30)) {
            startDate.plusDays(1).toLocalDate().atStartOfDay()
        } else startDate.plusMinutes(30L - (startDate.minute % 30)).toLocalDateTime()
    }

    /**
     * Generates Proton UID for new ICalendar components.
     */
    override fun generateProtonUid() = "${com.google.crypto.tink.subtle.Base64.urlSafeEncode(Random.randBytes(21))}@proton.me"

    /**
     * Generates UID in the form of "original UID prefix + recurrenceId + original UID postfix (after @ symbol)".
     */
    override fun generateProtonUid(originalUid: String, recurrenceId: String): String {
        // UID has a maximum length allowed so we need to remove existing date from originalUid
        val dateRegex = Regex("_R\\d{8}T\\d{6}")
        val cleanOriginalUid = originalUid.replace(dateRegex, "")
        val provider = cleanOriginalUid.substringAfterLast("@", "")
        return "${cleanOriginalUid.substringBeforeLast("@", cleanOriginalUid)}_R$recurrenceId" + if (provider.isNotEmpty()) "@${provider}" else ""
    }

    /**
     * Generates Proton UID for an imported event.
     */
    override fun generateProtonUidForImport(originalEventUid: String?, ics: String): String {
        val messageDigest = MessageDigest.getInstance(SHA1)
        messageDigest.update(ics.toByteArray())
        val token = messageDigest.digest()
        val icsHash = Hex.encode(token)
        return if (originalEventUid.isNullOrBlank()) {
            "sha1-uid-${icsHash}"
        } else {
            val croppedOriginalEventUid =
                if (originalEventUid.length > 128) originalEventUid.takeLast(128)
                else originalEventUid
            "original-uid-$croppedOriginalEventUid-sha1-uid-${icsHash}"
        }
    }

    /**
     * Generates Proton Product Identifier.
     */
    override fun generateProtonProdId() = "-//Proton AG//$PROD_ID_APPLICATION_NAME ${BuildConfig.VERSION_NAME}//EN"

    /**
     * Generates offline CalendarID to use before it's successfully sent to server.
     */
    override fun generateOfflineEventId() = "$OFFLINE_EVENT_ID_PREFIX${UUID.randomUUID()}${UUID.randomUUID()}${UUID.randomUUID()}"

    /**
     * Generates offline AlarmID for offline alarms calculated locally.
     */
    override fun generateOfflineAlarmId() = "$OFFLINE_ALARM_ID_PREFIX${UUID.randomUUID()}${UUID.randomUUID()}${UUID.randomUUID()}"

    /**
     * Returns iCal Events with Occurrence, but does not overwrite the DTSTART/DTEND. See [withOccurrence]
     * Takes single edits into account.
     *
     * @param events all single edits selected by UID
     */
    override fun expandOccurrencesWithSingleEdits(originalEvent: Event, events: List<Event>, toDate: LocalDate, timeZoneId: String): List<Event>? {

        val maxRecurrenceIdEvent = events.maxByOrNull { it.iCalEvent.recurrenceId?.value?.time ?: Long.MIN_VALUE }

        // take either maximum RecurrenceId from single edits or the requested "toDate"
        val maxToDate = if (maxRecurrenceIdEvent?.iCalEvent?.recurrenceId?.value?.toInstant()?.isAfter(toDate.plusDays(1).atStartOfDay(ZoneId.of(timeZoneId)).toInstant()) == true) {
            ZonedDateTime.ofInstant(maxRecurrenceIdEvent.iCalEvent.recurrenceId?.value?.toInstant(), ZoneId.of(timeZoneId)).toLocalDate()
        } else {
            toDate
        }
        val occurrences = originalEvent.generateOccurrencesUntil(maxToDate, timeZoneId) ?: return null

        return occurrences.map { occurrence ->
            val event = Event.from(
                events.find {
                    it.iCalEvent.recurrenceId?.value == eventStartZonedDateTimeToDate(occurrence.startDateTime, originalEvent.isAllDay())
                } ?: originalEvent
            )
            event.occurrence = occurrence
            event
        }

    }

    /**
     * Generated Event objects contain distinct Occurrence properties, but they point to the same ICalendar object!
     */
    override fun expandOccurrencesWithSingleEdits(
        originalEvent: Event,
        eventsSharingUid: List<Event>,
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): List<Event>? {

        val maxRecurrenceIdEvent = eventsSharingUid.maxByOrNull { it.iCalEvent.recurrenceId?.value?.time ?: Long.MIN_VALUE }
        val maxToDate = if (maxRecurrenceIdEvent?.iCalEvent?.recurrenceId?.value?.toInstant()?.isAfter(toDate.atStartOfDay(ZoneId.of(timeZoneId)).toInstant()) == true) {
            ZonedDateTime.ofInstant(maxRecurrenceIdEvent.iCalEvent.recurrenceId?.value?.toInstant(), ZoneId.of(timeZoneId)).toLocalDate()
        } else {
            toDate
        }

        val occurrences = originalEvent.generateOccurrencesUntil(maxToDate, timeZoneId) ?: return null

        return occurrences.map { occurrence ->
            val event = Event.from(
                eventsSharingUid.find {
                    it.iCalEvent.recurrenceId?.value == eventStartZonedDateTimeToDate(
                        occurrence.startDateTime,
                        originalEvent.isAllDay()
                    )
                } ?: originalEvent
            )
            event.occurrence = occurrence
            event
        }.filter { // TODO filterFromEnd doesn't work if there are gaps in occurrences caused by single edits
            val actualStart = it.getOccurrenceStart(timeZoneId) ?: return@filter false
            val actualEnd = it.getOccurrenceEnd(timeZoneId) ?: return@filter false
            startEndOverlapsWithFullDayRange(actualStart, actualEnd, fromDate, toDate, timeZoneId)
        }

    }

    /**
     * Creates ICalendar using only plaintext shared event part.
     */
    override fun toICalendarFromPlaintextSharedPart(json: Json, sharedEvents: List<JsonElement>): ICalendar? {

        val sharedPlainTextPart = sharedEvents.asSequence().map { json.decodeFromJsonElement<Event.EventPart.Shared>(it) }.firstOrNull { !it.isEncrypted }

        return sharedPlainTextPart?.let {
            parseICalString(it.data)
        }

    }

    /**
     * Given original Event, filter out all occurrences that are excluded by EXDATE
     */
    override fun List<Event>.filterOutOccurrencesByExdates(originalEvent: Event, timeZoneId: String): List<Event> {

        val exZonedDateTimes =
            originalEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                exDates.values.map { exDate ->
                    exDate.toZonedDateTime(timeZoneId)
                }
            }

        return if (exZonedDateTimes.isNullOrEmpty()) {
            this
        } else {
            this.filterNot {
                it.occurrence!!.startDateTime in exZonedDateTimes
            }
        }
    }

    override fun List<Event.Occurrence>.filterOutEventOccurrencesByExdates(
        originalEvent: Event,
        timeZoneId: String
    ): List<Event.Occurrence> {

        val exZonedDateTimes =
            originalEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                exDates.values.map { exDate ->
                    exDate.toZonedDateTime(timeZoneId)
                }
            }

        return if (exZonedDateTimes.isNullOrEmpty()) {
            this
        } else {
            this.filterNot {
                it.startDateTime in exZonedDateTimes
            }
        }

    }

    override fun List<EventAlarmEntity>.filterOutDuplicates(): List<EventAlarmEntity> {
        return this.distinctBy { "${it.eventId} ${it.occurrence} ${Duration.parse(it.trigger).toMillis()} ${it.action}" }
    }

    override fun List<Event>.filterOutEventsBySearchTerm(searchTerm: String): List<Event> {

        val filtered = this.map { SearchEventEntity.from("we don't have it in Event yet, should not matter here", it) }.filterOutBySearchTerm(searchTerm)

        return this.filter { event -> filtered.any { it.calendarId == event.calendar.id && it.eventId == event.id } }
    }

    override fun List<SearchEventEntity>.filterOutBySearchTerm(searchTerm: String): List<SearchEventEntity> {
        val searchTokens = searchTerm.lowercase().split(" ")

        return this.filter { searchEventEntity ->
            searchTokens.all {
                searchEventEntity.summary.lowercase().contains(it, ignoreCase = true) ||
                        searchEventEntity.description.lowercase().contains(it, ignoreCase = true) ||
                        searchEventEntity.location.lowercase().contains(it, ignoreCase = true) ||
                        searchEventEntity.organizer.lowercase().contains(it, ignoreCase = true) ||
                        searchEventEntity.attendees.lowercase().contains(it, ignoreCase = true)
            }
        }
    }

    override fun List<SkeletonEvent>.filterOutDuplicatesInSubscribedCalendars(): Pair<List<SkeletonEvent>, List<SkeletonEvent>> {

        val grouped = this.groupBy { "${it.uid}, ${it.calendar.id}, ${it.getRecurrenceId("UTC")}, ${it.occurrence?.occurrenceNumber}, ${it.iCalEvent.dateStart?.value?.time}" }

        val unique = mutableListOf<SkeletonEvent>()
        val duplicated = mutableListOf<SkeletonEvent>()

        grouped.forEach {

            val skeletons = it.value

            // Calendar is subscribed, find the best Skeleton
            if (skeletons.first().calendar.isSubscribed) {
                // find max modifyTime
                val maxModifyTime = skeletons.maxByOrNull { it.modifyTime }?.modifyTime

                // in case of different Skeletons with the same modifyTime, make sure to always return the same one
                val uniqueSkeletonId = skeletons.filter { it.modifyTime == maxModifyTime }.maxByOrNull { it.id }?.id

                // split skeletons for this one event into 1 unique and the rest are duplicates
                skeletons.forEach { sk ->
                    if (sk.id == uniqueSkeletonId) {
                        unique.add(sk)
                    } else {
                        duplicated.add(sk)
                    }
                }
            } else {
                // Calendar is regular type, add all Skeletons to result (in theory it should be only one)
                unique.addAll(skeletons)
            }

        }

        return Pair(unique, duplicated)
    }

    override fun List<EventEntityMetadata>.filterOutDuplicatesInSubscribedCalendars(
        calendars: List<Calendar>
    ): Pair<List<EventEntityMetadata>, List<EventEntityMetadata>> {

        val subscribedCalendarIds = calendars.filter { it.isSubscribed }.map { it.id }
        val grouped = this.groupBy {
            "${it.uid}, ${it.calendarId}, ${it.recurrenceID}, ${it.startTime}"
        }

        val unique = mutableListOf<EventEntityMetadata>()
        val duplicated = mutableListOf<EventEntityMetadata>()

        grouped.forEach {

            val entities = it.value

            // Calendar is subscribed, find the best Skeleton
            if (subscribedCalendarIds.contains(entities.first().calendarId)) {
                // find max modifyTime
                val maxModifyTime = entities.maxByOrNull { it.modifyTime }?.modifyTime

                // in case of different Skeletons with the same modifyTime, make sure to always return the same one
                val uniqueSkeletonId = entities.filter { it.modifyTime == maxModifyTime }.maxByOrNull { it.id }?.id

                // split skeletons for this one event into 1 unique and the rest are duplicates
                entities.forEach { sk ->
                    if (sk.id == uniqueSkeletonId) {
                        unique.add(sk)
                    } else {
                        duplicated.add(sk)
                    }
                }
            } else {
                // Calendar is regular type, add all Skeletons to result (in theory it should be only one)
                unique.addAll(entities)
            }

        }

        return Pair(unique, duplicated)
    }

    /**
     * Returns event ZonedDateTime on Date format
     * Converts it to default timezone when event is all day
     */
    override fun eventStartZonedDateTimeToDate(startDate: ZonedDateTime, isAllDay: Boolean): Date {
        // TODO Make utils method to get correct Date.from value
        return if (isAllDay) Date.from(startDate.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant())
        else Date.from(startDate.toInstant())
    }

    override fun calculateAlarmEntity(event: Event, vAlarm: VAlarm, timeZoneId: String, memberId: String): EventAlarmEntity {

        val occurrence = ZonedDateTime.ofInstant(vAlarm.trigger.duration.add(event.iCalEvent.dateStart.value).toInstant(), ZoneId.systemDefault())

        val occurrenceInTimeZone = if (event.isAllDay()) {
            occurrence.withZoneSameLocal(ZoneId.of(timeZoneId))
        } else occurrence

        return EventAlarmEntity(
            generateOfflineAlarmId(),
            occurrenceInTimeZone.toEpochSecond(),
            vAlarm.trigger.duration.toString(),
            if (vAlarm.action.isDisplay) 2 else 1,
            event.id,
            memberId,
            event.calendar.id
        )
    }

    /**
     * Calculates all Alarm Entities for any given Event occurrence in the format used in API.
     *
     * ID is generated locally.
     */
    override fun calculateAlarmEntities(event: Event, timeZoneId: String, memberId: String): List<EventAlarmEntity> {
        return event.alarms.map {
            calculateAlarmEntity(event, it, timeZoneId, memberId)
        }
    }

    /**
     * Calculates upcoming Alarms (triggering starting from [now]) for the upcoming occurrences of all the events
     * supplied, filtered by exdates and single edits if they are in [events].
     *
     * If you need to refresh all alarms for an Event, it's best to supply here all the events in chain
     * (sharing the same UID).
     */
    override fun calculateUpcomingAlarmEntities(events: List<Event>, now: ZonedDateTime, memberId: String
    ): List<EventAlarmEntity> {
        return events.flatMap { event ->
            event.alarms.mapNotNull { vAlarm ->

                val triggerRelativeSeconds = vAlarm.trigger.duration.toMillis() / 1000

                val generateOccurrenceSince = now.minusSeconds(triggerRelativeSeconds)

                val eventOccurrence = if (event.isRecurring()) {
                    val occurrence = event.generateFirstRealOccurrenceSince(events, generateOccurrenceSince)
                    occurrence?.let { Event.withOccurrence(event, it) }
                } else {
                    event
                }

                eventOccurrence?.let {
                    val alarmEntity = calculateAlarmEntity(it, vAlarm, now.zone.id, memberId)
                    alarmEntity
                }
            }
        }.filter { it.occurrence >= now.toEpochSecond() }
    }

    override fun List<EventAlarmEntity>.onlyDisplayType(): List<EventAlarmEntity> {
        return this.filter { it.action == 2 }
    }

    override fun isCalendarChangeAllowed(fromEvent: Event, toEvent: Event): Boolean {

        val isFromEventAnInvitation =
            fromEvent.iCalEvent.attendees?.isNotEmpty() == true || fromEvent.iCalEvent.organizer != null

        val isCurrentEventAnInvitation =
            toEvent.iCalEvent.attendees?.isNotEmpty() == true || toEvent.iCalEvent.organizer != null

        if (isFromEventAnInvitation) return false

        if (isCurrentEventAnInvitation) return false

        if (fromEvent.isPartOfChain() || !CalendarFeatureFlag.ChangeCalendarSimpleEvent.fallbackValue) return false

        return true
    }

    /**
     * Creates new ICalendar object and sets this VEvent as only event.
     */
    override fun VEvent.wrapInICalendar(): ICalendar {
        val calendar = ICalendar()
        calendar.setProductId(generateProtonProdId())
        calendar.addEvent(this)
        return calendar
    }

    // TODO we strip out "global timezone forward slash" manually, because for some requests server refuses to accept it
    override fun ICalendar.printToString(): String {
        fun String.sanitizeICalParam() = replace("\r", "").replace("\n", "").replace("\"", "")
        this.events?.forEach { e ->
            e.attendees?.forEach { a -> a.commonName = a.commonName?.sanitizeICalParam() }
            e.organizer?.let { o -> o.commonName = o.commonName?.sanitizeICalParam() }
        }
        return Biweekly.write(this).go().replace("TZID=/", "TZID=")
    }

    override fun VEvent.setStart(date: LocalDate) {
        this.setDateStart(date.toDate(), false)
    }

    override fun VEvent.setEnd(date: LocalDate) {
        this.setDateEnd(date.toDate(), false)
    }

    override fun VEvent.setStart(date: LocalDate, time: LocalTime, timeZoneId: String?) {
        this.setDateStart(Date.from(LocalDateTime.of(date, time.truncatedTo(ChronoUnit.MINUTES)).atZone(ZoneId.of(timeZoneId)).toInstant()), true)
    }

    override fun VEvent.setEnd(date: LocalDate, time: LocalTime, timeZoneId: String?) {
        this.setDateEnd(Date.from(LocalDateTime.of(date, time.truncatedTo(ChronoUnit.MINUTES)).atZone(ZoneId.of(timeZoneId)).toInstant()), true)
    }

    override fun VEvent.setStart(time: LocalTime, timeZoneId: String?) {
        this.setDateStart(Date.from(ZonedDateTime.ofInstant(this.dateStart.value.toInstant(), ZoneId.of(timeZoneId)).with(time).toInstant()), true)
    }

    override fun VEvent.setEnd(time: LocalTime, timeZoneId: String?) {
        this.setDateEnd(Date.from(ZonedDateTime.ofInstant(this.dateEnd.value.toInstant(), ZoneId.of(timeZoneId)).with(time).toInstant()), true)
    }

    /**
     * Sets or clears TimeZone for Date Start.
     */
    override fun ICalendar.setStartTimeZone(timeZoneId: String?) {
        this.events.first()?.dateStart?.let { this.timezoneInfo.setTimezone(this.events.first().dateStart, if (timeZoneId == null) null else TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), VTimezone(timeZoneId))) }
    }

    /**
     * Sets or clears TimeZone for Date End.
     */
    override fun ICalendar.setEndTimeZone(timeZoneId: String?) {
        this.events.first()?.dateEnd?.let { this.timezoneInfo.setTimezone(this.events.first().dateEnd, if (timeZoneId == null) null else TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), VTimezone(timeZoneId))) }
    }

    override fun ICalendar.setDefaultTimeZone(timeZoneId: String?) {
        this.timezoneInfo.defaultTimezone = if (timeZoneId == null) null else TimezoneAssignment(TimeZone.getTimeZone(timeZoneId), VTimezone(timeZoneId))
    }

    /**
     * Sets start and end timezones, preserving the original local datetimes.
     */
    override fun ICalendar.adjustStartEndTimeZones(currentDateTimeTimezoneId: String, timeZoneId: String) {

        val event = this.events.first()

        TimberLogger.d("adjusting timezone from ${currentDateTimeTimezoneId} to $timeZoneId")

        val endTimeZoneId = currentDateTimeTimezoneId//this.timezoneInfo.getTimezone(event.dateEnd)?.timeZone?.id ?: this.timezoneInfo.defaultTimezone?.timeZone?.id ?: "UTC"

        TimberLogger.d("adjusting using timezone $currentDateTimeTimezoneId, $endTimeZoneId")

        event.setStart(event.getStart(currentDateTimeTimezoneId)!!.toLocalDate(), event.getStart(currentDateTimeTimezoneId)!!.toLocalTime(), timeZoneId)
        event.setEnd(event.getEnd(endTimeZoneId)!!.toLocalDate(), event.getEnd(endTimeZoneId)!!.toLocalTime(), timeZoneId)

        this.setStartTimeZone(timeZoneId)
        this.setEndTimeZone(timeZoneId)

        this.timezoneInfo.timezones.clear()

    }

    /**
     * Removes Timezone Assignments and sets correct DTEND according to standard, not GUI form.
     *
     * @param timeZoneId needed to correctly interpret Dates if we are about to remove timezone info
     */
    override fun ICalendar.adjustOutgoingAllDayEvent(timeZoneId: String) {
        this.timezoneInfo.timezones.clear()
        this.events.first().apply {
            setStart(this.getStart(timeZoneId)!!.toLocalDate())
            setEnd(this.getEnd(timeZoneId)!!.toLocalDate().plusDays(1))
        }
    }

    override fun ICalendar.adjustIncomingAllDayEvent() {
        this.events.first().apply {

            if (this.dateStart.value != null && !this.dateStart.value.hasTime()) {
                if (this.dateStart.value == this.dateEnd.value) {
                    val endLocalDate = this.getStart(ZoneId.systemDefault().id)!!.toLocalDate().plusDays(1)
                    this.setDateEnd(endLocalDate.toDate(), false)
                }
            }
        }
    }


    override fun VEvent.getStart(timeZoneId: String): ZonedDateTime? {

        if (this.dateStart?.value == null) return null

        return this.dateStart.value.toZonedDateTime(timeZoneId)
    }

    override fun VEvent.getEnd(timeZoneId: String): ZonedDateTime? {

        if (this.dateEnd?.value == null) return null

        return this.dateEnd.value.toZonedDateTime(timeZoneId)
    }

    override fun Attendee.extractEmail(): String? {
        return extractEmail(this.uri, this.email, this.commonName)
    }

    override fun Organizer.extractEmail(): String? {
        return extractEmail(this.uri, this.email, this.commonName)
    }

    private fun extractEmail(uri: String?, email: String?, commonName: String?): String? {
        return when {
            uri?.contains("@") == true -> uri.substringAfter("mailto:")
            email?.contains("@") == true -> email
            commonName?.contains("@") == true -> commonName
            else -> null
        }
    }

    /**
     * Groups all-day and spanning multiple days Events first.
     */
    override fun List<Event>.sortForAgendaView(timeZoneId: String): List<Event> {
        val groupedByAllDayEvents = this.groupBy { it.isAllDay() || !it.spansSingleDay(timeZoneId = timeZoneId) }
        val result = mutableListOf<Event>()
        result.addAll(
            groupedByAllDayEvents.get(true)?.sortedWith(compareBy({ it.getOccurrenceStart(timeZoneId) }, { it.summary }))
                ?: emptyList()
        )
        result.addAll(
            groupedByAllDayEvents.get(false)?.sortedWith(compareBy({ it.getOccurrenceStart(timeZoneId) }, { it.summary }))
                ?: emptyList()
        )
        return result
    }

    /**
     * Groups all-day and spanning multiple days Events first.
     */
    override fun List<UiEvent>.sortUiEventsForAgendaView(timeZoneId: String): List<UiEvent> {
        val groupedByAllDayEvents = this.groupBy { it.isAllDay || !it.spansSingleDay() }
        val result = mutableListOf<UiEvent>()
        result.addAll(
            groupedByAllDayEvents.get(true)?.sortedWith(compareBy({ it.dateStart }, { it.summary }))
                ?: emptyList()
        )
        result.addAll(
            groupedByAllDayEvents.get(false)?.sortedWith(compareBy({ it.dateStart }, { it.summary }))
                ?: emptyList()
        )
        return result
    }

    /**
     * @returns true if event a is all day and event b is partial single day
     */
    private fun isAllDayPrio(timeZoneId: String, a: Event, b: Event): Boolean {
        return a.isAllDay() &&
                !b.isAllDay() &&
                a.getOccurrenceStart(
                    timeZoneId
                ).toLocalDate().isEqual((b.getOccurrenceEnd(
                    timeZoneId
                )).toLocalDate()) && b.spansSingleDay(timeZoneId = timeZoneId)
    }

    /**
     * @returns true if event a is single day and event b is spanning multiple days
     */
    private fun isMultiDayPrio(timeZoneId: String, a: Event, b: Event): Boolean {
        return !a.isAllDay() &&
                !b.isAllDay() &&
                !a.spansSingleDay(timeZoneId = timeZoneId) &&
                b.spansSingleDay(timeZoneId = timeZoneId)
    }

    /**
     * @returns true if event a is all day and event b is partial single day
     */
    private fun isAllDayPrio(a: UiEvent, b: UiEvent): Boolean {
        return a.isAllDay &&
                !b.isAllDay &&
                a.dateStart.toLocalDate().isEqual((b.dateEnd).toLocalDate()) &&
                b.spansSingleDay()
    }

    /**
     * @returns true if event a is single day and event b is spanning multiple days
     */
    private fun isMultiDayPrio(a: UiEvent, b: UiEvent): Boolean {
        return !a.isAllDay &&
                !b.isAllDay &&
                !a.spansSingleDay() &&
                b.spansSingleDay()
    }

    /**
     * Sorts events with following order:
     * 1- All day spanning multiple days
     * 2- All day
     * 3- Partial day spanning multiple days
     * 4- Partial day
     */
    override fun List<Event>.sortForMonthView(timeZoneId: String): List<Event> {
        val comparator = Comparator<Event> { a, b ->
            return@Comparator when {
                isAllDayPrio(timeZoneId, a, b) ->  -1
                isAllDayPrio(timeZoneId, b, a) -> 1
                isMultiDayPrio(timeZoneId, a, b) -> -1
                isMultiDayPrio(timeZoneId, b, a) -> 1
                else -> {
                    val coefficient1 = (a.getOccurrenceStart(timeZoneId)).toEpochSecond() - (b.getOccurrenceStart(timeZoneId)).toEpochSecond()
                    val coefficient2 = (b.getOccurrenceEnd(timeZoneId)).toEpochSecond() - (a.getOccurrenceEnd(timeZoneId)).toEpochSecond()

                    if (coefficient1 > 0) 1
                    else if (coefficient1 < 0) -1
                    else {
                        if (coefficient2 > 0) 1
                        else if (coefficient2 < 0) -1
                        else 0
                    }
                }
            }
        }
        return this.sortedWith(comparator)
    }

    /**
     * Sorts events with following order:
     * 1- All day spanning multiple days
     * 2- All day
     * 3- Partial day spanning multiple days
     * 4- Partial day
     */
    override fun List<UiEvent>.sortForMonthView(): List<UiEvent> {
        val comparator = Comparator<UiEvent> { a, b ->
            return@Comparator when {
                isAllDayPrio(a, b) ->  -1
                isAllDayPrio(b, a) -> 1
                isMultiDayPrio(a, b) -> -1
                isMultiDayPrio(b, a) -> 1
                else -> {
                    val coefficient1 = a.dateStart.toEpochSecond() - b.dateStart.toEpochSecond()
                    val coefficient2 = b.dateEnd.toEpochSecond() - a.dateEnd.toEpochSecond()

                    if (coefficient1 > 0) 1
                    else if (coefficient1 < 0) -1
                    else {
                        if (coefficient2 > 0) 1
                        else if (coefficient2 < 0) -1
                        else 0
                    }
                }
            }
        }
        return this.sortedWith(comparator)
    }

    override fun List<Event>.explodeEventDayByDay(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Map<LocalDate, List<Event>> {

        val result = mutableMapOf<LocalDate, MutableList<Event>>()

        this.forEach { event ->

            val startDate = event.getOccurrenceStart(timeZoneId).toLocalDate()

            if (event.spansSingleDay(timeZoneId = timeZoneId)) {
                result[startDate] = (result[startDate] ?: mutableListOf()).apply { add(event) }
            } else {
                val spansDays = event.calculateFullDayCounter(startDate, timeZoneId).second
                for (dayNumber in 0 until spansDays) {
                    result[startDate.plusDays(dayNumber.toLong())] = (result[startDate.plusDays(dayNumber.toLong())] ?: mutableListOf()).apply { add(event) }
                }
            }

        }

        return result.filterKeys { it.isBetween(fromDate, toDate) }
    }

    override fun List<UiEvent>.explodeDayByDay(
        fromDate: LocalDate,
        toDate: LocalDate,
        timeZoneId: String
    ): Map<LocalDate, List<UiEvent>> {

        val result = mutableMapOf<LocalDate, MutableList<UiEvent>>()

        this.forEach { event ->

            val startDate = event.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate()

            if (event.spansSingleDay()) {
                result[startDate] = (result[startDate] ?: mutableListOf()).apply { add(event) }
            } else {
                val spansDays = event.calculateFullDayCounter(startDate).second
                for (dayNumber in 0 until spansDays) {
                    result[startDate.plusDays(dayNumber.toLong())] = (result[startDate.plusDays(dayNumber.toLong())] ?: mutableListOf()).apply { add(event) }
                }
            }

        }

        return result.filterKeys { it.isBetween(fromDate, toDate) }
    }

    /**
     * Filters out original Events that have occurrences with RECURRENCE-ID pointing to
     * that original Event.
     */
    override fun List<Event>.filterOccurencesByRecurrenceId(): List<Event> { // TODO take SEQUENCE into account when filtering

        // TODO maybe we should make this use LocalDate so we can use an actual value of the RECURRENCE-ID
        return this.groupBy({it.uid}).mapValues { events ->
            events.value.find { it.iCalEvent.recurrenceId != null } ?: events.value.first()
        }.map { it.value }.toList()
    }

    override fun formatUidForICal(eventUid: String): String {
        // ICal fields maximum length is 75 octets. It separates its values with "\r\n[space]" when needed. In order to fetch
        //  the UID value from SharedEvents in DB, we need to add the ICal separator to our UID if its length is more than 75
        return if ((ICAL_UID_PREFIX + eventUid).length > ICAL_LINE_MAXIMUM_LENGTH) {
            val eventUidValueLines = arrayListOf<String>()
            val maxLengthWithPrefixIndex = ICAL_LINE_MAXIMUM_LENGTH - ICAL_UID_PREFIX.length
            eventUidValueLines.add(eventUid.substring(0, maxLengthWithPrefixIndex))
            var uid = eventUid.substring(maxLengthWithPrefixIndex)
            // From this point onward we need to count the space separator as part of the string when checking max line length
            while (uid.length > ICAL_LINE_MAXIMUM_LENGTH - 1) {
                eventUidValueLines.add(uid.substring(0, ICAL_LINE_MAXIMUM_LENGTH - 1))
                uid = uid.substring(ICAL_LINE_MAXIMUM_LENGTH - 1)
            }
            eventUidValueLines.add(uid)
            return eventUidValueLines.joinToString(ICAL_LINE_SEPARATOR)
        } else eventUid
    }

    override fun getResponseIcs(
        responseICalendar: ICalendar,
        userAttendee: Attendee,
        participationStatus: ParticipationStatus,
        originalTimeZoneInfo: TimezoneInfo?,
        dtStamp: Date,
        isProtonProtonInvite: Boolean,
        sharedEventId: String?,
        sharedSessionKey: String?
    ): String {
        // Update user PARTSTAT and remove useless X_PM_TOKEN property
        userAttendee.participationStatus = participationStatus
        userAttendee.removeParameter(CustomICalPropertyParameter.X_PM_TOKEN)
        userAttendee.participationLevel = null
        userAttendee.rsvp = null
        userAttendee.commonName = userAttendee.extractEmail()

        val iCalendar = ICalendar()
        iCalendar.setProductId(generateProtonProdId())
        iCalendar.version = ICalVersion.V2_0
        iCalendar.setMethod(Method.REPLY)
        iCalendar.calendarScale = CalendarScale.gregorian()
        originalTimeZoneInfo?.let { iCalendar.timezoneInfo = originalTimeZoneInfo }

        // TODO: Provide complete VTIMEZONE in the ics. In the meantime, we remove it from the ICS
        iCalendar.timezoneInfo.timezones.clear()

        val event = VEvent()
        event.addAttendee(userAttendee)
        responseICalendar.events.first().organizer?.let { event.organizer = it }
        responseICalendar.events.first().uid?.let { event.uid = it }
        responseICalendar.events.first().dateStart?.let { event.dateStart = it }
        responseICalendar.events.first().dateEnd?.let {
            //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
            //  set to avoid NPE in the app. We remove it when sending the ICS.
            event.dateEnd =
                if (it.value == responseICalendar.events.first().dateStart?.value) null
                else it
        }
        responseICalendar.events.first().sequence?.let { event.sequence = it }
        responseICalendar.events.first().recurrenceId?.let { event.recurrenceId = it }
        responseICalendar.events.first().recurrenceRule?.let { event.recurrenceRule = it }
        responseICalendar.events.first().location?.let { if (!it.value.isNullOrEmpty()) event.location = it }
        responseICalendar.events.first().summary?.let { if (!it.value.isNullOrEmpty()) event.summary = it }
        event.setDateTimeStamp(dtStamp)

        if (isProtonProtonInvite && sharedEventId != null && sharedSessionKey != null) {
            // Add base64 encoded session key
            event.setExperimentalProperty(X_PM_SESSION_KEY, sharedSessionKey)
            // Add shared event ID
            event.setExperimentalProperty(X_PM_SHARED_EVENT_ID, sharedEventId)
            // Add X-PM-PROTON-REPLY and set it to true
            event.setExperimentalProperty(X_PM_PROTON_REPLY, ICalDataType.BOOLEAN, "TRUE")
        }

        iCalendar.addEvent(event)

        return iCalendar.printToString()
    }

    private fun getBaseIcs(
        newEvent: Event
    ): ICalendar {

        val iCalendar = newEvent.iCalendar.clone()

        //  DTEND value MUST be later in time than the value of the "DTSTART" property, but we need it to be
        //  set to avoid NPE in the app. We remove it when sending the ICS.
        iCalendar.events.first().sanitiseForExternal()

        if (iCalendar.productId == null) iCalendar.setProductId(generateProtonProdId())
        if (iCalendar.version == null) iCalendar.version = ICalVersion.V2_0

        if (iCalendar.calendarScale == null) iCalendar.calendarScale = CalendarScale.gregorian()

        // Replace common names with emails
        iCalendar.events.first().attendees.forEach {
            it.commonName = it.extractEmail()
        }

        // Alarms should be dropped
        iCalendar.events.first().alarms.clear()

        // The EXDATE must be filtered out
        iCalendar.events.first().exceptionDates.clear()

        return iCalendar
    }

    override fun getInviteIcs(
        newEvent: Event,
        sharedEventId: String,
        sharedSessionKey: String
    ): String {

        val inviteICalendar = getBaseIcs(newEvent)

        // METHOD:REPLY as we answer the REQUEST of the organizer
        inviteICalendar.setMethod(Method.REQUEST)

        // Add base64 encoded session key
        inviteICalendar.events.first().setExperimentalProperty(X_PM_SESSION_KEY, sharedSessionKey)
        // Add shared event ID
        inviteICalendar.events.first().setExperimentalProperty(X_PM_SHARED_EVENT_ID, sharedEventId)

        // TODO: Provide complete VTIMEZONE in the ics. In the meantime, we remove it from the ICS
        inviteICalendar.timezoneInfo.timezones.clear()

        return inviteICalendar.printToString()
    }

    override fun getCancelIcs(
        event: Event,
        sharedEventId: String
    ): String {

        val cancelICalendar = getBaseIcs(event)

        // METHOD:REPLY as we answer the REQUEST of the organizer
        cancelICalendar.setMethod(Method.CANCEL)

        // Add shared event ID (shared session key is not needed for cancellation)
        cancelICalendar.events.first().setExperimentalProperty(X_PM_SHARED_EVENT_ID, sharedEventId)

        // TODO: Provide complete VTIMEZONE in the ics. In the meantime, we remove it from the ICS
        cancelICalendar.timezoneInfo.timezones.clear()

        // Event status is unnecessary
        cancelICalendar.events.first().status = null

        // Refresh DTSTAMP
        cancelICalendar.events.first().setDateTimeStamp(Date.from(Instant.now()))

        return cancelICalendar.printToString()
    }

    override fun VAlarm.isTheSameAs(
        alarm: VAlarm
    ): Boolean {
        return this.action == alarm.action && this.trigger?.duration?.toMillis() == alarm.trigger?.duration?.toMillis()
    }

    override fun Notification.isTheSameAs(
        notification: Notification
    ): Boolean {
        return this.toVAlarm().isTheSameAs(notification.toVAlarm())
    }

    override fun List<VAlarm>.isTheSameAs(alarms: List<VAlarm>): Boolean {

        fun rightContainsLeft(left: List<VAlarm>, right: List<VAlarm>): Boolean {
            left.forEach { alarm ->
                if (right.find { it.isTheSameAs(alarm) } == null) return false
            }

            return true
        }

        return rightContainsLeft(this, alarms) && rightContainsLeft(alarms, this)
    }

}

data class CalendarSplit(
    val sharedPart: ICalendar,
    val sharedPartToEncrypt: ICalendar,
    val calendarPart: ICalendar?,
    val calendarPartToEncrypt: ICalendar?, // TODO all the other properties not mentioned in matrix should be here
    val personalPart: ICalendar?,
    val attendeesPart: ICalendar?
)


