package com.skyanchor.mealtime.core.model

/**
 * 食材种类的稳定存储键（ingredient.type / recipe_ingredient.ingredientType）。
 * 种类列表本身可在设置中增删，内置两类沿用历史键以兼容旧数据与备份；
 * 自定义种类的键即用户输入的名称。
 */
object IngredientTypes {
    const val INGREDIENT = "INGREDIENT"
    const val SEASONING = "SEASONING"
    const val OTHER = "OTHER"

    /** 兜底展示名：查不到注册表时（如详情页单条数据）也能给出中文名 */
    fun label(key: String): String = when (key) {
        INGREDIENT -> "食材"
        SEASONING -> "调料"
        OTHER -> "其他"
        else -> key
    }
}

/**
 * 标准食材（Ingredient），表示"番茄"这一食材对象本身。
 * 与库存批次（InventoryItem）分离，是菜谱配料与库存之间的关联键。
 */
data class Ingredient(
    val id: Long = 0,
    val name: String,
    val type: String = IngredientTypes.INGREDIENT,
    val defaultUnit: String? = null,
    val category: String? = null,
    val icon: String? = null,
    /** 食材照片（file:// 私有路径），同一食材的各库存批次共享 */
    val imageUri: String? = null,
)
