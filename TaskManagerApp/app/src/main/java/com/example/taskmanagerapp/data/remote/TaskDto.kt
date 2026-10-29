package com.example.taskmanagerapp.data.remote

import com.example.taskmanagerapp.data.local.SyncStatus
import com.example.taskmanagerapp.data.local.TaskEntity
import com.google.gson.annotations.SerializedName

data class TaskDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("isCompleted") val isCompleted: Boolean = false,
    @SerializedName("updatedAt") val updatedAt: Long = System.currentTimeMillis(),
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("version") val version: Long = 1L
)

data class SyncRequestDto(
    @SerializedName("clientId") val clientId: String,
    @SerializedName("changes") val changes: List<TaskDto>
)

data class SyncResponseDto(
    @SerializedName("syncTime") val syncTime: Long,
    @SerializedName("appliedIds") val appliedIds: List<String>
)

data class DeltaResponseDto(
    @SerializedName("serverTime") val serverTime: Long,
    @SerializedName("tasks") val tasks: List<TaskDto>
)

// Extension mappers
fun TaskEntity.toDto(): TaskDto = TaskDto(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    version = version
)

fun TaskDto.toEntity(status: String = SyncStatus.SYNCED.name): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    isCompleted = isCompleted,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
    version = version,
    syncStatus = status
)
