package me.proton.android.calendar.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import me.proton.android.calendar.common.utils.CalendarFeatureFlag
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarAlarmEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarEventListenerNew
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarKeyEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarPassphraseEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarSettingsEventListener
import me.proton.android.calendar.eventmanager.listeners.calendar.CalendarSubscriptionsEventListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarMemberEventListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarUserAddressListener
import me.proton.android.calendar.eventmanager.listeners.core.CalendarUserSettingsEventListener
import me.proton.core.contact.data.ContactEmailEventListener
import me.proton.core.contact.data.ContactEventListener
import me.proton.core.eventmanager.domain.EventListener
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.notification.data.NotificationEventListener
import me.proton.core.push.data.PushEventListener
import me.proton.core.user.data.UserEventListener
import me.proton.core.usersettings.data.UserSettingsEventListener
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventManagerModule {
    @OptIn(ExperimentalProtonFeatureFlag::class)
    @Provides
    @Singleton
    @ElementsIntoSet
    @JvmSuppressWildcards
    fun provideEventListenerSet(
        // Core listeners
        userEventListener: UserEventListener,
        userSettingsEventListener: UserSettingsEventListener,
        contactEventListener: ContactEventListener,
        contactEmailEventListener: ContactEmailEventListener,
        notificationEventListener: NotificationEventListener,
        pushEventListener: PushEventListener,
        // Custom Core listener
        userAddressEventListener: CalendarUserAddressListener,
        // Calendar only listeners in Core event loop
        calendarListener: CalendarListener,
        calendarMemberEventListener: CalendarMemberEventListener,
        calendarUserSettingsEventListener: CalendarUserSettingsEventListener,
        // Calendar only listeners in Calendar event loop
        calendarEventListener: CalendarEventListener,
        calendarEventListenerNew: CalendarEventListenerNew,
        calendarAlarmEventListener: CalendarAlarmEventListener,
        calendarPassphraseEventListener: CalendarPassphraseEventListener,
        calendarKeyEventListener: CalendarKeyEventListener,
        calendarSubscriptionsEventListener: CalendarSubscriptionsEventListener,
        calendarSettingsEventListener: CalendarSettingsEventListener,
        // Feature Flag Manager needed for dynamically switching listeners
        featureFlagManager: FeatureFlagManager
    ): Set<EventListener<*, *>> = setOf(
        userEventListener,
        userSettingsEventListener,
        contactEventListener,
        contactEmailEventListener,
        notificationEventListener,
        pushEventListener,
        userAddressEventListener,
        calendarListener,
        calendarMemberEventListener,
        calendarUserSettingsEventListener,
        if (featureFlagManager.getValue(null, CalendarFeatureFlag.NewCalendarEventListenerAndroid.featureId)) calendarEventListenerNew else calendarEventListener,
        calendarAlarmEventListener,
        calendarPassphraseEventListener,
        calendarKeyEventListener,
        calendarSubscriptionsEventListener,
        calendarSettingsEventListener,
    )

}
