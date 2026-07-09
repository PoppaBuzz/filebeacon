package com.jphat.filebeacon

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class ResumableTransferManager {

    companion object {
        private const val TAG = "ResumableTransfer"
        private const val TEMP_SUFFIX = ".partial"
    }

    private val activeTransfers = ConcurrentHashMap<String, TransferState>()

    data class TransferState(
        val transferId: String,
        val targetFile: File,
        val totalSize: Long,
        var bytesTransferred: Long = 0,
        var isComplete: Boolean = false
    )

    fun createUploadTransfer(targetPath: String, fileName: String, totalSize: Long): TransferState {
        val transferId = generateTransferId(targetPath, fileName)
        val targetFile = File(targetPath, fileName)
        val partialFile = File(targetPath, "$fileName$TEMP_SUFFIX")

        val existingBytes = if (partialFile.exists()) partialFile.length() else 0L

        val state = TransferState(
            transferId = transferId,
            targetFile = targetFile,
            totalSize = totalSize,
            bytesTransferred = existingBytes
        )

        activeTransfers[transferId] = state
        Log.d(TAG, "Created upload transfer: $transferId, existing bytes: $existingBytes")
        return state
    }

    fun writeChunk(transferId: String, data: ByteArray, offset: Long): Boolean {
        val state = activeTransfers[transferId] ?: return false

        return try {
            val partialFile = File(state.targetFile.parent, "${state.targetFile.name}$TEMP_SUFFIX")
            RandomAccessFile(partialFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data)
            }

            state.bytesTransferred = offset + data.size
            Log.d(TAG, "Wrote chunk for $transferId: ${data.size} bytes at offset $offset")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write chunk for $transferId", e)
            false
        }
    }

    fun completeTransfer(transferId: String): Boolean {
        val state = activeTransfers[transferId] ?: return false

        return try {
            val partialFile = File(state.targetFile.parent, "${state.targetFile.name}$TEMP_SUFFIX")

            if (partialFile.exists() && partialFile.length() == state.totalSize) {
                partialFile.renameTo(state.targetFile)
                state.isComplete = true
                activeTransfers.remove(transferId)
                Log.d(TAG, "Transfer completed: $transferId")
                true
            } else {
                Log.w(TAG, "Transfer incomplete: $transferId, expected ${state.totalSize}, got ${partialFile.length()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete transfer $transferId", e)
            false
        }
    }

    fun getTransferState(transferId: String): TransferState? {
        return activeTransfers[transferId]
    }

    fun cancelTransfer(transferId: String): Boolean {
        val state = activeTransfers.remove(transferId) ?: return false

        return try {
            val partialFile = File(state.targetFile.parent, "${state.targetFile.name}$TEMP_SUFFIX")
            if (partialFile.exists()) {
                partialFile.delete()
            }
            Log.d(TAG, "Transfer cancelled: $transferId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel transfer $transferId", e)
            false
        }
    }

    fun getResumeOffset(targetPath: String, fileName: String): Long {
        val partialFile = File(targetPath, "$fileName$TEMP_SUFFIX")
        return if (partialFile.exists()) partialFile.length() else 0L
    }

    private fun generateTransferId(targetPath: String, fileName: String): String {
        return "${targetPath}_${fileName}_${System.currentTimeMillis()}"
    }

    fun getAllActiveTransfers(): List<TransferState> {
        return activeTransfers.values.toList()
    }
}
