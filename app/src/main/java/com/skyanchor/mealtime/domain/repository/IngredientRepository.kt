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

    /**
     * 查"已在库存中"的同名食材（字典存在且至少有一个有效库存批次）。
     * 库存新增查重用：菜谱创建的无库存字典条目不算重名，可复用建库存。
     */
    suspend fun getStockedIngredientByName(name: String): Ingredient?

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

    /** 更新食材种类；菜谱配料行的冗余种类一并同步，保持展示一致 */
    suspend fun updateType(id: Long, type: String)

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

    /** 上移/下移食材种类；已处于边界时静默忽略 */
    suspend fun moveType(key: String, up: Boolean)

    /**
     * 重命名食材种类展示名：键不变，食材与菜谱配料行按 key 关联，重命名后自动跟随新展示名。
     * 空名、与其他种类展示名重名抛 IllegalArgumentException；「其他」不可重命名。
     */
    suspend fun renameType(key: String, newLabel: String): IngredientTypeInfo
}
