package me.proton.android.calendar.init

import android.content.Context
import androidx.startup.Initializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.proton.android.calendar.eventmanager.CalendarEventManagerStarter

class CalendarEventManagerInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            CalendarEventManagerInitializerEntryPoint::class.java
        )
        val starter = entryPoint.starter()
        starter.start()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(WorkManagerInitializer::class.java)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CalendarEventManagerInitializerEntryPoint {
        fun starter(): CalendarEventManagerStarter
    }
}
