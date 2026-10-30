package com.example.taskmanagerapp.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.taskmanagerapp.data.local.SyncStatus
import com.example.taskmanagerapp.data.local.TaskDao
import com.example.taskmanagerapp.data.local.TaskEntity
import com.example.taskmanagerapp.data.remote.TaskApiService
import com.example.taskmanagerapp.sync.SyncWorker
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

class TaskRepository(
    private val taskDao: TaskDao,
    private val apiService: TaskApiService,
    private val context: Context
) {
    // 1. Luồng dữ liệu phản ứng cho UI (Observer Flow từ Room - Offline First)
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()

    // 2. Thêm mới công việc
    suspend fun insertTask(task: TaskEntity) {
        val newTask = task.copy(
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.insertTask(newTask)
        triggerSyncWork()
    }

    // 3. Cập nhật trạng thái hoàn thành (Checkbox)
    suspend fun updateTask(task: TaskEntity) {
        val updatedTask = task.copy(
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.updateTask(updatedTask)
        triggerSyncWork()
    }

    // 4. Chỉnh sửa chi tiết công việc (Tiêu đề, mô tả)
    suspend fun editTask(taskId: String, newTitle: String, newDescription: String?) {
        val currentTask = taskDao.getTaskById(taskId) ?: return
        val updatedTask = currentTask.copy(
            title = newTitle,
            description = newDescription,
            syncStatus = SyncStatus.PENDING.name,
            updatedAt = System.currentTimeMillis()
        )
        taskDao.updateTask(updatedTask)
        triggerSyncWork()
    }

    // 5. Xóa logic (Soft Delete - đánh dấu isDeleted = true)
    suspend fun deleteTask(taskId: String) {
        taskDao.softDeleteTask(
            taskId = taskId,
            timestamp = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING.name
        )
        triggerSyncWork()
    }

    // 6. Xử lý xung đột (Conflict Resolution: Chọn bản Local hay bản Server)
    suspend fun resolveConflict(localTask: TaskEntity, keepLocal: Boolean, serverTitle: String, serverDescription: String?) {
        if (keepLocal) {
            // Giữ bản local và đánh dấu đồng bộ lại lên server với version mới
            val updated = localTask.copy(
                syncStatus = SyncStatus.PENDING.name,
                updatedAt = System.currentTimeMillis(),
                version = localTask.version + 1
            )
            taskDao.updateTask(updated)
            triggerSyncWork()
        } else {
            // Ghi đè bằng bản Server
            val updated = localTask.copy(
                title = serverTitle,
                description = serverDescription,
                syncStatus = SyncStatus.SYNCED.name,
                updatedAt = System.currentTimeMillis()
            )
            taskDao.updateTask(updated)
        }
    }

    // 7. Kích hoạt WorkManager đồng bộ (Constraints, Backoff Policy, enqueueUniqueWork)
    fun triggerSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Chỉ chạy khi có kết nối mạng
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "TaskSyncUniqueWork",
            ExistingWorkPolicy.REPLACE, // Nếu yêu cầu mới đến, thay thế hoặc chạy ngay
            syncWorkRequest
        )
    }

    // 8. Quan sát trạng thái WorkManager để hiển thị Progress trên UI
    fun getSyncWorkInfoLiveData(): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("TaskSyncUniqueWork")
    }
}
