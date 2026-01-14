package me.proton.android.calendar.eventmanager

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.model.Calendar
import me.proton.core.account.domain.entity.AccountState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.getAccounts
import me.proton.core.domain.entity.UserId
import me.proton.core.eventmanager.data.CoreEventManagerStarter
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.eventmanager.domain.EventManagerConfig
import me.proton.core.eventmanager.domain.EventManagerProvider
import me.proton.core.util.kotlin.CoroutineScopeProvider
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarEventManagerStarter @Inject constructor(
    private val coreEventManagerStarter: CoreEventManagerStarter,
    private val eventManagerProvider: EventManagerProvider,
    private val accountManager: AccountManager,
    private val calendarsRepository: CalendarsRepository,
    private val scopeProvider: CoroutineScopeProvider
) {
    fun start() {
        // Start/Stop Core EventLoop.
        coreEventManagerStarter.start()

        // Start/Stop Calendar EventLoop.
        accountManager.getAccounts(AccountState.Ready)
            .mapLatest { list -> list.map { it.userId } }
            .distinctUntilChanged()
            .flatMapLatest { userIds -> observeAllCalendarsForUsers(userIds) }
            .onEach { map ->
                for ((userId, calendars) in map) {
                    stopAllCalendarLoop(userId)
                    startAllCalendarLoop(userId, calendars)
                }
            }.launchIn(scopeProvider.GlobalDefaultSupervisedScope)
    }

    private fun observeAllCalendarsForUsers(userIds: List<UserId>): Flow<Map<UserId, Set<Calendar>>> =
        combine(userIds.map { userId -> observeUserCalendars(userId).map { userId to it } }) { it.toMap() }

    @OptIn(FlowPreview::class)
    private fun observeUserCalendars(userId: UserId): Flow<Set<Calendar>> =
        combine(
            calendarsRepository.flowUserCalendars(userId.id),
            calendarsRepository.flowSubscribedCalendars(userId.id),
            calendarsRepository.flowHolidayCalendars(userId.id)
        ) { calendars, subscriptions, holidayCalendars ->
            (calendars + subscriptions + holidayCalendars).toSet()
        }.debounce(TimeUnit.SECONDS.toMillis(2)).distinctUntilChanged()

    private suspend fun stopAllCalendarLoop(userId: UserId) {
        // Stop all calendars managers, for this userId.
        eventManagerProvider.getAll(userId)
            .filter { it.config.listenerType == EventListener.Type.Calendar }
            .forEach { manager -> manager.stop() }
    }

    private suspend fun startAllCalendarLoop(userId: UserId, calendars: Set<Calendar>) {
        // Start all enabled calendars, for this userId.
        calendars.filter { it.display }.forEach {
            eventManagerProvider.get(EventManagerConfig.Calendar(userId, it.id)).start()
        }
    }
}
