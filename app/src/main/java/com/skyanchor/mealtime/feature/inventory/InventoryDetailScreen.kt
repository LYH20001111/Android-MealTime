package com.skyanchor.mealtime.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.R
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.AppStrings
import com.skyanchor.mealtime.core.common.ExpiryCalculator
import com.skyanchor.mealtime.core.common.ExpiryStatus
import com.skyanchor.mealtime.core.model.QuantityLevel
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.expiryStatusLabel
import com.skyanchor.mealtime.core.ui.ingredientTypeLabel
import java.time.format.DateTimeFormatter
import java.time.LocalDate

/**
 * 库存批次详情：字段展示 + 调整数量 / 编辑 / 归档（PAGES.md §7）。
 */
@Composable
fun InventoryDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: InventoryDetailViewModel = viewModel(
        key = "inventory_detail_$itemId",
        factory = InventoryDetailViewModel.factory(rememberAppContainer(), itemId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.adjustDone) {
        if (state.adjustDone) {
            snackbarHostState.showSnackbar(AppStrings.get(R.string.inventory_detail_quantity_adjusted))
            viewModel.consumeAdjustDone()
        }
    }

    // 调整到 0 或删除后批次归档，列表不再包含它：自动返回
    LaunchedEffect(state.isLoading, state.item) {
        if (!state.isLoading && state.item == null) onBack()
    }

    if (showAdjustDialog) {
        AdjustQuantityDialog(
            initial = state.item?.quantity,
            onDismiss = { showAdjustDialog = false },
            onConfirm = { newQuantity ->
                showAdjustDialog = false
                viewModel.adjustQuantity(newQuantity)
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.inventory_detail_delete_title)) },
            text = { Text(stringResource(R.string.inventory_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text(stringResource(R.string.common_delete), color = FoodTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    Scaffold(
        containerColor = FoodTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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
                        contentDescription = stringResource(R.string.common_back),
                        tint = FoodTheme.colors.textPrimary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (state.item != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                            tint = FoodTheme.colors.textSecondary,
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = FoodTheme.colors.textSecondary,
                        )
                    }
                }
            }

            val item = state.item
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FoodTheme.colors.primary)
                    }
                }

                item == null -> Unit // 已归档，自动返回

                else -> {
                    Column(
                        modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(FoodTheme.dimens.radiusMd))
                                    .background(FoodTheme.colors.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                val imageUri = item.ingredient.imageUri
                                if (imageUri != null) {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = item.ingredient.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Eco,
                                        contentDescription = null,
                                        tint = FoodTheme.colors.primary,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceMd))
                            Text(
                                text = item.ingredient.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = FoodTheme.colors.textPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                        SectionTitle(text = stringResource(R.string.inventory_detail_section_stock_info))
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                        FoodCard {
                            Column(
                                modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                                verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                            ) {
                                val quantityText = item.quantity?.toString()?.removeSuffix(".0")
                                    ?: item.quantityLevel?.chineseLabel
                                    ?: stringResource(R.string.inventory_detail_not_set)
                                DetailRow(stringResource(R.string.inventory_detail_label_quantity), if (item.unit != null) "$quantityText ${item.unit}" else quantityText)
                                DetailRow(stringResource(R.string.inventory_detail_label_type), ingredientTypeLabel(item.ingredient.type))
                                item.purchaseDate?.let { DetailRow(stringResource(R.string.inventory_detail_label_purchase_date), formatDate(it)) }
                                item.productionDate?.let { DetailRow(stringResource(R.string.inventory_detail_label_production_date), formatDate(it)) }
                                item.expireDate?.let { expire ->
                                    val status = ExpiryCalculator.status(expire, LocalDate.now())
                                    val days = ExpiryCalculator.daysUntil(expire, LocalDate.now())
                                    DetailRow(
                                        label = stringResource(R.string.inventory_detail_label_expiry_date),
                                        value = "${formatDate(expire)} · ${expiryStatusLabel(status, days)}",
                                        valueColor = when (status) {
                                            ExpiryStatus.EXPIRED -> FoodTheme.colors.danger
                                            ExpiryStatus.URGENT, ExpiryStatus.NEAR -> FoodTheme.colors.warning
                                            else -> FoodTheme.colors.textSecondary
                                        },
                                    )
                                }
                                    ?: DetailRow(stringResource(R.string.inventory_detail_label_expiry_date), stringResource(R.string.inventory_detail_not_set))
                                item.location?.let { DetailRow(stringResource(R.string.inventory_detail_label_location), it) }
                                item.note?.let { DetailRow(stringResource(R.string.inventory_detail_label_note), it) }
                            }
                        }

                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                        SectionTitle(text = stringResource(R.string.inventory_detail_section_actions))
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                        FoodCard {
                            Column(modifier = Modifier.padding(FoodTheme.dimens.spaceMd)) {
                                ActionRow(stringResource(R.string.inventory_detail_action_adjust), stringResource(R.string.inventory_detail_action_adjust_subtitle)) {
                                    showAdjustDialog = true
                                }
                                ActionRow(stringResource(R.string.inventory_detail_action_edit), stringResource(R.string.inventory_detail_action_edit_subtitle)) {
                                    onEdit()
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = FoodTheme.colors.textSecondary) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = FoodTheme.dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        TextButton(onClick = onClick) { Text(stringResource(R.string.inventory_detail_go)) }
    }
}

@Composable
private fun AdjustQuantityDialog(
    initial: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.toString()?.removeSuffix(".0") ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inventory_detail_action_adjust)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.inventory_detail_adjust_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.inventory_detail_adjust_placeholder)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.toDoubleOrNull()) }) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(AppStrings.get(R.string.date_pattern_full)))
