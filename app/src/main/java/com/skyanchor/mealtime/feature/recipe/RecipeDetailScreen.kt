package com.skyanchor.mealtime.feature.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.RecipeDetail
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodImagePreviewDialog
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.SectionTitle
import com.skyanchor.mealtime.core.ui.TagChip
import com.skyanchor.mealtime.core.ui.ingredientTypeLabel

/**
 * 菜谱详情：封面、基本信息、食材/调料、步骤、备注，
 * 底部固定"加入早餐/午餐/晚餐"（PAGES.md §4）。
 */
@Composable
fun RecipeDetailScreen(
    recipeId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: RecipeDetailViewModel = viewModel(
        key = "recipe_detail_$recipeId",
        factory = RecipeDetailViewModel.factory(rememberAppContainer(), recipeId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showImagePreview by remember { mutableStateOf(false) }

    LaunchedEffect(state.addResult) {
        state.addResult?.let { result ->
            snackbarHostState.showSnackbar(
                if (result.isDuplicate) {
                    AppStrings.get(R.string.recipe_detail_already_in_meal, mealLabel(result.mealType))
                } else {
                    AppStrings.get(R.string.recipe_detail_added_to_meal, mealLabel(result.mealType))
                }
            )
            viewModel.consumeAddResult()
        }
    }

    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            title = { Text(stringResource(R.string.recipe_detail_delete_title)) },
            text = { Text(stringResource(R.string.recipe_detail_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    viewModel.archive(onArchived = onBack)
                }) { Text(stringResource(R.string.common_delete), color = FoodTheme.colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showImagePreview) {
        FoodImagePreviewDialog(
            imageUri = state.detail?.recipe?.imageUri,
            onDismiss = { showImagePreview = false },
        )
    }

    Scaffold(
        containerColor = FoodTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.detail != null) {
                Surface(color = FoodTheme.colors.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = FoodTheme.dimens.pageHorizontalPadding,
                                vertical = FoodTheme.dimens.spaceMd,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                    ) {
                        listOf(
                            MealType.BREAKFAST,
                            MealType.LUNCH,
                            MealType.DINNER,
                        ).forEach { type ->
                            com.skyanchor.mealtime.core.ui.SecondaryButton(
                                text = stringResource(R.string.recipe_detail_add_to_meal, mealLabel(type)),
                                onClick = { viewModel.addToMeal(type) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = FoodTheme.dimens.spaceSm,
                        vertical = FoodTheme.dimens.spaceXs,
                    ),
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
                if (state.detail != null) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                            tint = FoodTheme.colors.textSecondary,
                        )
                    }
                    IconButton(onClick = { showArchiveDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = FoodTheme.colors.textSecondary,
                        )
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (state.detail?.recipe?.isFavorite == true) {
                                Icons.Filled.Star
                            } else {
                                Icons.Outlined.StarBorder
                            },
                            contentDescription = stringResource(R.string.recipe_detail_favorite),
                            tint = FoodTheme.colors.primary,
                        )
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = FoodTheme.colors.primary)
                    }
                }

                state.detail == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.recipe_detail_not_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoodTheme.colors.textTertiary,
                        )
                    }
                }

                else -> RecipeDetailContent(
                    detail = state.detail ?: return@Column,
                    onImageClick = { showImagePreview = true },
                )
            }
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
        }
    }
}

@Composable
private fun RecipeDetailContent(
    detail: RecipeDetail,
    onImageClick: () -> Unit,
) {
    val recipe = detail.recipe
    Column(
        modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        if (recipe.imageUri != null) {
            AsyncImage(
                model = recipe.imageUri,
                contentDescription = stringResource(R.string.recipe_detail_image_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(FoodTheme.dimens.radiusLg))
                    .clickable(onClick = onImageClick),
            )
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
        }

        Text(
            text = recipe.name,
            style = MaterialTheme.typography.titleLarge,
            color = FoodTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
        Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
            TagChip(text = recipe.difficulty.chineseLabel)
            recipe.cookingTimeMin?.let { TagChip(text = stringResource(R.string.recipe_detail_cooking_minutes, it)) }
        }

        // 按种类分区展示（默认 食材/调料），分区顺序即配料行顺序
        detail.ingredients.groupBy { it.type }.forEach { (typeKey, lines) ->
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = ingredientTypeLabel(typeKey))
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Column(
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                    verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm),
                ) {
                    lines.forEach { line ->
                        IngredientRow(
                            name = line.name,
                            amount = formatAmount(line.quantity, line.unit),
                        )
                    }
                }
            }
        }

        if (recipe.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = stringResource(R.string.recipe_detail_steps_title))
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            Column(verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd)) {
                recipe.steps.forEachIndexed { index, step ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(FoodTheme.colors.primarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FoodTheme.colors.primaryDark,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodyMedium,
                            color = FoodTheme.colors.textPrimary,
                        )
                    }
                }
            }
        }

        if (!recipe.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = stringResource(R.string.recipe_detail_description_title))
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Text(
                    text = recipe.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textSecondary,
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                )
            }
        }

        if (!recipe.note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            SectionTitle(text = stringResource(R.string.recipe_detail_note_title))
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            FoodCard {
                Text(
                    text = recipe.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textSecondary,
                    modifier = Modifier.padding(FoodTheme.dimens.spaceLg),
                )
            }
        }

        if (detail.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                detail.tags.forEach { tag -> TagChip(text = tag.name) }
            }
        }
    }
}

@Composable
private fun IngredientRow(name: String, amount: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = FoodTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.bodySmall,
            color = FoodTheme.colors.textSecondary,
        )
    }
}

private fun formatAmount(quantity: Double?, unit: String?): String {
    val quantityText = quantity?.toString()?.removeSuffix(".0") ?: AppStrings.get(R.string.recipe_detail_amount_as_needed)
    return if (unit.isNullOrBlank()) quantityText else "$quantityText $unit"
}

internal fun mealLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> AppStrings.get(R.string.meal_type_breakfast)
    MealType.LUNCH -> AppStrings.get(R.string.meal_type_lunch)
    MealType.DINNER -> AppStrings.get(R.string.meal_type_dinner)
}
