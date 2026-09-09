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

    /** 库存新增查重：字典存在且至少有一个有效库存批次才算“已在库存中”；菜谱创建的无库存条目不挡新增 */
    @Query(
        """
        SELECT ingredient.* FROM ingredient
        JOIN inventory_item ON inventory_item.ingredientId = ingredient.id
        WHERE ingredient.name = :name
          AND ingredient.isDeleted = 0
          AND inventory_item.isDeleted = 0
        LIMIT 1
        """
    )
    suspend fun getStockedByName(name: String): IngredientEntity?

    @Query("SELECT * FROM ingredient WHERE name = :name AND isDeleted = 1 ORDER BY id LIMIT 1")
    suspend fun getDeletedByName(name: String): IngredientEntity?

    /** 复活软删条目：沿用原 id，历史库存关联自动恢复；种类按新输入覆盖，图片未传时保留旧图 */
    @Query(
        """
        UPDATE ingredient SET isDeleted = 0, type = :type,
            imageUri = COALESCE(:imageUri, imageUri)
        WHERE id = :id
        """
    )
    suspend fun revive(id: Long, type: String, imageUri: String?)

    @Insert
    suspend fun insert(ingredient: IngredientEntity): Long

    @Update
    suspend fun update(ingredient: IngredientEntity)

    @Query("UPDATE ingredient SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    /** 食材种类删除时，把该种类的食材移入兜底种类 */
    @Query("UPDATE ingredient SET type = :toType WHERE type = :fromType")
    suspend fun reassignType(fromType: String, toType: String)

    @Query("UPDATE ingredient SET imageUri = :imageUri WHERE id = :id")
    suspend fun updateImage(id: Long, imageUri: String?)

    @Query("UPDATE ingredient SET type = :type WHERE id = :id")
    suspend fun updateType(id: Long, type: String)

    @Query("SELECT * FROM ingredient")
    suspend fun exportAll(): List<IngredientEntity>
}
