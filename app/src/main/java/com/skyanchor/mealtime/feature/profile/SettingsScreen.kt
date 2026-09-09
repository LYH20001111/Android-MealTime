package com.skyanchor.mealtime.feature.profile

import android.Manifest
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.FALLBACK_CATEGORY_NAME
import com.skyanchor.mealtime.core.model.IngredientTypeInfo
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.SecondaryButton
import com.skyanchor.mealtime.notification.ExpiryNotificationWorker

/**
 * 设置：临期通知（开关 + 提前天数 + 立即测试）、默认值、分类管理、关于。
 * 数据备份/恢复已独立为「我的 → 数据管理」（DataManagementScreen）。
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
    var pendingCategoryDelete by remember { mutableStateOf<Category?>(null) }
    var pendingTypeDelete by remember { mutableStateOf<IngredientTypeInfo?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationEnabled(granted)
        if (granted) ExpiryNotificationWorker.schedule(context)
    }

    if (pendingCategoryDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingCategoryDelete = null },
            title = { Text("删除分类") },
            text = { Text("确定删除分类「${pendingCategoryDelete?.name}」吗？其中的菜谱会自动归入「$FALLBACK_CATEGORY_NAME」。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingCategoryDelete?.let { viewModel.deleteCategory(it.id) }
                    pendingCategoryDelete = null
                }) { Text("删除", color = FoodTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingCategoryDelete = null }) { Text("取消") }
            },
        )
    }

    if (pendingTypeDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingTypeDelete = null },
            title = { Text("删除种类") },
            text = { Text("确定删除食材种类「${pendingTypeDelete?.label}」吗？其中的食材与菜谱配料会自动归入「其他」。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingTypeDelete?.let { viewModel.deleteIngredientType(it.key) }
                    pendingTypeDelete = null
                }) { Text("删除", color = FoodTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTypeDelete = null }) { Text("取消") }
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
            SectionTitle(text = "分类管理")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    Text(
                        text = "菜谱分类",
                        style = MaterialTheme.typography.titleSmall,
                        color = FoodTheme.colors.textPrimary,
                    )
                    state.categories.forEachIndexed { index, category ->
                        CategoryRow(
                            name = category.name,
                            deletable = category.name != FALLBACK_CATEGORY_NAME,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.categories.lastIndex,
                            onMoveUp = { viewModel.moveCategory(category.id, up = true) },
                            onMoveDown = { viewModel.moveCategory(category.id, up = false) },
                            onDelete = { pendingCategoryDelete = category },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        FoodTextField(
                            value = state.newCategoryName,
                            onValueChange = viewModel::setNewCategoryName,
                            placeholder = "新增菜谱分类",
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = "添加", onClick = viewModel::addCategory)
                    }
                }
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    Text(
                        text = "食材分类",
                        style = MaterialTheme.typography.titleSmall,
                        color = FoodTheme.colors.textPrimary,
                    )
                    state.ingredientTypes.forEachIndexed { index, type ->
                        CategoryRow(
                            name = type.label,
                            deletable = type.key != IngredientTypes.OTHER,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.ingredientTypes.lastIndex,
                            onMoveUp = { viewModel.moveIngredientType(type.key, up = true) },
                            onMoveDown = { viewModel.moveIngredientType(type.key, up = false) },
                            onDelete = { pendingTypeDelete = type },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        FoodTextField(
                            value = state.newTypeName,
                            onValueChange = viewModel::setNewTypeName,
                            placeholder = "新增食材分类",
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(text = "添加", onClick = viewModel::addIngredientType)
                    }
                }
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
            Text(
                text = "通过 ↑↓ 调整分类顺序，同步应用于菜谱筛选、食材分组等处的显示顺序；删除分类后，其中的菜谱/食材会自动归入「其他」",
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.textTertiary,
                modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceXs),
            )

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
private fun CategoryRow(
    name: String,
    deletable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "上移$name",
                tint = if (canMoveUp) FoodTheme.colors.textTertiary else FoodTheme.colors.textTertiary.copy(alpha = 0.3f),
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "下移$name",
                tint = if (canMoveDown) FoodTheme.colors.textTertiary else FoodTheme.colors.textTertiary.copy(alpha = 0.3f),
            )
        }
        if (deletable) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除$name",
                    tint = FoodTheme.colors.textTertiary,
                )
            }
        } else {
            Text(
                text = "不可删除",
                style = MaterialTheme.typography.labelSmall,
                color = FoodTheme.colors.textTertiary,
            )
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
