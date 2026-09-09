package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.local.entity.RecipeTagCrossRef
import com.skyanchor.mealtime.data.local.entity.TagEntity
import com.skyanchor.mealtime.data.local.relation.RecipeWithIngredients
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query(
        """
        SELECT * FROM recipe
        WHERE isArchived = 0
          AND (:query IS NULL
              OR name LIKE '%' || :query || '%'
              OR id IN (
                  SELECT recipeId FROM recipe_ingredient
                  WHERE ingredientName LIKE '%' || :query || '%'
              ))
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:favoritesOnly = 0 OR isFavorite = 1)
        ORDER BY updatedAt DESC
        """
    )
    fun observeRecipes(query: String?, categoryId: Long?, favoritesOnly: Boolean): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getById(id: Long): RecipeEntity?

    /** 按名称查有效（未归档）菜谱；新增/编辑时重名校验用 */
    @Query("SELECT * FROM recipe WHERE name = :name AND isArchived = 0 LIMIT 1")
    suspend fun getByName(name: String): RecipeEntity?

    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observeById(id: Long): Flow<RecipeEntity?>

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun getWithIngredients(id: Long): RecipeWithIngredients?

    @Transaction
    @Query("SELECT * FROM recipe WHERE id = :id")
    fun observeWithIngredients(id: Long): Flow<RecipeWithIngredients?>

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("UPDATE recipe SET isArchived = 1, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)

    @Query("UPDATE recipe SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    /** 分类删除时，把该分类下的菜谱移入兜底分类 */
    @Query("UPDATE recipe SET categoryId = :toCategoryId WHERE categoryId = :fromCategoryId")
    suspend fun reassignCategory(fromCategoryId: Long, toCategoryId: Long)

    /** 食材种类删除时，把该种类的配料行移入兜底种类 */
    @Query("UPDATE recipe_ingredient SET ingredientType = :toType WHERE ingredientType = :fromType")
    suspend fun reassignIngredientType(fromType: String, toType: String)

    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun clearIngredients(recipeId: Long)

    @Insert
    suspend fun insertIngredients(lines: List<RecipeIngredientEntity>)

    @Query("SELECT * FROM recipe")
    suspend fun exportAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredient")
    suspend fun exportRecipeIngredients(): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_tag")
    suspend fun exportRecipeTags(): List<RecipeTagCrossRef>

    @Query(
        """
        SELECT tag.* FROM tag
        JOIN recipe_tag ON recipe_tag.tagId = tag.id
        WHERE recipe_tag.recipeId = :recipeId
        ORDER BY tag.sortOrder
        """
    )
    suspend fun getTagsForRecipe(recipeId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagCrossRefs(refs: List<RecipeTagCrossRef>)

    @Query("DELETE FROM recipe_tag WHERE recipeId = :recipeId")
    suspend fun clearTags(recipeId: Long)

    /** 删除系统标签时，解除所有菜谱与该标签的关联 */
    @Query("DELETE FROM recipe_tag WHERE tagId = :tagId")
    suspend fun deleteTagCrossRefs(tagId: Long)
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tag ORDER BY sortOrder")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TagEntity>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM tag")
    suspend fun exportAll(): List<TagEntity>
}
