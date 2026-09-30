package com.skyanchor.mealtime.core.common

import android.content.Context
import androidx.annotation.StringRes

/**
 * 非 Compose 环境下（ViewModel / 数据层 / 通知）取当前语言字符串的统一入口。
 * 在 Application.onCreate 初始化；仅在组合树内应使用 stringResource 而非本类。
 */
object AppStrings {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes id: Int): String =
        requireNotNull(appContext) { "AppStrings 未初始化" }.getString(id)

    fun get(@StringRes id: Int, vararg args: Any?): String =
        requireNotNull(appContext) { "AppStrings 未初始化" }.getString(id, *args)
}
