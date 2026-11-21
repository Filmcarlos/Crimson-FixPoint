package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Battery-friendly offline periodic maintenance worker:
 * - Cleans up temporary local screenshot capture files older than 1 hour.
 * - Keeps disk footprint small without waking GPS or using network.
 */
class DailyMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val ocrDir = File(applicationContext.cacheDir, "ocr_temp_captures")
            if (ocrDir.exists() && ocrDir.isDirectory) {
                val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
                ocrDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoff) {
                        runCatching { file.delete() }
                    }
                }
            }
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "crimson_fixpoint_local_maintenance"

        fun schedule(context: Context) {
            runCatching {
                val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(
                    12, TimeUnit.HOURS
                ).build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
