package com.skyanchor.mealtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 标准食材字典；软删除以保留历史菜谱-食材关联 */
@Entity(tableName = "ingredient")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val defaultUnit: String? = null,
    val category: String? = null,
    val icon: String? = null,
    val imageUri: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long,
)
