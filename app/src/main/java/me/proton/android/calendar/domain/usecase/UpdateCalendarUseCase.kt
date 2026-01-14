package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.UpdateCalendarDisplayApiRequest
import me.proton.android.calendar.data.api.UpdateMemberApiRequest
import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class UpdateCalendarUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository,
    private val database: AppDatabase,
): UseCase {

    companion object {
        const val WORKER_ID = "UPDATE_CALENDAR"
        const val WORKER_LIST_ID = "UPDATE_CALENDAR_LIST"
    }

    // Update Single Calendar on Server
    suspend fun executeUpdateDisplayFromDb(userId: UserId, calendarId: String) : UseCase.Result {

        val dbMember = calendarsRepository.selectCalendarUserMember(calendarId) ?: return UseCase.Result.InvalidParams("UpdateCalendarUseCase: executeUpdateFromDb DB member was null")

        val updateMemberApiRequest = UpdateMemberApiRequest(
            display = dbMember.display
        )

        return updateSingleCalendarDisplay(userId, calendarId, dbMember.id, updateMemberApiRequest)
    }

    suspend fun executeUpdate(userId: UserId, calendarId: String, description: String? = null, name: String? = null, color: String? = null, display: Int? = null) : UseCase.Result {
        val dbMember = calendarsRepository.selectCalendarUserMember(calendarId) ?: return UseCase.Result.InvalidParams("UpdateCalendarUseCase: member was null")

        val updateMemberApiRequest = UpdateMemberApiRequest(
            color = color?.takeIf { !dbMember.color.equals(color, ignoreCase = true) }, // No need to send color if it hasn't changed. It also lets us make sure we don't send old color values to BE.
            display = display?.takeIf { dbMember.display != display },
            name = name?.takeIf { !dbMember.name.equals(name, ignoreCase = true) },
            description = description?.takeIf { !dbMember.description.equals(description, ignoreCase = true) }
        )

        return updateSingleCalendar(userId, calendarId, dbMember.id, updateMemberApiRequest)
    }

    private suspend fun updateSingleCalendarDisplay(
        userId: UserId,
        calendarId: String,
        memberId: String,
        updateMemberApiRequest: UpdateMemberApiRequest
    ): UseCase.Result {
        return when (val updateMemberResponse =
            calendarsApi.updateMember(userId, calendarId, memberId, updateMemberApiRequest)) {
            is ApiResponse.Success -> {
                calendarsRepository.persistMember(updateMemberResponse.data.member)
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update member: ${updateMemberResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update member: ${updateMemberResponse.exception.message ?: "(no exception message)"}")
        }
    }

    private suspend fun updateSingleCalendar(
        userId: UserId,
        calendarId: String,
        memberId: String,
        updateMemberApiRequest: UpdateMemberApiRequest
    ): UseCase.Result {
        return when (val updateMemberResponse =
            calendarsApi.updateMember(userId, calendarId, memberId, updateMemberApiRequest)) {
            is ApiResponse.Success -> {
                calendarsRepository.persistMember(updateMemberResponse.data.member)
                UseCase.Result.Success<Unit>()
            }
            is ApiResponse.Error -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update member: ${updateMemberResponse.error}")
            is ApiResponse.Exception -> UseCase.Result.Error("UpdateCalendarUseCase: executeUpdate error in update member: ${updateMemberResponse.exception.message ?: "(no exception message)"}")
        }
    }

    //Update Calendars Display values on Server
    suspend fun updateAllCalendarsDisplay(userId: UserId) : UseCase.Result {
        val dbCalendarMembers = database.membersDao().selectByUserId(userId.id).groupBy { it.calendarId }

        // Map<CalendarId, Pair<MemberId, DisplayInt>>
        val serverCalendarMembers = dbCalendarMembers.mapValues {
            calendarsApi.getMemberList(userId, it.key).valueOrNullAndLogErrors(logger)?.members
        }

        val membersToUpdateDisplay = dbCalendarMembers.filter {
            it.value.first().display != serverCalendarMembers[it.key]?.firstOrNull()?.display
        }

        val updateCalendarDisplayResponses = arrayListOf<UseCase.Result>()
        membersToUpdateDisplay.forEach {
            updateCalendarDisplayResponses.add(
                when (val updateCalendarDisplayResponse = calendarsApi.updateCalendarDisplay(userId, calendarId = it.key, memberId = it.value.first().id, UpdateCalendarDisplayApiRequest(display = it.value.first().display))) {
                    is ApiResponse.Success -> {
                        UseCase.Result.Success<Unit>()
                    }
                    is ApiResponse.Error ->
                        UseCase.Result.Error("UpdateCalendarUseCase: executeUpdateList error in update calendar display: ${updateCalendarDisplayResponse.error}")
                    is ApiResponse.Exception ->
                        UseCase.Result.Error("UpdateCalendarUseCase: executeUpdateList error in update calendar display: ${updateCalendarDisplayResponse.exception.message ?: "(no exception message)"}")
                }
            )
        }

        updateCalendarDisplayResponses.forEach {
            if (it !is UseCase.Result.Success<*>) return it
        }

        return UseCase.Result.Success<Unit>()
    }
}
