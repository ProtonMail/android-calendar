package me.proton.android.calendar.test.shared.mocks

import biweekly.component.VAlarm
import biweekly.parameter.ParticipationStatus
import biweekly.parameter.Related
import biweekly.property.Attendee
import biweekly.property.ExceptionDates
import biweekly.property.Organizer
import biweekly.property.Trigger
import biweekly.util.Duration
import biweekly.util.ICalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.setDefaultTimeZone
import me.proton.android.calendar.data.api.AttendeesInfoResponse
import me.proton.android.calendar.data.api.EventResponse
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.NotificationMigration
import me.proton.android.calendar.test.shared.mocks.CalendarMocks.provideCalendar
import me.proton.core.util.kotlin.toInt
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object EventMocks {

    fun provideEvent(
        isRecurring: Boolean = false,
        isOrganizer: Boolean = false,
        isAttendee: Boolean = false,
        isProtonProtonInvite: Boolean? = null,
        isSingleEdit: Boolean = false,
        hasDisabledCalendar: Boolean = false,
        hasExDate: Boolean = false,
        hasHiddenCalendar: Boolean = false,
        hasDefaultAlarms: Boolean = true,
        participationStatus: ParticipationStatus = ParticipationStatus.DECLINED,
        notifications: NotificationMigration = NotificationMigration(false, null)
    ): Event {

        val ics = when {
            isRecurring -> recurringIcs
            isSingleEdit -> singleEditIcs
            else -> baseIcs
        }
        val iCalendar = ICalUtilsImpl.parseICalString(ics)!!

        // Event with attendees
        if (isOrganizer) {
            // Set default attendee
            val attendee = Attendee(
                attendeeName,
                attendeeEmail
            )
            attendee.participationStatus = ParticipationStatus.DECLINED
            iCalendar.events.first().addAttendee(attendee)

            // Set default user as the organizer
            val organizer = Organizer(
                userName,
                userEmail
            )
            iCalendar.events.first().organizer = organizer
        }
        if (isAttendee) {
            // Set default user as an attendee
            val attendee = Attendee(
                userName,
                userEmail
            )
            attendee.participationStatus = participationStatus
            iCalendar.events.first().addAttendee(attendee)

            // Set default organizer
            val organizer = Organizer(
                organizerName,
                organizerEmail
            )
            iCalendar.events.first().organizer = organizer
        }

        if (hasExDate) {
            // Ex date on fourth occurrence
            val exceptionDates = ExceptionDates()

            // Create a Calendar instance for the desired date and time in the default timezone
            val calendar = Calendar.getInstance(TimeZone.getTimeZone(defaultTimezone))
            calendar.set(2021, Calendar.SEPTEMBER, 17, 15, 30, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // Create a Date object from the Calendar
            val newUntilDate = calendar.time

            // Add to exceptionDates
            exceptionDates.values.add(ICalDate(newUntilDate, true))
            iCalendar.events.first().addExceptionDates(exceptionDates)
        }

        if (hasDefaultAlarms) {
            // One alarm 15 minutes before
            iCalendar.events.first().addAlarm(
                VAlarm.display(
                    Trigger(Duration.builder().prior(true).minutes(15).build(), Related.START),
                    null
                )
            )
        }

        iCalendar.setDefaultTimeZone(defaultTimezone)

        return Event.from(
            if (isSingleEdit) singleEditEventId else eventId,
            provideCalendar(hasDisabledCalendar, hasHiddenCalendar),
            iCalendar,
            0,
            null, // TODO
            isProtonProtonInvite = isProtonProtonInvite,
            currentUserAttendeeId = if (isAttendee) attendeeId else null,
            notifications = notifications
        )!!
    }

    fun provideEventEntity(
        isSingleEdit: Boolean = false,
        isProtonProtonInvite: Boolean = false,
        hasAttendees: Boolean = false
    ): EventEntity {
        return EventEntity(
            id = if (isSingleEdit) singleEditEventId else eventId,
            calendarId = calendarId,
            sharedEventId = sharedEventId,
            calendarKeyPacket = calendarKeyPacket,
            createTime = 0L,
            modifyTime = 0L,
            permissions = 1,
            addressKeyPacket = null,
            addressId = null,
            sharedKeyPacket = sharedKeyPacket,
            sharedEvents = emptyList(),
            calendarEvents = emptyList(),
            attendeesEvents = emptyList(), // TODO Mock attendeesEvents too ?
            attendees = if (hasAttendees) listOf(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nATTENDEE;CN=calendarsingle9@proton.dev;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-\\r\\n TOKEN=905eb4e54055cdb47d9edf7e8f6a778bca369a97:mailto:calendarsingle9@proto\\r\\n n.dev\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za8a+AQDA/zlaCVvaSnlRv6HBLyScDTgUhbUE1ArnLaY0G2ot9wD/XGPE\\r\\nB9Ou63paO4mHQJOzBw9LQe6k2HA24doWDD8cfQA=\\r\\n=/tFw\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
            else emptyList(),
            // TODO see if we need to fix this to return proper expected attendessInfo data
            attendeesInfo = if (hasAttendees) listOf(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nATTENDEE;CN=calendarsingle9@proton.dev;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-\\r\\n TOKEN=905eb4e54055cdb47d9edf7e8f6a778bca369a97:mailto:calendarsingle9@proto\\r\\n n.dev\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za8a+AQDA/zlaCVvaSnlRv6HBLyScDTgUhbUE1ArnLaY0G2ot9wD/XGPE\\r\\nB9Ou63paO4mHQJOzBw9LQe6k2HA24doWDD8cfQA=\\r\\n=/tFw\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
            else emptyList(),
            isProtonProtonInvite = isProtonProtonInvite.toInt()
        )
    }

    fun provideEventResponse(
        isSingleEdit: Boolean = false,
        isProtonProtonInvite: Boolean = false,
        hasAttendees: Boolean = false
    ): EventResponse {
        return EventResponse(
            id = if (isSingleEdit) singleEditEventId else eventId,
            calendarId = calendarId,
            sharedEventId = sharedEventId,
            calendarKeyPacket = calendarKeyPacket,
            createTime = 0L,
            modifyTime = 0L,
            permissions = 1,
            addressKeyPacket = null,
            addressId = null,
            sharedKeyPacket = sharedKeyPacket,
            sharedEvents = emptyList(),
            calendarEvents = emptyList(),
            attendeesEvents = emptyList(), // TODO Mock attendeesEvents too ?
            attendees = if (hasAttendees) listOf(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nATTENDEE;CN=calendarsingle9@proton.dev;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-\\r\\n TOKEN=905eb4e54055cdb47d9edf7e8f6a778bca369a97:mailto:calendarsingle9@proto\\r\\n n.dev\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za8a+AQDA/zlaCVvaSnlRv6HBLyScDTgUhbUE1ArnLaY0G2ot9wD/XGPE\\r\\nB9Ou63paO4mHQJOzBw9LQe6k2HA24doWDD8cfQA=\\r\\n=/tFw\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
            else emptyList(),
            // TODO see if we need to fix this to return proper expected attendess Info
            attendeesInfo = AttendeesInfoResponse(
                attendees = if (hasAttendees) listOf(Json.decodeFromString<JsonElement>("{\"Type\":2,\"Data\":\"BEGIN:VCALENDAR\\r\\nVERSION:2.0\\r\\nBEGIN:VEVENT\\r\\nUID:4vi99f4jksbjr1472nir63suvk@google.com\\r\\nATTENDEE;CN=calendarsingle9@proton.dev;ROLE=REQ-PARTICIPANT;RSVP=TRUE;X-PM-\\r\\n TOKEN=905eb4e54055cdb47d9edf7e8f6a778bca369a97:mailto:calendarsingle9@proto\\r\\n n.dev\\r\\nEND:VEVENT\\r\\nEND:VCALENDAR\",\"Signature\":\"-----BEGIN PGP SIGNATURE-----\\r\\nVersion: OpenPGP.js v4.10.8\\r\\nComment: https://openpgpjs.org\\r\\n\\r\\nwnUEARYKAAYFAl+PFFEAIQkQvfbFISx9GWsWIQSFmVz3NIh7rYVWIdi99sUh\\r\\nLH0Za8a+AQDA/zlaCVvaSnlRv6HBLyScDTgUhbUE1ArnLaY0G2ot9wD/XGPE\\r\\nB9Ou63paO4mHQJOzBw9LQe6k2HA24doWDD8cfQA=\\r\\n=/tFw\\r\\n-----END PGP SIGNATURE-----\\r\\n\",\"Author\":\"calendarSingle9@proton.dev\"}"))
                else emptyList(),
                moreAttendees = 0
            ),
            isProtonProtonInvite = isProtonProtonInvite.toInt(),
            startTime = TimeUnit.SECONDS.toSeconds(System.currentTimeMillis()),
            startTimeZone = "GMT",
            endTime = TimeUnit.SECONDS.toSeconds(System.currentTimeMillis().plus(7200000L)),
            endTimeZone = "GMT",
            fullDay = 0,
            uid = eventUid,
            recurrenceID = null,
            exDates = emptyList(),
            rRule = null,
            isOrganizer = 0,
            isPersonalSingleEdit = false
        )
    }

}
