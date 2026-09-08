package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.domain.repository.BackupRepository
import com.skyanchor.mealtime.domain.repository.BackupPreview
import com.skyanchor.mealtime.domain.repository.BackupZipSummary
import java.io.File
import java.io.OutputStream

/** 完整 ZIP 备份：数据 + 图片 + manifest 写入输出流（规格文档 §25） */
class BackupDataUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(
        output: OutputStream,
        onProgress: (processedImages: Int, totalImages: Int) -> Unit = { _, _ -> },
    ): BackupZipSummary = backupRepository.createBackupZip(output, onProgress)
}

/** 恢复前校验备份 ZIP 并生成预览（ValidateBackupUseCase） */
class ValidateBackupUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(zip: File): BackupPreview = backupRepository.previewBackupZip(zip)
}

/** 从备份 ZIP 完整恢复（覆盖现有数据） */
class RestoreDataUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(zip: File): BackupZipSummary = backupRepository.restoreBackupZip(zip)
}

/** 导出全部数据为纯 JSON 文本（不含图片文件，仅适合高级用户；由界面经 SAF 写入用户选择的位置） */
class ExportJsonUseCase(
    private val backupRepository: BackupRepository,
) {
    suspend operator fun invoke(): String = backupRepository.exportJson()
}
