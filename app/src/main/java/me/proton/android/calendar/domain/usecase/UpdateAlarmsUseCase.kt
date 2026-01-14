package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.formatUidForICal
import me.proton.android.calendar.common.utils.ICalUtilsImpl.onlyDisplayType
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.entity.UserId
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

class UpdateAlarmsUseCase @Inject constructor(
    private val logger: Logger,
    private val eventDecryptor: EventDecryptor,
    private val database: AppDatabase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val transformEventUseCase: TransformEventUseCase,
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase
) {

    companion object {
        const val UPDATE_ALARMS = "UPDATE_ALARMS"
    }

    suspend fun execute(userId: String, eventEntities: List<EventEntity>): UseCase.Result {
        return handleAlarms(userId, eventEntities)
    }

    suspend fun execute(userId: String, calendarId: String, updateAllDayAlarms: Boolean, updatePartDayAlarms: Boolean): UseCase.Result {
        val dbEvents =
            if (updateAllDayAlarms && updatePartDayAlarms) {
                database.eventsDao().selectEventsByCalendar(calendarId)
            } else if (updateAllDayAlarms) {
                database.eventsDao().selectAllDayOnly(calendarId)
            } else if (updatePartDayAlarms) {
                database.eventsDao().selectPartDayOnly(calendarId)
            }
            else emptyList()

        return handleAlarms(userId, dbEvents)
    }

    private suspend fun handleAlarms(userId: String, dbEvents: List<EventEntity>): UseCase.Result {
        val primaryTimezone = database.calendarUserSettingsDao().select(userId)?.primaryTimezone
        val fromZonedDateTime = if (primaryTimezone == null) {
            logger.e("no primary timezone in UpdateAlarmsUseCase")
            ZonedDateTime.now(ZoneId.systemDefault())
        } else ZonedDateTime.now(ZoneId.of(primaryTimezone))

        val eventChains = dbEvents.mapNotNull { dbEvent ->
            val originalEvent = dbEvent.let { eventEntity ->
                if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
            }

            if (originalEvent == null) {
                logger.e("could not transform event in UpdateAlarmsUseCase")
                null
            } else {
                Pair(originalEvent, database.eventsDao().selectByUid(formatUidForICal(originalEvent.uid)))
            }
        }

        eventChains.forEach {

            val transformedChain = it.second.mapNotNull { eventEntity ->
                if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
            }

            val upcomingAlarms = ICalUtilsImpl.calculateUpcomingAlarmEntities(transformedChain, fromZonedDateTime, "TODO").onlyDisplayType()

            if (transformedChain.isEmpty()) {
                return@forEach
            }

            transformedChain.forEach { event ->
                database.eventAlarmsDao().deleteAllByEventId(event.id)
            }

            safePersistEventAlarmUseCase.invoke(upcomingAlarms)
        }

        return handleAlarmsUseCase.execute(UserId(userId))
    }
}
