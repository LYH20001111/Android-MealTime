package com.skyanchor.mealtime.core.model

import java.time.LocalDate

enum class MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
}

enum class MealStatus {
    PLANNED,
    COMPLETED,
}

/**
 * 计划用餐：一个餐次一条记录，一餐多菜即多条（R01）。
 */
data class MealPlan(
    val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val recipe: Recipe,
    val servings: Int? = null,
    val sortOrder: Int = 0,
    val status: MealStatus = MealStatus.PLANNED,
    val note: String? = null,
)

/** 实际用餐记录，与 MealPlan 分离；不含 recipeId 以保证菜谱归档后历史仍可见（R07） */
data class MealRecord(
    val id: Long = 0,
    val date: LocalDate,
    val mealType: MealType,
    val servings: Int? = null,
    val completedAt: Long? = null,
    val note: String? = null,
)

/** 推荐用的近期食用统计 */
data class RecentRecipeUsage(
    val recipeId: Long,
    val useCount: Int,
    val lastPlannedDate: LocalDate,
)

/**
 * 一次用餐确认后的单条库存扣减指令（按食材聚合）。
 * 由 CompleteMealUseCase 从菜谱配料计算，Repository 在单事务内执行。
 */
data class ConsumptionDeduction(
    val ingredientId: Long,
    val quantity: Double,
    val unit: String? = null,
)

/** 历史用餐记录（一餐），dishes 为当餐完成的菜品名（归档菜谱仍可见，R07） */
data class MealHistoryItem(
    val date: LocalDate,
    val mealType: MealType,
    val servings: Int?,
    val completedAt: Long?,
    val dishes: List<String>,
)
