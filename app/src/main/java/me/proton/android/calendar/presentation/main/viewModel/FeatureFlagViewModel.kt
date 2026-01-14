package me.proton.android.calendar.presentation.main.viewModel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import me.proton.android.calendar.common.FETCH_FEATURE_FLAG_INTERVAL_SECONDS
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.model.MeetIntegrationType
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import me.proton.core.presentation.viewmodel.ViewModelResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class FeatureFlagViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val featureFlagManager: FeatureFlagManager,
    private val logger: Logger
) : ViewModel() {

    private val mutableState = MutableStateFlow<ViewModelResult<FeatureFlag>>(ViewModelResult.Processing)
    val state = mutableState.asStateFlow()

    var colorPerEventFeatureFlag: LiveData<Boolean> = MutableLiveData()
    var eventSearchFeatureFlag: LiveData<Boolean> = MutableLiveData()
    var splitViewVerticalScrollingFlag: LiveData<Boolean> = MutableLiveData()
    var zoomIntegrationAndroidFlag: LiveData<Boolean> = MutableLiveData()
    var protonMeetIntegrationFlag: LiveData<Boolean> = MutableLiveData()
    var fetchedEventsCacheAndroidFlag: LiveData<Boolean> = MutableLiveData()
    var rsvpCommentsAndroidFlag: LiveData<Boolean> = MutableLiveData()
    var refreshButtonAndroidFlag: LiveData<Boolean> = MutableLiveData()

    private var lastFetchMs = 0L

    fun prefetchForCurrentUser() {
        if (System.currentTimeMillis().minus(lastFetchMs) <= TimeUnit.SECONDS.toMillis(FETCH_FEATURE_FLAG_INTERVAL_SECONDS)) return
        accountManager.getPrimaryUserId().filterNotNull().mapLatest { userId ->
            val featureIds = CalendarFeatureFlag.entries.filter { !it.isLocalFlag }.map { it.featureId }.toSet()
            featureFlagManager.prefetch(userId, featureIds)
            lastFetchMs = System.currentTimeMillis()
        }.launchIn(viewModelScope)
    }

    /**
     * Use this init method to initialize the remote feature flags we want to observe.
     */
    fun initRemoteFeatureFlagsToObserve(userId: UserId) {
        // Color per event feature flag
        colorPerEventFeatureFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.ColorPerEventAndroid.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.ColorPerEventAndroid.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // Event search feature flag
        eventSearchFeatureFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.EventSearchAndroid.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.EventSearchAndroid.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // Split agenda view vertical scrolling flag, CALAND-2905
        splitViewVerticalScrollingFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.SplitViewVerticalScrollingAndroid.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.SplitViewVerticalScrollingAndroid.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // Fetched Events Cache feature flag
        fetchedEventsCacheAndroidFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.FetchedEventsCacheAndroid.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.FetchedEventsCacheAndroid.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // Zoom integration feature flag
        zoomIntegrationAndroidFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.ZoomIntegrationAndroid.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.ZoomIntegrationAndroid.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // Proton meet integration feature flag
        protonMeetIntegrationFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.ProtonMeet.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.ProtonMeet.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // RSVP comments feature flag, CALAND-2951
        rsvpCommentsAndroidFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.RsvpCommentsAndroid.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.RsvpCommentsAndroid.fallbackValue
        }.asLiveData(Dispatchers.Default)

        // Sidebar refresh button
        refreshButtonAndroidFlag = featureFlagManager.observe(
            userId,
            CalendarFeatureFlag.RefreshButton.featureId
        ).map {
            it?.value ?: CalendarFeatureFlag.RefreshButton.fallbackValue
        }.asLiveData(Dispatchers.Default)
    }

    private suspend fun isFeatureEnabled(calendarFeatureFlag: CalendarFeatureFlag): Boolean {
        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: return false
        return featureFlagManager.getOrDefault(
            userId,
            calendarFeatureFlag.featureId,
            FeatureFlag.default(
                calendarFeatureFlag.featureId.id,
                calendarFeatureFlag.fallbackValue
            )
        ).value
    }

    private suspend fun getFeatureFlag(calendarFeatureFlag: CalendarFeatureFlag): FeatureFlag {
        val defaultFeatureFlag = FeatureFlag.default(
            calendarFeatureFlag.featureId.id,
            calendarFeatureFlag.fallbackValue
        )
        val userId = accountManager.getPrimaryUserId().firstOrNull() ?: return defaultFeatureFlag
        return featureFlagManager.getOrDefault(
            userId,
            calendarFeatureFlag.featureId,
            defaultFeatureFlag
        )
    }

    private suspend fun updateFeatureFlag(calendarFeatureFlag: CalendarFeatureFlag, value: Boolean) {
        val featureFlag = getFeatureFlag(calendarFeatureFlag)
        val updatedFeatureFlag = featureFlag.copy(defaultValue = featureFlag.defaultValue, value = value)
        featureFlagManager.update(updatedFeatureFlag)
    }

    fun isColorPerEventEnabled(): Boolean {
        return colorPerEventFeatureFlag.value ?: CalendarFeatureFlag.ColorPerEventAndroid.fallbackValue
    }

    fun isEventSearchEnabled(): Boolean {
        return eventSearchFeatureFlag.value ?: CalendarFeatureFlag.EventSearchAndroid.fallbackValue
    }

    fun isSplitViewVerticalScrollingEnabled(): Boolean {
        return splitViewVerticalScrollingFlag.value ?: CalendarFeatureFlag.SplitViewVerticalScrollingAndroid.fallbackValue
    }

    private fun isZoomIntegrationEnabled(): Boolean {
        return zoomIntegrationAndroidFlag.value ?: CalendarFeatureFlag.ZoomIntegrationAndroid.fallbackValue
    }

    private fun isProtonMeetIntegrationEnabled(): Boolean {
        return protonMeetIntegrationFlag.value ?: CalendarFeatureFlag.ProtonMeet.fallbackValue
    }

    fun enabledMeetIntegrations(): Set<MeetIntegrationType> = setOfNotNull(
        MeetIntegrationType.Zoom.takeIf { isZoomIntegrationEnabled() },
        MeetIntegrationType.ProtonMeet.takeIf { isProtonMeetIntegrationEnabled() }
    )

    fun isRsvpCommentsEnabled(): Boolean {
        return rsvpCommentsAndroidFlag.value ?: CalendarFeatureFlag.RsvpCommentsAndroid.fallbackValue
    }

    suspend fun isPlayStoreRatingEnabled(): Boolean {
        return isFeatureEnabled(CalendarFeatureFlag.RatingAndroidCalendar)
    }

    suspend fun isServerDownBannerEnabled(): Boolean {
        return isFeatureEnabled(CalendarFeatureFlag.CalendarAndroidServerDownBanner)
    }

    suspend fun reportPlayStoreRatingFlowStarted() {
        updateFeatureFlag(CalendarFeatureFlag.RatingAndroidCalendar, false)
    }
}
