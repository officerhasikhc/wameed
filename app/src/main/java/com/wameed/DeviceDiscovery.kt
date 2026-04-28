package com.wameed

import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
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
        stop()
        listening = true

        Thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    broadcast = true
                    soTimeout = timeoutSeconds * 1000
                }
                currentSocket = socket

                val buffer = ByteArray(1024)
                val seen = mutableSetOf<String>()
                val deadline = System.currentTimeMillis() + timeoutSeconds * 1000L

                while (System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val data = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(data)

                        if (json.optString("service") == "wameed") {
                            val ip = json.getString("ip")
                            val port = json.getInt("port")
                            val name = json.optString("name", context.getString(R.string.label_pc_generic))
                            val key = "$ip:$port"

                            if (key !in seen) {
                                seen.add(key)
                                val device = DiscoveredDevice(name, ip, port)
                                Log.d(TAG, "وُجد جهاز (UDP): $device")
                                callback.onDeviceFound(device)
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "خطأ في قراءة الحزمة", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في الاكتشاف", e)
                callback.onError(context.getString(R.string.error_discovery_failed, e.message ?: ""))
            } finally {
                socket?.close()
                currentSocket = null
                listening = false
                callback.onSearchFinished()
            }
        }.start()
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
