package com.skyanchor.mealtime.core.model

import java.time.LocalDate

/** 半精确库存：数字数量与模糊数量级别二选一或并存（PRD §7.3） */
enum class QuantityLevel {
    LOW,
    MODERATE,
    PLENTY,
}

val QuantityLevel.chineseLabel: String
    get() = when (this) {
        QuantityLevel.LOW -> "少量"
        QuantityLevel.MODERATE -> "适量"
        QuantityLevel.PLENTY -> "充足"
    }

/** 库存变化类型：入库 / 完成用餐消耗 / 人工调整 */
enum class InventoryChangeType {
    ADD,
    CONSUME,
    ADJUST,
}

/** 变化来源：完成用餐 / 手动操作 / 初始录入 */
enum class ChangeSource {
    MEAL,
    MANUAL,
    INITIAL,
}

/**
 * 库存批次（InventoryItem），表示"手上的一批某食材"。
 * 同一食材可存在多个批次、不同保质期（R09）。
 */
data class InventoryItem(
    val id: Long = 0,
    val ingredient: Ingredient,
    val quantity: Double? = null,
    val unit: String? = null,
    val quantityLevel: QuantityLevel? = null,
    val purchaseDate: LocalDate? = null,
    val productionDate: LocalDate? = null,
    val expireDate: LocalDate? = null,
    val location: String? = null,
    val note: String? = null,
)

/** 库存空批次：数字数量与数量级别均未记录，列表中归入「库存空」类 */
val InventoryItem.isEmptyStock: Boolean
    get() = quantity == null && quantityLevel == null

/**
 * 库存流水：一切库存变化（扣减、补货、人工调整）必须写入（R04、8.5）。
 */
data class InventoryTransaction(
    val id: Long = 0,
    val inventoryItemId: Long,
    val ingredientId: Long,
    val changeQuantity: Double? = null,
    val unit: String? = null,
    val type: InventoryChangeType,
    val sourceType: ChangeSource? = null,
    val sourceId: Long? = null,
    val note: String? = null,
    val createdAt: Long = 0,
)
