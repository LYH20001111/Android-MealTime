package com.skyanchor.mealtime.core.model

/** 菜谱分类 */
data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
)

/** 菜谱标签 */
data class Tag(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

/** 食材种类（可在设置中增删；内置 食材/调料，兜底 其他） */
data class IngredientTypeInfo(
    /** 存储键：ingredient.type / recipe_ingredient.ingredientType 的实际取值 */
    val key: String,
    val label: String,
    val sortOrder: Int = 0,
)

/** 删除分类时的兜底去向，不可删除 */
const val FALLBACK_CATEGORY_NAME = "其他"

/** 本地设置 KV */
data class AppSetting(
    val key: String,
    val value: String,
)
