package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity
import com.skyanchor.mealtime.data.local.relation.InventoryWithIngredient
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {

    /** 全部库存，临期优先（无过期日期的排最后） */
    @Transaction
    @Query(
        """
        SELECT inventory_item.* FROM inventory_item
        JOIN ingredient ON ingredient.id = inventory_item.ingredientId
        WHERE inventory_item.isDeleted = 0
          AND ingredient.isDeleted = 0
          AND (:type IS NULL OR ingredient.type = :type)
        ORDER BY CASE WHEN inventory_item.expireDate IS NULL THEN 1 ELSE 0 END,
                 inventory_item.expireDate ASC,
                 inventory_item.id DESC
        """
    )
    fun observeInventory(type: String?): Flow<List<InventoryWithIngredient>>

    /** 临期库存：expireDate 早于等于阈值 epoch day，按到期日升序 */
    @Transaction
    @Query(
        """
        SELECT inventory_item.* FROM inventory_item
        JOIN ingredient ON ingredient.id = inventory_item.ingredientId
        WHERE inventory_item.isDeleted = 0
          AND ingredient.isDeleted = 0
          AND inventory_item.expireDate IS NOT NULL
          AND inventory_item.expireDate <= :thresholdEpochDay
        ORDER BY inventory_item.expireDate ASC
        """
    )
    fun observeExpiring(thresholdEpochDay: Long): Flow<List<InventoryWithIngredient>>

    @Transaction
    @Query("SELECT * FROM inventory_item WHERE id = :id")
    suspend fun getWithIngredient(id: Long): InventoryWithIngredient?

    @Query("SELECT * FROM inventory_item WHERE id = :id")
    suspend fun getById(id: Long): InventoryItemEntity?

    @Query(
        """
        SELECT * FROM inventory_item
        WHERE ingredientId = :ingredientId AND isDeleted = 0
        ORDER BY CASE WHEN expireDate IS NULL THEN 1 ELSE 0 END, expireDate ASC
        """
    )
    suspend fun getByIngredient(ingredientId: Long): List<InventoryItemEntity>

    @Insert
    suspend fun insert(item: InventoryItemEntity): Long

    @Update
    suspend fun update(item: InventoryItemEntity)

    @Query("UPDATE inventory_item SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE inventory_item SET quantity = :quantity, updatedAt = :now WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double?, now: Long)

    /** 数量耗尽（<=0）的批次自动归档，保留流水（FLOWS.md §8.3） */
    @Query(
        """
        UPDATE inventory_item SET isDeleted = 1, updatedAt = :now
        WHERE isDeleted = 0 AND quantity IS NOT NULL AND quantity <= 0
        """
    )
    suspend fun archiveDepleted(now: Long)

    @Query("SELECT * FROM inventory_item")
    suspend fun exportAllItems(): List<InventoryItemEntity>
}

@Dao
interface InventoryTransactionDao {

    @Insert
    suspend fun insert(transaction: InventoryTransactionEntity): Long

    @Query("SELECT * FROM inventory_transaction WHERE ingredientId = :ingredientId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeByIngredient(ingredientId: Long, limit: Int): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transaction ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<InventoryTransactionEntity>>

    @Query("SELECT * FROM inventory_transaction")
    suspend fun exportAll(): List<InventoryTransactionEntity>
}
