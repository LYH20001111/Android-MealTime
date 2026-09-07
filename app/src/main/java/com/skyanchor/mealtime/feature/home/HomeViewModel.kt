package com.skyanchor.mealtime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import com.skyanchor.mealtime.domain.usecase.GetExpiringInventoryUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 餐次中的一道菜 */
data class HomeMealDish(
    val planId: Long,
    val recipeId: Long,
    val name: String,
    val imageUri: String? = null,
    val isCompleted: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val expiring: List<InventoryItem> = emptyList(),
    val expiringCount: Int = 0,
    val breakfast: List<HomeMealDish> = emptyList(),
    val lunch: List<HomeMealDish> = emptyList(),
    val dinner: List<HomeMealDish> = emptyList(),
    val note: String = "",
)

class HomeViewModel(
    mealRepository: MealRepository,
    getExpiringInventory: GetExpiringInventoryUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val uiState: StateFlow<HomeUiState> = combine(
        mealRepository.observeMeals(today),
        getExpiringInventory(),
        settingsRepository.observe(noteKey(today)),
    ) { plans, expiring, note ->
        fun dishesOf(type: MealType) = plans
            .filter { it.mealType == type }
            .map { plan ->
                HomeMealDish(
                    planId = plan.id,
                    recipeId = plan.recipe.id,
                    name = plan.recipe.name,
                    imageUri = plan.recipe.imageUri,
                    isCompleted = plan.status == MealStatus.COMPLETED,
                )
            }

        HomeUiState(
            isLoading = false,
            date = today,
            expiring = expiring,
            expiringCount = expiring.size,
            breakfast = dishesOf(MealType.BREAKFAST),
            lunch = dishesOf(MealType.LUNCH),
            dinner = dishesOf(MealType.DINNER),
            note = note ?: "",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
    )

    fun saveNote(text: String) {
        viewModelScope.launch {
            settingsRepository.putString(noteKey(today), text.trim())
        }
    }

    companion object {
        const val NOTE_KEY_PREFIX = "note:"

        fun noteKey(date: LocalDate): String = NOTE_KEY_PREFIX + date

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    mealRepository = container.mealRepository,
                    getExpiringInventory = GetExpiringInventoryUseCase(
                        container.inventoryRepository,
                        container.settingsRepository,
                    ),
                    settingsRepository = container.settingsRepository,
                )
            }
        }
    }
}
