package com.skyanchor.mealtime.data.backup

import org.json.JSONObject

/** manifest.json：描述备份容器格式与版本（规格文档 §10/§11） */
internal data class BackupManifest(
    val format: String,
    val backupVersion: Int,
    val appVersion: String,
    val databaseVersion: Int,
    /** ISO 8601 带时区，如 2026-09-08T15:12:00+08:00 */
    val createdAt: String,
    val recipeImageCount: Int,
    val ingredientImageCount: Int,
) {
    val totalImageCount: Int get() = recipeImageCount + ingredientImageCount

    fun toJson(): String = JSONObject().apply {
        put("format", format)
        put("backupVersion", backupVersion)
        put("appVersion", appVersion)
        put("databaseVersion", databaseVersion)
        put("createdAt", createdAt)
        put("files", JSONObject().apply {
            put("data", BackupFormats.ENTRY_DATA)
            put("imageDirectory", "images/")
            put("recipeImageCount", recipeImageCount)
            put("ingredientImageCount", ingredientImageCount)
            put("totalImageCount", totalImageCount)
        })
    }.toString(2)

    companion object {
        /** @throws IllegalArgumentException 格式不符 / 版本不支持 / 字段缺失 */
        fun parse(text: String): BackupManifest {
            val root = runCatching { JSONObject(text) }
                .getOrElse { throw IllegalArgumentException("备份文件格式不正确") }
            val format = root.optString("format")
            require(format == BackupFormats.MANIFEST_FORMAT) { "不是有效的饭点备份文件" }
            val backupVersion = root.optInt("backupVersion", -1)
            require(backupVersion in 1..BackupFormats.BACKUP_VERSION) { "备份版本过高，当前 App 暂不支持，请升级后再试" }
            return BackupManifest(
                format = format,
                backupVersion = backupVersion,
                appVersion = root.optString("appVersion", "unknown"),
                databaseVersion = root.optInt("databaseVersion", 1),
                createdAt = root.optString("createdAt", ""),
                recipeImageCount = root.optJSONObject("files")?.optInt("recipeImageCount", 0) ?: 0,
                ingredientImageCount = root.optJSONObject("files")?.optInt("ingredientImageCount", 0) ?: 0,
            )
        }
    }
}
