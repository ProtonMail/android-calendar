package me.proton.android.calendar.di

import android.content.Context
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.common.logger.TimberLogger
import me.proton.android.calendar.common.provider.DefaultSharedPreferencesProvider
import me.proton.android.calendar.common.provider.ResourceProviderImpl
import me.proton.android.calendar.domain.Logger
import me.proton.android.calendar.domain.ResourceProvider
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.compose.theme.AppTheme
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.domain.entity.AppStore
import me.proton.core.domain.entity.Product
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {

    @Provides
    @Singleton
    fun provideProduct(): Product = Product.Calendar

    @Provides
    @Singleton
    fun provideAppStore() = AppStore.GooglePlay

    @Provides
    @Singleton
    fun provideReviewManager(
        @ApplicationContext context: Context
    ): ReviewManager = ReviewManagerFactory.create(context)

    @Provides
    @Singleton
    fun provideRequiredAccountType(): AccountType = AccountType.Internal

    @Provides
    @Singleton
    fun provideLogger(): Logger = TimberLogger

    @Provides
    @Singleton
    fun provideDefaultSharedPreferencesProvider(
        @ApplicationContext context: Context
    ): DefaultSharedPreferencesProvider = DefaultSharedPreferencesProvider(context)

    @Provides
    fun provideResourceProvider(@ApplicationContext context: Context): ResourceProvider =
        ResourceProviderImpl(context.resources)

    @Provides
    fun provideAppTheme(): AppTheme = AppTheme { content -> ProtonTheme { content() } }
}
