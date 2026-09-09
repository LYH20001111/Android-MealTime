package com.skyanchor.mealtime.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.ui.FoodTheme
import com.skyanchor.mealtime.feature.home.HomeScreen
import com.skyanchor.mealtime.feature.home.ConsumeConfirmScreen
import com.skyanchor.mealtime.feature.home.MealPlanScreen
import com.skyanchor.mealtime.feature.inventory.InventoryDetailScreen
import com.skyanchor.mealtime.feature.inventory.InventoryEditScreen
import com.skyanchor.mealtime.feature.inventory.InventoryListScreen
import com.skyanchor.mealtime.feature.inventory.ExpiringInventoryScreen
import com.skyanchor.mealtime.feature.profile.DataManagementScreen
import com.skyanchor.mealtime.feature.profile.HistoryScreen
import com.skyanchor.mealtime.feature.profile.ProfileScreen
import com.skyanchor.mealtime.feature.profile.SettingsScreen
import com.skyanchor.mealtime.feature.recipe.RecipeDetailScreen
import com.skyanchor.mealtime.feature.recipe.RecipeListScreen
import com.skyanchor.mealtime.feature.recipe.RecipeEditScreen
import com.skyanchor.mealtime.feature.recommend.RecommendScreen
import java.time.LocalDate

/**
 * 应用根组件：底部导航 + NavHost。
 * 二级页面（详情/编辑）隐藏底部导航，仅一级页面显示。
 */
@Composable
fun FoodApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = FoodTab.entries.any { it.route == currentRoute }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FoodTheme.colors.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = FoodTheme.colors.surface,
                    modifier = Modifier.height(64.dp),
                ) {
                    FoodTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (currentRoute == tab.route) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(FoodTheme.colors.primarySoft)
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    ) {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.label,
                                            tint = FoodTheme.colors.primary,
                                        )
                                    }
                                } else {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        tint = FoodTheme.colors.textTertiary,
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    color = if (currentRoute == tab.route) {
                                        FoodTheme.colors.primary
                                    } else {
                                        FoodTheme.colors.textTertiary
                                    },
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = FoodTheme.colors.primary,
                                selectedTextColor = FoodTheme.colors.primary,
                                unselectedIconColor = FoodTheme.colors.textTertiary,
                                unselectedTextColor = FoodTheme.colors.textTertiary,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = FoodTab.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(FoodTab.HOME.route) {
                HomeScreen(
                    onOpenMealPlan = { mealType ->
                        navController.navigate(Routes.mealPlan(LocalDate.now(), mealType))
                    },
                    onOpenRecipe = { id -> navController.navigate(Routes.recipeDetail(id)) },
                    onCompleteMeal = { mealType ->
                        navController.navigate(Routes.consumeConfirm(LocalDate.now(), mealType))
                    },
                    onOpenRecommend = { mealType -> navController.navigate(Routes.recommend(mealType)) },
                    onOpenExpiring = { navController.navigate(Routes.EXPIRING) },
                    onAddRecipe = { navController.navigate(Routes.recipeEdit(-1L)) },
                )
            }
            composable(FoodTab.RECIPE.route) {
                RecipeListScreen(
                    onOpenRecipe = { id -> navController.navigate(Routes.recipeDetail(id)) },
                    onAddRecipe = { navController.navigate(Routes.recipeEdit(-1L)) },
                )
            }
            composable(FoodTab.INVENTORY.route) {
                InventoryListScreen(
                    onOpenItem = { id -> navController.navigate(Routes.inventoryDetail(id)) },
                    onAddItem = { navController.navigate(Routes.inventoryEdit(-1L)) },
                )
            }
            composable(FoodTab.PROFILE.route) {
                ProfileScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDataManagement = { navController.navigate(Routes.DATA_MANAGEMENT) },
                )
            }

            composable(Routes.RECIPE_DETAIL) { entry ->
                val recipeId = entry.arguments?.getString("recipeId")?.toLongOrNull() ?: 0L
                RecipeDetailScreen(
                    recipeId = recipeId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.recipeEdit(recipeId)) },
                )
            }
            composable(Routes.RECIPE_EDIT) { entry ->
                val recipeId = entry.arguments?.getString("recipeId")?.toLongOrNull() ?: -1L
                RecipeEditScreen(
                    recipeId = recipeId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.INVENTORY_DETAIL) { entry ->
                val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: 0L
                InventoryDetailScreen(
                    itemId = itemId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.inventoryEdit(itemId)) },
                )
            }
            composable(Routes.INVENTORY_EDIT) { entry ->
                val itemId = entry.arguments?.getString("itemId")?.toLongOrNull() ?: -1L
                InventoryEditScreen(
                    itemId = itemId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.MEAL_PLAN) { entry ->
                val date = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                val mealType = entry.arguments?.getString("mealType")
                    ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                    ?: MealType.LUNCH
                MealPlanScreen(
                    date = date,
                    mealType = mealType,
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { id -> navController.navigate(Routes.recipeDetail(id)) },
                )
            }
            composable(Routes.CONSUME_CONFIRM) { entry ->
                val date = entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()
                val mealType = entry.arguments?.getString("mealType")
                    ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                    ?: MealType.LUNCH
                ConsumeConfirmScreen(
                    date = date,
                    mealType = mealType,
                    onBack = { navController.popBackStack() },
                    onCompleted = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.RECOMMEND,
                arguments = listOf(
                    navArgument("mealType") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val targetMeal = entry.arguments?.getString("mealType")
                    ?.let { runCatching { MealType.valueOf(it) }.getOrNull() }
                RecommendScreen(
                    targetMealType = targetMeal,
                    onBack = { navController.popBackStack() },
                    onOpenRecipe = { id -> navController.navigate(Routes.recipeDetail(id)) },
                )
            }
            composable(Routes.EXPIRING) {
                ExpiringInventoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenItem = { id -> navController.navigate(Routes.inventoryDetail(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DATA_MANAGEMENT) {
                DataManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
