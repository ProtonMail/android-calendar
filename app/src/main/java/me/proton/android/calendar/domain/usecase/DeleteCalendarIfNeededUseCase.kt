package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.isNotFound
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Deletes local calendar from DB only if it doesn't exist on the backend.
 */
class DeleteCalendarIfNeededUseCase @Inject constructor(
    private val calendarsApi: CalendarsApi,
    private val database: AppDatabase,
    private val logger: Logger,
) {

    suspend fun execute(
        userId: String,
        calendarId: String
    ) {

        // fetch Calendar from API, if it doesn't exist, delete it from local DB
        if (calendarExistsOnServer(UserId(userId), calendarId) == false) {
            // TODO clear search database too
            logger.e("DeleteCalendarIfNeededUseCase deleting calendar from local DB")
            database.calendarsDao().deleteById(calendarId)
        }

    }

    private suspend fun calendarExistsOnServer(userId: UserId, calendarId: String): Boolean? {
        return when (val result = calendarsApi.getCalendar(userId, calendarId)) {
            is ApiResponse.Success -> true
            is ApiResponse.Error -> {
                if (result.isNotFound()) return false
                else null
            }

            is ApiResponse.Exception -> null
        }
    }

}
