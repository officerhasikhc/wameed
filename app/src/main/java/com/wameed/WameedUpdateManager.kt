package com.wameed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.perf.FirebasePerformance
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * مدير التحديث التلقائي لتطبيق وميض عبر GitHub
 * - يتحقق من update.json على GitHub
 * - ينزل الـ APK داخلياً مع تقدم حقيقي
 * - يثبته عبر FileProvider + PackageInstaller
 */
class WameedUpdateManager private constructor(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val crashReporter = WameedCrashReporter.getInstance()
    private val UPDATE_JSON_URL = "https://raw.githubusercontent.com/officerhasikhc/wameed/main/update.json"

    private var pendingUpdateUrl: String? = null
    private var pendingReleaseNotes: String? = null
    private var pendingRemoteVersionCode: Long = 0L
    private var pendingRemoteVersionName: String = ""
    @Volatile private var isChecking = false
    private val installPrefs by lazy {
        context.getSharedPreferences("wameed_update_install", Context.MODE_PRIVATE)
    }

    // حالة التحديث
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    companion object {
        private const val TAG = "WameedUpdate"
        private const val APK_FILE_NAME = "wameed-update.apk"
        private const val KEY_EXPECTED_CODE = "expected_code"
        private const val KEY_EXPECTED_NAME = "expected_name"
        private const val KEY_UPDATE_URL = "update_url"
        private const val KEY_LAUNCHED_AT = "launched_at"

        @Volatile
        private var INSTANCE: WameedUpdateManager? = null

        fun getInstance(context: Context): WameedUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WameedUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private fun updateRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Wameed-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .build()
    }

    /**
     * التحقق من وجود تحديثات من GitHub
     * @param isManual إذا كان البحث يدوياً من الإعدادات لإظهار حالة التحميل
     */
    suspend fun checkForUpdates(isManual: Boolean = false): Boolean {
        Log.w(TAG, "▶ checkForUpdates(isManual=$isManual) started")
        if (isChecking) {
            Log.w(TAG, "▶ Already checking — skipped")
            return false
        }
        isChecking = true
        if (isManual) _updateState.value = UpdateState.Checking
        val trace = FirebasePerformance.getInstance().newTrace("wameed_update_check").apply {
            putAttribute("app_version", BuildConfig.VERSION_NAME)
            putAttribute("manual", isManual.toString())
            start()
        }

        return withContext(Dispatchers.IO) {
            try {
                if (isManual) delay(800)
                Log.w(TAG, "▶ Fetching: $UPDATE_JSON_URL")
                val request = updateRequest(UPDATE_JSON_URL)
                client.newCall(request).execute().use { response ->
                    Log.w(TAG, "▶ Response code: ${response.code}")
                    if (!response.isSuccessful) {
                        Log.e(TAG, "✗ HTTP error: ${response.code} ${response.message}")
                        if (isManual) {
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
                    val remoteVersionName = androidJson.optString("versionName", "")
                    val updateUrl = androidJson.getString("updateUrl")
                    val releaseNotes = androidJson.getString("releaseNotes")

                    Log.w(TAG, "══════════════════════════════════")
                    Log.w(TAG, "LOCAL  versionCode = ${BuildConfig.VERSION_CODE}")
                    Log.w(TAG, "LOCAL  versionName = ${BuildConfig.VERSION_NAME}")
                    Log.w(TAG, "REMOTE versionCode = $remoteVersionCode")
                    Log.w(TAG, "REMOTE updateUrl   = $updateUrl")
                    Log.w(TAG, "RESULT: update available = ${remoteVersionCode > BuildConfig.VERSION_CODE}")
                    Log.w(TAG, "══════════════════════════════════")

                    if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                        pendingUpdateUrl = updateUrl
                        pendingReleaseNotes = releaseNotes
                        pendingRemoteVersionCode = remoteVersionCode.toLong()
                        pendingRemoteVersionName = remoteVersionName
                        _updateState.value = UpdateState.Available
                        true
                    } else {
                        _updateState.value = UpdateState.UpToDate
                        if (isManual) {
                            delay(3000)
                            _updateState.value = UpdateState.Idle
                        }
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ Exception during update check: ${e.javaClass.simpleName}: ${e.message}", e)
                crashReporter.recordOperationFailure(
                    operation = "update",
                    phase = "check",
                    category = "update_failed",
                    message = "check_failed: ${e.javaClass.simpleName}: ${e.message}",
                    throwable = e,
                    throttleMs = 120_000L
                )
                if (isManual) {
                    _updateState.value = UpdateState.Failed(-1)
                    delay(3000)
                    _updateState.value = UpdateState.Idle
                }
                false
            } finally {
                trace.stop()
                isChecking = false
            }
        }
    }

    /**
     * تنزيل الـ APK داخل التطبيق مع تقدم حقيقي، ثم فتح التثبيت
     */
    suspend fun startFlexibleUpdate(activity: Activity) {
        val url = pendingUpdateUrl ?: run {
            Log.e(TAG, "No pending update URL")
            return
        }
        val expectedVersionCode = pendingRemoteVersionCode
        val expectedVersionName = pendingRemoteVersionName
        Log.w(TAG, "▶ بدء تنزيل APK من: $url")
        val trace = FirebasePerformance.getInstance().newTrace("wameed_update_download").apply {
            putAttribute("app_version", BuildConfig.VERSION_NAME)
            start()
        }

        withContext(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Downloading(0f)

                // مجلد مؤقت آمن
                val updateDir = File(context.cacheDir, "updates")
                updateDir.mkdirs()
                val apkFile = File(updateDir, APK_FILE_NAME)
                // حذف ملف قديم إن وجد
                if (apkFile.exists()) apkFile.delete()

                val request = updateRequest(url)
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "✗ Download failed: HTTP ${response.code}")
                        _updateState.value = UpdateState.Failed(response.code)
                        return@withContext
                    }

                    val body = response.body ?: run {
                        _updateState.value = UpdateState.Failed(-2)
                        return@withContext
                    }

                    val contentLength = body.contentLength()
                    Log.w(TAG, "حجم APK: ${contentLength / 1024} KB")

                    var bytesRead = 0L
                    var lastReportedPercent = -1
                    FileOutputStream(apkFile).use { fos ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(65536) // 64 KB buffer
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                                bytesRead += read
                                if (contentLength > 0) {
                                    val percent = (bytesRead * 100 / contentLength).toInt()
                                    // تحديث UI كل 2% فقط لتقليل الضغط
                                    if (percent >= lastReportedPercent + 2) {
                                        lastReportedPercent = percent
                                        val progress = bytesRead.toFloat() / contentLength
                                        _updateState.value = UpdateState.Downloading(progress)
                                        if (percent % 20 == 0) {
                                            Log.w(TAG, "⬇ تحميل: $percent% (${bytesRead / 1024} KB)")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Log.w(TAG, "✅ تم تنزيل APK (${apkFile.length() / 1024} KB)")
                    validateApk(apkFile, expectedVersionCode, expectedVersionName)
                    _updateState.value = UpdateState.Downloaded
                }

                // فتح نافذة التثبيت
                withContext(Dispatchers.Main) {
                    _updateState.value = UpdateState.Installing
                    recordInstallAttempt(expectedVersionCode, expectedVersionName, url)
                    installApk(activity, apkFile)
                }

            } catch (e: Exception) {
                Log.e(TAG, "✗ خطأ أثناء تنزيل التحديث: ${e.message}", e)
                crashReporter.recordOperationFailure(
                    operation = "update",
                    phase = "download",
                    category = "update_failed",
                    message = "download_failed: ${e.javaClass.simpleName}: ${e.message}",
                    throwable = e,
                    throttleMs = 120_000L
                )
                _updateState.value = UpdateState.Failed(-1)
            } finally {
                trace.stop()
            }
        }
    }

    private fun validateApk(apkFile: File, expectedVersionCode: Long, expectedVersionName: String) {
        if (!apkFile.exists() || apkFile.length() < 1024 * 1024) {
            throw IOException("Downloaded APK is missing or unexpectedly small: ${apkFile.length()} bytes")
        }

        FileInputStream(apkFile).use { input ->
            val header = ByteArray(2)
            if (input.read(header) != 2 || header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
                throw IOException("Downloaded file is not a valid APK/ZIP payload")
            }
        }

        val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: throw IOException("Downloaded APK metadata could not be read")
        val packageName = packageInfo.packageName
        if (packageName != context.packageName) {
            throw IOException("Downloaded APK package mismatch: $packageName")
        }

        @Suppress("DEPRECATION")
        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        val apkVersionName = packageInfo.versionName.orEmpty()

        if (expectedVersionCode > 0L && apkVersionCode != expectedVersionCode) {
            throw IOException("Downloaded APK versionCode=$apkVersionCode, expected=$expectedVersionCode")
        }
        if (expectedVersionName.isNotBlank() && apkVersionName != expectedVersionName) {
            throw IOException("Downloaded APK versionName=$apkVersionName, expected=$expectedVersionName")
        }

        Log.w(TAG, "✅ APK metadata verified: $packageName $apkVersionName ($apkVersionCode)")
    }

    private fun recordInstallAttempt(expectedVersionCode: Long, expectedVersionName: String, updateUrl: String) {
        installPrefs.edit()
            .putLong(KEY_EXPECTED_CODE, expectedVersionCode)
            .putString(KEY_EXPECTED_NAME, expectedVersionName)
            .putString(KEY_UPDATE_URL, updateUrl)
            .putLong(KEY_LAUNCHED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearInstallAttempt() {
        installPrefs.edit().clear().apply()
    }

    fun consumePendingInstallResult(): UpdateInstallResult? {
        val expectedCode = installPrefs.getLong(KEY_EXPECTED_CODE, 0L)
        if (expectedCode <= 0L) return null

        val expectedName = installPrefs.getString(KEY_EXPECTED_NAME, "").orEmpty()
        val updateUrl = installPrefs.getString(KEY_UPDATE_URL, "").orEmpty()
        val launchedAt = installPrefs.getLong(KEY_LAUNCHED_AT, 0L)
        val installedCode = BuildConfig.VERSION_CODE.toLong()
        val installedName = BuildConfig.VERSION_NAME
        val completed = installedCode >= expectedCode

        clearInstallAttempt()
        _updateState.value = if (completed) {
            UpdateState.Installed
        } else {
            UpdateState.InstallNotCompleted(expectedName)
        }

        return UpdateInstallResult(
            expectedVersionCode = expectedCode,
            expectedVersionName = expectedName,
            installedVersionCode = installedCode,
            installedVersionName = installedName,
            updateUrl = updateUrl,
            launchedAtMs = launchedAt,
            completed = completed
        )
    }

    /**
     * فتح نافذة تثبيت الـ APK عبر FileProvider
     */
    private fun installApk(activity: Activity, apkFile: File) {
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            activity.startActivity(installIntent)
            Log.w(TAG, "✅ تم فتح نافذة التثبيت")
            // نرجع لحالة Idle — إذا نجح التثبيت سيُعاد تشغيل التطبيق تلقائياً
            _updateState.value = UpdateState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "✗ فشل فتح التثبيت: ${e.message}", e)
            crashReporter.recordOperationFailure(
                operation = "update",
                phase = "install_intent",
                category = "install_failed",
                message = "install_intent_failed: ${e.javaClass.simpleName}: ${e.message}",
                throwable = e,
                throttleMs = 120_000L
            )
            _updateState.value = UpdateState.Failed(-3)
        }
    }

    fun completeUpdate() {
        // Not needed for direct APK install
    }

    fun downloadAndInstallUpdate(activity: Activity, url: String) {
        pendingUpdateUrl = url
        // يُستدعى من خارج coroutine — لن يُستخدم مباشرة، startFlexibleUpdate هي المفضلة
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int) {
        // Not needed for GitHub-based updates
    }

    fun cleanup() {
        // حذف ملف APK المؤقت
        try {
            val apkFile = File(File(context.cacheDir, "updates"), APK_FILE_NAME)
            if (apkFile.exists()) apkFile.delete()
        } catch (_: Exception) {}
    }
}

data class UpdateInstallResult(
    val expectedVersionCode: Long,
    val expectedVersionName: String,
    val installedVersionCode: Long,
    val installedVersionName: String,
    val updateUrl: String,
    val launchedAtMs: Long,
    val completed: Boolean
)

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
    data class InstallNotCompleted(val expectedVersionName: String) : UpdateState()
    data class Failed(val errorCode: Int) : UpdateState()
}
