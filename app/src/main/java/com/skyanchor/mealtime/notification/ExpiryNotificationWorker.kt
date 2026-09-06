package com.skyanchor.mealtime.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skyanchor.mealtime.MainActivity
import com.skyanchor.mealtime.R
import com.skyanchor.mealtime.app.AppContainer
import com.skyanchor.mealtime.core.common.ExpiryCalculator
import com.skyanchor.mealtime.core.common.ExpiryStatus
import com.skyanchor.mealtime.domain.repository.SettingsKeys
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * 每日临期检测（PRD §13.3）：每天最多一次汇总通知，
 * 文案给出行动建议（"番茄明天到期，可考虑番茄炒蛋"式的下一步）。
 */
class ExpiryNotificationWorker(
    context: Context,
    params: WorkerParameters,
    private val container: AppContainer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = container.settingsRepository
        val enabled = settings.getString(SettingsKeys.NOTIFICATION_ENABLED)?.toBoolean() ?: false
        if (!enabled) return Result.success()

        val forceTest = inputData.getBoolean(KEY_FORCE_TEST, false)

        // 每天最多一次：当天已提醒过则跳过（手动测试除外）
        val today = LocalDate.now().toString()
        if (!forceTest && settings.getString(SettingsKeys.LAST_NOTIFIED_DATE) == today) {
            return Result.success()
        }

        val advanceDays = settings.getString(SettingsKeys.NOTIFICATION_ADVANCE_DAYS)
            ?.toIntOrNull() ?: SettingsKeys.DEFAULT_NOTIFICATION_ADVANCE_DAYS
        val expiring = container.inventoryRepository.observeExpiring(advanceDays).first()
        if (expiring.isEmpty()) return Result.success()

        val todayDate = LocalDate.now()
        val names = expiring.take(3).joinToString("、") { item ->
            val days = item.expireDate?.let { ExpiryCalculator.daysUntil(it, todayDate) }
            "${item.ingredient.name}${if (days != null) " ${days}天" else ""}"
        }
        val text = "$names 即将到期，打开饭点优先安排掉它们"

        showNotification(
            title = applicationContext.getString(R.string.notification_expiry_title),
            text = text,
        )
        settings.putString(SettingsKeys.LAST_NOTIFIED_DATE, today)
        return Result.success()
    }

    private fun showNotification(title: String, text: String) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // 无权限时静默跳过，首页临期条仍是主入口
        }
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "expiry_reminder"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "expiry_daily_check"
        const val KEY_FORCE_TEST = "force_test"

        /** App 启动时注册每日任务（KEEP：已存在则不重建） */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        /** 设置页"立即测试"：忽略当日已提醒限制，立即跑一次 */
        fun runOnceForTest(context: Context) {
            WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<ExpiryNotificationWorker>()
                    .setInputData(androidx.work.Data.Builder().putBoolean(KEY_FORCE_TEST, true).build())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
