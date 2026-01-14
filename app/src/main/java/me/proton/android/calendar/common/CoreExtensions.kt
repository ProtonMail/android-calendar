package me.proton.android.calendar.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.Logger
import me.proton.core.domain.arch.DataResult
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.domain.entity.UserId
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.entity.User
import me.proton.core.usersettings.domain.repository.UserSettingsRepository

suspend fun UserManager.getUserOrNull(
    sessionUserId: SessionUserId,
    logger: Logger? = null,
    refresh: Boolean = false
): User? {
    return kotlin.runCatching {
        this.getUser(sessionUserId, refresh)
    }.getOrElse {
        logger?.e("Exception in UserManager.getUserOrNull: ${it.message}", it)
        null
    }
}

suspend fun UserSettingsRepository.getTimeFormatFlow(userId: UserId, database: AppDatabase): Flow<Int> {
    return this.getUserSettingsEntityFlow(userId, database).map { it.timeFormat }.distinctUntilChanged()
}

suspend fun UserSettingsRepository.getTimeFormat(userId: UserId, database: AppDatabase): Int {
    return this.getUserSettingsEntity(userId, database).timeFormat
}

suspend fun UserSettingsRepository.getWeekStartFlow(userId: UserId, database: AppDatabase): Flow<Int> {
    return this.getUserSettingsEntityFlow(userId, database).map { it.weekStart }.distinctUntilChanged()
}

suspend fun UserSettingsRepository.getWeekStart(userId: UserId, database: AppDatabase): Int {
    return this.getUserSettingsEntity(userId, database).weekStart
}

suspend fun UserSettingsRepository.getUserSettingsEntityFlow(
    userId: UserId,
    database: AppDatabase
): Flow<UserSettingsEntity> {

    val legacyUserSettings = database.deprecatedUserSettingsDao().select(userId.id)
    val coreUserSettings = database.userSettingsDao().getByUserId(userId)

    return getUserSettingsFlow(userId).transform { userSettingsResult ->
        if (userSettingsResult is DataResult.Success) {
            emit(
                UserSettingsEntity(
                    fkUserId = userId.id,
                    weekStart = userSettingsResult.value.weekStart?.value ?: 0,
                    dateFormat = userSettingsResult.value.dateFormat?.value ?: 0,
                    timeFormat = userSettingsResult.value.timeFormat?.value ?: 0
                )
            )
        }
    }.onStart {
        // the Core flow will not emit until the data is pulled from server
        // so we need to fake the UserSettings for the time being
        //
        // we will use legacy settings if they are available in the DB (should be)
        if (coreUserSettings == null) {
            emit(
                UserSettingsEntity(
                    fkUserId = userId.id,
                    weekStart = legacyUserSettings?.weekStart ?: 0,
                    dateFormat = legacyUserSettings?.dateFormat ?: 0,
                    timeFormat = legacyUserSettings?.timeFormat ?: 0
                )
            )
        }
    }
}

suspend fun UserSettingsRepository.getUserSettingsEntity(userId: UserId, database: AppDatabase): UserSettingsEntity {
    return this.getUserSettingsEntityFlow(userId, database).first()
}
