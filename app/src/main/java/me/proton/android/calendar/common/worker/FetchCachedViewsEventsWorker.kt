package me.proton.android.calendar.common.worker

import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.proton.android.calendar.common.utils.WorkerUtils.enqueueAppending
import me.proton.core.domain.entity.UserId
import java.time.LocalDate

object FetchCachedViewsEventsWorker {

    fun enqueue(
        workManager: WorkManager,
        userId: UserId,
        calendarId: String,
        selectedDate: LocalDate,
        displayTimeZoneId: String
    ) : LiveData<Operation.State> {
        return workManager.enqueueAppending<UseCaseWorker>(workDataOf(
            UseCaseWorker.INPUT_USE_CASE_ID to UseCaseWorker.UseCaseId.FETCH_CACHED_VIEWS_EVENTS,
            UseCaseWorker.INPUT_USER_ID to userId.id,
            UseCaseWorker.INPUT_CALENDAR_ID to calendarId,
            UseCaseWorker.INPUT_DATE to selectedDate.toEpochDay(),
            UseCaseWorker.INPUT_TIME_ZONE_ID to displayTimeZoneId
        ), UseCaseWorker.UniqueWorkNames.FETCH_CACHED_VIEWS_EVENTS).state
    }
}
