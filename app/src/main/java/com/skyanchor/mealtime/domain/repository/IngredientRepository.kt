package com.skyanchor.mealtime.domain.repository

import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientTypeInfo
import kotlinx.coroutines.flow.Flow

interface IngredientRepository {

    /** @param type 食材种类键（IngredientTypes 或自定义），null 表示全部 */
    fun observeIngredients(
        type: String? = null,
        query: String? = null,
    ): Flow<List<Ingredient>>

    suspend fun getIngredient(id: Long): Ingredient?

    suspend fun getIngredientByName(name: String): Ingredient?

    /** 选择或创建：存在同名有效食材则直接返回，否则创建（录入菜谱/库存的快捷路径）；
     *  传入 imageUri 时，若已有食材还没图或图不同，则更新字典图片 */
    suspend fun getOrCreate(
        name: String,
        type: String,
        defaultUnit: String? = null,
        imageUri: String? = null,
    ): Ingredient

    /** 仅更新食材图片（null 表示移除） */
    suspend fun updateImage(id: Long, imageUri: String?)

    suspend fun softDelete(id: Long)

    /** 可配置的食材种类列表（设置页增删，列表/编辑页展示） */
    fun observeTypes(): Flow<List<IngredientTypeInfo>>

    /** 新增自定义食材种类（键与展示名相同；重名或空名抛 IllegalArgumentException） */
    suspend fun addType(label: String): IngredientTypeInfo

    /**
     * 删除食材种类：该种类下的食材与菜谱配料行自动归入「其他」（不存在时自动创建）。
     * 删除「其他」本身抛 IllegalArgumentException。
     */
    suspend fun deleteType(key: String)
}
