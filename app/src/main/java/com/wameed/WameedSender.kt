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
        /** Called when starting to send a new file in a batch. */
        fun onNextFile(index: Int, total: Int, fileName: String) {}
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
        // فتح WebSocket حقيقي والانتظار حتى اكتمال الاقتران
        // هذا يضمن أن الهاتف لا يظهر "متصل" أثناء انتظار موافقة الاقتران
        val ip = WameedPrefs.getPcIp(context)
        val port = WameedPrefs.getPcPort(context)

        if (ip.isEmpty()) {
            callback.onError(context.getString(R.string.error_pc_not_configured))
            return
        }

        Thread {
            // أولاً: التحقق السريع من TCP
            if (!isTcpReachable(ip, port, 1500)) {
                callback.onError(context.getString(R.string.status_pc_unavailable))
                return@Thread
            }

            // ثانياً: فتح WebSocket والانتظار حتى paired
            val wsUrl = WameedPrefs.getWsUrl(context)
            val request = Request.Builder().url(wsUrl).build()

            val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
            val lastProgressMs = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

            // Watchdog للاقتران: 3 دقائق (نافذة الاقتران قد تكون خلف نوافذ أخرى)
            val pairingTimeoutMs = 3 * 60_000L
            val watchdogThread = Thread {
                try {
                    while (!finishedFlag.get()) {
                        val idle = System.currentTimeMillis() - lastProgressMs.get()
                        if (idle > pairingTimeoutMs) {
                            if (finishedFlag.compareAndSet(false, true)) {
                                callback.onError(context.getString(R.string.error_pairing_timeout))
                            }
                            break
                        }
                        Thread.sleep(1000)
                    }
                } catch (_: InterruptedException) { }
            }.apply { isDaemon = true; start() }

            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    lastProgressMs.set(System.currentTimeMillis())
                    val hello = JSONObject().apply {
                        put("type", "hello")
                        put("device", WameedPrefs.getDeviceName())
                        put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                        put("app_version", BuildConfig.VERSION_NAME)
                    }
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    lastProgressMs.set(System.currentTimeMillis())
                    if (finishedFlag.get()) return

                    try {
                        val resp = JSONObject(text)
                        when (resp.optString("status")) {
                            "pairing_required" -> {
                                // الاقتران معلق - إعلام المستخدم بالانتظار
                                callback.onInfo(context.getString(R.string.status_waiting_for_approval))
                            }
                            "paired", "hello" -> {
                                // الاقتران ناجح!
                                if (finishedFlag.compareAndSet(false, true)) {
                                    markSendSuccess()
                                    callback.onSuccess(context.getString(R.string.status_connected))
                                    webSocket.close(1000, null)
                                }
                            }
                            "rejected" -> {
                                // الاقتران مرفوض
                                if (finishedFlag.compareAndSet(false, true)) {
                                    val msg = resp.optString("message",
                                        context.getString(R.string.error_pairing_rejected))
                                    callback.onError(msg)
                                    webSocket.close(1000, null)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (finishedFlag.compareAndSet(false, true)) {
                        val msg = when {
                            t is java.net.ConnectException -> context.getString(R.string.error_connect_failed)
                            t is java.net.SocketTimeoutException -> context.getString(R.string.error_socket_timeout)
                            else -> context.getString(R.string.error_connection_dropped, t.message ?: "")
                        }
                        callback.onError(msg)
                    }
                }
            })
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
        sendFiles(listOf(uri), callback)
    }

    /**
     * Sends multiple files over a single persistent WebSocket connection.
     * Reduces handshake overhead and improves batch performance.
     */
    fun sendFiles(uris: List<Uri>, callback: SendCallback) {
        if (uris.isEmpty()) return
        val wsUrl = WameedPrefs.getWsUrl(context)
        if (wsUrl.contains("://:7788")) {
            callback.onError(context.getString(R.string.error_pc_not_configured))
            return
        }

        Thread {
            val responseQueue = java.util.concurrent.LinkedBlockingQueue<JSONObject>()
            val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
            val lastProgressMs = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
            var currentWebSocket: WebSocket? = null

            // Global Watchdog: kills the operation if there is no activity for 2 minutes
            val watchdogThread = Thread {
                try {
                    while (!finishedFlag.get()) {
                        val idle = System.currentTimeMillis() - lastProgressMs.get()
                        if (idle > 120_000L) {
                            if (finishedFlag.compareAndSet(false, true)) {
                                Log.w(TAG, "Global batch watchdog fired")
                                callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                                currentWebSocket?.close(1000, null)
                            }
                            break
                        }
                        Thread.sleep(5000)
                    }
                } catch (_: InterruptedException) {}
            }.apply { isDaemon = true; start() }

            fun bumpWatchdog() = lastProgressMs.set(System.currentTimeMillis())

            val request = Request.Builder().url(wsUrl).build()
            currentWebSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    bumpWatchdog()
                    val hello = JSONObject().apply {
                        put("type", "hello")
                        put("device", WameedPrefs.getDeviceName())
                        put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                        put("app_version", BuildConfig.VERSION_NAME)
                    }
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    bumpWatchdog()
                    try {
                        val resp = JSONObject(text)
                        responseQueue.put(resp)
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (finishedFlag.compareAndSet(false, true)) {
                        val msg = when {
                            t is java.net.ConnectException -> context.getString(R.string.error_connect_failed)
                            t is java.net.SocketTimeoutException -> context.getString(R.string.error_socket_timeout)
                            else -> context.getString(R.string.error_connection_dropped, t.message ?: "")
                        }
                        callback.onError(msg)
                    }
                    responseQueue.put(JSONObject().apply { put("status", "ws_failure") })
                }
            })

            try {
                // Phase 1: Handshake/Pairing (once per session)
                var paired = false
                while (!finishedFlag.get() && !paired) {
                    val resp = responseQueue.poll(60, TimeUnit.SECONDS) ?: break
                    when (resp.optString("status")) {
                        "pairing_required" -> callback.onInfo(context.getString(R.string.info_pairing_approval))
                        "paired", "hello" -> paired = true
                        "rejected" -> {
                            if (finishedFlag.compareAndSet(false, true)) {
                                callback.onError(resp.optString("message", context.getString(R.string.error_pairing_rejected)))
                                currentWebSocket.close(1000, null)
                            }
                            return@Thread
                        }
                        "ws_failure" -> return@Thread
                    }
                }
                
                if (!paired) {
                    if (finishedFlag.compareAndSet(false, true)) {
                        callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                        currentWebSocket.close(1000, null)
                    }
                    return@Thread
                }

                // Phase 2: Sequential File Transfer
                for ((index, uri) in uris.withIndex()) {
                    if (finishedFlag.get()) break
                    
                    // تحديث فوري للواجهة لتعزيز شعور "الوميض" (السرعة)
                    callback.onProgress(0, 0.0)

                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val filename = getFilename(uri, mimeType)
                    callback.onNextFile(index + 1, uris.size, filename)

                    var fileSize = resolveSize(uri)
                    var tempFile: java.io.File? = null
                    
                    if (fileSize <= 0) {
                        callback.onInfo(context.getString(R.string.preparing))
                        try {
                            tempFile = java.io.File.createTempFile("wameed_", ".bin", context.cacheDir)
                            val MAX = 300L * 1024 * 1024
                            context.contentResolver.openInputStream(uri)?.use { ins ->
                                java.io.FileOutputStream(tempFile).use { out ->
                                    val buf = ByteArray(256 * 1024) // حجم أكبر لنسخ أسرع
                                    var total = 0L
                                    var n: Int
                                    while (ins.read(buf).also { n = it } > 0) {
                                        total += n
                                        if (total > MAX) throw IOException(context.getString(R.string.error_file_too_large))
                                        out.write(buf, 0, n)
                                        bumpWatchdog()
                                    }
                                    fileSize = total
                                }
                            }
                        } catch (e: Exception) {
                            callback.onError(context.getString(R.string.error_read_failed, e.message ?: ""))
                            finishedFlag.set(true)
                            break
                        }
                    }

                    if (fileSize <= 0) {
                        callback.onError(context.getString(R.string.error_unknown_size))
                        finishedFlag.set(true)
                        break
                    }

                    val chunkSize = 1024 * 1024
                    val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()

                    // Send metadata
                    val meta = JSONObject().apply {
                        put("type", "file_meta")
                        put("filename", filename)
                        put("mime", mimeType)
                        put("size", fileSize)
                        put("chunks", totalChunks)
                        put("display_mode", WameedPrefs.getDisplayMode(context))
                    }
                    currentWebSocket.send(meta.toString())
                    bumpWatchdog()

                    // Stream binary data
                    val inputStream = if (tempFile != null) java.io.FileInputStream(tempFile) 
                                      else context.contentResolver.openInputStream(uri)
                    
                    try {
                        inputStream?.use { stream ->
                            val buffer = ByteArray(chunkSize)
                            var chunkIdx = 0
                            var n: Int
                            var totalBytesSent = 0L
                            var lastCalcTime = System.currentTimeMillis()
                            var lastCalcBytes = 0L

                            while (stream.read(buffer).also { n = it } > 0) {
                                if (finishedFlag.get()) break
                                
                                // Flow control: throttle if OS buffer is full
                                // زيادة سعة المخزن المؤقت لضمان تدفق مستمر كالبرق
                                while (currentWebSocket!!.queueSize() > 16 * 1024 * 1024) {
                                    Thread.sleep(1) // تقليل وقت الانتظار لزيادة الاستجابة
                                    bumpWatchdog()
                                }

                                val chunkData = buffer.toByteString(0, n)
                                if (!currentWebSocket!!.send(chunkData)) {
                                    throw IOException("Failed to enqueue chunk")
                                }

                                totalBytesSent += n
                                chunkIdx++

                                val now = System.currentTimeMillis()
                                if (now - lastCalcTime >= 100) { // تحديث كل 100 ملي ثانية بدلاً من 500 لزيادة النعومة
                                    val deltaSec = (now - lastCalcTime) / 1000.0
                                    val deltaBytes = totalBytesSent - lastCalcBytes
                                    val speedMbps = (deltaBytes * 8.0) / (1024.0 * 1024.0 * deltaSec)
                                    // نصل إلى 98% بحد أقصى أثناء الإرسال الفعلي
                                    val progress = 5 + (chunkIdx * 93 / totalChunks) 
                                    callback.onProgress(progress, speedMbps)
                                    lastCalcTime = now
                                    lastCalcBytes = totalBytesSent
                                }
                                bumpWatchdog()
                            }
                        }
                    } catch (e: Exception) {
                        callback.onError(context.getString(R.string.error_send_failed, e.message ?: ""))
                        finishedFlag.set(true)
                    } finally {
                        tempFile?.delete()
                    }

                    if (finishedFlag.get()) break

                    // ننتظر الرد من الكمبيوتر. نقوم بعمل poll قصير أولاً قبل إظهار حالة "جاري الحفظ"
                    // لتجنب رعشة الواجهة (Flicker) في الملفات السريعة.
                    val fileWatchdogMs = maxOf(30_000L, fileSize / (256L * 1024) * 1000L + 30_000L)
                    var ackReceived = false
                    val ackStart = System.currentTimeMillis()
                    
                    var firstPoll = true
                    while (System.currentTimeMillis() - ackStart < fileWatchdogMs) {
                        // في أول محاولة، ننتظر 150ms فقط، إذا وصل الرد نتجاوز رسالة "جاري الحفظ"
                        val pollTimeout = if (firstPoll) 150L else 50L
                        val resp = responseQueue.poll(pollTimeout, TimeUnit.MILLISECONDS)
                        
                        if (resp == null) {
                            if (firstPoll && !finishedFlag.get()) {
                                callback.onInfo(context.getString(R.string.status_saving_on_pc))
                                firstPoll = false
                            }
                            continue
                        }

                        val status = resp.optString("status")
                        if (status == "saved") {
                            ackReceived = true
                            callback.onProgress(100) // اكتمال حقيقي عند الحفظ
                            break
                        } else if (status == "error") {
                            callback.onError(resp.optString("message", context.getString(R.string.error_save_failed)))
                            finishedFlag.set(true)
                            break
                        } else if (status == "ws_failure") {
                            finishedFlag.set(true)
                            break
                        }
                        bumpWatchdog()
                    }

                    if (!ackReceived && !finishedFlag.get()) {
                        callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                        finishedFlag.set(true)
                        break
                    }
                }

                if (!finishedFlag.get()) {
                    markSendSuccess()
                    callback.onSuccess(context.getString(R.string.status_saved))
                }

            } catch (e: Exception) {
                if (finishedFlag.compareAndSet(false, true)) {
                    callback.onError(context.getString(R.string.error_general, e.message ?: ""))
                }
            } finally {
                currentWebSocket?.close(1000, null)
                finishedFlag.set(true)
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
                    put("app_version", BuildConfig.VERSION_NAME)
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
