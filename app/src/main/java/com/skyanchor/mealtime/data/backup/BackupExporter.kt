package com.skyanchor.mealtime.data.backup

import android.content.Context
import com.skyanchor.mealtime.core.common.ImageCompressor
import com.skyanchor.mealtime.data.backup.BackupFormats.ENTRY_DATA
import com.skyanchor.mealtime.data.backup.BackupFormats.ENTRY_MANIFEST
import com.skyanchor.mealtime.data.backup.BackupFormats.ENTRY_README
import com.skyanchor.mealtime.data.backup.BackupFormats.ZIP_INGREDIENT_IMAGES
import com.skyanchor.mealtime.data.backup.BackupFormats.ZIP_RECIPE_IMAGES
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.domain.repository.BackupZipSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * BackupExporter（规格文档 §26）：Room → data.json，本机图片 → ZIP/images，manifest → ZIP。
 * 只负责生成完整备份 ZIP（manifest.json + data.json + images/ + README.txt），不处理 UI。
 */
internal class BackupExporter(
    private val context: Context,
    private val db: MealTimeDatabase,
) {

    /** 生成完整备份写入 [output]（本方法负责关闭流）；单张图片异常不中断整体备份。 */
    suspend fun exportTo(
        output: OutputStream,
        onProgress: (processedImages: Int, totalImages: Int) -> Unit = { _, _ -> },
    ): BackupZipSummary = withContext(Dispatchers.IO) {
        // 1. 查询 Room 全部业务数据
        val recipes = db.recipeDao().exportAll()
        val ingredients = db.ingredientDao().exportAll()
        val recipeIngredients = db.recipeDao().exportRecipeIngredients()
        val recipeTags = db.recipeDao().exportRecipeTags()
        val inventoryItems = db.inventoryItemDao().exportAllItems()
        val transactions = db.inventoryTransactionDao().exportAll()
        val mealPlans = db.mealPlanDao().exportAllPlans()
        val mealRecords = db.mealRecordDao().exportAllRecords()
        val categories = db.categoryDao().exportAll()
        val ingredientTypes = db.ingredientTypeDao().exportAll()
        val tags = db.tagDao().exportAll()
        val settings = db.appSettingDao().exportAll()

        // 2. 需要备份的图片（仅 App 私有目录内的文件）
        data class ImageJob(val zipPrefix: String, val source: File, val ownerId: Long, val isRecipe: Boolean)

        val recipeJobs = recipes.mapNotNull { recipe ->
            privateImageFile(recipe.imageUri)?.let { ImageJob(ZIP_RECIPE_IMAGES, it, recipe.id, isRecipe = true) }
        }
        val ingredientJobs = ingredients.mapNotNull { ingredient ->
            privateImageFile(ingredient.imageUri)?.let { ImageJob(ZIP_INGREDIENT_IMAGES, it, ingredient.id, isRecipe = false) }
        }
        val jobs = recipeJobs + ingredientJobs
        val totalImages = jobs.size

        var recipeImageCount = 0
        var ingredientImageCount = 0
        var imageFailures = 0
        val recipePaths = mutableMapOf<Long, String>()
        val ingredientPaths = mutableMapOf<Long, String>()
        val usedNames = mutableSetOf<String>()

        ZipOutputStream(BufferedOutputStream(output, BUFFER_SIZE)).use { zip ->
            // 3. 复制/压缩图片（压缩失败回退原样复制，再失败则跳过该图并计数）
            jobs.forEachIndexed { index, job ->
                val entryName = runCatching {
                    val bytes = ImageCompressor.compressToWebpBytes(job.source)
                    val ext = if (bytes != null) ".webp" else job.source.extension.ifBlank { "img" }
                    val base = job.source.nameWithoutExtension.ifBlank { "img_${job.ownerId}" }
                    val name = uniqueName(job.zipPrefix, "$base$ext", usedNames)
                    zip.putNextEntry(ZipEntry(name))
                    if (bytes != null) {
                        zip.write(bytes)
                    } else {
                        job.source.inputStream().use { it.copyTo(zip) }
                    }
                    zip.closeEntry()
                    name
                }.getOrNull()

                if (entryName != null) {
                    if (job.isRecipe) {
                        recipePaths[job.ownerId] = entryName
                        recipeImageCount++
                    } else {
                        ingredientPaths[job.ownerId] = entryName
                        ingredientImageCount++
                    }
                } else {
                    imageFailures++
                }
                onProgress(index + 1, totalImages)
            }

            // 4. data.json（只记录成功写入 ZIP 的图片相对路径，不保存旧设备 URI）
            val dataJson = BackupJsonCodec.buildJson(
                recipes = recipes,
                ingredients = ingredients,
                recipeIngredients = recipeIngredients,
                inventoryItems = inventoryItems,
                transactions = transactions,
                mealPlans = mealPlans,
                mealRecords = mealRecords,
                categories = categories,
                ingredientTypes = ingredientTypes,
                tags = tags,
                recipeTags = recipeTags,
                settings = settings,
                recipeImages = recipePaths,
                ingredientImages = ingredientPaths,
                imageFieldName = "imagePath",
            )
            zip.putNextEntry(ZipEntry(ENTRY_DATA))
            zip.write(dataJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 5. manifest.json（备份容器格式与版本）
            val manifest = BackupManifest(
                format = BackupFormats.MANIFEST_FORMAT,
                backupVersion = BackupFormats.BACKUP_VERSION,
                appVersion = appVersion(),
                databaseVersion = db.openHelper.readableDatabase.version,
                createdAt = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                recipeImageCount = recipeImageCount,
                ingredientImageCount = ingredientImageCount,
            )
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 6. README.txt
            zip.putNextEntry(ZipEntry(ENTRY_README))
            zip.write(readmeText(manifest.createdAt).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        BackupZipSummary(
            recipes = recipes.size,
            ingredients = ingredients.size,
            images = recipeImageCount + ingredientImageCount,
            imageFailures = imageFailures,
            mealRecords = mealRecords.size,
        )
    }

    /** 仅接受 App 私有目录内真实存在的 file:// 图片，避免读取任意路径 */
    private fun privateImageFile(imageUri: String?): File? {
        if (imageUri.isNullOrBlank() || !imageUri.startsWith("file://")) return null
        return runCatching {
            // 与 ImageStorage 的生成约定（"file://" + absolutePath）保持一致，避免 Uri 解析差异
            val file = File(imageUri.removePrefix("file://"))
            val filesRoot = context.filesDir.canonicalFile
            val canonical = file.canonicalFile
            if (canonical.path.startsWith(filesRoot.path + File.separator) && canonical.isFile) canonical else null
        }.getOrNull()
    }

    private fun uniqueName(prefix: String, desired: String, used: MutableSet<String>): String {
        var candidate = desired
        var n = 1
        while (!used.add(prefix + candidate)) {
            val dot = desired.lastIndexOf('.')
            candidate = if (dot > 0) "${desired.substring(0, dot)}_${n}${desired.substring(dot)}" else "${desired}_$n"
            n++
        }
        return prefix + candidate
    }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    private fun readmeText(createdAt: String): String = """
        饭点 完整备份文件
        生成时间：$createdAt
        内容：data.json（结构化数据）、images/（图片附件）、manifest.json（版本信息）。
        恢复方式：打开 App「我的 → 数据管理 → 恢复数据」，选择本文件。
        本文件由应用自动生成，请勿手工编辑，否则可能无法恢复。
    """.trimIndent() + "\n"

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
