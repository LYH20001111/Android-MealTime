package com.skyanchor.mealtime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.skyanchor.mealtime.data.local.entity.MealPlanEntity
import com.skyanchor.mealtime.data.local.entity.MealRecordEntity
import com.skyanchor.mealtime.data.local.relation.MealPlanWithRecipe
import kotlinx.coroutines.flow.Flow

/** 推荐算法使用的近期食用统计 POJO */
data class RecentRecipeUsageRow(
    val recipeId: Long,
    val useCount: Int,
    val lastPlannedDate: String,
)

@Dao
interface MealPlanDao {

    /** 某天的全部餐次，早/午/晚按业务顺序排列（不能按字母序） */
    @Transaction
    @Query(
        """
        SELECT * FROM meal_plan
        WHERE date = :date
        ORDER BY CASE mealType
                     WHEN 'BREAKFAST' THEN 0
                     WHEN 'LUNCH' THEN 1
                     ELSE 2
                 END, sortOrder, id
        """
    )
    fun observeByDate(date: String): Flow<List<MealPlanWithRecipe>>

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun getById(id: Long): MealPlanEntity?

    @Transaction
    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun getWithRecipe(id: Long): MealPlanWithRecipe?

    @Query("SELECT * FROM meal_plan WHERE date = :date AND mealType = :mealType AND status = :status")
    suspend fun getByDateAndMeal(date: String, mealType: String, status: String): List<MealPlanEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM meal_plan
        WHERE date = :date AND mealType = :mealType
        ORDER BY sortOrder, id
        """
    )
    suspend fun getByDateAndMealWithRecipe(date: String, mealType: String): List<MealPlanWithRecipe>

    /** 完成用餐：该餐次全部 PLANNED → COMPLETED */
    @Query(
        """
        UPDATE meal_plan SET status = 'COMPLETED', updatedAt = :now
        WHERE date = :date AND mealType = :mealType AND status = 'PLANNED'
        """
    )
    suspend fun completeMealPlans(date: String, mealType: String, now: Long)

    @Insert
    suspend fun insert(plan: MealPlanEntity): Long

    @Update
    suspend fun update(plan: MealPlanEntity)

    @Delete
    suspend fun delete(plan: MealPlanEntity)

    @Query("UPDATE meal_plan SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long)

    @Query(
        """
        SELECT recipeId AS recipeId, COUNT(*) AS useCount, MAX(date) AS lastPlannedDate
        FROM meal_plan
        WHERE date >= :since
        GROUP BY recipeId
        """
    )
    suspend fun getRecentUsage(since: String): List<RecentRecipeUsageRow>

    @Query("SELECT * FROM meal_plan")
    suspend fun exportAllPlans(): List<MealPlanEntity>
}

/** 历史记录联表行：用餐记录 + 当餐完成菜品名（菜谱归档后名字仍可读，R07） */
data class MealHistoryRow(
    val recordId: Long,
    val date: String,
    val mealType: String,
    val servings: Int?,
    val completedAt: Long?,
    val recipeName: String?,
)

@Dao
interface MealRecordDao {

    @Insert
    suspend fun insert(record: MealRecordEntity): Long

    @Query("SELECT * FROM meal_record ORDER BY date DESC, completedAt DESC")
    fun observeAll(): Flow<List<MealRecordEntity>>

    @Query("SELECT * FROM meal_record WHERE date = :date")
    suspend fun getByDate(date: String): List<MealRecordEntity>

    @Query(
        """
        SELECT mr.id AS recordId, mr.date AS date, mr.mealType AS mealType,
               mr.servings AS servings, mr.completedAt AS completedAt,
               r.name AS recipeName
        FROM meal_record mr
        LEFT JOIN meal_plan mp
               ON mp.date = mr.date AND mp.mealType = mr.mealType AND mp.status = 'COMPLETED'
        LEFT JOIN recipe r ON r.id = mp.recipeId
        ORDER BY mr.date DESC, mr.completedAt DESC, mr.id DESC
        """
    )
    fun observeHistory(): Flow<List<MealHistoryRow>>

    @Query("SELECT * FROM meal_record")
    suspend fun exportAllRecords(): List<MealRecordEntity>
}
