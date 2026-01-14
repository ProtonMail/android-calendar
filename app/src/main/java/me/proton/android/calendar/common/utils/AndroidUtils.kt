package me.proton.android.calendar.common.utils

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.provider.ContactsContract
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.Transformation
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.appcompat.widget.ListPopupWindow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.databinding.BindingAdapter
import biweekly.component.VAlarm
import biweekly.parameter.ParticipationStatus
import biweekly.property.Attendee
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.Recurrence
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.proton.android.calendar.R
import me.proton.android.calendar.common.Animation.HEIGHT_CHANGE_DURATION
import me.proton.android.calendar.common.CLICK_INTERVAL_MS
import me.proton.android.calendar.common.MAX_ANIM_DURATION
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.format
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatDate
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.formatTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toBiweeklyDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toDayOfWeek
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.toZonedDateTime
import me.proton.android.calendar.common.utils.DateTimeUtilsImpl.weekInMonth
import me.proton.android.calendar.databinding.DialogCalendarListBinding
import me.proton.android.calendar.databinding.ItemCalendarDialogBinding
import me.proton.android.calendar.databinding.ItemCalendarPickerBinding
import me.proton.android.calendar.databinding.ItemPickerDialogBinding
import me.proton.android.calendar.domain.model.Calendar
import me.proton.android.calendar.domain.model.Event
import me.proton.android.calendar.domain.usecase.ObtainSendPreferencesUseCase
import me.proton.core.presentation.utils.normSnack
import okhttp3.internal.toHexString
import java.text.CharacterIterator
import java.text.Normalizer
import java.text.StringCharacterIterator
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.Locale.getDefault
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.min

object AndroidUtils {

    fun displayTimePicker(
        context: Context,
        initialTime: LocalTime?,
        is24Hour: Boolean,
        callback: (result: LocalTime) -> Unit
    ) {
        val immutableInitialTime = initialTime?: LocalTime.now()
        val timePickerDialog = TimePickerDialog(context, 0,
            { _, hourOfDay, minute ->
                callback(LocalTime.of(hourOfDay, minute))
            },
            immutableInitialTime.hour,
            immutableInitialTime.minute,
            is24Hour)
        timePickerDialog.show()
    }

    fun displayDatePicker(
        context: Context,
        firstDayOfWeek: java.time.DayOfWeek,
        initialDate: LocalDate?,
        minDate: LocalDate? = null,
        maxDate: LocalDate? = null,
        callback: (result: LocalDate) -> Unit
    ) {
        val immutableInitialDate = initialDate?: LocalDate.now()
        val datePickerDialog = DatePickerDialog(context, 0,
            { _, year, month, dayOfMonth ->
                callback(LocalDate.of(year, month + 1, dayOfMonth))
            },
            immutableInitialDate.year,
            immutableInitialDate.monthValue - 1,
            immutableInitialDate.dayOfMonth)
        minDate?.let {
            datePickerDialog.datePicker.minDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        maxDate?.let {
            datePickerDialog.datePicker.maxDate = it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        datePickerDialog.datePicker.firstDayOfWeek = firstDayOfWeek.toBiweeklyDayOfWeek().calendarConstant
        datePickerDialog.show()
    }

    fun displaySingleChoicePicker(
        context: Context,
        title: String?,
        items: Array<String>,
        selectedIndex: Int,
        callback: (selectedIndex: Int) -> Unit
    ) {
        val builder = MaterialAlertDialogBuilder(context)
        title?.apply { builder.setTitle(this) }
        builder.setSingleChoiceItems(items, selectedIndex) { dialog, item ->
            callback(item)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.dialog_button_cancel, null)
        builder.create().show()
    }

    fun displaySingleChoiceConfirmationPicker(
        context: Context,
        title: String?,
        items: Array<String>,
        selectedIndex: Int,
        callback: (selectedIndex: Int, isCancel: Boolean) -> Unit
    ) {
        var selectedItem = 0
        val builder = MaterialAlertDialogBuilder(context)
        title?.apply { builder.setTitle(this) }
        builder.setSingleChoiceItems(items, selectedIndex) { _, item ->
            selectedItem = item
        }
        builder.setPositiveButton(R.string.dialog_button_ok) { dialog, _ ->
            callback(selectedItem, false)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.dialog_button_cancel) { _, _ ->
            callback(selectedIndex, true)
        }
        builder.setOnCancelListener { _ ->
            callback(selectedIndex, true)
        }
        builder.create().show()
    }

    fun displaySimpleOkAlert(
        context: Context,
        message: String,
        title: String? = null
    ) {
        val builder = MaterialAlertDialogBuilder(context)
        title?.apply { builder.setTitle(this) }
        builder.setMessage(message)
        builder.setPositiveButton(R.string.dialog_button_ok, null)
        builder.create().show()
    }

    fun displayPickerDialog(
        context: Context,
        title: String?,
        items: Array<String>,
        initiallySelectedIndex: Int,
        callback: (selectedIndex: Int) -> Unit
    ) {

        val adapter = object : ArrayAdapter<String>(context, R.layout.item_picker_dialog) {

            lateinit var dialog: DialogInterface
            var selectedIndex = initiallySelectedIndex

            override fun getCount(): Int = items.size

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    view = ItemPickerDialogBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).root
                }

                view.findViewById<AppCompatCheckedTextView>(R.id.ctv_item_name).apply {
                    text = items[position]
                    tag = position
                    isChecked = position == selectedIndex
                    setOnSingleClickListener() {
                        selectedIndex = it.tag as Int
                        notifyDataSetChanged()
                        callback(selectedIndex)
                        dialog.dismiss()
                    }
                }

                return view
            }

        }

        val builder = MaterialAlertDialogBuilder(context)
        title?.apply { builder.setTitle(this) }
        builder.setAdapter(adapter, null)
        builder.setNegativeButton(R.string.dialog_button_close, null)
        val dialog = builder.create()
        adapter.dialog = dialog
        dialog.show()
    }

    fun displayCalendarPicker(
        context: Context,
        title: String?,
        items: Array<Calendar>,
        initiallySelectedIndex: Int,
        callback: (selectedIndex: Int) -> Unit
    ) {

        val adapter = object : ArrayAdapter<String>(context, R.layout.item_calendar_picker) {

            lateinit var dialog: DialogInterface
            var selectedIndex = initiallySelectedIndex

            override fun getCount(): Int = items.size

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    view = ItemCalendarPickerBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).root
                }

                view.findViewById<AppCompatCheckedTextView>(R.id.ctv_calendar_name).apply {
                    text = items[position].name
                    tag = position
                    isChecked = position == selectedIndex
//                        compoundDrawablesRelative?.firstOrNull().setTint(Color.parseColor(items[position].color))
                    setOnSingleClickListener() {
                        selectedIndex = it.tag as Int
                        notifyDataSetChanged()
                        callback(selectedIndex)
                        dialog.dismiss()
                    }
                }

                view.findViewById<ImageView>(R.id.iv_calendar_circle).drawable.setTint(
                    Color.parseColor(
                        items[position].color
                    )
                )

                return view
            }

        }

        val builder = MaterialAlertDialogBuilder(context)
        title?.apply { builder.setTitle(this) }
        builder.setAdapter(adapter, null)
        builder.setNegativeButton(R.string.dialog_button_cancel, null)
        val dialog = builder.create()
        adapter.dialog = dialog
        dialog.show()
    }

    fun Context.displayCalendarListMaterialDialog(
        title: Int,
        message: Int,
        cancellable: Boolean,
        items: List<Calendar>,
        callback: DialogInterface.OnClickListener
    ) {
        val materialDialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setCancelable(cancellable)
            .setPositiveButton(R.string.bootstrap_error_continue_button, callback)

        val adapter = object : ArrayAdapter<Calendar>(this, R.layout.item_calendar_dialog, items) {

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                var view = convertView
                if (view == null) {
                    view = ItemCalendarDialogBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).root
                }

                view.findViewById<TextView>(R.id.item_calendar_dialog_title).apply {
                    text = getItem(position)?.name
                    tag = position
                }

                view.findViewById<ImageView>(R.id.item_calendar_dialog_icon).drawable.setTint(
                    Color.parseColor(
                        getItem(position)?.color
                    )
                )

                view.isClickable = false

                return view
            }

        }

        val viewBinding = DialogCalendarListBinding.inflate(LayoutInflater.from(this), null, false)

        viewBinding.dialogCalendarListHeader.text = getString(message)

        viewBinding.dialogCalendarListRecyclerView.adapter = adapter
        viewBinding.dialogCalendarListRecyclerView.divider = null

        materialDialogBuilder.setView(viewBinding.root)
        materialDialogBuilder.show()
    }

    fun formatRecurrence(resources: Resources, event: Event, timeZoneId: String): String? {

        val recurrence = event.iCalEvent.recurrenceRule?.value
        if (recurrence != null) {

            val label = listOfNotNull(
                recurrence.frequency?.let { // non-custom recurrence

                    val onDaysOfWeek = if (recurrence.byDay?.size == 7) {
                        resources.getString(R.string.event_recurrence_weekly_on_all_days)
                    } else recurrence.byDay?.sortedBy({ if (it.day == DayOfWeek.SUNDAY) 7 else it.day.ordinal /*TODO take start day of week into account*/ })
                        ?.mapIndexedNotNull { index, byDay ->

                            val dayOfWeekAsWord = byDay.day.toDayOfWeek().format()

                            val dayNumber: Int? = if (recurrence.bySetPos.isNotEmpty() && index <= recurrence.bySetPos.lastIndex) {
                                recurrence.bySetPos[index]
                            } else byDay.num

                            val dayOrdinal = if (dayNumber == null) {
                                null
                            } else if (dayNumber > 0) {
                                resources.getStringArray(R.array.ordinals_as_words)
                                    .getOrNull(dayNumber)
                            } else {
                                resources.getStringArray(R.array.ordinals_as_words_backwards)
                                    .getOrNull(dayNumber * -1)
                            }

                            "${if (dayOrdinal != null) "$dayOrdinal " else ""}$dayOfWeekAsWord"

                        }?.joinToString(separator = ", ")
                    // TODO handle .byMonthDay, .byYearDay when needed

                    when (it) {
                        Frequency.DAILY -> {
                            val repeat =
                                if (recurrence.interval == null || recurrence.interval == 1) {
                                    resources.getString(R.string.event_recurrence_daily)
                                } else {
                                    resources.getQuantityString(
                                        R.plurals.event_recurrence_every_some_day,
                                        recurrence.interval,
                                        recurrence.interval,
                                        resources.getQuantityString(
                                            R.plurals.plural_day,
                                            recurrence.interval,
                                            recurrence.interval
                                        ) /*TODO remove double quantity param, it's not needed anymore because we're not formatting plural string*/
                                    )
                                }
                            repeat
                        }
                        Frequency.WEEKLY -> {
                            val repeat =
                                if (recurrence.interval == null || recurrence.interval == 1) {
                                    resources.getString(R.string.event_recurrence_weekly)
                                } else {
                                    resources.getQuantityString(
                                        R.plurals.event_recurrence_every_some_week,
                                        recurrence.interval,
                                        recurrence.interval,
                                        resources.getQuantityString(
                                            R.plurals.plural_week,
                                            recurrence.interval,
                                            recurrence.interval
                                        )
                                    )
                                }
                            resources.getString(
                                R.string.event_recurrence_occurs_on_day_of_week,
                                repeat,
                                if (onDaysOfWeek.isNullOrBlank()) {
                                    (event.getStart(timeZoneId).dayOfWeek).format()
                                } else onDaysOfWeek
                            )
                        }
                        Frequency.MONTHLY -> {
                            val repeat =
                                if (recurrence.interval == null || recurrence.interval == 1) {
                                    resources.getString(R.string.event_recurrence_monthly)
                                } else {
                                    resources.getQuantityString(
                                        R.plurals.event_recurrence_every_some_month,
                                        recurrence.interval,
                                        recurrence.interval,
                                        resources.getQuantityString(
                                            R.plurals.plural_month,
                                            recurrence.interval,
                                            recurrence.interval
                                        )
                                    )
                                }

                            if (onDaysOfWeek.isNullOrBlank()) {
                                resources.getString(
                                    R.string.event_recurrence_occurs_on_day_of_month,
                                    repeat,
                                    event.getStart(timeZoneId)
                                        .toLocalDate().dayOfMonth
                                )
                            } else {
                                resources.getString(
                                    R.string.event_recurrence_occurs_on_day_of_week_full_words,
                                    repeat,
                                    onDaysOfWeek
                                )
                            }
                        }
                        Frequency.YEARLY -> {
                            val repeat =
                                if (recurrence.interval == null || recurrence.interval == 1) {
                                    resources.getString(R.string.event_recurrence_yearly)
                                } else {
                                    resources.getQuantityString(
                                        R.plurals.event_recurrence_every_some_year,
                                        recurrence.interval,
                                        recurrence.interval,
                                        resources.getQuantityString(
                                            R.plurals.plural_year,
                                            recurrence.interval,
                                            recurrence.interval
                                        )
                                    )
                                }
                            repeat
                        }
                        else -> null
                    }
                },
                recurrence.count?.let {
                    "${if (it > 1) "$it " else ""}${
                        resources.getQuantityString(
                            R.plurals.plural_recurrence_count,
                            it,
                            it
                        )
                    }"
                },
                recurrence.until?.let {
                    resources.getString(
                        R.string.event_recurrence_until,
                        it.toZonedDateTime(timeZoneId).formatDate(timeZoneId)
                    )
                },
            ).joinToString(separator = ", ")

            val startTimeZone = event.iCalendar.timezoneInfo?.getTimezone(event.iCalendar.events.first().dateStart)?.timeZone
            val formatTimeZone = TimeZone.getTimeZone(timeZoneId)

            TimberLogger.d("timezone start=${startTimeZone} format=${formatTimeZone}")

            return if (
                shouldShowRecurrenceTimeZone(recurrence)
                && startTimeZone?.id != null
                && formatTimeZone.id != startTimeZone.id
            ) {
                "$label (${formatTimeZone.id})"
            } else {
                label
            }
        }

        return null
    }

    private fun shouldShowRecurrenceTimeZone(recurrence: Recurrence): Boolean {
        return when (recurrence.frequency) {
            Frequency.DAILY -> {
                recurrence.until != null
            }
            Frequency.WEEKLY -> true
            Frequency.MONTHLY -> true
            Frequency.YEARLY -> {
                recurrence.until != null
            }
            else -> false
        }
    }

    fun ObtainSendPreferencesUseCase.Result.Error.formatSendPreferencesError(): Int =
        when (this) {
            ObtainSendPreferencesUseCase.Result.Error.AddressDisabled -> R.string.event_send_prefs_error_address_disabled
            ObtainSendPreferencesUseCase.Result.Error.GettingContactPreferences -> R.string.event_send_prefs_error_getting_contact
            ObtainSendPreferencesUseCase.Result.Error.NetworkError -> R.string.event_send_prefs_error_network
            ObtainSendPreferencesUseCase.Result.Error.TrustedKeysInvalid -> R.string.event_send_prefs_trusted_keys_invalid
            ObtainSendPreferencesUseCase.Result.Error.PublicKeysInvalid -> R.string.event_send_prefs_public_keys_invalid
            ObtainSendPreferencesUseCase.Result.Error.NoCorrectlySignedTrustedKeys -> R.string.event_send_prefs_trusted_keys_signature_invalid
        }

    fun formatAlarm(
        resources: Resources,
        isAllDay: Boolean,
        is24Hour: Boolean,
        startZonedDateTime: ZonedDateTime,
        alarm: VAlarm
    ): String? {

        val trigger = alarm.trigger.duration
        return if (isAllDay) { // example: "1 day before at 9:00"

            val startDate = if (trigger.isPrior) {
                startZonedDateTime
                    .minus(Period.ofWeeks(trigger.weeks ?: 0))
                    .minus(Period.ofDays(trigger.days ?: 0))
                    .minus(Duration.ofHours(trigger.hours?.toLong() ?: 0L))
                    .minus(Duration.ofMinutes(trigger.minutes?.toLong() ?: 0L))
            } else {
                startZonedDateTime
                    .plus(Period.ofWeeks(trigger.weeks ?: 0))
                    .plus(Period.ofDays(trigger.days ?: 0))
                    .plus(Duration.ofHours(trigger.hours?.toLong() ?: 0L))
                    .plus(Duration.ofMinutes(trigger.minutes?.toLong() ?: 0L))
            }

            TimberLogger.d("trigger weeks: ${trigger.weeks}, days: ${trigger.days}")

            val onTheSameDay = !trigger.isPrior // technically this means "not before" but we don't support "after" alarms

            // magic number 1 is needed for days, because 5 hours before midnight will actually be "1 day before" in "human speak"
            var daysFormatted: Int? = if (trigger.days != null) { // add 1 day if time of day exists and is different than midnight
                trigger.days + (if ((trigger.hours != null && trigger.hours.toInt() != 0) || (trigger.minutes != null && trigger.minutes?.toInt() != 0)) 1 else 0)
            } else {
                if (trigger.hours != null || trigger.minutes != null) {
                    1
                } else null
            }

            if (onTheSameDay) daysFormatted = null

            var weeksFormatted = trigger.weeks?.toInt()
            if (daysFormatted == 7) {
                weeksFormatted = (weeksFormatted ?: 0) + 1
                daysFormatted = null
            }

            val label = listOfNotNull(
                if (onTheSameDay) {
                    resources.getString(R.string.event_alarm_label_on_the_same_day)
                } else null,
                weeksFormatted?.let {
                    "$it ${
                        resources.getQuantityString(
                            R.plurals.plural_week,
                            it,
                            it
                        )
                    }"
                },
                daysFormatted?.let {
                    "$it ${
                        resources.getQuantityString(
                            R.plurals.plural_day,
                            it,
                            it
                        )
                    }"
                }
            ).joinToString(separator = ", ")

            val alarmTime = startDate.toLocalTime().formatTime(is24Hour)

            if (label.isBlank()) {
                null
            } else if (alarm.action?.isEmail == true) {
                resources.getString(
                    if (onTheSameDay) R.string.event_alarm_label_not_before_with_time_by_email else R.string.event_alarm_label_before_with_time_by_email,
                    label,
                    alarmTime
                )
            } else if (alarm.action?.isDisplay == true) {
                resources.getString(
                    if (onTheSameDay) R.string.event_alarm_label_not_before_with_time else R.string.event_alarm_label_before_with_time,
                    label,
                    alarmTime
                )
            } else {
                null
            }

        } else { // example: "15 minutes before"

            // Proton support only 1 component for partial-day alarms

            val label = listOfNotNull(
                trigger.weeks?.let {
                    "$it ${
                        resources.getQuantityString(
                            R.plurals.plural_week,
                            it,
                            it
                        )
                    }"
                },
                trigger.days?.let {
                    "$it ${
                        resources.getQuantityString(
                            R.plurals.plural_day,
                            it,
                            it
                        )
                    }"
                },
                trigger.hours?.let {
                    "$it ${
                        resources.getQuantityString(
                            R.plurals.plural_hour,
                            it,
                            it
                        )
                    }"
                },
                trigger.minutes?.let {
                    "$it ${
                        resources.getQuantityString(
                            R.plurals.plural_minute,
                            it,
                            it
                        )
                    }"
                }
            ).joinToString(separator = ", ")

            if (label.isBlank()) {
                if (!trigger.isPrior && (trigger.seconds != null && trigger.seconds == 0)) {
                    if (alarm.action?.isEmail == true) resources.getString(R.string.event_alarm_label_at_event_time_by_email)
                    else resources.getString(R.string.event_alarm_label_at_event_time)
                } else {
                    null
                }
            } else if (alarm.action?.isEmail == true) {
                resources.getString(R.string.event_alarm_label_before_by_email, label)
            } else if (alarm.action?.isDisplay == true) {
                resources.getString(R.string.event_alarm_label_before, label)
            } else {
                null
            }
        }

    }

    /**
     * @param labels Pair<String Resource ID, Color Resource ID>
     * @param icons Pair<Drawable Resource ID, Color Resource ID>
     */
    fun displayPopupMenu(
        view: View,
        onItemClicked: (position: Int) -> Unit
    ) {

        // TODO we need custom adapter to apply custom colors to icon and text, this is workaround for now

        val data = ArrayList<HashMap<String, Any>>()
        data.add(
            hashMapOf(
                "text" to view.resources.getText(R.string.action_delete),
                "icon" to R.drawable.ic_proton_trash
            )
        )

        val popupWindow = ListPopupWindow(view.context)

        val adapter: SimpleAdapter = object: SimpleAdapter(
            view.context,
            data,
            R.layout.item_popup_error, // TODO
            arrayOf("text", "icon"),
            intArrayOf(R.id.tv_text, R.id.iv_icon)
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                return super.getView(position, convertView, parent).apply {
                    findViewById<View>(R.id.press_popup).setOnSingleClickListener {
                        onItemClicked(position)
                        popupWindow.dismiss()
                    }
                }
            }
        }

        with(popupWindow) {
            isModal = true
            anchorView = view
            verticalOffset = view.resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            horizontalOffset = view.resources.getDimensionPixelSize(R.dimen.spacing_element_small)
            width = measureContentWidth(view.context, adapter)
            height = ListPopupWindow.WRAP_CONTENT
            setAdapter(adapter)
            show()
        }

    }

    // https://stackoverflow.com/questions/14200724/listpopupwindow-not-obeying-wrap-content-width-spec/26814964#26814964
    private fun measureContentWidth(context: Context, listAdapter: ListAdapter): Int {
        var mMeasureParent: ViewGroup? = null
        var maxWidth = 0
        var itemView: View? = null
        var itemType = 0
        val widthMeasureSpec: Int = View.MeasureSpec.makeMeasureSpec(
            0,
            View.MeasureSpec.UNSPECIFIED
        )
        val heightMeasureSpec: Int = View.MeasureSpec.makeMeasureSpec(
            0,
            View.MeasureSpec.UNSPECIFIED
        )
        val count = listAdapter.count
        for (i in 0 until count) {
            val positionType = listAdapter.getItemViewType(i)
            if (positionType != itemType) {
                itemType = positionType
                itemView = null
            }
            if (mMeasureParent == null) {
                mMeasureParent = FrameLayout(context)
            }
            itemView = listAdapter.getView(i, itemView, mMeasureParent)
            itemView.measure(widthMeasureSpec, heightMeasureSpec)
            val itemWidth = itemView.measuredWidth
            if (itemWidth > maxWidth) {
                maxWidth = itemWidth
            }
        }
        return maxWidth
    }

    private fun updateColorLightness(colorString: String, increaseBy: Float): String {
        val outHSL = FloatArray(3)
        ColorUtils.colorToHSL(Integer.valueOf(colorString.substringAfter("#"), 16), outHSL)

        val color = ColorUtils.HSLToColor(floatArrayOf(outHSL[0], outHSL[1], kotlin.math.max(0f, kotlin.math.min(outHSL[2] + increaseBy, 1.0f))))
        return "#${color.toHexString()}"
    }

    fun darkenCalendarColor(colorString: String): String {
        // magic number, reducing lightness by 12
        return updateColorLightness(colorString, -0.12f)
    }

    fun brightenCalendarColor(colorString: String, increaseBy: Float): String {
        return updateColorLightness(colorString, increaseBy)
    }

    fun Activity.clearFocusAndHideKeyboard(view: View?) {
        val windowToken = view?.rootView?.windowToken
        val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        view?.clearFocus()
    }

    fun Context.showKeyboard() {
        (this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
    }

    data class TimePickerData(
        /**
         * 0-23
         */
        val hour: Int,
        /**
         * 0-59
         */
        val minute: Int,
        val is24Hour: Boolean
    )

    /**
     * Month is normalized to be 1-based.
     */
    data class DatePickerData(
        val year: Int,
        /**
         * 1-12
         */
        val month: Int,
        val day: Int
    )

    fun View.visibleOrGone(visible: Boolean) {
        this.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun View.visibleOrInvisible(visible: Boolean) {
        this.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun View.animateHeightChange(toHeightPx: Int, onAnimationEnd: () -> Unit) {
        if (this.measuredHeight != toHeightPx) {
            val valueAnimator = ValueAnimator.ofInt(this.measuredHeight, toHeightPx)
            valueAnimator.duration = HEIGHT_CHANGE_DURATION
            valueAnimator.addUpdateListener {
                val animatedValue = valueAnimator.animatedValue as Int
                val layoutParams = this.layoutParams.apply {
                    height = animatedValue
                }
                this.layoutParams = layoutParams
            }
            valueAnimator.start()
            valueAnimator.doOnEnd { onAnimationEnd.invoke() }
        }
    }

    /**
     * Highlights all the strings passed in [tokens] in entire text of this TextView
     */
    fun TextView.highlightSearchTokens(tokens: List<String>) {

        val spannableStringBuilder = SpannableStringBuilder(this.text)

        tokens.filter { it.isNotBlank() }.forEach { token ->

            var startIndex = 0

            while (startIndex < this.text.length) {
                val start = this.text.indexOf(token, ignoreCase = true, startIndex = startIndex)
                val end = start + token.length

                if (start > -1) {
                    startIndex = end

                    // Set text bold style
                    spannableStringBuilder.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    // Set text highlight color
                    spannableStringBuilder.setSpan(
                        ForegroundColorSpan(this.context.getColorFromAttr(R.attr.proton_text_accent)),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    break
                }
            }
        }

        this.text = spannableStringBuilder

    }

    /**
     * @param toHeightPx is the desired height for the view
     * @param maxHeight is the maximum expected height of the view, used when collapsing (maxHeight >= toHeightPx)
     */
    fun Guideline.animateGuidelineHeightChange(toHeightPx: Int, maxHeight: Int?, onAnimationEnd: () -> Unit) {
        val layoutParams = this.layoutParams as ConstraintLayout.LayoutParams
        if (layoutParams.guideBegin != toHeightPx) {
            val duration =
                if (maxHeight == null) HEIGHT_CHANGE_DURATION
                else if (toHeightPx > layoutParams.guideBegin) {
                    val percentLeft = ((toHeightPx - layoutParams.guideBegin) * 100) / toHeightPx
                    (percentLeft * HEIGHT_CHANGE_DURATION) / 100
                } else {
                    val percentLeft = 100 - (((maxHeight - layoutParams.guideBegin) * 100) / maxHeight)
                    (percentLeft * HEIGHT_CHANGE_DURATION) / 100
                }

            val valueAnimator = ValueAnimator.ofInt(layoutParams.guideBegin, toHeightPx)
            valueAnimator.duration = duration.toLong()
            valueAnimator.addUpdateListener {
                val animatedValue = valueAnimator.animatedValue as Int
                layoutParams.guideBegin = animatedValue
                this.layoutParams = layoutParams
            }
            valueAnimator.start()
            valueAnimator.doOnEnd { onAnimationEnd.invoke() }
        }
    }

    interface AnimateGuidelineListener {
        fun onHeightChange(animatedValue: Int)
        fun onAnimationEnd()
    }

    fun Guideline.animateGuidelineHeightChange(toHeightPx: Int, animateGuidelineListener: AnimateGuidelineListener) {
        val layoutParams = this.layoutParams as ConstraintLayout.LayoutParams
        if (layoutParams.guideBegin != toHeightPx) {
            val valueAnimator = ValueAnimator.ofInt(layoutParams.guideBegin, toHeightPx)
            valueAnimator.duration = 300L
            valueAnimator.addUpdateListener {
                val animatedValue = valueAnimator.animatedValue as Int
                layoutParams.guideBegin = animatedValue
                animateGuidelineListener.onHeightChange(animatedValue)
                this.layoutParams = layoutParams
            }
            valueAnimator.start()
            valueAnimator.doOnEnd { animateGuidelineListener.onAnimationEnd() }
        }
    }

    /**
     * Listens for changes in EditText, only propagates values within range or forces default when
     * value is non-empty, but incorrect.
     *
     * @return TextWatcher so we can disable listening to that EditText
     */
    fun EditText.doAfterFilteredIntValueChanged(
        default: Int,
        min: Int,
        max: Int,
        onValueChanged: (value: Int) -> Unit
    ): TextWatcher {
        return this.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                val count = it.toString().toIntOrNull()
                when {
                    count == null || it.toString().startsWith("0") -> {
                        this.setText(default.toString())
                    }
                    count < min -> {
                        this.setText(min.toString())
                    }
                    count > max -> {
                        this.setText(max.toString())
                    }
                    else -> {
                        onValueChanged(count)
                    }
                }
                this.setSelection(this.text.toString().length)
            }
        }
    }

    /**
     *
     */
    fun RadioGroup.checkIndex(index: Int) {
        this.children.filter { it is RadioButton }.elementAtOrNull(index)?.let {
            this.check(it.id)
        }
    }



    /**
     * Returns "first Monday", "second Friday" etc.
     *
     * @param reversed when we need reversed ordinals, like "last Friday"
     */
    fun LocalDate.formatMonthlyDayOfWeek(resources: Resources, backwards: Boolean = false): String {

        val ordinal = if (backwards) {
            resources.getStringArray(R.array.ordinals_as_words_backwards)[1] // TODO we support only the last weekdays in month
        } else {
            resources.getStringArray(R.array.ordinals_as_words)[this.weekInMonth()]
        }

        return "$ordinal ${this.dayOfWeek.format()}"

    }

    /**
     * @param id string resource formatted with CDATA if support for basic formatting is needed
     */
    fun Context.getText(@StringRes id: Int, vararg args: Any?): CharSequence {
        val text = String.format(getString(id), *args)
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    fun <T> concatenate(vararg lists: List<T>): List<T> {
        return listOf(*lists).flatten()
    }

    fun getCheckedRadioButtonIndex(radioGroup: RadioGroup): Int {
        // Found a bug where id of radio custom was 10 instead of 5, this makes sure we have the right id
        return radioGroup.indexOfChild(radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId))
    }

    class CustomOnCheckedChangeListener(private val onCustomOnCheckedChange: (RadioGroup, Int) -> Unit) : RadioGroup.OnCheckedChangeListener {
        override fun onCheckedChanged(radioGroup: RadioGroup, checkedId: Int) {
            radioGroup.children.forEach {
                if (getCheckedRadioButtonIndex(radioGroup) != radioGroup.indexOfChild(it))
                    it.jumpDrawablesToCurrentState()
            }
            onCustomOnCheckedChange(radioGroup, checkedId)
        }
    }

    fun RadioGroup.setCustomOnCheckedChangeListener(onCustomOnCheckedChange: (RadioGroup, Int) -> Unit) {
        val customOnCheckedChangeListener = CustomOnCheckedChangeListener { radioGroup, index ->
            onCustomOnCheckedChange(radioGroup, index)
        }
        setOnCheckedChangeListener(customOnCheckedChangeListener)
    }

    fun getInitials(name: String, takeFirstOnly: Boolean? = false): String {
        if (name.isBlank()) return ""
        if (takeFirstOnly == true) return name.uppercase().take(1)
        val initials = name.uppercase().split(' ')
            .mapNotNull { it.firstOrNull()?.toString() }
            .reduce { acc, s -> acc + s }
        //Keep only the first and last initials
        return if (initials.length > 2) initials[0].toString() + initials[initials.lastIndex] else initials
    }

    /**
     * @return Pair<Int, Long> of new height in px and animation duration in ms
     */
    fun expand(v: View, duration: Long? = null, height: Int? = null): Pair<Int, Long> {
        val matchParentMeasureSpec = View.MeasureSpec.makeMeasureSpec((v.parent as View).width, View.MeasureSpec.EXACTLY)
        val wrapContentMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        v.measure(matchParentMeasureSpec, wrapContentMeasureSpec)
        val targetHeight = height?: v.measuredHeight
        if (targetHeight == 0) {
            TimberLogger.d("animation expand skipped")
            v.visibility = View.VISIBLE
            return Pair(0, 0)
        }
        TimberLogger.d("animation expand : targetHeight = ${targetHeight}")

        // Older versions of android (pre API 21) cancel animations for views with a height of 0.
        v.layoutParams.height = 1
        v.visibility = View.VISIBLE
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                // We make sure height cannot be set to 0 to avoid UI glitch when starting expand animation
                v.layoutParams.height =
                    if (targetHeight == 0 || interpolatedTime == 0f) 1 else if (interpolatedTime == 1f) targetHeight else (targetHeight * interpolatedTime).toInt()
                TimberLogger.d("animation expand : applyTransformation interpolatedTime = ${interpolatedTime} & height = ${v.layoutParams.height}")
                v.requestLayout()
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }

        // Expansion speed of 1dp/ms
        val animationDuration = (targetHeight / v.context.resources.displayMetrics.density).toLong()
        animation.duration = duration ?: min(animationDuration, MAX_ANIM_DURATION)
        TimberLogger.d("animation expand : duration = ${animation.duration}")
        v.startAnimation(animation)
        return Pair(targetHeight, animation.duration)
    }

    /**
     * @return Pair<Int, Long> of new height in px and animation duration in ms
     */
    fun collapse(v: View, duration: Long? = null): Pair<Int, Long> {
        val initialHeight = v.measuredHeight
        TimberLogger.d("animation collapse : initialHeight = ${initialHeight}")
        val animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                if (interpolatedTime == 1f) {
                    v.visibility = View.GONE
                } else {
                    v.layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                    v.requestLayout()
                }
                TimberLogger.d("animation collapse : applyTransformation interpolatedTime = $interpolatedTime & height = ${v.layoutParams.height}")
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }

        // Collapse speed of 1dp/ms
        val animationDuration = (initialHeight / v.context.resources.displayMetrics.density).toLong()
        animation.duration = duration ?: min(animationDuration, MAX_ANIM_DURATION)
        TimberLogger.d("animation collapse : duration = ${animation.duration}")
        v.startAnimation(animation)
        return Pair(initialHeight, animation.duration)
    }

    fun rotateArrowDownward(v: View, duration: Long = 100) {
        val rotate =
            RotateAnimation(
                180F,
                0F,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
        rotate.interpolator = LinearInterpolator()
        rotate.fillAfter = true
        rotate.duration = duration
        v.startAnimation(rotate)
    }

    fun rotateArrowUpward(v: View, duration: Long = 100) {
        val rotate =
            RotateAnimation(
                0F,
                180F,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
        rotate.interpolator = LinearInterpolator()
        rotate.fillAfter = true
        rotate.duration = duration
        v.startAnimation(rotate)
    }

    fun setStripedBackground(view: View, context: Context, stripeColor: Int, fullyOpaque: Boolean? = false) {
        val colorDrawable = ColorDrawable(ContextCompat.getColor(context, R.color.background_norm)) // bg color3
        val vDrawable = AppCompatResources.getDrawable(context, R.drawable.vector_stripes) // vector drawable
        vDrawable?.setTint(stripeColor)
        if (fullyOpaque == false) vDrawable?.alpha = 51 // decimal value for 20% opacity
        else vDrawable?.alpha = 255 // fully opaque

        if (vDrawable != null) {
            val bitmap = Bitmap.createBitmap(
                vDrawable.intrinsicWidth, vDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            vDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vDrawable.draw(canvas)
            val bitmapDrawable = BitmapDrawable(context.resources, bitmap)
            bitmapDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) // set repeat
            val drawable = LayerDrawable(arrayOf(colorDrawable, bitmapDrawable))
            view.background = drawable
        }
    }

    fun getParticipationStatusPriorityValue(participationStatus: ParticipationStatus): Int {
        // Lower value means higher priority in list, sort by ascending order
        return when(participationStatus) {
            ParticipationStatus.ACCEPTED -> 0
            ParticipationStatus.TENTATIVE -> 1
            ParticipationStatus.DECLINED -> 2
            ParticipationStatus.NEEDS_ACTION -> 3
            else -> 4
        }
    }

    fun Context.showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    // Call this method to display SnackBar in a Fragment
    fun Activity.displaySnackBar(message: String, length: Int? = null): Snackbar {
        return this.findViewById<View>(android.R.id.content).normSnack(message, length ?: Snackbar.LENGTH_LONG)
    }

    // Call this method to display SnackBar in a DialogFragment
    fun View.displaySnackBar(message: String, length: Int? = null): Snackbar {
        return this.normSnack(message, length ?: Snackbar.LENGTH_LONG)
    }

    @BindingAdapter("onSingleClick")
    fun View.setOnSingleClickListener(clickListener: View.OnClickListener?) {
        clickListener?.also {
            setOnClickListener(OnSingleClickListener(it))
        } ?: setOnClickListener(null)
    }

    class OnSingleClickListener(
        private val clickListener: View.OnClickListener,
    ) : View.OnClickListener {
        private var canClick = AtomicBoolean(true)

        override fun onClick(v: View?) {
            if (canClick.getAndSet(false)) {
                v?.run {
                    postDelayed({
                        canClick.set(true)
                    }, CLICK_INTERVAL_MS)
                    clickListener.onClick(v)
                }
            }
        }
    }

    fun Array<String>.sortFormattedTimeZoneIds() {
        this.sortWith { a, b ->
            val aFloat = a.formattedTimeZoneToFloat()
            val bFloat = b.formattedTimeZoneToFloat()
            when {
                (aFloat < bFloat) -> 1
                (aFloat > bFloat) -> -1
                (a < b) -> -1
                (a > b) -> 1
                else -> 0
            }
        }
    }

    /**
     * Returns timezone offset as float from formatted timezone with offset
     */
    private fun String.formattedTimeZoneToFloat(): Float {
        val pattern = Pattern.compile("^.*GMT([+-]\\d{1,2}):?(\\d{1,2})?\\).*\$")
        val matcher = pattern.matcher(this)
        matcher.find()
        val hours = matcher.group(1)?.toFloat()
        val minutes = matcher.group(2)?.toFloat()?.div(100) ?: 0F
        return (hours ?: 0F) + minutes
    }

    /**
     * Returns timezone id from formatted timezone with offset
     */
    fun String.formattedTimeZoneToId(): String {
        return this.replace(
            Regex(" (\\(GMT[+-]\\d{1,2}:?(\\d{1,2})?\\))"),
            ""
        )
    }

    fun getCurrentLocale(context: Context): Locale? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            context.resources.configuration.locale
        }
    }

    fun Context.dpToPixel(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    fun Context.dpToPixel(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    fun Context.pixelToDp(pixel: Int): Int {
        return (pixel / resources.displayMetrics.density).toInt()
    }

    fun Context.spToPixel(sp: Float): Float {
        return sp * this.resources.displayMetrics.scaledDensity
    }

    fun getWeekStartDayOfWeek(index: Int): java.time.DayOfWeek = when (index) {
        1 -> java.time.DayOfWeek.MONDAY
        6 -> java.time.DayOfWeek.SATURDAY
        7 -> java.time.DayOfWeek.SUNDAY
        else -> WeekFields.of(getDefault()).firstDayOfWeek
    }

    fun removeAccents(string: CharSequence): String {
        val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        val temp = Normalizer.normalize(string, Normalizer.Form.NFD)
        return regex.replace(temp, "")
    }

    inline fun <reified T> Any?.tryCast(block: T.() -> Unit) {
        if (this is T) block()
    }

    inline fun <reified T> Any?.tryCastOrNull(): T? {
        return if (this is T) this else null
    }

    fun Int.toParticipationStatus(): ParticipationStatus {
        return when (this) {
            1 -> ParticipationStatus.TENTATIVE
            2 -> ParticipationStatus.DECLINED
            3 -> ParticipationStatus.ACCEPTED
            else -> ParticipationStatus.NEEDS_ACTION
        }
    }

    fun ParticipationStatus.toInt(): Int {
        return when (this) {
            ParticipationStatus.TENTATIVE -> 1
            ParticipationStatus.DECLINED -> 2
            ParticipationStatus.ACCEPTED -> 3
            else -> 0
        }
    }

    /** Execute the [listener] on [TextWatcher.onTextChanged] */
    inline fun EditText.onTextChange(crossinline listener: (CharSequence) -> Unit): TextWatcher {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                /* Do nothing */
            }
            override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
                /* Do nothing */
            }
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                listener(text)
            }
        }
        addTextChangedListener(watcher)
        return watcher
    }

    @CheckResult
    fun EditText.onTextChange(): Flow<CharSequence> {
        return callbackFlow {
            val listener = object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = Unit
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    s?.let { trySend(s) }
                }
            }
            addTextChangedListener(listener)
            awaitClose { removeTextChangedListener(listener) }
        }
    }

    @ColorInt
    fun Context.getColorFromAttr(
        @AttrRes attrColor: Int,
        typedValue: TypedValue = TypedValue(),
        resolveRefs: Boolean = true
    ): Int {
        theme.resolveAttribute(attrColor, typedValue, resolveRefs)
        return typedValue.data
    }

    fun humanReadableByteCountBin(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(bytes)
        if (absB < 1024) {
            return "$bytes B"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("KMGTPE")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.1f %cB", value / 1024.0, ci.current())
    }

    fun String.ellipsize(maxLength: Int): String {
        if (this.length <= maxLength) return this
        return this.substring(0, maxLength - 1).plus("…")
    }

    fun Context.getDeviceContacts(): ArrayList<Attendee>? {
        val contactsAccessGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!contactsAccessGranted) return null
        val deviceContacts = ArrayList<Attendee>()
        val uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI
        val cursor = this.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.DATA
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY + " ASC"
        ) ?: return null
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val contactEmailColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val contactNameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY)
            val contactEmail = cursor.getString(contactEmailColumnIndex)
            val contactName = cursor.getString(contactNameColumnIndex)
            val contact = Attendee(
                contactName,
                contactEmail
            )
            deviceContacts.add(contact)
            cursor.moveToNext()
        }
        cursor.close()
        return deviceContacts
    }

    fun applyAndroid15EdgeToEdge(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { view, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemInsets.left
                topMargin = systemInsets.top
                rightMargin = systemInsets.right
                bottomMargin = systemInsets.bottom
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Below 11 the system does adjustResize already
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                view.updatePadding(bottom = imeInsets.bottom)
            }
            insets
        }
    }
}
