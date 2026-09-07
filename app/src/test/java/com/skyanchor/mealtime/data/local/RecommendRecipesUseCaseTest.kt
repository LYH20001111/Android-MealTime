package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.Difficulty
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.repository.RoomInventoryRepository
import com.skyanchor.mealtime.data.repository.RoomMealRepository
import com.skyanchor.mealtime.data.repository.RoomRecipeRepository
import com.skyanchor.mealtime.data.repository.RoomSettingsRepository
import com.skyanchor.mealtime.domain.usecase.RecommendRecipesUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Phase 6：规则推荐评分与排序（PRD §13） */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecommendRecipesUseCaseTest {

    private lateinit var db: MealTimeDatabase
    private lateinit var useCase: RecommendRecipesUseCase
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = DatabaseFactory.create(context, inMemory = true)
        db.openHelper.writableDatabase
        useCase = RecommendRecipesUseCase(
            RoomRecipeRepository(db),
            RoomInventoryRepository(db),
            RoomMealRepository(db),
            RoomSettingsRepository(db.appSettingDao()),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun ingredientId(name: String): Long =
        db.ingredientDao().getByName(name)?.id
            ?: db.ingredientDao().insert(
                IngredientEntity(name = name, type = "INGREDIENT", createdAt = 0),
            )

    private suspend fun newRecipe(
        name: String,
        lines: List<Triple<String, Double, String>>, // 食材名, 数量, 单位
        isFavorite: Boolean = false,
        cookingTimeMin: Int? = null,
    ): Long {
        val recipeId = db.recipeDao().insert(
            RecipeEntity(
                name = name,
                isFavorite = isFavorite,
                cookingTimeMin = cookingTimeMin,
                createdAt = 0,
                updatedAt = 0,
            ),
        )
        db.recipeDao().insertIngredients(
            lines.mapIndexed { index, (ingredient, quantity, unit) ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    ingredientId = ingredientId(ingredient),
                    quantity = quantity,
                    unit = unit,
                    ingredientType = IngredientTypes.INGREDIENT,
                    sortOrder = index,
                )
            },
        )
        return recipeId
    }

    private suspend fun newBatch(ingredientName: String, quantity: Double?, expireInDays: Long?) {
        db.inventoryItemDao().insert(
            InventoryItemEntity(
                ingredientId = ingredientId(ingredientName),
                quantity = quantity,
                unit = "个",
                expireDate = expireInDays?.let { today.toEpochDay() + it },
                createdAt = 0,
                updatedAt = 0,
            )
        )
    }

    private suspend fun planOn(recipeId: Long, daysAgo: Long) {
        db.mealPlanDao().insert(
            com.skyanchor.mealtime.data.local.entity.MealPlanEntity(
                date = today.minusDays(daysAgo).toString(),
                mealType = "LUNCH",
                recipeId = recipeId,
                status = "COMPLETED",
                createdAt = 0,
                updatedAt = 0,
            )
        )
    }

    @Test
    fun stockedRecipeScoresAboveUnstocked() = runBlocking {
        val stocked = newRecipe("库存充足菜", listOf(Triple("土豆", 2.0, "个")))
        val unstocked = newRecipe("没库存菜", listOf(Triple("青椒", 2.0, "个")))
        newBatch("土豆", 5.0, expireInDays = 10)

        val result = useCase(today = today)

        assertEquals(2, result.size)
        assertEquals(stocked, result[0].recipe.id)
        assertTrue(result[0].score > result[1].score)
        assertEquals(1, result[0].matchedCount)
        assertTrue(result[0].reasons.any { it.contains("库存充足") })
        assertEquals(0, result[1].matchedCount)
    }

    @Test
    fun expiringIngredientHitBoostsScoreAndReasons() = runBlocking {
        val usesExpiring = newRecipe("临期菜", listOf(Triple("番茄", 2.0, "个")))
        newRecipe("普通菜", listOf(Triple("土豆", 2.0, "个")))
        newBatch("番茄", 3.0, expireInDays = 1) // 临期
        newBatch("土豆", 3.0, expireInDays = 10)

        val result = useCase(today = today)

        val top = result.first()
        assertEquals(usesExpiring, top.recipe.id)
        // 两者库存都满配（50）+ 7 天没吃（10）；临期命中 1 种 = 40 × 1/2 = 20
        assertTrue(top.reasons.any { it.contains("临期") && it.contains("番茄") })
        assertEquals(80, top.score)
        val plain = result.first { it.recipe.name == "普通菜" }
        assertEquals(60, plain.score)
        assertTrue(top.score > plain.score)
    }

    @Test
    fun favoriteAndNotEatenBonusesApply() = runBlocking {
        newRecipe("收藏菜", listOf(Triple("土豆", 1.0, "个")), isFavorite = true)
        newRecipe("普通菜", listOf(Triple("土豆", 1.0, "个")))
        newBatch("土豆", 5.0, expireInDays = 10)

        val result = useCase(today = today)

        val favorite = result.first { it.recipe.name == "收藏菜" }
        val plain = result.first { it.recipe.name == "普通菜" }
        // 收藏 +10；两者都 7 天没吃过 +10
        assertEquals(plain.score + 10, favorite.score)
        assertTrue(favorite.reasons.any { it.contains("收藏") })
        assertTrue(favorite.reasons.any { it.contains("最近 7 天没有吃过") })
    }

    @Test
    fun recentRepeatPenalizesAndNotEatenRewards() = runBlocking {
        val recentlyEaten = newRecipe("刚吃过", listOf(Triple("土豆", 1.0, "个")))
        val fresh = newRecipe("很久没吃", listOf(Triple("土豆", 1.0, "个")))
        newBatch("土豆", 5.0, expireInDays = 10)
        planOn(recentlyEaten, daysAgo = 1) // 3 天内吃过 → -30

        val result = useCase(today = today)

        val eaten = result.first { it.recipe.id == recentlyEaten }
        val notEaten = result.first { it.recipe.id == fresh }
        assertTrue(eaten.score < notEaten.score)
        assertTrue(eaten.reasons.any { it.contains("最近 3 天刚安排过") })
        assertTrue(notEaten.reasons.any { it.contains("最近 7 天没有吃过") })
    }

    @Test
    fun limitTruncatesResults() = runBlocking {
        repeat(4) { index -> newRecipe("菜$index", listOf(Triple("土豆", 1.0, "个"))) }
        newBatch("土豆", 5.0, expireInDays = 10)

        val result = useCase(limit = 2, today = today)

        assertEquals(2, result.size)
    }

    @Test
    fun recipeWithoutIngredientLinesGetsZeroMatchButCanBeRecommended() = runBlocking {
        newRecipe("无明细菜", emptyList())

        val result = useCase(today = today)

        assertEquals(1, result.size)
        assertEquals(0, result[0].matchedCount)
        assertEquals(0, result[0].totalIngredients)
    }

    @Test
    fun randomPickNeverReturnsArchived() = runBlocking {
        val id = newRecipe("随机候选", listOf(Triple("土豆", 1.0, "个")))
        newBatch("土豆", 5.0, expireInDays = 10)

        val pick = useCase.randomPick(today)

        assertEquals(id, pick!!.recipe.id)
        assertEquals(Difficulty.EASY, pick.recipe.difficulty)
    }
}
