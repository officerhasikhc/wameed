content:
package com.wameed.ui.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 *  WAMEED — هوية الألوان الموحّدة (Material You 3 + الأخضر الأصلي)
 *  الفلسفة: بساطة، راحة بصرية، إيقاع لوني واحد متناسق
 * ============================================================================ */

// ─── 🌳 Primary (الأخضر الأصلي - هوية وميض) ───────────────────────────────
val WameedGreen      = Color(0xFF2E7D32)   // اللون الأساسي
val WameedGreenLight = Color(0xFF43A047)   // درجة أفتح
val WameedGreenDark  = Color(0xFF1B5E20)   // درجة أغمق (للنصوص على الفاتح)
val WameedGreen80    = Color(0xFF81C784)   // Dark mode primary
val WameedGreen95    = Color(0xFFE8F5E9)   // Container / Highlight ناعم

// ─── 🌿 Surfaces & Backgrounds ─────────────────────────────────────────────
val WameedMint       = Color(0xFFF1F8F4)   // خلفية التطبيق الرئيسية (نعناعي خفيف)
val WameedSurface    = Color(0xFFFFFFFF)   // البطاقات
val WameedSurfaceDim = Color(0xFFF8FAFC)   // بطاقات ثانوية

// ─── 🌑 Text Colors ────────────────────────────────────────────────────────
val WameedTextPrimary   = Color(0xFF1E293B) // نص رئيسي
val WameedTextSecondary = Color(0xFF64748B) // نص ثانوي (تواريخ، IP، subtitle)
val WameedTextMuted     = Color(0xFF94A3B8) // نص باهت جداً (hints)

// ─── 🚦 Status Colors (هادئة، غير صارخة) ───────────────────────────────────
val WameedSuccess = Color(0xFF22C55E)   // اتصال ناجح
val WameedWarning = Color(0xFFF59E0B)   // تحذير / pending
val WameedError   = Color(0xFFEF4444)   // خطأ
val WameedInfo    = Color(0xFF3B82F6)   // معلومة / searching

// ─── 🌒 Dark Mode Equivalents ──────────────────────────────────────────────
val WameedDarkBg       = Color(0xFF0F172A)
val WameedDarkSurface  = Color(0xFF1E293B)
val WameedDarkText     = Color(0xFFE2E8F0)
val WameedDarkSubtext  = Color(0xFF94A3B8)

// ─── 📏 Dividers & Borders ─────────────────────────────────────────────────
val WameedDivider      = Color(0xFFE2E8F0)
val WameedBorderSubtle = Color(0xFFF1F5F9)

/* ─── Legacy aliases (للحفاظ على التوافق مع الكود القديم) ─────────────────── */
val WameedGreenGrey80 = WameedGreen80
val WameedAccent80    = WameedGreen95
val WameedGreen40     = WameedGreenLight
val WameedGreenGrey40 = WameedGreen
val WameedAccent40    = WameedGreenDark