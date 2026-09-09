package com.skyanchor.mealtime.data.repository

import androidx.room.withTransaction
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientTypeInfo
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.IngredientTypeEntity
import com.skyanchor.mealtime.data.mapper.toDomain
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomIngredientRepository(private val db: MealTimeDatabase) : IngredientRepository {

    private val dao = db.ingredientDao()
    private val typeDao = db.ingredientTypeDao()

    override fun observeIngredients(
        type: String?,
        query: String?,
    ): Flow<List<Ingredient>> =
        dao.observeIngredients(
            type = type,
            query = query?.takeIf { it.isNotBlank() },
        ).map { list -> list.map { it.toDomain() } }

    override suspend fun getIngredient(id: Long): Ingredient? = dao.getById(id)?.toDomain()

    override suspend fun getIngredientByName(name: String): Ingredient? =
        dao.getByName(name)?.toDomain()

    override suspend fun getOrCreate(
        name: String,
        type: String,
        defaultUnit: String?,
        imageUri: String?,
    ): Ingredient {
        val trimmed = name.trim()
        return db.withTransaction {
            val existing = dao.getByName(trimmed)
            if (existing != null) {
                if (imageUri != null && existing.imageUri != imageUri) {
                    dao.updateImage(existing.id, imageUri)
                }
                existing.copy(imageUri = imageUri ?: existing.imageUri).toDomain()
            } else {
                val id = dao.insert(
                    IngredientEntity(
                        name = trimmed,
                        type = type,
                        defaultUnit = defaultUnit,
                        imageUri = imageUri,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                Ingredient(id = id, name = trimmed, type = type, defaultUnit = defaultUnit, imageUri = imageUri)
            }
        }
    }

    override suspend fun updateImage(id: Long, imageUri: String?) {
        dao.updateImage(id, imageUri)
    }

    override suspend fun softDelete(id: Long) {
        dao.softDelete(id)
    }

    override fun observeTypes(): Flow<List<IngredientTypeInfo>> =
        typeDao.observeAll().map { list -> list.map { it.toInfo() } }

    override suspend fun addType(label: String): IngredientTypeInfo {
        val trimmed = label.trim()
        require(trimmed.isNotEmpty()) { "种类名称不能为空" }
        return db.withTransaction {
            require(typeDao.getByKey(trimmed) == null) { "种类「$trimmed」已存在" }
            require(typeDao.getByLabel(trimmed) == null) { "种类「$trimmed」已存在" }
            val sortOrder = (typeDao.maxSortOrder() ?: -1) + 1
            typeDao.insert(IngredientTypeEntity(key = trimmed, label = trimmed, sortOrder = sortOrder))
            IngredientTypeInfo(key = trimmed, label = trimmed, sortOrder = sortOrder)
        }
    }

    override suspend fun deleteType(key: String) = db.withTransaction {
        require(key != IngredientTypes.OTHER) { "「其他」种类不能删除" }
        if (typeDao.getByKey(key) == null) return@withTransaction
        // 兜底种类不存在时自动补建
        val fallback = typeDao.getByKey(IngredientTypes.OTHER) ?: run {
            val sortOrder = (typeDao.maxSortOrder() ?: -1) + 1
            val entity = IngredientTypeEntity(
                key = IngredientTypes.OTHER,
                label = IngredientTypes.label(IngredientTypes.OTHER),
                sortOrder = sortOrder,
            )
            typeDao.insert(entity)
            entity
        }
        dao.reassignType(fromType = key, toType = fallback.key)
        db.recipeDao().reassignIngredientType(fromType = key, toType = fallback.key)
        typeDao.delete(key)
    }

    override suspend fun moveType(key: String, up: Boolean) = db.withTransaction {
        val all = typeDao.getAllSorted()
        val index = all.indexOfFirst { it.key == key }
        if (index < 0) return@withTransaction
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex < 0 || targetIndex >= all.size) return@withTransaction
        val reordered = all.toMutableList().apply { add(targetIndex, removeAt(index)) }
        // 顺带把排序值规范化为 0..n-1，消除历史脏数据中的并列排序值
        reordered.forEachIndexed { position, entity ->
            if (entity.sortOrder != position) typeDao.updateSortOrder(entity.key, position)
        }
    }

    override suspend fun renameType(key: String, newLabel: String): IngredientTypeInfo = db.withTransaction {
        val type = typeDao.getByKey(key) ?: throw IllegalArgumentException("种类不存在")
        val trimmed = newLabel.trim()
        require(trimmed.isNotEmpty()) { "种类名称不能为空" }
        require(key != IngredientTypes.OTHER) { "「其他」种类不能重命名" }
        require(trimmed != IngredientTypes.label(IngredientTypes.OTHER)) {
            "「${IngredientTypes.label(IngredientTypes.OTHER)}」为内置保留名称"
        }
        val existing = typeDao.getByLabel(trimmed)
        require(existing == null || existing.key == key) { "种类「$trimmed」已存在" }
        typeDao.renameLabel(key, trimmed)
        IngredientTypeInfo(key = key, label = trimmed, sortOrder = type.sortOrder)
    }

    private fun IngredientTypeEntity.toInfo() = IngredientTypeInfo(key = key, label = label, sortOrder = sortOrder)
}
