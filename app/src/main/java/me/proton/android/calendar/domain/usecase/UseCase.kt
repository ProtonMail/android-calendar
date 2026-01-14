package me.proton.android.calendar.domain.usecase

import me.proton.android.calendar.domain.Logger

interface UseCase {
    sealed class Result {
        class Success<T>(val returnValue: T? = null) : Result()
        class InvalidParams(val message: String, val userErrorMessage: String? = null) : Result()
        class Error(val message: String, val error: UseCase.Error? = null, val userErrorMessage: String? = null) : Result()
    }

    sealed class Error {
        sealed class Bootstrap: Error() {
            object NoCalendar : Bootstrap()
            object NoActiveCalendar : Bootstrap()
            class SomeCalendarsFailedBootstrap(val failedCalendarIds: List<String>): Bootstrap()
            object ResetNeeded : Bootstrap()
            object UpdatePassphrase : Bootstrap()
        }

        sealed class HandleSave: Error() {
            object EditSendEmail : HandleSave()
            object CreateSendEmail : HandleSave()
        }

        sealed class Crypto: Error() {
            object UserAddressInvalidForEncryption : Crypto()
        }

        sealed class Sync: Error() {
            object LostZoomAccess : HandleSave()
            object ZoomLinkDoesNotExist : HandleSave()
        }
    }
}

suspend fun UseCase.Result.ifSuccessAndLogErrors(logger: Logger, onSuccess: suspend () -> Unit) {
    when (this) {
        is UseCase.Result.Success<*> -> onSuccess.invoke()
        is UseCase.Result.InvalidParams -> logger.i("UseCase InvalidParams: ${this.message}")
        is UseCase.Result.Error -> logger.i("UseCase Error: ${this.message}")
    }
}

suspend fun UseCase.Result.logErrors(logger: Logger) = ifSuccessAndLogErrors(logger) {}
