package com.skyanchor.mealtime.core.common

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 食材保质期状态（R08：由 expireDate 动态计算，不落库） */
enum class ExpiryStatus {
    EXPIRED,
    URGENT,
    NEAR,
    NORMAL,
    NONE,
}

/**
 * 临期规则（PRD §7.4）：
 * 已过期 expireDate < today；紧急 0~urgentDays；临期 urgentDays+1~nearDays；其余正常。
 */
object ExpiryCalculator {

    fun daysUntil(expireDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, expireDate)

    fun status(
        expireDate: LocalDate?,
        today: LocalDate,
        urgentDays: Int = 1,
        nearDays: Int = 3,
    ): ExpiryStatus {
        if (expireDate == null) return ExpiryStatus.NONE
        val days = daysUntil(expireDate, today)
        return when {
            days < 0 -> ExpiryStatus.EXPIRED
            days <= urgentDays.toLong() -> ExpiryStatus.URGENT
            days <= nearDays.toLong() -> ExpiryStatus.NEAR
            else -> ExpiryStatus.NORMAL
        }
    }

    /** 状态的人类可读描述，必须配合颜色一起展示（UI_DESIGN.md §10） */
    fun label(status: ExpiryStatus, days: Long?): String = when (status) {
        ExpiryStatus.EXPIRED -> "已过期"
        ExpiryStatus.URGENT -> when (days) {
            0L -> "今天过期"
            else -> "${days ?: "?"} 天后过期"
        }

        ExpiryStatus.NEAR -> "$days 天后过期"
        ExpiryStatus.NORMAL -> days?.let { "$it 天后过期" } ?: ""
        ExpiryStatus.NONE -> "未设置保质期"
    }
}
