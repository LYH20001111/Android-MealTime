package com.skyanchor.mealtime.feature.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.Difficulty
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientType
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.core.model.Tag
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.usecase.SaveRecipeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 表单里的一行配料/调料输入 */
data class IngredientLineInput(
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
)

data class RecipeEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val imageUri: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val newTagText: String = "",
    val difficulty: Difficulty = Difficulty.EASY,
    val cookingTimeText: String = "",
    val description: String = "",
    val steps: List<String> = listOf(""),
    val ingredients: List<IngredientLineInput> = listOf(IngredientLineInput()),
    val seasonings: List<IngredientLineInput> = listOf(IngredientLineInput()),
    val note: String = "",
    val nameError: Boolean = false,
    val saveError: String? = null,
)

class RecipeEditViewModel(
    private val recipeId: Long?,
    private val recipeRepository: RecipeRepository,
    private val saveRecipeUseCase: SaveRecipeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeEditUiState(isNew = recipeId == null))
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val categories = recipeRepository.observeCategories().first()
            val tags = recipeRepository.observeTags().first()
            _uiState.update { it.copy(categories = categories, availableTags = tags) }

            val id = recipeId
            if (id != null) {
                val detail = recipeRepository.getRecipeDetail(id)
                if (detail != null) {
                    _uiState.update { state ->
                        val recipe = detail.recipe
                        state.copy(
                            isLoading = false,
                            isNew = false,
                            name = recipe.name,
                            imageUri = recipe.imageUri,
                            selectedCategoryId = recipe.categoryId,
                            difficulty = recipe.difficulty,
                            cookingTimeText = recipe.cookingTimeMin?.toString() ?: "",
                            description = recipe.description ?: "",
                            steps = recipe.steps.ifEmpty { listOf("") },
                            note = recipe.note ?: "",
                            ingredients = detail.ingredients
                                .filter { it.type == IngredientType.INGREDIENT }
                                .map { line ->
                                    IngredientLineInput(
                                        name = line.ingredient.name,
                                        quantity = line.quantity?.toString()
                                            ?.removeSuffix(".0") ?: "",
                                        unit = line.unit ?: "",
                                    )
                                }
                                .ifEmpty { listOf(IngredientLineInput()) },
                            seasonings = detail.ingredients
                                .filter { it.type == IngredientType.SEASONING }
                                .map { line ->
                                    IngredientLineInput(
                                        name = line.ingredient.name,
                                        quantity = line.quantity?.toString()
                                            ?.removeSuffix(".0") ?: "",
                                        unit = line.unit ?: "",
                                    )
                                }
                                .ifEmpty { listOf(IngredientLineInput()) },
                            selectedTagIds = detail.tags.map { it.id }.toSet(),
                        )
                    }
                    return@launch
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value, nameError = false) }

    fun setImageUri(uri: String?) = _uiState.update { it.copy(imageUri = uri) }

    fun selectCategory(id: Long?) =
        _uiState.update { it.copy(selectedCategoryId = if (it.selectedCategoryId == id) null else id) }

    fun toggleTag(tag: Tag) = _uiState.update { state ->
        val ids = state.selectedTagIds.toMutableSet()
        if (!ids.add(tag.id)) ids.remove(tag.id)
        state.copy(selectedTagIds = ids)
    }

    fun setNewTagText(value: String) = _uiState.update { it.copy(newTagText = value) }

    fun addNewTag() {
        val text = _uiState.value.newTagText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val tag = recipeRepository.getOrCreateTag(text)
            _uiState.update { state ->
                state.copy(
                    availableTags = if (state.availableTags.any { it.id == tag.id }) {
                        state.availableTags
                    } else {
                        state.availableTags + tag
                    },
                    selectedTagIds = state.selectedTagIds + tag.id,
                    newTagText = "",
                )
            }
        }
    }

    fun setDifficulty(value: Difficulty) = _uiState.update { it.copy(difficulty = value) }

    fun setCookingTime(value: String) =
        _uiState.update { it.copy(cookingTimeText = value.filter { c -> c.isDigit() }.take(4)) }

    fun setDescription(value: String) = _uiState.update { it.copy(description = value) }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

    fun updateStep(index: Int, value: String) = _uiState.update { state ->
        state.copy(steps = state.steps.mapIndexed { i, s -> if (i == index) value else s })
    }

    fun addStep() = _uiState.update { it.copy(steps = it.steps + "") }

    fun removeStep(index: Int) = _uiState.update { state ->
        val remaining = state.steps.filterIndexed { i, _ -> i != index }
        state.copy(steps = remaining.ifEmpty { listOf("") })
    }

    fun updateLine(type: IngredientType, index: Int, value: IngredientLineInput) =
        _uiState.update { state ->
            when (type) {
                IngredientType.INGREDIENT -> state.copy(
                    ingredients = state.ingredients.mapIndexed { i, l -> if (i == index) value else l },
                )

                IngredientType.SEASONING -> state.copy(
                    seasonings = state.seasonings.mapIndexed { i, l -> if (i == index) value else l },
                )
            }
        }

    fun addLine(type: IngredientType) = _uiState.update { state ->
        when (type) {
            IngredientType.INGREDIENT -> state.copy(ingredients = state.ingredients + IngredientLineInput())
            IngredientType.SEASONING -> state.copy(seasonings = state.seasonings + IngredientLineInput())
        }
    }

    fun removeLine(type: IngredientType, index: Int) = _uiState.update { state ->
        when (type) {
            IngredientType.INGREDIENT -> state.copy(
                ingredients = state.ingredients.filterIndexed { i, _ -> i != index }
                    .ifEmpty { listOf(IngredientLineInput()) },
            )

            IngredientType.SEASONING -> state.copy(
                seasonings = state.seasonings.filterIndexed { i, _ -> i != index }
                    .ifEmpty { listOf(IngredientLineInput()) },
            )
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val recipe = Recipe(
                    id = recipeId ?: 0,
                    name = state.name,
                    imageUri = state.imageUri,
                    categoryId = state.selectedCategoryId,
                    difficulty = state.difficulty,
                    cookingTimeMin = state.cookingTimeText.toIntOrNull(),
                    description = state.description.takeIf { it.isNotBlank() },
                    steps = state.steps.map { it.trim() }.filter { it.isNotEmpty() },
                    note = state.note.takeIf { it.isNotBlank() },
                )
                val lines = buildList {
                    state.ingredients.forEachIndexed { index, input ->
                        if (input.name.isNotBlank()) {
                            add(
                                RecipeIngredientLine(
                                    ingredient = Ingredient(name = input.name.trim()),
                                    quantity = parseQuantity(input.quantity),
                                    unit = input.unit.trim().takeIf { it.isNotEmpty() },
                                    type = IngredientType.INGREDIENT,
                                    sortOrder = index,
                                )
                            )
                        }
                    }
                    state.seasonings.forEachIndexed { index, input ->
                        if (input.name.isNotBlank()) {
                            add(
                                RecipeIngredientLine(
                                    ingredient = Ingredient(name = input.name.trim()),
                                    quantity = parseQuantity(input.quantity),
                                    unit = input.unit.trim().takeIf { it.isNotEmpty() },
                                    type = IngredientType.SEASONING,
                                    sortOrder = state.ingredients.size + index,
                                )
                            )
                        }
                    }
                }
                val id = saveRecipeUseCase(recipe, lines, state.selectedTagIds.toList())
                onSaved(id)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "保存失败，请重试") }
            }
        }
    }

    private fun parseQuantity(text: String): Double? =
        text.trim().toDoubleOrNull()

    companion object {
        fun factory(container: AppContainer, recipeId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RecipeEditViewModel(
                        recipeId = recipeId,
                        recipeRepository = container.recipeRepository,
                        saveRecipeUseCase = SaveRecipeUseCase(
                            container.recipeRepository,
                            container.ingredientRepository,
                        ),
                    )
                }
            }
    }
}
