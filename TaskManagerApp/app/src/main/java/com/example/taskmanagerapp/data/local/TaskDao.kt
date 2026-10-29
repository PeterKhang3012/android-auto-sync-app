package com.example.taskmanagerapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :timestamp, syncStatus = :syncStatus WHERE id = :taskId")
    suspend fun softDeleteTask(taskId: String, timestamp: Long, syncStatus: String = SyncStatus.PENDING.name)

    @Query("SELECT * FROM tasks WHERE syncStatus IN ('PENDING', 'FAILED', 'SYNCING')")
    suspend fun getPendingTasks(): List<TaskEntity>

    @Query("UPDATE tasks SET syncStatus = :status WHERE id = :taskId")
    suspend fun updateSyncStatus(taskId: String, status: String)

    @Query("SELECT * FROM tasks")
    suspend fun getAllIncludingDeleted(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE updatedAt > :sinceTimestamp")
    suspend fun getTasksModifiedSince(sinceTimestamp: Long): List<TaskEntity>
}
