package com.wameed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isEmpty
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * شاشة استقبال الملفات من الكمبيوتر.
 * تظهر عند بدء إرسال ملف من الكمبيوتر وتطلب الموافقة ثم تعرض التقدم.
 */
class ReceiveActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var iconView: TextView
    private lateinit var contentContainer: LinearLayout
    private lateinit var buttonsLayout: LinearLayout
    private var filename: String = ""
    private var fileSize: Long = 0
    private var isPairingRequest = false
    private var deviceId: String = ""
    private var deviceName = ""
    private var receivedText: String = ""
    private var receivedUrl: String = ""
    private val handler = Handler(Looper.getMainLooper())
    private var autoCloseRunnable: Runnable? = null
    private var countdownTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // شفافية الخلفية لتظهر كأنها نافذة منبثقة
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        isPairingRequest = intent.getBooleanExtra("pairing_request", false)
        deviceId = intent.getStringExtra("device_id") ?: ""
        deviceName = intent.getStringExtra("device_name") ?: "PC"
        filename = intent.getStringExtra("filename") ?: ""
        receivedText = intent.getStringExtra("received_text") ?: ""
        receivedUrl = intent.getStringExtra("received_url") ?: ""
        val completedUri = intent.getStringExtra("completed_uri")

        buildUI()
        
        if (isPairingRequest) {
            showPairingUI()
        } else if (receivedText.isNotEmpty()) {
            showTextReceivedUI()
        } else if (receivedUrl.isNotEmpty()) {
            showUrlReceivedUI()
        } else if (completedUri != null) {
            // File already finished before this Activity was created — show result directly.
            showCompletedUI(completedUri)
        } else if (filename.isNotEmpty()) {
            showTransferUI()
        }
        
        // Start listening to events
        lifecycleScope.launch {
            WameedEvents.events.collect { event ->
                when (event) {
                    is WameedEvent.ReceiveProgress -> {
                        updateProgress(event.percent, event.speedMbps)
                    }
                    is WameedEvent.ReceiveError -> {
                        showError(event.error)
                    }
                    is WameedEvent.ReceiveComplete -> {
                        updateProgress(100, 0.0, event.uri)
                    }
                    is WameedEvent.ReceiveMeta -> {
                        filename = event.filename
                        fileSize = event.size
                        showTransferUI()
                    }
                    is WameedEvent.ReceiveText -> {
                        receivedText = event.text
                        showTextReceivedUI()
                    }
                    is WameedEvent.ReceiveUrl -> {
                        receivedUrl = event.url
                        showUrlReceivedUI()
                    }
                    is WameedEvent.ServiceStatus -> {
                        // Not used in this activity
                    }
                    is WameedEvent.ReceiverStatus -> {
                        // Not used in this activity
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoCloseRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun buildUI() {
        val cardBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.WHITE)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 56, 64, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            background = cardBackground
            elevation = 16f
        }

        val headerStrip = View(this).apply {
            setBackgroundColor("#2E7D32".toColorInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 6
            ).apply { bottomMargin = 32 }
        }
        layout.addView(headerStrip)

        iconView = TextView(this).apply {
            text = "📲"
            textSize = 44f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        statusText = TextView(this).apply {
            text = getString(R.string.request_received)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor("#1B5E20".toColorInt())
        }

        detailText = TextView(this).apply {
            text = ""
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor("#757575".toColorInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
        }

        contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                20
            ).apply {
                topMargin = 28
                bottomMargin = 12
            }
            max = 100
            progress = 0
            visibility = View.GONE
        }

        countdownTextView = TextView(this).apply {
            text = ""
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor("#9E9E9E".toColorInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4 }
            visibility = View.GONE
        }

        buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 28 }
        }

        layout.addView(iconView)
        layout.addView(statusText)
        layout.addView(detailText)
        layout.addView(contentContainer)
        layout.addView(progressBar)
        layout.addView(countdownTextView)
        layout.addView(buttonsLayout)

        setContentView(layout)

        // حجم النافذة وجاذبيتها
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        // Y offset is applied in onWindowFocusChanged to avoid the layout system
        // resetting it before the first draw (which causes the bottom-screen bug).
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Apply vertical offset once the window is attached and measured.
        val attrs = window.attributes
        attrs.y = (resources.displayMetrics.heightPixels * 0.10).toInt()
        window.attributes = attrs
    }

    private fun showPairingUI() {
        iconView.text = "🤝"
        statusText.text = getString(R.string.pairing_request, deviceName)
        detailText.text = getString(R.string.pairing_request_detail)
        contentContainer.removeAllViews()
        buttonsLayout.removeAllViews()
        
        val rejectBtn = Button(this).apply {
            text = getString(R.string.reject)
            setOnClickListener {
                val intent = Intent(this@ReceiveActivity, WameedConnectionService::class.java).apply {
                    action = WameedConnectionService.ACTION_REJECT_PAIRING
                }
                startService(intent)
                finish()
            }
        }

        val acceptBtn = Button(this).apply {
            text = getString(R.string.accept)
            setOnClickListener {
                if (deviceId.isNotEmpty()) {
                    WameedPrefs.addTrustedDevice(this@ReceiveActivity, deviceId)
                }
                val intent = Intent(this@ReceiveActivity, WameedConnectionService::class.java).apply {
                    action = WameedConnectionService.ACTION_APPROVE_PAIRING
                }
                startService(intent)
                
                // Simplified "Connected" state
                iconView.text = "✅"
                statusText.text = getString(R.string.status_connected_to).replace("{name}", deviceName)
                statusText.setTextColor("#2E7D32".toColorInt())
                detailText.text = ""
                buttonsLayout.removeAllViews()
                
                // Auto-close after 3 seconds for pairing approval
                autoCloseRunnable?.let { handler.removeCallbacks(it) }
                countdownTextView?.visibility = View.VISIBLE
                startCountdown(3)
            }
        }

        buttonsLayout.addView(rejectBtn)
        buttonsLayout.addView(acceptBtn)
    }

    private fun showCompletedUI(uri: String?) {
        iconView.text = ""
        statusText.text = getString(R.string.receive_success)
        statusText.setTextColor("#1B5E20".toColorInt())
        detailText.text = filename
        progressBar.visibility = View.GONE
        contentContainer.removeAllViews()
        buttonsLayout.removeAllViews()
        vibrateSuccess()

        if (uri != null) {
            val openBtn = Button(this).apply {
                text = getString(R.string.open_file_btn)
                setOnClickListener {
                    autoCloseRunnable?.let { handler.removeCallbacks(it) }
                    countdownTextView?.visibility = View.GONE
                    try {
                        val contentUri = uri.toUri()
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, contentResolver.getType(contentUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(openIntent)
                        finish()
                    } catch (_: Exception) {
                        Toast.makeText(this@ReceiveActivity, getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            buttonsLayout.addView(openBtn)
        }

        val closeBtn = Button(this).apply {
            text = getString(R.string.close)
            setOnClickListener {
                autoCloseRunnable?.let { handler.removeCallbacks(it) }
                finish()
            }
        }
        buttonsLayout.addView(closeBtn)

        countdownTextView?.visibility = View.VISIBLE
        startCountdown(7)
    }

    private fun showTransferUI() {
        iconView.text = "📥"
        statusText.text = getString(R.string.receiving_file, filename)
        detailText.text = formatSize(fileSize)
        contentContainer.removeAllViews()
        progressBar.visibility = View.VISIBLE
        buttonsLayout.removeAllViews()
        
        val cancelBtn = Button(this).apply {
            text = getString(R.string.cancel)
            setOnClickListener {
                val intent = Intent(this@ReceiveActivity, WameedConnectionService::class.java).apply {
                    action = WameedConnectionService.ACTION_STOP_RECEIVING
                }
                startService(intent)
                finish()
            }
        }
        buttonsLayout.addView(cancelBtn)
    }

    private fun showTextReceivedUI() {
        runOnUiThread {
            iconView.text = "📝"
            statusText.text = getString(R.string.text_received_from, deviceName)
            statusText.setTextColor("#1B5E20".toColorInt())
            detailText.text = ""
            vibrateSuccess()
            progressBar.visibility = View.GONE
            contentContainer.removeAllViews()
            buttonsLayout.removeAllViews()

            val textView = TextView(this).apply {
                text = receivedText
                textSize = 15f
                setPadding(24, 24, 24, 24)
                setBackgroundColor("#F3F4F6".toColorInt())
                maxLines = 10
                movementMethod = ScrollingMovementMethod()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 }
            }
            contentContainer.addView(textView)

            val copyBtn = Button(this).apply {
                text = getString(R.string.copy)
                setOnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), receivedText))
                    Toast.makeText(this@ReceiveActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }
            val closeBtn = Button(this).apply {
                text = getString(R.string.close)
                setOnClickListener {
                    autoCloseRunnable?.let { handler.removeCallbacks(it) }
                    finish()
                }
            }
            buttonsLayout.addView(copyBtn)
            buttonsLayout.addView(closeBtn)

            // الإغلاق التلقائي للنصوص بعد 30 ثانية
            autoCloseRunnable?.let { handler.removeCallbacks(it) }
            countdownTextView?.visibility = View.VISIBLE
            startCountdown(30)
        }
    }

    private fun showUrlReceivedUI() {
        runOnUiThread {
            iconView.text = "🔗"
            statusText.text = getString(R.string.url_received_from, deviceName)
            statusText.setTextColor("#1565C0".toColorInt())
            detailText.text = ""
            vibrateSuccess()
            progressBar.visibility = View.GONE
            contentContainer.removeAllViews()
            buttonsLayout.removeAllViews()

            val urlView = TextView(this).apply {
                text = receivedUrl
                textSize = 14f
                setPadding(24, 24, 24, 24)
                setBackgroundColor("#EFF6FF".toColorInt())
                setTextColor("#1D4ED8".toColorInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 24 }
            }
            contentContainer.addView(urlView)

            val openBtn = Button(this).apply {
                text = getString(R.string.open)
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, receivedUrl.toUri()))
                    } catch (_: Exception) {
                        Toast.makeText(this@ReceiveActivity, getString(R.string.error_open_link), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val copyBtn = Button(this).apply {
                text = getString(R.string.copy)
                setOnClickListener {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), receivedUrl))
                    Toast.makeText(this@ReceiveActivity, getString(R.string.copied), Toast.LENGTH_SHORT).show()
                }
            }
            val closeBtn = Button(this).apply {
                text = getString(R.string.close)
                setOnClickListener {
                    autoCloseRunnable?.let { handler.removeCallbacks(it) }
                    finish()
                }
            }
            buttonsLayout.addView(openBtn)
            buttonsLayout.addView(copyBtn)
            buttonsLayout.addView(closeBtn)

            // الإغلاق التلقائي للروابط بعد 30 ثانية
            autoCloseRunnable?.let { handler.removeCallbacks(it) }
            countdownTextView?.visibility = View.VISIBLE
            startCountdown(30)
        }
    }

    fun updateProgress(percent: Int, speedMbps: Double, uri: String? = null) {
        runOnUiThread {
            progressBar.progress = percent
            val speedStr = "%.1f".format(speedMbps)
            statusText.text = if (percent < 100) getString(R.string.receiving_file, filename) else getString(R.string.receive_success)
            detailText.text = if (percent < 100) getString(R.string.receiving_speed_detail, percent, speedStr) else ""
            
            if (percent >= 100) {
                // If already in success state, don't rebuild UI
                if (iconView.text == "") return@runOnUiThread

                iconView.text = ""
                statusText.setTextColor(Color.parseColor("#1B5E20"))
                progressBar.visibility = View.GONE
                buttonsLayout.removeAllViews()
                vibrateSuccess()

                if (uri != null) {
                    val openBtn = Button(this).apply {
                        text = getString(R.string.open_file_btn)
                        setOnClickListener {
                            autoCloseRunnable?.let { handler.removeCallbacks(it) }
                            countdownTextView?.visibility = View.GONE
                            try {
                                val contentUri = uri.toUri()
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(contentUri, contentResolver.getType(contentUri))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(intent)
                                finish()
                            } catch (_: Exception) {
                                Toast.makeText(this@ReceiveActivity, getString(R.string.error_open_file), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    buttonsLayout.addView(openBtn)
                }

                val closeBtn = Button(this).apply {
                    text = getString(R.string.close)
                    setOnClickListener {
                        autoCloseRunnable?.let { handler.removeCallbacks(it) }
                        finish()
                    }
                }
                buttonsLayout.addView(closeBtn)

                // عداد تنازلي للإغلاق التلقائي بعد 7 ثوانٍ
                autoCloseRunnable?.let { handler.removeCallbacks(it) }
                countdownTextView?.visibility = View.VISIBLE
                startCountdown(7)
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        if (seconds <= 0) {
            if (!isFinishing) finish()
            return
        }
        countdownTextView?.text = getString(R.string.auto_close_countdown, seconds)
        autoCloseRunnable = Runnable { startCountdown(seconds - 1) }
        handler.postDelayed(autoCloseRunnable!!, 1000)
    }

    private fun vibrateSuccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                vib?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    fun showError(error: String) {
        runOnUiThread {
            iconView.text = "❌"
            statusText.text = getString(R.string.error_occurred)
            detailText.text = error
            progressBar.progress = 0
            
            if (buttonsLayout.isEmpty()) {
                val closeBtn = Button(this).apply {
                    text = getString(R.string.close)
                    setOnClickListener {
                        autoCloseRunnable?.let { handler.removeCallbacks(it) }
                        finish()
                    }
                }
                buttonsLayout.addView(closeBtn)
            }
            
            // Auto-close after 5 seconds on error
            autoCloseRunnable?.let { handler.removeCallbacks(it) }
            countdownTextView?.visibility = View.VISIBLE
            startCountdown(5)
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1048576) return "${"%.1f".format(bytes / 1024.0)} KB"
        if (bytes < 1073741824) return "${"%.1f".format(bytes / 1048576.0)} MB"
        return "${"%.1f".format(bytes / 1073741824.0)} GB"
    }
}
