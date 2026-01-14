package me.proton.android.calendar.domain.usecase

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import me.proton.android.calendar.common.worker.FetchCalendarsWorker
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueKey
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class ResetCalendarSearchUseCase @Inject constructor(
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository,
    private val workManager: WorkManager,
    private val logger: Logger,
) {

    suspend fun execute(userId: UserId, calendarIds: List<String>) {

        logger.i("running ResetCalendarSearchUseCase")

        val userValueStore = valueStoreProvider.provideValueStore(userId.id)

        // if search is disabled, skip everything else
        if ((userValueStore.getBoolean(ValueKey.SEARCH_ENABLED) == true).not()) return

        // clear helper data and search index
        calendarIds.forEach { calendarId ->
            userValueStore.removeKeyFromSet(ValueSet.FETCHING_CALENDAR_LAST_EVENT_ID, calendarId)
            userValueStore.removeKeyFromSet(ValueSet.FETCHING_CALENDAR_TOTAL_DOWNLOADED_COUNT, calendarId)
            calendarsRepository.deleteAllSearchEventsInCalendar(userId.id, calendarId)
        }

        // schedule worker to download the calendars again, it will treat affected calendars as non-finished
        // and attempt to fetch all events again
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest =
            OneTimeWorkRequest.Builder(FetchCalendarsWorker::class.java).setConstraints(constraints).build()
        workManager.enqueueUniqueWork(FetchCalendarsWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
    }
}
