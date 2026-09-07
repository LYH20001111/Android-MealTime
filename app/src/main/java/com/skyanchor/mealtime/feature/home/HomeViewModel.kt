package com.skyanchor.mealtime.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.model.InventoryItem
import com.skyanchor.mealtime.core.model.MealStatus
import com.skyanchor.mealtime.core.model.MealType
import com.skyanchor.mealtime.core.model.Recipe
import com.skyanchor.mealtime.core.model.chineseLabel
import com.skyanchor.mealtime.domain.repository.MealRepository
import com.skyanchor.mealtime.domain.repository.RecipeRepository
import com.skyanchor.mealtime.domain.repository.SettingsRepository
import com.skyanchor.mealtime.domain.usecase.CreateMealPlanUseCase
import com.skyanchor.mealtime.domain.usecase.GetDailyRecommendationUseCase
import com.skyanchor.mealtime.domain.usecase.GetExpiringInventoryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 餐次中的一道菜 */
data class HomeMealDish(
    val planId: Long,
    val recipeId: Long,
    val name: String,
    val imageUri: String? = null,
    /** 辅助信息，如 "家常 · 15分钟"（今日三餐规格文档 §4） */
    val meta: String = "",
    val isCompleted: Boolean,
)

/** 今日推荐中的一道菜（Hero Card 展示数据） */
data class HomeRecommendation(
    val recipeId: Long,
    val name: String,
    val imageUri: String?,
    /** 如 "家常菜 · 15分钟" */
    val meta: String,
)

/** 今日推荐模块状态（规格文档 §25） */
sealed interface RecommendationUiState {
    /** 首次读取，展示低饱和紫 Skeleton */
    data object Loading : RecommendationUiState

    /** 读取失败，展示"暂时无法获取今日推荐" + 重新加载 */
    data object Error : RecommendationUiState

    /** 就绪；items 为空即空态（§2：0 道菜显示 Empty State） */
    data class Ready(val items: List<HomeRecommendation>) : RecommendationUiState
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val expiring: List<InventoryItem> = emptyList(),
    val expiringCount: Int = 0,
    val recommendation: RecommendationUiState = RecommendationUiState.Loading,
    val breakfast: List<HomeMealDish> = emptyList(),
    val lunch: List<HomeMealDish> = emptyList(),
    val dinner: List<HomeMealDish> = emptyList(),
    val note: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    mealRepository: MealRepository,
    getExpiringInventory: GetExpiringInventoryUseCase,
    private val settingsRepository: SettingsRepository,
    getDailyRecommendation: GetDailyRecommendationUseCase,
    recipeRepository: RecipeRepository,
    private val createMealPlan: CreateMealPlanUseCase,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** 推荐读取失败后的手动重试信号 */
    private val recommendationRetry = MutableStateFlow(0)

    /**
     * 当天推荐结果：首次进入懒生成一次（幂等，当天固定），
     * 之后与有效菜谱/分类联动 —— 推荐菜被归档时自动隐藏（§27），不重新随机。
     */
    private val recommendationState: Flow<RecommendationUiState> =
        recommendationRetry.flatMapLatest {
            flow {
                emit(RecommendationUiState.Loading)
                val recommended = getDailyRecommendation(today)
                emitAll(
                    combine(
                        recipeRepository.observeRecipes(),
                        recipeRepository.observeCategories(),
                    ) { recipes, categories ->
                        val activeById = recipes.associateBy { it.id }
                        val categoryNames = categories.associate { it.id to it.name }
                        RecommendationUiState.Ready(
                            items = recommended.mapNotNull { recipe ->
                                activeById[recipe.id]?.let { active ->
                                    HomeRecommendation(
                                        recipeId = active.id,
                                        name = active.name,
                                        imageUri = active.imageUri,
                                        meta = recommendationMeta(active, categoryNames),
                                    )
                                }
                            },
                        )
                    },
                )
            }.catch {
                emit(RecommendationUiState.Error)
            }
        }

    private val _addedNotice = MutableStateFlow<String?>(null)

    /** "已加入今天午餐"之类的操作反馈 */
    val addedNotice: StateFlow<String?> = _addedNotice.asStateFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        mealRepository.observeMeals(today),
        getExpiringInventory(),
        settingsRepository.observe(noteKey(today)),
        recommendationState,
        recipeRepository.observeCategories(),
    ) { plans, expiring, note, recommendation, categories ->
        val categoryNames = categories.associate { it.id to it.name }
        fun dishesOf(type: MealType) = plans
            .filter { it.mealType == type }
            .map { plan ->
                HomeMealDish(
                    planId = plan.id,
                    recipeId = plan.recipe.id,
                    name = plan.recipe.name,
                    imageUri = plan.recipe.imageUri,
                    meta = recommendationMeta(plan.recipe, categoryNames),
                    isCompleted = plan.status == MealStatus.COMPLETED,
                )
            }

        HomeUiState(
            isLoading = false,
            date = today,
            expiring = expiring,
            expiringCount = expiring.size,
            recommendation = recommendation,
            breakfast = dishesOf(MealType.BREAKFAST),
            lunch = dishesOf(MealType.LUNCH),
            dinner = dishesOf(MealType.DINNER),
            note = note ?: "",
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true),
    )

    /** §25：读取失败时手动重试 */
    fun retryRecommendation() {
        recommendationRetry.value += 1
    }

    /** §22：把推荐菜加入今日某一餐 */
    fun addToMeal(recipeId: Long, mealType: MealType) {
        viewModelScope.launch {
            val name = (uiState.value.recommendation as? RecommendationUiState.Ready)
                ?.items?.firstOrNull { it.recipeId == recipeId }?.name.orEmpty()
            createMealPlan(date = today, mealType = mealType, recipeId = recipeId)
            _addedNotice.value = "已把「$name」加入今天${mealLabel(mealType)}"
        }
    }

    fun consumeAddedNotice() {
        _addedNotice.value = null
    }

    fun saveNote(text: String) {
        viewModelScope.launch {
            settingsRepository.putString(noteKey(today), text.trim())
        }
    }

    private fun recommendationMeta(
        recipe: Recipe,
        categoryNames: Map<Long, String>,
    ): String {
        val category = recipe.categoryId?.let { categoryNames[it] }
        val parts = buildList {
            category?.let(::add)
            recipe.cookingTimeMin?.let { add("$it 分钟") }
        }
        return if (parts.isEmpty()) recipe.difficulty.chineseLabel else parts.joinToString(" · ")
    }

    private fun mealLabel(type: MealType): String = when (type) {
        MealType.BREAKFAST -> "早餐"
        MealType.LUNCH -> "午餐"
        MealType.DINNER -> "晚餐"
    }

    companion object {
        const val NOTE_KEY_PREFIX = "note:"

        fun noteKey(date: LocalDate): String = NOTE_KEY_PREFIX + date

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    mealRepository = container.mealRepository,
                    getExpiringInventory = GetExpiringInventoryUseCase(
                        container.inventoryRepository,
                        container.settingsRepository,
                    ),
                    settingsRepository = container.settingsRepository,
                    getDailyRecommendation = GetDailyRecommendationUseCase(
                        container.dailyRecommendationRepository,
                        container.recipeRepository,
                    ),
                    recipeRepository = container.recipeRepository,
                    createMealPlan = CreateMealPlanUseCase(container.mealRepository),
                )
            }
        }
    }
}

