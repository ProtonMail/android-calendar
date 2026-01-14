package me.proton.android.calendar.init

import android.content.Context
import androidx.startup.Initializer
import androidx.work.WorkManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.common.*
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.domain.CalendarsRepository
import me.proton.android.calendar.domain.Crypto
import me.proton.android.calendar.domain.EventDecryptor
import me.proton.android.calendar.domain.Logger
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.contact.domain.repository.ContactRepository
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.mailmessage.domain.usecase.GetRecipientPublicAddresses
import me.proton.core.network.data.ApiProvider
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import me.proton.core.usersettings.domain.repository.UserSettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KoinInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            KoinInitializerEntryPoint::class.java
        )
        startKoin {
            androidContext(context)
            modules(
                commonModule,
                repositoryModule,
                networkModule,
                useCaseModule,
                widgetModule,
                coreModule(
                    entryPoint.appDatabase(),
                    entryPoint.apiProvider(),
                    entryPoint.crypto(),
                    entryPoint.cryptoContext(),
                    entryPoint.eventDecryptor(),
                    entryPoint.accountManager(),
                    entryPoint.userManager(),
                    entryPoint.userAddressManager(),
                    entryPoint.userRepository(),
                    entryPoint.userAddressRepository(),
                    entryPoint.calendarsRepository(),
                    entryPoint.contactEmailsRepository(),
                    entryPoint.userSettingsRepository(),
                    entryPoint.getRecipientPublicAddresses(),
                    entryPoint.defaultSharedPreferencesProvider()
                )
            )
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>?>> = emptyList()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface KoinInitializerEntryPoint {
        fun logger(): Logger
        fun workManager(): WorkManager
        fun appDatabase(): AppDatabase
        fun apiProvider(): ApiProvider
        fun crypto(): Crypto
        fun cryptoContext(): CryptoContext
        fun eventDecryptor(): EventDecryptor
        fun accountManager(): AccountManager
        fun userManager(): UserManager
        fun userAddressManager(): UserAddressManager
        fun userRepository(): UserRepository
        fun userAddressRepository(): UserAddressRepository
        fun calendarsRepository(): CalendarsRepository
        fun contactEmailsRepository(): ContactRepository
        fun userSettingsRepository(): UserSettingsRepository
        fun defaultSharedPreferencesProvider(): DefaultSharedPreferencesProvider
        fun getRecipientPublicAddresses(): GetRecipientPublicAddresses
    }
}