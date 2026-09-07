package com.skyanchor.mealtime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日推荐批次：一个自然日一条记录 */
@Entity(
    tableName = "daily_recommendation",
    indices = [Index(value = ["recommendationDate"], unique = true)],
)
data class DailyRecommendationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** ISO 日期字符串 "yyyy-MM-dd" */
    val recommendationDate: String,
    val createdAt: Long,
)

/** 推荐批次中的单道推荐菜 */
@Entity(
    tableName = "daily_recommendation_item",
    foreignKeys = [
        ForeignKey(
            entity = DailyRecommendationEntity::class,
            parentColumns = ["id"],
            childColumns = ["recommendationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("recommendationId"),
        Index("recipeId"),
    ],
)
data class DailyRecommendationItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recommendationId: Long,
    val recipeId: Long,
    val sortOrder: Int,
)
