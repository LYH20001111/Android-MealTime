package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.MealPlanEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.mapper.toDomain
import com.skyanchor.mealtime.data.repository.RoomIngredientRepository
import com.skyanchor.mealtime.data.repository.RoomInventoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * 数据层 DAO / Repository 测试（in-memory Room + Robolectric）。
 * 覆盖关键查询：菜谱-食材联表、归档过滤、临期排序、三餐顺序、流水事务。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MealTimeDatabaseTest {

    private lateinit var db: MealTimeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = DatabaseFactory.create(context, inMemory = true)
        db.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun ingredient(name: String, type: String = "INGREDIENT") = IngredientEntity(
        name = name, type = type, createdAt = 0,
    )

    private fun recipe(name: String) = RecipeEntity(name = name, createdAt = 0, updatedAt = 0)

    private fun inventoryItem(ingredientId: Long, quantity: Double?, expireDay: Long?) =
        InventoryItemEntity(
            ingredientId = ingredientId, quantity = quantity, unit = "个",
            expireDate = expireDay, createdAt = 0, updatedAt = 0,
        )

    @Test
    fun seedCallbackPrepopulatesDefaultCategories() = runBlocking {
        val categories = db.categoryDao().observeAll().first()
        assertEquals(MealTimeDatabase.DEFAULT_CATEGORIES, categories.map { it.name })
    }

    @Test
    fun recipeWithIngredientsRoundtrip() = runBlocking {
        val recipeId = db.recipeDao().insert(recipe("番茄炒蛋"))
        db.recipeDao().insertIngredients(
            listOf(
                RecipeIngredientEntity(
                    recipeId = recipeId, ingredientName = "番茄",
                    quantity = 2.0, unit = "个", ingredientType = "INGREDIENT", sortOrder = 0,
                ),
                RecipeIngredientEntity(
                    recipeId = recipeId, ingredientName = "鸡蛋",
                    quantity = 3.0, unit = "个", ingredientType = "INGREDIENT", sortOrder = 1,
                ),
            )
        )
        val detail = db.recipeDao().getWithIngredients(recipeId)!!
        assertEquals("番茄炒蛋", detail.recipe.name)
        assertEquals(2, detail.ingredients.size)
        assertTrue(detail.ingredients.any { it.ingredientName == "番茄" && it.quantity == 2.0 })
    }

    @Test
    fun recipeListFiltersBySearchAndArchive() = runBlocking {
        val archived = db.recipeDao().insert(recipe("番茄炒蛋"))
        db.recipeDao().insert(recipe("青椒肉丝"))
        db.recipeDao().archive(archived, now = 1)

        val visible = db.recipeDao().observeRecipes(query = null, categoryId = null, favoritesOnly = false).first()
        assertEquals(listOf("青椒肉丝"), visible.map { it.name })

        val searched = db.recipeDao().observeRecipes(query = "番茄", categoryId = null, favoritesOnly = false).first()
        assertTrue(searched.isEmpty())
    }

    @Test
    fun recipeSearchMatchesIngredientName() = runBlocking {
        val mapoId = db.recipeDao().insert(recipe("蚂蚁上树"))
        db.recipeDao().insert(recipe("青椒肉丝"))
        db.recipeDao().insertIngredients(
            listOf(
                RecipeIngredientEntity(
                    recipeId = mapoId, ingredientName = "粉丝",
                    quantity = 1.0, unit = "把", ingredientType = "INGREDIENT", sortOrder = 0,
                )
            )
        )

        val byIngredient = db.recipeDao().observeRecipes(query = "粉丝", categoryId = null, favoritesOnly = false).first()
        assertEquals(listOf("蚂蚁上树"), byIngredient.map { it.name })

        val noMatch = db.recipeDao().observeRecipes(query = "豆腐", categoryId = null, favoritesOnly = false).first()
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun expiringInventorySortedAscendingWithinThreshold() = runBlocking {
        val tomato = db.ingredientDao().insert(ingredient("番茄"))
        val today = LocalDate.now().toEpochDay()

        db.inventoryItemDao().insert(inventoryItem(tomato, 1.0, today + 5)) // 不临期
        val soon = db.inventoryItemDao().insert(inventoryItem(tomato, 1.0, today + 1))
        val sooner = db.inventoryItemDao().insert(inventoryItem(tomato, 1.0, today)) // 今天过期
        db.inventoryItemDao().insert(inventoryItem(tomato, 1.0, null)) // 无过期日
        db.inventoryItemDao().softDelete(
            db.inventoryItemDao().insert(inventoryItem(tomato, 1.0, today + 1)), now = 1,
        ) // 已删除

        val rows = db.inventoryItemDao().observeExpiring(today + 3).first()
        assertEquals(listOf(sooner, soon), rows.map { it.item.id })
    }

    @Test
    fun mealPlansOrderedByBreakfastLunchDinner() = runBlocking {
        val date = LocalDate.now().toString()
        val recipeId = db.recipeDao().insert(recipe("米饭"))
        listOf("DINNER", "BREAKFAST", "LUNCH").forEach { type ->
            db.mealPlanDao().insert(
                MealPlanEntity(
                    date = date, mealType = type, recipeId = recipeId,
                    createdAt = 0, updatedAt = 0,
                )
            )
        }
        val plans = db.mealPlanDao().observeByDate(date).first()
        assertEquals(listOf("BREAKFAST", "LUNCH", "DINNER"), plans.map { it.plan.mealType })
        assertEquals("米饭", plans.first().recipe.name)
    }

    @Test
    fun ingredientGetOrCreateReusesSameRow() = runBlocking {
        val repository = RoomIngredientRepository(db)
        val first = repository.getOrCreate("番茄", IngredientTypes.INGREDIENT)
        val second = repository.getOrCreate(" 番茄 ", IngredientTypes.INGREDIENT)
        assertEquals(first.id, second.id)
    }

    @Test
    fun adjustQuantityWritesTransactionAndArchivesDepleted() = runBlocking {
        val repository = RoomInventoryRepository(db)
        val ingredientId = db.ingredientDao().insert(ingredient("鸡蛋"))
        val ingredient = db.ingredientDao().getById(ingredientId)!!.toDomain()

        val itemId = repository.addInventory(
            InventoryItem(ingredient = ingredient, quantity = 3.0, unit = "个"),
            note = "买入",
        )
        repository.adjustQuantity(itemId, 0.0)

        val row = db.inventoryItemDao().getById(itemId)!!
        assertTrue("耗尽批次应自动归档", row.isDeleted)

        val transactions = db.inventoryTransactionDao().observeRecent(10).first()
        assertEquals(2, transactions.size) // ADD + ADJUST
        val adjust = transactions.first { it.type == "ADJUST" }
        assertEquals(-3.0, adjust.changeQuantity!!, 1e-9)
    }

    @Test
    fun deleteLastBatchRetiresIngredientForRecreation() = runBlocking {
        val inventoryRepository = RoomInventoryRepository(db)
        val ingredientRepository = RoomIngredientRepository(db)
        val ingredientId = db.ingredientDao().insert(ingredient("鸡蛋"))
        val ingredient = db.ingredientDao().getById(ingredientId)!!.toDomain()

        val itemId = inventoryRepository.addInventory(
            InventoryItem(ingredient = ingredient, quantity = 3.0, unit = "个"),
            note = "买入",
        )
        assertTrue(ingredientRepository.getStockedIngredientByName("鸡蛋") != null)

        inventoryRepository.softDelete(itemId)

        // 最后一个批次删除后，字典条目一并软删：查重不再命中，同名可再次新增
        assertTrue(db.inventoryItemDao().getById(itemId)!!.isDeleted)
        assertTrue(db.ingredientDao().getById(ingredientId)!!.isDeleted)
        assertTrue(ingredientRepository.getIngredientByName("鸡蛋") == null)
        val recreated = ingredientRepository.getOrCreate("鸡蛋", IngredientTypes.SEASONING)
        // 复活软删条目：沿用原 id（历史库存关联恢复），种类按新输入更新
        assertEquals(ingredientId, recreated.id)
        assertEquals(IngredientTypes.SEASONING, db.ingredientDao().getById(ingredientId)!!.type)
        assertTrue(!db.ingredientDao().getById(ingredientId)!!.isDeleted)
    }

    @Test
    fun validDictionaryEntryWithoutStockDoesNotBlockInventoryCreation() = runBlocking {
        val inventoryRepository = RoomInventoryRepository(db)
        val ingredientRepository = RoomIngredientRepository(db)
        // 字典中存在同名条目但无库存批次（有效未软删）
        val dictionaryEntry = ingredientRepository.getOrCreate("花菜", IngredientTypes.INGREDIENT)

        // 库存新增查重：字典存在但无库存 → 不算重名
        assertTrue(ingredientRepository.getIngredientByName("花菜") != null)
        assertTrue(ingredientRepository.getStockedIngredientByName("花菜") == null)

        // 走新增库存用例：复用字典条目建批次，不弹重名拦截；种类以食材页选择为准
        val addInventory = com.skyanchor.mealtime.domain.usecase.AddInventoryUseCase(
            inventoryRepository, ingredientRepository,
        )
        val itemId = addInventory(
            InventoryItem(
                ingredient = dictionaryEntry.copy(type = IngredientTypes.SEASONING),
                quantity = 1.0, unit = "颗",
            ),
        )
        assertTrue(itemId > 0)
        assertTrue(ingredientRepository.getStockedIngredientByName("花菜")?.id == dictionaryEntry.id)
        // 复用而非新建：字典中仍只有一条花菜
        assertEquals(1, db.ingredientDao().exportAll().count { it.name == "花菜" })
        // 种类以食材页表单选择为准
        assertEquals(IngredientTypes.SEASONING, db.ingredientDao().getById(dictionaryEntry.id)!!.type)
    }

    @Test
    fun deleteBatchKeepsIngredientWhileOtherBatchesRemain() = runBlocking {
        val repository = RoomInventoryRepository(db)
        val ingredientId = db.ingredientDao().insert(ingredient("鸡蛋"))
        val ingredient = db.ingredientDao().getById(ingredientId)!!.toDomain()

        val first = repository.addInventory(
            InventoryItem(ingredient = ingredient, quantity = 1.0, unit = "个"),
            note = null,
        )
        repository.addInventory(
            InventoryItem(ingredient = ingredient, quantity = 2.0, unit = "个"),
            note = null,
        )

        repository.softDelete(first)

        // 仍有有效批次：字典条目保留，查重继续命中
        assertTrue(!db.ingredientDao().getById(ingredientId)!!.isDeleted)
        assertTrue(RoomIngredientRepository(db).getIngredientByName("鸡蛋") != null)
    }

    @Test
    fun updateIngredientTypeOnlyAffectsDictionary() = runBlocking {
        // 配料与字典解耦：字典种类变更不再同步菜谱配料行
        val ingredientRepository = RoomIngredientRepository(db)
        val ingredientId = db.ingredientDao().insert(ingredient("生姜", type = "SEASONING"))
        val recipeId = db.recipeDao().insert(recipe("姜汁鸡"))
        db.recipeDao().insertIngredients(
            listOf(
                RecipeIngredientEntity(
                    recipeId = recipeId, ingredientName = "生姜",
                    quantity = 1.0, unit = "块", ingredientType = "SEASONING", sortOrder = 0,
                )
            )
        )

        ingredientRepository.updateType(ingredientId, IngredientTypes.INGREDIENT)

        assertEquals(IngredientTypes.INGREDIENT, db.ingredientDao().getById(ingredientId)!!.type)
        val line = db.recipeDao().getWithIngredients(recipeId)!!.ingredients.first()
        assertEquals("SEASONING", line.ingredientType)
    }

    @Test
    fun movePlanReordersDishesWithinMeal() = runBlocking {
        val today = LocalDate.now()
        val recipeRepository = com.skyanchor.mealtime.data.repository.RoomRecipeRepository(db)
        val mealRepository = com.skyanchor.mealtime.data.repository.RoomMealRepository(db)
        val r1 = db.recipeDao().insert(recipe("菜品A"))
        val r2 = db.recipeDao().insert(recipe("菜品B"))
        val r3 = db.recipeDao().insert(recipe("菜品C"))
        mealRepository.addPlan(today, com.skyanchor.mealtime.core.model.MealType.LUNCH, r1)
        val p2 = mealRepository.addPlan(today, com.skyanchor.mealtime.core.model.MealType.LUNCH, r2)
        val p3 = mealRepository.addPlan(today, com.skyanchor.mealtime.core.model.MealType.LUNCH, r3)

        mealRepository.movePlan(p3!!, -1)
        mealRepository.movePlan(p2!!, +1)

        val lunch = db.mealPlanDao().observeByDate(today.toString()).first()
            .filter { it.plan.mealType == "LUNCH" }
        assertEquals(listOf("菜品A", "菜品C", "菜品B"), lunch.map { it.recipe.name })
        assertTrue(recipeRepository.getRecipe(r1) != null)
    }
}
