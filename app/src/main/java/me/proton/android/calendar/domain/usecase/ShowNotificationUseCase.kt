package me.proton.android.calendar.domain.usecase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import biweekly.parameter.Related
import biweekly.property.Trigger
import biweekly.util.Duration
import dagger.hilt.android.qualifiers.ApplicationContext
import me.proton.android.calendar.R
import me.proton.android.calendar.common.getTimeFormat
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatStartForNotification
import me.proton.android.calendar.common.utils.EventUtilsImpl.generateFirstOccurrenceSince
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl.onlyDisplayType
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.EventAlarmEntity
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.domain.entity.UserId
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

class ShowNotificationUseCase @Inject constructor(
    private val logger: Logger,
    @ApplicationContext private val context: Context,
    private val transformEventUseCase: TransformEventUseCase,
    private val eventDecryptor: EventDecryptor,
    private val database: AppDatabase,
    private val safePersistEventAlarmUseCase: SafePersistEventAlarmUseCase,
    private val userSettingsRepository: UserSettingsRepository
) {

    suspend fun execute(eventAlarms: List<EventAlarmEntity>, userId: String) {

        logger.v("executing ShowNotificationUseCase, showing: ${eventAlarms}")

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!notificationManager.areNotificationsEnabled()) {
                // we ignore the fact that showing notifications is not allowed, but log it
                logger.i("ShowNotificationUseCase: Notifications disabled")
            }
        }

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID_EVENT_ALARMS)
        val systemDefaultZoneId = ZoneId.systemDefault()
        val displayTimeZoneId = database.calendarUserSettingsDao().select(userId)?.primaryTimezone

        if (displayTimeZoneId == null) {
            logger.e("empty displayTimeZoneId in ShowNotificationUseCase")
        }

        val is24Hour = when (userSettingsRepository.getTimeFormat(UserId(userId), database)) {
            0 -> null
            1 -> true
            2 -> false
            else -> null
        }

        eventAlarms.forEach { eventAlarm ->

            val eventEntity = database.eventsDao().selectById(eventAlarm.eventId)
            if (eventEntity == null) {
                logger.e("could not find EventEntity to show notification")
            } else {
                val dbEvent = if (CalendarFeatureFlag.UseEventDecryptor.fallbackValue) {
                    eventDecryptor.decrypt(eventEntity)
                } else {
                    transformEventUseCase.execute(eventEntity)
                }
                if (dbEvent == null) {
                    logger.e("could not transform EventEntity to show notification")
                } else {

                    val eventWithOccurrence = if (dbEvent.isRecurring()) {

                        val trigger = Trigger(Duration.parse(eventAlarm.trigger), Related.START)
                        val occurrenceStartEpoch =
                            Instant.ofEpochSecond(eventAlarm.occurrence - (trigger.duration.toMillis() / 1000))

                        logger.v("calculated occurrence start for event: ${occurrenceStartEpoch.atZone(ZoneId.systemDefault())}")

                        // generate occurrence based on Alarm Trigger and Alarm Occurrence
                        val alarmOccurrence = dbEvent.generateFirstOccurrenceSince(
                            ZonedDateTime.ofInstant(
                                occurrenceStartEpoch,
                                systemDefaultZoneId
                            ))

                        logger.v("occurrence for alarm: $alarmOccurrence")
                        if (alarmOccurrence == null) {
                            logger.e("could not generate occurrence for notification of recurring event")
                            null
                        } else Event.withOccurrence(dbEvent, alarmOccurrence)

                    } else null

                    val notificationTag = (eventWithOccurrence ?: dbEvent).generateNotificationTag()

                    val intent = MainViewModel.createMainIntentToShowEventDetails(
                        context,
                        dbEvent.id,
                        eventWithOccurrence?.occurrence?.occurrenceNumber
                    )

                    val pendingIntent: PendingIntent =
                        PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

                    val text = (eventWithOccurrence ?: dbEvent).formatStartForNotification(
                        systemDefaultZoneId.id,
                        context.resources,
                        is24Hour
                    ) // formatting in phone's timezone

                    notificationBuilder
                        .setSmallIcon(R.drawable.ic_brand_proton_calendar)
                        .setContentTitle(dbEvent.summary ?: context.getString(R.string.default_event_summary))
                        .setContentText(text)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)

                    // cancel all notifications for this particular Event before showing new one
                    notificationManager.activeNotifications.filter {
                        it.tag == notificationTag
                    }.forEach {
                        notificationManager.cancel(notificationTag, it.id)
                    }

                    notificationManager.notify(
                        notificationTag,
                        eventAlarm.occurrence.toInt(), // this is Epoch seconds
                        notificationBuilder.build()
                    )

                    eventWithOccurrence?.let { currentEvent ->
                        if (currentEvent.occurrence == null) {
                            logger.e("occurrence of currentEvent for notifications is null")
                        }
                        currentEvent.occurrence?.let {
                            createAlarmsForNextOccurrence(
                                dbEvent,
                                currentEvent,
                                it,
                                ZoneId.of(displayTimeZoneId ?: ZoneId.systemDefault().id)
                            )
                        }
                    }
                }
            }
        }

    }

    /**
     * Generates Notification TAG using CalendarID, EventID, OccurrenceNumber
     */
    private fun Event.generateNotificationTag(): String {
        return "${this.calendar.id}:${this.id}:${this.occurrence?.occurrenceNumber ?: 0}"
    }

    /**
     * For next occurrence of current Event, make sure we have its Alarms in the database.
     */
    private suspend fun createAlarmsForNextOccurrence(
        originalEvent: Event,
        currentEvent: Event,
        currentOccurrence: Event.Occurrence,
        zoneId: ZoneId
    ) {

        val now = ZonedDateTime.now(zoneId)

        val lastAlarmForCurrentEvent =
            ICalUtilsImpl.calculateAlarmEntities(currentEvent, zoneId.id, "doesn't matter TODO").onlyDisplayType()
                .sortedBy { it.occurrence }.lastOrNull()

        logger.v("lastAlarmForCurrentEvent: $lastAlarmForCurrentEvent")

        if (lastAlarmForCurrentEvent != null && now.isAfter(Instant.ofEpochSecond(lastAlarmForCurrentEvent.occurrence).atZone(zoneId))) {

            val nextOccurrenceNumber = currentOccurrence.occurrenceNumber + 1
            val nextEvent = Event.withOccurrence(originalEvent, nextOccurrenceNumber, zoneId.id)

            if (nextEvent != null) { // maybe [currentOccurrence] was the last valid occurrence of this event
                val alarmsForNextOccurrence = ICalUtilsImpl.calculateAlarmEntities(nextEvent, zoneId.id, "TODO").onlyDisplayType()

                database.eventAlarmsDao().deleteAllByEventId(nextEvent.id)

                logger.v("created next alarms for occurrence ${nextEvent.occurrence}:")
                safePersistEventAlarmUseCase.invoke(alarmsForNextOccurrence)
            }

        }

    }

    companion object {

        val CHANNEL_ID_EVENT_ALARMS = "CHANNEL_ID_EVENT_ALARMS"
        // deprecated but don't remove it, might come in handy
        val CHANNEL_ID_SYNC_SERVICE = "CHANNEL_ID_SYNC_SERVICE"
        val CHANNEL_ID_CALENDAR_FETCH = "CHANNEL_ID_CALENDAR_FETCH"

        // deprecated but don't remove it, might come in handy
        val NOTIFICATION_ID_SYNC_SERVICE: Int = 1
        val NOTIFICATION_ID_FETCH_CALENDARS_WORKER: Int = 2

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // event alarms channel
                val name = context.getString(R.string.notification_channel_alarms)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID_EVENT_ALARMS, name, importance)
                notificationManager.createNotificationChannel(channel)

                // sync service channel
                // deprecated but don't remove it, might come in handy
                val nameSync = context.getString(R.string.notification_channel_sync)
                val importanceSync = NotificationManager.IMPORTANCE_MIN
                val channelSync = NotificationChannel(CHANNEL_ID_SYNC_SERVICE, nameSync, importanceSync)
                notificationManager.createNotificationChannel(channelSync)

                // fetching all events for a Calendar channel
                val nameCalendarFetch = context.getString(R.string.notification_channel_calendar_fetch)
                val importanceCalendarFetch = NotificationManager.IMPORTANCE_DEFAULT
                val channelCalendarFetch = NotificationChannel(CHANNEL_ID_CALENDAR_FETCH, nameCalendarFetch, importanceCalendarFetch)
                notificationManager.createNotificationChannel(channelCalendarFetch)

            }
        }

        // TODO in the future tag notifications with userId and cancel only for given user
        fun cancelAllNotifications(context: Context) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancelAll()
        }
    }

}
