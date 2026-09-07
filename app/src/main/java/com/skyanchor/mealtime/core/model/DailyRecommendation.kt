package com.skyanchor.mealtime.core.model

/** 每日推荐中的一道菜 */
data class DailyRecommendationItem(
    val recipeId: Long,
    val sortOrder: Int,
)

/** 每日推荐批次 */
data class DailyRecommendation(
    val id: Long = 0,
    val recommendationDate: String,
    val createdAt: Long,
    val items: List<DailyRecommendationItem> = emptyList(),
)
