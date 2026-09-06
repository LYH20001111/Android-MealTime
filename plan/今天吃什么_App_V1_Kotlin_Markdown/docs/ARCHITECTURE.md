# 05｜Kotlin Android 技术架构

## 1. 总体架构

推荐采用：

```text
UI (Compose)
      ↓
ViewModel
      ↓
UseCase
      ↓
Repository
      ↓
Room DAO
      ↓
Room Database
```

### 目标

- UI 与数据访问解耦
- 业务逻辑集中在 UseCase
- ViewModel 负责 UI 状态
- Repository 负责数据来源抽象
- Room 负责本地持久化

## 2. 推荐技术组合

```text
Kotlin
Jetpack Compose
Navigation
ViewModel
StateFlow
Coroutines
Room
WorkManager
```

V1 暂不引入复杂依赖，优先保证可维护性。

## 3. 项目包结构

```text
com.example.todayeat
│
├── app
│   ├── App.kt
│   └── navigation
│
├── core
│   ├── common
│   ├── model
│   ├── ui
│   └── util
│
├── data
│   ├── local
│   │   ├── dao
│   │   ├── entity
│   │   ├── relation
│   │   └── database
│   ├── repository
│   └── mapper
│
├── domain
│   ├── repository
│   └── usecase
│
├── feature
│   ├── home
│   ├── recipe
│   ├── inventory
│   ├── recommend
│   └── profile
│
└── notification
```

## 4. Feature 模块职责

### home

负责：

- 今日三餐
- 今日备注
- 临期提醒卡片
- 快速推荐

### recipe

负责：

- 菜谱列表
- 搜索
- 分类
- 菜谱详情
- 新增/编辑

### inventory

负责：

- 库存列表
- 临期
- 库存详情
- 数量调整
- 库存流水

### recommend

负责：

- 推荐规则计算
- 推荐结果排序

### profile

负责：

- 设置
- 通知
- 数据管理

## 5. UI 状态模型

每个页面推荐采用统一 State：

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
```

对于编辑页面，另设独立 FormState。

## 6. ViewModel 示例

```kotlin
class HomeViewModel(
    private val getTodayMeals: GetTodayMealsUseCase,
    private val getExpiringInventory: GetExpiringInventoryUseCase,
    private val recommendRecipes: RecommendRecipesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load(date: String) {
        viewModelScope.launch {
            // 组合三餐、临期和推荐数据
        }
    }
}
```

## 7. UseCase

建议至少实现：

```text
CreateRecipeUseCase
UpdateRecipeUseCase
ArchiveRecipeUseCase

AddInventoryUseCase
UpdateInventoryUseCase
AdjustInventoryUseCase

CreateMealPlanUseCase
RemoveMealPlanUseCase
CompleteMealUseCase

GetExpiringInventoryUseCase
RecommendRecipesUseCase
ExportDataUseCase
ImportDataUseCase
```

## 8. Repository 接口

例如：

```kotlin
interface RecipeRepository {
    fun observeAll(): Flow<List<Recipe>>
    suspend fun getById(id: Long): Recipe?
    suspend fun save(recipe: Recipe): Long
    suspend fun archive(id: Long)
}
```

## 9. Navigation

推荐路由：

```text
home
meal/{date}/{mealType}
recipe
recipe/{id}
recipe/edit/{id}
recipe/edit/new
inventory
inventory/{id}
inventory/edit/{id}
inventory/edit/new
recommend
consume-confirm/{mealPlanId}
profile
settings
```

## 10. 本地优先原则

V1：

```text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository
 ↓
Room
```

不需要服务器即可完整使用。

未来增加云同步时：

```text
Repository
 ├── LocalDataSource
 └── RemoteDataSource
```

上层业务无需大改。

## 11. 图片处理

数据库只保存 `imageUri`，图片保存在 App 私有目录。

不要把图片二进制直接塞进 Room。

## 12. 后台任务

临期检测建议通过后台任务周期运行；但首页打开时仍应实时重新计算一次，保证数据新鲜。

## 13. 测试策略

### 单元测试

重点测试：

- 推荐算法
- 临期计算
- 库存扣减
- CompleteMealUseCase
- 数量不足处理

### DAO 测试

重点测试：

- 今日餐次查询
- 临期排序
- 菜谱-食材关联
- 库存流水

### UI 测试

重点测试：

- 四大导航
- 新增菜谱
- 新增库存
- 点菜
- 完成用餐
