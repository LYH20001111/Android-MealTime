package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.skyanchor.mealtime.data.local.entity.AppSettingEntity
import com.skyanchor.mealtime.data.local.entity.CategoryEntity
import com.skyanchor.mealtime.data.local.entity.IngredientTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category ORDER BY sortOrder, id")
    suspend fun getAllSorted(): List<CategoryEntity>

    @Query("UPDATE category SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT * FROM category WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT MAX(sortOrder) FROM category")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM category")
    suspend fun exportAll(): List<CategoryEntity>
}

@Dao
interface IngredientTypeDao {

    @Query("SELECT * FROM ingredient_type ORDER BY sortOrder, `key`")
    fun observeAll(): Flow<List<IngredientTypeEntity>>

    @Query("SELECT * FROM ingredient_type WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): IngredientTypeEntity?

    @Query("SELECT * FROM ingredient_type ORDER BY sortOrder, `key`")
    suspend fun getAllSorted(): List<IngredientTypeEntity>

    @Query("UPDATE ingredient_type SET sortOrder = :sortOrder WHERE `key` = :key")
    suspend fun updateSortOrder(key: String, sortOrder: Int)

    @Query("SELECT * FROM ingredient_type WHERE label = :label LIMIT 1")
    suspend fun getByLabel(label: String): IngredientTypeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(type: IngredientTypeEntity)

    @Query("DELETE FROM ingredient_type WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT MAX(sortOrder) FROM ingredient_type")
    suspend fun maxSortOrder(): Int?

    @Query("SELECT * FROM ingredient_type")
    suspend fun exportAll(): List<IngredientTypeEntity>
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