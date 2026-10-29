package com.example.taskmanagerapp.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.taskmanagerapp.data.remote.toEntity
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
        val database = com.example.taskmanagerapp.data.local.TaskDatabase.getDatabase(context)
        database.withTransaction {
            val current = taskDao.getTaskById(task.id) ?: return@withTransaction
            taskDao.updateTask(current.copy(
                isCompleted = task.isCompleted,
                syncStatus = SyncStatus.PENDING.name,
                updatedAt = System.currentTimeMillis()
            ))
        }
        triggerSyncWork()
    }

    // 4. Chỉnh sửa chi tiết công việc (Tiêu đề, mô tả)
    suspend fun editTask(taskId: String, newTitle: String, newDescription: String?) {
        val database = com.example.taskmanagerapp.data.local.TaskDatabase.getDatabase(context)
        database.withTransaction {
            val currentTask = taskDao.getTaskById(taskId) ?: return@withTransaction
            taskDao.updateTask(currentTask.copy(
                title = newTitle,
                description = newDescription,
                syncStatus = SyncStatus.PENDING.name,
                updatedAt = System.currentTimeMillis()
            ))
        }
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

    // Resolve using the complete server snapshot (version and tombstone included).
    suspend fun resolveConflict(localTask: TaskEntity, keepLocal: Boolean, server: com.example.taskmanagerapp.data.remote.TaskDto) {
        val database = com.example.taskmanagerapp.data.local.TaskDatabase.getDatabase(context)
        database.withTransaction {
            val current = taskDao.getTaskById(localTask.id) ?: return@withTransaction
            // Do not overwrite an edit made after the dialog opened.
            if (current != localTask) return@withTransaction
            if (keepLocal) {
                taskDao.insertTask(current.copy(version = server.version,
                    updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.PENDING.name))
            } else {
                taskDao.insertTask(server.toEntity())
            }
        }
        triggerSyncWork()
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
            ExistingWorkPolicy.APPEND_OR_REPLACE, // Chạy nối tiếp, không hủy request đang gửi
            syncWorkRequest
        )
    }

    // 8. Quan sát trạng thái WorkManager để hiển thị Progress trên UI
    fun getSyncWorkInfoLiveData(): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData("TaskSyncUniqueWork")
    }
}
