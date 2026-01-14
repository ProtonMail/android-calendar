package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.Logger
import javax.inject.Inject

class CalendarUserSettingsChangedUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase
) : UseCase {

    companion object {
        const val HANDLE_TIME_ZONE_CHANGE = "HANDLE_TIME_ZONE_CHANGE"
    }

    suspend fun handlePrimaryTimezoneChange(userId: String): UseCase.Result {
        // get all all-day events
        val events = database.eventsDao().selectAllDayOnly()

        // 2. recalculate their alarms
        updateAlarmsUseCase.execute(userId, events)

        return UseCase.Result.Success<Unit>()
    }

}
