package com.wameed

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
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
    val showUpdateDialog = remember { mutableStateOf(false) }
    val showUpdateNotification = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // Check for updates on app start
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            delay(2000) // Wait a bit after app start
            if (updateManager.checkForUpdates()) {
                showUpdateNotification.value = true
            }
        }
    }
    
    // Handle update state changes
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UpdateState.Available -> {
                showUpdateDialog.value = true
            }
            is UpdateState.Installed -> {
                Toast.makeText(context, "تم تحديث التطبيق بنجاح!", Toast.LENGTH_LONG).show()
                showUpdateDialog.value = false
                showUpdateNotification.value = false
            }
            is UpdateState.Downloaded -> {
                // For flexible updates, we need to call completeUpdate to finish the installation
                updateManager.completeUpdate()
            }
            is UpdateState.Failed -> {
                Toast.makeText(context, "فشل التحديث: ${state.errorCode}", Toast.LENGTH_LONG).show()
                showUpdateDialog.value = false
            }
            else -> {}
        }
    }
    
    // Update dialog
    WameedUpdateDialog(
        isVisible = showUpdateDialog.value,
        updateState = updateState,
        onUpdateAccepted = {
            updateManager.startFlexibleUpdate(context as ComponentActivity)
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
        message = "توجد نسخة جديدة من تطبيق وميض",
        onUpdateClick = {
            showUpdateDialog.value = true
            showUpdateNotification.value = false
        },
        onDismiss = {
            showUpdateNotification.value = false
        }
    )
}
