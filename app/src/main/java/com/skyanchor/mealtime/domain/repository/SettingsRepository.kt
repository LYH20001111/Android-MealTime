package com.skyanchor.mealtime.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    fun observe(key: String): Flow<String?>

    suspend fun getString(key: String): String?

    suspend fun putString(key: String, value: String)

    suspend fun remove(key: String)
}

/** 本地设置键与默认值（Phase 7 设置页读写，Phase 3 临期阈值使用） */
object SettingsKeys {
    const val NOTIFICATION_ENABLED = "notification_enabled"
    const val NOTIFICATION_ADVANCE_DAYS = "notification_advance_days"
    const val EXPIRY_URGENT_DAYS = "expiry_urgent_days"
    const val EXPIRY_NEAR_DAYS = "expiry_near_days"
    const val DEFAULT_SERVINGS = "default_servings"
    const val LAST_NOTIFIED_DATE = "last_notified_date"

    const val DEFAULT_NOTIFICATION_ADVANCE_DAYS = 3
    const val DEFAULT_EXPIRY_URGENT_DAYS = 1
    const val DEFAULT_EXPIRY_NEAR_DAYS = 3
    const val DEFAULT_SERVINGS_VALUE = 1
}
