package com.skyanchor.mealtime.feature.profile

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.SecondaryButton
import com.skyanchor.mealtime.notification.ExpiryNotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置：临期通知（开关 + 提前天数 + 立即测试）、默认值、数据导出/恢复、关于。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
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

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().toString(Charsets.UTF_8)
                        }
                    }.getOrNull()
                }
                if (text != null) {
                    importJsonText = text
                    showImportConfirm = true
                } else {
                    viewModel.notifyImportDone(success = false, summary = null)
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationEnabled(granted)
        if (granted) ExpiryNotificationWorker.schedule(context)
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                importJsonText = null
            },
            title = { Text("恢复备份") },
            text = { Text("恢复会覆盖当前全部数据（菜谱、库存、计划、记录、设置）。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importJsonText?.let { viewModel.import(it) }
                    importJsonText = null
                }) { Text("覆盖恢复", color = FoodTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importJsonText = null
                }) { Text("取消") }
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
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                color = FoodTheme.colors.textPrimary,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
        ) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            SectionTitle(text = "临期通知")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FoodTheme.dimens.spaceLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "每日临期汇总提醒",
                            style = MaterialTheme.typography.bodyLarge,
                            color = FoodTheme.colors.textPrimary,
                        )
                        Text(
                            text = "每天最多提醒一次，首页提醒始终可用",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoodTheme.colors.textTertiary,
                        )
                    }
                    Switch(
                        checked = state.notificationEnabled,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotificationEnabled(checked)
                                if (checked) {
                                    ExpiryNotificationWorker.schedule(context)
                                } else {
                                    ExpiryNotificationWorker.cancel(context)
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = FoodTheme.colors.primary),
                    )
                }
            }
            if (state.notificationEnabled) {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                FoodCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FoodTheme.dimens.spaceLg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "提前提醒天数",
                                style = MaterialTheme.typography.bodyLarge,
                                color = FoodTheme.colors.textPrimary,
                            )
                            Text(
                                text = "到期前多少天开始提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = FoodTheme.colors.textTertiary,
                            )
                        }
                        FoodTextField(
                            value = state.advanceDays,
                            onValueChange = viewModel::setAdvanceDays,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                SecondaryButton(
                    text = "发送测试通知",
                    onClick = { ExpiryNotificationWorker.runOnceForTest(context) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = "默认值")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                ) {
                    SettingNumberRow("默认用餐人数", state.servings, viewModel::setServings)
                    SettingNumberRow("临期阈值（天）", state.nearDays, viewModel::setNearDays)
                    SettingNumberRow("紧急阈值（天）", state.urgentDays, viewModel::setUrgentDays)
                }
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = "数据管理")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceMd),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    Text(
                        text = "导出为 JSON 备份；恢复会覆盖当前全部数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textTertiary,
                        modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceXs),
                    )
                    SecondaryButton(
                        text = "导出数据",
                        onClick = {
                            viewModel.buildExport { json ->
                                pendingJson = json
                                exportLauncher.launch("fandian-backup.json")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = "恢复数据",
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                        modifier = Modifier.fillMaxWidth(),
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
private fun SettingNumberRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        FoodTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.width(72.dp),
        )
    }
}
