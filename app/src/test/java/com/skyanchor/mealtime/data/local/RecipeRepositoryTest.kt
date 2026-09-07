package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.repository.RoomIngredientRepository
import com.skyanchor.mealtime.data.repository.RoomRecipeRepository
import com.skyanchor.mealtime.domain.usecase.SaveRecipeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 2：菜谱保存/更新/归档/校验的用例级测试 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecipeRepositoryTest {

    private lateinit var db: MealTimeDatabase
    private lateinit var recipeRepository: RoomRecipeRepository
    private lateinit var ingredientRepository: RoomIngredientRepository
    private lateinit var saveRecipe: SaveRecipeUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = DatabaseFactory.create(context, inMemory = true)
        db.openHelper.writableDatabase
        recipeRepository = RoomRecipeRepository(db)
        ingredientRepository = RoomIngredientRepository(db)
        saveRecipe = SaveRecipeUseCase(recipeRepository, ingredientRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveRecipeCreatesDetailWithIngredientsAndTags() = runBlocking {
        val recipeId = saveRecipe(
            recipe = Recipe(name = "番茄炒蛋"),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredient = com.skyanchor.mealtime.core.model.Ingredient(name = "番茄"),
                    quantity = 2.0,
                    unit = "个",
                    type = IngredientTypes.INGREDIENT,
                    sortOrder = 0,
                ),
                RecipeIngredientLine(
                    ingredient = com.skyanchor.mealtime.core.model.Ingredient(name = "盐"),
                    quantity = 2.0,
                    unit = "g",
                    type = IngredientTypes.SEASONING,
                    sortOrder = 1,
                ),
            ),
            tagIds = listOf(recipeRepository.getOrCreateTag("快手").id),
        )

        val detail = recipeRepository.getRecipeDetail(recipeId)
        assertNotNull(detail)
        assertEquals("番茄炒蛋", detail!!.recipe.name)
        assertEquals(listOf("番茄", "盐"), detail.ingredients.map { it.ingredient.name })
        assertEquals(IngredientTypes.SEASONING, detail.ingredients[1].type)
        assertEquals(listOf("快手"), detail.tags.map { it.name })

        // 食材字典应为"选择或创建"落库的两条
        assertNotNull(ingredientRepository.getIngredientByName("番茄"))
        assertNotNull(ingredientRepository.getIngredientByName("盐"))
    }

    @Test
    fun saveRecipeRejectsBlankName() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                saveRecipe(recipe = Recipe(name = "   "), ingredients = emptyList(), tagIds = emptyList())
            }
        }
    }

    @Test
    fun updateRecipeReplacesIngredientLines() = runBlocking {
        val recipeId = saveRecipe(
            recipe = Recipe(name = "旧菜"),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredient = com.skyanchor.mealtime.core.model.Ingredient(name = "土豆"),
                    quantity = 1.0,
                    unit = "个",
                    type = IngredientTypes.INGREDIENT,
                    sortOrder = 0,
                ),
            ),
            tagIds = emptyList(),
        )

        val existing = recipeRepository.getRecipeDetail(recipeId)!!
        saveRecipe(
            recipe = existing.recipe.copy(name = "新菜名", steps = listOf("切块", "下锅")),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredient = com.skyanchor.mealtime.core.model.Ingredient(name = "青椒"),
                    quantity = 2.0,
                    unit = "个",
                    type = IngredientTypes.INGREDIENT,
                    sortOrder = 0,
                ),
            ),
            tagIds = emptyList(),
        )

        val updated = recipeRepository.getRecipeDetail(recipeId)!!
        assertEquals("新菜名", updated.recipe.name)
        assertEquals(listOf("切块", "下锅"), updated.recipe.steps)
        assertEquals(listOf("青椒"), updated.ingredients.map { it.ingredient.name })
    }

    @Test
    fun archivedRecipeHiddenFromListButDetailStillReadable() = runBlocking {
        val recipeId = saveRecipe(
            recipe = Recipe(name = "被归档的菜"),
            ingredients = emptyList(),
            tagIds = emptyList(),
        )
        recipeRepository.archiveRecipe(recipeId)

        val visible = recipeRepository.observeRecipes().first()
        assertTrue(visible.isEmpty())
        // R07：归档后详情与历史仍可读
        assertNotNull(recipeRepository.getRecipeDetail(recipeId))
    }

    @Test
    fun getOrCreateTagReusesSameRow() = runBlocking {
        val first = recipeRepository.getOrCreateTag("家常")
        val second = recipeRepository.getOrCreateTag("家常")
        assertEquals(first.id, second.id)
    }
}
