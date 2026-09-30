package com.skyanchor.mealtime.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.skyanchor.mealtime.R

/** 底部导航一级路由，与设计文档 PAGES.md §1 的四大页面一一对应。 */
enum class FoodTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.tab_home, Icons.Outlined.Home),
    RECIPE("recipe", R.string.tab_recipe, Icons.AutoMirrored.Outlined.MenuBook),
    INVENTORY("inventory", R.string.tab_inventory, Icons.Outlined.Kitchen),
    PROFILE("profile", R.string.tab_profile, Icons.Outlined.Person),
}
