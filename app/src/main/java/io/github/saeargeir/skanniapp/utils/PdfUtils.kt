package io.github.saeargeir.skanniapp.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log

object PdfUtils {
    /**
     * Save simple text content as a single-page PDF into Downloads/SkanniApp and return its Uri.
     */
    fun saveTextAsPdf(context: Context, title: String, text: String): Uri? {
        return try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4-ish in points
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Basic text rendering
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }

            var y = 40f
            canvas.drawText(title.ifBlank { "SkanniApp PDF" }, 40f, y, paint)
            y += 24f

            val lines = text.lines().ifEmpty { listOf("Enginn texti") }
            for (line in lines) {
                // Simple wrapping: draw line by chunks of ~90 chars
                val chunks = line.chunked(90)
                for (chunk in chunks) {
                    if (y > pageInfo.pageHeight - 40) break
                    canvas.drawText(chunk, 40f, y, paint)
                    y += 18f
                }
                if (y > pageInfo.pageHeight - 40) break
            }

            doc.finishPage(page)

            val displayName = (title.ifBlank { "Skanni" }) + "_" + System.currentTimeMillis() + ".pdf"
            val resolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SkanniApp")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(collection, values)
            if (uri == null) {
                doc.close()
                Log.e("PdfUtils", "Failed to insert PDF into MediaStore")
                return null
            }

            resolver.openOutputStream(uri)?.use { out ->
                doc.writeTo(out)
            }
            doc.close()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pendingClear = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, pendingClear, null, null)
            }

            uri
        } catch (e: Exception) {
            Log.e("PdfUtils", "Error saving PDF", e)
            null
        }
    }
}
