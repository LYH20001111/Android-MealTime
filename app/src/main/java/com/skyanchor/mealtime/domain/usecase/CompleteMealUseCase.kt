package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.core.model.ConsumptionDeduction
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import java.time.LocalDate

/**
 * 完成用餐（R03/R05/R06/R10）：
 * 1. 读取该餐次全部【未完成】计划的食材明细（无明细的菜不产生扣减）；
 * 2. 预计消耗 = 配料数量；用户可逐项调整（key = "planId:ingredientName"）；
 * 3. 配料与食材字典解耦：按名称匹配库存里有批次（有效）的食材，
 *    食材页没有的配料不扣减（无库存可扣）；
 * 4. 按食材聚合后交给 Repository 在单事务内执行（扣库存 + 流水 + MealRecord + 状态）。
 *
 * @return 参与扣减的食材种数（0 表示本餐没有可扣减项）
 */
class CompleteMealUseCase(
    private val mealRepository: MealRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
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
        val stockedIdByName = HashMap<String, Long>()
        for (plan in pending) {
            val detail = recipeRepository.getRecipeDetail(plan.recipe.id) ?: continue
            detail.ingredients
                .filter { it.type != IngredientTypes.SEASONING && it.quantity != null }
                .forEach { line ->
                    // 名称 → 库存食材 id（0 = 食材页没有该名称的食材，无库存可扣）
                    val stockedId = stockedIdByName.getOrPut(line.name) {
                        ingredientRepository.getStockedIngredientByName(line.name)?.id ?: 0L
                    }
                    if (stockedId == 0L) return@forEach
                    val actual = adjustments["${plan.id}:${line.name}"] ?: line.quantity!!
                    val existing = aggregated[stockedId]
                    aggregated[stockedId] = if (existing == null) {
                        ConsumptionDeduction(
                            ingredientId = stockedId,
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
