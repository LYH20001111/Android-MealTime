package com.skyanchor.mealtime.core.model

/** 标准食材字典中的类型：食材 / 调料 */
enum class IngredientType {
    INGREDIENT,
    SEASONING,
}

/**
 * 标准食材（Ingredient），表示"番茄"这一食材对象本身。
 * 与库存批次（InventoryItem）分离，是菜谱配料与库存之间的关联键。
 */
data class Ingredient(
    val id: Long = 0,
    val name: String,
    val type: IngredientType = IngredientType.INGREDIENT,
    val defaultUnit: String? = null,
    val category: String? = null,
    val icon: String? = null,
    /** 食材照片（file:// 私有路径），同一食材的各库存批次共享 */
    val imageUri: String? = null,
)
