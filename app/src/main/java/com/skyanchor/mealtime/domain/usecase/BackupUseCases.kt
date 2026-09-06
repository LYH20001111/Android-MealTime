package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.domain.repository.BackupRepository
import com.skyanchor.mealtime.domain.repository.BackupSummary

/** 导出全部数据为 JSON 文本（由设置页经 SAF 写入用户选择的位置） */
class ExportDataUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(): String = backupRepository.exportJson()
}

/** 从 JSON 恢复（覆盖现有数据，事务内完成） */
class ImportDataUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(json: String): BackupSummary = backupRepository.importJson(json)
}
