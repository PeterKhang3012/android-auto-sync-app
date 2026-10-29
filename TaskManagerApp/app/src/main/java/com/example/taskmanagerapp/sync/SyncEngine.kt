package com.example.taskmanagerapp.sync

import android.content.Context
import android.os.SystemClock
import androidx.room.withTransaction
import com.example.taskmanagerapp.data.local.*
import com.example.taskmanagerapp.data.remote.*
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class ConflictPolicy { LAST_WRITE_WINS, SERVER_WINS, CLIENT_WINS }

object ConflictResolver {
    fun clientWins(policy: ConflictPolicy, local: TaskEntity, server: TaskDto): Boolean =
        when (policy) {
            ConflictPolicy.CLIENT_WINS -> true
            ConflictPolicy.SERVER_WINS -> false
            ConflictPolicy.LAST_WRITE_WINS -> local.updatedAt > server.updatedAt
        }
}

/** Both periodic and manual workers share a lock. Network calls run outside Room transactions. */
object SyncEngine {
    private val mutex = Mutex()

    suspend fun sync(context: Context) = mutex.withLock {
        val db = TaskDatabase.getDatabase(context)
        val dao = db.taskDao()
        val api = RetrofitClient.apiService
        val prefs = context.getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
        val started = SystemClock.elapsedRealtime()
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var success = false
        try {
            val policy = runCatching {
                ConflictPolicy.valueOf(prefs.getString("conflict_policy", ConflictPolicy.LAST_WRITE_WINS.name)!!)
            }.getOrDefault(ConflictPolicy.LAST_WRITE_WINS)
            repeat(2) {
                for (snapshot in dao.getPendingTasks()) {
                    dao.updateSyncStatus(snapshot.id, SyncStatus.SYNCING.name)
                    try {
                        val response = api.updateTask(snapshot.id, snapshot.toDto())
                        if (response.code() == 409) {
                            val server = Gson().fromJson(
                                response.errorBody()?.string() ?: throw IOException("Empty conflict response"),
                                ConflictResponseDto::class.java
                            ).server
                            db.withTransaction {
                                val current = dao.getTaskById(snapshot.id) ?: return@withTransaction
                                if (current.copy(syncStatus = snapshot.syncStatus) != snapshot ||
                                    ConflictResolver.clientWins(policy, current, server)) {
                                    dao.insertTask(current.copy(version = server.version, syncStatus = SyncStatus.PENDING.name))
                                } else {
                                    dao.insertTask(server.toEntity())
                                }
                            }
                            conflicts++
                        } else {
                            val server = response.body()
                            if (!response.isSuccessful || server == null) throw IOException("Push HTTP ${response.code()}")
                            db.withTransaction {
                                val current = dao.getTaskById(snapshot.id) ?: return@withTransaction
                                // Preserve edits made while the request was in flight, including same-ms edits.
                                val unchanged = current.copy(syncStatus = snapshot.syncStatus) == snapshot
                                dao.insertTask(current.copy(version = server.version,
                                    syncStatus = if (unchanged) SyncStatus.SYNCED.name else SyncStatus.PENDING.name))
                            }
                            pushed++
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        dao.updateSyncStatus(snapshot.id, SyncStatus.FAILED.name)
                        throw error
                    }
                }
            }
            if (dao.getPendingTasks().isNotEmpty()) throw IOException("Changes remain pending; retry required")
            val response = api.getDeltaTasks(prefs.getLong("last_server_seq", 0L))
            val delta = response.body()
            if (!response.isSuccessful || delta == null) throw IOException("Pull HTTP ${response.code()}")
            db.withTransaction {
                for (dto in delta.tasks) {
                    val local = dao.getTaskById(dto.id)
                    if (local == null || (local.syncStatus == SyncStatus.SYNCED.name && dto.version >= local.version)) {
                        dao.insertTask(dto.toEntity())
                    }
                    pulled++
                }
            }
            prefs.edit().putLong("last_server_seq", delta.serverTime)
                .putLong("last_sync_time", System.currentTimeMillis()).apply()
            success = true
        } finally {
            val count = prefs.getLong("sync_count", 0L) + 1
            prefs.edit().putLong("sync_count", count)
                .putLong("sync_ok", prefs.getLong("sync_ok", 0L) + if (success) 1L else 0L)
                .putLong("sync_duration_ms", prefs.getLong("sync_duration_ms", 0L) + SystemClock.elapsedRealtime() - started)
                .putLong("sync_conflicts", prefs.getLong("sync_conflicts", 0L) + conflicts)
                .putInt("last_pushed", pushed).putInt("last_pulled", pulled).apply()
        }
    }
}
