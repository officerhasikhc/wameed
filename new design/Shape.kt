package com.wameed.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/* ============================================================================
 *  WAMEED Shapes — زوايا دائرية كبيرة (طابع Material You 3)
 * ============================================================================ */

val WameedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(20.dp),   // البطاقات
    large      = RoundedCornerShape(24.dp),   // الأزرار الكبيرة
    extraLarge = RoundedCornerShape(32.dp),   // العناصر البارزة (status card)
)