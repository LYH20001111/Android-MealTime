package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.skyanchor.mealtime.data.local.entity.DailyRecommendationEntity
import com.skyanchor.mealtime.data.local.entity.DailyRecommendationItemEntity
import com.skyanchor.mealtime.data.local.relation.DailyRecommendationWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyRecommendationDao {

    @Transaction
    @Query("SELECT * FROM daily_recommendation WHERE recommendationDate = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyRecommendationWithItems?

    @Transaction
    @Query("SELECT * FROM daily_recommendation WHERE recommendationDate = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyRecommendationWithItems?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecommendation(recommendation: DailyRecommendationEntity): Long

    @Insert
    suspend fun insertItems(items: List<DailyRecommendationItemEntity>)

    @Query("SELECT * FROM daily_recommendation_item WHERE recommendationId = :recommendationId ORDER BY sortOrder ASC")
    suspend fun getItemsByRecommendationId(recommendationId: Long): List<DailyRecommendationItemEntity>

    @Query("DELETE FROM daily_recommendation WHERE recommendationDate < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)
}
