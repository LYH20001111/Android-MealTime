package com.skyanchor.mealtime.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.rememberCoroutineScope

/**
 * 数据管理（规格文档 §3/§33）：数据备份（ZIP 完整备份）、恢复数据（ZIP，预览后确认）、
 * 导出 JSON（仅结构化数据，高级用户）。入口在「我的」页，独立于设置。
 */
@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    viewModel: DataManagementViewModel = viewModel(
        factory = DataManagementViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.backupTo(context, uri)
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.previewRestore(context, uri)
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingJson
        if (uri != null && json != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } != null
                    }.getOrDefault(false)
                }
                viewModel.notifyExportDone(ok)
            }
        }
        pendingJson = null
    }

    val preview = state.preview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRestore() },
            title = { Text("发现备份数据") },
            text = {
                Column {
                    Text("备份日期：${formatBackupDate(preview.createdAt)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("菜谱：${preview.recipes} 道")
                    Text("食材：${preview.ingredients} 项")
                    Text("用餐记录：${preview.mealRecords} 条")
                    Text("图片：${preview.images} 张")
                    Text("备份大小：${formatFileSize(preview.zipSizeBytes)}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "恢复将覆盖当前设备上的全部数据（菜谱、图片、库存、计划、记录、设置）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.danger,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRestore() }) {
                    Text("开始恢复", color = FoodTheme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRestore() }) { Text("取消") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = FoodTheme.colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleLarge,
                color = FoodTheme.colors.textPrimary,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
        ) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            FoodCard {
                Column(
                    modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceLg, vertical = FoodTheme.dimens.spaceSm),
                ) {
                    ActionRow(
                        icon = Icons.Outlined.Backup,
                        title = "数据备份",
                        subtitle = "备份菜谱、图片、食材和用餐记录",
                        enabled = !state.busy,
                    ) {
                        backupLauncher.launch("饭点_backup_${LocalDate.now()}.zip")
                    }
                    ActionRow(
                        icon = Icons.Outlined.Restore,
                        title = "恢复数据",
                        subtitle = "从备份文件恢复到当前设备",
                        enabled = !state.busy,
                    ) {
                        restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = "高级")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceLg, vertical = FoodTheme.dimens.spaceSm),
                ) {
                    ActionRow(
                        icon = Icons.Outlined.Code,
                        title = "导出 JSON",
                        subtitle = "仅导出结构化数据，不含图片",
                        enabled = !state.busy,
                    ) {
                        viewModel.buildJsonExport { json ->
                            if (json != null) {
                                pendingJson = json
                                jsonExportLauncher.launch("fandian-backup.json")
                            }
                        }
                    }
                }
            }

            if (state.busy) {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = FoodTheme.colors.primary,
                    )
                    Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceMd))
                    Text(
                        text = state.progressText ?: "处理中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textSecondary,
                    )
                }
            }

            state.message?.let { message ->
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.startsWith("✓")) FoodTheme.colors.success else FoodTheme.colors.danger,
                )
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = FoodTheme.dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FoodTheme.colors.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = FoodTheme.colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.textTertiary,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = FoodTheme.colors.textTertiary,
        )
    }
}

/** ISO 8601（如 2026-09-08T15:12:00+08:00）→ "2026-09-08 15:12" */
private fun formatBackupDate(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(iso.ifBlank { "未知" })

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
