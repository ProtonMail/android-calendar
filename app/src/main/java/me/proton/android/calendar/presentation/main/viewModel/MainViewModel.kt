package me.proton.android.calendar.presentation.main.viewModel

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.Operation
import com.google.android.play.core.review.ReviewManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.android.calendar.common.INVITE_ICS_MIME_TYPE
import me.proton.android.calendar.common.INVITE_PROTON_INTENT_ACTION
import me.proton.android.calendar.common.Navigation
import me.proton.android.calendar.common.SharedPreferencesKeys
import me.proton.android.calendar.common.ViewMode
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.IcsSurgeryUtils
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.FeedbackApi
import me.proton.android.calendar.domain.usecase.HandleIcsUseCase
import me.proton.android.calendar.domain.usecase.RefreshCalendarUserSettingsUseCase
import me.proton.android.calendar.domain.usecase.ResetLocalEventDatabaseUseCase
import me.proton.android.calendar.domain.usecase.ScheduleSyncAlarmsUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.presentation.main.MainActivity
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.contact.domain.entity.ContactEmail
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.NetworkManager
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val accountRepository: AccountRepository,
    private val handleIcsUseCase: HandleIcsUseCase,
    private val networkManager: NetworkManager,
    private val defaultSharedPreferencesProvider: DefaultSharedPreferencesProvider,
    private val userSettingsRepository: UserSettingsRepository,
    private val feedbackApi: FeedbackApi,
    private val refreshCalendarUserSettingsUseCase: RefreshCalendarUserSettingsUseCase,
    private val logger: Logger,
    private val scheduleSyncAlarmsUseCase: ScheduleSyncAlarmsUseCase,
    private val reviewManager: ReviewManager,
    private val resetLocalEventDatabaseUseCase: ResetLocalEventDatabaseUseCase,
    private val contactEmailsRepository: ContactRepository,
    private val calendarsRepository: CalendarsRepository
) : AndroidViewModel(application) {

    private val intents = mutableMapOf<String, Intent>()

    val isConnectedToNetwork get() = networkManager.isConnectedToNetwork()

    // Used to notify us that user is back on the main view so we can trigger actions there
    // Workaround to imitate onResume behavior since that callback will not be called when we use DialogFragment
    val triggerMainViewActions = MutableStateFlow(false)

    /**
     * Try to open maps with event location.
     */
    fun handleEventLocationShow(location: String): Boolean {
        return try {
            val googleMapsUri = Uri.parse("geo:0,0?q=$location")
            val googleMapsIntent = Intent(Intent.ACTION_VIEW, googleMapsUri)
            googleMapsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            googleMapsIntent.setPackage("com.google.android.apps.maps")
            getApplication<Application>().startActivity(googleMapsIntent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun handleCopyToClipboard(content: String): Boolean {
        return try {
            val clipboard: ClipboardManager? = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                val clip = ClipData.newPlainText("", content)
                clipboard.setPrimaryClip(clip)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun isAlternativeRoutingEnabled(): Boolean {
        return defaultSharedPreferencesProvider.sharedPreferences.getBoolean(SharedPreferencesKeys.ALTERNATIVE_ROUTING, true)
    }

    fun setAlternativeRoutingEnabled(enabled: Boolean) {
        val isAlternativeRoutingEnabled = isAlternativeRoutingEnabled()
        if (isAlternativeRoutingEnabled != enabled) {
            val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
            editor.putBoolean(SharedPreferencesKeys.ALTERNATIVE_ROUTING, enabled)
            editor.apply()
        }
    }

    fun setUseDefaultViewMode(useDefaultViewMode: Boolean) {
        val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
        editor.putBoolean(SharedPreferencesKeys.USE_DEFAULT_VIEW_MODE, useDefaultViewMode)
        editor.apply()
    }

    fun useDefaultViewMode(): Boolean {
        return defaultSharedPreferencesProvider.sharedPreferences.getBoolean(
            SharedPreferencesKeys.USE_DEFAULT_VIEW_MODE,
            false
        )
    }

    /**
     * Set view mode in shared preferences
     */
    fun setViewMode(viewMode: ViewMode) {
        val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.VIEW_MODE, viewMode.value)
        editor.apply()
    }

    /**
     * Set view mode in shared preferences if user enabled resuming on last view mode
     */
    fun setLastViewMode(viewMode: ViewMode) {
        if (useDefaultViewMode()) return
        val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
        editor.putInt(SharedPreferencesKeys.VIEW_MODE, viewMode.value)
        editor.apply()
    }

    fun getLastViewMode(): ViewMode {
        // By default we display the month view
        val lastViewMode = ViewMode.values()[defaultSharedPreferencesProvider.sharedPreferences.getInt(
            SharedPreferencesKeys.VIEW_MODE,
            ViewMode.MONTH.value
        )]
        return if (!CalendarFeatureFlag.MonthView.fallbackValue && lastViewMode == ViewMode.MONTH) ViewMode.AGENDA else lastViewMode
    }

    fun setWeekViewHourHeight(newHourHeight: Float) {
        val editor = defaultSharedPreferencesProvider.sharedPreferences.edit()
        editor.putFloat(SharedPreferencesKeys.WEEK_VIEW_HOUR_HEIGHT, newHourHeight)
        editor.apply()
    }

    fun getWeekViewHourHeight(defaultHourHeight: Float): Float {
        return defaultSharedPreferencesProvider.sharedPreferences.getFloat(SharedPreferencesKeys.WEEK_VIEW_HOUR_HEIGHT, defaultHourHeight)
    }

    // TODO run only after bootstrap & successful "cold fetch" of events for the first required period
    fun syncAlarms(userId: UserId) : LiveData<Operation.State> {
        return scheduleSyncAlarmsUseCase.execute(userId, initialDelay = Duration.ofSeconds(10)).state
    }

    /**
     * Makes sure we have the Core UserSettings stored locally.
     */
    fun fetchUserSettings(userId: UserId) {
        ioScope.launch {
            withTimeoutOrNull(Duration.ofSeconds(30).toMillis()) {
                // we don't need the most up-to-date value, just need to make sure we have anything in Store
                val downloaded = kotlin.runCatching { userSettingsRepository.getUserSettings(userId, refresh = false) }.getOrNull()
            }
        }
    }

    fun refreshCalendarUserSettings(userId: UserId) {
        ioScope.launch {
            withTimeoutOrNull(Duration.ofSeconds(10).toMillis()) {
                refreshCalendarUserSettingsUseCase(userId)
            }
        }
    }

    /**
     * Sends feedback to API.
     */
    suspend fun handleFeedback(userId: UserId, logger: me.proton.android.calendar.domain.Logger, score: Int, feedback: String): UseCase.Result =
        withContext(ioScope.coroutineContext) {
            when (val response = feedbackApi.sendFeedback(userId, score, feedback)) {
                is ApiResponse.Error, is ApiResponse.Exception -> {
                    response.logErrorIfNeeded("error sending feedback", logger)
                    UseCase.Result.Error("")
                }
                is ApiResponse.Success -> UseCase.Result.Success<Unit>()
            }
        }

    private var viewModelJob = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + viewModelJob)

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    /**
     * Stores intent with given [action] in a map for clients to [consumeIntent] later.
     */
    fun handleIntent(intent: Intent) {
        intent.action?.let {
            intents.put(it, intent)
        }
    }

    fun containsIntent() = intents.isNotEmpty()
    
    fun containsIntent(action: String) = intents.containsKey(action)

    fun containsIntentToHandle() = intents.any { shouldHandleIntent(it.value) }

    fun shouldHandleIntent(intent: Intent): Boolean {
        return intent.action == INVITE_PROTON_INTENT_ACTION ||
                intent.action == Intent.ACTION_VIEW ||
                intent.type == INVITE_ICS_MIME_TYPE ||
                intent.action == INTENT_ACTION_NEW_EVENT ||
                intent.action == INTENT_ACTION_SHOW_DAY ||
                intent.action == INTENT_ACTION_SHOW_EVENT_DETAILS ||
                intent.action == Intent.ACTION_INSERT ||
                intent.action == Intent.ACTION_EDIT
    }

    /**
     * Returns and deletes intent with given [action], if it has been handled previously.
     */
    fun consumeIntent(action: String): Intent? {
        return intents.remove(action)
    }

    companion object {
        const val INTENT_ACTION_SHOW_EVENT_DETAILS = "INTENT_ACTION_SHOW_EVENT_DETAILS"
        const val INTENT_ACTION_SHOW_DAY = "INTENT_ACTION_SHOW_DAY"
        const val INTENT_ACTION_NEW_EVENT = "INTENT_ACTION_NEW_EVENT"

        fun createMainIntentToShowEventDetails(context: Context, eventId: String, occurrenceNumber: Int?): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = INTENT_ACTION_SHOW_EVENT_DETAILS
                data = Navigation.Deeplink.toMainActivityWithEventId(eventId, occurrenceNumber ?: 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    suspend fun handleIcsFile(iCalString: String, senderEmail: String?, recipientEmail: String?): IcsSurgeryUtils.HandleIcsResult {
        val userId = accountRepository.getPrimaryUserId().firstOrNull() ?: return IcsSurgeryUtils.HandleIcsResult.Error.DefaultError
        if (isConnectedToNetwork.not()) return IcsSurgeryUtils.HandleIcsResult.Error.NetworkError
        return handleIcsUseCase.execute(iCalString, userId, senderEmail, recipientEmail)
    }

    fun startPlayStoreRating(activity: Activity) {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // We got the ReviewInfo object
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    // The flow has finished. The API does not indicate whether the user
                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                    // matter the result, we continue our app flow.
                }
            } else {
                // There was some problem, log or handle the error code.
                val reviewError = task.exception
                logger.d("Rate app request failed $reviewError")
            }
        }
    }

    suspend fun resetLocalEventsDatabase(userId: UserId) {
        resetLocalEventDatabaseUseCase.invoke(userId)
    }

    suspend fun clearLocalEventsDatabase() {
        calendarsRepository.deleteAllEvents()
    }

    suspend fun getProtonContacts(userId: UserId): List<ContactEmail>? {
        return runCatching {
            contactEmailsRepository.getAllContactEmails(userId)
        }.getOrElse {
            logger.e("Exception in MainViewModel.getProtonContacts: ${it.message}", it)
            null
        }
    }
}
