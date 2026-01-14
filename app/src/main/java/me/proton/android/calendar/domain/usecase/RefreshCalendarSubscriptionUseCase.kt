package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.api.SettingsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarSubscriptionUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val calendarsApi: CalendarsApi
) {

    companion object {
        const val REFRESH_CALENDAR_SUBSCRIPTION = "REFRESH_CALENDAR_SUBSCRIPTION"
    }

    suspend operator fun invoke(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        val calendarSubscriptionResponse = calendarsApi.getCalendarSubscription(userId, calendarId)
        if (calendarSubscriptionResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarSubscriptionUseCase: error getting calendar subscription from API: $calendarSubscriptionResponse")
        }

        val calendarSubscription = calendarSubscriptionResponse.data.calendarSubscription

        // Persist new settings in DB
        calendarsRepository.persistCalendarSubscription(calendarSubscription)

        return UseCase.Result.Success(calendarSubscription)
    }


}
