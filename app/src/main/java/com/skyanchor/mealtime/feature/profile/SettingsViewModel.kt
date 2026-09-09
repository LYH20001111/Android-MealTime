package com.skyanchor.mealtime.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.FALLBACK_CATEGORY_NAME
import com.skyanchor.mealtime.core.model.IngredientTypeInfo
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.repository.SettingsKeys
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val notificationEnabled: Boolean = false,
    val advanceDays: String = SettingsKeys.DEFAULT_NOTIFICATION_ADVANCE_DAYS.toString(),
    val servings: String = SettingsKeys.DEFAULT_SERVINGS_VALUE.toString(),
    val nearDays: String = SettingsKeys.DEFAULT_EXPIRY_NEAR_DAYS.toString(),
    val urgentDays: String = SettingsKeys.DEFAULT_EXPIRY_URGENT_DAYS.toString(),
    val categories: List<Category> = emptyList(),
    val ingredientTypes: List<IngredientTypeInfo> = emptyList(),
    val newCategoryName: String = "",
    val newTypeName: String = "",
    val message: String? = null,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: IngredientRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val enabled = settingsRepository.getString(SettingsKeys.NOTIFICATION_ENABLED)?.toBoolean() ?: false
            _uiState.update {
                it.copy(
                    notificationEnabled = enabled,
                    advanceDays = settingsRepository.getString(SettingsKeys.NOTIFICATION_ADVANCE_DAYS)
                        ?: it.advanceDays,
                    servings = settingsRepository.getString(SettingsKeys.DEFAULT_SERVINGS) ?: it.servings,
                    nearDays = settingsRepository.getString(SettingsKeys.EXPIRY_NEAR_DAYS) ?: it.nearDays,
                    urgentDays = settingsRepository.getString(SettingsKeys.EXPIRY_URGENT_DAYS) ?: it.urgentDays,
                )
            }
        }
        viewModelScope.launch {
            recipeRepository.observeCategories().collect { list ->
                _uiState.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            ingredientRepository.observeTypes().collect { list ->
                _uiState.update { it.copy(ingredientTypes = list) }
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.putString(SettingsKeys.NOTIFICATION_ENABLED, enabled.toString())
            _uiState.update { it.copy(notificationEnabled = enabled) }
        }
    }

    fun setAdvanceDays(value: String) = updateNumber(SettingsKeys.NOTIFICATION_ADVANCE_DAYS, value) { s, v -> s.copy(advanceDays = v) }

    fun setServings(value: String) = updateNumber(SettingsKeys.DEFAULT_SERVINGS, value) { s, v -> s.copy(servings = v) }

    fun setNearDays(value: String) = updateNumber(SettingsKeys.EXPIRY_NEAR_DAYS, value) { s, v -> s.copy(nearDays = v) }

    fun setUrgentDays(value: String) = updateNumber(SettingsKeys.EXPIRY_URGENT_DAYS, value) { s, v -> s.copy(urgentDays = v) }

    // ---- 分类管理 ----

    fun setNewCategoryName(value: String) = _uiState.update { it.copy(newCategoryName = value) }

    fun addCategory() {
        val name = _uiState.value.newCategoryName
        viewModelScope.launch {
            runCatching { recipeRepository.addCategory(name) }
                .onSuccess { added ->
                    _uiState.update { it.copy(newCategoryName = "", message = "✓ 已添加分类「${added.name}」") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "添加失败"}") }
                }
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            runCatching { recipeRepository.deleteCategory(id) }
                .onSuccess {
                    _uiState.update { it.copy(message = "✓ 分类已删除，其中的菜谱已归入「$FALLBACK_CATEGORY_NAME」") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "删除失败"}") }
                }
        }
    }

    fun moveCategory(id: Long, up: Boolean) {
        viewModelScope.launch {
            runCatching { recipeRepository.moveCategory(id, up) }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "排序失败"}") }
                }
        }
    }

    fun renameCategory(id: Long, newName: String) {
        viewModelScope.launch {
            runCatching { recipeRepository.renameCategory(id, newName) }
                .onSuccess { renamed ->
                    _uiState.update { it.copy(message = "✓ 分类已重命名为「${renamed.name}」") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "重命名失败"}") }
                }
        }
    }

    fun setNewTypeName(value: String) = _uiState.update { it.copy(newTypeName = value) }

    fun addIngredientType() {
        val name = _uiState.value.newTypeName
        viewModelScope.launch {
            runCatching { ingredientRepository.addType(name) }
                .onSuccess { added ->
                    _uiState.update { it.copy(newTypeName = "", message = "✓ 已添加种类「${added.label}」") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "添加失败"}") }
                }
        }
    }

    fun deleteIngredientType(key: String) {
        viewModelScope.launch {
            runCatching { ingredientRepository.deleteType(key) }
                .onSuccess {
                    _uiState.update { it.copy(message = "✓ 种类已删除，其中的食材已归入「其他」") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "删除失败"}") }
                }
        }
    }

    fun moveIngredientType(key: String, up: Boolean) {
        viewModelScope.launch {
            runCatching { ingredientRepository.moveType(key, up) }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "排序失败"}") }
                }
        }
    }

    fun renameIngredientType(key: String, newLabel: String) {
        viewModelScope.launch {
            runCatching { ingredientRepository.renameType(key, newLabel) }
                .onSuccess { renamed ->
                    _uiState.update { it.copy(message = "✓ 种类已重命名为「${renamed.label}」") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(message = "✗ ${e.message ?: "重命名失败"}") }
                }
        }
    }

    private fun updateNumber(
        key: String,
        value: String,
        reduce: (SettingsUiState, String) -> SettingsUiState,
    ) {
        val cleaned = value.filter { it.isDigit() }.take(3)
        _uiState.update { reduce(it, cleaned) }
        viewModelScope.launch {
            if (cleaned.isNotEmpty()) {
                settingsRepository.putString(key, cleaned)
            } else {
                settingsRepository.remove(key) // 回落到默认值
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    recipeRepository = container.recipeRepository,
                    ingredientRepository = container.ingredientRepository,
                )
            }
        }
    }
}
