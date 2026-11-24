package com.creativeworkbench.writer.core.common

import java.io.IOException

sealed interface AppError {
    data class Storage(val detail: String, val canExport: Boolean = true) : AppError
    data class SaveConflict(val chapterId: String) : AppError
    data class Network(val retryable: Boolean, val detail: String) : AppError
    data class ApiKeyInvalid(val hint: String) : AppError
    data class Unknown(val detail: String) : AppError
}

fun Throwable.toAppError(): AppError = when (this) {
    is IOException -> AppError.Storage(message ?: "存储读写失败")
    else -> AppError.Unknown(message ?: this::class.java.simpleName)
}
