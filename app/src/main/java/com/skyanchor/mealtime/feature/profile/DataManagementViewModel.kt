package com.skyanchor.mealtime.feature.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.R
import com.skyanchor.mealtime.core.common.AppStrings
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
            _uiState.update { it.copy(busy = true, message = null, progressText = AppStrings.get(R.string.data_mgmt_backing_up)) }
            val result = runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error(AppStrings.get(R.string.data_mgmt_error_write_location))
                backupData(output) { processed, total ->
                    if (total > 0) {
                        _uiState.update { it.copy(progressText = AppStrings.get(R.string.data_mgmt_backing_up_images, processed, total)) }
                    }
                }
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { s ->
                        state.copy(
                            busy = false, progressText = null,
                            message = AppStrings.get(R.string.data_mgmt_backup_done, s.recipes, s.ingredients, s.images) +
                                if (s.imageFailures > 0) AppStrings.get(R.string.data_mgmt_backup_image_failures, s.imageFailures) else "",
                        )
                    },
                    onFailure = { e ->
                        state.copy(busy = false, progressText = null, message = AppStrings.get(R.string.data_mgmt_backup_failed, e.message ?: AppStrings.get(R.string.common_retry)))
                    },
                )
            }
        }
    }

    /** 读取所选备份文件并校验，成功后进入恢复预览 */
    fun previewRestore(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null, progressText = AppStrings.get(R.string.data_mgmt_reading_backup)) }
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, PENDING_ZIP_NAME)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    } ?: error(AppStrings.get(R.string.data_mgmt_error_read_file))
                    pendingZip = temp
                    validateBackup(temp)
                }
            }
            if (preview.isFailure) discardPending()
            _uiState.update { state ->
                preview.fold(
                    onSuccess = { p -> state.copy(busy = false, progressText = null, preview = p) },
                    onFailure = { e ->
                        state.copy(busy = false, progressText = null, message = AppStrings.get(R.string.data_mgmt_failed_prefix, e.message ?: AppStrings.get(R.string.data_mgmt_error_read_backup)))
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
            _uiState.update { it.copy(busy = true, preview = null, progressText = AppStrings.get(R.string.data_mgmt_restoring)) }
            val result = runCatching { restoreData(zip) }
            result.onSuccess { discardPending() }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { s ->
                        state.copy(
                            busy = false, progressText = null,
                            message = AppStrings.get(
                                R.string.data_mgmt_restore_done,
                                s.recipes, s.ingredients, s.images, s.mealRecords,
                            ),
                        )
                    },
                    onFailure = { e ->
                        state.copy(busy = false, progressText = null, message = AppStrings.get(R.string.data_mgmt_restore_failed, e.message ?: AppStrings.get(R.string.data_mgmt_error_backup_incomplete)))
                    },
                )
            }
        }
    }

    fun buildJsonExport(onReady: (String?) -> Unit) {
        viewModelScope.launch {
            val json = runCatching { exportJson() }.getOrNull()
            _uiState.update {
                it.copy(message = if (json != null) null else AppStrings.get(R.string.data_mgmt_json_build_failed))
            }
            onReady(json)
        }
    }

    fun notifyExportDone(success: Boolean) {
        _uiState.update {
            it.copy(message = if (success) AppStrings.get(R.string.data_mgmt_json_exported) else AppStrings.get(R.string.data_mgmt_export_failed))
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
