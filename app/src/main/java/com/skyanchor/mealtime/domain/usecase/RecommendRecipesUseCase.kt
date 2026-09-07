package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.Recommendation
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.repository.SettingsKeys
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.random.Random

/**
 * V1 规则推荐（不引入 AI，PRD §13）：
 *
 * score = inventoryMatch × 50      库存匹配率
 *       + expiringMatch × 40       临期食材命中（最多计 2 种）
 *       + favoriteBonus × 10       收藏
 *       + notRecentlyEaten × 10    最近 7 天没安排过
 *       - recentRepeat × 30        最近 3 天安排过
 *       - 5                        制作时长 > 30 分钟（偏好快手菜）
 */
class RecommendRecipesUseCase(
    private val recipeRepository: RecipeRepository,
    private val inventoryRepository: InventoryRepository,
    private val mealRepository: MealRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend operator fun invoke(limit: Int = 5, today: LocalDate = LocalDate.now()): List<Recommendation> {
        val recipes = recipeRepository.observeRecipes().first()
        if (recipes.isEmpty()) return emptyList()

        val nearDays = settingsRepository.getString(SettingsKeys.EXPIRY_NEAR_DAYS)
            ?.toIntOrNull() ?: SettingsKeys.DEFAULT_EXPIRY_NEAR_DAYS
        val expiring = inventoryRepository.observeExpiring(nearDays).first()
        val expiringIds = expiring.map { it.ingredient.id }.toSet()
        val expiringNames = expiring.map { it.ingredient.name }

        val stockedIds = inventoryRepository.observeInventory()
            .first()
            .mapNotNull { item ->
                // 数量级别批次（quantity 为 null）也算"有货"
                if (item.quantity == null || item.quantity > 0) item.ingredient.id else null
            }
            .toSet()

        val usageByRecipe = mealRepository.getRecentUsage(today.minusDays(7))
            .associateBy { it.recipeId }

        return recipes.map { recipe ->
            score(
                recipe = recipe,
                today = today,
                stockedIds = stockedIds,
                expiringIds = expiringIds,
                expiringNames = expiringNames,
                usageByRecipe = usageByRecipe,
                detailProvider = { recipeRepository.getRecipeDetail(recipe.id) },
            )
        }.sortedByDescending { it.score }.take(limit)
    }

    /** 随机挑一道：过滤已归档与最近 3 天安排过的（PRD §7.2 随机推荐） */
    suspend fun randomPick(today: LocalDate = LocalDate.now()): Recommendation? {
        val recipes = recipeRepository.observeRecipes().first()
        if (recipes.isEmpty()) return null
        val recent = mealRepository.getRecentUsage(today.minusDays(3)).map { it.recipeId }.toSet()
        val pool = recipes.filter { it.id !in recent }.ifEmpty { recipes }
        val picked = pool.random(Random.Default)
        return Recommendation(
            recipe = picked,
            score = 0,
            reasons = listOf("从菜谱库里随机为你挑选"),
            matchedCount = 0,
            totalIngredients = 0,
        )
    }

    internal suspend fun score(
        recipe: Recipe,
        today: LocalDate,
        stockedIds: Set<Long>,
        expiringIds: Set<Long>,
        expiringNames: List<String>,
        usageByRecipe: Map<Long, com.skyanchor.mealtime.core.model.RecentRecipeUsage>,
        detailProvider: suspend () -> com.skyanchor.mealtime.core.model.RecipeDetail?,
    ): Recommendation {
        val detail = detailProvider()
        val lines = detail?.ingredients
            ?.filter { it.type != IngredientTypes.SEASONING && it.quantity != null }
            ?: emptyList()

        val matched = lines.count { it.ingredient.id in stockedIds }
        val matchRatio = if (lines.isEmpty()) 0.0 else matched.toDouble() / lines.size
        val expiringHits = lines.filter { it.ingredient.id in expiringIds }

        var score = matchRatio * 50
        score += (minOf(expiringHits.size, 2) / 2.0) * 40
        if (recipe.isFavorite) score += 10

        val usage = usageByRecipe[recipe.id]
        val eatenWithin3Days = usage != null && !usage.lastPlannedDate.isBefore(today.minusDays(3))
        val notEatenForWeek = usage == null
        when {
            notEatenForWeek -> score += 10
            eatenWithin3Days -> score -= 30
        }
        if ((recipe.cookingTimeMin ?: 0) > 30) score -= 5

        val reasons = buildList {
            when {
                lines.isEmpty() -> Unit
                matched == lines.size -> add("✓ 库存充足，现在就能做")
                matched > 0 -> add("✓ 库存可覆盖 $matched/${lines.size} 种食材")
                else -> add("△ 库存里还没有它的食材")
            }
            if (expiringHits.isNotEmpty()) {
                val hitNames = expiringHits.mapNotNull { line ->
                    expiringNames.firstOrNull { it == line.ingredient.name }
                }.ifEmpty { expiringHits.map { it.ingredient.name } }
                add("✓ 能消耗临期食材：${hitNames.joinToString("、")}")
            }
            if (recipe.isFavorite) add("✓ 你的收藏")
            when {
                notEatenForWeek -> add("✓ 最近 7 天没有吃过")
                eatenWithin3Days -> add("△ 最近 3 天刚安排过")
            }
        }

        return Recommendation(
            recipe = recipe,
            score = score.toInt(),
            reasons = reasons,
            matchedCount = matched,
            totalIngredients = lines.size,
        )
    }
}
