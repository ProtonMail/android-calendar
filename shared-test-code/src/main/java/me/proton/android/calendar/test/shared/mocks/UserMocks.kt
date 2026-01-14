package me.proton.android.calendar.test.shared.mocks

import me.proton.android.calendar.data.entity.UserSettingsEntity
import me.proton.android.calendar.domain.model.SendPreferences
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.entity.UserAddress
import me.proton.core.usersettings.domain.entity.PasswordSetting
import me.proton.core.usersettings.domain.entity.UserSettings

object UserMocks {

    fun provideUserSettingsEntity(): UserSettingsEntity {
        return UserSettingsEntity(
            fkUserId = userId.id,
            weekStart = weekStart, // 0: Locale default, 1: Monday, 6: Saturday 7: Sunday
            dateFormat = dateFormat, // 0: Locale default, 1: DD_MM_YYYY, 2: MM_DD_YYYY, 3: YYYY_MM_DD
            timeFormat = timeFormat // 0: Locale default, 1: 24H, 2: 12H
        )
    }

    fun provideUserSettings(): UserSettings {
        return UserSettings(
            userId = userId,
            email = null,
            phone = null,
            password = PasswordSetting(
                null,
                null
            ),
            twoFA = null,
            news = null,
            locale = null,
            logAuth = null,
            density = null,
            weekStart = UserSettings.WeekStart.enumOf(weekStart),
            dateFormat = UserSettings.DateFormat.enumOf(dateFormat),
            timeFormat = UserSettings.TimeFormat.enumOf(timeFormat),
            earlyAccess = null,
            deviceRecovery = null,
            telemetry = null,
            crashReports = null,
            sessionAccountRecovery = null,
            easyDeviceMigrationOptOut = null
        )
    }

    fun provideUser(): User {
        return User(
            userId = userId,
            email = userEmail,
            name = userName,
            displayName = userDisplayName,
            currency = currency,
            type = userType,
            credit = credit,
            createdAtUtc = createdAtUtc,
            usedSpace = usedSpace,
            maxSpace = maxSpace,
            maxUpload = maxUpload,
            role = role,
            private = private,
            services = services,
            subscribed = subscribed,
            delinquent = delinquent,
            recovery = null,
            keys = emptyList(),
            flags = emptyMap()
        )
    }

    fun provideUserAddress(canSendParam: Boolean? = null, canReceiveParam: Boolean? = null, enabledParam: Boolean? = null): UserAddress {
        return UserAddress(
            userId = userId,
            addressId = addressId,
            email = userEmail,
            displayName = userDisplayName,
            signature = null, // TODO
            domainId = null, // TODO
            canSend = canSendParam ?: canSend,
            canReceive = canReceiveParam ?: canReceive,
            enabled = enabledParam ?: enabled,
            type = addressType,
            order = order,
            keys = emptyList(), // TODO
            signedKeyList = null // TODO
        )
    }

    fun provideAddressEntity(canSendParam: Boolean? = null, canReceiveParam: Boolean? = null, enabledParam: Boolean? = null): AddressEntity {
        return AddressEntity(
            userId = userId,
            addressId = addressId,
            email = userEmail,
            displayName = userDisplayName,
            signature = null, // TODO
            domainId = null, // TODO
            canSend = canSendParam ?: canSend,
            canReceive = canReceiveParam ?: canReceive,
            enabled = enabledParam ?: enabled,
            type = addressType.value,
            order = order,
            signedKeyList = null // TODO
        )
    }

    fun provideSendPreferences(): SendPreferences {
        return SendPreferences(
            encrypt = encrypt,
            sign = sign,
            pgpScheme = pgpScheme,
            mimeType = mimeType,
            publicKey = publicKey
        )
    }
}
