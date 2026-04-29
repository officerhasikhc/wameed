package com.wameed

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.app.ForegroundServiceStartNotAllowedException
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Keep-alive foreground service.
 *
 * Purpose: keep the app process alive (and optionally a WebSocket open) when
 * the user leaves Wameed briefly (WhatsApp, a call, etc.) so returning feels
 * instant instead of requiring a fresh discovery cycle.
 *
 * Behaviour:
 *   - Starts with a low-importance notification showing PC/device status.
 *   - Holds a persistent WS connection to PC; sends hello + ping every 25s.
 *   - Auto-stops after [IDLE_TIMEOUT_MS] (5 minutes) of no refresh() call.
 *   - Auto-stops on 3 consecutive ping failures.
 *
 * Not invasive: [WameedSender] does NOT depend on this service. File sends
 * still open their own short-lived WS. This service only keeps the PC-side
 * "active_clients" counter alive so PC shows the user as connected.
 */
class WameedConnectionService : Service() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private val TAG = "WameedKeepAlive"

    private val handler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var receiverServer: WameedServer? = null
    private var nsdHelper: WameedNsdHelper? = null
    private var discoveryResponder: WameedDiscoveryResponder? = null
    private var isReceiving = false
    private var isDiscovering = false
    private var pingFailures = 0
    @Volatile private var lastActivity: Long = System.currentTimeMillis()
    @Volatile private var lastReceiveActivity: Long = 0L
    private var pcDisplay: String = ""
    // Tracks whether we've completed the one-time startup (startForeground + openWs
    // + schedulers). When Android recreates a killed service via START_STICKY, this
    // flag is naturally reset to false on the new instance, so bootstrap() will run.
    private var bootstrapped = false
    // Pending delayed shutdown of the receiver server. Cancelled when activity resumes.
    private var receiveShutdownRunnable: Runnable? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "wameed_keepalive"
        private const val NOTIF_ID = 4711
        private const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L   // 5 minutes
        private const val RECEIVE_IDLE_TIMEOUT_MS = 5 * 60 * 1000L  // receiver server grace
        private const val PING_INTERVAL_MS = 25 * 1000L      // 25s
        private const val MAX_PING_FAILURES = 3

        const val ACTION_START = "com.wameed.keepalive.START"
        const val ACTION_STOP  = "com.wameed.keepalive.STOP"
        const val ACTION_REFRESH = "com.wameed.keepalive.REFRESH"
        const val ACTION_START_RECEIVING = "com.wameed.keepalive.START_RECEIVING"
        const val ACTION_STOP_RECEIVING = "com.wameed.keepalive.STOP_RECEIVING"
        const val ACTION_APPROVE_PAIRING = "com.wameed.keepalive.APPROVE_PAIRING"
        const val ACTION_REJECT_PAIRING = "com.wameed.keepalive.REJECT_PAIRING"

        /** True while the service instance is alive (set in onCreate/onDestroy). */
        @Volatile var isRunning: Boolean = false
            private set

        /** Safe entry point — caller doesn't need to know service internals. */
        fun start(context: Context) {
            if (!WameedPrefs.isKeepAliveEnabled(context)) return
            if (!WameedPrefs.isConfigured(context)) return  // no PC configured yet
            val i = Intent(context, WameedConnectionService::class.java).apply {
                action = ACTION_START
            }
            if (isRunning) {
                // Service is already alive — just send a regular intent (refresh logic).
                // NEVER call startForegroundService on an already-running service from a
                // background/paused context — it causes ForegroundServiceDidNotStartInTimeException.
                context.startService(i)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, WameedConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(i)
        }

        /** Start receiving mode — ensures the WS receiver server is up. */
        fun startReceiving(context: Context) {
            val i = Intent(context, WameedConnectionService::class.java).apply {
                action = ACTION_START_RECEIVING
            }
            if (isRunning) {
                context.startService(i)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { context.startForegroundService(i) } catch (_: Exception) {
                    context.startService(i)
                }
            } else {
                context.startService(i)
            }
        }

        /** Call when the user interacts with the app, to reset the idle timer. */
        fun refresh(context: Context) {
            if (!WameedPrefs.isKeepAliveEnabled(context)) return
            if (!WameedPrefs.isConfigured(context)) return
            val i = Intent(context, WameedConnectionService::class.java).apply {
                action = ACTION_REFRESH
            }
            try {
                if (isRunning) {
                    context.startService(i)
                } else {
                    // If not running, start as foreground to be safe
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(i)
                    } else {
                        context.startService(i)
                    }
                }
            } catch (e: Exception) {
                Log.e("WameedKeepAlive", "Failed to refresh service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure startForeground is called immediately to prevent ForegroundServiceDidNotStartInTimeException
        // We call it here with the last known state (or default) before processing the action.
        ensureForeground()

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCleanly("user")
                return START_NOT_STICKY
            }
            ACTION_START_RECEIVING -> {
                lastActivity = System.currentTimeMillis()
                if (!bootstrapped) {
                    bootstrap()  // bootstrap() already calls startReceivingMode()
                } else {
                    startReceivingMode()
                }
                return START_STICKY
            }
            ACTION_STOP_RECEIVING -> {
                stopReceivingMode()
                return START_STICKY
            }
            ACTION_APPROVE_PAIRING -> {
                receiverServer?.approvePairing()
                return START_STICKY
            }
            ACTION_REJECT_PAIRING -> {
                receiverServer?.rejectPairing()
                return START_STICKY
            }
            ACTION_REFRESH -> {
                // CRITICAL: if the service is a fresh instance (Android recreated
                // it after a kill, or ACTION_REFRESH was the very first intent),
                // we MUST still call startForeground() within 5 seconds and open
                // the WS — otherwise the OS kills the service and PC never sees
                // the phone as connected again until the user sends a file.
                lastActivity = System.currentTimeMillis()
                if (!bootstrapped) {
                    Log.i(TAG, "refresh() on fresh instance — bootstrapping")
                    bootstrap()
                } else {
                    // Already running. Just reset the timer. Also reopen WS
                    // if it dropped silently (e.g. Wi-Fi hiccup while app was
                    // in background — user deserves to be re-connected NOW).
                    if (ws == null) {
                        Log.i(TAG, "refresh() — WS was dead, reopening")
                        openWs()
                    } else {
                        Log.d(TAG, "refresh() — idle timer reset")
                    }
                    // Re-activate receiver if it was shut down by grace timeout.
                    if (!isReceiving) {
                        Log.i(TAG, "refresh() — receiver was stopped, restarting")
                        startReceivingMode()
                    } else {
                        cancelPendingReceiveShutdown()
                    }
                }
                return START_STICKY
            }
        }

        // Default = ACTION_START (or null intent from START_STICKY redelivery)
        lastActivity = System.currentTimeMillis()
        if (!bootstrapped) {
            bootstrap()
        } else {
            // start() called on an already-running service — reopen WS if dead.
            if (ws == null) openWs()
            // Re-activate receiver if it was shut down by grace timeout.
            if (!isReceiving) startReceivingMode()
            else cancelPendingReceiveShutdown()
        }
        return START_STICKY
    }

    /** One-time startup: register foreground notification, open WS, arm timers.
     *  Safe to call once per service instance. */
    private fun bootstrap() {
        pcDisplay = WameedPrefs.getPcIp(this).ifEmpty { getString(R.string.label_pc_generic) }
        ensureForeground()
        openWs()
        schedulePing()
        scheduleIdleWatch()
        bootstrapped = true

        // Auto-start receiving mode so the phone is always ready to accept
        // files/text from the PC without requiring a manual button press.
        startReceivingMode()
    }

    private fun ensureForeground() {
        val title = if (isReceiving) {
            getString(R.string.notif_title_receiving)
        } else {
            val pc = WameedPrefs.getPcIp(this).ifEmpty { getString(R.string.label_pc_generic) }
            getString(R.string.notif_title_connected, pc)
        }

        val text = if (isReceiving) {
            getString(R.string.notif_text_ready_to_receive)
        } else {
            getString(R.string.notif_text_keep_alive)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    buildNotification(title, text),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    NOTIF_ID,
                    buildNotification(title, text)
                )
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Foreground service not allowed, using regular notification", e)
                // Fallback to regular notification if foreground service is not allowed
                try {
                    val notification = buildNotification(title, text)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.notify(NOTIF_ID, notification)
                } catch (fallbackException: Exception) {
                    Log.e(TAG, "Failed to show regular notification", fallbackException)
                }
            } else {
                Log.e(TAG, "Failed to start foreground service", e)
                // Try again with a delay
                handler.postDelayed({
                    try {
                        startForeground(
                            NOTIF_ID,
                            buildNotification(title, text)
                        )
                    } catch (retryException: Exception) {
                        Log.e(TAG, "Retry failed for foreground service", retryException)
                    }
                }, 1000)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        cancelPendingReceiveShutdown()
        stopReceivingMode()
        try { ws?.close(1000, "service stop") } catch (_: Exception) {}
        ws = null
        Log.i(TAG, "Service destroyed")
    }

    // ------------------------- Discovery -------------------------
    private fun startBackgroundDiscovery() {
        if (isDiscovering) return
        isDiscovering = true

        Log.i(TAG, "بدء البحث التلقائي عن الكمبيوتر في الخلفية...")
        val discovery = DeviceDiscovery()
        discovery.startListening(this, timeoutSeconds = 10, object : DeviceDiscovery.DiscoveryCallback {
            override fun onDeviceFound(device: DeviceDiscovery.DiscoveredDevice) {
                Log.i(TAG, "✅ اكتشاف الكمبيوتر تلقائياً: ${device.name} (${device.address})")
                
                val oldIp = WameedPrefs.getPcIp(this@WameedConnectionService)
                val oldPort = WameedPrefs.getPcPort(this@WameedConnectionService)
                
                if (oldIp != device.ip || oldPort != device.port) {
                    Log.i(TAG, "تحديث عنوان الكمبيوتر: $oldIp:$oldPort \u2192 ${device.address}")
                }
                
                // Update prefs with new IP
                WameedPrefs.savePcAddress(this@WameedConnectionService, device.address)
                WameedPrefs.setPcName(this@WameedConnectionService, device.name)
                
                pcDisplay = device.name
                pingFailures = 0 // Reset failures
                
                // Stop discovery early and reconnect
                discovery.stop()
                isDiscovering = false
                
                handler.post { 
                    Log.d(TAG, "إعادة الاتصال بالكمبيوتر المكتشف حديثاً...")
                    openWs() 
                }
            }

            override fun onError(error: String) {
                Log.e(TAG, "❌ خطأ أثناء البحث في الخلفية: $error")
                isDiscovering = false
            }

            override fun onSearchFinished() {
                Log.d(TAG, "انتهى البحث في الخلفية")
                isDiscovering = false
            }
        })
    }

    // ------------------------- WebSocket -------------------------
    private fun openWs() {
        val url = WameedPrefs.getWsUrl(this)
        if (url.contains("://:")) {
            Log.w(TAG, "No PC configured — stopping")
            stopSelfCleanly("not-configured")
            return
        }
        val request = Request.Builder().url(url).build()
        ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WS open — sending hello")
                val deviceName = WameedPrefs.getDeviceName()
                val hello = JSONObject().apply {
                    put("type", "hello")
                    put("device", deviceName)
                    put("name", deviceName) // redundant but safer for some PC versions
                    put("device_id", WameedPrefs.getOrCreateDeviceId(this@WameedConnectionService))
                    put("app_version", BuildConfig.VERSION_NAME)
                    put("receiver_port", 7789)
                }
                webSocket.send(hello.toString())
                pingFailures = 0
                WameedEvents.tryEmit(WameedEvent.ServiceStatus(true, pcDisplay))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Any reply counts as healthy.
                pingFailures = 0
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WS failure: ${t.message}")
                // Clear handle so a subsequent refresh() / schedulePing tick
                // will detect ws==null and reopen cleanly.
                ws = null
                pingFailures++
                WameedEvents.tryEmit(WameedEvent.ServiceStatus(false, pcDisplay))
                
                if (pingFailures >= MAX_PING_FAILURES) {
                    if (!isDiscovering) {
                        Log.i(TAG, "Connection lost to $pcDisplay. Starting background discovery...")
                        startBackgroundDiscovery()
                    } else {
                        // If we are already discovering and still failing, maybe the PC is offline.
                        // We'll let the idle timer eventually stop the service.
                        Log.d(TAG, "Already discovering or PC is offline.")
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WS closed code=$code reason=$reason")
                // Clear handle so we reconnect when the user next interacts.
                ws = null
                WameedEvents.tryEmit(WameedEvent.ServiceStatus(false, pcDisplay))
            }
        })
    }

    private fun schedulePing() {
        handler.postDelayed({
            try {
                val w = ws
                if (w != null) {
                    val ok = w.send(JSONObject().put("type", "ping").toString())
                    if (!ok) {
                        pingFailures++
                        Log.w(TAG, "ping queue rejected — failures=$pingFailures")
                    }
                } else {
                    // WS dropped silently — reopen.
                    openWs()
                }
                if (pingFailures >= MAX_PING_FAILURES) {
                    stopSelfCleanly("ping-failed")
                    return@postDelayed
                }
            } catch (e: Exception) {
                Log.e(TAG, "ping error", e)
            }
            schedulePing()
        }, PING_INTERVAL_MS)
    }

    private fun scheduleIdleWatch() {
        handler.postDelayed({
            val idle = System.currentTimeMillis() - lastActivity
            if (idle >= IDLE_TIMEOUT_MS) {
                Log.i(TAG, "Idle ${idle}ms >= ${IDLE_TIMEOUT_MS}ms — auto-stop")
                stopSelfCleanly("idle")
                return@postDelayed
            }
            scheduleIdleWatch()
        }, 30_000L)  // check every 30s
    }

    private fun cancelPendingReceiveShutdown() {
        receiveShutdownRunnable?.let { handler.removeCallbacks(it) }
        receiveShutdownRunnable = null
    }

    private fun scheduleReceiveShutdown() {
        cancelPendingReceiveShutdown()
        val r = Runnable {
            Log.i(TAG, "Receiver grace expired — stopping receiver server")
            stopReceivingMode()
        }
        receiveShutdownRunnable = r
        handler.postDelayed(r, RECEIVE_IDLE_TIMEOUT_MS)
        Log.i(TAG, "Receiver server grace scheduled (${RECEIVE_IDLE_TIMEOUT_MS / 1000}s)")
    }

    private fun stopSelfCleanly(reason: String) {
        Log.i(TAG, "stopSelfCleanly: $reason")
        handler.removeCallbacksAndMessages(null) // cancel ping + idle watchers
        
        // Safety: Ensure foreground is handled to prevent crash if system kills us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        try { ws?.close(1000, reason) } catch (_: Exception) {}
        ws = null

        if (reason == "user") {
            // User explicitly stopped — kill everything immediately.
            cancelPendingReceiveShutdown()
            stopReceivingMode()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else if (isReceiving) {
            // WS keep-alive died (idle / ping-failed) but receiver server
            // is still up. Keep the service alive with a notification so the
            // PC can still send files for RECEIVE_IDLE_TIMEOUT_MS.
            Log.i(TAG, "Receiver still active — grace period ${RECEIVE_IDLE_TIMEOUT_MS / 1000}s")
            cancelPendingReceiveShutdown()
            val r = Runnable {
                Log.i(TAG, "Receiver grace expired — full stop")
                stopReceivingMode()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            receiveShutdownRunnable = r
            handler.postDelayed(r, RECEIVE_IDLE_TIMEOUT_MS)
        } else {
            // Nothing to keep alive — stop immediately.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startReceivingMode() {
        cancelPendingReceiveShutdown()
        lastReceiveActivity = System.currentTimeMillis()
        if (isReceiving) return
        isReceiving = true
        
        if (receiverServer == null) {
            receiverServer = WameedServer(this)
            receiverServer?.setCallback(object : WameedServer.ServerCallback {
                override fun onDeviceConnected(name: String, ip: String) {
                    pcDisplay = name
                    // Update notification if needed
                    ensureForeground()
                }

                override fun onFileTransferStarted(filename: String, size: Long) {
                    WameedEvents.tryEmit(WameedEvent.ReceiveMeta(filename, size))
                    // Note: We do NOT open ReceiveActivity here anymore.
                    // onTransferCompleted opens it with the final URI so small files
                    // that finish before the Activity is created never get stuck.
                }

                override fun onProgress(percent: Int, speedMbps: Double) {
                    lastActivity = System.currentTimeMillis() // Reset idle timer during active transfer
                    WameedEvents.tryEmit(WameedEvent.ReceiveProgress(percent, speedMbps))
                }

                override fun onTransferCompleted(file: File, filename: String) {
                    val uri = FileSaver.saveFileToDownloads(this@WameedConnectionService, file, filename)
                    WameedEvents.tryEmit(WameedEvent.ReceiveComplete(uri?.toString()))

                    // إضافة السجل للتاريخ
                    WameedPrefs.addHistoryEntry(this@WameedConnectionService, filename, "file", file.length(), "success", "received")

                    // Open ReceiveActivity with the final URI embedded.
                    // This way even tiny files that finish in <50ms show success immediately.
                    val intent = Intent(this@WameedConnectionService, ReceiveActivity::class.java).apply {
                        putExtra("filename", filename)
                        putExtra("size", file.length())
                        putExtra("completed_uri", uri?.toString())
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "Server Error: $error")
                    WameedEvents.tryEmit(WameedEvent.ReceiveError(error))
                }

                override fun onPairingRequest(deviceName: String, deviceId: String) {
                    val intent = Intent(this@WameedConnectionService, ReceiveActivity::class.java).apply {
                        putExtra("pairing_request", true)
                        putExtra("device_name", deviceName)
                        putExtra("device_id", deviceId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }

                override fun onTextReceived(text: String, from: String) {
                    Log.i(TAG, "Text received from $from: ${text.take(50)}")
                    WameedEvents.tryEmit(WameedEvent.ReceiveText(text, from))
                    WameedPrefs.addHistoryEntry(this@WameedConnectionService, "نص من $from", "text", text.length.toLong(), "success", "received")
                    val intent = Intent(this@WameedConnectionService, ReceiveActivity::class.java).apply {
                        putExtra("received_text", text)
                        putExtra("device_name", from)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }

                override fun onUrlReceived(url: String, from: String) {
                    Log.i(TAG, "URL received from $from: $url")
                    WameedEvents.tryEmit(WameedEvent.ReceiveUrl(url, from))
                    WameedPrefs.addHistoryEntry(this@WameedConnectionService, "رابط من $from", "url", url.length.toLong(), "success", "received")
                    val intent = Intent(this@WameedConnectionService, ReceiveActivity::class.java).apply {
                        putExtra("received_url", url)
                        putExtra("device_name", from)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            })
        }
        
        receiverServer?.start()

        if (nsdHelper == null) {
            nsdHelper = WameedNsdHelper(this)
        }
        nsdHelper?.registerService(7789)

        if (discoveryResponder == null) {
            discoveryResponder = WameedDiscoveryResponder(this)
        }
        discoveryResponder?.start()
        
        // تحديث الإشعار
        val notification = buildNotification(
            getString(R.string.notif_title_receiving),
            getString(R.string.notif_text_ready_to_receive)
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, notification)
        
        Log.i(TAG, "Receiving mode started")
    }

    private fun stopReceivingMode() {
        if (!isReceiving) return
        isReceiving = false
        receiverServer?.stop()
        nsdHelper?.unregisterService()
        discoveryResponder?.stop()
        Log.i(TAG, "Receiving mode stopped")
        
        // إعادة الإشعار للوضع الطبيعي إذا كانت الخدمة لا تزال تعمل
        if (bootstrapped) {
            val pcDisplay = WameedPrefs.getPcIp(this).ifEmpty { getString(R.string.label_pc_generic) }
            val notification = buildNotification(
                getString(R.string.notif_title_connected, pcDisplay),
                getString(R.string.notif_text_keep_alive)
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIF_ID, notification)
        }
    }

    // ------------------------- Notification -------------------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        // Tap notification → open Wameed main screen.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action: stop the service now.
        val stopIntent = Intent(this, WameedConnectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .addAction(0, getString(R.string.notif_action_stop), stopPi)
            .setShowWhen(false)
            .build()
    }
}
