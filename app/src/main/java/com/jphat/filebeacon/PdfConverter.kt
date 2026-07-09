package com.jphat.filebeacon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object PdfConverter {

    private const val TAG = "PdfConverter"

    data class ConversionOptions(
        val format: ImageFormat = ImageFormat.PNG,
        val quality: Int = 90, // For JPEG only (0-100)
        val scale: Float = 3.0f, // Resolution multiplier (1.0 = 72 DPI, 2.0 = 144 DPI, 3.0 = 216 DPI)
        val pageRange: PageRange = PageRange.All
    )

    enum class ImageFormat(val extension: String, val mimeType: String) {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg")
    }

    sealed class PageRange {
        object All : PageRange()
        data class Single(val page: Int) : PageRange()
        data class Range(val start: Int, val end: Int) : PageRange()
    }

    data class ConversionResult(
        val success: Boolean,
        val outputFiles: List<File>,
        val message: String,
        val totalPages: Int = 0
    )

    fun convertPdfToImages(
        context: Context,
        pdfFile: File,
        outputDir: File,
        options: ConversionOptions = ConversionOptions()
    ): ConversionResult {
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            return ConversionResult(
                success = false,
                outputFiles = emptyList(),
                message = "PDF file not found or cannot be read"
            )
        }

        return try {
            outputDir.mkdirs()

            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            val pdfRenderer = PdfRenderer(fileDescriptor)
            val totalPages = pdfRenderer.pageCount
            val outputFiles = mutableListOf<File>()

            val pagesToConvert = when (options.pageRange) {
                is PageRange.All -> 0 until totalPages
                is PageRange.Single -> listOf(options.pageRange.page.coerceIn(0, totalPages - 1))
                is PageRange.Range -> options.pageRange.start.coerceIn(0, totalPages - 1)..
                        options.pageRange.end.coerceIn(0, totalPages - 1)
            }

            for (pageIndex in pagesToConvert) {
                val page = pdfRenderer.openPage(pageIndex)

                // Calculate dimensions with scale
                val width = (page.width * options.scale).toInt()
                val height = (page.height * options.scale).toInt()

                // Validate dimensions to prevent OutOfMemoryError
                val maxDimension = 4096 // Maximum 4K resolution
                val estimatedMemory = width * height * 4L // 4 bytes per pixel (ARGB)
                val maxMemory = 100 * 1024 * 1024L // 100 MB per bitmap

                if (width > maxDimension || height > maxDimension) {
                    page.close()
                    pdfRenderer.close()
                    fileDescriptor.close()
                    return ConversionResult(
                        success = false,
                        outputFiles = emptyList(),
                        message = "Page dimensions too large: ${width}x${height}. Max: ${maxDimension}x${maxDimension}"
                    )
                }

                if (estimatedMemory > maxMemory) {
                    page.close()
                    pdfRenderer.close()
                    fileDescriptor.close()
                    return ConversionResult(
                        success = false,
                        outputFiles = emptyList(),
                        message = "Estimated memory usage too high: ${estimatedMemory / 1024 / 1024}MB. Max: ${maxMemory / 1024 / 1024}MB"
                    )
                }

                // Create bitmap
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                // Render page to bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Save bitmap to file
                val baseName = pdfFile.nameWithoutExtension
                val outputFile = File(
                    outputDir,
                    "${baseName}_page_${pageIndex + 1}.${options.format.extension}"
                )

                FileOutputStream(outputFile).use { out ->
                    when (options.format) {
                        ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        ImageFormat.JPEG -> bitmap.compress(
                            Bitmap.CompressFormat.JPEG,
                            options.quality,
                            out
                        )
                    }
                }

                outputFiles.add(outputFile)
                bitmap.recycle()
                page.close()

                Log.d(TAG, "Converted page ${pageIndex + 1}/$totalPages to ${outputFile.name}")
            }

            pdfRenderer.close()
            fileDescriptor.close()

            ConversionResult(
                success = true,
                outputFiles = outputFiles,
                message = "Successfully converted ${outputFiles.size} page(s)",
                totalPages = totalPages
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error converting PDF to images", e)
            ConversionResult(
                success = false,
                outputFiles = emptyList(),
                message = "Conversion failed: ${e.message}"
            )
        }
    }

    fun convertSinglePage(
        context: Context,
        pdfFile: File,
        pageNumber: Int,
        outputFile: File,
        options: ConversionOptions = ConversionOptions()
    ): Boolean {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            val pdfRenderer = PdfRenderer(fileDescriptor)

            if (pageNumber < 0 || pageNumber >= pdfRenderer.pageCount) {
                pdfRenderer.close()
                fileDescriptor.close()
                return false
            }

            val page = pdfRenderer.openPage(pageNumber)
            val width = (page.width * options.scale).toInt()
            val height = (page.height * options.scale).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                when (options.format) {
                    ImageFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    ImageFormat.JPEG -> bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        options.quality,
                        out
                    )
                }
            }

            bitmap.recycle()
            page.close()
            pdfRenderer.close()
            fileDescriptor.close()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting single page", e)
            false
        }
    }

    fun getPdfInfo(pdfFile: File): PdfInfo? {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            val pdfRenderer = PdfRenderer(fileDescriptor)
            val info = PdfInfo(
                pageCount = pdfRenderer.pageCount,
                pages = (0 until pdfRenderer.pageCount).map { index ->
                    val page = pdfRenderer.openPage(index)
                    val pageInfo = PageInfo(
                        index = index,
                        width = page.width,
                        height = page.height
                    )
                    page.close()
                    pageInfo
                }
            )

            pdfRenderer.close()
            fileDescriptor.close()

            info
        } catch (e: Exception) {
            Log.e(TAG, "Error getting PDF info", e)
            null
        }
    }

    data class PdfInfo(
        val pageCount: Int,
        val pages: List<PageInfo>
    )

    data class PageInfo(
        val index: Int,
        val width: Int,
        val height: Int
    )
}
