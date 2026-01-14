package me.proton.android.calendar.eventmanager.listeners.core

import androidx.work.WorkManager
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.common.worker.MembersKeySetupWorker
import me.proton.android.calendar.data.api.CalendarMembersEvents
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.MemberEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.eventmanager.listeners.CalendarBaseEventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.entity.Action
import me.proton.core.eventmanager.domain.entity.Event
import me.proton.core.eventmanager.domain.entity.EventsResponse
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.util.kotlin.deserialize
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class CalendarMemberEventListener @Inject constructor(
    db: AppDatabase,
    private val calendarsRepository: CalendarsRepository,
    private val logger: Logger,
    private val userAddressManager: UserAddressManager,
    private val workManager: WorkManager
): CalendarBaseEventListener<String, MemberEntity>(db) {
    override val order: Int = 2
    override val type: Type = Type.Core

    private val membersWithIncompleteKeySetup: HashSet<String> = hashSetOf()
    private val deletedMembersToCalendars: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    override suspend fun deserializeEvents(
        config: EventManagerConfig,
        response: EventsResponse
    ): List<Event<String, MemberEntity>>? {
        return response.body.deserialize<CalendarMembersEvents>().calendarMembers?.map {
            Event(requireNotNull(Action.map[it.action]), it.id, it.member)
        }
    }

    override suspend fun onCreateOrUpdate(config: EventManagerConfig, entities: List<MemberEntity>) {
        entities.forEach {
            if (calendarsRepository.hasCalendar(it.calendarId)) {
                calendarsRepository.persistMember(it)
                // Keep ids of members with flag incomplete setup so that we can call the use case in worker from onSuccess
                if (it.hasIncompleteKeySetup) membersWithIncompleteKeySetup.add(it.id)
            } else {
                logger.i("action CREATE/UPDATE for calendarMember ${it.id} in deleted calendar ${it.calendarId}")
            }
        }
    }

    override suspend fun onDelete(config: EventManagerConfig, keys: List<String>) {
        super.onDelete(config, keys)

        keys.forEach { memberId ->
            // Member is about to be deleted so let's remember its CalendarID
            val calendarId = calendarsRepository.selectMemberById(memberId)?.calendarId

            calendarsRepository.deleteMemberById(memberId)

            // We keep the id so that we can check if calendar linked to that member needs to be deleted too
            calendarId?.let {
                deletedMembersToCalendars[memberId] = calendarId
            }
        }
    }

    override suspend fun onSuccess(config: EventManagerConfig) {
        super.onSuccess(config)

        // Get user addresses emails to compare with members email
        var addresses: List<UserAddress>? = null
        deletedMembersToCalendars.forEach { (_, calendarId) ->

            if (addresses.isNullOrEmpty()) {
                addresses = userAddressManager.getAddressesOrNull(config.userId)
            }

            // Check that member is last user member for that calendar
            addresses?.let { addresses ->
                // Get all members for that calendar
                val calendarMembers = calendarsRepository.selectCalendarMembers(calendarId)
                // Find the member that belongs to the current user
                val userMember = calendarsRepository.getUserMember(addresses, calendarMembers)
                // If user member doesn't exists, safely delete calendar
                if (userMember == null) {
                    calendarsRepository.deleteCalendarById(calendarId)
                }
            }
        }

        // Launch worker to do key setup for members with incomplete setup flag
        MembersKeySetupWorker.enqueue(workManager, config.userId.id, membersWithIncompleteKeySetup)
    }

    override suspend fun onComplete(config: EventManagerConfig) {
        super.onComplete(config)

        deletedMembersToCalendars.clear()
        membersWithIncompleteKeySetup.clear()
    }

    override suspend fun onResetAll(config: EventManagerConfig) {
        super.onResetAll(config)
        // Nothing to do here since CalendarListener.resetAll will also delete members and do the bootstrap
    }
}
