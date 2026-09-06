package com.skyanchor.mealtime.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodSearchField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.PrimaryButton
import com.skyanchor.mealtime.core.ui.SecondaryButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 餐次管理页：本餐次菜品列表（排序/更换/删除）+ 从菜谱库选菜（PAGES.md §8 点菜流程）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    date: LocalDate,
    mealType: MealType,
    onBack: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    viewModel: MealPlanViewModel = viewModel(
        key = "meal_plan_${date}_$mealType",
        factory = MealPlanViewModel.factory(rememberAppContainer(), date, mealType),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var replacingPlanId by remember { mutableStateOf<Long?>(null) }

    Scaffold(containerColor = FoodTheme.colors.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
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
                    text = "${mealLabel(mealType)} · ${formatShortDate(state.date)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = FoodTheme.colors.textPrimary,
                )
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "加载中…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoodTheme.colors.textTertiary,
                        )
                    }
                }

                state.dishes.isEmpty() -> {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = "这餐还没有菜品",
                        hint = "从你的菜谱库里挑一道吧",
                        actionText = "添加菜品",
                        onAction = { showPicker = true },
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                    ) {
                        items(state.dishes, key = { it.planId }) { dish ->
                            FoodCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = FoodTheme.dimens.spaceLg,
                                            vertical = FoodTheme.dimens.spaceMd,
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dish.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FoodTheme.colors.textPrimary,
                                            modifier = Modifier.clickable {
                                                onOpenRecipe(dish.recipeId)
                                            },
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveDish(dish.planId, -1) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowUp,
                                            contentDescription = "上移",
                                            tint = FoodTheme.colors.textSecondary,
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveDish(dish.planId, +1) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "下移",
                                            tint = FoodTheme.colors.textSecondary,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            replacingPlanId = dish.planId
                                            showPicker = true
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SwapHoriz,
                                            contentDescription = "更换",
                                            tint = FoodTheme.colors.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeDish(dish.planId) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = "删除",
                                            tint = FoodTheme.colors.textTertiary,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            PrimaryButton(
                                text = "＋ 添加菜品",
                                onClick = { showPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(
            onDismissRequest = {
                showPicker = false
                replacingPlanId = null
                viewModel.setPickerQuery("")
            },
            containerColor = FoodTheme.colors.background,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
            ) {
                Text(
                    text = if (replacingPlanId != null) "换成哪道菜？" else "选择菜品",
                    style = MaterialTheme.typography.titleMedium,
                    color = FoodTheme.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                FoodSearchField(
                    value = state.pickerQuery,
                    onValueChange = viewModel::setPickerQuery,
                    placeholder = "搜索菜谱",
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                if (state.pickerRecipes.isEmpty()) {
                    Text(
                        text = if (state.pickerQuery.isBlank()) "还没有菜谱，先去菜谱页创建" else "没有匹配的菜谱",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textTertiary,
                        modifier = Modifier.padding(vertical = FoodTheme.dimens.spaceXxl),
                    )
                } else {
                    LazyColumn {
                        items(state.pickerRecipes, key = { it.id }) { recipe ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val replacing = replacingPlanId
                                        if (replacing != null) {
                                            viewModel.replaceDish(replacing, recipe.id)
                                        } else {
                                            viewModel.addDish(recipe.id)
                                        }
                                        showPicker = false
                                        replacingPlanId = null
                                        viewModel.setPickerQuery("")
                                    }
                                    .padding(vertical = FoodTheme.dimens.spaceMd),
                            ) {
                                Column {
                                    Text(
                                        text = recipe.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = FoodTheme.colors.textPrimary,
                                    )
                                    val meta = listOfNotNull(
                                        recipe.difficulty.chineseLabel,
                                        recipe.cookingTimeMin?.let { "$it 分钟" },
                                    ).joinToString(" · ")
                                    if (meta.isNotEmpty()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = FoodTheme.colors.textTertiary,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
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

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
