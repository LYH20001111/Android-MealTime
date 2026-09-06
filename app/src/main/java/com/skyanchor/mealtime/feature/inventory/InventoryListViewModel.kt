package com.skyanchor.mealtime.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.IngredientType
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import com.skyanchor.mealtime.domain.usecase.GetExpiringInventoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 库存列表 Tab：全部 / 临期 / 食材 / 调料（PAGES.md §6） */
enum class InventoryTab(val label: String) {
    ALL("全部"),
    EXPIRING("临期"),
    INGREDIENT("食材"),
    SEASONING("调料"),
}

data class InventoryListUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryItem> = emptyList(),
    val expiringCount: Int = 0,
    val tab: InventoryTab = InventoryTab.ALL,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryListViewModel(
    inventoryRepository: InventoryRepository,
    getExpiringInventory: GetExpiringInventoryUseCase,
) : ViewModel() {

    private val tab = MutableStateFlow(InventoryTab.ALL)

    val uiState: StateFlow<InventoryListUiState> = combine(
        inventoryRepository.observeInventory(null),
        getExpiringInventory(),
        inventoryRepository.observeInventory(IngredientType.INGREDIENT),
        inventoryRepository.observeInventory(IngredientType.SEASONING),
        tab,
    ) { all, expiring, ingredients, seasonings, currentTab ->
        val items = when (currentTab) {
            InventoryTab.ALL -> all
            InventoryTab.EXPIRING -> expiring
            InventoryTab.INGREDIENT -> ingredients
            InventoryTab.SEASONING -> seasonings
        }
        InventoryListUiState(
            isLoading = false,
            items = items,
            expiringCount = expiring.size,
            tab = currentTab,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryListUiState(isLoading = true),
    )

    fun selectTab(value: InventoryTab) {
        tab.value = value
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InventoryListViewModel(
                    inventoryRepository = container.inventoryRepository,
                    getExpiringInventory = GetExpiringInventoryUseCase(
                        container.inventoryRepository,
                        container.settingsRepository,
                    ),
                )
            }
        }
    }
}
