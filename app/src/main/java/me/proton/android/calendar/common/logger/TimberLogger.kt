package me.proton.android.calendar.common.logger

import me.proton.android.calendar.domain.Logger
import timber.log.Timber

object TimberLogger : Logger {
    override fun v(message: String) = Timber.v(message)
    override fun v(message: String, throwable: Throwable) = Timber.v(throwable, message)
    override fun d(message: String) = Timber.d(message)
    override fun d(message: String, throwable: Throwable) = Timber.d(throwable, message)
    override fun i(message: String) = Timber.i(message)
    override fun i(message: String, throwable: Throwable) = Timber.i(throwable, message)
    override fun e(message: String) = Timber.e(message)
    override fun e(message: String, throwable: Throwable) = Timber.e(throwable, message)
}
