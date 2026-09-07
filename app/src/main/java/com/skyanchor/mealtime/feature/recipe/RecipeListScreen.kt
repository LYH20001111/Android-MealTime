package com.skyanchor.mealtime.feature.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodSearchField
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.core.ui.TagChip

/**
 * 菜谱列表：搜索、分类/收藏筛选、卡片列表、FAB 新增（PAGES.md §3）。
 */
@Composable
fun RecipeListScreen(
    onOpenRecipe: (Long) -> Unit,
    onAddRecipe: () -> Unit,
    viewModel: RecipeListViewModel = viewModel(
        factory = RecipeListViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
        ) {
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            FoodSearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "搜索菜谱、食材...",
            )

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                item {
                    TagChip(
                        text = "全部",
                        selected = state.selectedCategoryId == null && !state.favoritesOnly,
                        onClick = {
                            viewModel.selectCategory(null)
                            if (state.favoritesOnly) viewModel.toggleFavoritesOnly()
                        },
                    )
                }
                items(state.categories, key = { it.id }) { category ->
                    TagChip(
                        text = category.name,
                        selected = state.selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                    )
                }
                item {
                    TagChip(
                        text = "收藏",
                        selected = state.favoritesOnly,
                        onClick = { viewModel.toggleFavoritesOnly() },
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

                state.recipes.isEmpty() && state.query.isNotBlank() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "没有找到相关菜谱",
                            hint = "换个关键词试试",
                        )
                    }
                }

                state.recipes.isEmpty() -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.MenuBook,
                            title = "还没有菜谱",
                            hint = "创建你的第一道菜，点菜时就能直接选用",
                            actionText = "创建菜谱",
                            onAction = onAddRecipe,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = FoodTheme.dimens.spaceMd),
                        horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                        verticalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceMd),
                    ) {
                        items(state.recipes, key = { it.id }) { recipe ->
                            RecipeCard(
                                recipe = recipe,
                                onClick = { onOpenRecipe(recipe.id) },
                                onToggleFavorite = { viewModel.toggleFavorite(recipe) },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                }
            }
        }

        AddRecipeFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(FoodTheme.dimens.spaceXxl),
            onClick = onAddRecipe,
        )
    }
}

/** 两列网格卡片：封面图 + 菜名 + 难度/时长/收藏（对齐设计稿菜谱墙样式）。 */
@Composable
private fun RecipeCard(
    recipe: Recipe,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusLg))
            .background(FoodTheme.colors.surface)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(FoodTheme.colors.surfaceSoft),
        ) {
            if (recipe.imageUri != null) {
                AsyncImage(
                    model = recipe.imageUri,
                    contentDescription = recipe.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = FoodTheme.colors.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(
                horizontal = FoodTheme.dimens.spaceMd,
                vertical = FoodTheme.dimens.spaceSm,
            ),
        ) {
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = FoodTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(FoodTheme.dimens.radiusPill))
                        .background(FoodTheme.colors.primarySoft)
                        .padding(horizontal = FoodTheme.dimens.spaceSm, vertical = 2.dp),
                ) {
                    Text(
                        text = recipe.difficulty.chineseLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = FoodTheme.colors.primaryDark,
                    )
                }
                recipe.cookingTimeMin?.let { minutes ->
                    Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
                    Text(
                        text = "$minutes 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = FoodTheme.colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onToggleFavorite)
                        .padding(FoodTheme.dimens.spaceXs),
                ) {
                    Icon(
                        imageVector = if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (recipe.isFavorite) "取消收藏" else "收藏",
                        tint = if (recipe.isFavorite) FoodTheme.colors.primary else FoodTheme.colors.textTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRecipeFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(brush = FoodTheme.colors.primaryGradient)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "新增菜谱",
            tint = FoodTheme.colors.surface,
        )
    }
}
