package com.jphat.filebeacon

import android.os.Environment
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

object FileManager {

    private const val TAG = "FileManager"

    fun rootDir(): File = Environment.getExternalStorageDirectory()

    // Returns a unique file name in the directory by adding (2), (3), etc. on conflict
    fun getUniqueFileName(directory: File, originalName: String): String {
        val baseName = originalName.substringBeforeLast('.', originalName)
        val extension = originalName.substringAfterLast('.', "")
        var candidate = originalName
        var counter = 2
        while (File(directory, candidate).exists()) {
            candidate = if (extension.isNotEmpty())
                "$baseName($counter).$extension"
            else
                "$baseName($counter)"
            counter++
        }
        return candidate
    }

    fun saveUploadedFile(tempFilePath: String, targetDirPath: String, fileName: String, overwrite: Boolean = false): Boolean {
        return try {
            if (!isPathSafe(targetDirPath)) return false
            val targetDir = File(targetDirPath)
            val uniqueName = if (overwrite) fileName else getUniqueFileName(targetDir, fileName)
            val targetFile = File(targetDir, uniqueName)
            val tempFile = File(tempFilePath)
            if (!tempFile.exists()) return false
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save uploaded file $fileName to $targetDirPath", e)
            false
        }
    }

    fun deleteFiles(paths: List<String>): Boolean {
        var allSuccess = true
        for (path in paths) {
            val success = deleteFile(path)
            if (!success) allSuccess = false
        }
        return allSuccess
    }
    fun isPathSafe(requestedPath: String): Boolean {
        return try {
            val canonicalPath = File(requestedPath).canonicalPath
            val rootPath = rootDir().canonicalPath
            canonicalPath.startsWith(rootPath)
        } catch (e: Exception) {
            Log.e(TAG, "Path safety check failed for: $requestedPath", e)
            false
        }
    }

    fun serveFile(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val filePath = session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
            ?: return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File path missing.\"}"
            )

        if (!isPathSafe(filePath)) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access Denied.\"}"
            )
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File not found.\"}"
            )
        }

        return try {
            val mimeType = getMimeType(file.name)
            val fis = FileInputStream(file)
            val response = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, mimeType, fis, file.length())
            response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Could not serve file: $filePath", e)
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Error reading file.\"}"
            )
        }
    }

    fun createDirectory(path: String): Boolean = try {
        if (!isPathSafe(path)) false else File(path).mkdirs()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create directory: $path", e)
        false
    }

    fun deleteFile(path: String): Boolean = try {
        if (!isPathSafe(path)) false
        else {
            val file = File(path)
            if (!file.exists()) true else if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to delete item: $path", e)
        false
    }

    private fun getMimeType(filename: String): String {
        return when (filename.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "txt", "log", "md" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4", "mkv" -> "video/mp4"
            "mp3", "ogg" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
