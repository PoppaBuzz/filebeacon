package com.jphat.filebeacon

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlinx.coroutines.*

object SearchManager {

    private const val TAG = "SearchManager"
    private const val MAX_FILE_SIZE_FOR_CONTENT_SEARCH = 10 * 1024 * 1024 // 10MB

    data class SearchResult(
        val file: File,
        val matchType: MatchType,
        val snippet: String? = null,
        val lineNumber: Int? = null
    )

    enum class MatchType {
        FILE_NAME,
        FILE_CONTENT
    }

    suspend fun search(
        rootDir: File,
        query: String,
        searchContent: Boolean = true,
        maxResults: Int = 100
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SearchResult>()
        val queryLower = query.lowercase()

        try {
            searchRecursive(rootDir, queryLower, searchContent, results, maxResults)
        } catch (e: Exception) {
            Log.e(TAG, "Search error", e)
        }

        results
    }

    private fun searchRecursive(
        dir: File,
        query: String,
        searchContent: Boolean,
        results: MutableList<SearchResult>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return

        val files = dir.listFiles() ?: return

        for (file in files) {
            if (results.size >= maxResults) break

            try {
                // Check file name
                if (file.name.lowercase().contains(query)) {
                    results.add(
                        SearchResult(
                            file = file,
                            matchType = MatchType.FILE_NAME
                        )
                    )
                }

                // Search in directories recursively
                if (file.isDirectory) {
                    searchRecursive(file, query, searchContent, results, maxResults)
                } else if (searchContent && isTextFile(file) && file.length() < MAX_FILE_SIZE_FOR_CONTENT_SEARCH) {
                    // Search file content
                    searchFileContent(file, query, results)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error searching file: ${file.absolutePath}", e)
            }
        }
    }

    private fun searchFileContent(file: File, query: String, results: MutableList<SearchResult>) {
        try {
            BufferedReader(FileReader(file)).use { reader ->
                var lineNumber = 0
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    lineNumber++
                    val lineLower = line!!.lowercase()

                    if (lineLower.contains(query)) {
                        val snippet = createSnippet(line!!, query)
                        results.add(
                            SearchResult(
                                file = file,
                                matchType = MatchType.FILE_CONTENT,
                                snippet = snippet,
                                lineNumber = lineNumber
                            )
                        )
                        break // Only add first match per file
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore files that can't be read as text
        }
    }

    private fun createSnippet(line: String, query: String, contextLength: Int = 50): String {
        val index = line.lowercase().indexOf(query.lowercase())
        if (index == -1) return line.take(100)

        val start = maxOf(0, index - contextLength)
        val end = minOf(line.length, index + query.length + contextLength)

        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < line.length) "..." else ""

        return prefix + line.substring(start, end).trim() + suffix
    }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt", "log", "md", "json", "xml", "html", "htm", "css", "js",
            "java", "kt", "py", "c", "cpp", "h", "hpp", "cs", "php",
            "rb", "go", "rs", "swift", "sh", "bat", "ps1", "yaml", "yml",
            "ini", "conf", "cfg", "properties", "gradle", "sql"
        )

        val extension = file.extension.lowercase()
        return extension in textExtensions
    }
}
