package me.proton.android.calendar.domain

interface Logger {
    fun v(message: String)
    fun v(message: String, throwable: Throwable)
    fun d(message: String)
    fun d(message: String, throwable: Throwable)
    fun i(message: String)
    fun i(message: String, throwable: Throwable)
    fun e(message: String)
    fun e(message: String, throwable: Throwable)
}