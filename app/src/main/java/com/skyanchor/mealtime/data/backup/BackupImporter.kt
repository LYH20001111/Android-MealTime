package com.skyanchor.mealtime.data.backup

import android.content.Context
import com.skyanchor.mealtime.data.backup.BackupFormats.ENTRY_DATA
import com.skyanchor.mealtime.data.backup.BackupFormats.ENTRY_MANIFEST
import com.skyanchor.mealtime.data.backup.BackupFormats.LOCAL_INGREDIENT_IMAGES
import com.skyanchor.mealtime.data.backup.BackupFormats.LOCAL_RECIPE_IMAGES
import com.skyanchor.mealtime.data.backup.BackupFormats.ZIP_INGREDIENT_IMAGES
import com.skyanchor.mealtime.data.backup.BackupFormats.ZIP_RECIPE_IMAGES
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.domain.repository.BackupPreview
import com.skyanchor.mealtime.domain.repository.BackupZipSummary
import com.skyanchor.mealtime.domain.repository.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/**
 * BackupImporter（规格文档 §27/§30/§31）：ZIP → manifest 校验 → data.json 校验 → 图片 → Room。
 * 恢复采用「临时目录 → 完整验证 → Room 事务 → 正式图片目录」，失败不破坏当前有效数据。
 */
internal class BackupImporter(
    private val context: Context,
    private val db: MealTimeDatabase,
) {

    /** 校验并统计备份内容（恢复预览，不写入任何数据） */
    fun preview(zip: File): BackupPreview {
        ZipFile(zip).use { zf ->
            val (manifest, root) = readAndValidate(zf)
            return BackupPreview(
                createdAt = manifest.createdAt,
                recipes = root.getJSONArray("recipes").length(),
                ingredients = root.getJSONArray("ingredients").length(),
                mealRecords = root.getJSONArray("mealRecords").length(),
                images = countImageEntries(zf),
                zipSizeBytes = zip.length(),
            )
        }
    }

    /** 完整恢复：覆盖当前全部数据并写回图片；失败抛异常且不破坏现有数据 */
    suspend fun restore(zip: File): BackupZipSummary = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "restore_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            ZipFile(zip).use { zf ->
                val (manifest, root) = readAndValidate(zf)
                require(manifest.databaseVersion <= db.openHelper.readableDatabase.version) {
                    "备份的数据库版本过高，当前 App 暂不支持，请升级后再试"
                }

                // 1. 图片恢复到临时目录（限制可恢复路径，防目录穿越）
                val extracted = extractImages(zf, tempDir)

                // 2. 临时目录 → 正式图片目录，生成 imagePath → 本机 file:// URI 的映射
                val imageResolver = buildResolver(extracted)

                // 3. Room 事务内整包覆盖恢复（通知权限等设备绑定设置不随备份恢复）
                val summary = BackupJsonCodec.restoreIntoDatabase(
                    db = db,
                    root = root,
                    skipSettingKeys = setOf(SettingsKeys.NOTIFICATION_ENABLED),
                    resolveImage = imageResolver,
                )

                // 4. 清理不再被引用的旧图片（尽力而为）
                cleanupOrphanImages()

                BackupZipSummary(
                    recipes = summary.recipes,
                    ingredients = summary.ingredients,
                    images = extracted.size,
                    mealRecords = summary.mealRecords,
                )
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** 读取 manifest 与 data.json 并做格式/版本校验 */
    private fun readAndValidate(zf: ZipFile): Pair<BackupManifest, JSONObject> {
        val manifestEntry = zf.getEntry(ENTRY_MANIFEST)
            ?: throw IllegalArgumentException("不是有效的饭点备份文件（缺少 manifest.json）")
        val manifest = BackupManifest.parse(zf.getInputStream(manifestEntry).readBytes().toString(Charsets.UTF_8))

        val dataEntry = zf.getEntry(ENTRY_DATA)
            ?: throw IllegalArgumentException("备份文件不完整（缺少 data.json）")
        val root = runCatching {
            JSONObject(zf.getInputStream(dataEntry).readBytes().toString(Charsets.UTF_8))
        }.getOrElse { throw IllegalArgumentException("备份文件格式不正确") }
        BackupJsonCodec.validateRoot(root)
        return manifest to root
    }

    private fun countImageEntries(zf: ZipFile): Int =
        zf.entries().asSequence().count { !it.isDirectory && isAllowedImageEntry(it.name) }

    /** 只接受 images/recipes 与 images/ingredients 目录下的单层文件名，拒绝绝对路径与“..”穿越 */
    private fun isAllowedImageEntry(name: String): Boolean {
        if (name.startsWith("/") || name.contains("..")) return false
        val prefix = when {
            name.startsWith(ZIP_RECIPE_IMAGES) -> ZIP_RECIPE_IMAGES
            name.startsWith(ZIP_INGREDIENT_IMAGES) -> ZIP_INGREDIENT_IMAGES
            else -> return false
        }
        val rest = name.removePrefix(prefix)
        return rest.isNotEmpty() && !rest.contains('/')
    }

    private fun extractImages(zf: ZipFile, tempDir: File): Map<String, File> {
        val extracted = mutableMapOf<String, File>()
        val rootPath = tempDir.canonicalPath + File.separator
        zf.entries().asSequence().filter { !it.isDirectory && isAllowedImageEntry(it.name) }.forEach { entry ->
            runCatching {
                val target = File(tempDir, entry.name)
                // 双重防护：规范化后必须仍在临时目录内
                check(target.canonicalPath.startsWith(rootPath)) { "非法路径: ${entry.name}" }
                target.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                extracted[entry.name] = target
            }
        }
        return extracted
    }

    /** ZIP 内图片路径 → 本机正式目录（recipe_covers / ingredient_covers），返回 file:// URI 映射 */
    private fun buildResolver(extracted: Map<String, File>): (String) -> String? {
        val mapping = extracted.mapNotNull { (entryName, tempFile) ->
            val localDir = when {
                entryName.startsWith(ZIP_RECIPE_IMAGES) -> LOCAL_RECIPE_IMAGES
                else -> LOCAL_INGREDIENT_IMAGES
            }
            val target = File(File(context.filesDir, localDir), tempFile.name)
            val copied = runCatching {
                target.parentFile?.mkdirs()
                tempFile.copyTo(target, overwrite = true)
            }.isSuccess
            if (copied) entryName to "file://${target.absolutePath}" else null
        }.toMap()
        return { path -> mapping[path] }
    }

    /** 删除 recipe_covers / ingredient_covers 中不再被任何菜谱/食材引用的文件 */
    private suspend fun cleanupOrphanImages() {
        runCatching {
            val referenced = buildSet {
                db.recipeDao().exportAll().forEach { recipe ->
                    recipe.imageUri?.removePrefix("file://")?.let { add(File(it).absolutePath) }
                }
                db.ingredientDao().exportAll().forEach { ingredient ->
                    ingredient.imageUri?.removePrefix("file://")?.let { add(File(it).absolutePath) }
                }
            }
            listOf(LOCAL_RECIPE_IMAGES, LOCAL_INGREDIENT_IMAGES).forEach { dirName ->
                val dir = File(context.filesDir, dirName)
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.absolutePath !in referenced) file.delete()
                }
            }
        }
    }
}
