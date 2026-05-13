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
        private const val TRANSFER_PROTOCOL_VERSION = 2
        private const val TRANSFER_CHUNK_SIZE = 512 * 1024
        private const val WS_QUEUE_SOFT_LIMIT = 8L * 1024 * 1024
        private const val PEER_IN_FLIGHT_LIMIT = 8L * 1024 * 1024
        private const val CHUNK_ENQUEUE_TIMEOUT_MS = 120_000L

        // محرك اتصال للعمليات العامة (ping, persistent WS) مع ping دوري للحفاظ على الاتصال
        private val client = OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()

        // محرك اتصال مخصص لإرسال الملفات — بدون pingInterval لمنع Broken Pipe
        // أثناء إرسال الملفات أو انتظار تأكيد الحفظ
        private val sendClient = OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .pingInterval(0, TimeUnit.SECONDS)
            .build()

        // ⚡ Persistent WebSocket — اتصال دائم للإرسال الفوري
        @Volatile
        private var persistentWs: WebSocket? = null
        @Volatile
        private var persistentPaired = false
        @Volatile
        private var persistentIp: String = ""
        @Volatile
        private var persistentPort: Int = 0
        private val persistentLock = Any()

        /** Fast TCP reachability check before opening a WebSocket. */
        private fun isTcpReachable(ip: String, port: Int, timeoutMs: Int = 2000): Boolean {
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
        private fun isHostReachable(ip: String, timeoutMs: Int = 2000): Boolean {
            return try {
                java.net.InetAddress.getByName(ip).isReachable(timeoutMs)
            } catch (_: Exception) { false }
        }

        // ======================== Persistent WebSocket ========================

        /** فتح اتصال دائم مع الكمبيوتر (يُستدعى بعد أول اقتران ناجح) */
        fun openPersistent(context: Context) {
            val ip = WameedPrefs.getPcIp(context)
            val port = WameedPrefs.getPcPort(context)
            if (ip.isEmpty()) return

            synchronized(persistentLock) {
                // إذا الاتصال قائم ونفس العنوان، لا نعيد الفتح
                if (persistentWs != null && persistentIp == ip && persistentPort == port) return
                // أغلق القديم
                closePersistent()

                persistentIp = ip
                persistentPort = port
                persistentPaired = false

                val wsUrl = "ws://$ip:$port"
                Log.i("WameedSender", "⚡ فتح اتصال دائم: $wsUrl")
                WameedLogger.net("WameedSender", "فتح اتصال دائم: $wsUrl")
                val request = Request.Builder().url(wsUrl).build()
                persistentWs = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i("WameedSender", "⚡ Persistent WS مفتوح، إرسال hello")
                        WameedLogger.net("WameedSender", "Persistent WS مفتوح")
                        val hello = JSONObject().apply {
                            put("type", "hello")
                            put("device", WameedPrefs.getDeviceName())
                            put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                            put("app_version", BuildConfig.VERSION_NAME)
                        }
                        webSocket.send(hello.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val resp = JSONObject(text)
                            when (resp.optString("status")) {
                                "paired", "hello" -> {
                                    persistentPaired = true
                                    Log.i("WameedSender", "⚡ Persistent WS مقترن وجاهز")
                                    WameedLogger.i("WameedSender", "Persistent WS مقترن وجاهز")
                                }
                                "rejected" -> {
                                    Log.w("WameedSender", "⚡ Persistent WS مرفوض")
                                    closePersistent()
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w("WameedSender", "⚡ Persistent WS فشل: ${t.message}")
                        WameedLogger.e("WameedSender", "Persistent WS فشل: ${t.javaClass.simpleName} — ${t.message}", t)
                        synchronized(persistentLock) {
                            persistentWs = null
                            persistentPaired = false
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i("WameedSender", "⚡ Persistent WS أُغلق: $reason")
                        synchronized(persistentLock) {
                            persistentWs = null
                            persistentPaired = false
                        }
                    }
                })
            }
        }

        /** إغلاق الاتصال الدائم */
        fun closePersistent() {
            synchronized(persistentLock) {
                try { persistentWs?.close(1000, null) } catch (_: Exception) {}
                persistentWs = null
                persistentPaired = false
                persistentIp = ""
                persistentPort = 0
            }
        }

        /** هل الاتصال الدائم جاهز؟ */
        fun isPersistentReady(): Boolean = persistentWs != null && persistentPaired

        /** إرسال نص/رابط عبر الاتصال الدائم (فوري — بدون handshake) */
        fun sendViaPersistent(payload: String): Boolean {
            synchronized(persistentLock) {
                val ws = persistentWs ?: return false
                if (!persistentPaired) return false
                return try {
                    ws.send(payload)
                } catch (_: Exception) { false }
            }
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
        val ip = WameedPrefs.getPcIp(context)
        val port = WameedPrefs.getPcPort(context)

        Log.i(TAG, "محاولة الاتصال (Ping) بـ $ip:$port")
        WameedLogger.net(TAG, "محاولة اتصال (Ping) بـ $ip:$port")

        if (ip.isEmpty()) {
            Log.w(TAG, "فشل الإرسال: لم يتم تكوين عنوان IP للكمبيوتر")
            WameedLogger.w(TAG, "لم يتم تكوين عنوان IP")
            callback.onError(context.getString(R.string.error_pc_not_configured))
            return
        }

        // ⚡ إذا الاتصال الدائم جاهز، نجاح فوري
        if (isPersistentReady()) {
            Log.i(TAG, "⚡ الاتصال الدائم جاهز — Ping فوري")
            markSendSuccess()
            callback.onSuccess(context.getString(R.string.status_connected))
            return
        }

        // فتح WebSocket مباشرة (بدون TCP check — connectTimeout يكفي)
        val wsUrl = WameedPrefs.getWsUrl(context)
        val request = Request.Builder().url(wsUrl).build()

        val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        val lastProgressMs = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())

        // Watchdog للاقتران: 3 دقائق
        val pairingTimeoutMs = 3 * 60_000L
        val watchdogThread = Thread {
            try {
                while (!finishedFlag.get()) {
                    val idle = System.currentTimeMillis() - lastProgressMs.get()
                    if (idle > pairingTimeoutMs) {
                        if (finishedFlag.compareAndSet(false, true)) {
                            WameedLogger.e(TAG, "انتهت مهلة الاقتران (Timeout) لـ $ip")
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
                Log.i(TAG, "تم فتح WebSocket لـ $ip، إرسال تحية 'hello'...")
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
                Log.v(TAG, "استلام رسالة من $ip: $text")
                lastProgressMs.set(System.currentTimeMillis())
                if (finishedFlag.get()) return

                try {
                    val resp = JSONObject(text)
                    when (resp.optString("status")) {
                        "pairing_required" -> {
                            Log.i(TAG, "الكمبيوتر يطلب الاقتران (Pairing Required)")
                            callback.onInfo(context.getString(R.string.status_waiting_for_approval))
                        }
                        "paired", "hello" -> {
                            Log.i(TAG, "✅ تم الاتصال بنجاح مع $ip")
                            WameedLogger.i(TAG, "✅ تم الاتصال بنجاح مع $ip")
                            if (finishedFlag.compareAndSet(false, true)) {
                                markSendSuccess()
                                callback.onSuccess(context.getString(R.string.status_connected))
                                webSocket.close(1000, null)
                                // ⚡ فتح اتصال دائم بعد الاقتران الناجح
                                openPersistent(context)
                            }
                        }
                        "rejected" -> {
                            Log.w(TAG, "❌ الكمبيوتر رفض الاتصال")
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
                WameedLogger.e(TAG, "فشل WebSocket ($ip): ${t.javaClass.simpleName} — ${t.message}", t)
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
    }

    fun sendText(text: String, callback: SendCallback) {
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        val payload = JSONObject().apply {
            put("type", if (isUrl) "url" else "text")
            if (isUrl) put("url", text) else put("text", text)
        }

        // ⚡ محاولة الإرسال الفوري عبر الاتصال الدائم
        if (isPersistentReady()) {
            Log.i(TAG, "⚡ إرسال فوري عبر الاتصال الدائم")
            if (sendViaPersistent(payload.toString())) {
                markSendSuccess()
                callback.onSuccess(context.getString(R.string.status_sent))
                return
            }
            Log.w(TAG, "⚡ فشل الإرسال الفوري، التراجع للطريقة العادية")
        }

        // Fallback: الطريقة القديمة
        val wsUrl = WameedPrefs.getWsUrl(context)
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
        val ip = WameedPrefs.getPcIp(context)
        val wsUrl = WameedPrefs.getWsUrl(context)
        
        Log.i(TAG, "بدء إرسال ${uris.size} ملفات إلى $ip")

        if (ip.isEmpty()) {
            Log.w(TAG, "فشل الإرسال: لم يتم تكوين الكمبيوتر")
            callback.onError(context.getString(R.string.error_pc_not_configured))
            return
        }

        Thread {
            val responseQueue = java.util.concurrent.LinkedBlockingQueue<JSONObject>()
            val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
            val lastProgressMs = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis())
            val currentIdleTimeoutMs = java.util.concurrent.atomic.AtomicLong(120_000L)
            val currentPhase = java.util.concurrent.atomic.AtomicReference("connect")
            val currentTransferSize = java.util.concurrent.atomic.AtomicLong(0L)
            val currentBytesDone = java.util.concurrent.atomic.AtomicLong(0L)
            val peerReceivedBytes = java.util.concurrent.atomic.AtomicLong(0L)
            // علامة: جميع البيانات أُرسلت وننتظر ACK فقط — لا نعتبر انقطاع الاتصال فشلاً
            val allDataSentFlag = java.util.concurrent.atomic.AtomicBoolean(false)
            var currentWebSocket: WebSocket? = null

            // Phase-aware watchdog: large files can spend longer in transfer/finalize
            // as long as the peer keeps sending progress or saving acknowledgements.
            val watchdogThread = Thread {
                try {
                    while (!finishedFlag.get()) {
                        val idle = System.currentTimeMillis() - lastProgressMs.get()
                        val timeout = currentIdleTimeoutMs.get()
                        if (idle > timeout) {
                            if (finishedFlag.compareAndSet(false, true)) {
                                val phase = currentPhase.get()
                                WameedCrashReporter.getInstance().setTransferDiagnostics(
                                    direction = "android_to_windows",
                                    phase = phase,
                                    sizeBytes = currentTransferSize.get(),
                                    bytesDone = currentBytesDone.get(),
                                    port = WameedPrefs.getPcPort(context),
                                    failureType = "network_timeout"
                                )
                                WameedLogger.e(TAG, "Batch watchdog fired in phase=$phase after ${idle}ms without peer activity")
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
            fun setPhase(phase: String, sizeBytes: Long = currentTransferSize.get(), bytesDone: Long = currentBytesDone.get()) {
                currentPhase.set(phase)
                currentTransferSize.set(sizeBytes)
                currentBytesDone.set(bytesDone)
                currentIdleTimeoutMs.set(
                    when (phase) {
                        "handshake" -> 60_000L
                        "transfer" -> 180_000L
                        "finalizing" -> maxOf(180_000L, sizeBytes / (2L * 1024 * 1024) * 1000L + 120_000L)
                        else -> 120_000L
                    }
                )
                WameedCrashReporter.getInstance().setTransferDiagnostics(
                    direction = "android_to_windows",
                    phase = phase,
                    sizeBytes = sizeBytes,
                    bytesDone = bytesDone,
                    port = WameedPrefs.getPcPort(context)
                )
                bumpWatchdog()
            }

            Log.d(TAG, "فتح WebSocket للإرسال المتعدد: $wsUrl")
            val request = Request.Builder().url(wsUrl).build()
            currentWebSocket = sendClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket مفتوح، إرسال التحية...")
                    bumpWatchdog()
                    val hello = JSONObject().apply {
                        put("type", "hello")
                        put("device", WameedPrefs.getDeviceName())
                        put("device_id", WameedPrefs.getOrCreateDeviceId(context))
                        put("app_version", BuildConfig.VERSION_NAME)
                        put("protocol_version", TRANSFER_PROTOCOL_VERSION)
                        put("receiver_port", 7789)
                    }
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.v(TAG, "استلام من الكمبيوتر: $text")
                    bumpWatchdog()
                    try {
                        val resp = JSONObject(text)
                        when (resp.optString("status")) {
                            "ready", "progress", "saving", "saved" -> {
                                val received = resp.optLong("received_bytes", resp.optLong("received", -1L))
                                if (received >= 0L) {
                                    peerReceivedBytes.updateAndGet { old -> maxOf(old, received) }
                                }
                            }
                        }
                        responseQueue.put(resp)
                    } catch (_: Exception) {}
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (allDataSentFlag.get()) {
                        WameedLogger.w(TAG, "WebSocket closed before final ACK: ${t.javaClass.simpleName} — ${t.message}", t)
                    } else if (finishedFlag.compareAndSet(false, true)) {
                        WameedCrashReporter.getInstance().setTransferDiagnostics(
                            direction = "android_to_windows",
                            phase = currentPhase.get(),
                            sizeBytes = currentTransferSize.get(),
                            bytesDone = currentBytesDone.get(),
                            port = WameedPrefs.getPcPort(context),
                            failureType = "peer_closed"
                        )
                        WameedLogger.e(TAG, "خطأ في WebSocket للإرسال: ${t.javaClass.simpleName} — ${t.message}", t)
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
                setPhase("handshake")
                var paired = false
                Log.d(TAG, "انتظار حالة الاقتران...")
                while (!finishedFlag.get() && !paired) {
                    val resp = responseQueue.poll(60, TimeUnit.SECONDS) ?: break
                    when (resp.optString("status")) {
                        "pairing_required" -> {
                            Log.i(TAG, "حالة: انتظار موافقة الاقتران على الكمبيوتر")
                            callback.onInfo(context.getString(R.string.info_pairing_approval))
                        }
                        "paired", "hello" -> {
                            Log.i(TAG, "تم الاقتران بنجاح، البدء في إرسال الملفات")
                            paired = true
                            // ⚡ فتح اتصال دائم للعمليات المستقبلية
                            openPersistent(context)
                        }
                        "rejected" -> {
                            Log.w(TAG, "تم رفض طلب الاقتران من الكمبيوتر")
                            if (finishedFlag.compareAndSet(false, true)) {
                                callback.onError(resp.optString("message", context.getString(R.string.error_pairing_rejected)))
                                currentWebSocket?.close(1000, null)
                            }
                            return@Thread
                        }
                        "ws_failure" -> {
                            WameedLogger.e(TAG, "فشل الاتصال أثناء انتظار الاقتران")
                            return@Thread
                        }
                    }
                }
                
                if (!paired) {
                    WameedLogger.e(TAG, "فشل الاتصال: لم يكتمل الاقتران")
                    if (finishedFlag.compareAndSet(false, true)) {
                        callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                        currentWebSocket?.close(1000, null)
                    }
                    return@Thread
                }

                // Phase 2: Sequential File Transfer
                for ((index, uri) in uris.withIndex()) {
                    if (finishedFlag.get()) break
                    allDataSentFlag.set(false) // إعادة تعيين لكل ملف جديد
                    
                    callback.onProgress(0, 0.0)

                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val filename = getFilename(uri, mimeType)
                    Log.i(TAG, "جاري تحضير ملف [${index+1}/${uris.size}]: $filename ($mimeType)")
                    callback.onNextFile(index + 1, uris.size, filename)
                    setPhase("preparing", 0L, 0L)

                    var fileSize = resolveSize(uri)
                    var tempFile: java.io.File? = null
                    
                    if (fileSize <= 0) {
                        Log.d(TAG, "حجم الملف غير معروف، جاري النسخ للمخزن المؤقت للقياس...")
                        callback.onInfo(context.getString(R.string.preparing))
                        try {
                            tempFile = java.io.File.createTempFile("wameed_", ".bin", context.cacheDir)
                            // Increase fallback limit to 4GB for rare cases where size is unknown
                            val MAX = 4L * 1024 * 1024 * 1024 
                            context.contentResolver.openInputStream(uri)?.use { ins ->
                                java.io.FileOutputStream(tempFile).use { out ->
                                    val buf = ByteArray(512 * 1024)
                                    var total = 0L
                                    var n: Int
                                    while (ins.read(buf).also { n = it } > 0) {
                                        total += n
                                        if (total > MAX) throw IOException(context.getString(R.string.error_file_too_large))
                                        if ((tempFile.parentFile?.usableSpace ?: Long.MAX_VALUE) < 64L * 1024 * 1024) {
                                            throw IOException("Not enough temporary storage")
                                        }
                                        out.write(buf, 0, n)
                                        bumpWatchdog()
                                        // Optional: update UI that we are still preparing
                                        if (total % (50 * 1024 * 1024) == 0L) {
                                            Log.d(TAG, "Preparing large file: ${total / (1024*1024)}MB copied to cache")
                                        }
                                    }
                                    fileSize = total
                                }
                            }
                            Log.d(TAG, "تم نسخ الملف للمخزن المؤقت، الحجم: $fileSize بايت")
                        } catch (e: Exception) {
                            WameedLogger.e(TAG, "فشل قراءة الملف: ${e.message}", e)
                            callback.onError(context.getString(R.string.error_read_failed, e.message ?: ""))
                            finishedFlag.set(true)
                            break
                        }
                    }

                    if (fileSize <= 0) {
                        WameedLogger.e(TAG, "خطأ: حجم الملف $filename لا يزال غير معروف")
                        callback.onError(context.getString(R.string.error_unknown_size))
                        finishedFlag.set(true)
                        break
                    }

                    val chunkSize = TRANSFER_CHUNK_SIZE
                    val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
                    val transferId = createTransferId(filename, fileSize)
                    peerReceivedBytes.set(0L)
                    setPhase("transfer", fileSize, 0L)

                    // Send metadata
                    Log.d(TAG, "إرسال البيانات الوصفية للملف: $filename (Chunks: $totalChunks)")
                    val meta = JSONObject().apply {
                        put("type", "file_meta")
                        put("protocol_version", TRANSFER_PROTOCOL_VERSION)
                        put("transfer_id", transferId)
                        put("direction", "android_to_windows")
                        put("filename", filename)
                        put("mime", mimeType)
                        put("size", fileSize)
                        put("chunks", totalChunks)
                        put("chunk_size", chunkSize)
                        put("display_mode", WameedPrefs.getDisplayMode(context))
                    }
                    currentWebSocket?.send(meta.toString())
                    bumpWatchdog()

                    var resumeOffset = 0L
                    val readyResp = responseQueue.poll(750, TimeUnit.MILLISECONDS)
                    if (readyResp != null) {
                        when (readyResp.optString("status")) {
                            "ready" -> {
                                resumeOffset = readyResp.optLong("offset", 0L).coerceIn(0L, fileSize)
                                peerReceivedBytes.set(resumeOffset)
                                if (resumeOffset > 0L) {
                                    Log.i(TAG, "استئناف إرسال $filename من $resumeOffset bytes")
                                    currentBytesDone.set(resumeOffset)
                                    callback.onProgress(((resumeOffset * 100) / fileSize).toInt())
                                }
                            }
                            "error" -> {
                                WameedLogger.e(TAG, "الكمبيوتر رفض استقبال الملف: ${readyResp.optString("message")}")
                                callback.onError(readyResp.optString("message", context.getString(R.string.error_save_failed)))
                                finishedFlag.set(true)
                                break
                            }
                            else -> responseQueue.offer(readyResp)
                        }
                    }

                    // Stream binary data
                    val inputStream = if (tempFile != null) java.io.FileInputStream(tempFile) 
                                      else context.contentResolver.openInputStream(uri)
                    
                    try {
                        inputStream?.use { stream ->
                            val buffer = ByteArray(chunkSize)
                            skipFully(stream, resumeOffset, buffer)
                            var chunkIdx = (resumeOffset / chunkSize).toInt()
                            var n: Int
                            var totalBytesSent = resumeOffset
                            var lastLogTime = System.currentTimeMillis()
                            var lastCalcTime = System.currentTimeMillis()
                            var lastCalcBytes = resumeOffset
                            var lastThrottleInfoTime = 0L

                            fun drainPeerProgress() {
                                while (true) {
                                    val resp = responseQueue.poll() ?: break
                                    when (resp.optString("status")) {
                                        "progress", "ready", "saving" -> {
                                            val received = resp.optLong(
                                                "received_bytes",
                                                resp.optLong("received", peerReceivedBytes.get())
                                            ).coerceIn(0L, fileSize)
                                            peerReceivedBytes.updateAndGet { old -> maxOf(old, received) }
                                            currentBytesDone.set(peerReceivedBytes.get())
                                            bumpWatchdog()
                                            if (resp.optString("status") == "saving") {
                                                responseQueue.offer(resp)
                                                break
                                            }
                                        }
                                        else -> {
                                            responseQueue.offer(resp)
                                            break
                                        }
                                    }
                                }
                            }

                            fun waitForSendWindow(bytesQueuedByApp: Long, chunkBytes: Int): Boolean {
                                val started = System.currentTimeMillis()
                                var sleepMs = 4L
                                while (!finishedFlag.get()) {
                                    drainPeerProgress()
                                    val ws = currentWebSocket ?: return false
                                    val queueSize = ws.queueSize()
                                    val peerLag = (bytesQueuedByApp - peerReceivedBytes.get()).coerceAtLeast(0L)
                                    if (queueSize + chunkBytes < WS_QUEUE_SOFT_LIMIT && peerLag < PEER_IN_FLIGHT_LIMIT) {
                                        return true
                                    }

                                    val now = System.currentTimeMillis()
                                    if (now - lastThrottleInfoTime > 3000L) {
                                        Log.d(
                                            TAG,
                                            "تهدئة الإرسال: queue=${queueSize}B lag=${peerLag}B sent=$bytesQueuedByApp received=${peerReceivedBytes.get()} transfer=$transferId"
                                        )
                                        lastThrottleInfoTime = now
                                    }
                                    if (now - started > CHUNK_ENQUEUE_TIMEOUT_MS) {
                                        WameedCrashReporter.getInstance().setTransferDiagnostics(
                                            direction = "android_to_windows",
                                            phase = "transfer_backpressure",
                                            sizeBytes = fileSize,
                                            bytesDone = peerReceivedBytes.get(),
                                            port = WameedPrefs.getPcPort(context),
                                            failureType = "send_queue_backpressure_timeout"
                                        )
                                        return false
                                    }
                                    Thread.sleep(sleepMs)
                                    sleepMs = minOf(250L, sleepMs * 2)
                                    bumpWatchdog()
                                }
                                return false
                            }

                            fun enqueueChunk(chunkData: okio.ByteString, sentBeforeChunk: Long): Boolean {
                                val started = System.currentTimeMillis()
                                var attempts = 0
                                while (!finishedFlag.get()) {
                                    if (!waitForSendWindow(sentBeforeChunk, chunkData.size)) return false
                                    val ws = currentWebSocket ?: return false
                                    if (ws.send(chunkData)) return true

                                    attempts++
                                    WameedLogger.w(
                                        TAG,
                                        "WebSocket queue refused chunk; retrying attempt=$attempts queue=${ws.queueSize()} sent=$sentBeforeChunk received=${peerReceivedBytes.get()} chunk=${chunkData.size} transfer=$transferId"
                                    )
                                    if (System.currentTimeMillis() - started > CHUNK_ENQUEUE_TIMEOUT_MS) {
                                        WameedCrashReporter.getInstance().setTransferDiagnostics(
                                            direction = "android_to_windows",
                                            phase = "transfer_enqueue",
                                            sizeBytes = fileSize,
                                            bytesDone = peerReceivedBytes.get(),
                                            port = WameedPrefs.getPcPort(context),
                                            failureType = "send_queue_full"
                                        )
                                        return false
                                    }
                                    Thread.sleep(minOf(500L, 25L * attempts))
                                    bumpWatchdog()
                                }
                                return false
                            }

                            while (stream.read(buffer).also { n = it } > 0) {
                                if (finishedFlag.get()) break

                                val chunkData = buffer.toByteString(0, n)
                                if (!enqueueChunk(chunkData, totalBytesSent)) {
                                    throw IOException("Timed out waiting for send buffer to drain")
                                }

                                totalBytesSent += n
                                currentBytesDone.set(totalBytesSent)
                                chunkIdx++

                                val now = System.currentTimeMillis()
                                if (now - lastLogTime >= 2000) {
                                    Log.v(TAG, "جاري إرسال $filename: ${(totalBytesSent*100/fileSize)}%")
                                    lastLogTime = now
                                }

                                if (now - lastCalcTime >= 100) {
                                    val deltaSec = (now - lastCalcTime) / 1000.0
                                    val deltaBytes = totalBytesSent - lastCalcBytes
                                    val speedMbps = (deltaBytes * 8.0) / (1024.0 * 1024.0 * deltaSec)
                                    val received = peerReceivedBytes.get()
                                        .coerceAtLeast(totalBytesSent - PEER_IN_FLIGHT_LIMIT)
                                        .coerceAtMost(fileSize)
                                    val progress = 5 + ((received * 93) / fileSize).toInt()
                                    callback.onProgress(progress, speedMbps)
                                    WameedCrashReporter.getInstance().setTransferDiagnostics(
                                        direction = "android_to_windows",
                                        phase = "transfer",
                                        sizeBytes = fileSize,
                                        bytesDone = received,
                                        port = WameedPrefs.getPcPort(context)
                                    )
                                    lastCalcTime = now
                                    lastCalcBytes = totalBytesSent
                                }
                                bumpWatchdog()
                            }
                            Log.i(TAG, "انتهى إرسال بيانات الملف $filename، بانتظار تأكيد الحفظ...")
                            allDataSentFlag.set(true)
                        }
                    } catch (e: Exception) {
                        WameedLogger.e(TAG, "فشل أثناء إرسال بيانات الملف: ${e.message}", e)
                        callback.onError(context.getString(R.string.error_send_failed, e.message ?: ""))
                        finishedFlag.set(true)
                    } finally {
                        tempFile?.delete()
                    }

                    if (finishedFlag.get()) break

                    // Wait for ACK
                    setPhase("finalizing", fileSize, currentBytesDone.get())
                    val fileWatchdogMs = maxOf(30_000L, fileSize / (256L * 1024) * 1000L + 30_000L)
                    var ackReceived = false
                    val ackStart = System.currentTimeMillis()
                    
                    var firstPoll = true
                    while (System.currentTimeMillis() - ackStart < fileWatchdogMs) {
                        val pollTimeout = if (firstPoll) 150L else 50L
                        val resp = responseQueue.poll(pollTimeout, TimeUnit.MILLISECONDS)
                        
                        if (resp == null) {
                            if (firstPoll && !finishedFlag.get()) {
                                Log.d(TAG, "بانتظار تأكيد الحفظ من الكمبيوتر (Saving...)")
                                firstPoll = false
                            }
                            continue
                        }

                        val status = resp.optString("status")
                        if (status == "saved") {
                            Log.i(TAG, "✅ تأكيد الحفظ: $filename")
                            ackReceived = true
                            callback.onProgress(100)
                            break
                        } else if (status == "saving") {
                            Log.d(TAG, "الطرف الآخر يقوم بحفظ الملف حالياً...")
                            bumpWatchdog() // Reset watchdog because we know it's working
                        } else if (status == "progress") {
                            val received = resp.optLong("received_bytes", resp.optLong("received", currentBytesDone.get()))
                            if (received > 0L) {
                                currentBytesDone.set(received.coerceAtMost(fileSize))
                            }
                            bumpWatchdog()
                        } else if (status == "error") {
                            WameedLogger.e(TAG, "❌ فشل حفظ الملف على الكمبيوتر: ${resp.optString("message")}")
                            callback.onError(resp.optString("message", context.getString(R.string.error_save_failed)))
                            finishedFlag.set(true)
                            break
                        } else if (status == "ws_failure") {
                            WameedLogger.e(TAG, "انقطع الاتصال قبل تأكيد حفظ الملف $filename")
                            callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                            finishedFlag.set(true)
                            break
                        }
                        bumpWatchdog()
                    }

                    if (!ackReceived && !finishedFlag.get()) {
                        WameedLogger.e(TAG, "فشل: انتهت مهلة تأكيد الحفظ للملف $filename")
                        callback.onError(context.getString(R.string.error_timeout_pc_no_response))
                        finishedFlag.set(true)
                        break
                    }
                }

                if (!finishedFlag.get()) {
                    Log.i(TAG, "✨ اكتملت العملية بنجاح!")
                    markSendSuccess()
                    callback.onSuccess(context.getString(R.string.status_saved))
                }

            } catch (e: Exception) {
                WameedLogger.e(TAG, "❌ خطأ غير متوقع في Batch Sender: ${e.message}", e)
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
                        // ⚡ فتح اتصال دائم للعمليات المستقبلية
                        openPersistent(context)
                    }
                    "saving" -> {
                        // Reset possible internal watchdog if we had one for single sends
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

    private fun createTransferId(filename: String, fileSize: Long): String {
        val raw = "${filename.trim().lowercase()}:$fileSize"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "a2w-${digest.take(20)}"
    }

    private fun skipFully(stream: java.io.InputStream, bytesToSkip: Long, scratch: ByteArray) {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
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
