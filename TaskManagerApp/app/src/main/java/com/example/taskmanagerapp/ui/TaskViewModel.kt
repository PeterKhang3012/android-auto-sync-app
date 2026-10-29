package com.example.taskmanagerapp.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.example.taskmanagerapp.data.local.TaskDatabase
import com.example.taskmanagerapp.data.local.TaskEntity
import com.example.taskmanagerapp.data.remote.RetrofitClient
import com.example.taskmanagerapp.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TaskRepository

    // Luồng dữ liệu các công việc
    val tasks: StateFlow<List<TaskEntity>>

    // Quan sát trạng thái đồng bộ từ WorkManager
    val syncWorkInfo: LiveData<List<WorkInfo>>

    init {
        val database = TaskDatabase.getDatabase(application)
        val taskDao = database.taskDao()
        val apiService = RetrofitClient.apiService
        repository = TaskRepository(taskDao, apiService, application)

        tasks = repository.allTasks
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        syncWorkInfo = repository.getSyncWorkInfoLiveData()
    }

    fun addTask(title: String, description: String?) {
        viewModelScope.launch {
            val newTask = TaskEntity(
                title = title,
                description = description
            )
            repository.insertTask(newTask)
        }
    }

    fun updateTask(task: TaskEntity, isCompleted: Boolean) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = isCompleted)
            repository.updateTask(updated)
        }
    }

    fun editTask(taskId: String, newTitle: String, newDescription: String?) {
        viewModelScope.launch {
            repository.editTask(taskId, newTitle, newDescription)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun resolveConflict(localTask: TaskEntity, keepLocal: Boolean, serverTitle: String, serverDescription: String?) {
        viewModelScope.launch {
            repository.resolveConflict(localTask, keepLocal, serverTitle, serverDescription)
        }
    }

    fun triggerManualSync() {
        repository.triggerSyncWork()
    }
}
