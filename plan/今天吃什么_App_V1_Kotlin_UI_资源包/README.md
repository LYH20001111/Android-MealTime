# 今天吃什么 App V1 UI 资源包

这是一套基于当前首页优化方案的 Android UI 资源包，主视觉采用低饱和紫渐变。

## 资源结构

```text
app/src/main/res/
├── drawable-nodpi/
│   ├── bg_splash.png
│   ├── bg_home_top.png
│   ├── bg_home_bottom.png
│   ├── bg_recommendation.png
│   ├── bg_today_note.png
│   ├── decoration_wave.png
│   └── decoration_leaf.png
├── mipmap-mdpi/
├── mipmap-hdpi/
├── mipmap-xhdpi/
├── mipmap-xxhdpi/
├── mipmap-xxxhdpi/
└── values/
    └── colors.xml
```

## Compose 推荐用法

背景资源建议放在 `drawable-nodpi`，避免被 Android 按密度自动缩放。

示例：

```kotlin
Box(
    modifier = Modifier.fillMaxSize()
) {
    Image(
        painter = painterResource(R.drawable.bg_home_top),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}
```

今日推荐卡片：

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(AppShapes.hero))
) {
    Image(
        painter = painterResource(R.drawable.bg_recommendation),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
    )
}
```

## 使用原则

1. 首页大面积仍以 `#F8F7FB` 为背景。
2. 紫色渐变主要用于 Header、今日推荐、智能推荐和 CTA。
3. 菜品图片应该保持最高视觉权重。
4. 背景装饰透明度要低，不能和菜品图片竞争。
5. 不建议把大幅背景图直接覆盖整个滚动内容区。
6. `bg_home_top` 更适合 Header；`bg_home_bottom` 更适合作为页面底部装饰。
7. `bg_recommendation` 只用于今日推荐 Hero 卡片。
8. `bg_today_note` 只用于今日备注区域。

## 注意

本包中的背景图是根据当前视觉方案制作的开发素材，实际 Android 项目建议在真机上再根据屏幕尺寸、系统状态栏、字体和菜品图片比例做一次微调。
