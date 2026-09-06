package com.skyanchor.mealtime.data.repository

import com.skyanchor.mealtime.data.local.dao.AppSettingDao
import com.skyanchor.mealtime.data.local.entity.AppSettingEntity
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(private val dao: AppSettingDao) : SettingsRepository {

    override fun observe(key: String): Flow<String?> = dao.observe(key)

    override suspend fun getString(key: String): String? = dao.get(key)

    /** 数值型设置：读取时给出默认值 */
    suspend fun getInt(key: String, default: Int): Int =
        dao.get(key)?.toIntOrNull() ?: default

    override suspend fun putString(key: String, value: String) {
        dao.put(AppSettingEntity(key = key, value = value))
    }

    override suspend fun remove(key: String) {
        dao.delete(key)
    }
}
