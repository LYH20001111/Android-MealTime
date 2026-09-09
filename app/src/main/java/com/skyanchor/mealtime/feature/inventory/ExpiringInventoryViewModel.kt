package com.skyanchor.mealtime.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.domain.usecase.GetExpiringInventoryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 临期食材页状态：列表按到期日升序（含已过期批次） */
data class ExpiringInventoryUiState(
    val isLoading: Boolean = true,
    val items: List<InventoryItem> = emptyList(),
)

/** 临期食材列表：复用首页临期提醒同一条数据流，阈值读设置 */
class ExpiringInventoryViewModel(
    getExpiringInventory: GetExpiringInventoryUseCase,
) : ViewModel() {

    val uiState: StateFlow<ExpiringInventoryUiState> = getExpiringInventory()
        .map { ExpiringInventoryUiState(isLoading = false, items = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExpiringInventoryUiState(),
        )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ExpiringInventoryViewModel(
                    getExpiringInventory = GetExpiringInventoryUseCase(
                        container.inventoryRepository,
                        container.settingsRepository,
                    ),
                )
            }
        }
    }
}
