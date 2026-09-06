package com.skyanchor.mealtime.data.repository

import androidx.room.withTransaction
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientType
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.mapper.toDomain
import com.skyanchor.mealtime.domain.repository.IngredientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomIngredientRepository(private val db: MealTimeDatabase) : IngredientRepository {

    private val dao = db.ingredientDao()

    override fun observeIngredients(
        type: IngredientType?,
        query: String?,
    ): Flow<List<Ingredient>> =
        dao.observeIngredients(
            type = type?.name,
            query = query?.takeIf { it.isNotBlank() },
        ).map { list -> list.map { it.toDomain() } }

    override suspend fun getIngredient(id: Long): Ingredient? = dao.getById(id)?.toDomain()

    override suspend fun getIngredientByName(name: String): Ingredient? =
        dao.getByName(name)?.toDomain()

    override suspend fun getOrCreate(
        name: String,
        type: IngredientType,
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
                        type = type.name,
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
}
