package com.amresalehin.emreshots.service.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amresalehin.emreshots.data.local.AppPreferences
import kotlinx.coroutines.flow.first

class BackgroundMediaSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val enabled = AppPreferences(applicationContext).autoSyncDeviceMedia.first()
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

}
