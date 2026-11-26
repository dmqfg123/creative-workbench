package com.creativeworkbench.writer.core.common

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Empty(val hint: String) : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val message: String, val retry: (() -> Unit)? = null) : UiState<Nothing>
}
