package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {

    @Query(
        """
        SELECT * FROM ingredient
        WHERE isDeleted = 0
          AND (:type IS NULL OR type = :type)
          AND (:query IS NULL OR name LIKE '%' || :query || '%')
        ORDER BY name
        """
    )
    fun observeIngredients(type: String?, query: String?): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM ingredient WHERE id = :id")
    suspend fun getById(id: Long): IngredientEntity?

    @Query("SELECT * FROM ingredient WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getByName(name: String): IngredientEntity?

    @Insert
    suspend fun insert(ingredient: IngredientEntity): Long

    @Update
    suspend fun update(ingredient: IngredientEntity)

    @Query("UPDATE ingredient SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    @Query("UPDATE ingredient SET imageUri = :imageUri WHERE id = :id")
    suspend fun updateImage(id: Long, imageUri: String?)

    @Query("SELECT * FROM ingredient")
    suspend fun exportAll(): List<IngredientEntity>
}
