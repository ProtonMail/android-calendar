package me.proton.android.calendar.domain.model

import biweekly.ICalendar
import biweekly.component.VAlarm
import biweekly.component.VEvent
import biweekly.parameter.ParticipationStatus
import biweekly.property.Action
import biweekly.property.Status
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CustomICalPropertyParameter
import me.proton.android.calendar.common.CustomICalPropertyParameter.CONFERENCE_DESCRIPTION_HEADER
import me.proton.android.calendar.common.CustomICalPropertyParameter.CONFERENCE_DESCRIPTION_REGEX_STRING
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_HOST
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_HOST_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PASSWORD
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PASSWORD_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PROVIDER
import me.proton.android.calendar.common.CustomICalPropertyParameter.PARAMETER_CONFERENCE_PROVIDER_READONLY
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_CONFERENCE_ID
import me.proton.android.calendar.common.CustomICalPropertyParameter.X_PM_CONFERENCE_URL
import me.proton.android.calendar.common.OFFLINE_EVENT_ID_PREFIX
import me.proton.android.calendar.common.PROTON_OLD_UID
import me.proton.android.calendar.common.PROTON_UID
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatShort
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrence
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateOccurrencesUntil
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.getStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.common.utils.ICalUtilsImpl.sanitise
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEnd
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setEndTimeZone
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStart
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setStartTimeZone
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.presentation.calendar.adapter.TimelineEventAdapter
import me.proton.core.util.kotlin.takeIfNotBlank
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// TODO remove nullability from signature verification and decryption statuses
data class Event private constructor(
    override val id: String, // ID from API and local database
    val calendar: Calendar,
    val iCalendar: ICalendar,
    val modifyTime: Long,
    val verificationStatus: SignatureVerification? = null,
    val decryptionStatus: DecryptionStatus? = null,
    val currentUserAttendeeId: String? = null,
    val sharedEventId: String? = null,
    val isProtonProtonInvite: Boolean? = null,
    var notifications: NotificationMigration = NotificationMigration(false, null),
    val attendeeComments: Map<String, Pair<SignatureVerification, String?>> = emptyMap(),
    val color: String? = null
) : BaseModel() {

    companion object {

        fun from(
            id: String, // ID from API and local database
            calendar: Calendar,
            iCalendar: ICalendar,
            modifyTime: Long,
            verificationStatus: SignatureVerification? = null,
            decryptionStatus: DecryptionStatus? = null,
            currentUserAttendeeId: String? = null,
            sharedEventId: String? = null,
            isProtonProtonInvite: Boolean? = null,
            notifications: NotificationMigration? = null,
            attendeeComments: Map<String, Pair<SignatureVerification, String?>> = emptyMap(),
            color: String? = null
        ): Event? {

            val vEvent = iCalendar.events.firstOrNull()

            return if (vEvent?.sanitise() == true) {
                Event(
                    id,
                    calendar,
                    iCalendar,
                    modifyTime,
                    verificationStatus,
                    decryptionStatus,
                    currentUserAttendeeId,
                    sharedEventId,
                    isProtonProtonInvite,
                    notifications ?: NotificationMigration(false, null),
                    attendeeComments,
                    color
                )
            } else null

        }

        /**
         * Makes sure we have deep copy of [ICalendar] object inside [Event].
         * Copy event with specified id / calendar / iCalendar / notifications values
         * and Timezone Assignments
         */
        fun from(
            event: Event,
            id: String? = null,
            calendar: Calendar? = null,
            iCalendar: ICalendar? = null,
            notifications: NotificationMigration? = null,
            color: String? = null
        ): Event {
            val defaultTimezoneId = event.iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id
            val startTimezoneId = event.iCalendar.timezoneInfo?.getTimezone(event.iCalEvent.dateStart)?.timeZone?.id
            val endTimezoneId = event.iCalendar.timezoneInfo?.getTimezone(event.iCalEvent.dateStart)?.timeZone?.id
            return event.copy(
                id = id ?: event.id,
                calendar = calendar ?: event.calendar,
                iCalendar = (iCalendar ?: ICalendar(event.iCalendar)).apply {

                    // manually copy timezone assignments
                    val originalCalendar = event.iCalendar
                    val newICalendar = this

                    originalCalendar.timezoneInfo.getTimezone(originalCalendar.events.first().recurrenceId)?.let {
                        newICalendar.timezoneInfo.setTimezone(newICalendar.events.first().recurrenceId, it)
                    }

                    newICalendar.events.first().exceptionDates?.clear()
                    originalCalendar.events.first().exceptionDates.forEachIndexed { index, exceptionDate ->
                        newICalendar.events.first().addExceptionDates(exceptionDate)

                        // copy timezone assignments for EXDATEs
                        val timezoneAssignment = originalCalendar.timezoneInfo.getTimezone(exceptionDate)
                        if (timezoneAssignment != null) {
                            newICalendar.timezoneInfo.setTimezone(newICalendar.events.first().exceptionDates[index], timezoneAssignment)
                        }
                    }

                    setStartTimeZone(startTimezoneId)
                    setEndTimeZone(endTimezoneId)
                    setDefaultTimeZone(defaultTimezoneId)
                },
                notifications = notifications ?: event.notifications,
                color = color ?: event.color
            )
        }

        /**
         * Wraps the [iCalendar] in [Event] using dummy values for everything else.
         */
        fun dummyFrom(
            iCalendar: ICalendar
        ): Event? {

            val vEvent = iCalendar.events.firstOrNull()

            return if (vEvent?.sanitise() == true) {
                Event(
                    "",
                    Calendar("", "", "", "", "", "", 0, "", "", 1, true, 0, 0, 0, emptyList(), emptyList()),
                    iCalendar,
                    0
                )
            } else null

        }

        /**
         * Returns copy of an [Event] with overwritten start & end datetime with [Occurrence] values in a given [timeZoneId].
         */
        fun withOccurrence(event: Event, occurrenceNumber: Int, timeZoneId: String): Event? {

            val occurrence = event.generateOccurrence(occurrenceNumber, timeZoneId)

            return if (occurrence != null) Event.withOccurrence(event, occurrence) else null
        }

        /**
         * Returns copy of an [Event] with overwritten start & end datetime with [Occurrence] values.
         */
        fun withOccurrence(event: Event, occurrence: Occurrence): Event {
            return Event.from(event).apply {
                if (this.isAllDay()) {
                    this.iCalEvent.setStart(occurrence.startDateTime.toLocalDate())
                    this.iCalEvent.setEnd(occurrence.endDateTime.toLocalDate())
                } else {
                    this.iCalEvent.setStart(occurrence.startDateTime.toLocalDate(), occurrence.startDateTime.toLocalTime(), occurrence.startDateTime.zone.id)
                    this.iCalEvent.setEnd(occurrence.endDateTime.toLocalDate(), occurrence.endDateTime.toLocalTime(), occurrence.endDateTime.zone.id)
                    this.iCalendar.setStartTimeZone(occurrence.startDateTime.zone.id)
                    this.iCalendar.setEndTimeZone(occurrence.endDateTime.zone.id)
                }
                this.occurrence = occurrence
            }
        }


    }

    var occurrence: Occurrence? = null

    val iCalEvent: VEvent get() = iCalendar.events.first()

    val uid: String get() = iCalEvent.uid.value
    val summary: String? get() = iCalEvent.summary?.value
    val location: String? get() = iCalEvent.location?.value
    val description: String? get() = iCalEvent.description?.value

    val meetType: MeetIntegrationType? by lazy {
        iCalEvent.getExperimentalProperty(X_PM_CONFERENCE_ID)?.let {
            val providerValue = (it.parameters.get(PARAMETER_CONFERENCE_PROVIDER)?.takeIfNotEmpty()
                ?: it.parameters.get(PARAMETER_CONFERENCE_PROVIDER_READONLY)?.takeIfNotEmpty())
                ?.firstOrNull()
            when (providerValue) {
                "1" -> MeetIntegrationType.Zoom
                "2" -> MeetIntegrationType.ProtonMeet
                else -> null
            }
        }
    }

    val meetConferenceId = iCalEvent.getExperimentalProperty(X_PM_CONFERENCE_ID)?.value
    val meetUrl: String? get() = iCalEvent.getExperimentalProperty(X_PM_CONFERENCE_URL)?.value
    val meetConferencePassword: String? get() = iCalEvent.getExperimentalProperty(X_PM_CONFERENCE_URL)?.let {
        it.getParameter(PARAMETER_CONFERENCE_PASSWORD)?.takeIfNotBlank() ?: it.getParameter(PARAMETER_CONFERENCE_PASSWORD_READONLY)?.takeIfNotBlank()
    }
    val meetMeetingHost: String? get() = iCalEvent.getExperimentalProperty(X_PM_CONFERENCE_URL)?.let {
        it.getParameter(PARAMETER_CONFERENCE_HOST)?.takeIfNotBlank() ?: it.getParameter(PARAMETER_CONFERENCE_HOST_READONLY)?.takeIfNotBlank()
    }

    val status: Status? get() = iCalEvent.status

    /**
     * Use this getter to handle Alarms instead of taking them directly from ICS. This contains custom logic
     * for supporting default Calendar Alarms that can't be represented easily in ICS.
     */
    val alarms: List<VAlarm> get() {
        return if (notifications.isMigrated) {
            if (notifications.notifications == null) { // take defaults from Calendar
                if (isAllDay()) {
                    calendar.defaultFullDayNotifications.map { it.toVAlarm() }
                } else {
                    calendar.defaultPartDayNotifications.map { it.toVAlarm() }
                }
            } else { // take migrated Alarms
                notifications.notifications?.map { it.toVAlarm() } ?: emptyList()
            }
        } else { // take Alarms from PersonalPart baked into ICS
            iCalEvent.alarms
        }
    }

    fun getDisplayColor(isFreeUser: Boolean) = if (isFreeUser) calendar.color else color ?: calendar.color

    fun clearAlarms() {
        iCalEvent.alarms?.clear()
        notifications = notifications.copy(notifications = emptyList())
    }

    fun setDefaultAlarms() {
        iCalEvent.alarms?.clear()

        // as long as we need PersonalPart generated from ICS, we keep injecting VAlarms
        if (isAllDay()) {
            calendar.defaultFullDayNotifications
        } else {
            calendar.defaultPartDayNotifications
        }.forEach {
            iCalEvent.addAlarm(it.toVAlarm())
        }

        notifications = notifications.copy(notifications = null)
    }

    fun addAlarms(alarmsToAdd: List<VAlarm>) {
        val currentNotifications =
            if (notifications.isMigrated) {
                notifications.notifications ?: run {
                    // If notifications is null, event uses default alarms
                    if (this.isAllDay()) calendar.defaultFullDayNotifications
                    else calendar.defaultPartDayNotifications
                }
            } else {
                iCalEvent.alarms.mapNotNull { Notification.fromVAlarm(it) }
            }

        iCalEvent.alarms.addAll(alarmsToAdd) // as long as we need PersonalPart generated from ICS, we keep injecting VAlarms

        notifications = notifications.copy(notifications = currentNotifications.plus( alarmsToAdd.mapNotNull { Notification.fromVAlarm(it) }))
    }

    fun removeAlarm(alarm: VAlarm) {
        val notificationsWithAlarmRemoved = Notification.fromVAlarm(alarm)?.let { notificationToDelete ->
            val notifications =
                if (notifications.isMigrated) {
                    notifications.notifications ?: run {
                        // If notifications is null, event uses default alarms, so we need to get the list to remove notificationToDelete
                        if (isAllDay()) calendar.defaultFullDayNotifications
                        else calendar.defaultPartDayNotifications
                    }
                } else {
                    iCalEvent.alarms.mapNotNull { Notification.fromVAlarm(it) }
                }
            notifications.indexOfFirst { it.isTheSameAs(notificationToDelete) }.takeIf { it != -1 }?.let {
                notifications.toMutableList().apply { removeAt(it) }
            }
        }

        iCalEvent.alarms.indexOfFirst { it.isTheSameAs(alarm) }.takeIf { it != -1 }?.let {
            iCalEvent.alarms.removeAt(it)
        }

        notifications = notifications.copy(notifications = notificationsWithAlarmRemoved)
    }

    fun containsMeetDescription(): Boolean {
        return this.iCalEvent.description?.value?.contains(
            Regex(CONFERENCE_DESCRIPTION_REGEX_STRING)
        ) == true
    }

    fun addMeetDescription(resourceProvider: ResourceProvider) {
        val prompt = when (meetType ?: return) {
            MeetIntegrationType.ProtonMeet -> "Join Proton Meeting"
            MeetIntegrationType.Zoom -> resourceProvider.provideString(R.string.join_zoom_meet_ical_description)
        }
        val header = "\n$CONFERENCE_DESCRIPTION_HEADER\n$prompt: $meetUrl (ID: $meetConferenceId${meetConferencePassword?.let { ", passcode: $meetConferencePassword" }})\n\nMeeting host: $meetMeetingHost\n$CONFERENCE_DESCRIPTION_HEADER"

        /*
            ~-~-~-~-~-~-~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~-~-~-~-~-~-~
            Join Zoom Meeting: https://zoom.us/j/XXX?pwd=XXX (ID: XXX, passcode: XXX)

            Meeting host: john.doe@proton.ch
            ~-~-~-~-~-~-~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~-~-~-~-~-~-~
             */
        this.iCalEvent.setDescription(description.orEmpty() + header)
    }

    fun removeConference() {
        this.iCalEvent.removeExperimentalProperties(X_PM_CONFERENCE_ID)
        this.iCalEvent.removeExperimentalProperties(X_PM_CONFERENCE_URL)
        this.removeConferenceDescription()
    }

    fun removeConferenceDescription() {
        if (this.containsMeetDescription() && !this.meetUrl.isNullOrBlank()) {
            this.iCalEvent.description.value = this.iCalEvent.description.value.replace(
                Regex(CONFERENCE_DESCRIPTION_REGEX_STRING),
                ""
            ).trim()
        }
    }

    val hasProtonUid: Boolean get() = uid.endsWith(PROTON_UID) || uid.startsWith(PROTON_OLD_UID)

    val defaultTimeZone: String? get() = iCalendar.timezoneInfo?.defaultTimezone?.timeZone?.id

    // TODO Change this once we allow editing event with attendees
    val isAnInvitation: Boolean get() = !this.iCalEvent.attendees.isNullOrEmpty()

    val hasProtonProtonProperties: Boolean get() =
        iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SHARED_EVENT_ID)?.value?.isNotBlank() == true &&
                iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_SESSION_KEY)?.value?.isNotBlank() == true

    val isProtonProtonReply = iCalEvent.getExperimentalProperty(CustomICalPropertyParameter.X_PM_PROTON_REPLY)?.value == "TRUE"

    val hasEmailNotifications: Boolean get() = this.alarms.any { it.action == Action.email() }

    fun getStart(timeZoneId: String): ZonedDateTime {
        return iCalEvent.getStart(timeZoneId)!!
    }

    fun getEnd(timeZoneId: String): ZonedDateTime {
        return iCalEvent.getEnd(timeZoneId)!!
    }

    fun getOccurrenceStart(timeZoneId: String): ZonedDateTime {
        return if (this.isSingleEdit()) {
            getStart(timeZoneId)
        } else if (this.isAllDay()) {
            occurrence?.startDateTime?.withZoneSameLocal(ZoneId.of(timeZoneId)) ?: getStart(timeZoneId)
        } else {
            occurrence?.startDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: getStart(timeZoneId)
        }
    }

    fun getOccurrenceEnd(timeZoneId: String): ZonedDateTime {
        return if (this.isSingleEdit()) {
            getEnd(timeZoneId)
        } else if (this.isAllDay()) {
            occurrence?.endDateTime?.withZoneSameLocal(ZoneId.of(timeZoneId)) ?: getEnd(timeZoneId)
        } else {
            occurrence?.endDateTime?.withZoneSameInstant(ZoneId.of(timeZoneId)) ?: getEnd(timeZoneId)
        }
    }

    fun getRecurrenceId(timeZoneId: String): ZonedDateTime? {
        return iCalEvent.recurrenceId?.value?.toInstant()?.atZone(ZoneId.of(timeZoneId))
    }

    fun isInThePast(timeZoneId: String): Boolean {
        return this.getOccurrenceEnd(timeZoneId).isBefore(ZonedDateTime.now(ZoneId.of(timeZoneId))) == true
    }

    fun isCancelled(): Boolean {
        return (this.status != null) && (this.status as Status).isCancelled
    }



    fun isSyncedWithApi(): Boolean = !id.startsWith(OFFLINE_EVENT_ID_PREFIX, ignoreCase = false)

    fun isAllDay(): Boolean = iCalEvent.dateStart?.value?.hasTime() == false && (if (iCalEvent.dateEnd != null) iCalEvent.dateEnd?.value?.hasTime() == false else true)

    /**
     * Attention: part-time Event ending at 00:00 is not considered to span the end-day.
     */
    fun spansSingleDay(actualEndDate: Boolean = false, timeZoneId: String? = null): Boolean {

        val dateTimeStart = this.getStart(timeZoneId ?: ZoneId.systemDefault().id)
        val dateStart = dateTimeStart.toLocalDate()
        val dateTimeEnd = this.getEnd(timeZoneId ?: ZoneId.systemDefault().id)
        val dateEnd = dateTimeEnd.toLocalDate()

        return if (isAllDay()) {
            dateEnd == null || dateStart == dateEnd.minusDays(if (actualEndDate) 0 else 1)
        } else {

            if (dateTimeStart == dateTimeEnd) {
                true
            } else if (dateTimeEnd.toLocalTime() == LocalTime.MIDNIGHT) {
                // for part-day Event, if it ends on Midnight, we don't count it spanning that last day
                dateStart == dateEnd.minusDays(1)
            } else {
                dateStart == dateEnd
            }
        }
    }

    /**
     * Occurrence should always be expressed in timezone we format or display the calendar with.
     */
    data class Occurrence(val startDateTime: ZonedDateTime, val endDateTime: ZonedDateTime, val occurrenceNumber: Int)



    fun isRecurring(): Boolean = this.iCalEvent.recurrenceRule != null

    fun isFiniteRecurring(): Boolean = when {
        this.iCalEvent.recurrenceRule?.value?.count != null -> true
        this.iCalEvent.recurrenceRule?.value?.until != null -> true
        else -> false
    }

    fun isSingleEdit(): Boolean = this.iCalEvent.recurrenceId != null

    fun isSingleOccurrenceRecurring(timeZoneId: String): Boolean =
        isRecurring() && (iCalEvent.recurrenceRule.value.count == 1 ||
                (iCalEvent.recurrenceRule.value.count != null && iCalEvent.exceptionDates?.distinct()?.size == iCalEvent.recurrenceRule.value.count - 1) ||
                isRecurringUntilSameDay(timeZoneId) ||
                isRecurringUntilBeforeNextOccurrence(timeZoneId))

    fun isRecurringUntilSameDay(timeZoneId: String): Boolean {
        if (iCalEvent.recurrenceRule.value.until == null) return false
        val untilZonedDateTime = iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        val dateStart = iCalEvent.dateStart.value.toZonedDateTime(timeZoneId)
        return ChronoUnit.DAYS.between(dateStart, untilZonedDateTime).toInt() <= 0
    }

    fun isRecurringUntilBeforeNextOccurrence(timeZoneId: String): Boolean {
        if (iCalEvent.recurrenceRule.value.until == null) return false
        val untilZonedDateTime = iCalEvent.recurrenceRule.value.until.toZonedDateTime(timeZoneId)
        val occurrenceCount = this.generateOccurrencesUntil(untilZonedDateTime.toLocalDate(), timeZoneId)?.size
        return occurrenceCount == null || occurrenceCount <= 1 || occurrenceCount - 1 == iCalEvent.exceptionDates?.distinct()?.size
    }

    fun isEventFirstOccurrence(originalEvent: Event, timeZoneId: String): Boolean {

        val startDate = this.getStart(timeZoneId).toLocalDate()
        val occurrences = originalEvent.generateOccurrencesUntil(startDate, timeZoneId)

        val exZonedDateTimes =
            originalEvent.iCalEvent.exceptionDates.flatMap { exDates ->
                exDates.values.map { exDate ->
                    exDate.toZonedDateTime(timeZoneId)
                }
            }

        return if (exZonedDateTimes.isNullOrEmpty()) {
            false
        } else {
            occurrences?.filterNot {
                it.startDateTime in exZonedDateTimes
            }?.size == 1
        }
    }

    fun isCustomRecurring(): Boolean {

        // no Recurrence Rule
        if (!this.isRecurring()) return false

        // look for any of the supported properties
        if (this.iCalEvent.recurrenceRule.value.interval != null ||
            this.iCalEvent.recurrenceRule.value.count != null ||
            this.iCalEvent.recurrenceRule.value.until != null ||
            this.iCalEvent.recurrenceRule.value.byDay?.size ?: 0 > 0
        ) return true

        // by default we return false which means we will ignore non-supported combinations
        return false

    }

    fun isUserOrganizer(canonicalUserEmails: List<String>?): Boolean {
        return if (this.isAnInvitation) {
            val organizerEmail = this.iCalEvent.organizer?.extractEmail()
            organizerEmail != null && canonicalUserEmails?.contains(ProtonUtilsImpl.canonicalizeProtonEmail(organizerEmail, forceCanonicalization = true)) == true
        } else false
    }

    fun isUserAttendee(canonicalUserEmails: List<String>?): Boolean {
        return if (this.isAnInvitation) {
            val attendeeEmails = this.iCalEvent.attendees?.mapNotNull { it.extractEmail() }
            attendeeEmails != null && attendeeEmails.find { canonicalUserEmails?.contains(ProtonUtilsImpl.canonicalizeProtonEmail(it, forceCanonicalization = true)) == true } != null
        } else false
    }

    // this event might be a single edit so it's technically a separate event in the database,
    //  but it's still considered as a part of a chain of events
    fun isPartOfChain(): Boolean = this.isRecurring() || this.isSingleEdit()

    sealed class EventPart {

        abstract val type: Int // 0: cleartext, 1: encrypted, 2: signed, 3: encrypted & signed
        abstract val data: String
        abstract val signature: String?
        abstract val author: String

        val isEncrypted: Boolean get() = type and 1 > 0
        val isSigned: Boolean get() = type and 2 > 0

        @Serializable
        data class Shared(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

        @Serializable
        data class Calendar(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

        @Serializable
        data class Personal(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String,
            @SerialName("MemberID")
            val memberId: String
        ): EventPart()

        @Serializable
        data class Attendee(
            @SerialName("Type")
            override val type: Int,
            @SerialName("Data")
            override val data: String,
            @SerialName("Signature")
            override val signature: String?,
            @SerialName("Author")
            override val author: String
        ): EventPart()

    }

    @Serializable
    data class AttendeeStatusEvent(
        @SerialName("ID")
        val id: String? = null,
        @SerialName("Token")
        val token: String,
        @SerialName("Status")
        val status: Int,
        @SerialName("UpdateTime")
        val updateTime: Int? = null,
        @SerialName("Comment")
        val comment: AttendeeStatusEventComment? = null
    ) {
        val participationStatus: ParticipationStatus get() = when (status) {
            1 -> ParticipationStatus.TENTATIVE
            2 -> ParticipationStatus.DECLINED
            3 -> ParticipationStatus.ACCEPTED
            else -> ParticipationStatus.NEEDS_ACTION
        }
    }

    @Serializable
    data class AttendeeStatusEventComment(
        @SerialName("Message")
        val message: String,
        @SerialName("Type")
        val type: Int
    )

    /**
     * Used for sending SharedSessionKey encrypted with Attendee's AddressKey
     */
    @Serializable
    data class AddedAttendee(
        @SerialName("Email")
        val email: String,
        @SerialName("AddressKeyPacket")
        val AddressKeyPacket: String
    )

    enum class SignatureVerification {
        SUCCESS,
        FAILURE,
        // there are no verification keys so we don't actually verify
        SIGNED_BUT_NO_KEYS,
        // couldn't get the verification keys so it's an error
        SIGNED_BUT_CANT_GET_KEYS,
        NOT_SIGNED
    }

    sealed interface DecryptionStatus {
        data object Success : DecryptionStatus
        sealed interface Failure : DecryptionStatus {
            data object NoAddressKey : Failure
            data object Generic : Failure
        }
    }

    /**
     * Converts to helper object used in Timeline view.
     */
    fun toTimelineEvent(
        resourceProvider: ResourceProvider,
        happensOn: LocalDate,
        timeZoneId: String,
        showDateColumn: Boolean,
        highlightDateColumn: Boolean,
        showBottomSpacing: Boolean,
        userEmails: List<String>,
        is24Hour: Boolean,
        isFreeUser: Boolean,
        searchTerm: String
    ): TimelineEventAdapter.TimelineEvent {

        val fullDayCounter = this.calculateFullDayCounter(happensOn, timeZoneId)

        val fullDayCounterString = if (fullDayCounter.second > 1) {
            this.formatFullDayCounter(happensOn, timeZoneId)
        } else null

        val dateText = if (this.isAllDay()) {
            resourceProvider.provideString(R.string.event_all_day)
        } else {
            if (fullDayCounter.second > 1) { // multi-day part-day
                when (fullDayCounter.first) {
                    1 -> { // first day
                        resourceProvider.provideString(
                            R.string.calendar_widget_part_day_event_starts_at,
                            getOccurrenceStart(timeZoneId).formatTime(timeZoneId, is24Hour)
                        )
                    }
                    fullDayCounter.second -> { // last day
                        resourceProvider.provideString(
                            R.string.calendar_widget_part_day_event_ends_at,
                            getOccurrenceEnd(timeZoneId).formatTime(timeZoneId, is24Hour)
                        )
                    }
                    else -> { // day in the middle
                        resourceProvider.provideString(R.string.event_all_day)
                    }
                }
            } else { // single-day part-day
                "${
                    (getOccurrenceStart(timeZoneId)).formatTime(
                        timeZoneId,
                        is24Hour
                    )
                } ‐ ${(getOccurrenceEnd(timeZoneId)).formatTime(timeZoneId, is24Hour)}"
            }
        }

        val participationStatus = this.getParticipationStatus(userEmails)

        val locationText = this.location?.takeIfNotBlank()

        return TimelineEventAdapter.TimelineEvent(
            id = this.id,
            summary = this.summary?.takeIfNotBlank() ?: resourceProvider.provideString(R.string.default_event_summary),
            dateContent = "${happensOn.dayOfWeek.formatShort()}, ${dateText}${if (locationText != null) " • " else ""}",
            location = locationText ?: "",
            happensOn = happensOn,
            showDateColumn = showDateColumn,
            highlightDateColumn = highlightDateColumn,
            showBottomSpacing = showBottomSpacing,
            fullDayCounter = fullDayCounterString,
            occurrenceNumber = this.occurrence?.occurrenceNumber ?: 0,
            color = this.getDisplayColor(isFreeUser),
            isCancelledOrDeclined = this.decryptionStatus == DecryptionStatus.Success && (this.isCancelled() || participationStatus == ParticipationStatus.DECLINED),
            needsAction = !this.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
            failedToDecrypt = this.decryptionStatus is DecryptionStatus.Failure,
            searchTerm = searchTerm
        )
    }

    fun toUiEvent(userEmails: List<String>, timeZoneId: String, isFreeUser: Boolean): UiEvent = UiEvent(
        id,
        calendar.id,
        uid,
        summary,
        location,
        description,
        this.getOccurrenceStart(timeZoneId),
        this.getOccurrenceEnd(timeZoneId),
        isAllDay(),
        occurrence?.occurrenceNumber ?: 0,
        this.getDisplayColor(isFreeUser),
        decryptionStatus ?: DecryptionStatus.Failure.Generic, // TODO when can this be null? only in SkeletonEvents?
        getParticipationStatus(userEmails),
        this.status ?: Status.confirmed()
    )
}




