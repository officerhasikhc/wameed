package com.wameed

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * مسئول عن حفظ الملفات المستلمة في مكان دائم (Downloads / MediaStore).
 */
object FileSaver {
    private const val TAG = "FileSaver"
    private const val BUFFER_SIZE = 512 * 1024

    data class PendingDownload(
        val uri: Uri?,
        val filename: String,
        private val outputStream: BufferedOutputStream,
        private val onFinish: (success: Boolean) -> Unit
    ) {
        fun write(data: ByteArray) {
            outputStream.write(data)
        }

        fun write(data: ByteArray, offset: Int, length: Int) {
            outputStream.write(data, offset, length)
        }

        fun flush() {
            outputStream.flush()
        }

        fun close() {
            outputStream.close()
        }

        fun finish(success: Boolean) {
            onFinish(success)
        }
    }

    fun openDownloadStream(
        context: Context,
        originalName: String,
        mimeType: String? = null,
        expectedSize: Long = 0L
    ): PendingDownload? {
        val safeName = sanitizeFilename(originalName)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                openMediaStoreStream(context, safeName, mimeType, expectedSize)
            } else {
                openLegacyStream(context, safeName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening download stream", e)
            null
        }
    }

    fun saveFileToDownloads(context: Context, tempFile: File, originalName: String): Uri? {
        return try {
            val pending = openDownloadStream(context, originalName, expectedSize = tempFile.length()) ?: return null
            var success = false
            try {
                FileInputStream(tempFile).use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read <= 0) break
                        pending.write(buffer, 0, read)
                    }
                }
                pending.flush()
                pending.close()
                success = true
                pending.finish(true)
                tempFile.delete()
                pending.uri
            } finally {
                if (!success) {
                    try {
                        pending.close()
                    } catch (_: Exception) {}
                    pending.finish(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openMediaStoreStream(
        context: Context,
        name: String,
        mimeType: String?,
        expectedSize: Long
    ): PendingDownload? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Wameed")
            if (!mimeType.isNullOrBlank()) {
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }
            if (expectedSize > 0) {
                put(MediaStore.MediaColumns.SIZE, expectedSize)
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
        val outputStream = resolver.openOutputStream(uri, "w") ?: run {
            resolver.delete(uri, null, null)
            return null
        }

        return PendingDownload(
            uri = uri,
            filename = name,
            outputStream = BufferedOutputStream(outputStream, BUFFER_SIZE)
        ) { success ->
            try {
                if (success) {
                    val updateValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, updateValues, null, null)
                    Log.i(TAG, "File saved to MediaStore: $name")
                } else {
                    resolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to finalize MediaStore file", e)
                if (!success) {
                    resolver.delete(uri, null, null)
                }
            }
        }
    }

    private fun openLegacyStream(context: Context, name: String): PendingDownload? {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Wameed")
        if (!publicDir.exists() && !publicDir.mkdirs()) {
            return null
        }

        val destFile = uniqueFile(publicDir, name)
        val outputStream: OutputStream = FileOutputStream(destFile)
        return PendingDownload(
            uri = Uri.fromFile(destFile),
            filename = destFile.name,
            outputStream = BufferedOutputStream(outputStream, BUFFER_SIZE)
        ) { success ->
            if (!success) {
                destFile.delete()
                return@PendingDownload
            }
            Log.i(TAG, "File saved legacy: ${destFile.absolutePath}")
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                null,
                null
            )
        }
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\r\\n\\t]"), "_")
            .trim()
        return cleaned.ifBlank { "received_file" }
    }

    private fun uniqueFile(directory: File, name: String): File {
        val baseName = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = File(directory, name)
        var index = 1
        while (candidate.exists()) {
            val suffix = if (ext.isBlank()) "_$index" else "_$index.$ext"
            candidate = File(directory, "$baseName$suffix")
            index++
        }
        return candidate
    }
}
