package me.proton.android.calendar.domain.usecase

import androidx.work.WorkManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.proton.android.calendar.common.utils.AndroidUtils.tryCastOrNull
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.fallbackTimeZone
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.Duration
import java.util.TimeZone
import javax.inject.Inject

class BootstrapAllCalendarsUseCase @Inject constructor( // TODO TEST
    private val logger: Logger,
    private val boostrapCalendarUseCase: BootstrapCalendarUseCase,
    private val calendarsApi: CalendarsApi,
    private val settingsApi: SettingsApi,
    private val calendarsRepository: CalendarsRepository,
    private val createCalendarUseCase: CreateCalendarUseCase,
    private val scheduleSyncAlarmsUseCase: ScheduleSyncAlarmsUseCase,
    private val keySetupUseCase: KeySetupUseCase,
    private val reactivateCalendarKeyUseCase: ReactivateCalendarKeyUseCase,
    private val userAddressManager: UserAddressManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val refreshCalendarUserSettingsUseCase: RefreshCalendarUserSettingsUseCase,
    private val joinCalendarUseCase: JoinCalendarUseCase,
    private val workManager: WorkManager,
    private val featureFlagManager: FeatureFlagManager
): UseCase {

    suspend fun execute(
        userId: UserId,
        showConfirmationDialog: Boolean,
        defaultCalendarName: String,
        defaultCalendarColor: String,
        defaultHolidayCalendarColor: String,
        defaultLanguageCode: String?,
        defaultCountryCode: String?
    ): UseCase.Result {

        logger.v("executing BootstrapAllCalendarsUseCase")

        var allCalendarEntities = calendarsRepository.fetchCalendarEntities(userId) ?: return UseCase.Result.Error("BootstrapAllCalendarsUseCase: error getting Calendar Entities from API")
        var allCalendars = calendarsRepository.fetchMembersToCalendarEntities(userId, allCalendarEntities)
        val userCalendars = allCalendars.filterNot { it.isSubscribed }

        if (allCalendars.isNotEmpty() && allCalendars.any { it.isResetNeeded }) {
            // Always show confirmation dialog if a calendar has flag RESET_NEEDED
            return UseCase.Result.Error(
                "BootstrapAllCalendarsUseCase: error reset needed for calendar",
                UseCase.Error.Bootstrap.ResetNeeded
            )
        } else if (userCalendars.isNotEmpty() &&
            userCalendars.firstOrNull { it.isActive || it.isDisabled || it.hasIncompleteKeySetup || it.hasUpdatePassphrase } == null
        ) {
            return UseCase.Result.Error(
                "BootstrapAllCalendarsUseCase: error user has no active calendar",
                UseCase.Error.Bootstrap.NoActiveCalendar
            )
        }

        var redoGetCalendars = false

        // We need to create a default calendar if user has none or has only subscribed or shared calendars
        var ownedUserCalendars = userCalendars.filter { it.isOwner }
        if (ownedUserCalendars.isEmpty()) {

            val createDefaultCalendarResult = createCalendarUseCase.execute(
                userId,
                defaultCalendarName,
                color = defaultCalendarColor
            )

            createDefaultCalendarResult.ifSuccessAndLogErrors(logger) {
                // Only update user primary timezone if we just created the first calendar
                when (val updateCalendarUserPrimaryTimezoneResponse =
                    settingsApi.updateCalendarUserPrimaryTimezone(
                        userId,
                        fallbackTimeZone(TimeZone.getDefault().id, fallbackToDefault = true)!!
                    )) {
                    is ApiResponse.Error -> logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                    is ApiResponse.Exception -> logger.e("api error updating user timezone: $updateCalendarUserPrimaryTimezoneResponse")
                    is ApiResponse.Success -> Unit
                }
            }

            if (createDefaultCalendarResult !is UseCase.Result.Success<*>) {
                logger.e("BootstrapAllCalendarsUseCase: error unable to create default calendar for user")
                return UseCase.Result.Error("BootstrapAllCalendarsUseCase: error unable to create default calendar for user")
            }

            // Make sure user doesn't have any holiday calendar already
            if (!allCalendarEntities.any { it.isHolidayCalendar }
                && !defaultLanguageCode.isNullOrEmpty()
                && defaultCountryCode != null) {

                // Fetch the holiday calendar feature flag value
                val featureIds = CalendarFeatureFlag.values().filter { !it.isLocalFlag }.map { it.featureId }.toSet()
                featureFlagManager.prefetch(userId, featureIds)

                val primaryTimeZone = fallbackTimeZone(TimeZone.getDefault().id, fallbackToDefault = true)!!
                calendarsRepository.refreshVisibleManagedHolidayCalendars(userId)?.let { holidayCalendars ->
                    // Get calendars matching the default time zone
                    val matchingDefaultHolidayCalendar = ProtonUtilsImpl.getMatchingDefaultHolidayCalendar(
                        holidayCalendars,
                        primaryTimeZone,
                        defaultLanguageCode,
                        defaultCountryCode
                    )

                    // If holiday calendar already exists, leave the fields empty
                    matchingDefaultHolidayCalendar?.let { holidayCalendar ->
                        val joinCalendarResult = joinCalendarUseCase.joinHolidayCalendar(
                            userId,
                            holidayCalendar,
                            defaultHolidayCalendarColor,
                            arrayListOf()
                        )

                        if (joinCalendarResult !is UseCase.Result.Success<*>) {
                            logger.e("BootstrapAllCalendarsUseCase: error unable to join holiday calendar for user")
                        }
                    }
                } ?: run {
                    logger.e("BootstrapAllCalendarsUseCase: error failed to fetch holiday calendars for user")
                }
            }

            redoGetCalendars = true
        }

        // We fix both normal and subscribed calendars
        val addresses =
            if (allCalendars.any { it.hasIncompleteKeySetup || it.hasUpdatePassphrase }) {
                // Fetch the user addresses only once if we need to do key setup or reactivate calendar keys
                userAddressManager.getAddressesOrNull(userId)
            } else null
        allCalendars.forEach {
            if (it.hasIncompleteKeySetup) {
                // Handle flag INCOMPLETE_SETUP
                val keySetupResult = keySetupUseCase.execute(userId, it.id, addresses)

                keySetupResult.ifSuccessAndLogErrors(logger) { }

                redoGetCalendars = true
            }

            if (it.hasUpdatePassphrase) {
                // Handle flag UPDATE_PASSPHRASE

                // Skip confirmation dialog if we just handled flag RESET_NEEDED
                if (showConfirmationDialog) return UseCase.Result.Error(
                    "BootstrapAllCalendarsUseCase: error update passphrase for calendar",
                    UseCase.Error.Bootstrap.UpdatePassphrase
                )

                val reactivateCalendarKeyResult = reactivateCalendarKeyUseCase.execute(userId, it.id, addresses)

                reactivateCalendarKeyResult.ifSuccessAndLogErrors(logger) { }

                redoGetCalendars = true
            }
        }

        if (redoGetCalendars) {
            // GET the calendar(entities) list again after creating default one or fixing incomplete setup
            allCalendarEntities = calendarsRepository.fetchCalendarEntities(userId) ?: return UseCase.Result.Error("BootstrapAllCalendarsUseCase: error getting Calendar Entities from API in redoGetCalendars")
            allCalendars = calendarsRepository.fetchMembersToCalendarEntities(userId, allCalendarEntities)

            // Subscribed calendars do not count for those checks
            ownedUserCalendars = allCalendars.filterNot { it.isSubscribed && it.isOwner.not() }
            if (ownedUserCalendars.isEmpty()) {
                logger.e("BootstrapAllCalendarsUseCase: still no calendar after creating default calendar")
                return UseCase.Result.Error(
                    "BootstrapAllCalendarsUseCase: error user has no calendar",
                    UseCase.Error.Bootstrap.NoCalendar
                )
            } else if (ownedUserCalendars.none { it.isActive || it.isDisabled }) { // If has no active or disabled cals
                logger.e("BootstrapAllCalendarsUseCase: still no active calendar after creating default calendar")
                return UseCase.Result.Error(
                    "BootstrapAllCalendarsUseCase: error user has no active calendar",
                    UseCase.Error.Bootstrap.NoActiveCalendar
                )
            }
        }

        val refreshedCalendarUserSettingsResult = refreshCalendarUserSettingsUseCase(userId)
        val refreshedCalendarUserSettings = if (refreshedCalendarUserSettingsResult is UseCase.Result.Success<*>) {
            refreshedCalendarUserSettingsResult.returnValue.tryCastOrNull<CalendarUserSettingsEntity>()
        } else null

        if (refreshedCalendarUserSettings == null) {
            refreshedCalendarUserSettingsResult.ifSuccessAndLogErrors(logger) {}
            return UseCase.Result.Error("BootstrapAllCalendarsUseCase: error getting calendar user settings from API")
        }

        val userSettingsResponseException =
            kotlin.runCatching { userSettingsRepository.getUserSettings(userId, refresh = true) }.exceptionOrNull()
        if (userSettingsResponseException != null) {
            logger.e(
                "BootstrapAllCalendarsUseCase: error getting user settings from API: ${userSettingsResponseException.message}",
                userSettingsResponseException
            )
            return UseCase.Result.Error("BootstrapAllCalendarsUseCase: error getting user settings from API: $userSettingsResponseException")
        }

        val failedCalendarIds = mutableListOf<String>()

        // TODO For now we ignore the inactive calendars where user is not an owner
        coroutineScope {
            allCalendars.filterNot { !it.isOwner && it.isInactive }.map { calendar ->
                async {
                    val calendarEntity = allCalendarEntities.find { it.id == calendar.id }

                    val executeBootstrapResult = if (calendarEntity != null) {
                        boostrapCalendarUseCase.executeBootstrap(
                            calendarEntity,
                            userId,
                            addresses
                        )
                    } else UseCase.Result.InvalidParams("could not select CalendarEntity for executeBootstrapResult")

                    executeBootstrapResult.ifSuccessAndLogErrors(logger) { }
                    if (executeBootstrapResult !is UseCase.Result.Success<*>) {
                        failedCalendarIds.add(calendar.id)

                        val failReason = if (executeBootstrapResult is UseCase.Result.Error) {
                            "${executeBootstrapResult.message} + ${executeBootstrapResult.error}"
                        } else if (executeBootstrapResult is UseCase.Result.InvalidParams) {
                            executeBootstrapResult.message
                        } else null

                        logger.e("calendar ${calendar.id} failed bootstrap: ${failReason}")
                    }
                }
            }.awaitAll()
        }

        failedCalendarIds.forEach {
            calendarsRepository.deleteCalendarById(it)
            // foreign keys on Calendar ID will also delete:
            //  - Calendar Settings
            //  - Passphrase
            //  - CalendarKeys
            //  - Members

            // CalendarUserSettings and UserSettings will not be deleted
        }

        return if (failedCalendarIds.isNotEmpty()) {
            UseCase.Result.Error(
                "calendar bootstrap failed for: ${failedCalendarIds.joinToString(separator = ", ")}",
                UseCase.Error.Bootstrap.SomeCalendarsFailedBootstrap(failedCalendarIds)
            )
        } else {

            // sync alarms after downloading calendars and events
            scheduleSyncAlarmsUseCase.execute(userId, initialDelay = Duration.ofSeconds(10))

            UseCase.Result.Success<Unit>()
        }
    }
}
