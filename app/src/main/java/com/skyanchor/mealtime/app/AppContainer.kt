package com.skyanchor.mealtime.app

import android.content.Context
import com.skyanchor.mealtime.data.local.database.DatabaseFactory
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.repository.RoomBackupRepository
import com.skyanchor.mealtime.data.repository.RoomIngredientRepository
import com.skyanchor.mealtime.data.repository.RoomInventoryRepository
import com.skyanchor.mealtime.data.repository.RoomMealRepository
import com.skyanchor.mealtime.data.repository.RoomRecipeRepository
import com.skyanchor.mealtime.data.repository.RoomSettingsRepository
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手动依赖容器（V1 不引入 DI 框架）。
 * 数据库为进程级单例，仓库共享同一实例以获得事务一致性。
 */
class AppContainer(appContext: Context) {

    private val context: Context = appContext.applicationContext

    val database: MealTimeDatabase by lazy { DatabaseFactory.create(context) }

    val recipeRepository: RecipeRepository by lazy { RoomRecipeRepository(database) }
    val ingredientRepository: IngredientRepository by lazy { RoomIngredientRepository(database) }
    val inventoryRepository: InventoryRepository by lazy { RoomInventoryRepository(database) }
    val mealRepository: MealRepository by lazy { RoomMealRepository(database) }
    val settingsRepository: SettingsRepository by lazy { RoomSettingsRepository(database.appSettingDao()) }
    val backupRepository: com.skyanchor.mealtime.domain.repository.BackupRepository by lazy {
        RoomBackupRepository(database)
    }

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
