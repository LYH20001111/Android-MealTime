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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InventoryDetailUiState(
    val isLoading: Boolean = true,
    val item: InventoryItem? = null,
    val adjustDone: Boolean = false,
)

class InventoryDetailViewModel(
    private val itemId: Long,
    private val inventoryRepository: InventoryRepository,
    private val adjustInventory: com.skyanchor.mealtime.domain.usecase.AdjustInventoryUseCase,
) : ViewModel() {

    private val adjustDone = MutableStateFlow(false)

    val uiState: StateFlow<InventoryDetailUiState> = combine(
        inventoryRepository.observeInventory(null),
        adjustDone,
    ) { all, done ->
        InventoryDetailUiState(
            isLoading = false,
            item = all.firstOrNull { it.id == itemId },
            adjustDone = done,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InventoryDetailUiState(isLoading = true),
    )

    /** 手动调整数量，必须写流水（在 Repository 事务内完成） */
    fun adjustQuantity(newQuantity: Double?) {
        viewModelScope.launch {
            adjustInventory(itemId, newQuantity)
            adjustDone.value = true
        }
    }

    fun consumeAdjustDone() {
        adjustDone.value = false
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            inventoryRepository.softDelete(itemId)
            onDeleted()
        }
    }

    companion object {
        fun factory(container: AppContainer, itemId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    InventoryDetailViewModel(
                        itemId = itemId,
                        inventoryRepository = container.inventoryRepository,
                        adjustInventory = com.skyanchor.mealtime.domain.usecase.AdjustInventoryUseCase(
                            container.inventoryRepository,
                        ),
                    )
                }
            }
    }
}
