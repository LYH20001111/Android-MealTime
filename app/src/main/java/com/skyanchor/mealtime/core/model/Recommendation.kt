package com.skyanchor.mealtime.core.model

/**
 * 一条推荐结果：菜谱 + 规则评分 + 人类可读的推荐理由（PRD §13 推荐页要素）。
 */
data class Recommendation(
    val recipe: Recipe,
    /** 0~100 左右的规则总分，越高越靠前 */
    val score: Int,
    val reasons: List<String>,
    /** 库存可覆盖的食材种数 / 菜谱食材总种数 */
    val matchedCount: Int,
    val totalIngredients: Int,
)
