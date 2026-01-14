package me.proton.android.calendar.domain

interface ResourceProvider {
    fun provideString(resId: Int, vararg formatArgs: Any?): String
    fun provideColor(resId: Int): Int
}
