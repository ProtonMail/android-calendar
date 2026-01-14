package me.proton.android.calendar.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.presentation.account.CalendarUserCheck
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.auth.presentation.DefaultHelpOptionHandler
import me.proton.core.auth.presentation.HelpOptionHandler
import me.proton.core.auth.presentation.ui.LoginActivity
import me.proton.core.user.domain.UserManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideUserCheck(
        @ApplicationContext context: Context,
        accountManager: AccountManager,
        userManager: UserManager
    ): PostLoginAccountSetup.UserCheck = CalendarUserCheck(context, accountManager, userManager)

    @Provides
    @Singleton
    fun provideHelpOptionHandler(): HelpOptionHandler = DefaultHelpOptionHandler()
}
