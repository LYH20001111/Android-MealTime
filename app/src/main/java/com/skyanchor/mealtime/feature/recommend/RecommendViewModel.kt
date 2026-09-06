package com.skyanchor.mealtime.feature.recommend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recommendation
import com.skyanchor.mealtime.domain.usecase.CreateMealPlanUseCase
import com.skyanchor.mealtime.domain.usecase.RecommendRecipesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class RecommendUiState(
    val isLoading: Boolean = true,
    val items: List<Recommendation> = emptyList(),
    /** 建议加入的下一餐（按当前时间推算） */
    val nextMeal: MealType = MealType.LUNCH,
    /** 最近一次成功加入的提示 */
    val addedName: String? = null,
    val isRandom: Boolean = false,
)

class RecommendViewModel(
    private val recommendRecipes: RecommendRecipesUseCase,
    private val createMealPlan: CreateMealPlanUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendUiState())
    val uiState: StateFlow<RecommendUiState> = _uiState.asStateFlow()

    init {
        refresh()
        _uiState.update { it.copy(nextMeal = nextMealNow()) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRandom = false) }
            val items = recommendRecipes()
            _uiState.update { it.copy(isLoading = false, items = items) }
        }
    }

    fun randomPick() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val pick = recommendRecipes.randomPick()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRandom = true,
                    items = listOfNotNull(pick),
                )
            }
        }
    }

    fun addToMeal(recipeId: Long, mealType: MealType) {
        viewModelScope.launch {
            createMealPlan(mealType = mealType, recipeId = recipeId)
            val name = _uiState.value.items
                .firstOrNull { it.recipe.id == recipeId }?.recipe?.name ?: ""
            _uiState.update { it.copy(addedName = name) }
        }
    }

    fun consumeAdded() {
        _uiState.update { it.copy(addedName = null) }
    }

    private fun nextMealNow(): MealType {
        val hour = LocalTime.now().hour
        return when {
            hour < 11 -> MealType.BREAKFAST
            hour < 16 -> MealType.LUNCH
            else -> MealType.DINNER
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                RecommendViewModel(
                    recommendRecipes = RecommendRecipesUseCase(
                        container.recipeRepository,
                        container.inventoryRepository,
                        container.mealRepository,
                        container.settingsRepository,
                    ),
                    createMealPlan = CreateMealPlanUseCase(container.mealRepository),
                )
            }
        }
    }
}
