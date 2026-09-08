package com.skyanchor.mealtime.feature.recommend

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recommendation
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SecondaryButton

/**
 * 智能推荐页（PAGES.md §9）：规则得分 + 推荐理由 + 库存匹配情况 + 加入下一餐。
 */
@Composable
fun RecommendScreen(
    targetMealType: MealType? = null,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    viewModel: RecommendViewModel = viewModel(
        key = "recommend_${targetMealType?.name ?: "auto"}",
        factory = RecommendViewModel.factory(rememberAppContainer(), targetMealType),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.addedName) {
        state.addedName?.let { name ->
            snackbarHostState.showSnackbar("已把「$name」加入今天${mealLabel(state.nextMeal)}")
            viewModel.consumeAdded()
        }
    }

    Scaffold(
        containerColor = FoodTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
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
                    text = if (state.isRandom) "随机一道" else "为你推荐",
                    style = MaterialTheme.typography.titleLarge,
                    color = FoodTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::randomPick) {
                    Icon(
                        imageVector = Icons.Outlined.Casino,
                        contentDescription = "随机一道",
                        tint = FoodTheme.colors.textSecondary,
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        tint = FoodTheme.colors.textSecondary,
                    )
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = FoodTheme.colors.primary,
                        )
                    }
                }

                state.items.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "还没有可推荐的菜谱",
                        hint = "先在菜谱页创建几道菜，推荐才有用武之地",
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = FoodTheme.dimens.pageHorizontalPadding,
                            vertical = FoodTheme.dimens.spaceMd,
                        ),
                        verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                    ) {
                        items(state.items, key = { it.recipe.id }) { item ->
                            RecommendCard(
                                item = item,
                                nextMealLabel = mealLabel(state.nextMeal),
                                onOpen = { onOpenRecipe(item.recipe.id) },
                                onAdd = { viewModel.addToMeal(item.recipe.id, state.nextMeal) },
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                        }
                    }
                }
            }
        }
    }
}

internal fun mealLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}

@Composable
private fun RecommendCard(
    item: Recommendation,
    nextMealLabel: String,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
) {
    FoodCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FoodTheme.dimens.spaceLg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.recipe.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = FoodTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(FoodTheme.dimens.radiusSm))
                        .clickable(onClick = onOpen),
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FoodTheme.colors.primarySoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${item.score}分",
                        style = MaterialTheme.typography.labelSmall,
                        color = FoodTheme.colors.primaryDark,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (item.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                item.reasons.forEach { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = FoodTheme.dimens.spaceXs),
                    )
                }
            }

            if (item.totalIngredients > 0) {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                Text(
                    text = "库存匹配 ${item.matchedCount}/${item.totalIngredients} 种食材",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textTertiary,
                )
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            SecondaryButton(
                text = "加入$nextMealLabel",
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
