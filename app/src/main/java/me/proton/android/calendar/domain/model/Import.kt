package me.proton.android.calendar.domain.model

import java.time.LocalDateTime

data class Import(
    val id: String,
    val account: String,
    val size: Int?,
    val dateTime: LocalDateTime?,
    val state: ImportState?,
    val errorCode: Int? = null // 1: Lost connection, 2: Storage limit reached
) {
    enum class ImportState(val value: Int) {
        QUEUED(0),
        RUNNING(1),
        DONE(2),
        FAILED(3),
        PAUSED(4),
        CANCELED(5),
        CANCELING(6)
    }

    enum class ErrorCode(val value: Int) {
        LOST_CONNECTION(1),
        STORAGE_LIMIT_REACHED(2)
    }

    val lostConnection: Boolean get() = state == ImportState.PAUSED && errorCode == ErrorCode.LOST_CONNECTION.value
    val storageFull: Boolean get() = state == ImportState.PAUSED && errorCode == ErrorCode.STORAGE_LIMIT_REACHED.value
}
