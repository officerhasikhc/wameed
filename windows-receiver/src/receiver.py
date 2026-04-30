import os
import sys
import logging
import logging.handlers
import socket
import json
import webbrowser
import asyncio
import threading
import time
from datetime import datetime
import tkinter as tk
from tkinter import messagebox, filedialog, ttk
from PIL import Image, ImageTk

# محاولة استيراد مكتبة السحب والإفلات
try:
    from tkinterdnd2 import DND_FILES, TkinterDnD
    TKDND_AVAILABLE = True
except ImportError:
    TKDND_AVAILABLE = False
    print("Warning: tkinterdnd2 not installed. Drag & drop will not be available.")
import pystray
from pystray import MenuItem as item
import requests
import websockets
from websockets.server import serve
import subprocess

# ======================== Configuration ========================
VERSION = "1.4.0"
APP_NAME = "وميض (Wameed)"
PORT_WS = 7788
PORT_UDP = 7789

# ======================== Font Configuration ========================
# خط عربي احترافي مع fallback
def _detect_arabic_font():
    """اكتشاف أفضل خط عربي متاح على النظام"""
    import tkinter as _tk
    _root = _tk.Tk()
    _root.withdraw()
    available = _root.tk.call("font", "families")
    _root.destroy()
    for candidate in ("Sakkal Majalla", "Arabic Typesetting", "Traditional Arabic", "Simplified Arabic"):
        if candidate in available:
            return candidate
    return "Segoe UI"

try:
    FONT_AR = _detect_arabic_font()
except Exception:
    FONT_AR = "Segoe UI"

def fs(size):
    """حجم خط ذكي: +1 للعربية، +2 للإنجليزية"""
    return size + 1 if LANG == "ar" else size + 2

# High DPI support for Windows
try:
    from ctypes import windll
    windll.shcore.SetProcessDpiAwareness(1)
except Exception:
    try:
        import ctypes
        ctypes.windll.user32.SetProcessDPIAware()
    except Exception:
        pass

# ======================== Translations ========================
LANG = "ar"
translations = {
    "ar": {
        "app_header": "وميض",
        "status_ready": "⚪ غير متصل",
        "status_connected_to": "✅ متصل بـ {name}",
        "status_starting": "جاري التشغيل...",
        "status_searching": "🔍 جاري البحث...",
        "tab_home": "الرئيسية",
        "tab_devices": "الأجهزة",
        "tab_history": "السجل",
        "tab_settings": "الإعدادات",
        "btn_open_folder": "📁 فتح المجلد",
        "btn_send": "📤 إرسال ملف/نص",
        "btn_search_devices": "🔍 بحث عن أجهزة",
        "btn_quick_connect": "⚡ اتصال سريع",
        "recent_files": "📂 آخر الملفات المستلمة",
        "view_all": "عرض الكل →",
        "save_dir_label": "📂 مجلد الحفظ:",
        "auto_open_label": "فتح الملفات تلقائياً عند الاستلام",
        "trusted_devices": "الأجهزة الموثوقة",
        "delete_device": "🗑️ حذف الجهاز",
        "no_history": "لا يوجد سجل بعد 📭",
        "no_devices": "لا توجد أجهزة متصلة 🔍",
        "last_connected": "آخر اتصال: {time}",
        "connected_now": "● متصل الآن",
        "version": "الإصدار",
        "pairing_req": "⚡ طلب اقتران جديد",
        "pairing_msg": "هل تريد السماح لجهاز '{name}' بالاتصال؟",
        "copied": "✅ تم نسخ النص إلى الحافظة",
        "accept": "✓ قبول",
        "reject": "✗ رفض",
        "tab_file": "📎 ملف",
        "tab_text": "✏️ نص",
        "drop_here": "اسحب الملف هنا",
        "or_click_browse": "أو انقر للاختيار",
        "ready_to_send": "✓ جاهز للإرسال",
        "paste_from_clipboard": "📋 لصق",
        "clear": "✕ مسح",
        "send_now": "إرسال",
        "target_ip": "IP الهاتف",
        "error": "خطأ",
        "enter_ip_first": "أدخل IP الهاتف",
        "select_file_first": "اختر ملفاً أولاً",
        "enter_text_first": "أدخل نصاً أولاً",
        "open_file": "👁️ فتح الملف",
        "open_folder": "📁 فتح المجلد",
        "received_from": "مستلم من: {device}",
        "sent_to": "مرسل إلى: {device}",
        "direction_sent": "📤 مرسل",
        "direction_received": "📥 مستلم",
        "discovered_devices": "📱 الأجهزة المكتشفة",
        "select_device": "اختر جهازاً للاتصال",
        "no_devices_found": "لم يتم العثور على أجهزة 📵",
        "manual_connect": "🔗 اتصال يدوي",
        "manual_connect_title": "🔗 أدخل عنوان IP الهاتف",
        "manual_connect_btn": "✓ اتصال",
        "manual_connect_warn": "أدخل عنوان IP صالح",
        "status_last_seen": "⚪ غير متصل (آخر اتصال قبل {mins} دقيقة)",
        "selected_files": "الملفات المختارة:",
        "add_more": "+ إضافة",
        "no_files_selected": "لم يتم اختيار ملفات بعد",
        "no_transfers_yet": "لا توجد عمليات نقل بعد",
        "text_input_hint": "✏️ اكتب أو الصق النص هنا:",
        "paste_clipboard": "📋 لصق من الحافظة",
        "clear_all": "✕ مسح الكل",
        "preparing": "جاري التحضير...",
        "sending_progress": "⏳ جاري الإرسال...",
        "sending_file": "إرسال ({idx}/{total}): {name}",
        "saving_file": "جاري الحفظ: {name}...",
        "retry_connect": "إعادة محاولة الاتصال ({attempt}/{max})... افتح التطبيق",
        "found_devices": "✅ تم العثور على {count} جهاز",
        "close": "✕ إغلاق",
        "no_device_connected_error": "لا يوجد جهاز متصل!\n\nاستخدم 'بحث عن أجهزة' أو 'اتصال يدوي' من الصفحة الرئيسية أولاً.",
        "auto_open_file_label": "فتح الملفات تلقائياً عند الاستلام",
        "auto_open_folder_label": "فتح مجلد الحفظ عند الاستلام",
        "warning": "تنبيه",
        "lang_label": "اللغة / Language"
    },
    "en": {
        "app_header": "Wameed",
        "status_ready": "⚪ Not connected",
        "status_connected_to": "✅ Connected to {name}",
        "status_starting": "Starting...",
        "status_searching": "🔍 Searching...",
        "tab_home": "Home",
        "tab_devices": "Devices",
        "tab_history": "History",
        "tab_settings": "Settings",
        "btn_open_folder": "📁 Open Folder",
        "btn_send": "📤 Send File/Text",
        "btn_search_devices": "🔍 Search Devices",
        "btn_quick_connect": "⚡ Quick Connect",
        "recent_files": "📂 Recent Files",
        "view_all": "View All →",
        "save_dir_label": "📂 Save Directory:",
        "auto_open_label": "Auto-open files on receipt",
        "trusted_devices": "Trusted Devices",
        "delete_device": "🗑️ Delete Device",
        "no_history": "No history yet 📭",
        "no_devices": "No connected devices 🔍",
        "last_connected": "Last connected: {time}",
        "connected_now": "● Connected now",
        "version": "Version",
        "pairing_req": "⚡ New Pairing Request",
        "pairing_msg": "Allow device '{name}' to connect?",
        "copied": "✅ Text copied to clipboard",
        "accept": "✓ Accept",
        "reject": "✗ Reject",
        "tab_file": "📎 File",
        "tab_text": "✏️ Text",
        "drop_here": "Drop file here",
        "or_click_browse": "or click to browse",
        "ready_to_send": "✓ Ready to send",
        "paste_from_clipboard": "📋 Paste",
        "clear": "✕ Clear",
        "send_now": "Send",
        "target_ip": "Phone IP",
        "error": "Error",
        "enter_ip_first": "Enter phone IP first",
        "select_file_first": "Select a file first",
        "enter_text_first": "Enter text first",
        "open_file": "👁️ Open File",
        "open_folder": "📁 Open Folder",
        "received_from": "Received from: {device}",
        "sent_to": "Sent to: {device}",
        "direction_sent": "📤 Sent",
        "direction_received": "📥 Received",
        "discovered_devices": "📱 Discovered Devices",
        "select_device": "Select a device to connect",
        "no_devices_found": "No devices found 📵",
        "manual_connect": "🔗 Manual Connect",
        "manual_connect_title": "🔗 Enter phone IP address",
        "manual_connect_btn": "✓ Connect",
        "manual_connect_warn": "Enter a valid IP address",
        "status_last_seen": "⚪ Not connected (last seen {mins} min ago)",
        "selected_files": "Selected files:",
        "add_more": "+ Add",
        "no_files_selected": "No files selected yet",
        "no_transfers_yet": "No transfers yet",
        "text_input_hint": "✏️ Type or paste text here:",
        "paste_clipboard": "📋 Paste from clipboard",
        "clear_all": "✕ Clear all",
        "preparing": "Preparing...",
        "sending_progress": "⏳ Sending...",
        "sending_file": "Sending ({idx}/{total}): {name}",
        "saving_file": "Saving: {name}...",
        "retry_connect": "Retrying ({attempt}/{max})... Open the app",
        "found_devices": "✅ Found {count} device(s)",
        "close": "✕ Close",
        "no_device_connected_error": "No device connected!\n\nUse 'Search Devices' or 'Manual Connect' from the home page first.",
        "auto_open_file_label": "Auto-open files on receipt",
        "auto_open_folder_label": "Open save folder on receipt",
        "warning": "Warning",
        "lang_label": "Language / اللغة"
    }
}

def t(key):
    return translations.get(LANG, translations["ar"]).get(key, key)

# ======================== Logging & Storage ========================
# App Data Directory (for config)
APP_DATA_DIR = os.path.join(os.path.expanduser("~"), ".wameed")
os.makedirs(APP_DATA_DIR, exist_ok=True)

# Local Logs Directory (as requested for easier debugging)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LOCAL_LOG_DIR = os.path.join(SCRIPT_DIR, "logs")
os.makedirs(LOCAL_LOG_DIR, exist_ok=True)
LOG_FILE = os.path.join(LOCAL_LOG_DIR, "receiver.log")

logger = logging.getLogger("Wameed")
logger.setLevel(logging.DEBUG)
_fmt = logging.Formatter("%(asctime)s | %(levelname)s | %(message)s")

# File Handler (Rotating for stability)
try:
    _fh = logging.handlers.RotatingFileHandler(LOG_FILE, maxBytes=2_000_000, backupCount=3, encoding="utf-8")
    _fh.setFormatter(_fmt)
    logger.addHandler(_fh)
except Exception as e:
    print(f"Logging Error: {e}")

# Console Handler (Stream to CMD)
_sh = logging.StreamHandler(sys.stdout)
_sh.setFormatter(_fmt)
logger.addHandler(_sh)

# ======================== Utils ========================
def get_resource_path(relative_path):
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath("."), relative_path)

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

# ======================== State & Storage ========================
CONFIG_FILE = os.path.join(APP_DATA_DIR, "config.json")
state = {
    "trusted_devices": [],
    "device_history": [],  # سجل الأجهزة المتصلة مع وقت الاتصال
    "history": [],
    "save_dir": os.path.join(os.path.expanduser("~"), "Downloads", "Wameed"),
    "auto_open": True,
    "auto_open_folder": False,
    "lang": "ar",
    "running": True,
    "target_ip": ""
}

# متغيرات عامة للتتبع
connected_device = None  # الجهاز المتصل حالياً {"id", "name", "ip", "connected_at"}
last_connection_time = None  # وقت آخر اتصال
connection_check_thread = None  # Thread لفحص الاتصال الدوري
active_connections = {}  # عدد اتصالات WebSocket النشطة لكل IP {ip: count}

def load_config():
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, 'r', encoding='utf-8') as f:
                saved = json.load(f)
                state.update(saved)
                global LANG
                LANG = state.get("lang", "ar")
        except Exception as e:
            logger.error(f"Failed to load config: {e}")

def save_config():
    try:
        state["lang"] = LANG
        with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(state, f, ensure_ascii=False, indent=4)
    except Exception as e:
        logger.error(f"Failed to save config: {e}")

load_config()
os.makedirs(state["save_dir"], exist_ok=True)

# ======================== GUI Logic ========================
class WameedApp:
    def __init__(self):
        # استخدام TkinterDnD.Tk() إذا كانت المكتبة متوفرة للـ Drag & Drop
        if TKDND_AVAILABLE:
            self.root = TkinterDnD.Tk()
        else:
            self.root = tk.Tk()

        self.root.title(APP_NAME)
        self.root.geometry("480x580")
        self.root.configure(bg="#F8FAFC")
        self.root.protocol("WM_DELETE_WINDOW", self.hide_window)

        self.icon_path = get_resource_path("wameed.ico")
        if os.path.exists(self.icon_path):
            self.root.iconbitmap(self.icon_path)
            self.tray_image = Image.open(self.icon_path)
        else:
            self.tray_image = Image.new('RGB', (64, 64), color=(46, 125, 50))

        self.setup_ui()
        self.setup_tray()

    def setup_ui(self):
        for w in self.root.winfo_children(): w.destroy()

        # --- Header with Logo ---
        hdr = tk.Frame(self.root, bg="#2E7D32", height=75)
        hdr.pack(fill="x")
        hdr.pack_propagate(False)

        # شعار "وميض" مع أيقونة البرق
        header_frame = tk.Frame(hdr, bg="#2E7D32")
        header_frame.pack(expand=True)

        # أيقونة البرق
        self.lightning_label = tk.Label(header_frame, text="⚡", bg="#2E7D32", fg="#FFD700",
                                        font=(FONT_AR, 24))
        self.lightning_label.pack(side="right" if LANG=="ar" else "left", padx=5)

        # نص "وميض"
        tk.Label(header_frame, text=t("app_header"), bg="#2E7D32", fg="white",
                 font=(FONT_AR, fs(22), "bold")).pack(side="right" if LANG=="ar" else "left", padx=5)

        # --- Style ---
        style = ttk.Style()
        style.configure("TNotebook", background="#F8FAFC")
        style.configure("TNotebook.Tab", font=(FONT_AR, fs(10)), padding=[12, 4])

        self.nb = ttk.Notebook(self.root)
        self.nb.pack(fill="both", expand=True, padx=12, pady=12)

        self.tab_home = tk.Frame(self.nb, bg="white")
        self.tab_devices = tk.Frame(self.nb, bg="white")
        self.tab_history = tk.Frame(self.nb, bg="white")
        self.tab_settings = tk.Frame(self.nb, bg="white")

        self.nb.add(self.tab_home, text=t("tab_home"))
        self.nb.add(self.tab_devices, text=t("tab_devices"))
        self.nb.add(self.tab_history, text=t("tab_history"))
        self.nb.add(self.tab_settings, text=t("tab_settings"))

        self._build_home()
        self._build_devices()
        self._build_history()
        self._build_settings()

        # تسجيل التنقل بين التبويبات
        tab_names = {0: "الرئيسية", 1: "الأجهزة", 2: "السجل", 3: "الإعدادات"}
        def on_tab_changed(event):
            idx = self.nb.index(self.nb.select())
            logger.info(f"📑 انتقال إلى تبويب: {tab_names.get(idx, idx)}")
            # تحديث البيانات عند التنقل
            if idx == 0: self._update_status_display(); self._refresh_recent()
            elif idx == 1: self._build_devices()
            elif idx == 2: self._build_history()
        self.nb.bind("<<NotebookTabSelect>>", on_tab_changed)

        # بدء فحص حالة الاتصال الدوري
        self._start_connection_monitor()

        logger.info(f"✅ تم تشغيل وميض بنجاح | الإصدار: {VERSION} | IP المحلي: {get_local_ip()}")

        # --- Bottom Bar ---
        btm = tk.Frame(self.root, bg="#F1F5F9", height=35)
        btm.pack(fill="x", side="bottom")
        tk.Label(btm, text=f"{t('version')} {VERSION} | {get_local_ip()}",
                 bg="#F1F5F9", fg="#64748B", font=(FONT_AR, fs(9))).pack(pady=6)

    def _build_home(self):
        for w in self.tab_home.winfo_children(): w.destroy()

        # Status Card - بطاقة الحالة المحسّنة
        card = tk.Frame(self.tab_home, bg="#F8FAFC", highlightthickness=1, highlightbackground="#E2E8F0")
        card.pack(fill="x", padx=16, pady=(16, 10))

        inner = tk.Frame(card, bg="#F8FAFC")
        inner.pack(padx=16, pady=14, fill="x")

        self.status_frame = tk.Frame(inner, bg="#F8FAFC")
        self.status_frame.pack(fill="x")

        self._update_status_display()

        # متغير IP مخفي (يُستخدم داخلياً فقط، لا يُعرض في الصفحة الرئيسية)
        self.home_ip_var = tk.StringVar(value=state.get("target_ip", ""))
        def on_ip_change(*args):
            state["target_ip"] = self.home_ip_var.get().strip()
            save_config()
        self.home_ip_var.trace_add("write", on_ip_change)

        # أزرار الإجراء السريع — صف أفقي
        quick_frame = tk.Frame(self.tab_home, bg="white")
        quick_frame.pack(fill="x", padx=16, pady=(0, 8))

        # زر الإرسال الرئيسي (بارز)
        tk.Button(quick_frame, text=f"⚡ {t('btn_send')}", bg="#2E7D32", fg="white",
                  font=(FONT_AR, fs(12), "bold"), bd=0, pady=12, cursor="hand2",
                  activebackground="#1B5E20", activeforeground="white",
                  command=self._show_send_dialog).pack(fill="x", pady=(0, 6))

        # صف الأزرار الثانوية
        sub_btn_frame = tk.Frame(quick_frame, bg="white")
        sub_btn_frame.pack(fill="x")

        search_btn = tk.Button(sub_btn_frame, text=t("btn_search_devices"), bg="#EFF6FF", fg="#2563EB",
                  font=(FONT_AR, fs(9), "bold"), bd=0, pady=8, cursor="hand2",
                  activebackground="#DBEAFE", activeforeground="#1D4ED8",
                  command=self._show_discovery_dialog)
        search_btn.pack(side="left", fill="x", expand=True, padx=(0, 4))

        tk.Button(sub_btn_frame, text=t("manual_connect"), bg="#F0FDF4", fg="#16A34A",
                  font=(FONT_AR, fs(9), "bold"), bd=0, pady=8, cursor="hand2",
                  activebackground="#DCFCE7", activeforeground="#15803D",
                  command=self._show_manual_ip_dialog).pack(side="left", fill="x", expand=True, padx=(4, 4))

        tk.Button(sub_btn_frame, text=t("btn_open_folder"), bg="#F1F5F9", fg="#475569",
                  font=(FONT_AR, fs(9), "bold"), bd=0, pady=8, cursor="hand2",
                  activebackground="#E2E8F0",
                  command=lambda: os.startfile(state["save_dir"])).pack(side="left", fill="x", expand=True, padx=(4, 0))

        # خط فاصل
        tk.Frame(self.tab_home, bg="#F1F5F9", height=1).pack(fill="x", padx=16, pady=6)

        # Recent Files — بطاقات محسّنة
        rec_label_frame = tk.Frame(self.tab_home, bg="white")
        rec_label_frame.pack(fill="x", padx=18, pady=(4, 5))
        tk.Label(rec_label_frame, text=t("recent_files"), bg="white",
                 font=(FONT_AR, fs(10), "bold"), fg="#475569").pack(side="right" if LANG=="ar" else "left")

        self.recent_container = tk.Frame(self.tab_home, bg="white")
        self.recent_container.pack(fill="both", expand=True, padx=16)
        self._refresh_recent()

    def _refresh_recent(self):
        for w in self.recent_container.winfo_children(): w.destroy()
        recent = state["history"][-5:]
        if not recent:
            empty = tk.Frame(self.recent_container, bg="white")
            empty.pack(expand=True)
            tk.Label(empty, text="📭", font=(FONT_AR, 24), bg="white", fg="#CBD5E1").pack(pady=(20, 4))
            tk.Label(empty, text=t("no_transfers_yet"),
                    bg="white", fg="#94A3B8", font=(FONT_AR, fs(10))).pack()
            return

        def _icon_for(fn, direction="received"):
            if direction == "sent": return "📤"
            ext = os.path.splitext(fn)[1].lower()
            m = {'.jpg':'🖼️','.jpeg':'🖼️','.png':'🖼️','.gif':'🖼️','.webp':'🖼️',
                 '.mp4':'🎬','.avi':'🎬','.mov':'🎬','.mp3':'🎵','.wav':'🎵',
                 '.pdf':'📕','.doc':'📝','.docx':'📝','.txt':'📃',
                 '.zip':'📦','.rar':'📦','.exe':'⚙️'}
            return m.get(ext, '📄')

        for item in reversed(recent):
            fname = item.get('filename', '')
            fpath = item.get('path', '')
            ftime = item.get('time', '')
            direction = item.get('direction', 'received')
            icon = _icon_for(fname, direction)

            row = tk.Frame(self.recent_container, bg="#FAFAFA", pady=6, padx=10)
            row.pack(fill="x", pady=2)

            tk.Label(row, text=icon, bg="#FAFAFA", font=(FONT_AR, fs(14))).pack(
                side="right" if LANG=="ar" else "left", padx=(0, 8))

            info = tk.Frame(row, bg="#FAFAFA")
            info.pack(side="right" if LANG=="ar" else "left", fill="x", expand=True)

            display_name = fname if len(fname) < 30 else fname[:27] + "..."
            tk.Label(info, text=display_name, bg="#FAFAFA", font=(FONT_AR, fs(9), "bold"),
                    fg="#1E293B", anchor="e" if LANG=="ar" else "w").pack(fill="x")

            detail_txt = ftime
            if direction == "sent":
                detail_txt = f"{ftime} | {t('direction_sent')}"

            tk.Label(info, text=detail_txt, bg="#FAFAFA", fg="#94A3B8",
                    font=(FONT_AR, fs(7)), anchor="e" if LANG=="ar" else "w").pack(fill="x")

            if fpath and os.path.exists(fpath):
                tk.Button(row, text="👁️", command=lambda p=fpath: os.startfile(p),
                         bg="#FAFAFA", fg="#3B82F6", bd=0, cursor="hand2",
                         font=(FONT_AR, fs(11))).pack(side="left" if LANG=="ar" else "right", padx=4)

    def _update_status_display(self):
        """تحديث عرض الحالة الذكية"""
        for w in self.status_frame.winfo_children(): w.destroy()

        global connected_device, last_connection_time

        if connected_device and connected_device.get("name"):
            # متصل بجهاز
            device_name = connected_device.get("name", "جهاز غير معروف")
            status_text = t("status_connected_to").format(name=device_name)
            dot_color = "#22C55E"  # أخضر
            show_ip = True
        elif last_connection_time:
            # كان متصلاً سابقاً (خلال 30 دقيقة)
            elapsed = (datetime.now() - last_connection_time).total_seconds()
            if elapsed < 1800:  # أقل من 30 دقيقة
                mins = max(1, int(elapsed / 60))
                status_text = t("status_last_seen").format(mins=mins)
            else:
                status_text = t("status_ready")
            dot_color = "#FBBF24"  # أصفر
            show_ip = False
        else:
            status_text = t("status_ready")
            dot_color = "#9CA3AF"  # رمادي
            show_ip = False

        self.status_dot = tk.Label(self.status_frame, text="●", fg=dot_color, bg="#F8FAFC", font=(FONT_AR, 18))
        self.status_dot.pack(side="right" if LANG=="ar" else "left", padx=8)

        self.status_label = tk.Label(self.status_frame, text=status_text,
                                     bg="#F8FAFC", font=(FONT_AR, fs(12), "bold"), fg="#1E293B")
        self.status_label.pack(side="right" if LANG=="ar" else "left")

        # تسجيل حالة الاتصال فقط عند تغيرها (لتقليل الضجيج)
        new_status = status_text
        if not hasattr(self, '_last_logged_status') or self._last_logged_status != new_status:
            self._last_logged_status = new_status
            logger.info(f"🔄 تحديث الحالة: {new_status}")

    def _start_connection_monitor(self):
        """بدء مراقبة الاتصال الدورية والبحث التلقائي"""
        def monitor():
            # بحث أولي عند التشغيل إذا لم يكن هناك IP
            if not state.get("target_ip"):
                time.sleep(2)
                self.root.after(0, self._auto_discover_target)

            while state["running"]:
                try:
                    # تحديث العرض كل 10 ثوانٍ
                    if hasattr(self, 'status_frame'):
                        self.root.after(0, self._update_status_display)
                except Exception as e:
                    logger.error(f"Status monitor error: {e}")
                time.sleep(10)

        threading.Thread(target=monitor, daemon=True).start()

    def _auto_discover_target(self):
        """البحث التلقائي عن أول جهاز متاح وتحديده كهدف"""
        if not state.get("target_ip"):
            logger.info("Auto-discovery: searching for available devices...")
            found = self._broadcast_discovery_multi(timeout=1.5)
            if found:
                device = found[0]
                ip = device.get("ip")
                state["target_ip"] = ip
                save_config()
                if hasattr(self, 'home_ip_var'):
                    self.home_ip_var.set(ip)
                logger.info(f"Auto-selected discovered device: {device.get('name')} ({ip})")

    def _show_manual_ip_dialog(self):
        """نافذة إدخال IP يدوي للاتصال"""
        logger.info("فتح نافذة الاتصال اليدوي")
        dialog = tk.Toplevel(self.root)
        dialog.title("🔗 اتصال يدوي")
        dialog.geometry("360x180")
        dialog.configure(bg="white")
        dialog.transient(self.root)
        dialog.grab_set()
        dialog.resizable(False, False)

        tk.Label(dialog, text=t("manual_connect_title"), font=(FONT_AR, fs(13), "bold"),
                bg="white", fg="#1E293B").pack(pady=(20, 10))

        ip_var = tk.StringVar(value=state.get("target_ip", ""))
        ip_entry = tk.Entry(dialog, textvariable=ip_var, font=(FONT_AR, fs(14)),
                           bd=0, relief="flat", justify="center", bg="#F1F5F9",
                           highlightthickness=2, highlightbackground="#CBD5E1",
                           highlightcolor="#3B82F6", width=20)
        ip_entry.pack(padx=30, pady=5, ipady=6)
        ip_entry.focus_set()

        def connect():
            ip = ip_var.get().strip()
            if ip:
                logger.info(f"اتصال يدوي بـ IP: {ip}")
                state["target_ip"] = ip
                save_config()
                if hasattr(self, 'home_ip_var'):
                    self.home_ip_var.set(ip)
                dialog.destroy()
            else:
                messagebox.showwarning(t("warning"), t("manual_connect_warn"))

        tk.Button(dialog, text=t("manual_connect_btn"), bg="#2E7D32", fg="white",
                  font=(FONT_AR, fs(11), "bold"), bd=0, pady=8, cursor="hand2",
                  activebackground="#1B5E20", activeforeground="white",
                  command=connect).pack(fill="x", padx=30, pady=(10, 15))

        ip_entry.bind("<Return>", lambda e: connect())

    def _show_discovery_dialog(self):
        """نافذة البحث عن الأجهزة والاتصال السريع"""
        logger.info("فتح نافذة البحث عن أجهزة📱")
        dialog = tk.Toplevel(self.root)
        dialog.title(t("discovered_devices"))
        dialog.geometry("400x350")
        dialog.configure(bg="white")
        dialog.transient(self.root)
        dialog.grab_set()

        # Header
        tk.Label(dialog, text=t("discovered_devices"), font=(FONT_AR, fs(14), "bold"),
                bg="white", fg="#2E7D32").pack(pady=15)

        # Status label
        status_label = tk.Label(dialog, text=t("status_searching"), font=(FONT_AR, fs(10)),
                               bg="white", fg="#3B82F6")
        status_label.pack()

        # Device list frame
        list_frame = tk.Frame(dialog, bg="white", bd=1, relief="solid")
        list_frame.pack(fill="both", expand=True, padx=20, pady=10)

        devices_listbox = tk.Listbox(list_frame, font=(FONT_AR, fs(11)), bd=0, selectmode="single")
        devices_listbox.pack(fill="both", expand=True, padx=5, pady=5)

        discovered_devices = []

        def on_device_select(event):
            selection = devices_listbox.curselection()
            if selection and discovered_devices:
                idx = selection[0]
                if idx < len(discovered_devices):
                    device = discovered_devices[idx]
                    ip = device.get("ip")
                    logger.info(f"تم اختيار الجهاز {device.get('name')} من قائمة البحث")

                    # تحديث IP الهدف في الرئيسية
                    state["target_ip"] = ip
                    save_config()
                    if hasattr(self, 'home_ip_var'):
                        self.home_ip_var.set(ip)

                    dialog.destroy()
                    # فتح نافذة الإرسال مع الجهاز المحدد
                    self._show_send_dialog(device_ip=ip, device_name=device.get("name"))

        devices_listbox.bind("<<ListboxSelect>>", on_device_select)

        def search_devices():
            nonlocal discovered_devices
            discovered_devices.clear()
            devices_listbox.delete(0, tk.END)
            logger.info("بدء البحث عن أجهزة وميض في الشبكة (Broadcast)...")

            # البحث عن الأجهزة
            found = self._broadcast_discovery_multi(timeout=1.5)

            if found:
                logger.info(f"تم العثور على {len(found)} أجهزة")
                discovered_devices.extend(found)
                for device in found:
                    name = device.get("name", "جهاز غير معروف")
                    ip = device.get("ip", "")
                    display = f"📱 {name}"
                    devices_listbox.insert(tk.END, display)
                status_label.config(text=t("found_devices").format(count=len(found)), fg="#22C55E")
            else:
                logger.warning("لم يتم العثور على أي أجهزة هاتف نشطة")
                status_label.config(text=t("no_devices_found"), fg="#EF4444")

        # زر البحث
        tk.Button(dialog, text="🔍 " + t("btn_search_devices"), command=search_devices,
                 bg="#3B82F6", fg="white", font=(FONT_AR, fs(10), "bold"),
                 bd=0, pady=8, cursor="hand2").pack(fill="x", padx=20, pady=5)

        # إغلاق
        tk.Button(dialog, text=t("close"), command=dialog.destroy,
                 bg="#E2E8F0", fg="#1E293B", font=(FONT_AR, fs(9)),
                 bd=0, pady=6).pack(fill="x", padx=20, pady=5)

        # بدء البحث تلقائياً
        dialog.after(500, search_devices)

    def _broadcast_discovery_multi(self, timeout=2.0):
        """البحث عن عدة أجهزة"""
        devices = []
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(timeout)

            message = json.dumps({
                "type": "discovery_ping",
                "service": "wameed_pc",
                "device": socket.gethostname(),
                "port": PORT_WS
            }).encode('utf-8')

            sock.sendto(message, ('<broadcast>', PORT_UDP))

            start_time = time.time()
            seen_ips = set()

            while time.time() - start_time < timeout:
                try:
                    data, addr = sock.recvfrom(1024)
                    resp = json.loads(data.decode('utf-8'))
                    if resp.get("type") == "discovery_pong" and resp.get("service") == "wameed_phone":
                        ip = addr[0]
                        if ip not in seen_ips:
                            seen_ips.add(ip)
                            devices.append({
                                "ip": ip,
                                "name": resp.get("device", f"جهاز {len(devices)+1}"),
                                "id": resp.get("device_id", "")
                            })
                except socket.timeout:
                    break
                except Exception as e:
                    logger.error(f"Discovery error: {e}")

            sock.close()
        except Exception as e:
            logger.error(f"Discovery failed: {e}")

        return devices

    def _build_devices(self):
        """بناء تبويب الأجهزة - سجل الأجهزة المتصلة"""
        for w in self.tab_devices.winfo_children(): w.destroy()

        # Header
        header = tk.Frame(self.tab_devices, bg="#F8FAFC", padx=20, pady=15)
        header.pack(fill="x")
        tk.Label(header, text=t("tab_devices"), font=(FONT_AR, fs(14), "bold"),
                bg="#F8FAFC", fg="#2E7D32").pack(side="right" if LANG=="ar" else "left")

        if not state["trusted_devices"] and not state.get("device_history", []):
            # لا توجد أجهزة
            empty_frame = tk.Frame(self.tab_devices, bg="white")
            empty_frame.pack(expand=True)
            tk.Label(empty_frame, text=t("no_devices"), font=(FONT_AR, fs(12)),
                    bg="white", fg="#94A3B8").pack(pady=50)

            tk.Button(empty_frame, text="🔍 " + t("btn_search_devices"),
                     command=self._show_discovery_dialog, bg="#3B82F6", fg="white",
                     font=(FONT_AR, fs(10), "bold"), bd=0, pady=8, padx=20).pack()
            return

        # Canvas for scrolling
        canvas = tk.Canvas(self.tab_devices, bg="white", highlightthickness=0)
        scroll = ttk.Scrollbar(self.tab_devices, orient="vertical", command=canvas.yview)
        frame = tk.Frame(canvas, bg="white")

        frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0,0), window=frame, anchor="nw", width=460)
        canvas.configure(yscrollcommand=scroll.set)

        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        # دمج الأجهزة الموثوقة وسجل الأجهزة
        all_devices = {}

        # إضافة الأجهزة الموثوقة
        for d in state["trusted_devices"]:
            device_id = d.get("id", "")
            if device_id:
                all_devices[device_id] = {
                    "id": device_id,
                    "name": d.get("name", "جهاز غير معروف"),
                    "trusted": True,
                    "last_seen": None
                }

        # إضافة سجل الأجهزة مع وقت الاتصال
        for d in state.get("device_history", []):
            device_id = d.get("id", "")
            if device_id:
                if device_id in all_devices:
                    all_devices[device_id]["last_seen"] = d.get("last_connected")
                else:
                    all_devices[device_id] = {
                        "id": device_id,
                        "name": d.get("name", "جهاز غير معروف"),
                        "trusted": False,
                        "last_seen": d.get("last_connected")
                    }

        # عرض الأجهزة
        for device_id, device in all_devices.items():
            row = tk.Frame(frame, bg="white", pady=10, padx=15)
            row.pack(fill="x")

            # التحقق إذا كان الجهاز متصلاً حالياً
            is_connected = connected_device and connected_device.get("id") == device_id

            # أيقونة الجهاز
            icon = "📱" if not is_connected else "✅"
            color = "#22C55E" if is_connected else "#3B82F6"

            # اسم الجهاز والحالة
            name_frame = tk.Frame(row, bg="white")
            name_frame.pack(fill="x")

            tk.Label(name_frame, text=icon, bg="white", font=(FONT_AR, fs(16))).pack(side="right" if LANG=="ar" else "left", padx=5)

            name_text = device["name"]
            if is_connected:
                name_text += f"  {t('connected_now')}"

            tk.Label(name_frame, text=name_text, bg="white", font=(FONT_AR, fs(11), "bold"),
                    fg=color, anchor="e" if LANG=="ar" else "w").pack(side="right" if LANG=="ar" else "left", fill="x", expand=True)

            # وقت آخر اتصال
            if device.get("last_seen") and not is_connected:
                time_text = t("last_connected").format(time=device["last_seen"])
                tk.Label(row, text=time_text, bg="white", fg="#94A3B8",
                        font=(FONT_AR, fs(9))).pack(anchor="e" if LANG=="ar" else "w")

            # زر الإرسال السريع (للأجهزة الموثوقة)
            if device["trusted"]:
                def make_send_handler(dev_ip, dev_name):
                    return lambda: self._quick_send_to_device(dev_ip, dev_name)

                # البحث عن IP الحالي إذا كان متاحاً
                current_ip = None
                if is_connected:
                    current_ip = connected_device.get("ip")

                btn_text = "📤 " + t("btn_send")
                btn_color = "#2E7D32"

                tk.Button(row, text=btn_text, command=make_send_handler(current_ip, device["name"]),
                         bg=btn_color, fg="white", font=(FONT_AR, fs(9), "bold"),
                         bd=0, pady=5, padx=15, cursor="hand2").pack(anchor="e" if LANG=="ar" else "w", pady=5)

            # خط فاصل
            tk.Frame(frame, bg="#F1F5F9", height=1).pack(fill="x", padx=15)

    def _quick_connect(self, device_id, device_name):
        """اتصال سريع بجهاز موثوق"""
        # البحث عن IP الجهاز
        found_devices = self._broadcast_discovery_multi(timeout=2.0)
        target_device = None

        for d in found_devices:
            if d.get("id") == device_id:
                target_device = d
                break

        if target_device:
            global connected_device, last_connection_time
            connected_device = {
                "id": device_id,
                "name": device_name,
                "ip": target_device.get("ip"),
                "connected_at": datetime.now()
            }
            last_connection_time = datetime.now()

            # تحديث العرض
            self._update_status_display()
            self._build_devices()

            # فتح نافذة الإرسال
            self._show_send_dialog(device_ip=target_device.get("ip"), device_name=device_name)
        else:
            messagebox.showwarning("تنبيه", f"لم يتم العثور على الجهاز '{device_name}' في الشبكة\nتأكد من أن التطبيق مفتوح على الهاتف")

    def _quick_send_to_device(self, device_ip, device_name):
        """إرسال سريع: يفتح نافذة الإرسال للجهاز المحدد مباشرة"""
        target_ip = device_ip
        if not target_ip:
            # محاولة البحث عن الجهاز في الشبكة
            logger.info(f"Quick send: searching for {device_name}...")
            found = self._broadcast_discovery_multi(timeout=1.5)
            for d in found:
                if d.get("name") == device_name:
                    target_ip = d.get("ip")
                    break

        if target_ip:
            # تحديث IP الهدف في الحالة الرئيسية
            state["target_ip"] = target_ip
            if hasattr(self, 'home_ip_var'):
                self.home_ip_var.set(target_ip)

            # فتح نافذة الإرسال
            self._show_send_dialog(device_ip=target_ip, device_name=device_name)
        else:
            messagebox.showwarning("تنبيه", f"تعذر العثور على الجهاز '{device_name}' حالياً.\nتأكد من اتصال الهاتف بنفس الشبكة.")

    def _build_history(self):
        """بناء تبويب السجل ببطاقات ملفات محسنة تدعم الإرسال والاستقبال"""
        for w in self.tab_history.winfo_children(): w.destroy()

        # Header
        header = tk.Frame(self.tab_history, bg="#F8FAFC", padx=20, pady=15)
        header.pack(fill="x")
        tk.Label(header, text=t("tab_history"), font=(FONT_AR, fs(14), "bold"),
                bg="#F8FAFC", fg="#2E7D32").pack(side="right" if LANG=="ar" else "left")

        if not state["history"]:
            empty_frame = tk.Frame(self.tab_history, bg="white")
            empty_frame.pack(expand=True)
            tk.Label(empty_frame, text=t("no_history"), bg="white", fg="#94A3B8",
                    font=(FONT_AR, fs(12))).pack(pady=50)
            return

        canvas = tk.Canvas(self.tab_history, bg="white", highlightthickness=0)
        scroll = ttk.Scrollbar(self.tab_history, orient="vertical", command=canvas.yview)
        frame = tk.Frame(canvas, bg="white")

        frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0,0), window=frame, anchor="nw", width=460)
        canvas.configure(yscrollcommand=scroll.set)

        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        def get_file_icon(filename, direction="received"):
            if direction == "sent": return "📤"
            ext = os.path.splitext(filename)[1].lower()
            icons = {
                '.jpg': '🖼️', '.jpeg': '🖼️', '.png': '🖼️', '.gif': '🖼️',
                '.mp4': '🎬', '.avi': '🎬', '.mov': '🎬', '.mkv': '🎬',
                '.mp3': '🎵', '.wav': '🎵', '.flac': '🎵',
                '.pdf': '📄', '.doc': '📝', '.docx': '📝', '.txt': '📃',
                '.zip': '📦', '.rar': '📦', '.7z': '📦',
                '.exe': '⚙️', '.msi': '⚙️'
            }
            return icons.get(ext, '📎')

        for entry in reversed(state["history"]):
            # بطاقة الملف
            direction = entry.get("direction", "received")
            card_bg = "#F8FAFC" if direction == "received" else "#F0F9FF"
            border_color = "#E2E8F0" if direction == "received" else "#BAE6FD"

            card = tk.Frame(frame, bg=card_bg, padx=15, pady=12,
                           highlightthickness=1, highlightbackground=border_color)
            card.pack(fill="x", padx=15, pady=5)

            filename = entry.get("filename", "")
            time_str = entry.get("time", "")
            filepath = entry.get("path", "")
            device_name = entry.get("device", "")

            # الصف العلوي: أيقونة + اسم الملف
            top_row = tk.Frame(card, bg=card_bg)
            top_row.pack(fill="x")

            icon = get_file_icon(filename, direction)
            tk.Label(top_row, text=icon, bg=card_bg, font=(FONT_AR, fs(20))).pack(
                side="right" if LANG=="ar" else "left", padx=(0, 10))

            # اسم الملف (مختصر إذا طويل)
            display_name = filename if len(filename) < 35 else filename[:32] + "..."
            tk.Label(top_row, text=display_name, bg=card_bg, font=(FONT_AR, fs(11), "bold"),
                    anchor="e" if LANG=="ar" else "w").pack(
                side="right" if LANG=="ar" else "left", fill="x", expand=True)

            # الصف السفلي: التفاصيل والأزرار
            bottom_row = tk.Frame(card, bg=card_bg)
            bottom_row.pack(fill="x", pady=(8, 0))

            # وقت وتفاصيل النقل
            if direction == "received":
                info_text = f"{time_str}  •  {t('received_from').format(device=device_name)}"
            else:
                info_text = f"{time_str}  •  {t('sent_to').format(device=device_name or 'الهاتف')}"

            tk.Label(bottom_row, text=info_text, bg=card_bg, fg="#64748B",
                    font=(FONT_AR, fs(9))).pack(side="right" if LANG=="ar" else "left")

            # أزرار الإجراءات
            btn_frame = tk.Frame(bottom_row, bg=card_bg)
            btn_frame.pack(side="left" if LANG=="ar" else "right")

            if filepath and os.path.exists(filepath):
                # زر فتح الملف
                tk.Button(btn_frame, text=t("open_file"), command=lambda p=filepath: os.startfile(p),
                         bg="#10B981" if direction=="received" else "#3B82F6", fg="white", font=(FONT_AR, fs(8)),
                         bd=0, pady=3, padx=10, cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=2)

                # زر فتح المجلد
                folder = os.path.dirname(filepath)
                tk.Button(btn_frame, text=t("open_folder"), command=lambda f=folder: os.startfile(f),
                         bg="#64748B", fg="white", font=(FONT_AR, fs(8)),
                         bd=0, pady=3, padx=10, cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=2)

    def _build_settings(self):
        for w in self.tab_settings.winfo_children(): w.destroy()

        container = tk.Frame(self.tab_settings, bg="white", padx=20, pady=20)
        container.pack(fill="both", expand=True)

        # Language
        tk.Label(container, text=t("lang_label"), bg="white", font=(FONT_AR, fs(10), "bold")).pack(anchor="e" if LANG=="ar" else "w")
        lang_frame = tk.Frame(container, bg="white")
        lang_frame.pack(fill="x", pady=5)

        def set_lang(l):
            global LANG
            logger.info(f"⚙️ تغيير اللغة: {LANG} → {l}")
            LANG = l
            save_config()
            self.setup_ui()

        tk.Button(lang_frame, text="العربية", command=lambda: set_lang("ar"), width=10).pack(side="right" if LANG=="ar" else "left", padx=5)
        tk.Button(lang_frame, text="English", command=lambda: set_lang("en"), width=10).pack(side="right" if LANG=="ar" else "left", padx=5)

        ttk.Separator(container).pack(fill="x", pady=15)

        # Save Dir
        tk.Label(container, text=t("save_dir_label"), bg="white", font=(FONT_AR, fs(10), "bold")).pack(anchor="e" if LANG=="ar" else "w")
        path_frame = tk.Frame(container, bg="white")
        path_frame.pack(fill="x", pady=5)

        self.path_var = tk.StringVar(value=state["save_dir"])
        tk.Entry(path_frame, textvariable=self.path_var, font=(FONT_AR, fs(9)), bd=1, relief="solid").pack(side="right" if LANG=="ar" else "left", fill="x", expand=True, padx=5)
        tk.Button(path_frame, text="...", command=self.browse_folder).pack(side="right" if LANG=="ar" else "left")

        # Auto Open File
        self.auto_open_var = tk.BooleanVar(value=state["auto_open"])
        tk.Checkbutton(container, text=t("auto_open_file_label"), variable=self.auto_open_var,
                       bg="white", font=(FONT_AR, fs(10)), command=self.toggle_auto_open).pack(anchor="e" if LANG=="ar" else "w", pady=(10, 0))

        # Auto Open Folder
        self.auto_open_folder_var = tk.BooleanVar(value=state.get("auto_open_folder", False))
        tk.Checkbutton(container, text=t("auto_open_folder_label"), variable=self.auto_open_folder_var,
                       bg="white", font=(FONT_AR, fs(10)), command=self.toggle_auto_open_folder).pack(anchor="e" if LANG=="ar" else "w", pady=(0, 10))

        # Trusted Devices
        tk.Label(container, text=t("trusted_devices"), bg="white", font=(FONT_AR, fs(10), "bold")).pack(anchor="e" if LANG=="ar" else "w", pady=(10, 2))
        self.devices_list = tk.Listbox(container, height=5, font=(FONT_AR, fs(9)), bd=1, relief="solid")
        self.devices_list.pack(fill="x")
        self.refresh_devices_list()

        tk.Button(container, text=t("delete_device"), command=self.remove_device,
                  bg="#FEE2E2", fg="#991B1B", bd=0, pady=5, font=(FONT_AR, fs(9))).pack(anchor="e" if LANG=="ar" else "w", pady=5)

    def _broadcast_discovery(self, timeout=2.0):
        """يرسل رسالة UDP Broadcast للبحث عن الهواتف التي تشغل تطبيق وميض"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(timeout)

            message = json.dumps({
                "type": "discovery_ping",
                "service": "wameed_pc",
                "device": socket.gethostname(),
                "port": PORT_WS
            }).encode('utf-8')

            sock.sendto(message, ('<broadcast>', PORT_UDP))

            start_time = time.time()
            while time.time() - start_time < timeout:
                try:
                    data, addr = sock.recvfrom(1024)
                    resp = json.loads(data.decode('utf-8'))
                    if resp.get("type") == "discovery_pong" and resp.get("service") == "wameed_phone":
                        logger.info(f"Found Wameed Phone at {addr[0]}")
                        return addr[0]
                except socket.timeout:
                    break
                except Exception as e:
                    logger.error(f"Error during discovery recv: {e}")
        except Exception as e:
            logger.error(f"Discovery error: {e}")
        finally:
            sock.close()
        return None

    def _show_send_dialog(self, device_ip=None, device_name=None):
        """نافذة الإرسال الاحترافية - تدعم التعدد والتصميم الحديث"""
        logger.info(f"📤 فتح نافذة الإرسال | الهدف: {device_name or 'غير محدد'} ({device_ip or state.get('target_ip', 'لا يوجد IP')})")
        win = tk.Toplevel(self.root)
        win.title(t("btn_send"))
        win.geometry("520x680")
        win.configure(bg="#F8FAFC")
        win.transient(self.root)
        win.grab_set()
        win.resizable(False, False)

        # المتغيرات
        self.selected_files = [] # قائمة المسارات الكاملة

        # ===== Header بتصميم أنيق =====
        header_frame = tk.Frame(win, bg="#2E7D32", padx=20, pady=18)
        header_frame.pack(fill="x")

        header_text = t("btn_send")
        if device_name:
            header_text = f"📤 {t('btn_send')} ← {device_name}"
        tk.Label(header_frame, text=header_text, font=(FONT_AR, 15, "bold"),
                bg="#2E7D32", fg="white").pack()

        # ===== [Bottom Area] IP + Progress + Send — يُحزم أولاً ليبقى ثابتاً =====
        bottom_frame = tk.Frame(win, bg="white", padx=20, pady=12)
        bottom_frame.pack(side="bottom", fill="x")

        # خط فاصل فوق المنطقة السفلية
        tk.Frame(win, bg="#E2E8F0", height=1).pack(side="bottom", fill="x")

        # تحديد IP الهدف تلقائياً (بدون عرض حقل IP في نافذة الإرسال)
        initial_ip = device_ip or state.get("target_ip", "")
        if not initial_ip and connected_device:
            initial_ip = connected_device.get("ip", "")
            if not device_name:
                device_name = connected_device.get("name", "")

        self.target_ip_var = tk.StringVar(value=initial_ip if initial_ip else "")

        # حالة الإرسال (مخفية للبداية)
        self.send_status_frame = tk.Frame(bottom_frame, bg="white")
        self.progress_var = tk.DoubleVar(value=0)
        self.progress_bar = ttk.Progressbar(self.send_status_frame, orient="horizontal",
                                           mode="determinate", variable=self.progress_var)
        self.progress_bar.pack(fill="x", pady=4)
        self.progress_label = tk.Label(self.send_status_frame, text=t("preparing"),
                                      bg="white", font=(FONT_AR, fs(9)), fg="#64748B")
        self.progress_label.pack()

        def start_sending():
            ip = self.target_ip_var.get().strip()
            if not ip:
                logger.warning("Send attempt failed: No target device connected.")
                messagebox.showerror(t("error"), t("no_device_connected_error"))
                return

            mode = tabs.index(tabs.select())
            logger.info(f"Send button clicked. Mode: {'Files' if mode == 0 else 'Text'}, Target IP: {ip}")

            self.send_status_frame.pack(fill="x", before=send_btn, pady=(4, 0))
            send_btn.config(state="disabled", text=t("sending_progress"),
                          bg="#66BB6A", cursor="watch")

            if mode == 0: # ملفات
                if not self.selected_files:
                    logger.warning("⚠️ محاولة إرسال فاشلة: لم يتم اختيار ملفات")
                    messagebox.showerror(t("error"), t("select_file_first"))
                    self.send_status_frame.pack_forget()
                    send_btn.config(state="normal", text=f"⚡ {t('send_now')}", bg="#2E7D32", cursor="hand2")
                    return
                logger.info(f"Starting to send {len(self.selected_files)} files to {ip}")
                threading.Thread(target=self._execute_multi_send, args=(ip, self.selected_files, win, send_btn, device_name), daemon=True).start()
            else: # نص
                txt = self.send_text_area.get("1.0", "end").strip()
                if not txt:
                    logger.warning("Send attempt failed: Text area is empty.")
                    messagebox.showerror(t("error"), t("enter_text_first"))
                    self.send_status_frame.pack_forget()
                    send_btn.config(state="normal", text=f"⚡ {t('send_now')}", bg="#2E7D32", cursor="hand2")
                    return
                logger.info(f"Starting to send text content ({len(txt)} chars) to {ip}")
                threading.Thread(target=self._execute_send_text, args=(ip, txt, win, self.progress_var, device_name, lambda: win.destroy()), daemon=True).start()

        send_btn = tk.Button(bottom_frame, text=f"⚡ {t('send_now')}", command=start_sending,
                            bg="#2E7D32", fg="white", font=(FONT_AR, fs(14), "bold"),
                            bd=0, pady=14, cursor="hand2",
                            activebackground="#1B5E20", activeforeground="white")
        send_btn.pack(fill="x", pady=(8, 0))

        # ===== تبويبات التصميم الحديث =====
        style = ttk.Style()
        style.configure("Send.TNotebook", background="#F8FAFC")
        style.configure("Send.TNotebook.Tab", font=(FONT_AR, fs(11)), padding=[20, 8])

        tabs = ttk.Notebook(win, style="Send.TNotebook")
        tabs.pack(fill="both", expand=True, padx=16, pady=(12, 8))

        tab_file = tk.Frame(tabs, bg="white")
        tab_text = tk.Frame(tabs, bg="white")

        tabs.add(tab_file, text=f"  {t('tab_file')}  ")
        tabs.add(tab_text, text=f"  {t('tab_text')}  ")

        # ========== [1] تبويب الملفات (دعم التعدد) ==========
        file_container = tk.Frame(tab_file, bg="white", padx=15, pady=12)
        file_container.pack(fill="both", expand=True)

        # منطقة السحب والإفلات — تصميم محسّن مع حدود منقطة
        self.drop_frame = tk.Frame(file_container, bg="#F0FFF4", height=90,
                              highlightthickness=2, highlightbackground="#86EFAC")
        self.drop_frame.pack(fill="x", pady=(0, 10))
        self.drop_frame.pack_propagate(False)

        drop_inner = tk.Frame(self.drop_frame, bg="#F0FFF4")
        drop_inner.pack(expand=True)

        self.drop_icon = tk.Label(drop_inner, text="📂", font=(FONT_AR, 22), bg="#F0FFF4", fg="#22C55E")
        self.drop_icon.pack(side="left" if LANG=="ar" else "right", padx=8)

        drop_text_frame = tk.Frame(drop_inner, bg="#F0FFF4")
        drop_text_frame.pack(side="left" if LANG=="ar" else "right")

        self.drop_txt = tk.Label(drop_text_frame, text=t("drop_here"),
                                bg="#F0FFF4", font=(FONT_AR, fs(11), "bold"), fg="#166534")
        self.drop_txt.pack()
        tk.Label(drop_text_frame, text=t("or_click_browse"),
                bg="#F0FFF4", font=(FONT_AR, fs(9)), fg="#4ADE80").pack()

        # قائمة الملفات المختارة (Scrollable)
        list_header = tk.Frame(file_container, bg="white")
        list_header.pack(fill="x")
        tk.Label(list_header, text=t("selected_files"), bg="white",
                font=(FONT_AR, fs(9), "bold"), fg="#64748B").pack(
                    side="right" if LANG=="ar" else "left")

        # زر إضافة ملفات إضافية
        add_more_btn = tk.Label(list_header, text=t("add_more"), bg="white",
                               fg="#3B82F6", font=(FONT_AR, fs(9), "bold"), cursor="hand2")
        add_more_btn.pack(side="left" if LANG=="ar" else "right")

        self.files_list_frame = tk.Frame(file_container, bg="white",
                                        highlightthickness=1, highlightbackground="#E2E8F0")
        self.files_list_frame.pack(fill="both", expand=True, pady=5)

        self.files_canvas = tk.Canvas(self.files_list_frame, bg="white", highlightthickness=0)
        self.files_scroll = ttk.Scrollbar(self.files_list_frame, orient="vertical", command=self.files_canvas.yview)
        self.files_inner = tk.Frame(self.files_canvas, bg="white")

        self.files_inner.bind("<Configure>", lambda e: self.files_canvas.configure(scrollregion=self.files_canvas.bbox("all")))
        self.files_canvas.create_window((0,0), window=self.files_inner, anchor="nw", width=400)
        self.files_canvas.configure(yscrollcommand=self.files_scroll.set)

        self.files_canvas.pack(side="left", fill="both", expand=True)
        self.files_scroll.pack(side="right", fill="y")

        def _get_file_type_icon(filename):
            ext = os.path.splitext(filename)[1].lower()
            icons = {
                '.jpg': '🖼️', '.jpeg': '🖼️', '.png': '🖼️', '.gif': '🖼️', '.webp': '🖼️',
                '.mp4': '🎬', '.avi': '🎬', '.mov': '🎬', '.mkv': '🎬',
                '.mp3': '🎵', '.wav': '🎵', '.flac': '🎵',
                '.pdf': '📕', '.doc': '📝', '.docx': '📝', '.txt': '📃',
                '.zip': '📦', '.rar': '📦', '.7z': '📦',
                '.exe': '⚙️', '.msi': '⚙️', '.apk': '📱'
            }
            return icons.get(ext, '📄')

        def update_file_list_ui():
            for w in self.files_inner.winfo_children(): w.destroy()
            if not self.selected_files:
                empty_frame = tk.Frame(self.files_inner, bg="white")
                empty_frame.pack(expand=True, fill="both")
                tk.Label(empty_frame, text="📭", font=(FONT_AR, fs(20)), bg="white", fg="#CBD5E1").pack(pady=(15, 2))
                tk.Label(empty_frame, text=t("no_files_selected"),
                        bg="white", fg="#94A3B8", font=(FONT_AR, fs(10))).pack()
                return

            for idx, path in enumerate(self.selected_files):
                fname = os.path.basename(path)
                fsize = os.path.getsize(path) / 1024
                size_str = f"{fsize:.1f} KB" if fsize < 1024 else f"{fsize/1024:.1f} MB"
                icon = _get_file_type_icon(fname)

                f_row = tk.Frame(self.files_inner, bg="#FAFAFA", pady=6, padx=10)
                f_row.pack(fill="x", pady=2, padx=4)

                tk.Label(f_row, text=icon, bg="#FAFAFA", font=(FONT_AR, fs(14))).pack(
                    side="right" if LANG=="ar" else "left")

                info_frame = tk.Frame(f_row, bg="#FAFAFA")
                info_frame.pack(side="right" if LANG=="ar" else "left", fill="x", expand=True, padx=8)
                tk.Label(info_frame, text=fname, bg="#FAFAFA", font=(FONT_AR, fs(10)),
                        anchor="e" if LANG=="ar" else "w").pack(fill="x")
                tk.Label(info_frame, text=size_str, bg="#FAFAFA", fg="#94A3B8",
                        font=(FONT_AR, fs(8)), anchor="e" if LANG=="ar" else "w").pack(fill="x")

                def remove_f(i=idx):
                    removed_file = self.selected_files.pop(i)
                    logger.info(f"File removed from list: {os.path.basename(removed_file)}")
                    update_file_list_ui()

                tk.Button(f_row, text="✕", command=remove_f, bg="#FAFAFA", fg="#EF4444",
                         bd=0, cursor="hand2", font=(FONT_AR, fs(10))).pack(
                    side="left" if LANG=="ar" else "right", padx=5)

        def on_add_click(event=None):
            logger.info("Opening file picker dialog")
            paths = filedialog.askopenfilenames()
            if paths:
                logger.info(f"User selected {len(paths)} files")
                for p in paths:
                    if p not in self.selected_files: self.selected_files.append(p)
                update_file_list_ui()
            else:
                logger.info("File picker closed without selection")

        self.drop_frame.bind("<Button-1>", on_add_click)
        drop_inner.bind("<Button-1>", on_add_click)
        self.drop_icon.bind("<Button-1>", on_add_click)
        self.drop_txt.bind("<Button-1>", on_add_click)
        add_more_btn.bind("<Button-1>", on_add_click)
        update_file_list_ui()

        if TKDND_AVAILABLE:
            win.drop_target_register(DND_FILES)
            win.dnd_bind('<<Drop>>', lambda e: self._handle_multi_drop(e, update_file_list_ui))

        # ========== [2] تبويب النص — تصميم محسّن ==========
        text_container = tk.Frame(tab_text, bg="white", padx=15, pady=12)
        text_container.pack(fill="both", expand=True)

        text_header = tk.Frame(text_container, bg="white")
        text_header.pack(fill="x", pady=(0, 8))
        tk.Label(text_header, text=t("text_input_hint"),
                bg="white", font=(FONT_AR, fs(10), "bold"), fg="#475569").pack(
                    side="right" if LANG=="ar" else "left")

        # صندوق النص بتصميم أنيق
        text_frame = tk.Frame(text_container, bg="#F8FAFC",
                             highlightthickness=1, highlightbackground="#E2E8F0")
        text_frame.pack(fill="both", expand=True)

        self.send_text_area = tk.Text(text_frame, font=(FONT_AR, fs(12)), wrap="word",
                                bd=0, relief="flat", padx=16, pady=14,
                                bg="#F8FAFC", fg="#1E293B",
                                insertbackground="#2E7D32", insertwidth=2,
                                undo=True, highlightthickness=0,
                                selectbackground="#BBF7D0", selectforeground="#14532D")
        self.send_text_area.pack(fill="both", expand=True)

        # دعم اللصق والاختصارات بشكل صريح ومرن
        def handle_paste(e=None):
            try:
                content = win.clipboard_get()
                logger.info(f"Pasted text into send area ({len(content)} characters)")
                self.send_text_area.insert(tk.INSERT, content)
                return "break" # منع السلوك الافتراضي المزدوج
            except: pass

        self.send_text_area.bind("<Control-v>", handle_paste)
        self.send_text_area.bind("<Control-V>", handle_paste)
        self.send_text_area.bind("<Control-a>", lambda e: self.send_text_area.tag_add("sel", "1.0", "end") or "break")
        self.send_text_area.bind("<Control-A>", lambda e: self.send_text_area.tag_add("sel", "1.0", "end") or "break")

        # أزرار أدوات النص
        text_tools = tk.Frame(text_container, bg="white")
        text_tools.pack(fill="x", pady=(8, 0))

        def paste_now():
            try:
                content = win.clipboard_get()
                logger.info(f"Paste button clicked. Adding {len(content)} characters")
                self.send_text_area.insert(tk.INSERT, content)
            except:
                logger.warning("Paste failed: Clipboard might be empty or not contain text")

        def clear_now():
            logger.info("Text area cleared by user")
            self.send_text_area.delete("1.0", "end")

        tk.Button(text_tools, text=t("paste_clipboard"), command=paste_now,
                 bg="#F1F5F9", fg="#475569", bd=0, pady=6, padx=15,
                 cursor="hand2", font=(FONT_AR, fs(9))).pack(
                     side="right" if LANG=="ar" else "left")
        tk.Button(text_tools, text=t("clear_all"),
                 command=clear_now,
                 bg="#FEF2F2", fg="#991B1B", bd=0, pady=6, padx=15,
                 cursor="hand2", font=(FONT_AR, fs(9))).pack(
                     side="right" if LANG=="ar" else "left", padx=10)

    def _handle_multi_drop(self, event, callback):
        """معالجة إفلات ملفات متعددة"""
        try:
            data = event.data
            # تنظيف مسارات ويندوز التي تحتوي على مسافات وتوضع بين {}
            import re
            paths = re.findall(r'\{(.*?)\}|(\S+)', data)
            cleaned_paths = [p[0] if p[0] else p[1] for p in paths]

            logger.info(f"تم استقبال {len(cleaned_paths)} ملفات عبر السحب والإفلات")
            for p in cleaned_paths:
                if os.path.exists(p) and p not in self.selected_files:
                    self.selected_files.append(p)
            callback()
        except Exception as e:
            logger.error(f"Multi-drop error: {e}")

    def _show_inline_message(self, window, message, color="#2E7D32", duration=2500):
        """عرض رسالة تنبيه داخلية بدلاً من messagebox"""
        msg_label = tk.Label(window, text=message, bg=color, fg="white", font=(FONT_AR, fs(10), "bold"), pady=10)
        msg_label.pack(fill="x", side="bottom")

        # تلاشي تدريجي أو إغلاق بعد مدة
        def remove():
            try:
                msg_label.destroy()
                if color == "#2E7D32": # إذا كان نجاح، نغلق النافذة بالكامل
                    window.destroy()
            except: pass

        window.after(duration, remove)

    def _execute_multi_send(self, ip, files, window, btn, device_name=None):
        """إرسال مجموعة ملفات مع تحديث واجهة المستخدم"""
        logger.info(f"بدء إرسال {len(files)} ملفات إلى الهاتف ({ip})")
        async def task():
            max_retries = 3
            retry_delay = 2

            try:
                for attempt in range(max_retries):
                    try:
                        uri = f"ws://{ip}:7789"
                        # ملاحظة: تم تغيير connect_timeout إلى open_timeout لتوافق مكتبة websockets
                        async with websockets.connect(uri, open_timeout=10, ping_interval=5, ping_timeout=10) as ws:
                            # Hello
                            await ws.send(json.dumps({"type":"hello", "device":socket.gethostname(), "device_id":"pc_client", "app_version":VERSION}))

                            # استلام الرد مع مهلة زمنية
                            resp_raw = await asyncio.wait_for(ws.recv(), timeout=10)
                            resp = json.loads(resp_raw)

                            if resp.get("status") != "paired":
                                logger.warning(f"تم رفض الاتصال من الهاتف ({ip})")
                                window.after(0, lambda: self._show_inline_message(window, "❌ تم رفض الاتصال من الهاتف", "#EF4444"))
                                return

                            total_files = len(files)
                            for idx, path in enumerate(files):
                                fname = os.path.basename(path)
                                fsize = os.path.getsize(path)
                                logger.info(f"جاري إرسال ({idx+1}/{total_files}): {fname}")
                                start_time = time.time()

                                window.after(0, lambda i=idx, n=fname: self.progress_label.config(text=t("sending_file").format(idx=i+1, total=total_files, name=n)))

                                # تقليل حجم الـ Chunk إلى 512KB لتفادي خطأ Semaphore timeout (WinError 121)
                                chunk_size = 512 * 1024
                                total_chunks = (fsize + chunk_size - 1) // chunk_size

                                await ws.send(json.dumps({"type":"file_meta", "filename":fname, "size":fsize, "chunks":total_chunks}))

                                sent = 0
                                with open(path, "rb") as f:
                                    for _ in range(total_chunks):
                                        chunk = f.read(chunk_size)
                                        await ws.send(chunk)
                                        sent += len(chunk)
                                        pct = (sent / fsize) * 100
                                        window.after(0, lambda p=pct: self.progress_var.set(p))

                                # استلام تأكيد الحفظ لكل ملف مع مهلة زمنية ذكية لتجنب التجمد
                                try:
                                    while True:
                                        # Use a longer timeout (120s) as the Android watchdog is 2 mins,
                                        # but "saving" status will reset the wait in each iteration.
                                        final_resp_raw = await asyncio.wait_for(ws.recv(), timeout=120)
                                        final_resp = json.loads(final_resp_raw)

                                        if final_resp.get("status") == "saving":
                                            logger.info(f"الهاتف يقوم بحفظ الملف {fname}...")
                                            window.after(0, lambda n=fname: self.progress_label.config(text=t("saving_file").format(name=n)))
                                            continue

                                        if final_resp.get("status") == "saved": break
                                        if final_resp.get("status") == "error":
                                            raise Exception(final_resp.get("message", "خطأ غير معروف في الهاتف"))
                                except asyncio.TimeoutError:
                                    logger.warning(f"Timeout waiting for 'saved' status for {fname}")
                                    # نواصل الإرسال للملف التالي رغم التايم آوت لضمان عدم توقف العملية بالكامل

                                elapsed = time.time() - start_time
                                logger.info(f"تم إرسال {fname} بنجاح")

                                # إضافة للملفات المرسلة في السجل (تم إصلاح تمرير اسم الجهاز)
                                d_name = device_name if device_name else ip
                                self.root.after(0, lambda n=fname, p=path, dn=d_name: self.add_to_history(n, p, device_name=dn, direction="sent"))

                            logger.info(f"اكتمل إرسال جميع الملفات ({total_files}) بنجاح.")
                            window.after(0, lambda: self._show_inline_message(window, f"✅ تم إرسال {total_files} ملفات بنجاح"))
                            return # نجاح، اخرج من حلقة المحاولات

                    except (ConnectionRefusedError, OSError) as e:
                        if attempt < max_retries - 1:
                            logger.warning(f"🔄 فشل الاتصال بـ {ip} (محاولة {attempt+1}/{max_retries}): {type(e).__name__}: {e}")
                            window.after(0, lambda a=attempt+1: self.progress_label.config(text=t("retry_connect").format(attempt=a, max=max_retries)))
                            await asyncio.sleep(retry_delay)
                        else:
                            logger.error(f"❌ فشل نهائي بعد {max_retries} محاولات للاتصال بـ {ip}: {e}")
                            raise e
                    except Exception as e:
                        logger.error(f"❌ خطأ غير متوقع أثناء الإرسال إلى {ip}: {type(e).__name__}: {e}")
                        raise e

            except Exception as e:
                logger.error(f"❌ خطأ في إرسال الملفات إلى {ip}: {type(e).__name__}: {e}")
                error_msg = str(e)
                if "121" in error_msg: error_msg = "خطأ في الشبكة (Timeout) - تحقق من اتصال WiFi"
                elif "1225" in error_msg or "ConnectionRefused" in type(e).__name__: error_msg = "الهاتف رفض الاتصال - تأكد من فتح التطبيق وتفعيل الاستقبال"
                elif "unexpected keyword argument 'connect_timeout'" in error_msg: error_msg = "خطأ داخلي في مكتبة الاتصال"
                elif "timed out" in error_msg.lower() or "timeout" in error_msg.lower(): error_msg = "انتهت مهلة الاتصال - الجهاز لا يستجيب"
                else: error_msg = f"فشل الإرسال: {error_msg[:80]}"

                window.after(0, lambda m=error_msg: self._show_inline_message(window, f"❌ {m}", "#EF4444"))
                window.after(0, lambda: btn.config(state="normal", text=t("send_now"), bg="#2E7D32"))

        asyncio.run(task())

    def _execute_send_text(self, ip, text, window, progress_var, device_name=None, on_success=None):
        """إرسال نص للهاتف"""
        logger.info(f"بدء إرسال نص إلى الهاتف ({ip})")
        async def send_text_task():
            try:
                uri = f"ws://{ip}:7789"
                # ملاحظة: تم تغيير connect_timeout إلى open_timeout لتوافق مكتبة websockets
                async with websockets.connect(uri, open_timeout=5) as websocket:
                    # إرسال hello
                    await websocket.send(json.dumps({
                        "type": "hello",
                        "device": socket.gethostname(),
                        "device_id": "pc_client",
                        "app_version": VERSION
                    }))

                    resp_raw = await asyncio.wait_for(websocket.recv(), timeout=5)
                    resp = json.loads(resp_raw)
                    if resp.get("status") != "paired":
                        logger.warning(f"تم رفض إرسال النص من الهاتف ({ip})")
                        window.after(0, lambda: self._show_inline_message(window, "❌ تم رفض الاتصال", "#EF4444"))
                        return

                    window.after(0, lambda: self.progress_label.config(text="جاري إرسال النص..."))
                    window.after(0, lambda: progress_var.set(50))

                    # إرسال النص
                    await websocket.send(json.dumps({
                        "type": "text",
                        "text": text
                    }))

                    # استلام التأكيد مع مهلة زمنية
                    while True:
                        final_resp_raw = await asyncio.wait_for(websocket.recv(), timeout=15)
                        final_resp = json.loads(final_resp_raw)
                        if final_resp.get("status") == "saving":
                            continue
                        if final_resp.get("status") == "saved":
                            logger.info("تم إرسال النص بنجاح.")
                            # إضافة للسجل كـ نص مرسل
                            self.root.after(0, lambda: self.add_to_history(f"نص: {text[:30]}...", "", device_name=device_name or ip, direction="sent"))

                            window.after(0, lambda: progress_var.set(100))
                            window.after(0, lambda: self._show_inline_message(window, "✅ تم إرسال النص بنجاح"))
                            break
                        if final_resp.get("status") == "error":
                            raise Exception(final_resp.get("message", "خطأ في الهاتف"))

            except Exception as e:
                logger.error(f"خطأ في إرسال النص: {e}")
                window.after(0, lambda: self._show_inline_message(window, f"❌ فشل الإرسال: {str(e)[:40]}", "#EF4444"))

        asyncio.run(send_text_task())

    def setup_tray(self):
        menu = (
            item('فتح الواجهة', self.show_window, default=True),
            item('إيقاف التشغيل', self.quit_app)
        )
        self.tray_icon = pystray.Icon("wameed", self.tray_image, APP_NAME, menu)
        threading.Thread(target=self.tray_icon.run, daemon=True).start()

    def browse_folder(self):
        path = filedialog.askdirectory(initialdir=state["save_dir"])
        if path:
            logger.info(f"تغيير مجلد الحفظ إلى: {path}")
            state["save_dir"] = path
            self.path_var.set(path)
            save_config()

    def toggle_auto_open(self):
        state["auto_open"] = self.auto_open_var.get()
        logger.info(f"⚙️ فتح الملفات تلقائياً: {state['auto_open']}")
        save_config()

    def toggle_auto_open_folder(self):
        state["auto_open_folder"] = self.auto_open_folder_var.get()
        logger.info(f"⚙️ فتح المجلد تلقائياً: {state['auto_open_folder']}")
        save_config()

    def refresh_devices_list(self):
        self.devices_list.delete(0, tk.END)
        for d in state["trusted_devices"]:
            self.devices_list.insert(tk.END, f"{d['name']} ({d['id'][:8]}...)")

    def remove_device(self):
        selection = self.devices_list.curselection()
        if selection:
            idx = selection[0]
            device_name = state["trusted_devices"][idx].get("name")
            logger.info(f"حذف الجهاز الموثوق: {device_name}")
            del state["trusted_devices"][idx]
            save_config()
            self.refresh_devices_list()

    def add_to_history(self, filename, path, device_name=None, direction="received"):
        """إضافة ملف للسجل مع اسم الجهاز وتحديد الاتجاه (مرسل/مستلم)"""
        entry = {
            "filename": filename,
            "path": path,
            "time": datetime.now().strftime("%Y-%m-%d %H:%M"),
            "device": device_name or (connected_device.get("name") if connected_device else None),
            "direction": direction
        }
        state["history"].append(entry)
        if len(state["history"]) > 100: state["history"].pop(0)
        save_config()
        self.root.after(0, self._refresh_recent)
        self.root.after(0, self._build_history)

    def update_device_history(self, device_id, device_name):
        """تحديث سجل الأجهزة المتصلة"""
        global last_connection_time
        last_connection_time = datetime.now()

        # البحث عن الجهاز في السجل
        found = False
        for d in state.get("device_history", []):
            if d.get("id") == device_id:
                d["last_connected"] = datetime.now().strftime("%Y-%m-%d %H:%M")
                d["name"] = device_name
                found = True
                break

        if not found:
            if "device_history" not in state:
                state["device_history"] = []
            state["device_history"].append({
                "id": device_id,
                "name": device_name,
                "last_connected": datetime.now().strftime("%Y-%m-%d %H:%M")
            })

        save_config()
        # تحديث عرض الأجهزة
        self.root.after(0, self._build_devices)

    def show_window(self):
        self.root.after(0, self.root.deiconify)
        self.root.after(0, self.root.focus_force)

    def hide_window(self):
        self.root.withdraw()

    def show_pairing_dialog(self, device_name):
        """نافذة اقتران محسّنة - أيقونة جهاز + عداد تنازلي + صوت تنبيه"""
        result = {"approved": False}

        # صوت تنبيه عند ظهور طلب الاقتران
        try:
            import winsound
            winsound.MessageBeep(winsound.MB_ICONEXCLAMATION)
        except Exception:
            pass

        dialog = tk.Toplevel(self.root)
        dialog.title(t("pairing_req"))
        dialog.geometry("460x300")
        dialog.configure(bg="white")
        dialog.transient(self.root)
        dialog.grab_set()
        dialog.attributes('-topmost', True)
        dialog.resizable(False, False)

        # Header بتدرج لوني
        header = tk.Frame(dialog, bg="#2E7D32", height=65)
        header.pack(fill="x")
        header.pack_propagate(False)
        tk.Label(header, text="⚡ " + t("pairing_req"), bg="#2E7D32", fg="white",
                 font=(FONT_AR, 15, "bold")).pack(pady=18)

        # محتوى — أيقونة الجهاز واسمه
        content = tk.Frame(dialog, bg="white")
        content.pack(fill="both", expand=True, padx=24, pady=16)

        # صف الجهاز مع أيقونة
        device_row = tk.Frame(content, bg="#F0FFF4", pady=12, padx=16)
        device_row.pack(fill="x", pady=(0, 12))

        tk.Label(device_row, text="📱", font=(FONT_AR, 24), bg="#F0FFF4").pack(
            side="right" if LANG=="ar" else "left", padx=(0, 12))
        device_info = tk.Frame(device_row, bg="#F0FFF4")
        device_info.pack(side="right" if LANG=="ar" else "left", fill="x", expand=True)
        tk.Label(device_info, text=device_name, bg="#F0FFF4",
                font=(FONT_AR, fs(13), "bold"), fg="#166534").pack(
                    anchor="e" if LANG=="ar" else "w")
        tk.Label(device_info, text="يطلب الإذن بالاتصال", bg="#F0FFF4",
                font=(FONT_AR, fs(9)), fg="#4B5563").pack(
                    anchor="e" if LANG=="ar" else "w")

        tk.Label(content, text=t("pairing_msg").format(name=device_name),
                 bg="white", font=(FONT_AR, fs(10)), fg="#475569", wraplength=400).pack(pady=(0, 4))

        # عداد تنازلي (30 ثانية) — يُرفض تلقائياً
        countdown_var = tk.IntVar(value=30)
        countdown_label = tk.Label(content, text="⏱ ينتهي خلال 30 ثانية",
                                  bg="white", fg="#9CA3AF", font=(FONT_AR, fs(8)))
        countdown_label.pack()

        def tick():
            if not dialog.winfo_exists(): return
            val = countdown_var.get() - 1
            countdown_var.set(val)
            if val <= 0:
                result["approved"] = False
                dialog.destroy()
                return
            countdown_label.config(text=f"⏱ ينتهي خلال {val} ثانية")
            if val <= 10:
                countdown_label.config(fg="#EF4444")
            dialog.after(1000, tick)

        dialog.after(1000, tick)

        # أزرار
        btn_frame = tk.Frame(dialog, bg="white")
        btn_frame.pack(fill="x", padx=24, pady=(0, 16))

        def approve():
            result["approved"] = True
            dialog.destroy()

        def reject():
            result["approved"] = False
            dialog.destroy()

        tk.Button(btn_frame, text=f"✗ {t('reject')}", command=reject,
                  bg="#FEE2E2", fg="#991B1B", font=(FONT_AR, fs(10), "bold"),
                  bd=0, pady=10, padx=24, cursor="hand2",
                  activebackground="#FECACA").pack(side="left", padx=5, fill="x", expand=True)

        tk.Button(btn_frame, text=f"✓ {t('accept')}", command=approve,
                  bg="#2E7D32", fg="white", font=(FONT_AR, fs(10), "bold"),
                  bd=0, pady=10, padx=24, cursor="hand2",
                  activebackground="#1B5E20", activeforeground="white").pack(side="right", padx=5, fill="x", expand=True)

        # توسيط النافذة
        dialog.update_idletasks()
        x = (dialog.winfo_screenwidth() // 2) - (460 // 2)
        y = (dialog.winfo_screenheight() // 2) - (300 // 2)
        dialog.geometry(f"460x300+{x}+{y}")

        self.root.wait_window(dialog)
        return result["approved"]

    def quit_app(self):
        state["running"] = False
        self.tray_icon.stop()
        self.root.quit()
        os._exit(0)

    def run(self):
        self.root.mainloop()

# ======================== Receiver Logic ========================
async def handle_client(websocket, path=None):
    global connected_device, last_connection_time, active_connections
    client_ip = websocket.remote_address[0]
    # تتبع عدد الاتصالات النشطة من هذا الـ IP
    active_connections[client_ip] = active_connections.get(client_ip, 0) + 1
    logger.info(f"اتصال وارد من IP: {client_ip} (اتصالات نشطة: {active_connections[client_ip]})")
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                mtype = data.get("type")

                if mtype == "hello":
                    device_id = data.get("device_id")
                    device_name = data.get("device")
                    logger.info(f"طلب مصافحة من جهاز: {device_name} ({device_id})")
                    is_trusted = any(d["id"] == device_id for d in state["trusted_devices"])

                    if is_trusted:
                        logger.info(f"تم قبول الاتصال تلقائياً: {device_name} (جهاز موثوق)")
                        await websocket.send(json.dumps({"status": "paired"}))
                        # تحديث الجهاز المتصل والسجل
                        connected_device = {
                            "id": device_id,
                            "name": device_name,
                            "ip": client_ip, # Store IP for automatic use in send dialog
                            "connected_at": datetime.now()
                        }

                        # تحديث IP الهدف ليكون الجهاز الذي اتصل بنا حالياً
                        state["target_ip"] = client_ip
                        save_config()
                        if hasattr(app, 'home_ip_var'):
                            app.root.after(0, lambda: app.home_ip_var.set(client_ip))

                        app.root.after(0, lambda: app.update_device_history(device_id, device_name))
                        app.root.after(0, app._update_status_display)
                    else:
                        logger.info(f"جهاز غير معروف '{device_name}' يطلب الاقتران. بانتظار رد المستخدم...")
                        await websocket.send(json.dumps({"status": "pairing_required"}))

                        loop = asyncio.get_event_loop()
                        approved = await loop.run_in_executor(
                            None,
                            lambda: app.show_pairing_dialog(device_name)
                        )

                        if approved:
                            logger.info(f"تم قبول اقتران الجهاز الجديد: {device_name}")
                            state["trusted_devices"].append({"id": device_id, "name": device_name})
                            save_config()
                            app.root.after(0, app.refresh_devices_list)
                            # تحديث الجهاز المتصل والسجل
                            connected_device = {
                                "id": device_id,
                                "name": device_name,
                                "ip": client_ip, # Store IP for automatic use in send dialog
                                "connected_at": datetime.now()
                            }

                            # تحديث IP الهدف
                            state["target_ip"] = client_ip
                            save_config()
                            if hasattr(app, 'home_ip_var'):
                                app.root.after(0, lambda: app.home_ip_var.set(client_ip))

                            app.root.after(0, lambda: app.update_device_history(device_id, device_name))
                            app.root.after(0, app._update_status_display)
                            await websocket.send(json.dumps({"status": "paired"}))
                        else:
                            logger.warning(f"تم رفض اقتران الجهاز: {device_name}")
                            await websocket.send(json.dumps({"status": "rejected", "message": "تم رفض الاقتران من المستخدم"}))

                elif mtype == "text":
                    text = data.get("text")
                    logger.info(f"استلام نص من الهاتف (الطول: {len(text)} حرف)")
                    app.root.clipboard_clear()
                    app.root.clipboard_append(text)
                    # ⚡ إرسال saved فوراً قبل الأعمال الثانوية
                    await websocket.send(json.dumps({"status": "saved"}))
                    device_name = connected_device.get("name") if connected_device else "جهاز غير معروف"
                    app.add_to_history(f"نص: {text[:30]}...", "", device_name)
                    show_notification("Wameed - نص جديد", f"تم نسخ النص إلى الحافظة تلقائياً")
                    try:
                        import winsound
                        winsound.MessageBeep(winsound.MB_OK)
                    except Exception: pass

                elif mtype == "url":
                    url = data.get("url")
                    logger.info(f"استلام رابط من الهاتف: {url}")
                    # ⚡ إرسال saved فوراً قبل الأعمال الثانوية
                    await websocket.send(json.dumps({"status": "saved"}))
                    device_name = connected_device.get("name") if connected_device else "جهاز غير معروف"
                    app.add_to_history(f"رابط: {url[:40]}", "", device_name)
                    if state["auto_open"]: webbrowser.open(url)

                elif mtype == "file_meta":
                    filename = data.get("filename")
                    chunks = data.get("chunks")
                    fsize = data.get("size", 0)
                    display_mode = data.get("display_mode", "both")
                    filepath = os.path.join(state["save_dir"], filename)

                    logger.info(f"بدء استقبال ملف: {filename} ({fsize} bytes) | عدد الأجزاء: {chunks}")
                    start_time = time.time()

                    base, ext = os.path.splitext(filepath)
                    c = 1
                    while os.path.exists(filepath):
                        filepath = f"{base}_{c}{ext}"; c += 1

                    with open(filepath, 'wb') as f:
                        for i in range(chunks):
                            chunk = await websocket.recv()
                            f.write(chunk)

                    elapsed = time.time() - start_time
                    logger.info(f"تم استقبال الملف بنجاح: {filename} في {elapsed:.2f} ثانية")

                    # ⚡ إرسال saved فوراً للهاتف قبل أي عمل آخر (لتقليل التأخير)
                    await websocket.send(json.dumps({"status": "saved", "path": filepath}))

                    # الأعمال الثانوية بعد الرد (لا تؤخر الهاتف)
                    device_name = connected_device.get("name") if connected_device else "جهاز غير معروف"
                    app.add_to_history(filename, filepath, device_name)

                    show_notification("Wameed - ملف جديد", f"تم استلام {filename} بنجاح.")
                    try:
                        import winsound
                        winsound.MessageBeep(winsound.MB_OK)
                    except Exception: pass

                    # تنفيذ تعليمات العرض حسب إعدادات المستخدم
                    try:
                        if state.get("auto_open"):
                            os.startfile(filepath)
                        if state.get("auto_open_folder"):
                            os.startfile(os.path.dirname(filepath))
                    except Exception as e:
                        logger.error(f"خطأ أثناء فتح الملف/المجلد: {e}")

            except Exception as e:
                logger.exception("حدث خطأ أثناء معالجة رسالة العميل")
    except Exception as e:
        logger.debug(f"انتهى اتصال WebSocket مع ({client_ip}): {e}")

    # تقليل عدّاد الاتصالات النشطة
    active_connections[client_ip] = max(0, active_connections.get(client_ip, 1) - 1)
    remaining = active_connections.get(client_ip, 0)
    logger.debug(f"إغلاق اتصال من {client_ip} (متبقي: {remaining})")

    # مسح حالة الجهاز فقط إذا لم يتبقَّ أي اتصال نشط من نفس الـ IP
    if remaining == 0 and connected_device and connected_device.get("ip") == client_ip:
        logger.info(f"📴 تم قطع جميع الاتصالات مع {connected_device.get('name', '?')} ({client_ip})")
        last_connection_time = datetime.now()
        connected_device = None
        if hasattr(app, 'root'):
            app.root.after(0, app._update_status_display)

def show_notification(title, message):
    """إرسال تنبيه ويندوز حديث (Toast) يظهر في مركز الإشعارات"""
    try:
        # سكربت PowerShell لإنشاء تنبيه Toast حديث
        # تم استخدام نغمة 'Default' ويمكن تغييرها لـ 'IM' أو 'Mail' لتكون أقل حدة
        ps_script = f"""
        $title = "{title}"
        $message = "{message}"
        [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] > $null
        $template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
        $toastXml = [xml]$template.GetXml()
        $toastXml.GetElementsByTagName("text")[0].AppendChild($toastXml.CreateTextNode($title)) > $null
        $toastXml.GetElementsByTagName("text")[1].AppendChild($toastXml.CreateTextNode($message)) > $null

        # إضافة صوت هادئ
        $audio = $toastXml.CreateElement("audio")
        $audio.SetAttribute("src", "ms-winsoundevent:Notification.Default")
        $toastXml.SelectSingleNode("/toast").AppendChild($audio) > $null

        $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
        $xml.LoadXml($toastXml.OuterXml)
        $toast = [Windows.UI.Notifications.ToastNotification]::new($xml)
        [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("Wameed").Show($toast)
        """
        subprocess.run(["powershell", "-Command", ps_script], capture_output=True, check=False)
    except Exception as e:
        logger.error(f"Notification Error: {e}")

async def run_ws_server():
    async with serve(handle_client, "0.0.0.0", PORT_WS):
        await asyncio.Future()

def start_ws_thread():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    loop.run_until_complete(run_ws_server())

def udp_broadcast():
    logger.info(f"بدء تشغيل مستجيب Discovery على منفذ UDP: {PORT_UDP}")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('', PORT_UDP))
        sock.settimeout(1.0)
        while state["running"]:
            try:
                data, addr = sock.recvfrom(1024)
                try:
                    req = json.loads(data.decode('utf-8'))
                    if req.get("service") == "wameed_pc":
                        continue # Ignore our own broadcast
                except: pass

                # رد تلقائي على طلبات البحث من الهواتف
                msg = json.dumps({
                    "service": "wameed_pc",
                    "name": socket.gethostname(),
                    "ip": get_local_ip(),
                    "port": PORT_WS,
                    "version": VERSION
                })
                sock.sendto(msg.encode('utf-8'), addr)
            except socket.timeout:
                continue
            except Exception as e:
                logger.debug(f"UDP Recv Error: {e}")
    except Exception as e:
        logger.error(f"فشل تشغيل مستجيب UDP: {e}")

# ======================== Main ========================
if __name__ == "__main__":
    logger.info("="*60)
    logger.info(f"--- بدء تشغيل تطبيق وميض (Wameed) الإصدار {VERSION} ---")
    logger.info(f"عنوان IP المستخدم: {get_local_ip()}")
    logger.info(f"منفذ الاستقبال (WS): {PORT_WS}")
    logger.info(f"سجل العمليات: {LOG_FILE}")
    logger.info("="*60)

    try:
        app = WameedApp()

        # تشغيل السيرفرات في الخلفية
        threading.Thread(target=start_ws_thread, daemon=True).start()
        threading.Thread(target=udp_broadcast, daemon=True).start()

        # تشغيل واجهة المستخدم
        app.run()
    except Exception as e:
        logger.exception("حدث خطأ فادح أدى لتوقف التطبيق")
