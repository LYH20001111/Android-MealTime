package com.skyanchor.mealtime.data.repository

import android.content.Context
import com.skyanchor.mealtime.data.backup.BackupExporter
import com.skyanchor.mealtime.data.backup.BackupImporter
import com.skyanchor.mealtime.data.backup.BackupJsonCodec
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.domain.repository.BackupPreview
import com.skyanchor.mealtime.domain.repository.BackupRepository
import com.skyanchor.mealtime.domain.repository.BackupSummary
import com.skyanchor.mealtime.domain.repository.BackupZipSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * 备份实现：纯 JSON 走 [BackupJsonCodec]；完整 ZIP 委托 [BackupExporter]/[BackupImporter]。
 * 结构见 BackupJsonCodec 注释；恢复为覆盖式整包写入（保留原 id）。
 */
class RoomBackupRepository(
    private val db: MealTimeDatabase,
    private val context: Context,
) : BackupRepository {

    private val exporter by lazy { BackupExporter(context, db) }
    private val importer by lazy { BackupImporter(context, db) }

    override suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val recipes = db.recipeDao().exportAll()
        val ingredients = db.ingredientDao().exportAll()
        BackupJsonCodec.buildJson(
            recipes = recipes,
            ingredients = ingredients,
            recipeIngredients = db.recipeDao().exportRecipeIngredients(),
            inventoryItems = db.inventoryItemDao().exportAllItems(),
            transactions = db.inventoryTransactionDao().exportAll(),
            mealPlans = db.mealPlanDao().exportAllPlans(),
            mealRecords = db.mealRecordDao().exportAllRecords(),
            categories = db.categoryDao().exportAll(),
            ingredientTypes = db.ingredientTypeDao().exportAll(),
            tags = db.tagDao().exportAll(),
            recipeTags = db.recipeDao().exportRecipeTags(),
            settings = db.appSettingDao().exportAll(),
            recipeImages = recipes.mapNotNull { r -> r.imageUri?.let { r.id to it } }.toMap(),
            ingredientImages = ingredients.mapNotNull { i -> i.imageUri?.let { i.id to it } }.toMap(),
            imageFieldName = "imageUri",
        )
    }

    override suspend fun importJson(json: String): BackupSummary = withContext(Dispatchers.IO) {
        val root = org.json.JSONObject(json)
        BackupJsonCodec.restoreIntoDatabase(db, root)
    }

    override suspend fun createBackupZip(
        output: OutputStream,
        onProgress: (Int, Int) -> Unit,
    ): BackupZipSummary = exporter.exportTo(output, onProgress)

    override suspend fun previewBackupZip(zip: File): BackupPreview =
        withContext(Dispatchers.IO) {
            try {
                importer.preview(zip)
            } catch (e: java.util.zip.ZipException) {
                throw IllegalArgumentException("不是有效的饭点备份文件", e)
            }
        }

    override suspend fun restoreBackupZip(zip: File): BackupZipSummary =
        withContext(Dispatchers.IO) {
            try {
                importer.restore(zip)
            } catch (e: java.util.zip.ZipException) {
                throw IllegalArgumentException("不是有效的饭点备份文件", e)
            }
        }
}
