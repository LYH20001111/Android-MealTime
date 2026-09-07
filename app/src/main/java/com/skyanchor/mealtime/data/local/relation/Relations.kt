package com.skyanchor.mealtime.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.skyanchor.mealtime.data.local.entity.DailyRecommendationEntity
import com.skyanchor.mealtime.data.local.entity.DailyRecommendationItemEntity
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.MealPlanEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity

data class RecipeIngredientWithIngredient(
    @Embedded val line: RecipeIngredientEntity,
    @Relation(parentColumn = "ingredientId", entityColumn = "id")
    val ingredient: IngredientEntity,
)

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(entity = RecipeIngredientEntity::class, parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<RecipeIngredientWithIngredient>,
)

data class InventoryWithIngredient(
    @Embedded val item: InventoryItemEntity,
    @Relation(parentColumn = "ingredientId", entityColumn = "id")
    val ingredient: IngredientEntity,
)

data class MealPlanWithRecipe(
    @Embedded val plan: MealPlanEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity,
)

data class DailyRecommendationWithItems(
    @Embedded val recommendation: DailyRecommendationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "recommendationId",
    )
    val items: List<DailyRecommendationItemEntity>,
)
