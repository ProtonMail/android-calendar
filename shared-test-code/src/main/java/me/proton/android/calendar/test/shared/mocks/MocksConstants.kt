package me.proton.android.calendar.test.shared.mocks

import me.proton.android.calendar.domain.model.PackageType
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.AddressId
import me.proton.core.user.domain.entity.AddressType
import me.proton.core.user.domain.entity.Delinquent
import me.proton.core.user.domain.entity.Role
import me.proton.core.user.domain.entity.Type

/**
 * Global mocks
 */

val userId = UserId("userId")

const val defaultTimezone = "Europe/Paris"
const val defaultEventDuration = 30

/**
 * Calendar mocks
 */

const val calendarId = "calendarId"
const val calendarName = "calendarName"
const val calendarDescription = "calendarDescription"
const val calendarColor = "#657EE4"
const val calendarDisplay = 1
const val calendarFlags = 1
const val calendarPermissions = 127
const val calendarType = 0

const val calendarSettingsId = "calendarSettingsId"

const val weekLength = 7
const val displayWeekNumber = 1
const val autoDetectPrimaryTimezone = 1
const val viewPreference = 0

/**
 * Event mocks
 */

const val eventId = "eventId"
const val singleEditEventId = "singleEditEventId"

const val eventUid = "eventUid@proton.me"

const val attendeeEmail = "attendee@pm.me"
const val attendeeName = "attendeeName"
const val secondAttendeeEmail = "secondAttendee@pm.me"
const val secondAttendeeName = "secondAttendeeName"

const val organizerEmail = "organizer@pm.me"
const val organizerName = "organizerName"

const val sharedEventId = "sharedEventId"
const val calendarKeyPacket = "calendarKeyPacket"
const val sharedKeyPacket = "sharedKeyPacket"

const val attendeeId = "attendeeId"

/**
 * User mocks
 */

const val weekStart = 0
const val dateFormat = 0
const val timeFormat = 0

const val userEmail = "userEmail@pm.me"
const val userName = "userName"
const val userDisplayName = "displayName"

val addressId = AddressId("addressId")

const val memberId = "memberId"

const val canSend = true
const val canReceive = true
const val enabled = true
const val order = 1

val addressType = AddressType.Original

const val currency = "EUR"
const val credit = 50
const val createdAtUtc = 1692883228263L
const val usedSpace = 0L
const val maxSpace = 3096L
const val maxUpload = 3096L
const val private = true
const val services = 1
const val subscribed = 1

val userType = Type.Proton
val role = Role.NoOrganization
val delinquent = Delinquent.None

const val encrypt: Boolean = true
const val sign: Boolean = true
val pgpScheme: PackageType = PackageType.ProtonMail
const val mimeType: String = "" // TODO
const val publicKey: String = "" // TODO

/**
 * Event Ics mocks
 */

// Single part day event
val baseIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
    BEGIN:VEVENT
    DTSTAMP:20210914T132502Z
    UID:eventUid@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;TZID=Europe/Paris:20210914T153000
    DTEND;TZID=Europe/Paris:20210914T160000
    SUMMARY:Single event
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

// Daily recurring 10 times part day event
val recurringIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
    BEGIN:VEVENT
    DTSTAMP:20210914T132502Z
    UID:eventUid@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    RRULE:FREQ=DAILY;COUNT=10
    DTSTART;TZID=Europe/Paris:20210914T153000
    DTEND;TZID=Europe/Paris:20210914T160000
    SUMMARY:Recurring event
    END:VEVENT
    END:VCALENDAR
""".trimIndent()

// All day single edit replacing 2nd occurrence
val singleEditIcs = """
    BEGIN:VCALENDAR
    VERSION:2.0
    PRODID:-//Proton Technologies//AndroidCalendar 0.25.1//EN
    BEGIN:VEVENT
    DTSTAMP:20210914T132502Z
    UID:eventUid@proton.me
    STATUS:CONFIRMED
    SEQUENCE:0
    DTSTART;VALUE=DATE:20210915
    DTEND;VALUE=DATE:20210916
    RECURRENCE-ID;TZID=Europe/Paris:20210915T153000
    SUMMARY:Single edit
    END:VEVENT
    END:VCALENDAR
""".trimIndent()
