package com.wameed

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wameed.ui.components.PulsingDot
import com.wameed.ui.components.SectionLabel
import com.wameed.ui.components.WameedBadge
import com.wameed.ui.components.WameedCard
import com.wameed.ui.components.WameedEmptyState
import com.wameed.ui.components.WameedPrimaryButton
import com.wameed.ui.components.WameedProgressBar
import com.wameed.ui.components.WameedScreen
import com.wameed.ui.components.WameedSettingsRow
import com.wameed.ui.components.WameedTextAction
import com.wameed.ui.theme.WameedError
import com.wameed.ui.theme.WameedGreen
import com.wameed.ui.theme.WameedGreen95
import com.wameed.ui.theme.WameedInfo
import com.wameed.ui.theme.WameedMint
import com.wameed.ui.theme.WameedSuccess
import com.wameed.ui.theme.WameedSurface
import com.wameed.ui.theme.WameedTextMuted
import com.wameed.ui.theme.WameedTextPrimary
import com.wameed.ui.theme.WameedTextSecondary
import com.wameed.ui.theme.WameedTheme
import com.wameed.ui.theme.WameedWarning
import com.wameed.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private suspend inline fun <T> withContextIO(crossinline block: () -> T): T =
    withContext(Dispatchers.IO) { block() }

class MainActivity : ComponentActivity() {
    private lateinit var sender: WameedSender
    private val discovery = DeviceDiscovery()
    private lateinit var updateManager: WameedUpdateManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sender = WameedSender(this)
        updateManager = WameedUpdateManager.getInstance(this)
        
        // تهيئة نظام تتبع الأخطاء
        WameedCrashReporter.initialize(this)
        
        // تهيئة سجل التشخيص
        WameedLogger.init(this)
        
        // تشغيل استقبال الهاتف حتى لو لم يكن مسار الهاتف -> الكمبيوتر جاهزاً بعد.
        WameedConnectionService.startReceiving(this)

        setContent {
            WameedTheme {
                MainScreen(sender, discovery, updateManager)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        discovery.stop()
        // We no longer stop the service here to allow it to keep the app alive in background
        // and to avoid ForegroundServiceDidNotStartInTimeException race conditions.
    }

    override fun onPause() {
        super.onPause()
        // Service is already started in onCreate and kept alive during receiving; no need to restart
    }

    override fun onResume() {
        super.onResume()
        WameedConnectionService.refresh(this)
    }
}

enum class ConnectionState { Idle, Checking, Searching, Connecting, PairingPending, Connected, Stale, Discovered, Failed, Rejected }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(sender: WameedSender, discovery: DeviceDiscovery, updateManager: WameedUpdateManager) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var connectionState by remember { mutableStateOf(ConnectionState.Idle) }
    var statusText by remember { mutableStateOf("") }
    var isSearchingDevices by remember { mutableStateOf(false) }
    var sendErrorMessage by remember { mutableStateOf("") }
    var receivingReady by remember { mutableStateOf(WameedConnectionService.isReceiverReady) }
    val devices = remember { mutableStateMapOf<String, DeviceDiscovery.DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<DeviceDiscovery.DiscoveredDevice?>(null) }
    val showManualDialog = remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var pendingCrashReport by remember {
        mutableStateOf(WameedCrashReporter.getInstance().consumePendingCrashReport(context))
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    
    // Batch sending state
    var isSendingBatch by remember { mutableStateOf(false) }
    var currentFileIndex by remember { mutableIntStateOf(0) }
    var totalFilesCount by remember { mutableIntStateOf(0) }
    var currentFileProgress by remember { mutableIntStateOf(0) }
    var currentFileSpeed by remember { mutableStateOf(0.0) }
    var currentFileName by remember { mutableStateOf("") }
    var currentInfoStatus by remember { mutableStateOf("") }
    var isReceivingFile by remember { mutableStateOf(false) }
    var receivingFileName by remember { mutableStateOf("") }
    var receivingProgress by remember { mutableIntStateOf(0) }
    var receivingSpeed by remember { mutableStateOf(0.0) }
    
    // Update management logic is handled inside UpdateIntegration

    val selectedUris: SnapshotStateList<Uri> = remember { mutableStateListOf<Uri>() }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (selectedUris.size + uris.size > 10) {
                Toast.makeText(context, context.getString(R.string.error_too_many_files), Toast.LENGTH_SHORT).show()
                val remainingCount = (10 - selectedUris.size).coerceAtLeast(0)
                selectedUris.addAll(uris.take(remainingCount))
            } else {
                selectedUris.addAll(uris)
            }
        }
    }

    fun startBatchSend(urisCopy: List<Uri>) {
        Log.i("MainActivity", "بدء عملية إرسال ${urisCopy.size} ملفات مختارة")
        isSendingBatch = true
        sendErrorMessage = ""

        sender.sendFiles(urisCopy, object : WameedSender.SendCallback {
            override fun onNextFile(index: Int, total: Int, fileName: String) {
                Log.d("MainActivity", "إرسال ملف $index/$total: $fileName")
                currentFileIndex = index
                totalFilesCount = total
                currentFileName = fileName
                currentFileProgress = 0
                currentFileSpeed = 0.0
                currentInfoStatus = ""
            }

            override fun onProgress(percent: Int, speedMbps: Double) {
                currentFileProgress = percent
                currentFileSpeed = speedMbps
                if (percent > 0) currentInfoStatus = "" // إخفاء أي حالة معلومات عند بدء التقدم
            }

            override fun onProgress(percent: Int) {
                currentFileProgress = percent
            }

            override fun onSuccess(message: String) {
                Log.i("MainActivity", "✅ نجاح الإرسال: $message")
                mainHandler.post {
                    isSendingBatch = false
                    selectedUris.clear()
                    currentInfoStatus = ""
                    connectionState = ConnectionState.Connected
                    statusText = context.getString(R.string.send_ready_to_pc)
                    WameedPrefs.setLastConnected(context)
                    Toast.makeText(context, context.getString(R.string.success_all_sent, urisCopy.size), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                Log.e("MainActivity", "❌ فشل الإرسال: $error")
                mainHandler.post {
                    isSendingBatch = false
                    currentInfoStatus = ""
                    connectionState = ConnectionState.Discovered
                    statusText = context.getString(R.string.connection_visible_not_ready)
                    sendErrorMessage = context.getString(R.string.send_preflight_inline_error, error)
                }
            }

            override fun onInfo(message: String) {
                Log.i("MainActivity", "ℹ️ معلومة: $message")
                mainHandler.post {
                    currentInfoStatus = ""
                }
            }
        })
    }

    fun sendSelectedFiles() {
        if (selectedUris.isEmpty()) {
            Log.w("MainActivity", "محاولة إرسال فاشلة: لم يتم اختيار ملفات")
            return
        }

        if (connectionState != ConnectionState.Connected) {
            sendErrorMessage = context.getString(R.string.send_not_ready_inline)
            statusText = context.getString(R.string.send_not_ready_to_pc)
            return
        }

        val urisCopy = selectedUris.toList()
        connectionState = ConnectionState.Checking
        statusText = context.getString(R.string.verifying_send_ready)
        sendErrorMessage = ""
        WameedConnectionService.start(context)

        sender.verifySendReady(object : WameedSender.SendCallback {
            override fun onSuccess(message: String) {
                mainHandler.post {
                    connectionState = ConnectionState.Connected
                    statusText = context.getString(R.string.send_ready_to_pc)
                    startBatchSend(urisCopy)
                }
            }

            override fun onError(error: String) {
                mainHandler.post {
                    connectionState = ConnectionState.Discovered
                    statusText = context.getString(R.string.connection_visible_not_ready)
                    sendErrorMessage = context.getString(R.string.send_preflight_inline_error, error)
                }
            }

            override fun onInfo(message: String) {
                mainHandler.post {
                    connectionState = ConnectionState.PairingPending
                    statusText = message
                }
            }

            override fun onProgress(percent: Int) {}
        })
    }

    fun connectToDevice(device: DeviceDiscovery.DiscoveredDevice) {
        Log.i("MainActivity", "محاولة الربط مع الجهاز: ${device.name} (${device.address})")
        selectedDevice = device
        connectionState = ConnectionState.Connecting
        statusText = context.getString(R.string.connecting_to, device.name)
        WameedPrefs.savePcAddress(context, device.address)

        if (device.name.isNotBlank() && device.name != device.ip) {
            WameedPrefs.setPcName(context, device.name)
        }

        var retryCount = 0
        val maxRetries = 1

        fun attemptConnect() {
            sender.sendPing(object : WameedSender.SendCallback {
                override fun onSuccess(message: String) {
                    Log.i("MainActivity", "✅ تم الربط بنجاح مع ${device.name}")
                    mainHandler.post {
                        connectionState = ConnectionState.Connected
                        statusText = context.getString(R.string.send_ready_to_pc)
                        WameedPrefs.setLastConnected(context)
                    }
                }
                override fun onError(error: String) {
                    val isRejected = error.contains("رفض") || error.contains("rejected", ignoreCase = true)
                    if (isRejected) {
                        Log.w("MainActivity", "❌ الكمبيوتر رفض الاقتران: ${device.name}")
                        mainHandler.post {
                            connectionState = ConnectionState.Rejected
                            statusText = context.getString(R.string.status_pairing_rejected)
                        }
                    } else if (retryCount < maxRetries) {
                        retryCount++
                        Log.w("MainActivity", "⚠️ فشل الربط (محاولة $retryCount/${maxRetries+1}), إعادة المحاولة...")
                        mainHandler.postDelayed({ attemptConnect() }, 2000)
                    } else {
                        Log.e("MainActivity", "❌ فشل الربط نهائياً مع ${device.name}: $error")
                        mainHandler.post {
                            connectionState = ConnectionState.Failed
                            statusText = context.getString(R.string.send_not_ready_to_pc)
                        }
                    }
                }
                override fun onInfo(message: String) {
                    Log.d("MainActivity", "ℹ️ حالة الربط: $message")
                    mainHandler.post {
                        if (message.contains("انتظار") || message.contains("waiting", ignoreCase = true)) {
                            connectionState = ConnectionState.PairingPending
                            statusText = context.getString(R.string.status_waiting_for_approval)
                        }
                    }
                }
                override fun onProgress(percent: Int) {}
            })
        }

        attemptConnect()
    }

    fun startDiscovery() {
        val keepCurrentConnection = connectionState == ConnectionState.Connected
        isSearchingDevices = true
        sendErrorMessage = ""
        if (!keepCurrentConnection) {
            connectionState = ConnectionState.Searching
            statusText = context.getString(R.string.searching_devices)
            selectedDevice = null
        }
        devices.clear()

        discovery.startListening(context, callback = object : DeviceDiscovery.DiscoveryCallback {
            override fun onDeviceFound(device: DeviceDiscovery.DiscoveredDevice) {
                mainHandler.post {
                    devices[device.address] = device
                    if (device.name.isNotBlank() && device.name != device.ip) {
                        WameedPrefs.setPcName(context, device.name)
                    }

                    val savedName = WameedPrefs.getPcName(context)
                    val savedIp = WameedPrefs.getPcIp(context)
                    val savedPort = WameedPrefs.getPcPort(context)

                    // فحص هل هذا هو الجهاز المفضل (نفس الاسم أو نفس الـ IP)
                    val isLastDevice = (savedIp == device.ip && savedPort == device.port) ||
                            (device.name.isNotBlank() && device.name == savedName)

                    if (isLastDevice && connectionState != ConnectionState.Connected && connectionState != ConnectionState.Connecting) {
                        Log.i("Wameed", "Auto-connecting to known device: ${device.name} at ${device.ip}")
                        connectToDevice(device)
                    }

                    val sameName = device.name.isNotBlank()
                            && device.name != device.ip
                            && device.name.equals(savedName, ignoreCase = true)
                    val ipChanged = savedIp.isNotEmpty()
                            && (savedIp != device.ip || savedPort != device.port)

                    if (sameName && ipChanged) {
                        Log.i("Wameed",
                            "PC IP changed: $savedIp:$savedPort \u2192 ${device.ip}:${device.port} (name=${device.name})")
                        WameedPrefs.savePcAddress(context, "${device.ip}:${device.port}")
                        WameedConnectionService.start(context)
                    }
                }
            }
            override fun onError(error: String) {
                mainHandler.post {
                    isSearchingDevices = false
                    if (connectionState != ConnectionState.Connected) {
                        connectionState = ConnectionState.Failed
                        statusText = error
                    }
                }
            }
            override fun onSearchFinished() {
                mainHandler.post {
                    isSearchingDevices = false
                    if (connectionState == ConnectionState.Searching) {
                        connectionState = ConnectionState.Idle
                        statusText = if (devices.isEmpty()) context.getString(R.string.no_devices_found)
                                     else context.getString(R.string.choose_device)
                    }
                }
            }
        })
    }

    suspend fun revalidate(triggerDiscoveryOnFailure: Boolean = true) {
        if (!WameedPrefs.isConfigured(context)) {
            if (triggerDiscoveryOnFailure) startDiscovery()
            return
        }
        if (connectionState != ConnectionState.Connected) {
            connectionState = ConnectionState.Checking
            val last = WameedPrefs.formatLastConnected(context)
            statusText = if (last.isNotEmpty()) context.getString(R.string.checking_connection_last, last)
                         else context.getString(R.string.checking_connection)
        }
        val ip = WameedPrefs.getPcIp(context)
        val port = WameedPrefs.getPcPort(context)
        val tcpOk = withContextIO { DeviceDiscovery.isTcpReachable(ip, port, 1500) }
        val recentSend = WameedPrefs.getLastSendInfo(context)
            ?.takeIf { (System.currentTimeMillis() - it.first) < 120_000 }

        when {
            tcpOk -> {
                connectionState = ConnectionState.Connected
                val friendly = WameedPrefs.getPcName(context)
                selectedDevice = DeviceDiscovery.DiscoveredDevice(
                    name = friendly.ifBlank { WameedPrefs.getDisplayAddress(context) },
                    ip = ip, port = port
                )
                WameedPrefs.setLastConnected(context)
                statusText = context.getString(R.string.send_ready_to_pc)
            }
            recentSend != null -> {
                connectionState = ConnectionState.Stale
                if (selectedDevice == null) {
                    val friendly = WameedPrefs.getPcName(context)
                    selectedDevice = DeviceDiscovery.DiscoveredDevice(
                        name = friendly.ifBlank { WameedPrefs.getDisplayAddress(context) },
                        ip = ip, port = port
                    )
                }
                val ago = (System.currentTimeMillis() - recentSend.first) / 1000
                statusText = context.getString(R.string.connection_recent_needs_check, ago)
            }
            else -> {
                // TCP failed and no recent send: check if PC is at least discoverable
                val wasConnected = connectionState == ConnectionState.Connected
                if (wasConnected) {
                    // Give a brief grace — mark as Discovered, not Failed
                    connectionState = ConnectionState.Discovered
                    statusText = context.getString(R.string.connection_visible_not_ready)
                    // Retry TCP once after a short delay before declaring failure
                    delay(2000)
                    val retryOk = withContextIO { DeviceDiscovery.isTcpReachable(ip, port, 1500) }
                    if (retryOk) {
                        connectionState = ConnectionState.Connected
                        val friendly = WameedPrefs.getPcName(context)
                        selectedDevice = DeviceDiscovery.DiscoveredDevice(
                            name = friendly.ifBlank { WameedPrefs.getDisplayAddress(context) },
                            ip = ip, port = port
                        )
                        statusText = context.getString(R.string.send_ready_to_pc)
                    } else {
                        connectionState = ConnectionState.Failed
                        statusText = context.getString(R.string.send_not_ready_to_pc)
                        selectedDevice = null
                        if (triggerDiscoveryOnFailure) startDiscovery()
                    }
                } else {
                    connectionState = ConnectionState.Failed
                    statusText = context.getString(R.string.send_not_ready_to_pc)
                    selectedDevice = null
                    if (triggerDiscoveryOnFailure) startDiscovery()
                }
            }
        }
    }

    LaunchedEffect(Unit) { revalidate() }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        WameedEvents.events.collect { event ->
            when (event) {
                is WameedEvent.ServiceStatus -> {
                    if (event.isWsConnected) {
                        connectionState = ConnectionState.Connected
                        statusText = context.getString(R.string.send_ready_to_pc)
                    } else {
                        // Only downgrade if we were connected AND no recent send.
                        // This prevents flicker when the keep-alive WS drops temporarily.
                        if (connectionState == ConnectionState.Connected) {
                            val recentSendGrace = WameedPrefs.getLastSendInfo(context)
                                ?.takeIf { (System.currentTimeMillis() - it.first) < 120_000 }
                            if (recentSendGrace != null) {
                                val ago = (System.currentTimeMillis() - recentSendGrace.first) / 1000
                                Log.d("MainActivity", "ServiceStatus(false) -> stale; recent send ${ago}s ago")
                                connectionState = ConnectionState.Stale
                                statusText = context.getString(R.string.connection_recent_needs_check, ago)
                            } else {
                                connectionState = ConnectionState.Discovered
                                statusText = context.getString(R.string.connection_visible_not_ready)
                            }
                        }
                    }
                }
                is WameedEvent.ReceiverStatus -> {
                    receivingReady = event.isReady
                }
                is WameedEvent.ReceiveMeta -> {
                    isReceivingFile = true
                    receivingFileName = event.filename
                    receivingProgress = 0
                    receivingSpeed = 0.0
                }
                is WameedEvent.ReceiveProgress -> {
                    isReceivingFile = true
                    receivingProgress = event.percent.coerceIn(0, 99)
                    receivingSpeed = event.speedMbps
                }
                is WameedEvent.ReceiveComplete -> {
                    receivingProgress = 100
                    receivingSpeed = 0.0
                    delay(1600)
                    isReceivingFile = false
                    receivingFileName = ""
                    receivingProgress = 0
                }
                is WameedEvent.ReceiveError -> {
                    isReceivingFile = false
                    receivingFileName = ""
                    receivingProgress = 0
                    receivingSpeed = 0.0
                }
                else -> Unit
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { revalidate(triggerDiscoveryOnFailure = false) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(connectionState, selectedDevice) {
        if (connectionState != ConnectionState.Connected) return@LaunchedEffect
        var failures = 0
        while (connectionState == ConnectionState.Connected || connectionState == ConnectionState.Discovered) {
            delay(10_000)
            val dev = selectedDevice ?: break
            val alive = withContextIO { DeviceDiscovery.isTcpReachable(dev.ip, dev.port, 2000) }
            if (alive) {
                failures = 0
                if (connectionState == ConnectionState.Discovered) {
                    connectionState = ConnectionState.Connected
                    statusText = context.getString(R.string.send_ready_to_pc)
                }
                WameedPrefs.setLastConnected(context)
            } else {
                failures += 1
                // Check recent send grace before downgrading
                val recentSendGrace = WameedPrefs.getLastSendInfo(context)
                    ?.takeIf { (System.currentTimeMillis() - it.first) < 120_000 }
                if (recentSendGrace != null) {
                    val ago = (System.currentTimeMillis() - recentSendGrace.first) / 1000
                    connectionState = ConnectionState.Stale
                    statusText = context.getString(R.string.connection_recent_needs_check, ago)
                    break
                } else if (failures == 2) {
                    connectionState = ConnectionState.Discovered
                    statusText = context.getString(R.string.connection_visible_not_ready)
                } else if (failures >= 4) {
                    connectionState = ConnectionState.Failed
                    statusText = context.getString(R.string.send_not_ready_to_pc)
                    break
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_title),
                        fontWeight = FontWeight.ExtraBold,
                        color = WameedGreen,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = WameedMint)
            )
        },
        bottomBar = {
            if (selectedTab in 0..3) {
                WameedBottomBar(
                    selectedTab = selectedTab,
                    onSelect = { tab ->
                        selectedTab = tab
                        Log.i("Wameed", "انتقال إلى تبويب $tab")
                    }
                )
            }
        },
        containerColor = WameedMint
    ) { padding ->
        when (selectedTab) {
            0 -> ConnectionTab(
                modifier = Modifier.padding(padding),
                connectionState = connectionState,
                statusText = statusText,
                isSearchingDevices = isSearchingDevices,
                sendErrorMessage = sendErrorMessage,
                selectedDevice = selectedDevice,
                devices = devices,
                onConnect = { connectToDevice(it) },
                onRefresh = { startDiscovery() },
                onSend = { filePicker.launch(arrayOf("*/*")) },
                onManualConnect = { showManualDialog.value = true },
                selectedUris = selectedUris,
                onRemoveUri = { selectedUris.remove(it) },
                onConfirmSend = { sendSelectedFiles() },
                isSendingBatch = isSendingBatch,
                onDiagnose = { selectedTab = 6 }
            )
            1 -> HistoryTab(modifier = Modifier.padding(padding))
            2 -> ReceivedTab(modifier = Modifier.padding(padding))
            3 -> SettingsTab(
                modifier = Modifier.padding(padding),
                updateManager = updateManager,
                onShowTrusted = { selectedTab = 4 },
                onShowDiagLog = { selectedTab = 5 },
                onShowNetDiag = { selectedTab = 6 }
            )
            4 -> TrustedDevicesTab(modifier = Modifier.padding(padding), onBack = { selectedTab = 3 })
            5 -> DiagLogScreen(onBack = { selectedTab = 3 })
            6 -> NetworkDiagScreen(onBack = { selectedTab = 3 })
        }
    }

    // Update integration
    UpdateIntegration(updateManager, context)

    if (isSendingBatch || isReceivingFile) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column {
                if (isReceivingFile) {
                    BatchProgressOverlay(
                        label = stringResource(R.string.receiving),
                        currentFile = 1,
                        totalFiles = 1,
                        progress = receivingProgress,
                        speed = receivingSpeed,
                        fileName = receivingFileName
                    )
                }
                if (isSendingBatch) {
                    BatchProgressOverlay(
                        label = stringResource(R.string.sending),
                        currentFile = currentFileIndex,
                        totalFiles = totalFilesCount,
                        progress = currentFileProgress,
                        speed = currentFileSpeed,
                        fileName = currentFileName,
                        infoStatus = currentInfoStatus
                    )
                }
            }
        }
    }

    pendingCrashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { pendingCrashReport = null },
            title = { Text(stringResource(R.string.crash_report_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.crash_report_message,
                        report.reportId,
                        report.type,
                        report.message.ifBlank { report.thread }
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val description = context.getString(
                        R.string.crash_report_prefill,
                        report.reportId,
                        report.type,
                        report.thread,
                        report.message.ifBlank { "no message" }
                    )
                    context.startActivity(
                        Intent(context, WameedBugReportActivity::class.java)
                            .putExtra(WameedBugReportActivity.EXTRA_INITIAL_DESCRIPTION, description)
                    )
                    pendingCrashReport = null
                }) {
                    Text(stringResource(R.string.crash_report_add_details))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCrashReport = null }) {
                    Text(stringResource(R.string.crash_report_later))
                }
            }
        )
    }

    if (showManualDialog.value) {
        AlertDialog(
            onDismissRequest = { showManualDialog.value = false },
            title = { Text(stringResource(R.string.manual_ip_title)) },
            text = {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text(stringResource(R.string.manual_ip_label)) },
                    placeholder = { Text(stringResource(R.string.manual_ip_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manualIp.isNotBlank()) {
                        val parts = manualIp.trim().split(":")
                        val ip = parts[0]
                        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 7788 else 7788
                        val device = DeviceDiscovery.DiscoveredDevice(ip, ip, port)
                        connectToDevice(device)
                        showManualDialog.value = false
                    }
                }) { Text(stringResource(R.string.connect)) }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog.value = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun WameedBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit
) {
    val tabs = listOf(
        0 to stringResource(R.string.tab_home),
        1 to stringResource(R.string.tab_history),
        2 to stringResource(R.string.tab_received),
        3 to stringResource(R.string.tab_settings)
    )

    Surface(
        color = WameedSurface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { (index, label) ->
                val active = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (active) WameedGreen95 else Color.Transparent)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) WameedGreen else WameedTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionTab(
    modifier: Modifier,
    connectionState: ConnectionState,
    statusText: String,
    isSearchingDevices: Boolean,
    sendErrorMessage: String,
    selectedDevice: DeviceDiscovery.DiscoveredDevice?,
    devices: Map<String, DeviceDiscovery.DiscoveredDevice>,
    onConnect: (DeviceDiscovery.DiscoveredDevice) -> Unit,
    onRefresh: () -> Unit,
    onSend: () -> Unit,
    onManualConnect: () -> Unit,
    selectedUris: List<Uri> = emptyList(),
    onRemoveUri: (Uri) -> Unit = {},
    onConfirmSend: () -> Unit = {},
    isSendingBatch: Boolean = false,
    onDiagnose: () -> Unit = {}
) {
    val currentAddress = selectedDevice?.address
    val visibleDevices = devices.values.filterNot {
        connectionState == ConnectionState.Connected && it.address == currentAddress
    }
    val showingOtherDevices = connectionState == ConnectionState.Connected && currentAddress != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WameedMint)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        StatusCard(connectionState, statusText, selectedDevice)
        Spacer(Modifier.height(18.dp))

        if (selectedUris.isNotEmpty()) {
            WameedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.attached_files, selectedUris.size),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = WameedTextPrimary
                )
                Spacer(Modifier.height(10.dp))
                selectedUris.forEach { uri ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                uri.path?.split("/")?.last() ?: uri.toString(),
                                maxLines = 1,
                                fontSize = 13.sp,
                                color = WameedTextPrimary
                            )
                            Text(
                                uri.scheme.orEmpty(),
                                maxLines = 1,
                                fontSize = 11.sp,
                                color = WameedTextMuted
                            )
                        }
                        IconButton(
                            onClick = { onRemoveUri(uri) },
                            modifier = Modifier.size(34.dp),
                            enabled = !isSendingBatch
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = if (isSendingBatch) WameedTextMuted else WameedError,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                WameedPrimaryButton(
                    text = if (isSendingBatch) stringResource(R.string.sending) else stringResource(R.string.send_all),
                    onClick = onConfirmSend,
                    enabled = connectionState == ConnectionState.Connected && !isSendingBatch,
                    loading = isSendingBatch
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            SectionLabel(
                stringResource(
                    if (showingOtherDevices) R.string.section_other_devices else R.string.section_devices
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearchingDevices) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = WameedInfo
                    )
                    Spacer(Modifier.width(6.dp))
                }
                WameedTextAction(
                    text = stringResource(R.string.action_refresh),
                    onClick = onRefresh,
                    enabled = !isSearchingDevices && !isSendingBatch
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                isSearchingDevices && visibleDevices.isEmpty() ->
                    WameedEmptyState(
                        title = stringResource(R.string.searching_devices),
                        showProgress = true
                    )
                visibleDevices.isEmpty() && connectionState != ConnectionState.Connecting -> {
                    WameedEmptyState(
                        title = if (showingOtherDevices) {
                            stringResource(R.string.no_other_devices_found)
                        } else {
                            stringResource(R.string.no_devices_found)
                        },
                        subtitle = stringResource(R.string.no_devices_hint)
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(visibleDevices, key = { it.address }) { device ->
                            val isSel = selectedDevice?.address == device.address
                            DeviceItem(
                                device = device,
                                isSelected = isSel,
                                isConnecting = connectionState == ConnectionState.Connecting && isSel,
                                isConnected = connectionState == ConnectionState.Connected && isSel
                            ) { onConnect(device) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sendErrorMessage.isNotBlank()) {
            WameedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    sendErrorMessage,
                    fontSize = 13.sp,
                    color = WameedError,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                WameedTextAction(
                    text = stringResource(R.string.diag_fix_hint),
                    onClick = onDiagnose,
                    modifier = Modifier.fillMaxWidth(),
                    color = WameedInfo
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        WameedPrimaryButton(
            text = if (selectedUris.isEmpty()) stringResource(R.string.action_send_file) else stringResource(R.string.send_all),
            onClick = { if (selectedUris.isEmpty()) onSend() else onConfirmSend() },
            enabled = connectionState == ConnectionState.Connected && !isSendingBatch,
            loading = isSendingBatch
        )
        Spacer(Modifier.height(6.dp))
        WameedTextAction(
            text = stringResource(R.string.action_manual_connect),
            onClick = onManualConnect,
            modifier = Modifier.fillMaxWidth(),
            color = WameedTextSecondary
        )

        if (connectionState == ConnectionState.Failed) {
            Spacer(Modifier.height(6.dp))
            WameedTextAction(
                text = stringResource(R.string.diag_fix_hint),
                onClick = onDiagnose,
                modifier = Modifier.fillMaxWidth(),
                color = WameedInfo
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun HistoryTab(modifier: Modifier) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WameedPrefs.getHistory(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WameedMint)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                stringResource(R.string.history_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = WameedGreen
            )
            if (history.isNotEmpty()) {
                WameedTextAction(text = stringResource(R.string.clear_history), color = WameedError, onClick = {
                    WameedPrefs.clearHistory(context)
                    history = emptyList()
                })
            }
        }
        Spacer(Modifier.height(12.dp))

        if (history.isEmpty()) {
            WameedEmptyState(title = stringResource(R.string.no_history))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(history, key = { "${it.time}_${it.filename}" }) { entry ->
                    HistoryItem(entry)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(entry: WameedPrefs.HistoryEntry) {
    val statusColor = if (entry.status == "success") WameedSuccess else WameedError
    val sizeText = formatSize(entry.size)
    val dirText = if (entry.direction == "received") stringResource(R.string.direction_received) else stringResource(R.string.direction_sent)
    val dirColor = if (entry.direction == "received") WameedInfo else WameedGreen

    WameedCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = statusColor, pulsing = false, size = 9.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.filename,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = WameedTextPrimary,
                    maxLines = 1
                )
                Text(
                    "$dirText  •  ${entry.type}  •  $sizeText",
                    fontSize = 11.sp,
                    color = WameedTextSecondary
                )
            }
            WameedBadge(text = entry.time.substringAfter(" "), color = dirColor)
        }
    }
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1048576) return "${"%.1f".format(bytes / 1024.0)} KB"
    if (bytes < 1073741824) return "${"%.1f".format(bytes / 1048576.0)} MB"
    return "${"%.1f".format(bytes / 1073741824.0)} GB"
}

@Composable
fun ReceivedTab(modifier: Modifier) {
    val context = LocalContext.current
    var files by remember { mutableStateOf(listReceivedFiles(context)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WameedMint)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(
                stringResource(R.string.received_files_title),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = WameedGreen
            )
            WameedTextAction(text = stringResource(R.string.open_wameed_folder), onClick = {
                openWameedFolder(context)
            })
        }
        Spacer(Modifier.height(2.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.section_recent_files))
            WameedTextAction(text = stringResource(R.string.action_refresh), onClick = { files = listReceivedFiles(context) })
        }
        Spacer(Modifier.height(8.dp))

        if (files.isEmpty()) {
            WameedEmptyState(title = stringResource(R.string.no_received_files))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(files, key = { it.uri.toString() }) { file ->
                    ReceivedFileItem(file, context)
                }
            }
        }
    }
}

data class ReceivedFileInfo(
    val name: String,
    val size: Long,
    val uri: Uri,
    val mimeType: String,
    val dateModified: Long
)

@Composable
fun ReceivedFileItem(file: ReceivedFileInfo, context: Context) {
    val sizeText = formatSize(file.size)
    val dateText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .format(Date(file.dateModified * 1000))

    WameedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { openFile(context, file) },
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WameedFileTypeDot(file.mimeType)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = WameedTextPrimary,
                    maxLines = 1
                )
                Text("$sizeText  •  $dateText", fontSize = 11.sp, color = WameedTextSecondary)
            }
            WameedTextAction(text = stringResource(R.string.action_open), onClick = { openFile(context, file) })
            WameedTextAction(text = stringResource(R.string.action_share), onClick = { shareFile(context, file) })
        }
    }
}

@Composable
private fun WameedFileTypeDot(mimeType: String) {
    PulsingDot(
        color = when {
            mimeType.startsWith("image/") -> WameedInfo
            mimeType.startsWith("video/") -> WameedWarning
            mimeType.startsWith("audio/") -> WameedSuccess
            mimeType.contains("pdf") -> WameedError
            else -> WameedGreen
        },
        pulsing = false,
        size = 9.dp
    )
}

private fun fileTypeEmoji(mimeType: String): String {
    return when {
        mimeType.startsWith("image/") -> "\uD83D\uDDBC"
        mimeType.startsWith("video/") -> "\uD83C\uDFAC"
        mimeType.startsWith("audio/") -> "\uD83C\uDFB5"
        mimeType.contains("pdf") -> "\uD83D\uDCC4"
        mimeType.startsWith("text/") -> "\uD83D\uDCDD"
        else -> "\uD83D\uDCCE"
    }
}

private fun openFile(context: Context, file: ReceivedFileInfo) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: ReceivedFileInfo) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, file.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun openWameedFolder(context: Context) {
    try {
        val folderFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Wameed")
        if (!folderFile.exists()) {
            folderFile.mkdirs()
        }

        // 1. Try to open via MediaStore (Most modern and reliable way for Downloads)
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FWameed")
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.d("Wameed", "Failed to open specific SAF path")
        }

        // 2. Try generic Downloads view
        try {
            val downloadsIntent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(downloadsIntent)
            return
        } catch (e: Exception) {
            Log.d("Wameed", "Failed to open ACTION_VIEW_DOWNLOADS")
        }

        // 3. Fallback: Try to open with FileProvider
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                folderFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Last resort: Just open Files app
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Log.e("Wameed", "Failed to open folder", e)
        Toast.makeText(context, context.getString(R.string.error_open_folder), Toast.LENGTH_SHORT).show()
    }
}

private fun listReceivedFiles(context: Context): List<ReceivedFileInfo> {
    val filesList = mutableListOf<ReceivedFileInfo>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.DATE_MODIFIED
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf("%Wameed%")
        val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                )
                filesList.add(ReceivedFileInfo(
                    name = cursor.getString(nameCol) ?: "?",
                    size = cursor.getLong(sizeCol),
                    uri = uri,
                    mimeType = cursor.getString(mimeCol) ?: "*/*",
                    dateModified = cursor.getLong(dateCol)
                ))
            }
        }
    } else {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ), "Wameed"
        )
        if (dir.exists()) {
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { f ->
                if (f.isFile) {
                    filesList.add(ReceivedFileInfo(
                        name = f.name,
                        size = f.length(),
                        uri = Uri.fromFile(f),
                        mimeType = getMimeType(f.name),
                        dateModified = f.lastModified() / 1000
                    ))
                }
            }
        }
    }
    return filesList
}

private fun getMimeType(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        "apk" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }
}

@Composable
fun SettingsTab(modifier: Modifier, updateManager: WameedUpdateManager, onShowTrusted: () -> Unit, onShowDiagLog: () -> Unit = {}, onShowNetDiag: () -> Unit = {}) {
    val context = LocalContext.current
    var displayMode by remember { mutableStateOf(WameedPrefs.getDisplayMode(context)) }
    var currentLang by remember { mutableStateOf(WameedPrefs.getLanguage(context)) }
    var keepAlive by remember { mutableStateOf(WameedPrefs.isKeepAliveEnabled(context)) }
    val updateState by updateManager.updateState.collectAsState()
    val scope = rememberCoroutineScope()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WameedMint)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WameedGreen)
        Spacer(Modifier.height(16.dp))

        WameedSettingsRow(
            title = stringResource(R.string.language_title),
            subtitle = stringResource(R.string.language_detail),
            onClick = {
                val newLang = if (currentLang == "ar") "en" else "ar"
                WameedPrefs.setLanguage(context, newLang)
                currentLang = newLang
                (context as? android.app.Activity)?.recreate()
            },
            action = {
                WameedBadge(text = if (currentLang == "ar") "E" else "ع", color = WameedGreen)
            }
        )

        Spacer(Modifier.height(14.dp))

        WameedCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pc_display_mode), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WameedTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pc_display_mode_detail), fontSize = 12.sp, color = WameedTextSecondary)
            Spacer(Modifier.height(14.dp))

            listOf(
                "open" to stringResource(R.string.mode_open),
                "path" to stringResource(R.string.mode_path),
                "both" to stringResource(R.string.mode_both),
                "none" to stringResource(R.string.mode_none)
            ).forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            displayMode = value
                            WameedPrefs.setDisplayMode(context, value)
                        }
                        .background(if (displayMode == value) WameedGreen95 else Color.Transparent)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = displayMode == value,
                        onClick = {
                            displayMode = value
                            WameedPrefs.setDisplayMode(context, value)
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = WameedGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontSize = 14.sp, color = WameedTextPrimary)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        WameedSettingsRow(
            title = stringResource(R.string.trusted_devices_title),
            subtitle = stringResource(R.string.trusted_devices_detail),
            onClick = onShowTrusted,
            action = { WameedBadge(text = stringResource(R.string.open), color = WameedGreen) }
        )

        Spacer(Modifier.height(14.dp))

        WameedSettingsRow(
            title = stringResource(R.string.keep_alive_title),
            subtitle = stringResource(R.string.keep_alive_detail),
            onClick = {
                keepAlive = !keepAlive
                WameedPrefs.setKeepAliveEnabled(context, keepAlive)
                if (!keepAlive) WameedConnectionService.stop(context)
            },
            action = {
                Switch(
                    checked = keepAlive,
                    onCheckedChange = {
                        keepAlive = it
                        WameedPrefs.setKeepAliveEnabled(context, it)
                        if (!it) WameedConnectionService.stop(context)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = WameedGreen)
                )
            }
        )

        Spacer(Modifier.height(14.dp))

        val address = WameedPrefs.getDisplayAddress(context)
        WameedSettingsRow(
            title = stringResource(R.string.connection_info),
            subtitle = if (address.isNotEmpty()) stringResource(R.string.pc_label, address) else stringResource(R.string.not_connected)
        )

        Spacer(Modifier.height(14.dp))

        if (BuildConfig.DEBUG) {
            WameedSettingsRow(
                title = "اختبار العطل",
                subtitle = "اضغط هنا لاختبار Firebase Crashlytics",
                onClick = {
                    Log.d("FirebaseTest", "About to trigger test crash for Crashlytics")
                    val testException = RuntimeException("Test Crash - Firebase Crashlytics Testing")
                    Firebase.crashlytics.recordException(testException)
                    Firebase.crashlytics.log("Test crash triggered by user")
                    android.widget.Toast.makeText(context, "تم إرسال اختبار العطل إلى Firebase", android.widget.Toast.LENGTH_SHORT).show()
                },
                action = { WameedBadge(text = "DEBUG", color = WameedError) }
            )
            Spacer(Modifier.height(14.dp))
        }

        WameedSettingsRow(
            title = stringResource(R.string.bug_report_title),
            subtitle = "الإبلاغ عن مشاكل في التطبيق",
            onClick = {
                val intent = Intent(context, WameedBugReportActivity::class.java)
                context.startActivity(intent)
            },
            action = { WameedBadge(text = stringResource(R.string.open), color = WameedGreen) }
        )

        Spacer(Modifier.height(14.dp))

        WameedSettingsRow(
            title = stringResource(R.string.diag_log_title),
            subtitle = stringResource(R.string.diag_log_detail),
            onClick = onShowDiagLog,
            action = { WameedBadge(text = stringResource(R.string.open), color = WameedInfo) }
        )

        Spacer(Modifier.height(14.dp))

        WameedSettingsRow(
            title = stringResource(R.string.diag_net_title),
            subtitle = stringResource(R.string.diag_net_detail),
            onClick = onShowNetDiag,
            action = { WameedBadge(text = stringResource(R.string.open), color = WameedGreen) }
        )

        Spacer(Modifier.height(14.dp))

        WameedCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WameedTextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), fontSize = 12.sp, color = WameedTextSecondary)
            Text(stringResource(R.string.about_detail), fontSize = 12.sp, color = WameedTextSecondary)
            Spacer(Modifier.height(16.dp))
            WameedPrimaryButton(
                text = when (updateState) {
                    is UpdateState.UpToDate -> stringResource(R.string.app_up_to_date)
                    is UpdateState.Failed -> "تعذر التحقق، حاول لاحقاً"
                    else -> stringResource(R.string.check_for_updates)
                },
                onClick = {
                    scope.launch { updateManager.checkForUpdates(isManual = true) }
                },
                enabled = updateState !is UpdateState.Checking,
                loading = updateState is UpdateState.Checking
            )
        }
    }
}

@Composable
fun TrustedDevicesTab(modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var trustedIds by remember { mutableStateOf(WameedPrefs.getTrustedDevices(context).toList()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WameedMint)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WameedTextAction(text = stringResource(R.string.close), onClick = onBack, color = WameedTextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.trusted_devices_title), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = WameedGreen)
        }
        Spacer(Modifier.height(20.dp))

        if (trustedIds.isEmpty()) {
            WameedEmptyState(title = stringResource(R.string.no_trusted_devices))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trustedIds) { id ->
                    WameedCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulsingDot(color = WameedGreen, pulsing = false, size = 9.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(id, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = WameedTextPrimary)
                            }
                            WameedTextAction(text = stringResource(R.string.delete), color = WameedError, onClick = {
                                WameedPrefs.removeTrustedDevice(context, id)
                                trustedIds = WameedPrefs.getTrustedDevices(context).toList()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    state: ConnectionState,
    statusText: String,
    device: DeviceDiscovery.DiscoveredDevice?
) {
    val dotColor = when (state) {
        ConnectionState.Connected -> WameedSuccess
        ConnectionState.Stale -> WameedWarning
        ConnectionState.Discovered -> WameedWarning
        ConnectionState.Failed -> WameedError
        ConnectionState.Rejected -> WameedError
        ConnectionState.Connecting -> WameedWarning
        ConnectionState.PairingPending -> WameedWarning
        ConnectionState.Checking -> WameedTextMuted
        ConnectionState.Searching -> WameedInfo
        ConnectionState.Idle -> WameedTextMuted
    }
    val pulsing = state == ConnectionState.Connected || state == ConnectionState.Searching || state == ConnectionState.Connecting

    val displayText = when {
        state == ConnectionState.Connected && device != null -> {
            val name = if (device.name.isNotBlank() && device.name != device.ip && device.name != device.address)
                device.name else device.address
            stringResource(R.string.connected_to_device, name)
        }
        state == ConnectionState.Discovered && device != null -> {
            val name = if (device.name.isNotBlank() && device.name != device.ip) device.name else device.address
            stringResource(R.string.discovered_device, name)
        }
        state == ConnectionState.Stale && statusText.isNotBlank() -> statusText
        state == ConnectionState.Stale && device != null -> {
            val name = if (device.name.isNotBlank() && device.name != device.ip) device.name else device.address
            stringResource(R.string.connection_needs_check_device, name)
        }
        statusText.isNotBlank() -> statusText
        else -> stringResource(R.string.not_connected)
    }

    WameedCard(
        modifier = Modifier.fillMaxWidth(),
        selected = state == ConnectionState.Connected,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulsingDot(color = dotColor, pulsing = pulsing, size = 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = WameedTextPrimary
                )
                if (device != null && state == ConnectionState.Connected) {
                    Text(
                        device.address,
                        fontSize = 12.sp,
                        color = WameedTextSecondary
                    )
                }
            }
            if (state == ConnectionState.Connecting
                || state == ConnectionState.Searching
                || state == ConnectionState.Checking
                || state == ConnectionState.PairingPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = dotColor
                )
            }
            if (state == ConnectionState.Connected) {
                WameedBadge(text = stringResource(R.string.status_connected), color = WameedSuccess)
            }
        }
    }
}

@Composable
fun BatchProgressOverlay(
    label: String,
    currentFile: Int,
    totalFiles: Int,
    progress: Int,
    speed: Double,
    fileName: String,
    infoStatus: String = ""
) {
    WameedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 80.dp),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = WameedGreen
                )
                if (totalFiles > 1) {
                    Text(
                        text = "$currentFile / $totalFiles",
                        fontSize = 12.sp,
                        color = WameedTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (infoStatus.isNotEmpty()) {
                    Text(
                        text = infoStatus,
                        fontSize = 12.sp,
                        color = WameedWarning,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (speed > 0 && infoStatus.isEmpty()) {
                WameedBadge(text = "%.1f Mbps".format(speed), color = WameedGreen)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = fileName,
            fontSize = 13.sp,
            maxLines = 1,
            fontWeight = FontWeight.Medium,
            color = WameedTextSecondary
        )

        Spacer(Modifier.height(14.dp))
        WameedProgressBar(progress = progress / 100f)
        Spacer(Modifier.height(8.dp))

        Text(
            text = if (progress >= 100) stringResource(R.string.send_complete) else "$progress%",
            modifier = Modifier.align(Alignment.End),
            fontSize = 12.sp,
            color = if (progress >= 100) WameedSuccess else WameedTextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DeviceItem(
    device: DeviceDiscovery.DiscoveredDevice,
    isSelected: Boolean,
    isConnecting: Boolean,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    val dotColor = when {
        isConnected -> WameedSuccess
        isConnecting -> WameedWarning
        isSelected -> WameedGreen
        else -> WameedTextMuted
    }

    WameedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() },
        selected = isSelected,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = dotColor, pulsing = isConnected || isConnecting, size = 10.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name.ifBlank { device.address },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = WameedTextPrimary
                )
                Text(device.address, fontSize = 12.sp, color = WameedTextSecondary)
            }
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = WameedWarning
                )
            } else if (isConnected) {
                WameedBadge(text = stringResource(R.string.status_connected), color = WameedSuccess)
            }
        }
    }
}

@Composable
fun QuickAction(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .height(90.dp)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) color.copy(alpha = 0.08f) else Color(0xFFF3F4F6),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon, null,
                tint = if (enabled) color else Color(0xFFBBBBBB),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (enabled) color else Color(0xFFBBBBBB)
            )
        }
    }
}
