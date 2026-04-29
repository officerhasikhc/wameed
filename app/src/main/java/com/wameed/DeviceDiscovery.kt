package com.wameed

import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * يستمع لبث UDP من المستقبل على الكمبيوتر ويكتشفه تلقائياً
 */
class DeviceDiscovery {

    private val TAG = "DeviceDiscovery"
    private val DISCOVERY_PORT = 7789

    data class DiscoveredDevice(
        val name: String,
        val ip: String,
        val port: Int
    ) {
        val address get() = "$ip:$port"
    }

    interface DiscoveryCallback {
        fun onDeviceFound(device: DiscoveredDevice)
        fun onError(error: String)
        fun onSearchFinished()
    }

    @Volatile
    private var listening = false
    private var currentSocket: DatagramSocket? = null

    /**
     * يبدأ الاستماع لمدة محددة (بالثواني) — يعمل في Thread منفصل
     */
    fun startListening(context: android.content.Context, timeoutSeconds: Int = 6, callback: DiscoveryCallback) {
        Log.i(TAG, "بدء البحث عن أجهزة (UDP) لمدة $timeoutSeconds ثوانٍ...")
        stop()
        listening = true

        Thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0)) // bind to any available port
                    broadcast = true
                    soTimeout = 2000 // 2s timeout per receive cycle
                }
                currentSocket = socket
                Log.d(TAG, "تم فتح Socket للاكتشاف على المنفذ المحلي: ${socket.localPort}")

                val buffer = ByteArray(1024)
                val seen = mutableSetOf<String>()
                val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L

                // إرسال broadcast ping لتفعيل استجابة الكمبيوتر
                sendDiscoveryPing(socket)

                while (listening && System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val data = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        Log.v(TAG, "استلام حزمة من ${packet.address.hostAddress}: $data")
                        val json = JSONObject(data)

                        if (json.optString("service") == "wameed_pc") {
                            val ip = json.optString("ip", packet.address?.hostAddress ?: "")
                            val port = json.optInt("port", 7788)
                            val name = json.optString("name", context.getString(R.string.label_pc_generic))
                            val key = "$ip:$port"

                            if (key !in seen) {
                                seen.add(key)
                                val device = DiscoveredDevice(name, ip, port)
                                Log.i(TAG, "✅ اكتشاف جهاز جديد: $name ($ip:$port)")
                                callback.onDeviceFound(device)
                            } else {
                                Log.v(TAG, "تجاهل جهاز مكتشف مسبقاً: $key")
                            }
                        } else {
                            Log.v(TAG, "تجاهل حزمة غير تابعة لخدمة وميض")
                        }
                    } catch (_: SocketTimeoutException) {
                        // أعد إرسال ping كل دورة timeout للبحث مجدداً
                        if (listening && System.currentTimeMillis() < deadline) {
                            Log.v(TAG, "انتهاء مهلة الاستقبال، إعادة إرسال Ping للاكتشاف...")
                            sendDiscoveryPing(socket)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ خطأ في قراءة حزمة الاكتشاف", e)
                    }
                }
                Log.i(TAG, "انتهت فترة البحث عن الأجهزة")
            } catch (e: Exception) {
                Log.e(TAG, "❌ فشل بدء عملية الاكتشاف", e)
                callback.onError(context.getString(R.string.error_discovery_failed, e.message ?: ""))
            } finally {
                socket?.close()
                currentSocket = null
                listening = false
                callback.onSearchFinished()
            }
        }.start()
    }

    /**
     * يرسل حزمة broadcast لتفعيل استجابة الكمبيوتر على port 7789
     */
    private fun sendDiscoveryPing(socket: DatagramSocket) {
        try {
            val ping = JSONObject().apply {
                put("type", "discovery_ping")
                put("service", "wameed_phone")
                put("device", android.os.Build.MODEL)
            }
            val data = ping.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(
                data, data.size,
                InetAddress.getByName("255.255.255.255"),
                DISCOVERY_PORT
            )
            socket.send(packet)
            Log.d(TAG, "أُرسلت حزمة اكتشاف broadcast على port $DISCOVERY_PORT")
        } catch (e: Exception) {
            Log.w(TAG, "فشل إرسال حزمة الاكتشاف: ${e.message}")
        }
    }

    fun stop() {
        listening = false
        try { currentSocket?.close() } catch (_: Exception) {}
        currentSocket = null
    }

    companion object {
        /** Fast TCP reachability check — used as liveness probe. */
        fun isTcpReachable(ip: String, port: Int, timeoutMs: Int = 600): Boolean {
            return try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), timeoutMs)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
