package com.wameed

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Suppress("CustomSplashScreen") // we intentionally ship a branded animated splash
class SplashActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(
                        OVERRIDE_TRANSITION_CLOSE,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Using explicit MutableState (.value) instead of `by` delegation: Kotlin's
    // dataflow analyzer otherwise flags `startAnim = true` as "assigned but
    // never read" because it can't see the state reads performed by Compose
    // during recomposition.
    val startAnim = remember { mutableStateOf(false) }

    // Multi-layer dramatic flash effect
    val flashAlpha by animateFloatAsState(
        targetValue = if (startAnim.value) 0f else 1f,
        animationSpec = tween(durationMillis = 900, delayMillis = 150, easing = EaseOutQuart),
        label = "flash"
    )
    
    // Secondary flash for more dramatic effect
    val flashAlpha2 by animateFloatAsState(
        targetValue = if (startAnim.value) 0f else 0.7f,
        animationSpec = tween(durationMillis = 600, delayMillis = 300, easing = EaseOutQuart),
        label = "flash2"
    )

    // Text slide up with dramatic entrance
    val textAlpha by animateFloatAsState(
        targetValue = if (startAnim.value) 1f else 0f,
        animationSpec = tween(800, delayMillis = 500, easing = EaseOutCubic),
        label = "textAlpha"
    )
    
    val textScale by animateFloatAsState(
        targetValue = if (startAnim.value) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "textScale"
    )
    
    // Subtle glow effect
    val glowAlpha by animateFloatAsState(
        targetValue = if (startAnim.value) 0.3f else 0f,
        animationSpec = tween(1200, delayMillis = 700, easing = EaseInOutSine),
        label = "glowAlpha"
    )

    LaunchedEffect(Unit) {
        startAnim.value = true
        delay(2500) // Slightly longer for dramatic effect
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D47A1), // أزرق داكن في الأعلى
                        Color(0xFF1B5E20), // أخضر داكن في المنتصف
                        Color(0xFF2E7D32), // أخضر متوسط
                        Color(0xFF388E3C)  // أخضر فاتح في الأسفل
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect behind logo
        Box(
            modifier = Modifier
                .size(200.dp)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.about_detail),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .alpha(textAlpha)
                    .scale(textScale)
            )
        }

        // Multi-layer dramatic flash effects
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(flashAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color(0xFFE3F2FD).copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Secondary flash for more dramatic effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(flashAlpha2)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF81D4FA).copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
