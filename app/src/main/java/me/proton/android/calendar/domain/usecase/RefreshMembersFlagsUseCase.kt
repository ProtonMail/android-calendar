package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.data.api.valueOrNullAndLogErrors
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class RefreshMembersFlagsUseCase @Inject constructor(
    private val logger: Logger,
    private val calendarsApi: CalendarsApi,
    private val calendarsRepository: CalendarsRepository
) {

    companion object {
        const val REFRESH_MEMBERS_FLAGS = "REFRESH_MEMBERS_FLAGS"
    }

    suspend operator fun invoke(
        userId: UserId
    ): UseCase.Result {
        
        // TODO We could probably optimise by only refreshing members linked to addressIds that changed

        val remoteMembers = calendarsApi.getAllMembers(userId).valueOrNullAndLogErrors(logger)
        remoteMembers?.members?.forEach {
            if (calendarsRepository.hasCalendar(it.calendarId)) {
                calendarsRepository.persistMember(it)
            } else {
                logger.i("RefreshMembersFlags: Member's Calendar doesn't exist locally")
                // TODO Should we fetch and bootstrap calendar if that happens ? Check Sentry if log appeared
            }
        }

        return UseCase.Result.Success<Unit>()
    }

}