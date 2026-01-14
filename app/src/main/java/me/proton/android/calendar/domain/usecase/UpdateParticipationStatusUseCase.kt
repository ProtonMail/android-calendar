package me.proton.android.calendar.domain.usecase

import biweekly.parameter.ParticipationStatus
import me.proton.android.calendar.common.utils.AndroidUtils.toInt
import me.proton.android.calendar.common.utils.AndroidUtils.toParticipationStatus
import me.proton.android.calendar.common.utils.EventUtilsImpl.getParticipationStatus
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.logErrorIfNeeded
import me.proton.android.calendar.data.entity.toEventEntity
import me.proton.android.calendar.data.entity.toEventEntityMetadata
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.android.calendar.domain.model.Notification
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class UpdateParticipationStatusUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val updatePersonalPartUseCase: UpdatePersonalPartUseCase,
    private val handleAlarmsUseCase: HandleAlarmsUseCase,
    private val updateEventOccurrencesUseCase: UpdateEventOccurrencesUseCase
): UseCase {

    companion object {
        const val WORKER_ID = "WORKER_ID_UPDATE_PARTICIPATION_STATUS"
        const val WORKER_ID_SINGLE_EDIT = "WORKER_ID_UPDATE_PARTICIPATION_STATUS_SINGLE_EDIT"
    }

    suspend fun execute(
        userId: UserId,
        calendarId: String,
        eventId: String,
        attendeeId: String,
        status: Int,
        personalPartICalString: String?, // if null, we don't update personal part on the server
        notifications: List<Notification>?,
        updateTime: Int? = null
    ): UseCase.Result {
        return when (val updateParticipationStatusResponse =
            calendarsApi.updateParticipationStatus(userId, calendarId, eventId, attendeeId, status, updateTime)
        ) {
            is ApiResponse.Success -> {

                // personalPartICalString == null ignore alarms update, personalPartICalString == "" clear alarms, else update event with new alarms
                if (personalPartICalString != null) {
                    // TODO Ignore update alarms errors or display snack ?
                    val updatePersonalPartUseCaseUseCaseResult = updatePersonalPartUseCase.execute(userId, calendarId, eventId, notifications)
                    if (updatePersonalPartUseCaseUseCaseResult !is UseCase.Result.Success<*>) {
                        calendarsRepository.persistEvents(updateParticipationStatusResponse.data.event.toEventEntity())
                        updateEventOccurrencesUseCase.execute(userId.id, updateParticipationStatusResponse.data.event.toEventEntityMetadata())
                    }
                } else {
                    calendarsRepository.persistEvents(updateParticipationStatusResponse.data.event.toEventEntity())
                    updateEventOccurrencesUseCase.execute(userId.id, updateParticipationStatusResponse.data.event.toEventEntityMetadata())
                }

                handleAlarmsUseCase.execute(userId)

                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> {
                updateParticipationStatusResponse.logErrorIfNeeded("api error updating participation status", logger)
                UseCase.Result.Error("api error updating participation status")
            }
            is ApiResponse.Exception -> {
                updateParticipationStatusResponse.logErrorIfNeeded("api exception updating participation status", logger)
                UseCase.Result.Error("api exception updating participation status")
            }
        }
    }

    suspend fun executeClearSingleEdits(userId: UserId, calendarId: String, eventUid: String, userEmails: List<String>, mainChainStatus: Int): UseCase.Result {

        val singleEdits = calendarsRepository.getSingleEdits(userId, eventUid)

        var singleEditsClearedSuccessfully = true
        singleEdits?.forEach { event ->
            val mainChanParticipationStatus = mainChainStatus.toParticipationStatus()
            if (event.currentUserAttendeeId == null || event.getParticipationStatus(userEmails) == mainChanParticipationStatus) return@forEach
            when (val updateParticipationStatusResponse =
                calendarsApi.updateParticipationStatus(userId, calendarId, event.id, event.currentUserAttendeeId, ParticipationStatus.NEEDS_ACTION.toInt()) // 0 == NEEDS_ACTION
            ) {
                is ApiResponse.Success -> {
                    // Clear alarms
                    // TODO Ignore update alarms errors or display snack ?
                    val updatePersonalPartUseCaseUseCaseResult = updatePersonalPartUseCase.execute(userId, calendarId, event.id, emptyList())
                    updatePersonalPartUseCaseUseCaseResult.ifSuccessAndLogErrors(logger) { }
                }
                is ApiResponse.Error -> {
                    singleEditsClearedSuccessfully = false
                    logger.e("api error updating single edit participation status: ${updateParticipationStatusResponse.error}")
                }
                is ApiResponse.Exception -> {
                    singleEditsClearedSuccessfully = false
                    logger.e("api error updating single edit participation status: ${updateParticipationStatusResponse.exception.message ?: "(no exception message)"}")
                }
            }
        }

        when (val eventsSharingUidResponse = calendarsApi.getEventsByUid(userId, eventUid, 0, 100)) { // TODO pagination
            is ApiResponse.Success -> {
                calendarsRepository.persistEvents(*eventsSharingUidResponse.data.events.map { it.toEventEntity() }.toTypedArray())
                eventsSharingUidResponse.data.events.map { it.toEventEntityMetadata() }.forEach {
                    updateEventOccurrencesUseCase.execute(userId.id, it)
                }
                handleAlarmsUseCase.execute(userId)
            }
            is ApiResponse.Error -> {
                logger.e("api error fetching events by uid: ${eventsSharingUidResponse.error}")
            }
            is ApiResponse.Exception -> {
                logger.e("api error fetching events by uid: ${eventsSharingUidResponse.exception.message ?: "(no exception message)"}")
            }
        }

        return if (singleEditsClearedSuccessfully) UseCase.Result.Success<Unit>()
        else UseCase.Result.Error("Failed to update participation status for one or more single edits")
    }
}
