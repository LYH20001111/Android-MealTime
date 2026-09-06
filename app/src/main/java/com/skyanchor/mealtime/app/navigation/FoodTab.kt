package com.skyanchor.mealtime.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

/** 底部导航一级路由，与设计文档 PAGES.md §1 的四大页面一一对应。 */
enum class FoodTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "首页", Icons.Outlined.Home),
    RECIPE("recipe", "菜谱", Icons.AutoMirrored.Outlined.MenuBook),
    INVENTORY("inventory", "食材", Icons.Outlined.Kitchen),
    PROFILE("profile", "我的", Icons.Outlined.Person),
}
