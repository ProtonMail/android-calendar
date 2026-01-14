package me.proton.android.calendar.domain.model

import kotlinx.serialization.json.Json
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.data.entity.getCalendarOwnerEntity
import me.proton.android.calendar.data.entity.getDefaultNotifications
import me.proton.core.util.kotlin.toBoolean

data class Calendar(
        override val id: String,
        val name: String,
        val email: String,
        val ownerEmail: String?,
        val description: String,
        val color: String,
        val priority: Int?,
        val addressId: String?,
        val memberId: String?,
        val flags: Int,
        val display: Boolean,
        val type: Int,
        val permissions: Int,
        val defaultEventDuration: Int,
        val defaultPartDayNotifications: List<Notification>,
        val defaultFullDayNotifications: List<Notification>
) : BaseModel() {

        companion object {
                fun from(calendarEntity: CalendarEntity, memberEntity: MemberEntity, calendarSettingsEntity: CalendarSettingsEntity, json: Json) = Calendar(
                        id = calendarEntity.id,
                        name = memberEntity.name,
                        email = memberEntity.email,
                        ownerEmail = calendarEntity.getCalendarOwnerEntity(json)?.email,
                        description = memberEntity.description,
                        color = memberEntity.color,
                        priority = memberEntity.priority,
                        addressId = memberEntity.addressId,
                        memberId = memberEntity.id,
                        flags = memberEntity.flags,
                        display = memberEntity.display.toBoolean(),
                        type = calendarEntity.type,
                        permissions = memberEntity.permissions,
                        defaultEventDuration = calendarSettingsEntity.defaultEventDuration,
                        defaultPartDayNotifications = calendarSettingsEntity.getDefaultNotifications(json, isAllDay = false),
                        defaultFullDayNotifications = calendarSettingsEntity.getDefaultNotifications(json, isAllDay = true)
                )
        }

        // Functions to check all three states because it can be disabled but not inactive, or inactive but not disabled
        val isActive: Boolean get() = !isDisabled && !isInactive && (flags and 1 == 1)
        val isInactive: Boolean get() = (flags and (0 + 2 + 4 + 8 + 16) >= 1)
        val isDisabled: Boolean get() = !isInactive && (flags and (32 + 64) >= 1)
        val isSuperOwnerDisabled: Boolean get() = flags and 64 == 64
        val hasIncompleteKeySetup: Boolean get() = flags and 8 == 8
        val isResetNeeded: Boolean get() = flags and 4 == 4
        val hasUpdatePassphrase: Boolean get() = flags and 2 == 2

        val isSubscribed: Boolean get() = type == 1
        val isHolidayCalendar: Boolean get() = type == 2
        val isSharedWithMe: Boolean get() = !isOwner && type == 0

        val isOwner: Boolean get() = permissions and 2 == 2
        val allowEditEvents: Boolean get() = permissions and 16 == 16

        enum class CalendarType(val value: Int) {
            NORMAL(0),
            SUBSCRIBED(1),
            HOLIDAY(2)
        }

}
// TODO fields need to be duplicated here, plus local metadata added

fun List<Calendar>.filterVisibleCalendars(): List<Calendar> {
    return this.filter {
        it.display && (it.isActive || it.isDisabled)
    }
}
