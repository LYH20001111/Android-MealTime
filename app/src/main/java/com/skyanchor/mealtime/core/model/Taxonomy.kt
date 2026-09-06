package com.skyanchor.mealtime.core.model

/** 菜谱分类 */
data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
)

/** 菜谱标签 */
data class Tag(
    val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
)

/** 本地设置 KV */
data class AppSetting(
    val key: String,
    val value: String,
)
