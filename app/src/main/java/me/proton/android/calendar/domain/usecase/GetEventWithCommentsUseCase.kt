package me.proton.android.calendar.domain.usecase

import kotlinx.serialization.json.JsonElement
import me.proton.android.calendar.data.api.ApiResponse
import me.proton.android.calendar.data.api.EventApiResponse
import me.proton.android.calendar.domain.api.CalendarsApi
import me.proton.core.domain.entity.UserId
import javax.inject.Inject

class GetEventWithCommentsUseCase @Inject constructor(
    private val calendarApi: CalendarsApi
) {

    suspend fun execute(userId: UserId, calendarId: String, eventId: String): ApiResponse<EventApiResponse> {
        val eventResponse = calendarApi.getEvent(userId, calendarId, eventId)

        if (eventResponse is ApiResponse.Success) {
            var page = 0
            var moreAttendees = eventResponse.data.event.attendeesInfo?.moreAttendees

            val attendeesWithComments: MutableList<JsonElement> = mutableListOf()
            attendeesWithComments.addAll(eventResponse.data.event.attendeesInfo?.attendees ?: emptyList())

            while (moreAttendees == 1) {
                page++
                val fetchMoreComments = calendarApi.getEventAttendees(userId, calendarId, eventId, page)
                when (fetchMoreComments) {
                    is ApiResponse.Success -> attendeesWithComments.addAll(fetchMoreComments.data.attendees)
                    is ApiResponse.Error -> return ApiResponse.Error(
                        fetchMoreComments.httpCode,
                        fetchMoreComments.errorCode,
                        fetchMoreComments.error,
                        fetchMoreComments.shouldLog
                    )

                    is ApiResponse.Exception -> return ApiResponse.Exception(fetchMoreComments.exception)
                }
                moreAttendees = fetchMoreComments.data.moreAttendees
            }
            return eventResponse.copy(
                data = eventResponse.data.copy(
                    event = eventResponse.data.event.copy(
                        attendeesInfo = eventResponse.data.event.attendeesInfo?.copy(
                            attendees = attendeesWithComments,
                            moreAttendees = 0
                        )
                    )
                )
            )
        } else return eventResponse
    }
}