package com.amresalehin.emreshots.service.media

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {
    private const val WORK_NAME = "emreshots-background-media-sync"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackgroundMediaSyncWorker>(15, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
