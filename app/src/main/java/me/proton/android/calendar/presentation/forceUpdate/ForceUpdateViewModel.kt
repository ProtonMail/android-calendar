package me.proton.android.calendar.presentation.forceUpdate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple ViewModel that should notify the Activity that it should handle (show) the ForceUpdate dialog.
 * @author Dino Kadrikj.
 */
@Singleton
class ForceUpdateViewModel @Inject constructor() : ViewModel() {

    private val _forceUpdate: MutableLiveData<ForceUpdateInput> = MutableLiveData()
    val forceUpdate: LiveData<ForceUpdateInput> = _forceUpdate

    /**
     * Public interface that should trigger
     */
    fun forceUpdate(apiErrorMessage: String) {
        _forceUpdate.postValue(ForceUpdateInput(apiErrorMessage = apiErrorMessage, forceUpdate = true))
    }
}

data class ForceUpdateInput(
    val apiErrorMessage: String,
    val forceUpdate: Boolean
)
