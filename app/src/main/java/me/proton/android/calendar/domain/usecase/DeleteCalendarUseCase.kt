package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.common.utils.ProtonUtilsImpl.sortPersonalCalendars
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class DeleteCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val updateCalendarUserSettingsUseCase: UpdateCalendarUserSettingsUseCase,
    private val calendarsRepository: CalendarsRepository
) : UseCase {

    sealed class DeleteCalendarOption {
        data class Error(val reason: String) : DeleteCalendarOption()

        sealed class Delete(open val calendarId: String) : DeleteCalendarOption() {
            data class DefaultLastActive(override val calendarId: String) : Delete(calendarId)
            data class DefaultNextActive(
                override val calendarId: String,
                val nextDefaultId: String,
                val nextDefaultName: String
            ) : Delete(calendarId)

            data class NonDefault(override val calendarId: String) : Delete(calendarId)
        }
    }

    /**
     * Returns allowed mode of deleting the Calendar
     */
    suspend fun prepare(userId: UserId, calendarId: String): DeleteCalendarOption {

        val calendar = calendarsRepository.selectCalendar(calendarId)
            ?: return DeleteCalendarOption.Error("could not select Calendar from DB")
        // Do not use getDefaultCalendarIdWithFallback here as we only want to update the default calendar field if value was set
        val defaultCalendarId = calendarsRepository.getDefaultCalendarId(userId.id)
        val isCalendarDefault =
            calendar.id == defaultCalendarId && calendar.isActive && calendar.isSubscribed.not()
        val activeOwnedUserCalendars = calendarsRepository.selectActiveUserCalendars(userId.id).filter { it.isOwner }

        return if (isCalendarDefault) {

            // Pick the first from active personal calendars sorted by priority as next default calendar
            val nextDefaultCalendar = sortPersonalCalendars(
                activeOwnedUserCalendars,
                null
            ).firstOrNull { it.id != calendarId }
            if (nextDefaultCalendar != null) {
                DeleteCalendarOption.Delete.DefaultNextActive(
                    calendarId,
                    nextDefaultCalendar.id,
                    nextDefaultCalendar.name
                )
            } else {
                // there is no other active User Calendar to make it the next default
                DeleteCalendarOption.Delete.DefaultLastActive(calendarId)
            }

        } else {
            DeleteCalendarOption.Delete.NonDefault(calendarId)
        }

    }

    /**
     * @return [UseCase.Result.InvalidParams] if "password confirmation" failed
     */
    suspend fun execute(userId: UserId, deleteOption: DeleteCalendarOption): UseCase.Result {

        // delete the Calendar
        val deleteSuccess = when (deleteOption) {
            is DeleteCalendarOption.Error -> return UseCase.Result.Error("DeleteCalendarUseCase: invalid Delete Option")
            is DeleteCalendarOption.Delete -> {
                val deleteCalendarResponse = calendarsApi.deleteCalendar(userId, deleteOption.calendarId)

                if (deleteCalendarResponse is ApiResponse.Error && deleteCalendarResponse.httpCode == 503) {
                    calendarsRepository.pingServer(userId)
                }
                if (deleteCalendarResponse is ApiResponse.Error && deleteCalendarResponse.httpCode == 403 && deleteCalendarResponse.errorCode == 403) {
                    return UseCase.Result.InvalidParams("DeleteCalendarUseCase: password confirmation failed")
                }

                deleteCalendarResponse.valueOrNullAndLogErrors(logger)?.isSuccessful ?: false
            }
        }

        return if (deleteSuccess) {
            // delete from local database
            calendarsRepository.deleteCalendarById(deleteOption.calendarId)

            // set the new default Calendar in API but ignore error
            if (deleteOption is DeleteCalendarOption.Delete.DefaultNextActive) {
                updateCalendarUserSettingsUseCase.executeDefaultCalendarId(userId, deleteOption.nextDefaultId)
                    .ifSuccessAndLogErrors(logger) { }
            }

            UseCase.Result.Success<Unit>()
        } else {
            UseCase.Result.Error("DeleteCalendarUseCase: error deleting calendar")
        }

    }

}
