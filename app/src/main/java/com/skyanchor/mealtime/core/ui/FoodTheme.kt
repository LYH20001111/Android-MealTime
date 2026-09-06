package com.skyanchor.mealtime.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = FoodColors.Default.primary,
    onPrimary = FoodColors.Default.surface,
    primaryContainer = FoodColors.Default.primarySoft,
    onPrimaryContainer = FoodColors.Default.primaryDark,
    secondary = FoodColors.Default.primaryDark,
    onSecondary = FoodColors.Default.surface,
    secondaryContainer = FoodColors.Default.primarySoft,
    onSecondaryContainer = FoodColors.Default.primaryDark,
    tertiary = FoodColors.Default.success,
    onTertiary = FoodColors.Default.surface,
    background = FoodColors.Default.background,
    onBackground = FoodColors.Default.textPrimary,
    surface = FoodColors.Default.surface,
    onSurface = FoodColors.Default.textPrimary,
    surfaceVariant = FoodColors.Default.surfaceSoft,
    onSurfaceVariant = FoodColors.Default.textSecondary,
    outline = FoodColors.Default.divider,
    outlineVariant = FoodColors.Default.divider,
    error = FoodColors.Default.danger,
    onError = FoodColors.Default.surface,
)

/**
 * 饭点主题入口。V1 仅提供浅色方案（设计文档只定义浅色 Token）；
 * 自定义颜色与间距通过 [FoodTheme.colors] / [FoodTheme.dimens] 取用，
 * Material 组件沿用 MaterialTheme 默认入口。
 */
@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalFoodColors provides FoodColors.Default,
        LocalFoodDimens provides FoodDimens(),
    ) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = FoodTypography,
            shapes = FoodShapes,
            content = content,
        )
    }
}

object FoodTheme {
    val colors: FoodColors
        @Composable get() = LocalFoodColors.current

    val dimens: FoodDimens
        @Composable get() = LocalFoodDimens.current
}
