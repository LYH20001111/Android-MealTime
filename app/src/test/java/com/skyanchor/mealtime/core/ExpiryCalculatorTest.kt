package com.skyanchor.mealtime.core

import com.skyanchor.mealtime.core.common.ExpiryCalculator
import com.skyanchor.mealtime.core.common.ExpiryStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 临期规则边界测试（PRD §7.4：已过期 / 紧急 0~1 / 临期 2~3 / 正常 >3） */
class ExpiryCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 6)

    private fun status(daysFromToday: Long): ExpiryStatus =
        ExpiryCalculator.status(today.plusDays(daysFromToday), today)

    @Test
    fun expiredWhenDateBeforeToday() {
        assertEquals(ExpiryStatus.EXPIRED, status(-1))
        assertEquals(ExpiryStatus.EXPIRED, status(-30))
    }

    @Test
    fun urgentWithinZeroToOneDay() {
        assertEquals(ExpiryStatus.URGENT, status(0)) // 今天过期
        assertEquals(ExpiryStatus.URGENT, status(1)) // 明天过期
    }

    @Test
    fun nearWithinTwoToThreeDays() {
        assertEquals(ExpiryStatus.NEAR, status(2))
        assertEquals(ExpiryStatus.NEAR, status(3))
    }

    @Test
    fun normalBeyondThreshold() {
        assertEquals(ExpiryStatus.NORMAL, status(4))
        assertEquals(ExpiryStatus.NORMAL, status(365))
    }

    @Test
    fun noneWhenNoExpireDate() {
        assertEquals(ExpiryStatus.NONE, ExpiryCalculator.status(null, today))
        assertEquals("未设置保质期", ExpiryCalculator.label(ExpiryStatus.NONE, null))
    }

    @Test
    fun labelsDescribeAction() {
        assertEquals("已过期", ExpiryCalculator.label(ExpiryStatus.EXPIRED, -1))
        assertEquals("今天过期", ExpiryCalculator.label(ExpiryStatus.URGENT, 0))
        assertEquals("1 天后过期", ExpiryCalculator.label(ExpiryStatus.URGENT, 1))
        assertEquals("3 天后过期", ExpiryCalculator.label(ExpiryStatus.NEAR, 3))
        assertEquals("10 天后过期", ExpiryCalculator.label(ExpiryStatus.NORMAL, 10))
    }

    @Test
    fun customThresholdsRespected() {
        val expire = today.plusDays(2)
        assertEquals(
            ExpiryStatus.NORMAL,
            ExpiryCalculator.status(expire, today, urgentDays = 1, nearDays = 1),
        )
        assertEquals(
            ExpiryStatus.URGENT,
            ExpiryCalculator.status(expire, today, urgentDays = 2, nearDays = 3),
        )
    }
}
