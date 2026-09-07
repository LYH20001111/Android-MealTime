package com.skyanchor.mealtime.domain.repository

import com.skyanchor.mealtime.core.model.DailyRecommendation
import kotlinx.coroutines.flow.Flow

interface DailyRecommendationRepository {
    fun observeTodayRecommendation(date: String): Flow<DailyRecommendation?>

    suspend fun getTodayRecommendation(date: String): DailyRecommendation?

    suspend fun saveRecommendation(recommendation: DailyRecommendation)
}
