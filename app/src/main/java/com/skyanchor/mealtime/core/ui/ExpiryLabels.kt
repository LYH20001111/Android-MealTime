package com.skyanchor.mealtime.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.skyanchor.mealtime.R
import com.skyanchor.mealtime.core.common.ExpiryStatus

/** ExpiryCalculator.status 的界面显示文案（当前语言；原 label() 保留给非 UI 场景） */
@Composable
fun expiryStatusLabel(status: ExpiryStatus, days: Long?): String = when (status) {
    ExpiryStatus.EXPIRED -> stringResource(R.string.expiry_label_expired)
    ExpiryStatus.URGENT ->
        if (days == 0L) {
            stringResource(R.string.expiry_label_expires_today)
        } else {
            days?.let { expiresInDays(it) } ?: ""
        }

    ExpiryStatus.NEAR -> days?.let { expiresInDays(it) } ?: ""
    ExpiryStatus.NORMAL -> days?.let { expiresInDays(it) } ?: ""
    ExpiryStatus.NONE -> stringResource(R.string.expiry_label_none)
}

@Composable
private fun expiresInDays(days: Long): String =
    pluralStringResource(R.plurals.expiry_label_expires_in_days, days.toInt(), days)
