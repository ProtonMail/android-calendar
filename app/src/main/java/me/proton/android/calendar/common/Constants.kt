package me.proton.android.calendar.common

import biweekly.component.VAlarm
import biweekly.parameter.Related
import biweekly.property.Trigger
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

typealias CalDuration = biweekly.util.Duration

const val API_VERSION_CALENDAR = "v1"
const val API_APPLICATION_NAME = "android-calendar"
const val API_DEBUG_APPLICATION_SUFFIX = "-dev"
const val PROD_ID_APPLICATION_NAME = "AndroidCalendar"

const val DEFAULT_DOMAIN_HOST = "proton.me"

const val USER_AGENT_NAME = "ProtonCalendar"

const val OFFLINE_EVENT_ID_PREFIX = "Proton-Android-App-Offline-Event-ID:"
const val OFFLINE_ALARM_ID_PREFIX = "Proton-Android-App-Offline-Alarm-ID:"
const val ATTENDEE_AUTO_EXPAND_LIMIT = 5

const val CLICK_INTERVAL_MS: Long = 500L

const val SIGNATURE_VERIFICATION_API_TIMEOUT: Long = 10_000L

const val WORKER_MAX_RETRY_COUNT = 5

val PERIODIC_CALENDAR_WORKER_REFRESH_PERIOD: Duration = Duration.ofHours(1)

val SYNC_CALENDARS_DELAY: Duration = Duration.ofSeconds(3)
val UPDATE_PASSPHRASE_CALENDARS_DELAY: Duration = Duration.ofSeconds(5)
val PLAY_STORE_RATING_DELAY: Duration = Duration.ofSeconds(3)

val REFRESH_CURRENT_TIME_INDICATOR = Duration.ofMinutes(1).toMillis()

// TODO change this also in Navigation.kt
const val DEEPLINK_PATH_EVENT_DETAILS = "proton-calendar://protonmail.com/event_details/"
const val DEEPLINK_PATH_EVENT_EDIT = "proton-calendar://protonmail.com/event/edit?eventId="
const val DEEPLINK_PATH_EVENT_EDIT_PERSONAL = "proton-calendar://protonmail.com/event/editPersonal?eventId="
const val DEEPLINK_PATH_EVENT_CREATE = "proton-calendar://protonmail.com/event/create"

const val DEFAULT_CALENDAR_COLOR: String = "#8080FF"
const val DEFAULT_HOLIDAY_CALENDAR_COLOR: String = "#F78400"

const val INVITE_ICS_FILE_NAME = "invite.ics"
const val INVITE_ICS_MIME_TYPE = "text/calendar"
const val INVITE_ICS_MIME_TYPE_TEMPLATE = "text/calendar; method=%s"
const val INVITE_EMAIL_MIME_TYPE = "text/plain"
const val INVITE_PROTON_INTENT_ACTION = "me.proton.android.calendar.intent.action.CTA_OPEN_ICS"
const val INVITE_PROTON_EXTRA_SENDER_EMAIL = "me.proton.android.calendar.intent.extra.ICS_SENDER_EMAIL"
const val INVITE_PROTON_EXTRA_RECIPIENT_EMAIL = "me.proton.android.calendar.intent.extra.ICS_RECIPIENT_EMAIL"

const val ICAL_LINE_MAXIMUM_LENGTH = 75
const val ICAL_LINE_SEPARATOR = "\\r\\n "
const val ICAL_UID_PREFIX = "UID:"

const val MAX_ANIM_DURATION = 500L

const val CONTACTS_SEARCH_QUERY = "CONTACTS_SEARCH_QUERY"

const val SESSION_KEY_ALGO = "aes256"

val PROTON_MAIL_DOMAINS = arrayListOf("protonmail.ch", "protonmail.com", "pm.me", "proton.me")
const val PROTON_MAIL_SHORT_DOMAIN = "@pm.me"

const val PROTON_UID = "@proton.me"
const val PROTON_OLD_UID = "proton-calendar"

const val MAX_EMAILS_PER_QUERY: Int = 8

const val DAY_VIEW_ALL_DAY_MAX = 3

const val MAX_CALENDAR_PAID = 25
const val MAX_CALENDAR_FREE = 3

const val MAX_CALENDAR_INDICATORS = 5

const val SEARCH_MIN_QUERY_LENGTH = 2
const val SEARCH_QUERY_DEBOUNCE_MS = 500L
const val SEARCH_RESULTS_RANGE = 500

const val HOLIDAY_SEARCH_MIN_QUERY_LENGTH = 2

const val RC_CREATE_IMPORT_SIGN_IN = 11

const val DAY_VIEW_DAYS_COUNT = 1
const val THREE_DAYS_VIEW_DAYS_COUNT = 3
const val WEEK_VIEW_DAYS_COUNT = 7

const val WEEK_VIEW_PAST_DAYS_TO_LOAD = 7L
const val WEEK_VIEW_FUTURE_DAYS_TO_LOAD = 13L

const val WEEK_VIEW_WEEKDAY_FORMATTER_PATTERN = "EEE"
const val WEEK_VIEW_DATE_FORMATTER_PATTERN = "d"

const val MONTH_VIEW_VERSION_CODE = 112
const val REBRANDING_VERSION_CODE = 145
const val EASY_SWITCH_VERSION_CODE = 150
const val WEEK_VIEW_VERSION_CODE = 164
const val IMPORT_VERSION_CODE = 186
const val CALENDAR_PROVIDER_VERSION_CODE = 191
const val HOLIDAY_CALENDAR_VERSION_CODE = 223
const val COLOR_PER_EVENT_VERSION_CODE = 262
const val SEARCH_VERSION_CODE = 267

const val FETCH_EVENTS_MAX_DAYS_WINDOW = 42 // Maximum number of days shown at once in a view (Currently month view)

const val PING_INTERVAL_SECONDS = 30L
const val PING_TIMEOUT_SECONDS = 3
const val SERVER_DOWN_BANNER_DURATION_SECONDS = 10L

const val FETCH_FEATURE_FLAG_INTERVAL_SECONDS = 30L

val SPOTLIGHT_VERSION_CODES = arrayListOf(
    MONTH_VIEW_VERSION_CODE, // Month view (0.30.3)
    REBRANDING_VERSION_CODE, // Rebranding (2.0.2)
    EASY_SWITCH_VERSION_CODE, // Easy switch (2.2.0)
    WEEK_VIEW_VERSION_CODE, // Week view (2.3.10)
    IMPORT_VERSION_CODE, // Import (2.6.6)
    CALENDAR_PROVIDER_VERSION_CODE, // Calendar provider (2.9.1)
    HOLIDAY_CALENDAR_VERSION_CODE, // Holiday calendar (2.12.4)
    COLOR_PER_EVENT_VERSION_CODE, // Color per event (2.17.3)
    SEARCH_VERSION_CODE // Search view (2.19.1)
)

object HttpResponseCode {
    const val NOT_FOUND = 404
    const val UNPROCESSABLE_ENTITY = 422
}

object ApiResponseCode {
    const val DOES_NOT_EXIST = 2501
}

object SharedPreferencesKeys {
    const val THEME = "theme"
    const val ALTERNATIVE_ROUTING = "alternative_routing"
    const val VIEW_MODE = "view_mode"
    const val USE_DEFAULT_VIEW_MODE = "use_default_view_mode"
    const val SHOW_CONTACTS_PERMISSIONS_DIALOG = "show_contacts_permissions_dialog"
    const val SHOW_NOTIFICATIONS_PERMISSIONS_DIALOG = "show_notifications_permissions_dialog"
    const val HACK_USER_ADDRESS_INVALID_FOR_SENDING = "hack_user_address_invalid_for_sending"
    const val LAST_SPOTLIGHT_SHOWN = "last_spotlight_shown"
    const val APP_SETTINGS_LANGUAGE = "app_settings_language"
    const val APP_CURRENT_LANGUAGE = "app_current_language"
    const val APP_INSTALLATION_ID = "app_installation_id"
    const val WEEK_VIEW_HOUR_HEIGHT = "week_view_hour_height"
}

object MiniCalendarGestures {
    const val MAX_CLICK_DURATION = 1000L
    const val MAX_FLICK_DURATION = 100L
    const val MIN_FLICK_DISTANCE = 50
    const val MAX_CLICK_DISTANCE = 15
}

object Animation {
    const val HEIGHT_CHANGE_DURATION = 300L
}

object CalendarSettings {
    const val DAYS_IN_A_WEEK = 7 // always 7
}

enum class AppTheme(val value: Int) {
    LIGHT(0),
    DARK(1),
    SYSTEM_DEFAULT(2)
}

enum class AlarmAction(val value: Int) {
    EMAIL(1),
    DISPLAY(2)
}

enum class ViewMode(val value: Int) {
    AGENDA(0),
    DAY(1),
    THREE_DAY(2),
    WEEK(3),
    MONTH(4)
}

enum class EventEditDeleteOption {
    THIS_EVENT,
    THIS_EVENT_AND_FUTURE,
    ALL_EVENTS
}

enum class EventDeletionReason(val value: Int) {
    ByUser(0),
    CalendarChange(1)
}

object IcsParsingValidation {
    const val UID_MAX_LENGTH = 191
    const val CONTACT_NAME_MAX_LENGTH = 190
    const val SUMMARY_MAX_LENGTH = 255
    const val LOCATION_MAX_LENGTH = 255
    const val DESCRIPTION_MAX_LENGTH = 3000

    const val X_PM_TOKEN_LENGTH = 40

    const val MAX_COUNT = 49
    const val MAX_COUNT_INVITATION = 499
    const val MAX_DAILY_INTERVAL = 999
    const val MAX_WEEKLY_INTERVAL = 4999
    const val MAX_MONTHLY_INTERVAL = 999
    const val MAX_YEARLY_INTERVAL = 99
    const val MAX_ATTENDEES = 100

    const val MAX_VCALENDAR_COUNT = 1
    const val MAX_VEVENT_COUNT = 1

    val MIN_DATE = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))
    val MAX_DATE = ZonedDateTime.of(2038, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"))

    const val UTC_TIME_ZONE_ID = "UTC"
    const val X_WR_TIMEZONE = "X-WR-TIMEZONE"
    const val TZID = "TZID"
    const val TZID_PARAMETER = ";TZID="
    const val ATTENDEE_PROPERTY = "ATTENDEE;"
    const val ORGANIZER_PROPERTY = "ORGANIZER;"
}

object AppLinksQueryParameters {
    const val EVENT_ID = "EventID"
    const val CALENDAR_ID = "CalendarID"
    const val RECURRENCE_ID = "RecurrenceID"
    const val ACTION = "Action"

    const val EASY_SWITCH_CODE = "code"
    const val EASY_SWITCH_SCOPE = "scope"
    const val EASY_SWITCH_STATE = "state"
}

object CalendarImport {
    const val GOOGLE_CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    const val REDIRECT_URI = "https://calendar.proton.me/easy_switch"
    const val PRODUCT_CALENDAR = "Calendar"
    const val SOURCE = "calendar-android-settings"
}

object GoogleSignInCodes {
    const val SIGN_IN_CANCELED = 12501
    const val SIGN_IN_CURRENTLY_IN_PROGRESS = 12502
    const val SIGN_IN_FAILED = 12500
}

object AppLinksAction {
    const val VIEW = "VIEW"
}

object FormValidation {

    val MIN_SUPPORTED_DATETIME: ZonedDateTime =
        LocalDate.of(1970, 1, 1)
            .atStartOfDay(ZoneId.of("UTC"))
    val MAX_SUPPORTED_DATETIME: ZonedDateTime =
        LocalDate.of(2038, 12, 31)
            .atStartOfDay(ZoneId.of("UTC"))

    const val OCCURRENCE_COUNT_DEFAULT = 2
    const val OCCURRENCE_COUNT_MIN = 1
    const val OCCURRENCE_COUNT_MAX = 49

    const val INTERVAL_DAY_COUNT_DEFAULT = 1
    const val INTERVAL_DAY_COUNT_MIN = 1
    const val INTERVAL_DAY_COUNT_MAX = 999

    const val INTERVAL_WEEK_COUNT_DEFAULT = 1
    const val INTERVAL_WEEK_COUNT_MIN = 1
    const val INTERVAL_WEEK_COUNT_MAX = 4999

    const val INTERVAL_MONTH_COUNT_DEFAULT = 1
    const val INTERVAL_MONTH_COUNT_MIN = 1
    const val INTERVAL_MONTH_COUNT_MAX = 999

    const val INTERVAL_YEAR_COUNT_DEFAULT = 1
    const val INTERVAL_YEAR_COUNT_MIN = 1
    const val INTERVAL_YEAR_COUNT_MAX = 99

    // TODO use this when saving/editing/IMPORTING Event
    const val EVENT_SUMMARY_MAX_LENGTH = 255
    const val EVENT_LOCATION_MAX_LENGTH = 255
    const val EVENT_DESCRIPTION_MAX_LENGTH = 3000

    const val ALARM_COUNT_MAX = 10

    const val ALARM_PERIOD_COUNT_ALL_DAY_DEFAULT = 1
    const val ALARM_PERIOD_COUNT_PARTIAL_DAY_DEFAULT = 15
    const val ALARM_PERIOD_COUNT_MIN = 1

    const val ALARM_PERIOD_MAX_WEEKS = 999
    const val ALARM_PERIOD_MAX_DAYS = 6999
    const val ALARM_PERIOD_MAX_HOURS = 999
    const val ALARM_PERIOD_MAX_MINUTES = 9999

    const val ATTENDEE_SHOW_TRESHOLD = 5

    const val ATTENDEE_MAX_ALLOWED = 100
    const val ATTENDEE_MAX_CHIP_ALLOWED = 4

}

object CalendarForm {
    const val CALENDAR_NAME_CHARACTER_LIMIT = 100
    const val CALENDAR_DESCRIPTION_CHARACTER_LIMIT = 255
    const val DEFAULT_NOTIFICATIONS_COUNT_MAX = 5

    val EVENT_DEFAULT_DURATION_MINUTES = listOf(30, 60, 90, 120)

    private val prior15Minutes = CalDuration.builder().prior(true).minutes(15).build()
    private val prior15Hours = CalDuration.builder().prior(true).hours(15).build()

    val DEFAULT_PART_DAY_ALARM = VAlarm.display(Trigger(prior15Minutes, Related.START), null)!!
    val DEFAULT_PART_DAY_EMAIL_ALARM = VAlarm.email(Trigger(prior15Minutes, Related.START), null, null)!!
    val DEFAULT_ALL_DAY_ALARM = VAlarm.display(Trigger(prior15Hours, Related.START), null)!!
    val DEFAULT_ALL_DAY_EMAIL_ALARM = VAlarm.email(Trigger(prior15Hours, Related.START), null, null)!!
}

object FragmentArguments {
    // Fragment position in the adapter
    const val POSITION_ARG = "POSITION_ARG"
    // Adapter starting position
    const val STARTING_POSITION_ARG = "STARTING_POSITION_ARG"
    // Date value for adapter item
    const val DATE_ARG = "DATE_ARG"

    /* Arguments defined in graph_main */

    // Calendar ID for calendar form
    const val CALENDAR_ID_ARG = "calendarId"
    // All day value for alarm form (show different views when part or all day)
    const val IS_ALL_DAY_ARG = "isAllDay"
    // Boolean to know whether we open the alarm form to create calendar or event alarms
    const val DEFAULT_NOTIFICATIONS_TYPE_ARG = "defaultNotificationsType"
    const val READ_ONLY_ARG = "readOnly"
}

object CustomICalPropertyParameter {
    const val X_PM_TOKEN = "X-PM-TOKEN"
    const val X_PM_SESSION_KEY = "X-PM-SESSION-KEY"
    const val X_PM_SHARED_EVENT_ID = "X-PM-SHARED-EVENT-ID"
    const val X_PM_PROTON_REPLY = "X-PM-PROTON-REPLY"
    const val X_PM_CONFERENCE_ID = "X-PM-CONFERENCE-ID"
    const val X_PM_CONFERENCE_URL = "X-PM-CONFERENCE-URL"
    const val PARAMETER_CONFERENCE_PASSWORD = "X-PM-PASSWORD"
    const val PARAMETER_CONFERENCE_PASSWORD_READONLY = "PASSWORD"
    const val PARAMETER_CONFERENCE_HOST = "X-PM-HOST"
    const val PARAMETER_CONFERENCE_HOST_READONLY = "HOST"
    const val PARAMETER_CONFERENCE_PROVIDER = "X-PM-PROVIDER"
    const val PARAMETER_CONFERENCE_PROVIDER_READONLY = "PROVIDER"
    const val PARAMETER_CONFERENCE_CREATOR = "X-PM-CREATOR"
    const val PARAMETER_CONFERENCE_CREATOR_READONLY = "CREATOR"

    const val CONFERENCE_DESCRIPTION_HEADER = "~-~-~-~-~-~-~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~-~-~-~-~-~-~"
    const val CONFERENCE_DESCRIPTION_REGEX_STRING = "~-~-~-~-~-~-~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~-~-~-~-~-~-~[\\s\\S]*?~-~-~-~-~-~-~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~%~!~-~-~-~-~-~-~"
}

object MessageDigestHashType {
    const val SHA1 = "SHA-1"
}

private val EuropeKyiv = "Europe/Kyiv"
private val EuropeKyivApiSupported = "Europe/Kiev"

val timezoneApiOverrides = mapOf(
    EuropeKyiv to EuropeKyivApiSupported,
)

val timezoneDisplayOverrides = mapOf(
    EuropeKyivApiSupported to EuropeKyiv,
)

val allowedTimezoneIds = listOf(
    "Africa/Abidjan",
    "Africa/Accra",
    "Africa/Algiers",
    "Africa/Bissau",
    "Africa/Cairo",
    "Africa/Casablanca",
    "Africa/Ceuta",
    "Africa/El_Aaiun",
    "Africa/Johannesburg",
    "Africa/Juba",
    "Africa/Khartoum",
    "Africa/Lagos",
    "Africa/Maputo",
    "Africa/Monrovia",
    "Africa/Nairobi",
    "Africa/Ndjamena",
    "Africa/Sao_Tome",
    "Africa/Tripoli",
    "Africa/Tunis",
    "Africa/Windhoek",
    "America/Adak",
    "America/Anchorage",
    "America/Araguaina",
    "America/Argentina/Buenos_Aires",
    "America/Argentina/Catamarca",
    "America/Argentina/Cordoba",
    "America/Argentina/Jujuy",
    "America/Argentina/La_Rioja",
    "America/Argentina/Mendoza",
    "America/Argentina/Rio_Gallegos",
    "America/Argentina/Salta",
    "America/Argentina/San_Juan",
    "America/Argentina/San_Luis",
    "America/Argentina/Tucuman",
    "America/Argentina/Ushuaia",
    "America/Asuncion",
    "America/Atikokan",
    "America/Bahia",
    "America/Bahia_Banderas",
    "America/Barbados",
    "America/Belem",
    "America/Belize",
    "America/Blanc-Sablon",
    "America/Boa_Vista",
    "America/Bogota",
    "America/Boise",
    "America/Cambridge_Bay",
    "America/Campo_Grande",
    "America/Cancun",
    "America/Caracas",
    "America/Cayenne",
    "America/Chicago",
    "America/Chihuahua",
    "America/Costa_Rica",
    "America/Creston",
    "America/Cuiaba",
    "America/Curacao",
    "America/Danmarkshavn",
    "America/Dawson",
    "America/Dawson_Creek",
    "America/Denver",
    "America/Detroit",
    "America/Edmonton",
    "America/Eirunepe",
    "America/El_Salvador",
    "America/Fort_Nelson",
    "America/Fortaleza",
    "America/Glace_Bay",
    "America/Godthab",
    "America/Goose_Bay",
    "America/Grand_Turk",
    "America/Guatemala",
    "America/Guayaquil",
    "America/Guyana",
    "America/Halifax",
    "America/Havana",
    "America/Hermosillo",
    "America/Indiana/Knox",
    "America/Indiana/Marengo",
    "America/Indiana/Petersburg",
    "America/Indiana/Tell_City",
    "America/Indiana/Vevay",
    "America/Indiana/Vincennes",
    "America/Indiana/Winamac",
    "America/Inuvik",
    "America/Iqaluit",
    "America/Jamaica",
    "America/Juneau",
    "America/Kentucky/Louisville",
    "America/Kentucky/Monticello",
    "America/La_Paz",
    "America/Lima",
    "America/Los_Angeles",
    "America/Maceio",
    "America/Managua",
    "America/Manaus",
    "America/Martinique",
    "America/Matamoros",
    "America/Mazatlan",
    "America/Menominee",
    "America/Merida",
    "America/Metlakatla",
    "America/Mexico_City",
    "America/Miquelon",
    "America/Moncton",
    "America/Monterrey",
    "America/Montevideo",
    "America/Nassau",
    "America/New_York",
    "America/Nipigon",
    "America/Nome",
    "America/Noronha",
    "America/North_Dakota/Beulah",
    "America/North_Dakota/Center",
    "America/North_Dakota/New_Salem",
    "America/Ojinaga",
    "America/Panama",
    "America/Pangnirtung",
    "America/Paramaribo",
    "America/Phoenix",
    "America/Port-au-Prince",
    "America/Port_of_Spain",
    "America/Porto_Velho",
    "America/Puerto_Rico",
    "America/Punta_Arenas",
    "America/Rainy_River",
    "America/Rankin_Inlet",
    "America/Recife",
    "America/Regina",
    "America/Resolute",
    "America/Rio_Branco",
    "America/Santarem",
    "America/Santiago",
    "America/Santo_Domingo",
    "America/Sao_Paulo",
    "America/Scoresbysund",
    "America/Sitka",
    "America/St_Johns",
    "America/Swift_Current",
    "America/Tegucigalpa",
    "America/Thule",
    "America/Thunder_Bay",
    "America/Tijuana",
    "America/Toronto",
    "America/Vancouver",
    "America/Whitehorse",
    "America/Winnipeg",
    "America/Yakutat",
    "America/Yellowknife",
    "Antarctica/Casey",
    "Antarctica/Davis",
    "Antarctica/DumontDUrville",
    "Antarctica/Macquarie",
    "Antarctica/Mawson",
    "Antarctica/Palmer",
    "Antarctica/Rothera",
    "Antarctica/Syowa",
    "Antarctica/Troll",
    "Antarctica/Vostok",
    "Asia/Almaty",
    "Asia/Amman",
    "Asia/Anadyr",
    "Asia/Aqtau",
    "Asia/Aqtobe",
    "Asia/Ashgabat",
    "Asia/Atyrau",
    "Asia/Baghdad",
    "Asia/Baku",
    "Asia/Bangkok",
    "Asia/Barnaul",
    "Asia/Beirut",
    "Asia/Bishkek",
    "Asia/Brunei",
    "Asia/Chita",
    "Asia/Choibalsan",
    "Asia/Colombo",
    "Asia/Damascus",
    "Asia/Dhaka",
    "Asia/Dili",
    "Asia/Dubai",
    "Asia/Dushanbe",
    "Asia/Famagusta",
    "Asia/Gaza",
    "Asia/Hebron",
    "Asia/Ho_Chi_Minh",
    "Asia/Hong_Kong",
    "Asia/Hovd",
    "Asia/Irkutsk",
    "Asia/Jakarta",
    "Asia/Jayapura",
    "Asia/Jerusalem",
    "Asia/Kabul",
    "Asia/Kamchatka",
    "Asia/Karachi",
    "Asia/Kathmandu",
    "Asia/Khandyga",
    "Asia/Kolkata",
    "Asia/Krasnoyarsk",
    "Asia/Kuala_Lumpur",
    "Asia/Kuching",
    "Asia/Macau",
    "Asia/Magadan",
    "Asia/Makassar",
    "Asia/Manila",
    "Asia/Nicosia",
    "Asia/Novokuznetsk",
    "Asia/Novosibirsk",
    "Asia/Omsk",
    "Asia/Oral",
    "Asia/Pontianak",
    "Asia/Pyongyang",
    "Asia/Qatar",
    "Asia/Qostanay",
    "Asia/Qyzylorda",
    "Asia/Riyadh",
    "Asia/Sakhalin",
    "Asia/Samarkand",
    "Asia/Seoul",
    "Asia/Shanghai",
    "Asia/Srednekolymsk",
    "Asia/Taipei",
    "Asia/Tashkent",
    "Asia/Tbilisi",
    "Asia/Tehran",
    "Asia/Thimphu",
    "Asia/Tokyo",
    "Asia/Tomsk",
    "Asia/Ulaanbaatar",
    "Asia/Urumqi",
    "Asia/Ust-Nera",
    "Asia/Vladivostok",
    "Asia/Yakutsk",
    "Asia/Yekaterinburg",
    "Asia/Yerevan",
    "Atlantic/Azores",
    "Atlantic/Bermuda",
    "Atlantic/Canary",
    "Atlantic/Cape_Verde",
    "Atlantic/Faroe",
    "Atlantic/Madeira",
    "Atlantic/Reykjavik",
    "Atlantic/South_Georgia",
    "Atlantic/Stanley",
    "Australia/Adelaide",
    "Australia/Brisbane",
    "Australia/Broken_Hill",
    "Australia/Currie",
    "Australia/Darwin",
    "Australia/Eucla",
    "Australia/Hobart",
    "Australia/Lindeman",
    "Australia/Lord_Howe",
    "Australia/Melbourne",
    "Australia/Perth",
    "Australia/Sydney",
    "Europe/Amsterdam",
    "Europe/Andorra",
    "Europe/Astrakhan",
    "Europe/Athens",
    "Europe/Belgrade",
    "Europe/Berlin",
    "Europe/Brussels",
    "Europe/Bucharest",
    "Europe/Budapest",
    "Europe/Chisinau",
    "Europe/Copenhagen",
    "Europe/Dublin",
    "Europe/Gibraltar",
    "Europe/Helsinki",
    "Europe/Istanbul",
    "Europe/Kaliningrad",
    EuropeKyiv,
    "Europe/Kirov",
    "Europe/Lisbon",
    "Europe/London",
    "Europe/Luxembourg",
    "Europe/Madrid",
    "Europe/Malta",
    "Europe/Minsk",
    "Europe/Monaco",
    "Europe/Moscow",
    "Europe/Oslo",
    "Europe/Paris",
    "Europe/Prague",
    "Europe/Riga",
    "Europe/Rome",
    "Europe/Samara",
    "Europe/Saratov",
    "Europe/Simferopol",
    "Europe/Sofia",
    "Europe/Stockholm",
    "Europe/Tallinn",
    "Europe/Tirane",
    "Europe/Ulyanovsk",
    "Europe/Uzhgorod",
    "Europe/Vienna",
    "Europe/Vilnius",
    "Europe/Volgograd",
    "Europe/Warsaw",
    "Europe/Zaporozhye",
    "Europe/Zurich",
    "Indian/Chagos",
    "Indian/Christmas",
    "Indian/Cocos",
    "Indian/Kerguelen",
    "Indian/Mahe",
    "Indian/Maldives",
    "Indian/Mauritius",
    "Indian/Reunion",
    "Pacific/Apia",
    "Pacific/Auckland",
    "Pacific/Bougainville",
    "Pacific/Chatham",
    "Pacific/Chuuk",
    "Pacific/Easter",
    "Pacific/Efate",
    "Pacific/Fakaofo",
    "Pacific/Fiji",
    "Pacific/Galapagos",
    "Pacific/Gambier",
    "Pacific/Guadalcanal",
    "Pacific/Guam",
    "Pacific/Honolulu",
    "Pacific/Kiritimati",
    "Pacific/Kosrae",
    "Pacific/Kwajalein",
    "Pacific/Majuro",
    "Pacific/Marquesas",
    "Pacific/Nauru",
    "Pacific/Niue",
    "Pacific/Norfolk",
    "Pacific/Noumea",
    "Pacific/Pago_Pago",
    "Pacific/Palau",
    "Pacific/Pitcairn",
    "Pacific/Pohnpei",
    "Pacific/Port_Moresby",
    "Pacific/Rarotonga",
    "Pacific/Tahiti",
    "Pacific/Tarawa",
    "Pacific/Tongatapu",
    "UTC"
)

val aliasesTimezonesMap: Map<String, String> = mapOf(
    "Africa/Bamako" to "Africa/Abidjan",
    "Africa/Banjul" to "Africa/Abidjan",
    "Africa/Conakry" to "Africa/Abidjan",
    "Africa/Dakar" to "Africa/Abidjan",
    "Africa/Freetown" to "Africa/Abidjan",
    "Africa/Lome" to "Africa/Abidjan",
    "Africa/Nouakchott" to "Africa/Abidjan",
    "Africa/Ouagadougou" to "Africa/Abidjan",
    "Atlantic/St_Helena" to "Africa/Abidjan",
    "Africa/Maseru" to "Africa/Johannesburg",
    "Africa/Mbabane" to "Africa/Johannesburg",
    "Africa/Bangui" to "Africa/Lagos",
    "Africa/Brazzaville" to "Africa/Lagos",
    "Africa/Douala" to "Africa/Lagos",
    "Africa/Kinshasa" to "Africa/Lagos",
    "Africa/Libreville" to "Africa/Lagos",
    "Africa/Luanda" to "Africa/Lagos",
    "Africa/Malabo" to "Africa/Lagos",
    "Africa/Niamey" to "Africa/Lagos",
    "Africa/Porto-Novo" to "Africa/Lagos",
    "Africa/Blantyre" to "Africa/Maputo",
    "Africa/Bujumbura" to "Africa/Maputo",
    "Africa/Gaborone" to "Africa/Maputo",
    "Africa/Harare" to "Africa/Maputo",
    "Africa/Kigali" to "Africa/Maputo",
    "Africa/Lubumbashi" to "Africa/Maputo",
    "Africa/Lusaka" to "Africa/Maputo",
    "Africa/Addis_Ababa" to "Africa/Nairobi",
    "Africa/Asmara" to "Africa/Nairobi",
    "Africa/Dar_es_Salaam" to "Africa/Nairobi",
    "Africa/Djibouti" to "Africa/Nairobi",
    "Africa/Kampala" to "Africa/Nairobi",
    "Africa/Mogadishu" to "Africa/Nairobi",
    "Indian/Antananarivo" to "Africa/Nairobi",
    "Indian/Comoro" to "Africa/Nairobi",
    "Indian/Mayotte" to "Africa/Nairobi",
    "America/Aruba" to "America/Curacao",
    "America/Kralendijk" to "America/Curacao",
    "America/Lower_Princes" to "America/Curacao",
    "America/Cayman" to "America/Panama",
    "America/Anguilla" to "America/Port_of_Spain",
    "America/Antigua" to "America/Port_of_Spain",
    "America/Dominica" to "America/Port_of_Spain",
    "America/Grenada" to "America/Port_of_Spain",
    "America/Guadeloupe" to "America/Port_of_Spain",
    "America/Marigot" to "America/Port_of_Spain",
    "America/Montserrat" to "America/Port_of_Spain",
    "America/St_Barthelemy" to "America/Port_of_Spain",
    "America/St_Kitts" to "America/Port_of_Spain",
    "America/St_Lucia" to "America/Port_of_Spain",
    "America/St_Thomas" to "America/Port_of_Spain",
    "America/St_Vincent" to "America/Port_of_Spain",
    "America/Tortola" to "America/Port_of_Spain",
    "America/Indiana/Indianapolis" to "America/New_York",
    "America/Nuuk" to "Atlantic/Stanley",
    "Asia/Phnom_Penh" to "Asia/Bangkok",
    "Asia/Vientiane" to "Asia/Bangkok",
    "Asia/Muscat" to "Asia/Dubai",
    "Asia/Singapore" to "Asia/Shanghai",
    "Asia/Yangon" to "Indian/Cocos",
    "Europe/Nicosia" to "Asia/Nicosia",
    "Asia/Bahrain" to "Asia/Qatar",
    "Asia/Aden" to "Asia/Riyadh",
    "Asia/Kuwait" to "Asia/Riyadh",
    "Europe/Ljubljana" to "Europe/Belgrade",
    "Europe/Podgorica" to "Europe/Belgrade",
    "Europe/Sarajevo" to "Europe/Belgrade",
    "Europe/Skopje" to "Europe/Belgrade",
    "Europe/Zagreb" to "Europe/Belgrade",
    "Europe/Mariehamn" to "Europe/Helsinki",
    "Asia/Istanbul" to "Europe/Istanbul",
    "Europe/Guernsey" to "Europe/London",
    "Europe/Isle_of_Man" to "Europe/London",
    "Europe/Jersey" to "Europe/London",
    "Arctic/Longyearbyen" to "Europe/Oslo",
    "Europe/Bratislava" to "Europe/Prague",
    "Europe/San_Marino" to "Europe/Rome",
    "Europe/Vatican" to "Europe/Rome",
    "Europe/Busingen" to "Europe/Zurich",
    "Europe/Vaduz" to "Europe/Zurich",
    "Etc/GMT+0" to "UTC",
    "Etc/GMT-0" to "UTC",
    "Etc/GMT0" to "UTC",
    "GMT" to "UTC",
    "Antarctica/McMurdo" to "Pacific/Auckland",
    "Pacific/Saipan" to "Pacific/Guam",
    "Pacific/Midway" to "Pacific/Pago_Pago",
    "Pacific/Funafuti" to "Asia/Kamchatka",
    "Pacific/Wake" to "Asia/Kamchatka",
    "Pacific/Wallis" to "Asia/Kamchatka"
)

val windowsTimeZoneMap: Map<String, String> = mapOf(
    "abu dhabi, muscat" to "Asia/Dubai",
    "acre" to "America/Rio_Branco",
    "adelaide, central australia" to "Australia/Adelaide",
    "afghanistan" to "Asia/Kabul",
    "afghanistan standard time" to "Asia/Kabul",
    "africa central" to "Africa/Maputo",
    "africa eastern" to "Africa/Nairobi",
    "africa farwestern" to "Africa/El_Aaiun",
    "africa southern" to "Africa/Johannesburg",
    "africa western" to "Africa/Lagos",
    "aktyubinsk" to "Asia/Aqtobe",
    "alaska" to "America/Anchorage",
    "alaska hawaii" to "America/Anchorage",
    "alaskan" to "America/Anchorage",
    "alaskan standard time" to "America/Anchorage",
    "aleutian standard time" to "America/Adak",
    "almaty" to "Asia/Almaty",
    "almaty, novosibirsk, north central asia" to "Asia/Almaty",
    "altai standard time" to "Asia/Barnaul",
    "amazon" to "America/Manaus",
    "america central" to "America/Chicago",
    "america eastern" to "America/New_York",
    "america mountain" to "America/Denver",
    "america pacific" to "America/Los_Angeles",
    "amsterdam, berlin, bern, rome, stockholm, vienna" to "Europe/Berlin",
    "anadyr" to "Asia/Anadyr",
    "apia" to "Pacific/Apia",
    "aqtau" to "Asia/Aqtau",
    "aqtobe" to "Asia/Aqtobe",
    "arab" to "Asia/Riyadh",
    "arab standard time" to "Asia/Riyadh",
    "arab, kuwait, riyadh" to "Asia/Riyadh",
    "arabian" to "Asia/Dubai",
    "arabian standard time" to "Asia/Dubai",
    "arabic" to "Asia/Baghdad",
    "arabic standard time" to "Asia/Baghdad",
    "argentina" to "America/Argentina/Buenos_Aires",
    "argentina standard time" to "America/Argentina/Buenos_Aires",
    "argentina western" to "America/Argentina/San_Luis",
    "arizona" to "America/Phoenix",
    "armenia" to "Asia/Yerevan",
    "armenian" to "Asia/Yerevan",
    "armenian standard time" to "Asia/Yerevan",
    "ashkhabad" to "Asia/Ashgabat",
    "astana, dhaka" to "Asia/Dhaka",
    "astrakhan standard time" to "Europe/Astrakhan",
    "athens, istanbul, minsk" to "Europe/Athens",
    "atlantic" to "America/Halifax",
    "atlantic standard time" to "America/Halifax",
    "atlantic time (canada)" to "America/Halifax",
    "auckland, wellington" to "Pacific/Auckland",
    "aus central" to "Australia/Darwin",
    "aus central standard time" to "Australia/Darwin",
    "aus central w standard time" to "Australia/Eucla",
    "aus eastern" to "Australia/Sydney",
    "aus eastern standard time" to "Australia/Sydney",
    "australia central" to "Australia/Adelaide",
    "australia centralwestern" to "Australia/Eucla",
    "australia eastern" to "Australia/Sydney",
    "australia western" to "Australia/Perth",
    "azerbaijan" to "Asia/Baku",
    "azerbaijan standard time" to "Asia/Baku",
    "azerbijan" to "Asia/Baku",
    "azores" to "Atlantic/Azores",
    "azores standard time" to "Atlantic/Azores",
    "baghdad" to "Asia/Baghdad",
    "bahia standard time" to "America/Bahia",
    "baku" to "Asia/Baku",
    "baku, tbilisi, yerevan" to "Asia/Baku",
    "bangkok, hanoi, jakarta" to "Asia/Bangkok",
    "bangladesh" to "Asia/Dhaka",
    "bangladesh standard time" to "Asia/Dhaka",
    "beijing, chongqing, hong kong sar, urumqi" to "Asia/Shanghai",
    "belarus standard time" to "Europe/Minsk",
    "belgrade, pozsony, budapest, ljubljana, prague" to "Europe/Prague",
    "bering" to "America/Adak",
    "bhutan" to "Asia/Thimphu",
    "bogota, lima, quito" to "America/Bogota",
    "bolivia" to "America/La_Paz",
    "borneo" to "Asia/Kuching",
    "bougainville standard time" to "Pacific/Bougainville",
    "brasilia" to "America/Sao_Paulo",
    "brisbane, east australia" to "Australia/Brisbane",
    "british" to "Europe/London",
    "brunei" to "Asia/Brunei",
    "brussels, copenhagen, madrid, paris" to "Europe/Paris",
    "bucharest" to "Europe/Bucharest",
    "buenos aires" to "America/Argentina/Buenos_Aires",
    "cairo" to "Africa/Cairo",
    "canada central" to "America/Edmonton",
    "canada central standard time" to "America/Regina",
    "canberra, melbourne, sydney, hobart (year 2000 only)" to "Australia/Sydney",
    "cape verde" to "Atlantic/Cape_Verde",
    "cape verde is" to "Atlantic/Cape_Verde",
    "cape verde standard time" to "Atlantic/Cape_Verde",
    "caracas, la paz" to "America/Caracas",
    "casablanca, monrovia" to "Africa/Casablanca",
    "casey" to "Antarctica/Casey",
    "caucasus" to "Asia/Yerevan",
    "caucasus standard time" to "Asia/Yerevan",
    "cen australia" to "Australia/Adelaide",
    "cen australia standard time" to "Australia/Adelaide",
    "central" to "America/Chicago",
    "central america" to "America/Guatemala",
    "central america standard time" to "America/Guatemala",
    "central asia" to "Asia/Dhaka",
    "central asia standard time" to "Asia/Almaty",
    "central brazilian" to "America/Manaus",
    "central brazilian standard time" to "America/Cuiaba",
    "central europe" to "Europe/Prague",
    "central europe standard time" to "Europe/Budapest",
    "central european" to "Europe/Belgrade",
    "central european standard time" to "Europe/Warsaw",
    "central pacific" to "Asia/Magadan",
    "central pacific standard time" to "Pacific/Guadalcanal",
    "central standard time" to "America/Chicago",
    "central standard time (mexico)" to "America/Mexico_City",
    "central time (us & canada)" to "America/Chicago",
    "chamorro" to "Pacific/Guam",
    "chatham" to "Pacific/Chatham",
    "chatham islands standard time" to "Pacific/Chatham",
    "chile" to "America/Santiago",
    "china" to "Asia/Shanghai",
    "china standard time" to "Asia/Shanghai",
    "choibalsan" to "Asia/Choibalsan",
    "christmas" to "Indian/Christmas",
    "cocos" to "Indian/Cocos",
    "colombia" to "America/Bogota",
    "cook" to "Pacific/Rarotonga",
    "cuba" to "America/Havana",
    "cuba standard time" to "America/Havana",
    "dacca" to "Asia/Dhaka",
    "darwin" to "Australia/Darwin",
    "dateline" to "Pacific/Auckland",
    "dateline standard time" to "Pacific/Niue",
    "davis" to "Antarctica/Davis",
    "dominican" to "America/Santo_Domingo",
    "dumontdurville" to "Antarctica/DumontDUrville",
    "dushanbe" to "Asia/Dushanbe",
    "dutch guiana" to "America/Paramaribo",
    "e africa" to "Africa/Nairobi",
    "e africa standard time" to "Africa/Nairobi",
    "e australia" to "Australia/Brisbane",
    "e australia standard time" to "Australia/Brisbane",
    "e europe" to "Europe/Minsk",
    "e europe standard time" to "Europe/Chisinau",
    "e south america" to "America/Belem",
    "e south america standard time" to "America/Sao_Paulo",
    "east africa, nairobi" to "Africa/Nairobi",
    "east timor" to "Asia/Dili",
    "easter" to "Pacific/Easter",
    "easter island standard time" to "Pacific/Easter",
    "eastern" to "America/New_York",
    "eastern standard time" to "America/New_York",
    "eastern standard time (mexico)" to "America/Cancun",
    "eastern time (us & canada)" to "America/New_York",
    "ecuador" to "America/Guayaquil",
    "egypt" to "Africa/Cairo",
    "egypt standard time" to "Africa/Cairo",
    "ekaterinburg" to "Asia/Yekaterinburg",
    "ekaterinburg standard time" to "Asia/Yekaterinburg",
    "eniwetok, kwajalein, dateline time" to "Pacific/Kwajalein",
    "europe central" to "Europe/Paris",
    "europe eastern" to "Europe/Bucharest",
    "europe further eastern" to "Europe/Minsk",
    "europe western" to "Atlantic/Canary",
    "falkland" to "Atlantic/Stanley",
    "fiji" to "Pacific/Fiji",
    "fiji islands standard time" to "Pacific/Fiji",
    "fiji islands, kamchatka, marshall is" to "Pacific/Fiji",
    "fiji standard time" to "Pacific/Fiji",
    "fle" to "Europe/Helsinki",
    "fle standard time" to "Europe/Kyiv",
    "french guiana" to "America/Cayenne",
    "french southern" to "Indian/Kerguelen",
    "frunze" to "Asia/Bishkek",
    "galapagos" to "Pacific/Galapagos",
    "gambier" to "Pacific/Gambier",
    "georgia" to "Asia/Tbilisi",
    "georgian" to "Asia/Tbilisi",
    "georgian standard time" to "Asia/Tbilisi",
    "gilbert islands" to "Pacific/Tarawa",
    "gmt" to "Europe/London",
    "gmt standard time" to "Europe/London",
    "goose bay" to "America/Goose_Bay",
    "greenland" to "America/Godthab",
    "greenland central" to "America/Scoresbysund",
    "greenland eastern" to "America/Scoresbysund",
    "greenland standard time" to "America/Godthab",
    "greenland western" to "America/Godthab",
    "greenwich" to "Atlantic/Reykjavik",
    "greenwich mean time; dublin, edinburgh, london" to "Europe/London",
    "greenwich mean time: dublin, edinburgh, lisbon, london" to "Europe/Lisbon",
    "greenwich standard time" to "Atlantic/Reykjavik",
    "gtb" to "Europe/Athens",
    "gtb standard time" to "Europe/Bucharest",
    "guam" to "Pacific/Guam",
    "guam, port moresby" to "Pacific/Guam",
    "gulf" to "Asia/Dubai",
    "guyana" to "America/Guyana",
    "haiti standard time" to "America/Port-au-Prince",
    "harare, pretoria" to "Africa/Maputo",
    "hawaii" to "Pacific/Honolulu",
    "hawaii aleutian" to "Pacific/Honolulu",
    "hawaiian" to "Pacific/Honolulu",
    "hawaiian standard time" to "Pacific/Honolulu",
    "helsinki, riga, tallinn" to "Europe/Helsinki",
    "hobart, tasmania" to "Australia/Hobart",
    "hong kong" to "Asia/Hong_Kong",
    "hovd" to "Asia/Hovd",
    "india" to "Asia/Kolkata",
    "india standard time" to "Asia/Kolkata",
    "indian ocean" to "Indian/Chagos",
    "indiana (east)" to "America/New_York",
    "indochina" to "Asia/Bangkok",
    "indonesia central" to "Asia/Makassar",
    "indonesia eastern" to "Asia/Jayapura",
    "indonesia western" to "Asia/Jakarta",
    "iran" to "Asia/Tehran",
    "iran standard time" to "Asia/Tehran",
    "irish" to "Europe/Dublin",
    "irkutsk" to "Asia/Irkutsk",
    "irkutsk, ulaan bataar" to "Asia/Irkutsk",
    "islamabad, karachi, tashkent" to "Asia/Karachi",
    "israel" to "Asia/Jerusalem",
    "israel standard time" to "Asia/Jerusalem",
    "israel, jerusalem standard time" to "Asia/Jerusalem",
    "japan" to "Asia/Tokyo",
    "jordan" to "Asia/Amman",
    "jordan standard time" to "Asia/Amman",
    "kabul" to "Asia/Kabul",
    "kaliningrad standard time" to "Europe/Kaliningrad",
    "kamchatka" to "Asia/Kamchatka",
    "kamchatka standard time" to "Asia/Kamchatka",
    "karachi" to "Asia/Karachi",
    "kathmandu, nepal" to "Asia/Kathmandu",
    "kazakhstan eastern" to "Asia/Almaty",
    "kazakhstan western" to "Asia/Aqtobe",
    "kizilorda" to "Asia/Qyzylorda",
    "kolkata, chennai, mumbai, new delhi, india standard time" to "Asia/Kolkata",
    "korea" to "Asia/Seoul",
    "korea standard time" to "Asia/Seoul",
    "kosrae" to "Pacific/Kosrae",
    "krasnoyarsk" to "Asia/Krasnoyarsk",
    "kuala lumpur, singapore" to "Asia/Shanghai",
    "kuybyshev" to "Europe/Samara",
    "kwajalein" to "Pacific/Kwajalein",
    "kyrgystan" to "Asia/Bishkek",
    "lanka" to "Asia/Colombo",
    "liberia" to "Africa/Monrovia",
    "libya standard time" to "Africa/Tripoli",
    "line islands" to "Pacific/Kiritimati",
    "line islands standard time" to "Pacific/Kiritimati",
    "lord howe" to "Australia/Lord_Howe",
    "lord howe standard time" to "Australia/Lord_Howe",
    "macau" to "Asia/Macau",
    "macquarie" to "Antarctica/Macquarie",
    "magadan" to "Asia/Magadan",
    "magadan standard time" to "Asia/Magadan",
    "magadan, solomon is, new caledonia" to "Asia/Magadan",
    "magallanes standard time" to "America/Punta_Arenas",
    "malaya" to "Asia/Kuala_Lumpur",
    "malaysia" to "Asia/Kuching",
    "maldives" to "Indian/Maldives",
    "marquesas" to "Pacific/Marquesas",
    "marquesas standard time" to "Pacific/Marquesas",
    "marshall islands" to "Pacific/Majuro",
    "mauritius" to "Indian/Mauritius",
    "mauritius standard time" to "Indian/Mauritius",
    "mawson" to "Antarctica/Mawson",
    "mexico" to "America/Mexico_City",
    "mexico city, tegucigalpa" to "America/Mexico_City",
    "mexico pacific" to "America/Mazatlan",
    "mexico standard time" to "America/Mexico_City",
    "mexico standard time 2" to "America/Chihuahua",
    "mid-atlantic" to "America/Noronha",
    "mid-atlantic standard time" to "Atlantic/Cape_Verde",
    "middle east" to "Asia/Beirut",
    "middle east standard time" to "Asia/Beirut",
    "midway island, samoa" to "Pacific/Pago_Pago",
    "mongolia" to "Asia/Ulaanbaatar",
    "montevideo" to "America/Montevideo",
    "montevideo standard time" to "America/Montevideo",
    "morocco" to "Africa/Casablanca",
    "morocco standard time" to "Africa/Casablanca",
    "moscow" to "Europe/Moscow",
    "moscow, st petersburg, volgograd" to "Europe/Moscow",
    "mountain" to "America/Denver",
    "mountain standard time" to "America/Denver",
    "mountain standard time (mexico)" to "America/Chihuahua",
    "mountain time (us & canada)" to "America/Denver",
    "myanmar" to "Indian/Cocos",
    "myanmar standard time" to "Indian/Cocos",
    "n central asia" to "Asia/Almaty",
    "n central asia standard time" to "Asia/Novosibirsk",
    "namibia" to "Africa/Windhoek",
    "namibia standard time" to "Africa/Windhoek",
    "nauru" to "Pacific/Nauru",
    "nepal" to "Asia/Kathmandu",
    "nepal standard time" to "Asia/Kathmandu",
    "new caledonia" to "Pacific/Noumea",
    "new zealand" to "Pacific/Auckland",
    "new zealand standard time" to "Pacific/Auckland",
    "newfoundland" to "America/St_Johns",
    "newfoundland and labrador standard time" to "America/St_Johns",
    "newfoundland standard time" to "America/St_Johns",
    "niue" to "Pacific/Niue",
    "norfolk" to "Pacific/Norfolk",
    "norfolk standard time" to "Pacific/Norfolk",
    "noronha" to "America/Noronha",
    "north asia" to "Asia/Krasnoyarsk",
    "north asia east" to "Asia/Irkutsk",
    "north asia east standard time" to "Asia/Irkutsk",
    "north asia standard time" to "Asia/Krasnoyarsk",
    "north korea standard time" to "Asia/Pyongyang",
    "north mariana" to "Pacific/Guam",
    "novosibirsk" to "Asia/Novosibirsk",
    "nuku'alofa, tonga" to "Pacific/Tongatapu",
    "omsk" to "Asia/Omsk",
    "omsk standard time" to "Asia/Omsk",
    "oral" to "Asia/Oral",
    "osaka, sapporo, tokyo" to "Asia/Tokyo",
    "pacific" to "America/Los_Angeles",
    "pacific sa" to "America/Santiago",
    "pacific sa standard time" to "America/Santiago",
    "pacific standard time" to "America/Los_Angeles",
    "pacific standard time (mexico)" to "America/Tijuana",
    "pacific time (us & canada)" to "America/Los_Angeles",
    "pacific time (us & canada); tijuana" to "America/Los_Angeles",
    "pakistan" to "Asia/Karachi",
    "pakistan standard time" to "Asia/Karachi",
    "palau" to "Pacific/Palau",
    "papua new guinea" to "Pacific/Port_Moresby",
    "paraguay" to "America/Asuncion",
    "paraguay standard time" to "America/Asuncion",
    "paris, madrid, brussels, copenhagen" to "Europe/Paris",
    "perth, western australia" to "Australia/Perth",
    "peru" to "America/Lima",
    "philippines" to "Asia/Manila",
    "phoenix islands" to "Pacific/Fakaofo",
    "pierre miquelon" to "America/Miquelon",
    "pitcairn" to "Pacific/Pitcairn",
    "prague, central europe" to "Europe/Prague",
    "pyongyang" to "Asia/Pyongyang",
    "qyzylorda" to "Asia/Qyzylorda",
    "qyzylorda standard time" to "Asia/Qyzylorda",
    "rangoon" to "Indian/Cocos",
    "reunion" to "Indian/Reunion",
    "romance" to "Europe/Paris",
    "romance standard time" to "Europe/Paris",
    "rothera" to "Antarctica/Rothera",
    "russia time zone 10" to "Asia/Srednekolymsk",
    "russia time zone 11" to "Asia/Kamchatka",
    "russia time zone 3" to "Europe/Samara",
    "russian" to "Europe/Moscow",
    "russian standard time" to "Europe/Moscow",
    "sa eastern" to "America/Belem",
    "sa eastern standard time" to "America/Cayenne",
    "sa pacific" to "America/Bogota",
    "sa pacific standard time" to "America/Bogota",
    "sa western" to "America/La_Paz",
    "sa western standard time" to "America/La_Paz",
    "saint pierre standard time" to "America/Miquelon",
    "sakhalin" to "Asia/Sakhalin",
    "sakhalin standard time" to "Asia/Sakhalin",
    "samara" to "Europe/Samara",
    "samarkand" to "Asia/Samarkand",
    "samoa" to "Pacific/Apia",
    "samoa standard time" to "Pacific/Apia",
    "santiago" to "America/Santiago",
    "sao tome standard time" to "Africa/Sao_Tome",
    "sarajevo, skopje, sofija, vilnius, warsaw, zagreb" to "Europe/Belgrade",
    "saratov standard time" to "Europe/Saratov",
    "saskatchewan" to "America/Edmonton",
    "se asia" to "Asia/Bangkok",
    "se asia standard time" to "Asia/Bangkok",
    "seoul, korea standard time" to "Asia/Seoul",
    "seychelles" to "Indian/Mahe",
    "shevchenko" to "Asia/Aqtau",
    "singapore" to "Asia/Shanghai",
    "singapore standard time" to "Asia/Shanghai",
    "solomon" to "Pacific/Guadalcanal",
    "south africa" to "Africa/Maputo",
    "south africa standard time" to "Africa/Johannesburg",
    "south georgia" to "Atlantic/South_Georgia",
    "sri jayawardenepura, sri lanka" to "Asia/Colombo",
    "sri lanka" to "Asia/Colombo",
    "sri lanka standard time" to "Asia/Colombo",
    "sudan standard time" to "Africa/Khartoum",
    "suriname" to "America/Paramaribo",
    "sverdlovsk" to "Asia/Yekaterinburg",
    "syowa" to "Antarctica/Syowa",
    "syria standard time" to "Asia/Damascus",
    "tahiti" to "Pacific/Tahiti",
    "taipei" to "Asia/Taipei",
    "taipei standard time" to "Asia/Taipei",
    "tajikistan" to "Asia/Dushanbe",
    "tashkent" to "Asia/Tashkent",
    "tasmania" to "Australia/Hobart",
    "tasmania standard time" to "Australia/Hobart",
    "tbilisi" to "Asia/Tbilisi",
    "tehran" to "Asia/Tehran",
    "tocantins standard time" to "America/Araguaina",
    "tokelau" to "Pacific/Fakaofo",
    "tokyo" to "Asia/Tokyo",
    "tokyo standard time" to "Asia/Tokyo",
    "tomsk standard time" to "Asia/Tomsk",
    "tonga" to "Pacific/Tongatapu",
    "tonga standard time" to "Pacific/Tongatapu",
    "transbaikal standard time" to "Asia/Chita",
    "transitional islamic state of afghanistan standard time" to "Asia/Kabul",
    "turkey" to "Europe/Istanbul",
    "turkey standard time" to "Europe/Istanbul",
    "turkmenistan" to "Asia/Ashgabat",
    "turks and caicos standard time" to "America/Grand_Turk",
    "tuvalu" to "Asia/Kamchatka",
    "ulaanbaatar standard time" to "Asia/Ulaanbaatar",
    "universal coordinated time" to "UTC",
    "uralsk" to "Asia/Oral",
    "uruguay" to "America/Montevideo",
    "urumqi" to "Asia/Urumqi",
    "us eastern" to "America/New_York",
    "us eastern standard time" to "America/New_York",
    "us mountain" to "America/Phoenix",
    "us mountain standard time" to "America/Phoenix",
    "utc-02" to "America/Noronha",
    "utc-08" to "Pacific/Pitcairn",
    "utc-09" to "Pacific/Gambier",
    "utc-11" to "Pacific/Niue",
    "utc+12" to "Pacific/Auckland",
    "uzbekistan" to "Asia/Tashkent",
    "vanuatu" to "Pacific/Efate",
    "venezuela" to "America/Caracas",
    "venezuela standard time" to "America/Caracas",
    "vladivostok" to "Asia/Vladivostok",
    "vladivostok standard time" to "Asia/Vladivostok",
    "volgograd" to "Europe/Volgograd",
    "volgograd standard time" to "Europe/Volgograd",
    "vostok" to "Antarctica/Vostok",
    "w australia" to "Australia/Perth",
    "w australia standard time" to "Australia/Perth",
    "w central africa" to "Africa/Lagos",
    "w central africa standard time" to "Africa/Lagos",
    "w europe" to "Europe/Amsterdam",
    "w europe standard time" to "Europe/Berlin",
    "w mongolia standard time" to "Asia/Hovd",
    "wake" to "Asia/Kamchatka",
    "wallis" to "Asia/Kamchatka",
    "west asia" to "Asia/Tashkent",
    "west asia standard time" to "Asia/Tashkent",
    "west bank standard time" to "Asia/Hebron",
    "west central africa" to "Africa/Lagos",
    "west pacific" to "Pacific/Guam",
    "west pacific standard time" to "Pacific/Port_Moresby",
    "yakutsk" to "Asia/Yakutsk",
    "yakutsk standard time" to "Asia/Yakutsk",
    "yekaterinburg" to "Asia/Yekaterinburg",
    "yerevan" to "Asia/Yerevan",
    "yukon" to "America/Yakutat",
    "coordinated universal time-11" to "Pacific/Pago_Pago",
    "aleutian islands" to "America/Adak",
    "marquesas islands" to "Pacific/Marquesas",
    "coordinated universal time-09" to "America/Anchorage",
    "baja california" to "America/Tijuana",
    "coordinated universal time-08" to "Pacific/Pitcairn",
    "chihuahua, la paz, mazatlan" to "America/Chihuahua",
    "easter island" to "Pacific/Easter",
    "guadalajara, mexico city, monterrey" to "America/Mexico_City",
    "bogota, lima, quito, rio branco" to "America/Bogota",
    "chetumal" to "America/Cancun",
    "haiti" to "America/Port-au-Prince",
    "havana" to "America/Havana",
    "turks and caicos" to "America/Grand_Turk",
    "asuncion" to "America/Asuncion",
    "caracas" to "America/Caracas",
    "cuiaba" to "America/Cuiaba",
    "georgetown, la paz, manaus, san juan" to "America/La_Paz",
    "araguaina" to "America/Araguaina",
    "cayenne, fortaleza" to "America/Cayenne",
    "city of buenos aires" to "America/Argentina/Buenos_Aires",
    "punta arenas" to "America/Punta_Arenas",
    "saint pierre and miquelon" to "America/Miquelon",
    "salvador" to "America/Bahia",
    "coordinated universal time-02" to "America/Noronha",
    "mid-atlantic - old" to "America/Noronha",
    "cabo verde is" to "Atlantic/Cape_Verde",
    "coordinated universal time" to "UTC",
    "dublin, edinburgh, lisbon, london" to "Europe/London",
    "monrovia, reykjavik" to "Atlantic/Reykjavik",
    "belgrade, bratislava, budapest, ljubljana, prague" to "Europe/Budapest",
    "casablanca" to "Africa/Casablanca",
    "sao tome" to "Africa/Sao_Tome",
    "sarajevo, skopje, warsaw, zagreb" to "Europe/Warsaw",
    "amman" to "Asia/Amman",
    "athens, bucharest" to "Europe/Bucharest",
    "beirut" to "Asia/Beirut",
    "chisinau" to "Europe/Chisinau",
    "damascus" to "Asia/Damascus",
    "gaza, hebron" to "Asia/Hebron",
    "jerusalem" to "Asia/Jerusalem",
    "kaliningrad" to "Europe/Kaliningrad",
    "khartoum" to "Africa/Khartoum",
    "tripoli" to "Africa/Tripoli",
    "windhoek" to "Africa/Windhoek",
    "istanbul" to "Europe/Istanbul",
    "kuwait, riyadh" to "Asia/Riyadh",
    "minsk" to "Europe/Minsk",
    "moscow, st petersburg" to "Europe/Moscow",
    "nairobi" to "Africa/Nairobi",
    "astrakhan, ulyanovsk" to "Europe/Astrakhan",
    "izhevsk, samara" to "Europe/Samara",
    "port louis" to "Indian/Mauritius",
    "saratov" to "Europe/Saratov",
    "ashgabat, tashkent" to "Asia/Tashkent",
    "islamabad, karachi" to "Asia/Karachi",
    "chennai, kolkata, mumbai, new delhi" to "Asia/Kolkata",
    "sri jayawardenepura" to "Asia/Colombo",
    "kathmandu" to "Asia/Kathmandu",
    "astana" to "Asia/Almaty",
    "dhaka" to "Asia/Dhaka",
    "yangon (rangoon)" to "Indian/Cocos",
    "barnaul, gorno-altaysk" to "Asia/Barnaul",
    "tomsk" to "Asia/Tomsk",
    "beijing, chongqing, hong kong, urumqi" to "Asia/Shanghai",
    "perth" to "Australia/Perth",
    "ulaanbaatar" to "Asia/Ulaanbaatar",
    "eucla" to "Australia/Eucla",
    "chita" to "Asia/Chita",
    "seoul" to "Asia/Seoul",
    "adelaide" to "Australia/Adelaide",
    "brisbane" to "Australia/Brisbane",
    "canberra, melbourne, sydney" to "Australia/Sydney",
    "hobart" to "Australia/Hobart",
    "lord howe island" to "Australia/Lord_Howe",
    "bougainville island" to "Pacific/Bougainville",
    "chokurdakh" to "Asia/Srednekolymsk",
    "norfolk island" to "Pacific/Norfolk",
    "solomon is, new caledonia" to "Pacific/Guadalcanal",
    "anadyr, petropavlovsk-kamchatsky" to "Asia/Kamchatka",
    "coordinated universal time+12" to "Pacific/Tarawa",
    "petropavlovsk-kamchatsky - old" to "Asia/Anadyr",
    "chatham islands" to "Pacific/Chatham",
    "coordinated universal time+13" to "Pacific/Fakaofo",
    "utc+13" to "Pacific/Fakaofo",
    "nuku'alofa" to "Pacific/Tongatapu",
    "kiritimati island" to "Pacific/Kiritimati",
    "helsinki, kyiv, riga, sofia, tallinn, vilnius" to "Europe/Helsinki"
)
