package com.example.service.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppPreferences

class BackgroundMediaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val enabled = AppPreferences(applicationContext).autoSyncDeviceMedia.firstValue()
            if (!enabled || !DeviceMediaScanner.hasPermissions(applicationContext)) {
                Result.success()
            } else {
                MediaSyncManager(applicationContext).synchronize()
                Result.success()
            }
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun kotlinx.coroutines.flow.Flow<Boolean>.firstValue(): Boolean =
        kotlinx.coroutines.flow.first(this)
}
