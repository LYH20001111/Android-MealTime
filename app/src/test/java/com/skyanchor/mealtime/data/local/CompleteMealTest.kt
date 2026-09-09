package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.repository.RoomIngredientRepository
import com.skyanchor.mealtime.data.repository.RoomInventoryRepository
import com.skyanchor.mealtime.data.repository.RoomMealRepository
import com.skyanchor.mealtime.data.repository.RoomRecipeRepository
import com.skyanchor.mealtime.domain.usecase.CompleteMealUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Phase 5 核心闭环测试：CompleteMealUseCase 的多菜品 FIFO 扣减、
 * 库存不足、无明细菜（R10）、用户调整（R05）、归档后记录保留（R07）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompleteMealTest {

    private lateinit var db: MealTimeDatabase
    private lateinit var mealRepository: RoomMealRepository
    private lateinit var recipeRepository: RoomRecipeRepository
    private lateinit var inventoryRepository: RoomInventoryRepository
    private lateinit var ingredientRepository: RoomIngredientRepository
    private lateinit var completeMeal: CompleteMealUseCase
    private val today: LocalDate = LocalDate.now()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = DatabaseFactory.create(context, inMemory = true)
        db.openHelper.writableDatabase
        mealRepository = RoomMealRepository(db)
        recipeRepository = RoomRecipeRepository(db)
        inventoryRepository = RoomInventoryRepository(db)
        ingredientRepository = RoomIngredientRepository(db)
        completeMeal = CompleteMealUseCase(mealRepository, recipeRepository, ingredientRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 库存批次仍挂在食材字典上：同名共享一条字典记录，保证扣减可按名称匹配到 */
    private suspend fun ingredientId(name: String): Long =
        db.ingredientDao().getByName(name)?.id
            ?: db.ingredientDao().insert(
                IngredientEntity(name = name, type = "INGREDIENT", createdAt = 0),
            )

    private suspend fun newRecipe(
        name: String,
        lines: List<RecipeIngredientLine>,
    ): Long {
        val recipeId = db.recipeDao().insert(RecipeEntity(name = name, createdAt = 0, updatedAt = 0))
        db.recipeDao().insertIngredients(
            lines.map { line ->
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    ingredientName = line.name,
                    quantity = line.quantity,
                    unit = line.unit,
                    ingredientType = line.type,
                    sortOrder = line.sortOrder,
                )
            },
        )
        return recipeId
    }

    private suspend fun newBatch(ingredientName: String, quantity: Double?, expireInDays: Long?): Long =
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

    private suspend fun consumeTransactions() =
        db.inventoryTransactionDao().observeRecent(50).first().filter { it.type == "CONSUME" }

    @Test
    fun multiDishMealDeductsInventoryFifoAndWritesRecords() = runBlocking {
        // 菜1：番茄 x2 + 鸡蛋 x3；菜2：番茄 x1（同食材跨菜聚合）
        newRecipe(
            "番茄炒蛋",
            listOf(
                RecipeIngredientLine(name = "番茄", quantity = 2.0, unit = "个", type = IngredientTypes.INGREDIENT, sortOrder = 0),
                RecipeIngredientLine(name = "鸡蛋", quantity = 3.0, unit = "个", type = IngredientTypes.INGREDIENT, sortOrder = 1),
            ),
        )
        newRecipe(
            "番茄汤",
            listOf(
                RecipeIngredientLine(name = "番茄", quantity = 1.0, unit = "个", type = IngredientTypes.INGREDIENT, sortOrder = 0),
            ),
        )
        val earlyBatch = newBatch("番茄", 1.0, expireInDays = 1) // 先过期，先扣
        val laterBatch = newBatch("番茄", 5.0, expireInDays = 5)
        val eggBatch = newBatch("鸡蛋", 10.0, expireInDays = 7)

        mealRepository.addPlan(today, MealType.LUNCH, db.recipeDao().observeRecipes(null, null, false).first()[0].id)
        mealRepository.addPlan(today, MealType.LUNCH, db.recipeDao().observeRecipes(null, null, false).first()[1].id)

        val deductedKinds = completeMeal(today, MealType.LUNCH, servings = 2)
        assertEquals(2, deductedKinds) // 番茄 + 鸡蛋

        // 番茄 FIFO：早批次扣光归档，晚批次扣 2 剩 3
        val early = db.inventoryItemDao().getById(earlyBatch)!!
        val later = db.inventoryItemDao().getById(laterBatch)!!
        assertEquals(0.0, early.quantity!!, 1e-9)
        assertTrue("耗尽批次应自动归档", early.isDeleted)
        assertEquals(3.0, later.quantity!!, 1e-9)
        assertEquals(7.0, db.inventoryItemDao().getById(eggBatch)!!.quantity!!, 1e-9)

        // 流水：番茄两条（-1、-2）+ 鸡蛋一条（-3），全部 CONSUME 且关联 MealRecord
        val consumes = consumeTransactions()
        assertEquals(3, consumes.size)
        assertTrue(consumes.all { it.sourceType == "MEAL" && it.sourceId != null })
        val tomatoId = later.ingredientId
        assertEquals(
            setOf(-1.0, -2.0),
            consumes.filter { it.ingredientId == tomatoId }.map { it.changeQuantity }.toSet(),
        )
        assertEquals(
            setOf(-3.0),
            consumes.filter { it.ingredientId == db.inventoryItemDao().getById(eggBatch)!!.ingredientId }
                .map { it.changeQuantity }.toSet(),
        )

        // 用餐记录一条（人数 2）+ 计划全部完成
        val records = db.mealRecordDao().getByDate(today.toString())
        assertEquals(1, records.size)
        assertEquals(2, records.first().servings)
        val plans = mealRepository.getPlans(today, MealType.LUNCH)
        assertEquals(2, plans.size)
        assertTrue(plans.all { it.status == MealStatus.COMPLETED })
    }

    @Test
    fun insufficientStockRecordsActualConsumption() = runBlocking {
        newRecipe(
            "需要很多鸡蛋",
            listOf(
                RecipeIngredientLine(name = "鸡蛋", quantity = 3.0, unit = "个", type = IngredientTypes.INGREDIENT, sortOrder = 0),
            ),
        )
        val batch = newBatch("鸡蛋", 1.0, expireInDays = 2)
        val recipeId = db.recipeDao().observeRecipes(null, null, false).first().first().id
        mealRepository.addPlan(today, MealType.LUNCH, recipeId)

        completeMeal(today, MealType.LUNCH)

        assertEquals(0.0, db.inventoryItemDao().getById(batch)!!.quantity!!, 1e-9)
        val consumes = consumeTransactions()
        assertEquals(2, consumes.size) // -1 扣库存 + -2 超出部分如实记录
        assertEquals(
            setOf(-1.0, -2.0),
            consumes.map { it.changeQuantity }.toSet(),
        )
        assertTrue(consumes.any { it.note?.contains("库存不足") == true })
    }

    @Test
    fun recipeWithoutIngredientsCreatesRecordButNoDeduction() = runBlocking {
        newRecipe("白米饭", emptyList())
        val recipeId = db.recipeDao().observeRecipes(null, null, false).first().first().id
        mealRepository.addPlan(today, MealType.LUNCH, recipeId)

        val deducted = completeMeal(today, MealType.LUNCH)

        assertEquals(0, deducted)
        assertEquals(0, consumeTransactions().size)
        assertEquals(1, db.mealRecordDao().getByDate(today.toString()).size)
    }

    @Test
    fun unstockedIngredientIsSkippedFromDeduction() = runBlocking {
        // 配料与食材字典解耦：食材页没有"仙人掌"，完成用餐不产生任何扣减与流水
        newRecipe(
            "仙人掌沙拉",
            listOf(
                RecipeIngredientLine(name = "仙人掌", quantity = 2.0, unit = "片", type = IngredientTypes.INGREDIENT, sortOrder = 0),
            ),
        )
        val recipeId = db.recipeDao().observeRecipes(null, null, false).first().first().id
        mealRepository.addPlan(today, MealType.LUNCH, recipeId)

        val deducted = completeMeal(today, MealType.LUNCH)

        assertEquals(0, deducted)
        assertEquals(0, consumeTransactions().size)
        assertEquals(1, db.mealRecordDao().getByDate(today.toString()).size)
    }

    @Test
    fun userAdjustmentOverridesExpectedQuantity() = runBlocking {
        newRecipe(
            "番茄炒蛋",
            listOf(
                RecipeIngredientLine(name = "番茄", quantity = 2.0, unit = "个", type = IngredientTypes.INGREDIENT, sortOrder = 0),
            ),
        )
        val batch = newBatch("番茄", 5.0, expireInDays = 3)
        val recipeId = db.recipeDao().observeRecipes(null, null, false).first().first().id
        val planId = mealRepository.addPlan(today, MealType.LUNCH, recipeId)

        // 用户把实际消耗从 2 改为 1（R05）：调整项按 "planId:食材名" 标识
        completeMeal(today, MealType.LUNCH, adjustments = mapOf("$planId:番茄" to 1.0))

        assertEquals(4.0, db.inventoryItemDao().getById(batch)!!.quantity!!, 1e-9)
    }

    @Test
    fun secondCompleteOfSameMealIsRejected() = runBlocking {
        newRecipe(
            "番茄炒蛋",
            listOf(
                RecipeIngredientLine(name = "番茄", quantity = 2.0, unit = "个", type = IngredientTypes.INGREDIENT, sortOrder = 0),
            ),
        )
        newBatch("番茄", 5.0, expireInDays = 4)
        val recipeId = db.recipeDao().observeRecipes(null, null, false).first().first().id
        mealRepository.addPlan(today, MealType.LUNCH, recipeId)

        completeMeal(today, MealType.LUNCH)
        assertEquals(1, db.mealRecordDao().getByDate(today.toString()).size)

        val second = runCatching { completeMeal(today, MealType.LUNCH) }
        assertTrue(second.isFailure)
        assertTrue(second.exceptionOrNull() is IllegalStateException)
        // 不产生第二条用餐记录
        assertEquals(1, db.mealRecordDao().getByDate(today.toString()).size)
    }

    @Test
    fun archivedRecipeKeepsMealRecord() = runBlocking {
        newRecipe(
            "会被归档的菜",
            listOf(
                RecipeIngredientLine(name = "豆腐", quantity = 1.0, unit = "块", type = IngredientTypes.INGREDIENT, sortOrder = 0),
            ),
        )
        newBatch("豆腐", 5.0, expireInDays = 6)
        val recipeId = db.recipeDao().observeRecipes(null, null, false).first().first().id
        mealRepository.addPlan(today, MealType.LUNCH, recipeId)
        completeMeal(today, MealType.LUNCH)

        recipeRepository.archiveRecipe(recipeId)

        // R07：菜谱归档后，历史用餐记录与已完成的计划不受影响
        assertEquals(1, db.mealRecordDao().getByDate(today.toString()).size)
        val plans = mealRepository.getPlans(today, MealType.LUNCH)
        assertEquals(1, plans.size)
        assertNotNull(db.recipeDao().getById(recipeId)) // 归档非物理删除
        assertTrue(db.recipeDao().getById(recipeId)!!.isArchived)
        assertTrue(db.mealPlanDao().getByDateAndMeal(today.toString(), "LUNCH", "COMPLETED").isNotEmpty())
    }
}
