package com.wameed

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wameed.ui.components.PulsingDot
import com.wameed.ui.components.WameedCard
import com.wameed.ui.components.WameedPrimaryButton
import com.wameed.ui.components.WameedProgressBar
import com.wameed.ui.components.WameedSecondaryButton
import com.wameed.ui.components.WameedTextAction
import com.wameed.ui.theme.WameedError
import com.wameed.ui.theme.WameedGreen
import com.wameed.ui.theme.WameedGreen95
import com.wameed.ui.theme.WameedInfo
import com.wameed.ui.theme.WameedTextPrimary
import com.wameed.ui.theme.WameedTextSecondary
import com.wameed.ui.theme.WameedWarning

/**
 * حوار تحديث التطبيق مع شريط تقدم
 */
@Composable
fun WameedUpdateDialog(
    isVisible: Boolean,
    updateState: UpdateState,
    updateInfo: UpdateInfo?,
    onUpdateAccepted: () -> Unit,
    onUpdateDeclined: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        val remoteVersion = updateInfo?.let {
            it.remoteVersionName.ifBlank { it.remoteVersionCode.toString() }
        }
        Dialog(onDismissRequest = onDismiss) {
            WameedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentPadding = PaddingValues(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PulsingDot(
                        color = when (updateState) {
                            is UpdateState.Failed -> WameedError
                            is UpdateState.Downloading, is UpdateState.Installing -> WameedInfo
                            else -> WameedGreen
                        },
                        pulsing = updateState is UpdateState.Downloading || updateState is UpdateState.Installing,
                        size = 14.dp
                    )
                    
                    // العنوان
                    Text(
                        text = when (updateState) {
                            is UpdateState.Available -> stringResource(R.string.update_available)
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading)
                            is UpdateState.Installing -> stringResource(R.string.update_installing)
                            else -> stringResource(R.string.update_available)
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = WameedTextPrimary
                    )
                    
                    // الوصف
                    Text(
                        text = when (updateState) {
                            is UpdateState.Available -> remoteVersion?.let {
                                stringResource(R.string.update_available_detail, it)
                            } ?: stringResource(R.string.update_description)
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading_desc)
                            is UpdateState.Installing -> stringResource(R.string.update_installing_desc)
                            else -> stringResource(R.string.update_description)
                        },
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = WameedTextSecondary,
                        lineHeight = 20.sp
                    )

                    if (updateInfo != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.update_installed_version_detail,
                                    updateInfo.localVersionName,
                                    updateInfo.localVersionCode
                                ),
                                fontSize = 12.sp,
                                color = WameedTextSecondary
                            )
                            Text(
                                text = stringResource(
                                    R.string.update_remote_version_detail,
                                    remoteVersion ?: updateInfo.remoteVersionCode.toString(),
                                    updateInfo.remoteVersionCode
                                ),
                                fontSize = 12.sp,
                                color = WameedTextSecondary
                            )
                            if (updateInfo.releaseNotes.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.update_release_notes_detail, updateInfo.releaseNotes),
                                    fontSize = 12.sp,
                                    color = WameedTextSecondary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    
                    // شريط التقدم
                    if (updateState is UpdateState.Downloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WameedProgressBar(progress = updateState.progress)
                            
                            Text(
                                text = "${(updateState.progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = WameedGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (updateState is UpdateState.Installing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = WameedGreen,
                            trackColor = WameedGreen95
                        )
                    }
                    
                    // الأزرار
                    when (updateState) {
                        is UpdateState.Available -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                WameedSecondaryButton(
                                    text = stringResource(R.string.update_later),
                                    onClick = onUpdateDeclined,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                WameedPrimaryButton(
                                    text = stringResource(R.string.update_now),
                                    onClick = onUpdateAccepted,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        
                        is UpdateState.Downloading, is UpdateState.Installing -> {
                            // لا تظهر أزرار أثناء التحميل أو التثبيت
                        }
                        
                        is UpdateState.Failed -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.update_failed),
                                    fontSize = 12.sp,
                                    color = WameedError,
                                    textAlign = TextAlign.Center
                                )
                                
                                WameedPrimaryButton(
                                    text = stringResource(R.string.update_close),
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        else -> {
                            WameedPrimaryButton(
                                text = stringResource(R.string.update_close),
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * إشعار تحديث صغير يظهر في الأعلى
 */
@Composable
fun WameedUpdateNotification(
    isVisible: Boolean,
    message: String,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        WameedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PulsingDot(color = WameedGreen, size = 10.dp)
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "تحديث جديد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WameedTextPrimary
                    )
                    
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = WameedTextSecondary
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WameedTextAction(text = "تجاهل", onClick = onDismiss, color = WameedTextSecondary)
                    
                    Button(
                        onClick = onUpdateClick,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WameedGreen)
                    ) {
                        Text("تحديث", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
