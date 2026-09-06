package com.skyanchor.mealtime.core.common

/**
 * 全 App 统一页面状态模型（见设计文档 ARCHITECTURE.md §5）。
 * 编辑类页面在此基础上另设独立 FormState。
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
