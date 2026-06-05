content:
package com.wameed.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/* ============================================================================
 *  WAMEED Theme — Material You 3 بأخضر وميض الأصلي
 *  - Dynamic Color مُعطّل افتراضياً للحفاظ على هوية التطبيق
 *  - يمكن تفعيله بتمرير useDynamicColor = true
 * ============================================================================ */

private val LightColors = lightColorScheme(
    primary            = WameedGreen,
    onPrimary          = WameedSurface,
    primaryContainer   = WameedGreen95,
    onPrimaryContainer = WameedGreenDark,

    secondary            = WameedGreenLight,
    onSecondary          = WameedSurface,
    secondaryContainer   = WameedGreen95,
    onSecondaryContainer = WameedGreenDark,

    tertiary           = WameedGreenLight,
    onTertiary         = WameedSurface,
    tertiaryContainer  = WameedGreen95,
    onTertiaryContainer = WameedGreenDark,

    background      = WameedMint,
    onBackground    = WameedTextPrimary,

    surface         = WameedSurface,
    onSurface       = WameedTextPrimary,
    surfaceVariant  = WameedSurfaceDim,
    onSurfaceVariant = WameedTextSecondary,

    error           = WameedError,
    onError         = WameedSurface,
    errorContainer  = androidx.compose.ui.graphics.Color(0xFFFEE2E2),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFF991B1B),

    outline         = WameedDivider,
    outlineVariant  = WameedBorderSubtle,
)

private val DarkColors = darkColorScheme(
    primary            = WameedGreen80,
    onPrimary          = WameedGreenDark,
    primaryContainer   = WameedGreen,
    onPrimaryContainer = WameedGreen95,

    secondary            = WameedGreen80,
    onSecondary          = WameedGreenDark,
    secondaryContainer   = WameedGreenLight,
    onSecondaryContainer = WameedGreen95,

    tertiary           = WameedGreen80,
    onTertiary         = WameedGreenDark,

    background      = WameedDarkBg,
    onBackground    = WameedDarkText,

    surface         = WameedDarkSurface,
    onSurface       = WameedDarkText,
    surfaceVariant  = androidx.compose.ui.graphics.Color(0xFF334155),
    onSurfaceVariant = WameedDarkSubtext,

    error           = WameedError,
    onError         = WameedSurface,

    outline         = androidx.compose.ui.graphics.Color(0xFF475569),
    outlineVariant  = androidx.compose.ui.graphics.Color(0xFF334155),
)

@Composable
fun WameedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,    // ⚠️ معطّل افتراضياً لإبقاء أخضر وميض
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }

    // تلوين شريط الحالة ليتطابق مع خلفية التطبيق
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = WameedShapes,
        content     = content
    )
}