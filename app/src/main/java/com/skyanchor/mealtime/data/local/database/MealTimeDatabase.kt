package com.skyanchor.mealtime.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.skyanchor.mealtime.data.local.dao.AppSettingDao
import com.skyanchor.mealtime.data.local.dao.CategoryDao
import com.skyanchor.mealtime.data.local.dao.IngredientDao
import com.skyanchor.mealtime.data.local.dao.InventoryItemDao
import com.skyanchor.mealtime.data.local.dao.InventoryTransactionDao
import com.skyanchor.mealtime.data.local.dao.MealPlanDao
import com.skyanchor.mealtime.data.local.dao.MealRecordDao
import com.skyanchor.mealtime.data.local.dao.RecipeDao
import com.skyanchor.mealtime.data.local.dao.TagDao
import com.skyanchor.mealtime.data.local.entity.AppSettingEntity
import com.skyanchor.mealtime.data.local.entity.CategoryEntity
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity
import com.skyanchor.mealtime.data.local.entity.MealPlanEntity
import com.skyanchor.mealtime.data.local.entity.MealRecordEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.local.entity.RecipeTagCrossRef
import com.skyanchor.mealtime.data.local.entity.TagEntity

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        RecipeIngredientEntity::class,
        InventoryItemEntity::class,
        InventoryTransactionEntity::class,
        MealPlanEntity::class,
        MealRecordEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        RecipeTagCrossRef::class,
        AppSettingEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MealTimeDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun mealPlanDao(): MealPlanDao
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DATABASE_NAME = "mealtime.db"

        /** 首次启动预置的菜谱分类 */
        val DEFAULT_CATEGORIES = listOf("家常菜", "汤羹", "主食", "凉菜", "甜点", "其他")
    }
}

object DatabaseFactory {

    fun create(context: Context, inMemory: Boolean = false): MealTimeDatabase {
        val builder = if (inMemory) {
            Room.inMemoryDatabaseBuilder(context, MealTimeDatabase::class.java)
        } else {
            Room.databaseBuilder(context, MealTimeDatabase::class.java, MealTimeDatabase.DATABASE_NAME)
        }
        return builder
            .addCallback(SeedCallback)
            // V1 未发布前允许破坏性升级（schema 变更直接重建）
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    /** 首次建库时预置默认分类（测试可用 inMemory 复用同一逻辑） */
    private object SeedCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            MealTimeDatabase.DEFAULT_CATEGORIES.forEachIndexed { index, name ->
                db.execSQL(
                    "INSERT OR IGNORE INTO category (name, icon, sortOrder) VALUES (?, NULL, ?)",
                    arrayOf<Any>(name, index),
                )
            }
        }
    }
}
