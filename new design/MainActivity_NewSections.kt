content:
/* ============================================================================
 *  WAMEED — أقسام MainActivity.kt المعاد تصميمها
 *  
 *  ⚠️ هذا الملف يحتوي على الـ @Composable functions التي يجب أن تستبدل
 *     نظيراتها في MainActivity.kt الأصلي. حافظ على باقي منطق الـ Activity
 *     والـ State والـ Connection logic كما هو.
 *  
 *  الأقسام المُستبدلة:
 *    1. MainScreen Scaffold + TopAppBar + BottomBar
 *    2. HomeTab (الشاشة الرئيسية)
 *    3. StatusCard
 *    4. DeviceItem
 *    5. BatchProgressOverlay
 *    6. HistoryTab + HistoryItem
 *    7. ReceivedTab + ReceivedFileItem
 * ============================================================================ */

package com.wameed

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wameed.ui.components.*
import com.wameed.ui.theme.*

/* ─── Tab Definition (نصي فقط - بدون أيقونات حسب توجيه المستخدم) ──────────── */
private enum class WameedTab(val labelRes: Int) {
    Home(R.string.tab_home),
    History(R.string.tab_history),
    Received(R.string.tab_received),
    Settings(R.string.tab_settings)
}


/* ══════════════════════════════════════════════════════════════════════════
 *  📱 MainScreen — الـ Scaffold الرئيسي
 * ══════════════════════════════════════════════════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    connectionState: ConnectionState,
    statusText: String,
    selectedDevice: DeviceDiscovery.DiscoveredDevice?,
    devices: List<DeviceDiscovery.DiscoveredDevice>,
    isSendingBatch: Boolean,
    batchProgress: BatchProgressState?,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (DeviceDiscovery.DiscoveredDevice) -> Unit,
    onManualConnect: () -> Unit,
    onDiagnose: () -> Unit,
    onCancel: () -> Unit
) {
    var currentTab by remember { mutableStateOf(WameedTab.Home) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_title),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = WameedGreen
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = WameedMint
                )
                // لا توجد أيقونات في الـ TopBar - فقط الاسم
            )
        },
        bottomBar = {
            WameedBottomBar(
                current = currentTab,
                onSelect = { currentTab = it }
            )
        },
        containerColor = WameedMint
    ) { inner ->
        Box(modifier = Modifier.padding(inner)) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                label = "tabSwitch"
            ) { tab ->
                when (tab) {
                    WameedTab.Home -> HomeTab(
                        connectionState = connectionState,
                        statusText = statusText,
                        selectedDevice = selectedDevice,
                        devices = devices,
                        isSendingBatch = isSendingBatch,
                        onSend = onSend,
                        onRefresh = onRefresh,
                        onConnect = onConnect,
                        onManualConnect = onManualConnect,
                        onDiagnose = onDiagnose
                    )
                    WameedTab.History  -> HistoryTab(Modifier.fillMaxSize())
                    WameedTab.Received -> ReceivedTab(Modifier.fillMaxSize())
                    WameedTab.Settings -> SettingsTab(Modifier.fillMaxSize())
                }
            }

            // طبقة شريط تقدم الإرسال (تظهر أعلى المحتوى)
            this@Scaffold.let {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSendingBatch && batchProgress != null,
                    enter = slideInVertically(tween(300)) { it } + fadeIn(),
                    exit  = slideOutVertically(tween(250)) { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    batchProgress?.let {
                        BatchProgressOverlay(
                            label = it.label,
                            currentFile = it.currentFile,
                            totalFiles = it.totalFiles,
                            progress = it.progress,
                            speed = it.speed,
                            fileName = it.fileName,
                            onCancel = onCancel
                        )
                    }
                }
            }
        }
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  📑 WameedBottomBar — شريط تنقل نصي (بدون أيقونات)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
private fun WameedBottomBar(
    current: WameedTab,
    onSelect: (WameedTab) -> Unit
) {
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
            WameedTab.values().forEach { tab ->
                val isActive = tab == current
                val labelText = stringResource(tab.labelRes)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isActive) WameedGreen95 else Color.Transparent
                        )
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelText,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) WameedGreen else WameedTextSecondary
                    )
                }
            }
        }
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  🏠 HomeTab — الشاشة الرئيسية (مبسّطة جداً)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
fun HomeTab(
    connectionState: ConnectionState,
    statusText: String,
    selectedDevice: DeviceDiscovery.DiscoveredDevice?,
    devices: List<DeviceDiscovery.DiscoveredDevice>,
    isSendingBatch: Boolean,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (DeviceDiscovery.DiscoveredDevice) -> Unit,
    onManualConnect: () -> Unit,
    onDiagnose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        // ── 1. بطاقة الحالة (Pulsing Dot + Text) ───────────────────────
        StatusCard(connectionState, statusText, selectedDevice)

        Spacer(Modifier.height(20.dp))

        // ── 2. عنوان قسم الأجهزة + زر تحديث نصي ───────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(stringResource(R.string.section_devices))
            TextButton(
                onClick = onRefresh,
                enabled = connectionState != ConnectionState.Searching && !isSendingBatch
            ) {
                Text(
                    stringResource(R.string.action_refresh),
                    color = WameedGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ── 3. قائمة الأجهزة ───────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when {
                connectionState == ConnectionState.Searching && devices.isEmpty() -> {
                    EmptyStateCard(
                        title = stringResource(R.string.searching_devices),
                        showProgress = true
                    )
                }
                devices.isEmpty() -> {
                    EmptyStateCard(
                        title = stringResource(R.string.no_devices_found),
                        subtitle = stringResource(R.string.no_devices_hint)
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(devices, key = { it.address }) { device ->
                            DeviceItem(
                                device = device,
                                isSelected = device.address == selectedDevice?.address,
                                isConnecting = connectionState == ConnectionState.Connecting
                                    && device.address == selectedDevice?.address,
                                isConnected = connectionState == ConnectionState.Connected
                                    && device.address == selectedDevice?.address,
                                onClick = { onConnect(device) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── 4. الأزرار (الزر الأساسي الكبير + الثانوي نصي) ───────────────
        WameedPrimaryButton(
            text = stringResource(R.string.action_send_file),
            onClick = onSend,
            enabled = connectionState == ConnectionState.Connected && !isSendingBatch
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onManualConnect,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.action_manual_connect),
                color = WameedTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  🟢 StatusCard — بطاقة الحالة (نقطة نابضة + نص مختصر)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
fun StatusCard(
    state: ConnectionState,
    statusText: String,
    device: DeviceDiscovery.DiscoveredDevice?
) {
    val dotColor = when (state) {
        ConnectionState.Connected      -> WameedSuccess
        ConnectionState.Discovered     -> WameedWarning
        ConnectionState.Failed         -> WameedError
        ConnectionState.Rejected       -> WameedError
        ConnectionState.Connecting     -> WameedWarning
        ConnectionState.PairingPending -> WameedWarning
        ConnectionState.Checking       -> WameedTextMuted
        ConnectionState.Searching      -> WameedInfo
        ConnectionState.Idle           -> WameedTextMuted
    }

    val pulsing = state == ConnectionState.Connected
        || state == ConnectionState.Searching
        || state == ConnectionState.Connecting

    val displayText = when {
        state == ConnectionState.Connected && device != null -> {
            val name = if (device.name.isNotBlank() && device.name != device.ip) device.name else device.address
            stringResource(R.string.connected_to_device, name)
        }
        state == ConnectionState.Discovered && device != null -> {
            val name = if (device.name.isNotBlank() && device.name != device.ip) device.name else device.address
            stringResource(R.string.discovered_device, name)
        }
        statusText.isNotBlank() -> statusText
        else -> stringResource(R.string.not_connected)
    }

    WameedCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulsingDot(color = dotColor, pulsing = pulsing, size = 12)
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
            if (state == ConnectionState.Connecting || state == ConnectionState.Searching || state == ConnectionState.PairingPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = dotColor
                )
            }
        }
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  📱 DeviceItem — بطاقة جهاز (اسم + IP فقط، بدون أيقونات)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
fun DeviceItem(
    device: DeviceDiscovery.DiscoveredDevice,
    isSelected: Boolean,
    isConnecting: Boolean,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    val statusColor = when {
        isConnected  -> WameedSuccess
        isConnecting -> WameedWarning
        isSelected   -> WameedGreen
        else         -> WameedTextMuted
    }

    val statusLabel = when {
        isConnected  -> stringResource(R.string.status_connected)
        isConnecting -> stringResource(R.string.status_connecting)
        else         -> null
    }

    WameedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        selected = isSelected,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // النقطة الدالة على الحالة (تنبض إذا متصل)
            PulsingDot(color = statusColor, pulsing = isConnected || isConnecting, size = 10)
            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifBlank { device.address },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WameedTextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = device.address,
                    fontSize = 12.sp,
                    color = WameedTextSecondary
                )
            }

            // شارة الحالة كنص فقط (بدون أيقونة)
            if (statusLabel != null) {
                Surface(
                    color = statusColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }
        }
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  📊 BatchProgressOverlay — شريط تقدم الإرسال (overlay سفلي)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
fun BatchProgressOverlay(
    label: String,
    currentFile: Int,
    totalFiles: Int,
    progress: Int,
    speed: Double,
    fileName: String,
    onCancel: () -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 80.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = WameedGreen.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = WameedSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                }

                if (speed > 0) {
                    Surface(
                        color = WameedGreen95,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "%.1f Mbps".format(speed),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = WameedGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

            WameedProgressBar(progress = progress / 100f, height = 10)

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$progress%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progress >= 100) WameedSuccess else WameedTextSecondary
                )
                TextButton(onClick = onCancel) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = WameedError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  📂 ReceivedFileItem — بطاقة ملف مستقبَل (مبسّطة جداً)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
fun ReceivedFileItem(
    file: ReceivedFileInfo,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit = {}
) {
    WameedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WameedTextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${file.sizeText} · ${file.timeText}",
                    fontSize = 12.sp,
                    color = WameedTextSecondary
                )
            }

            // زر "فتح" نصي فقط (بدون أيقونة)
            TextButton(onClick = onOpen) {
                Text(
                    stringResource(R.string.action_open),
                    color = WameedGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


/* ══════════════════════════════════════════════════════════════════════════
 *  💡 EmptyStateCard — حالة فارغة (بدون أيقونات كبيرة)
 * ══════════════════════════════════════════════════════════════════════════ */
@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String? = null,
    showProgress: Boolean = false
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    color = WameedGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = WameedTextPrimary
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = WameedTextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}


/* ── Helper data classes (المتوقعة من باقي الكود) ──────────────────────────── */
data class BatchProgressState(
    val label: String,
    val currentFile: Int,
    val totalFiles: Int,
    val progress: Int,
    val speed: Double,
    val fileName: String
)

data class ReceivedFileInfo(
    val name: String,
    val sizeText: String,
    val timeText: String,
    val path: String
)