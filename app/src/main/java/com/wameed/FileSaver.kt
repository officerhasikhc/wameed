package com.wameed

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * مسئول عن حفظ الملفات المستلمة في مكان دائم (Downloads / MediaStore).
 */
object FileSaver {
    private const val TAG = "FileSaver"

    fun saveFileToDownloads(context: Context, tempFile: File, originalName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(context, tempFile, originalName)
            } else {
                saveLegacy(context, tempFile, originalName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(context: Context, tempFile: File, name: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Wameed")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        return uri?.also {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                // تحديث القيم لضمان الظهور في المعرض
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(it, contentValues, null, null)

                tempFile.delete()
                Log.i(TAG, "File saved to MediaStore: $name")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to MediaStore", e)
                resolver.delete(it, null, null)
            }
        }
    }

    private fun saveLegacy(context: Context, tempFile: File, name: String): Uri? {
        return try {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Wameed")
            if (!publicDir.exists()) publicDir.mkdirs()

            val destFile = File(publicDir, name)
            FileInputStream(tempFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
            Log.i(TAG, "File saved legacy: ${destFile.absolutePath}")
            
            // تحديث MediaScanner
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                null,
                null
            )

            Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save legacy", e)
            null
        }
    }
}
