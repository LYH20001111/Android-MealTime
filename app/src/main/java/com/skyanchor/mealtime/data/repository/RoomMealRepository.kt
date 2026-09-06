package com.skyanchor.mealtime.data.repository

import androidx.room.withTransaction
import com.skyanchor.mealtime.core.model.ConsumptionDeduction
import com.skyanchor.mealtime.core.model.MealHistoryItem
import com.skyanchor.mealtime.core.model.MealPlan
import com.skyanchor.mealtime.core.model.MealRecord
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.RecentRecipeUsage
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.mapper.toDomain
import com.skyanchor.mealtime.data.mapper.toEntity
import com.skyanchor.mealtime.data.mapper.toEnumOrDefault
import com.skyanchor.mealtime.domain.repository.MealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomMealRepository(private val db: MealTimeDatabase) : MealRepository {

    private val planDao = db.mealPlanDao()
    private val recordDao = db.mealRecordDao()

    override fun observeMeals(date: LocalDate): Flow<List<MealPlan>> =
        planDao.observeByDate(date.toString()).map { list -> list.map { it.toDomain() } }

    override suspend fun getPlan(id: Long): MealPlan? =
        planDao.getWithRecipe(id)?.toDomain()

    override suspend fun getPlans(date: LocalDate, mealType: MealType): List<MealPlan> =
        planDao.getByDateAndMealWithRecipe(date.toString(), mealType.name).map { it.toDomain() }

    override suspend fun completeMeal(
        date: LocalDate,
        mealType: MealType,
        servings: Int?,
        deductions: List<ConsumptionDeduction>,
        note: String?,
    ) = db.withTransaction {
        val now = System.currentTimeMillis()
        val recordId = recordDao.insert(
            com.skyanchor.mealtime.data.local.entity.MealRecordEntity(
                date = date.toString(),
                mealType = mealType.name,
                servings = servings,
                completedAt = now,
                note = note,
            )
        )
        val itemDao = db.inventoryItemDao()
        val transactionDao = db.inventoryTransactionDao()

        for (deduction in deductions) {
            val batches = itemDao.getByIngredient(deduction.ingredientId)
            var remaining = deduction.quantity
            for (batch in batches) {
                if (remaining <= 0.0) break
                val available = batch.quantity ?: continue
                if (available <= 0.0) continue
                val take = minOf(available, remaining)
                itemDao.update(batch.copy(quantity = available - take, updatedAt = now))
                transactionDao.insert(
                    com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity(
                        inventoryItemId = batch.id,
                        ingredientId = deduction.ingredientId,
                        changeQuantity = -take,
                        unit = deduction.unit ?: batch.unit,
                        type = "CONSUME",
                        sourceType = "MEAL",
                        sourceId = recordId,
                        note = null,
                        createdAt = now,
                    )
                )
                remaining -= take
            }
            if (remaining > 0.0) {
                // 库存不足：按实际消耗如实补记流水（R04），不阻塞用餐确认
                transactionDao.insert(
                    com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity(
                        inventoryItemId = batches.firstOrNull()?.id ?: 0L,
                        ingredientId = deduction.ingredientId,
                        changeQuantity = -remaining,
                        unit = deduction.unit,
                        type = "CONSUME",
                        sourceType = "MEAL",
                        sourceId = recordId,
                        note = "库存不足，超出部分如实记录",
                        createdAt = now,
                    )
                )
            }
        }
        itemDao.archiveDepleted(now)
        planDao.completeMealPlans(date.toString(), mealType.name, now)
    }

    override suspend fun addPlan(
        date: LocalDate,
        mealType: MealType,
        recipeId: Long,
        servings: Int?,
    ): Long {
        val now = System.currentTimeMillis()
        return planDao.insert(
            com.skyanchor.mealtime.data.local.entity.MealPlanEntity(
                date = date.toString(),
                mealType = mealType.name,
                recipeId = recipeId,
                servings = servings,
                status = MealStatus.PLANNED.name,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun replacePlan(planId: Long, newRecipeId: Long) {
        val existing = planDao.getById(planId) ?: return
        planDao.update(existing.copy(recipeId = newRecipeId, updatedAt = System.currentTimeMillis()))
    }

    override suspend fun removePlan(planId: Long) {
        val existing = planDao.getById(planId) ?: return
        planDao.delete(existing)
    }

    override suspend fun movePlan(planId: Long, delta: Int) = db.withTransaction {
        val plan = planDao.getById(planId) ?: return@withTransaction
        val siblings = planDao
            .getByDateAndMeal(plan.date, plan.mealType, "PLANNED")
            .sortedBy { it.sortOrder }
        val index = siblings.indexOfFirst { it.id == planId }
        if (index < 0) return@withTransaction
        val newIndex = (index + delta).coerceIn(0, siblings.size - 1)
        if (newIndex == index) return@withTransaction
        val now = System.currentTimeMillis()
        siblings.toMutableList()
            .apply { add(newIndex, removeAt(index)) }
            .forEachIndexed { position, entity ->
                if (entity.sortOrder != position) {
                    planDao.update(entity.copy(sortOrder = position, updatedAt = now))
                }
            }
    }

    override suspend fun markPlanCompleted(planId: Long, completedAt: Long) {
        planDao.updateStatus(planId, MealStatus.COMPLETED.name, completedAt)
    }

    override suspend fun insertRecord(record: MealRecord): Long = recordDao.insert(record.toEntity())

    override fun observeHistory(): Flow<List<MealHistoryItem>> =
        recordDao.observeHistory().map { rows ->
            rows.groupBy { it.recordId }.map { (_, group) ->
                val first = group.first()
                MealHistoryItem(
                    date = LocalDate.parse(first.date),
                    mealType = first.mealType.toEnumOrDefault(MealType.LUNCH),
                    servings = first.servings,
                    completedAt = first.completedAt,
                    dishes = group.mapNotNull { it.recipeName },
                )
            }
        }

    override fun observeRecords(): Flow<List<MealRecord>> =
        recordDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getRecentUsage(since: LocalDate): List<RecentRecipeUsage> =
        planDao.getRecentUsage(since.toString()).map { row ->
            RecentRecipeUsage(
                recipeId = row.recipeId,
                useCount = row.useCount,
                lastPlannedDate = LocalDate.parse(row.lastPlannedDate),
            )
        }
}
