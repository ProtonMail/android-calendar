package me.proton.android.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.data.api.EmailMessageRepositoryImpl
import me.proton.android.calendar.domain.api.EmailMessageRepository
import me.proton.core.network.data.ApiProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MailModule {

    @Provides
    @Singleton
    fun provideEmailMessageRepositoryImpl(
        provider: ApiProvider
    ): EmailMessageRepository = EmailMessageRepositoryImpl(provider)
}
