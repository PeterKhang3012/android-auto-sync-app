package com.example.taskmanagerapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING.name,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val version: Long = 0L
)
