package com.wameed

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.wameed.BuildConfig
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * نظام تتبع الأخطاء والتقارير لتطبيق وميض
 * يستخدم Firebase Crashlytics لتتبع الأعطال تلقائياً
 */
class WameedCrashReporter private constructor() {
    
    private val crashlytics = Firebase.crashlytics
    private val lastRecordedAt = ConcurrentHashMap<String, Long>()
    private val softSignalCounts = ConcurrentHashMap<String, Int>()
    @Volatile
    private var uncaughtHandlerInstalled = false
    
    companion object {
        private const val CRASH_PREFS = "wameed_crash_reporting"
        private const val KEY_PENDING_CRASH = "pending_crash"
        private const val KEY_CRASH_TYPE = "crash_type"
        private const val KEY_CRASH_MESSAGE = "crash_message"
        private const val KEY_CRASH_THREAD = "crash_thread"
        private const val KEY_CRASH_TIME = "crash_time"

        @Volatile
        private var INSTANCE: WameedCrashReporter? = null
        @Volatile
        private var initialized = false
        
        fun getInstance(): WameedCrashReporter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WameedCrashReporter().also { INSTANCE = it }
            }
        }

        fun isInitialized(): Boolean = initialized
        
        /**
         * تهيئة نظام تتبع الأخطاء
         */
        fun initialize(context: Context) {
            val instance = getInstance()
            val appContext = context.applicationContext
            instance.setDeviceInfo(appContext)
            instance.refreshContext(appContext)
            instance.installUncaughtHandler(appContext)
            
            if (!initialized) {
                // تفعيل Crashlytics دائماً (debug + release)
                instance.crashlytics.setCrashlyticsCollectionEnabled(true)
                
                // إرسال أي تقارير معلقة
                instance.crashlytics.sendUnsentReports()
                
                // تسجيل حدث بدء التطبيق في Analytics
                Firebase.analytics.logEvent("app_initialized", null)
                initialized = true
                
                Log.d("WameedCrashReporter", "Crashlytics initialized - collection enabled")
            }
        }
    }
    
    /**
     * تعيين معلومات الجهاز للتقارير
     */
    private fun setDeviceInfo(context: Context) {
        crashlytics.setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        crashlytics.setCustomKey("android_version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        crashlytics.setCustomKey("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        crashlytics.setCustomKey("language", Locale.getDefault().language)
        crashlytics.setUserId(hashInstallId(context))
    }

    /**
     * تحديث سياق الاتصال الحالي حتى يظهر مع أي عطل أو non-fatal لاحق.
     */
    fun refreshContext(context: Context) {
        crashlytics.setCustomKey("pc_configured", WameedPrefs.isConfigured(context).toString())
        crashlytics.setCustomKey("pc_port", WameedPrefs.getPcPort(context).toString())
        crashlytics.setCustomKey("pc_ip_group", maskPrivateIp(WameedPrefs.getPcIp(context)))
        crashlytics.setCustomKey("pc_name", WameedPrefs.getPcName(context).take(80))
        crashlytics.setCustomKey("keep_alive_enabled", WameedPrefs.isKeepAliveEnabled(context).toString())
        crashlytics.setCustomKey("receiver_service_running", WameedConnectionService.isRunning.toString())
        crashlytics.setCustomKey("phone_receiver_ready", WameedConnectionService.isReceiverReady.toString())
    }
    
    /**
     * تسجيل خطأ مخصص
     */
    fun logError(message: String, throwable: Throwable? = null) {
        crashlytics.log("ERROR: ${message.take(900)}")
        if (throwable != null) {
            crashlytics.recordException(throwable)
        } else {
            recordNonFatal("app_error", message)
        }
    }
    
    /**
     * تسجيل رسالة مخصصة
     */
    fun log(message: String) {
        crashlytics.log(message.take(900))
    }

    data class PendingCrashReport(
        val type: String,
        val message: String,
        val thread: String,
        val timestamp: Long
    )

    fun consumePendingCrashReport(context: Context): PendingCrashReport? {
        val prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING_CRASH, false)) return null

        val report = PendingCrashReport(
            type = prefs.getString(KEY_CRASH_TYPE, "Unknown") ?: "Unknown",
            message = prefs.getString(KEY_CRASH_MESSAGE, "") ?: "",
            thread = prefs.getString(KEY_CRASH_THREAD, "") ?: "",
            timestamp = prefs.getLong(KEY_CRASH_TIME, 0L)
        )

        prefs.edit()
            .putBoolean(KEY_PENDING_CRASH, false)
            .apply()

        return report
    }

    fun setTransferDiagnostics(
        direction: String,
        phase: String,
        sizeBytes: Long,
        bytesDone: Long,
        port: Int,
        failureType: String = ""
    ) {
        crashlytics.setCustomKey("transfer_direction", direction.take(32))
        crashlytics.setCustomKey("transfer_phase", phase.take(32))
        crashlytics.setCustomKey("transfer_size_bucket", sizeBucket(sizeBytes))
        crashlytics.setCustomKey("transfer_bytes_done_mb", (bytesDone / (1024 * 1024)).toString())
        crashlytics.setCustomKey("transfer_port", port.toString())
        if (failureType.isNotBlank()) {
            crashlytics.setCustomKey("transfer_failure_type", failureType.take(40))
        }
    }

    /**
     * يربط WameedLogger بـ Firebase: السطور المهمة تصبح breadcrumbs،
     * والأخطاء/فشل الشبكة تتحول إلى non-fatal throttled حتى لا نغرق Crashlytics.
     */
    fun recordLogEntry(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val safeMessage = message.take(900)
        crashlytics.log("[$level][$tag] $safeMessage")
        crashlytics.setCustomKey("last_log_level", level)
        crashlytics.setCustomKey("last_log_tag", tag.take(40))
        crashlytics.setCustomKey("last_log_message", safeMessage.take(180))

        when (level) {
            "ERROR" -> recordClassifiedLogFailure(tag, safeMessage, throwable)
            "WARN" -> logEvent("wameed_warning", Bundle().apply {
                putString("tag", tag.take(40))
                putString("message", safeMessage.take(100))
            })
            "NET" -> {
                if (safeMessage.contains("fail", ignoreCase = true) ||
                    safeMessage.contains("failure", ignoreCase = true) ||
                    safeMessage.contains("timeout", ignoreCase = true) ||
                    safeMessage.contains("فشل") ||
                    safeMessage.contains("انتهت المهلة")) {
                    recordNonFatal(
                        category = "network_log_failure",
                        message = "$tag: $safeMessage",
                        throwable = throwable,
                        throttleMs = 90_000L
                    )
                }
            }
        }
    }

    private fun recordClassifiedLogFailure(tag: String, message: String, throwable: Throwable?) {
        val category = classifyFailure(tag, message)
        val throttleMs = when (category) {
            "persistent_ws_disconnect" -> 10 * 60_000L
            "pairing_timeout" -> 2 * 60_000L
            "firewall_suspected" -> 5 * 60_000L
            else -> 60_000L
        }

        if (category == "persistent_ws_disconnect" && !isActiveTransferFailure(message)) {
            logEvent("persistent_ws_disconnect", Bundle().apply {
                putString("tag", tag.take(40))
                putString("throwable", throwable?.javaClass?.simpleName?.take(40) ?: "none")
            })
            val countKey = "$category:$tag:${throwable?.javaClass?.name.orEmpty()}:${message.take(80)}"
            val count = softSignalCounts.merge(countKey, 1) { old, one -> old + one } ?: 1
            crashlytics.setCustomKey("persistent_ws_soft_count", count.toString())
            if (count < 5 || count % 5 != 0) return
        }

        recordNonFatal(
            category = category,
            message = "$tag: $message",
            throwable = throwable,
            throttleMs = throttleMs
        )
    }

    private fun classifyFailure(tag: String, message: String): String {
        val text = "$tag $message".lowercase(Locale.US)
        return when {
            text.contains("pairing") || text.contains("اقتران") -> "pairing_timeout"
            text.contains("firewall") || text.contains("جدار") || text.contains("محجوب") -> "firewall_suspected"
            text.contains("update") || text.contains("تحديث") || text.contains("install") -> "update_failed"
            text.contains("persistent ws") || text.contains("ws failure") || text.contains("websocket") -> "persistent_ws_disconnect"
            text.contains("send") || text.contains("إرسال") || text.contains("ارسال") -> "send_failed"
            else -> "app_log_error"
        }
    }

    private fun isActiveTransferFailure(message: String): Boolean {
        return message.contains("send", ignoreCase = true) ||
            message.contains("إرسال") ||
            message.contains("ارسال") ||
            message.contains("file", ignoreCase = true) ||
            message.contains("ملف")
    }

    fun recordNonFatal(
        category: String,
        message: String,
        throwable: Throwable? = null,
        throttleMs: Long = 60_000L
    ) {
        val key = "$category:${message.take(160)}:${throwable?.javaClass?.name.orEmpty()}"
        val now = System.currentTimeMillis()
        val last = lastRecordedAt[key] ?: 0L
        if (now - last < throttleMs) return
        lastRecordedAt[key] = now

        val safeMessage = message.take(900)
        crashlytics.setCustomKey("last_nonfatal_category", category.take(40))
        crashlytics.setCustomKey("last_nonfatal_message", safeMessage.take(180))
        crashlytics.setCustomKey("last_nonfatal_throwable", throwable?.javaClass?.simpleName?.take(80) ?: "none")
        crashlytics.log("NON_FATAL[$category]: $safeMessage")

        logEvent("wameed_nonfatal", Bundle().apply {
            putString("category", category.take(40))
            putString("message", safeMessage.take(100))
            putString("throwable", throwable?.javaClass?.simpleName?.take(40) ?: "none")
        })

        crashlytics.recordException(
            throwable ?: WameedNonFatalException("$category: $safeMessage")
        )
    }

    fun logEvent(name: String, params: Bundle? = null) {
        Firebase.analytics.logEvent(name, params)
    }

    fun attachRecentLogs(limit: Int = 80) {
        WameedLogger.getEntries().takeLast(limit).forEach { entry ->
            crashlytics.log(entry.formatForFile().take(900))
        }
    }
    
    /**
     * تعيين معرف المستخدم
     */
    fun setUserId(userId: String) {
        crashlytics.setUserId(userId)
    }
    
    /**
     * إضافة مفتاح مخصص للتقارير
     */
    fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
    
    /**
     * إبلاغ المستخدم عن مشكلة
     */
    fun reportUserIssue(context: Context, description: String, email: String = "") {
        refreshContext(context)
        setCustomKey("user_reported_issue", "true")
        setCustomKey("user_description", description.take(180))
        if (email.isNotEmpty()) {
            setCustomKey("user_email", email.take(80))
        }
        attachRecentLogs()
        log("User reported issue: ${description.take(900)}")
        
        // إرسال Exception فعلي حتى يظهر في Firebase Console
        crashlytics.recordException(UserReportedIssueException(description.take(900)))
        
        // تسجيل الحدث في Analytics أيضاً
        Firebase.analytics.logEvent("user_bug_report", Bundle().apply {
            putString("description", description.take(100))
            putString("email", email.take(80))
        })
    }

    private fun hashInstallId(context: Context): String {
        val raw = WameedPrefs.getOrCreateDeviceId(context)
        return sha256(raw).take(16)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun maskPrivateIp(ip: String): String {
        if (ip.isBlank()) return "not_configured"
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.x" else "non_ipv4"
    }

    private fun sizeBucket(bytes: Long): String {
        if (bytes <= 0) return "unknown"
        val mb = bytes / (1024 * 1024)
        return when {
            mb < 10 -> "lt_10mb"
            mb < 100 -> "10_100mb"
            mb < 1024 -> "100mb_1gb"
            mb < 4096 -> "1_4gb"
            else -> "gt_4gb"
        }
    }

    private fun installUncaughtHandler(context: Context) {
        if (uncaughtHandlerInstalled) return
        synchronized(this) {
            if (uncaughtHandlerInstalled) return
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    refreshContext(context)
                    crashlytics.setCustomKey("fatal_thread", thread.name.take(80))
                    crashlytics.setCustomKey("fatal_type", throwable.javaClass.simpleName.take(80))
                    crashlytics.setCustomKey("fatal_message", (throwable.message ?: "").take(180))
                    crashlytics.log("FATAL[${thread.name}]: ${throwable.javaClass.simpleName}: ${(throwable.message ?: "").take(500)}")
                    attachRecentLogs()

                    context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_PENDING_CRASH, true)
                        .putString(KEY_CRASH_TYPE, throwable.javaClass.simpleName.take(80))
                        .putString(KEY_CRASH_MESSAGE, (throwable.message ?: "").take(240))
                        .putString(KEY_CRASH_THREAD, thread.name.take(80))
                        .putLong(KEY_CRASH_TIME, System.currentTimeMillis())
                        .commit()
                } catch (_: Exception) {
                } finally {
                    if (previousHandler != null) {
                        previousHandler.uncaughtException(thread, throwable)
                    } else {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(10)
                    }
                }
            }
            uncaughtHandlerInstalled = true
        }
    }

    private class WameedNonFatalException(message: String) : Exception(message)
    private class UserReportedIssueException(message: String) : Exception("User Report: $message")
}
