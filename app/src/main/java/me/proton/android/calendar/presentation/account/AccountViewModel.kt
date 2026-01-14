package me.proton.android.calendar.presentation.account

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.android.calendar.R
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.DEFAULT_CALENDAR_COLOR
import me.proton.android.calendar.common.DEFAULT_HOLIDAY_CALENDAR_COLOR
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.android.calendar.domain.usecase.BootstrapAllCalendarsUseCase
import me.proton.android.calendar.domain.usecase.ResetCalendarsKeyUseCase
import me.proton.android.calendar.domain.usecase.UseCase
import me.proton.android.calendar.domain.usecase.ifSuccessAndLogErrors
import me.proton.core.account.domain.entity.Account
import me.proton.core.account.domain.entity.isDisabled
import me.proton.core.account.domain.entity.isReady
import me.proton.core.account.domain.entity.isStepNeeded
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.presentation.observe
import me.proton.core.accountmanager.presentation.onAccountCreateAddressFailed
import me.proton.core.accountmanager.presentation.onAccountCreateAddressNeeded
import me.proton.core.accountmanager.presentation.onAccountDeviceSecretNeeded
import me.proton.core.accountmanager.presentation.onAccountDisabled
import me.proton.core.accountmanager.presentation.onAccountReady
import me.proton.core.accountmanager.presentation.onAccountRemoved
import me.proton.core.accountmanager.presentation.onAccountTwoPassModeFailed
import me.proton.core.accountmanager.presentation.onAccountTwoPassModeNeeded
import me.proton.core.accountmanager.presentation.onSessionSecondFactorNeeded
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.auth.presentation.onAddAccountResult
import me.proton.core.domain.entity.UserId
import me.proton.core.presentation.utils.currentLocale
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val authOrchestrator: AuthOrchestrator,
    private val bootstrapAllCalendarsUseCase: BootstrapAllCalendarsUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository,
    private val resetCalendarsKeyUseCase: ResetCalendarsKeyUseCase,
    private val logger: Logger,
    private val widgetRefresher: WidgetRefresher,
    private val eventDecryptor: EventDecryptor,
    private val workManager: WorkManager,
) : ViewModel() {

    sealed class State {
        object Initial : State()
        object LoginNeeded : State()
        object StepNeeded : State()
        object Processing : State()
        object Ready : State()
    }

    private val _state = MutableStateFlow<State>(State.Initial)
    private val _hasPrimary = MutableLiveData<Boolean>()
    private val _errorReport = MutableLiveData<UseCase.Error?>()

    // Those values are set in init.
    private var defaultCalendarName: String = "My calendar"
    private var defaultCalendarColor: String = DEFAULT_CALENDAR_COLOR
    private var defaultHolidayCalendarColor: String = DEFAULT_HOLIDAY_CALENDAR_COLOR
    private var defaultCountryCode: String? = null
    private var defaultLanguageCode: String? = null

    private suspend fun Account.isBootstrapped() = calendarsRepository.selectCalendarUserSettings(userId.id) != null

    private suspend fun checkAccount(account: Account, context: Context) {
        runCatching {
            if (account.isBootstrapped()) return

            account.details.session?.initialEventId?.let { eventId ->
                val valueStore = valueStoreProvider.provideValueStore(account.userId.id)
                valueStore.putString(ValueKey.LAST_SERVER_EVENT_ID, eventId)
            }

            val colorValuesArray = context.resources.getStringArray(R.array.colors_values)
            defaultCalendarName = context.resources.getString(R.string.default_calendar_name)
            defaultCalendarColor = colorValuesArray.random()
            defaultHolidayCalendarColor = colorValuesArray.random()
            for (i in 0 until 10) {
                // Try and use a different value for default calendar and holiday calendar colors
                if (defaultHolidayCalendarColor != defaultCalendarColor) break
                defaultHolidayCalendarColor = colorValuesArray.random()
            }
            val languageTag = context.resources.configuration.currentLocale().toLanguageTag().lowercase()
            defaultCountryCode = languageTag.substringAfter("-", "")
            defaultLanguageCode = context.resources.configuration.currentLocale().language.lowercase()

            setupUser(account.userId)
        }.onFailure {
            logger.e("checkAccount failed, removing user.", it)
            disableUser(account.userId)

            if (it is CancellationException) throw it
        }
    }

    private suspend fun setupUser(userId: UserId, showConfirmationDialog: Boolean = true) {
        _state.tryEmit(State.Processing)

        val bootstrapResult = bootstrapAllCalendarsUseCase.execute(
            userId,
            showConfirmationDialog,
            defaultCalendarName,
            defaultCalendarColor,
            defaultHolidayCalendarColor,
            defaultLanguageCode,
            defaultCountryCode
        )
        bootstrapResult.ifSuccessAndLogErrors(logger) { }
        if (bootstrapResult !is UseCase.Result.Success<*>) {
            if (bootstrapResult is UseCase.Result.Error) {
                _errorReport.postValue(bootstrapResult.error)
                if (bootstrapResult.error == UseCase.Error.Bootstrap.ResetNeeded ||
                    bootstrapResult.error == UseCase.Error.Bootstrap.UpdatePassphrase
                ) return
            } else {
                disableUser(userId)
            }
            return
        }

        _state.tryEmit(State.Ready)
    }

    private suspend fun disableUser(userId: UserId) {
        accountManager.disableAccount(userId)
    }

    private suspend fun cleanUser(userId: UserId) {
        workManager.cancelAllWork()
        calendarsRepository.clearSearchDatabase()
        // Calendar currently do not support multi user.
        calendarsRepository.deleteAllCalendars()
        calendarsRepository.shutdown()
        eventDecryptor.clearCache()
        valueStoreProvider.provideValueStore(userId.id).clearAll()
        widgetRefresher.refreshEventList()
    }

    // TODO: Merge State & Error in the same StateFlow.
    val state = _state.asStateFlow()
    val errorReport: LiveData<UseCase.Error?> = _errorReport
    val hasPrimary: LiveData<Boolean> = _hasPrimary

    fun init(context: FragmentActivity) {
        // Make sure we clear error on init
        clearError()

        // Account state handling.
        with(authOrchestrator) {
            register(context)

            accountManager.observe(context.lifecycle, minActiveState = Lifecycle.State.CREATED)
                .onAccountReady { checkAccount(it, context) }
                .onSessionSecondFactorNeeded { startSecondFactorWorkflow(it) }
                .onAccountTwoPassModeNeeded { startTwoPassModeWorkflow(it) }
                .onAccountCreateAddressNeeded { startChooseAddressWorkflow(it) }
                .onAccountDeviceSecretNeeded { startDeviceSecretWorkflow(it) }
                .onAccountTwoPassModeFailed { disableUser(it.userId) }
                .onAccountCreateAddressFailed { disableUser(it.userId) }
                .onAccountDisabled(initialState = false) { cleanUser(it.userId) }
                .onAccountRemoved { cleanUser(it.userId) }
        }

        // Check if we already have Ready account.
        accountManager.getAccounts().onEach { accounts ->
            when {
                accounts.isEmpty() || accounts.all { it.isDisabled() } -> _state.tryEmit(State.LoginNeeded)
                accounts.any { it.isReady() && it.isBootstrapped() } -> _state.tryEmit(State.Ready)
                accounts.any { it.isStepNeeded() } -> _state.tryEmit(State.StepNeeded)
            }
        }.launchIn(viewModelScope)

        // Observe primary user id.
        accountManager.getPrimaryUserId().onEach { userId ->
            _hasPrimary.postValue(userId != null)
        }.launchIn(viewModelScope)
    }

    fun addAccount() {
        authOrchestrator.startAddAccountWorkflow()
    }

    fun onAddAccountClosed(block: () -> Unit) {
        authOrchestrator.onAddAccountResult { result -> if (result == null) block() }
    }

    suspend fun getPrimaryUserId(): UserId? {
        return accountManager.getPrimaryUserId().firstOrNull()
    }

    fun logoutPrimary() = viewModelScope.launch {
        getPrimaryUserId()?.let { userId -> disableUser(userId) }
    }

    fun clearError() {
        _errorReport.postValue(null)
    }

    fun resetCalendarsKey() {
        viewModelScope.launch {
            val userId = getPrimaryUserId() ?: run {
                logger.e("User ID was null in resetCalendarsKey")
                return@launch
            }
            val resetCalendarsKeyResult = resetCalendarsKeyUseCase.execute(userId)
            resetCalendarsKeyResult.ifSuccessAndLogErrors(logger) { }
            if (resetCalendarsKeyResult !is UseCase.Result.Success<*>) {
                disableUser(userId)
                return@launch
            }

            setupUser(userId, false)
        }
    }

    fun updatePassphrase() {
        viewModelScope.launch {
            val userId = getPrimaryUserId() ?: run {
                logger.e("User ID was null in updatePassphrase")
                return@launch
            }
            setupUser(userId, false)
        }
    }
}
