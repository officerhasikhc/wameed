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
        "status_ready": "⏳ جاهز للاستقبال",
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
        "discovered_devices": "📱 الأجهزة المكتشفة",
        "select_device": "اختر جهازاً للاتصال",
        "no_devices_found": "لم يتم العثور على أجهزة 📵"
    },
    "en": {
        "app_header": "Wameed",
        "status_ready": "⏳ Ready to receive",
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
        "discovered_devices": "📱 Discovered Devices",
        "select_device": "Select a device to connect",
        "no_devices_found": "No devices found 📵"
    }
}

def t(key):
    return translations.get(LANG, translations["ar"]).get(key, key)

# ======================== Logging ========================
_LOG_DIR = os.path.join(os.path.expanduser("~"), ".wameed")
os.makedirs(_LOG_DIR, exist_ok=True)
LOG_FILE = os.path.join(_LOG_DIR, "wameed.log")

logger = logging.getLogger("wameed")
logger.setLevel(logging.DEBUG)
_fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(message)s")

try:
    _fh = logging.handlers.RotatingFileHandler(LOG_FILE, maxBytes=1_000_000, backupCount=3, encoding="utf-8")
    _fh.setFormatter(_fmt)
    logger.addHandler(_fh)
except Exception:
    pass

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
CONFIG_FILE = os.path.join(_LOG_DIR, "config.json")
state = {
    "trusted_devices": [],
    "device_history": [],  # سجل الأجهزة المتصلة مع وقت الاتصال
    "history": [],
    "save_dir": os.path.join(os.path.expanduser("~"), "Downloads", "Wameed"),
    "auto_open": True,
    "lang": "ar",
    "running": True
}

# متغيرات عامة للتتبع
connected_device = None  # الجهاز المتصل حالياً {"id", "name", "ip", "connected_at"}
last_connection_time = None  # وقت آخر اتصال
connection_check_thread = None  # Thread لفحص الاتصال الدوري

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
                                        font=("Segoe UI", 24))
        self.lightning_label.pack(side="right" if LANG=="ar" else "left", padx=5)

        # نص "وميض"
        tk.Label(header_frame, text=t("app_header"), bg="#2E7D32", fg="white",
                 font=("Segoe UI", 22, "bold")).pack(side="right" if LANG=="ar" else "left", padx=5)

        # --- Style ---
        style = ttk.Style()
        style.configure("TNotebook", background="#F8FAFC")
        style.configure("TNotebook.Tab", font=("Segoe UI", 10), padding=[12, 4])

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

        # بدء فحص حالة الاتصال الدوري
        self._start_connection_monitor()

        # --- Bottom Bar ---
        btm = tk.Frame(self.root, bg="#F1F5F9", height=35)
        btm.pack(fill="x", side="bottom")
        tk.Label(btm, text=f"{t('version')} {VERSION} | {get_local_ip()}",
                 bg="#F1F5F9", fg="#64748B", font=("Segoe UI", 9)).pack(pady=6)

    def _build_home(self):
        for w in self.tab_home.winfo_children(): w.destroy()

        # Status Card - الحالة الذكية
        card = tk.Frame(self.tab_home, bg="#F8FAFC", highlightthickness=1, highlightbackground="#E2E8F0")
        card.pack(fill="x", padx=16, pady=16)

        inner = tk.Frame(card, bg="#F8FAFC")
        inner.pack(padx=16, pady=16, fill="x")

        self.status_frame = tk.Frame(inner, bg="#F8FAFC")
        self.status_frame.pack(fill="x")

        self._update_status_display()

        # Search & Connect Frame
        search_frame = tk.Frame(self.tab_home, bg="white")
        search_frame.pack(fill="x", padx=16, pady=(0, 10))

        # زر البحث عن أجهزة
        search_btn = tk.Button(search_frame, text=t("btn_search_devices"), bg="#3B82F6", fg="white",
                  font=("Segoe UI", 10, "bold"), bd=0, pady=10, cursor="hand2",
                  activebackground="#2563EB", activeforeground="white",
                  command=self._show_discovery_dialog)
        search_btn.pack(fill="x", pady=3)

        # Action Buttons
        btn_frame = tk.Frame(self.tab_home, bg="white")
        btn_frame.pack(fill="x", padx=16, pady=5)

        tk.Button(btn_frame, text=t("btn_send"), bg="#2E7D32", fg="white",
                  font=("Segoe UI", 11, "bold"), bd=0, pady=12, cursor="hand2",
                  activebackground="#1B5E20", activeforeground="white",
                  command=self._show_send_dialog).pack(fill="x", pady=5)

        tk.Button(btn_frame, text=t("btn_open_folder"), bg="#E2E8F0", fg="#1E293B",
                  font=("Segoe UI", 10), bd=0, pady=8, cursor="hand2",
                  command=lambda: os.startfile(state["save_dir"])).pack(fill="x", pady=5)

        # Recent Files
        rec_label_frame = tk.Frame(self.tab_home, bg="white")
        rec_label_frame.pack(fill="x", padx=18, pady=(15, 5))
        tk.Label(rec_label_frame, text=t("recent_files"), bg="white",
                 font=("Segoe UI", 10, "bold"), fg="#475569").pack(side="right" if LANG=="ar" else "left")

        self.recent_container = tk.Frame(self.tab_home, bg="white")
        self.recent_container.pack(fill="both", expand=True, padx=18)
        self._refresh_recent()

    def _refresh_recent(self):
        for w in self.recent_container.winfo_children(): w.destroy()
        recent = state["history"][-4:]
        if not recent:
            tk.Label(self.recent_container, text="-", bg="white", fg="#94A3B8").pack()
            return
        for item in reversed(recent):
            f = tk.Frame(self.recent_container, bg="#F8FAFC", pady=4)
            f.pack(fill="x", pady=2)
            tk.Label(f, text=f"• {item['filename']}", bg="#F8FAFC", font=("Segoe UI", 9),
                     anchor="e" if LANG=="ar" else "w").pack(fill="x", padx=5)

    def _update_status_display(self):
        """تحديث عرض الحالة الذكية"""
        for w in self.status_frame.winfo_children(): w.destroy()

        global connected_device, last_connection_time

        if connected_device and connected_device.get("name"):
            # متصل بجهاز
            device_name = connected_device.get("name", "جهاز غير معروف")
            status_text = t("status_connected_to").format(name=device_name)
            dot_color = "#22C55E"  # أخضر
        elif last_connection_time:
            # كان متصلاً سابقاً (خلال 30 دقيقة)
            elapsed = (datetime.now() - last_connection_time).total_seconds()
            if elapsed < 1800:  # أقل من 30 دقيقة
                status_text = t("status_ready") + f" (آخر اتصال قبل {int(elapsed/60)} دقيقة)"
            else:
                status_text = t("status_ready")
            dot_color = "#FBBF24"  # أصفر
        else:
            status_text = t("status_ready")
            dot_color = "#9CA3AF"  # رمادي

        self.status_dot = tk.Label(self.status_frame, text="●", fg=dot_color, bg="#F8FAFC", font=("Segoe UI", 18))
        self.status_dot.pack(side="right" if LANG=="ar" else "left", padx=8)

        self.status_label = tk.Label(self.status_frame, text=status_text,
                                     bg="#F8FAFC", font=("Segoe UI", 12, "bold"), fg="#1E293B")
        self.status_label.pack(side="right" if LANG=="ar" else "left")

    def _start_connection_monitor(self):
        """بدء مراقبة الاتصال الدورية"""
        def monitor():
            while state["running"]:
                try:
                    # تحديث العرض كل 10 ثوانٍ
                    if hasattr(self, 'status_frame'):
                        self.root.after(0, self._update_status_display)
                except Exception as e:
                    logger.error(f"Status monitor error: {e}")
                time.sleep(10)

        threading.Thread(target=monitor, daemon=True).start()

    def _show_discovery_dialog(self):
        """نافذة البحث عن الأجهزة والاتصال السريع"""
        dialog = tk.Toplevel(self.root)
        dialog.title(t("discovered_devices"))
        dialog.geometry("400x350")
        dialog.configure(bg="white")
        dialog.transient(self.root)
        dialog.grab_set()

        # Header
        tk.Label(dialog, text=t("discovered_devices"), font=("Segoe UI", 14, "bold"),
                bg="white", fg="#2E7D32").pack(pady=15)

        # Status label
        status_label = tk.Label(dialog, text=t("status_searching"), font=("Segoe UI", 10),
                               bg="white", fg="#3B82F6")
        status_label.pack()

        # Device list frame
        list_frame = tk.Frame(dialog, bg="white", bd=1, relief="solid")
        list_frame.pack(fill="both", expand=True, padx=20, pady=10)

        devices_listbox = tk.Listbox(list_frame, font=("Segoe UI", 11), bd=0, selectmode="single")
        devices_listbox.pack(fill="both", expand=True, padx=5, pady=5)

        discovered_devices = []

        def on_device_select(event):
            selection = devices_listbox.curselection()
            if selection and discovered_devices:
                idx = selection[0]
                if idx < len(discovered_devices):
                    device = discovered_devices[idx]
                    dialog.destroy()
                    # فتح نافذة الإرسال مع الجهاز المحدد
                    self._show_send_dialog(device_ip=device.get("ip"), device_name=device.get("name"))

        devices_listbox.bind("<<ListboxSelect>>", on_device_select)

        def search_devices():
            nonlocal discovered_devices
            discovered_devices.clear()
            devices_listbox.delete(0, tk.END)

            # البحث عن الأجهزة
            found = self._broadcast_discovery_multi(timeout=3.0)

            if found:
                discovered_devices.extend(found)
                for device in found:
                    name = device.get("name", "جهاز غير معروف")
                    ip = device.get("ip", "")
                    display = f"📱 {name}"
                    devices_listbox.insert(tk.END, display)
                status_label.config(text=f"✅ تم العثور على {len(found)} جهاز", fg="#22C55E")
            else:
                status_label.config(text=t("no_devices_found"), fg="#EF4444")

        # زر البحث
        tk.Button(dialog, text="🔍 " + t("btn_search_devices"), command=search_devices,
                 bg="#3B82F6", fg="white", font=("Segoe UI", 10, "bold"),
                 bd=0, pady=8, cursor="hand2").pack(fill="x", padx=20, pady=5)

        # إغلاق
        tk.Button(dialog, text="✕ إغلاق", command=dialog.destroy,
                 bg="#E2E8F0", fg="#1E293B", font=("Segoe UI", 9),
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
                    if resp.get("type") == "discovery_pong":
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
        tk.Label(header, text=t("tab_devices"), font=("Segoe UI", 14, "bold"),
                bg="#F8FAFC", fg="#2E7D32").pack(side="right" if LANG=="ar" else "left")

        if not state["trusted_devices"] and not state.get("device_history", []):
            # لا توجد أجهزة
            empty_frame = tk.Frame(self.tab_devices, bg="white")
            empty_frame.pack(expand=True)
            tk.Label(empty_frame, text=t("no_devices"), font=("Segoe UI", 12),
                    bg="white", fg="#94A3B8").pack(pady=50)

            tk.Button(empty_frame, text="🔍 " + t("btn_search_devices"),
                     command=self._show_discovery_dialog, bg="#3B82F6", fg="white",
                     font=("Segoe UI", 10, "bold"), bd=0, pady=8, padx=20).pack()
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

            tk.Label(name_frame, text=icon, bg="white", font=("Segoe UI", 16)).pack(side="right" if LANG=="ar" else "left", padx=5)

            name_text = device["name"]
            if is_connected:
                name_text += f"  {t('connected_now')}"

            tk.Label(name_frame, text=name_text, bg="white", font=("Segoe UI", 11, "bold"),
                    fg=color, anchor="e" if LANG=="ar" else "w").pack(side="right" if LANG=="ar" else "left", fill="x", expand=True)

            # وقت آخر اتصال
            if device.get("last_seen") and not is_connected:
                time_text = t("last_connected").format(time=device["last_seen"])
                tk.Label(row, text=time_text, bg="white", fg="#94A3B8",
                        font=("Segoe UI", 9)).pack(anchor="e" if LANG=="ar" else "w")

            # زر الاتصال السريع (للأجهزة الموثوقة)
            if device["trusted"]:
                def make_connect_handler(dev_id, dev_name):
                    return lambda: self._quick_connect(dev_id, dev_name)

                btn_text = "⚡ " + t("btn_quick_connect") if is_connected else "🔗 " + t("btn_quick_connect")
                btn_color = "#10B981" if is_connected else "#3B82F6"

                tk.Button(row, text=btn_text, command=make_connect_handler(device_id, device["name"]),
                         bg=btn_color, fg="white", font=("Segoe UI", 9),
                         bd=0, pady=4, padx=12, cursor="hand2").pack(anchor="e" if LANG=="ar" else "w", pady=5)

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

    def _build_history(self):
        """بناء تبويب السجل ببطاقات ملفات محسنة"""
        for w in self.tab_history.winfo_children(): w.destroy()

        # Header
        header = tk.Frame(self.tab_history, bg="#F8FAFC", padx=20, pady=15)
        header.pack(fill="x")
        tk.Label(header, text=t("tab_history"), font=("Segoe UI", 14, "bold"),
                bg="#F8FAFC", fg="#2E7D32").pack(side="right" if LANG=="ar" else "left")

        if not state["history"]:
            empty_frame = tk.Frame(self.tab_history, bg="white")
            empty_frame.pack(expand=True)
            tk.Label(empty_frame, text=t("no_history"), bg="white", fg="#94A3B8",
                    font=("Segoe UI", 12)).pack(pady=50)
            return

        canvas = tk.Canvas(self.tab_history, bg="white", highlightthickness=0)
        scroll = ttk.Scrollbar(self.tab_history, orient="vertical", command=canvas.yview)
        frame = tk.Frame(canvas, bg="white")

        frame.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.create_window((0,0), window=frame, anchor="nw", width=460)
        canvas.configure(yscrollcommand=scroll.set)

        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")

        def get_file_icon(filename):
            """إرجاع أيقونة مناسبة لنوع الملف"""
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
            card = tk.Frame(frame, bg="#F8FAFC", padx=15, pady=12,
                           highlightthickness=1, highlightbackground="#E2E8F0")
            card.pack(fill="x", padx=15, pady=5)

            filename = entry.get("filename", "")
            time_str = entry.get("time", "")
            filepath = entry.get("path", "")
            device_name = entry.get("device", "")

            # الصف العلوي: أيقونة + اسم الملف
            top_row = tk.Frame(card, bg="#F8FAFC")
            top_row.pack(fill="x")

            icon = get_file_icon(filename)
            tk.Label(top_row, text=icon, bg="#F8FAFC", font=("Segoe UI", 20)).pack(
                side="right" if LANG=="ar" else "left", padx=(0, 10))

            # اسم الملف (مختصر إذا طويل)
            display_name = filename if len(filename) < 35 else filename[:32] + "..."
            tk.Label(top_row, text=display_name, bg="#F8FAFC", font=("Segoe UI", 11, "bold"),
                    anchor="e" if LANG=="ar" else "w").pack(
                side="right" if LANG=="ar" else "left", fill="x", expand=True)

            # الصف السفلي: التفاصيل والأزرار
            bottom_row = tk.Frame(card, bg="#F8FAFC")
            bottom_row.pack(fill="x", pady=(8, 0))

            # وقت الاستلام
            info_text = time_str
            if device_name:
                info_text += f"  •  {t('received_from').format(device=device_name)}"

            tk.Label(bottom_row, text=info_text, bg="#F8FAFC", fg="#64748B",
                    font=("Segoe UI", 9)).pack(side="right" if LANG=="ar" else "left")

            # أزرار الإجراءات
            btn_frame = tk.Frame(bottom_row, bg="#F8FAFC")
            btn_frame.pack(side="left" if LANG=="ar" else "right")

            if filepath and os.path.exists(filepath):
                # زر فتح الملف
                tk.Button(btn_frame, text=t("open_file"), command=lambda p=filepath: os.startfile(p),
                         bg="#10B981", fg="white", font=("Segoe UI", 8),
                         bd=0, pady=3, padx=10, cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=2)

                # زر فتح المجلد
                folder = os.path.dirname(filepath)
                tk.Button(btn_frame, text=t("open_folder"), command=lambda f=folder: os.startfile(f),
                         bg="#64748B", fg="white", font=("Segoe UI", 8),
                         bd=0, pady=3, padx=10, cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=2)

    def _build_settings(self):
        for w in self.tab_settings.winfo_children(): w.destroy()

        container = tk.Frame(self.tab_settings, bg="white", padx=20, pady=20)
        container.pack(fill="both", expand=True)

        # Language
        tk.Label(container, text="اللغة / Language", bg="white", font=("Segoe UI", 10, "bold")).pack(anchor="e" if LANG=="ar" else "w")
        lang_frame = tk.Frame(container, bg="white")
        lang_frame.pack(fill="x", pady=5)

        def set_lang(l):
            global LANG
            LANG = l
            save_config()
            self.setup_ui()

        tk.Button(lang_frame, text="العربية", command=lambda: set_lang("ar"), width=10).pack(side="right" if LANG=="ar" else "left", padx=5)
        tk.Button(lang_frame, text="English", command=lambda: set_lang("en"), width=10).pack(side="right" if LANG=="ar" else "left", padx=5)

        ttk.Separator(container).pack(fill="x", pady=15)

        # Save Dir
        tk.Label(container, text=t("save_dir_label"), bg="white", font=("Segoe UI", 10, "bold")).pack(anchor="e" if LANG=="ar" else "w")
        path_frame = tk.Frame(container, bg="white")
        path_frame.pack(fill="x", pady=5)

        self.path_var = tk.StringVar(value=state["save_dir"])
        tk.Entry(path_frame, textvariable=self.path_var, font=("Segoe UI", 9), bd=1, relief="solid").pack(side="right" if LANG=="ar" else "left", fill="x", expand=True, padx=5)
        tk.Button(path_frame, text="...", command=self.browse_folder).pack(side="right" if LANG=="ar" else "left")

        # Auto Open
        self.auto_open_var = tk.BooleanVar(value=state["auto_open"])
        tk.Checkbutton(container, text=t("auto_open_label"), variable=self.auto_open_var,
                       bg="white", font=("Segoe UI", 10), command=self.toggle_auto_open).pack(anchor="e" if LANG=="ar" else "w", pady=10)

        # Trusted Devices
        tk.Label(container, text=t("trusted_devices"), bg="white", font=("Segoe UI", 10, "bold")).pack(anchor="e" if LANG=="ar" else "w", pady=(10, 2))
        self.devices_list = tk.Listbox(container, height=5, font=("Segoe UI", 9), bd=1, relief="solid")
        self.devices_list.pack(fill="x")
        self.refresh_devices_list()

        tk.Button(container, text=t("delete_device"), command=self.remove_device,
                  bg="#FEE2E2", fg="#991B1B", bd=0, pady=5, font=("Segoe UI", 9)).pack(anchor="e" if LANG=="ar" else "w", pady=5)

    def _broadcast_discovery(self, timeout=2.0):
        """يرسل رسالة UDP Broadcast للبحث عن الهواتف التي تشغل تطبيق وميض"""
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(timeout)

            message = json.dumps({
                "type": "discovery_ping",
                "device": socket.gethostname(),
                "port": PORT_WS
            }).encode('utf-8')

            sock.sendto(message, ('<broadcast>', PORT_UDP))

            start_time = time.time()
            while time.time() - start_time < timeout:
                try:
                    data, addr = sock.recvfrom(1024)
                    resp = json.loads(data.decode('utf-8'))
                    if resp.get("type") == "discovery_pong":
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
        """نافذة الإرسال المُحسّنة - أصغر وأبسط"""
        win = tk.Toplevel(self.root)
        win.title(t("btn_send"))
        win.geometry("430x400")  # أصغر
        win.configure(bg="white")
        win.transient(self.root)
        win.grab_set()
        win.resizable(False, False)

        # Header مضغوط
        header_frame = tk.Frame(win, bg="#2E7D32", padx=15, pady=10)
        header_frame.pack(fill="x")

        header_text = t("btn_send")
        if device_name:
            header_text = f"→ {device_name}"
        tk.Label(header_frame, text=header_text, font=("Segoe UI", 12, "bold"),
                bg="#2E7D32", fg="white").pack()

        # تبويبات: ملف | نص
        tabs = ttk.Notebook(win)
        tabs.pack(fill="both", expand=True, padx=15, pady=8)

        tab_file = tk.Frame(tabs, bg="white")
        tab_text = tk.Frame(tabs, bg="white")

        tabs.add(tab_file, text=f"  {t('tab_file')}  ")
        tabs.add(tab_text, text=f"  {t('tab_text')}  ")

        # ========== تبويب الملف ==========
        self.selected_file_path = tk.StringVar(value="")

        # منطقة السحب والإفلات - أيقونة بسيطة ↑
        drop_frame = tk.Frame(tab_file, bg="#F8FAFC", height=120,
                              highlightthickness=2, highlightbackground="#CBD5E1")
        drop_frame.pack(fill="x", padx=10, pady=10)
        drop_frame.pack_propagate(False)

        # أيقونة سهم بسيط ↑
        self.drop_icon_label = tk.Label(drop_frame, text="↑", font=("Segoe UI", 36),
                                        bg="#F8FAFC", fg="#94A3B8")
        self.drop_icon_label.pack(pady=(20, 0))

        self.drop_label = tk.Label(drop_frame, text=t("drop_here"), bg="#F8FAFC",
                                  font=("Segoe UI", 10), fg="#64748B")
        self.drop_label.pack()

        self.drop_sub_label = tk.Label(drop_frame, text=t("or_click_browse"), bg="#F8FAFC",
                                        font=("Segoe UI", 9), fg="#94A3B8")
        self.drop_sub_label.pack()

        # تفعيل السحب والإفلات على النافذة نفسها
        if TKDND_AVAILABLE:
            try:
                win.drop_target_register(DND_FILES)
                win.dnd_bind('<<Drop>>', lambda e: self._on_file_drop_win(e, win))

                def on_drag_enter(e):
                    drop_frame.configure(highlightbackground="#22C55E", bg="#F0FFF4")
                    self.drop_icon_label.config(fg="#22C55E")
                    self.drop_label.config(fg="#22C55E")

                def on_drag_leave(e):
                    drop_frame.configure(highlightbackground="#CBD5E1", bg="#F8FAFC")
                    self.drop_icon_label.config(fg="#94A3B8")
                    self.drop_label.config(fg="#64748B")

                win.dnd_bind('<<DragEnter>>', on_drag_enter)
                win.dnd_bind('<<DragLeave>>', on_drag_leave)
            except Exception as e:
                logger.warning(f"Drag & drop init error: {e}")

        # النقر للاختيار
        def on_drop_click(event=None):
            path = filedialog.askopenfilename()
            if path:
                self.selected_file_path.set(path)
                self.drop_label.config(text=os.path.basename(path), fg="#22C55E",
                                      font=("Segoe UI", 10, "bold"))
                self.drop_sub_label.config(text="✓ جاهز للإرسال", fg="#22C55E")
                self.drop_icon_label.config(text="✓", fg="#22C55E")
                drop_frame.configure(bg="#F0FFF4", highlightbackground="#22C55E")

        drop_frame.bind("<Button-1>", on_drop_click)
        self.drop_label.bind("<Button-1>", on_drop_click)
        self.drop_sub_label.bind("<Button-1>", on_drop_click)
        self.drop_icon_label.bind("<Button-1>", on_drop_click)

        # ========== تبويب النص ==========
        # textarea محسن - يبدو كمحرر نص
        text_container = tk.Frame(tab_text, bg="white", padx=10, pady=10)
        text_container.pack(fill="both", expand=True)

        # إطار يبدو كورقة بيضاء
        text_frame = tk.Frame(text_container, bg="white", bd=1, relief="solid",
                              highlightthickness=1, highlightbackground="#E2E8F0")
        text_frame.pack(fill="both", expand=True)

        text_area = tk.Text(text_frame, font=("Segoe UI", 11), wrap="word",
                           bd=0, relief="flat", padx=8, pady=8,
                           selectbackground="#3B82F6", selectforeground="white")
        text_area.pack(fill="both", expand=True)

        # تغيير مؤشر الماوس عند الدخول
        def on_text_enter(event):
            text_area.config(cursor="xterm")
        def on_text_leave(event):
            text_area.config(cursor="")

        text_area.bind("<Enter>", on_text_enter)
        text_area.bind("<FocusIn>", on_text_enter)

        # أزرار صغيرة أسفل textarea
        btn_frame = tk.Frame(tab_text, bg="white", padx=10)
        btn_frame.pack(fill="x", pady=(0, 8))

        def paste_clip():
            try:
                text = win.clipboard_get()
                text_area.insert(tk.INSERT, text)
            except: pass

        tk.Button(btn_frame, text="📋 لصق", command=paste_clip,
                 bg="#F1F5F9", fg="#374151", font=("Segoe UI", 8),
                 bd=0, pady=4, padx=10, cursor="hand2").pack(side="right" if LANG=="ar" else "left")

        tk.Button(btn_frame, text="✕ مسح", command=lambda: text_area.delete("1.0", tk.END),
                 bg="#FEF2F2", fg="#991B1B", font=("Segoe UI", 8),
                 bd=0, pady=4, padx=10, cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=5)

        # ========== IP الهدف ==========
        ip_frame = tk.Frame(win, bg="white", padx=15, pady=5)
        ip_frame.pack(fill="x")

        self.phone_ip_var = tk.StringVar(value=device_ip if device_ip else "")
        ip_entry = tk.Entry(ip_frame, textvariable=self.phone_ip_var,
                           font=("Segoe UI", 11), bd=1, justify="center",
                           fg="#374151", selectbackground="#3B82F6")
        ip_entry.pack(fill="x")

        tk.Label(ip_frame, text=t("target_ip"), bg="white", fg="#94A3B8",
                font=("Segoe UI", 9)).pack()

        # شريط التقدم مخفي
        progress_var = tk.DoubleVar(value=0)
        progress_bar = ttk.Progressbar(win, orient="horizontal",
                                      mode="determinate", variable=progress_var)
        progress_bar.pack(fill="x", padx=40, pady=5)
        progress_bar.pack_forget()

        # زر الإرسال - أكبر وبارز
        def do_send():
            target_ip = self.phone_ip_var.get().strip()
            if not target_ip:
                messagebox.showerror(t("error"), t("enter_ip_first"))
                return

            current_tab = tabs.index(tabs.select())
            progress_bar.pack(fill="x", padx=40, pady=5)

            def on_success():
                win.after(500, win.destroy)  # إغلاق تلقائي بعد نصف ثانية

            if current_tab == 0:  # ملف
                file_path = self.selected_file_path.get()
                if not file_path or not os.path.exists(file_path):
                    messagebox.showerror(t("error"), t("select_file_first"))
                    progress_bar.pack_forget()
                    return
                threading.Thread(target=self._execute_send,
                               args=(target_ip, file_path, win, progress_var, on_success),
                               daemon=True).start()
            else:  # نص
                text = text_area.get("1.0", tk.END).strip()
                if not text:
                    messagebox.showerror(t("error"), t("enter_text_first"))
                    progress_bar.pack_forget()
                    return
                threading.Thread(target=self._execute_send_text,
                               args=(target_ip, text, win, progress_var, on_success),
                               daemon=True).start()

        send_btn = tk.Button(win, text=t("send_now"), command=do_send,
                            bg="#2E7D32", fg="white", font=("Segoe UI", 13, "bold"),
                            bd=0, pady=12, cursor="hand2", activebackground="#1B5E20")
        send_btn.pack(fill="x", padx=40, pady=15)

    def _on_file_drop_win(self, event, win):
        """معالجة إفلات الملف على النافذة"""
        try:
            file_path = event.data.strip()
            # تنظيف المسار
            if file_path.startswith('{') and file_path.endswith('}'):
                file_path = file_path[1:-1]
            # في حالة ملفات متعددة، نأخذ الأول فقط
            if '{' in file_path:
                file_path = file_path.split('} {')[0].replace('{', '').replace('}', '')

            if os.path.isfile(file_path):
                self.selected_file_path.set(file_path)
                self.drop_label.config(text=os.path.basename(file_path), fg="#22C55E",
                                      font=("Segoe UI", 10, "bold"))
                self.drop_sub_label.config(text="✓ جاهز للإرسال", fg="#22C55E")
                self.drop_icon_label.config(text="✓", fg="#22C55E")
        except Exception as e:
            logger.error(f"Drop error: {e}")

    def _execute_send(self, ip, path, window, progress_var, on_success=None):
        async def send_task():
            try:
                uri = f"ws://{ip}:7789"
                async with websockets.connect(uri) as websocket:
                    filename = os.path.basename(path)
                    filesize = os.path.getsize(path)

                    await websocket.send(json.dumps({
                        "type": "hello",
                        "device": socket.gethostname(),
                        "device_id": "pc_client",
                        "app_version": VERSION
                    }))

                    resp = json.loads(await websocket.recv())
                    if resp.get("status") != "paired":
                        window.after(0, lambda: messagebox.showwarning("تنبيه", "تم رفض الاتصال من الهاتف"))
                        return

                    chunk_size = 512 * 1024
                    total_chunks = (filesize + chunk_size - 1) // chunk_size

                    await websocket.send(json.dumps({
                        "type": "file_meta",
                        "filename": filename,
                        "size": filesize,
                        "chunks": total_chunks
                    }))

                    with open(path, "rb") as f:
                        sent_bytes = 0
                        for i in range(total_chunks):
                            chunk = f.read(chunk_size)
                            await websocket.send(chunk)
                            sent_bytes += len(chunk)
                            pct = (sent_bytes / filesize) * 100
                            window.after(0, lambda p=pct: progress_var.set(p))

                    final_resp = json.loads(await websocket.recv())
                    if final_resp.get("status") == "saved":
                        def success_handler():
                            messagebox.showinfo("نجاح", f"✅ تم إرسال '{filename}' بنجاح")
                            if on_success:
                                on_success()
                            else:
                                window.destroy()
                        window.after(0, success_handler)

            except Exception as e:
                window.after(0, lambda ex=e: messagebox.showerror("خطأ في الإرسال", f"تعذر الاتصال بالهاتف:\n{str(ex)}"))

        asyncio.run(send_task())

    def _execute_send_text(self, ip, text, window, progress_var, on_success=None):
        """إرسال نص للهاتف"""
        async def send_text_task():
            try:
                uri = f"ws://{ip}:7789"
                async with websockets.connect(uri) as websocket:
                    # إرسال hello
                    await websocket.send(json.dumps({
                        "type": "hello",
                        "device": socket.gethostname(),
                        "device_id": "pc_client",
                        "app_version": VERSION
                    }))

                    resp = json.loads(await websocket.recv())
                    if resp.get("status") != "paired":
                        window.after(0, lambda: messagebox.showwarning("تنبيه", "تم رفض الاتصال من الهاتف"))
                        return

                    # إرسال النص
                    await websocket.send(json.dumps({
                        "type": "text",
                        "text": text
                    }))

                    # استلام التأكيد
                    final_resp = json.loads(await websocket.recv())
                    if final_resp.get("status") == "saved":
                        def success_handler():
                            messagebox.showinfo("نجاح", "✅ تم إرسال النص بنجاح")
                            if on_success:
                                on_success()
                            else:
                                window.destroy()
                        window.after(0, success_handler)

            except Exception as e:
                window.after(0, lambda ex=e: messagebox.showerror("خطأ في الإرسال", f"تعذر الاتصال بالهاتف:\n{str(ex)}"))

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
            state["save_dir"] = path
            self.path_var.set(path)
            save_config()

    def toggle_auto_open(self):
        state["auto_open"] = self.auto_open_var.get()
        save_config()

    def refresh_devices_list(self):
        self.devices_list.delete(0, tk.END)
        for d in state["trusted_devices"]:
            self.devices_list.insert(tk.END, f"{d['name']} ({d['id'][:8]}...)")

    def remove_device(self):
        selection = self.devices_list.curselection()
        if selection:
            idx = selection[0]
            del state["trusted_devices"][idx]
            save_config()
            self.refresh_devices_list()

    def add_to_history(self, filename, path, device_name=None):
        """إضافة ملف للسجل مع اسم الجهاز"""
        entry = {
            "filename": filename,
            "path": path,
            "time": datetime.now().strftime("%Y-%m-%d %H:%M"),
            "device": device_name or (connected_device.get("name") if connected_device else None)
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
        """نافذة اقتران مودال مخصصة - تبقى فوق كل النوافذ"""
        result = {"approved": False}

        dialog = tk.Toplevel(self.root)
        dialog.title(t("pairing_req"))
        dialog.geometry("450x250")
        dialog.configure(bg="white")
        dialog.transient(self.root)
        dialog.grab_set()
        dialog.attributes('-topmost', True)

        header = tk.Frame(dialog, bg="#2E7D32", height=60)
        header.pack(fill="x")
        header.pack_propagate(False)
        tk.Label(header, text="⚡ " + t("pairing_req"), bg="#2E7D32", fg="white",
                 font=("Segoe UI", 14, "bold")).pack(pady=15)

        content = tk.Frame(dialog, bg="white")
        content.pack(fill="both", expand=True, padx=20, pady=20)

        tk.Label(content, text=t("pairing_msg").format(name=device_name),
                 bg="white", font=("Segoe UI", 11), wraplength=400).pack(pady=10)

        btn_frame = tk.Frame(dialog, bg="white")
        btn_frame.pack(fill="x", padx=20, pady=15)

        def approve():
            result["approved"] = True
            dialog.destroy()

        def reject():
            result["approved"] = False
            dialog.destroy()

        tk.Button(btn_frame, text=t("reject"), command=reject,
                  bg="#FEE2E2", fg="#991B1B", font=("Segoe UI", 10, "bold"),
                  bd=0, pady=8, padx=20, cursor="hand2").pack(side="left", padx=5)

        tk.Button(btn_frame, text=t("accept"), command=approve,
                  bg="#2E7D32", fg="white", font=("Segoe UI", 10, "bold"),
                  bd=0, pady=8, padx=20, cursor="hand2").pack(side="right", padx=5)

        dialog.update_idletasks()
        x = (dialog.winfo_screenwidth() // 2) - (450 // 2)
        y = (dialog.winfo_screenheight() // 2) - (250 // 2)
        dialog.geometry(f"450x250+{x}+{y}")

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
    try:
        async for message in websocket:
            try:
                data = json.loads(message)
                mtype = data.get("type")

                if mtype == "hello":
                    device_id = data.get("device_id")
                    device_name = data.get("device")
                    is_trusted = any(d["id"] == device_id for d in state["trusted_devices"])

                    if is_trusted:
                        await websocket.send(json.dumps({"status": "paired"}))
                        # تحديث الجهاز المتصل والسجل
                        global connected_device
                        connected_device = {
                            "id": device_id,
                            "name": device_name,
                            "connected_at": datetime.now()
                        }
                        app.root.after(0, lambda: app.update_device_history(device_id, device_name))
                    else:
                        await websocket.send(json.dumps({"status": "pairing_required"}))

                        loop = asyncio.get_event_loop()
                        approved = await loop.run_in_executor(
                            None,
                            lambda: app.show_pairing_dialog(device_name)
                        )

                        if approved:
                            state["trusted_devices"].append({"id": device_id, "name": device_name})
                            save_config()
                            app.root.after(0, app.refresh_devices_list)
                            # تحديث الجهاز المتصل والسجل
                            connected_device = {
                                "id": device_id,
                                "name": device_name,
                                "connected_at": datetime.now()
                            }
                            app.root.after(0, lambda: app.update_device_history(device_id, device_name))
                            await websocket.send(json.dumps({"status": "paired"}))
                        else:
                            await websocket.send(json.dumps({"status": "rejected", "message": "تم رفض الاقتران من المستخدم"}))

                elif mtype == "text":
                    text = data.get("text")
                    app.root.clipboard_clear()
                    app.root.clipboard_append(text)
                    device_name = connected_device.get("name") if connected_device else None
                    app.add_to_history(f"نص: {text[:30]}...", "", device_name)
                    await websocket.send(json.dumps({"status": "saved"}))

                elif mtype == "url":
                    url = data.get("url")
                    device_name = connected_device.get("name") if connected_device else None
                    app.add_to_history(f"رابط: {url[:40]}", "", device_name)
                    await websocket.send(json.dumps({"status": "saved"}))
                    if state["auto_open"]: webbrowser.open(url)

                elif mtype == "file_meta":
                    filename = data.get("filename")
                    chunks = data.get("chunks")
                    display_mode = data.get("display_mode", "both")
                    filepath = os.path.join(state["save_dir"], filename)

                    base, ext = os.path.splitext(filepath)
                    c = 1
                    while os.path.exists(filepath):
                        filepath = f"{base}_{c}{ext}"; c += 1

                    with open(filepath, 'wb') as f:
                        for _ in range(chunks):
                            chunk = await websocket.recv()
                            f.write(chunk)

                    device_name = connected_device.get("name") if connected_device else None
                    app.add_to_history(filename, filepath, device_name)

                    # إرسال تنبيه ويندوز
                    show_notification("Wameed - ملف جديد", f"تم استلام {filename} بنجاح.")

                    await websocket.send(json.dumps({"status": "saved", "path": filepath}))

                    # تنفيذ تعليمات العرض (Instant Open / Windows Notification)
                    if state["auto_open"]:
                        try:
                            if display_mode == "open":
                                os.startfile(filepath)
                            elif display_mode == "path":
                                os.startfile(os.path.dirname(filepath))
                            elif display_mode == "both":
                                os.startfile(filepath)
                                os.startfile(os.path.dirname(filepath))
                        except Exception as e:
                            logger.error(f"Error opening file/folder: {e}")

            except Exception as e:
                logger.error(f"Error: {e}")
    except Exception: pass

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
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('', PORT_UDP))
        sock.settimeout(1.0) # تعيين مهلة لتجنب التعليق عند الإغلاق
        while state["running"]:
            try:
                data, addr = sock.recvfrom(1024)
                msg = json.dumps({"service": "wameed", "name": socket.gethostname(), "ip": get_local_ip(), "port": PORT_WS, "version": VERSION})
                sock.sendto(msg.encode('utf-8'), addr)
            except socket.timeout:
                continue
            except: pass
    except Exception as e:
        logger.error(f"UDP Error: {e}")

# ======================== Main ========================
if __name__ == "__main__":
    app = WameedApp()
    threading.Thread(target=start_ws_thread, daemon=True).start()
    threading.Thread(target=udp_broadcast, daemon=True).start()
    app.run()
