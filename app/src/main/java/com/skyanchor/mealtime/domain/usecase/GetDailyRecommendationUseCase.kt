package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.core.model.DailyRecommendation
import com.skyanchor.mealtime.core.model.DailyRecommendationItem
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.domain.repository.DailyRecommendationRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 今日推荐（规格文档 §3/§4/§26）：以自然日为周期懒生成。
 *
 * 当天已有推荐批次 → 直接读库，绝不重新随机；
 * 没有 → 在有效菜谱（未归档）中纯随机抽取最多 3 道并持久化。
 * 结果幂等：App 重启、页面切换、进程重建都不会改变当天结果。
 */
class GetDailyRecommendationUseCase(
    private val recommendationRepository: DailyRecommendationRepository,
    private val recipeRepository: RecipeRepository,
) {

    suspend operator fun invoke(today: LocalDate = LocalDate.now()): List<Recipe> {
        val dateKey = today.toString()
        val existing = recommendationRepository.getTodayRecommendation(dateKey)
        if (existing != null) {
            // §27：当天推荐记录保持稳定，仅隐藏已归档/删除的无效项
            return existing.items
                .mapNotNull { item -> recipeRepository.getRecipe(item.recipeId) }
                .filterNot { it.isArchived }
        }

        val candidates = recipeRepository.observeRecipes().first()
        if (candidates.isEmpty()) return emptyList()

        val picked = candidates.shuffled().take(MAX_COUNT)
        recommendationRepository.saveRecommendation(
            DailyRecommendation(
                recommendationDate = dateKey,
                createdAt = System.currentTimeMillis(),
                items = picked.mapIndexed { index, recipe ->
                    DailyRecommendationItem(recipeId = recipe.id, sortOrder = index)
                },
            ),
        )
        return picked
    }

    companion object {
        /** §2：有效菜谱 ≥3 时最多推荐 3 道 */
        const val MAX_COUNT = 3
    }
}
