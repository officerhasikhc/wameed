package com.wameed

import android.content.Context
import android.util.Log
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * محرك الاستقبال: يفتح سيرفر WebSocket على الهاتف لاستقبال الملفات من الكمبيوتر.
 */
class WameedServer(private val context: Context) {
    private var server: EmbeddedServer<*, *>? = null
    private val isRunning = AtomicBoolean(false)

    companion object {
        private const val TAG = "WameedServer"
    }

    interface ServerCallback {
        fun onDeviceConnected(name: String, ip: String)
        fun onFileTransferStarted(filename: String, size: Long)
        fun onProgress(percent: Int, speedMbps: Double)
        fun onTransferCompleted(uri: String?, filename: String, size: Long)
        fun onError(error: String)
        fun onPairingRequest(deviceName: String, deviceId: String)
        fun onTextReceived(text: String, from: String)
        fun onUrlReceived(url: String, from: String)
    }

    private var callback: ServerCallback? = null
    private var pendingSession: DefaultWebSocketServerSession? = null

    fun setCallback(callback: ServerCallback) {
        this.callback = callback
    }

    fun approvePairing() {
        val session = pendingSession
        if (session != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = buildJsonObject {
                        put("status", "paired")
                    }.toString()
                    session.send(Frame.Text(response))
                    Log.i(TAG, "Pairing approved by user")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send paired status", e)
                }
            }
        }
    }

    fun rejectPairing() {
        val session = pendingSession
        if (session != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = buildJsonObject {
                        put("status", "rejected")
                        put("message", "Connection rejected by user")
                    }.toString()
                    session.send(Frame.Text(response))
                    session.close(CloseReason(CloseReason.Codes.NORMAL, "Rejected by user"))
                    Log.i(TAG, "Pairing rejected by user")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send rejected status", e)
                } finally {
                    pendingSession = null
                }
            }
        }
    }

    fun start(port: Int = 7789) {
        if (isRunning.get()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                server = embeddedServer(CIO, port = port) {
                    install(WebSockets) {
                        // رفع الحد الافتراضي (64KB) إلى 16MB لاستيعاب chunks الملفات الكبيرة
                        maxFrameSize = 16L * 1024 * 1024
                        pingPeriod = 20.seconds
                    }
                    install(ContentNegotiation) {
                        json()
                    }

                    routing {
                        webSocket("/") {
                            handleConnection(this)
                        }
                    }
                }
                isRunning.set(true)
                Log.i(TAG, "Server started on port $port")
                server?.start(wait = true)
            } catch (e: Exception) {
                Log.e(TAG, "Server failed to start", e)
                isRunning.set(false)
                callback?.onError(e.message ?: "Unknown server error")
            }
        }
    }

    private suspend fun handleConnection(session: DefaultWebSocketServerSession) {
        var currentDownload: FileSaver.PendingDownload? = null
        var finalUri: String? = null
        var finalFilename = "received_file"
        var expectedSize = 0L
        var receivedSize = 0L
        var chunkCount = 0
        var expectedChunks = 0
        var transferId = ""
        var startTime = 0L
        var lastAckMs = 0L
        var deviceName = "PC"

        suspend fun sendTransferStatus(status: String, extra: JsonObjectBuilder.() -> Unit = {}) {
            session.send(Frame.Text(buildJsonObject {
                put("status", status)
                put("protocol_version", 2)
                if (transferId.isNotBlank()) put("transfer_id", transferId)
                put("received_bytes", receivedSize)
                put("received", receivedSize)
                put("chunk_index", chunkCount)
                if (expectedChunks > 0) put("total_chunks", expectedChunks)
                extra()
            }.toString()))
        }

        suspend fun finalizeCurrentTransfer() {
            val target = currentDownload ?: throw IOException("No active download target")
            WameedCrashReporter.getInstance().setTransferDiagnostics(
                direction = "windows_to_android",
                phase = "finalizing",
                sizeBytes = expectedSize,
                bytesDone = receivedSize,
                port = 7789
            )
            sendTransferStatus("saving")
            withContext(Dispatchers.IO) {
                target.flush()
                target.close()
                target.finish(true)
            }
            finalUri = target.uri?.toString()
            finalFilename = target.filename
            currentDownload = null
            callback?.onTransferCompleted(finalUri, finalFilename, receivedSize)
            sendTransferStatus("saved") {
                finalUri?.let { put("uri", it) }
                put("filename", finalFilename)
                put("size", receivedSize)
            }
        }

        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        val json = Json.parseToJsonElement(text).jsonObject
                        
                        when (json["type"]?.jsonPrimitive?.content) {
                            "hello" -> {
                                deviceName = json["device"]?.jsonPrimitive?.content ?: "PC"
                                val deviceId = json["device_id"]?.jsonPrimitive?.content ?: ""
                                
                                // حفظ الجلسة لانتظار الموافقة
                                pendingSession = session
                                
                                // تحقق مما إذا كان الجهاز موثوقاً به أو هو الكمبيوتر المبرمج حالياً
                                val savedIp = WameedPrefs.getPcIp(context)
                                var remoteIp = session.call.request.local.remoteHost
                                
                                // تطبيع عنوان IP (إزالة ::ffff: إذا وجد)
                                if (remoteIp.startsWith("::ffff:")) {
                                    remoteIp = remoteIp.removePrefix("::ffff:")
                                }
                                
                                Log.d(TAG, "Connection from remoteHost: $remoteIp, Saved PC IP: $savedIp")

                                if (WameedPrefs.isDeviceTrusted(context, deviceId) || (savedIp.isNotEmpty() && remoteIp == savedIp)) {
                                    Log.i(TAG, "Auto-approving trusted device: $deviceName ($deviceId)")
                                    
                                    // إذا كان الجهاز موثوقاً أو هو الكمبيوتر المحفوظ، نتأكد من حفظ عنوانه الحالي
                                    // هذا يضمن تحديث الـ IP إذا تغير (DHCP) دون تدخل يدوي.
                                    WameedPrefs.savePcAddress(context, remoteIp)
                                    WameedPrefs.setPcName(context, deviceName)
                                    callback?.onDeviceConnected(deviceName, remoteIp)

                                    if (remoteIp == savedIp && deviceId.isNotEmpty()) {
                                        WameedPrefs.addTrustedDevice(context, deviceId)
                                    }
                                    approvePairing()
                                } else {
                                    callback?.onPairingRequest(deviceName, deviceId)
                                }
                            }
                            "file_meta" -> {
                                val filename = json["filename"]?.jsonPrimitive?.content ?: "received_file"
                                val mimeType = json["mime"]?.jsonPrimitive?.contentOrNull
                                expectedSize = json["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                                expectedChunks = json["chunks"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                transferId = json["transfer_id"]?.jsonPrimitive?.contentOrNull
                                    ?: "android-recv-${System.currentTimeMillis()}"
                                finalFilename = filename

                                val target = FileSaver.openDownloadStream(
                                    context = context,
                                    originalName = filename,
                                    mimeType = mimeType,
                                    expectedSize = expectedSize
                                )

                                if (target == null) {
                                    sendTransferStatus("error") {
                                        put("reason", "permission_denied")
                                        put("message", "Could not open Downloads destination")
                                    }
                                    callback?.onError("Could not open Downloads destination")
                                    continue
                                }

                                currentDownload = target
                                finalUri = target.uri?.toString()
                                receivedSize = 0L
                                chunkCount = 0
                                startTime = System.currentTimeMillis()
                                lastAckMs = startTime
                                
                                callback?.onFileTransferStarted(filename, expectedSize)
                                Log.i(TAG, "Receiving file: $filename, size: $expectedSize, transfer=$transferId")
                                WameedCrashReporter.getInstance().setTransferDiagnostics(
                                    direction = "windows_to_android",
                                    phase = "receive",
                                    sizeBytes = expectedSize,
                                    bytesDone = 0L,
                                    port = 7789
                                )
                                sendTransferStatus("ready") {
                                    put("offset", 0L)
                                    finalUri?.let { put("uri", it) }
                                }

                                if (expectedSize == 0L) {
                                    finalizeCurrentTransfer()
                                }
                            }
                            "text" -> {
                                val content = json["text"]?.jsonPrimitive?.content ?: ""
                                Log.i(TAG, "Received text from $deviceName: ${content.take(50)}...")
                                callback?.onTextReceived(content, deviceName)
                                val response = buildJsonObject {
                                    put("status", "saved")
                                }.toString()
                                session.send(Frame.Text(response))
                            }
                            "url" -> {
                                val url = json["url"]?.jsonPrimitive?.content ?: ""
                                Log.i(TAG, "Received URL from $deviceName: $url")
                                callback?.onUrlReceived(url, deviceName)
                                val response = buildJsonObject {
                                    put("status", "saved")
                                }.toString()
                                session.send(Frame.Text(response))
                            }
                            "ping" -> {
                                val response = buildJsonObject {
                                    put("status", "pong")
                                }.toString()
                                session.send(Frame.Text(response))
                            }
                        }
                    }
                    is Frame.Binary -> {
                        val data = frame.readBytes()
                        withContext(Dispatchers.IO) {
                            currentDownload?.write(data) ?: throw IOException("Received file chunk before file_meta")
                        }
                        receivedSize += data.size
                        chunkCount++

                        val now = System.currentTimeMillis()
                        val duration = (now - startTime) / 1000.0
                        if (duration > 0) {
                            val speedMbps = (receivedSize * 8.0) / (1024.0 * 1024.0 * duration)
                            val percent = if (expectedSize > 0) (receivedSize * 100 / expectedSize).toInt() else 0
                            callback?.onProgress(percent, speedMbps)
                        }

                        // Send progress acknowledgements so large transfers never look idle.
                        if (chunkCount % 8 == 0 || now - lastAckMs >= 1000L) {
                            try {
                                WameedCrashReporter.getInstance().setTransferDiagnostics(
                                    direction = "windows_to_android",
                                    phase = "receive",
                                    sizeBytes = expectedSize,
                                    bytesDone = receivedSize,
                                    port = 7789
                                )
                                sendTransferStatus("progress") {
                                    put("offset", receivedSize)
                                }
                                lastAckMs = now
                            } catch (_: Exception) {}
                        }

                        if (expectedSize > 0 && receivedSize >= expectedSize) {
                            finalizeCurrentTransfer()
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            WameedCrashReporter.getInstance().setTransferDiagnostics(
                direction = "windows_to_android",
                phase = "receive",
                sizeBytes = expectedSize,
                bytesDone = receivedSize,
                port = 7789,
                failureType = e.javaClass.simpleName
            )
            callback?.onError(e.message ?: "Transfer interrupted")
        } finally {
            withContext(Dispatchers.IO) {
                try {
                    currentDownload?.close()
                } catch (_: Exception) {}
                currentDownload?.finish(false)
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        isRunning.set(false)
        Log.i(TAG, "Server stopped")
    }
}
