# 「今天吃什么」App V1 —— 新增食材页面规范（库存状态优化版）

> 版本：V1.1  
> 平台：Android  
> 技术栈：Kotlin + Jetpack Compose + Room  
> 页面：新增食材（Add Inventory Item）  
> 本次重点：**库存状态增加“有库存 / 无库存”，并根据状态动态控制后续字段。**

---

# 1. 本次核心变更

原方案中“库存”主要通过数量和单位体现。

本版调整为：

```text
库存 *
[ 有库存 ] [ 无库存 ]
```

并根据用户选择动态改变页面内容。

## 有库存

用户选择：

```text
有库存
```

显示：

```text
数量 *
单位 *
数量级别（可选）
保质期（可选）
购买日期（可选）
生产日期（可选）
存放位置（可选）
备注（可选）
```

---

## 无库存

用户选择：

```text
无库存
```

则：

```text
隐藏 / 禁用：
数量
单位
数量级别
保质期
购买日期
生产日期
存放位置
```

只保留：

```text
备注（可选）
```

这样用户可以记录：

> “这个食材我常用，但目前家里没有。”

例如：

```text
番茄酱
无库存
备注：下次买一瓶
```

这对后续采购清单也有扩展价值。

---

# 2. 为什么要增加库存状态

“库存”实际上存在两种业务状态：

```text
有库存
```

表示用户当前拥有该食材。

```text
无库存
```

表示用户知道/记录这个食材，但当前没有库存。

因此：

> “食材存在”和“当前有库存”应该分开理解。

---

# 3. 页面目标

新增食材页面需要让用户快速完成：

```text
我记录的是什么？
↓
它是什么类型？
↓
现在有还是没有？
↓
如果有，有多少？
```

再决定是否补充：

```text
什么时候过期？
放在哪里？
什么时候买？
有什么备注？
```

---

# 4. V1 必填项

统一定义为 4 类：

```text
食材名称 *
食材图片 *
类型 *
库存状态 *
```

注意：

> “有库存”时，数量 + 单位属于库存信息，是“有库存”状态下的必填内容。

因此最终校验规则为：

```text
无库存：
名称 + 图片 + 类型 + 库存状态

有库存：
名称 + 图片 + 类型 + 库存状态 + 数量 + 单位
```

---

# 5. 页面信息结构

```text
新增食材
│
├── Header
│
├── 轻量引导 Banner
│
├── ① 基本信息 · 必填
│   ├── 食材名称 *
│   ├── 食材图片 *
│   └── 类型 *
│
├── ② 库存状态 · 必填
│   ├── 有库存
│   └── 无库存
│
├── ③ 库存详情
│   ├── 数量
│   ├── 单位
│   └── 数量级别
│
├── ④ 保质期信息
│   ├── 过期日期
│   ├── 购买日期
│   └── 生产日期
│
├── ⑤ 存放与备注
│   ├── 存放位置
│   └── 备注
│
└── BottomBar
    └── 保存食材
```

但实际页面会根据“库存状态”动态显示区域。

---

# 6. 有库存状态

用户选择：

```text
✓ 有库存
```

页面继续显示：

```text
库存详情
数量 *
单位 *

数量级别

保质期信息
过期日期
购买日期
生产日期

存放位置

备注
```

---

# 7. 无库存状态

用户选择：

```text
✓ 无库存
```

页面变为：

```text
库存状态

[ 有库存 ] [ ✓ 无库存 ]

说明：
当前没有这项食材

备注

[ 例如：下次买一瓶 ]
```

隐藏：

```text
数量
单位
数量级别
过期日期
购买日期
生产日期
存放位置
```

---

# 8. 无库存为什么不需要保质期

因为：

> 当前没有库存，就不存在当前持有商品的有效期管理问题。

因此无库存时：

```text
过期日期 → 无意义
购买日期 → 无意义
生产日期 → 无意义
存放位置 → 无意义
```

这些字段直接隐藏，比显示成灰色禁用状态更清晰。

---

# 9. 无库存为什么保留备注

备注仍然有价值。

例如：

```text
无库存
备注：下次买低盐生抽
```

或者：

```text
无库存
备注：家里用完了，下次采购
```

未来可以直接扩展成：

```text
无库存
→ 加入采购清单
```

---

# 10. 库存状态控件

推荐使用：

```text
Segmented Button
```

或两个大的 Choice Card。

建议：

```text
┌─────────────────┐
│ ✓  有库存       │
│ 当前家里有这项食材│
└─────────────────┘

┌─────────────────┐
│    无库存       │
│ 当前没有这项食材 │
└─────────────────┘
```

在手机上比小 Chip 更容易理解。

---

# 11. “有库存”选中状态

背景：

```text
#EEE9FF
```

边框：

```text
#8068E8
```

图标：

```text
Icons.Outlined.CheckCircle
```

颜色：

```text
#8068E8
```

标题：

```text
有库存
```

文字：

```text
#8068E8
```

---

# 12. “无库存”选中状态

使用同样的组件。

例如：

```text
✓ 无库存
```

背景：

```text
#F6F3FF
```

边框：

```text
#8068E8
```

不使用红色。

因为：

> 无库存不是错误，而是合法业务状态。

---

# 13. 默认状态

对于“新增食材”，建议：

```text
默认：
有库存
```

原因：

> 用户打开“新增食材”通常是在记录刚买到/正在拥有的食材。

用户如果只是想建立“常用食材档案”，可以手动切换到：

```text
无库存
```

---

# 14. 数量与单位

选择：

```text
有库存
```

后出现：

```text
数量 *
[ 4 ]

单位 *
[ 个 ▼ ]
```

数量输入：

```text
整数
小数
```

例如：

```text
4
0.5
500
```

---

# 15. 单位选择

推荐：

```text
Dropdown
```

常用：

```text
个
克
千克
毫升
升
包
瓶
罐
盒
根
颗
把
份
```

---

# 16. 数量级别

只有用户无法精确记录数量时使用。

```text
数量级别（数量不确定时可选）

[少量] [适量] [充足]
```

建议规则：

如果已经输入：

```text
4 个
```

数量级别可以保持可选，但不要强迫用户同时填写。

---

# 17. 保质期

仅在：

```text
有库存
```

状态显示。

结构：

```text
保质期信息

过期日期       选择日期 >
购买日期       选择日期 >
生产日期       选择日期 >
```

---

# 18. 日期逻辑

建议校验：

```text
生产日期 ≤ 购买日期 ≤ 过期日期
```

但每一个日期都允许为空。

例如用户不知道生产日期：

```text
生产日期：空
```

仍然可以保存。

---

# 19. 存放位置

仅在：

```text
有库存
```

状态显示。

输入：

```text
冰箱冷藏 / 冷冻 / 橱柜...
```

未来可以转成选择：

```text
冰箱冷藏
冷冻
橱柜
常温
其他
```

---

# 20. 备注

两种状态都显示。

有库存：

```text
例如：开封后冷藏
```

无库存：

```text
例如：下次买低盐生抽
```

Placeholder 可以根据状态动态变化。

---

# 21. 无库存状态的页面表现

推荐：

```text
库存
┌───────────────────────────────┐
│ ✓ 有库存       ○ 无库存       │
│                               │
│ 当前没有这项食材               │
└───────────────────────────────┘

其他信息 · 可选

备注
[例如：下次买一瓶]
```

不要留下：

```text
数量
单位
保质期
购买日期
生产日期
存放位置
```

的大量空白区域。

---

# 22. 页面高度会动态变化

这是本次优化的重要收益。

有库存：

```text
页面较长
```

无库存：

```text
页面明显变短
```

这样可以让“无库存”记录变得非常快速。

---

# 23. 保存逻辑

## 有库存

检查：

```text
名称 ✅
图片 ✅
类型 ✅
库存状态 ✅
数量 ✅
单位 ✅
```

允许保存。

---

## 无库存

检查：

```text
名称 ✅
图片 ✅
类型 ✅
库存状态 ✅
```

直接允许保存。

不检查：

```text
数量
单位
保质期
购买日期
生产日期
存放位置
```

---

# 24. 数据库建议

`InventoryItemEntity` 建议：

```kotlin
@Entity(tableName = "inventory_item")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String,

    val ingredientId: Long,

    val imagePath: String? = null,

    /**
     * IN_STOCK / OUT_OF_STOCK
     */
    val stockStatus: String,

    val quantity: Double? = null,

    val unit: String? = null,

    val quantityLevel: String? = null,

    val expireDate: Long? = null,

    val purchaseDate: Long? = null,

    val productionDate: Long? = null,

    val storageLocation: String? = null,

    val note: String? = null,

    val isDeleted: Boolean = false,

    val createdAt: Long,

    val updatedAt: Long
)
```

---

# 25. 为什么不能仅靠 quantity 判断库存

不要使用：

```kotlin
quantity == null
```

代表无库存。

因为：

```text
quantity == null
```

还可能表示：

> 用户只是没有录入数量。

这与：

> 当前明确没有库存

不是一回事。

所以建议：

```text
stockStatus = IN_STOCK
stockStatus = OUT_OF_STOCK
```

单独保存。

---

# 26. 状态与数量的业务规则

### IN_STOCK

允许：

```text
quantity
unit
quantityLevel
expireDate
purchaseDate
productionDate
storageLocation
note
```

### OUT_OF_STOCK

建议保存：

```text
quantity = null
unit = null
quantityLevel = null
expireDate = null
purchaseDate = null
productionDate = null
storageLocation = null
note = ...
```

这样数据语义最干净。

---

# 27. 从有库存切换到无库存

如果用户原本：

```text
有库存
番茄
4个
过期 9月10日
冰箱冷藏
```

切换：

```text
无库存
```

建议弹出确认：

```text
切换为无库存？

当前库存数量、日期和存放位置
将不再作为当前库存信息保留。

[取消] [确认]
```

确认后：

```text
quantity → null
expireDate → null
purchaseDate → null
productionDate → null
storageLocation → null
```

备注保留。

---

# 28. 从无库存切换到有库存

切换后：

```text
显示库存详情
```

要求：

```text
数量 *
单位 *
```

然后用户重新填写。

无需恢复之前的旧值。

---

# 29. 为什么建议切换时清空库存详情

避免出现：

```text
无库存
数量：4个
过期：9月10日
```

这种业务矛盾数据。

因此：

> 无库存必须代表当前没有库存信息。

---

# 30. 首页与库存系统的关系

只有：

```text
stockStatus = IN_STOCK
```

的数据：

- 进入当前库存；
- 参与临期计算；
- 参与库存匹配；
- 参与智能推荐。

`OUT_OF_STOCK`：

- 不参与临期；
- 不参与当前库存数量统计；
- 可以作为未来采购候选。

---

# 31. 与智能推荐的关系

例如：

```text
番茄
IN_STOCK
4个
明天过期
```

可以：

```text
↑ 推荐番茄炒蛋
```

而：

```text
番茄
OUT_OF_STOCK
```

不能被算作已有库存。

---

# 32. 与未来采购清单的关系

无库存食材可以成为未来采购候选：

```text
OUT_OF_STOCK
    ↓
用户点击“加入采购清单”
    ↓
ShoppingList
```

这为后续功能预留空间。

---

# 33. 页面视觉设计

继续与“新增菜谱”统一：

### Background

```text
#F8F7FB
```

### Surface

```text
#FFFFFF
```

### Primary

```text
#8068E8
```

### Primary Light

```text
#EEE9FF
```

### Text Primary

```text
#282432
```

### Text Secondary

```text
#777181
```

---

# 34. 页面圆角

```text
Input：16dp
Card：20dp
Cover：20dp
Button：24dp
Stock Choice Card：16～18dp
Chip：12dp
```

---

# 35. 页面间距

```text
页面左右：20～24dp
Section：20～24dp
标题 → 控件：10～12dp
控件之间：12～16dp
```

---

# 36. 引导 Banner

统一使用：

```text
#F0EBFF → #F7F3FF
```

示意：

```text
┌──────────────────────────────┐
│ 添加食材                      │
│ 记录手边库存，让管理更轻松      │
│                        🥕     │
└──────────────────────────────┘
```

不要加入过重的插画。

---

# 37. 图片上传

继续：

```text
添加食材图片
```

建议：

```text
150～180dp
20dp 圆角
#EEE9FF
```

上传成功后：

```text
右上角 ×
```

可删除。

---

# 38. 最终页面示意：有库存

```text
┌──────────────────────────────┐
│ ← 新增食材                  │
│                              │
│ ┌──────────────────────────┐ │
│ │ 添加食材                 │ │
│ │ 记录手边库存，让管理更轻松 │ │
│ └──────────────────────────┘ │
│                              │
│ 基本信息 · 必填4项           │
│                              │
│ 食材名称 *                   │
│ [ 番茄                     ] │
│                              │
│ 食材图片 *                   │
│ ┌──────────────────────────┐ │
│ │      番茄图片        ×    │ │
│ └──────────────────────────┘ │
│                              │
│ 类型 *                       │
│ [✓ 食材] [调料] [其他]       │
│                              │
│ 库存状态 *                   │
│ [✓ 有库存] [  无库存 ]       │
│                              │
│ 库存详情                     │
│ 数量 *             单位 *    │
│ [ 4 ]             [ 个 ▼ ]   │
│                              │
│ 数量级别                     │
│ [少量] [适量] [充足]         │
│                              │
│ 保质期信息                   │
│ [过期日期              >]    │
│ [购买日期              >]    │
│ [生产日期              >]    │
│                              │
│ 存放位置                     │
│ [冰箱冷藏 / 冷冻 / 橱柜...]  │
│                              │
│ 备注                         │
│ [品牌、开封日期等...]        │
│                              │
├──────────────────────────────┤
│          [ 保存食材 ]         │
└──────────────────────────────┘
```

---

# 39. 最终页面示意：无库存

```text
┌──────────────────────────────┐
│ ← 新增食材                  │
│                              │
│ 基本信息 · 必填4项           │
│                              │
│ 食材名称 *                   │
│ [ 番茄                     ] │
│                              │
│ 食材图片 *                   │
│ [        番茄图片       × ]  │
│                              │
│ 类型 *                       │
│ [✓ 食材] [调料] [其他]       │
│                              │
│ 库存状态 *                   │
│ [ 有库存 ] [✓ 无库存 ]       │
│                              │
│ 当前没有这项食材             │
│                              │
│ 其他信息 · 可选              │
│                              │
│ 备注                         │
│ [下次购买 / 采购提醒...]     │
│                              │
├──────────────────────────────┤
│          [ 保存食材 ]         │
└──────────────────────────────┘
```

---

# 40. Compose 状态模型

推荐：

```kotlin
enum class StockStatus {
    IN_STOCK,
    OUT_OF_STOCK
}
```

UI：

```kotlin
data class AddInventoryUiState(
    val name: String = "",
    val imagePath: String? = null,
    val type: IngredientType = IngredientType.FOOD,

    val stockStatus: StockStatus = StockStatus.IN_STOCK,

    val quantity: String = "",
    val unit: String = "",
    val quantityLevel: QuantityLevel? = null,

    val expireDate: LocalDate? = null,
    val purchaseDate: LocalDate? = null,
    val productionDate: LocalDate? = null,

    val storageLocation: String = "",
    val note: String = ""
)
```

---

# 41. Compose 动态显示

核心逻辑：

```kotlin
if (uiState.stockStatus == StockStatus.IN_STOCK) {
    InventoryDetailSection(...)
    ExpirySection(...)
    StorageLocationSection(...)
}

NoteSection(...)
```

这样无库存时天然减少页面内容。

---

# 42. 切换状态

```kotlin
fun onStockStatusChange(status: StockStatus) {
    uiState = when (status) {
        StockStatus.IN_STOCK ->
            uiState.copy(
                stockStatus = status
            )

        StockStatus.OUT_OF_STOCK ->
            uiState.copy(
                stockStatus = status,
                quantity = "",
                unit = "",
                quantityLevel = null,
                expireDate = null,
                purchaseDate = null,
                productionDate = null,
                storageLocation = ""
            )
    }
}
```

生产环境建议通过 ViewModel 管理，而不是直接在 Composable 中修改业务状态。

---

# 43. 验收标准

## 必填

- [ ] 名称必填
- [ ] 图片必填
- [ ] 类型必填
- [ ] 库存状态必选
- [ ] 有库存时数量必填
- [ ] 有库存时单位必填
- [ ] 无库存不需要数量/单位

## 动态页面

- [ ] 有库存显示库存详情
- [ ] 有库存显示保质期
- [ ] 有库存显示存放位置
- [ ] 无库存隐藏上述字段
- [ ] 无库存保留备注
- [ ] 切换为无库存时清理库存相关值
- [ ] 切换为有库存后要求重新填写数量/单位

## 业务

- [ ] IN_STOCK 参与当前库存
- [ ] IN_STOCK 参与临期计算
- [ ] IN_STOCK 参与智能推荐
- [ ] OUT_OF_STOCK 不参与上述计算
- [ ] OUT_OF_STOCK 为未来采购清单预留

---

# 44. 最终设计原则

> **先回答“我有没有”，再决定“有多少、什么时候过期、放在哪里”。**

这会让新增食材页面比原来的长表单更加符合真实库存管理场景。

最终用户可以：

```text
有库存
→ 详细记录

无库存
→ 快速记录
→ 只保留备注
```

让两种场景都保持轻量，不要求用户填写无意义的信息。
