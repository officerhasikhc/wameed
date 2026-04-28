package com.wameed

import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * واجهة إبلاغ المستخدم عن المشاكل
 */
class WameedBugReportActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BugReportScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen() {
    val context = LocalContext.current
    val crashReporter = WameedCrashReporter.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    var description by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "إبلاغ عن مشكلة",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        (context as? ComponentActivity)?.finish()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // معلومات التعليمات
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📝 كيف تساعدنا؟",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Text(
                        text = "صف المشكلة التي واجهتها بالتفصيل. كلما كانت المعلومات أكثر دقة، تمكنا من حل المشكلة بشكل أسرع.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )
                    
                    Text(
                        text = "• متى حدثت المشكلة؟\n• ماذا كنت تفعل؟\n• هل تظهر رسالة خطأ؟",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                }
            }
            
            // حقل وصف المشكلة
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("وصف المشكلة *") },
                placeholder = { Text("اكتب وصفاً تفصيلياً للمشكلة...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                enabled = !isSending,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            )
            
            // حقل البريد الإلكتروني (اختياري)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني (اختياري)") },
                placeholder = { Text("بريدك الإلكتروني للتواصل معك") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )
            
            // معلومات الجهاز
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "📱 معلومات الجهاز (سيتم إرسالها تلقائياً):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "• النسخة: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                              "• الجهاز: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                              "• التطبيق: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
            
            // زر الإرسال
            Button(
                onClick = {
                    if (description.trim().isEmpty()) {
                        Toast.makeText(context, "يرجى كتابة وصف للمشكلة", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    isSending = true
                    coroutineScope.launch {
                        try {
                            crashReporter.reportUserIssue(context, description, email)
                            Toast.makeText(
                                context, 
                                "تم إرسال التقرير بنجاح! شكراً لمساعدتك.", 
                                Toast.LENGTH_LONG
                            ).show()
                            
                            // إغلاق الشاشة بعد الإرسال
                            (context as? ComponentActivity)?.finish()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context, 
                                "حدث خطأ أثناء الإرسال. يرجى المحاولة مرة أخرى.", 
                                Toast.LENGTH_LONG
                            ).show()
                        } finally {
                            isSending = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSending && description.trim().isNotEmpty()
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("جاري الإرسال...")
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إرسال التقرير")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
