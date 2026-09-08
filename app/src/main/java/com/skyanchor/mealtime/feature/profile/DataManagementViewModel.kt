package com.skyanchor.mealtime.feature.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.domain.repository.BackupPreview
import com.skyanchor.mealtime.domain.usecase.BackupDataUseCase
import com.skyanchor.mealtime.domain.usecase.ExportJsonUseCase
import com.skyanchor.mealtime.domain.usecase.RestoreDataUseCase
import com.skyanchor.mealtime.domain.usecase.ValidateBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DataManagementUiState(
    /** 备份/恢复/校验进行中 */
    val busy: Boolean = false,
    val progressText: String? = null,
    /** 结果消息；✓ 开头为成功，✗ 开头为失败 */
    val message: String? = null,
    /** 非 null 时显示恢复确认预览 */
    val preview: BackupPreview? = null,
)

/** 数据管理：完整备份（ZIP）、恢复（ZIP，先预览后确认）、导出 JSON（规格文档 §3） */
class DataManagementViewModel(
    private val backupData: BackupDataUseCase,
    private val validateBackup: ValidateBackupUseCase,
    private val restoreData: RestoreDataUseCase,
    private val exportJson: ExportJsonUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    /** 已复制到缓存、等待用户确认恢复的备份文件 */
    private var pendingZip: File? = null

    /** 生成完整备份 ZIP 并写入 SAF URI */
    fun backupTo(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null, progressText = "正在备份数据…") }
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法写入所选位置")
                backupData(output) { processed, total ->
                    if (total > 0) {
                        _uiState.update { it.copy(progressText = "正在备份图片 $processed/$total") }
                    }
                }
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { s ->
                        state.copy(
                            busy = false, progressText = null,
                            message = "✓ 备份完成：菜谱 ${s.recipes}、食材 ${s.ingredients}、图片 ${s.images}" +
                                if (s.imageFailures > 0) "（${s.imageFailures} 张图片未能备份）" else "",
                        )
                    },
                    onFailure = { e ->
                        state.copy(busy = false, progressText = null, message = "✗ 备份失败：${e.message ?: "请重试"}")
                    },
                )
            }
        }
    }

    /** 读取所选备份文件并校验，成功后进入恢复预览 */
    fun previewRestore(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null, progressText = "正在读取备份…") }
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, PENDING_ZIP_NAME)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取所选文件")
                    pendingZip = temp
                    validateBackup(temp)
                }
            }
            if (preview.isFailure) discardPending()
            _uiState.update { state ->
                preview.fold(
                    onSuccess = { p -> state.copy(busy = false, progressText = null, preview = p) },
                    onFailure = { e ->
                        state.copy(busy = false, progressText = null, message = "✗ ${e.message ?: "备份文件读取失败"}")
                    },
                )
            }
        }
    }

    fun cancelRestore() {
        discardPending()
        _uiState.update { it.copy(preview = null) }
    }

    fun confirmRestore() {
        val zip = pendingZip
        if (zip == null) {
            _uiState.update { it.copy(preview = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, preview = null, progressText = "正在恢复数据…") }
            val result = runCatching { restoreData(zip) }
            result.onSuccess { discardPending() }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { s ->
                        state.copy(
                            busy = false, progressText = null,
                            message = "✓ 恢复完成：${s.recipes} 道菜谱、${s.ingredients} 项食材、" +
                                "${s.images} 张图片、${s.mealRecords} 条用餐记录",
                        )
                    },
                    onFailure = { e ->
                        state.copy(busy = false, progressText = null, message = "✗ 恢复失败：${e.message ?: "备份文件不完整"}")
                    },
                )
            }
        }
    }

    fun buildJsonExport(onReady: (String?) -> Unit) {
        viewModelScope.launch {
            val json = runCatching { exportJson() }.getOrNull()
            _uiState.update {
                it.copy(message = if (json != null) null else "✗ 备份生成失败，请重试")
            }
            onReady(json)
        }
    }

    fun notifyExportDone(success: Boolean) {
        _uiState.update {
            it.copy(message = if (success) "✓ JSON 已导出（不含图片）" else "✗ 导出失败，请重试")
        }
    }

    private fun discardPending() {
        pendingZip?.delete()
        pendingZip = null
    }

    companion object {
        private const val PENDING_ZIP_NAME = "restore_pending.zip"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DataManagementViewModel(
                    backupData = BackupDataUseCase(container.backupRepository),
                    validateBackup = ValidateBackupUseCase(container.backupRepository),
                    restoreData = RestoreDataUseCase(container.backupRepository),
                    exportJson = ExportJsonUseCase(container.backupRepository),
                )
            }
        }
    }
}
