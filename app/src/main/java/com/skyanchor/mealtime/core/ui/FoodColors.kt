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
            primary = Color(0xFF8068E8),
            primaryDark = Color(0xFF6F55C9),
            primaryLight = Color(0xFFEEE9FF),
            primarySoft = Color(0xFFF6F3FF),
            background = Color(0xFFF8F7FB),
            surface = Color(0xFFFFFFFF),
            surfaceSoft = Color(0xFFF0EBFF),
            textPrimary = Color(0xFF282432),
            textSecondary = Color(0xFF777181),
            textTertiary = Color(0xFFA49FAE),
            divider = Color(0xFFE9E6F0),
            success = Color(0xFF70B79B),
            warning = Color(0xFFF2A65A),
            danger = Color(0xFFE96A7B),
            gradientStart = Color(0xFF8E79E8),
            gradientEnd = Color(0xFFC5B7F7),
        )
    }
}

val LocalFoodColors = staticCompositionLocalOf { FoodColors.Default }
