package com.wameed

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * Listens for UDP discovery pings from the PC and responds with the phone's info.
 */
class WameedDiscoveryResponder(private val context: Context) {
    private val TAG = "WameedDiscoveryResp"
    private val DISCOVERY_PORT = WameedProtocol.DISCOVERY_UDP_PORT
    private var socket: DatagramSocket? = null
    private var running = false

    fun start() {
        if (running) return
        running = true
        
        Thread {
            try {
                // Listen on all interfaces on the discovery port
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
                
                val buffer = ByteArray(1024)
                Log.i(TAG, "Discovery responder started on port $DISCOVERY_PORT")
                
                while (running) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket?.receive(packet)
                        
                        val data = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(data)
                        
                        if (WameedProtocol.isDiscoveryPingFromPc(json)) {
                            Log.d(TAG, "Received discovery ping from ${packet.address}")
                            sendResponse(packet.address, packet.port)
                        }
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "Error receiving packet: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Responder failed", e)
            } finally {
                stop()
            }
        }.start()
    }

    private fun sendResponse(address: java.net.InetAddress, port: Int) {
        try {
            val response = WameedProtocol.discoveryPong(
                device = WameedPrefs.getDeviceName(),
                deviceId = WameedPrefs.getOrCreateDeviceId(context),
                appVersion = BuildConfig.VERSION_NAME,
            )
            val data = response.toString().toByteArray(Charsets.UTF_8)
            val packet = DatagramPacket(data, data.size, address, port)
            socket?.send(packet)
            Log.d(TAG, "Sent discovery pong to $address:$port")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send response", e)
        }
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }
}
