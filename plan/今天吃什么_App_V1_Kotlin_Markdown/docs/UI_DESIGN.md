# 06｜UI 设计系统

## 1. 视觉方向

关键词：

> **低饱和、柔和、干净、温暖、轻食感、现代 Android**

主视觉采用低饱和紫渐变，但不要使用高饱和荧光紫。

## 2. Design Tokens

所有颜色、字体、间距、圆角统一集中定义，业务页面禁止随意写魔法值。

### 2.1 颜色

推荐初始 Token：

```text
Primary          #8B7CF6
PrimaryDark      #7164D9
PrimaryLight     #B8AFFB
PrimarySoft      #F0EEFF
Background       #FAF9FD
Surface          #FFFFFF
SurfaceSoft      #F5F3FA
TextPrimary      #2B2940
TextSecondary    #77748A
TextTertiary     #A09DAF
Divider          #E9E6F0
Success          #79BFA7
Warning          #E6B56B
Danger           #D9828B
```

### 2.2 紫渐变

主渐变建议：

```text
GradientStart = #A79AF7
GradientEnd   = #7C6BE8
```

主要使用位置：

- CTA 按钮
- 推荐卡片重点区域
- 首页主视觉区域
- 浮动操作按钮

不要全屏大面积使用渐变。

## 3. 字体层级

使用 Android 系统字体体系，中文与英文统一走 Material Typography 配置。

```text
Display     28sp / Bold
Title       22sp / SemiBold
Headline    18sp / SemiBold
Body        16sp / Regular
BodySmall   14sp / Regular
Caption     12sp / Regular
```

原则：

- 页面标题 22sp
- 卡片标题 16~18sp
- 正文 14~16sp
- 辅助说明 12~14sp

## 4. 间距系统

统一采用 4 的倍数：

```text
4dp
8dp
12dp
16dp
20dp
24dp
32dp
```

推荐：

- 页面左右边距：16dp
- 卡片内部：16dp
- 卡片之间：12dp
- 大分组间距：24dp

## 5. 圆角系统

```text
Small    8dp
Medium   12dp
Large    16dp
XL       20dp
Pill     999dp
```

推荐：

- 输入框：12dp
- 普通卡片：16dp
- 大型推荐卡：20dp
- 标签：999dp
- BottomSheet：24dp 顶部圆角

## 6. 阴影

以“轻阴影”为原则，不做厚重悬浮效果。

普通卡片：非常轻或无阴影。  
重点 CTA / FAB：轻微阴影。

## 7. 按钮规范

### Primary Button

- 紫渐变背景
- 白色文字
- 高度 48~52dp
- 圆角 14~16dp

### Secondary Button

- 背景透明或淡紫
- 紫色文字
- 1dp 边框可选

## 8. 输入框

```text
高度：52dp
圆角：12dp
左右 padding：16dp
```

错误状态必须同时给出：

- 边框变化
- 错误说明

不能只靠颜色表达错误。

## 9. 标签

示例：

```text
[家常] [快手] [不辣] [15分钟]
```

采用浅紫底 + 深紫字。

## 10. 食材状态颜色

```text
正常      → Success / 中性
临期      → Warning
已过期    → Danger
```

颜色必须配合文字，例如：

> ⚠ 番茄还有 1 天过期

不能只显示一个黄色圆点。

## 11. Bottom Navigation

四项：

```text
首页     菜谱     食材     我的
```

当前项：

- 紫色图标
- 紫色文字
- 可使用淡紫色选中背景

非当前项：中性灰紫。

## 12. 首页设计原则

首页中最显眼的 CTA 必须是：

> **帮我安排** / **添加菜品**

而不是“查看统计”。

## 13. 原型参考

参见：

`assets/app_ui_prototype.png`

这张图用于整体布局和视觉方向参考，真正开发时以本 Design Token 为最终规范。
