package com.example.taskmanagerapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.example.taskmanagerapp.data.local.TaskEntity
import com.example.taskmanagerapp.ui.TaskAdapter
import com.example.taskmanagerapp.ui.TaskViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: TaskViewModel by viewModels()
    private lateinit var taskAdapter: TaskAdapter

    private lateinit var tvTaskCount: TextView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var layoutSyncStatus: LinearLayout
    private lateinit var tvSyncStatusMessage: TextView
    private lateinit var progressBarSync: ProgressBar
    private lateinit var btnSync: MaterialButton
    private lateinit var fabAddTask: ExtendedFloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun initViews() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        tvTaskCount = findViewById(R.id.tvTaskCount)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        layoutSyncStatus = findViewById(R.id.layoutSyncStatus)
        tvSyncStatusMessage = findViewById(R.id.tvSyncStatusMessage)
        progressBarSync = findViewById(R.id.progressBarSync)
        btnSync = findViewById(R.id.btnSync)
        fabAddTask = findViewById(R.id.fabAddTask)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val prefs = getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
        val isMock = prefs.getBoolean("mock_sync_mode", false)

        val item = menu?.add(0, 1001, 0, if (isMock) "Chế độ: Demo Mock (BẬT)" else "Chế độ: Server Thật")
        item?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1001) {
            val prefs = getSharedPreferences("task_sync_prefs", Context.MODE_PRIVATE)
            val current = prefs.getBoolean("mock_sync_mode", false)
            prefs.edit().putBoolean("mock_sync_mode", !current).apply()
            val newStatus = if (!current) "BẬT Chế độ Demo Mock" else "TẮT Chế độ Demo Mock (dùng Server thật)"
            Toast.makeText(this, newStatus, Toast.LENGTH_SHORT).show()
            invalidateOptionsMenu()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTasks)
        taskAdapter = TaskAdapter(
            onTaskCheckedChange = { task, isChecked ->
                viewModel.updateTask(task, isChecked)
            },
            onEditClick = { task ->
                showEditTaskDialog(task)
            },
            onDeleteClick = { task ->
                showDeleteConfirmDialog(task)
            },
            onItemLongClick = { task ->
                showTaskOptionsDialog(task)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = taskAdapter
    }

    private fun setupListeners() {
        fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }

        btnSync.setOnClickListener {
            viewModel.triggerManualSync()
            Toast.makeText(this, "Đã gửi yêu cầu đồng bộ...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        // 1. Quan sát danh sách công việc (StateFlow từ Room)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.tasks.collectLatest { tasks ->
                    taskAdapter.submitList(tasks)

                    val total = tasks.size
                    val completed = tasks.count { it.isCompleted }
                    tvTaskCount.text = "Tổng số: $total công việc ($completed đã xong)"

                    if (tasks.isEmpty()) {
                        layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        layoutEmptyState.visibility = View.GONE
                    }
                }
            }
        }

        // 2. Quan sát tiến trình WorkManager
        viewModel.syncWorkInfo.observe(this) { workInfos ->
            if (workInfos.isNullOrEmpty()) {
                layoutSyncStatus.visibility = View.GONE
                return@observe
            }

            val currentWork = workInfos.firstOrNull() ?: return@observe
            when (currentWork.state) {
                WorkInfo.State.RUNNING -> {
                    layoutSyncStatus.visibility = View.VISIBLE
                    progressBarSync.visibility = View.VISIBLE
                    tvSyncStatusMessage.text = "Đang đồng bộ dữ liệu với máy chủ..."
                }
                WorkInfo.State.SUCCEEDED -> {
                    layoutSyncStatus.visibility = View.VISIBLE
                    progressBarSync.visibility = View.GONE
                    tvSyncStatusMessage.text = "✓ Đồng bộ thành công!"
                    layoutSyncStatus.postDelayed({
                        layoutSyncStatus.visibility = View.GONE
                    }, 2500)
                }
                WorkInfo.State.FAILED -> {
                    layoutSyncStatus.visibility = View.VISIBLE
                    progressBarSync.visibility = View.GONE
                    tvSyncStatusMessage.text = "⚠ Đồng bộ thất bại (sẽ tự động thử lại khi có mạng)"
                    layoutSyncStatus.postDelayed({
                        layoutSyncStatus.visibility = View.GONE
                    }, 4000)
                }
                WorkInfo.State.ENQUEUED -> {
                    layoutSyncStatus.visibility = View.VISIBLE
                    progressBarSync.visibility = View.GONE
                    tvSyncStatusMessage.text = "⏳ Đã xếp hàng đồng bộ (chờ kết nối mạng...)"
                }
                else -> {
                    layoutSyncStatus.visibility = View.GONE
                }
            }
        }
    }

    // Menu khi nhấn giữ công việc
    private fun showTaskOptionsDialog(task: TaskEntity) {
        val options = arrayOf("Mô phỏng xung đột dữ liệu (Conflict Demo)", "Chỉnh sửa công việc", "Xóa công việc")
        MaterialAlertDialogBuilder(this)
            .setTitle(task.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Kích hoạt mô phỏng xung đột: Server có phiên bản khác với local
                        showConflictResolutionDialog(
                            localTask = task,
                            serverTitle = "${task.title} (Bản đã sửa trên Web Server)",
                            serverDescription = "Nội dung này được cập nhật từ thiết bị khác trên máy chủ lúc ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}",
                            serverUpdatedAt = System.currentTimeMillis() + 60000
                        )
                    }
                    1 -> showEditTaskDialog(task)
                    2 -> showDeleteConfirmDialog(task)
                }
            }
            .show()
    }

    // Dialog thêm công việc mới
    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etDialogTaskTitle)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDialogTaskDescription)

        MaterialAlertDialogBuilder(this)
            .setTitle("Thêm công việc mới")
            .setView(dialogView)
            .setPositiveButton("Lưu") { _, _ ->
                val title = etTitle.text.toString().trim()
                val description = etDescription.text.toString().trim()
                if (title.isNotEmpty()) {
                    viewModel.addTask(title, if (description.isEmpty()) null else description)
                } else {
                    Toast.makeText(this, "Tiêu đề không được để trống!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // Dialog chỉnh sửa công việc
    private fun showEditTaskDialog(task: TaskEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_task, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.etDialogTaskTitle)
        val etDescription = dialogView.findViewById<EditText>(R.id.etDialogTaskDescription)

        etTitle.setText(task.title)
        etDescription.setText(task.description ?: "")

        MaterialAlertDialogBuilder(this)
            .setTitle("Chỉnh sửa công việc")
            .setView(dialogView)
            .setPositiveButton("Cập nhật") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newDescription = etDescription.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    viewModel.editTask(task.id, newTitle, if (newDescription.isEmpty()) null else newDescription)
                } else {
                    Toast.makeText(this, "Tiêu đề không được để trống!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // Dialog xác nhận xóa
    private fun showDeleteConfirmDialog(task: TaskEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa công việc \"${task.title}\"?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deleteTask(task.id)
                Toast.makeText(this, "Đã xóa công việc", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // Dialog giải quyết xung đột (Conflict Resolution Dialog)
    fun showConflictResolutionDialog(
        localTask: TaskEntity,
        serverTitle: String,
        serverDescription: String?,
        serverUpdatedAt: Long
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_conflict_resolution, null)
        val dateFormat = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())

        val tvLocalTitle = dialogView.findViewById<TextView>(R.id.tvConflictLocalTitle)
        val tvLocalDesc = dialogView.findViewById<TextView>(R.id.tvConflictLocalDesc)
        val tvLocalTime = dialogView.findViewById<TextView>(R.id.tvConflictLocalTime)
        val btnKeepLocal = dialogView.findViewById<Button>(R.id.btnKeepLocal)

        val tvServerTitle = dialogView.findViewById<TextView>(R.id.tvConflictServerTitle)
        val tvServerDesc = dialogView.findViewById<TextView>(R.id.tvConflictServerDesc)
        val tvServerTime = dialogView.findViewById<TextView>(R.id.tvConflictServerTime)
        val btnKeepServer = dialogView.findViewById<Button>(R.id.btnKeepServer)

        tvLocalTitle.text = "Tiêu đề: ${localTask.title}"
        tvLocalDesc.text = "Mô tả: ${localTask.description ?: "(Không có)"}"
        tvLocalTime.text = "Cập nhật lúc: ${dateFormat.format(Date(localTask.updatedAt))}"

        tvServerTitle.text = "Tiêu đề: $serverTitle"
        tvServerDesc.text = "Mô tả: ${serverDescription ?: "(Không có)"}"
        tvServerTime.text = "Cập nhật lúc: ${dateFormat.format(Date(serverUpdatedAt))}"

        var dialog: AlertDialog? = null

        btnKeepLocal.setOnClickListener {
            viewModel.resolveConflict(localTask, keepLocal = true, serverTitle = serverTitle, serverDescription = serverDescription)
            dialog?.dismiss()
            Toast.makeText(this, "Đã chọn giữ bản Cục Bộ", Toast.LENGTH_SHORT).show()
        }

        btnKeepServer.setOnClickListener {
            viewModel.resolveConflict(localTask, keepLocal = false, serverTitle = serverTitle, serverDescription = serverDescription)
            dialog?.dismiss()
            Toast.makeText(this, "Đã ghi đè bằng bản Máy Chủ", Toast.LENGTH_SHORT).show()
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .show()
    }
}
