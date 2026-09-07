package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import java.time.LocalDate

/**
 * 保存菜谱：校验名称，配料行中的食材先在字典中"选择或创建"，
 * 再整单（主记录 + 配料 + 标签）事务落库。
 */
class SaveRecipeUseCase(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
) {

    /** @return 菜谱 id */
    suspend operator fun invoke(
        recipe: Recipe,
        ingredients: List<RecipeIngredientLine>,
        tagIds: List<Long>,
    ): Long {
        val name = recipe.name.trim()
        require(name.isNotEmpty()) { "菜名不能为空" }

        val resolved = ingredients
            .filter { it.ingredient.name.isNotBlank() }
            .map { line ->
                val stored = ingredientRepository.getOrCreate(
                    name = line.ingredient.name.trim(),
                    type = line.type,
                )
                line.copy(ingredient = stored)
            }
        return recipeRepository.saveRecipe(recipe.copy(name = name), resolved, tagIds)
    }
}

/** 归档菜谱（删除语义），历史用餐记录不受影响（R07） */
class ArchiveRecipeUseCase(
    private val recipeRepository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: Long) = recipeRepository.archiveRecipe(recipeId)
}

/** 把菜谱加入某天某餐次（详情页快捷入口与首页点菜共用） */
class CreateMealPlanUseCase(
    private val mealRepository: MealRepository,
) {
    suspend operator fun invoke(
        date: LocalDate = LocalDate.now(),
        mealType: MealType,
        recipeId: Long,
        servings: Int? = null,
    ): Long = mealRepository.addPlan(date, mealType, recipeId, servings)
}
