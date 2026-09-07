package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.FALLBACK_CATEGORY_NAME
import com.skyanchor.mealtime.core.model.Ingredient
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 分类管理：菜谱分类与食材种类的增删，删除时归入「其他」 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaxonomyRepositoryTest {

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
    fun defaultCategoriesAndTypesSeeded() = runBlocking {
        val categories = recipeRepository.observeCategories().first()
        assertEquals(MealTimeDatabase.DEFAULT_CATEGORIES, categories.map { it.name })

        val types = ingredientRepository.observeTypes().first()
        assertEquals(
            MealTimeDatabase.DEFAULT_INGREDIENT_TYPES,
            types.map { it.key to it.label },
        )
    }

    @Test
    fun addCategoryAppendsAndRejectsDuplicate() = runBlocking {
        val added = recipeRepository.addCategory("烘焙")
        assertEquals("烘焙", added.name)
        assertEquals(
            MealTimeDatabase.DEFAULT_CATEGORIES + "烘焙",
            recipeRepository.observeCategories().first().map { it.name },
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { recipeRepository.addCategory(" 烘焙 ") }
        }
        Unit
    }

    @Test
    fun deleteCategoryMovesRecipesToFallback() = runBlocking {
        val soup = recipeRepository.observeCategories().first().first { it.name == "汤羹" }
        val recipeId = saveRecipe(
            recipe = Recipe(name = "紫菜蛋花汤", categoryId = soup.id),
            ingredients = emptyList(),
            tagIds = emptyList(),
        )

        recipeRepository.deleteCategory(soup.id)

        val detail = recipeRepository.getRecipeDetail(recipeId)!!
        val fallback = recipeRepository.observeCategories().first().first { it.name == FALLBACK_CATEGORY_NAME }
        assertEquals(fallback.id, detail.recipe.categoryId)
        // 「其他」仍在分类列表中
        assertTrue(recipeRepository.observeCategories().first().any { it.name == FALLBACK_CATEGORY_NAME })
    }

    @Test
    fun fallbackCategoryCannotBeDeleted() = runBlocking {
        val fallback = recipeRepository.observeCategories().first().first { it.name == FALLBACK_CATEGORY_NAME }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { recipeRepository.deleteCategory(fallback.id) }
        }
        Unit
    }

    @Test
    fun deleteIngredientTypeMovesIngredientsAndRecipeLinesToFallback() = runBlocking {
        ingredientRepository.addType("干货")
        val recipeId = saveRecipe(
            recipe = Recipe(name = "香菇炖鸡"),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredient = Ingredient(name = "干香菇"),
                    quantity = 5.0,
                    unit = "朵",
                    type = "干货",
                    sortOrder = 0,
                ),
            ),
            tagIds = emptyList(),
        )

        // 库存也挂一条干货种类的食材
        val ingredient = ingredientRepository.getIngredientByName("干香菇")!!
        assertNotNull(ingredient)

        ingredientRepository.deleteType("干货")

        // 食材与配料行都归入「其他」，种类行被删除
        assertEquals(IngredientTypes.OTHER, ingredientRepository.getIngredientByName("干香菇")!!.type)
        val detail = recipeRepository.getRecipeDetail(recipeId)!!
        assertEquals(listOf(IngredientTypes.OTHER), detail.ingredients.map { it.type })
        assertNull(db.ingredientTypeDao().getByKey("干货"))
        // 兜底种类自动出现
        assertEquals(
            MealTimeDatabase.DEFAULT_INGREDIENT_TYPES.map { it.first } + IngredientTypes.OTHER,
            ingredientRepository.observeTypes().first().map { it.key },
        )
    }

    @Test
    fun deleteSeasoningTypeKeepsConsumableLogicConsistent() = runBlocking {
        // 删除「调料」后，调料食材归入「其他」；「其他」按食材语义参与扣减
        val recipeId = saveRecipe(
            recipe = Recipe(name = "番茄炒蛋"),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredient = Ingredient(name = "盐"),
                    quantity = 2.0,
                    unit = "g",
                    type = IngredientTypes.SEASONING,
                    sortOrder = 0,
                ),
            ),
            tagIds = emptyList(),
        )

        val seasoningType = ingredientRepository.observeTypes().first().first { it.key == IngredientTypes.SEASONING }
        ingredientRepository.deleteType(seasoningType.key)

        assertEquals(IngredientTypes.OTHER, ingredientRepository.getIngredientByName("盐")!!.type)
        assertEquals(IngredientTypes.OTHER, recipeRepository.getRecipeDetail(recipeId)!!.ingredients[0].type)
    }

    @Test
    fun fallbackTypeCannotBeDeletedAndDuplicateRejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ingredientRepository.deleteType(IngredientTypes.OTHER) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ingredientRepository.addType(IngredientTypes.label(IngredientTypes.INGREDIENT)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ingredientRepository.addType(IngredientTypes.INGREDIENT) }
        }
        Unit
    }
}
