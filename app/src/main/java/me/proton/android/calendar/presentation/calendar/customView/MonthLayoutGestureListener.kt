package me.proton.android.calendar.presentation.calendar.customView

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import me.proton.android.calendar.R
import me.proton.android.calendar.common.MiniCalendarGestures
import me.proton.android.calendar.presentation.calendar.viewModel.CalendarViewModel
import kotlin.math.sqrt

class MonthLayoutGestureListener(
    private val context: Context,
    private val calendarViewModel: CalendarViewModel,
    private val viewPagerTopGuideline: View,
    private val viewPagerSliderGuideline: View,
    private val weekView: View,
    private val monthLayoutOnFinishMoveListener: MonthLayoutOnFinishMoveListener
) : View.OnTouchListener {
    private var oldScrollY: Float? = null
    private var actionUpY: Float? = null
    private var actionDownY: Float? = null
    private var scrollUp: Boolean = false
    private var scrollDown: Boolean = false

    private var pressStartTime: Long = 0
    private var pressedX = 0f
    private var pressedY = 0f
    private var stayedWithinClickDistance = false // Used to differentiates clicks and swipes

    interface MonthLayoutOnFinishMoveListener {
        fun fullyExpand(animationEndListener: (() -> Unit))
        fun fullyCollapse(animationEndListener: (() -> Unit))
        fun simulateExpand(animationEndListener: (() -> Unit))
        fun simulateCollapse(animationEndListener: (() -> Unit))
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        val distanceInPx = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        return pxToDp(distanceInPx)
    }

    private fun pxToDp(px: Float): Float {
        return px / context.resources.displayMetrics.density
    }

    override fun onTouch(v: View?, event: MotionEvent): Boolean {

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {

                if (stayedWithinClickDistance && distance(
                        pressedX,
                        pressedY,
                        event.x,
                        event.y
                    ) > MiniCalendarGestures.MAX_CLICK_DISTANCE
                ) {
                    stayedWithinClickDistance = false
                }

                val scrollY = event.y
                if (oldScrollY != null && scrollY < oldScrollY!!) {

                    // Scrolling up
                    scrollUp = true
                    scrollDown = false
                    val scrollValue = (scrollY - oldScrollY!!) * -1
                    val viewPagerGuidelineLayoutParams =
                        (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
                    if (viewPagerGuidelineLayoutParams.guideBegin > context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)) {
                        viewPagerGuidelineLayoutParams.guideBegin -= scrollValue.toInt()

                        // Set Guidelines guide begin
                        if (viewPagerGuidelineLayoutParams.guideBegin < context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height))
                            viewPagerGuidelineLayoutParams.guideBegin =
                                context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height)
                        viewPagerTopGuideline.layoutParams = viewPagerGuidelineLayoutParams

                    }
                } else if (oldScrollY != null && scrollY > oldScrollY!!) {

                    // Scrolling down
                    scrollUp = false
                    scrollDown = true
                    val scrollValue = scrollY - oldScrollY!!
                    val viewPagerGuidelineLayoutParams =
                        (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)
                    val viewPagerSliderGuidelineLayoutParams =
                        (viewPagerSliderGuideline.layoutParams as ConstraintLayout.LayoutParams)
                    if (viewPagerGuidelineLayoutParams.guideBegin < calendarViewModel.currentPosDesiredMonthHeight) {
                        viewPagerGuidelineLayoutParams.guideBegin += scrollValue.toInt()

                        // Set Guidelines guide begin
                        if (viewPagerGuidelineLayoutParams.guideBegin > calendarViewModel.currentPosDesiredMonthHeight)
                            viewPagerGuidelineLayoutParams.guideBegin =
                                calendarViewModel.currentPosDesiredMonthHeight
                        viewPagerTopGuideline.layoutParams = viewPagerGuidelineLayoutParams
                        if (viewPagerSliderGuidelineLayoutParams.guideBegin < calendarViewModel.currentPosDesiredMonthHeight)
                            viewPagerSliderGuidelineLayoutParams.guideBegin =
                                calendarViewModel.currentPosDesiredMonthHeight - context.resources.getDimensionPixelSize(
                                    R.dimen.calendar_slider_height
                                )
                        viewPagerSliderGuideline.layoutParams = viewPagerSliderGuidelineLayoutParams
                    }
                }

                oldScrollY = scrollY
                return true
            }
            MotionEvent.ACTION_DOWN -> {
                scrollUp = false
                scrollDown = false
                actionDownY = event.y

                pressStartTime = System.currentTimeMillis()
                pressedX = event.x
                pressedY = event.y
                stayedWithinClickDistance = true

                return true
            }
            MotionEvent.ACTION_UP -> {
                actionUpY = event.y
                oldScrollY = null

                val pressDuration = System.currentTimeMillis() - pressStartTime
                val delegateArea = Rect()
                weekView.getHitRect(delegateArea)
                val isWithinPager =
                    delegateArea.contains(pressedX.toInt(), pressedY.toInt()) // Check if click is within agenda view

                if (isWithinPager &&
                    pressDuration < MiniCalendarGestures.MAX_CLICK_DURATION &&
                    stayedWithinClickDistance &&
                    calendarViewModel.monthView.value == true
                ) {
                    // Click event has occurred
                    monthLayoutOnFinishMoveListener.simulateCollapse { }
                    return true
                } else if (pressDuration < MiniCalendarGestures.MAX_FLICK_DURATION && distance(
                        pressedX,
                        pressedY,
                        event.x,
                        event.y
                    ) > MiniCalendarGestures.MIN_FLICK_DISTANCE
                ) {
                    // Flick event has occurred
                    if (calendarViewModel.monthView.value == true) monthLayoutOnFinishMoveListener.simulateCollapse { }
                    else monthLayoutOnFinishMoveListener.simulateExpand { }
                    return true
                }

                val layoutParams = (viewPagerTopGuideline.layoutParams as ConstraintLayout.LayoutParams)

                // Check if we're closer to top or bottom so that we finish the animation for the user
                val distanceWithWeekTop = layoutParams.guideBegin -
                        context.resources.getDimensionPixelSize(R.dimen.calendar_slider_height) -
                        if (calendarViewModel.monthView.value == true) // Make it easier to collapse when expanded
                            (context.resources.getDimensionPixelSize(R.dimen.calendar_item_height) + context.resources.getDimensionPixelSize(
                                R.dimen.calendar_item_header_height
                            ))
                        else 0
                val distanceWithMonthBottom = calendarViewModel.currentPosDesiredMonthHeight -
                        layoutParams.guideBegin -
                        if (calendarViewModel.monthView.value == false) // Make it easier to expand when collapsed
                            (context.resources.getDimensionPixelSize(R.dimen.calendar_item_height) + context.resources.getDimensionPixelSize(
                                R.dimen.calendar_item_header_height
                            ))
                        else 0

                if (distanceWithMonthBottom > distanceWithWeekTop && distanceWithWeekTop != 0 && distanceWithMonthBottom != 0) {
                    // Finish collapse animation for the user
                    monthLayoutOnFinishMoveListener.fullyCollapse { }
                } else if (distanceWithWeekTop != 0 && distanceWithMonthBottom != 0) {
                    // Finish expand animation for the user
                    monthLayoutOnFinishMoveListener.fullyExpand { }
                } else if (distanceWithWeekTop == 0 && scrollUp) {
                    // View is already fully collapsed, continue the process
                    monthLayoutOnFinishMoveListener.fullyCollapse { }
                } else if (distanceWithMonthBottom == 0 && scrollDown) {
                    // View is already fully expanded, continue the process
                    monthLayoutOnFinishMoveListener.fullyExpand { }
                }

                scrollUp = false
                scrollDown = false
                return true
            }
        }
        return false
    }
}
