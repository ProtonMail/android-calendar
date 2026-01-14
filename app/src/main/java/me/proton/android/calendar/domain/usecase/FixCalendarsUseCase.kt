package me.proton.android.calendar.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import me.proton.android.calendar.common.utils.getAddressesOrNull
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.CalendarEntity
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ValueSet
import me.proton.android.calendar.domain.ValueStoreProvider
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.util.kotlin.takeIfNotEmpty
import timber.log.Timber
import java.time.ZoneId
import javax.inject.Inject

class FixCalendarsUseCase @Inject constructor(
    private val logger: Logger,
    private val database: AppDatabase,
    private val json: Json,
    private val userAddressManager: UserAddressManager,
    private val bootstrapCalendarUseCase: BootstrapCalendarUseCase,
    private val valueStoreProvider: ValueStoreProvider,
    private val calendarsRepository: CalendarsRepository
): UseCase {

    companion object {
        const val WORKER_ID = "FIX_CALENDARS"
    }

    suspend fun execute(userId: UserId): UseCase.Result {
        // Try to get addresses
        val userAddresses = userAddressManager.getAddressesOrNull(userId)
            ?: run {
                // Force refresh if userAddresses are null
                logger.i("FixCalendarsUseCase, userAddresses were null, calling getAddressesOrNull with refresh true")
                userAddressManager.getAddressesOrNull(userId, refresh = true)
            } ?: run {
                // If user addresses are still null, log and return
                logger.e("FixCalendarsUseCase, userAddresses are null")
                // We use InvalidParams because we don't want the worker to keep retrying this
                return UseCase.Result.InvalidParams("FixCalendarsUseCase, userAddresses are null")
            }

        val backendCalendarEntities = calendarsRepository.fetchCalendarEntities(userId) ?: return UseCase.Result.Error("FixCalendarsUseCase, error fetching calendars from backend")
        val calendarEntities = database.calendarsDao().selectCalendars(userId.id)

        if (calendarEntities.size < backendCalendarEntities.size) {
            Timber.e("FixCalendarsUseCase, more calendars on backend than locally")
        }

        val calendarsToBootstrap = arrayListOf<CalendarEntity>()
        backendCalendarEntities.forEach { calendarEntity ->
            val calendarId = calendarEntity.id

            // Check calendar private keys
            database.calendarKeysDao().select(calendarId).filter {
                it.isActive
            }.map {
                it.privateKey
            }.takeIfNotEmpty() ?: run {
                logger.i("FixCalendarsUseCase, calendarKey was null")
                calendarsToBootstrap.add(calendarEntity)
                return@forEach
            }

            // Check calendar passphrase
            val calendarPassphrase = database.passphrasesDao().select(calendarId).map {
                it.toPassphrase(json)
            }.firstOrNull {
                it.isActive
            } ?: run {
                logger.i("FixCalendarsUseCase, calendarPassphrase was null")
                calendarsToBootstrap.add(calendarEntity)
                return@forEach
            }

            // Check calendar key passphrase
            valueStoreProvider.provideValueStore(userId.id).getStringFromSet(
                ValueSet.CALENDAR_PASSPHRASE,
                calendarPassphrase.id
            ) ?: run {
                logger.i("FixCalendarsUseCase, keyPassphrase was null")
                calendarsToBootstrap.add(calendarEntity)
                return@forEach
            }
        }

        // Retry bootstrap for calendars with missing data
        if (calendarsToBootstrap.isNotEmpty()) {
            coroutineScope {
                calendarsToBootstrap.map {
                    async {
                        when (val bootstrapResult = bootstrapCalendarUseCase.executeBootstrap(it, userId, userAddresses)) {
                            is UseCase.Result.Success<*> -> logger.i("FixCalendarsUseCase successfully fixed calendar")
                            is UseCase.Result.InvalidParams -> logger.i("FixCalendarsUseCase failed to fix calendar ${bootstrapResult.message}")
                            is UseCase.Result.Error -> logger.i("FixCalendarsUseCase failed to fix calendar ${bootstrapResult.message}")
                        }
                    }
                }.awaitAll()
            }
        }
        return UseCase.Result.Success<Unit>()
    }

}
