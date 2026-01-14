package me.proton.android.calendar.presentation.calendar.customView

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import me.proton.android.calendar.common.ViewMode

class InterceptTouchConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    var allowScrolling: Boolean = false
    var viewMode: ViewMode = ViewMode.DAY
    var agendaPager: View? = null
    var weekView: View? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onTouchEvent will be called and we do the actual
         * scrolling there.
         */
        return when (ev.actionMasked) {
            // Always handle the case of the touch gesture being complete.
            MotionEvent.ACTION_DOWN -> {
                if (viewMode == ViewMode.MONTH) return false // This behavior does not apply to month view
                val delegateArea = Rect()
                agendaPager?.getHitRect(delegateArea)

                val weekViewDelegateArea = Rect()
                weekView?.getHitRect(weekViewDelegateArea)

                !allowScrolling &&
                        (delegateArea.contains(ev.x.toInt(), ev.y.toInt()) ||
                                weekViewDelegateArea.contains(ev.x.toInt(), ev.y.toInt()))
            }
            MotionEvent.ACTION_UP -> {
                // Do not intercept touch event, let the child handle it
                false
            }
            MotionEvent.ACTION_MOVE -> {
                if (viewMode == ViewMode.MONTH) return false // This behavior does not apply to month view
                val delegateArea = Rect()
                agendaPager?.getHitRect(delegateArea)

                val weekViewDelegateArea = Rect()
                weekView?.getHitRect(weekViewDelegateArea)

                !allowScrolling &&
                        (delegateArea.contains(ev.x.toInt(), ev.y.toInt()) ||
                                weekViewDelegateArea.contains(ev.x.toInt(), ev.y.toInt()))
            }
            else -> {
                // In general, we don't want to intercept touch events. They should be handled by the child view.
                false
            }
        }
    }

}
