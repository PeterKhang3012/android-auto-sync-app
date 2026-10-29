package com.example.taskmanagerapp

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.taskmanagerapp.data.local.SyncStatus
import com.example.taskmanagerapp.data.local.TaskDao
import com.example.taskmanagerapp.data.local.TaskDatabase
import com.example.taskmanagerapp.data.local.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var taskDao: TaskDao
    private lateinit var db: TaskDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetTaskById() = runBlocking {
        val task = TaskEntity(
            id = "test-uuid-1",
            title = "Hoàn thành Deliverable D3",
            description = "Viết mã nguồn Android",
            isCompleted = false,
            syncStatus = SyncStatus.PENDING.name
        )
        taskDao.insertTask(task)

        val loaded = taskDao.getTaskById("test-uuid-1")
        assertNotNull(loaded)
        assertEquals("Hoàn thành Deliverable D3", loaded?.title)
        assertEquals(SyncStatus.PENDING.name, loaded?.syncStatus)
    }

    @Test
    fun softDeleteTask_shouldHideFromGetAllTasks() = runBlocking {
        val task = TaskEntity(
            id = "test-uuid-2",
            title = "Task cần xóa",
            isDeleted = false
        )
        taskDao.insertTask(task)

        // Thực hiện xóa logic (Soft Delete)
        val deleteTimestamp = System.currentTimeMillis()
        taskDao.softDeleteTask("test-uuid-2", deleteTimestamp, SyncStatus.PENDING.name)

        // Kiểm tra getAllTasks chỉ lấy isDeleted = 0
        val activeTasks = taskDao.getAllTasks().first()
        assertTrue(activeTasks.none { it.id == "test-uuid-2" })

        // Nhưng trong DB vẫn còn bản ghi với isDeleted = true để đẩy đồng bộ lên server
        val rawTask = taskDao.getTaskById("test-uuid-2")
        assertNotNull(rawTask)
        assertTrue(rawTask!!.isDeleted)
    }

    @Test
    fun getPendingTasks_shouldReturnPendingAndFailedOnly() = runBlocking {
        val task1 = TaskEntity(id = "1", title = "Task Pending", syncStatus = SyncStatus.PENDING.name)
        val task2 = TaskEntity(id = "2", title = "Task Synced", syncStatus = SyncStatus.SYNCED.name)
        val task3 = TaskEntity(id = "3", title = "Task Failed", syncStatus = SyncStatus.FAILED.name)

        taskDao.insertTask(task1)
        taskDao.insertTask(task2)
        taskDao.insertTask(task3)

        val pendingList = taskDao.getPendingTasks()
        assertEquals(2, pendingList.size)
        assertTrue(pendingList.any { it.id == "1" })
        assertTrue(pendingList.any { it.id == "3" })
    }

    @Test
    fun updateSyncStatus_shouldUpdateCorrectly() = runBlocking {
        val task = TaskEntity(id = "task-sync-1", title = "Task sync test", syncStatus = SyncStatus.PENDING.name)
        taskDao.insertTask(task)

        taskDao.updateSyncStatus("task-sync-1", SyncStatus.SYNCED.name)

        val updated = taskDao.getTaskById("task-sync-1")
        assertEquals(SyncStatus.SYNCED.name, updated?.syncStatus)
    }
}
