package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.core.model.ConsumptionDeduction
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import java.time.LocalDate

/**
 * 完成用餐（R03/R05/R06/R10）：
 * 1. 读取该餐次全部【未完成】计划的食材明细（无明细的菜不产生扣减）；
 * 2. 预计消耗 = 配料数量；用户可逐项调整（key = "planId:ingredientId"）；
 * 3. 按食材聚合后交给 Repository 在单事务内执行（扣库存 + 流水 + MealRecord + 状态）。
 *
 * @return 参与扣减的食材种数（0 表示本餐没有可扣减项）
 */
class CompleteMealUseCase(
    private val mealRepository: MealRepository,
    private val recipeRepository: RecipeRepository,
) {

    suspend operator fun invoke(
        date: LocalDate,
        mealType: MealType,
        servings: Int? = null,
        adjustments: Map<String, Double> = emptyMap(),
        note: String? = null,
    ): Int {
        val plans = mealRepository.getPlans(date, mealType)
        val pending = plans.filter { it.status == MealStatus.PLANNED }
        if (plans.isEmpty()) throw IllegalStateException("该餐次还没有安排菜品")
        if (pending.isEmpty()) throw IllegalStateException("这餐已经完成过了，不用重复确认")

        val aggregated = LinkedHashMap<Long, ConsumptionDeduction>()
        for (plan in pending) {
            val detail = recipeRepository.getRecipeDetail(plan.recipe.id) ?: continue
            detail.ingredients
                .filter { it.type != IngredientTypes.SEASONING && it.quantity != null }
                .forEach { line ->
                    val actual = adjustments["${plan.id}:${line.ingredient.id}"] ?: line.quantity!!
                    val existing = aggregated[line.ingredient.id]
                    aggregated[line.ingredient.id] = if (existing == null) {
                        ConsumptionDeduction(
                            ingredientId = line.ingredient.id,
                            quantity = actual,
                            unit = line.unit,
                        )
                    } else {
                        existing.copy(quantity = existing.quantity + actual)
                    }
                }
        }
        mealRepository.completeMeal(date, mealType, servings, aggregated.values.toList(), note)
        return aggregated.size
    }
}
