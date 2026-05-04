package com.wameed

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
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
            val udpResult = withContext(Dispatchers.IO) { testUdpDiscovery() }
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
                val wsResult = withContext(Dispatchers.IO) { testWebSocket(ip, port) }
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

private fun testUdpDiscovery(): DiagResult {
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
        val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), 7788)
        socket.send(packet)

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
        DiagResult("UDP Discovery", false, detail = e.message ?: "فشل غير معروف")
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
        DiagResult("TCP ($ip:$port)", false, detail = e.message ?: "فشل غير معروف")
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
        DiagResult("ICMP Ping ($ip)", false, detail = e.message ?: "فشل غير معروف")
    }
}

private fun testWebSocket(ip: String, port: Int): DiagResult {
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
                    put("device", "diag_test")
                    put("device_id", "diag_probe")
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
                wsResult = DiagResult("WebSocket ($ip:$port)", false, detail = t.message ?: "فشل الاتصال")
                latch.countDown()
            }
        })

        latch.await(8, TimeUnit.SECONDS)
        wsResult ?: DiagResult("WebSocket ($ip:$port)", false, detail = "انتهت المهلة (Timeout)")
    } catch (e: Exception) {
        DiagResult("WebSocket ($ip:$port)", false, detail = e.message ?: "فشل غير معروف")
    }
}

private fun shareDiagResults(context: Context, wifiInfo: String, results: List<DiagResult>) {
    val sb = StringBuilder()
    sb.appendLine("=== Wameed Network Diagnostics ===")
    sb.appendLine(WameedLogger.getDiagnosticSummary(context))
    sb.appendLine("--- WiFi ---")
    sb.appendLine(wifiInfo)
    sb.appendLine("--- Tests ---")
    results.forEach { r ->
        val status = if (r.passed) "✅ PASS (${r.timeMs}ms)" else "❌ FAIL: ${r.detail}"
        sb.appendLine("${r.name}: $status")
    }

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
        putExtra(android.content.Intent.EXTRA_SUBJECT, "Wameed Network Diagnostics")
    }
    context.startActivity(android.content.Intent.createChooser(intent, "مشاركة نتائج التشخيص"))
}
