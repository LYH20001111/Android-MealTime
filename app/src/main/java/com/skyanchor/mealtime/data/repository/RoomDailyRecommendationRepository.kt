package com.skyanchor.mealtime.data.repository

import com.skyanchor.mealtime.core.model.DailyRecommendation
import com.skyanchor.mealtime.core.model.DailyRecommendationItem
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.DailyRecommendationEntity
import com.skyanchor.mealtime.data.local.entity.DailyRecommendationItemEntity
import com.skyanchor.mealtime.domain.repository.DailyRecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDailyRecommendationRepository(
    private val db: MealTimeDatabase,
) : DailyRecommendationRepository {

    private val dao = db.dailyRecommendationDao()

    override fun observeTodayRecommendation(date: String): Flow<DailyRecommendation?> =
        dao.observeByDate(date).map { withItems ->
            withItems?.toDomain()
        }

    override suspend fun getTodayRecommendation(date: String): DailyRecommendation? =
        dao.getByDate(date)?.toDomain()

    override suspend fun saveRecommendation(recommendation: DailyRecommendation) {
        val entityId = dao.insertRecommendation(
            DailyRecommendationEntity(
                id = recommendation.id,
                recommendationDate = recommendation.recommendationDate,
                createdAt = recommendation.createdAt,
            ),
        )
        val items = recommendation.items.mapIndexed { index, item ->
            DailyRecommendationItemEntity(
                recommendationId = if (recommendation.id == 0L) entityId else recommendation.id,
                recipeId = item.recipeId,
                sortOrder = index,
            )
        }
        dao.insertItems(items)
    }

    private fun com.skyanchor.mealtime.data.local.relation.DailyRecommendationWithItems.toDomain(): DailyRecommendation =
        DailyRecommendation(
            id = recommendation.id,
            recommendationDate = recommendation.recommendationDate,
            createdAt = recommendation.createdAt,
            items = items
                .sortedBy { it.sortOrder }
                .map {
                    DailyRecommendationItem(
                        recipeId = it.recipeId,
                        sortOrder = it.sortOrder,
                    )
                },
        )
}
