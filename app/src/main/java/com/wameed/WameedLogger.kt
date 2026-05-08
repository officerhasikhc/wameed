package com.wameed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * سجل تشخيص مدمج لتطبيق وميض
 * - ring buffer في الذاكرة (500 سطر)
 * - كتابة متزامنة لملف wameed_log.txt
 * - تصدير ومشاركة عبر FileProvider
 */
object WameedLogger {

    private const val TAG = "WameedLogger"
    private const val MAX_LINES = 500
    private const val LOG_FILENAME = "wameed_log.txt"

    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    // Listeners for live UI updates
    private val listeners = mutableListOf<() -> Unit>()

    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            return "[$time] [${level.name}] [$tag] $message"
        }

        fun formatForFile(): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            return "[$time] [${level.name}] [$tag] $message"
        }
    }

    enum class Level { DEBUG, INFO, WARN, ERROR, NET }

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILENAME)
        i("WameedLogger", "سجل التشخيص جاهز — ${logFile?.absolutePath}")
    }

    fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = log(Level.ERROR, tag, msg)
    fun net(tag: String, msg: String) = log(Level.NET, tag, msg)

    private fun log(level: Level, tag: String, msg: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, msg)

        // Add to ring buffer
        buffer.addLast(entry)
        while (buffer.size > MAX_LINES) {
            buffer.pollFirst()
        }

        // Write to file (best effort, non-blocking)
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.appendLine(entry.formatForFile())
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to write log: ${ex.message}")
        }

        // Also log to standard Android logcat
        when (level) {
            Level.DEBUG -> Log.d(tag, msg)
            Level.INFO -> Log.i(tag, msg)
            Level.WARN -> Log.w(tag, msg)
            Level.ERROR -> Log.e(tag, msg)
            Level.NET -> Log.d(tag, "[NET] $msg")
        }

        // Remote breadcrumbs for developer diagnostics. Best effort only:
        // local logging must keep working even if Firebase is unavailable.
        try {
            if (WameedCrashReporter.isInitialized()) {
                WameedCrashReporter.getInstance().recordLogEntry(level.name, tag, msg)
            }
        } catch (ex: Exception) {
            Log.w(TAG, "Failed to forward log to Crashlytics: ${ex.message}")
        }

        // Notify listeners
        listeners.forEach { it.invoke() }
    }

    fun getEntries(): List<LogEntry> = buffer.toList()

    fun getEntries(filter: Level?): List<LogEntry> {
        return if (filter == null) buffer.toList()
        else buffer.filter { it.level == filter }.toList()
    }

    fun clear() {
        buffer.clear()
        try {
            logFile?.writeText("")
        } catch (_: Exception) {}
        listeners.forEach { it.invoke() }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /**
     * تصدير السجل كملف قابل للمشاركة عبر FileProvider
     */
    fun exportLogUri(context: Context): Uri? {
        return try {
            val file = logFile ?: return null
            if (!file.exists() || file.length() == 0L) {
                // Write current buffer to file first
                FileWriter(file, false).use { writer ->
                    buffer.forEach { entry ->
                        writer.appendLine(entry.formatForFile())
                    }
                }
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export log: ${e.message}")
            null
        }
    }

    /**
     * مشاركة السجل عبر intent (واتساب، تلغرام، إلخ)
     */
    fun shareLog(context: Context) {
        val uri = exportLogUri(context) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Wameed Diagnostic Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة سجل التشخيص"))
    }

    /**
     * الحصول على ملخص تشخيصي سريع
     */
    fun getDiagnosticSummary(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== Wameed Diagnostic Summary ===")
        sb.appendLine("Time: ${fileDateFormat.format(Date())}")
        sb.appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine("PC IP: ${WameedPrefs.getPcIp(context)}")
        sb.appendLine("PC Port: ${WameedPrefs.getPcPort(context)}")
        sb.appendLine("Keep-alive: ${WameedPrefs.isKeepAliveEnabled(context)}")
        sb.appendLine("Service running: ${WameedConnectionService.isRunning}")
        sb.appendLine("Log entries: ${buffer.size}")
        val errors = buffer.count { it.level == Level.ERROR }
        val warns = buffer.count { it.level == Level.WARN }
        sb.appendLine("Errors: $errors, Warnings: $warns")
        sb.appendLine("================================")
        return sb.toString()
    }
}
