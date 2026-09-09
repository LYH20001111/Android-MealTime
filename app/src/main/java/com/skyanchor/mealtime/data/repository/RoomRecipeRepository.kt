package com.skyanchor.mealtime.data.repository

import androidx.room.withTransaction
import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.FALLBACK_CATEGORY_NAME
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeDetail
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.core.model.Tag
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.CategoryEntity
import com.skyanchor.mealtime.data.local.entity.RecipeTagCrossRef
import com.skyanchor.mealtime.data.local.entity.TagEntity
import com.skyanchor.mealtime.data.mapper.toDomain
import com.skyanchor.mealtime.data.mapper.toEntity
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRecipeRepository(private val db: MealTimeDatabase) : RecipeRepository {

    private val recipeDao = db.recipeDao()
    private val tagDao = db.tagDao()

    override fun observeRecipes(
        query: String?,
        categoryId: Long?,
        favoritesOnly: Boolean,
    ): Flow<List<Recipe>> =
        recipeDao.observeRecipes(
            query = query?.takeIf { it.isNotBlank() },
            categoryId = categoryId,
            favoritesOnly = favoritesOnly,
        ).map { list -> list.map { it.toDomain() } }

    override fun observeRecipe(id: Long): Flow<Recipe?> =
        recipeDao.observeById(id).map { it?.toDomain() }

    override suspend fun getRecipe(id: Long): Recipe? = recipeDao.getById(id)?.toDomain()

    override suspend fun getRecipeByName(name: String): Recipe? =
        recipeDao.getByName(name.trim())?.toDomain()

    override suspend fun getRecipeDetail(id: Long): RecipeDetail? = db.withTransaction {
        val withIngredients = recipeDao.getWithIngredients(id) ?: return@withTransaction null
        val tags = recipeDao.getTagsForRecipe(id).map { it.toDomain() }
        withIngredients.toDomain(tags)
    }

    override fun observeRecipeDetail(id: Long): Flow<RecipeDetail?> =
        recipeDao.observeWithIngredients(id).map { withIngredients ->
            withIngredients ?: return@map null
            val tags = recipeDao.getTagsForRecipe(id).map { it.toDomain() }
            withIngredients.toDomain(tags)
        }

    override suspend fun saveRecipe(
        recipe: Recipe,
        ingredients: List<RecipeIngredientLine>,
        tagIds: List<Long>,
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val existing = if (recipe.id != 0L) recipeDao.getById(recipe.id) else null
        val recipeId = if (existing != null) {
            recipeDao.update(recipe.toEntity(createdAt = existing.createdAt, updatedAt = now))
            recipe.id
        } else {
            recipeDao.insert(recipe.toEntity(createdAt = now, updatedAt = now))
        }
        recipeDao.clearIngredients(recipeId)
        if (ingredients.isNotEmpty()) {
            recipeDao.insertIngredients(ingredients.map { it.toEntity(recipeId) })
        }
        recipeDao.clearTags(recipeId)
        if (tagIds.isNotEmpty()) {
            recipeDao.insertTagCrossRefs(tagIds.map { RecipeTagCrossRef(recipeId, it) })
        }
        recipeId
    }

    override suspend fun archiveRecipe(id: Long) {
        recipeDao.archive(id, System.currentTimeMillis())
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean) {
        recipeDao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    override suspend fun getRecipesUsingIngredients(ingredientIds: List<Long>): List<Recipe> {
        if (ingredientIds.isEmpty()) return emptyList()
        return recipeDao.getRecipesUsingIngredients(ingredientIds).map { it.toDomain() }
    }

    override fun observeCategories(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addCategory(name: String): Category {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "分类名不能为空" }
        return db.withTransaction {
            require(db.categoryDao().getByName(trimmed) == null) { "分类「$trimmed」已存在" }
            val sortOrder = (db.categoryDao().maxSortOrder() ?: -1) + 1
            val id = db.categoryDao().insert(CategoryEntity(name = trimmed, sortOrder = sortOrder))
            Category(id = id, name = trimmed, sortOrder = sortOrder)
        }
    }

    override suspend fun deleteCategory(id: Long) = db.withTransaction {
        val dao = db.categoryDao()
        val category = dao.getById(id) ?: return@withTransaction
        require(category.name != FALLBACK_CATEGORY_NAME) { "「${FALLBACK_CATEGORY_NAME}」分类不能删除" }
        // 兜底分类被误删过时自动补建
        val fallback = dao.getByName(FALLBACK_CATEGORY_NAME)
            ?: CategoryEntity(
                id = dao.insert(CategoryEntity(name = FALLBACK_CATEGORY_NAME, sortOrder = (dao.maxSortOrder() ?: -1) + 1)),
                name = FALLBACK_CATEGORY_NAME,
            )
        db.recipeDao().reassignCategory(fromCategoryId = id, toCategoryId = fallback.id)
        dao.delete(id)
    }

    override fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreateTag(name: String): Tag {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "标签名不能为空" }
        return db.withTransaction {
            tagDao.getByName(trimmed)?.toDomain() ?: run {
                val id = tagDao.insert(TagEntity(name = trimmed, sortOrder = 0))
                Tag(id = id, name = trimmed, sortOrder = 0)
            }
        }
    }
}
