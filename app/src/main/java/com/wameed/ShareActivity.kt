package com.wameed

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.core.content.IntentCompat

/**
 * هذه الشاشة تظهر لثوانٍ فقط عند الضغط على "مشاركة عبر وميض"
 * تُرسل المحتوى فوراً وتختفي
 */
class ShareActivity : Activity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var sender: WameedSender
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawableResource(android.R.color.transparent)

        sender = WameedSender(this)

        if (!WameedPrefs.isConfigured(this)) {
            showSetupNeeded()
            return
        }

        // User is sharing from another app -> start keep-alive so a big transfer
        // isn't killed if they switch back. Auto-stops after 5 min idle.
        WameedConnectionService.start(this)

        buildUI()
        handleIntent(intent)
    }

    private fun buildUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }

        statusText = TextView(this).apply {
            text = getString(R.string.sending)
            textSize = 16f
            gravity = Gravity.CENTER
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
            max = 100
            progress = 0
        }

        val cancelBtn = Button(this).apply {
            text = getString(R.string.cancel)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setOnClickListener { finish() }
        }

        layout.addView(statusText)
        layout.addView(progressBar)
        layout.addView(cancelBtn)

        setContentView(layout)

        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action ?: return

        if (action == Intent.ACTION_SEND) {
            val mimeType = intent.type ?: ""

            when {
                mimeType == "text/plain" -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    if (text.isNotEmpty()) {
                        sendText(text)
                    } else {
                        showError(getString(R.string.error_no_content))
                    }
                }
                else -> {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (uri != null) {
                        sendFile(uri)
                    } else {
                        showError(getString(R.string.error_no_file))
                    }
                }
            }
        } else if (action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = IntentCompat.getParcelableArrayListExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )
            if (!uris.isNullOrEmpty()) {
                sendMultipleFiles(uris)
            } else {
                showError(getString(R.string.error_no_files))
            }
        }
    }

    private fun sendText(text: String) {
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        statusText.text = if (isUrl) getString(R.string.sending_url) else getString(R.string.sending_text)
        val label = if (isUrl) text.take(60) else getString(R.string.label_text_content)
        val type = if (isUrl) "url" else "text/plain"

        sender.sendText(text, object : WameedSender.SendCallback {
            override fun onSuccess(message: String) {
                WameedPrefs.addHistoryEntry(this@ShareActivity, label, type,
                    text.toByteArray().size.toLong(), "success")
                runOnUiThread {
                    statusText.text = getString(R.string.success_message, message)
                    progressBar.progress = 100
                    statusText.postDelayed({ finish() }, 300)
                }
            }

            override fun onError(error: String) {
                WameedPrefs.addHistoryEntry(this@ShareActivity, label, type,
                    text.toByteArray().size.toLong(), "error")
                runOnUiThread { showError(error) }
            }

            override fun onProgress(percent: Int) {
                runOnUiThread { progressBar.progress = percent }
            }

            override fun onProgress(percent: Int, speedMbps: Double) {
                runOnUiThread {
                    progressBar.progress = percent
                    if (speedMbps > 0) {
                        val speedStr = "%.1f".format(speedMbps)
                        statusText.text = if (isUrl) getString(R.string.sending_url_speed, speedStr)
                        else getString(R.string.sending_text_speed, speedStr)
                    } else {
                        statusText.text = if (isUrl) getString(R.string.sending_url)
                        else getString(R.string.sending_text)
                    }
                }
            }

            override fun onInfo(message: String) {
                runOnUiThread { statusText.text = message }
            }
        })
    }

    private fun sendFile(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: ""
        val typeLabel = when {
            mimeType.startsWith("image/") -> getString(R.string.label_image)
            mimeType.contains("pdf") -> getString(R.string.label_pdf)
            mimeType.startsWith("video/") -> getString(R.string.label_video)
            else -> getString(R.string.label_file)
        }
        statusText.text = getString(R.string.sending_type, typeLabel)
        val filename = getFilenameFromUri(uri)
        var fileSize = 0L
        try { contentResolver.openAssetFileDescriptor(uri, "r")?.use { fileSize = it.length } } catch (_: Exception) {}

        sender.sendFile(uri, object : WameedSender.SendCallback {
            override fun onSuccess(message: String) {
                WameedPrefs.addHistoryEntry(this@ShareActivity, filename, mimeType, fileSize, "success")
                runOnUiThread {
                    statusText.text = getString(R.string.success_message, message)
                    progressBar.progress = 100
                    statusText.postDelayed({ finish() }, 300)
                }
            }

            override fun onError(error: String) {
                WameedPrefs.addHistoryEntry(this@ShareActivity, filename, mimeType, fileSize, "error")
                runOnUiThread { showError(error) }
            }

            override fun onProgress(percent: Int) {
                runOnUiThread { progressBar.progress = percent }
            }

            override fun onProgress(percent: Int, speedMbps: Double) {
                runOnUiThread {
                    progressBar.progress = percent
                    if (speedMbps > 0) {
                        val speedStr = "%.1f".format(speedMbps)
                        statusText.text = getString(R.string.sending_type_speed, typeLabel, speedStr)
                    } else {
                        statusText.text = getString(R.string.sending_type, typeLabel)
                    }
                }
            }

            override fun onInfo(message: String) {
                runOnUiThread { statusText.text = message }
            }
        })
    }

    private fun sendMultipleFiles(uris: List<Uri>) {
        val total = uris.size
        statusText.text = getString(R.string.sending_multiple, total)
        progressBar.max = total * 100
        sendNextFile(uris, 0, total, 0)
    }

    private fun sendNextFile(uris: List<Uri>, index: Int, total: Int, failCount: Int) {
        if (index >= total) {
            runOnUiThread {
                val sent = total - failCount
                statusText.text = if (failCount == 0) getString(R.string.success_all_sent, total)
                                  else getString(R.string.success_partial, sent, total, failCount)
                progressBar.progress = total * 100
                statusText.postDelayed({ finish() }, 1500)
            }
            return
        }

        runOnUiThread {
            statusText.text = getString(R.string.sending_progress, index + 1, total)
        }

        sender.sendFile(uris[index], object : WameedSender.SendCallback {
            override fun onSuccess(message: String) {
                runOnUiThread { progressBar.progress = (index + 1) * 100 }
                sendNextFile(uris, index + 1, total, failCount)
            }

            override fun onError(error: String) {
                sendNextFile(uris, index + 1, total, failCount + 1)
            }

            override fun onProgress(percent: Int) {
                runOnUiThread { progressBar.progress = index * 100 + percent }
            }

            override fun onProgress(percent: Int, speedMbps: Double) {
                runOnUiThread {
                    progressBar.progress = index * 100 + percent
                    if (speedMbps > 0) {
                        val speedStr = "%.1f".format(speedMbps)
                        statusText.text = getString(R.string.sending_progress_speed, index + 1, total, speedStr)
                    } else {
                        statusText.text = getString(R.string.sending_progress, index + 1, total)
                    }
                }
            }
        })
    }

    private fun showError(message: String) {
        statusText.text = getString(R.string.error_prefix, message)
        val btn = Button(this).apply {
            text = getString(R.string.open_settings)
            setOnClickListener {
                startActivity(Intent(this@ShareActivity, MainActivity::class.java))
                finish()
            }
        }
        (statusText.parent as? LinearLayout)?.addView(btn)
    }

    private fun getFilenameFromUri(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) return cursor.getString(idx) ?: "unknown"
            }
        }
        return "unknown"
    }

    private fun showSetupNeeded() {
        android.widget.Toast.makeText(this, getString(R.string.setup_needed), android.widget.Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
