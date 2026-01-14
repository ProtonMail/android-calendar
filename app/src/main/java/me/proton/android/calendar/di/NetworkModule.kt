package me.proton.android.calendar.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.data.api.CalendarApiClient
import me.proton.core.configuration.EnvironmentConfiguration
import me.proton.core.network.data.client.ExtraHeaderProviderImpl
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.Constants
import me.proton.core.network.data.di.DohProviderUrls
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.util.kotlin.takeIfNotBlank
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @BaseProtonApiUrl
    fun provideProtonApiUrl(envConfiguration: EnvironmentConfiguration): HttpUrl = envConfiguration.baseUrl.toHttpUrl()

    @DohProviderUrls
    @Provides
    fun provideDohProviderUrls(): Array<String> = Constants.DOH_PROVIDERS_URLS

    @CertificatePins
    @Provides
    fun provideCertificatePins(envConfiguration: EnvironmentConfiguration) = if (envConfiguration.useDefaultPins) {
        Constants.DEFAULT_SPKI_PINS
    } else {
        emptyArray()
    }

    @AlternativeApiPins
    @Provides
    fun provideAlternativeApiPins(envConfiguration: EnvironmentConfiguration) = if (envConfiguration.useDefaultPins) {
        Constants.ALTERNATIVE_API_SPKI_PINS
    } else {
        emptyList()
    }

    @Provides
    @Singleton
    fun provideDohAlternativesListener(): DohAlternativesListener? = null

    @Provides
    @Singleton
    fun provideExtraHeaderProvider(envConfiguration: EnvironmentConfiguration): ExtraHeaderProvider =
        ExtraHeaderProviderImpl().apply {
            envConfiguration.proxyToken?.takeIfNotBlank()
                ?.let { addHeaders("X-atlas-secret" to it) }
        }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {
    @Binds
    abstract fun provideApiClient(calendarApiClient: CalendarApiClient): ApiClient
}
