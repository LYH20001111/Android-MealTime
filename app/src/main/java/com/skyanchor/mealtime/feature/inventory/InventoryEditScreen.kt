package com.skyanchor.mealtime.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.copyImageToPrivate
import com.skyanchor.mealtime.core.model.QuantityLevel
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.PrimaryButton
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.TagChip
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 常用单位（规范文档 §15），用户已有自定义单位时置顶补充 */
private val CommonUnits = listOf("个", "克", "千克", "毫升", "升", "包", "瓶", "罐", "盒", "根", "颗", "把", "份")

/**
 * 新增/编辑库存（规范文档：新增食材页面规范_库存状态优化版）。
 * 先回答"有没有"（库存状态选择卡），再决定"有多少、何时过期、放哪里"；
 * 无库存时隐藏库存详情仅保留备注，页面随之变短。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InventoryEditScreen(
    itemId: Long,
    onDone: () -> Unit,
    viewModel: InventoryEditViewModel = viewModel(
        key = "inventory_edit_$itemId",
        factory = InventoryEditViewModel.factory(
            container = rememberAppContainer(),
            itemId = itemId.takeIf { it > 0 },
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val stored = copyImageToPrivate(context, uri, "ingredient_covers")
                if (stored != null) viewModel.setImageUri(stored)
            }
        }
    }

    Scaffold(
        containerColor = FoodTheme.colors.background,
        bottomBar = {
            if (!state.isLoading) {
                PrimaryButton(
                    text = if (state.isSaving) "保存中…" else "保存食材",
                    onClick = { viewModel.save(onSaved = { onDone() }) },
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = FoodTheme.dimens.pageHorizontalPadding,
                            vertical = FoodTheme.dimens.spaceMd,
                        ),
                )
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = FoodTheme.colors.primary)
            }
            return@Scaffold
        }

        val unitOptions = remember(state.unit) {
            if (state.unit.isNotEmpty() && state.unit !in CommonUnits) {
                listOf(state.unit) + CommonUnits
            } else {
                CommonUnits
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = FoodTheme.dimens.pageHorizontalPadding,
                end = FoodTheme.dimens.pageHorizontalPadding,
                bottom = innerPadding.calculateBottomPadding(),
            ),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().offset(x = (-20).dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = FoodTheme.colors.textPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
                    Text(
                        text = if (state.isNew) "新增食材" else "编辑食材",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = FoodTheme.colors.textPrimary,
                    )
                }
            }

            // 轻量引导 Banner（仅新增时）
            if (state.isNew) {
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    GuideBanner()
                }
            }

            // ① 基本信息 · 必填：名称 / 图片 / 类型
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                SectionHeader(title = "基本信息", badge = "必填 3 项")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                FormLabel("食材名称", true)
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                FoodTextField(
                    value = state.ingredientName,
                    onValueChange = viewModel::setIngredientName,
                    placeholder = "例如：番茄",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.nameError) ErrorText("请填写食材名称")
            }
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                FormLabel("食材图片", true)
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                IngredientImagePicker(
                    imageUri = state.imageUri,
                    onPick = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClear = { viewModel.setImageUri(null) },
                )
                if (state.imageError) ErrorText("请添加食材图片")
            }
            // 类型属于基本信息，新增时始终可选；编辑保留原食材关联不在此变更
            if (state.isNew) {
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    FormLabel("类型", true)
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        state.ingredientTypes.forEach { type ->
                            TagChip(
                                text = type.label,
                                selected = state.ingredientType == type.key,
                                onClick = { viewModel.setIngredientType(type.key) },
                            )
                        }
                    }
                }
            }

            // ② 库存状态 · 必填：有库存 / 无库存
            item {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                SectionHeader(title = "库存状态")
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    StockChoiceCard(
                        title = "有库存",
                        subtitle = "可记录数量、保质期等信息",
                        selected = !state.isEmptyStock,
                        onClick = { viewModel.requestEmptyStock(false) },
                        modifier = Modifier.weight(1f),
                    )
                    StockChoiceCard(
                        title = "无库存",
                        subtitle = "不需要填写库存相关信息",
                        selected = state.isEmptyStock,
                        onClick = { viewModel.requestEmptyStock(true) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.isEmptyStock) {
                // 无库存：仅保留备注，页面明显变短（规范文档 §7/§21）
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    Text(
                        text = "当前没有这项食材",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textTertiary,
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    SectionHeader(title = "其他信息 · 可选")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                    FormLabel("备注")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FoodTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = "例如：下次买一瓶",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                // ③ 库存详情：数量 / 单位 / 数量级别
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    SectionHeader(title = "库存详情")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            FormLabel("数量")
                            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                            FoodTextField(
                                value = state.quantityText,
                                onValueChange = viewModel::setQuantity,
                                placeholder = "如：4",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            FormLabel("单位")
                            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                            UnitDropdown(
                                value = state.unit,
                                options = unitOptions,
                                onValueChange = viewModel::setUnit,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    FormLabel("数量级别（不确定具体数量时可选）")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                        QuantityLevel.entries.forEach { level ->
                            TagChip(
                                text = level.chineseLabel,
                                selected = state.quantityLevel == level,
                                onClick = { viewModel.selectQuantityLevel(level) },
                            )
                        }
                    }
                }

                // ④ 保质期信息：过期 / 购买 / 生产日期
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    SectionTitle(text = "保质期")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                    DateField(
                        label = "保质期",
                        value = state.expireDate,
                        highlight = true,
                        onChange = viewModel::setExpireDate,
                    )
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    SectionTitle(text = "购买日期")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    DateField(
                        label = "购买日期",
                        value = state.purchaseDate,
                        highlight = false,
                        onChange = viewModel::setPurchaseDate,
                    )
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                    SectionTitle(text = "生产日期")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    DateField(
                        label = "生产日期",
                        value = state.productionDate,
                        highlight = false,
                        onChange = viewModel::setProductionDate,
                    )
                }

                // ⑤ 存放与备注
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FormLabel("存放位置")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FoodTextField(
                        value = state.location,
                        onValueChange = viewModel::setLocation,
                        placeholder = "冷藏室 / 冷冻层 / 橱柜…",
                        leadingIcon = Icons.Outlined.LocationOn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                    FormLabel("备注")
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    FoodTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = "品牌、开封日期等",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                state.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.danger,
                    )
                }
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
            }
        }
    }

    // 切换为无库存确认（规范文档 §27）
    if (state.pendingEmptyStock) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEmptyStock,
            title = { Text("切换为无库存？") },
            text = { Text("当前库存数量、日期和存放位置将不再作为当前库存信息保留。") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEmptyStock) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEmptyStock) { Text("取消") }
            },
        )
    }
}

@Composable
private fun FormLabel(
    text: String,
    required: Boolean = false,
) {
    Text(
        text = buildAnnotatedString {
            append(text)

            if (required) {
                append(" ")
                withStyle(
                    SpanStyle(
                        color = FoodTheme.colors.danger
                    )
                ) {
                    append("*")
                }
            }
        },
        style = MaterialTheme.typography.titleMedium,
        color = FoodTheme.colors.textPrimary,
    )
}

@Composable
private fun ErrorText(text: String) {
    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = FoodTheme.colors.danger,
    )
}

/** 分组标题，可带淡紫色小标签（如"必填 3 项"） */
@Composable
private fun SectionHeader(title: String, badge: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FoodTheme.colors.textPrimary,
        )
        if (badge != null) {
            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(FoodTheme.dimens.radiusPill))
                    .background(FoodTheme.colors.primaryLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = FoodTheme.colors.primaryDark,
                )
            }
        }
    }
}

/** 轻量引导 Banner：淡紫渐变，不加重插画（规范文档 §36） */
@Composable
private fun GuideBanner(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(
                RoundedCornerShape(
                    FoodTheme.dimens.radiusLg
                )
            )
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE1D8FF),
                        Color(0xFFC3B0F8),
                    )
                )
            )
    ) {

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFF2EEFF),
                            Color(0xFFF2EEFF).copy(alpha = 0.95f),
                            Color(0xFFF2EEFF).copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(
                    start = 16.dp,
                    end = 100.dp,
                )
        ) {
            Text(
                text = "添加食材",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FoodTheme.colors.primary,
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "记录食材信息，方便管理和使用",
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 库存状态选择卡：选中浅紫底 + 紫描边 + CheckCircle（规范文档 §10～§12） */
@Composable
private fun StockChoiceCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoodTheme.colors
    val shape = RoundedCornerShape(FoodTheme.dimens.radiusLg)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.primaryLight else colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary else colors.divider,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = FoodTheme.dimens.spaceMd, vertical = FoodTheme.dimens.spaceMd),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (selected) colors.primary else colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.primaryDark else colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = 28.dp),
            )
        }
    }
}

/** 单位下拉：常用单位列表，已有自定义单位置顶（规范文档 §15） */
@Composable
private fun UnitDropdown(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = FoodTheme.colors
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FoodTheme.dimens.inputHeight)
                .clip(RoundedCornerShape(FoodTheme.dimens.radiusMd))
                .background(colors.surface)
                .clickable { expanded = true }
                .padding(horizontal = FoodTheme.dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = value.ifEmpty { "选择单位" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isEmpty()) colors.textTertiary else colors.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = "选择单位",
                tint = colors.textTertiary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onValueChange(unit)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 食材照片选择：与新增食谱封面框同规格（16:9 圆角通栏），点击换图，右上角可移除。 */
@Composable
private fun IngredientImagePicker(
    imageUri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusXl))
            .background(FoodTheme.colors.surfaceSoft)
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = "食材图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FoodTheme.dimens.spaceSm)
                    .clip(CircleShape)
                    .background(FoodTheme.colors.surface),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除图片",
                    tint = FoodTheme.colors.textSecondary,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = null,
                    tint = FoodTheme.colors.primary,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                Text(
                    text = "添加食材图片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
                Text(
                    text = "一张清晰的图片更好认",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textTertiary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: LocalDate?,
    highlight: Boolean = false,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember {
        mutableStateOf(false)
    }

    val formatter = remember {
        DateTimeFormatter.ofPattern(
            "yyyy年M月d日",
            Locale.CHINA
        )
    }

    val primaryColor = FoodTheme.colors.primary
    val backgroundColor = Color.White
    val borderColor = if (highlight) {
        primaryColor.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }

    /*
     * 截图对应的日期选择条：
     * - 高度约 48dp
     * - 白色背景
     * - 轻紫色边框
     * - 左侧日历 Icon
     * - 中间日期/Placeholder
     * - 右侧箭头
     */
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(
                RoundedCornerShape(16.dp)
            )
            .background(backgroundColor)
            .border(
                width = if (highlight) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                showDatePicker = true
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 左侧日历图标
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = "选择$label",
                tint = primaryColor.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            // 日期文本
            Text(
                text = value?.format(formatter) ?: "请选择$label",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) {
                    FoodTheme.colors.textPrimary
                } else {
                    FoodTheme.colors.textTertiary
                },
                modifier = Modifier.weight(1f)
            )

            // 右侧箭头
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "选择日期",
                tint = primaryColor.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // 日期选择弹窗
    if (showDatePicker) {
        val initialMillis = value?.toPickerMillis()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )

        DatePickerDialog(
            onDismissRequest = {
                showDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis =
                            datePickerState.selectedDateMillis

                        if (selectedMillis != null) {
                            onChange(
                                selectedMillis.toLocalDate()
                            )
                        }

                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = "确定",
                        color = primaryColor
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = "取消",
                        color = FoodTheme.colors.textSecondary
                    )
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = "",
                        color = FoodTheme.colors.textPrimary
                    )
                },
                headline = {
                    Text(
                        text = datePickerState
                            .selectedDateMillis
                            ?.toLocalDate()
                            ?.format(formatter)
                            ?: "请选择日期",
                        color = primaryColor
                    )
                }
            )
        }
    }
}

private fun LocalDate.toPickerMillis(): Long {
    return this
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private fun formatDate(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

private fun LocalDate.toEpochMilli(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
