package com.skyanchor.mealtime.domain.repository

import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientType
import kotlinx.coroutines.flow.Flow

interface IngredientRepository {

    fun observeIngredients(
        type: IngredientType? = null,
        query: String? = null,
    ): Flow<List<Ingredient>>

    suspend fun getIngredient(id: Long): Ingredient?

    suspend fun getIngredientByName(name: String): Ingredient?

    /** 选择或创建：存在同名有效食材则直接返回，否则创建（录入菜谱/库存的快捷路径）；
     *  传入 imageUri 时，若已有食材还没图或图不同，则更新字典图片 */
    suspend fun getOrCreate(
        name: String,
        type: IngredientType,
        defaultUnit: String? = null,
        imageUri: String? = null,
    ): Ingredient

    /** 仅更新食材图片（null 表示移除） */
    suspend fun updateImage(id: Long, imageUri: String?)

    suspend fun softDelete(id: Long)
}
