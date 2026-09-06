package com.skyanchor.mealtime.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.skyanchor.mealtime.notification.ExpiryNotificationWorker

class MealTimeApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ExpiryNotificationWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(FoodWorkerFactory(container))
            .build()
}

/** Worker 里的依赖从 AppContainer 取（V1 手动 DI 的延伸） */
class FoodWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        ExpiryNotificationWorker::class.java.name ->
            ExpiryNotificationWorker(appContext, workerParameters, container)

        else -> null
    }
}
