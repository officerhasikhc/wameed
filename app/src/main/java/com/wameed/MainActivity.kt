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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
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
import com.wameed.ui.theme.WameedTheme
import com.wameed.BuildConfig
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
        
        // تشغيل خدمة الخلفية فوراً لضمان جاهزية الاستقبال
        WameedConnectionService.start(this)

        setContent {
            WameedTheme {
                MainScreen(sender, discovery, updateManager)
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

enum class ConnectionState { Idle, Checking, Searching, Connecting, PairingPending, Connected, Failed, Rejected }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(sender: WameedSender, discovery: DeviceDiscovery, updateManager: WameedUpdateManager) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var connectionState by remember { mutableStateOf(ConnectionState.Idle) }
    var statusText by remember { mutableStateOf("") }
    val devices = remember { mutableStateMapOf<String, DeviceDiscovery.DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<DeviceDiscovery.DiscoveredDevice?>(null) }
    val showManualDialog = remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    
    // Batch sending state
    var isSendingBatch by remember { mutableStateOf(false) }
    var currentFileIndex by remember { mutableIntStateOf(0) }
    var totalFilesCount by remember { mutableIntStateOf(0) }
    var currentFileProgress by remember { mutableIntStateOf(0) }
    var currentFileSpeed by remember { mutableStateOf(0.0) }
    var currentFileName by remember { mutableStateOf("") }
    var currentInfoStatus by remember { mutableStateOf("") }
    
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

    fun sendSelectedFiles() {
        if (selectedUris.isEmpty()) return
        
        val urisCopy = selectedUris.toList()
        isSendingBatch = true
        
        WameedConnectionService.start(context)
        
        sender.sendFiles(urisCopy, object : WameedSender.SendCallback {
            override fun onNextFile(index: Int, total: Int, fileName: String) {
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
                mainHandler.post {
                    isSendingBatch = false
                    selectedUris.clear()
                    currentInfoStatus = ""
                    Toast.makeText(context, context.getString(R.string.success_all_sent, urisCopy.size), Toast.LENGTH_SHORT).show()
                }
            }

            override fun onError(error: String) {
                mainHandler.post {
                    isSendingBatch = false
                    currentInfoStatus = ""
                    Toast.makeText(context, context.getString(R.string.error_prefix, error), Toast.LENGTH_LONG).show()
                }
            }

            override fun onInfo(message: String) {
                mainHandler.post {
                    currentInfoStatus = message
                }
            }
        })
    }

    fun connectToDevice(device: DeviceDiscovery.DiscoveredDevice) {
        selectedDevice = device
        connectionState = ConnectionState.Connecting
        statusText = context.getString(R.string.connecting_to, device.name)
        WameedPrefs.savePcAddress(context, device.address)

        if (device.name.isNotBlank() && device.name != device.ip) {
            WameedPrefs.setPcName(context, device.name)
        }

        sender.sendPing(object : WameedSender.SendCallback {
            override fun onSuccess(message: String) {
                mainHandler.post {
                    connectionState = ConnectionState.Connected
                    statusText = context.getString(R.string.connected_to, device.name)
                    WameedPrefs.setLastConnected(context)
                }
            }
            override fun onError(error: String) {
                mainHandler.post {
                    // التحقق إذا كان الخطأ بسبب رفض الاقتران
                    val isRejected = error.contains("رفض") || error.contains("rejected", ignoreCase = true)
                    connectionState = if (isRejected) ConnectionState.Rejected else ConnectionState.Failed
                    statusText = if (isRejected) {
                        context.getString(R.string.status_pairing_rejected)
                    } else {
                        context.getString(R.string.connection_failed_to, device.name)
                    }
                }
            }
            override fun onInfo(message: String) {
                mainHandler.post {
                    // عند انتظار موافقة الاقتران
                    if (message.contains("انتظار") || message.contains("waiting", ignoreCase = true)) {
                        connectionState = ConnectionState.PairingPending
                        statusText = context.getString(R.string.status_waiting_for_approval)
                    }
                }
            }
            override fun onProgress(percent: Int) {}
        })
    }

    fun startDiscovery() {
        connectionState = ConnectionState.Searching
        statusText = context.getString(R.string.searching_devices)
        devices.clear()
        selectedDevice = null

        discovery.startListening(context, callback = object : DeviceDiscovery.DiscoveryCallback {
            override fun onDeviceFound(device: DeviceDiscovery.DiscoveredDevice) {
                devices[device.address] = device
                if (device.name.isNotBlank() && device.name != device.ip) {
                    WameedPrefs.setPcName(context, device.name)
                }
                val savedName = WameedPrefs.getPcName(context)
                val savedIp = WameedPrefs.getPcIp(context)
                val savedPort = WameedPrefs.getPcPort(context)
                val sameName = device.name.isNotBlank()
                        && device.name != device.ip
                        && device.name.equals(savedName, ignoreCase = true)
                val ipChanged = savedIp.isNotEmpty()
                        && (savedIp != device.ip || savedPort != device.port)
                if (sameName && ipChanged) {
                    Log.i("Wameed",
                        "PC IP changed: $savedIp:$savedPort \u2192 ${device.ip}:${device.port} (name=${device.name})")
                    WameedPrefs.savePcAddress(context, "${device.ip}:${device.port}")
                    // فوراً قم بتحديث الخدمة للاتصال بالعنوان الجديد
                    WameedConnectionService.start(context)
                }
            }
            override fun onError(error: String) {
                connectionState = ConnectionState.Failed
                statusText = error
            }
            override fun onSearchFinished() {
                if (connectionState == ConnectionState.Searching) {
                    connectionState = ConnectionState.Idle
                    statusText = if (devices.isEmpty()) context.getString(R.string.no_devices_found)
                                 else context.getString(R.string.choose_device)
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
        val tcpOk = withContextIO { DeviceDiscovery.isTcpReachable(ip, port, 800) }
        val recentSend = WameedPrefs.getLastSendInfo(context)
            ?.takeIf { (System.currentTimeMillis() - it.first) < 60_000 }

        when {
            tcpOk -> {
                connectionState = ConnectionState.Connected
                val friendly = WameedPrefs.getPcName(context)
                selectedDevice = DeviceDiscovery.DiscoveredDevice(
                    name = friendly.ifBlank { WameedPrefs.getDisplayAddress(context) },
                    ip = ip, port = port
                )
                WameedPrefs.setLastConnected(context)
                statusText = if (recentSend != null) {
                    val ago = (System.currentTimeMillis() - recentSend.first) / 1000
                    context.getString(R.string.connected_last_send, ago)
                } else context.getString(R.string.connected_ready)
            }
            recentSend != null -> {
                connectionState = ConnectionState.Connected
                if (selectedDevice == null) {
                    val friendly = WameedPrefs.getPcName(context)
                    selectedDevice = DeviceDiscovery.DiscoveredDevice(
                        name = friendly.ifBlank { WameedPrefs.getDisplayAddress(context) },
                        ip = ip, port = port
                    )
                }
                val ago = (System.currentTimeMillis() - recentSend.first) / 1000
                statusText = context.getString(R.string.connected_last_send_short, ago)
            }
            else -> {
                connectionState = ConnectionState.Failed
                statusText = context.getString(R.string.pc_unavailable)
                selectedDevice = null
                if (triggerDiscoveryOnFailure) startDiscovery()
            }
        }
    }

    LaunchedEffect(Unit) { revalidate() }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        WameedEvents.events.collect { event ->
            if (event is WameedEvent.ServiceStatus) {
                if (event.isWsConnected) {
                    connectionState = ConnectionState.Connected
                    statusText = context.getString(R.string.connected_to, event.pcName)
                } else {
                    // Only downgrade if we were connected.
                    if (connectionState == ConnectionState.Connected) {
                        connectionState = ConnectionState.Failed
                        statusText = context.getString(R.string.connection_lost)
                    }
                }
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
        // We now rely on ServiceStatus events for background health,
        // but we still keep this as a fallback/active check when in foreground.
        var failures = 0
        while (connectionState == ConnectionState.Connected) {
            delay(10_000)
            val dev = selectedDevice ?: break
            val alive = withContextIO { DeviceDiscovery.isTcpReachable(dev.ip, dev.port, 1000) }
            if (alive) {
                failures = 0
                WameedPrefs.setLastConnected(context)
            } else {
                failures += 1
                if (failures >= 2) {
                    connectionState = ConnectionState.Failed
                    statusText = context.getString(R.string.connection_lost)
                    break
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_title), fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(stringResource(R.string.tab_connection)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text(stringResource(R.string.tab_history)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Download, null) },
                    label = { Text(stringResource(R.string.tab_received)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> ConnectionTab(
                modifier = Modifier.padding(padding),
                connectionState = connectionState,
                statusText = statusText,
                selectedDevice = selectedDevice,
                devices = devices,
                onConnect = { connectToDevice(it) },
                onRefresh = { startDiscovery() },
                onSend = { filePicker.launch(arrayOf("*/*")) },
                onManualConnect = { showManualDialog.value = true },
                selectedUris = selectedUris,
                onRemoveUri = { selectedUris.remove(it) },
                onConfirmSend = { sendSelectedFiles() },
                isSendingBatch = isSendingBatch
            )
            1 -> HistoryTab(modifier = Modifier.padding(padding))
            2 -> ReceivedTab(modifier = Modifier.padding(padding))
            3 -> SettingsTab(modifier = Modifier.padding(padding), updateManager = updateManager, onShowTrusted = { selectedTab = 4 })
            4 -> TrustedDevicesTab(modifier = Modifier.padding(padding), onBack = { selectedTab = 3 })
        }
    }

    // Update integration
    UpdateIntegration(updateManager, context)

    if (isSendingBatch) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            BatchProgressOverlay(
                currentFile = currentFileIndex,
                totalFiles = totalFilesCount,
                progress = currentFileProgress,
                speed = currentFileSpeed,
                fileName = currentFileName,
                infoStatus = currentInfoStatus
            )
        }
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
fun ConnectionTab(
    modifier: Modifier,
    connectionState: ConnectionState,
    statusText: String,
    selectedDevice: DeviceDiscovery.DiscoveredDevice?,
    devices: Map<String, DeviceDiscovery.DiscoveredDevice>,
    onConnect: (DeviceDiscovery.DiscoveredDevice) -> Unit,
    onRefresh: () -> Unit,
    onSend: () -> Unit,
    onManualConnect: () -> Unit,
    selectedUris: List<Uri> = emptyList(),
    onRemoveUri: (Uri) -> Unit = {},
    onConfirmSend: () -> Unit = {},
    isSendingBatch: Boolean = false
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        StatusCard(connectionState, statusText, selectedDevice)
        Spacer(modifier = Modifier.height(20.dp))

        if (selectedUris.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.attached_files, selectedUris.size),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedUris.forEach { uri ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                uri.path?.split("/")?.last() ?: uri.toString(),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                            IconButton(
                                onClick = { onRemoveUri(uri) }, 
                                modifier = Modifier.size(24.dp),
                                enabled = !isSendingBatch
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    null, 
                                    tint = if (isSendingBatch) Color.Gray else Color.Red, 
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onConfirmSend,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectionState == ConnectionState.Connected && !isSendingBatch,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSendingBatch) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSendingBatch) stringResource(R.string.sending) else stringResource(R.string.send_all))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(Modifier.weight(1f), stringResource(R.string.quick_action_send), Icons.Default.Add,
                Color(0xFF43A047), !isSendingBatch) { onSend() }
            QuickAction(Modifier.weight(1f), stringResource(R.string.quick_action_refresh), Icons.Default.Refresh,
                Color(0xFF3B82F6), connectionState != ConnectionState.Searching && !isSendingBatch) { onRefresh() }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.discovered_devices), fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
            TextButton(onClick = onManualConnect) {
                Icon(Icons.Default.Edit, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.manual_entry), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when {
                connectionState == ConnectionState.Searching -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.size(36.dp), color = Color(0xFF43A047), strokeWidth = 3.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.searching_devices), color = Color.Gray, fontSize = 13.sp)
                    }
                }
                devices.isEmpty() && connectionState != ConnectionState.Connecting -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.no_devices_found_detail),
                            color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(devices.values.toList(), key = { it.address }) { device ->
                            DeviceItem(device, selectedDevice?.address == device.address,
                                connectionState == ConnectionState.Connecting
                                        && selectedDevice?.address == device.address) { onConnect(device) }
                        }
                    }
                }
            }
        }

        if (connectionState == ConnectionState.Failed) {
            Button(onClick = { selectedDevice?.let { onConnect(it) } ?: onRefresh() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quick_action_refresh))
            }
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
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = Color(0xFF2E7D32))
            if (history.isNotEmpty()) {
                IconButton(onClick = {
                    WameedPrefs.clearHistory(context)
                    history = emptyList()
                }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.clear_history), tint = Color(0xFFEF4444))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.List, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.no_history), color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
    val statusColor = if (entry.status == "success") Color(0xFF22C55E) else Color(0xFFEF4444)
    val statusIcon = if (entry.status == "success") "\u2713" else "\u2717"
    val sizeText = formatSize(entry.size)
    val dirIcon = if (entry.direction == "received") "⬇" else "⬆"
    val dirText = if (entry.direction == "received") stringResource(R.string.direction_received) else stringResource(R.string.direction_sent)
    val dirColor = if (entry.direction == "received") Color(0xFF3B82F6) else Color(0xFF43A047)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(statusIcon, color = statusColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.filename, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dirIcon, color = dirColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("$dirText  •  ${entry.type}  •  $sizeText", fontSize = 11.sp, color = Color.Gray)
                }
            }
            Text(entry.time.substringAfter(" "), fontSize = 11.sp, color = Color.Gray)
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
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(stringResource(R.string.received_files_title), fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = Color(0xFF2E7D32))
            TextButton(onClick = {
                openWameedFolder(context)
            }) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.open_wameed_folder), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))

        TextButton(onClick = { files = listReceivedFiles(context) }) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.quick_action_refresh), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, tint = Color(0xFFD1D5DB), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.no_received_files), color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF3B82F6).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    fileTypeEmoji(file.mimeType),
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text("$sizeText  \u2022  $dateText", fontSize = 11.sp, color = Color.Gray)
            }
            IconButton(onClick = { openFile(context, file) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.OpenInNew, stringResource(R.string.open_file),
                    tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { shareFile(context, file) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, stringResource(R.string.share_file),
                    tint = Color(0xFF43A047), modifier = Modifier.size(20.dp))
            }
        }
    }
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
fun SettingsTab(modifier: Modifier, updateManager: WameedUpdateManager, onShowTrusted: () -> Unit) {
    val context = LocalContext.current
    var displayMode by remember { mutableStateOf(WameedPrefs.getDisplayMode(context)) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E7D32))
        Spacer(Modifier.height(20.dp))

        // Language toggle card
        var currentLang by remember { mutableStateOf(WameedPrefs.getLanguage(context)) }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newLang = if (currentLang == "ar") "en" else "ar"
                        WameedPrefs.setLanguage(context, newLang)
                        currentLang = newLang
                        (context as? android.app.Activity)?.recreate()
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.language_title),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.language_detail),
                        fontSize = 12.sp, color = Color.Gray)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (currentLang == "ar") "E" else "ع",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.pc_display_mode), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.pc_display_mode_detail), fontSize = 12.sp, color = Color.Gray)
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
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                displayMode = value
                                WameedPrefs.setDisplayMode(context, value)
                            }
                            .background(
                                if (displayMode == value) Color(0xFFE8F5E9) else Color.Transparent
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = displayMode == value,
                            onClick = {
                                displayMode = value
                                WameedPrefs.setDisplayMode(context, value)
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF2E7D32))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onShowTrusted() }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.trusted_devices_title),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.trusted_devices_detail),
                        fontSize = 12.sp, color = Color.Gray)
                }
                Icon(Icons.AutoMirrored.Filled.List, null, tint = Color.Gray)
            }
        }

        Spacer(Modifier.height(16.dp))

        var keepAlive by remember { mutableStateOf(WameedPrefs.isKeepAliveEnabled(context)) }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        keepAlive = !keepAlive
                        WameedPrefs.setKeepAliveEnabled(context, keepAlive)
                        if (!keepAlive) WameedConnectionService.stop(context)
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.keep_alive_title),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.keep_alive_detail),
                        fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = keepAlive,
                    onCheckedChange = {
                        keepAlive = it
                        WameedPrefs.setKeepAliveEnabled(context, it)
                        if (!it) WameedConnectionService.stop(context)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF2E7D32))
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.connection_info), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                val address = WameedPrefs.getDisplayAddress(context)
                if (address.isNotEmpty()) {
                    Text(stringResource(R.string.pc_label, address), fontSize = 13.sp, color = Color.Gray)
                } else {
                    Text(stringResource(R.string.not_connected), fontSize = 13.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Test Crash button (only in debug mode)
        if (BuildConfig.DEBUG) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFF5252).copy(alpha = 0.1f), shadowElevation = 1.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Test crash for Firebase Crashlytics
                            Log.d("FirebaseTest", "About to trigger test crash for Crashlytics")
                            Log.d("FirebaseTest", "Firebase App initialized: ${com.google.firebase.FirebaseApp.getInstance().name}")
                            throw RuntimeException("Test Crash - Firebase Crashlytics Testing")
                        }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("اختبار العطل",
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFFF5252))
                        Spacer(Modifier.height(4.dp))
                        Text("اضغط هنا لاختبار Firebase Crashlytics",
                            fontSize = 12.sp, color = Color(0xFFFF5252).copy(alpha = 0.7f))
                    }
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF5252))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bug Report button
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(context, WameedBugReportActivity::class.java)
                        context.startActivity(intent)
                    }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bug_report_title),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("الإبلاغ عن مشاكل في التطبيق",
                        fontSize = 12.sp, color = Color.Gray)
                }
                Icon(Icons.Default.BugReport, null, tint = Color(0xFF2E7D32))
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.about_title), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), fontSize = 12.sp, color = Color.Gray)
                Text(stringResource(R.string.about_detail), fontSize = 12.sp, color = Color.Gray)
                
                Spacer(Modifier.height(16.dp))
                
                val updateState by updateManager.updateState.collectAsState()
                val scope = rememberCoroutineScope()
                
                Button(
                    onClick = {
                        scope.launch {
                            updateManager.checkForUpdates(isManual = true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = updateState !is UpdateState.Checking,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (updateState is UpdateState.UpToDate) Color(0xFF2E7D32) else Color(0xFF43A047),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    if (updateState is UpdateState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.checking_updates), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Icon(
                            if (updateState is UpdateState.UpToDate) Icons.Default.CheckCircle else Icons.Default.Update, 
                            null, 
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when(updateState) {
                                is UpdateState.UpToDate -> stringResource(R.string.app_up_to_date)
                                is UpdateState.Failed -> "تعذر التحقق، حاول لاحقاً"
                                else -> stringResource(R.string.check_for_updates)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
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
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Home, null, tint = Color(0xFF2E7D32))
            }
            Text(stringResource(R.string.trusted_devices_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2E7D32))
        }
        Spacer(Modifier.height(20.dp))

        if (trustedIds.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_trusted_devices), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trustedIds) { id ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(id, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            IconButton(onClick = {
                                WameedPrefs.removeTrustedDevice(context, id)
                                trustedIds = WameedPrefs.getTrustedDevices(context).toList()
                            }) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                            }
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
        ConnectionState.Connected -> Color(0xFF22C55E)
        ConnectionState.Failed -> Color(0xFFEF4444)
        ConnectionState.Rejected -> Color(0xFFDC2626)
        ConnectionState.Connecting -> Color(0xFFFBBF24)
        ConnectionState.PairingPending -> Color(0xFFF97316)
        ConnectionState.Checking -> Color(0xFF9CA3AF)
        ConnectionState.Searching -> Color(0xFF3B82F6)
        ConnectionState.Idle -> Color(0xFF9CA3AF)
    }

    val bgColor = when (state) {
        ConnectionState.Connected -> Color(0xFFF0FFF4)
        ConnectionState.Failed -> Color(0xFFFFF5F5)
        ConnectionState.Rejected -> Color(0xFFFEF2F2)
        ConnectionState.Connecting -> Color(0xFFFFFBEB)
        ConnectionState.PairingPending -> Color(0xFFFFF7ED)
        ConnectionState.Checking -> Color(0xFFF3F4F6)
        ConnectionState.Searching -> Color(0xFFEFF6FF)
        ConnectionState.Idle -> Color.White
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(statusText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (device != null && state == ConnectionState.Connected) {
                    val hasFriendlyName = device.name.isNotBlank()
                            && device.name != device.ip
                            && device.name != device.address
                    if (hasFriendlyName) {
                        Text(
                            "\uD83D\uDCBB ${device.name}",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color(0xFF166534)
                        )
                    }
                    Text(device.address, fontSize = 12.sp, color = Color.Gray)
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(16.dp).padding(top = 4.dp)
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
        }
    }
}

@Composable
fun BatchProgressOverlay(
    currentFile: Int,
    totalFiles: Int,
    progress: Int,
    speed: Double,
    fileName: String,
    infoStatus: String = ""
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 80.dp), // Height of BottomBar
        shape = RoundedCornerShape(24.dp), // زوايا أكثر نعومة
        color = Color.White,
        shadowElevation = 12.dp // ظل أعمق لبروز البرق
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.sending_progress, currentFile, totalFiles),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF2E7D32)
                    )
                    if (infoStatus.isNotEmpty()) {
                        Text(
                            text = infoStatus,
                            fontSize = 12.sp,
                            color = Color(0xFFF97316), // لون برتقالي للتنبيهات اللحظية
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                if (speed > 0 && infoStatus.isEmpty()) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "%.1f Mbps".format(speed),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = fileName,
                fontSize = 13.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(contentAlignment = Alignment.CenterEnd) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = Color(0xFF43A047),
                    trackColor = Color(0xFFE8F5E9),
                    strokeCap = StrokeCap.Round
                )
            }
            
            if (progress >= 100) {
                 Text(
                    text = "✓ تم الإرسال",
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    fontSize = 11.sp,
                    color = Color(0xFF43A047),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DeviceItem(
    device: DeviceDiscovery.DiscoveredDevice,
    isSelected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFFE8F5E9) else Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF43A047).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    device.name.first().uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF43A047)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(device.address, fontSize = 12.sp, color = Color.Gray)
            }
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF43A047)
                )
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
