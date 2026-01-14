package me.proton.android.calendar.common.logger

import me.proton.android.calendar.domain.Logger

object TestsLogger : Logger {
    override fun v(message: String) = println(message)
    override fun v(message: String, throwable: Throwable) = println(message + ": " + throwable.message)
    override fun d(message: String) = println(message)
    override fun d(message: String, throwable: Throwable) = println(message + ": " + throwable.message)
    override fun i(message: String) = println(message)
    override fun i(message: String, throwable: Throwable) = println(message + ": " + throwable.message)
    override fun e(message: String) = println(message)
    override fun e(message: String, throwable: Throwable) = println(message + ": " + throwable.message)
}
