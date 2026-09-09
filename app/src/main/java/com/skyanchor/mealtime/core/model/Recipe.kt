package com.skyanchor.mealtime.core.model

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
}

val Difficulty.chineseLabel: String
    get() = when (this) {
        Difficulty.EASY -> "简单"
        Difficulty.MEDIUM -> "中等"
        Difficulty.HARD -> "困难"
    }

data class Recipe(
    val id: Long = 0,
    val name: String,
    val imageUri: String? = null,
    val categoryId: Long? = null,
    val difficulty: Difficulty = Difficulty.EASY,
    val cookingTimeMin: Int? = null,
    val description: String? = null,
    /** 制作步骤，有序列表 */
    val steps: List<String> = emptyList(),
    val note: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** 菜谱中的一行配料/调料：与食材字典解耦，只存名称与种类，用户可自由填写（R：不校验重名） */
data class RecipeIngredientLine(
    val id: Long = 0,
    val recipeId: Long = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    /** 食材种类键（IngredientTypes 或自定义种类），决定在哪个分区展示 */
    val type: String = IngredientTypes.INGREDIENT,
    val note: String? = null,
    val sortOrder: Int = 0,
)

data class RecipeDetail(
    val recipe: Recipe,
    val ingredients: List<RecipeIngredientLine>,
    val tags: List<Tag>,
)
