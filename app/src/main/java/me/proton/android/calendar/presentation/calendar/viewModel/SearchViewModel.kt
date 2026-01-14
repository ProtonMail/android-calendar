package me.proton.android.calendar.presentation.calendar.viewModel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.map
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.worker.FetchCalendarsWorker
import me.proton.android.calendar.data.entity.CalendarUserSettingsEntity
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.entity.User
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    application: Application,
    private val accountManager: AccountManager,
    private val calendarsRepository: CalendarsRepository,
    private val valueStoreProvider: ValueStoreProvider,
    private val workManager: WorkManager,
) : AndroidViewModel(application) {

    private var viewModelJob = Job()
    private var coroutineScope = CoroutineScope(Dispatchers.Default)
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private lateinit var userId: UserId

    lateinit var calendarUserSettings: CalendarUserSettingsEntity
    lateinit var userSettings: UserSettingsEntity
    lateinit var user: User

    private val _downloadingState = MutableStateFlow<DownloadingState>(DownloadingState.INIT)
    val downloadingState = _downloadingState.asStateFlow()

    private val _calendarDownloadEnabledState = MutableStateFlow(false)
    val calendarDownloadEnabledState = _calendarDownloadEnabledState.asStateFlow()

    init {
        coroutineScope.launch {
            _calendarDownloadEnabledState.update { isCalendarDownloadEnabled() }
        }
    }

    sealed class DownloadingState {
        object INIT: DownloadingState()
        object NONE: DownloadingState()
        object PAUSED: DownloadingState()
        object FINISHED: DownloadingState()
        object ERROR: DownloadingState()

        class ONGOING(val progressPercentage: Int, val progressText: String): DownloadingState()
    }

    fun startObservingWorkerState(viewLifecycleOwner: LifecycleOwner) {

        var lastWorkerState: WorkInfo.State? = null

        workManager.getWorkInfosForUniqueWorkLiveData(FetchCalendarsWorker.UNIQUE_WORK_NAME)
            .map { it.firstOrNull() }.observe(viewLifecycleOwner, Observer<WorkInfo?> {

                val progressPercentage = it?.progress?.getInt(FetchCalendarsWorker.PROGRESS_KEY_PROGRESS_PERCENTAGE, 0) ?: 0
                val progressText = it?.progress?.getString(FetchCalendarsWorker.PROGRESS_KEY_PROGRESS_TIME_LEFT) ?: ""

                when(it?.state) {
                    WorkInfo.State.ENQUEUED -> {
                        if (lastWorkerState == WorkInfo.State.RUNNING) { // lost internet connection
                            _downloadingState.update { DownloadingState.PAUSED }
                        } else if (lastWorkerState == null) { // last run failed, work is enqueued for retry when conditions are met
                            _downloadingState.update { DownloadingState.PAUSED }
                        }
                    }
                    WorkInfo.State.RUNNING -> {
                        _downloadingState.update { DownloadingState.ONGOING(progressPercentage, progressText) }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        if (lastWorkerState != null) { // just finished downloading
                            _downloadingState.update { DownloadingState.FINISHED }
                        } else {
                            coroutineScope.launch {
                                if (isCalendarDownloadEnabled()) {
                                    // worker finished when SearchViewModel was outside of scope
                                    _downloadingState.update { DownloadingState.FINISHED }
                                } else {
                                    // worker finished correctly before, but we're running it again
                                    //  (most likely after manually clearing search DB)
                                    _downloadingState.update { DownloadingState.NONE }
                                }
                            }
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        coroutineScope.launch {
                            if (isCalendarDownloadEnabled()) {
                                // worker failed last time
                                _downloadingState.update { DownloadingState.ERROR }
                            } else {
                                // worker failed but after that we also disabled search altogether
                                _downloadingState.update { DownloadingState.NONE }
                            }
                        }
                    }
                    WorkInfo.State.BLOCKED -> {
                        _downloadingState.update { DownloadingState.ERROR }
                    }
                    WorkInfo.State.CANCELLED -> {

                        coroutineScope.launch {
                            if (isCalendarDownloadEnabled()) {
                                // there is no real pause in WorkManager so we use this state for pausing
                                _downloadingState.update { DownloadingState.PAUSED }
                            } else {
                                // we paused downloading but after that, also disabled search altogether
                                _downloadingState.update { DownloadingState.NONE }
                            }
                        }

                    }
                    null -> {
                        // worker has never been started before
                        _downloadingState.update { DownloadingState.NONE }
                    }
                }

                it?.let { lastWorkerState = it.state }

            })
    }

    /**
     * @return WorkRequest ID
     */
    fun enableCalendarDownload(): java.util.UUID {

        _calendarDownloadEnabledState.update { true }

        coroutineScope.launch {
            accountManager.getPrimaryUserId().firstOrNull()?.let {
                valueStoreProvider.provideValueStore(it.id).putBoolean(ValueKey.SEARCH_ENABLED, true)
            }
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest =
            OneTimeWorkRequest.Builder(FetchCalendarsWorker::class.java).setConstraints(constraints).build()
        workManager.enqueueUniqueWork(FetchCalendarsWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
        return workRequest.id
    }

    fun disableCalendarDownload() {

        _calendarDownloadEnabledState.update { false }

        // cancel Work
        workManager.cancelUniqueWork(FetchCalendarsWorker.UNIQUE_WORK_NAME)

        coroutineScope.launch {
            accountManager.getPrimaryUserId().firstOrNull()?.let {
                // delete all helper values from ValueStore
                with(valueStoreProvider.provideValueStore(it.id)) {
                    putBoolean(ValueKey.SEARCH_ENABLED, false)
                    removeKeySet(ValueSet.FETCHING_CALENDAR_LAST_EVENT_ID)
                    removeKeySet(ValueSet.FETCHING_CALENDAR_TOTAL_DOWNLOADED_COUNT)
                }

                // delete all data from SearchDB
                calendarsRepository.deleteAllSearchEvents(it.id)
            }
        }
    }

    fun pauseCalendarDownload() {
        // pausing is really cancelling the Work and counting on it to be properly resumed later
        workManager.cancelUniqueWork(FetchCalendarsWorker.UNIQUE_WORK_NAME)
    }

    fun actionButtonClicked() {

        when (downloadingState.value) {
            DownloadingState.INIT -> { } // shouldn't happen
            DownloadingState.NONE -> { // start downloading
                enableCalendarDownload()
            }
            is DownloadingState.ONGOING -> { // pause downloading
                pauseCalendarDownload()
            }
            DownloadingState.PAUSED -> { // resume downloading
                enableCalendarDownload()
            }
            DownloadingState.FINISHED -> { } // shouldn't really happen, button should be invisible
            DownloadingState.ERROR -> { // retry = resume downloading
                enableCalendarDownload()
            }
        }

    }

    suspend fun isCalendarDownloadEnabled(): Boolean {

        return accountManager.getPrimaryUserId().firstOrNull()?.let {
            valueStoreProvider.provideValueStore(it.id).getBoolean(ValueKey.SEARCH_ENABLED) ?: false
        } ?: false
    }

    fun clearDownloadingState() {
        _downloadingState.update { DownloadingState.NONE }
    }

}

