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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wameed.ui.components.WameedCard
import com.wameed.ui.components.WameedPrimaryButton
import com.wameed.ui.components.WameedTextAction
import com.wameed.ui.theme.WameedGreen
import com.wameed.ui.theme.WameedMint
import com.wameed.ui.theme.WameedTextPrimary
import com.wameed.ui.theme.WameedTextSecondary
import com.wameed.ui.theme.WameedTheme
import kotlinx.coroutines.launch

/**
 * واجهة إبلاغ المستخدم عن المشاكل
 */
class WameedBugReportActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_DESCRIPTION = "com.wameed.extra.INITIAL_DESCRIPTION"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WameedCrashReporter.initialize(this)
        WameedLogger.init(this)
        val initialDescription = intent.getStringExtra(EXTRA_INITIAL_DESCRIPTION).orEmpty()
        setContent {
            WameedTheme {
                BugReportScreen(initialDescription = initialDescription)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BugReportScreen(initialDescription: String = "") {
    val context = LocalContext.current
    val crashReporter = WameedCrashReporter.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    var description by remember { mutableStateOf(initialDescription) }
    var email by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    
    Scaffold(
        containerColor = WameedMint,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "إبلاغ عن مشكلة",
                        fontWeight = FontWeight.Bold,
                        color = WameedGreen
                    ) 
                },
                navigationIcon = {
                    WameedTextAction(text = "رجوع", color = WameedTextSecondary, onClick = {
                        (context as? ComponentActivity)?.finish()
                    })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WameedMint
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
            WameedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "كيف تساعدنا؟",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WameedTextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "صف المشكلة التي واجهتها بالتفصيل. كلما كانت المعلومات أكثر دقة، تمكنا من حل المشكلة بشكل أسرع.",
                    fontSize = 14.sp,
                    color = WameedTextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "• متى حدثت المشكلة؟\n• ماذا كنت تفعل؟\n• هل تظهر رسالة خطأ؟",
                    fontSize = 13.sp,
                    color = WameedTextSecondary,
                    lineHeight = 18.sp
                )
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
            WameedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "معلومات الجهاز (سيتم إرسالها تلقائياً):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WameedTextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "• النسخة: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                          "• الجهاز: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                          "• التطبيق: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 11.sp,
                    color = WameedTextSecondary,
                    lineHeight = 16.sp
                )
            }
            
            // زر الإرسال
            WameedPrimaryButton(
                text = if (isSending) "جاري الإرسال..." else "إرسال التقرير",
                onClick = {
                    if (description.trim().isEmpty()) {
                        Toast.makeText(context, "يرجى كتابة وصف للمشكلة", Toast.LENGTH_SHORT).show()
                        return@WameedPrimaryButton
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
                enabled = !isSending && description.trim().isNotEmpty(),
                loading = isSending
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
