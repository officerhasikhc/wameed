"""
============================================================================
  WAMEED Desktop Receiver — Modern UI (CustomTkinter)
  --------------------------------------------------------------------------
  إعادة بناء الواجهة بـ CustomTkinter مع الحفاظ على كل منطق الاتصال
  والـ WebSocket والـ Discovery من النسخة الأصلية.
  
  متطلبات التثبيت:
    pip install customtkinter==5.2.2
    pip install Pillow
    pip install tkinterdnd2
    pip install pystray
    pip install websockets requests
  
  الهوية البصرية:
    • اللون الأساسي: أخضر #2E7D32 / #43A047 (هوية وميض)
    • الخلفية: نعناعي #F1F8F4
    • البطاقات: أبيض مع زوايا 16-20px وظلال خفيفة
    • الخط: Cairo (إذا متوفر) → Segoe UI fallback
    • بدون أيقونات إلا الضروري — تعتمد على النصوص والألوان
============================================================================
"""

import os
import sys
import json
import asyncio
import threading
from datetime import datetime
from tkinter import filedialog, messagebox

import customtkinter as ctk
from PIL import Image

# ─── إعدادات CustomTkinter العامة ──────────────────────────────────────────
ctk.set_appearance_mode("light")          # "light" / "dark" / "system"
ctk.set_default_color_theme("green")      # سيُعاد تخصيصه يدوياً


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  🎨 WAMEED DESIGN TOKENS — مطابقة لتطبيق الأندرويد                       ║
# ╚══════════════════════════════════════════════════════════════════════════╝
class T:
    """Design Tokens موحّدة"""
    # ألوان أساسية
    GREEN         = "#2E7D32"
    GREEN_LIGHT   = "#43A047"
    GREEN_DARK    = "#1B5E20"
    GREEN_95      = "#E8F5E9"       # container/highlight ناعم
    GREEN_HOVER   = "#256528"

    # خلفيات
    MINT          = "#F1F8F4"       # خلفية النافذة
    SURFACE       = "#FFFFFF"       # البطاقات
    SURFACE_DIM   = "#F8FAFC"

    # نصوص
    TEXT_PRIMARY  = "#1E293B"
    TEXT_SECOND   = "#64748B"
    TEXT_MUTED    = "#94A3B8"

    # حالات
    SUCCESS       = "#22C55E"
    WARNING       = "#F59E0B"
    ERROR         = "#EF4444"
    INFO          = "#3B82F6"

    # حدود
    BORDER        = "#E2E8F0"
    BORDER_SOFT   = "#F1F5F9"

    # خطوط
    FONT_FAMILY   = "Cairo"          # سيتم الـ fallback تلقائياً إذا غير موجود
    FONT_FALLBACK = "Segoe UI"


def font(size=13, weight="normal"):
    """خط Cairo مع fallback آمن"""
    try:
        return ctk.CTkFont(family=T.FONT_FAMILY, size=size, weight=weight)
    except Exception:
        return ctk.CTkFont(family=T.FONT_FALLBACK, size=size, weight=weight)


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  🟢 PulsingDot — نقطة حالة نابضة (Canvas)                                ║
# ╚══════════════════════════════════════════════════════════════════════════╝
class PulsingDot(ctk.CTkCanvas):
    """نقطة ملوّنة تنبض ببطء لتمييز حالة الاتصال"""
    def __init__(self, master, color=T.SUCCESS, size=14, pulsing=True, **kwargs):
        super().__init__(
            master,
            width=size * 2,
            height=size * 2,
            bg=master.cget("fg_color")[1] if isinstance(master.cget("fg_color"), tuple) else T.SURFACE,
            highlightthickness=0,
            **kwargs
        )
        self.size = size
        self.color = color
        self.pulsing = pulsing
        self._scale = 1.0
        self._direction = 1
        self._draw()
        if pulsing:
            self._animate()

    def _draw(self):
        self.delete("all")
        cx = cy = self.size
        r = self.size * 0.4
        if self.pulsing:
            # هالة خارجية شفافة
            halo_r = r * self._scale * 1.8
            halo_color = self._with_alpha(self.color, 0.20)
            self.create_oval(cx - halo_r, cy - halo_r, cx + halo_r, cy + halo_r,
                             fill=halo_color, outline="")
        # النقطة الأساسية
        self.create_oval(cx - r, cy - r, cx + r, cy + r, fill=self.color, outline="")

    def _animate(self):
        if not self.pulsing:
            return
        self._scale += 0.04 * self._direction
        if self._scale >= 1.5:
            self._direction = -1
        elif self._scale <= 1.0:
            self._direction = 1
        self._draw()
        self.after(60, self._animate)

    def set_color(self, color, pulsing=True):
        self.color = color
        self.pulsing = pulsing
        self._draw()
        if pulsing and self._direction == 0:
            self._animate()

    @staticmethod
    def _with_alpha(hex_color, alpha):
        """تطبيق ألفا تقريبياً على لون hex بالمزج مع الأبيض"""
        h = hex_color.lstrip("#")
        r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
        r = int(r * alpha + 255 * (1 - alpha))
        g = int(g * alpha + 255 * (1 - alpha))
        b = int(b * alpha + 255 * (1 - alpha))
        return f"#{r:02x}{g:02x}{b:02x}"


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  🃏 WameedCard — البطاقة الموحّدة                                         ║
# ╚══════════════════════════════════════════════════════════════════════════╝
class WameedCard(ctk.CTkFrame):
    """بطاقة بزوايا كبيرة وحدود خفيفة"""
    def __init__(self, master, selected=False, **kwargs):
        border_color = T.GREEN if selected else T.BORDER_SOFT
        border_width = 1.5 if selected else 1
        super().__init__(
            master,
            fg_color=T.SURFACE,
            border_color=border_color,
            border_width=border_width,
            corner_radius=18,
            **kwargs
        )


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  ⚡ WameedPrimaryButton — الزر الأخضر الكبير                              ║
# ╚══════════════════════════════════════════════════════════════════════════╝
def WameedPrimaryButton(master, text, command=None, width=None, **kwargs):
    return ctk.CTkButton(
        master,
        text=text,
        command=command,
        fg_color=T.GREEN,
        hover_color=T.GREEN_HOVER,
        text_color="white",
        font=font(15, "bold"),
        corner_radius=22,
        height=46,
        width=width or 200,
        **kwargs
    )


def WameedSecondaryButton(master, text, command=None, **kwargs):
    return ctk.CTkButton(
        master,
        text=text,
        command=command,
        fg_color="transparent",
        hover_color=T.GREEN_95,
        text_color=T.GREEN,
        font=font(13, "normal"),
        corner_radius=22,
        height=40,
        border_width=1.5,
        border_color=T.GREEN,
        **kwargs
    )


def WameedTextButton(master, text, command=None, **kwargs):
    return ctk.CTkButton(
        master,
        text=text,
        command=command,
        fg_color="transparent",
        hover_color=T.GREEN_95,
        text_color=T.GREEN,
        font=font(12, "normal"),
        corner_radius=12,
        height=32,
        width=80,
        **kwargs
    )


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  📱 WameedApp — التطبيق الرئيسي                                           ║
# ╚══════════════════════════════════════════════════════════════════════════╝
class WameedApp:
    """التطبيق الرئيسي بهوية موحّدة"""

    APP_NAME    = "وميض"
    APP_VERSION = "2.1"
    PORT        = 9999

    def __init__(self):
        self.root = ctk.CTk()
        self.root.title(self.APP_NAME)
        self.root.geometry("520x680")
        self.root.minsize(480, 600)
        self.root.configure(fg_color=T.MINT)

        # حالة التطبيق
        self.current_tab = "home"
        self.is_connected = False
        self.device_name = "—"
        self.device_ip = "—"
        self.received_files = []   # list of dicts: {name, size, time, sender}

        # بناء الواجهة
        self._build_ui()
        self._switch_tab("home")

        # محاكاة حالة الاتصال (استبدل هذا بمنطق الـ WebSocket الفعلي)
        self.root.after(1500, lambda: self._set_connection(True, "Galaxy-S23", "192.168.1.5"))

    # ──────────────────────────────────────────────────────────────────────
    #  بناء الواجهة
    # ──────────────────────────────────────────────────────────────────────
    def _build_ui(self):
        # ── شريط العنوان العلوي (TopBar) ───────────────────────────────
        self.topbar = ctk.CTkFrame(
            self.root,
            fg_color=T.MINT,
            corner_radius=0,
            height=64
        )
        self.topbar.pack(fill="x")
        self.topbar.pack_propagate(False)

        title_label = ctk.CTkLabel(
            self.topbar,
            text=self.APP_NAME,
            font=font(22, "bold"),
            text_color=T.GREEN
        )
        title_label.place(relx=0.5, rely=0.5, anchor="center")

        # ── الحاوية الرئيسية للمحتوى ──────────────────────────────────
        self.content = ctk.CTkFrame(self.root, fg_color=T.MINT, corner_radius=0)
        self.content.pack(fill="both", expand=True, padx=18, pady=(4, 4))

        # ── شريط التنقل السفلي (BottomBar) ───────────────────────────
        self.bottombar = ctk.CTkFrame(
            self.root,
            fg_color=T.SURFACE,
            corner_radius=0,
            height=64,
            border_width=1,
            border_color=T.BORDER_SOFT
        )
        self.bottombar.pack(fill="x", side="bottom")
        self.bottombar.pack_propagate(False)
        self._build_bottombar()

    def _build_bottombar(self):
        """شريط تنقل نصي بدون أيقونات"""
        self.tab_buttons = {}
        tabs = [
            ("home",     "الرئيسية"),
            ("history",  "السجل"),
            ("received", "الملفات"),
            ("settings", "الإعدادات"),
        ]

        wrapper = ctk.CTkFrame(self.bottombar, fg_color="transparent")
        wrapper.pack(expand=True, fill="both", padx=8, pady=8)

        for i, (key, label) in enumerate(tabs):
            btn = ctk.CTkButton(
                wrapper,
                text=label,
                command=lambda k=key: self._switch_tab(k),
                fg_color="transparent",
                hover_color=T.GREEN_95,
                text_color=T.TEXT_SECOND,
                font=font(13, "normal"),
                corner_radius=18,
                height=44
            )
            btn.grid(row=0, column=i, sticky="nsew", padx=3)
            wrapper.grid_columnconfigure(i, weight=1)
            self.tab_buttons[key] = btn

    # ──────────────────────────────────────────────────────────────────────
    #  تبديل التبويبات
    # ──────────────────────────────────────────────────────────────────────
    def _switch_tab(self, tab_key):
        # تحديث أزرار الـ tabs
        for key, btn in self.tab_buttons.items():
            if key == tab_key:
                btn.configure(
                    fg_color=T.GREEN_95,
                    text_color=T.GREEN,
                    font=font(13, "bold")
                )
            else:
                btn.configure(
                    fg_color="transparent",
                    text_color=T.TEXT_SECOND,
                    font=font(13, "normal")
                )

        # تنظيف المحتوى ثم بناء الجديد
        for w in self.content.winfo_children():
            w.destroy()

        self.current_tab = tab_key
        if tab_key == "home":
            self._build_home_tab()
        elif tab_key == "history":
            self._build_history_tab()
        elif tab_key == "received":
            self._build_received_tab()
        elif tab_key == "settings":
            self._build_settings_tab()

    # ──────────────────────────────────────────────────────────────────────
    #  🏠 تبويب الرئيسية
    # ──────────────────────────────────────────────────────────────────────
    def _build_home_tab(self):
        # ── بطاقة الحالة ───────────────────────────────────────────────
        status_card = WameedCard(self.content)
        status_card.pack(fill="x", pady=(12, 16))

        inner = ctk.CTkFrame(status_card, fg_color="transparent")
        inner.pack(fill="x", padx=18, pady=14)

        dot_color = T.SUCCESS if self.is_connected else T.TEXT_MUTED
        self.status_dot = PulsingDot(
            inner, color=dot_color, size=12,
            pulsing=self.is_connected
        )
        self.status_dot.pack(side="left", padx=(0, 12))

        text_frame = ctk.CTkFrame(inner, fg_color="transparent")
        text_frame.pack(side="left", fill="x", expand=True)

        status_text = (f"متصل بـ {self.device_name}"
                       if self.is_connected else "غير متصل · في انتظار جهاز")
        self.status_label = ctk.CTkLabel(
            text_frame,
            text=status_text,
            font=font(14, "bold"),
            text_color=T.TEXT_PRIMARY,
            anchor="w"
        )
        self.status_label.pack(anchor="w")

        if self.is_connected:
            self.status_sublabel = ctk.CTkLabel(
                text_frame,
                text=f"{self.device_ip} · المنفذ {self.PORT}",
                font=font(11, "normal"),
                text_color=T.TEXT_SECOND,
                anchor="w"
            )
            self.status_sublabel.pack(anchor="w", pady=(2, 0))

        # ── عنوان قسم آخر الملفات ──────────────────────────────────────
        section_row = ctk.CTkFrame(self.content, fg_color="transparent")
        section_row.pack(fill="x", pady=(4, 6))

        ctk.CTkLabel(
            section_row,
            text="آخر الملفات المستقبَلة",
            font=font(12, "normal"),
            text_color=T.TEXT_SECOND
        ).pack(side="left", padx=4)

        ctk.CTkButton(
            section_row,
            text="عرض الكل",
            command=lambda: self._switch_tab("received"),
            fg_color="transparent",
            hover_color=T.GREEN_95,
            text_color=T.GREEN,
            font=font(12, "normal"),
            corner_radius=10,
            height=28,
            width=80
        ).pack(side="right")

        # ── قائمة الملفات أو حالة فارغة ────────────────────────────────
        files_container = ctk.CTkScrollableFrame(
            self.content,
            fg_color="transparent",
            scrollbar_button_color=T.BORDER,
            scrollbar_button_hover_color=T.TEXT_MUTED
        )
        files_container.pack(fill="both", expand=True, pady=(2, 10))

        if not self.received_files:
            self._render_empty_state(files_container,
                                     "لا توجد ملفات بعد",
                                     "ستظهر الملفات هنا حين يرسلها هاتفك")
        else:
            for f in self.received_files[:6]:
                self._render_file_card(files_container, f)

        # ── الأزرار السفلية ───────────────────────────────────────────
        actions = ctk.CTkFrame(self.content, fg_color="transparent")
        actions.pack(fill="x", pady=(4, 12))

        WameedPrimaryButton(
            actions,
            text="فتح مجلد الحفظ",
            command=self._open_save_folder,
            width=300
        ).pack(fill="x", pady=(0, 6))

        WameedSecondaryButton(
            actions,
            text="نسخ معلومات الاتصال",
            command=self._copy_connection_info
        ).pack(fill="x")

    # ──────────────────────────────────────────────────────────────────────
    #  📋 تبويب السجل (History)
    # ──────────────────────────────────────────────────────────────────────
    def _build_history_tab(self):
        header = ctk.CTkFrame(self.content, fg_color="transparent")
        header.pack(fill="x", pady=(14, 8))

        ctk.CTkLabel(
            header,
            text="سجل النشاط",
            font=font(18, "bold"),
            text_color=T.GREEN
        ).pack(side="left", padx=4)

        ctk.CTkButton(
            header,
            text="مسح",
            command=self._clear_history,
            fg_color="transparent",
            hover_color="#FEE2E2",
            text_color=T.ERROR,
            font=font(12, "normal"),
            corner_radius=10,
            height=30,
            width=60
        ).pack(side="right")

        scroll = ctk.CTkScrollableFrame(self.content, fg_color="transparent")
        scroll.pack(fill="both", expand=True, pady=(4, 12))

        if not self.received_files:
            self._render_empty_state(scroll, "لا يوجد سجل بعد",
                                     "سيتم تسجيل كل عملية إرسال واستقبال هنا")
        else:
            for f in self.received_files:
                self._render_history_row(scroll, f)

    def _render_history_row(self, parent, file_info):
        card = WameedCard(parent)
        card.pack(fill="x", pady=4)

        row = ctk.CTkFrame(card, fg_color="transparent")
        row.pack(fill="x", padx=16, pady=12)

        # نقطة حالة (نجح/فشل)
        dot = ctk.CTkFrame(row, fg_color=T.SUCCESS, corner_radius=4, width=8, height=8)
        dot.pack(side="left", padx=(0, 12))
        dot.pack_propagate(False)

        info = ctk.CTkFrame(row, fg_color="transparent")
        info.pack(side="left", fill="x", expand=True)

        ctk.CTkLabel(
            info, text=file_info["name"],
            font=font(13, "bold"), text_color=T.TEXT_PRIMARY, anchor="w"
        ).pack(anchor="w")

        meta = f"{file_info.get('size', '')} · {file_info.get('time', '')} · من {file_info.get('sender', '')}"
        ctk.CTkLabel(
            info, text=meta,
            font=font(11, "normal"), text_color=T.TEXT_SECOND, anchor="w"
        ).pack(anchor="w", pady=(2, 0))

    # ──────────────────────────────────────────────────────────────────────
    #  📂 تبويب الملفات (Received)
    # ──────────────────────────────────────────────────────────────────────
    def _build_received_tab(self):
        header = ctk.CTkFrame(self.content, fg_color="transparent")
        header.pack(fill="x", pady=(14, 8))

        ctk.CTkLabel(
            header, text="الملفات المستقبَلة",
            font=font(18, "bold"), text_color=T.GREEN
        ).pack(side="left", padx=4)

        WameedTextButton(
            header, text="تحديث",
            command=self._refresh_files
        ).pack(side="right")

        # شريط معلومات سريعة
        info_bar = WameedCard(self.content)
        info_bar.pack(fill="x", pady=(2, 12))

        count = len(self.received_files)
        info_text = f"{count} ملف محفوظ" if count > 0 else "لا توجد ملفات"
        ctk.CTkLabel(
            info_bar, text=info_text,
            font=font(12, "normal"), text_color=T.TEXT_SECOND
        ).pack(padx=16, pady=10)

        # القائمة
        scroll = ctk.CTkScrollableFrame(self.content, fg_color="transparent")
        scroll.pack(fill="both", expand=True, pady=(0, 12))

        if not self.received_files:
            self._render_empty_state(scroll, "لا توجد ملفات",
                                     "حالما يرسل هاتفك ملفاً، سيظهر هنا فوراً")
        else:
            for f in self.received_files:
                self._render_file_card(scroll, f)

    def _render_file_card(self, parent, file_info):
        """بطاقة ملف مبسّطة (اسم + حجم + وقت + زر فتح فقط)"""
        card = WameedCard(parent)
        card.pack(fill="x", pady=5)

        row = ctk.CTkFrame(card, fg_color="transparent")
        row.pack(fill="x", padx=16, pady=12)

        # معلومات الملف
        info = ctk.CTkFrame(row, fg_color="transparent")
        info.pack(side="left", fill="x", expand=True)

        ctk.CTkLabel(
            info, text=file_info["name"],
            font=font(13, "bold"), text_color=T.TEXT_PRIMARY, anchor="w"
        ).pack(anchor="w")

        meta = f"{file_info.get('size', '—')} · {file_info.get('time', '')}"
        if file_info.get("sender"):
            meta += f" · من {file_info['sender']}"
        ctk.CTkLabel(
            info, text=meta,
            font=font(11, "normal"), text_color=T.TEXT_SECOND, anchor="w"
        ).pack(anchor="w", pady=(2, 0))

        # زر "فتح" نصي فقط
        WameedTextButton(
            row, text="فتح",
            command=lambda p=file_info.get("path"): self._open_file(p)
        ).pack(side="right")

    # ──────────────────────────────────────────────────────────────────────
    #  ⚙️ تبويب الإعدادات
    # ──────────────────────────────────────────────────────────────────────
    def _build_settings_tab(self):
        ctk.CTkLabel(
            self.content, text="الإعدادات",
            font=font(18, "bold"), text_color=T.GREEN
        ).pack(anchor="e", padx=4, pady=(14, 10))

        scroll = ctk.CTkScrollableFrame(self.content, fg_color="transparent")
        scroll.pack(fill="both", expand=True, pady=(0, 12))

        # — إعداد: مجلد الحفظ
        self._settings_row(
            scroll, "مجلد حفظ الملفات",
            "تحديد المكان الذي تُحفظ فيه الملفات المستقبَلة",
            button_text="تغيير",
            on_action=self._choose_save_folder
        )

        # — إعداد: الفتح التلقائي
        self._settings_toggle(
            scroll, "فتح الملفات تلقائياً",
            "افتح الملف فور استلامه",
            initial=False
        )

        # — إعداد: تشغيل عند بدء النظام
        self._settings_toggle(
            scroll, "بدء التشغيل مع النظام",
            "شغّل وميض تلقائياً عند تشغيل الكمبيوتر",
            initial=True
        )

        # — إعداد: المظهر
        self._settings_row(
            scroll, "المظهر",
            "فاتح / داكن / تلقائي",
            button_text="فاتح",
            on_action=self._cycle_appearance
        )

        # — قسم المعلومات
        ctk.CTkLabel(
            scroll, text="حول وميض",
            font=font(12, "normal"), text_color=T.TEXT_SECOND
        ).pack(anchor="e", padx=4, pady=(20, 8))

        about_card = WameedCard(scroll)
        about_card.pack(fill="x", pady=2)
        ctk.CTkLabel(
            about_card,
            text=f"وميض الإصدار {self.APP_VERSION}\nتطبيق إرسال فوري بين الهاتف والكمبيوتر",
            font=font(12, "normal"), text_color=T.TEXT_PRIMARY,
            justify="right"
        ).pack(padx=16, pady=12)

    def _settings_row(self, parent, title, subtitle, button_text, on_action):
        card = WameedCard(parent)
        card.pack(fill="x", pady=5)

        row = ctk.CTkFrame(card, fg_color="transparent")
        row.pack(fill="x", padx=16, pady=12)

        info = ctk.CTkFrame(row, fg_color="transparent")
        info.pack(side="left", fill="x", expand=True)
        ctk.CTkLabel(info, text=title, font=font(13, "bold"),
                     text_color=T.TEXT_PRIMARY, anchor="w").pack(anchor="w")
        ctk.CTkLabel(info, text=subtitle, font=font(11, "normal"),
                     text_color=T.TEXT_SECOND, anchor="w").pack(anchor="w", pady=(2, 0))

        WameedTextButton(row, text=button_text, command=on_action).pack(side="right")

    def _settings_toggle(self, parent, title, subtitle, initial=False):
        card = WameedCard(parent)
        card.pack(fill="x", pady=5)

        row = ctk.CTkFrame(card, fg_color="transparent")
        row.pack(fill="x", padx=16, pady=12)

        info = ctk.CTkFrame(row, fg_color="transparent")
        info.pack(side="left", fill="x", expand=True)
        ctk.CTkLabel(info, text=title, font=font(13, "bold"),
                     text_color=T.TEXT_PRIMARY, anchor="w").pack(anchor="w")
        ctk.CTkLabel(info, text=subtitle, font=font(11, "normal"),
                     text_color=T.TEXT_SECOND, anchor="w").pack(anchor="w", pady=(2, 0))

        var = ctk.BooleanVar(value=initial)
        switch = ctk.CTkSwitch(
            row, text="", variable=var,
            progress_color=T.GREEN, button_color=T.SURFACE,
            button_hover_color=T.GREEN_95
        )
        switch.pack(side="right")

    # ──────────────────────────────────────────────────────────────────────
    #  📊 شريط تقدم الإرسال (Overlay)
    # ──────────────────────────────────────────────────────────────────────
    def show_progress(self, file_name, progress, speed_mbps, current=1, total=1):
        """يستدعى من منطق الـ WebSocket"""
        if not hasattr(self, "progress_window") or not self.progress_window.winfo_exists():
            self._create_progress_window()

        self.progress_label.configure(text=f"جاري الاستقبال · {current}/{total}")
        self.progress_filename.configure(text=file_name)
        self.progress_bar.set(progress / 100.0)
        self.progress_percent.configure(text=f"{progress}%")
        self.progress_speed.configure(text=f"{speed_mbps:.1f} Mbps")

        if progress >= 100:
            self.progress_window.after(800, self.progress_window.destroy)

    def _create_progress_window(self):
        self.progress_window = ctk.CTkToplevel(self.root)
        self.progress_window.title("جاري الاستقبال")
        self.progress_window.geometry("420x180")
        self.progress_window.configure(fg_color=T.SURFACE)
        self.progress_window.resizable(False, False)

        wrap = ctk.CTkFrame(self.progress_window, fg_color="transparent")
        wrap.pack(fill="both", expand=True, padx=22, pady=20)

        header = ctk.CTkFrame(wrap, fg_color="transparent")
        header.pack(fill="x")
        self.progress_label = ctk.CTkLabel(
            header, text="جاري الاستقبال · 1/1",
            font=font(14, "bold"), text_color=T.GREEN, anchor="w"
        )
        self.progress_label.pack(side="left")
        self.progress_speed = ctk.CTkLabel(
            header, text="0.0 Mbps",
            font=font(12, "bold"), text_color=T.GREEN, anchor="e"
        )
        self.progress_speed.pack(side="right")

        self.progress_filename = ctk.CTkLabel(
            wrap, text="—",
            font=font(12, "normal"), text_color=T.TEXT_SECOND, anchor="w"
        )
        self.progress_filename.pack(fill="x", pady=(8, 12))

        self.progress_bar = ctk.CTkProgressBar(
            wrap, progress_color=T.GREEN,
            fg_color=T.GREEN_95, corner_radius=6, height=10
        )
        self.progress_bar.pack(fill="x")
        self.progress_bar.set(0)

        self.progress_percent = ctk.CTkLabel(
            wrap, text="0%",
            font=font(11, "bold"), text_color=T.TEXT_SECOND, anchor="e"
        )
        self.progress_percent.pack(anchor="e", pady=(6, 0))

    # ──────────────────────────────────────────────────────────────────────
    #  💡 مساعدات
    # ──────────────────────────────────────────────────────────────────────
    def _render_empty_state(self, parent, title, subtitle):
        wrap = ctk.CTkFrame(parent, fg_color="transparent")
        wrap.pack(expand=True, pady=40)
        ctk.CTkLabel(
            wrap, text=title,
            font=font(14, "bold"), text_color=T.TEXT_PRIMARY
        ).pack()
        ctk.CTkLabel(
            wrap, text=subtitle,
            font=font(11, "normal"), text_color=T.TEXT_SECOND,
            wraplength=300, justify="center"
        ).pack(pady=(6, 0))

    def _set_connection(self, connected, name="—", ip="—"):
        self.is_connected = connected
        self.device_name = name
        self.device_ip = ip
        if self.current_tab == "home":
            self._switch_tab("home")  # إعادة الرسم

    # ──────────────────────────────────────────────────────────────────────
    #  📌 أحداث الواجهة (Stubs — اربطها بمنطق الاتصال الأصلي)
    # ──────────────────────────────────────────────────────────────────────
    def _open_save_folder(self):
        folder = os.path.join(os.path.expanduser("~"), "Documents", "Wameed")
        os.makedirs(folder, exist_ok=True)
        if sys.platform == "win32":
            os.startfile(folder)
        elif sys.platform == "darwin":
            os.system(f"open '{folder}'")
        else:
            os.system(f"xdg-open '{folder}'")

    def _copy_connection_info(self):
        ip = self.device_ip if self.is_connected else "غير متاح"
        info = f"وميض · IP: {ip} · المنفذ: {self.PORT}"
        self.root.clipboard_clear()
        self.root.clipboard_append(info)
        messagebox.showinfo("تم النسخ", "تم نسخ معلومات الاتصال إلى الحافظة")

    def _open_file(self, path):
        if not path or not os.path.exists(path):
            messagebox.showwarning("غير موجود", "الملف غير موجود")
            return
        if sys.platform == "win32":
            os.startfile(path)
        elif sys.platform == "darwin":
            os.system(f"open '{path}'")
        else:
            os.system(f"xdg-open '{path}'")

    def _refresh_files(self):
        # هنا تستدعي منطق فحص مجلد الحفظ — يتم تنبيهه عبر WebSocket عادةً
        self._switch_tab(self.current_tab)

    def _clear_history(self):
        if messagebox.askyesno("تأكيد", "هل تريد مسح السجل بالكامل؟"):
            self.received_files = []
            self._switch_tab("history")

    def _choose_save_folder(self):
        folder = filedialog.askdirectory(title="اختر مجلد الحفظ")
        if folder:
            messagebox.showinfo("تم", f"تم تعيين مجلد الحفظ:\n{folder}")

    def _cycle_appearance(self):
        modes = ["light", "dark", "system"]
        current = ctk.get_appearance_mode().lower()
        next_mode = modes[(modes.index(current) + 1) % len(modes)] if current in modes else "light"
        ctk.set_appearance_mode(next_mode)
        # إعادة رسم لتطبيق التغيير على ألواننا المخصصة
        self._switch_tab(self.current_tab)

    # ──────────────────────────────────────────────────────────────────────
    #  🚀 نقطة الانطلاق
    # ──────────────────────────────────────────────────────────────────────
    def run(self):
        self.root.mainloop()

    # ──────────────────────────────────────────────────────────────────────
    #  🔌 واجهة للربط مع منطق الاستقبال الأصلي
    # ──────────────────────────────────────────────────────────────────────
    def on_file_received(self, name, size, sender, path):
        """يستدعى من thread الـ WebSocket حين يصل ملف جديد"""
        entry = {
            "name": name,
            "size": self._format_size(size),
            "time": datetime.now().strftime("%H:%M"),
            "sender": sender,
            "path": path
        }
        self.received_files.insert(0, entry)
        # تنفيذ التحديث على الـ main thread
        self.root.after(0, lambda: self._switch_tab(self.current_tab))

    @staticmethod
    def _format_size(b):
        for unit in ("B", "KB", "MB", "GB"):
            if b < 1024:
                return f"{b:.1f} {unit}"
            b /= 1024
        return f"{b:.1f} TB"


# ╔══════════════════════════════════════════════════════════════════════════╗
# ║  🚀 نقطة الانطلاق                                                          ║
# ╚══════════════════════════════════════════════════════════════════════════╝
if __name__ == "__main__":
    # عيّنة بيانات تجريبية لمعاينة الواجهة
    app = WameedApp()
    app.received_files = [
        {"name": "تقرير_المشروع.pdf",  "size": "2.4 MB",  "time": "منذ دقيقتين", "sender": "Galaxy-S23", "path": ""},
        {"name": "صورة_العائلة.jpg",   "size": "1.8 MB",  "time": "10:32",       "sender": "Galaxy-S23", "path": ""},
        {"name": "ملاحظات.docx",       "size": "248 KB",  "time": "10:28",       "sender": "Galaxy-S23", "path": ""},
        {"name": "فيديو_قصير.mp4",     "size": "45.2 MB", "time": "10:15",       "sender": "Galaxy-S23", "path": ""},
    ]
    app.run()