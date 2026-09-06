package com.skyanchor.mealtime.domain.repository

/**
 * 本地备份：全量导出为 JSON 文本 / 从 JSON 恢复（恢复 = 清空后整包写入，同一事务）。
 */
interface BackupRepository {

    /** @return JSON 文本（含 version、exportedAt 与全部业务表） */
    suspend fun exportJson(): String

    /** @return 恢复的各项数量摘要，格式校验失败抛 IllegalArgumentException */
    suspend fun importJson(json: String): BackupSummary
}

data class BackupSummary(
    val recipes: Int,
    val ingredients: Int,
    val inventoryItems: Int,
    val transactions: Int,
    val mealPlans: Int,
    val mealRecords: Int,
)
