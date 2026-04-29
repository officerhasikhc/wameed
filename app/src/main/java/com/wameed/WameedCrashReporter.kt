package com.wameed

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.wameed.BuildConfig

/**
 * نظام تتبع الأخطاء والتقارير لتطبيق وميض
 * يستخدم Firebase Crashlytics لتتبع الأعطال تلقائياً
 */
class WameedCrashReporter private constructor() {
    
    private val crashlytics = Firebase.crashlytics
    
    companion object {
        @Volatile
        private var INSTANCE: WameedCrashReporter? = null
        
        fun getInstance(): WameedCrashReporter {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WameedCrashReporter().also { INSTANCE = it }
            }
        }
        
        /**
         * تهيئة نظام تتبع الأخطاء
         */
        fun initialize(context: Context) {
            val instance = getInstance()
            instance.setDeviceInfo(context)
            
            // تفعيل Crashlytics دائماً (debug + release)
            instance.crashlytics.setCrashlyticsCollectionEnabled(true)
            
            // إرسال أي تقارير معلقة
            instance.crashlytics.sendUnsentReports()
            
            // تسجيل حدث بدء التطبيق في Analytics
            Firebase.analytics.logEvent("app_initialized", null)
            
            Log.d("WameedCrashReporter", "Crashlytics initialized - collection enabled")
        }
    }
    
    /**
     * تعيين معلومات الجهاز للتقارير
     */
    private fun setDeviceInfo(context: Context) {
        crashlytics.setCustomKey("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
        crashlytics.setCustomKey("android_version", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        crashlytics.setCustomKey("app_version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        crashlytics.setCustomKey("language", java.util.Locale.getDefault().language)
    }
    
    /**
     * تسجيل خطأ مخصص
     */
    fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            crashlytics.recordException(throwable)
        } else {
            crashlytics.log("ERROR: $message")
        }
    }
    
    /**
     * تسجيل رسالة مخصصة
     */
    fun log(message: String) {
        crashlytics.log(message)
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
        setCustomKey("user_reported_issue", "true")
        setCustomKey("user_description", description)
        if (email.isNotEmpty()) {
            setCustomKey("user_email", email)
        }
        log("User reported issue: $description")
        
        // إرسال Exception فعلي حتى يظهر في Firebase Console
        crashlytics.recordException(Exception("User Report: $description"))
        
        // تسجيل الحدث في Analytics أيضاً
        Firebase.analytics.logEvent("user_bug_report", android.os.Bundle().apply {
            putString("description", description.take(100))
            putString("email", email)
        })
    }
}
