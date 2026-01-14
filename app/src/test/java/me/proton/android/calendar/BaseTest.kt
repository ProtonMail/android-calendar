package me.proton.android.calendar

import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.domain.model.Event

internal abstract class BaseTest {

    fun eventForICalString(iCalString: String, eventId: String? = null, type: Int = 0, calendarIsSharedWithMe: Boolean = false): Event {
        return Event.from(eventId ?: "event-id", me.proton.android.calendar.domain.model.Calendar(
            "calendar-id",
            "calendar",
            "email",
            "ownerEmail",
            "description",
            "",
            0,
            "addressId",
            "memberId",
            1,
            true,
            type,
            if (calendarIsSharedWithMe) (127 and 2.inv() /* remove bit with owner permissions */) else 127,
            30,
            emptyList(),
            emptyList()
        ), ICalUtilsImpl.parseICalString(iCalString)!!, 0, null, null)!!
    }
}
