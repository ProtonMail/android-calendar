package me.proton.android.calendar.data.api

import me.proton.core.network.domain.ApiResult

fun <T : Any> ApiResult<T>.toApiResponse(): ApiResponse<T> = when (this) {
    is ApiResult.Success -> ApiResponse.Success(this.value)
    is ApiResult.Error.Http -> {
        ApiResponse.Error(
            error = proton?.error ?: message,
            errorCode = proton?.code ?: 0,
            httpCode = httpCode,
            shouldLog = true
        )
    }
    is ApiResult.Error.Parse -> {
        ApiResponse.Error(
            error = cause?.message ?: "no message in ApiResult.Error.Parse",
            errorCode = 0,
            httpCode = 0,
            shouldLog = true
        )
    }
    is ApiResult.Error.Connection -> {
        ApiResponse.Error(
            error = cause?.message ?: "no message in ApiResult.Error.Connection",
            errorCode = 0,
            httpCode = 0,
            shouldLog = false
        )
    }
}
