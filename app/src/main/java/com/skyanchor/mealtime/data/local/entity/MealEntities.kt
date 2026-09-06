package com.skyanchor.mealtime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 计划用餐；date 存 ISO "yyyy-MM-dd" 字符串（可字典序比较） */
@Entity(
    tableName = "meal_plan",
    indices = [
        Index("date"),
        Index("recipeId"),
        Index("date", "mealType"),
    ],
)
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val mealType: String,
    val recipeId: Long,
    val servings: Int? = null,
    val sortOrder: Int = 0,
    val status: String = "PLANNED",
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 实际用餐记录；与 MealPlan 分离，不含 recipeId（R07） */
@Entity(
    tableName = "meal_record",
    indices = [Index("date"), Index("mealType")],
)
data class MealRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val mealType: String,
    val servings: Int? = null,
    val completedAt: Long? = null,
    val note: String? = null,
)
