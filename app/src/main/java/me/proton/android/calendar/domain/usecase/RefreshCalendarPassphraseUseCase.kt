package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

/**
 * Fetches from API and saves CalendarUserSettings in DB.
 */
class RefreshCalendarPassphraseUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsRepository: CalendarsRepository,
    private val calendarsApi: CalendarsApi,
    private val cacheCalendarPassphraseUseCase: CacheCalendarPassphraseUseCase
) {

    companion object {
        const val REFRESH_CALENDAR_PASSPHRASE = "REFRESH_CALENDAR_PASSPHRASE"
    }

    suspend operator fun invoke(
        userId: UserId,
        calendarId: String
    ): UseCase.Result {

        val calendarActivePassphraseResponse = calendarsApi.getActivePassphrase(userId, calendarId)
        if (calendarActivePassphraseResponse !is ApiResponse.Success) {
            return UseCase.Result.Error("RefreshCalendarPassphrasesUseCase: error getting calendar active passphrase from API: $calendarActivePassphraseResponse")
        }

        val calendarPassphrase = calendarActivePassphraseResponse.data.passphrase

        // Persist active passphrase in DB
        calendarsRepository.persistCalendarPassphrase(calendarPassphrase)

        // Cache calendar passphrase
        return cacheCalendarPassphraseUseCase.execute(userId, calendarId)
    }


}
