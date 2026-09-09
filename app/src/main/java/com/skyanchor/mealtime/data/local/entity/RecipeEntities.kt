package com.skyanchor.mealtime.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val imageUri: String? = null,
    val categoryId: Long? = null,
    val difficulty: String = "EASY",
    val cookingTimeMin: Int? = null,
    val description: String? = null,
    /** 制作步骤，每行一步（换行分隔） */
    val steps: String? = null,
    val note: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("recipeId"),
    ],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recipeId: Long,
    /** 配料名称：与食材字典解耦，仅作展示/搜索/名称匹配，不写 ingredient 表 */
    val ingredientName: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val ingredientType: String,
    val note: String? = null,
    val sortOrder: Int = 0,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(tableName = "recipe_tag", primaryKeys = ["recipeId", "tagId"])
data class RecipeTagCrossRef(
    val recipeId: Long,
    val tagId: Long,
)
