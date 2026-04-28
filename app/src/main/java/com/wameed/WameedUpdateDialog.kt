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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * حوار تحديث التطبيق مع شريط تقدم
 */
@Composable
fun WameedUpdateDialog(
    isVisible: Boolean,
    updateState: UpdateState,
    onUpdateAccepted: () -> Unit,
    onUpdateDeclined: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // أيقونة التحديث
                    Icon(
                        imageVector = if (updateState is UpdateState.Downloading || updateState is UpdateState.Installing) 
                            Icons.Default.Download 
                        else 
                            Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    // العنوان
                    Text(
                        text = when (updateState) {
                            is UpdateState.Available -> "تحديث جديد"
                            is UpdateState.Downloading -> "جاري التحميل..."
                            is UpdateState.Installing -> "جاري التثبيت..."
                            else -> "تحديث التطبيق"
                        },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    // الوصف
                    Text(
                        text = when (updateState) {
                            is UpdateState.Available -> "نسخة أحدث متاحة. التحديث يحمل تحسينات وميزات جديدة."
                            is UpdateState.Downloading -> "يتم تحميل التحديث..."
                            is UpdateState.Installing -> "يتم تثبيت التحديث..."
                            else -> "تحديث التطبيق"
                        },
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 20.sp
                    )
                    
                    // شريط التقدم
                    if (updateState is UpdateState.Downloading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { updateState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            
                            Text(
                                text = "${(updateState.progress * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (updateState is UpdateState.Installing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // الأزرار
                    when (updateState) {
                        is UpdateState.Available -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onUpdateDeclined,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("لاحقاً", fontSize = 14.sp)
                                }
                                
                                Button(
                                    onClick = onUpdateAccepted,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("تحديث الآن", fontSize = 14.sp)
                                }
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
                                    text = "تعذر التحديث، حاول لاحقاً",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("إغلاق", fontSize = 14.sp)
                                }
                            }
                        }
                        
                        else -> {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("إغلاق", fontSize = 14.sp)
                            }
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "🔄 تحديث جديد",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("تجاهل", fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = onUpdateClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تحديث", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
