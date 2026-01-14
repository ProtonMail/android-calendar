package me.proton.android.calendar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import biweekly.parameter.ParticipationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.android.calendar.CalendarWidget.Companion.WIDGET_DAYS_AHEAD
import me.proton.android.calendar.common.getUserSettingsEntity
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
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
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.takeIfNotBlank
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal class CalendarWidgetUpdater(
    private val resourceProvider: ResourceProvider,
    private val accountManager: AccountManager,
    private val userAddressManager: UserAddressManager,
    private val userSettingsRepository: UserSettingsRepository,
    private val logger: Logger,
    private val database: AppDatabase,
    private val getUiEventsUseCase: GetUiEventsUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var snapshot: List<WidgetEvent>? = null
        private set

    @Volatile
    var statusText: String? = null
        private set

    @Volatile
    var showButtons: Boolean = false
        private set

    private var lastConfigKey: String? = null

    private fun currentConfigKey(context: Context): String {
        val is24h = DateFormat.is24HourFormat(context)
        val lang = Locale.getDefault().toLanguageTag()
        val tz = ZoneId.systemDefault().id
        return "$lang|$tz|$is24h"
    }

    fun ensureSnapshotAvailable(context: Context) {
        val current = currentConfigKey(context)
        val isNotStale = snapshot != null && lastConfigKey == current
        if (isNotStale) return
        rebuildSnapshot(context)
    }

    fun rebuildSnapshot(context: Context) {
        if (snapshot == null) {
            statusText = resourceProvider.provideString(R.string.calendar_widget_loading_events)
            showButtons = false
            updateHeader(context)
        }
        scope.launch {
            val result = withTimeoutOrNull(Duration.ofSeconds(30).toMillis()) {
                buildSnapshot(context)
            }
            if (result == null) {
                logger.e("Timeout building widget snapshot")
                statusText = resourceProvider.provideString(R.string.calendar_widget_loading_events_error)
            } else {
                lastConfigKey = currentConfigKey(context)
                snapshot = result.events
                statusText = result.statusText
                showButtons = result.showButtons
            }
            val mgr = AppWidgetManager.getInstance(context)
            val comp = ComponentName(context, CalendarWidget::class.java)
            val ids = mgr.getAppWidgetIds(comp)
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.lv_widget)
            updateHeader(context)
        }
    }

    private data class Snapshot(
        val events: List<WidgetEvent>,
        val statusText: String?,
        val showButtons: Boolean,
    )

    private fun signedOutState() = Snapshot(
        events = emptyList(),
        statusText = resourceProvider.provideString(R.string.calendar_widget_please_log_in),
        showButtons = false,
    )

    private suspend fun buildSnapshot(context: Context): Snapshot {
        val userId =
            accountManager.getPrimaryAccount().firstOrNull()?.userId ?: return signedOutState()
        userAddressManager.getAddressesOrNull(userId)?.map { it.email }?.takeIfNotEmpty()
            ?: return signedOutState()

        val is24Hour = userSettingsRepository.getUserSettingsEntity(userId, database)
            .timeFormatIs24Hour(DateFormat.is24HourFormat(context))

        val zoneId = ZoneId.systemDefault()
        val fromDate = LocalDate.now(zoneId)
        val toDate = fromDate.plusDays(WIDGET_DAYS_AHEAD.toLong())

        val events = when (val res =
            getUiEventsUseCase.execute(userId, fromDate, toDate, zoneId.id).firstOrNull()) {
            is CalendarsRepository.GetEventsResult.Success<UiEvent> -> res.events
            else -> emptyList()
        }

        val widgetEvents = mutableListOf<WidgetEvent>()
        events.explodeDayByDay(fromDate, toDate, zoneId.id).toSortedMap().forEach { entry ->
            val upcomingEvents = entry.value
                .filter { !it.isInThePast() }
                .distinctBy { it.id to it.occurrenceNumber }
            val sortedEvents =
                upcomingEvents.sortedBy { "${!it.isAllDay}${it.dateStart.toEpochSecond()}${it.summary}" }
            sortedEvents.forEachIndexed { index, e ->
                widgetEvents.add(
                    e.toWidgetEvent(
                        happensOn = entry.key,
                        timeZoneId = zoneId.id,
                        showDateColumn = index == 0,
                        showBottomSpacing = index == sortedEvents.size - 1,
                        showNoEventsToday = false,
                        is24Hour = is24Hour
                    )
                )
            }
        }
        // add special dummy WidgetEvent if there are no Events to show for today
        if (widgetEvents.isNotEmpty() && widgetEvents.first().happensOn != fromDate) {
            widgetEvents.add(
                0,
                widgetEvents.first().copy(
                    happensOn = LocalDate.now(),
                    showDateColumn = true,
                    showBottomSpacing = true,
                    showNoEventsToday = true,
                    color = resourceProvider.provideString(R.color.separator_norm),
                )
            )
        }
        // hide spacing below the very last WidgetEvent on list
        widgetEvents.removeLastOrNull()
            ?.let { widgetEvents.add(it.copy(showBottomSpacing = false)) }
        return Snapshot(
            events = widgetEvents,
            statusText = if (widgetEvents.isEmpty()) resourceProvider.provideString(R.string.calendar_widget_no_upcoming_events) else null,
            showButtons = true,
        )
    }

    private fun updateHeader(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val comp = ComponentName(context, CalendarWidget::class.java)
        val ids = mgr.getAppWidgetIds(comp)
        ids.forEach { id ->
            val rv = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_widget)
            val text = statusText
            if (text != null) {
                rv.setTextViewText(R.id.tv_widget_main_info_text, text)
                rv.setViewVisibility(R.id.tv_widget_main_info_text, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.tv_widget_main_info_text, View.INVISIBLE)
            }
            rv.setViewVisibility(R.id.ib_plus, if (showButtons) View.VISIBLE else View.INVISIBLE)
            rv.setViewVisibility(R.id.ib_refresh, if (showButtons) View.VISIBLE else View.INVISIBLE)
            mgr.partiallyUpdateAppWidget(id, rv)
        }
    }

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
            summary = this.summary?.takeIfNotBlank()
                ?: resourceProvider.provideString(R.string.default_event_summary),
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
}
