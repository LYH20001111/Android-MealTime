package com.skyanchor.mealtime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.SettingsKeys
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.usecase.CompleteMealUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 一行可调整的预计消耗 */
data class ConsumeLineUi(
    val planId: Long,
    val ingredientId: Long,
    val name: String,
    val unit: String?,
    val expected: Double,
    val actualText: String,
)

data class ConsumeDishUi(
    val recipeId: Long,
    val name: String,
    val lines: List<ConsumeLineUi>,
) {
    val hasDeductions: Boolean get() = lines.isNotEmpty()
}

data class ConsumeConfirmUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val mealType: MealType = MealType.LUNCH,
    val dishes: List<ConsumeDishUi> = emptyList(),
    val servingsText: String = "",
    val isSaving: Boolean = false,
    val saveError: String? = null,
    /** 该餐次已全部完成：无可确认项 */
    val alreadyCompleted: Boolean = false,
)

/**
 * 完成用餐确认（PAGES.md §8）：展示按菜谱计算的预计消耗，允许逐项修改实际值，
 * 确认后由 CompleteMealUseCase 单事务落库。
 */
class ConsumeConfirmViewModel(
    private val date: LocalDate,
    private val mealType: MealType,
    private val mealRepository: MealRepository,
    private val recipeRepository: RecipeRepository,
    private val settingsRepository: SettingsRepository,
    private val completeMeal: CompleteMealUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ConsumeConfirmUiState(date = date, mealType = mealType),
    )
    val uiState: StateFlow<ConsumeConfirmUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultServings = settingsRepository.getString(SettingsKeys.DEFAULT_SERVINGS)
                ?.toIntOrNull() ?: SettingsKeys.DEFAULT_SERVINGS_VALUE
            val plans = mealRepository.getPlans(date, mealType)
                .filter { it.status == com.skyanchor.mealtime.core.model.MealStatus.PLANNED }
            if (plans.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, alreadyCompleted = true) }
                return@launch
            }
            val dishes = plans.map { plan ->
                val detail = recipeRepository.getRecipeDetail(plan.recipe.id)
                ConsumeDishUi(
                    recipeId = plan.recipe.id,
                    name = plan.recipe.name,
                    lines = detail?.ingredients
                        ?.filter { it.quantity != null && it.ingredient.name.isNotBlank() }
                        ?.filter { it.type != com.skyanchor.mealtime.core.model.IngredientTypes.SEASONING }
                        ?.map { line ->
                            ConsumeLineUi(
                                planId = plan.id,
                                ingredientId = line.ingredient.id,
                                name = line.ingredient.name,
                                unit = line.unit,
                                expected = line.quantity!!,
                                actualText = line.quantity.toString().removeSuffix(".0"),
                            )
                        }
                        ?: emptyList(),
                )
            }
            _uiState.update {
                it.copy(isLoading = false, dishes = dishes, servingsText = defaultServings.toString())
            }
        }
    }

    fun updateActual(planId: Long, ingredientId: Long, value: String) = _uiState.update { state ->
        state.copy(
            dishes = state.dishes.map { dish ->
                dish.copy(
                    lines = dish.lines.map { line ->
                        if (line.planId == planId && line.ingredientId == ingredientId) {
                            line.copy(actualText = value.filter { c -> c.isDigit() || c == '.' }.take(7))
                        } else {
                            line
                        }
                    },
                )
            },
        )
    }

    fun setServings(value: String) =
        _uiState.update { it.copy(servingsText = value.filter { c -> c.isDigit() }.take(3)) }

    fun confirm(onCompleted: () -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            try {
                val adjustments = buildMap {
                    state.dishes.forEach { dish ->
                        dish.lines.forEach { line ->
                            put("${line.planId}:${line.ingredientId}", line.actualText.toDoubleOrNull() ?: line.expected)
                        }
                    }
                }
                completeMeal(
                    date = date,
                    mealType = mealType,
                    servings = state.servingsText.toIntOrNull(),
                    adjustments = adjustments,
                )
                onCompleted()
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "确认失败，请重试") }
            }
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            date: LocalDate,
            mealType: MealType,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConsumeConfirmViewModel(
                    date = date,
                    mealType = mealType,
                    mealRepository = container.mealRepository,
                    recipeRepository = container.recipeRepository,
                    settingsRepository = container.settingsRepository,
                    completeMeal = CompleteMealUseCase(
                        container.mealRepository,
                        container.recipeRepository,
                    ),
                )
            }
        }
    }
}
