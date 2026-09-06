package com.skyanchor.mealtime.app.navigation

/** 二级路由。Phase 5+ 将补充 consume-confirm、recommend 等。 */
object Routes {
    const val RECIPE_DETAIL = "recipe/{recipeId}"
    const val RECIPE_EDIT = "recipe/edit/{recipeId}"
    const val INVENTORY_DETAIL = "inventory/{itemId}"
    const val INVENTORY_EDIT = "inventory/edit/{itemId}"
    const val MEAL_PLAN = "meal/{date}/{mealType}"
    const val CONSUME_CONFIRM = "consume-confirm/{date}/{mealType}"
    const val RECOMMEND = "recommend"
    const val SETTINGS = "settings"
    const val HISTORY = "history"

    fun recipeDetail(id: Long): String = "recipe/$id"

    /** id 传 -1 表示新增 */
    fun recipeEdit(id: Long): String = "recipe/edit/$id"

    fun inventoryDetail(id: Long): String = "inventory/$id"

    /** id 传 -1 表示新增 */
    fun inventoryEdit(id: Long): String = "inventory/edit/$id"

    fun mealPlan(date: java.time.LocalDate, mealType: com.skyanchor.mealtime.core.model.MealType): String =
        "meal/$date/${mealType.name}"

    fun consumeConfirm(date: java.time.LocalDate, mealType: com.skyanchor.mealtime.core.model.MealType): String =
        "consume-confirm/$date/${mealType.name}"
}
