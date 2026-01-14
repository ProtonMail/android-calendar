package me.proton.android.calendar.presentation.calendar.customView

import android.view.View
import android.widget.RadioButton
import androidx.annotation.IdRes
import me.proton.android.calendar.common.utils.AndroidUtils.setOnSingleClickListener

/**
 * Class mimicking RadioGroup component for checking RadioButtons that are not inside RadioGroup.
 */
class NoLayoutRadioGroup(onChecked: (viewId: Int) -> Unit) {

    val buttons = mutableMapOf<Int, RadioButton>()

    private val onClickListener = object: View.OnClickListener {
        override fun onClick(view: View?) {
            view?.let {
                check(it.id)
                onChecked(it.id)
            }
        }

    }

    fun add(vararg radioButtons: RadioButton) {
        radioButtons.forEach {
            buttons.putIfAbsent(it.id, it.apply {
                setOnSingleClickListener(onClickListener)
            })
        }
    }

    fun check(@IdRes viewId: Int) {
        buttons.values.forEach {
            it.isChecked = it.id == viewId
        }
    }

    fun getCheckedRadioButtonId(): Int? {
        return (buttons.filter { it.value.isChecked }.entries.firstOrNull())?.key
    }

}
