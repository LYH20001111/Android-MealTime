package com.skyanchor.mealtime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.usecase.CreateMealPlanUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class MealPlanUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val mealType: MealType = MealType.LUNCH,
    val dishes: List<HomeMealDish> = emptyList(),
    /** 选择器里的候选菜谱（按搜索词过滤） */
    val pickerRecipes: List<Recipe> = emptyList(),
    val pickerQuery: String = "",
    /** 一次性提示（如菜品重复），显示后由 [MealPlanViewModel.consumeNotice] 清空 */
    val notice: String? = null,
)

/**
 * 餐次管理（点菜）：添加 / 更换 / 删除 / 排序；菜品必须来自菜谱库（ROADMAP §2 点菜）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealPlanViewModel(
    private val date: LocalDate,
    private val mealType: MealType,
    private val mealRepository: MealRepository,
    recipeRepository: RecipeRepository,
    private val createMealPlan: CreateMealPlanUseCase,
) : ViewModel() {

    private val pickerQuery = MutableStateFlow("")
    private val notice = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MealPlanUiState> = combine(
        mealRepository.observeMeals(date),
        recipeRepository.observeRecipes(),
        pickerQuery,
        notice,
    ) { plans, recipes, query, notice ->
        MealPlanUiState(
            isLoading = false,
            date = date,
            mealType = mealType,
            dishes = plans
                .filter { it.mealType == mealType }
                .map { plan ->
                    HomeMealDish(
                        planId = plan.id,
                        recipeId = plan.recipe.id,
                        name = plan.recipe.name,
                        isCompleted = plan.status == com.skyanchor.mealtime.core.model.MealStatus.COMPLETED,
                    )
                },
            pickerRecipes = recipes.filter {
                query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
            },
            pickerQuery = query,
            notice = notice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MealPlanUiState(isLoading = true, date = date, mealType = mealType),
    )

    fun setPickerQuery(value: String) {
        pickerQuery.value = value
    }

    fun addDish(recipeId: Long) {
        viewModelScope.launch {
            val added = createMealPlan(date = date, mealType = mealType, recipeId = recipeId)
            if (added == null) {
                notice.value = "「${recipeNameOf(recipeId)}」已在本餐菜单中"
            }
        }
    }

    fun replaceDish(planId: Long, newRecipeId: Long) {
        viewModelScope.launch {
            val replaced = mealRepository.replacePlan(planId, newRecipeId)
            if (!replaced) {
                notice.value = "「${recipeNameOf(newRecipeId)}」已在本餐菜单中"
            }
        }
    }

    fun consumeNotice() {
        notice.value = null
    }

    private fun recipeNameOf(recipeId: Long): String =
        uiState.value.pickerRecipes.firstOrNull { it.id == recipeId }?.name
            ?: uiState.value.dishes.firstOrNull { it.recipeId == recipeId }?.name
            ?: "该菜品"

    fun removeDish(planId: Long) {
        viewModelScope.launch {
            mealRepository.removePlan(planId)
        }
    }

    fun moveDish(planId: Long, delta: Int) {
        viewModelScope.launch {
            mealRepository.movePlan(planId, delta)
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            date: LocalDate,
            mealType: MealType,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MealPlanViewModel(
                    date = date,
                    mealType = mealType,
                    mealRepository = container.mealRepository,
                    recipeRepository = container.recipeRepository,
                    createMealPlan = CreateMealPlanUseCase(container.mealRepository),
                )
            }
        }
    }
}
