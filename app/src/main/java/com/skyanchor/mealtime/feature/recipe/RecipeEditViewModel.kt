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
import com.skyanchor.mealtime.core.model.IngredientTypeInfo
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.core.model.Tag
import com.skyanchor.mealtime.domain.repository.IngredientRepository
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

/** 表单里的一个配料分区：按食材种类动态生成（默认 食材/调料） */
data class IngredientSectionUi(
    val typeKey: String,
    val typeLabel: String,
    val lines: List<IngredientLineInput>,
)

data class RecipeEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val imageUri: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val sections: List<IngredientSectionUi> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val newTagText: String = "",
    val difficulty: Difficulty = Difficulty.EASY,
    val cookingTimeText: String = "",
    val description: String = "",
    val steps: List<String> = listOf(""),
    val note: String = "",
    val nameError: Boolean = false,
    val imageError: Boolean = false,
    val categoryError: Boolean = false,
    val saveError: String? = null,
)

/** 加载完成时的表单快照，用于"未保存修改"离开保护（规范文档 §40） */
private data class RecipeEditSnapshot(
    val name: String,
    val imageUri: String?,
    val categoryId: Long?,
    val difficulty: Difficulty,
    val cookingTimeText: String,
    val description: String,
    val steps: List<String>,
    val note: String,
    val sections: List<IngredientSectionUi>,
    val selectedTagIds: Set<Long>,
)

class RecipeEditViewModel(
    private val recipeId: Long?,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
    private val saveRecipeUseCase: SaveRecipeUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeEditUiState(isNew = recipeId == null))
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private var snapshot: RecipeEditSnapshot? = null

    /** 是否存在未保存修改（返回键离开前确认，规范文档 §40） */
    fun isDirty(): Boolean = snapshot?.let { it != snapshotOf(_uiState.value) } ?: false

    private fun snapshotOf(state: RecipeEditUiState) = RecipeEditSnapshot(
        name = state.name,
        imageUri = state.imageUri,
        categoryId = state.selectedCategoryId,
        difficulty = state.difficulty,
        cookingTimeText = state.cookingTimeText,
        description = state.description,
        steps = state.steps,
        note = state.note,
        sections = state.sections,
        selectedTagIds = state.selectedTagIds,
    )

    init {
        viewModelScope.launch {
            val categories = recipeRepository.observeCategories().first()
            val tags = recipeRepository.observeTags().first()
            val types = ingredientRepository.observeTypes().first()
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
                            sections = buildSections(types, detail.ingredients),
                            selectedTagIds = detail.tags.map { it.id }.toSet(),
                        )
                    }
                    snapshot = snapshotOf(_uiState.value)
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    sections = buildSections(types, emptyList()),
                )
            }
            snapshot = snapshotOf(_uiState.value)
        }
    }

    /** 每个已配置种类一个分区；未匹配到当前种类的行（理论上不出现）并入第一分区 */
    private fun buildSections(
        types: List<IngredientTypeInfo>,
        lines: List<RecipeIngredientLine>,
    ): List<IngredientSectionUi> {
        val byType = lines.groupBy { it.type }
        val knownKeys = types.map { it.key }.toSet()
        val orphans = lines.filter { it.type !in knownKeys }.map(::toLineInput)
        return types.mapIndexed { index, type ->
            val sectionLines = (byType[type.key] ?: emptyList()).map(::toLineInput)
            val merged = if (index == 0) orphans + sectionLines else sectionLines
            IngredientSectionUi(
                typeKey = type.key,
                typeLabel = type.label,
                lines = merged.ifEmpty { listOf(IngredientLineInput()) },
            )
        }
    }

    private fun toLineInput(line: RecipeIngredientLine) = IngredientLineInput(
        name = line.ingredient.name,
        quantity = line.quantity?.toString()?.removeSuffix(".0") ?: "",
        unit = line.unit ?: "",
    )

    fun setName(value: String) = _uiState.update { it.copy(name = value, nameError = false) }

    fun setImageUri(uri: String?) = _uiState.update { it.copy(imageUri = uri, imageError = false) }

    fun selectCategory(id: Long?) =
        _uiState.update {
            it.copy(
                selectedCategoryId = if (it.selectedCategoryId == id) null else id,
                categoryError = false,
            )
        }

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

    fun updateLine(typeKey: String, index: Int, value: IngredientLineInput) =
        updateSection(typeKey) { lines -> lines.mapIndexed { i, l -> if (i == index) value else l } }

    fun addLine(typeKey: String) = updateSection(typeKey) { it + IngredientLineInput() }

    fun removeLine(typeKey: String, index: Int) = updateSection(typeKey) { lines ->
        lines.filterIndexed { i, _ -> i != index }.ifEmpty { listOf(IngredientLineInput()) }
    }

    private fun updateSection(typeKey: String, transform: (List<IngredientLineInput>) -> List<IngredientLineInput>) =
        _uiState.update { state ->
            state.copy(
                sections = state.sections.map { section ->
                    if (section.typeKey == typeKey) section.copy(lines = transform(section.lines)) else section
                },
            )
        }

    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        // 必填三项：菜名 / 封面 / 分类（规范文档 §2.1、§59）
        val nameError = state.name.isBlank()
        val imageError = state.imageUri == null
        val categoryError = state.selectedCategoryId == null
        if (nameError || imageError || categoryError) {
            _uiState.update {
                it.copy(nameError = nameError, imageError = imageError, categoryError = categoryError)
            }
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
                var sortOrder = 0
                val lines = buildList {
                    state.sections.forEach { section ->
                        section.lines.forEachIndexed { index, input ->
                            if (input.name.isNotBlank()) {
                                add(
                                    RecipeIngredientLine(
                                        ingredient = Ingredient(name = input.name.trim()),
                                        quantity = parseQuantity(input.quantity),
                                        unit = input.unit.trim().takeIf { it.isNotEmpty() },
                                        type = section.typeKey,
                                        sortOrder = sortOrder,
                                    )
                                )
                                sortOrder++
                            }
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
                        ingredientRepository = container.ingredientRepository,
                        saveRecipeUseCase = SaveRecipeUseCase(
                            container.recipeRepository,
                            container.ingredientRepository,
                        ),
                    )
                }
            }
    }
}
