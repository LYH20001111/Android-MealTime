package com.skyanchor.mealtime.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.model.MealHistoryItem
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import java.time.format.DateTimeFormatter

/** 历史用餐记录：按日期倒序，显示已完成菜品名（菜谱归档后仍可见，R07）。 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.factory(rememberAppContainer()),
    ),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = FoodTheme.colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.padding(horizontal = FoodTheme.dimens.spaceSm))
            Text(
                text = "历史记录",
                style = MaterialTheme.typography.titleLarge,
                color = FoodTheme.colors.textPrimary,
            )
        }

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = "还没有用餐记录",
                hint = "完成一餐后，这里会留下记录",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
            ) {
                items(items, key = { "${it.date}-${it.mealType}-${it.completedAt}" }) { item ->
                    HistoryCard(item)
                }
                item {
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(item: MealHistoryItem) {
    FoodCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FoodTheme.dimens.spaceLg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE")),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = FoodTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = mealLabel(item.mealType),
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.primaryDark,
                )
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
            val servingsText = item.servings?.let { " · $it 人" } ?: ""
            if (item.dishes.isNotEmpty()) {
                Text(
                    text = item.dishes.joinToString("、") + servingsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textSecondary,
                )
            } else {
                Text(
                    text = "完成用餐$servingsText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textSecondary,
                )
            }
        }
    }
}

private fun mealLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}
