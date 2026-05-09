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
    
    companion object {
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
            instance.setDeviceInfo(context.applicationContext)
            instance.refreshContext(context.applicationContext)
            
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

    /**
     * يربط WameedLogger بـ Firebase: السطور المهمة تصبح breadcrumbs،
     * والأخطاء/فشل الشبكة تتحول إلى non-fatal throttled حتى لا نغرق Crashlytics.
     */
    fun recordLogEntry(level: String, tag: String, message: String) {
        val safeMessage = message.take(900)
        crashlytics.log("[$level][$tag] $safeMessage")
        crashlytics.setCustomKey("last_log_level", level)
        crashlytics.setCustomKey("last_log_tag", tag.take(40))
        crashlytics.setCustomKey("last_log_message", safeMessage.take(180))

        when (level) {
            "ERROR" -> recordNonFatal(
                category = "app_log_error",
                message = "$tag: $safeMessage",
                throttleMs = 60_000L
            )
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
                        throttleMs = 90_000L
                    )
                }
            }
        }
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
        crashlytics.log("NON_FATAL[$category]: $safeMessage")

        logEvent("wameed_nonfatal", Bundle().apply {
            putString("category", category.take(40))
            putString("message", safeMessage.take(100))
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

    private class WameedNonFatalException(message: String) : Exception(message)
    private class UserReportedIssueException(message: String) : Exception("User Report: $message")
}
