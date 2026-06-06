package com.wameed

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * تكامل نظام التحديث مع واجهة المستخدم
 */
@Composable
fun UpdateIntegration(
    updateManager: WameedUpdateManager,
    context: Context
) {
    val updateState by updateManager.updateState.collectAsState()
    val updateInfo by updateManager.lastUpdateInfo.collectAsState()
    val showUpdateDialog = remember { mutableStateOf(false) }
    val showUpdateNotification = remember { mutableStateOf(false) }
    var installResult by remember { mutableStateOf<UpdateInstallResult?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun consumeInstallResult() {
        val result = updateManager.consumePendingInstallResult() ?: return
        if (result.completed) {
            Toast.makeText(
                context,
                context.getString(R.string.update_install_verified, result.installedVersionName),
                Toast.LENGTH_LONG
            ).show()
            showUpdateDialog.value = false
            showUpdateNotification.value = false
        } else {
            installResult = result
            showUpdateDialog.value = false
        }
    }
    
    // Check for updates on app start (silently)
    LaunchedEffect(Unit) {
        consumeInstallResult()
        delay(3000) // Wait a bit after app start
        // البحث التلقائي صامت (isManual = false) حتى لا تظهر حالة "جاري البحث" في الإعدادات فجأة
        if (installResult == null && updateManager.checkForUpdates(isManual = false)) {
            showUpdateNotification.value = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                consumeInstallResult()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Handle update state changes
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateState.Available -> {
                showUpdateDialog.value = true
            }
            is UpdateState.Installed -> {
                showUpdateDialog.value = false
                showUpdateNotification.value = false
            }
            is UpdateState.Downloaded -> {
                // التثبيت يبدأ تلقائياً بعد التنزيل
            }
            is UpdateState.Failed -> {
                showUpdateDialog.value = false
            }
            is UpdateState.InstallNotCompleted -> {
                showUpdateDialog.value = false
            }
            else -> {}
        }
    }
    
    // Update dialog
    WameedUpdateDialog(
        isVisible = showUpdateDialog.value,
        updateState = updateState,
        updateInfo = updateInfo,
        onUpdateAccepted = {
            coroutineScope.launch {
                updateManager.startFlexibleUpdate(context as ComponentActivity)
            }
        },
        onUpdateDeclined = {
            showUpdateDialog.value = false
        },
        onDismiss = {
            showUpdateDialog.value = false
        }
    )
    
    // Update notification (small banner)
    WameedUpdateNotification(
        isVisible = showUpdateNotification.value && updateState is UpdateState.Available,
        message = updateInfo?.remoteVersionName?.takeIf { it.isNotBlank() }?.let {
            stringResource(R.string.update_available_detail, it)
        } ?: stringResource(R.string.update_notification_desc),
        onUpdateClick = {
            showUpdateDialog.value = true
            showUpdateNotification.value = false
        },
        onDismiss = {
            showUpdateNotification.value = false
        }
    )

    installResult?.let { result ->
        AlertDialog(
            onDismissRequest = { installResult = null },
            title = { Text(stringResource(R.string.update_install_not_completed_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.update_install_not_completed_message,
                        result.expectedVersionName.ifBlank { result.expectedVersionCode.toString() },
                        result.installedVersionName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { installResult = null }) {
                    Text(stringResource(R.string.update_close))
                }
            }
        )
    }
}
