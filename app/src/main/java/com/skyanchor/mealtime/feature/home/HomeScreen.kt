package com.skyanchor.mealtime.feature.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skyanchor.mealtime.R
import com.skyanchor.mealtime.app.rememberAppContainer
import com.skyanchor.mealtime.core.common.ExpiryCalculator
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.ui.EmptyState
import com.skyanchor.mealtime.core.ui.FoodCard
import com.skyanchor.mealtime.core.ui.FoodTheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 首页 = 今日饮食控制台（V2 优化版）：问候头部、临期提醒、今日推荐 Hero、
 * 今日三餐（当前餐次高亮）、智能推荐、今日备注。
 */
@Composable
fun HomeScreen(
    onOpenMealPlan: (MealType) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onCompleteMeal: (MealType) -> Unit,
    onOpenRecommend: () -> Unit,
    onAddRecipe: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(rememberAppContainer()),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val addedNotice by viewModel.addedNotice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNoteDialog by remember { mutableStateOf(false) }
    var mealPickerRecipeId by remember { mutableStateOf<Long?>(null) }
    val currentMealType = getCurrentMealType()

    LaunchedEffect(addedNotice) {
        addedNotice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeAddedNotice()
        }
    }

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

    // §22：点击"加入今日三餐"后选择餐次
    mealPickerRecipeId?.let { recipeId ->
        AddToMealDialog(
            defaultMealType = currentMealType,
            onDismiss = { mealPickerRecipeId = null },
            onConfirm = { mealType ->
                mealPickerRecipeId = null
                viewModel.addToMeal(recipeId, mealType)
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(FoodTheme.colors.background),
        ) {
            // === 顶部问候区域 ===
            HomeHeader(date = state.date)

            // === 临期食材提醒 ===
            if (state.expiringCount > 0) {
                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
                ExpiringBanner(
                    items = state.expiring,
                    count = state.expiringCount,
                    onClick = onOpenRecommend,
                )
            }

            // === 今日推荐 Hero 卡片 ===
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXl))
            TodayRecommendationSection(
                state = state.recommendation,
                onOpenRecipe = onOpenRecipe,
                onAddToMeal = { recipeId -> mealPickerRecipeId = recipeId },
                onRetry = viewModel::retryRecommendation,
                onAddRecipe = onAddRecipe,
            )

            // === 今日三餐 ===
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
            TodayMealsSectionTitle()

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            MealCard(
                mealType = MealType.BREAKFAST,
                dishes = state.breakfast,
                isCurrent = currentMealType == MealType.BREAKFAST,
                onManage = { onOpenMealPlan(MealType.BREAKFAST) },
                onComplete = { onCompleteMeal(MealType.BREAKFAST) },
                onOpenRecipe = onOpenRecipe,
            )

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            MealCard(
                mealType = MealType.LUNCH,
                dishes = state.lunch,
                isCurrent = currentMealType == MealType.LUNCH,
                onManage = { onOpenMealPlan(MealType.LUNCH) },
                onComplete = { onCompleteMeal(MealType.LUNCH) },
                onOpenRecipe = onOpenRecipe,
            )

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))
            MealCard(
                mealType = MealType.DINNER,
                dishes = state.dinner,
                isCurrent = currentMealType == MealType.DINNER,
                onManage = { onOpenMealPlan(MealType.DINNER) },
                onComplete = { onCompleteMeal(MealType.DINNER) },
                onOpenRecipe = onOpenRecipe,
            )

            // === 智能推荐 ===
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
            SmartRecommendationCard(onClick = onOpenRecommend)

            // === 今日备注 ===
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
            TodayNoteCard(
                note = state.note,
                onClick = { showNoteDialog = true },
            )

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceXxl))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ===================== 顶部问候区域 =====================

@Composable
private fun HomeHeader(date: LocalDate) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(R.drawable.bg_home_top),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getGreeting(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = FoodTheme.colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "今天吃什么？",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6F68A1),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDate(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textTertiary,
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "通知",
                    tint = FoodTheme.colors.textSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { }
                        .padding(8.dp),
                )
            }
        }
    }
}

private fun getGreeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 6 -> "夜深了 🌙"
        hour < 11 -> "早上好 ☀️"
        hour < 14 -> "中午好 ️"
        hour < 18 -> "下午好 🌅"
        else -> "晚上好 👋"
    }
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA))

// ===================== 临期食材提醒 =====================

@Composable
private fun ExpiringBanner(
    items: List<InventoryItem>,
    count: Int,
    onClick: () -> Unit,
) {
    val today = LocalDate.now()
    val detail = items.take(2).joinToString("   ") { item ->
        val days = item.expireDate?.let { ExpiryCalculator.daysUntil(it, today) }
        "${item.ingredient.name}（${days ?: "?"}天）"
    }

    FoodCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        backgroundColor = FoodTheme.colors.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FoodTheme.dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF7EF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Alarm,
                    contentDescription = null,
                    tint = FoodTheme.colors.warning,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "有 $count 种食材即将过期",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FoodTheme.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "查看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.textTertiary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = FoodTheme.colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ===================== 今日推荐 Hero 卡片（今日推荐功能规格文档） =====================

/** §9：自动轮播间隔 5 秒 */
private const val RECOMMENDATION_AUTO_SCROLL_INTERVAL_MS = 5_000L

/** §8/§25：按状态分发 —— 加载 Skeleton、错误重试、空态、轮播 */
@Composable
private fun TodayRecommendationSection(
    state: RecommendationUiState,
    onOpenRecipe: (Long) -> Unit,
    onAddToMeal: (recipeId: Long) -> Unit,
    onRetry: () -> Unit,
    onAddRecipe: () -> Unit,
) {
    when (state) {
        RecommendationUiState.Loading -> TodayRecommendationSkeleton()
        RecommendationUiState.Error -> TodayRecommendationError(onRetry = onRetry)
        is RecommendationUiState.Ready -> when {
            state.items.isEmpty() -> TodayRecommendationEmpty(onAddRecipe = onAddRecipe)
            else -> TodayRecommendationCarousel(
                items = state.items,
                onOpenRecipe = onOpenRecipe,
                onAddToMeal = onAddToMeal,
            )
        }
    }
}

/** §7/§8：Horizontal Carousel，支持手动滑动与自动轮播 */
@Composable
private fun TodayRecommendationCarousel(
    items: List<HomeRecommendation>,
    onOpenRecipe: (Long) -> Unit,
    onAddToMeal: (recipeId: Long) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })

    // §9/§10/§29：仅 2~3 道时自动轮播；用户拖动期间跳过本次翻页，
    // 停止后约 4~5 秒内恢复，防止自动轮播抢走操作权
    if (items.size > 1) {
        LaunchedEffect(pagerState, items.size) {
            while (true) {
                delay(RECOMMENDATION_AUTO_SCROLL_INTERVAL_MS)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(
                        (pagerState.currentPage + 1) % items.size,
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = FoodTheme.dimens.spaceMd,
        ) { page ->
            RecommendationCard(
                item = items[page],
                reserveIndicatorSpace = items.size > 1,
                onClick = { onOpenRecipe(items[page].recipeId) },
                onAddToMeal = { onAddToMeal(items[page].recipeId) },
            )
        }

        // §8：分页指示器，仅多于 1 道菜时显示
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = FoodTheme.dimens.spaceMd),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(items.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** §12-§19：左侧内容 + 右侧菜品图的一体化 Hero 卡片 */
@Composable
private fun RecommendationCard(
    item: HomeRecommendation,
    reserveIndicatorSpace: Boolean,
    onClick: () -> Unit,
    onAddToMeal: () -> Unit,
) {
    val colors = FoodTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusXl))
            .background(
                Brush.horizontalGradient(listOf(colors.gradientStart, colors.gradientEnd)),
            )
            .clickable(onClick = onClick),
    ) {
        // §15-§17：右侧菜品图约占一半，左缘用紫色渐变遮罩融入背景，不做生硬分栏
        AsyncImage(
            model = item.imageUri,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(R.drawable.bg_recommendation),
            error = painterResource(R.drawable.bg_recommendation),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.52f),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.52f)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            colors.gradientEnd.copy(alpha = 0.9f),
                            colors.gradientEnd.copy(alpha = 0.45f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // §19：文字层级 —— 标签 / 菜名 / 辅助信息 / CTA
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.58f)
                .padding(start = FoodTheme.dimens.spaceXl, top = FoodTheme.dimens.spaceXl)
                .padding(
                    bottom = if (reserveIndicatorSpace) {
                        FoodTheme.dimens.spaceXxl
                    } else {
                        FoodTheme.dimens.spaceXl
                    },
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "✨",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "今日推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "为你精选",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))

            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))

            Text(
                text = item.meta,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .clickable(onClick = onAddToMeal)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✨",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "加入今日三餐",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/** §25：首次读取的低饱和紫 Skeleton */
@Composable
private fun TodayRecommendationSkeleton() {
    val transition = rememberInfiniteTransition(label = "recommendation-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recommendation-skeleton-alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding)
            .height(220.dp)
            .clip(RoundedCornerShape(FoodTheme.dimens.radiusXl))
            .background(FoodTheme.colors.primaryLight.copy(alpha = alpha)),
    )
}

/** §25：读取失败，不展示技术异常信息 */
@Composable
private fun TodayRecommendationError(onRetry: () -> Unit) {
    FoodCard(
        modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FoodTheme.dimens.spaceXxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "暂时无法获取今日推荐",
                style = MaterialTheme.typography.bodyMedium,
                color = FoodTheme.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceLg))
            com.skyanchor.mealtime.core.ui.SecondaryButton(
                text = "重新加载",
                onClick = onRetry,
            )
        }
    }
}

/** §2：无有效菜谱的空态，按钮进入菜谱新增入口 */
@Composable
private fun TodayRecommendationEmpty(onAddRecipe: () -> Unit) {
    FoodCard(
        modifier = Modifier.padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        EmptyState(
            icon = Icons.Outlined.RestaurantMenu,
            title = "还没有推荐菜谱",
            hint = "先添加几道喜欢的菜吧",
            actionText = "去添加菜谱",
            onAction = onAddRecipe,
        )
    }
}

/** §22：加入哪一餐（早/午/晚单选，确认后创建 MealPlan） */
@Composable
private fun AddToMealDialog(
    defaultMealType: MealType,
    onDismiss: () -> Unit,
    onConfirm: (MealType) -> Unit,
) {
    var selected by remember { mutableStateOf(defaultMealType) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入哪一餐？") },
        text = {
            Column {
                MealType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FoodTheme.dimens.radiusMd))
                            .clickable { selected = type }
                            .padding(vertical = FoodTheme.dimens.spaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == type,
                            onClick = { selected = type },
                        )
                        Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceSm))
                        Text(
                            text = getMealTypeLabel(type),
                            style = MaterialTheme.typography.bodyLarge,
                            color = FoodTheme.colors.textPrimary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

// ===================== 今日三餐区域 =====================

@Composable
private fun TodayMealsSectionTitle() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WbSunny,
            contentDescription = null,
            tint = FoodTheme.colors.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "今日三餐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = FoodTheme.colors.textPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.clickable { },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "查看全部",
                style = MaterialTheme.typography.bodySmall,
                color = FoodTheme.colors.textTertiary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = FoodTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun MealCard(
    mealType: MealType,
    dishes: List<HomeMealDish>,
    isCurrent: Boolean,
    onManage: () -> Unit,
    onComplete: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    val borderColor = if (isCurrent) FoodTheme.colors.primary else Color.Transparent
    val cardBg = if (isCurrent) FoodTheme.colors.primarySoft.copy(alpha = 0.3f) else FoodTheme.colors.surface

    FoodCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding)
            .border(
                width = if (isCurrent) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(FoodTheme.dimens.radiusLg),
            ),
        backgroundColor = cardBg,
    ) {
        Column(modifier = Modifier.padding(FoodTheme.dimens.spaceLg)) {
            // 餐次标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isCurrent) FoodTheme.colors.primary else FoodTheme.colors.primarySoft,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = getMealTypeLabel(mealType),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrent) Color.White else FoodTheme.colors.primaryDark,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getMealTimeLabel(mealType),
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textTertiary,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isCurrent && dishes.isNotEmpty() && !dishes.all { it.isCompleted }) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(FoodTheme.colors.warning.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelSmall,
                            color = FoodTheme.colors.warning,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))

            if (dishes.isEmpty()) {
                // 空状态
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(FoodTheme.colors.primarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestaurantMenu,
                            contentDescription = null,
                            tint = FoodTheme.colors.primaryLight,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "还没有安排这一餐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FoodTheme.colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "根据你的库存和口味帮你选一道？",
                        style = MaterialTheme.typography.bodySmall,
                        color = FoodTheme.colors.textTertiary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    com.skyanchor.mealtime.core.ui.SecondaryButton(
                        text = "帮我安排",
                        onClick = onManage,
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                }
            } else {
                // 菜品列表 - 大图模式
                dishes.forEachIndexed { index, dish ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceSm))
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(FoodTheme.dimens.radiusImage))
                            .clickable { onOpenRecipe(dish.recipeId) },
                    ) {
                        // 菜品大图
                        AsyncImage(
                            model = dish.imageUri,
                            contentDescription = dish.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(FoodTheme.dimens.radiusImage)),
                            placeholder = painterResource(R.drawable.bg_recommendation),
                            error = painterResource(R.drawable.bg_recommendation),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dish.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FoodTheme.colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (dish.isCompleted) {
                                Text(
                                    text = "✓ 已完成",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FoodTheme.colors.success,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "家常 · 15分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = FoodTheme.colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))

                // 操作按钮
                val allCompleted = dishes.all { it.isCompleted }
                if (!allCompleted) {
                    if (isCurrent) {
                        // 当前餐次：主按钮完成 + 次按钮修改
                        Row(horizontalArrangement = Arrangement.spacedBy(FoodTheme.dimens.spaceSm)) {
                            com.skyanchor.mealtime.core.ui.SecondaryButton(
                                text = "修改",
                                onClick = onManage,
                                modifier = Modifier.weight(1f),
                            )
                            com.skyanchor.mealtime.core.ui.PrimaryButton(
                                text = "完成用餐",
                                onClick = onComplete,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        // 非当前餐次：只有修改按钮
                        com.skyanchor.mealtime.core.ui.SecondaryButton(
                            text = "修改菜品",
                            onClick = onManage,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun getMealTypeLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "早餐"
    MealType.LUNCH -> "午餐"
    MealType.DINNER -> "晚餐"
}

private fun getMealTimeLabel(type: MealType): String = when (type) {
    MealType.BREAKFAST -> "07:30"
    MealType.LUNCH -> "12:00"
    MealType.DINNER -> "18:30"
}

private fun getCurrentMealType(): MealType {
    val hour = LocalTime.now().hour
    return when {
        hour < 10 -> MealType.BREAKFAST
        hour < 15 -> MealType.LUNCH
        else -> MealType.DINNER
    }
}

// ===================== 智能推荐卡片 =====================

@Composable
private fun SmartRecommendationCard(onClick: () -> Unit) {
    FoodCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding)
            .clickable(onClick = onClick),
        backgroundColor = FoodTheme.colors.surfaceSoft,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FoodTheme.dimens.spaceXl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FoodTheme.colors.primaryLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    tint = FoodTheme.colors.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(FoodTheme.dimens.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "智能推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = FoodTheme.colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "根据你的库存和口味偏好，推荐今日菜谱",
                    style = MaterialTheme.typography.bodySmall,
                    color = FoodTheme.colors.textSecondary,
                )
            }
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(FoodTheme.colors.primary)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "一键安排",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// ===================== 今日备注卡片 =====================

@Composable
private fun TodayNoteCard(note: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FoodTheme.dimens.pageHorizontalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                tint = FoodTheme.colors.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "今日备注",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = FoodTheme.colors.textPrimary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = FoodTheme.colors.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoodTheme.colors.primary,
                )
            }
        }

        Spacer(modifier = Modifier.height(FoodTheme.dimens.spaceMd))

        FoodCard(
            modifier = Modifier.clickable(onClick = onClick),
            backgroundColor = FoodTheme.colors.surface,
        ) {
            Text(
                text = note.ifBlank { "记录一下今天的饮食计划、心情或其他想法吧..." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (note.isBlank()) FoodTheme.colors.textTertiary else FoodTheme.colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(FoodTheme.dimens.spaceLg),
            )
        }
    }
}

// ===================== 备注编辑对话框 =====================

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
