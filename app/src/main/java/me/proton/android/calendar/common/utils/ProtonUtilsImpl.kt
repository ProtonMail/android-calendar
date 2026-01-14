package me.proton.android.calendar.common.utils

import android.content.Context
import android.content.DialogInterface
import biweekly.property.Attendee
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.proton.android.calendar.R
import me.proton.android.calendar.common.CalendarSettings
import me.proton.android.calendar.common.PROTON_MAIL_DOMAINS
import me.proton.android.calendar.common.PROTON_MAIL_SHORT_DOMAIN
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.getLocaleForFormatting
import me.proton.android.calendar.common.utils.ICalUtilsImpl.extractEmail
import me.proton.android.calendar.data.entity.ManagedHolidayCalendarEntity
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.utils.ProtonUtils
import me.proton.android.calendar.presentation.calendar.customView.MonthView
import me.proton.core.configuration.EnvironmentConfigurationDefaults
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.presentation.utils.InputValidationResult
import me.proton.core.util.kotlin.equalsNoCase
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object ProtonUtilsImpl : ProtonUtils {

    override fun validateEmail(email: CharSequence): Boolean {
        val regex = InputValidationResult.EMAIL_VALIDATION_PATTERN.toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(email)
    }

    override fun canonicalizeProtonEmails(emails: List<String>, forceCanonicalization: Boolean): Map<String, String> {
        val canonicalEmails = hashMapOf<String, String>()
        emails.forEach {
            val canonicalEmail = canonicalizeProtonEmail(it, forceCanonicalization)
            canonicalEmails[it] = canonicalEmail
        }
        return canonicalEmails
    }

    override fun canonicalizeProtonEmail(email: String, forceCanonicalization: Boolean): String {
        // If user uses a custom domain, we don't apply any canonicalization.
        //  Can be forced with forceCanonicalization when we are certain the address belongs to a Proton user.
        //  forceCanonicalization should be used when comparing email with an attendee email, but not for the organizer.
        //  forceCanonicalization should only be used when comparing email, but not when using the value (ex: DO NOT force when saving it or generating xpm tokens with it).
        if (!forceCanonicalization && !isProtonDomain(email)) return email.lowercase(getLocaleForFormatting())

        val regex = Regex("(?:\\.|\\-|\\_|\\+.*)(?=.*@)")
        return email.replace(regex, "").lowercase(getLocaleForFormatting())
    }

    override fun isProtonDomain(email: String): Boolean {
        return (PROTON_MAIL_DOMAINS + listOf(EnvironmentConfigurationDefaults.host)).any {
            email.endsWith("@$it", true)
        }
    }

    override fun isShortDomainAddress(email: String): Boolean {
        return email.endsWith(PROTON_MAIL_SHORT_DOMAIN)
    }

    override fun Context.displayEventDecryptionErrorDialog(allowDelete: Boolean, isRecurring: Boolean, callback: DialogInterface.OnClickListener) {
        val confirmationMessage =
            if (isRecurring) R.string.event_decryption_error_dialog_confirmation_recurring
            else R.string.event_decryption_error_dialog_confirmation
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.event_decryption_error_dialog_title)
            .setMessage(R.string.event_decryption_error_dialog_message)
            .setNegativeButton(R.string.event_decryption_error_dialog_close) { _, _ -> }
        if (allowDelete) dialog.setPositiveButton(confirmationMessage, callback)
        dialog.show()
    }

    override fun Context.displayFreeUserCalendarLimitReached(
        manageCalendarsCallback: DialogInterface.OnClickListener?
    ) {
        // Display limit reached for free user dialog
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_calendar_limit_reached_title)
            .setMessage(R.string.create_calendar_limit_reached_free_description)
            .setNegativeButton(R.string.dialog_button_cancel) { _, _ -> }
        if (manageCalendarsCallback != null) {
            materialAlertDialogBuilder
                .setPositiveButton(R.string.create_calendar_limit_reached_manage, manageCalendarsCallback)
        }
        materialAlertDialogBuilder.show()
    }

    override fun Context.displayFreeUserMandatoryPersonalCalendarLimitReached(
        manageCalendarsCallback: DialogInterface.OnClickListener?
    ) {
        // Display limit reached for free user dialog
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_calendar_limit_reached_title)
            .setMessage(R.string.create_mandatory_personal_calendar_limit_reached_free_description)
            .setNegativeButton(R.string.dialog_button_cancel) { _, _ -> }
        if (manageCalendarsCallback != null) {
            materialAlertDialogBuilder
                .setPositiveButton(R.string.create_calendar_limit_reached_manage, manageCalendarsCallback)
        }
        materialAlertDialogBuilder.show()
    }

    override fun Context.displayPaidUserCalendarLimitReached(
        manageCalendarsCallback: DialogInterface.OnClickListener?
    ) {
        // Display limit reached for paid user dialog
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_calendar_limit_reached_title)
            .setMessage(R.string.create_calendar_limit_reached_paid_description)
            .setNegativeButton(R.string.dialog_button_cancel) { _, _ -> }
        if (manageCalendarsCallback != null) {
            materialAlertDialogBuilder
                .setPositiveButton(R.string.create_calendar_limit_reached_manage, manageCalendarsCallback)
        }
        materialAlertDialogBuilder.show()
    }

    override fun Context.displayPaidUserMandatoryPersonalCalendarLimitReached(
        manageCalendarsCallback: DialogInterface.OnClickListener?
    ) {
        // Display limit reached for paid user dialog
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_calendar_limit_reached_title)
            .setMessage(R.string.create_mandatory_personal_calendar_limit_reached_paid_description)
            .setNegativeButton(R.string.dialog_button_cancel) { _, _ -> }
        if (manageCalendarsCallback != null) {
            materialAlertDialogBuilder
                .setPositiveButton(R.string.create_calendar_limit_reached_manage, manageCalendarsCallback)
        }
        materialAlertDialogBuilder.show()
    }

    /**
     * This will return a Pair of LocalDate representing the total cached month views time window, taking into account offset days.
     */
    override fun getCachedMonthViewsTimeWindow(selectedDate: LocalDate, weekStart: Int): Pair<LocalDate, LocalDate> {
        val startWeekOn = AndroidUtils.getWeekStartDayOfWeek(weekStart)

        val firstDayOfTheMonth = selectedDate.with(TemporalAdjusters.firstDayOfMonth())

        // Calculate the offset days shown in minimum cached month view
        val firstDayOfMinMonth = firstDayOfTheMonth.minusMonths(MonthView.MonthViewSettings.MONTH_VIEW_CACHE)
        val minMonthFirstDayOfTheWeekNumber = firstDayOfMinMonth.dayOfWeek.value - startWeekOn.value
        val minMonthFirstDayOfTheWeekOffset =
            if (minMonthFirstDayOfTheWeekNumber < 0) minMonthFirstDayOfTheWeekNumber + CalendarSettings.DAYS_IN_A_WEEK
            else minMonthFirstDayOfTheWeekNumber
        val fromDate = firstDayOfMinMonth.minusDays(minMonthFirstDayOfTheWeekOffset.toLong())

        // Calculate the offset days shown in maximum cached month view
        val firstDayOfMaxMonth = firstDayOfTheMonth.plusMonths(MonthView.MonthViewSettings.MONTH_VIEW_CACHE)
        val maxMonthFirstDayOfTheWeekNumber = firstDayOfMaxMonth.dayOfWeek.value - startWeekOn.value
        val maxMonthFirstDayOfTheWeekOffset =
            if (maxMonthFirstDayOfTheWeekNumber < 0) maxMonthFirstDayOfTheWeekNumber + CalendarSettings.DAYS_IN_A_WEEK
            else maxMonthFirstDayOfTheWeekNumber
        val maxMonthLastDayOfMonthOffset = MonthView.MonthViewSettings.MONTH_GRID_ITEMS_MAX - (maxMonthFirstDayOfTheWeekOffset + firstDayOfMaxMonth.lengthOfMonth())
        val toDate = firstDayOfMaxMonth.with(TemporalAdjusters.lastDayOfMonth()).plusDays(maxMonthLastDayOfMonthOffset.toLong())

        return Pair(fromDate, toDate)
    }

    override fun sortPersonalCalendars(calendars: List<Calendar>, defaultCalendarId: String?): List<Calendar> {
        return if (calendars.any { it.priority == null }) {
            calendars.sortedBy {
                it.isDisabled // Disabled will appear last
            }.sortedByDescending {
                it.id == defaultCalendarId // Default will appear first
            }
        } else {
            calendars.sortedBy {
                it.priority
            }.sortedBy {
                it.isDisabled // Disabled will appear last
            }.sortedByDescending {
                it.id == defaultCalendarId // Default will appear first
            }
        }
    }

    override fun sortOtherCalendars(calendars: List<Calendar>): List<Calendar> {
        return if (calendars.any { it.priority == null }) {
            calendars.sortedBy {
                it.isDisabled // Disabled will appear last
            }
        } else {
            calendars.sortedBy {
                it.priority
            }.sortedBy {
                it.isDisabled // Disabled will appear last
            }
        }
    }

    override fun getMatchingDefaultHolidayCalendar(
        holidayCalendars: List<ManagedHolidayCalendarEntity>,
        primaryTimeZoneId: String,
        defaultLanguageCode: String,
        defaultCountryCode: String
    ): ManagedHolidayCalendarEntity? {
        // Get calendars matching the default time zone
        val calendarsMatchingTimeZone = holidayCalendars.filter {
            it.timezones.contains(primaryTimeZoneId)
        }

        val calendarsMatchingCode =
            if (calendarsMatchingTimeZone.size > 1) {
                // If there are more than one match, use the Locale country tag.
                val calendarsMatchingCountryCode =
                    defaultCountryCode.takeIfNotEmpty()?.let {
                        calendarsMatchingTimeZone.filter {
                            it.countryCode.equalsNoCase(defaultCountryCode)
                        }
                    } ?: emptyList()
                // If we don't have a country tag or didn't find a match, use the Locale language tag.
                calendarsMatchingCountryCode.ifEmpty {
                    val calendarsMatchingLanguageCode =
                        calendarsMatchingTimeZone.filter {
                            it.languageCode.equalsNoCase(defaultLanguageCode)
                        }
                    calendarsMatchingLanguageCode.ifEmpty {
                        calendarsMatchingTimeZone
                    }
                }
            } else calendarsMatchingTimeZone

        // Get the calendar matching the default language
        val matchingDefaultHolidayCalendar = calendarsMatchingCode.firstOrNull {
            it.languageCode.equals(defaultLanguageCode, ignoreCase = true)
        } ?: calendarsMatchingCode.minByOrNull { it.language }

        return matchingDefaultHolidayCalendar
    }

    override fun matchAttendeesWithContacts(
        attendees: List<Attendee>,
        deviceContacts: List<Attendee>,
        protonContacts: List<ContactEmail>
    ): List<Attendee> {
        return attendees.map { attendee ->
            val protonContact = protonContacts.filter {
                it.email.equalsNoCase(attendee.extractEmail())
            }.minByOrNull {
                // Take the contact with the lowest Order value
                it.order
            }
            val deviceContact = deviceContacts.find {
                it.extractEmail()?.equalsNoCase(attendee.extractEmail()) == true
            }
            // Take the entry that has a name, with priority on Proton contact
            if (protonContact != null && protonContact.name.isNotEmpty()) {
                attendee.commonName = protonContact.name
                attendee.email = protonContact.email
                attendee
            } else if (deviceContact != null && !deviceContact.commonName.isNullOrEmpty()) {
                attendee.commonName = deviceContact.commonName
                attendee.email = deviceContact.email
                attendee
            } else {
                attendee
            }
        }
    }
}

