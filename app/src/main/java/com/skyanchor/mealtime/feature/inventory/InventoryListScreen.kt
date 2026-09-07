package com.skyanchor.mealtime.feature.inventory

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.ExpiryCalculator
import com.skyanchor.mealtime.core.common.ExpiryStatus
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.QuantityLevel
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodFab
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.TagChip
import java.time.LocalDate

/**
 * 食材/库存列表：全部 / 临期 + 可配置的食材种类 Tab，临期优先排序（PAGES.md §6）。
 */
@Composable
fun InventoryListScreen(
    onOpenItem: (Long) -> Unit,
    onAddItem: () -> Unit,
    viewModel: InventoryListViewModel = viewModel(
        factory = InventoryListViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
        ) {
            if (state.expiringCount > 0) {
                Text(
                    text = "有 ${state.expiringCount} 批食材临近保质期，优先吃掉它们",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.warning,
                )
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                items(state.tabs, key = { it.key }) { tab ->
                    TagChip(
                        text = tab.label,
                        selected = state.selectedTabKey == tab.key,
                        onClick = { viewModel.selectTab(tab.key) },
                    )
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FoodTheme.colors.primary)
                    }
                }

                state.items.isEmpty() && state.selectedTabKey == InventoryTabKeys.EXPIRING -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Eco,
                            title = "没有临期食材",
                            hint = "保质期临近的食材会出现在这里",
                        )
                    }
                }

                state.items.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Kitchen,
                            title = "冰箱还是空的",
                            hint = "录入食材和保质期，饭点会提醒你先吃什么",
                            actionText = "录入食材",
                            onAction = onAddItem,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = FoodTheme.dimens.spaceMd),
                        verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            InventoryCard(
                                item = item,
                                today = today,
                                onClick = { onOpenItem(item.id) },
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                }
            }
        }

        FoodFab(
            onClick = onAddItem,
            contentDescription = "录入库存",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(FoodTheme.dimens.spaceXxl),
        )
    }
}

@Composable
private fun InventoryCard(
    item: InventoryItem,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val status = ExpiryCalculator.status(item.expireDate, today)
    val days = item.expireDate?.let { ExpiryCalculator.daysUntil(it, today) }
    val statusColor = when (status) {
        ExpiryStatus.EXPIRED -> FoodTheme.colors.danger
        ExpiryStatus.URGENT, ExpiryStatus.NEAR -> FoodTheme.colors.warning
        else -> FoodTheme.colors.textTertiary
    }

    FoodCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(FoodTheme.dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
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
                    imageVector = if (item.ingredient.type == com.skyanchor.mealtime.core.model.IngredientTypes.SEASONING) {
                        Icons.Outlined.Kitchen
                    } else {
                        Icons.Outlined.Eco
                    },
                        contentDescription = null,
                        tint = FoodTheme.colors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.ingredient.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = FoodTheme.colors.textPrimary,
                    )
                    if (status == ExpiryStatus.EXPIRED || status == ExpiryStatus.URGENT) {
                        Spacer(modifier = Modifier.size(FoodTheme.dimens.spaceSm))
                        Text(text = "⚠", style = MaterialTheme.typography.bodySmall)
                    }
                }
                val amount = item.quantity?.let { q ->
                    val quantityText = q.toString().removeSuffix(".0")
                    item.unit?.let { "$quantityText $it" } ?: quantityText
                } ?: item.quantityLevel?.chineseLabel ?: "—"
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )
            }
            Text(
                text = ExpiryCalculator.label(status, days),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
    }
}
