package com.example.taskmanagerapp.sync

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        // Demo mode is isolated: never mark real pending records as acknowledged by a server.
        val prefs = applicationContext.getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("mock_sync_mode", false)) {
            Result.failure(workDataOf("error" to "Tắt Demo Mock để đồng bộ với server"))
        } else {
            SyncEngine.sync(applicationContext)
            Result.success()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.retry()
    }

    companion object {
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "PeriodicTaskSync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
