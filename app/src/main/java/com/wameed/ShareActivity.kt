package com.wameed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────
// حالات الإرسال
// ─────────────────────────────────────────
sealed class SendState {
    object Idle : SendState()
    data class Sending(
        val label: String,
        val percent: Int = 0,
        val speedMbps: Double = 0.0,
        val currentIndex: Int = 0,
        val total: Int = 1
    ) : SendState()
    data class Success(val message: String) : SendState()
    data class Error(val message: String) : SendState()
}

// ─────────────────────────────────────────
// ألوان وميض — ثابتة
// ─────────────────────────────────────────
private val WameedGreen   = Color(0xFF2E7D32)
private val WameedGreenLt = Color(0xFF43A047)
private val SurfaceLight  = Color(0xFFF8F9FA)
private val OnSurface     = Color(0xFF1C1B1F)
private val OnSurfaceMed  = Color(0xFF49454F)
private val OnSurfaceLow  = Color(0xFF79747E)

// ─────────────────────────────────────────
// نموذج البيانات
// ─────────────────────────────────────────
sealed class ShareData {
    object Empty : ShareData()
    data class Text(val content: String) : ShareData()
    data class SingleFile(val uri: Uri, val mime: String) : ShareData()
    data class MultipleFiles(val uris: List<Uri>) : ShareData()
}

// ─────────────────────────────────────────
// Activity
// ─────────────────────────────────────────
class ShareActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var sender: WameedSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // خلفية شفافة لإظهار التطبيق خلف الـ Sheet
        window.setBackgroundDrawableResource(android.R.color.transparent)

        if (!WameedPrefs.isConfigured(this) && !WameedConnectionService.isRunning) {
            android.widget.Toast.makeText(
                this, getString(R.string.setup_needed), android.widget.Toast.LENGTH_LONG
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // User is sharing from another app -> start keep-alive so a big transfer
        // isn't killed if they switch back. Auto-stops after 5 min idle.
        WameedConnectionService.start(this)
        sender = WameedSender(this)

        // استخرج المحتوى من Intent قبل setContent
        val shareData = extractShareData(intent)

        Log.i("Wameed", "📤 فتح شاشة المشاركة | action=${intent.action} type=${intent.type}")

        setContent {
            MaterialTheme {
                WameedShareSheet(
                    shareData = shareData,
                    sender = sender,
                    activity = this@ShareActivity,
                    onDismiss = { finish() },
                    onOpenSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }

    // ─── استخراج البيانات من Intent ───────
    private fun extractShareData(intent: Intent): ShareData {
        val action = intent.action ?: return ShareData.Empty

        return when (action) {
            Intent.ACTION_SEND -> {
                val mime = intent.type ?: ""
                if (mime == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    if (text.isEmpty()) ShareData.Empty else ShareData.Text(text)
                } else {
                    val uri = IntentCompat.getParcelableExtra(
                        intent, Intent.EXTRA_STREAM, Uri::class.java
                    )
                    if (uri != null) ShareData.SingleFile(uri, mime)
                    else ShareData.Empty
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(
                    intent, Intent.EXTRA_STREAM, Uri::class.java
                )
                if (!uris.isNullOrEmpty()) ShareData.MultipleFiles(uris)
                else ShareData.Empty
            }
            else -> ShareData.Empty
        }
    }

    // ─── استخراج اسم الملف من Uri — للسجل ───
    fun getFilenameFromUri(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1) return cursor.getString(idx) ?: "unknown"
            }
        }
        return "unknown"
    }

    // ─── حجم الملف — للسجل ───
    fun getFileSize(uri: Uri): Long {
        var size = 0L
        try { contentResolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length } } catch (_: Exception) {}
        return size
    }
}

// ─────────────────────────────────────────
// الـ Bottom Sheet الرئيسي
// ─────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WameedShareSheet(
    shareData: ShareData,
    sender: WameedSender,
    activity: ShareActivity,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var sendState by remember { mutableStateOf<SendState>(SendState.Idle) }
    val scope = rememberCoroutineScope()

    // ابدأ الإرسال فور ظهور الـ Sheet
    LaunchedEffect(shareData) {
        if (shareData != ShareData.Empty) {
            startSending(shareData, sender, activity,
                onState = { sendState = it },
                onDone = {
                    scope.launch {
                        delay(500)
                        sheetState.hide()
                        onDismiss()
                    }
                }
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceLight,
        dragHandle = {
            // Drag handle مخصص
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFFCAC4D0))
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        SheetContent(
            shareData = shareData,
            sendState = sendState,
            onCancel = onDismiss,
            onOpenSettings = onOpenSettings
        )
    }
}

// ─────────────────────────────────────────
// محتوى الـ Sheet
// ─────────────────────────────────────────
@Composable
private fun SheetContent(
    shareData: ShareData,
    sendState: SendState,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // ─── Header ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة وميض
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(WameedGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "و",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )
                Text(
                    text = contentLabel(shareData),
                    fontSize = 13.sp,
                    color = OnSurfaceLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // زر الإلغاء — يظهر فقط أثناء الإرسال أو الخطأ
            AnimatedVisibility(
                visible = sendState is SendState.Sending || sendState is SendState.Error
            ) {
                TextButton(onClick = onCancel) {
                    Text(
                        text = stringResource(R.string.cancel),
                        color = OnSurfaceMed,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ─── المحتوى حسب الحالة ─────────────
        AnimatedContent(
            targetState = sendState,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },
            label = "state"
        ) { state ->
            when (state) {
                is SendState.Idle -> LoadingIndicator()

                is SendState.Sending -> SendingContent(state)

                is SendState.Success -> SuccessContent(state.message)

                is SendState.Error -> ErrorContent(
                    message = state.message,
                    onOpenSettings = onOpenSettings,
                    onRetry = onCancel
                )
            }
        }
    }
}

// ─────────────────────────────────────────
// حالة: جارٍ التحضير
// ─────────────────────────────────────────
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = WameedGreen,
            strokeWidth = 2.dp,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ─────────────────────────────────────────
// حالة: جارٍ الإرسال
// ─────────────────────────────────────────
@Composable
private fun SendingContent(state: SendState.Sending) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // نص الحالة
        Text(
            text = state.label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // إذا كان هناك أكثر من ملف: عداد
        if (state.total > 1) {
            Text(
                text = "${state.currentIndex} / ${state.total}",
                fontSize = 12.sp,
                color = OnSurfaceLow,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // شريط التقدم
        LinearProgressIndicator(
            progress = { state.percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = WameedGreen,
            trackColor = Color(0xFFE8F5E9),
            strokeCap = StrokeCap.Round
        )

        Spacer(modifier = Modifier.height(10.dp))

        // السرعة والنسبة في صف واحد
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (state.speedMbps > 0) "${"%.1f".format(state.speedMbps)} Mbps" else "",
                fontSize = 12.sp,
                color = OnSurfaceLow
            )
            Text(
                text = "${state.percent}%",
                fontSize = 12.sp,
                color = WameedGreen,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ─────────────────────────────────────────
// حالة: نجاح
// ─────────────────────────────────────────
@Composable
private fun SuccessContent(message: String) {
    // أنيميشن ظهور
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "success_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // دائرة النجاح
        Box(
            modifier = Modifier
                .size((56 * scale).dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✓", fontSize = 24.sp, color = WameedGreen)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = WameedGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ─────────────────────────────────────────
// حالة: خطأ
// ─────────────────────────────────────────
@Composable
private fun ErrorContent(
    message: String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // أيقونة الخطأ
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "✕", fontSize = 22.sp, color = Color(0xFFE53935))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = OnSurfaceMed,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // زر الإعدادات — إذا كانت المشكلة في الاتصال
        val connectionKeywords = listOf(
            context.getString(R.string.label_pc_generic),
            context.getString(R.string.not_connected)
        )
        if (connectionKeywords.any { message.contains(it, ignoreCase = true) } ||
            message.contains("كمبيوتر") || message.contains("اتصال") || message.contains("شبكة") ||
            message.contains("connect", ignoreCase = true) || message.contains("network", ignoreCase = true)
        ) {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = WameedGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.open_settings),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMed)
        ) {
            Text(
                text = stringResource(R.string.close),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────
// منطق الإرسال — خارج الـ Composable
// ─────────────────────────────────────────
private suspend fun startSending(
    shareData: ShareData,
    sender: WameedSender,
    activity: ShareActivity,
    onState: (SendState) -> Unit,
    onDone: () -> Unit
) {
    when (shareData) {
        is ShareData.Text -> sendText(shareData.content, sender, activity, onState, onDone)
        is ShareData.SingleFile -> sendSingleFile(shareData.uri, shareData.mime, sender, activity, onState, onDone)
        is ShareData.MultipleFiles -> sendMultiple(shareData.uris, sender, activity, onState, onDone)
        ShareData.Empty -> onState(SendState.Error(activity.getString(R.string.error_no_content)))
    }
}

private fun sendText(
    text: String,
    sender: WameedSender,
    activity: ShareActivity,
    onState: (SendState) -> Unit,
    onDone: () -> Unit
) {
    val isUrl = text.startsWith("http://") || text.startsWith("https://")
    val label = activity.getString(R.string.sending)
    val historyLabel = if (isUrl) text.take(60) else activity.getString(R.string.label_text_content)
    val historyType = if (isUrl) "url" else "text/plain"
    onState(SendState.Sending(label = label))

    sender.sendText(text, object : WameedSender.SendCallback {
        override fun onSuccess(message: String) {
            Log.i("Wameed", "✅ تم إرسال النص بنجاح")
            WameedPrefs.addHistoryEntry(activity, historyLabel, historyType,
                text.toByteArray().size.toLong(), "success")
            onState(SendState.Success(message))
            onDone()
        }
        override fun onError(error: String) {
            Log.e("Wameed", "❌ فشل إرسال النص: $error")
            WameedPrefs.addHistoryEntry(activity, historyLabel, historyType,
                text.toByteArray().size.toLong(), "error")
            onState(SendState.Error(error))
        }
        override fun onProgress(percent: Int) =
            onState(SendState.Sending(label = label, percent = percent))
        override fun onProgress(percent: Int, speedMbps: Double) =
            onState(SendState.Sending(label = label, percent = percent, speedMbps = speedMbps))
        override fun onInfo(message: String) =
            onState(SendState.Sending(label = label, percent = 0))
    })
}

private fun sendSingleFile(
    uri: Uri,
    mime: String,
    sender: WameedSender,
    activity: ShareActivity,
    onState: (SendState) -> Unit,
    onDone: () -> Unit
) {
    val label = activity.getString(R.string.sending)
    val filename = activity.getFilenameFromUri(uri)
    val fileSize = activity.getFileSize(uri)
    onState(SendState.Sending(label = label))

    sender.sendFile(uri, object : WameedSender.SendCallback {
        override fun onSuccess(message: String) {
            Log.i("Wameed", "✅ تم إرسال الملف $filename بنجاح")
            WameedPrefs.addHistoryEntry(activity, filename, mime, fileSize, "success")
            onState(SendState.Success(message))
            onDone()
        }
        override fun onError(error: String) {
            Log.e("Wameed", "❌ فشل إرسال الملف $filename: $error")
            WameedPrefs.addHistoryEntry(activity, filename, mime, fileSize, "error")
            onState(SendState.Error(error))
        }
        override fun onProgress(percent: Int) =
            onState(SendState.Sending(label = label, percent = percent))
        override fun onProgress(percent: Int, speedMbps: Double) =
            onState(SendState.Sending(label = label, percent = percent, speedMbps = speedMbps))
        override fun onInfo(message: String) =
            onState(SendState.Sending(label = label, percent = 0))
    })
}

private fun sendMultiple(
    uris: List<Uri>,
    sender: WameedSender,
    activity: ShareActivity,
    onState: (SendState) -> Unit,
    onDone: () -> Unit
) {
    val total = uris.size

    fun sendNext(index: Int, failCount: Int) {
        if (index >= total) {
            val sent = total - failCount
            val msg = if (failCount == 0) activity.getString(R.string.success_all_sent, total)
                      else activity.getString(R.string.success_partial, sent, total, failCount)
            onState(SendState.Success(msg))
            onDone()
            return
        }

        val progressLabel = activity.getString(R.string.sending)
        onState(SendState.Sending(
            label = progressLabel,
            currentIndex = index + 1,
            total = total
        ))

        val filename = activity.getFilenameFromUri(uris[index])
        val mime = activity.contentResolver.getType(uris[index]) ?: ""
        val fileSize = activity.getFileSize(uris[index])

        sender.sendFile(uris[index], object : WameedSender.SendCallback {
            override fun onSuccess(message: String) {
                Log.i("Wameed", "✅ تم إرسال $filename (${index + 1}/$total)")
                WameedPrefs.addHistoryEntry(activity, filename, mime, fileSize, "success")
                sendNext(index + 1, failCount)
            }
            override fun onError(error: String) {
                Log.e("Wameed", "❌ فشل إرسال $filename: $error")
                WameedPrefs.addHistoryEntry(activity, filename, mime, fileSize, "error")
                sendNext(index + 1, failCount + 1)
            }
            override fun onProgress(percent: Int) = onState(
                SendState.Sending(
                    label = progressLabel,
                    percent = percent,
                    currentIndex = index + 1,
                    total = total
                )
            )
            override fun onProgress(percent: Int, speedMbps: Double) = onState(
                SendState.Sending(
                    label = progressLabel,
                    percent = percent,
                    speedMbps = speedMbps,
                    currentIndex = index + 1,
                    total = total
                )
            )
            override fun onInfo(message: String) = onState(
                SendState.Sending(label = progressLabel, currentIndex = index + 1, total = total)
            )
        })
    }

    sendNext(0, 0)
}

// ─────────────────────────────────────────
// مساعدات
// ─────────────────────────────────────────
private fun mimeToLabel(mime: String, context: Context) = when {
    mime.startsWith("image/") -> context.getString(R.string.label_image)
    mime.contains("pdf")      -> context.getString(R.string.label_pdf)
    mime.startsWith("video/") -> context.getString(R.string.label_video)
    mime.startsWith("audio/") -> context.getString(R.string.label_audio)
    else                      -> context.getString(R.string.label_file)
}

@Composable
private fun contentLabel(data: ShareData): String {
    val context = LocalContext.current
    return when (data) {
        is ShareData.Text -> data.content.take(50)
        is ShareData.SingleFile -> mimeToLabel(data.mime, context)
        is ShareData.MultipleFiles -> context.getString(R.string.share_file_count, data.uris.size)
        ShareData.Empty -> ""
    }
}
