@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.common.utils.CalendarUnleashVariants
import me.proton.android.calendar.data.CalendarsRepositoryImpl.FetchWindow
import me.proton.core.domain.entity.UserId
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureFlag
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class GetFetchedEventWindowsValidity @Inject constructor(
    private val featureFlagManager: FeatureFlagManager,
) {
    suspend fun getValidityDuration(userId: String): Duration =
        featureFlagManager.get(UserId(userId), featureId = CalendarUnleashVariants.FetchedEventsValidityMin)
            ?.payloadValue?.toLongOrNull()
            ?.let { Duration.ofMinutes(it) }
            ?: DefaultValidityDuration

    suspend fun shouldUseFetchedEventsMetadata(fetchWindow: FetchWindow) = featureFlagManager.getOrDefault(
        fetchWindow.userId,
        CalendarFeatureFlag.FetchedEventsCacheAndroid.featureId,
        FeatureFlag.default(
            CalendarFeatureFlag.FetchedEventsCacheAndroid.featureId.id,
            CalendarFeatureFlag.FetchedEventsCacheAndroid.fallbackValue
        )
    ).value

    suspend fun isWindowFetchValid(window: FetchWindow, fetchTime: Instant): Boolean =
        fetchTime.isAfter(Instant.now() - getValidityDuration(window.userId.id))
}

private val DefaultValidityDuration = Duration.ofMinutes(180)
