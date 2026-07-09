package com.jphat.filebeacon

import android.os.Handler
import android.os.Looper
import java.util.UUID

data class TransferTask(
    val id: String,
    val type: TransferType,
    val fileName: String,
    val totalSize: Long,
    var bytesTransferred: Long = 0,
    val startTime: Long = System.currentTimeMillis(),
    var lastUpdateTime: Long = System.currentTimeMillis(),
    var status: TaskStatus = TaskStatus.ACTIVE,
    var cancelRequested: Boolean = false
)

enum class TransferType { UPLOAD, DOWNLOAD }
enum class TaskStatus { ACTIVE, COMPLETED, CANCELLED, ERROR }

class TransferTaskManager {
    private val activeTasks = mutableMapOf<String, TransferTask>()

    fun createTask(type: TransferType, fileName: String, totalSize: Long): String {
        val taskId = UUID.randomUUID().toString()
        val task = TransferTask(id = taskId, type = type, fileName = fileName, totalSize = totalSize)
        activeTasks[taskId] = task
        return taskId
    }

    fun updateProgress(taskId: String, bytesTransferred: Long) {
        activeTasks[taskId]?.let {
            it.bytesTransferred = bytesTransferred
            it.lastUpdateTime = System.currentTimeMillis()
        }
    }

    fun getTask(taskId: String): TransferTask? {
        return activeTasks[taskId]
    }

    fun cancelTask(taskId: String) {
        activeTasks[taskId]?.cancelRequested = true
    }

    fun completeTask(taskId: String) {
        activeTasks[taskId]?.status = TaskStatus.COMPLETED
        // Remove completed tasks after 5 seconds to ensure the UI can show completion
        Handler(Looper.getMainLooper()).postDelayed({
            activeTasks.remove(taskId)
        }, 5000)
    }

    fun getActiveTasks(): List<TransferTask> = activeTasks.values.toList()

    fun getTaskProgress(taskId: String): TaskProgress? {
        val task = activeTasks[taskId] ?: return null
        val currentTime = System.currentTimeMillis()
        val elapsedTime = (currentTime - task.startTime) / 1000.0
        val speed = if (elapsedTime > 0) task.bytesTransferred / elapsedTime else 0.0
        val eta = if (speed > 0) (task.totalSize - task.bytesTransferred) / speed else 0.0

        return TaskProgress(
            taskId = task.id,
            type = task.type,
            fileName = task.fileName,
            progress = (task.bytesTransferred.toDouble() / task.totalSize * 100).toInt(),
            speed = speed,
            eta = eta.toLong(),
            status = task.status
        )
    }
}

data class TaskProgress(
    val taskId: String,
    val type: TransferType,
    val fileName: String,
    val progress: Int,
    val speed: Double, // bytes per second
    val eta: Long, // seconds remaining
    val status: TaskStatus
)
