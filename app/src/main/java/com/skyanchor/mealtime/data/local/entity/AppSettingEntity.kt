package com.skyanchor.mealtime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 本地设置 KV 表 */
@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey
    val key: String,
    val value: String,
)
