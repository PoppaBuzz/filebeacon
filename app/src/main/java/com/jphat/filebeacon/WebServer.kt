package com.jphat.filebeacon

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class WebServer(context: Context, port: Int) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "WebServer"
    }

    // Use application context to prevent memory leaks
    private val appContext = context.applicationContext

    private val resumableTransferManager = ResumableTransferManager()
    private val transferTaskManager = TransferTaskManager()

    init {
        // Initialize AuthManager with application context
        AuthManager.initialize(appContext)
    }

    /**
     * Check if endpoint should be publicly accessible (no auth required)
     */
    private fun isPublicEndpoint(uri: String): Boolean {
        return uri in listOf("/style.css", "/script.js", "/manifest.json", "/sw.js", "/icon.png")
    }

    /**
     * Check authentication for the current request
     */
    private fun checkAuthentication(session: IHTTPSession): Boolean {
        if (!AuthManager.isAuthRequired()) {
            return true
        }

        val authHeader = session.headers["authorization"]
        return AuthManager.verifyBasicAuth(authHeader)
    }

    /**
     * Create an unauthorized response with WWW-Authenticate header
     */
    private fun createUnauthorizedResponse(): Response {
        val response = newFixedLengthResponse(
            Response.Status.UNAUTHORIZED,
            MIME_HTML,
            """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Authentication Required</title>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body {
                        font-family: sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        background: #f0f2f5;
                    }
                    .auth-box {
                        background: white;
                        padding: 40px;
                        border-radius: 8px;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                        text-align: center;
                    }
                    h1 { color: #2196F3; margin-top: 0; }
                    p { color: #666; }
                </style>
            </head>
            <body>
                <div class="auth-box">
                    <h1>🔒 Authentication Required</h1>
                    <p>Please provide valid credentials to access this server.</p>
                </div>
            </body>
            </html>
            """.trimIndent()
        )
        response.addHeader("WWW-Authenticate", "Basic realm=\"FileBeacon\"")
        return response
    }

    /**
     * Safely escape a string for use in JavaScript context
     */
    private fun escapeJavaScript(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("<", "\\x3C")
            .replace(">", "\\x3E")
            .replace("&", "\\x26")
    }

    /**
     * Safely escape a string for use in JSON
     */
    private fun escapeJson(str: String): String {
        return com.google.gson.Gson().toJson(str).trim('"')
    }

    override fun serve(session: IHTTPSession?): Response {
        return session?.let { handleRequest(it) }
            ?: newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal Server Error."
            )
    }

    private fun handleRequest(session: IHTTPSession): Response {
        return try {
            val uri = session.uri ?: "/"

            // Check authentication for all requests except static assets
            if (!isPublicEndpoint(uri) && !checkAuthentication(session)) {
                return createUnauthorizedResponse()
            }

            when {
                uri == "/" || uri.startsWith("/browse") -> handleBrowse(session)
                uri.startsWith("/download") -> handleDownload(session)
                uri.startsWith("/view") -> handleViewFile(session)
                session.method == Method.POST && uri.startsWith("/upload") -> handleUpload(session)
                uri.startsWith("/upload-status") -> handleUploadStatus(session)
                session.method == Method.POST && uri.startsWith("/delete") -> handleDelete(session)
                session.method == Method.POST && uri.startsWith("/move") -> handleMove(session)
                session.method == Method.POST && uri.startsWith("/copy") -> handleCopy(session)
                session.method == Method.POST && uri.startsWith("/mkdir") -> handleMkdir(session)
                uri.startsWith("/archive/list") -> handleArchiveList(session)
                session.method == Method.POST && uri.startsWith("/archive/extract") -> handleArchiveExtract(
                    session
                )

                session.method == Method.POST && uri.startsWith("/archive/create") -> handleArchiveCreate(
                    session
                )

                uri.startsWith("/search") -> handleSearch(session)
                uri.startsWith("/gallery") -> handleGallery(session)
                uri.startsWith("/player") -> handleMediaPlayer(session)
                uri.startsWith("/pdf-viewer") -> handlePdfViewer(session)
                uri.startsWith("/pdf-info") -> handlePdfInfo(session)
                session.method == Method.POST && uri.startsWith("/pdf-convert") -> handlePdfConvert(
                    session
                )

                uri.startsWith("/progress-stream") -> handleProgressStream(session)
                session.method == Method.POST && uri.startsWith("/cancel-task") -> handleCancelTask(
                    session
                )
                // Add routes for static assets
                uri == "/style.css" -> serveAsset("style.css", "text/css")
                uri == "/script.js" -> serveAsset("script.js", "application/javascript")
                uri == "/manifest.json" -> serveAsset("manifest.json", "application/json")
                uri == "/sw.js" -> serveAsset("sw.js", "application/javascript")
                uri == "/icon.png" -> serveDrawable("icon")
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_HTML,
                    "<html><body><h2>404 Not Found</h2></body></html>"
                )
            }
        } catch (ex: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Server error: ${ex.message}"
            )
        }
    }

    // New function to serve files from the assets folder
    private fun serveAsset(fileName: String, mimeType: String): Response {
        return try {
            val inputStream: InputStream = appContext.assets.open(fileName)
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Error: File not found - $fileName"
            )
        }
    }

    private fun serveDrawable(name: String): Response {
        return try {
            val resId = appContext.resources.getIdentifier(name, "drawable", appContext.packageName)
            if (resId == 0) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Icon not found")
            }
            val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(
                appContext.resources, resId, null
            ) ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Icon not found")

            val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            newFixedLengthResponse(
                Response.Status.OK, "image/png",
                java.io.ByteArrayInputStream(bytes), bytes.size.toLong()
            )
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error serving icon")
        }
    }

    private fun handleBrowse(session: IHTTPSession): Response {
        val path = session.parameters["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
            ?: FileManager.rootDir().absolutePath
        val sortBy = session.parameters["sortBy"]?.firstOrNull() ?: "name"

        val dir = File(path)
        if (!FileManager.isPathSafe(dir.absolutePath))
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                MIME_PLAINTEXT,
                "Access Denied."
            )
        if (!dir.exists() || !dir.isDirectory)
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Directory not found."
            )
        return generateDirectoryPage(dir, sortBy)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        try {
            val tempFiles = mutableMapOf<String, String>()
            session.parseBody(tempFiles)

            val overwrite = session.parameters["overwrite"]?.firstOrNull()
                ?.equals("on", ignoreCase = true) == true

            val uploadPath = session.parameters["uploadPath"]?.firstOrNull()
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "application/json",
                    "{\"status\":\"error\",\"message\":\"Upload path missing\"}"
                )

            if (!FileManager.isPathSafe(uploadPath))
                return newFixedLengthResponse(
                    Response.Status.FORBIDDEN, "application/json",
                    "{\"status\":\"error\",\"message\":\"Invalid upload destination\"}"
                )

            val uploadDir = File(uploadPath)
            if (!uploadDir.exists()) uploadDir.mkdirs()

            // Get the original filename from the explicit 'filename' parameter
            val originalFileName = session.parameters["filename"]?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: "uploaded_file_${System.currentTimeMillis()}"

            Log.d(TAG, "[DEBUG] tempFiles keys: ${tempFiles.keys.joinToString(", ")}")
            Log.d(TAG, "[DEBUG] filename param: '$originalFileName'")

            // Find the actual uploaded file — it's the 'file' entry in tempFiles
            val tempFilePath = tempFiles["file"]
                ?: tempFiles.entries.firstOrNull { it.key != "filename" && it.key != "uploadPath" && it.key != "overwrite" }?.value
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST, "application/json",
                    "{\"status\":\"error\",\"message\":\"No file received\"}"
                )

            val tempFile = File(tempFilePath)
            if (!tempFile.exists())
                return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"status\":\"error\",\"message\":\"Temp file missing\"}"
                )

            val targetFileName = if (overwrite) originalFileName
                else FileManager.getUniqueFileName(uploadDir, originalFileName)
            val targetFile = File(uploadDir, targetFileName)

            return if (copyFile(tempFile, targetFile)) {
                try { tempFile.delete() } catch (e: Exception) { /* ignore */ }
                Log.d(TAG, "[DEBUG] Saved as: '$targetFileName'")
                newFixedLengthResponse(
                    Response.Status.OK, "application/json",
                    "{\"status\":\"success\",\"overwrite\":$overwrite," +
                    "\"message\":\"1 file(s) uploaded successfully\"," +
                    "\"files\":[\"$targetFileName\"]}"
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"status\":\"error\",\"message\":\"Failed to save file\"}"
                )
            }

        } catch (e: Exception) {
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "application/json",
                "{\"status\":\"error\",\"message\":\"Upload failed: ${e.message}\"}"
            )
        }
    }

    private fun extractOriginalFileName(paramName: String): String? {
        // Filenames are now sent separately, so this should handle legacy cases
        // Legacy format: file_INDEX_FILENAME (kept for backward compatibility)
        if (paramName.startsWith("file_")) {
            val firstUnderscoreIndex = paramName.indexOf('_')
            val secondUnderscoreIndex = paramName.indexOf('_', firstUnderscoreIndex + 1)
            
            if (secondUnderscoreIndex != -1 && secondUnderscoreIndex + 1 < paramName.length) {
                val fileName = paramName.substring(secondUnderscoreIndex + 1)
                if (fileName.isNotEmpty()) {
                    return fileName
                }
            }
        }
        
        return null
    }

    private fun copyFile(source: File, dest: File): Boolean {
        return try {
            if (!source.exists()) {
                return false
            }
            dest.parentFile?.let { parent ->
                if (!parent.exists()) {
                    parent.mkdirs()
                }
            }
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output -> // This will overwrite if dest file exists
                    input.copyTo(output)
                }
            }
            val success = dest.exists() && dest.length() > 0
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun handleDelete(session: IHTTPSession): Response {
        session.parseBody(mutableMapOf())
        val pathToDelete =
            session.parameters["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"Path to delete is missing\"}"
                )

        return if (FileManager.deleteFile(pathToDelete))
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Item deleted\"}"
            )
        else
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Failed to delete item\"}"
            )
    }

    private fun handleMove(session: IHTTPSession): Response {
        session.parseBody(mutableMapOf())
        val filesParam = session.parameters["files"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Files parameter missing\"}"
            )
        val destination = session.parameters["destination"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Destination path missing\"}"
            )

        if (!FileManager.isPathSafe(destination)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val filePaths = filesParam.split(",").map { URLDecoder.decode(it, "UTF-8") }
        val destDir = File(destination)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        var movedCount = 0
        var errorCount = 0

        filePaths.forEach { path ->
            if (FileManager.isPathSafe(path)) {
                val sourceFile = File(path)
                if (sourceFile.exists()) {
                    val destFile = File(destDir, sourceFile.name)
                    try {
                        if (sourceFile.renameTo(destFile)) {
                            movedCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                    }
                } else {
                    errorCount++
                }
            } else {
                errorCount++
            }
        }

        return if (errorCount == 0) {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Moved $movedCount file(s) successfully\"}"
            )
        } else {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Moved $movedCount file(s), $errorCount error(s)\"}"
            )
        }
    }

    private fun handleCopy(session: IHTTPSession): Response {
        session.parseBody(mutableMapOf())
        val filesParam = session.parameters["files"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Files parameter missing\"}"
            )
        val destination = session.parameters["destination"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Destination path missing\"}"
            )

        if (!FileManager.isPathSafe(destination)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val filePaths = filesParam.split(",").map { URLDecoder.decode(it, "UTF-8") }
        val destDir = File(destination)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        var copiedCount = 0
        var errorCount = 0

        filePaths.forEach { path ->
            if (FileManager.isPathSafe(path)) {
                val sourceFile = File(path)
                if (sourceFile.exists()) {
                    val destFile = File(destDir, sourceFile.name)
                    try {
                        if (copyFile(sourceFile, destFile)) {
                            copiedCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                    }
                } else {
                    errorCount++
                }
            } else {
                errorCount++
            }
        }

        return if (errorCount == 0) {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Copied $copiedCount file(s) successfully\"}"
            )
        } else {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Copied $copiedCount file(s), $errorCount error(s)\"}"
            )
        }
    }

    private fun handleViewFile(session: IHTTPSession): Response {
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"File path missing.\"}"
                )

        if (!FileManager.isPathSafe(filePath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access Denied.\"}"
            )
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File not found.\"}"
            )
        }

        return try {
            val rangeHeader = session.headers["range"]
            val fileSize = file.length()

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Handle resumable download with Range header
                val range = rangeHeader.substring(6).split("-")
                val start = range[0].toLongOrNull() ?: 0L
                val end =
                    if (range.size > 1 && range[1].isNotEmpty()) range[1].toLong() else fileSize - 1

                // NanoHTTPD will close the stream when response is sent
                val fis = FileInputStream(file)
                try {
                    fis.skip(start)

                    val response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        getMimeType(file.name),
                        fis,
                        end - start + 1
                    )
                    response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
                    response.addHeader("Accept-Ranges", "bytes")
                    response.addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
                    response.addHeader("Access-Control-Allow-Origin", "*")
                    response
                } catch (e: Exception) {
                    fis.close()
                    throw e
                }
            } else {
                // Normal inline view - NanoHTTPD will close the stream
                val fis = FileInputStream(file)
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    getMimeType(file.name),
                    fis,
                    fileSize
                )
                response.addHeader("Accept-Ranges", "bytes")
                response.addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
                response.addHeader("Access-Control-Allow-Origin", "*")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not serve file: $filePath", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Error reading file.\"}"
            )
        }
    }

    private fun handleDownload(session: IHTTPSession): Response {
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"File path missing.\"}"
                )

        if (!FileManager.isPathSafe(filePath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access Denied.\"}"
            )
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File not found.\"}"
            )
        }

        return try {
            val rangeHeader = session.headers["range"]
            val fileSize = file.length()

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                // Handle resumable download with Range header
                val range = rangeHeader.substring(6).split("-")
                val start = range[0].toLongOrNull() ?: 0L
                val end =
                    if (range.size > 1 && range[1].isNotEmpty()) range[1].toLong() else fileSize - 1

                // NanoHTTPD will close the stream when response is sent
                val fis = FileInputStream(file)
                try {
                    fis.skip(start)

                    val response = newFixedLengthResponse(
                        Response.Status.PARTIAL_CONTENT,
                        getMimeType(file.name),
                        fis,
                        end - start + 1
                    )
                    response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
                    response.addHeader("Accept-Ranges", "bytes")
                    response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                    response
                } catch (e: Exception) {
                    fis.close()
                    throw e
                }
            } else {
                // Normal download - NanoHTTPD will close the stream
                val fis = FileInputStream(file)
                val response = newFixedLengthResponse(
                    Response.Status.OK,
                    getMimeType(file.name),
                    fis,
                    fileSize
                )
                response.addHeader("Accept-Ranges", "bytes")
                response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
                response
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not serve file: $filePath", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Error reading file.\"}"
            )
        }
    }

    private fun handleUploadStatus(session: IHTTPSession): Response {
        val fileName = session.parameters["fileName"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File name missing\"}"
            )
        val uploadPath =
            session.parameters["uploadPath"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"Upload path missing\"}"
                )

        val offset = resumableTransferManager.getResumeOffset(uploadPath, fileName)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            "{\"status\":\"success\",\"offset\":$offset}"
        )
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

    private fun handleArchiveList(session: IHTTPSession): Response {
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"File path missing\"}"
                )

        if (!FileManager.isPathSafe(filePath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File not found\"}"
            )
        }

        val entries = ArchiveManager.listArchiveContents(file)
        val json = com.google.gson.Gson().toJson(mapOf("status" to "success", "entries" to entries))
        return newFixedLengthResponse(Response.Status.OK, "application/json", json)
    }

    private fun handleArchiveExtract(session: IHTTPSession): Response {
        session.parseBody(mutableMapOf())
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"File path missing\"}"
                )
        val destPath =
            session.parameters["dest"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: File(filePath).parent

        if (!FileManager.isPathSafe(filePath) || !FileManager.isPathSafe(destPath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val file = File(filePath)
        val destDir = File(destPath, file.nameWithoutExtension)

        return if (ArchiveManager.extractArchive(file, destDir)) {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Archive extracted successfully\"}"
            )
        } else {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Failed to extract archive\"}"
            )
        }
    }

    private fun handleArchiveCreate(session: IHTTPSession): Response {
        session.parseBody(mutableMapOf())
        val filesParam = session.parameters["files"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Files parameter missing\"}"
            )
        val outputPath =
            session.parameters["output"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"Output path missing\"}"
                )

        val filePaths = filesParam.split(",").map { URLDecoder.decode(it, "UTF-8") }
        val files = filePaths.mapNotNull { path ->
            if (FileManager.isPathSafe(path)) File(path) else null
        }

        if (files.isEmpty()) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"No valid files provided\"}"
            )
        }

        val outputFile = File(outputPath)
        return if (ArchiveManager.createZipArchive(files, outputFile)) {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Archive created successfully\"}"
            )
        } else {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Failed to create archive\"}"
            )
        }
    }

    private fun handleProgressStream(session: IHTTPSession): Response {
        val response = newChunkedResponse(Response.Status.OK, "text/event-stream", null)
        response.addHeader("Cache-Control", "no-cache")
        response.addHeader("Connection", "keep-alive")

        // Create a simple SSE stream
        val stream = object : java.io.InputStream() {
            private var closed = false
            private val buffer = java.io.ByteArrayOutputStream()

            override fun read(): Int {
                if (closed) return -1

                // Send progress updates every second
                try {
                    Thread.sleep(1000)
                    val tasks = transferTaskManager.getActiveTasks()
                    val progressList = tasks.mapNotNull { task ->
                        transferTaskManager.getTaskProgress(task.id)
                    }

                    val json = com.google.gson.Gson().toJson(progressList)
                    val data = "data: $json\n\n"
                    buffer.write(data.toByteArray())

                    if (buffer.size() > 0) {
                        val byte = buffer.toByteArray()[0].toInt()
                        buffer.reset()
                        return byte
                    }
                } catch (e: InterruptedException) {
                    closed = true
                }

                return -1
            }

            override fun close() {
                closed = true
                super.close()
            }
        }

        return newChunkedResponse(Response.Status.OK, "text/event-stream", stream)
    }

    private fun handleCancelTask(session: IHTTPSession): Response {
        val taskId = session.parameters["taskId"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"success\":false,\"message\":\"Task ID missing\"}"
            )

        transferTaskManager.cancelTask(taskId)
        return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"success\":true}")
    }

    private fun handlePdfViewer(session: IHTTPSession): Response {
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "text/plain",
                    "File parameter missing"
                )

        if (!FileManager.isPathSafe(filePath)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Access denied")
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        }

        val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
        val jsEscapedPath = escapeJavaScript(file.absolutePath)
        val jsEscapedFileName = escapeJavaScript(file.name)

        // Simple PDF viewer using browser's native PDF viewer
        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>${file.name}</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { background: #2b2b2b; font-family: sans-serif; }
                .toolbar {
                    background: #1a1a1a;
                    padding: 12px 20px;
                    display: flex;
                    align-items: center;
                    gap: 15px;
                    color: white;
                }
                .toolbar-title { flex: 1; font-size: 16px; }
                .toolbar-btn {
                    background: #3a3a3a;
                    border: none;
                    color: white;
                    padding: 8px 16px;
                    border-radius: 4px;
                    cursor: pointer;
                    text-decoration: none;
                    display: inline-block;
                }
                .toolbar-btn:hover { background: #4a4a4a; }
                iframe {
                    width: 100%;
                    height: calc(100vh - 60px);
                    border: none;
                }
            </style>
            <script src="/script.js"></script>
        </head>
        <body>
            <div class="toolbar">
                <div class="toolbar-title">${file.name}</div>
                <button class="toolbar-btn" onclick="convertPdfToImages('$jsEscapedPath', '$jsEscapedFileName')">🖼️ Convert to Images</button>
                <a href="/download?file=$encodedPath" class="toolbar-btn">⬇️ Download</a>
                <button class="toolbar-btn" onclick="window.history.back()">✖ Close</button>
            </div>
            <iframe src="/view?file=$encodedPath#toolbar=1&navpanes=1&scrollbar=1"></iframe>
        </body>
        </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handlePdfInfo(session: IHTTPSession): Response {
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"File path missing\"}"
                )

        if (!FileManager.isPathSafe(filePath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File not found\"}"
            )
        }

        val info = PdfConverter.getPdfInfo(file)
        return if (info != null) {
            val json = com.google.gson.Gson().toJson(mapOf("status" to "success", "info" to info))
            newFixedLengthResponse(Response.Status.OK, "application/json", json)
        } else {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Failed to read PDF info\"}"
            )
        }
    }

    private fun handlePdfConvert(session: IHTTPSession): Response {
        Log.d(TAG, "=== PDF CONVERT REQUEST RECEIVED ===")
        Log.d(TAG, "Method: ${session.method}")
        Log.d(TAG, "URI: ${session.uri}")
        Log.d(TAG, "Headers: ${session.headers}")

        session.parseBody(mutableMapOf())
        Log.d(TAG, "Body parsed")
        Log.d(TAG, "Parameters: ${session.parameters}")

        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: run {
                    Log.e(TAG, "File path missing in parameters")
                    return newFixedLengthResponse(
                        Response.Status.BAD_REQUEST,
                        "application/json",
                        "{\"status\":\"error\",\"message\":\"File path missing\"}"
                    )
                }

        Log.d(TAG, "File path: $filePath")

        val format = session.parameters["format"]?.firstOrNull() ?: "png"
        val qualityRaw = session.parameters["quality"]?.firstOrNull()?.toIntOrNull() ?: 90
        val scaleRaw = session.parameters["scale"]?.firstOrNull()?.toFloatOrNull() ?: 2.0f
        val pageParam = session.parameters["page"]?.firstOrNull()
        val startPage = session.parameters["startPage"]?.firstOrNull()?.toIntOrNull()
        val endPage = session.parameters["endPage"]?.firstOrNull()?.toIntOrNull()

        // Validate and clamp parameters to prevent resource exhaustion
        val quality = qualityRaw.coerceIn(1, 100)
        val scale = scaleRaw.coerceIn(0.1f, 5.0f)

        if (qualityRaw != quality || scaleRaw != scale) {
            Log.w(TAG, "PDF conversion parameters were clamped: quality $qualityRaw->$quality, scale $scaleRaw->$scale")
        }

        Log.d(TAG, "Conversion params - format: $format, quality: $quality, scale: $scale")

        if (!FileManager.isPathSafe(filePath)) {
            Log.e(TAG, "Path not safe: $filePath")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val pdfFile = File(filePath)
        Log.d(TAG, "PDF file exists: ${pdfFile.exists()}, is file: ${pdfFile.isFile}")

        if (!pdfFile.exists() || !pdfFile.isFile) {
            Log.e(TAG, "File not found or not a file: $filePath")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"File not found\"}"
            )
        }

        val imageFormat = when (format.lowercase()) {
            "jpg", "jpeg" -> PdfConverter.ImageFormat.JPEG
            else -> PdfConverter.ImageFormat.PNG
        }
        Log.d(TAG, "Image format: $imageFormat")

        val pageRange = when {
            pageParam != null -> PdfConverter.PageRange.Single(
                pageParam.toIntOrNull()?.minus(1) ?: 0
            )

            startPage != null && endPage != null -> PdfConverter.PageRange.Range(
                startPage - 1,
                endPage - 1
            )

            else -> PdfConverter.PageRange.All
        }
        Log.d(TAG, "Page range: $pageRange")

        val options = PdfConverter.ConversionOptions(
            format = imageFormat,
            quality = quality,
            scale = scale,
            pageRange = pageRange
        )
        Log.d(TAG, "Conversion options created: $options")

        // Create output directory
        val outputDir = File(pdfFile.parent, "${pdfFile.nameWithoutExtension}_images")
        Log.d(TAG, "Output directory: ${outputDir.absolutePath}")

        Log.d(TAG, "Starting PDF conversion...")
        val result = PdfConverter.convertPdfToImages(appContext, pdfFile, outputDir, options)
        Log.d(TAG, "Conversion complete. Success: ${result.success}, Message: ${result.message}")

        return if (result.success) {
            Log.d(TAG, "Conversion successful! Output files: ${result.outputFiles.size}")
            val files = result.outputFiles.map { file ->
                Log.d(TAG, "Output file: ${file.name}, size: ${file.length()}")
                mapOf(
                    "name" to file.name,
                    "path" to file.absolutePath,
                    "size" to file.length()
                )
            }
            val responseMap = mapOf(
                "status" to "success",
                "message" to result.message,
                "totalPages" to result.totalPages,
                "convertedPages" to result.outputFiles.size,
                "outputDir" to outputDir.absolutePath,
                "files" to files
            )
            val json = com.google.gson.Gson().toJson(responseMap)
            Log.d(TAG, "Sending success response: $json")
            newFixedLengthResponse(Response.Status.OK, "application/json", json)
        } else {
            Log.e(TAG, "Conversion failed: ${result.message}")
            val errorJson = "{\"status\":\"error\",\"message\":\"${result.message}\"}"
            Log.d(TAG, "Sending error response: $errorJson")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                errorJson
            )
        }
    }


    private fun handleMediaPlayer(session: IHTTPSession): Response {
        val filePath =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "text/plain",
                    "File parameter missing"
                )

        if (!FileManager.isPathSafe(filePath)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "Access denied")
        }

        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
        }

        val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
        val fileUrl = "/download?file=$encodedPath"
        val mimeType = getMimeType(file.name)
        val isVideo = mimeType.startsWith("video/")
        val mediaType = if (isVideo) "video" else "audio"

        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>${file.name} - FileBeacon</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1"></script>
            <style>
                body { margin: 0; padding: 20px; background: #1a1a1a; font-family: sans-serif; color: #fff; }
                .player-container { max-width: 900px; margin: 0 auto; }
                .player-header { margin-bottom: 20px; }
                .player-title { font-size: 24px; margin-bottom: 10px; }
                $mediaType { width: 100%; ${if (isVideo) "max-height: 70vh;" else ""} background: #000; border-radius: 8px; }
                .controls { margin-top: 20px; display: flex; gap: 10px; flex-wrap: wrap; }
                .btn { background: #2196F3; color: white; border: none; padding: 10px 20px; border-radius: 5px; cursor: pointer; font-size: 14px; }
                .btn:hover { background: #1976D2; }
                .btn:disabled { background: #666; cursor: not-allowed; }
                .cast-btn { background: #FF5722; }
                .cast-btn:hover { background: #E64A19; }
                .cast-status { margin-top: 10px; padding: 10px; background: rgba(255,255,255,0.1); border-radius: 5px; display: none; }
                .cast-status.active { display: block; }
            </style>
        </head>
        <body>
            <div class="player-container">
                <div class="player-header">
                    <div class="player-title">${file.name}</div>
                    <button class="btn" onclick="window.history.back()">⬅ Back</button>
                </div>
                <$mediaType id="mediaPlayer" controls>
                    <source src="$fileUrl" type="$mimeType">
                    Your browser does not support the $mediaType element.
                </$mediaType>
                <div class="controls">
                    <button class="btn" onclick="player.currentTime -= 10">⏪ -10s</button>
                    <button class="btn" onclick="togglePlayPause()" id="playPauseBtn">⏸ Pause</button>
                    <button class="btn" onclick="player.currentTime += 10">⏩ +10s</button>
                    <button class="btn cast-btn" onclick="initializeCast()" id="castBtn">📡 Cast</button>
                    <button class="btn" onclick="downloadFile()">⬇️ Download</button>
                </div>
                <div class="cast-status" id="castStatus">
                    <div id="castMessage">Initializing Cast...</div>
                </div>
            </div>
            <script>
                const player = document.getElementById('mediaPlayer');
                const playPauseBtn = document.getElementById('playPauseBtn');
                
                player.addEventListener('play', () => { playPauseBtn.textContent = '⏸ Pause'; });
                player.addEventListener('pause', () => { playPauseBtn.textContent = '▶ Play'; });
                
                function togglePlayPause() {
                    if (player.paused) player.play();
                    else player.pause();
                }
                
                function downloadFile() {
                    window.location.href = '$fileUrl';
                }
                
                // Cast functionality
                let castSession = null;
                let castContext = null;
                
                window['__onGCastApiAvailable'] = function(isAvailable) {
                    if (isAvailable) {
                        castContext = cast.framework.CastContext.getInstance();
                        castContext.setOptions({
                            receiverApplicationId: chrome.cast.media.DEFAULT_MEDIA_RECEIVER_APP_ID,
                            autoJoinPolicy: chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED
                        });
                        
                        castContext.addEventListener(
                            cast.framework.CastContextEventType.SESSION_STATE_CHANGED,
                            (event) => {
                                switch (event.sessionState) {
                                    case cast.framework.SessionState.SESSION_STARTED:
                                        castSession = castContext.getCurrentSession();
                                        showCastStatus('Connected to ' + castSession.getCastDevice().friendlyName);
                                        loadMediaToCast();
                                        break;
                                    case cast.framework.SessionState.SESSION_ENDED:
                                        castSession = null;
                                        showCastStatus('Cast session ended');
                                        setTimeout(() => hideCastStatus(), 2000);
                                        break;
                                }
                            }
                        );
                    }
                };
                
                function initializeCast() {
                    if (!castContext) {
                        alert('Cast API not available. Make sure you are using a supported browser.');
                        return;
                    }
                    castContext.requestSession().then(
                        () => { console.log('Cast session started'); },
                        (error) => { console.error('Error starting cast:', error); }
                    );
                }
                
                function loadMediaToCast() {
                    if (!castSession) return;
                    
                    const mediaInfo = new chrome.cast.media.MediaInfo(window.location.origin + '$fileUrl', '$mimeType');
                    mediaInfo.metadata = new chrome.cast.media.GenericMediaMetadata();
                    mediaInfo.metadata.title = '${file.name}';
                    
                    const request = new chrome.cast.media.LoadRequest(mediaInfo);
                    request.currentTime = player.currentTime;
                    
                    castSession.loadMedia(request).then(
                        () => { showCastStatus('Playing on Cast device'); },
                        (error) => { showCastStatus('Error loading media: ' + error); }
                    );
                }
                
                function showCastStatus(message) {
                    document.getElementById('castMessage').textContent = message;
                    document.getElementById('castStatus').classList.add('active');
                }
                
                function hideCastStatus() {
                    document.getElementById('castStatus').classList.remove('active');
                }
            </script>
        </body>
        </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handleGallery(session: IHTTPSession): Response {
        val dirPath =
            session.parameters["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: FileManager.rootDir().absolutePath

        if (!FileManager.isPathSafe(dirPath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Directory not found\"}"
            )
        }

        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
        val images = dir.listFiles()?.filter { file ->
            file.isFile && file.extension.lowercase() in imageExtensions
        }?.sortedBy { it.name } ?: emptyList()

        val currentFile =
            session.parameters["file"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
        val currentIndex = if (currentFile != null) {
            images.indexOfFirst { it.absolutePath == currentFile }.takeIf { it >= 0 } ?: 0
        } else 0

        val imageList = images.mapIndexed { index, file ->
            val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
            val escapedName = escapeJson(file.name)
            """{"name":"$escapedName","url":"/download?file=$encodedPath","index":$index}"""
        }.joinToString(",")

        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Gallery - FileBeacon</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 0; padding: 0; background: #000; font-family: sans-serif; overflow: hidden; }
                .gallery-container { position: relative; width: 100vw; height: 100vh; display: flex; align-items: center; justify-content: center; }
                .gallery-image { max-width: 90%; max-height: 90vh; object-fit: contain; }
                .gallery-controls { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.8); padding: 15px 25px; border-radius: 30px; display: flex; gap: 15px; align-items: center; }
                .gallery-btn { background: #fff; border: none; padding: 10px 20px; border-radius: 20px; cursor: pointer; font-size: 16px; }
                .gallery-btn:hover { background: #ddd; }
                .gallery-info { color: #fff; font-size: 14px; }
                .close-btn { position: fixed; top: 20px; right: 20px; background: rgba(255,255,255,0.9); border: none; padding: 10px 20px; border-radius: 20px; cursor: pointer; font-size: 16px; }
                .nav-btn { position: fixed; top: 50%; transform: translateY(-50%); background: rgba(255,255,255,0.9); border: none; padding: 20px; border-radius: 50%; cursor: pointer; font-size: 24px; }
                .nav-btn.prev { left: 20px; }
                .nav-btn.next { right: 20px; }
                .slideshow-active .gallery-btn.slideshow { background: #4CAF50; color: white; }
            </style>
        </head>
        <body>
            <div class="gallery-container">
                <img id="galleryImage" class="gallery-image" src="" alt="Gallery Image" />
            </div>
            <button class="close-btn" onclick="closeGallery()">✖ Close</button>
            <button class="nav-btn prev" onclick="prevImage()">◀</button>
            <button class="nav-btn next" onclick="nextImage()">▶</button>
            <div class="gallery-controls">
                <button class="gallery-btn" onclick="prevImage()">⬅ Previous</button>
                <button class="gallery-btn slideshow" onclick="toggleSlideshow()">▶ Slideshow</button>
                <button class="gallery-btn" onclick="nextImage()">Next ➡</button>
                <span class="gallery-info" id="imageInfo"></span>
            </div>
            <script>
                const images = [$imageList];
                let currentIndex = $currentIndex;
                let slideshowInterval = null;
                
                function showImage(index) {
                    if (images.length === 0) return;
                    currentIndex = (index + images.length) % images.length;
                    const img = images[currentIndex];
                    document.getElementById('galleryImage').src = img.url;
                    document.getElementById('imageInfo').textContent = img.name + ' (' + (currentIndex + 1) + '/' + images.length + ')';
                }
                
                function nextImage() { showImage(currentIndex + 1); }
                function prevImage() { showImage(currentIndex - 1); }
                
                function toggleSlideshow() {
                    if (slideshowInterval) {
                        clearInterval(slideshowInterval);
                        slideshowInterval = null;
                        document.body.classList.remove('slideshow-active');
                    } else {
                        slideshowInterval = setInterval(nextImage, 3000);
                        document.body.classList.add('slideshow-active');
                    }
                }
                
                function closeGallery() {
                    window.history.back();
                }
                
                document.addEventListener('keydown', (e) => {
                    if (e.key === 'ArrowLeft') prevImage();
                    else if (e.key === 'ArrowRight') nextImage();
                    else if (e.key === 'Escape') closeGallery();
                    else if (e.key === ' ') { e.preventDefault(); toggleSlideshow(); }
                });
                
                showImage(currentIndex);
            </script>
        </body>
        </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handleSearch(session: IHTTPSession): Response {
        val query = session.parameters["q"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Query parameter missing\"}"
            )
        val searchPath =
            session.parameters["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: FileManager.rootDir().absolutePath
        val searchContent = session.parameters["content"]?.firstOrNull()?.toBoolean() ?: true

        if (!FileManager.isPathSafe(searchPath)) {
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Access denied\"}"
            )
        }

        return try {
            val searchDir = File(searchPath)
            val results = kotlinx.coroutines.runBlocking {
                SearchManager.search(searchDir, query, searchContent, maxResults = 50)
            }

            val resultsJson = results.map { result ->
                mapOf(
                    "path" to result.file.absolutePath,
                    "name" to result.file.name,
                    "matchType" to result.matchType.name,
                    "snippet" to result.snippet,
                    "lineNumber" to result.lineNumber,
                    "isDirectory" to result.file.isDirectory
                )
            }

            val json = com.google.gson.Gson()
                .toJson(mapOf("status" to "success", "results" to resultsJson))
            newFixedLengthResponse(Response.Status.OK, "application/json", json)
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Search failed: ${e.message}\"}"
            )
        }
    }

    private fun handleMkdir(session: IHTTPSession): Response {
        session.parseBody(mutableMapOf())
        val parentPath =
            session.parameters["path"]?.firstOrNull()?.let { URLDecoder.decode(it, "UTF-8") }
                ?: return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"status\":\"error\",\"message\":\"Parent path is missing\"}"
                )
        val folderName = session.parameters["name"]?.firstOrNull()
            ?: return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Folder name is missing\"}"
            )

        val newDirPath = File(parentPath, folderName).absolutePath
        return if (FileManager.createDirectory(newDirPath))
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"success\",\"message\":\"Folder '$folderName' created\"}"
            )
        else
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                "{\"status\":\"error\",\"message\":\"Could not create folder\"}"
            )
    }

    private fun generateDirectoryPage(dir: File, sortBy: String): Response {
        val itemsOrNull = dir.listFiles()

        val items = when (sortBy) {
            "type" -> {
                itemsOrNull?.sortedWith(
                    compareBy<File> { !it.isDirectory }
                        .thenBy { it.extension.lowercase(Locale.getDefault()) }
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                ) ?: emptyList()
            }

            else -> { // Default to "name"
                itemsOrNull?.sortedWith(
                    compareBy<File> { !it.isDirectory } // Directories first
                        .thenByDescending { it.name.isNotEmpty() && !it.name[0].isLetterOrDigit() } // Special chars after alphanumeric
                        .thenBy { it.name.lowercase(Locale.getDefault()) }
                ) ?: emptyList()
            }
        }
        val parentPath =
            dir.parentFile?.takeIf { FileManager.isPathSafe(it.absolutePath) }?.absolutePath

        val tableRowsHtml = when {
            itemsOrNull == null -> "<tr><td colspan='4' class='empty error'>Could not read directory.<br/>Please grant 'Files and media' or 'All files access' permission to the app in your device settings.</td></tr>"
            items.isEmpty() -> "<tr><td colspan='4' class='empty'>This folder is empty.</td></tr>"
            else -> items.joinToString("") { file ->
                val encodedPath = URLEncoder.encode(file.absolutePath, "UTF-8")
                val encodedDirPath = URLEncoder.encode(dir.absolutePath, "UTF-8")
                val icon = if (file.isDirectory) "📁" else getFileIcon(file.name)
                val ext = file.extension.lowercase()
                val isImage = ext in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")
                val isVideo = ext in setOf("mp4", "mov", "avi", "mkv", "webm")
                val isAudio = ext in setOf("mp3", "wav", "ogg", "aac", "flac", "m4a")
                val isMedia = isVideo || isAudio
                val isPdf = ext == "pdf"

                val link = if (file.isDirectory) {
                    "/browse?path=$encodedPath"
                } else if (isImage) {
                    "/gallery?path=$encodedDirPath&file=$encodedPath"
                } else if (isMedia) {
                    "/player?file=$encodedPath"
                } else if (isPdf) {
                    "/pdf-viewer?file=$encodedPath"
                } else {
                    "/download?file=$encodedPath"
                }

                val size = if (file.isFile) formatFileSize(file.length()) else "--"
                val modified = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    Locale.US
                ).format(Date(file.lastModified()))
                val isArchive = ArchiveManager.isArchiveFile(file.name)
                val extractBtn =
                    if (isArchive) """<button class="extract-btn" onclick="extractArchive('$encodedPath', '${file.name.replace("'", "\\'")}')">📦</button>""" else ""
                val galleryBtn =
                    if (isImage) """<button class="gallery-btn" onclick="window.location.href='/gallery?path=$encodedDirPath&file=$encodedPath'">🖼️</button>""" else ""
                val playerBtn =
                    if (isMedia) """<button class="player-btn" onclick="window.location.href='/player?file=$encodedPath'">${if (isVideo) "🎬" else "🎵"}</button>""" else ""
                val pdfBtn =
                    if (isPdf) """<button class="pdf-btn" onclick="window.location.href='/pdf-viewer?file=$encodedPath'">📄</button>""" else ""
                val pdfConvertBtn =
                    if (isPdf) """<button class="pdf-convert-btn" onclick="convertPdfToImages('$encodedPath', '${file.name.replace("'", "\\'")}')">🖼️</button>""" else ""
                """
                <tr>
                    <td class="col-name"><a class="filename" href="$link"><span class="icon">$icon</span>${file.name}</a></td>
                    <td class="col-modified">$modified</td>
                    <td class="col-size">$size</td>
                    <td class="col-actions">$galleryBtn$playerBtn$pdfBtn$pdfConvertBtn$extractBtn<button class="delete-btn" onclick="deleteItem('$encodedPath', '${file.name.replace("'", "\\'")}')">🗑️</button></td>
                </tr>
                """
            }
        }

        // Encode current directory path for JavaScript variable
        val encodedCurrentDirPath = URLEncoder.encode(dir.absolutePath, "UTF-8")

        val html = """
       <!DOCTYPE html>
       <html>

       <head>
           <title>FileBeacon</title>
           <meta name="viewport" content="width=device-width, initial-scale=1.0">
           <meta name="theme-color" content="#2196F3">
           <meta name="description" content="Browse and manage files over WiFi">
           <link rel="stylesheet" type="text/css" href="/style.css">
           <link rel="manifest" href="/manifest.json">
           <meta name="apple-mobile-web-app-capable" content="yes">
           <meta name="apple-mobile-web-app-status-bar-style" content="default">
           <meta name="apple-mobile-web-app-title" content="WiFi Explorer">
       </head>

       <body>
           <div class="header">
               <div class="header-inner">
                   <img src="/icon.png" class="header-logo" alt="FileBeacon" />
                   <div class="header-text">
                       <div class="header-title">File<span>Beacon</span></div>
                       <div class="header-tagline">Your files. Always within reach.</div>
                   </div>
               </div>
               <div class="header-path">
                   <div class="path-label">Current Path</div>
                   <div class="path">${dir.absolutePath}</div>
               </div>
           </div>
           <div class="container">
               <div class="nav">
                   <button class="btn" onclick="goToParent()" title="Go to parent folder" ${if (parentPath == null) "disabled" else ""}>⬆️ Up</button>
                   <button class="btn" onclick="goToRoot()" title="Go to home folder">🏠 Home</button>
                   <button class="btn" onclick="createFolder()" title="Create new folder">📁+ Folder</button>
                   <button class="btn" onclick="toggleUpload()" title="Upload files">📤 Upload</button>
                   <button class="btn" onclick="toggleSearch()" title="Search files">🔍 Search</button>
                   <button class="btn" onclick="location.reload()" title="Refresh page">🔄</button>
                   <button class="btn" onclick="sortByName()" title="Sort by name">📝 Name</button>
                   <button class="btn" onclick="sortByType()" title="Sort by type">📋 Type</button>
                   <button class="btn" onclick="toggleThemeMenu()" title="Change theme">🎨</button>
               </div>

               <div id="searchBox" class="search-box" style="display:none;">
                   <input type="text" id="searchInput" placeholder="Search files..." />
                   <label><input type="checkbox" id="searchContent" checked /> Search content</label>
                   <button class="btn" onclick="performSearch()">Search</button>
                   <button class="btn" onclick="clearSearch()">Clear</button>
               </div>

               <div id="themeMenu" class="theme-menu" style="display:none;">
                   <div class="theme-section">
                       <h3>Color Theme</h3>
                       <button class="theme-btn" data-type="theme" onclick="setTheme('light')">☀️ Light</button>
                       <button class="theme-btn" data-type="theme" onclick="setTheme('dark')">🌙 Dark</button>
                       <button class="theme-btn" data-type="theme" onclick="setTheme('blue')">💙 Blue</button>
                       <button class="theme-btn" data-type="theme" onclick="setTheme('green')">💚 Green</button>
                       <button class="theme-btn" data-type="theme" onclick="setTheme('purple')">💜 Purple</button>
                   </div>
                   <div class="theme-section">
                       <h3>Icon Style</h3>
                       <button class="theme-btn" data-type="icons" onclick="setIconPack('emoji')">😀 Emoji</button>
                       <button class="theme-btn" data-type="icons" onclick="setIconPack('minimal')">⚪ Minimal</button>
                       <button class="theme-btn" data-type="icons" onclick="setIconPack('colorful')">🌈 Colorful</button>
                   </div>
               </div>

               <div id="multiSelectActions" class="multi-select-actions">
                   <span class="count"><span id="selectedCount">0</span> selected</span>
                   <button class="btn" onclick="selectAll()" title="Select all files">☑️ All</button>
                   <button class="btn" onclick="moveSelected()" title="Move selected files">📁➡️ Move</button>
                   <button class="btn" onclick="copySelected()" title="Copy selected files">📋 Copy</button>
                   <button class="btn" onclick="downloadSelected()" title="Download selected files">⬇️ Download</button>
                   <button class="btn" onclick="createArchiveFromSelected()" title="Create archive from selected">📦 Archive</button>
                   <button class="btn" onclick="deleteSelected()" title="Delete selected files">🗑️ Delete</button>
                   <button class="btn" onclick="clearSelection()" title="Clear selection">✖️ Clear</button>
               </div>

               <div id="uploadZone" onclick="document.getElementById('fileInput').click()" ondrop="dropHandler(event);" ondragover="dragOverHandler(event);" ondragenter="dragEnterHandler(event);" ondragleave="dragLeaveHandler(event);">
                   <div class="upload-animation" id="uploadAnimation">
                       <div class="spinner"></div>
                       <div>Uploading files...</div>
                       <div class="upload-progress">
                           <div class="upload-progress-bar" id="progressBar"></div>
                       </div>
                       <div id="uploadStatus">Preparing upload...</div>
                   </div>
                   <p id="uploadText">Drop files here or click to select for upload</p>
                   <input type="file" id="fileInput" multiple style="display:none;" onchange="handleFiles(this.files)" />
                   <input type="checkbox" name="overwrite" id="overwrite">
                   <label for="overwrite">Overwrite existing files</label>
               </div>

               <table class="file-table">
                   <thead>
                       <tr>
                           <th>Name</th>
                           <th class="col-modified">Modified</th>
                           <th class="col-size">Size</th>
                           <th class="col-actions"></th>
                       </tr>
                   </thead>
                   <tbody>$tableRowsHtml</tbody>
               </table>
           </div>
           <div id="progress-bubbles" class="progress-bubbles-container"></div>
           <script>
               const currentUploadPath = decodeURIComponent("$encodedCurrentDirPath");
           </script>
           <script src="/script.js"></script>
       </body>
       </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun getFileIcon(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> "🖼️" // Image
            "mp3", "wav", "ogg", "aac", "flac" -> "🎵" // Audio
            "mp4", "mov", "avi", "mkv", "webm" -> "🎬" // Video
            "pdf" -> "📄" // PDF
            "doc", "docx" -> "📄" // Word Document
            "xls", "xlsx" -> "📊" // Excel Spreadsheet
            "ppt", "pptx" -> "🖥️" // PowerPoint
            "txt", "md", "log" -> "📝" // Text file
            "zip", "rar", "7z", "tar", "gz" -> "📦" // Archive
            "apk" -> "📱" // APK
            "html", "htm", "css", "js" -> "💻" // Web files
            else -> "❓" // Unknown / Generic file
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return String.format(
            Locale.US,
            "%.1f %s",
            size / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }

}
