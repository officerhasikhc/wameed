package com.wameed

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableFloatStateOf
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.ActivityResult.RESULT_IN_APP_UPDATE_FAILED
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * مدير التحديث التلقائي لتطبيق وميض
 * يستخدم Google Play In-App Update API للتحديثات الاختيارية
 */
class WameedUpdateManager private constructor(context: Context) {
    
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val crashReporter = WameedCrashReporter.getInstance()
    
    // حالة التحديث
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    
    // تقدم التحديث
    val updateProgress = mutableFloatStateOf(0f)
    
    companion object {
        private const val UPDATE_REQUEST_CODE = 1234
        
        @Volatile
        private var INSTANCE: WameedUpdateManager? = null
        
        fun getInstance(context: Context): WameedUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WameedUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * مراقب حالة التثبيت
     */
    private val installStateUpdatedListener = object : InstallStateUpdatedListener {
        override fun onStateUpdate(state: InstallState) {
            when (state.installStatus()) {
                InstallStatus.DOWNLOADING -> {
                    val progress = state.bytesDownloaded().toFloat() / state.totalBytesToDownload().toFloat()
                    updateProgress.floatValue = progress
                    _updateState.value = UpdateState.Downloading(progress)
                    crashReporter.log("Update downloading: ${(progress * 100).toInt()}%")
                }
                InstallStatus.DOWNLOADED -> {
                    _updateState.value = UpdateState.Downloaded
                    crashReporter.log("Update downloaded, waiting for completion")
                }
                InstallStatus.INSTALLING -> {
                    _updateState.value = UpdateState.Installing
                    crashReporter.log("Update installing...")
                }
                InstallStatus.INSTALLED -> {
                    _updateState.value = UpdateState.Installed
                    crashReporter.log("Update installed successfully")
                    appUpdateManager.unregisterListener(this)
                }
                InstallStatus.FAILED -> {
                    _updateState.value = UpdateState.Failed(state.installErrorCode())
                    crashReporter.logError("Update failed with code: ${state.installErrorCode()}")
                    appUpdateManager.unregisterListener(this)
                }
                InstallStatus.CANCELED -> {
                    _updateState.value = UpdateState.Canceled
                    crashReporter.log("Update canceled by user")
                    appUpdateManager.unregisterListener(this)
                }
                else -> {
                    // حالات أخرى مثل PENDING, DOWNLOADED
                    crashReporter.log("Update status: ${state.installStatus()}")
                }
            }
        }
    }
    
    /**
     * التحقق من وجود تحديثات
     */
    suspend fun checkForUpdates(): Boolean {
        return try {
            val appUpdateInfo = appUpdateManager.appUpdateInfo.await()
            val isUpdateAvailable = appUpdateInfo.availableVersionCode() > BuildConfig.VERSION_CODE
            
            if (isUpdateAvailable) {
                _updateState.value = UpdateState.Available(appUpdateInfo)
                crashReporter.log("Update available: ${appUpdateInfo.availableVersionCode()}")
                true
            } else {
                _updateState.value = UpdateState.UpToDate
                crashReporter.log("App is up to date")
                false
            }
        } catch (e: Exception) {
            crashReporter.logError("Failed to check for updates", e)
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
            false
        }
    }
    
    /**
     * بدء التحديث المرن (اختياري)
     */
    fun startFlexibleUpdate(activity: Activity) {
        val currentState = _updateState.value
        if (currentState !is UpdateState.Available) {
            return
        }
        
        try {
            appUpdateManager.registerListener(installStateUpdatedListener)
            
            appUpdateManager.startUpdateFlowForResult(
                currentState.appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
            
            _updateState.value = UpdateState.Downloading(0f)
            crashReporter.log("Flexible update started")
        } catch (e: Exception) {
            crashReporter.logError("Failed to start flexible update", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to start update")
        }
    }
    
    /**
     * بدء التحديث الفوري (إلزامي)
     */
    fun startImmediateUpdate(activity: Activity) {
        val currentState = _updateState.value
        if (currentState !is UpdateState.Available) {
            return
        }
        
        try {
            appUpdateManager.startUpdateFlowForResult(
                currentState.appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
            
            _updateState.value = UpdateState.Updating
            crashReporter.log("Immediate update started")
        } catch (e: Exception) {
            crashReporter.logError("Failed to start immediate update", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to start update")
        }
    }
    
    /**
     * إكمال التحديث (للتحديث المرن)
     */
    fun completeUpdate() {
        try {
            appUpdateManager.completeUpdate()
            crashReporter.log("Update completion triggered")
        } catch (e: Exception) {
            crashReporter.logError("Failed to complete update", e)
        }
    }
    
    /**
     * معالجة نتيجة طلب التحديث
     */
    fun handleActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (requestCode == UPDATE_REQUEST_CODE) {
            return when (resultCode) {
                Activity.RESULT_OK -> {
                    crashReporter.log("Update flow accepted by user")
                    true
                }
                Activity.RESULT_CANCELED -> {
                    _updateState.value = UpdateState.Canceled
                    crashReporter.log("Update flow canceled by user")
                    true
                }
                RESULT_IN_APP_UPDATE_FAILED -> {
                    _updateState.value = UpdateState.Failed(-1)
                    crashReporter.logError("In-app update failed")
                    true
                }
                else -> false
            }
        }
        return false
    }
    
    /**
     * تنظيف الموارد
     */
    fun cleanup() {
        try {
            appUpdateManager.unregisterListener(installStateUpdatedListener)
        } catch (e: Exception) {
            crashReporter.logError("Failed to cleanup update manager", e)
        }
    }
}

/**
 * حالات التحديث الممكنة
 */
sealed class UpdateState {
    object Idle : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val appUpdateInfo: AppUpdateInfo) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    object Installing : UpdateState()
    object Installed : UpdateState()
    object Downloaded : UpdateState()
    object Updating : UpdateState()
    object Canceled : UpdateState()
    data class Failed(val errorCode: Int) : UpdateState()
    data class Error(val message: String) : UpdateState()
}
