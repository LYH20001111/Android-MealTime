package com.skyanchor.mealtime.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 临期食材列表：首页临期提醒条「查看」进入。
 * 展示每种临期食材的名称、库存与剩余天数，按到期时间升序，点击进入批次详情。
 */
@Composable
fun ExpiringInventoryScreen(
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    viewModel: ExpiringInventoryViewModel = viewModel(
        factory = ExpiringInventoryViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(containerColor = FoodTheme.colors.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize(),
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
                    text = "临期食材",
                    style = MaterialTheme.typography.titleLarge,
                    color = FoodTheme.colors.textPrimary,
                )
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

                state.items.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.Eco,
                            title = "没有临期食材",
                            hint = "保质期临近的食材会出现在这里",
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
                    ) {
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                        Text(
                            text = "共 ${state.items.size} 批食材临近保质期，按到期时间排序，优先吃掉最早过期的",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoodTheme.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                ExpiringCard(
                                    item = item,
                                    today = today,
                                    onClick = { onOpenItem(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiringCard(
    item: InventoryItem,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val expireDate = item.expireDate
    val status = ExpiryCalculator.status(expireDate, today)
    val days = expireDate?.let { ExpiryCalculator.daysUntil(it, today) }
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
                        imageVector = if (item.ingredient.type == IngredientTypes.SEASONING) {
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "库存：${stockText(item)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )
                expireDate?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "保质期至 ${formatDate(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textTertiary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(FoodTheme.dimens.radiusPill))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = daysLabel(days),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                )
            }
        }
    }
}

/** 库存描述：数字数量 + 单位 / 模糊数量级别 / 库存空，与库存列表口径一致 */
private fun stockText(item: InventoryItem): String {
    val amount = item.quantity?.let { q ->
        val quantityText = q.toString().removeSuffix(".0")
        item.unit?.let { "$quantityText $it" } ?: quantityText
    } ?: item.quantityLevel?.chineseLabel ?: "库存空"
    return amount
}

/** 剩余天数：还有几天过期 / 今天过期 / 已过期 N 天 / 未设置保质期 */
private fun daysLabel(days: Long?): String = when {
    days == null -> "未设置保质期"
    days < 0 -> "已过期 ${-days} 天"
    days == 0L -> "今天过期"
    else -> "还有 $days 天过期"
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M月d日"))
