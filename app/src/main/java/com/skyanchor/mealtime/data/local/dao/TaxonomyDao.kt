package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.skyanchor.mealtime.data.local.entity.AppSettingEntity
import com.skyanchor.mealtime.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM category")
    suspend fun exportAll(): List<CategoryEntity>
}

@Dao
interface AppSettingDao {

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    @Query("DELETE FROM app_setting WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM app_setting")
    suspend fun exportAll(): List<AppSettingEntity>
}
