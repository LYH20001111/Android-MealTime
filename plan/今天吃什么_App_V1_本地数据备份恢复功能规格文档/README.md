# 「今天吃什么」App V1 —— 本地数据备份与恢复功能设计规范

> 版本：V1.0  
> 平台：Android  
> 技术栈：Kotlin + Jetpack Compose + Room  
> 核心原则：**结构化数据与图片附件分离管理，完整备份采用 ZIP 容器统一保存。**

## 1. 功能背景

菜谱通常包含名称、食材、调料、步骤、备注和图片。若仅导出 JSON，保存原设备的 `content://` / `file://` URI，换手机后图片引用可能失效。

因此 V1 建议：

> **JSON 保存结构化数据 + 图片作为附件 + ZIP 作为完整备份文件。**

## 2. 产品目标

支持：
- 换手机后恢复菜谱、图片、食材、用餐记录；
- 用户定期保存本地完整备份；
- 高级用户单独导出纯 JSON。

因此：

```text
数据备份 ≠ 导出 JSON
```

## 3. 功能入口

“我的” → “数据管理”

```text
数据管理
├── 数据备份
├── 恢复数据
└── 导出 JSON
```

推荐文案：

- 数据备份：备份菜谱、图片、食材和用餐记录
- 恢复数据：从备份文件恢复到当前设备
- 导出 JSON：导出结构化数据，仅适合高级用户

## 4. 完整备份文件

建议文件名：

```text
今天吃什么_backup_2026-09-08.zip
```

结构：

```text
MEALTIME_BACKUP.zip
├── manifest.json
├── data.json
├── images/
│   ├── recipes/
│   ├── ingredients/
│   └── avatars/
└── README.txt
```

## 5. 为什么使用 ZIP

ZIP 可以同时保存结构化数据、图片、版本信息和未来其他附件。

相比把图片 Base64 塞进 JSON：
- 体积更合理；
- JSON 可读性更好；
- 导入时内存压力更小；
- 更容易扩展附件。

## 6. 图片存储策略

用户从系统相册选择图片：

```text
系统相册
↓
content://...
↓
App 复制
↓
压缩为 WebP
↓
App 私有目录
```

建议：

```text
files/
└── images/
    ├── recipes/
    ├── ingredients/
    └── avatars/
```

数据库保存相对路径，而不是原始 URI：

```text
images/recipes/{uuid}.webp
```

运行时再通过：

```kotlin
File(context.filesDir, imagePath)
```

解析真实文件。

## 7. RecipeEntity 建议

```kotlin
@Entity(tableName = "recipe")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String,

    val name: String,

    val imagePath: String? = null,

    val categoryId: Long? = null,

    val difficulty: String? = null,

    val cookingTimeMin: Int? = null,

    val description: String? = null,

    val note: String? = null,

    val isFavorite: Boolean = false,

    val isArchived: Boolean = false,

    val createdAt: Long,

    val updatedAt: Long
)
```

其中：
- `id`：Room 本地关系主键；
- `uuid`：跨设备稳定的数据身份；
- `imagePath`：App 私有目录相对路径。

## 8. UUID 设计

不建议未来仅依赖自增 ID。

推荐：

```text
id   = 本地数据库关联
uuid = 跨设备稳定身份
```

UUID 可用于：
- 备份恢复；
- 数据去重；
- 云同步；
- 未来家庭共享。

## 9. data.json

JSON 只保存结构化数据和图片相对路径，不保存旧设备的 `content://` URI。

示例：

```json
{
  "recipes": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "name": "番茄炒蛋",
      "imagePath": "images/recipes/550e8400-e29b-41d4-a716-446655440000.webp",
      "difficulty": "EASY",
      "cookingTimeMin": 15
    }
  ],
  "ingredients": [],
  "recipeIngredients": [],
  "inventoryItems": [],
  "inventoryTransactions": [],
  "mealPlans": [],
  "mealRecords": [],
  "categories": [],
  "tags": [],
  "recipeTags": [],
  "appSettings": []
}
```

## 10. manifest.json

用于描述备份包格式和版本：

```json
{
  "format": "MEALTIME_BACKUP",
  "backupVersion": 1,
  "appVersion": "1.0.0",
  "databaseVersion": 1,
  "createdAt": "2026-09-08T15:12:00+08:00",
  "files": {
    "data": "data.json",
    "imageDirectory": "images/",
    "recipeImageCount": 12,
    "ingredientImageCount": 3,
    "totalImageCount": 15
  }
}
```

## 11. Backup Version 与 Database Version

必须区分：

```text
backupVersion
```

表示备份文件格式。

```text
databaseVersion
```

表示 Room 数据库版本。

以后数据格式变化时，可以按备份版本做迁移。

## 12. 图片压缩规范

建议导出备份前，将图片处理为：

```text
最长边：约 1600px
格式：WebP
质量：80～85
```

不要把用户原始 8MB / 12MB / 20MB 照片无压缩塞进备份。

## 13. 图片命名规范

建议：

```text
images/recipes/{uuid}.webp
```

不要使用用户原始文件名作为唯一标识。

好处：
- 避免重名；
- 避免中文文件名差异；
- 跨设备稳定；
- 恢复简单。

## 14. 完整备份流程

```text
点击“数据备份”
↓
查询 Room 全部业务数据
↓
查询需要备份的图片
↓
创建临时目录
↓
生成 data.json
↓
复制/压缩图片
↓
生成 manifest.json
↓
生成 README.txt
↓
ZIP 压缩
↓
通过 Android 文件系统保存/分享
```

## 15. 备份完整性检查

备份前建议检查：
- JSON 是否可序列化；
- 图片文件是否存在；
- 图片是否可读取；
- UUID 是否为空；
- UUID 是否重复；
- 外键关联是否完整。

单张图片异常时，不建议直接让全部备份失败，可以记录异常文件并提示用户。

## 16. 恢复流程

```text
点击“恢复数据”
↓
选择 ZIP
↓
读取 manifest.json
↓
检查 backupVersion
↓
验证 data.json
↓
验证图片
↓
显示恢复预览
↓
用户确认
↓
恢复图片到临时目录
↓
恢复数据库
↓
移动图片到正式目录
↓
完成
```

## 17. 恢复预览

建议在真正执行前显示：

```text
发现备份数据

备份日期：2026-09-08
菜谱：128 道
食材：56 项
用餐记录：340 条
图片：118 张
备份大小：86 MB

[取消]    [开始恢复]
```

## 18. 恢复安全策略

推荐：

```text
ZIP
↓
临时目录
↓
完整验证
↓
Room Transaction
↓
正式图片目录
↓
完成
```

失败时：
- 清理临时目录；
- 不破坏当前有效数据。

## 19. 导入冲突

V1 不建议做复杂数据合并。

建议优先支持：

> **完整恢复**

恢复前必须明确提示当前数据可能被替换。

未来 V2 再考虑：
- 智能合并；
- UUID 去重；
- 云同步。

## 20. 导入为新数据

V1 可不做。

原因是复杂度来自：
- Recipe；
- Ingredient；
- MealPlan；
- Inventory；
- Category；
- Tag；
- 关联表。

建议：

```text
V1：完整恢复
V2：智能合并
```

## 21. 库存与用餐记录

完整备份应包含：

```text
InventoryItem
InventoryTransaction
MealPlan
MealRecord
```

避免恢复后只剩菜谱，却丢失库存流水和历史用餐记录。

## 22. AppSetting 处理

可以备份业务设置，例如：

```text
默认早餐时间
默认午餐时间
默认晚餐时间
临期提醒开关
临期提醒阈值
```

不应备份设备绑定或系统权限状态，例如：
- 通知权限状态；
- 原设备 URI；
- Android 设备标识。

## 23. Android 文件系统方案

推荐使用 Storage Access Framework：

导出：
```text
ACTION_CREATE_DOCUMENT
```

恢复：
```text
ACTION_OPEN_DOCUMENT
```

好处：
- 使用系统文件选择器；
- 用户可以保存到本地或云盘；
- 不需要自建文件管理器。

## 24. Kotlin 分层建议

```text
presentation
└── dataManagement
    ├── BackupScreen
    ├── RestorePreviewScreen
    └── DataManagementViewModel

domain
├── BackupDataUseCase
├── RestoreDataUseCase
├── ExportJsonUseCase
└── ValidateBackupUseCase

data
├── backup
│   ├── BackupExporter
│   ├── BackupImporter
│   ├── BackupManifest
│   └── BackupJsonSerializer
├── image
│   ├── ImageStorage
│   └── ImageCompressor
└── local
    └── Room
```

## 25. 核心技术对象

```text
BackupDataUseCase
RestoreDataUseCase
ExportJsonUseCase
ValidateBackupUseCase

BackupExporter
BackupImporter
BackupManifest
BackupJsonSerializer
BackupValidator

ImageStorage
ImageCompressor
```

## 26. BackupExporter 职责

```text
Room → data.json
本地图片 → ZIP/images
Manifest → ZIP
```

不处理 UI。

## 27. BackupImporter 职责

```text
ZIP
↓
Manifest
↓
data.json
↓
图片
↓
Room
```

不处理 UI。

## 28. ImageStorage 职责

建议：

```kotlin
copyFromUri()
saveRecipeImage()
deleteRecipeImage()
resolveImageFile()
```

例如：

```kotlin
suspend fun copyRecipeImage(
    sourceUri: Uri,
    recipeUuid: String
): String
```

返回：

```text
images/recipes/{uuid}.webp
```

## 29. ImageCompressor 职责

```text
读取
↓
缩放
↓
压缩
↓
WebP
```

建议：

```text
maxDimension = 1600
quality = 80..85
```

## 30. BackupValidator 职责

检查：
- manifest 是否存在；
- format 是否正确；
- backupVersion 是否支持；
- data.json 是否存在；
- JSON 是否合法；
- 图片路径是否安全；
- UUID 是否重复。

## 31. ZIP 路径安全

恢复 ZIP 时必须防止目录穿越。

禁止：

```text
../../xxx
```

只允许恢复到临时目录内部，并限制可恢复路径。

建议只接受：

```text
images/recipes/*
images/ingredients/*
images/avatars/*
data.json
manifest.json
README.txt
```

## 32. 用户体验

### 备份中

```text
正在备份...

菜谱 128 / 128
图片 116 / 118
食材 56 / 56
```

### 备份完成

```text
备份完成

菜谱：128
食材：56
图片：118

今天吃什么_backup_2026-09-08.zip

[完成]
```

### 恢复成功

```text
恢复完成

128 道菜谱
56 项食材
118 张图片
340 条用餐记录

[完成]
```

### 恢复失败

```text
恢复失败

备份版本过高，
当前 App 暂不支持。

请升级 App 后再尝试。

[确定]
```

## 33. 数据管理 UI

推荐：

```text
数据管理

┌──────────────────────────────┐
│ 数据备份                     │
│ 备份菜谱、图片、食材和用餐记录 │
│                         >    │
└──────────────────────────────┘

┌──────────────────────────────┐
│ 恢复数据                     │
│ 从备份文件恢复到当前设备      │
│                         >    │
└──────────────────────────────┘

高级
┌──────────────────────────────┐
│ 导出 JSON                   │
│ 仅导出结构化数据             │
│                         >    │
└──────────────────────────────┘
```

## 34. V1 验收标准

### 备份
- [ ] 核心 Room 数据可导出
- [ ] 菜谱图片随 ZIP 保存
- [ ] 不依赖旧 content:// URI
- [ ] data.json 可解析
- [ ] manifest.json 可解析
- [ ] backupVersion 存在
- [ ] 可以使用系统文件保存/分享

### 恢复
- [ ] ZIP 可以选择
- [ ] manifest 校验
- [ ] JSON 校验
- [ ] 图片恢复
- [ ] `Recipe.imagePath` 正确
- [ ] Room 数据恢复
- [ ] 恢复后图片可正常显示
- [ ] App 重启后图片仍正常
- [ ] 恢复失败不破坏当前数据

### 图片
- [ ] 进入 App 私有目录
- [ ] 数据库使用相对路径
- [ ] 导出前压缩
- [ ] 恢复后路径正确
- [ ] 不依赖旧设备 URI

## 35. 最终推荐架构

```text
用户照片
   ↓
ImageStorage
   ↓
App 私有目录
images/recipes/*.webp
   ↓
Recipe.imagePath
   ↓
┌────────────────────────────┐
│                            │
│ 正常使用              数据备份 │
│                            │
└────────────────────────────┘
                         ↓
              ┌──────────┴──────────┐
              ↓                     ↓
          data.json              images/
              │                     │
              └──────────┬──────────┘
                         ↓
                        ZIP
                         ↓
                      用户保存
                         ↓
                     新设备恢复
                         ↓
               ┌─────────┴─────────┐
               ↓                   ↓
            数据恢复             图片恢复
               │                   │
               └─────────┬─────────┘
                         ↓
                  imagePath 正常工作
```

## 36. 最终设计结论

V1 最推荐：

> **App 私有图片存储 + 数据库保存相对路径 + JSON 保存结构化数据 + ZIP 保存完整备份 + manifest 记录版本。**

不要采用：

> **把所有内容，包括图片 Base64，塞进一个 JSON。**

用户最终看到的产品体验应该是：

```text
我的
↓
数据管理
↓
数据备份
↓
保存一个 ZIP

换手机
↓
我的
↓
数据管理
↓
恢复数据
↓
选择 ZIP
↓
预览
↓
确认
↓
菜谱 + 图片 + 食材 + 历史记录全部恢复
```
