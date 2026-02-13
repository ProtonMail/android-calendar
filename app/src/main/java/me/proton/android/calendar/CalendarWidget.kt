package me.proton.android.calendar

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import biweekly.parameter.ParticipationStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.android.calendar.CalendarWidget.Companion.WIDGET_DAYS_AHEAD
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.getUserSettingsEntity
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDayOfWeekMedium
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDate
import me.proton.android.calendar.common.utils.EventUtilsImpl.calculateFullDayCounter
import me.proton.android.calendar.common.utils.EventUtilsImpl.formatFullDayCounter
import me.proton.android.calendar.common.utils.ICalUtilsImpl.explodeDayByDay
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.GetUiEventsUseCase
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.takeIfNotBlank
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface WidgetRefresher {
    /**
     * Sends a broadcast to force-refresh all the app-widgets.
     */
    fun broadcastRefresh()

    /**
     * Refreshes all Event lists in all Widgets using AppWidgetManager (without broadcast).
     */
    fun refreshEventList()
}

class CalendarWidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) : WidgetRefresher {

    override fun broadcastRefresh() {
        CalendarWidget.sendRefreshBroadcast(context)
        CalendarMonthWidget.sendRefreshBroadcast(context)
    }

    override fun refreshEventList() {
        val widgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, CalendarWidget::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)

        widgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.lv_widget)

        // Month widget uses static layout, trigger a full refresh
        CalendarMonthWidget.sendRefreshBroadcast(context)
    }
}

/**
 * App Widget showing upcoming Events
 */
class CalendarWidget : AppWidgetProvider(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent?) {
        intent?.let {
            // manually refresh Widget when we get the refresh broadcast
            if (it.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
                // and these special ones
                it.action == Intent.ACTION_TIME_CHANGED ||
                it.action == Intent.ACTION_TIMEZONE_CHANGED ||
                it.action == Intent.ACTION_DATE_CHANGED ||
                it.action == Intent.ACTION_LOCALE_CHANGED) {

                val widgetManager = AppWidgetManager.getInstance(context)
                val widgetComponent = ComponentName(context, CalendarWidget::class.java)
                val appWidgetIds = widgetManager.getAppWidgetIds(widgetComponent)

                onUpdate(context, widgetManager, appWidgetIds)
            } else {
                // delegate to super for other actions like adding or removing the Widget
                super.onReceive(context, intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {

        val remoteViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget)

        // format current date
        val localDate = LocalDate.now(ZoneId.systemDefault())
        remoteViews.setTextViewText(R.id.tv_header_text, localDate.formatDayOfWeek())
        val monthAndDay: String = DateUtils.formatDateTime(
            context,
            localDate.toDate(ZoneId.systemDefault().id).time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR
        )
        remoteViews.setTextViewText(R.id.tv_main_text, monthAndDay)

        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

        // intent for "Open the App"
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            createOpenAppIntent(context),
            pendingIntentFlags
        )
        remoteViews.setOnClickPendingIntent(R.id.rl_header_container, openAppPendingIntent)

        // open the app when user clicks on main info text
        remoteViews.setOnClickPendingIntent(R.id.tv_widget_main_info_text, openAppPendingIntent)

        // intent for "Refresh" button
        val refreshPendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                createWidgetRefreshIntent(context),
                pendingIntentFlags
            )
        remoteViews.setOnClickPendingIntent(R.id.ib_refresh, refreshPendingIntent)

        // intent for "New Event"
        val newEventPendingIntent = PendingIntent.getActivity(
            context,
            0,
            createNewEventIntent(context),
            pendingIntentFlags
        )
        remoteViews.setOnClickPendingIntent(R.id.ib_plus, newEventPendingIntent)

        // intent for RemoteViewService that creates the ListView with Events
        val remoteViewsServiceIntent = Intent(context, CalendarWidgetRemoteViewsService::class.java)
        // we need to pass the AppWidgetID to RemoteViewsService
        remoteViewsServiceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // without "data" property, extras are ignored when comparing intents
        remoteViewsServiceIntent.data = Uri.parse(remoteViewsServiceIntent.toUri(Intent.URI_INTENT_SCHEME))
        remoteViews.setRemoteAdapter(
            R.id.lv_widget,
            remoteViewsServiceIntent
        )
        // used for handling clicks on ListView elements
        remoteViews.setPendingIntentTemplate(R.id.lv_widget, createPendingIntentTemplate(context))

        // trigger Remote Views update
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)

        // force ListView to refresh
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lv_widget)
    }

    private fun createOpenAppIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
        data = Navigation.Deeplink.toMonth(LocalDate.now())
        action = MainViewModel.INTENT_ACTION_SHOW_DAY
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun createNewEventIntent(context: Context) = Intent(context, MainActivity::class.java).apply {
        action = MainViewModel.INTENT_ACTION_NEW_EVENT
        addCategory(Intent.CATEGORY_LAUNCHER)
        // addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // TODO figure out the "not going back to create new event"
    }

    /**
     * Special PendingIntent that will be merged with data specific to clicked Event, right before opening the app.
     */
    private fun createPendingIntentTemplate(context: Context): PendingIntent {

        // Intent 'data' and 'action' will be filled in later by FillInIntent
        val intentTemplate = Intent(context, MainActivity::class.java)

        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(intentTemplate)
            .getPendingIntent(0, pendingIntentFlags)
    }

    companion object {

        const val WIDGET_DAYS_AHEAD = 14

        private fun createWidgetRefreshIntent(context: Context): Intent {
            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, CalendarWidget::class.java)
            val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)

            return Intent(context, CalendarWidget::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
        }

        /**
         * Widget is refreshed only when this special Intent gets broadcast.
         */
        fun sendRefreshBroadcast(context: Context) {
            // TODO https://developer.android.com/guide/topics/appwidgets/advanced#broadcastreceiver-priority
            context.sendBroadcast(createWidgetRefreshIntent(context))
        }

    }

}

internal class CalendarWidgetRemoteViewsService : RemoteViewsService(), KoinComponent {

    private val resourceProvider: ResourceProvider by inject()
    private val accountManager: AccountManager by inject()
    private val userAddressManager: UserAddressManager by inject()
    private val userSettingsRepository: UserSettingsRepository by inject()
    private val logger: Logger by inject()
    private val database: AppDatabase by inject()
    private val getUiEventsUseCase: GetUiEventsUseCase by inject()

    override fun onGetViewFactory(intent: Intent?): RemoteViewsFactory {

        val widgetId = intent?.let { intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) }

        if (widgetId == null) {
            logger.e("widgetId == null in onGetViewFactory")
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            logger.e("widgetId == INVALID_APPWIDGET_ID in onGetViewFactory")
        }
        if (intent == null) {
            logger.e("intent == null in onGetViewFactory")
        }

        return CalendarWidgetRemoteViewsFactory(
            widgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID,
            resourceProvider,
            accountManager,
            userAddressManager,
            userSettingsRepository,
            applicationContext,
            logger,
            database,
            getUiEventsUseCase
        )
    }
}

internal data class WidgetEvent(
    val id: String,
    val summary: String,
    val subheaderContent: String,
    val isCancelledOrDeclined: Boolean,
    val needsAction: Boolean,
    val failedToDecrypt: Boolean,
    // LocalDate that this Event spans, not necessarily the same as dateStart
    val happensOn: LocalDate,
    val showDateColumn: Boolean,
    val showBottomSpacing: Boolean,
    val showNoEventsToday: Boolean,
    val fullDayCounter: String?,
    val occurrenceNumber: Int,
    val color: String,
)

internal class CalendarWidgetRemoteViewsFactory(
    private val appWidgetId: Int,
    private val resourceProvider: ResourceProvider,
    private val accountManager: AccountManager,
    private val userAddressManager: UserAddressManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val applicationContext: Context,
    private val logger: Logger,
    private val database: AppDatabase,
    private val getUiEventsUseCase: GetUiEventsUseCase
) : RemoteViewsService.RemoteViewsFactory {

    private var adapterData = emptyList<WidgetEvent>()

    private fun UiEvent.toWidgetEvent(
        happensOn: LocalDate,
        timeZoneId: String,
        showDateColumn: Boolean,
        showBottomSpacing: Boolean,
        showNoEventsToday: Boolean,
        is24Hour: Boolean,
    ): WidgetEvent {

        val fullDayCounter = this.calculateFullDayCounter(happensOn)

        val fullDayCounterString = if (fullDayCounter.second > 1) {
            this.formatFullDayCounter(happensOn)
        } else null

        val dateText = if (this.isAllDay) {
            resourceProvider.provideString(R.string.event_all_day)
        } else {
            if (fullDayCounter.second > 1) { // multi-day part-day
                when (fullDayCounter.first) {
                    1 -> { // first day
                        resourceProvider.provideString(
                            R.string.calendar_widget_part_day_event_starts_at,
                            this.dateStart.formatTime(timeZoneId, is24Hour)
                        )
                    }
                    fullDayCounter.second -> { // last day
                        resourceProvider.provideString(
                            R.string.calendar_widget_part_day_event_ends_at,
                            this.dateEnd.formatTime(timeZoneId, is24Hour)
                        )
                    }
                    else -> { // day in the middle
                        resourceProvider.provideString(R.string.event_all_day)
                    }
                }
            } else { // single-day part-day
                "${
                    dateStart.formatTime(
                        timeZoneId,
                        is24Hour
                    )
                } ‐ ${dateEnd.formatTime(timeZoneId, is24Hour)}"
            }
        }

        val locationText = this.location?.takeIfNotBlank()?.let { " • $it" }

        val subheaderContent = "${dateText}${locationText ?: ""}"

        return WidgetEvent(
            id = this.id,
            summary = this.summary?.takeIfNotBlank() ?: resourceProvider.provideString(R.string.default_event_summary),
            subheaderContent = subheaderContent,
            happensOn = happensOn,
            showDateColumn = showDateColumn,
            showBottomSpacing = showBottomSpacing,
            showNoEventsToday = showNoEventsToday,
            fullDayCounter = fullDayCounterString,
            occurrenceNumber = this.occurrenceNumber,
            color = this.displayColor,
            isCancelledOrDeclined = this.decryptionStatus == Event.DecryptionStatus.Success && (this.isCancelled() || participationStatus == ParticipationStatus.DECLINED),
            needsAction = !this.isCancelled() && participationStatus == ParticipationStatus.NEEDS_ACTION,
            failedToDecrypt = this.decryptionStatus is Event.DecryptionStatus.Failure
        )
    }

    override fun getViewAt(position: Int): RemoteViews? {

        val remoteView = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.item_widget)

        val event = adapterData.getOrNull(position)

        if (event == null) {
            logger.e("widget item position out of bounds")
            return null
        }

        // show or hide date column
        remoteView.setViewVisibility(
            R.id.rl_event_day_container,
            if (event.showDateColumn) View.VISIBLE else View.INVISIBLE
        )
        remoteView.setTextViewText(
            R.id.tv_event_day_of_week,
            if (event.showDateColumn) event.happensOn.formatDayOfWeekMedium() else ""
        )
        remoteView.setTextViewText(
            R.id.tv_event_day_of_month,
            if (event.showDateColumn) "${event.happensOn.dayOfMonth}" else ""
        )

        // strikethrough if event is cancelled
        if (event.isCancelledOrDeclined) {
            remoteView.setInt(
                R.id.tv_event_header,
                "setPaintFlags",
                Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            )
            remoteView.setInt(
                R.id.tv_event_subheader,
                "setPaintFlags",
                Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            )
        } else {
            remoteView.setInt(R.id.tv_event_header, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)
            remoteView.setInt(R.id.tv_event_subheader, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)
        }

        // set summary and time
        remoteView.setTextViewText(R.id.tv_event_header, event.summary)
        remoteView.setTextViewText(
            R.id.tv_event_header_day_indicator,
            if (event.fullDayCounter != null) " " + event.fullDayCounter else ""
        )
        remoteView.setTextViewText(R.id.tv_event_subheader, event.subheaderContent)

        // clear summary and show views for event that failed decryption
        if (event.failedToDecrypt) remoteView.setTextViewText(R.id.tv_event_header, "")
        remoteView.setViewVisibility(
            R.id.decryption_error_view,
            if (event.failedToDecrypt) View.VISIBLE else View.INVISIBLE
        )
        remoteView.setViewVisibility(
            R.id.decryption_error_icon,
            if (event.failedToDecrypt) View.VISIBLE else View.INVISIBLE
        )

        // show or hide "no upcoming events today"
        remoteView.setViewVisibility(
            R.id.rl_event_container,
            if (event.showNoEventsToday) View.INVISIBLE else View.VISIBLE
        )
        remoteView.setViewVisibility(
            R.id.tv_event_no_events_today,
            if (event.showNoEventsToday) View.VISIBLE else View.INVISIBLE
        )

        // add spacing after last event in a day
        remoteView.setViewVisibility(R.id.v_event_spacing, if (event.showBottomSpacing) View.VISIBLE else View.GONE)

        // set style of calendar bar
        if (event.needsAction) {
            remoteView.setImageViewResource(R.id.iv_calendar_bar, R.drawable.ic_calendar_bar_unanswered)
        } else {
            remoteView.setImageViewResource(R.id.iv_calendar_bar, R.drawable.shape_calendar_bar)
        }

        // tint calendar bar
        remoteView.setInt(R.id.iv_calendar_bar, "setColorFilter", Color.parseColor(event.color))

        // setup tapping on occurrences
        val eventId = adapterData[position].id
        val occurrenceNumber = adapterData[position].occurrenceNumber

        // combine `data` property with the Intent Template, click on day
        remoteView.setOnClickFillInIntent(R.id.rl_event_day_container, Intent().apply {
            data = Navigation.Deeplink.toMonth(event.happensOn)
            action = MainViewModel.INTENT_ACTION_SHOW_DAY
        })

        // combine `data` property with the Intent Template, click on Event
        remoteView.setOnClickFillInIntent(R.id.rl_event_container, Intent().apply {
            if (event.failedToDecrypt) { // instead of opening event, go to day
                data = Navigation.Deeplink.toMonth(event.happensOn)
                action = MainViewModel.INTENT_ACTION_SHOW_DAY
            } else {
                data = Navigation.Deeplink.toMainActivityWithEventId(eventId, occurrenceNumber)
                action = MainViewModel.INTENT_ACTION_SHOW_EVENT_DETAILS
            }
        })

        return remoteView
    }

    override fun onDataSetChanged() {

        // val identityToken = Binder.clearCallingIdentity() // TODO use this as last resort

        // this method has to get the data synchronously
        runBlocking {

            val widgetEvents = withTimeoutOrNull(java.time.Duration.ofSeconds(30).toMillis()) {

                val userId = accountManager.getPrimaryAccount().firstOrNull()?.userId

                val userEmails = userId?.let {
                    userAddressManager.getAddressesOrNull(userId)?.map { it.email }
                } ?: emptyList()

                // show or hide "logged out" or "loading" info
                if (userEmails.isEmpty()) { // user is logged out
                    displayMainInfoText(resourceProvider.provideString(R.string.calendar_widget_please_log_in))
                    displayActionButtons(display = false)
                    // there is no need to query the Events
                    return@withTimeoutOrNull emptyList<WidgetEvent>()
                } else {
                    displayMainInfoText(if (adapterData.isEmpty()) resourceProvider.provideString(R.string.calendar_widget_loading_events) else null)
                    displayActionButtons(display = true)
                }

                val is24Hour = if (userId != null) {
                    userSettingsRepository.getUserSettingsEntity(userId, database).timeFormatIs24Hour(DateFormat.is24HourFormat(applicationContext))
                } else true

                val zoneId = ZoneId.systemDefault()

                val fromDate = LocalDate.now(zoneId)
                val toDate = LocalDate.now(zoneId).plusDays(WIDGET_DAYS_AHEAD.toLong())

                val events = withContext(this.coroutineContext) {
                    if (userId != null) {
                        val events = getUiEventsUseCase.execute(
                            userId,
                            fromDate,
                            toDate,
                            zoneId.id
                        ).firstOrNull()

                        when (events) {
                            is CalendarsRepository.GetEventsResult.Success<UiEvent> -> events.events
                            else -> emptyList()
                        }
                    } else emptyList()
                }

                // create a list of Events with correct time labels to display on Widget list
                val widgetEvents = mutableListOf<WidgetEvent>()
                events.explodeDayByDay(fromDate, toDate, zoneId.id).toSortedMap().forEach { entry ->

                    val upcomingEvents = entry.value.filter { !it.isInThePast() }
                        .distinctBy { Pair(it.id, it.occurrenceNumber) } // TODO hack for duplicated events

                    val sortedEvents = upcomingEvents.sortedBy {
                        "${!it.isAllDay}${
                            it.dateStart.toEpochSecond()
                        }${it.summary}"
                    }

                    sortedEvents.forEachIndexed { index, event ->
                        // show date column only in the first Event on a given day
                        // show bottom spacing below last Event on a given day
                        val widgetEvent = event.toWidgetEvent(
                            entry.key,
                            zoneId.id,
                            index == 0,
                            index == sortedEvents.size - 1,
                            false,
                            is24Hour
                        )
                        widgetEvents.add(widgetEvent)
                    }
                }

                // show or hide "no upcoming events" only if user is logged in
                if (userEmails.isNotEmpty()) {
                    displayMainInfoText(if (widgetEvents.isEmpty()) resourceProvider.provideString(R.string.calendar_widget_no_upcoming_events) else null)
                }

                // add special dummy WidgetEvent if there are no Events to show for today
                if (widgetEvents.isNotEmpty() && widgetEvents.first().happensOn != fromDate) {
                    widgetEvents.add(
                        0, widgetEvents.first().copy(
                            happensOn = LocalDate.now(),
                            showDateColumn = true,
                            showBottomSpacing = true,
                            showNoEventsToday = true,
                            color = resourceProvider.provideString(R.color.separator_norm)
                        )
                    )
                }

                // hide spacing below the very last WidgetEvent on list
                widgetEvents.removeLastOrNull()?.let {
                    widgetEvents.add(it.copy(showBottomSpacing = false))
                }

                widgetEvents
            }

            if (widgetEvents == null) {
                logger.e("widgetEvents == null in onDataSetChanged")
                displayMainInfoText(resourceProvider.provideString(R.string.calendar_widget_loading_events_error))
            } else {
                adapterData = widgetEvents
            }
        }

        //Binder.restoreCallingIdentity(identityToken) // TODO use this as last resort

    }

    private fun displayMainInfoText(text: String?) {

        val widgetManager = AppWidgetManager.getInstance(applicationContext)

        val remoteViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget)
        if (text != null) {
            remoteViews.setTextViewText(R.id.tv_widget_main_info_text, text)
            remoteViews.setViewVisibility(R.id.tv_widget_main_info_text, View.VISIBLE)
        } else {
            remoteViews.setViewVisibility(R.id.tv_widget_main_info_text, View.INVISIBLE)
        }

        widgetManager.partiallyUpdateAppWidget(appWidgetId, remoteViews)

    }

    private fun displayActionButtons(display: Boolean) {

        val widgetManager = AppWidgetManager.getInstance(applicationContext)

        val remoteViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget)
        remoteViews.setViewVisibility(R.id.ib_plus, if (display) View.VISIBLE else View.INVISIBLE)
        remoteViews.setViewVisibility(R.id.ib_refresh, if (display) View.VISIBLE else View.INVISIBLE)

        widgetManager.partiallyUpdateAppWidget(appWidgetId, remoteViews)

    }

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun getCount(): Int = adapterData.size

    override fun getLoadingView(): RemoteViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.item_widget_loading)

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds() = false

}
