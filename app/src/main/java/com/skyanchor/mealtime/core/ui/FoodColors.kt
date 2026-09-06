package com.skyanchor.mealtime.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 饭点 Design Token —— 颜色（UI_DESIGN.md §2）。
 * 业务页面禁止直接写魔法色值，一律通过 FoodTheme.colors 取用。
 */
@Immutable
data class FoodColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val primarySoft: Color,
    val background: Color,
    val surface: Color,
    val surfaceSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    /** 紫渐变，仅用于 CTA 按钮、FAB、推荐卡重点区域，禁止大面积铺底。 */
    val primaryGradient: Brush
        get() = Brush.horizontalGradient(listOf(gradientStart, gradientEnd))

    companion object {
        val Default = FoodColors(
            primary = Color(0xFF8B7CF6),
            primaryDark = Color(0xFF7164D9),
            primaryLight = Color(0xFFB8AFFB),
            primarySoft = Color(0xFFF0EEFF),
            background = Color(0xFFFAF9FD),
            surface = Color(0xFFFFFFFF),
            surfaceSoft = Color(0xFFF5F3FA),
            textPrimary = Color(0xFF2B2940),
            textSecondary = Color(0xFF77748A),
            textTertiary = Color(0xFFA09DAF),
            divider = Color(0xFFE9E6F0),
            success = Color(0xFF79BFA7),
            warning = Color(0xFFE6B56B),
            danger = Color(0xFFD9828B),
            gradientStart = Color(0xFFA79AF7),
            gradientEnd = Color(0xFF7C6BE8),
        )
    }
}

val LocalFoodColors = staticCompositionLocalOf { FoodColors.Default }
