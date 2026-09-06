package com.skyanchor.mealtime.domain.usecase

import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import com.skyanchor.mealtime.domain.repository.SettingsKeys
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * 新增库存批次：食材先在字典中"选择或创建"，再入库并写 ADD 流水（R04）。
 */
class AddInventoryUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
) {
    suspend operator fun invoke(item: InventoryItem, note: String? = null): Long {
        val stored = ingredientRepository.getOrCreate(
            name = item.ingredient.name.trim(),
            type = item.ingredient.type,
            defaultUnit = item.unit,
            imageUri = item.ingredient.imageUri,
        )
        return inventoryRepository.addInventory(item.copy(ingredient = stored), note)
    }
}

/** 编辑库存批次；数量变化部分写 ADJUST 流水 */
class UpdateInventoryUseCase(
    private val inventoryRepository: InventoryRepository,
) {
    suspend operator fun invoke(item: InventoryItem, previousQuantity: Double?) =
        inventoryRepository.updateInventory(item, previousQuantity)
}

/** 人工调整数量并写 ADJUST 流水（FLOWS.md §8.5） */
class AdjustInventoryUseCase(
    private val inventoryRepository: InventoryRepository,
) {
    suspend operator fun invoke(itemId: Long, newQuantity: Double?) =
        inventoryRepository.adjustQuantity(itemId, newQuantity)
}

/**
 * 临期库存流：阈值读设置（默认 3 天），
 * 返回 expireDate ≤ today + 阈值 的批次（含已过期与紧急），按到期日升序。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GetExpiringInventoryUseCase(
    private val inventoryRepository: InventoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<List<InventoryItem>> =
        settingsRepository.observe(SettingsKeys.EXPIRY_NEAR_DAYS)
            .map { it?.toIntOrNull() ?: SettingsKeys.DEFAULT_EXPIRY_NEAR_DAYS }
            .flatMapLatest { nearDays ->
                inventoryRepository.observeExpiring(withinDays = nearDays)
            }
}
