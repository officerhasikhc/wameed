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
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
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
        fun onFileTransferStarted(filename: String, size: Long)
        fun onProgress(percent: Int, speedMbps: Double)
        fun onTransferCompleted(file: File, filename: String)
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
        var currentFile: File? = null
        var fileOutput: BufferedOutputStream? = null
        var expectedSize = 0L
        var receivedSize = 0L
        var chunkCount = 0
        var startTime = 0L
        var deviceName = "PC"

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
                                expectedSize = json["size"]?.jsonPrimitive?.long ?: 0L
                                
                                val tempFile = File(context.cacheDir, "wameed_receive_$filename")
                                currentFile = tempFile
                                withContext(Dispatchers.IO) {
                                    fileOutput = BufferedOutputStream(
                                        FileOutputStream(tempFile), 512 * 1024
                                    )
                                }
                                receivedSize = 0L
                                chunkCount = 0
                                startTime = System.currentTimeMillis()
                                
                                callback?.onFileTransferStarted(filename, expectedSize)
                                Log.i(TAG, "Receiving file: $filename, size: $expectedSize")
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
                            fileOutput?.write(data)
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

                        // أرسل ack للمرسل كل 8 chunks لإبقاء الـ watchdog حياً أثناء الملفات الكبيرة
                        if (chunkCount % 8 == 0) {
                            try {
                                session.send(Frame.Text(
                                    buildJsonObject {
                                        put("status", "progress")
                                        put("received", receivedSize)
                                    }.toString()
                                ))
                            } catch (_: Exception) {}
                        }

                        if (expectedSize > 0 && receivedSize >= expectedSize) {
                            withContext(Dispatchers.IO) {
                                fileOutput?.flush()
                                fileOutput?.close()
                            }
                            fileOutput = null
                            val finalFilename = currentFile?.name?.removePrefix("wameed_receive_") ?: "received_file"
                            currentFile?.let { callback?.onTransferCompleted(it, finalFilename) }

                            val response = buildJsonObject {
                                put("status", "saved")
                            }.toString()
                            session.send(Frame.Text(response))
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            callback?.onError(e.message ?: "Transfer interrupted")
        } finally {
            withContext(Dispatchers.IO) {
                try {
                    fileOutput?.close()
                } catch (_: Exception) {}
            }
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        isRunning.set(false)
        Log.i(TAG, "Server stopped")
    }
}
