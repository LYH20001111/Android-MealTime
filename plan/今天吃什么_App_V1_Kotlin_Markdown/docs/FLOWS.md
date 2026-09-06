# 03｜用户流程与业务流程

## 1. 新用户首次使用

```mermaid
flowchart TD
    A[首次打开 App] --> B[进入首页]
    B --> C{是否有菜谱?}
    C -- 否 --> D[展示示例/引导]
    D --> E[进入菜谱]
    E --> F[新增菜谱]
    C -- 是 --> G[显示今日安排]
    F --> G
```

## 2. 添加菜谱

```mermaid
flowchart LR
    A[菜谱] --> B[点击 +]
    B --> C[填写基础信息]
    C --> D[添加食材/调料]
    D --> E[添加步骤]
    E --> F[保存]
    F --> G[菜谱详情]
```

## 3. 添加库存

```mermaid
flowchart LR
    A[食材] --> B[点击 +]
    B --> C[选择食材]
    C --> D[输入数量/单位]
    D --> E[设置保质期]
    E --> F[保存库存]
```

## 4. 手动点菜

```mermaid
flowchart TD
    A[首页] --> B[选择早餐/午餐/晚餐]
    B --> C[添加菜品]
    C --> D[搜索菜谱]
    D --> E[选择菜谱]
    E --> F[确认]
    F --> G[写入 MealPlan]
```

## 5. 完成用餐

```mermaid
flowchart TD
    A[点击完成用餐] --> B[获取当前餐次菜谱]
    B --> C[读取 RecipeIngredient]
    C --> D[匹配 InventoryItem]
    D --> E[计算预计消耗]
    E --> F[用户修改实际消耗]
    F --> G[数据库事务]
    G --> H[更新库存]
    G --> I[写库存流水]
    G --> J[写 MealRecord]
    G --> K[更新 MealPlan]
```

## 6. 临期推荐

```mermaid
flowchart TD
    A[后台/打开 App] --> B[检测过期日期]
    B --> C{存在临期?}
    C -- 否 --> D[结束]
    C -- 是 --> E[首页显示提醒]
    E --> F[查看临期]
    F --> G[计算匹配菜谱]
    G --> H[排序推荐]
    H --> I[加入早餐/午餐/晚餐]
```

## 7. 智能推荐规则流程

```text
查询有效库存
   ↓
查询有效菜谱
   ↓
对每个菜谱计算：
  ① 库存匹配率
  ② 临期命中数
  ③ 收藏状态
  ④ 最近食用情况
  ⑤ 制作时间
   ↓
计算总分
   ↓
过滤过度重复菜品
   ↓
按分数降序
   ↓
展示 Top N
```

## 8. 核心异常流程

### 8.1 库存不足

允许点菜，显示警告。

### 8.2 菜谱没有食材

允许保存，但不能参与库存匹配与自动扣减。

### 8.3 库存数量为 0

自动归档库存记录，但保留库存流水。

### 8.4 删除菜谱

菜谱主记录可归档；历史 MealRecord 不删除。

### 8.5 修改库存

所有手动调整必须写 InventoryTransaction。

## 9. 一次“吃饭”完整时序

```text
用户
 │
 ├─ 打开首页
 │
 ├─ 查看午餐
 │
 ├─ 点击完成用餐
 │
 ├─ 确认实际消耗
 │
 ▼
CompleteMealUseCase
 │
 ├─ 读取 MealPlan
 ├─ 读取 RecipeIngredient
 ├─ 匹配 InventoryItem
 ├─ 开启 DB Transaction
 │    ├─ 更新库存
 │    ├─ 写流水
 │    ├─ 创建 MealRecord
 │    └─ 更新 MealPlan
 │
 ▼
首页重新计算临期与推荐
```
