package com.skyanchor.mealtime.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 库存批次。日期字段存 epoch day（Long，可比较、可建索引）；
 * 业务层与 LocalDate 互转。
 */
@Entity(
    tableName = "inventory_item",
    indices = [
        Index("ingredientId"),
        Index("expireDate"),
    ],
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ingredientId: Long,
    val quantity: Double? = null,
    val unit: String? = null,
    val quantityLevel: String? = null,
    val purchaseDate: Long? = null,
    val productionDate: Long? = null,
    val expireDate: Long? = null,
    val location: String? = null,
    val note: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 库存流水：一切库存变化必须落一条流水（R04） */
@Entity(
    tableName = "inventory_transaction",
    indices = [
        Index("inventoryItemId"),
        Index("ingredientId"),
        Index("createdAt"),
    ],
)
data class InventoryTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val inventoryItemId: Long,
    val ingredientId: Long,
    val changeQuantity: Double? = null,
    val unit: String? = null,
    val type: String,
    val sourceType: String? = null,
    val sourceId: Long? = null,
    val note: String? = null,
    val createdAt: Long,
)
