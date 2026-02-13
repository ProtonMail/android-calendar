package me.proton.android.calendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.CalendarSettings.DAYS_IN_A_WEEK
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.getWeekStart
import me.proton.android.calendar.common.utils.AndroidUtils.getWeekStartDayOfWeek
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.android.calendar.domain.model.UiEvent
import me.proton.android.calendar.domain.usecase.GetUiEventsUseCase
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.android.calendar.presentation.main.viewModel.MainViewModel
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getPrimaryAccount
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import me.proton.core.util.kotlin.takeIfNotBlank
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * App Widget showing a month overview with event titles on each day.
 */
class CalendarMonthWidget : AppWidgetProvider(), KoinComponent {

    private val accountManager: AccountManager by inject()
    private val userSettingsRepository: UserSettingsRepository by inject()
    private val database: AppDatabase by inject()
    private val getUiEventsUseCase: GetUiEventsUseCase by inject()
    private val logger: Logger by inject()
    private val resourceProvider: ResourceProvider by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        intent?.let {
            when (it.action) {
                ACTION_MONTH_PREV, ACTION_MONTH_NEXT -> {
                    val appWidgetId = it.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                    val prefs = getPrefs(context)
                    val currentOffset = prefs.getInt(prefKey(appWidgetId), 0)
                    val newOffset = if (it.action == ACTION_MONTH_PREV) currentOffset - 1 else currentOffset + 1
                    prefs.edit().putInt(prefKey(appWidgetId), newOffset).apply()

                    val widgetManager = AppWidgetManager.getInstance(context)
                    showLoadingState(context, widgetManager, appWidgetId, newOffset)

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            updateAppWidget(context, widgetManager, appWidgetId)
                        } catch (e: Exception) {
                            logger.e("Error updating month widget", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
                ACTION_MONTH_TODAY -> {
                    val appWidgetId = it.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
                    getPrefs(context).edit().putInt(prefKey(appWidgetId), 0).apply()

                    val widgetManager = AppWidgetManager.getInstance(context)
                    showLoadingState(context, widgetManager, appWidgetId, 0)

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            updateAppWidget(context, widgetManager, appWidgetId)
                        } catch (e: Exception) {
                            logger.e("Error updating month widget", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
                AppWidgetManager.ACTION_APPWIDGET_UPDATE,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_LOCALE_CHANGED -> {
                    val widgetManager = AppWidgetManager.getInstance(context)
                    val widgetComponent = ComponentName(context, CalendarMonthWidget::class.java)
                    val appWidgetIds = widgetManager.getAppWidgetIds(widgetComponent)

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            for (appWidgetId in appWidgetIds) {
                                updateAppWidget(context, widgetManager, appWidgetId)
                            }
                        } catch (e: Exception) {
                            logger.e("Error updating month widget", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
                else -> super.onReceive(context, intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = getPrefs(context)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove(prefKey(id))
        }
        editor.apply()
        super.onDeleted(context, appWidgetIds)
    }

    /**
     * Immediately pushes a loading state: updates the month title and shows a loading overlay
     * so the user gets instant feedback while events are being fetched.
     */
    private fun showLoadingState(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, monthOffset: Int) {
        val remoteViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_month_widget)
        val today = LocalDate.now(ZoneId.systemDefault())
        val firstOfMonth = today.withDayOfMonth(1).plusMonths(monthOffset.toLong())
        val monthYearFormat = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
        remoteViews.setTextViewText(R.id.tv_month_title, firstOfMonth.format(monthYearFormat))
        remoteViews.setViewVisibility(R.id.tv_month_widget_info_text, View.VISIBLE)
        remoteViews.setTextViewText(R.id.tv_month_widget_info_text, context.getString(R.string.month_widget_loading))
        remoteViews.setViewVisibility(R.id.ll_month_grid, View.INVISIBLE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, remoteViews)
    }

    private suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val remoteViews = RemoteViews(BuildConfig.APPLICATION_ID, R.layout.calendar_month_widget)

        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

        val mutablePendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val monthOffset = getPrefs(context).getInt(prefKey(appWidgetId), 0)
        val firstOfMonth = today.withDayOfMonth(1).plusMonths(monthOffset.toLong())

        // Header title click -> open app to the displayed month
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            data = Navigation.Deeplink.toMonth(firstOfMonth)
            action = MainViewModel.INTENT_ACTION_SHOW_DAY
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val openAppPendingIntent = PendingIntent.getActivity(context, appWidgetId * 100 + HEADER_REQUEST_CODE, openAppIntent, pendingIntentFlags)
        remoteViews.setOnClickPendingIntent(R.id.tv_month_title, openAppPendingIntent)
        remoteViews.setOnClickPendingIntent(R.id.tv_month_widget_info_text, openAppPendingIntent)

        // Previous month button
        val prevIntent = Intent(context, CalendarMonthWidget::class.java).apply {
            action = ACTION_MONTH_PREV
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        remoteViews.setOnClickPendingIntent(R.id.ib_month_prev,
            PendingIntent.getBroadcast(context, appWidgetId * 100 + PREV_REQUEST_CODE, prevIntent, pendingIntentFlags))

        // Next month button
        val nextIntent = Intent(context, CalendarMonthWidget::class.java).apply {
            action = ACTION_MONTH_NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        remoteViews.setOnClickPendingIntent(R.id.ib_month_next,
            PendingIntent.getBroadcast(context, appWidgetId * 100 + NEXT_REQUEST_CODE, nextIntent, pendingIntentFlags))

        // Refresh button
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, appWidgetId * 100 + REFRESH_REQUEST_CODE, createWidgetRefreshIntent(context), mutablePendingIntentFlags
        )
        remoteViews.setOnClickPendingIntent(R.id.ib_month_refresh, refreshPendingIntent)

        // New event button
        val newEventIntent = Intent(context, MainActivity::class.java).apply {
            action = MainViewModel.INTENT_ACTION_NEW_EVENT
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val newEventPendingIntent = PendingIntent.getActivity(context, appWidgetId * 100 + NEW_EVENT_REQUEST_CODE, newEventIntent, pendingIntentFlags)
        remoteViews.setOnClickPendingIntent(R.id.ib_month_plus, newEventPendingIntent)

        // Load user
        val userId = accountManager.getPrimaryAccount().firstOrNull()?.userId

        if (userId == null) {
            remoteViews.setViewVisibility(R.id.tv_month_widget_info_text, View.VISIBLE)
            remoteViews.setTextViewText(R.id.tv_month_widget_info_text, context.getString(R.string.calendar_widget_please_log_in))
            remoteViews.setViewVisibility(R.id.ll_month_grid, View.INVISIBLE)
            remoteViews.setViewVisibility(R.id.ll_dow_labels, View.INVISIBLE)
            remoteViews.setViewVisibility(R.id.ib_month_plus, View.INVISIBLE)
            remoteViews.setViewVisibility(R.id.ib_month_refresh, View.INVISIBLE)
            remoteViews.setViewVisibility(R.id.ib_month_prev, View.INVISIBLE)
            remoteViews.setViewVisibility(R.id.ib_month_next, View.INVISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            return
        }

        remoteViews.setViewVisibility(R.id.ib_month_plus, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.ib_month_refresh, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.ib_month_prev, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.ib_month_next, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.ll_month_grid, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.ll_dow_labels, View.VISIBLE)
        remoteViews.setViewVisibility(R.id.tv_month_widget_info_text, View.GONE)

        // Week start preference
        val weekStartIndex = userSettingsRepository.getWeekStart(userId, database)
        val startWeekOn = getWeekStartDayOfWeek(weekStartIndex)

        // Calculate grid
        val gridData = calculateMonthGrid(firstOfMonth, startWeekOn)

        // Month title
        val monthYearFormat = DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())
        remoteViews.setTextViewText(R.id.tv_month_title, firstOfMonth.format(monthYearFormat))

        // Day-of-week labels
        for (i in 0 until DAYS_IN_A_WEEK) {
            remoteViews.setTextViewText(DOW_LABEL_IDS[i], gridData.dayOfWeekLabels[i])
        }

        // Fetch events
        val events = getUiEventsUseCase.execute(
            userId, gridData.fromDate, gridData.toDate, zoneId.id
        ).firstOrNull()

        val uiEvents = when (events) {
            is CalendarsRepository.GetEventsResult.Success<UiEvent> -> events.events
            else -> emptyList()
        }

        // Build per-day event list: date -> list of (summary, color)
        val eventsPerDay = buildEventsPerDay(uiEvents, zoneId.id)

        // Colors
        val textNormColor = ContextCompat.getColor(context, R.color.text_norm)
        val textHintColor = ContextCompat.getColor(context, R.color.text_hint)
        val textInvertedColor = ContextCompat.getColor(context, R.color.text_on_calendar_color)

        // Populate 42 cells
        for (i in 0 until GRID_SIZE) {
            val date = gridData.dates[i]
            val dayTextId = DAY_TEXT_IDS[i]
            val cellId = CELL_IDS[i]

            // Day number
            remoteViews.setTextViewText(dayTextId, "${date.dayOfMonth}")

            // Today / current month / other month styling
            when {
                date == today -> {
                    remoteViews.setInt(dayTextId, "setBackgroundResource", R.drawable.shape_widget_today_circle)
                    remoteViews.setTextColor(dayTextId, textInvertedColor)
                }
                date.month == firstOfMonth.month -> {
                    remoteViews.setInt(dayTextId, "setBackgroundResource", 0)
                    remoteViews.setTextColor(dayTextId, textNormColor)
                }
                else -> {
                    remoteViews.setInt(dayTextId, "setBackgroundResource", 0)
                    remoteViews.setTextColor(dayTextId, textHintColor)
                }
            }

            // Event text slots
            val dayEvents = eventsPerDay[date] ?: emptyList()
            for (slot in 0 until MAX_EVENT_SLOTS) {
                val evId = EV_IDS[i][slot]
                if (slot < dayEvents.size) {
                    val (summary, color) = dayEvents[slot]
                    remoteViews.setViewVisibility(evId, View.VISIBLE)
                    remoteViews.setTextViewText(evId, summary)
                    remoteViews.setInt(evId, "setBackgroundColor", adjustAlpha(Color.parseColor(color), 200))
                } else {
                    remoteViews.setViewVisibility(evId, View.GONE)
                }
            }

            // "More" indicator
            val moreId = MORE_IDS[i]
            val remaining = dayEvents.size - MAX_EVENT_SLOTS
            if (remaining > 0) {
                remoteViews.setViewVisibility(moreId, View.VISIBLE)
                remoteViews.setTextViewText(moreId, "+$remaining")
            } else {
                remoteViews.setViewVisibility(moreId, View.GONE)
            }

            // Click handler -> open app to that day
            val dayIntent = Intent(context, MainActivity::class.java).apply {
                data = Navigation.Deeplink.toMonth(date)
                action = MainViewModel.INTENT_ACTION_SHOW_DAY
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val dayPendingIntent = PendingIntent.getActivity(
                context, appWidgetId * 100 + i, dayIntent, pendingIntentFlags
            )
            remoteViews.setOnClickPendingIntent(cellId, dayPendingIntent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    companion object {
        private const val GRID_SIZE = 42
        private const val MAX_EVENT_SLOTS = 3

        private const val PREFS_NAME = "month_widget_prefs"
        private const val PREF_KEY_MONTH_OFFSET = "month_offset_"

        const val ACTION_MONTH_PREV = "me.proton.android.calendar.ACTION_MONTH_PREV"
        const val ACTION_MONTH_NEXT = "me.proton.android.calendar.ACTION_MONTH_NEXT"
        const val ACTION_MONTH_TODAY = "me.proton.android.calendar.ACTION_MONTH_TODAY"

        private const val HEADER_REQUEST_CODE = 50
        private const val REFRESH_REQUEST_CODE = 51
        private const val NEW_EVENT_REQUEST_CODE = 52
        private const val PREV_REQUEST_CODE = 53
        private const val NEXT_REQUEST_CODE = 54

        private fun getPrefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun prefKey(appWidgetId: Int) = "$PREF_KEY_MONTH_OFFSET$appWidgetId"

        private val DOW_LABEL_IDS = intArrayOf(
            R.id.tv_dow_0, R.id.tv_dow_1, R.id.tv_dow_2, R.id.tv_dow_3,
            R.id.tv_dow_4, R.id.tv_dow_5, R.id.tv_dow_6
        )

        private val CELL_IDS = intArrayOf(
            R.id.cell_0, R.id.cell_1, R.id.cell_2, R.id.cell_3, R.id.cell_4, R.id.cell_5, R.id.cell_6,
            R.id.cell_7, R.id.cell_8, R.id.cell_9, R.id.cell_10, R.id.cell_11, R.id.cell_12, R.id.cell_13,
            R.id.cell_14, R.id.cell_15, R.id.cell_16, R.id.cell_17, R.id.cell_18, R.id.cell_19, R.id.cell_20,
            R.id.cell_21, R.id.cell_22, R.id.cell_23, R.id.cell_24, R.id.cell_25, R.id.cell_26, R.id.cell_27,
            R.id.cell_28, R.id.cell_29, R.id.cell_30, R.id.cell_31, R.id.cell_32, R.id.cell_33, R.id.cell_34,
            R.id.cell_35, R.id.cell_36, R.id.cell_37, R.id.cell_38, R.id.cell_39, R.id.cell_40, R.id.cell_41
        )

        private val DAY_TEXT_IDS = intArrayOf(
            R.id.day_text_0, R.id.day_text_1, R.id.day_text_2, R.id.day_text_3, R.id.day_text_4, R.id.day_text_5, R.id.day_text_6,
            R.id.day_text_7, R.id.day_text_8, R.id.day_text_9, R.id.day_text_10, R.id.day_text_11, R.id.day_text_12, R.id.day_text_13,
            R.id.day_text_14, R.id.day_text_15, R.id.day_text_16, R.id.day_text_17, R.id.day_text_18, R.id.day_text_19, R.id.day_text_20,
            R.id.day_text_21, R.id.day_text_22, R.id.day_text_23, R.id.day_text_24, R.id.day_text_25, R.id.day_text_26, R.id.day_text_27,
            R.id.day_text_28, R.id.day_text_29, R.id.day_text_30, R.id.day_text_31, R.id.day_text_32, R.id.day_text_33, R.id.day_text_34,
            R.id.day_text_35, R.id.day_text_36, R.id.day_text_37, R.id.day_text_38, R.id.day_text_39, R.id.day_text_40, R.id.day_text_41
        )

        // ev_N_0, ev_N_1, ev_N_2 for each of 42 cells
        private val EV_IDS = arrayOf(
            intArrayOf(R.id.ev_0_0, R.id.ev_0_1, R.id.ev_0_2), intArrayOf(R.id.ev_1_0, R.id.ev_1_1, R.id.ev_1_2),
            intArrayOf(R.id.ev_2_0, R.id.ev_2_1, R.id.ev_2_2), intArrayOf(R.id.ev_3_0, R.id.ev_3_1, R.id.ev_3_2),
            intArrayOf(R.id.ev_4_0, R.id.ev_4_1, R.id.ev_4_2), intArrayOf(R.id.ev_5_0, R.id.ev_5_1, R.id.ev_5_2),
            intArrayOf(R.id.ev_6_0, R.id.ev_6_1, R.id.ev_6_2), intArrayOf(R.id.ev_7_0, R.id.ev_7_1, R.id.ev_7_2),
            intArrayOf(R.id.ev_8_0, R.id.ev_8_1, R.id.ev_8_2), intArrayOf(R.id.ev_9_0, R.id.ev_9_1, R.id.ev_9_2),
            intArrayOf(R.id.ev_10_0, R.id.ev_10_1, R.id.ev_10_2), intArrayOf(R.id.ev_11_0, R.id.ev_11_1, R.id.ev_11_2),
            intArrayOf(R.id.ev_12_0, R.id.ev_12_1, R.id.ev_12_2), intArrayOf(R.id.ev_13_0, R.id.ev_13_1, R.id.ev_13_2),
            intArrayOf(R.id.ev_14_0, R.id.ev_14_1, R.id.ev_14_2), intArrayOf(R.id.ev_15_0, R.id.ev_15_1, R.id.ev_15_2),
            intArrayOf(R.id.ev_16_0, R.id.ev_16_1, R.id.ev_16_2), intArrayOf(R.id.ev_17_0, R.id.ev_17_1, R.id.ev_17_2),
            intArrayOf(R.id.ev_18_0, R.id.ev_18_1, R.id.ev_18_2), intArrayOf(R.id.ev_19_0, R.id.ev_19_1, R.id.ev_19_2),
            intArrayOf(R.id.ev_20_0, R.id.ev_20_1, R.id.ev_20_2), intArrayOf(R.id.ev_21_0, R.id.ev_21_1, R.id.ev_21_2),
            intArrayOf(R.id.ev_22_0, R.id.ev_22_1, R.id.ev_22_2), intArrayOf(R.id.ev_23_0, R.id.ev_23_1, R.id.ev_23_2),
            intArrayOf(R.id.ev_24_0, R.id.ev_24_1, R.id.ev_24_2), intArrayOf(R.id.ev_25_0, R.id.ev_25_1, R.id.ev_25_2),
            intArrayOf(R.id.ev_26_0, R.id.ev_26_1, R.id.ev_26_2), intArrayOf(R.id.ev_27_0, R.id.ev_27_1, R.id.ev_27_2),
            intArrayOf(R.id.ev_28_0, R.id.ev_28_1, R.id.ev_28_2), intArrayOf(R.id.ev_29_0, R.id.ev_29_1, R.id.ev_29_2),
            intArrayOf(R.id.ev_30_0, R.id.ev_30_1, R.id.ev_30_2), intArrayOf(R.id.ev_31_0, R.id.ev_31_1, R.id.ev_31_2),
            intArrayOf(R.id.ev_32_0, R.id.ev_32_1, R.id.ev_32_2), intArrayOf(R.id.ev_33_0, R.id.ev_33_1, R.id.ev_33_2),
            intArrayOf(R.id.ev_34_0, R.id.ev_34_1, R.id.ev_34_2), intArrayOf(R.id.ev_35_0, R.id.ev_35_1, R.id.ev_35_2),
            intArrayOf(R.id.ev_36_0, R.id.ev_36_1, R.id.ev_36_2), intArrayOf(R.id.ev_37_0, R.id.ev_37_1, R.id.ev_37_2),
            intArrayOf(R.id.ev_38_0, R.id.ev_38_1, R.id.ev_38_2), intArrayOf(R.id.ev_39_0, R.id.ev_39_1, R.id.ev_39_2),
            intArrayOf(R.id.ev_40_0, R.id.ev_40_1, R.id.ev_40_2), intArrayOf(R.id.ev_41_0, R.id.ev_41_1, R.id.ev_41_2),
        )

        private val MORE_IDS = intArrayOf(
            R.id.more_0, R.id.more_1, R.id.more_2, R.id.more_3, R.id.more_4, R.id.more_5, R.id.more_6,
            R.id.more_7, R.id.more_8, R.id.more_9, R.id.more_10, R.id.more_11, R.id.more_12, R.id.more_13,
            R.id.more_14, R.id.more_15, R.id.more_16, R.id.more_17, R.id.more_18, R.id.more_19, R.id.more_20,
            R.id.more_21, R.id.more_22, R.id.more_23, R.id.more_24, R.id.more_25, R.id.more_26, R.id.more_27,
            R.id.more_28, R.id.more_29, R.id.more_30, R.id.more_31, R.id.more_32, R.id.more_33, R.id.more_34,
            R.id.more_35, R.id.more_36, R.id.more_37, R.id.more_38, R.id.more_39, R.id.more_40, R.id.more_41
        )

        private fun createWidgetRefreshIntent(context: Context): Intent {
            val widgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, CalendarMonthWidget::class.java)
            val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)

            return Intent(context, CalendarMonthWidget::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
        }

        fun sendRefreshBroadcast(context: Context) {
            context.sendBroadcast(createWidgetRefreshIntent(context))
        }

        /**
         * Apply alpha to a color (0-255).
         */
        private fun adjustAlpha(color: Int, alpha: Int): Int {
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}

internal data class MonthGridData(
    val dates: List<LocalDate>,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val currentMonth: Int,
    val dayOfWeekLabels: List<String>,
)

internal fun calculateMonthGrid(firstOfMonth: LocalDate, startWeekOn: DayOfWeek): MonthGridData {
    val firstDayOfWeekNumber = firstOfMonth.dayOfWeek.value - startWeekOn.value
    val firstDayOffset = if (firstDayOfWeekNumber < 0) firstDayOfWeekNumber + 7 else firstDayOfWeekNumber

    val gridStart = firstOfMonth.minusDays(firstDayOffset.toLong())
    val dates = (0 until 42).map { gridStart.plusDays(it.toLong()) }

    val dayOfWeekLabels = (0 until 7).map {
        startWeekOn.plus(it.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }

    return MonthGridData(
        dates = dates,
        fromDate = dates.first(),
        toDate = dates.last(),
        currentMonth = firstOfMonth.monthValue,
        dayOfWeekLabels = dayOfWeekLabels,
    )
}

/**
 * Builds a map of date -> list of (summary, color) for events on that day.
 * Events are sorted: all-day first, then by start time.
 */
internal fun buildEventsPerDay(
    events: List<UiEvent>,
    timeZoneId: String,
): Map<LocalDate, List<Pair<String, String>>> {

    val result = mutableMapOf<LocalDate, MutableList<Triple<String, String, Long>>>()

    events.forEach { event ->
        val summary = event.summary?.takeIfNotBlank() ?: "(No title)"
        val color = event.displayColor
        val sortKey = if (event.isAllDay) 0L else event.dateStart.toEpochSecond()

        val partTimeEndsOnMidnight = !event.isAllDay &&
            event.dateEnd.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalTime() == LocalTime.MIDNIGHT
        var start = event.dateStart.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate()
        val end = event.dateEnd.withZoneSameInstant(ZoneId.of(timeZoneId)).toLocalDate()

        while (!start.isAfter(end)) {
            result.getOrPut(start) { mutableListOf() }.add(Triple(summary, color, sortKey))
            start = start.plusDays(1)
            if (start == end && (event.isAllDay || partTimeEndsOnMidnight)) break
        }
    }

    // Sort: all-day (sortKey=0) first, then by start time; deduplicate by summary
    return result.mapValues { (_, triples) ->
        triples
            .sortedBy { it.third }
            .distinctBy { it.first }
            .map { Pair(it.first, it.second) }
    }
}
