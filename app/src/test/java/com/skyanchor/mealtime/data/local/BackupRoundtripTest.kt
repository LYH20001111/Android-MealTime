package com.skyanchor.mealtime.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.repository.RoomBackupRepository
import com.skyanchor.mealtime.data.repository.RoomRecipeRepository
import com.skyanchor.mealtime.domain.usecase.SaveRecipeUseCase
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Phase 7：备份导出 → 恢复的往返一致性（纯 JSON + 完整 ZIP） */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundtripTest {

    private lateinit var context: Context
    private lateinit var db: MealTimeDatabase
    private lateinit var backup: RoomBackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = DatabaseFactory.create(context, inMemory = true)
        db.openHelper.writableDatabase
        backup = RoomBackupRepository(db, context)
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
                    type = IngredientTypes.INGREDIENT,
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

    // ---- ZIP 完整备份（数据 + 图片） ----

    @Test
    fun zipBackupRoundtripRestoresDataAndImages() = runBlocking {
        seed()
        val coversDir = File(context.filesDir, "recipe_covers").apply { mkdirs() }
        val imageFile = File(coversDir, "img_backup_test.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val recipe = db.recipeDao().exportAll().first()
        db.recipeDao().update(recipe.copy(imageUri = "file://" + imageFile.absolutePath))

        val zipFile = File(context.cacheDir, "backup_test.zip")
        val progress = mutableListOf<Pair<Int, Int>>()
        FileOutputStream(zipFile).use { out ->
            backup.createBackupZip(out) { p, t -> progress.add(p to t) }
        }
        assertTrue(zipFile.exists())
        assertTrue(progress.isNotEmpty())
        assertEquals(1 to 1, progress.last())

        // 模拟换机：本机图片与数据库中的旧路径失效
        imageFile.delete()

        val preview = backup.previewBackupZip(zipFile)
        assertEquals(1, preview.recipes)
        assertEquals(1, preview.ingredients)
        assertEquals(1, preview.images)
        assertEquals(1, preview.mealRecords)
        assertTrue(preview.zipSizeBytes > 0)

        val summary = backup.restoreBackupZip(zipFile)
        assertEquals(1, summary.recipes)
        assertEquals(1, summary.images)
        assertEquals(1, summary.mealRecords)

        // data.json 不保存旧设备绝对路径；恢复后 imagePath 重写为本机实际路径
        val restored = db.recipeDao().exportAll().first()
        val restoredUri = restored.imageUri
        assertNotNull(restoredUri)
        assertTrue(restoredUri!!.startsWith("file://"))
        val restoredFile = File(restoredUri.removePrefix("file://"))
        assertEquals("recipe_covers", restoredFile.parentFile!!.name)
        assertTrue(restoredFile.exists())
        assertTrue(restoredFile.name.startsWith("img_backup_test"))
        assertEquals("番茄炒蛋", restored.name)
        assertTrue(db.mealPlanDao().exportAllPlans().isNotEmpty())
    }

    @Test
    fun zipRestoreRejectsMissingManifest() {
        val zipFile = File(context.cacheDir, "no_manifest.zip")
        craftZip(zipFile, manifestJson(backupVersion = 1), dataJson(recipes = emptyList()), skipManifest = true)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { backup.previewBackupZip(zipFile) }
        }
    }

    @Test
    fun zipRestoreRejectsHigherBackupVersion() {
        val zipFile = File(context.cacheDir, "future.zip")
        craftZip(zipFile, manifestJson(backupVersion = 99), dataJson(recipes = emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { backup.previewBackupZip(zipFile) }
        }
    }

    @Test
    fun zipRestoreRejectsCorruptFile() {
        val zipFile = File(context.cacheDir, "corrupt.zip").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { backup.previewBackupZip(zipFile) }
        }
    }

    @Test
    fun zipRestoreWritesImagesToPrivateDirAndSkipsDeviceBoundSettings() = runBlocking {
        val data = dataJson(
            recipes = listOf("""{"id":1,"name":"换机菜谱","createdAt":1,"updatedAt":1,"imagePath":"images/recipes/a.webp"}"""),
            appSettings = listOf(
                """{"key":"notification_enabled","value":"true"}""",
                """{"key":"default_servings","value":"3"}""",
            ),
        )
        val zipFile = File(context.cacheDir, "crafted.zip")
        craftZip(zipFile, manifestJson(backupVersion = 1), data, mapOf("images/recipes/a.webp" to byteArrayOf(9, 9)))

        val summary = backup.restoreBackupZip(zipFile)
        assertEquals(1, summary.recipes)
        assertEquals(1, summary.images)

        val recipe = db.recipeDao().exportAll().single()
        val file = File(recipe.imageUri!!.removePrefix("file://"))
        assertEquals(File(File(context.filesDir, "recipe_covers"), "a.webp").absolutePath, file.absolutePath)
        assertArrayEquals(byteArrayOf(9, 9), file.readBytes())

        // 设备绑定设置（通知权限开关）不随备份恢复；业务设置正常恢复
        assertNull(db.appSettingDao().exportAll().firstOrNull { it.key == "notification_enabled" })
        assertEquals("3", db.appSettingDao().exportAll().first { it.key == "default_servings" }.value)
    }

    @Test
    fun zipRestoreRejectsPathTraversalEntries() = runBlocking {
        val data = dataJson(
            recipes = listOf("""{"id":1,"name":"恶意菜谱","createdAt":1,"updatedAt":1,"imagePath":"images/recipes/../../evil.webp"}"""),
        )
        val zipFile = File(context.cacheDir, "evil.zip")
        craftZip(
            zipFile,
            manifestJson(backupVersion = 1),
            data,
            mapOf(
                "images/recipes/../../evil.webp" to byteArrayOf(1),
                "images/recipes/sub/dir.webp" to byteArrayOf(2),
                "images/avatars/x.webp" to byteArrayOf(3),
            ),
        )

        backup.restoreBackupZip(zipFile)

        // 目录穿越条目不落盘；菜谱恢复但图片引用被置空
        assertFalse(File(context.cacheDir, "evil.webp").exists())
        assertFalse(File(context.filesDir, "evil.webp").exists())
        assertNull(db.recipeDao().exportAll().single().imageUri)
    }

    // ---- 测试辅助：手工构造 ZIP 备份 ----

    private fun manifestJson(backupVersion: Int): String = JSONObject().apply {
        put("format", "MEALTIME_BACKUP")
        put("backupVersion", backupVersion)
        put("appVersion", "test")
        put("databaseVersion", 1)
        put("createdAt", "2026-09-08T10:00:00+08:00")
        put("files", JSONObject().apply {
            put("data", "data.json")
            put("imageDirectory", "images/")
            put("recipeImageCount", 0)
            put("ingredientImageCount", 0)
            put("totalImageCount", 0)
        })
    }.toString()

    private fun dataJson(
        recipes: List<String> = emptyList(),
        ingredients: List<String> = emptyList(),
        appSettings: List<String> = emptyList(),
    ): String = JSONObject().apply {
        put("version", 1)
        put("exportedAt", 0L)
        put("recipes", org.json.JSONArray().apply { recipes.forEach { put(org.json.JSONObject(it)) } })
        put("ingredients", org.json.JSONArray().apply { ingredients.forEach { put(org.json.JSONObject(it)) } })
        put("recipeIngredients", org.json.JSONArray())
        put("inventoryItems", org.json.JSONArray())
        put("inventoryTransactions", org.json.JSONArray())
        put("mealPlans", org.json.JSONArray())
        put("mealRecords", org.json.JSONArray())
        put("categories", org.json.JSONArray())
        put("ingredientTypes", org.json.JSONArray())
        put("tags", org.json.JSONArray())
        put("recipeTags", org.json.JSONArray())
        put("appSettings", org.json.JSONArray().apply { appSettings.forEach { put(org.json.JSONObject(it)) } })
    }.toString()

    private fun craftZip(
        target: File,
        manifest: String,
        data: String,
        entries: Map<String, ByteArray> = emptyMap(),
        skipManifest: Boolean = false,
    ) {
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            if (!skipManifest) {
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(data.toByteArray())
            zip.closeEntry()
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
