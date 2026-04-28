package com.wameed

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * يرسل المحتوى (ملف / نص / رابط) للكمبيوتر عبر WebSocket بأقصى سرعة
 */
class WameedSender(private val context: Context) {

    private val TAG = "WameedSender"

    companion object {
        // محرك اتصال واحد لسرعة البرق — مهلات كبيرة للملفات الضخمة
        private val client = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        /** Fast TCP reachability check before opening a WebSocket. */
        private fun isTcpReachable(ip: String, port: Int, timeoutMs: Int = 800): Boolean {
            return try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), timeoutMs)
                    true
                }
            } catch (_: Exception) { false }
        }

        /** Best-effort host liveness (ICMP). Used to distinguish "PC off" vs
         *  "PC on but Wameed crashed". May return false on some networks that
         *  block ICMP even if the host is up — so this is only a hint. */
        private fun isHostReachable(ip: String, timeoutMs: Int = 1000): Boolean {
            return try {
                java.net.InetAddress.getByName(ip).isReachable(timeoutMs)
            } catch (_: Exception) { false }
        }
    }

    interface SendCallback {
        fun onSuccess(message: String)
        fun onError(error: String)
        fun onProgress(percent: Int)
        fun onProgress(percent: Int, speedMbps: Double) { onProgress(percent) }
        /** Optional: called with informational / intermediate status like
         *  "waiting for pairing approval on the PC". Default: no-op. */
        fun onInfo(message: String) {}
    }

    /** Record a successful send so MainActivity can show "متصل" even without
     *  running its own TCP probe. Called on every onSuccess path. */
    private fun markSendSuccess() {
        val ip = WameedPrefs.getPcIp(context)
        val port = WameedPrefs.getPcPort(context)
        if (ip.isNotEmpty()) {
            WameedPrefs.setLastSendSuccess(context, ip, port)
        }
    }

    fun sendPing(callback: SendCallback) {
        // التحقق من أن العنوان متاح عبر TCP فقط دون فتح WebSocket كامل
        // هذا يمنع ظهور طلب الاقتران مرتين على الكمبيوتر عند الفحص المبدئي
        val ip = WameedPrefs.getPcIp(context)
        val port = WameedPrefs.getPcPort(context)
        
        if (ip.isEmpty()) {
            callback.onError(context.getString(R.string.error_pc_not_configured))
            return
        }

        Thread {
            if (isTcpReachable(ip, port, 1500)) {
                callback.onSuccess(context.getString(R.string.status_connected))
            } else {
                callback.onError(context.getString(R.string.status_pc_unavailable))
            }
        }.start()
    }

    fun sendText(text: String, callback: SendCallback) {
        val wsUrl = WameedPrefs.getWsUrl(context)
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        val payload = JSONObject().apply {
            put("type", if (isUrl) "url" else "text")
            if (isUrl) put("url", text) else put("text", text)
        }
        sendPayload(wsUrl, payload.toString(), callback)
    }

    fun sendFile(uri: Uri, callback: SendCallback) {
        val wsUrl = WameedPrefs.getWsUrl(context)
        if (wsUrl.contains("://:7788")) {
            callback.onError(context.getString(R.string.error_pc_not_configured))
            return
        }

        Thread {
            try {
                // Immediate visual feedback
                callback.onProgress(1)

                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val filename = getFilename(uri, mimeType)

                // حجم القطعة 256KB: توازن بين السرعة والتحكم في الـ queue.
                // 1MB chunks على شبكات بطيئة تملأ buffer OkHttp بسرعة وتتسبب في التجميد.
                val chunkSize = 256 * 1024

                // --- Resolve file size robustly ---
                // Many PDF providers (Drive, WhatsApp, Telegram, …) return
                // AssetFileDescriptor.UNKNOWN_LENGTH (-1). Images usually
                // expose a length so the old code "worked" for them only.
                // Strategy: OpenableColumns.SIZE → AssetFileDescriptor →
                // copy to cache file as last resort.
                var fileSize = resolveSize(uri)
                var tempFile: java.io.File? = null
                if (fileSize <= 0) {
                    // Last resort: stream into a cache file so we can measure it.
                    // Capped at 300MB to protect memory / disk on low-end devices.
                    // Show fake-but-believable progress (1–4%) during the probe
                    // so the user never sees a frozen bar for multi-second PDFs.
                    callback.onInfo(context.getString(R.string.preparing))
                    try {
                        tempFile = java.io.File.createTempFile("wameed_", ".bin", context.cacheDir)
                        val MAX = 300L * 1024 * 1024
                        contentResolver.openInputStream(uri)?.use { ins ->
                            java.io.FileOutputStream(tempFile).use { out ->
                                val buf = ByteArray(64 * 1024)
                                var total = 0L
                                var n: Int
                                var lastReport = 0L
                                while (ins.read(buf).also { n = it } > 0) {
                                    total += n
                                    if (total > MAX) {
                                        throw IOException(context.getString(R.string.error_file_too_large))
                                    }
                                    out.write(buf, 0, n)
                                    // Throttle UI updates to ~5/s; cap at 4% so
                                    // the real send-progress (starts at 5%)
                                    // remains monotonic.
                                    val now = System.currentTimeMillis()
                                    if (now - lastReport > 180) {
                                        val pct = 1 + ((total * 3) / MAX).toInt().coerceAtMost(3)
                                        callback.onProgress(pct)
                                        lastReport = now
                                    }
                                }
                                fileSize = total
                            }
                        }
                    } catch (e: Exception) {
                        try { tempFile?.delete() } catch (_: Exception) {}
                        Log.w(TAG, "cache-copy size probe failed: ${e.message}")
                        callback.onError(context.getString(R.string.error_read_failed, e.message ?: context.getString(R.string.error_unknown_reason)))
                        return@Thread
                    }
                }

                if (fileSize <= 0) {
                    try { tempFile?.delete() } catch (_: Exception) {}
                    callback.onError(context.getString(R.string.error_unknown_size))
                    return@Thread
                }

                // Use tempFile as the read source if we created one, otherwise
                // read directly from the uri. openReadStream() below abstracts this.
                val readSource: () -> java.io.InputStream? = {
                    if (tempFile != null) java.io.FileInputStream(tempFile)
                    else contentResolver.openInputStream(uri)
                }

                val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
                val request = Request.Builder().url(wsUrl).build()

                // Two-phase send: wait for `paired` (or legacy `hello`) response
                // BEFORE streaming file_meta/binary — otherwise a slow pairing
                // dialog on the PC would overflow the server's message queue.
                val transferStarted = java.util.concurrent.atomic.AtomicBoolean(false)
                val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
                val lastProgressMs = java.util.concurrent.atomic.AtomicLong(
                    System.currentTimeMillis())

                // Watchdog: guarantees the callback *always* resolves. Without
                // it, if the PC never replies (e.g. pairing dialog is hidden
                // behind other windows, or receiver crashed mid-transfer after
                // file_meta), OkHttp would silently wait `readTimeout` = 10min
                // and the phone UI looks frozen on "جاري الإرسال".
                //
                // DYNAMIC: OkHttp queues chunks to the OS socket buffer almost
                // instantly, but actual WiFi transmission of a large file takes
                // much longer. A fixed 25s timeout works for small images but
                // fires too early for PDFs (38MB+). Scale the window based on
                // file size assuming a worst-case WiFi speed of ~256 KB/s,
                // plus a 30s base buffer for handshake/save overhead.
                val watchdogMs = maxOf(30_000L, fileSize / (256L * 1024) * 1000L + 30_000L)
                val watchdogThread = Thread {
                    try {
                        while (!finishedFlag.get()) {
                            val idle = System.currentTimeMillis() -
                                lastProgressMs.get()
                            if (idle > watchdogMs) break
                            Thread.sleep((watchdogMs - idle + 500).coerceAtLeast(500))
                        }
                        if (finishedFlag.compareAndSet(false, true)) {
                            Log.w(TAG, "Watchdog fired — no progress for ${watchdogMs}ms")
                            try { tempFile?.delete() } catch (_: Exception) {}
                            callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                        }
                    } catch (_: InterruptedException) { /* normal exit */ }
                }.apply { isDaemon = true; start() }

                // Helper: refreshes the watchdog window on each real progress.
                fun bumpWatchdog() {
                    lastProgressMs.set(System.currentTimeMillis())
                }

                fun startTransfer(webSocket: WebSocket) {
                    if (!transferStarted.compareAndSet(false, true)) return
                    Thread {
                        try {
                            val meta = JSONObject().apply {
                                put("type", "file_meta")
                                put("filename", filename)
                                put("mime", mimeType)
                                put("size", fileSize)
                                put("chunks", totalChunks)
                            }
                            webSocket.send(meta.toString())
                            bumpWatchdog()
                            Log.i(TAG, "file_meta sent: $filename size=$fileSize chunks=$totalChunks")

                            readSource()?.use { inputStream ->
                                val buffer = ByteArray(chunkSize)
                                var idx = 0
                                var n: Int
                                var totalBytesSent = 0L
                                val startTime = System.currentTimeMillis()
                                var lastCalcTime = startTime
                                var lastCalcBytes = 0L

                                while (inputStream.read(buffer).also { n = it } > 0) {
                                    // انتظر حتى تفرغ الـ queue دون حد زمني ثابت —
                                // bumpWatchdog() يمنع الـ watchdog من الإطلاق طالما نحن نتقدم.
                                    while (webSocket.queueSize() > 4 * 1024 * 1024) {
                                        Thread.sleep(10)
                                        bumpWatchdog()
                                    }

                                    val chunkData = buffer.toByteString(0, n)
                                    var sent = webSocket.send(chunkData)
                                    
                                    if (!sent) {
                                        Log.w(TAG, "Chunk $idx send failed (queue full?), retrying...")
                                        Thread.sleep(100)
                                        sent = webSocket.send(chunkData)
                                    }

                                    if (!sent) {
                                        throw IOException(context.getString(R.string.error_chunk_send_failed))
                                    }

                                    idx++
                                    totalBytesSent += n
                                    
                                    // Calculate speed every 500ms
                                    val now = System.currentTimeMillis()
                                    if (now - lastCalcTime >= 500) {
                                        val deltaSec = (now - lastCalcTime) / 1000.0
                                        val deltaBytes = totalBytesSent - lastCalcBytes
                                        val speedMbps = (deltaBytes * 8.0) / (1024.0 * 1024.0 * deltaSec)
                                        
                                        callback.onProgress(5 + (idx * 90 / totalChunks), speedMbps)
                                        
                                        lastCalcTime = now
                                        lastCalcBytes = totalBytesSent
                                    } else {
                                        callback.onProgress(5 + (idx * 90 / totalChunks))
                                    }

                                    bumpWatchdog()
                                }
                                Log.i(TAG, "All $idx chunks successfully enqueued, awaiting saved ack")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "startTransfer failed", e)
                            if (finishedFlag.compareAndSet(false, true)) {
                                callback.onError(context.getString(R.string.error_send_failed, e.message ?: ""))
                                try { webSocket.close(1000, null) } catch (_: Exception) {}
                            }
                        } finally {
                            // Always clean up the cache probe file, success or failure.
                            try { tempFile?.delete() } catch (_: Exception) {}
                        }
                    }.start()
                }

                Log.i(TAG, "sendFile: opening WS to $wsUrl for $filename ($fileSize bytes)")
                client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        try {
                            Log.i(TAG, "WS opened, sending hello")
                            bumpWatchdog()
                            val hello = JSONObject().apply {
                                put("type", "hello")
                                put("device", WameedPrefs.getDeviceName())
                                put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                                put("app_version", "1.1.0")
                            }
                            webSocket.send(hello.toString())
                            // Do NOT start file_meta yet — wait for `paired` / `hello` reply.
                        } catch (e: Exception) {
                            if (finishedFlag.compareAndSet(false, true)) {
                                callback.onError(context.getString(R.string.error_open_ws_failed, e.message ?: ""))
                                try { webSocket.close(1000, null) } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (finishedFlag.get()) return
                        bumpWatchdog()
                        Log.d(TAG, "WS message: $text")
                        try {
                            val resp = JSONObject(text)
                            when (resp.optString("status")) {
                                "pairing_required" -> {
                                    Log.i(TAG, "PC requires pairing approval")
                                    callback.onInfo(context.getString(R.string.info_pairing_approval))
                                    // Pairing dialog may be invisible behind
                                    // other windows — give the user 3 minutes
                                    // to find and approve it.
                                    lastProgressMs.set(
                                        System.currentTimeMillis() + 3 * 60_000L)
                                }
                                "paired", "hello" -> {
                                    Log.i(TAG, "PC paired — starting transfer")
                                    startTransfer(webSocket)
                                }
                                "rejected" -> {
                                    if (finishedFlag.compareAndSet(false, true)) {
                                        val msg = resp.optString("message",
                                            context.getString(R.string.error_pairing_rejected))
                                        callback.onError(msg)
                                        try { webSocket.close(1000, null) } catch (_: Exception) {}
                                    }
                                }
                                "saved" -> {
                                    Log.i(TAG, "PC confirmed save: ${resp.optString("path")}")
                                    if (finishedFlag.compareAndSet(false, true)) {
                                        callback.onProgress(100)
                                        markSendSuccess()
                                        callback.onSuccess(context.getString(R.string.status_saved))
                                        try { webSocket.close(1000, null) } catch (_: Exception) {}
                                    }
                                }
                                "progress" -> {
                                    // Receiver confirms it got N bytes — real
                                    // network progress. Bump watchdog so large
                                    // files don't false-timeout.
                                    bumpWatchdog()
                                }
                                "error" -> {
                                    if (finishedFlag.compareAndSet(false, true)) {
                                        callback.onError(resp.optString("message",
                                            context.getString(R.string.error_save_failed)))
                                        try { webSocket.close(1000, null) } catch (_: Exception) {}
                                    }
                                }
                                else -> {
                                    // Unknown — assume success to avoid blocking.
                                    if (finishedFlag.compareAndSet(false, true)) {
                                        markSendSuccess()
                                        callback.onSuccess(context.getString(R.string.status_sent))
                                        try { webSocket.close(1000, null) } catch (_: Exception) {}
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            if (finishedFlag.compareAndSet(false, true)) {
                                markSendSuccess()
                                callback.onSuccess(context.getString(R.string.status_sent))
                                try { webSocket.close(1000, null) } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WS send failure: ${t.javaClass.simpleName}: ${t.message} | resp=${response?.code}", t)
                        if (finishedFlag.compareAndSet(false, true)) {
                            val msg = when {
                                t is java.net.ConnectException -> context.getString(R.string.error_connect_failed)
                                t is java.net.SocketTimeoutException -> context.getString(R.string.error_socket_timeout)
                                else -> context.getString(R.string.error_connection_dropped, t.message ?: context.getString(R.string.error_unknown_reason))
                            }
                            callback.onError(msg)
                        }
                    }
                })
            } catch (e: Exception) {
                callback.onError(context.getString(R.string.error_general, e.message ?: ""))
            }
        }.start()
    }

    private fun sendPayload(wsUrl: String, payload: String, callback: SendCallback) {
        val request = Request.Builder().url(wsUrl).build()
        // For ping we can skip pairing (server allows ping pre-trust). For
        // text/url we must wait for `paired` before sending the payload.
        val payloadType = try { JSONObject(payload).optString("type", "") } catch (_: Exception) { "" }
        val needsPairing = payloadType in setOf("text", "url")
        val payloadSent = java.util.concurrent.atomic.AtomicBoolean(false)
        val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = JSONObject().apply {
                    put("type", "hello")
                    put("device", WameedPrefs.getDeviceName())
                    put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                    put("app_version", "1.1.0")
                }
                webSocket.send(hello.toString())
                // For ping we fire the payload immediately (server responds pong
                // without requiring trust — used for TCP preflight and keep-alive).
                if (!needsPairing && payloadSent.compareAndSet(false, true)) {
                    webSocket.send(payload)
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (finishedFlag.get()) return
                val status = try { JSONObject(text).optString("status", "") } catch (_: Exception) { "" }
                when (status) {
                    "pairing_required" -> callback.onInfo(context.getString(R.string.info_waiting_approval))
                    "paired", "hello" -> {
                        if (needsPairing && payloadSent.compareAndSet(false, true)) {
                            webSocket.send(payload)
                        }
                    }
                    "rejected" -> {
                        if (finishedFlag.compareAndSet(false, true)) {
                            callback.onError(try { JSONObject(text).optString("message",
                                context.getString(R.string.error_pairing_rejected)) } catch (_: Exception) {
                                context.getString(R.string.error_pairing_rejected) })
                            try { webSocket.close(1000, null) } catch (_: Exception) {}
                        }
                    }
                    "pong", "saved" -> {
                        if (finishedFlag.compareAndSet(false, true)) {
                            markSendSuccess()
                            callback.onSuccess(context.getString(R.string.status_sent))
                            try { webSocket.close(1000, null) } catch (_: Exception) {}
                        }
                    }
                    else -> {
                        if (finishedFlag.compareAndSet(false, true)) {
                            markSendSuccess()
                            callback.onSuccess(context.getString(R.string.status_sent))
                            try { webSocket.close(1000, null) } catch (_: Exception) {}
                        }
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (finishedFlag.compareAndSet(false, true)) {
                    callback.onError(context.getString(R.string.error_connect_failed))
                }
            }
        })
    }

    /** Robust size lookup. Returns -1 if unknown.
     *  1) OpenableColumns.SIZE — works for most providers including PDF.
     *  2) AssetFileDescriptor.length — fallback.
     *  3) FileDescriptor stat size — bypasses some provider length-reporting issues.
     *  Caller is expected to handle -1 by copying to a cache file. */
    private fun resolveSize(uri: Uri): Long {
        // Method 1: Query OpenableColumns.SIZE (standard for ContentProviders)
        try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.SIZE),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx != -1 && !c.isNull(idx)) {
                        val sz = c.getLong(idx)
                        if (sz > 0) return sz
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "OpenableColumns.SIZE query failed: ${e.message}")
        }

        // Method 2 & 3: AssetFileDescriptor and direct fstat.
        // Some providers (like WhatsApp/Drive for PDFs) return -1 for .length
        // but the underlying FileDescriptor is still stat-able.
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0) return len

                // Method 3: Direct stat on the FD
                try {
                    val fd = afd.fileDescriptor
                    val size = android.system.Os.fstat(fd).st_size
                    if (size > 0) return size
                } catch (e2: Exception) {
                    Log.d(TAG, "fstat failed: ${e2.message}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "openAssetFileDescriptor failed: ${e.message}")
        }

        // Method 4: File URI direct access
        if (uri.scheme == "file") {
            try {
                val f = java.io.File(uri.path ?: "")
                if (f.exists()) return f.length()
            } catch (_: Exception) {}
        }

        return -1L
    }

    private fun getFilename(uri: Uri, mimeType: String): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) return cursor.getString(nameIndex) ?: ""
            }
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"
        return "wameed_file.$ext"
    }
}
