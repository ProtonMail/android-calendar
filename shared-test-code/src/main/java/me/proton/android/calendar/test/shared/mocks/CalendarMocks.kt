package me.proton.android.calendar.test.shared.mocks

import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.Duration
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.entity.*
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Notification
import me.proton.core.util.kotlin.toBoolean

object CalendarMocks {

    fun provideCalendarEntity(id: String = calendarId): CalendarEntity {
        return CalendarEntity(
            id = id,
            type = calendarType,
            owner = Json.decodeFromString("{\"Email\": \"$userEmail\"}"),
            fkUserId = userId.id
        )
    }

    fun provideCalendarSettingsEntity(id: String = calendarId): CalendarSettingsEntity {
        return CalendarSettingsEntity(
            id = calendarSettingsId,
            calendarId = id,
            defaultEventDuration = defaultEventDuration,
            // One alarm 15 minutes before
            defaultPartDayNotifications = listOf(Json.decodeFromString("{\"Type\":1,\"Trigger\":\"-PT15M\"}")),
            // One day before at 9am
            defaultFullDayNotifications = listOf(Json.decodeFromString("{\"Type\":1,\"Trigger\":\"-PT15H\"}"))
        )
    }

    fun provideCalendarUserSettingsEntity(): CalendarUserSettingsEntity {
        return CalendarUserSettingsEntity(
            fkUserId = userId.id,
            weekLength = weekLength,
            displayWeekNumber = displayWeekNumber,
            autoDetectPrimaryTimezone = autoDetectPrimaryTimezone,
            primaryTimezone = defaultTimezone,
            displaySecondaryTimezone = 0, // TODO
            secondaryTimezone = null, // TODO
            viewPreference = viewPreference,
            defaultCalendarId = calendarId,
            autoImportInvite = 0
        )
    }

    fun provideCalendar(
        isDisabled: Boolean = false,
        isHidden: Boolean = false,
        id: String = calendarId,
        defaultPartDayNotifications: List<Notification>? = null
    ): Calendar {
        return Calendar(
            id,
            calendarName,
            userEmail,
            userEmail,
            calendarDescription,
            calendarColor,
            0,
            addressId.id,
            memberId,
            if (isDisabled) MemberEntity.CalendarFlags.DISABLED.value else calendarFlags,
            if (isHidden) false else calendarDisplay.toBoolean(),
            calendarType,
            calendarPermissions,
            defaultEventDuration,
            defaultPartDayNotifications = defaultPartDayNotifications ?: listOf(
                Notification.Display(
                    Trigger(
                        Duration.builder().prior(true).minutes(15).build(),
                        Related.START
                    )
                )
            ),
            defaultFullDayNotifications = listOf(
                Notification.Display(
                    Trigger(
                        Duration.builder().prior(true).hours(15).build(), Related.START
                    )
                )
            )
        )
    }

    fun provideMemberEntity(memberEmail: String = userEmail, flags: Int = calendarFlags): MemberEntity {
        return MemberEntity(
            id = memberId,
            permissions = MemberEntity.Permission.SUPEROWNER.value,
            addressId = addressId.id,
            email = memberEmail,
            calendarId = calendarId,
            color = calendarColor,
            display = calendarDisplay,
            flags = flags,
            name = calendarName,
            description = calendarDescription,
            priority = 0
        )
    }
}
