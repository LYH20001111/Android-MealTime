package com.skyanchor.mealtime.data.mapper

import com.skyanchor.mealtime.core.model.Category
import com.skyanchor.mealtime.core.model.ChangeSource
import com.skyanchor.mealtime.core.model.Difficulty
import com.skyanchor.mealtime.core.model.Ingredient
import com.skyanchor.mealtime.core.model.IngredientTypes
import com.skyanchor.mealtime.core.model.InventoryChangeType
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.InventoryTransaction
import com.skyanchor.mealtime.core.model.MealPlan
import com.skyanchor.mealtime.core.model.MealRecord
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.QuantityLevel
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.RecipeDetail
import com.skyanchor.mealtime.core.model.RecipeIngredientLine
import com.skyanchor.mealtime.core.model.Tag
import com.skyanchor.mealtime.data.local.entity.CategoryEntity
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity
import com.skyanchor.mealtime.data.local.entity.MealPlanEntity
import com.skyanchor.mealtime.data.local.entity.MealRecordEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.local.entity.TagEntity
import com.skyanchor.mealtime.data.local.relation.InventoryWithIngredient
import com.skyanchor.mealtime.data.local.relation.MealPlanWithRecipe
import com.skyanchor.mealtime.data.local.relation.RecipeIngredientWithIngredient
import com.skyanchor.mealtime.data.local.relation.RecipeWithIngredients
import java.time.LocalDate

/** 枚举安全转换：数据库中的未知值回退为默认，避免升级时崩溃 */
inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

// ---------- Entity -> Domain ----------

fun IngredientEntity.toDomain(): Ingredient = Ingredient(
    id = id,
    name = name,
    type = type.ifBlank { IngredientTypes.INGREDIENT },
    defaultUnit = defaultUnit,
    category = category,
    icon = icon,
    imageUri = imageUri,
)

fun RecipeEntity.toDomain(): Recipe = Recipe(
    id = id,
    name = name,
    imageUri = imageUri,
    categoryId = categoryId,
    difficulty = difficulty.toEnumOrDefault(Difficulty.EASY),
    cookingTimeMin = cookingTimeMin,
    description = description,
    steps = steps?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    note = note,
    isFavorite = isFavorite,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecipeIngredientWithIngredient.toDomain(): RecipeIngredientLine = RecipeIngredientLine(
    id = line.id,
    recipeId = line.recipeId,
    ingredient = ingredient.toDomain(),
    quantity = line.quantity,
    unit = line.unit,
    type = line.ingredientType.ifBlank { IngredientTypes.INGREDIENT },
    note = line.note,
    sortOrder = line.sortOrder,
)

fun RecipeWithIngredients.toDomain(tags: List<Tag> = emptyList()): RecipeDetail = RecipeDetail(
    recipe = recipe.toDomain(),
    ingredients = ingredients.map { it.toDomain() }.sortedBy { it.sortOrder },
    tags = tags,
)

fun InventoryWithIngredient.toDomain(): InventoryItem = InventoryItem(
    id = item.id,
    ingredient = ingredient.toDomain(),
    quantity = item.quantity,
    unit = item.unit,
    quantityLevel = item.quantityLevel?.let { it.toEnumOrDefault(QuantityLevel.MODERATE) },
    purchaseDate = item.purchaseDate?.let(LocalDate::ofEpochDay),
    productionDate = item.productionDate?.let(LocalDate::ofEpochDay),
    expireDate = item.expireDate?.let(LocalDate::ofEpochDay),
    location = item.location,
    note = item.note,
)

fun InventoryTransactionEntity.toDomain(): InventoryTransaction = InventoryTransaction(
    id = id,
    inventoryItemId = inventoryItemId,
    ingredientId = ingredientId,
    changeQuantity = changeQuantity,
    unit = unit,
    type = type.toEnumOrDefault(InventoryChangeType.ADJUST),
    sourceType = sourceType?.toEnumOrDefault(ChangeSource.MANUAL),
    sourceId = sourceId,
    note = note,
    createdAt = createdAt,
)

fun MealPlanWithRecipe.toDomain(): MealPlan = MealPlan(
    id = plan.id,
    date = LocalDate.parse(plan.date),
    mealType = plan.mealType.toEnumOrDefault(MealType.LUNCH),
    recipe = recipe.toDomain(),
    servings = plan.servings,
    sortOrder = plan.sortOrder,
    status = plan.status.toEnumOrDefault(MealStatus.PLANNED),
    note = plan.note,
)

fun MealRecordEntity.toDomain(): MealRecord = MealRecord(
    id = id,
    date = LocalDate.parse(date),
    mealType = mealType.toEnumOrDefault(MealType.LUNCH),
    servings = servings,
    completedAt = completedAt,
    note = note,
)

fun CategoryEntity.toDomain(): Category = Category(id = id, name = name, icon = icon, sortOrder = sortOrder)

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name, sortOrder = sortOrder)

// ---------- Domain -> Entity ----------

fun Recipe.toEntity(createdAt: Long, updatedAt: Long): RecipeEntity = RecipeEntity(
    id = id,
    name = name,
    imageUri = imageUri,
    categoryId = categoryId,
    difficulty = difficulty.name,
    cookingTimeMin = cookingTimeMin,
    description = description,
    steps = steps.joinToString("\n"),
    note = note,
    isFavorite = isFavorite,
    isArchived = isArchived,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecipeIngredientLine.toEntity(recipeId: Long): RecipeIngredientEntity = RecipeIngredientEntity(
    id = id,
    recipeId = recipeId,
    ingredientId = ingredient.id,
    quantity = quantity,
    unit = unit,
    ingredientType = type,
    note = note,
    sortOrder = sortOrder,
)

fun InventoryItem.toEntity(createdAt: Long, updatedAt: Long): InventoryItemEntity = InventoryItemEntity(
    id = id,
    ingredientId = ingredient.id,
    quantity = quantity,
    unit = unit,
    quantityLevel = quantityLevel?.name,
    purchaseDate = purchaseDate?.toEpochDay(),
    productionDate = productionDate?.toEpochDay(),
    expireDate = expireDate?.toEpochDay(),
    location = location,
    note = note,
    isDeleted = false,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun InventoryTransaction.toEntity(): InventoryTransactionEntity = InventoryTransactionEntity(
    id = id,
    inventoryItemId = inventoryItemId,
    ingredientId = ingredientId,
    changeQuantity = changeQuantity,
    unit = unit,
    type = type.name,
    sourceType = sourceType?.name,
    sourceId = sourceId,
    note = note,
    createdAt = createdAt,
)

fun MealPlan.toEntity(now: Long): MealPlanEntity = MealPlanEntity(
    id = id,
    date = date.toString(),
    mealType = mealType.name,
    recipeId = recipe.id,
    servings = servings,
    sortOrder = sortOrder,
    status = status.name,
    note = note,
    createdAt = now,
    updatedAt = now,
)

fun MealRecord.toEntity(): MealRecordEntity = MealRecordEntity(
    id = id,
    date = date.toString(),
    mealType = mealType.name,
    servings = servings,
    completedAt = completedAt,
    note = note,
)
