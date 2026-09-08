package com.skyanchor.mealtime.domain.repository

import java.io.File
import java.io.OutputStream

/**
 * 本地备份：
 * - 纯 JSON 导出/导入（结构化数据，不含图片文件，适合高级用户）；
 * - 完整 ZIP 备份/恢复（结构化数据 + 图片附件，换机迁移用）。
 * 恢复 = 清空后整包写入（同一事务），ZIP 恢复会把图片写回本机并重写图片路径。
 */
interface BackupRepository {

    /** @return JSON 文本（含 version、exportedAt 与全部业务表） */
    suspend fun exportJson(): String

    /** @return 恢复的各项数量摘要，格式校验失败抛 IllegalArgumentException */
    suspend fun importJson(json: String): BackupSummary

    /**
     * 生成完整备份 ZIP（data.json + images/ + manifest.json + README.txt）写入 [output]。
     * [onProgress] 汇报图片处理进度（processed, total）；本方法负责关闭 [output]。
     */
    suspend fun createBackupZip(
        output: OutputStream,
        onProgress: (processedImages: Int, totalImages: Int) -> Unit = { _, _ -> },
    ): BackupZipSummary

    /** 校验备份 ZIP 并返回恢复预览（不写入数据），非法文件抛 IllegalArgumentException */
    suspend fun previewBackupZip(zip: File): BackupPreview

    /** 从备份 ZIP 完整恢复（覆盖当前数据），失败抛异常且不破坏现有数据 */
    suspend fun restoreBackupZip(zip: File): BackupZipSummary
}

data class BackupSummary(
    val recipes: Int,
    val ingredients: Int,
    val inventoryItems: Int,
    val transactions: Int,
    val mealPlans: Int,
    val mealRecords: Int,
)

/** ZIP 备份/恢复结果摘要（含图片） */
data class BackupZipSummary(
    val recipes: Int,
    val ingredients: Int,
    val images: Int,
    val mealRecords: Int = 0,
    val imageFailures: Int = 0,
)

/** 恢复前预览（规格文档 §17） */
data class BackupPreview(
    /** ISO 8601 备份时间 */
    val createdAt: String,
    val recipes: Int,
    val ingredients: Int,
    val mealRecords: Int,
    val images: Int,
    val zipSizeBytes: Long,
)
