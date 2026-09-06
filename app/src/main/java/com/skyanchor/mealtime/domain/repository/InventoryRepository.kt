package com.skyanchor.mealtime.domain.repository

import com.skyanchor.mealtime.core.model.IngredientType
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.InventoryTransaction
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {

    /** 全部有效库存，临期优先（无过期日期排最后） */
    fun observeInventory(type: IngredientType? = null): Flow<List<InventoryItem>>

    /** 未来 withinDays 天内到期的库存，按到期日升序 */
    fun observeExpiring(withinDays: Int): Flow<List<InventoryItem>>

    suspend fun getItem(id: Long): InventoryItem?

    /** 同一食材的多个有效批次（R09），按到期日升序 */
    suspend fun getBatches(ingredientId: Long): List<InventoryItem>

    /** 新增库存批次并写入 ADD 流水（同一事务，R04） */
    suspend fun addInventory(item: InventoryItem, note: String? = null): Long

    /**
     * 更新批次信息；若数量发生变化，写入 ADJUST 流水（FLOWS.md §8.5）。
     * @param previousQuantity 更新前的数量，用于计算流水增量
     */
    suspend fun updateInventory(item: InventoryItem, previousQuantity: Double?)

    /** 人工调整数量并写 ADJUST 流水；数量耗尽的批次自动归档（保留流水） */
    suspend fun adjustQuantity(itemId: Long, newQuantity: Double?)

    /** 归档批次（软删除），历史流水保留 */
    suspend fun softDelete(id: Long)

    fun observeTransactions(limit: Int = 50): Flow<List<InventoryTransaction>>

    fun observeIngredientTransactions(ingredientId: Long, limit: Int = 50): Flow<List<InventoryTransaction>>
}
