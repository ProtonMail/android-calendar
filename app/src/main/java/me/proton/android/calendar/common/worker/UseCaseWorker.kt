package me.proton.android.calendar.common.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.proton.android.calendar.common.WORKER_MAX_RETRY_COUNT
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.usecase.BootstrapCalendarUseCase
import me.proton.android.calendar.domain.usecase.CalendarUserSettingsChangedUseCase
import me.proton.android.calendar.domain.usecase.FetchCachedViewsEventsUseCase
import me.proton.android.calendar.domain.usecase.FixCalendarsUseCase
import me.proton.android.calendar.domain.usecase.GetMinimalCalendarEventsUseCase
import me.proton.android.calendar.domain.usecase.HandleAlarmsWithMissingEventUseCase
import me.proton.android.calendar.domain.usecase.KeySetupUseCase
import me.proton.android.calendar.domain.usecase.MigrateEventMetadataToOccurrencesUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarKeysUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarPassphraseUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarSettingsUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarSubscriptionUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.RefreshMembersFlagsUseCase
import me.proton.android.calendar.domain.usecase.UpdateAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUseCase
import me.proton.android.calendar.domain.usecase.UpdateCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.UpdateParticipationStatusUseCase
import me.proton.android.calendar.domain.usecase.UpdateUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.core.domain.entity.UserId
import java.time.LocalDate

@HiltWorker
class UseCaseWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val logger: Logger,
    private val updateCalendarUseCase: UpdateCalendarUseCase,
    private val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase,
    private val updateUserSettingsUseCase: UpdateUserSettingsUseCase,
    private val updateParticipationStatusUseCase: UpdateParticipationStatusUseCase,
    private val fetchCachedViewsEventsUseCase: FetchCachedViewsEventsUseCase,
    private val fixCalendarsUseCase: FixCalendarsUseCase,
    private val bootstrapCalendarUseCase: BootstrapCalendarUseCase,
    private val keySetupUseCase: KeySetupUseCase,
    private val refreshMembersFlagsUseCase: RefreshMembersFlagsUseCase,
    private val getMinimalCalendarEventsUseCase: GetMinimalCalendarEventsUseCase,
    private val calendarUserSettingsChangedUseCase: CalendarUserSettingsChangedUseCase,
    private val refreshCalendarUserSettingsUseCase: RefreshCalendarUserSettingsUseCase,
    private val refreshCalendarSettingsUseCase: RefreshCalendarSettingsUseCase,
    private val refreshCalendarSubscriptionUseCase: RefreshCalendarSubscriptionUseCase,
    private val refreshCalendarPassphraseUseCase: RefreshCalendarPassphraseUseCase,
    private val refreshCalendarKeysUseCase: RefreshCalendarKeysUseCase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase,
    private val handleAlarmsWithMissingEventUseCase: HandleAlarmsWithMissingEventUseCase,
    private val migrateEventMetadataToOccurrencesUseCase: MigrateEventMetadataToOccurrencesUseCase,
) : CoroutineWorker(context, workerParameters) {
    /**
     * Used to inject and execute different usecases from this Worker
     */
    class UseCaseId {
        companion object {
            // const val SYNC_SERVER_EVENTS = "SYNC_SERVER_EVENTS" deprecated, don't remove this comment
            const val UPDATE_CALENDAR_LIST = UpdateCalendarUseCase.WORKER_LIST_ID
            const val UPDATE_PRIMARY_TIMEZONE = UpdateCalendarUserSettingsUseCase.WORKER_ID_TZ
            const val UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE = UpdateCalendarUserSettingsUseCase.WORKER_ID_AUTO_DETECT
            const val UPDATE_DISPLAY_WEEK_NUMBER = UpdateCalendarUserSettingsUseCase.WORKER_ID_WEEK_NUMBER
            const val UPDATE_AUTO_IMPORT_INVITE = UpdateCalendarUserSettingsUseCase.WORKER_ID_AUTO_IMPORT_INVITE
            const val UPDATE_TIME_FORMAT = UpdateUserSettingsUseCase.WORKER_ID_TIME_FORMAT
            const val UPDATE_WEEK_START = UpdateUserSettingsUseCase.WORKER_ID_WEEK_START
            const val UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT = UpdateParticipationStatusUseCase.WORKER_ID_SINGLE_EDIT
            const val FETCH_CACHED_VIEWS_EVENTS = FetchCachedViewsEventsUseCase.WORKER_ID
            const val FIX_CALENDARS = FixCalendarsUseCase.WORKER_ID
            const val BOOTSTRAP_CALENDARS = BootstrapCalendarUseCase.BOOTSTRAP_CALENDARS
            const val BOOTSTRAP_ALL_CALENDARS = BootstrapCalendarUseCase.BOOTSTRAP_ALL_CALENDARS
            const val MEMBERS_KEY_SETUP = KeySetupUseCase.MEMBERS_KEY_SETUP
            const val REFRESH_MEMBERS_FLAGS = RefreshMembersFlagsUseCase.REFRESH_MEMBERS_FLAGS
            const val GET_MINIMAL_CALENDAR_EVENTS = GetMinimalCalendarEventsUseCase.GET_MINIMAL_CALENDAR_EVENTS
            const val HANDLE_TIME_ZONE_CHANGE = CalendarUserSettingsChangedUseCase.HANDLE_TIME_ZONE_CHANGE
            const val REFRESH_CALENDAR_USER_SETTINGS = RefreshCalendarUserSettingsUseCase.REFRESH_CALENDAR_USER_SETTINGS
            const val REFRESH_CALENDAR_SETTINGS = RefreshCalendarSettingsUseCase.REFRESH_CALENDAR_SETTINGS
            const val REFRESH_CALENDAR_SUBSCRIPTION = RefreshCalendarSubscriptionUseCase.REFRESH_CALENDAR_SUBSCRIPTION
            const val REFRESH_CALENDAR_PASSPHRASE = RefreshCalendarPassphraseUseCase.REFRESH_CALENDAR_PASSPHRASE
            const val REFRESH_CALENDAR_KEYS = RefreshCalendarKeysUseCase.REFRESH_CALENDAR_KEYS
            const val UPDATE_ALARMS = UpdateAlarmsUseCase.UPDATE_ALARMS
            const val HANDLE_ALARMS_WITH_MISSING_EVENT = HandleAlarmsWithMissingEventUseCase.HANDLE_ALARMS_WITH_MISSING_EVENT
            const val MIGRATE_EVENT_METADATA_TO_OCCURRENCES = MigrateEventMetadataToOccurrencesUseCase.WORKER_ID
        }
    }

    /**
     * Work Input Data keys.
     */
    companion object {
        const val INPUT_USE_CASE_ID = "INPUT_USE_CASE_ID"
        const val INPUT_USER_ID = "INPUT_USER_ID"
        const val INPUT_CALENDAR_ID = "INPUT_CALENDAR_ID"
        const val INPUT_CALENDAR_IDS = "INPUT_CALENDAR_IDS"
        const val INPUT_MEMBER_IDS = "INPUT_MEMBER_IDS"
        const val INPUT_PARTICIPATION_STATUS = "INPUT_PARTICIPATION_STATUS"
        const val INPUT_PRIMARY_TIMEZONE = "INPUT_PRIMARY_TIMEZONE"
        const val INPUT_AUTO_DETECT_PRIMARY_TIMEZONE = "INPUT_AUTO_DETECT_PRIMARY_TIMEZONE"
        const val INPUT_DISPLAY_WEEK_NUMBER = "INPUT_DISPLAY_WEEK_NUMBER"
        const val INPUT_AUTO_IMPORT_INVITE = "INPUT_AUTO_IMPORT_INVITE"
        const val INPUT_TIME_FORMAT = "INPUT_TIME_FORMAT"
        const val INPUT_WEEK_START = "INPUT_WEEK_START"
        const val INPUT_EVENT_UID = "INPUT_EVENT_UID"
        const val INPUT_EVENT_ID = "INPUT_EVENT_ID"
        const val INPUT_USER_EMAILS = "INPUT_USER_EMAILS"
        const val INPUT_DATE = "INPUT_DATE"
        const val INPUT_TIME_ZONE_ID = "INPUT_TIME_ZONE_ID"
        const val INPUT_UPDATE_ALL_DAY_ALARMS = "INPUT_UPDATE_ALL_DAY_ALARMS"
        const val INPUT_UPDATE_PART_DAY_ALARMS = "INPUT_UPDATE_PART_DAY_ALARMS"
    }

    /**
     * Used by WorkManager to ensure uniqueness.
     */
    class UniqueWorkNames {
        companion object {
            // const val SYNC_SERVER_EVENTS = "SYNC_SERVER_EVENTS" deprecated, don't remove this comment
            const val UPDATE_CALENDAR_LIST = "UPDATE_CALENDAR_LIST"
            const val UPDATE_PRIMARY_TIMEZONE = "UPDATE_PRIMARY_TIMEZONE"
            const val UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE = "UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE"
            const val UPDATE_DISPLAY_WEEK_NUMBER = "UPDATE_DISPLAY_WEEK_NUMBER"
            const val UPDATE_AUTO_IMPORT_INVITE = "UPDATE_AUTO_IMPORT_INVITE"
            const val UPDATE_TIME_FORMAT = "UPDATE_TIME_FORMAT"
            const val UPDATE_WEEK_START = "UPDATE_WEEK_START"
            const val UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT = "UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT"
            const val FETCH_CACHED_VIEWS_EVENTS = "FETCH_CACHED_VIEWS_EVENTS"
            const val FIX_CALENDARS = "FIX_CALENDARS"
            const val BOOTSTRAP_CALENDARS = "BOOTSTRAP_CALENDARS"
            const val BOOTSTRAP_ALL_CALENDARS = "BOOTSTRAP_ALL_CALENDARS"
            const val MEMBERS_KEY_SETUP = "MEMBERS_KEY_SETUP"
            const val REFRESH_MEMBERS_FLAGS = "REFRESH_MEMBERS_FLAGS"
            const val HANDLE_TIME_ZONE_CHANGE = "HANDLE_TIME_ZONE_CHANGE"
            const val REFRESH_CALENDAR_USER_SETTINGS = "REFRESH_CALENDAR_USER_SETTINGS"
            const val REFRESH_CALENDAR_SETTINGS = "REFRESH_CALENDAR_SETTINGS"
            const val REFRESH_CALENDAR_SUBSCRIPTION = "REFRESH_CALENDAR_SUBSCRIPTION"
            const val REFRESH_CALENDAR_PASSPHRASE = "REFRESH_CALENDAR_PASSPHRASE"
            const val REFRESH_CALENDAR_KEYS = "REFRESH_CALENDAR_KEYS"
            const val UPDATE_ALARMS = "UPDATE_ALARMS"
            const val HANDLE_ALARMS_WITH_MISSING_EVENT = "HANDLE_ALARMS_WITH_MISSING_EVENT"
            const val MIGRATE_EVENT_METADATA_TO_OCCURRENCES = "MIGRATE_EVENT_METADATA_TO_OCCURRENCES"
        }
    }

    override suspend fun doWork(): Result {
        logger.v("inside UseCaseWorker doWork(), usecaseid: ${inputData.getString(INPUT_USE_CASE_ID)}")

        val userId = inputData.getString(INPUT_USER_ID)?.let { UserId(it) } ?: return Result.failure()
        val useCaseId = inputData.getString(INPUT_USE_CASE_ID)
        val useCaseResult = when (useCaseId) {
            UseCaseId.UPDATE_CALENDAR_LIST -> {
                updateCalendarUseCase.updateAllCalendarsDisplay(userId)
            }
            UseCaseId.UPDATE_PRIMARY_TIMEZONE -> {
                updateCalendarUserSettingsUseCase.executePrimaryTimezone(
                    userId,
                    inputData.getString(INPUT_PRIMARY_TIMEZONE) ?: return Result.failure()
                )
            }
            UseCaseId.UPDATE_AUTO_DETECT_PRIMARY_TIMEZONE -> {
                updateCalendarUserSettingsUseCase.executeAutoDetectPrimaryTimezone(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Boolean>(INPUT_AUTO_DETECT_PRIMARY_TIMEZONE))
                        inputData.getBoolean(INPUT_AUTO_DETECT_PRIMARY_TIMEZONE, true)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_DISPLAY_WEEK_NUMBER -> {
                updateCalendarUserSettingsUseCase.executeDisplayWeekNumber(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Boolean>(INPUT_DISPLAY_WEEK_NUMBER))
                        inputData.getBoolean(INPUT_DISPLAY_WEEK_NUMBER, true)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_AUTO_IMPORT_INVITE -> {
                updateCalendarUserSettingsUseCase.executeAutoImportInvite(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Boolean>(INPUT_AUTO_IMPORT_INVITE))
                        inputData.getBoolean(INPUT_AUTO_IMPORT_INVITE, true)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_TIME_FORMAT -> {
                updateUserSettingsUseCase.executeTimeFormat(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Int>(INPUT_TIME_FORMAT))
                        inputData.getInt(INPUT_TIME_FORMAT, 0)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_WEEK_START -> {
                updateUserSettingsUseCase.executeWeekStart(
                    userId,
                    if (inputData.hasKeyWithValueOfType<Int>(INPUT_WEEK_START))
                        inputData.getInt(INPUT_WEEK_START, 0)
                    else return Result.failure()
                )
            }
            UseCaseId.UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT -> {
                updateParticipationStatusUseCase.executeClearSingleEdits(
                    userId,
                    inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure(),
                    inputData.getString(INPUT_EVENT_UID) ?: return Result.failure(),
                    inputData.getStringArray(INPUT_USER_EMAILS)?.toList() ?: return Result.failure(),
                    inputData.getInt(INPUT_PARTICIPATION_STATUS, 0))
            }
            UseCaseId.FETCH_CACHED_VIEWS_EVENTS -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                val selectedDateEpochDay = inputData.getLong(INPUT_DATE, LocalDate.now().toEpochDay())
                val timeZoneId = inputData.getString(INPUT_TIME_ZONE_ID) ?: return Result.failure()
                fetchCachedViewsEventsUseCase.execute(
                    userId,
                    calendarId,
                    LocalDate.ofEpochDay(selectedDateEpochDay),
                    timeZoneId
                )
            }
            UseCaseId.FIX_CALENDARS -> {
                fixCalendarsUseCase.execute(userId)
            }
            UseCaseId.BOOTSTRAP_CALENDARS -> {
                val calendarIds = inputData.getStringArray(INPUT_CALENDAR_IDS)?.toList() ?: return Result.failure()
                bootstrapCalendarUseCase.executeCalendarsBootstrap(userId, calendarIds)
            }
            UseCaseId.BOOTSTRAP_ALL_CALENDARS -> {
                bootstrapCalendarUseCase.executeAllCalendarsBootstrap(userId)
            }
            UseCaseId.MEMBERS_KEY_SETUP -> {
                val memberIds = inputData.getStringArray(INPUT_MEMBER_IDS)?.toList() ?: return Result.failure()
                keySetupUseCase.handleMembersWithIncompleteKeySetup(userId, memberIds)
            }
            UseCaseId.REFRESH_MEMBERS_FLAGS -> {
                refreshMembersFlagsUseCase.invoke(userId)
            }
            UseCaseId.GET_MINIMAL_CALENDAR_EVENTS -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                getMinimalCalendarEventsUseCase.execute(userId, calendarId)
            }
            UseCaseId.HANDLE_TIME_ZONE_CHANGE -> {
                calendarUserSettingsChangedUseCase.handlePrimaryTimezoneChange(userId.id)
            }
            UseCaseId.REFRESH_CALENDAR_USER_SETTINGS -> {
                refreshCalendarUserSettingsUseCase.invoke(userId)
            }
            UseCaseId.REFRESH_CALENDAR_SETTINGS -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                refreshCalendarSettingsUseCase.invoke(userId, calendarId)
            }
            UseCaseId.REFRESH_CALENDAR_SUBSCRIPTION -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                refreshCalendarSubscriptionUseCase.invoke(userId, calendarId)
            }
            UseCaseId.REFRESH_CALENDAR_PASSPHRASE -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                refreshCalendarPassphraseUseCase.invoke(userId, calendarId)
            }
            UseCaseId.REFRESH_CALENDAR_KEYS -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                refreshCalendarKeysUseCase.invoke(userId, calendarId)
            }
            UseCaseId.UPDATE_ALARMS -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                val updateAllDayAlarms = inputData.getBoolean(INPUT_UPDATE_ALL_DAY_ALARMS, false)
                val updatePartDayAlarms = inputData.getBoolean(INPUT_UPDATE_PART_DAY_ALARMS, false)
                updateAlarmsUseCase.execute(userId.id, calendarId, updateAllDayAlarms, updatePartDayAlarms)
            }
            UseCaseId.HANDLE_ALARMS_WITH_MISSING_EVENT -> {
                val calendarId = inputData.getString(INPUT_CALENDAR_ID) ?: return Result.failure()
                val eventId = inputData.getString(INPUT_EVENT_ID) ?: return Result.failure()
                handleAlarmsWithMissingEventUseCase.invoke(userId, calendarId, eventId)
            }
            UseCaseId.MIGRATE_EVENT_METADATA_TO_OCCURRENCES -> {
                migrateEventMetadataToOccurrencesUseCase.execute()
            }
            else -> {
                TODO("unsupported or empty UseCaseId: $useCaseId")
            }
        }

        return when (useCaseResult) {
            is UseCase.Result.Success<*> -> {
                logger.v("UseCaseId=$useCaseId success")
                Result.success()
            }
            is UseCase.Result.InvalidParams -> {
                logger.i("UseCaseId=$useCaseId failure, reason: ${useCaseResult.message}")
                Result.failure()
            }
            is UseCase.Result.Error -> {
                if (this.runAttemptCount >= WORKER_MAX_RETRY_COUNT) {
                    logger.e("UseCaseId=$useCaseId error, reason: ${useCaseResult.message}, max retry exceeded")
                    Result.failure()
                } else {
                    logger.i("UseCaseId=$useCaseId error, reason: ${useCaseResult.message}, retrying")
                    Result.retry()
                }
            }
        }
    }

}
