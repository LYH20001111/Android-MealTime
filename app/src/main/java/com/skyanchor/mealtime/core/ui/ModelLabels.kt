package com.skyanchor.mealtime.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.skyanchor.mealtime.R
import com.skyanchor.mealtime.core.model.IngredientTypes

/**
 * 食材种类显示名：内置种类按当前语言显示；自定义种类（键即名称）原样显示。
 * 注册表中的存量 label 为存储数据，本助手仅用于无注册表兜底时（详情页单条数据）。
 */
@Composable
fun ingredientTypeLabel(key: String): String = when (key) {
    IngredientTypes.INGREDIENT -> stringResource(R.string.ingredient_type_ingredient)
    IngredientTypes.SEASONING -> stringResource(R.string.ingredient_type_seasoning)
    IngredientTypes.OTHER -> stringResource(R.string.ingredient_type_other)
    else -> key
}

/**
 * 注册表（IngredientTypeInfo）种类的显示名：内置种类在未被用户改名时按当前语言显示；
 * 已改名或自定义种类显示存量 label（存储数据）。
 */
@Composable
fun ingredientTypeDisplayLabel(storedLabel: String, key: String): String =
    if ((key == IngredientTypes.INGREDIENT || key == IngredientTypes.SEASONING || key == IngredientTypes.OTHER) &&
        storedLabel == IngredientTypes.label(key)
    ) {
        ingredientTypeLabel(key)
    } else {
        storedLabel
    }
