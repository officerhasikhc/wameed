content:
package com.wameed.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wameed.ui.theme.*

/* ============================================================================
 *  WAMEED REUSABLE COMPONENTS
 *  مكونات قابلة لإعادة الاستخدام بهوية موحّدة وبساطة عالية
 * ============================================================================ */


// ╔════════════════════════════════════════════════════════════════╗
// ║  🟢 PulsingDot - نقطة حالة نابضة                                ║
// ╚════════════════════════════════════════════════════════════════╝
@Composable
fun PulsingDot(
    color: Color,
    pulsing: Boolean = true,
    size: Int = 12
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = if (pulsing) 1.35f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue  = if (pulsing) 0.15f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size((size * 2).dp)) {
        if (pulsing) {
            Box(
                modifier = Modifier
                    .size(size.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}


// ╔════════════════════════════════════════════════════════════════╗
// ║  🃏 WameedCard - البطاقة الموحّدة (ظل ناعم + زوايا كبيرة)        ║
// ╚════════════════════════════════════════════════════════════════╝
@Composable
fun WameedCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "cardScale"
    )

    val border = if (selected) BorderStroke(1.5.dp, WameedGreen) else null

    Surface(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (selected) 6.dp else 3.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = WameedGreen.copy(alpha = 0.12f),
                ambientColor = Color.Black.copy(alpha = 0.04f)
            )
            .then(if (onClick != null) Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = WameedSurface,
        border = border
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}


// ╔════════════════════════════════════════════════════════════════╗
// ║  ⚡ WameedPrimaryButton - الزر الأساسي الأخضر الكبير             ║
// ╚════════════════════════════════════════════════════════════════╝
@Composable
fun WameedPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(120),
        label = "btnScale"
    )

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = WameedGreen.copy(alpha = 0.35f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WameedGreen,
            contentColor = Color.White,
            disabledContainerColor = WameedDivider,
            disabledContentColor = WameedTextMuted
        ),
        interactionSource = interaction
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}


// ╔════════════════════════════════════════════════════════════════╗
// ║  WameedSecondaryButton - زر ثانوي نصي (Outlined)                ║
// ╚════════════════════════════════════════════════════════════════╝
@Composable
fun WameedSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.5.dp, if (enabled) WameedGreen else WameedDivider),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = WameedGreen,
            disabledContentColor = WameedTextMuted
        )
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}


// ╔════════════════════════════════════════════════════════════════╗
// ║  📊 WameedProgressBar - شريط تقدم بحركة Shimmer                  ║
// ╚════════════════════════════════════════════════════════════════╝
@Composable
fun WameedProgressBar(
    progress: Float,           // 0f..1f
    modifier: Modifier = Modifier,
    height: Int = 10
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "progress"
    )

    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp / 2)),
        color = WameedGreen,
        trackColor = WameedGreen95,
        strokeCap = StrokeCap.Round
    )
}


// ╔════════════════════════════════════════════════════════════════╗
// ║  SectionLabel - تسمية قسم (نص رمادي صغير)                       ║
// ╚════════════════════════════════════════════════════════════════╝
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = WameedTextSecondary,
        letterSpacing = 0.5.sp
    )
}