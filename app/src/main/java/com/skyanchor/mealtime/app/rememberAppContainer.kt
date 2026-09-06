package com.skyanchor.mealtime.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** 从组合树中取手动 DI 容器（Application 即 MealTimeApp） */
@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as MealTimeApp).container
