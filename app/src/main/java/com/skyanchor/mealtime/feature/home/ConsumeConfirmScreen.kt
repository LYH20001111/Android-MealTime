package com.skyanchor.mealtime.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTextField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.PrimaryButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 完成用餐确认（PAGES.md §8）：逐菜展示预计消耗，可修改实际值后确认。
 * 确认触发单事务：扣库存 + 流水 + 用餐记录 + 餐次状态。
 */
@Composable
fun ConsumeConfirmScreen(
    date: LocalDate,
    mealType: MealType,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: ConsumeConfirmViewModel = viewModel(
        key = "consume_confirm_${date}_$mealType",
        factory = ConsumeConfirmViewModel.factory(rememberAppContainer(), date, mealType),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = FoodTheme.colors.background) { padding ->
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
                    text = "完成${mealTypeName(mealType)} · ${formatDate(state.date)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = FoodTheme.colors.textPrimary,
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
            ) {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                Text(
                    text = "确认这顿饭实际消耗的食材，库存会自动扣减。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )

                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                ) {
                    Text(
                        text = "用餐人数",
                        style = MaterialTheme.typography.titleMedium,
                        color = FoodTheme.colors.textPrimary,
                    )
                    FoodTextField(
                        value = state.servingsText,
                        onValueChange = viewModel::setServings,
                        placeholder = "1",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }

                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))

                if (state.isLoading) {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textTertiary,
                    )
                } else if (state.alreadyCompleted) {
                    Text(
                        text = "这一餐已经完成过了，去历史记录看看吧。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textSecondary,
                    )
                } else if (state.dishes.isEmpty()) {
                    Text(
                        text = "这餐还没有安排菜品",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textTertiary,
                    )
                } else {
                    state.dishes.forEach { dish ->
                        FoodCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(FoodTheme.dimens.spaceLg),
                            ) {
                                Text(
                                    text = dish.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = FoodTheme.colors.textPrimary,
                                )
                                if (!dish.hasDeductions) {
                                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                                    Text(
                                        text = "没有食材明细，不扣减库存",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = FoodTheme.colors.textTertiary,
                                    )
                                } else {
                                    dish.lines.forEach { line ->
                                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = line.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = FoodTheme.colors.textPrimary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "-",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = FoodTheme.colors.danger,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            FoodTextField(
                                                value = line.actualText,
                                                onValueChange = {
                                                    viewModel.updateActual(line.planId, line.ingredientId, it)
                                                },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                modifier = Modifier.width(84.dp),
                                            )
                                            Text(
                                                text = line.unit ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = FoodTheme.colors.textTertiary,
                                                modifier = Modifier.width(36.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                    }
                }

                state.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.danger,
                    )
                }

                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
                PrimaryButton(
                    text = if (state.isSaving) "确认中…" else "确认用餐",
                    onClick = { viewModel.confirm(onCompleted = onCompleted) },
                    enabled = !state.isSaving && !state.isLoading && !state.alreadyCompleted,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
            }
        }
    }
}

internal fun mealTypeName(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
