package com.example.taskmanagerapp.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.taskmanagerapp.R
import com.example.taskmanagerapp.data.local.SyncStatus
import com.example.taskmanagerapp.data.local.TaskEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onTaskCheckedChange: (TaskEntity, Boolean) -> Unit,
    private val onEditClick: (TaskEntity) -> Unit,
    private val onDeleteClick: (TaskEntity) -> Unit,
    private val onItemLongClick: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    private val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbCompleted: CheckBox = itemView.findViewById(R.id.cbCompleted)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val tvSyncBadge: TextView = itemView.findViewById(R.id.tvSyncBadge)
        private val tvUpdatedAt: TextView = itemView.findViewById(R.id.tvUpdatedAt)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(task: TaskEntity) {
            tvTitle.text = task.title

            // Gạch ngang tiêu đề nếu đã hoàn thành
            if (task.isCompleted) {
                tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvTitle.alpha = 0.6f
            } else {
                tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvTitle.alpha = 1.0f
            }

            // Hiển thị mô tả nếu có
            if (!task.description.isNullOrBlank()) {
                tvDescription.visibility = View.VISIBLE
                tvDescription.text = task.description
            } else {
                tvDescription.visibility = View.GONE
            }

            // Format thời gian cập nhật
            tvUpdatedAt.text = dateFormat.format(Date(task.updatedAt))

            // Badge trạng thái đồng bộ
            val context = itemView.context
            when (task.syncStatus) {
                SyncStatus.SYNCED.name -> {
                    tvSyncBadge.text = "✓ Đã đồng bộ"
                    tvSyncBadge.setTextColor(ContextCompat.getColor(context, R.color.status_synced))
                    tvSyncBadge.background.setTint(ContextCompat.getColor(context, R.color.status_synced_bg))
                }
                SyncStatus.SYNCING.name -> {
                    tvSyncBadge.text = "⟳ Đang đồng bộ"
                    tvSyncBadge.setTextColor(ContextCompat.getColor(context, R.color.status_syncing))
                    tvSyncBadge.background.setTint(ContextCompat.getColor(context, R.color.status_syncing_bg))
                }
                SyncStatus.FAILED.name -> {
                    tvSyncBadge.text = "⚠ Lỗi đồng bộ"
                    tvSyncBadge.setTextColor(ContextCompat.getColor(context, R.color.status_failed))
                    tvSyncBadge.background.setTint(ContextCompat.getColor(context, R.color.status_failed_bg))
                }
                else -> { // PENDING
                    tvSyncBadge.text = "⏳ Chờ đồng bộ"
                    tvSyncBadge.setTextColor(ContextCompat.getColor(context, R.color.status_pending))
                    tvSyncBadge.background.setTint(ContextCompat.getColor(context, R.color.status_pending_bg))
                }
            }

            // Xử lý sự kiện Checkbox
            cbCompleted.setOnCheckedChangeListener(null)
            cbCompleted.isChecked = task.isCompleted
            cbCompleted.setOnCheckedChangeListener { _, isChecked ->
                onTaskCheckedChange(task, isChecked)
            }

            // Nút sửa
            btnEdit.setOnClickListener {
                onEditClick(task)
            }

            // Nút xóa
            btnDelete.setOnClickListener {
                onDeleteClick(task)
            }

            // Nhấn giữ để mở menu mô phỏng xung đột / thao tác nhanh
            itemView.setOnLongClickListener {
                onItemLongClick(task)
                true
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity): Boolean = oldItem == newItem
    }
}
