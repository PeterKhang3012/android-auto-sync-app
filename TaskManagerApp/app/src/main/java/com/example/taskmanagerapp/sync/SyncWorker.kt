package com.example.taskmanagerapp.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.taskmanagerapp.data.local.SyncStatus
import com.example.taskmanagerapp.data.local.TaskDatabase
import com.example.taskmanagerapp.data.remote.RetrofitClient
import com.example.taskmanagerapp.data.remote.SyncRequestDto
import com.example.taskmanagerapp.data.remote.toDto
import com.example.taskmanagerapp.data.remote.toEntity
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.UUID

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = TaskDatabase.getDatabase(applicationContext)
        val taskDao = database.taskDao()
        val apiService = RetrofitClient.apiService

        return try {
            // 1. Lấy danh sách các task đang chờ đồng bộ (PENDING hoặc FAILED)
            val pendingTasks = taskDao.getPendingTasks()

            if (pendingTasks.isNotEmpty()) {
                // Đánh dấu các task đang ở trạng thái SYNCING để UI cập nhật badge màu xanh
                pendingTasks.forEach { task ->
                    taskDao.updateSyncStatus(task.id, SyncStatus.SYNCING.name)
                }

                // Kiểm tra cờ Mock Sync Mode (dùng khi Người A demo độc lập chưa có Server của Người B)
                val prefs = applicationContext.getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
                val isMockMode = prefs.getBoolean("mock_sync_mode", false)

                if (isMockMode) {
                    // Giả lập thời gian truyền tải qua mạng 1.5s
                    delay(1500)
                    pendingTasks.forEach { task ->
                        taskDao.updateSyncStatus(task.id, SyncStatus.SYNCED.name)
                    }
                } else {
                    // Gửi request thật lên Server thông qua Retrofit
                    val clientId = prefs.getString("client_id", null) ?: UUID.randomUUID().toString().also {
                        prefs.edit().putString("client_id", it).apply()
                    }

                    val request = SyncRequestDto(
                        clientId = clientId,
                        changes = pendingTasks.map { it.toDto() }
                    )

                    val response = apiService.syncBatch(request)
                    if (response.isSuccessful && response.body() != null) {
                        val appliedIds = response.body()!!.appliedIds
                        appliedIds.forEach { id ->
                            taskDao.updateSyncStatus(id, SyncStatus.SYNCED.name)
                        }
                    } else {
                        // Server từ chối hoặc lỗi 4xx/5xx
                        pendingTasks.forEach { task ->
                            taskDao.updateSyncStatus(task.id, SyncStatus.FAILED.name)
                        }
                        return Result.retry()
                    }
                }
            }

            // 2. Kéo delta changes từ Server về (Pull sync)
            try {
                val prefs = applicationContext.getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
                val lastSyncTime = prefs.getLong("last_sync_time", 0L)
                val isMockMode = prefs.getBoolean("mock_sync_mode", false)

                if (!isMockMode) {
                    val deltaResponse = apiService.getDeltaTasks(since = if (lastSyncTime > 0) lastSyncTime else null)
                    if (deltaResponse.isSuccessful && deltaResponse.body() != null) {
                        val serverTasks = deltaResponse.body()!!.tasks
                        serverTasks.forEach { dto ->
                            taskDao.insertTask(dto.toEntity(SyncStatus.SYNCED.name))
                        }
                        prefs.edit().putLong("last_sync_time", deltaResponse.body()!!.serverTime).apply()
                    }
                }
            } catch (e: Exception) {
                // Không ngắt nếu pull lỗi sau khi đã push thành công
                e.printStackTrace()
            }

            Result.success()
        } catch (e: IOException) {
            e.printStackTrace()
            // Lỗi kết nối mạng hoặc không tìm thấy server: đánh dấu FAILED và yêu cầu WorkManager retry
            val pendingTasks = taskDao.getPendingTasks()
            pendingTasks.forEach { task ->
                taskDao.updateSyncStatus(task.id, SyncStatus.FAILED.name)
            }
            Result.retry()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
