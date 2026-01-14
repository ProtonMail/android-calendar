package me.proton.android.calendar.domain.utils

import android.content.Context
import android.content.DialogInterface
import biweekly.property.Attendee
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.contact.domain.entity.ContactEmail
import java.time.LocalDate

interface ProtonUtils {
    fun validateEmail(email: CharSequence): Boolean
    fun canonicalizeProtonEmails(emails: List<String>, forceCanonicalization: Boolean = false): Map<String, String>
    fun canonicalizeProtonEmail(email: String, forceCanonicalization: Boolean = false): String
    fun isProtonDomain(email: String): Boolean
    fun isShortDomainAddress(email: String): Boolean
    fun Context.displayEventDecryptionErrorDialog(isSharedEvent: Boolean, isRecurring: Boolean, callback: DialogInterface.OnClickListener)
    fun Context.displayFreeUserCalendarLimitReached(manageCalendarsCallback: DialogInterface.OnClickListener? = null)
    fun Context.displayFreeUserMandatoryPersonalCalendarLimitReached(manageCalendarsCallback: DialogInterface.OnClickListener? = null)
    fun Context.displayPaidUserCalendarLimitReached(manageCalendarsCallback: DialogInterface.OnClickListener? = null)
    fun Context.displayPaidUserMandatoryPersonalCalendarLimitReached(manageCalendarsCallback: DialogInterface.OnClickListener? = null)
    fun getCachedMonthViewsTimeWindow(selectedDate: LocalDate, weekStart: Int): Pair<LocalDate, LocalDate>
    fun sortPersonalCalendars(calendars: List<Calendar>, defaultCalendarId: String?): List<Calendar>
    fun sortOtherCalendars(calendars: List<Calendar>): List<Calendar>

    fun getMatchingDefaultHolidayCalendar(
        holidayCalendars: List<ManagedHolidayCalendarEntity>,
        primaryTimeZoneId: String,
        defaultLanguageCode: String,
        defaultCountryCode: String
    ): ManagedHolidayCalendarEntity?

    fun matchAttendeesWithContacts(
        attendees: List<Attendee>,
        deviceContacts: List<Attendee>,
        protonContacts: List<ContactEmail>
    ): List<Attendee>
}
