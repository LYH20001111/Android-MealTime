package com.skyanchor.mealtime.feature.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.ExpiryCalculator
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.SecondaryButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 首页 = 今日饮食控制台（PRD §6）：临期提醒、三餐卡片、今日备注、快捷点菜。
 * 完成用餐入口在 Phase 5（consume-confirm 流程）接入。
 */
@Composable
fun HomeScreen(
    onOpenMealPlan: (MealType) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onCompleteMeal: (MealType) -> Unit,
    onOpenRecommend: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNoteDialog by remember { mutableStateOf(false) }

    if (showNoteDialog) {
        NoteEditDialog(
            initial = state.note,
            onDismiss = { showNoteDialog = false },
            onConfirm = { text ->
                showNoteDialog = false
                viewModel.saveNote(text)
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDate(state.date),
                    style = MaterialTheme.typography.bodyLarge,
                    color = FoodTheme.colors.textTertiary,
                )
            }
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "通知",
                tint = FoodTheme.colors.textSecondary,
            )
        }

        if (state.expiringCount > 0) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            ExpiringBanner(
                items = state.expiring,
                count = state.expiringCount,
                onClick = onOpenRecommend,
            )
        }

        MealSection(
            title = "早餐",
            dishes = state.breakfast,
            onManage = { onOpenMealPlan(MealType.BREAKFAST) },
            onComplete = { onCompleteMeal(MealType.BREAKFAST) },
            onOpenRecipe = onOpenRecipe,
        )
        MealSection(
            title = "午餐",
            dishes = state.lunch,
            onManage = { onOpenMealPlan(MealType.LUNCH) },
            onComplete = { onCompleteMeal(MealType.LUNCH) },
            onOpenRecipe = onOpenRecipe,
        )
        MealSection(
            title = "晚餐",
            dishes = state.dinner,
            onManage = { onOpenMealPlan(MealType.DINNER) },
            onComplete = { onCompleteMeal(MealType.DINNER) },
            onOpenRecipe = onOpenRecipe,
        )

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        FoodCard(backgroundColor = FoodTheme.colors.primarySoft) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FoodTheme.dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "纠结吃什么？",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = FoodTheme.colors.primaryDark,
                    )
                    Text(
                        text = "按库存和临期情况为你排菜",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textSecondary,
                    )
                }
                com.skyanchor.mealtime.core.ui.SecondaryButton(
                    text = "智能推荐",
                    onClick = onOpenRecommend,
                )
            }
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
        SectionTitle(text = "今日备注")
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
        FoodCard(
            modifier = Modifier.clickable { showNoteDialog = true },
        ) {
            Text(
                text = state.note.ifBlank { "记点什么…（如：晚上 3 人用餐）" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.note.isBlank()) {
                    FoodTheme.colors.textTertiary
                } else {
                    FoodTheme.colors.textPrimary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FoodTheme.dimens.spaceLg),
            )
        }
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = FoodTheme.colors.primary)
            }
        }
    }
}

@Composable
private fun ExpiringBanner(
    items: List<InventoryItem>,
    count: Int,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    val detail = items.take(2).joinToString(" · ") { item ->
        val days = item.expireDate?.let { ExpiryCalculator.daysUntil(it, today) }
        "${item.ingredient.name} ${days ?: "?"}天"
    }
    FoodCard(backgroundColor = FoodTheme.colors.primarySoft) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(FoodTheme.dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                tint = FoodTheme.colors.warning,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(FoodTheme.dimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$count 种食材即将过期",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FoodTheme.colors.primaryDark,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = FoodTheme.colors.textTertiary,
            )
        }
    }
}

@Composable
private fun MealSection(
    title: String,
    dishes: List<HomeMealDish>,
    onManage: () -> Unit,
    onComplete: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
    SectionTitle(text = title)
    Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
    FoodCard {
        Column(modifier = Modifier.padding(FoodTheme.dimens.spaceLg)) {
            if (dishes.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(FoodTheme.colors.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestaurantMenu,
                            contentDescription = null,
                            tint = FoodTheme.colors.primaryLight,
                        )
                    }
                    Spacer(modifier = Modifier.size(FoodTheme.dimens.spaceMd))
                    Text(
                        text = "今天还没有安排",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                SecondaryButton(
                    text = "帮我安排",
                    onClick = onManage,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                dishes.forEachIndexed { index, dish ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FoodTheme.dimens.radiusSm))
                            .clickable { onOpenRecipe(dish.recipeId) }
                            .padding(vertical = FoodTheme.dimens.spaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(FoodTheme.colors.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestaurantMenu,
                                contentDescription = null,
                                tint = FoodTheme.colors.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.size(FoodTheme.dimens.spaceMd))
                        Text(
                            text = dish.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoodTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (dish.isCompleted) {
                            Text(
                                text = "已完成",
                                style = MaterialTheme.typography.bodySmall,
                                color = FoodTheme.colors.success,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
                val allCompleted = dishes.isNotEmpty() && dishes.all { it.isCompleted }
                if (allCompleted) {
                    Text(
                        text = "本餐已完成，库存已同步扣减",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.success,
                        modifier = Modifier.padding(vertical = FoodTheme.dimens.spaceSm),
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        SecondaryButton(
                            text = "修改菜品",
                            onClick = onManage,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "完成用餐",
                            onClick = onComplete,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("今日备注") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("如：晚上 3 人用餐") },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
