package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientType
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.repository.RoomBackupRepository
import com.skyanchor.mealtime.data.repository.RoomRecipeRepository
import com.skyanchor.mealtime.domain.usecase.SaveRecipeUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Phase 7：备份导出 → 恢复的往返一致性 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundtripTest {

    private lateinit var db: MealTimeDatabase
    private lateinit var backup: RoomBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = DatabaseFactory.create(context, inMemory = true)
        db.openHelper.writableDatabase
        backup = RoomBackupRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seed() = runBlocking {
        val recipeRepository = RoomRecipeRepository(db)
        val saveRecipe = SaveRecipeUseCase(recipeRepository, com.skyanchor.mealtime.data.repository.RoomIngredientRepository(db))
        val recipeId = saveRecipe(
            recipe = Recipe(name = "番茄炒蛋", steps = listOf("炒蛋", "下番茄")),
            ingredients = listOf(
                RecipeIngredientLine(
                    ingredient = Ingredient(name = "番茄"),
                    quantity = 2.0,
                    unit = "个",
                    type = IngredientType.INGREDIENT,
                    sortOrder = 0,
                ),
            ),
            tagIds = listOf(recipeRepository.getOrCreateTag("快手").id),
        )
        val ingredientId = db.ingredientDao().getByName("番茄")!!.id
        db.inventoryItemDao().insert(
            InventoryItemEntity(
                ingredientId = ingredientId,
                quantity = 4.0,
                unit = "个",
                expireDate = LocalDate.now().toEpochDay() + 3,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        val mealRepository = com.skyanchor.mealtime.data.repository.RoomMealRepository(db)
        mealRepository.addPlan(LocalDate.now(), MealType.LUNCH, recipeId)
        mealRepository.insertRecord(
            com.skyanchor.mealtime.core.model.MealRecord(
                date = LocalDate.now(),
                mealType = MealType.LUNCH,
                servings = 2,
            )
        )
        db.appSettingDao().put(
            com.skyanchor.mealtime.data.local.entity.AppSettingEntity("default_servings", "2")
        )
    }

    @Test
    fun exportContainsAllTablesAndImportRestoresThem() = runBlocking {
        seed()
        val json = backup.exportJson()
        val root = JSONObject(json)
        assertEquals(1, root.getInt("version"))
        assertEquals(1, root.getJSONArray("recipes").length())
        assertEquals(1, root.getJSONArray("recipeIngredients").length())
        assertEquals(1, root.getJSONArray("inventoryItems").length())
        assertEquals(1, root.getJSONArray("mealPlans").length())
        assertEquals(1, root.getJSONArray("mealRecords").length())
        assertTrue(root.getJSONArray("appSettings").length() >= 1)

        // 覆盖式恢复到同一库（先 import 清空再写入，数量应保持一致）
        val summary = backup.importJson(json)
        assertEquals(1, summary.recipes)
        assertEquals(1, summary.inventoryItems)
        assertEquals(1, summary.mealRecords)

        val restored = db.recipeDao().exportAll().first()
        assertEquals("番茄炒蛋", restored.name)
        assertEquals(listOf("炒蛋", "下番茄"), restored.steps?.split("\n"))
        val line = db.recipeDao().exportRecipeIngredients().first()
        assertEquals(2.0, line.quantity!!, 1e-9)
        assertEquals(4.0, db.inventoryItemDao().exportAllItems().first().quantity!!, 1e-9)
        assertEquals(2, db.appSettingDao().exportAll().first { it.key == "default_servings" }.value.toInt())
        assertTrue(db.mealPlanDao().exportAllPlans().isNotEmpty())
        // 预置分类随备份完整往返（建库 6 个 → 导出 → 恢复仍 6 个）
        assertEquals(MealTimeDatabase.DEFAULT_CATEGORIES.size, db.categoryDao().exportAll().size)
    }

    @Test
    fun importRejectsUnknownVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { backup.importJson("{\"version\":999}") }
        }
    }
}
