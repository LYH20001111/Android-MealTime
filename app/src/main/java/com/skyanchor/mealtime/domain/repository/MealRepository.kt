package com.skyanchor.mealtime.domain.repository

import com.skyanchor.mealtime.core.model.ConsumptionDeduction
import com.skyanchor.mealtime.core.model.MealHistoryItem
import com.skyanchor.mealtime.core.model.MealPlan
import com.skyanchor.mealtime.core.model.MealRecord
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.RecentRecipeUsage
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface MealRepository {

    /** 某天全部餐次计划（含菜谱），早/午/晚按业务顺序 */
    fun observeMeals(date: LocalDate): Flow<List<MealPlan>>

    /** 某天某餐次的计划（含菜谱） */
    suspend fun getPlans(date: LocalDate, mealType: MealType): List<MealPlan>

    suspend fun getPlan(id: Long): MealPlan?

    suspend fun addPlan(
        date: LocalDate,
        mealType: MealType,
        recipeId: Long,
        servings: Int? = null,
    ): Long

    /** 换菜 */
    suspend fun replacePlan(planId: Long, newRecipeId: Long)

    suspend fun removePlan(planId: Long)

    /** 在同一餐次内上移/下移菜品：delta = -1 上移，+1 下移 */
    suspend fun movePlan(planId: Long, delta: Int)

    /** 完成用餐的 MealPlan 状态推进（完整扣库存在 CompleteMealUseCase，Phase 5） */
    suspend fun markPlanCompleted(planId: Long, completedAt: Long)

    /**
     * 完成用餐（单事务，R03/R04/R06）：写 MealRecord → 按扣减清单
     * FIFO 扣库存批次并逐批写 CONSUME 流水（不足部分如实补记）→
     * 耗尽批次自动归档 → 该餐次全部计划置为 COMPLETED。
     */
    suspend fun completeMeal(
        date: LocalDate,
        mealType: MealType,
        servings: Int?,
        deductions: List<ConsumptionDeduction>,
        note: String? = null,
    )

    suspend fun insertRecord(record: MealRecord): Long

    /** 历史用餐记录，按日期倒序；展示已完成菜品名（含已归档菜谱，R07） */
    fun observeHistory(): Flow<List<MealHistoryItem>>

    fun observeRecords(): Flow<List<MealRecord>>

    /** 推荐用：since 起菜谱被安排/食用的统计 */
    suspend fun getRecentUsage(since: LocalDate): List<RecentRecipeUsage>
}
