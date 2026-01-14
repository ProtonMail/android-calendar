package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadingStateUseCase @Inject constructor() : UseCase {

    private val state = MutableStateFlow(LoadingState())

    operator fun invoke(): Flow<LoadingState> = state.asStateFlow()

    fun markMinimalCalendarFetching(calendarId: String, inProgress: Boolean) {
        state.update {
            it.copy(fetchingMinimalCalendarEventsFor = if (inProgress) it.fetchingMinimalCalendarEventsFor + calendarId else it.fetchingMinimalCalendarEventsFor - calendarId)
        }
    }

    data class LoadingState(
        val fetchingMinimalCalendarEventsFor: Set<String> = emptySet(),
    ) {
        fun inProgress() = fetchingMinimalCalendarEventsFor.isNotEmpty()
    }
}
