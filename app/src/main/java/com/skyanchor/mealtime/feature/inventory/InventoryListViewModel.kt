package com.skyanchor.mealtime.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import com.skyanchor.mealtime.domain.usecase.GetExpiringInventoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 固定 Tab 键：全部 / 临期（非种类筛选） */
object InventoryTabKeys {
    const val ALL = "__all"
    const val EXPIRING = "__expiring"
}

/** 库存列表 Tab：全部 / 临期固定，其余为设置里可配置的食材种类 */
data class InventoryTabUi(
    val key: String,
    val label: String,
)

data class InventoryListUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryItem> = emptyList(),
    val expiringCount: Int = 0,
    val tabs: List<InventoryTabUi> = listOf(
        InventoryTabUi(InventoryTabKeys.ALL, "全部"),
        InventoryTabUi(InventoryTabKeys.EXPIRING, "临期"),
    ),
    val selectedTabKey: String = InventoryTabKeys.ALL,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryListViewModel(
    inventoryRepository: InventoryRepository,
    ingredientRepository: IngredientRepository,
    getExpiringInventory: GetExpiringInventoryUseCase,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(InventoryTabKeys.ALL)

    val uiState: StateFlow<InventoryListUiState> = combine(
        inventoryRepository.observeInventory(null),
        getExpiringInventory(),
        ingredientRepository.observeTypes(),
        selectedTab,
    ) { all, expiring, types, tab ->
        // 正在浏览的种类被删除时回落到「全部」
        val effectiveTab = if (tab == InventoryTabKeys.ALL ||
            tab == InventoryTabKeys.EXPIRING ||
            types.any { it.key == tab }
        ) {
            tab
        } else {
            InventoryTabKeys.ALL
        }
        val items = when (effectiveTab) {
            InventoryTabKeys.ALL -> all
            InventoryTabKeys.EXPIRING -> expiring
            else -> all.filter { it.ingredient.type == effectiveTab }
        }
        InventoryListUiState(
            isLoading = false,
            items = items,
            expiringCount = expiring.size,
            tabs = listOf(
                InventoryTabUi(InventoryTabKeys.ALL, "全部"),
                InventoryTabUi(InventoryTabKeys.EXPIRING, "临期"),
            ) + types.map { InventoryTabUi(it.key, it.label) },
            selectedTabKey = effectiveTab,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryListUiState(isLoading = true),
    )

    fun selectTab(key: String) {
        selectedTab.value = key
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InventoryListViewModel(
                    inventoryRepository = container.inventoryRepository,
                    ingredientRepository = container.ingredientRepository,
                    getExpiringInventory = GetExpiringInventoryUseCase(
                        container.inventoryRepository,
                        container.settingsRepository,
                    ),
                )
            }
        }
    }
}
