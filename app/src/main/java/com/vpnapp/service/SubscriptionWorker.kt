package com.vpnapp.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.vpnapp.repository.VpnRepository
import com.vpnapp.utils.NotificationHelper
import com.vpnapp.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SubscriptionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SubscriptionWorker"
        const val WORK_NAME = "subscription_update"

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<SubscriptionWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.d(TAG, "Subscription worker scheduled every $intervalHours hours")
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = PreferencesManager(applicationContext).loadSettings()
        val url = settings.subscriptionUrl

        if (url.isBlank()) {
            Log.d(TAG, "No subscription URL configured, skipping update")
            return@withContext Result.success()
        }

        Log.d(TAG, "Fetching subscription from: $url")
        val repo = VpnRepository(applicationContext)
        val result = repo.fetchServers(url)

        return@withContext if (result.isSuccess) {
            val count = result.getOrNull()?.size ?: 0
            Log.d(TAG, "Subscription updated: $count servers")
            NotificationHelper.showNotification(
                applicationContext,
                "Подписка обновлена",
                "Загружено $count серверов"
            )
            Result.success()
        } else {
            Log.e(TAG, "Subscription update failed: ${result.exceptionOrNull()?.message}")
            Result.retry()
        }
    }
}
