package com.skyanchor.mealtime.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.RecipeDetail
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.usecase.ArchiveRecipeUseCase
import com.skyanchor.mealtime.domain.usecase.CreateMealPlanUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 加入餐次的一次性反馈：餐次 + 是否因重复被拒绝 */
data class AddMealResult(
    val mealType: MealType,
    val isDuplicate: Boolean,
)

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    /** null 且 !isLoading 表示菜谱不存在或已归档 */
    val detail: RecipeDetail? = null,
    /** 最近一次加入餐次的结果，用于 Snackbar 提示 */
    val addResult: AddMealResult? = null,
)

class RecipeDetailViewModel(
    private val recipeId: Long,
    private val recipeRepository: RecipeRepository,
    private val createMealPlan: CreateMealPlanUseCase,
    private val archiveRecipe: ArchiveRecipeUseCase,
) : ViewModel() {

    private val addResult = MutableStateFlow<AddMealResult?>(null)

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        recipeRepository.observeRecipeDetail(recipeId),
        addResult,
    ) { detail, added ->
        RecipeDetailUiState(isLoading = false, detail = detail, addResult = added)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeDetailUiState(isLoading = true),
    )

    fun toggleFavorite() {
        val current = uiState.value.detail?.recipe ?: return
        viewModelScope.launch {
            recipeRepository.setFavorite(current.id, !current.isFavorite)
        }
    }

    fun addToMeal(mealType: MealType) {
        viewModelScope.launch {
            val added = createMealPlan(mealType = mealType, recipeId = recipeId)
            addResult.value = AddMealResult(mealType = mealType, isDuplicate = added == null)
        }
    }

    fun consumeAddResult() {
        addResult.value = null
    }

    fun archive(onArchived: () -> Unit) {
        viewModelScope.launch {
            archiveRecipe(recipeId)
            onArchived()
        }
    }

    companion object {
        fun factory(container: AppContainer, recipeId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RecipeDetailViewModel(
                        recipeId = recipeId,
                        recipeRepository = container.recipeRepository,
                        createMealPlan = CreateMealPlanUseCase(container.mealRepository),
                        archiveRecipe = ArchiveRecipeUseCase(container.recipeRepository),
                    )
                }
            }
    }
}
