package com.skyanchor.mealtime.data.repository

import androidx.room.withTransaction
import com.skyanchor.mealtime.core.model.ChangeSource
import com.skyanchor.mealtime.core.model.InventoryChangeType
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.InventoryTransaction
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity
import com.skyanchor.mealtime.data.mapper.toDomain
import com.skyanchor.mealtime.data.mapper.toEntity
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomInventoryRepository(private val db: MealTimeDatabase) : InventoryRepository {

    private val itemDao = db.inventoryItemDao()
    private val transactionDao = db.inventoryTransactionDao()

    override fun observeInventory(type: String?): Flow<List<InventoryItem>> =
        itemDao.observeInventory(type).map { list -> list.map { it.toDomain() } }

    override fun observeExpiring(withinDays: Int): Flow<List<InventoryItem>> =
        itemDao.observeExpiring(LocalDate.now().toEpochDay() + withinDays)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getItem(id: Long): InventoryItem? =
        itemDao.getWithIngredient(id)?.toDomain()

    override suspend fun getBatches(ingredientId: Long): List<InventoryItem> =
        itemDao.getByIngredient(ingredientId).mapNotNull { row ->
            // 单条查询联不上 Relation，这里按字段重组
            itemDao.getWithIngredient(row.id)?.toDomain()
        }

    override suspend fun addInventory(item: InventoryItem, note: String?): Long =
        db.withTransaction {
            val now = System.currentTimeMillis()
            val itemId = itemDao.insert(item.toEntity(createdAt = now, updatedAt = now))
            transactionDao.insert(
                InventoryTransactionEntity(
                    inventoryItemId = itemId,
                    ingredientId = item.ingredient.id,
                    changeQuantity = item.quantity,
                    unit = item.unit,
                    type = InventoryChangeType.ADD.name,
                    sourceType = ChangeSource.MANUAL.name,
                    sourceId = null,
                    note = note,
                    createdAt = now,
                )
            )
            itemId
        }

    override suspend fun updateInventory(item: InventoryItem, previousQuantity: Double?) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val createdAt = itemDao.getById(item.id)?.createdAt ?: now
            itemDao.update(item.toEntity(createdAt = createdAt, updatedAt = now))
            if (previousQuantity != item.quantity) {
                transactionDao.insert(
                    InventoryTransactionEntity(
                        inventoryItemId = item.id,
                        ingredientId = item.ingredient.id,
                        changeQuantity = (item.quantity ?: 0.0) - (previousQuantity ?: 0.0),
                        unit = item.unit,
                        type = InventoryChangeType.ADJUST.name,
                        sourceType = ChangeSource.MANUAL.name,
                        sourceId = null,
                        note = "编辑库存",
                        createdAt = now,
                    )
                )
            }
        }
    }

    override suspend fun adjustQuantity(itemId: Long, newQuantity: Double?) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val existing = itemDao.getById(itemId) ?: return@withTransaction
            itemDao.updateQuantity(itemId, newQuantity, now)
            transactionDao.insert(
                InventoryTransactionEntity(
                    inventoryItemId = itemId,
                    ingredientId = existing.ingredientId,
                    changeQuantity = (newQuantity ?: 0.0) - (existing.quantity ?: 0.0),
                    unit = existing.unit,
                    type = InventoryChangeType.ADJUST.name,
                    sourceType = ChangeSource.MANUAL.name,
                    sourceId = null,
                    note = "手动调整",
                    createdAt = now,
                )
            )
            itemDao.archiveDepleted(now)
        }
    }

    override suspend fun softDelete(id: Long) {
        itemDao.softDelete(id, System.currentTimeMillis())
    }

    override fun observeTransactions(limit: Int): Flow<List<InventoryTransaction>> =
        transactionDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeIngredientTransactions(ingredientId: Long, limit: Int): Flow<List<InventoryTransaction>> =
        transactionDao.observeByIngredient(ingredientId, limit).map { list -> list.map { it.toDomain() } }
}
