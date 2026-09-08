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
import com.skyanchor.mealtime.core.model.isEmptyStock
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
    /** 库存空：仅记录食材本身，不含数量/级别/日期等库存信息 */
    val isEmptyStock: Boolean = false,
    val quantityText: String = "",
    val unit: String = "",
    val quantityLevel: QuantityLevel? = null,
    val purchaseDate: LocalDate? = null,
    val productionDate: LocalDate? = null,
    val expireDate: LocalDate? = null,
    val location: String = "",
    val note: String = "",
    val nameError: Boolean = false,
    val imageError: Boolean = false,
    val quantityError: Boolean = false,
    val unitError: Boolean = false,
    /** 已填写库存详情时切换为无库存：先弹确认，确认后清空库存字段（规范文档 §27） */
    val pendingEmptyStock: Boolean = false,
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
                            isEmptyStock = item.isEmptyStock,
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

    fun setImageUri(value: String?) = _uiState.update { it.copy(imageUri = value, imageError = false) }

    /**
     * 切换库存状态。切换为无库存且已填写库存详情时先弹确认（规范文档 §27）；
     * 确认后清空数量/级别/日期/位置，备注保留；切回有库存需重新填写（§28）。
     */
    fun requestEmptyStock(value: Boolean) = _uiState.update { state ->
        if (!value || state.isEmptyStock || !hasStockDetails(state)) {
            state.copy(isEmptyStock = value)
        } else {
            state.copy(pendingEmptyStock = true)
        }
    }

    fun confirmEmptyStock() {
        _uiState.update {
            it.copy(
                isEmptyStock = true,
                pendingEmptyStock = false,
                quantityText = "",
                unit = "",
                quantityLevel = null,
                purchaseDate = null,
                productionDate = null,
                expireDate = null,
                location = "",
            )
        }
    }

    fun dismissEmptyStock() = _uiState.update { it.copy(pendingEmptyStock = false) }

    private fun hasStockDetails(state: InventoryEditUiState): Boolean =
        state.quantityText.isNotBlank() ||
            state.unit.isNotBlank() ||
            state.quantityLevel != null ||
            state.purchaseDate != null ||
            state.productionDate != null ||
            state.expireDate != null ||
            state.location.isNotBlank()

    fun setQuantity(value: String) =
        _uiState.update {
            it.copy(
                quantityText = value.filter { c -> c.isDigit() || c == '.' }.take(7),
                quantityError = false,
            )
        }

    fun setUnit(value: String) = _uiState.update { it.copy(unit = value, unitError = false) }

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
        // 必填：名称 / 图片；有库存时数量、单位必填（规范文档 §4/§43）
        val nameError = state.ingredientName.isBlank()
        val imageError = state.imageUri == null
        val quantityError = !state.isEmptyStock && state.quantityText.toDoubleOrNull() == null
        val unitError = !state.isEmptyStock && state.unit.isBlank()
        if (nameError || imageError || quantityError || unitError) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    imageError = imageError,
                    quantityError = quantityError,
                    unitError = unitError,
                )
            }
            return
        }
        if (!isValidDateOrder(state)) {
            _uiState.update { it.copy(saveError = "日期需满足：生产日期 ≤ 购买日期 ≤ 过期日期") }
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
                // 无库存：数量/级别/日期/位置不落库；备注两种状态都保留（规范文档 §20/§26）
                val empty = state.isEmptyStock
                val item = InventoryItem(
                    id = itemId ?: 0,
                    ingredient = ingredient,
                    quantity = if (empty) null else state.quantityText.toDoubleOrNull(),
                    unit = if (empty) null else state.unit.trim().takeIf { it.isNotEmpty() },
                    quantityLevel = if (empty) null else state.quantityLevel,
                    purchaseDate = if (empty) null else state.purchaseDate,
                    productionDate = if (empty) null else state.productionDate,
                    expireDate = if (empty) null else state.expireDate,
                    location = if (empty) null else state.location.trim().takeIf { it.isNotEmpty() },
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

    /** 日期顺序校验：生产日期 ≤ 购买日期 ≤ 过期日期，未填写的日期不参与比较（规范文档 §18） */
    private fun isValidDateOrder(state: InventoryEditUiState): Boolean {
        val production = state.productionDate
        val purchase = state.purchaseDate
        val expire = state.expireDate
        if (production != null && purchase != null && production > purchase) return false
        if (purchase != null && expire != null && purchase > expire) return false
        if (production != null && expire != null && production > expire) return false
        return true
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
