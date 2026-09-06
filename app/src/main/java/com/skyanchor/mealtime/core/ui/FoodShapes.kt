package com.skyanchor.mealtime.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 饭点 Design Token —— 圆角（UI_DESIGN.md §5）：
 * 输入框 12 / 普通卡片 16 / 大推荐卡 20 / BottomSheet 顶 24 / Chip 胶囊 999。
 */
val FoodShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
