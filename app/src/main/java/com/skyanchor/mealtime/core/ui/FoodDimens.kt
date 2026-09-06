package com.skyanchor.mealtime.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 饭点 Design Token —— 间距与圆角（UI_DESIGN.md §4/§5）。
 * 间距一律 4 的倍数；页面左右边距 16dp，卡片间 12dp，大分组 24dp。
 */
@Immutable
data class FoodDimens(
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 20.dp,
    val spaceXxl: Dp = 24.dp,
    val spaceXxxl: Dp = 32.dp,
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 16.dp,
    val radiusXl: Dp = 20.dp,
    val radiusPill: Dp = 999.dp,
    val radiusBottomSheet: Dp = 24.dp,
    val pageHorizontalPadding: Dp = 16.dp,
    val buttonHeight: Dp = 48.dp,
    val inputHeight: Dp = 52.dp,
)

val LocalFoodDimens = staticCompositionLocalOf { FoodDimens() }
