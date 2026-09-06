package com.skyanchor.mealtime.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeListUiState(
    val isLoading: Boolean = true,
    val recipes: List<Recipe> = emptyList(),
    val categories: List<Category> = emptyList(),
    val query: String = "",
    val selectedCategoryId: Long? = null,
    val favoritesOnly: Boolean = false,
)

private data class RecipeFilter(
    val categories: List<Category>,
    val query: String,
    val categoryId: Long?,
    val favoritesOnly: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModel(
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<Long?>(null)
    private val favoritesOnly = MutableStateFlow(false)

    val uiState: StateFlow<RecipeListUiState> = combine(
        recipeRepository.observeCategories(),
        query,
        categoryId,
        favoritesOnly,
    ) { categories, q, cat, fav ->
        RecipeFilter(categories, q, cat, fav)
    }.flatMapLatest { filter ->
        recipeRepository.observeRecipes(
            query = filter.query,
            categoryId = filter.categoryId,
            favoritesOnly = filter.favoritesOnly,
        ).map { recipes ->
            RecipeListUiState(
                isLoading = false,
                recipes = recipes,
                categories = filter.categories,
                query = filter.query,
                selectedCategoryId = filter.categoryId,
                favoritesOnly = filter.favoritesOnly,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeListUiState(isLoading = true),
    )

    fun setQuery(value: String) {
        query.value = value
    }

    /** 再次点击同一分类取消选择 */
    fun selectCategory(id: Long?) {
        categoryId.value = if (categoryId.value == id) null else id
    }

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun toggleFavorite(recipe: Recipe) {
        viewModelScope.launch {
            recipeRepository.setFavorite(recipe.id, !recipe.isFavorite)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RecipeListViewModel(container.recipeRepository) }
        }
    }
}
