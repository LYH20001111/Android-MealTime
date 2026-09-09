package com.skyanchor.mealtime.domain.repository

import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeDetail
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.core.model.Tag
import kotlinx.coroutines.flow.Flow

interface RecipeRepository {

    fun observeRecipes(
        query: String? = null,
        categoryId: Long? = null,
        favoritesOnly: Boolean = false,
    ): Flow<List<Recipe>>

    fun observeRecipe(id: Long): Flow<Recipe?>

    suspend fun getRecipe(id: Long): Recipe?

    /** 按名称查有效（未归档）菜谱，录入页重名提示用 */
    suspend fun getRecipeByName(name: String): Recipe?

    suspend fun getRecipeDetail(id: Long): RecipeDetail?

    /** 详情页实时流：菜谱、配料、标签任一变化都会重新发射 */
    fun observeRecipeDetail(id: Long): Flow<RecipeDetail?>

    /**
     * 新增或更新菜谱：菜谱主记录、配料行、标签在同一个事务中保存。
     * 配料行只存名称与种类，与食材字典解耦（R：不校验重名、不写字典）。
     */
    suspend fun saveRecipe(
        recipe: Recipe,
        ingredients: List<RecipeIngredientLine>,
        tagIds: List<Long>,
    ): Long

    /** 删除用归档实现，历史用餐记录不受影响（R07） */
    suspend fun archiveRecipe(id: Long)

    suspend fun setFavorite(id: Long, favorite: Boolean)

    fun observeCategories(): Flow<List<Category>>

    /** 新增菜谱分类（重名或空名抛 IllegalArgumentException） */
    suspend fun addCategory(name: String): Category

    /**
     * 删除菜谱分类，其中已有的菜谱自动归入「其他」（不存在时自动创建）。
     * 删除「其他」本身抛 IllegalArgumentException。
     */
    suspend fun deleteCategory(id: Long)

    /** 上移/下移分类；已处于边界时静默忽略 */
    suspend fun moveCategory(id: Long, up: Boolean)

    /**
     * 重命名菜谱分类：菜谱按 categoryId 关联，重命名后自动跟随新名称。
     * 空名、与其他分类重名抛 IllegalArgumentException；「其他」不可重命名。
     */
    suspend fun renameCategory(id: Long, newName: String): Category

    fun observeTags(): Flow<List<Tag>>

    /** 选择或创建标签：存在同名标签则直接返回 */
    suspend fun getOrCreateTag(name: String): Tag

    /** 删除系统标签，同时解除所有菜谱与该标签的关联 */
    suspend fun deleteTag(id: Long)
}
