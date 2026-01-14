package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.flow.firstOrNull
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.ProtonUtilsImpl
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Refreshes calendars from API,
 * then fetches the event metadata for all calendars, over the current cached window.
 * Should not really do anything visible other than show the loader,
 * but the idea is any potentially missing events will be fetched and inserted.
 */
class ManualRefreshUseCase @Inject constructor(
    private val calendarsRepository: CalendarsRepository,
    private val accountManager: AccountManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val database: AppDatabase,
) : UseCase {

    suspend fun execute(): UseCase.Result {
        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: return UseCase.Result.Error("User not logged in")

        val timeZoneId = calendarsRepository.selectCalendarUserSettingsPrimaryTimezone(userId.id)?.let {
            ZoneId.of(it)
        } ?: ZoneId.systemDefault()

        val weekStart = userSettingsRepository.getWeekStart(userId, database)
        val (fromDate, toDate) = ProtonUtilsImpl.getCachedMonthViewsTimeWindow(LocalDate.now(timeZoneId), weekStart)

        calendarsRepository.refreshCalendars(userId)

        val visibleIds = calendarsRepository.flowVisibleCalendarIds(userId.id).firstOrNull().orEmpty()
        if (visibleIds.isEmpty()) return UseCase.Result.Success<Unit>()

        calendarsRepository.fetchEvents(
            userId = userId,
            fromDate = fromDate,
            toDate = toDate,
            timeZoneId = timeZoneId.id,
            force = true,
        )
        return UseCase.Result.Success<Unit>()
    }
}
