package me.proton.android.calendar.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import me.proton.android.calendar.CalendarWidgetRefresher
import me.proton.android.calendar.WidgetRefresher
import me.proton.android.calendar.common.CryptoImpl
import me.proton.android.calendar.common.provider.ValueStoreProviderImpl
import me.proton.android.calendar.common.utils.ICalUtilsImpl
import me.proton.android.calendar.data.CalendarsRepositoryImpl
import me.proton.android.calendar.data.EventDecryptorImpl
import me.proton.android.calendar.data.api.*
import me.proton.android.calendar.data.db.AppDatabase
import me.proton.android.calendar.data.db.SearchDatabase
import me.proton.android.calendar.domain.*
import me.proton.android.calendar.domain.api.*
import me.proton.android.calendar.domain.usecase.IndexEventForSearchUseCase
import me.proton.android.calendar.domain.usecase.TransformEventUseCase
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CalendarsModule {

    @Provides
    fun provideKotlinxJson() = Json { ignoreUnknownKeys = true }

    @Provides
    fun provideICalUtilsImpl() = ICalUtilsImpl

    @Provides
    @Singleton
    fun provideSearchDatabase(
        @ApplicationContext context: Context,
        valueStoreProvider: ValueStoreProvider,
        keyStoreCrypto: KeyStoreCrypto
    ): SearchDatabase = SearchDatabase.buildDatabase(context, valueStoreProvider, keyStoreCrypto)

    @Provides
    fun provideIndexEventForSearchUseCase(
        valueStoreProvider: ValueStoreProvider,
        searchDatabase: SearchDatabase,
        transformEventUseCase: TransformEventUseCase
    ): IndexEventForSearchUseCase = IndexEventForSearchUseCase(valueStoreProvider, searchDatabase, transformEventUseCase)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarsBindModule {

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(calendarsRepositoryImpl: CalendarsRepositoryImpl): CalendarsRepository

    @Binds
    @Singleton
    abstract fun bindCalendarApi(calendarsApiImpl: CalendarsApiImpl): CalendarsApi

    @Binds
    @Singleton
    abstract fun bindAddressesApi(addressesApiImpl: AddressesApiImpl): AddressesApi

    @Binds
    @Singleton
    abstract fun bindMailSettingsApi(mailSettingsApiImpl: MailSettingsApiImpl): MailSettingsApi

    @Binds
    @Singleton
    abstract fun bindBugReportsApi(bugReportsApi: BugReportsApiImpl): BugReportsApi

    @Binds
    @Singleton
    abstract fun bindSettingsApi(settingsApi: SettingsApiImpl): SettingsApi

    @Binds
    @Singleton
    abstract fun bindFeedbackApi(feedbackApi: FeedbackApiImpl): FeedbackApi

    @Binds
    @Singleton
    abstract fun bindImporterApi(importerApi: ImporterApiImpl): ImporterApi

    @Binds
    @Singleton
    abstract fun bindTestsApi(testsApi: TestsApiImpl): TestsApi

    @Binds
    @Singleton
    abstract fun bindServerEventsApi(serverEventsApiImpl: ServerEventsApiImpl): ServerEventsApi

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(widgetRefresher: CalendarWidgetRefresher): WidgetRefresher

    @Binds
    abstract fun bindValueStoreProvider(valueStoreProviderImpl: ValueStoreProviderImpl): ValueStoreProvider

    @Binds
    @Singleton
    abstract fun bindCrypto(cryptoImpl: CryptoImpl): Crypto

    @Binds
    @Singleton
    abstract fun bindEventDecryptor(eventDecryptorImpl: EventDecryptorImpl): EventDecryptor

}
