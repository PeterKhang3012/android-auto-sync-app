package com.example.taskmanagerapp

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.taskmanagerapp.data.local.SyncStatus
import com.example.taskmanagerapp.data.local.TaskDatabase
import com.example.taskmanagerapp.data.local.TaskEntity
import com.example.taskmanagerapp.data.remote.RetrofitClient
import com.example.taskmanagerapp.data.remote.TaskDto
import com.example.taskmanagerapp.repository.TaskRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real Room -> repository -> WorkManager -> HTTP benchmark. Run on a test emulator. */
@RunWith(AndroidJUnit4::class)
class TC09SyncBenchmarkTest {
    @Test
    fun syncNTasksThroughRepositoryAndVerifyServer() = runBlocking(Dispatchers.IO) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("TC09 is opt-in; pass tc09Enabled=true", arguments.getString("tc09Enabled") == "true")
        val n = (arguments.getString("tc09N") ?: "10").toInt()
        val timeoutSeconds = (arguments.getString("tc09TimeoutSeconds") ?: "180").toLong()
        require(n in 1..100) { "tc09N must be in 1..100" }
        require(timeoutSeconds in 30..600) { "tc09TimeoutSeconds must be in 30..600" }

        val runId = "TC09-${System.currentTimeMillis()}-${UUID.randomUUID()}"
        val logFile = File(context.filesDir, "tc09_logs/$runId.jsonl")
        check(logFile.parentFile!!.mkdirs() || logFile.parentFile!!.isDirectory)
        fun record(event: String, vararg fields: Pair<String, Any?>) {
            val json = JSONObject().put("event", event).put("runId", runId)
                .put("timestampMs", System.currentTimeMillis())
            for ((key, value) in fields) json.put(key, value ?: JSONObject.NULL)
            val line = json.toString()
            logFile.appendText(line + "\n")
            Log.i("TC09", line)
        }

        record("START", "n" to n, "timeoutSeconds" to timeoutSeconds,
            "model" to Build.MODEL, "apiLevel" to Build.VERSION.SDK_INT,
            "package" to context.packageName, "logFile" to logFile.absolutePath,
            "measurement" to "repository_creation_to_verified_server_and_completed_manual_work")
        var status = "BLOCKED"
        var error: String? = null
        var started: Long? = null
        var insertionMs: Long? = null
        var completionMs: Long? = null
        var verified = 0
        var duplicates: Int? = null
        var mismatches: Int? = null
        var lastObservation: String? = null
        var lastDetails: List<JSONObject> = emptyList()
        var created = 0
        var workStates = ""
        try {
            val prefs = context.getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
            check(!prefs.getBoolean("mock_sync_mode", false)) { "Turn off Demo Mock first" }
            val db = TaskDatabase.getDatabase(context)
            val dao = db.taskDao()
            val api = RetrofitClient.apiService
            val manager = WorkManager.getInstance(context)
            fun works() = manager.getWorkInfosForUniqueWork("TaskSyncUniqueWork")
                .get(10, TimeUnit.SECONDS)
            val baseline = works()
            check(baseline.none { !it.state.isFinished }) {
                "Existing manual sync is unfinished; wait until idle before TC09"
            }
            check(dao.getPendingTasks().isEmpty()) {
                "Existing pending/failed/syncing tasks found; finish previous test first"
            }
            val preflight = api.getDeltaTasks(0)
            check(preflight.isSuccessful && preflight.body() != null) {
                "Server preflight failed: HTTP ${preflight.code()}"
            }
            val baselineIds = baseline.map { it.id }.toSet()
            val repository = TaskRepository(dao, api, context)
            val expected = (1..n).map { index ->
                TaskEntity(id = "$runId-$index", title = "$runId task $index",
                    description = "TC09 n=$n index=$index", isCompleted = index % 2 == 0)
            }.associateBy { it.id }
            record("PREFLIGHT_OK", "existingServerTasks" to preflight.body()!!.tasks.size,
                "note" to "Existing data is retained. No reset, cleanup, or preference changes.")

            status = "FAIL"
            val startTime = SystemClock.elapsedRealtime()
            started = startTime
            for (task in expected.values) {
                // Production repository writes Room and enqueues real constrained WorkManager work.
                repository.insertTask(task)
                created++
            }
            insertionMs = SystemClock.elapsedRealtime() - startTime
            repository.triggerSyncWork()
            record("CREATED", "created" to created, "insertionMs" to insertionMs)
            val deadline = startTime + TimeUnit.SECONDS.toMillis(timeoutSeconds)
            var nextProgress = startTime
            while (SystemClock.elapsedRealtime() < deadline) {
                val localById = dao.getAllIncludingDeleted().filter { it.id.startsWith("$runId-") }
                    .associateBy { it.id }
                val newWork = works().filter { it.id !in baselineIds }
                workStates = newWork.groupingBy { it.state.name }.eachCount().toString()
                try {
                    val response = api.getDeltaTasks(0)
                    val delta = response.body()
                    if (!response.isSuccessful || delta == null) {
                        lastObservation = "Verification GET HTTP ${response.code()}"
                    } else {
                        val remote = delta.tasks.filter { it.id.startsWith("$runId-") }
                        val remoteById = remote.associateBy { it.id }
                        duplicates = remote.size - remoteById.size
                        val allIds = expected.keys + localById.keys + remoteById.keys
                        lastDetails = allIds.map { id ->
                            val input = expected[id]
                            val local = localById[id]
                            val server = remoteById[id]
                            val matches = input != null && local != null && server != null &&
                                    local.syncStatus == SyncStatus.SYNCED.name && server.version > 0 &&
                                    matchesInput(input, server) && matchesInput(input, local) &&
                                    local.version == server.version && local.updatedAt == server.updatedAt
                            JSONObject().put("id", id).put("verified", matches)
                                .put("localStatus", local?.syncStatus ?: JSONObject.NULL)
                                .put("localVersion", local?.version ?: JSONObject.NULL)
                                .put("serverVersion", server?.version ?: JSONObject.NULL)
                                .put("localUpdatedAt", local?.updatedAt ?: JSONObject.NULL)
                                .put("serverUpdatedAt", server?.updatedAt ?: JSONObject.NULL)
                                .put("localTitle", local?.title ?: JSONObject.NULL)
                                .put("serverTitle", server?.title ?: JSONObject.NULL)
                                .put("expected", input?.let { JSONObject(Gson().toJson(it)) } ?: JSONObject.NULL)
                                .put("local", local?.let { JSONObject(Gson().toJson(it)) } ?: JSONObject.NULL)
                                .put("server", server?.let { JSONObject(Gson().toJson(it)) } ?: JSONObject.NULL)
                        }
                        verified = expected.keys.count { id ->
                            lastDetails.any { it.getString("id") == id && it.getBoolean("verified") }
                        }
                        mismatches = lastDetails.count { !it.getBoolean("verified") }
                        lastObservation = "GET OK; expected=$n local=${localById.size} server=${remote.size}"
                        if (verified == n && mismatches == 0 && duplicates == 0 &&
                            newWork.isNotEmpty() && newWork.all { it.state == WorkInfo.State.SUCCEEDED }) {
                            completionMs = SystemClock.elapsedRealtime() - startTime
                            status = "PASS"
                            break
                        }
                    }
                } catch (verificationError: Exception) {
                    lastObservation = "Verification GET failed: ${verificationError.javaClass.simpleName}: ${verificationError.message}"
                }
                check(newWork.none { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED }) {
                    "New sync work failed or was cancelled: $workStates"
                }
                if (SystemClock.elapsedRealtime() >= nextProgress) {
                    record("PROGRESS", "elapsedMs" to (SystemClock.elapsedRealtime() - startTime),
                        "verified" to verified, "workStates" to workStates,
                        "observation" to lastObservation)
                    nextProgress = SystemClock.elapsedRealtime() + 5000
                }
                delay(1000)
            }
            if (status != "PASS") error = "Deadline reached without confirmed convergence and completed work"
        } catch (failure: Exception) {
            error = "${failure.javaClass.simpleName}: ${failure.message}"
        } finally {
            for (detail in lastDetails) {
                record("TASK", "detail" to detail)
            }
            record("SUMMARY", "status" to status, "requested" to n, "created" to created,
                "verified" to verified, "successRatePercent" to (verified * 100.0 / n),
                "completedMs" to completionMs,
                "observedElapsedMs" to started?.let { SystemClock.elapsedRealtime() - it },
                "insertionMs" to insertionMs, "duplicateIds" to duplicates,
                "mismatchedIds" to mismatches, "workStates" to workStates,
                "lastObservation" to lastObservation, "error" to error,
                "scope" to "Only this runId group; no two-device or UI measurement")
            println("TC09_LOG_FILE=${logFile.absolutePath}")
            println("TC09_STATUS=$status; verified=$verified/$n; completedMs=$completionMs; error=$error")
        }
        assertTrue("TC09 $status: $error. See tag TC09 and ${logFile.absolutePath}", status == "PASS")
    }

    private fun matchesInput(input: TaskEntity, actual: TaskDto): Boolean =
        input.id == actual.id && input.title == actual.title &&
                input.description == actual.description && input.isCompleted == actual.isCompleted &&
                input.isDeleted == actual.isDeleted

    private fun matchesInput(input: TaskEntity, actual: TaskEntity): Boolean =
        input.id == actual.id && input.title == actual.title &&
                input.description == actual.description && input.isCompleted == actual.isCompleted &&
                input.isDeleted == actual.isDeleted
}
