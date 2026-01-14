package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UpdateEventPersonalPartApiRequest
import me.proton.android.calendar.data.entity.NotificationEntity
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Notification
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class UpdatePersonalPartUseCase @Inject constructor(
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PERSONAL_PART"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String,
        eventId: String,
        notifications: List<Notification>?,
        color: String? = null
    ): UseCase.Result {

        return when (val updateEventPersonalPartResponse = calendarsApi.updateEventPersonalPart(
            userId,
            calendarId,
            eventId,
            UpdateEventPersonalPartApiRequest(
                notifications = notifications?.map { NotificationEntity.fromNotification(it) },
                color = color
            )
        )) {
            is ApiResponse.Success -> {
                val eventResponse = updateEventPersonalPartResponse.data.event
                calendarsRepository.persistEvents(eventResponse.toEventEntity())
                updateEventOccurrencesUseCase.execute(userId.id, eventResponse.toEventEntityMetadata())
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                UseCase.Result.Error("api error updating event personal part: $updateEventPersonalPartResponse")
            }
            is ApiResponse.Exception -> {
                UseCase.Result.Error("api error updating event personal part: ${updateEventPersonalPartResponse.exception.message ?: "(no exception message)"}")
            }
        }
    }
}
