package com.jphat.filebeacon

import android.util.Log
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ArchiveManager {

    private const val TAG = "ArchiveManager"

    data class ArchiveEntry(
        val name: String,
        val size: Long,
        val isDirectory: Boolean,
        val compressedSize: Long = 0
    )

    /**
     * Validates that a file path does not escape the destination directory.
     * Prevents path traversal attacks through malicious archive entries.
     */
    private fun isPathSafeForExtraction(destDir: File, entryName: String): Boolean {
        try {
            // Normalize the entry name to prevent various tricks
            val normalizedEntry = entryName.replace("\\", "/")

            // Reject paths with suspicious patterns
            if (normalizedEntry.contains("..") ||
                normalizedEntry.startsWith("/") ||
                normalizedEntry.contains("//") ||
                normalizedEntry.contains("\u0000")) {
                Log.w(TAG, "Rejected suspicious path: $entryName")
                return false
            }

            // Create the destination file and verify it's within bounds
            val destFile = File(destDir, normalizedEntry)
            val canonicalDestPath = destFile.canonicalPath
            val canonicalDestDir = destDir.canonicalPath

            // Ensure the file is within the destination directory
            if (!canonicalDestPath.startsWith(canonicalDestDir + File.separator) &&
                canonicalDestPath != canonicalDestDir) {
                Log.w(TAG, "Path traversal attempt detected: $entryName -> $canonicalDestPath")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating path: $entryName", e)
            return false
        }
    }

    fun listArchiveContents(archiveFile: File): List<ArchiveEntry> {
        return when (archiveFile.extension.lowercase()) {
            "zip" -> listZipContents(archiveFile)
            "rar" -> listRarContents(archiveFile)
            "7z" -> list7zContents(archiveFile)
            "tar" -> listTarContents(archiveFile)
            "gz", "tgz" -> listGzipContents(archiveFile)
            else -> emptyList()
        }
    }

    private fun listZipContents(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        try {
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entries.add(
                        ArchiveEntry(
                            name = entry.name,
                            size = entry.size,
                            isDirectory = entry.isDirectory,
                            compressedSize = entry.compressedSize
                        )
                    )
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing ZIP contents", e)
        }
        return entries
    }

    private fun listRarContents(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        try {
            Archive(file).use { archive ->
                val headers = archive.fileHeaders
                for (header in headers) {
                    entries.add(
                        ArchiveEntry(
                            name = header.fileName,
                            size = header.fullUnpackSize,
                            isDirectory = header.isDirectory,
                            compressedSize = header.fullPackSize
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing RAR contents", e)
        }
        return entries
    }

    private fun list7zContents(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        try {
            SevenZFile.builder().setFile(file).get().use { sevenZFile ->
                var entry: SevenZArchiveEntry? = sevenZFile.nextEntry
                while (entry != null) {
                    entries.add(
                        ArchiveEntry(
                            name = entry.name,
                            size = entry.size,
                            isDirectory = entry.isDirectory
                        )
                    )
                    entry = sevenZFile.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing 7Z contents", e)
        }
        return entries
    }

    private fun listTarContents(file: File): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        try {
            TarArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { tis ->
                var entry = tis.nextEntry
                while (entry != null) {
                    entries.add(
                        ArchiveEntry(
                            name = entry.name,
                            size = entry.size,
                            isDirectory = entry.isDirectory
                        )
                    )
                    entry = tis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing TAR contents", e)
        }
        return entries
    }

    private fun listGzipContents(file: File): List<ArchiveEntry> {
        // GZIP typically contains a single file
        return try {
            val originalName = file.nameWithoutExtension
            listOf(
                ArchiveEntry(
                    name = originalName,
                    size = -1, // Size unknown without decompression
                    isDirectory = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error listing GZIP contents", e)
            emptyList()
        }
    }

    fun extractArchive(archiveFile: File, destinationDir: File): Boolean {
        return when (archiveFile.extension.lowercase()) {
            "zip" -> extractZip(archiveFile, destinationDir)
            "rar" -> extractRar(archiveFile, destinationDir)
            "7z" -> extract7z(archiveFile, destinationDir)
            "tar" -> extractTar(archiveFile, destinationDir)
            "gz", "tgz" -> extractGzip(archiveFile, destinationDir)
            else -> false
        }
    }

    private fun extractZip(file: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            ZipArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    // Validate path before extraction
                    if (!isPathSafeForExtraction(destDir, entry.name)) {
                        Log.w(TAG, "Skipping unsafe entry: ${entry.name}")
                        entry = zis.nextEntry
                        continue
                    }

                    val destFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ZIP", e)
            false
        }
    }

    private fun extractRar(file: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            Archive(file).use { archive ->
                val headers = archive.fileHeaders
                for (header in headers) {
                    // Validate path before extraction
                    if (!isPathSafeForExtraction(destDir, header.fileName)) {
                        Log.w(TAG, "Skipping unsafe entry: ${header.fileName}")
                        continue
                    }

                    val destFile = File(destDir, header.fileName)
                    if (header.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            archive.extractFile(header, fos)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting RAR", e)
            false
        }
    }

    private fun extract7z(file: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            SevenZFile.builder().setFile(file).get().use { sevenZFile ->
                var entry: SevenZArchiveEntry? = sevenZFile.nextEntry
                while (entry != null) {
                    // Validate path before extraction
                    if (!isPathSafeForExtraction(destDir, entry.name)) {
                        Log.w(TAG, "Skipping unsafe entry: ${entry.name}")
                        entry = sevenZFile.nextEntry
                        continue
                    }

                    val destFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (sevenZFile.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    entry = sevenZFile.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting 7Z", e)
            false
        }
    }

    private fun extractTar(file: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            TarArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { tis ->
                var entry = tis.nextEntry
                while (entry != null) {
                    // Validate path before extraction
                    if (!isPathSafeForExtraction(destDir, entry.name)) {
                        Log.w(TAG, "Skipping unsafe entry: ${entry.name}")
                        entry = tis.nextEntry
                        continue
                    }

                    val destFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            tis.copyTo(fos)
                        }
                    }
                    entry = tis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting TAR", e)
            false
        }
    }

    private fun extractGzip(file: File, destDir: File): Boolean {
        return try {
            destDir.mkdirs()
            val outputFile = File(destDir, file.nameWithoutExtension)
            GzipCompressorInputStream(BufferedInputStream(FileInputStream(file))).use { gis ->
                FileOutputStream(outputFile).use { fos ->
                    gis.copyTo(fos)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting GZIP", e)
            false
        }
    }

    fun createZipArchive(files: List<File>, outputFile: File): Boolean {
        return try {
            // Calculate total size to prevent disk exhaustion
            var totalSize = 0L
            val maxArchiveSize = 2L * 1024 * 1024 * 1024 // 2 GB limit

            fun calculateSize(file: File): Long {
                return if (file.isDirectory) {
                    (file.listFiles() ?: emptyArray()).sumOf { calculateSize(it) }
                } else {
                    file.length()
                }
            }

            totalSize = files.sumOf { calculateSize(it) }

            if (totalSize > maxArchiveSize) {
                Log.e(TAG, "Archive size would exceed limit: $totalSize bytes > $maxArchiveSize bytes")
                return false
            }

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                for (file in files) {
                    addFileToZip(zos, file, "")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating ZIP archive", e)
            false
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, parentPath: String) {
        val entryName = if (parentPath.isEmpty()) file.name else "$parentPath/${file.name}"

        if (file.isDirectory) {
            val dirEntry = ZipEntry("$entryName/")
            zos.putNextEntry(dirEntry)
            zos.closeEntry()

            file.listFiles()?.forEach { child ->
                addFileToZip(zos, child, entryName)
            }
        } else {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    fun isArchiveFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("zip", "rar", "7z", "tar", "gz", "tgz")
    }
}
