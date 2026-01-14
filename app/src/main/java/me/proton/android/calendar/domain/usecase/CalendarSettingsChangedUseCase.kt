package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.ICalUtilsImpl.isTheSameAs
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarSettingsEntity
import me.proton.android.calendar.data.entity.getDefaultAlarms
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class CalendarSettingsChangedUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val database: AppDatabase,
    private val updateAlarmsUseCase: UpdateAlarmsUseCase
) : UseCase {

    suspend fun execute(userId: UserId, calendarSettings: CalendarSettingsEntity): UseCase.Result {

        handleDefaultAlarmsChange(userId, calendarsRepository, calendarSettings)

        return UseCase.Result.Success<Unit>()
    }

    /**
     * If default alarm settings changed, recalculate all local alarms for events from this calendar.
     */
    private suspend fun handleDefaultAlarmsChange(userId: UserId, calendarsRepository: CalendarsRepository, newCalendarSettings: CalendarSettingsEntity) {
        val currentPartDayAlarms = calendarsRepository.selectCalendarSettings(newCalendarSettings.calendarId)?.getDefaultAlarms(Json.Default, false)
        val newPartDayAlarms = newCalendarSettings.getDefaultAlarms(Json.Default, false)

        val currentFullDayAlarms = calendarsRepository.selectCalendarSettings(newCalendarSettings.calendarId)?.getDefaultAlarms(Json.Default, true)
        val newFullDayAlarms = newCalendarSettings.getDefaultAlarms(Json.Default, true)

        // we persist new Calendar Settings because they are needed in calculations in next steps,
        //  but we have the previous Settings cached above
        calendarsRepository.persistCalendarSettings(newCalendarSettings)

        if (currentPartDayAlarms?.isTheSameAs(newPartDayAlarms) == false) {
            logger.d("handleDefaultAlarmsChange recalculating part day")

            updateAlarmsUseCase.execute(userId.id, database.eventsDao().selectPartDayOnly(newCalendarSettings.calendarId))
        }

        if (currentFullDayAlarms?.isTheSameAs(newFullDayAlarms) == false) {
            logger.d("handleDefaultAlarmsChange recalculating all day")

            updateAlarmsUseCase.execute(userId.id, database.eventsDao().selectAllDayOnly(newCalendarSettings.calendarId))
        }
    }

}
