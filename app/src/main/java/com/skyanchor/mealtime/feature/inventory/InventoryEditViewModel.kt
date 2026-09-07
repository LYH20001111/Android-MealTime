package com.skyanchor.mealtime.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientTypeInfo
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.QuantityLevel
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import com.skyanchor.mealtime.domain.repository.InventoryRepository
import com.skyanchor.mealtime.domain.usecase.AddInventoryUseCase
import com.skyanchor.mealtime.domain.usecase.UpdateInventoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class InventoryEditUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isNew: Boolean = true,
    val ingredientName: String = "",
    /** 食材种类键（设置里可配置） */
    val ingredientType: String = IngredientTypes.INGREDIENT,
    val ingredientTypes: List<IngredientTypeInfo> = emptyList(),
    val imageUri: String? = null,
    val quantityText: String = "",
    val unit: String = "",
    val quantityLevel: QuantityLevel? = null,
    val purchaseDate: LocalDate? = null,
    val productionDate: LocalDate? = null,
    val expireDate: LocalDate? = null,
    val location: String = "",
    val note: String = "",
    val nameError: Boolean = false,
    val saveError: String? = null,
)

class InventoryEditViewModel(
    private val itemId: Long?,
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
    private val addInventory: AddInventoryUseCase,
    private val updateInventory: UpdateInventoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryEditUiState(isNew = itemId == null))
    val uiState: StateFlow<InventoryEditUiState> = _uiState.asStateFlow()

    private var previousQuantity: Double? = null

    /** 编辑时保留原食材关联（食材变更不在库存编辑范围内） */
    private var originalIngredient: Ingredient? = null

    init {
        viewModelScope.launch {
            val types = ingredientRepository.observeTypes().first()
            val id = itemId
            if (id == null) {
                _uiState.update { it.copy(isLoading = false, ingredientTypes = types) }
            } else {
                val item = inventoryRepository.getItem(id)
                if (item != null) {
                    previousQuantity = item.quantity
                    originalIngredient = item.ingredient
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isNew = false,
                            ingredientName = item.ingredient.name,
                            ingredientType = item.ingredient.type,
                            ingredientTypes = types,
                            imageUri = item.ingredient.imageUri,
                            quantityText = item.quantity?.toString()?.removeSuffix(".0") ?: "",
                            unit = item.unit ?: "",
                            quantityLevel = item.quantityLevel,
                            purchaseDate = item.purchaseDate,
                            productionDate = item.productionDate,
                            expireDate = item.expireDate,
                            location = item.location ?: "",
                            note = item.note ?: "",
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, saveError = "库存不存在或已删除", ingredientTypes = types) }
                }
            }
        }
    }

    fun setIngredientName(value: String) =
        _uiState.update { it.copy(ingredientName = value, nameError = false) }

    fun setIngredientType(value: String) =
        _uiState.update { it.copy(ingredientType = value) }

    fun setImageUri(value: String?) = _uiState.update { it.copy(imageUri = value) }

    fun setQuantity(value: String) =
        _uiState.update { it.copy(quantityText = value.filter { c -> c.isDigit() || c == '.' }.take(7)) }

    fun setUnit(value: String) = _uiState.update { it.copy(unit = value) }

    /** 数量级别三态切换：再次点击同级别取消 */
    fun selectQuantityLevel(value: QuantityLevel?) =
        _uiState.update { it.copy(quantityLevel = if (it.quantityLevel == value) null else value) }

    fun setPurchaseDate(value: LocalDate?) = _uiState.update { it.copy(purchaseDate = value) }

    fun setProductionDate(value: LocalDate?) = _uiState.update { it.copy(productionDate = value) }

    fun setExpireDate(value: LocalDate?) = _uiState.update { it.copy(expireDate = value) }

    fun setLocation(value: String) = _uiState.update { it.copy(location = value) }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

    fun save(onSaved: (Long) -> Unit) {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.ingredientName.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, saveError = null) }

        viewModelScope.launch {
            try {
                val ingredient = if (state.isNew) {
                    Ingredient(
                        name = state.ingredientName.trim(),
                        type = state.ingredientType,
                        imageUri = state.imageUri,
                    )
                } else {
                    originalIngredient ?: Ingredient(name = state.ingredientName.trim())
                }
                val item = InventoryItem(
                    id = itemId ?: 0,
                    ingredient = ingredient,
                    quantity = state.quantityText.toDoubleOrNull(),
                    unit = state.unit.trim().takeIf { it.isNotEmpty() },
                    quantityLevel = state.quantityLevel,
                    purchaseDate = state.purchaseDate,
                    productionDate = state.productionDate,
                    expireDate = state.expireDate,
                    location = state.location.trim().takeIf { it.isNotEmpty() },
                    note = state.note.trim().takeIf { it.isNotEmpty() },
                )
                val savedId = if (state.isNew) {
                    addInventory(item)
                } else {
                    // 图片挂在食材字典上；编辑批次时图片有变则同步更新字典
                    val original = originalIngredient
                    if (original != null && original.imageUri != state.imageUri) {
                        ingredientRepository.updateImage(original.id, state.imageUri)
                    }
                    updateInventory(item, previousQuantity)
                    item.id
                }
                onSaved(savedId)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = "保存失败，请重试") }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, itemId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    InventoryEditViewModel(
                        itemId = itemId,
                        inventoryRepository = container.inventoryRepository,
                        ingredientRepository = container.ingredientRepository,
                        addInventory = AddInventoryUseCase(
                            container.inventoryRepository,
                            container.ingredientRepository,
                        ),
                        updateInventory = UpdateInventoryUseCase(container.inventoryRepository),
                    )
                }
            }
    }
}
