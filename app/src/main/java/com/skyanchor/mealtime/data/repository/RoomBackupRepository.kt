package com.skyanchor.mealtime.data.repository

import androidx.room.withTransaction
import com.skyanchor.mealtime.data.local.database.MealTimeDatabase
import com.skyanchor.mealtime.data.local.entity.AppSettingEntity
import com.skyanchor.mealtime.data.local.entity.CategoryEntity
import com.skyanchor.mealtime.data.local.entity.IngredientEntity
import com.skyanchor.mealtime.data.local.entity.IngredientTypeEntity
import com.skyanchor.mealtime.data.local.entity.InventoryItemEntity
import com.skyanchor.mealtime.data.local.entity.InventoryTransactionEntity
import com.skyanchor.mealtime.data.local.entity.MealPlanEntity
import com.skyanchor.mealtime.data.local.entity.MealRecordEntity
import com.skyanchor.mealtime.data.local.entity.RecipeEntity
import com.skyanchor.mealtime.data.local.entity.RecipeIngredientEntity
import com.skyanchor.mealtime.data.local.entity.RecipeTagCrossRef
import com.skyanchor.mealtime.data.local.entity.TagEntity
import com.skyanchor.mealtime.domain.repository.BackupRepository
import com.skyanchor.mealtime.domain.repository.BackupSummary
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON 备份实现（org.json，零新增依赖）。
 * 结构：{ version, exportedAt, recipes[], ingredients[], recipeIngredients[],
 *        inventoryItems[], inventoryTransactions[], mealPlans[], mealRecords[],
 *        categories[], ingredientTypes[], tags[], recipeTags[], appSettings[] }
 * 恢复：同一事务内清空业务表 → 按依赖顺序整包写入（保留原 id）；
 *       旧备份缺 ingredientTypes 时保留默认种子种类。
 */
class RoomBackupRepository(private val db: MealTimeDatabase) : BackupRepository {

    override suspend fun exportJson(): String {
        val recipes = db.recipeDao().exportAll()
        val ingredients = db.ingredientDao().exportAll()
        val inventory = db.inventoryItemDao().exportAllItems()
        val transactions = db.inventoryTransactionDao().exportAll()
        val plans = db.mealPlanDao().exportAllPlans()
        val records = db.mealRecordDao().exportAllRecords()
        val categories = db.categoryDao().exportAll()
        val ingredientTypes = db.ingredientTypeDao().exportAll()
        val tags = db.tagDao().exportAll()
        val recipeIngredients = db.recipeDao().exportRecipeIngredients()
        val recipeTags = db.recipeDao().exportRecipeTags()
        val settings = db.appSettingDao().exportAll()

        return JSONObject().apply {
            put("version", BACKUP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("recipes", JSONArray().apply { recipes.forEach { put(it.toJson()) } })
            put("ingredients", JSONArray().apply { ingredients.forEach { put(it.toJson()) } })
            put("recipeIngredients", JSONArray().apply { recipeIngredients.forEach { put(it.toJson()) } })
            put("inventoryItems", JSONArray().apply { inventory.forEach { put(it.toJson()) } })
            put("inventoryTransactions", JSONArray().apply { transactions.forEach { put(it.toJson()) } })
            put("mealPlans", JSONArray().apply { plans.forEach { put(it.toJson()) } })
            put("mealRecords", JSONArray().apply { records.forEach { put(it.toJson()) } })
            put("categories", JSONArray().apply { categories.forEach { put(it.toJson()) } })
            put("ingredientTypes", JSONArray().apply { ingredientTypes.forEach { put(it.toJson()) } })
            put("tags", JSONArray().apply { tags.forEach { put(it.toJson()) } })
            put("recipeTags", JSONArray().apply { recipeTags.forEach { put(it.toJson()) } })
            put("appSettings", JSONArray().apply { settings.forEach { put(it.toJson()) } })
        }.toString(2)
    }

    override suspend fun importJson(json: String): BackupSummary = db.withTransaction {
        val root = JSONObject(json)
        require(root.optInt("version", -1) == BACKUP_VERSION) { "备份文件版本不支持" }

        val sql = db.openHelper.writableDatabase
        listOf(
            "recipe_tag", "recipe_ingredient", "inventory_transaction", "inventory_item",
            "meal_plan", "meal_record", "tag", "category", "ingredient", "recipe",
            "ingredient_type", "app_setting",
        ).forEach { sql.execSQL("DELETE FROM `$it`") }

        suspend fun JSONArray.forEachItem(block: suspend (JSONObject) -> Unit) {
            for (i in 0 until length()) block(getJSONObject(i))
        }

        root.getJSONArray("categories").forEachItem { db.categoryDao().insertAll(listOf(it.toCategory())) }
        // 旧备份没有 ingredientTypes 字段：保留建库时的默认种子
        root.optJSONArray("ingredientTypes")?.forEachItem { db.ingredientTypeDao().insert(it.toIngredientType()) }
        root.getJSONArray("tags").forEachItem { db.tagDao().insert(it.toTag()) }
        root.getJSONArray("recipes").forEachItem { db.recipeDao().insert(it.toRecipe()) }
        root.getJSONArray("ingredients").forEachItem { db.ingredientDao().insert(it.toIngredient()) }
        root.getJSONArray("recipeIngredients").forEachItem { db.recipeDao().insertIngredients(listOf(it.toRecipeIngredient())) }
        root.getJSONArray("recipeTags").forEachItem { db.recipeDao().insertTagCrossRefs(listOf(it.toCrossRef())) }
        root.getJSONArray("inventoryItems").forEachItem { db.inventoryItemDao().insert(it.toInventoryItem()) }
        root.getJSONArray("inventoryTransactions").forEachItem { db.inventoryTransactionDao().insert(it.toTransaction()) }
        root.getJSONArray("mealPlans").forEachItem { db.mealPlanDao().insert(it.toMealPlan()) }
        root.getJSONArray("mealRecords").forEachItem { db.mealRecordDao().insert(it.toMealRecord()) }
        root.getJSONArray("appSettings").forEachItem { db.appSettingDao().put(it.toSetting()) }

        BackupSummary(
            recipes = root.getJSONArray("recipes").length(),
            ingredients = root.getJSONArray("ingredients").length(),
            inventoryItems = root.getJSONArray("inventoryItems").length(),
            transactions = root.getJSONArray("inventoryTransactions").length(),
            mealPlans = root.getJSONArray("mealPlans").length(),
            mealRecords = root.getJSONArray("mealRecords").length(),
        )
    }

    companion object {
        const val BACKUP_VERSION = 1
    }
}

// ---- Entity <-> JSON（备份专用，id 原样保留） ----

private fun JSONObject.stringOr(key: String): String = if (isNull(key)) "" else getString(key)
private fun JSONObject.longOr(key: String): Long = if (isNull(key)) 0L else getLong(key)
private fun JSONObject.optLongBoxed(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.optIntBoxed(key: String): Int? = if (isNull(key)) null else getInt(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.optBoolOr(key: String, default: Boolean = false): Boolean =
    if (isNull(key)) default else getBoolean(key)

private fun RecipeEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); imageUri?.let { put("imageUri", it) }
    categoryId?.let { put("categoryId", it) }; put("difficulty", difficulty)
    cookingTimeMin?.let { put("cookingTimeMin", it) }
    description?.let { put("description", it) }; steps?.let { put("steps", it) }
    note?.let { put("note", it) }; put("isFavorite", isFavorite); put("isArchived", isArchived)
    put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun JSONObject.toRecipe() = RecipeEntity(
    id = getLong("id"), name = stringOr("name"), imageUri = optStringOrNull("imageUri"),
    categoryId = optLongBoxed("categoryId"), difficulty = optStringOrNull("difficulty") ?: "EASY",
    cookingTimeMin = optIntBoxed("cookingTimeMin"), description = optStringOrNull("description"),
    steps = optStringOrNull("steps"), note = optStringOrNull("note"),
    isFavorite = optBoolOr("isFavorite"), isArchived = optBoolOr("isArchived"),
    createdAt = longOr("createdAt"), updatedAt = longOr("updatedAt"),
)

private fun IngredientEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type)
    defaultUnit?.let { put("defaultUnit", it) }; category?.let { put("category", it) }
    icon?.let { put("icon", it) }; imageUri?.let { put("imageUri", it) }
    put("isDeleted", isDeleted); put("createdAt", createdAt)
}

private fun JSONObject.toIngredient() = IngredientEntity(
    id = getLong("id"), name = stringOr("name"), type = optStringOrNull("type") ?: "INGREDIENT",
    defaultUnit = optStringOrNull("defaultUnit"), category = optStringOrNull("category"),
    icon = optStringOrNull("icon"), imageUri = optStringOrNull("imageUri"),
    isDeleted = optBoolOr("isDeleted"), createdAt = longOr("createdAt"),
)

private fun RecipeIngredientEntity.toJson() = JSONObject().apply {
    put("id", id); put("recipeId", recipeId); put("ingredientId", ingredientId)
    quantity?.let { put("quantity", it) }; unit?.let { put("unit", it) }
    put("ingredientType", ingredientType); note?.let { put("note", it) }; put("sortOrder", sortOrder)
}

private fun JSONObject.toRecipeIngredient() = RecipeIngredientEntity(
    id = getLong("id"), recipeId = getLong("recipeId"), ingredientId = getLong("ingredientId"),
    quantity = if (isNull("quantity")) null else getDouble("quantity"),
    unit = optStringOrNull("unit"), ingredientType = optStringOrNull("ingredientType") ?: "INGREDIENT",
    note = optStringOrNull("note"), sortOrder = optIntBoxed("sortOrder") ?: 0,
)

private fun InventoryItemEntity.toJson() = JSONObject().apply {
    put("id", id); put("ingredientId", ingredientId)
    quantity?.let { put("quantity", it) }; unit?.let { put("unit", it) }
    quantityLevel?.let { put("quantityLevel", it) }
    purchaseDate?.let { put("purchaseDate", it) }; productionDate?.let { put("productionDate", it) }
    expireDate?.let { put("expireDate", it) }
    location?.let { put("location", it) }; note?.let { put("note", it) }
    put("isDeleted", isDeleted); put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun JSONObject.toInventoryItem() = InventoryItemEntity(
    id = getLong("id"), ingredientId = getLong("ingredientId"),
    quantity = if (isNull("quantity")) null else getDouble("quantity"),
    unit = optStringOrNull("unit"), quantityLevel = optStringOrNull("quantityLevel"),
    purchaseDate = optLongBoxed("purchaseDate"), productionDate = optLongBoxed("productionDate"),
    expireDate = optLongBoxed("expireDate"), location = optStringOrNull("location"),
    note = optStringOrNull("note"), isDeleted = optBoolOr("isDeleted"),
    createdAt = longOr("createdAt"), updatedAt = longOr("updatedAt"),
)

private fun InventoryTransactionEntity.toJson() = JSONObject().apply {
    put("id", id); put("inventoryItemId", inventoryItemId); put("ingredientId", ingredientId)
    changeQuantity?.let { put("changeQuantity", it) }; unit?.let { put("unit", it) }
    put("type", type); sourceType?.let { put("sourceType", it) }
    sourceId?.let { put("sourceId", it) }; note?.let { put("note", it) }; put("createdAt", createdAt)
}

private fun JSONObject.toTransaction() = InventoryTransactionEntity(
    id = getLong("id"), inventoryItemId = getLong("inventoryItemId"), ingredientId = getLong("ingredientId"),
    changeQuantity = if (isNull("changeQuantity")) null else getDouble("changeQuantity"),
    unit = optStringOrNull("unit"), type = stringOr("type"),
    sourceType = optStringOrNull("sourceType"), sourceId = optLongBoxed("sourceId"),
    note = optStringOrNull("note"), createdAt = longOr("createdAt"),
)

private fun MealPlanEntity.toJson() = JSONObject().apply {
    put("id", id); put("date", date); put("mealType", mealType); put("recipeId", recipeId)
    servings?.let { put("servings", it) }; put("sortOrder", sortOrder); put("status", status)
    note?.let { put("note", it) }; put("createdAt", createdAt); put("updatedAt", updatedAt)
}

private fun JSONObject.toMealPlan() = MealPlanEntity(
    id = getLong("id"), date = stringOr("date"), mealType = stringOr("mealType"),
    recipeId = getLong("recipeId"), servings = optIntBoxed("servings"),
    sortOrder = optIntBoxed("sortOrder") ?: 0, status = optStringOrNull("status") ?: "PLANNED",
    note = optStringOrNull("note"), createdAt = longOr("createdAt"), updatedAt = longOr("updatedAt"),
)

private fun MealRecordEntity.toJson() = JSONObject().apply {
    put("id", id); put("date", date); put("mealType", mealType)
    servings?.let { put("servings", it) }; completedAt?.let { put("completedAt", it) }
    note?.let { put("note", it) }
}

private fun JSONObject.toMealRecord() = MealRecordEntity(
    id = getLong("id"), date = stringOr("date"), mealType = stringOr("mealType"),
    servings = optIntBoxed("servings"), completedAt = optLongBoxed("completedAt"),
    note = optStringOrNull("note"),
)

private fun CategoryEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); icon?.let { put("icon", it) }; put("sortOrder", sortOrder)
}

private fun JSONObject.toCategory() = CategoryEntity(
    id = getLong("id"), name = stringOr("name"), icon = optStringOrNull("icon"),
    sortOrder = optIntBoxed("sortOrder") ?: 0,
)

private fun IngredientTypeEntity.toJson() = JSONObject().apply {
    put("key", key); put("label", label); put("sortOrder", sortOrder)
}

private fun JSONObject.toIngredientType() = IngredientTypeEntity(
    key = stringOr("key"), label = stringOr("label"),
    sortOrder = optIntBoxed("sortOrder") ?: 0,
)

private fun TagEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("sortOrder", sortOrder)
}

private fun JSONObject.toTag() = TagEntity(
    id = getLong("id"), name = stringOr("name"), sortOrder = optIntBoxed("sortOrder") ?: 0,
)

private fun RecipeTagCrossRef.toJson() = JSONObject().apply {
    put("recipeId", recipeId); put("tagId", tagId)
}

private fun JSONObject.toCrossRef() = RecipeTagCrossRef(
    recipeId = getLong("recipeId"), tagId = getLong("tagId"),
)

private fun AppSettingEntity.toJson() = JSONObject().apply {
    put("key", key); put("value", value)
}

private fun JSONObject.toSetting() = AppSettingEntity(key = stringOr("key"), value = stringOr("value"))
