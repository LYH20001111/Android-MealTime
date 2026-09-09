package com.skyanchor.mealtime.feature.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dining
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    var renamingCategory by remember { mutableStateOf<Category?>(null) }
    var renamingType by remember { mutableStateOf<IngredientTypeInfo?>(null) }

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

    renamingCategory?.let { pending ->
        RenameDialog(
            title = "重命名分类",
            initialName = pending.name,
            onConfirm = { newName ->
                viewModel.renameCategory(pending.id, newName)
                renamingCategory = null
            },
            onDismiss = { renamingCategory = null },
        )
    }

    renamingType?.let { pending ->
        RenameDialog(
            title = "重命名食材种类",
            initialName = pending.label,
            onConfirm = { newName ->
                viewModel.renameIngredientType(pending.key, newName)
                renamingType = null
            },
            onDismiss = { renamingType = null },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
            // ================= 临期通知 =================
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            SectionTitle(text = "临期通知")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column {
                    SettingItemRow(
                        icon = Icons.Outlined.NotificationsActive,
                        iconTint = FoodTheme.colors.primary,
                        title = "每日临期汇总提醒",
                        subtitle = "每天最多提醒一次，首页提醒始终可用"
                    ) {
                        Switch(
                            checked = state.notificationEnabled,
                            onCheckedChange = { checked ->
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setNotificationEnabled(checked)
                                    if (checked) ExpiryNotificationWorker.schedule(context)
                                    else ExpiryNotificationWorker.cancel(context)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = FoodTheme.colors.primary),
                        )
                    }

                    if (state.notificationEnabled) {
                        SettingItemRow(
                            icon = Icons.Outlined.DateRange,
                            iconTint = FoodTheme.colors.primary,
                            title = "提前提醒天数",
                            subtitle = "到期前多少天开始提醒"
                        ) {
                            Stepper(
                                value = state.advanceDays,
                                onValueChange = viewModel::setAdvanceDays
                            )
                        }

                        Box(modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceLg, vertical = FoodTheme.dimens.spaceMd)) {
                            SecondaryButton(
                                text = "发送测试通知",
                                onClick = { ExpiryNotificationWorker.runOnceForTest(context) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // ================= 默认值 =================
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = "默认值")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(vertical = FoodTheme.dimens.spaceSm),
                ) {
                    SettingDropdownRow(
                        icon = Icons.Outlined.Person,
                        iconTint = FoodTheme.colors.textSecondary, // 假设你有 textSecondary，或者用 primary
                        label = "默认用餐人数",
                        value = state.servings,
                        options = (1..10).map { it.toString() },
                        onChange = viewModel::setServings
                    )
                    SettingDropdownRow(
                        icon = Icons.Outlined.DateRange,
                        iconTint = FoodTheme.colors.primary,
                        label = "临期阈值（天）",
                        value = state.nearDays,
                        options = (1..7).map { it.toString() },
                        onChange = viewModel::setNearDays
                    )
                    SettingDropdownRow(
                        icon = Icons.Outlined.Warning,
                        iconTint = FoodTheme.colors.danger,
                        label = "紧急阈值（天）",
                        value = state.urgentDays,
                        options = (1..3).map { it.toString() },
                        onChange = viewModel::setUrgentDays
                    )
                }
            }

            // ================= 分类管理 =================
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = "分类管理")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    Text(
                        text = "菜谱分类",
                        style = MaterialTheme.typography.titleSmall,
                        color = FoodTheme.colors.textPrimary,
                    )
                    state.categories.forEachIndexed { index, category ->
                        val isFallback = category.name == FALLBACK_CATEGORY_NAME
                        CategoryRow(
                            name = category.name,
                            deletable = !isFallback,
                            renamable = !isFallback,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.categories.lastIndex,
                            onMoveUp = { viewModel.moveCategory(category.id, up = true) },
                            onMoveDown = { viewModel.moveCategory(category.id, up = false) },
                            onRename = { renamingCategory = category },
                            onDelete = { pendingCategoryDelete = category },
                        )
                    }
                    // 新增分类输入框（保持原样即可，也可加入Icon）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FoodTheme.dimens.spaceMd)
                    ) {
                        // 1. 定制带图标和背景的输入框
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp) // 固定高度，与按钮保持协调
                                .background(
                                    color = FoodTheme.colors.primary.copy(alpha = 0.08f), // 浅色背景
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = FoodTheme.colors.primary.copy(alpha = 0.3f), // 细边框
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 占位图标
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = FoodTheme.colors.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // 核心输入区域
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (state.newCategoryName.isEmpty()) {
                                    Text(
                                        text = "新增菜谱分类",
                                        color = FoodTheme.colors.textTertiary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                BasicTextField(
                                    value = state.newCategoryName,
                                    onValueChange = viewModel::setNewCategoryName,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = FoodTheme.colors.textPrimary),
                                    cursorBrush = SolidColor(FoodTheme.colors.primary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 2. 定制高亮的主色调添加按钮
                        Button(
                            onClick = viewModel::addCategory,
                            shape = RoundedCornerShape(50), // 药丸形状的全圆角
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoodTheme.colors.primary, // 品牌主色
                                contentColor = Color.White // 白色文字和图标
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "添加",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    Text(
                        text = "食材分类",
                        style = MaterialTheme.typography.titleSmall,
                        color = FoodTheme.colors.textPrimary,
                    )
                    state.ingredientTypes.forEachIndexed { index, type ->
                        val isBuiltinOther = type.key == IngredientTypes.OTHER
                        CategoryRow(
                            name = type.label,
                            deletable = !isBuiltinOther,
                            renamable = !isBuiltinOther,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.ingredientTypes.lastIndex,
                            onMoveUp = { viewModel.moveIngredientType(type.key, up = true) },
                            onMoveDown = { viewModel.moveIngredientType(type.key, up = false) },
                            onRename = { renamingType = type },
                            onDelete = { pendingTypeDelete = type },
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp), // 使用具体值以保持一致
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = FoodTheme.dimens.spaceSm)
                    ) {
                        // 1. 定制带图标和背景的食材输入框
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp) // 固定高度，与按钮保持协调
                                .background(
                                    color = FoodTheme.colors.primary.copy(alpha = 0.08f), // 浅色背景
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = FoodTheme.colors.primary.copy(alpha = 0.3f), // 细边框
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 食材主题占位图标：选择餐饮图标
                            Icon(
                                imageVector = Icons.Outlined.Kitchen, // 替换为更适合食材的图标
                                contentDescription = null,
                                tint = FoodTheme.colors.textTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            // 核心输入区域
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (state.newTypeName.isEmpty()) {
                                    Text(
                                        text = "新增食材分类", // 更新占位符文本
                                        color = FoodTheme.colors.textTertiary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                BasicTextField(
                                    value = state.newTypeName, // 更新值和回调
                                    onValueChange = viewModel::setNewTypeName,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = FoodTheme.colors.textPrimary),
                                    cursorBrush = SolidColor(FoodTheme.colors.primary),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 2. 定制高亮的主色调添加按钮
                        Button(
                            onClick = viewModel::addIngredientType, // 更新回调
                            shape = RoundedCornerShape(50), // 药丸形状的全圆角
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FoodTheme.colors.primary, // 品牌主色
                                contentColor = Color.White // 白色文字和图标
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(44.dp)
                        ) {
                            // 加号图标
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "添加",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
            Text(
                text = "重命名或调整顺序后，菜谱筛选、食材分组等处同步生效；删除分类后，其中的菜谱/食材会自动归入「其他」",
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
private fun RenameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    val changed = text.trim().isNotEmpty() && text.trim() != initialName
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FoodTextField(
                value = text,
                onValueChange = { text = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = changed) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
// ================= 新增的基础 UI 组件 =================

@Composable
private fun SettingItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.spaceLg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = FoodTheme.colors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textTertiary,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        trailingContent()
    }
}

@Composable
private fun SettingDropdownRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.spaceLg, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(FoodTheme.colors.textTertiary.copy(alpha = 0.1f))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = FoodTheme.colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Stepper(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val intValue = value.toIntOrNull() ?: 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FoodTheme.colors.textTertiary.copy(alpha = 0.1f))
    ) {
        IconButton(
            onClick = { onValueChange((intValue - 1).coerceAtLeast(0).toString()) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "减少", tint = FoodTheme.colors.textSecondary)
        }

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textPrimary,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = { onValueChange((intValue + 1).toString()) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "增加", tint = FoodTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun CategoryRow(
    name: String,
    deletable: Boolean,
    renamable: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧的拖拽指示图标（装饰用，实际排序由右侧上下箭头控制）
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = null,
            tint = FoodTheme.colors.textTertiary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )

        // 操作区按钮，使用了更紧凑的间距和轻量级图标
        if (canMoveUp) {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移", tint = FoodTheme.colors.textSecondary)
            }
        }
        if (canMoveDown) {
            IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移", tint = FoodTheme.colors.textSecondary)
            }
        }
        if (renamable) {
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "重命名",
                    tint = FoodTheme.colors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (deletable) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = FoodTheme.colors.danger, // 标红提示危险操作
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Text(
                text = "系统",
                style = MaterialTheme.typography.labelSmall,
                color = FoodTheme.colors.textTertiary,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}