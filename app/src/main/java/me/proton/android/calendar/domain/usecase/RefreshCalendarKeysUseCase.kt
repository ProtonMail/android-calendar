package me.proton.android.calendar.domain.usecase

import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.GetMinimalCalendarEventsWorker
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Fetches Calendar Keys from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarKeysUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val calendarsApi: CalendarsApi,
    private val workManager: WorkManager
) {

    companion object {
        const val REFRESH_CALENDAR_KEYS = "REFRESH_CALENDAR_KEYS"
    }

    suspend operator fun invoke(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        val calendarKeysResponse = calendarsApi.getKeys(userId, calendarId)
        if (calendarKeysResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarKeysUseCase: error getting calendar keys from API: $calendarKeysResponse")
        }

        val calendarKeys = calendarKeysResponse.data.keys

        // Persist new keys in DB
        calendarKeys.forEach {
            calendarsRepository.persistCalendarKey(it)
        }

        // Launch worker to fetch minimal events for calendar
        GetMinimalCalendarEventsWorker.enqueue(workManager, userId = userId.id, calendarId = calendarId)

        return UseCase.Result.Success(calendarKeys)
    }


}
