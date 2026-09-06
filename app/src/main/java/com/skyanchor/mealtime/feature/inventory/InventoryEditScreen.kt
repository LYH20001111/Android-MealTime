package com.skyanchor.mealtime.feature.inventory

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.copyImageToPrivate
import com.skyanchor.mealtime.core.model.IngredientType
import com.skyanchor.mealtime.core.model.QuantityLevel
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.PrimaryButton
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.TagChip
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 新增/编辑库存批次：食材、数量/级别、单位、日期、位置、备注（PAGES.md §6/§7）。
 * 新增时食材走字典"选择或创建"；编辑时保留原食材关联。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = FoodTheme.colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
            Text(
                text = if (state.isNew) "录入库存" else "编辑库存",
                style = MaterialTheme.typography.titleLarge,
                color = FoodTheme.colors.textPrimary,
            )
        }

        if (state.isLoading) {
            Spacer(modifier = Modifier.height(200.dp))
            Text(
                text = "加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = FoodTheme.colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            return@Column
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        FormLabel("食材名称 *")
        FoodTextField(
            value = state.ingredientName,
            onValueChange = viewModel::setIngredientName,
            placeholder = "例如：番茄",
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.nameError) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
            Text(
                text = "请填写食材名称",
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.danger,
            )
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        FormLabel("食材图片")
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

        if (state.isNew) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            FormLabel("类型")
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                IngredientType.entries.forEach { type ->
                    TagChip(
                        text = if (type == IngredientType.INGREDIENT) "食材" else "调料",
                        selected = state.ingredientType == type,
                        onClick = { viewModel.setIngredientType(type) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
            Column(modifier = Modifier.weight(1f)) {
                FormLabel("数量")
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
                FoodTextField(
                    value = state.unit,
                    onValueChange = viewModel::setUnit,
                    placeholder = "个 / 把 / g",
                    modifier = Modifier.fillMaxWidth(),
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

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        SectionTitle(text = "保质期信息")
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
        DateField(
            label = "过期日期",
            value = state.expireDate,
            highlight = true,
            onChange = viewModel::setExpireDate,
        )
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
        DateField(
            label = "购买日期",
            value = state.purchaseDate,
            highlight = false,
            onChange = viewModel::setPurchaseDate,
        )
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
        DateField(
            label = "生产日期",
            value = state.productionDate,
            highlight = false,
            onChange = viewModel::setProductionDate,
        )

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        FormLabel("存放位置")
        FoodTextField(
            value = state.location,
            onValueChange = viewModel::setLocation,
            placeholder = "冷藏室 / 冷冻层 / 橱柜…",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        FormLabel("备注")
        FoodTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            placeholder = "品牌、开封日期等",
            modifier = Modifier.fillMaxWidth(),
        )

        state.saveError?.let { error ->
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.danger,
            )
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
        PrimaryButton(
            text = if (state.isSaving) "保存中…" else "保存",
            onClick = { viewModel.save(onSaved = { onDone() }) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
    }
}

@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = FoodTheme.colors.textPrimary,
    )
}

/** 食材照片选择：96dp 圆角块，点击换图，右上角可移除。 */
@Composable
private fun IngredientImagePicker(
    imageUri: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusMd))
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FoodTheme.dimens.spaceXs)
                    .clip(CircleShape)
                    .background(FoodTheme.colors.surface)
                    .clickable(onClick = onClear)
                    .padding(FoodTheme.dimens.spaceXs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除图片",
                    tint = FoodTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Outlined.AddPhotoAlternate,
                contentDescription = "添加食材图片",
                tint = FoodTheme.colors.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    highlight: Boolean,
    onChange: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    FoodCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(horizontal = FoodTheme.dimens.spaceLg, vertical = FoodTheme.dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlight) FoodTheme.colors.textPrimary else FoodTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value?.let { formatDate(it) } ?: "选择日期",
                style = MaterialTheme.typography.bodyMedium,
                color = if (value != null) FoodTheme.colors.primaryDark else FoodTheme.colors.textTertiary,
            )
            if (value != null) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "清除",
                    tint = FoodTheme.colors.textTertiary,
                    modifier = Modifier
                        .padding(start = FoodTheme.dimens.spaceSm)
                        .clickable { onChange(null) },
                )
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(pickerState.selectedDateMillis?.toLocalDate())
                        showPicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatDate(date: LocalDate): String =
    "${date.year}年${date.monthValue}月${date.dayOfMonth}日"

private fun LocalDate.toEpochMilli(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
