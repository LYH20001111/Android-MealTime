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

/** 食材种类注册表（可在设置中增删）；key 为 ingredient.type 的存储值 */
@Entity(tableName = "ingredient_type")
data class IngredientTypeEntity(
    @PrimaryKey
    val key: String,
    val label: String,
    val sortOrder: Int = 0,
)
