package com.wameed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableFloatStateOf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدير التحديث التلقائي لتطبيق وميض عبر GitHub
 */
class WameedUpdateManager private constructor(private val context: Context) {
    
    private val client = OkHttpClient()
    private val crashReporter = WameedCrashReporter.getInstance()
    private val UPDATE_JSON_URL = "https://raw.githubusercontent.com/officerhasikhc/wameed/main/update.json"
    
    private var pendingUpdateUrl: String? = null
    private var pendingReleaseNotes: String? = null
    
    // حالة التحديث
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    companion object {
        @Volatile
        private var INSTANCE: WameedUpdateManager? = null
        
        fun getInstance(context: Context): WameedUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WameedUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * التحقق من وجود تحديثات من GitHub
     * @param isManual إذا كان البحث يدوياً من الإعدادات لإظهار حالة التحميل
     */
    suspend fun checkForUpdates(isManual: Boolean = false): Boolean {
        if (isManual) _updateState.value = UpdateState.Checking
        
        return withContext(Dispatchers.IO) {
            try {
                if (isManual) delay(800) // تأخير بسيط لراحة عين المستخدم
                val request = Request.Builder().url(UPDATE_JSON_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (isManual) {
                            // إذا كان الملف غير موجود (404)، نعتبره "لا يوجد تحديث جديد" بدلاً من "فشل"
                            if (response.code == 404) {
                                _updateState.value = UpdateState.UpToDate
                            } else {
                                _updateState.value = UpdateState.Failed(response.code)
                            }
                            delay(3000)
                            _updateState.value = UpdateState.Idle
                        }
                        return@withContext false
                    }
                    
                    val jsonData = response.body?.string() ?: return@withContext false
                    val jsonObject = JSONObject(jsonData)
                    val androidJson = jsonObject.getJSONObject("android")
                    
                    val remoteVersionCode = androidJson.getInt("versionCode")
                    val updateUrl = androidJson.getString("updateUrl")
                    val releaseNotes = androidJson.getString("releaseNotes")
                    
                    if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                        pendingUpdateUrl = updateUrl
                        pendingReleaseNotes = releaseNotes
                        _updateState.value = UpdateState.Available
                        true
                    } else {
                        _updateState.value = UpdateState.UpToDate
                        // العودة للحالة العادية بعد 3 ثوانٍ إذا كان يدوياً
                        if (isManual) {
                            delay(3000)
                            _updateState.value = UpdateState.Idle
                        }
                        false
                    }
                }
            } catch (e: Exception) {
                crashReporter.logError("Failed to check for GitHub updates", e)
                if (isManual) {
                    _updateState.value = UpdateState.Failed(-1)
                    delay(3000)
                    _updateState.value = UpdateState.Idle
                }
                false
            }
        }
    }

    fun startFlexibleUpdate(activity: Activity) {
        pendingUpdateUrl?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
            // For now, since we just open browser, we can't easily track progress
            // So we might just stay in Available or move to Idle
        }
    }

    fun completeUpdate() {
        // Typically used to restart app after Play Store update
    }

    fun downloadAndInstallUpdate(activity: Activity, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        activity.startActivity(intent)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        // Not needed for GitHub-based updates
    }

    fun cleanup() {
        // Not needed for GitHub-based updates
    }
}

/**
 * حالات التحديث الممكنة
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    object Available : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object Downloaded : UpdateState()
    object Installing : UpdateState()
    object Installed : UpdateState()
    data class Failed(val errorCode: Int) : UpdateState()
}

