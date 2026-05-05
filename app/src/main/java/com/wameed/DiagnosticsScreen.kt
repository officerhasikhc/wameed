package com.wameed

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// ======================== Log Viewer ========================

@Composable
fun DiagLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf<WameedLogger.Level?>(null) }
    var entries by remember { mutableStateOf(WameedLogger.getEntries()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            entries = WameedLogger.getEntries(filter)
        }
        WameedLogger.addListener(listener)
        onDispose { WameedLogger.removeListener(listener) }
    }

    LaunchedEffect(filter) {
        entries = WameedLogger.getEntries(filter)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF2E7D32))
            }
            Text(
                stringResource(R.string.diag_log_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { WameedLogger.shareLog(context) }) {
                Icon(Icons.Default.Share, null, tint = Color(0xFF3B82F6))
            }
            IconButton(onClick = {
                WameedLogger.clear()
                entries = emptyList()
            }) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
            }
        }

        // Filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipItem(stringResource(R.string.diag_filter_all), filter == null) { filter = null }
            FilterChipItem(stringResource(R.string.diag_filter_errors), filter == WameedLogger.Level.ERROR) { filter = WameedLogger.Level.ERROR }
            FilterChipItem(stringResource(R.string.diag_filter_warnings), filter == WameedLogger.Level.WARN) { filter = WameedLogger.Level.WARN }
            FilterChipItem(stringResource(R.string.diag_filter_network), filter == WameedLogger.Level.NET) { filter = WameedLogger.Level.NET }
        }

        // Log entries
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.diag_empty_log), color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(entries, key = { "${it.timestamp}_${it.message.hashCode()}" }) { entry ->
                    LogEntryRow(entry)
                }
            }

            // Auto-scroll to bottom when new entries arrive
            LaunchedEffect(entries.size) {
                if (entries.isNotEmpty()) {
                    listState.animateScrollToItem(entries.size - 1)
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color(0xFF2E7D32) else Color(0xFFE2E8F0)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color(0xFF475569)
        )
    }
}

@Composable
private fun LogEntryRow(entry: WameedLogger.LogEntry) {
    val bgColor = when (entry.level) {
        WameedLogger.Level.ERROR -> Color(0xFFFEF2F2)
        WameedLogger.Level.WARN -> Color(0xFFFFFBEB)
        WameedLogger.Level.NET -> Color(0xFFEFF6FF)
        else -> Color.White
    }
    val textColor = when (entry.level) {
        WameedLogger.Level.ERROR -> Color(0xFFDC2626)
        WameedLogger.Level.WARN -> Color(0xFFD97706)
        WameedLogger.Level.NET -> Color(0xFF2563EB)
        WameedLogger.Level.DEBUG -> Color(0xFF6B7280)
        else -> Color(0xFF1E293B)
    }
    val levelBadge = when (entry.level) {
        WameedLogger.Level.ERROR -> "ERR"
        WameedLogger.Level.WARN -> "WRN"
        WameedLogger.Level.NET -> "NET"
        WameedLogger.Level.DEBUG -> "DBG"
        WameedLogger.Level.INFO -> "INF"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                levelBadge,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(textColor.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.message,
                    fontSize = 11.sp,
                    color = textColor,
                    lineHeight = 14.sp
                )
                Text(
                    entry.format().substringBefore("]") + "]",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ======================== Network Diagnostics ========================

data class DiagResult(
    val name: String,
    val passed: Boolean,
    val timeMs: Long = 0,
    val detail: String = ""
)

data class ConnectionDiagnosis(
    val title: String,
    val summary: String,
    val likelyCauses: List<String>,
    val nextSteps: List<String>
)

@Composable
fun NetworkDiagScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<DiagResult>>(emptyList()) }
    var wifiInfo by remember { mutableStateOf("") }

    fun runDiagnostics() {
        isRunning = true
        results = emptyList()
        scope.launch {
            val newResults = mutableListOf<DiagResult>()

            // 1. WiFi info
            val wifi = withContext(Dispatchers.IO) { getWifiInfo(context) }
            wifiInfo = wifi

            // 2. UDP Discovery test
            val udpResult = withContext(Dispatchers.IO) { testUdpDiscovery(context) }
            newResults.add(udpResult)
            results = newResults.toList()

            // 3. TCP test
            val ip = WameedPrefs.getPcIp(context)
            val port = WameedPrefs.getPcPort(context)
            if (ip.isNotEmpty()) {
                val tcpResult = withContext(Dispatchers.IO) { testTcp(ip, port) }
                newResults.add(tcpResult)
                results = newResults.toList()

                // 4. ICMP Ping
                val pingResult = withContext(Dispatchers.IO) { testIcmpPing(ip) }
                newResults.add(pingResult)
                results = newResults.toList()

                // 5. WebSocket test
                val wsResult = withContext(Dispatchers.IO) { testWebSocket(context, ip, port) }
                newResults.add(wsResult)
                results = newResults.toList()
            } else {
                newResults.add(DiagResult("TCP", false, detail = "لم يتم تكوين عنوان الكمبيوتر"))
                newResults.add(DiagResult("ICMP", false, detail = "لم يتم تكوين عنوان الكمبيوتر"))
                newResults.add(DiagResult("WebSocket", false, detail = "لم يتم تكوين عنوان الكمبيوتر"))
                results = newResults.toList()
            }

            // Log results
            WameedLogger.net("Diagnostics", "=== Network Diagnostics ===")
            WameedLogger.net("Diagnostics", "WiFi: $wifi")
            newResults.forEach { r ->
                val status = if (r.passed) "PASS (${r.timeMs}ms)" else "FAIL: ${r.detail}"
                WameedLogger.net("Diagnostics", "${r.name}: $status")
            }

            isRunning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF2E7D32))
            }
            Text(
                stringResource(R.string.diag_net_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
            if (results.isNotEmpty()) {
                IconButton(onClick = {
                    shareDiagResults(context, wifiInfo, results)
                }) {
                    Icon(Icons.Default.Share, null, tint = Color(0xFF3B82F6))
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Run button
            Button(
                onClick = { runDiagnostics() },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diag_running))
                } else {
                    Text(stringResource(R.string.diag_run_tests), fontSize = 15.sp)
                }
            }

            // WiFi info card
            if (wifiInfo.isNotEmpty()) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 1.dp
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.diag_wifi_info),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            wifiInfo,
                            fontSize = 12.sp,
                            color = Color(0xFF475569),
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            if (results.isNotEmpty()) {
                val diagnosis = buildConnectionDiagnosis(context, wifiInfo, results)
                DiagnosisCard(diagnosis)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { copyConnectionIssue(context, wifiInfo, results) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("نسخ السبب", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { shareDiagnosticReport(context, wifiInfo, results) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ملف التشخيص", fontSize = 12.sp)
                    }
                }
            }

            // Test results
            results.forEach { result ->
                DiagResultCard(result)
            }

            // PC address info
            val pcIp = WameedPrefs.getPcIp(context)
            val pcPort = WameedPrefs.getPcPort(context)
            if (pcIp.isNotEmpty()) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("معلومات الكمبيوتر المحفوظة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("IP: $pcIp", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        Text("Port: $pcPort", fontSize = 12.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagResultCard(result: DiagResult) {
    val bgColor = if (result.passed) Color(0xFFF0FFF4) else Color(0xFFFEF2F2)
    val iconColor = if (result.passed) Color(0xFF22C55E) else Color(0xFFEF4444)

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (result.passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(result.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (result.passed) {
                    Text("${result.timeMs}ms", fontSize = 12.sp, color = Color(0xFF16A34A))
                } else {
                    Text(result.detail, fontSize = 12.sp, color = Color(0xFFDC2626), lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DiagnosisCard(diagnosis: ConnectionDiagnosis) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFBEB),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    diagnosis.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF92400E)
                )
            }

            Text(
                diagnosis.summary,
                fontSize = 12.sp,
                color = Color(0xFF78350F),
                lineHeight = 17.sp
            )

            if (diagnosis.likelyCauses.isNotEmpty()) {
                Text("الأسباب الأقرب:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                diagnosis.likelyCauses.take(3).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color(0xFF78350F), lineHeight = 17.sp)
                }
            }

            if (diagnosis.nextSteps.isNotEmpty()) {
                Text("ما يجب تجربته:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                diagnosis.nextSteps.take(3).forEach {
                    Text("• $it", fontSize = 12.sp, color = Color(0xFF78350F), lineHeight = 17.sp)
                }
            }
        }
    }
}

// ======================== Diagnostic Tests ========================

private fun getWifiInfo(context: Context): String {
    return try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo
        val dhcp = wifiManager.dhcpInfo
        val sb = StringBuilder()
        @Suppress("DEPRECATION")
        val ssid = info.ssid?.replace("\"", "") ?: "N/A"
        sb.appendLine("SSID: $ssid")
        sb.appendLine("RSSI: ${info.rssi} dBm (${WifiManager.calculateSignalLevel(info.rssi, 5)}/4)")
        sb.appendLine("Speed: ${info.linkSpeed} Mbps")
        val ip = intToIp(info.ipAddress)
        sb.appendLine("IP: $ip")
        sb.appendLine("Gateway: ${intToIp(dhcp.gateway)}")
        sb.appendLine("DNS: ${intToIp(dhcp.dns1)}")
        sb.toString().trim()
    } catch (e: Exception) {
        "WiFi info unavailable: ${e.message}"
    }
}

private fun intToIp(ip: Int): String {
    return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
}

private fun testUdpDiscovery(context: Context): DiagResult {
    return try {
        val start = System.currentTimeMillis()
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(0))
            broadcast = true
            soTimeout = 4000
        }
        val ping = JSONObject().apply {
            put("type", "discovery_ping")
            put("service", "wameed_phone")
            put("device", "diag_test")
        }
        val data = ping.toString().toByteArray(Charsets.UTF_8)
        val targets = linkedSetOf("255.255.255.255")
        getSubnetBroadcast()?.let { targets.add(it) }
        WameedPrefs.getPcIp(context).takeIf { it.isNotBlank() }?.let { targets.add(it) }
        targets.forEach { target ->
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName(target), 7789))
        }

        val buf = ByteArray(1024)
        val recv = DatagramPacket(buf, buf.size)
        socket.receive(recv)
        val elapsed = System.currentTimeMillis() - start
        socket.close()

        val response = String(recv.data, 0, recv.length, Charsets.UTF_8)
        val json = JSONObject(response)
        if (json.optString("service") == "wameed_pc") {
            DiagResult("UDP Discovery", true, elapsed, "PC: ${json.optString("name")} (${recv.address.hostAddress})")
        } else {
            DiagResult("UDP Discovery", false, elapsed, "رد غير متوقع: $response")
        }
    } catch (e: Exception) {
        val detail = when (e) {
            is SocketTimeoutException -> "لم يصل رد UDP على المنفذ 7789. قد يكون broadcast محجوباً أو وميض على الكمبيوتر غير مفتوح."
            else -> "${e.javaClass.simpleName}: ${e.message ?: "فشل غير معروف"}"
        }
        DiagResult("UDP Discovery", false, detail = detail)
    }
}

private fun getSubnetBroadcast(): String? {
    return try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
        for (iface in interfaces) {
            if (iface.isLoopback || !iface.isUp) continue
            for (addr in iface.interfaceAddresses) {
                addr.broadcast?.hostAddress?.let { return it }
            }
        }
        null
    } catch (_: Exception) {
        null
    }
}

private fun testTcp(ip: String, port: Int): DiagResult {
    return try {
        val start = System.currentTimeMillis()
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), 3000)
        }
        val elapsed = System.currentTimeMillis() - start
        DiagResult("TCP ($ip:$port)", true, elapsed)
    } catch (e: Exception) {
        val detail = when (e) {
            is ConnectException -> "رفض الاتصال: الجهاز موجود لكن منفذ وميض $port مغلق أو محجوب."
            is SocketTimeoutException -> "انتهت المهلة: غالباً Firewall أو عزل بين الأجهزة أو وميض لا يستمع على المنفذ."
            else -> "${e.javaClass.simpleName}: ${e.message ?: "فشل غير معروف"}"
        }
        DiagResult("TCP ($ip:$port)", false, detail = detail)
    }
}

private fun testIcmpPing(ip: String): DiagResult {
    return try {
        val start = System.currentTimeMillis()
        val reachable = InetAddress.getByName(ip).isReachable(3000)
        val elapsed = System.currentTimeMillis() - start
        if (reachable) {
            DiagResult("ICMP Ping ($ip)", true, elapsed)
        } else {
            DiagResult("ICMP Ping ($ip)", false, elapsed, "الجهاز لم يستجب (قد يكون ICMP محجوباً)")
        }
    } catch (e: Exception) {
        DiagResult("ICMP Ping ($ip)", false, detail = "${e.javaClass.simpleName}: ${e.message ?: "فشل غير معروف"}")
    }
}

private fun testWebSocket(context: Context, ip: String, port: Int): DiagResult {
    return try {
        val start = System.currentTimeMillis()
        val latch = CountDownLatch(1)
        var wsResult: DiagResult? = null

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url("ws://$ip:$port").build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject().apply {
                    put("type", "hello")
                    put("device", WameedPrefs.getDeviceName())
                    put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                    put("app_version", BuildConfig.VERSION_NAME)
                }
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val elapsed = System.currentTimeMillis() - start
                val resp = JSONObject(text)
                val status = resp.optString("status")
                wsResult = DiagResult("WebSocket ($ip:$port)", true, elapsed, "رد: $status")
                webSocket.close(1000, null)
                latch.countDown()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val detail = when (t) {
                    is ConnectException -> "فشل فتح WebSocket لأن منفذ TCP غير متاح."
                    is SocketTimeoutException -> "انتهت مهلة WebSocket: الاتصال يصل للجهاز لكن الخدمة لا ترد."
                    else -> "${t.javaClass.simpleName}: ${t.message ?: "فشل الاتصال"}"
                }
                wsResult = DiagResult("WebSocket ($ip:$port)", false, detail = detail)
                latch.countDown()
            }
        })

        latch.await(8, TimeUnit.SECONDS)
        wsResult ?: DiagResult("WebSocket ($ip:$port)", false, detail = "انتهت المهلة (Timeout)")
    } catch (e: Exception) {
        DiagResult("WebSocket ($ip:$port)", false, detail = e.message ?: "فشل غير معروف")
    }
}

private fun buildConnectionDiagnosis(
    context: Context,
    wifiInfo: String,
    results: List<DiagResult>
): ConnectionDiagnosis {
    val ip = WameedPrefs.getPcIp(context)
    val port = WameedPrefs.getPcPort(context)
    val udp = results.firstOrNull { it.name.startsWith("UDP") }
    val tcp = results.firstOrNull { it.name.startsWith("TCP") }
    val ping = results.firstOrNull { it.name.startsWith("ICMP") }
    val ws = results.firstOrNull { it.name.startsWith("WebSocket") }

    if (ip.isBlank()) {
        return ConnectionDiagnosis(
            title = "لم يتم حفظ عنوان الكمبيوتر",
            summary = "الهاتف لا يعرف أي IP للكمبيوتر، لذلك لا يمكن اختبار قناة الإرسال.",
            likelyCauses = listOf("لم يتم اختيار جهاز من البحث بعد", "تم مسح إعدادات التطبيق أو تغيرت الشبكة"),
            nextSteps = listOf("شغّل وميض على الكمبيوتر", "اضغط بحث من الهاتف ثم اختر الكمبيوتر", "أو أدخل IP الكمبيوتر يدوياً")
        )
    }

    val wsStatus = ws?.detail.orEmpty()
    val wsNeedsApproval = ws?.passed == true && wsStatus.contains("pairing_required", ignoreCase = true)

    return when {
        tcp?.passed == true && ws?.passed == true && !wsNeedsApproval -> ConnectionDiagnosis(
            title = "الاتصال الأساسي سليم",
            summary = "الهاتف وصل إلى $ip:$port وWebSocket رد بنجاح. إذا فشل الإرسال بعد ذلك فالمشكلة غالباً في الاقتران أو حفظ الملف أو صلاحيات الملف المرسل.",
            likelyCauses = listOf("موافقة الاقتران لم تحفظ على الطرف الآخر", "الملف نفسه من تطبيق لا يسمح بالقراءة المستمرة", "انقطاع Wi-Fi أثناء نقل ملف كبير"),
            nextSteps = listOf("جرّب إرسال نص صغير أولاً", "راقب سجل التشخيص أثناء الإرسال", "أعد الاقتران إذا تغير الجهاز أو أعدت تثبيت التطبيق")
        )

        wsNeedsApproval -> ConnectionDiagnosis(
            title = "القناة تعمل وتنتظر موافقة الاقتران",
            summary = "منفذ وميض مفتوح وWebSocket يعمل، لكن الكمبيوتر طلب موافقة الاقتران.",
            likelyCauses = listOf("هذا الهاتف غير موثوق بعد على الكمبيوتر", "تم حذف أجهزة الثقة أو أُعيد تثبيت التطبيق", "نافذة الموافقة على الكمبيوتر لم تُقبل"),
            nextSteps = listOf("افتح وميض على الكمبيوتر واضغط قبول", "بعد القبول أعد تشغيل الفحص", "إذا لا تظهر نافذة القبول امسح الثقة وأعد المحاولة")
        )

        ping?.passed == true && tcp?.passed != true -> ConnectionDiagnosis(
            title = "الجهاز موجود لكن منفذ وميض غير متاح",
            summary = "Ping إلى الكمبيوتر ناجح، لكن TCP/WebSocket على $ip:$port يفشل. هذا هو نفس تناقض الصور: ظهور الاسم لا يعني أن قناة الإرسال مفتوحة.",
            likelyCauses = listOf("Windows Firewall يمنع TCP 7788", "وميض على الكمبيوتر غير مفتوح أو لم يبدأ السيرفر", "الشبكة Public/VPN/Guest تعزل الاتصالات الداخلية"),
            nextSteps = listOf("افتح وميض على الكمبيوتر وتأكد أنه يعمل", "اسمح للمنفذ TCP 7788 وUDP 7789 في Firewall", "اجعل شبكة ويندوز Private وأوقف VPN مؤقتاً")
        )

        udp?.passed == true && tcp?.passed != true -> ConnectionDiagnosis(
            title = "الاكتشاف يعمل لكن الاتصال محجوب",
            summary = "الهاتف تلقى إعلان وميض عبر UDP، لكن الاتصال الفعلي على TCP لم ينجح.",
            likelyCauses = listOf("قاعدة UDP موجودة لكن TCP 7788 محجوب", "البرنامج يعلن نفسه بينما سيرفر WebSocket متوقف", "راوتر أو نقطة وصول تمنع TCP بين الأجهزة"),
            nextSteps = listOf("أعد تشغيل وميض على الكمبيوتر", "أضف قاعدة Firewall لمنفذ TCP 7788", "جرّب نفس الشبكة بدون Guest Wi-Fi أو Hotspot معزول")
        )

        tcp?.passed == true && ws?.passed != true -> ConnectionDiagnosis(
            title = "المنفذ مفتوح لكن WebSocket لا يكتمل",
            summary = "TCP يقبل الاتصال على $ip:$port، لكن بروتوكول وميض لا يحصل على رد WebSocket صحيح.",
            likelyCauses = listOf("نسخة الكمبيوتر قديمة أو عالقة", "خدمة أخرى تستخدم المنفذ 7788", "سيرفر وميض بدأ ثم تعطل داخلياً"),
            nextSteps = listOf("أغلق وميض من شريط المهام وافتحه من جديد", "تأكد أن Wameed.exe هو من يستمع على 7788", "ثبت نفس إصدار الحزمة على الهاتف والكمبيوتر")
        )

        udp?.passed != true && tcp?.passed == true -> ConnectionDiagnosis(
            title = "الاتصال اليدوي يعمل لكن الاكتشاف التلقائي محجوب",
            summary = "قناة الإرسال يمكن أن تعمل عبر IP محفوظ، لكن broadcast الخاص بالاكتشاف لا يصل.",
            likelyCauses = listOf("الراوتر يمنع UDP broadcast", "الشبكة Guest أو Enterprise", "بعض VPN/Hotspot يحجب حزم الاكتشاف"),
            nextSteps = listOf("استخدم الإدخال اليدوي للـ IP", "افتح UDP 7789 في Firewall", "غيّر الشبكة أو عطّل عزل الأجهزة في الراوتر")
        )

        else -> ConnectionDiagnosis(
            title = "لا يوجد مسار واضح إلى الكمبيوتر",
            summary = "الفحوصات لم تثبت وجود قناة مستقرة إلى $ip:$port.",
            likelyCauses = listOf("الهاتف والكمبيوتر ليسا على نفس الشبكة الفعلية", "IP الكمبيوتر تغير وبقي القديم محفوظاً", "الكمبيوتر نائم أو وميض غير مشغل أو الشبكة تعزل الأجهزة"),
            nextSteps = listOf("تأكد من نفس Wi-Fi وليس Guest", "أعد البحث من الهاتف لتحديث IP", "افتح وميض على الكمبيوتر وأعد الفحص")
        )
    }
}

private fun buildConnectionIssueText(
    context: Context,
    wifiInfo: String,
    results: List<DiagResult>,
    includeLogs: Boolean = false
): String {
    val diagnosis = buildConnectionDiagnosis(context, wifiInfo, results)
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
    val sb = StringBuilder()
    sb.appendLine("=== Wameed Connection Diagnosis ===")
    sb.appendLine("Time: $time")
    sb.appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} API ${android.os.Build.VERSION.SDK_INT}")
    sb.appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
    sb.appendLine("Phone receiver service: ${if (WameedConnectionService.isRunning) "running" else "stopped"}")
    sb.appendLine("PC: ${WameedPrefs.getPcName(context).ifBlank { "unknown" }}")
    sb.appendLine("PC address: ${WameedPrefs.getDisplayAddress(context).ifBlank { "not configured" }}")
    sb.appendLine("Keep-alive: ${WameedPrefs.isKeepAliveEnabled(context)}")
    sb.appendLine()
    sb.appendLine("--- Diagnosis ---")
    sb.appendLine(diagnosis.title)
    sb.appendLine(diagnosis.summary)
    sb.appendLine()
    sb.appendLine("Likely causes:")
    diagnosis.likelyCauses.forEach { sb.appendLine("- $it") }
    sb.appendLine()
    sb.appendLine("Next steps:")
    diagnosis.nextSteps.forEach { sb.appendLine("- $it") }
    sb.appendLine()
    sb.appendLine("--- WiFi ---")
    sb.appendLine(wifiInfo.ifBlank { "WiFi info unavailable" })
    sb.appendLine()
    sb.appendLine("--- Tests ---")
    results.forEach { r ->
        val status = if (r.passed) "PASS (${r.timeMs}ms)" else "FAIL"
        sb.appendLine("${r.name}: $status${if (r.detail.isNotBlank()) " | ${r.detail}" else ""}")
    }

    if (includeLogs) {
        sb.appendLine()
        sb.appendLine("--- Recent App Log ---")
        WameedLogger.getEntries().takeLast(160).forEach { entry ->
            sb.appendLine(entry.formatForFile())
        }
    }

    return sb.toString()
}

private fun copyConnectionIssue(context: Context, wifiInfo: String, results: List<DiagResult>) {
    val text = buildConnectionIssueText(context, wifiInfo, results, includeLogs = false)
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Wameed connection diagnosis", text))
    Toast.makeText(context, "تم نسخ سبب مشكلة الاتصال", Toast.LENGTH_SHORT).show()
}

private fun shareDiagnosticReport(context: Context, wifiInfo: String, results: List<DiagResult>) {
    try {
        val report = buildConnectionIssueText(context, wifiInfo, results, includeLogs = true)
        val file = File(context.filesDir, "wameed_diagnostic_report.txt")
        file.writeText(report, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, report.take(4000))
            putExtra(Intent.EXTRA_SUBJECT, "Wameed Diagnostic Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف التشخيص"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر إنشاء ملف التشخيص: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun shareDiagResults(context: Context, wifiInfo: String, results: List<DiagResult>) {
    shareDiagnosticReport(context, wifiInfo, results)
}
