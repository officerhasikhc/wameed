import os
import sys
import atexit
import logging
import logging.handlers
import socket
import json
import webbrowser
import asyncio
import threading
import time
import shutil
import hashlib
from datetime import datetime
from urllib.parse import urlparse
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

try:
    import sentry_sdk
except ImportError:
    sentry_sdk = None

try:
    from wameed_version import (
        VERSION_NAME,
        VERSION_CODE,
        RELEASE_TAG,
        UPDATE_JSON_URL,
        SENTRY_DSN,
    )
except Exception:
    VERSION_NAME = "0.0.0"
    VERSION_CODE = 0
    RELEASE_TAG = "local"
    UPDATE_JSON_URL = "https://raw.githubusercontent.com/officerhasikhc/wameed/main/update.json"
    SENTRY_DSN = ""

# ======================== Configuration ========================
VERSION = VERSION_NAME
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
        "status_receive_ready": "جاهز لاستقبال ملفات الهاتف على هذا الكمبيوتر",
        "status_send_ready": "✅ متصل لإرسال الملفات إلى {name}",
        "status_discovered_only": "🔵 تم اكتشاف {name} فقط — لم يتم تأكيد WebSocket بعد",
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
        "receiving_progress": "⏳ جاري الاستلام...",
        "receiving_detail": "{percent}% • {speed} Mbps",
        "saving_file": "جاري الحفظ: {name}...",
        "retry_connect": "إعادة محاولة الاتصال ({attempt}/{max})... افتح التطبيق",
        "found_devices": "✅ تم العثور على {count} جهاز",
        "close": "✕ إغلاق",
        "no_device_connected_error": "لا يوجد جهاز متصل!\n\nاستخدم 'بحث عن أجهزة' أو 'اتصال يدوي' من الصفحة الرئيسية أولاً.",
        "auto_open_file_label": "فتح الملفات تلقائياً عند الاستلام",
        "auto_open_folder_label": "فتح مجلد الحفظ عند الاستلام",
        "warning": "تنبيه",
        "lang_label": "اللغة / Language",
        "status_discovered": "🔵 مكتشف: {name} (غير متصل بعد)",
        "status_verifying": "🔄 جاري التحقق من الاتصال...",
        "status_unstable": "🟡 اتصال غير مستقر مع {name}",
        "device_not_reachable": "الجهاز '{name}' مكتشف لكن غير متاح حالياً.\nتأكد من فتح تطبيق وميض على الهاتف وتفعيل الاستقبال.",
        "connection_lost_notif": "تم فقدان الاتصال مع {name}",
        "diag_log_btn": "📋 سجل التشخيص",
        "diag_net_btn": "🔧 تشخيص الشبكة",
        "diag_open_log": "📂 فتح ملف السجل",
        "diag_copy_log": "📋 نسخ السجل",
        "diag_copy_results": "📋 نسخ النتائج",
        "diag_run": "▶ تشغيل الفحص",
        "diag_running": "جاري الفحص...",
        "diag_title": "تشخيص الشبكة والسجل",
        "firewall_copy": "📋 نسخ أوامر Firewall",
        "firewall_fix": "🛡️ إصلاح Firewall",
        "firewall_confirm": "سيطلب ويندوز صلاحية المدير لفتح TCP 7788 و UDP 7789 لوميض. هل تريد المتابعة؟",
        "firewall_copied": "تم نسخ أوامر Firewall",
        "preflight_failed": "الجهاز مكتشف لكن WebSocket غير جاهز. افتح وميض على الهاتف ووافق على الاقتران.",
        "updates_title": "التحديثات",
        "updates_current": "الإصدار الحالي: {version}",
        "check_updates": "البحث عن تحديثات",
        "checking_updates": "جاري البحث عن تحديثات...",
        "update_available_title": "تحديث جديد متاح",
        "update_available_msg": "الإصدار {version} متاح للتثبيت.",
        "update_release_notes": "ملاحظات الإصدار",
        "update_now": "تحديث الآن",
        "update_later": "لاحقاً",
        "update_up_to_date": "أحدث إصدار مثبت",
        "update_failed": "تعذر إكمال التحديث",
        "update_download_start": "بدء تنزيل التحديث...",
        "update_download_progress": "{percent}% - {done} من {total} ({speed}/ث)",
        "update_download_unknown": "{done} تم تنزيلها ({speed}/ث)",
        "update_installing": "جاري تشغيل المثبت. سيتم إغلاق وميض لإكمال الاستبدال.",
        "update_ready_restart": "تم تنزيل التحديث. سيتم إغلاق وميض وتشغيل المثبت.",
        "update_confirm_install": "سيتم إغلاق وميض وتشغيل المثبت لاستبدال النسخة الحالية. متابعة؟",
        "update_close": "إغلاق",
        "update_manifest_invalid": "ملف التحديث لا يحتوي على بيانات ويندوز صالحة.",
        "firewall_blocked_msg": "يبدو أن Windows Firewall يحجب منفذ TCP 7788 المطلوب لاستقبال الملفات من الهاتف.\n\nهل تريد إضافة قاعدة Firewall تلقائياً؟ (يتطلب صلاحية المدير)"
    },
    "en": {
        "app_header": "Wameed",
        "status_ready": "⚪ Not connected",
        "status_connected_to": "✅ Connected to {name}",
        "status_receive_ready": "Ready to receive phone files on this PC",
        "status_send_ready": "✅ Ready to send files to {name}",
        "status_discovered_only": "🔵 {name} discovered only — WebSocket not confirmed yet",
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
        "receiving_progress": "⏳ Receiving...",
        "receiving_detail": "{percent}% • {speed} Mbps",
        "saving_file": "Saving: {name}...",
        "retry_connect": "Retrying ({attempt}/{max})... Open the app",
        "found_devices": "✅ Found {count} device(s)",
        "close": "✕ Close",
        "no_device_connected_error": "No device connected!\n\nUse 'Search Devices' or 'Manual Connect' from the home page first.",
        "auto_open_file_label": "Auto-open files on receipt",
        "auto_open_folder_label": "Open save folder on receipt",
        "warning": "Warning",
        "lang_label": "Language / اللغة",
        "status_discovered": "🔵 Discovered: {name} (not connected yet)",
        "status_verifying": "🔄 Verifying connection...",
        "status_unstable": "🟡 Unstable connection with {name}",
        "device_not_reachable": "Device '{name}' was discovered but is not reachable.\nMake sure the Wameed app is open on the phone with receiving enabled.",
        "connection_lost_notif": "Connection lost with {name}",
        "diag_log_btn": "📋 Diagnostic Log",
        "diag_net_btn": "🔧 Network Diagnostics",
        "diag_open_log": "📂 Open Log File",
        "diag_copy_log": "📋 Copy Log",
        "diag_copy_results": "📋 Copy Results",
        "diag_run": "▶ Run Tests",
        "diag_running": "Running tests...",
        "diag_title": "Network Diagnostics & Log",
        "firewall_copy": "📋 Copy Firewall Commands",
        "firewall_fix": "🛡️ Fix Firewall",
        "firewall_confirm": "Windows will request administrator permission to open TCP 7788 and UDP 7789 for Wameed. Continue?",
        "firewall_copied": "Firewall commands copied",
        "preflight_failed": "The device is discovered but WebSocket is not ready. Open Wameed on the phone and approve pairing.",
        "updates_title": "Updates",
        "updates_current": "Current version: {version}",
        "check_updates": "Check for updates",
        "checking_updates": "Checking for updates...",
        "update_available_title": "New update available",
        "update_available_msg": "Version {version} is available to install.",
        "update_release_notes": "Release notes",
        "update_now": "Update now",
        "update_later": "Later",
        "update_up_to_date": "You are up to date",
        "update_failed": "Update could not be completed",
        "update_download_start": "Starting update download...",
        "update_download_progress": "{percent}% - {done} of {total} ({speed}/s)",
        "update_download_unknown": "{done} downloaded ({speed}/s)",
        "update_installing": "Starting the installer. Wameed will close to finish replacing the app.",
        "update_ready_restart": "The update was downloaded. Wameed will close and start the installer.",
        "update_confirm_install": "Wameed will close and start the installer to replace the current version. Continue?",
        "update_close": "Close",
        "update_manifest_invalid": "The update file does not contain valid Windows update data.",
        "firewall_blocked_msg": "Windows Firewall appears to be blocking TCP port 7788 required to receive files from the phone.\n\nWould you like to add a firewall rule automatically? (Requires administrator permission)"
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

# ======================== Remote Diagnostics ========================
def _sanitize_for_telemetry(value):
    """Remove user-specific paths and full private IPs before remote reporting."""
    import re
    text = str(value)
    text = re.sub(r"\b(\d{1,3}\.\d{1,3}\.\d{1,3})\.\d{1,3}\b", r"\1.x", text)
    text = re.sub(r"([A-Za-z]:\\Users\\)[^\\\r\n]+", r"\1<user>", text)
    text = re.sub(r"(/home/)[^/\r\n]+", r"\1<user>", text)
    return text[:900]

def _sanitize_event(obj):
    if isinstance(obj, dict):
        return {k: _sanitize_event(v) for k, v in obj.items()}
    if isinstance(obj, list):
        return [_sanitize_event(v) for v in obj]
    if isinstance(obj, str):
        return _sanitize_for_telemetry(obj)
    return obj

def _sentry_before_send(event, hint):
    return _sanitize_event(event)

def init_sentry():
    dsn = os.environ.get("WAMEED_SENTRY_DSN") or SENTRY_DSN
    if not dsn:
        logger.info("Sentry disabled: no WAMEED_SENTRY_DSN/version.properties sentryDsn configured")
        return False
    if sentry_sdk is None:
        logger.warning("Sentry disabled: sentry-sdk is not installed")
        return False

    try:
        sample_rate = float(os.environ.get("WAMEED_SENTRY_TRACES_SAMPLE_RATE", "0.05"))
    except ValueError:
        sample_rate = 0.05

    try:
        sentry_sdk.init(
            dsn=dsn,
            release=f"wameed-windows@{VERSION}",
            environment=os.environ.get("WAMEED_ENV", "production"),
            traces_sample_rate=sample_rate,
            before_send=_sentry_before_send,
        )
        sentry_sdk.set_tag("app", "wameed-windows")
        sentry_sdk.set_tag("version", VERSION)
        sentry_sdk.set_tag("version_code", str(VERSION_CODE))
        sentry_sdk.set_tag("release_tag", RELEASE_TAG)
        sentry_sdk.set_tag("platform", "windows")
        logger.info("Sentry initialized for Windows diagnostics")
        return True
    except Exception as exc:
        logger.warning(f"Sentry initialization failed: {exc}")
        return False

SENTRY_ENABLED = init_sentry()

def report_windows_issue(category, exc=None, level="error", **context):
    safe_context = {str(k): _sanitize_for_telemetry(v) for k, v in context.items()}
    if exc is not None:
        logger.debug(f"Telemetry issue {category}: {type(exc).__name__}: {_sanitize_for_telemetry(exc)}")
    else:
        logger.debug(f"Telemetry issue {category}: {safe_context}")

    if not SENTRY_ENABLED or sentry_sdk is None:
        return None

    try:
        with sentry_sdk.push_scope() as scope:
            scope.set_tag("wameed.category", category)
            scope.set_tag("wameed.level", level)
            for key, value in safe_context.items():
                if len(value) <= 120:
                    scope.set_tag(f"wameed.{key}", value)
            scope.set_context("wameed", safe_context)
            if exc is not None:
                return sentry_sdk.capture_exception(exc)
            else:
                return sentry_sdk.capture_message(category, level=level)
    except Exception as telemetry_exc:
        logger.debug(f"Sentry capture failed: {telemetry_exc}")
    return None

def show_error_report_dialog(category, exc, event_id=None, parent=None):
    """Show a compact user-facing report notice after serious failures."""
    try:
        sentry_line = (
            f"تم إرسال التقرير إلى Sentry.\nEvent ID: {event_id}"
            if event_id else
            "تم حفظ التفاصيل في سجل التشخيص المحلي. لم يتم إرسالها لأن Sentry غير مهيأ."
        )
        message = (
            f"حدث خطأ في وميض.\n\n"
            f"النوع: {type(exc).__name__}\n"
            f"القسم: {category}\n\n"
            f"{sentry_line}\n\n"
            f"هل تريد فتح مجلد السجل المحلي؟"
        )
        if messagebox.askyesno("وميض - تقرير خطأ", message, parent=parent):
            os.startfile(LOCAL_LOG_DIR)
    except Exception:
        pass

def install_global_exception_hooks():
    """Capture unhandled Windows errors even when they happen outside app.run()."""
    previous_sys_hook = sys.excepthook
    previous_thread_hook = getattr(threading, "excepthook", None)

    def sys_hook(exc_type, exc, tb):
        logger.exception("Unhandled Python exception", exc_info=(exc_type, exc, tb))
        event_id = report_windows_issue("unhandled_app_error", exc)
        show_error_report_dialog("unhandled_app_error", exc, event_id=event_id)
        if previous_sys_hook:
            previous_sys_hook(exc_type, exc, tb)

    def thread_hook(args):
        logger.exception(
            "Unhandled thread exception",
            exc_info=(args.exc_type, args.exc_value, args.exc_traceback)
        )
        report_windows_issue(
            "unhandled_thread_error",
            args.exc_value,
            thread=getattr(args.thread, "name", "unknown")
        )
        if previous_thread_hook:
            previous_thread_hook(args)

    sys.excepthook = sys_hook
    if previous_thread_hook:
        threading.excepthook = thread_hook

TRANSFER_PROTOCOL_VERSION = 2
TRANSFER_CHUNK_SIZE = 512 * 1024
TRANSFER_MAX_FRAME_SIZE = 8 * 1024 * 1024

def _safe_filename(filename):
    name = os.path.basename(str(filename or "received_file")).strip()
    return name or "received_file"

def _ensure_unique_path(path):
    if not os.path.exists(path):
        return path
    base, ext = os.path.splitext(path)
    counter = 1
    while True:
        candidate = f"{base}_{counter}{ext}"
        if not os.path.exists(candidate):
            return candidate
        counter += 1

def _transfer_id_for_file(path, size):
    raw = f"{os.path.basename(path).lower()}:{int(size)}".encode("utf-8", errors="ignore")
    return "w2a-" + hashlib.sha256(raw).hexdigest()[:20]

def _ack_timeout_for_size(size_bytes):
    # Finalization on mobile storage can be slow; keep this bounded but size-aware.
    return max(180, min(3600, int(size_bytes / (2 * 1024 * 1024)) + 120))

async def _send_transfer_status(websocket, status, **fields):
    payload = {
        "status": status,
        "protocol_version": TRANSFER_PROTOCOL_VERSION,
        **fields,
    }
    await websocket.send(json.dumps(payload))

# ======================== Utils ========================
def get_resource_path(relative_path):
    if hasattr(sys, '_MEIPASS'):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath("."), relative_path)

def _is_usable_ipv4(ip: str) -> bool:
    return bool(ip) and not ip.startswith("127.") and ip != "0.0.0.0"

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        if _is_usable_ipv4(ip):
            return ip
    except Exception:
        pass

    try:
        for ip in socket.gethostbyname_ex(socket.gethostname())[2]:
            if _is_usable_ipv4(ip):
                return ip
    except Exception:
        pass
    return "127.0.0.1"

def get_local_ip_for_peer(peer_ip: str = None) -> str:
    if peer_ip:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect((peer_ip, 9))
            ip = s.getsockname()[0]
            s.close()
            if _is_usable_ipv4(ip):
                return ip
        except Exception:
            pass
    return get_local_ip()

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
# حالة الاتصال الموحدة: disconnected, discovered, connecting, connected, unstable
connection_state = "disconnected"
_health_check_failures = 0  # عدد فشل فحص TCP المتتالي

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

        self.receive_status_frame = tk.Frame(inner, bg="#F8FAFC")
        self.receive_progress_var = tk.DoubleVar(value=0)
        self.receive_progress_bar = ttk.Progressbar(
            self.receive_status_frame,
            orient="horizontal",
            mode="determinate",
            variable=self.receive_progress_var
        )
        self.receive_progress_label = tk.Label(
            self.receive_status_frame,
            text=t("receiving_progress"),
            bg="#F8FAFC",
            font=(FONT_AR, fs(9), "bold"),
            fg="#2E7D32"
        )
        self.receive_progress_detail = tk.Label(
            self.receive_status_frame,
            text="",
            bg="#F8FAFC",
            font=(FONT_AR, fs(8)),
            fg="#64748B"
        )

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
        """تحديث عرض الحالة بناءً على connection_state الموحد"""
        for w in self.status_frame.winfo_children(): w.destroy()

        global connected_device, last_connection_time, connection_state

        device_name = connected_device.get("name", "?") if connected_device else ""

        if connection_state == "connected" and device_name:
            status_text = t("status_send_ready").format(name=device_name)
            dot_color = "#22C55E"  # أخضر
        elif connection_state == "discovered" and device_name:
            status_text = t("status_discovered_only").format(name=device_name)
            dot_color = "#3B82F6"  # أزرق
        elif connection_state == "unstable" and device_name:
            status_text = t("status_unstable").format(name=device_name)
            dot_color = "#FBBF24"  # أصفر
        elif connection_state == "connecting":
            status_text = t("status_verifying")
            dot_color = "#3B82F6"  # أزرق
        elif last_connection_time:
            elapsed = (datetime.now() - last_connection_time).total_seconds()
            if elapsed < 1800:
                mins = max(1, int(elapsed / 60))
                status_text = t("status_last_seen").format(mins=mins)
                dot_color = "#FBBF24"  # أصفر
            else:
                status_text = t("status_ready")
                dot_color = "#9CA3AF"  # رمادي
        else:
            status_text = t("status_ready")
            dot_color = "#9CA3AF"  # رمادي

        self.status_dot = tk.Label(self.status_frame, text="●", fg=dot_color, bg="#F8FAFC", font=(FONT_AR, 18))
        self.status_dot.pack(side="right" if LANG=="ar" else "left", padx=8)

        self.status_label = tk.Label(self.status_frame, text=status_text,
                                     bg="#F8FAFC", font=(FONT_AR, fs(12), "bold"), fg="#1E293B")
        self.status_label.pack(side="right" if LANG=="ar" else "left")

        new_status = status_text
        if not hasattr(self, '_last_logged_status') or self._last_logged_status != new_status:
            self._last_logged_status = new_status
            logger.info(f"🔄 تحديث الحالة: {new_status}")

    def _show_receive_progress(self, filename):
        if not hasattr(self, "receive_status_frame"):
            return
        self.receive_progress_var.set(0)
        self.receive_progress_label.config(text=t("receiving_progress"))
        self.receive_progress_detail.config(text=filename)
        if not self.receive_status_frame.winfo_ismapped():
            self.receive_status_frame.pack(fill="x", pady=(12, 0))
            self.receive_progress_label.pack(anchor="e" if LANG == "ar" else "w")
            self.receive_progress_bar.pack(fill="x", pady=(6, 3))
            self.receive_progress_detail.pack(anchor="e" if LANG == "ar" else "w")

    def _update_receive_progress(self, percent, speed_mbps):
        if not hasattr(self, "receive_status_frame"):
            return
        percent = max(0, min(100, int(percent)))
        self.receive_progress_var.set(percent)
        self.receive_progress_label.config(text=t("receiving_progress"))
        self.receive_progress_detail.config(
            text=t("receiving_detail").format(percent=percent, speed=f"{speed_mbps:.1f}")
        )

    def _hide_receive_progress(self, delay=1800):
        if not hasattr(self, "receive_status_frame"):
            return
        def _hide():
            try:
                self.receive_status_frame.pack_forget()
                self.receive_progress_var.set(0)
            except Exception:
                pass
        self.root.after(delay, _hide)

    def _check_firewall_on_startup(self):
        """فحص Firewall عند أول تشغيل — يكتشف إذا كان TCP 7788 محجوباً من الشبكة المحلية"""
        if state.get("firewall_checked"):
            return  # تم الفحص سابقاً

        def _do_check():
            time.sleep(3)  # انتظار بدء WS server
            local_ip = get_local_ip()
            if not _is_usable_ipv4(local_ip):
                return

            # فحص: هل المنفذ مفتوح من الـ LAN IP (ليس localhost)؟
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.settimeout(2)
                s.connect((local_ip, PORT_WS))
                s.close()
                logger.info(f"✅ فحص Firewall: المنفذ TCP {PORT_WS} مفتوح على {local_ip}")
                state["firewall_checked"] = True
                save_config()
            except (ConnectionRefusedError, OSError) as e:
                logger.warning(f"⚠️ فحص Firewall: المنفذ TCP {PORT_WS} محجوب على {local_ip}: {e}")
                # عرض رسالة للمستخدم مع خيار الإصلاح
                self.root.after(0, self._show_firewall_warning)

        threading.Thread(target=_do_check, daemon=True).start()

    def _show_firewall_warning(self):
        """عرض تنبيه Firewall مع زر إصلاح تلقائي"""
        result = messagebox.askyesno(
            "Wameed — Firewall",
            t("firewall_blocked_msg"),
            icon="warning"
        )
        if result:
            self._run_firewall_fix()
            # بعد الإصلاح، علّم أنه تم الفحص
            state["firewall_checked"] = True
            save_config()

    def _start_connection_monitor(self):
        """بدء مراقبة الاتصال الدورية مع فحص TCP فعلي"""
        # فحص Firewall عند أول تشغيل
        self._check_firewall_on_startup()

        def monitor():
            global _health_check_failures, connection_state

            # بحث أولي عند التشغيل إذا لم يكن هناك IP
            if not state.get("target_ip"):
                time.sleep(2)
                self.root.after(0, self._auto_discover_target)

            cycle = 0
            while state["running"]:
                try:
                    # تحديث العرض كل 10 ثوانٍ
                    if hasattr(self, 'status_frame'):
                        self.root.after(0, self._update_status_display)

                    # فحص صحة الاتصال كل 30 ثانية (كل 3 دورات)
                    cycle += 1
                    if cycle % 3 == 0 and connected_device and connected_device.get("ip"):
                        ip = connected_device["ip"]
                        reachable = self._verify_device_connection(ip)

                        if reachable:
                            _health_check_failures = 0
                            if connection_state == "unstable":
                                self._set_connection_state("connected", connected_device)
                        else:
                            _health_check_failures += 1
                            if _health_check_failures == 1 and connection_state == "connected":
                                self._set_connection_state("unstable", connected_device)
                            elif _health_check_failures >= 3:
                                logger.warning(f"📴 فقدان الاتصال مع {connected_device.get('name', '?')} بعد {_health_check_failures} فحوصات فاشلة")
                                old_device = connected_device.copy() if connected_device else None
                                self._set_connection_state("disconnected", old_device)
                except Exception as e:
                    logger.error(f"Status monitor error: {e}")
                time.sleep(10)

        threading.Thread(target=monitor, daemon=True).start()

    def _auto_discover_target(self):
        """البحث التلقائي عن أول جهاز متاح وتحديده كهدف"""
        if not state.get("target_ip"):
            logger.info("Auto-discovery: searching for available devices...")
            found = self._broadcast_discovery_multi(timeout=3.0)
            if found:
                device = found[0]
                ip = device.get("ip")
                state["target_ip"] = ip
                save_config()
                if hasattr(self, 'home_ip_var'):
                    self.home_ip_var.set(ip)
                self._set_connection_state("discovered", {
                    "id": device.get("id", ""),
                    "name": device.get("name", ip),
                    "ip": ip
                })
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
                self._set_connection_state("discovered", {"id": "", "name": ip, "ip": ip})
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
                    name = device.get("name", "?")
                    logger.info(f"تم اختيار الجهاز {name} من قائمة البحث — التحقق من الاتصال...")

                    status_label.config(text=t("status_verifying"), fg="#3B82F6")
                    dialog.update_idletasks()

                    device_info = {
                        "id": device.get("id", ""),
                        "name": name,
                        "ip": ip,
                    }
                    self._set_connection_state("connecting", device_info)

                    def verify_and_open():
                        ok, reason = self._verify_phone_websocket(ip, timeout=30)

                        def finish():
                            if ok:
                                logger.info(f"✅ WebSocket/Pairing confirmed for {name} ({ip}) — opening send dialog")
                                state["target_ip"] = ip
                                save_config()
                                if hasattr(self, 'home_ip_var'):
                                    self.home_ip_var.set(ip)

                                connected_info = {
                                    "id": device.get("id", ""),
                                    "name": name,
                                    "ip": ip,
                                    "connected_at": datetime.now()
                                }
                                self._set_connection_state("connected", connected_info)
                                self.root.after(0, lambda: self.update_device_history(device.get("id", ""), name))

                                dialog.destroy()
                                self._show_send_dialog(device_ip=ip, device_name=name)
                            else:
                                logger.warning(f"⚠️ {name} ({ip}) discovered but WebSocket is not ready: {reason}")
                                self._set_connection_state("discovered", device_info)
                                status_label.config(
                                    text=f"{t('preflight_failed')} ({reason})",
                                    fg="#EF4444"
                                )

                        self.root.after(0, finish)

                    threading.Thread(target=verify_and_open, daemon=True).start()

        devices_listbox.bind("<<ListboxSelect>>", on_device_select)

        def search_devices():
            nonlocal discovered_devices
            discovered_devices.clear()
            devices_listbox.delete(0, tk.END)
            logger.info("بدء البحث عن أجهزة وميض في الشبكة (Broadcast)...")

            # البحث عن الأجهزة
            found = self._broadcast_discovery_multi(timeout=3.0)

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

    def _broadcast_discovery_multi(self, timeout=3.0):
        """البحث عن عدة أجهزة — مع burst ودعم subnet broadcast للشبكات البطيئة"""
        devices = []
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(1.5)  # per-recv timeout, not total

            message = json.dumps({
                "type": "discovery_ping",
                "service": "wameed_pc",
                "device": socket.gethostname(),
                "port": PORT_WS
            }).encode('utf-8')

            # Burst: إرسال 3 حزم broadcast متتالية لزيادة احتمالية الوصول
            targets = [('<broadcast>', PORT_UDP), ('255.255.255.255', PORT_UDP)]
            # إضافة subnet broadcast إذا متاح
            try:
                import netifaces
                for iface in netifaces.interfaces():
                    addrs = netifaces.ifaddresses(iface).get(netifaces.AF_INET, [])
                    for a in addrs:
                        bc = a.get('broadcast')
                        if bc and bc not in ('255.255.255.255',):
                            targets.append((bc, PORT_UDP))
            except ImportError:
                pass  # netifaces not available, use fallback

            for i in range(3):
                for target in targets:
                    try:
                        sock.sendto(message, target)
                    except Exception:
                        pass
                if i < 2:
                    time.sleep(0.2)

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
                    # إعادة إرسال ping إذا لم يُعثر على أجهزة بعد
                    if not devices and time.time() - start_time < timeout - 1:
                        for target in targets:
                            try:
                                sock.sendto(message, target)
                            except Exception:
                                pass
                    elif devices:
                        break  # وجدنا أجهزة، لا حاجة للانتظار أكثر
                except Exception as e:
                    logger.error(f"Discovery error: {e}")

            sock.close()
        except Exception as e:
            logger.error(f"Discovery failed: {e}")

        return devices

    def _verify_device_connection(self, ip, port=7789, timeout=3.0):
        """TCP reachability check — verifies the phone's WS server is actually accepting connections"""
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(timeout)
            s.connect((ip, port))
            s.close()
            return True
        except Exception:
            return False

    def _verify_phone_websocket(self, ip, timeout=25):
        """Verify PC -> phone WebSocket and pairing, not just TCP reachability."""
        async def verify():
            uri = f"ws://{ip}:7789"
            async with websockets.connect(
                uri,
                open_timeout=min(5, timeout),
                ping_interval=None,
                close_timeout=1
            ) as ws:
                await ws.send(json.dumps({
                    "type": "hello",
                    "device": socket.gethostname(),
                    "device_id": "pc_client",
                    "app_version": VERSION
                }))

                deadline = time.time() + timeout
                last_status = ""
                while time.time() < deadline:
                    remaining = max(1, deadline - time.time())
                    try:
                        raw = await asyncio.wait_for(ws.recv(), timeout=min(5, remaining))
                    except asyncio.TimeoutError:
                        continue

                    try:
                        resp = json.loads(raw)
                    except Exception:
                        return False, "invalid_response"

                    status = resp.get("status", "")
                    last_status = status or last_status
                    if status == "paired":
                        return True, "paired"
                    if status == "rejected":
                        return False, resp.get("message", "rejected")
                    if status == "pairing_required":
                        continue

                return False, last_status or "timeout"

        try:
            return asyncio.run(verify())
        except Exception as e:
            return False, f"{type(e).__name__}: {e}"

    def _set_connection_state(self, new_state, device_info=None):
        """Update the unified connection state and refresh UI"""
        global connection_state, connected_device, last_connection_time, _health_check_failures
        old_state = connection_state
        connection_state = new_state

        if new_state == "connected" and device_info:
            connected_device = device_info
            last_connection_time = datetime.now()
            _health_check_failures = 0
        elif new_state == "discovered" and device_info:
            # Keep device info but mark as not fully connected
            if not connected_device or connected_device.get("ip") != device_info.get("ip"):
                connected_device = device_info
        elif new_state == "disconnected":
            if connected_device:
                last_connection_time = datetime.now()
            connected_device = None
            _health_check_failures = 0

        if old_state != new_state:
            logger.info(f"🔄 حالة الاتصال: {old_state} → {new_state}")
            if new_state == "disconnected" and old_state == "connected" and device_info:
                show_notification("Wameed", t("connection_lost_notif").format(name=device_info.get("name", "?")))

        if hasattr(self, 'status_frame'):
            self.root.after(0, self._update_status_display)

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
        found_devices = self._broadcast_discovery_multi(timeout=3.0)
        target_device = None

        for d in found_devices:
            if d.get("id") == device_id:
                target_device = d
                break

        if target_device:
            ip = target_device.get("ip")
            # التحقق من WebSocket والاقتران قبل اعتبار الجهاز متصلاً.
            ok, reason = self._verify_phone_websocket(ip, timeout=30)
            if ok:
                device_info = {
                    "id": device_id,
                    "name": device_name,
                    "ip": ip,
                    "connected_at": datetime.now()
                }
                self._set_connection_state("connected", device_info)
                self._build_devices()
                self._show_send_dialog(device_ip=ip, device_name=device_name)
            else:
                device_info = {"id": device_id, "name": device_name, "ip": ip}
                self._set_connection_state("discovered", device_info)
                messagebox.showwarning("تنبيه", f"{t('preflight_failed')}\n{reason}")
        else:
            messagebox.showwarning("تنبيه", f"لم يتم العثور على الجهاز '{device_name}' في الشبكة\nتأكد من أن التطبيق مفتوح على الهاتف")

    def _quick_send_to_device(self, device_ip, device_name):
        """إرسال سريع: يفتح نافذة الإرسال للجهاز المحدد مباشرة"""
        target_ip = device_ip
        if not target_ip:
            # محاولة البحث عن الجهاز في الشبكة
            logger.info(f"Quick send: searching for {device_name}...")
            found = self._broadcast_discovery_multi(timeout=3.0)
            for d in found:
                if d.get("name") == device_name:
                    target_ip = d.get("ip")
                    break

        if target_ip:
            # تحديث IP الهدف في الحالة الرئيسية
            state["target_ip"] = target_ip
            if hasattr(self, 'home_ip_var'):
                self.home_ip_var.set(target_ip)
            if connection_state != "connected":
                self._set_connection_state("discovered", {
                    "id": "",
                    "name": device_name or target_ip,
                    "ip": target_ip
                })

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

        canvas = tk.Canvas(self.tab_settings, bg="white", highlightthickness=0)
        scroll = ttk.Scrollbar(self.tab_settings, orient="vertical", command=canvas.yview)
        container = tk.Frame(canvas, bg="white", padx=20, pady=20)
        container_window = canvas.create_window((0, 0), window=container, anchor="nw")

        def _sync_scroll_region(_event=None):
            canvas.configure(scrollregion=canvas.bbox("all"))

        def _sync_width(event):
            canvas.itemconfigure(container_window, width=event.width)

        def _on_mousewheel(event):
            canvas.yview_scroll(int(-1 * (event.delta / 120)), "units")

        def _bind_mousewheel(_event):
            canvas.bind_all("<MouseWheel>", _on_mousewheel)

        def _unbind_mousewheel(_event):
            canvas.unbind_all("<MouseWheel>")

        container.bind("<Configure>", _sync_scroll_region)
        canvas.bind("<Configure>", _sync_width)
        canvas.bind("<Enter>", _bind_mousewheel)
        canvas.bind("<Leave>", _unbind_mousewheel)
        canvas.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        canvas.pack(side="left", fill="both", expand=True)

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

        ttk.Separator(container).pack(fill="x", pady=15)

        # =================== Updates Section ===================
        tk.Label(container, text=t("updates_title"), bg="white", font=(FONT_AR, fs(10), "bold")).pack(anchor="e" if LANG=="ar" else "w", pady=(5, 2))

        update_frame = tk.Frame(container, bg="white")
        update_frame.pack(fill="x", pady=5)

        tk.Label(update_frame, text=t("updates_current").format(version=VERSION),
                 bg="white", fg="#475569", font=(FONT_AR, fs(9))).pack(
            side="right" if LANG=="ar" else "left", fill="x", expand=True,
            anchor="e" if LANG=="ar" else "w")

        tk.Button(update_frame, text=t("check_updates"), command=self._show_update_dialog,
                  bg="#E0F2FE", fg="#075985", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=14,
                  cursor="hand2").pack(side="left" if LANG=="ar" else "right", padx=3)

        ttk.Separator(container).pack(fill="x", pady=15)

        # Trusted Devices
        tk.Label(container, text=t("trusted_devices"), bg="white", font=(FONT_AR, fs(10), "bold")).pack(anchor="e" if LANG=="ar" else "w", pady=(10, 2))
        self.devices_list = tk.Listbox(container, height=5, font=(FONT_AR, fs(9)), bd=1, relief="solid")
        self.devices_list.pack(fill="x")
        self.refresh_devices_list()

        tk.Button(container, text=t("delete_device"), command=self.remove_device,
                  bg="#FEE2E2", fg="#991B1B", bd=0, pady=5, font=(FONT_AR, fs(9))).pack(anchor="e" if LANG=="ar" else "w", pady=5)

        ttk.Separator(container).pack(fill="x", pady=15)

        # =================== Diagnostics Section ===================
        tk.Label(container, text=t("diag_title"), bg="white", font=(FONT_AR, fs(10), "bold")).pack(anchor="e" if LANG=="ar" else "w", pady=(5, 2))

        diag_frame = tk.Frame(container, bg="white")
        diag_frame.pack(fill="x", pady=5)

        tk.Button(diag_frame, text=t("diag_open_log"), command=self._open_log_file,
                  bg="#EFF6FF", fg="#1E40AF", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(diag_frame, text=t("diag_log_btn"), command=self._show_log_viewer,
                  bg="#F0FDF4", fg="#166534", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(diag_frame, text=t("diag_net_btn"), command=self._show_network_diagnostics,
                  bg="#FFF7ED", fg="#9A3412", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(diag_frame, text=t("firewall_copy"), command=self._copy_firewall_commands,
                  bg="#EFF6FF", fg="#2563EB", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(diag_frame, text=t("firewall_fix"), command=self._run_firewall_fix,
                  bg="#FEF2F2", fg="#991B1B", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

    def _call_if_widget_exists(self, widget, callback):
        try:
            if widget.winfo_exists():
                callback()
        except Exception:
            pass

    def _version_tuple(self, value):
        parts = []
        current = ""
        for ch in str(value or ""):
            if ch.isdigit():
                current += ch
            elif current:
                parts.append(int(current))
                current = ""
        if current:
            parts.append(int(current))
        while len(parts) < 4:
            parts.append(0)
        return tuple(parts[:4])

    def _is_newer_version(self, remote_version, local_version=VERSION):
        return self._version_tuple(remote_version) > self._version_tuple(local_version)

    def _format_bytes(self, value):
        try:
            value = float(value)
        except Exception:
            value = 0.0
        units = ["B", "KB", "MB", "GB"]
        unit_index = 0
        while value >= 1024 and unit_index < len(units) - 1:
            value /= 1024
            unit_index += 1
        if unit_index == 0:
            return f"{int(value)} {units[unit_index]}"
        return f"{value:.1f} {units[unit_index]}"

    def _update_file_name(self, update_url, version):
        try:
            name = os.path.basename(urlparse(update_url).path)
        except Exception:
            name = ""
        if not name or not name.lower().endswith(".exe"):
            name = f"WameedSetup-{version}.exe"
        return name

    def _center_dialog(self, dialog, width, height):
        dialog.update_idletasks()
        x = (dialog.winfo_screenwidth() // 2) - (width // 2)
        y = (dialog.winfo_screenheight() // 2) - (height // 2)
        dialog.geometry(f"{width}x{height}+{x}+{y}")

    def _show_update_dialog(self):
        dialog = tk.Toplevel(self.root)
        dialog.title(t("updates_title"))
        dialog.geometry("520x360")
        dialog.configure(bg="white")
        dialog.transient(self.root)
        dialog.grab_set()

        anchor = "e" if LANG == "ar" else "w"
        primary_side = "right" if LANG == "ar" else "left"
        secondary_side = "left" if LANG == "ar" else "right"
        justify = "right" if LANG == "ar" else "left"

        content = tk.Frame(dialog, bg="white", padx=24, pady=22)
        content.pack(fill="both", expand=True)

        tk.Label(content, text=t("updates_title"), bg="white", fg="#0F172A",
                 font=(FONT_AR, fs(15), "bold")).pack(anchor=anchor)
        tk.Label(content, text=t("updates_current").format(version=VERSION), bg="white",
                 fg="#64748B", font=(FONT_AR, fs(9))).pack(anchor=anchor, pady=(4, 18))

        title_var = tk.StringVar(value=t("checking_updates"))
        detail_var = tk.StringVar(value="")
        notes_var = tk.StringVar(value="")
        progress_var = tk.StringVar(value="")

        tk.Label(content, textvariable=title_var, bg="white", fg="#1E293B",
                 font=(FONT_AR, fs(11), "bold"), justify=justify).pack(anchor=anchor)
        tk.Label(content, textvariable=detail_var, bg="white", fg="#475569",
                 font=(FONT_AR, fs(9)), justify=justify, wraplength=450).pack(anchor=anchor, pady=(8, 0))
        tk.Label(content, textvariable=notes_var, bg="white", fg="#64748B",
                 font=(FONT_AR, fs(9)), justify=justify, wraplength=450).pack(anchor=anchor, pady=(10, 0))

        progress = ttk.Progressbar(content, maximum=100, mode="determinate")
        progress.pack(fill="x", pady=(18, 4))
        progress.pack_forget()

        progress_label = tk.Label(content, textvariable=progress_var, bg="white", fg="#334155",
                                  font=(FONT_AR, fs(9)), justify=justify)
        progress_label.pack(anchor=anchor)
        progress_label.pack_forget()

        button_frame = tk.Frame(content, bg="white")
        button_frame.pack(side="bottom", fill="x", pady=(20, 0))

        close_btn = tk.Button(button_frame, text=t("update_close"), command=dialog.destroy,
                              bg="#F1F5F9", fg="#334155", bd=0, padx=18, pady=8,
                              font=(FONT_AR, fs(9)), cursor="hand2")
        close_btn.pack(side=secondary_side, padx=4)

        primary_btn = tk.Button(button_frame, text=t("checking_updates"), state="disabled",
                                bg="#0EA5E9", fg="white", bd=0, padx=18, pady=8,
                                font=(FONT_AR, fs(9), "bold"), cursor="hand2",
                                activebackground="#0284C7", activeforeground="white")
        primary_btn.pack(side=primary_side, padx=4)

        ui = {
            "dialog": dialog,
            "title_var": title_var,
            "detail_var": detail_var,
            "notes_var": notes_var,
            "progress_var": progress_var,
            "progress": progress,
            "progress_label": progress_label,
            "primary_btn": primary_btn,
            "close_btn": close_btn,
        }

        def post(callback):
            self.root.after(0, lambda: self._call_if_widget_exists(dialog, callback))

        def show_error(error_text):
            title_var.set(t("update_failed"))
            detail_var.set(str(error_text))
            notes_var.set("")
            primary_btn.config(text=t("check_updates"), state="normal", command=start_check)
            close_btn.config(state="normal")
            progress.pack_forget()
            progress_label.pack_forget()

        def show_result(info):
            if self._is_newer_version(info["version"]):
                title_var.set(t("update_available_title"))
                detail_var.set(t("update_available_msg").format(version=info["version"]))
                notes = info.get("releaseNotes") or ""
                notes_var.set(f"{t('update_release_notes')}: {notes}" if notes else "")
                primary_btn.config(text=t("update_now"), state="normal",
                                   command=lambda: start_download(info))
            else:
                title_var.set(t("update_up_to_date"))
                detail_var.set(t("updates_current").format(version=VERSION))
                notes_var.set("")
                primary_btn.config(text=t("check_updates"), state="normal", command=start_check)
            close_btn.config(text=t("update_close"), state="normal")
            progress.pack_forget()
            progress_label.pack_forget()

        def start_download(info):
            if not messagebox.askyesno(t("update_available_title"), t("update_confirm_install"), parent=dialog):
                return
            self._download_windows_update(info, ui)

        def start_check():
            title_var.set(t("checking_updates"))
            detail_var.set("")
            notes_var.set("")
            progress.pack_forget()
            progress_label.pack_forget()
            primary_btn.config(text=t("checking_updates"), state="disabled")
            close_btn.config(state="disabled")

            def worker():
                try:
                    response = requests.get(
                        UPDATE_JSON_URL,
                        timeout=15,
                        headers={
                            "User-Agent": f"Wameed-Windows/{VERSION}",
                            "Cache-Control": "no-cache",
                            "Pragma": "no-cache",
                        }
                    )
                    response.raise_for_status()
                    manifest = response.json()
                    info = manifest.get("windows") or {}
                    version = str(info.get("version", "")).strip()
                    update_url = str(info.get("updateUrl", "")).strip()
                    if not version or not update_url:
                        raise ValueError(t("update_manifest_invalid"))
                    update_info = {
                        "version": version,
                        "updateUrl": update_url,
                        "releaseNotes": str(info.get("releaseNotes", "")).strip()
                    }
                    post(lambda: show_result(update_info))
                except Exception as exc:
                    logger.exception("Windows update check failed")
                    report_windows_issue("update_check_failed", exc, url=UPDATE_JSON_URL)
                    post(lambda error=exc: show_error(error))

            threading.Thread(target=worker, daemon=True).start()

        self._center_dialog(dialog, 520, 360)
        start_check()

    def _download_windows_update(self, info, ui):
        dialog = ui["dialog"]
        title_var = ui["title_var"]
        detail_var = ui["detail_var"]
        notes_var = ui["notes_var"]
        progress_var = ui["progress_var"]
        progress = ui["progress"]
        progress_label = ui["progress_label"]
        primary_btn = ui["primary_btn"]
        close_btn = ui["close_btn"]

        def post(callback):
            self.root.after(0, lambda: self._call_if_widget_exists(dialog, callback))

        def show_error(error_text):
            title_var.set(t("update_failed"))
            detail_var.set(str(error_text))
            notes_var.set("")
            progress_var.set("")
            primary_btn.config(text=t("check_updates"), state="normal",
                               command=lambda: (dialog.destroy(), self._show_update_dialog()))
            close_btn.config(state="normal")

        def show_ready(installer_path):
            title_var.set(t("update_ready_restart"))
            detail_var.set(t("update_installing"))
            notes_var.set("")
            progress["value"] = 100
            progress_var.set(t("update_download_progress").format(
                percent=100,
                done=self._format_bytes(os.path.getsize(installer_path)),
                total=self._format_bytes(os.path.getsize(installer_path)),
                speed=self._format_bytes(0)
            ))
            primary_btn.config(state="disabled")
            close_btn.config(state="disabled")
            self.root.after(800, lambda: self._launch_update_installer(installer_path, dialog))

        def worker():
            try:
                update_url = info["updateUrl"]
                version = info["version"]
                updates_dir = os.path.join(APP_DATA_DIR, "updates")
                os.makedirs(updates_dir, exist_ok=True)
                installer_path = os.path.join(updates_dir, self._update_file_name(update_url, version))
                if os.path.exists(installer_path):
                    os.remove(installer_path)

                post(lambda: (
                    title_var.set(t("update_download_start")),
                    detail_var.set(t("update_available_msg").format(version=version)),
                    notes_var.set(""),
                    progress.config(mode="determinate", maximum=100, value=0),
                    progress.pack(fill="x", pady=(18, 4)),
                    progress_label.pack(anchor="e" if LANG == "ar" else "w"),
                    progress_var.set(""),
                    primary_btn.config(state="disabled"),
                    close_btn.config(state="disabled")
                ))

                downloaded = 0
                start_time = time.time()
                last_ui_time = 0.0
                with requests.get(
                    update_url,
                    stream=True,
                    timeout=(15, 300),
                    headers={
                        "User-Agent": f"Wameed-Windows/{VERSION}",
                        "Cache-Control": "no-cache",
                        "Pragma": "no-cache",
                    }
                ) as response:
                    response.raise_for_status()
                    total = int(response.headers.get("content-length") or 0)
                    with open(installer_path, "wb") as output:
                        for chunk in response.iter_content(chunk_size=1024 * 1024):
                            if not chunk:
                                continue
                            output.write(chunk)
                            downloaded += len(chunk)
                            now = time.time()
                            if now - last_ui_time < 0.12 and (not total or downloaded < total):
                                continue
                            last_ui_time = now
                            elapsed = max(now - start_time, 0.001)
                            speed = downloaded / elapsed
                            if total:
                                percent = min(100, int((downloaded / total) * 100))
                                text = t("update_download_progress").format(
                                    percent=percent,
                                    done=self._format_bytes(downloaded),
                                    total=self._format_bytes(total),
                                    speed=self._format_bytes(speed)
                                )
                            else:
                                percent = 0
                                text = t("update_download_unknown").format(
                                    done=self._format_bytes(downloaded),
                                    speed=self._format_bytes(speed)
                                )
                            post(lambda p=percent, label=text: (
                                progress.config(value=p),
                                progress_var.set(label)
                            ))

                if downloaded <= 0:
                    raise IOError("Downloaded installer is empty")
                if downloaded < 1024 * 1024:
                    raise IOError(f"Downloaded installer is unexpectedly small: {downloaded} bytes")
                with open(installer_path, "rb") as downloaded_installer:
                    if downloaded_installer.read(2) != b"MZ":
                        raise IOError("Downloaded file is not a Windows executable")
                logger.info(f"Windows update downloaded and validated: {installer_path} ({downloaded} bytes)")
                post(lambda path=installer_path: show_ready(path))
            except Exception as exc:
                logger.exception("Windows update download failed")
                report_windows_issue(
                    "update_download_failed",
                    exc,
                    url=info.get("updateUrl", ""),
                    remote_version=info.get("version", ""),
                )
                post(lambda error=exc: show_error(error))

        threading.Thread(target=worker, daemon=True).start()

    def _launch_update_installer(self, installer_path, dialog=None):
        def ps_quote(value):
            return "'" + str(value).replace("'", "''") + "'"

        try:
            installer_path = os.path.abspath(installer_path)
            updates_dir = os.path.join(APP_DATA_DIR, "updates")
            os.makedirs(updates_dir, exist_ok=True)
            script_path = os.path.join(updates_dir, "apply_wameed_update.ps1")
            current_exe = sys.executable if getattr(sys, "frozen", False) else os.path.abspath(sys.argv[0])
            fallback_exe = os.path.join(os.environ.get("ProgramFiles", r"C:\Program Files"), "Wameed", "Wameed.exe")
            script = f"""$ErrorActionPreference = 'SilentlyContinue'
$PidToWait = {os.getpid()}
$Installer = {ps_quote(installer_path)}
$CurrentExe = {ps_quote(current_exe)}
$FallbackExe = {ps_quote(fallback_exe)}
try {{ Wait-Process -Id $PidToWait -Timeout 20 }} catch {{}}
try {{ Get-Process -Name 'Wameed' -ErrorAction SilentlyContinue | Stop-Process -Force }} catch {{}}
$InstallArgs = '/SILENT /SUPPRESSMSGBOXES /CLOSEAPPLICATIONS /RESTARTAPPLICATIONS'
try {{ Start-Process -FilePath $Installer -ArgumentList $InstallArgs -Verb RunAs -Wait }} catch {{ exit 1 }}
Start-Sleep -Seconds 2
if (($CurrentExe.ToLower().EndsWith('.exe')) -and (Test-Path $CurrentExe)) {{
    Start-Process -FilePath $CurrentExe
}} elseif (Test-Path $FallbackExe) {{
    Start-Process -FilePath $FallbackExe
}}
"""
            with open(script_path, "w", encoding="utf-8") as script_file:
                script_file.write(script)
            flags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
            subprocess.Popen(
                ["powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script_path],
                creationflags=flags
            )
            logger.info(f"Windows update installer launched: {installer_path}; script={script_path}")
            try:
                if dialog and dialog.winfo_exists():
                    dialog.destroy()
            except Exception:
                pass
            self.quit_app()
        except Exception as exc:
            logger.exception("Failed to launch update installer")
            event_id = report_windows_issue("update_install_launch_failed", exc, installer=installer_path)
            show_error_report_dialog("update_install_launch_failed", exc, event_id=event_id, parent=dialog)
            messagebox.showerror(t("update_failed"), str(exc), parent=dialog)

    def _open_log_file(self):
        """فتح مجلد السجل"""
        try:
            os.startfile(LOCAL_LOG_DIR)
        except Exception as e:
            logger.error(f"Failed to open log dir: {e}")

    def _show_log_viewer(self):
        """عارض سجل مدمج"""
        dialog = tk.Toplevel(self.root)
        dialog.title(t("diag_log_btn"))
        dialog.geometry("700x500")
        dialog.configure(bg="white")

        # Header
        header = tk.Frame(dialog, bg="#F0FDF4", pady=8)
        header.pack(fill="x")
        tk.Label(header, text=t("diag_log_btn"), bg="#F0FDF4", fg="#166534",
                 font=(FONT_AR, fs(12), "bold")).pack(side="right" if LANG=="ar" else "left", padx=15)

        btn_frame = tk.Frame(header, bg="#F0FDF4")
        btn_frame.pack(side="left" if LANG=="ar" else "right", padx=15)

        def copy_log():
            content = log_text.get("1.0", tk.END)
            dialog.clipboard_clear()
            dialog.clipboard_append(content)
            logger.info("Log copied to clipboard")

        def refresh_log():
            log_text.config(state="normal")
            log_text.delete("1.0", tk.END)
            try:
                with open(LOG_FILE, "r", encoding="utf-8") as f:
                    lines = f.readlines()
                    # Show last 200 lines
                    for line in lines[-200:]:
                        line = line.rstrip()
                        if "| ERROR |" in line:
                            log_text.insert(tk.END, line + "\n", "error")
                        elif "| WARNING |" in line:
                            log_text.insert(tk.END, line + "\n", "warning")
                        else:
                            log_text.insert(tk.END, line + "\n")
            except Exception as e:
                log_text.insert(tk.END, f"Error reading log: {e}")
            log_text.config(state="disabled")
            log_text.see(tk.END)

        tk.Button(btn_frame, text=t("diag_copy_log"), command=copy_log,
                  bg="#E2E8F0", fg="#1E293B", font=(FONT_AR, fs(8)), bd=0, pady=3, padx=8,
                  cursor="hand2").pack(side="left", padx=3)
        tk.Button(btn_frame, text="🔄", command=refresh_log,
                  bg="#E2E8F0", fg="#1E293B", font=(FONT_AR, fs(8)), bd=0, pady=3, padx=8,
                  cursor="hand2").pack(side="left", padx=3)

        # Log text
        log_text = tk.Text(dialog, wrap="word", font=("Consolas", fs(9)),
                          bg="#1E293B", fg="#E2E8F0", insertbackground="#E2E8F0",
                          bd=0, padx=10, pady=10)
        log_text.tag_configure("error", foreground="#EF4444")
        log_text.tag_configure("warning", foreground="#F59E0B")
        scrollbar = tk.Scrollbar(dialog, command=log_text.yview)
        log_text.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        log_text.pack(fill="both", expand=True)

        refresh_log()

    def _show_network_diagnostics(self):
        """أداة تشخيص شبكة مدمجة"""
        dialog = tk.Toplevel(self.root)
        dialog.title(t("diag_net_btn"))
        dialog.geometry("600x550")
        dialog.configure(bg="white")

        # Header
        header = tk.Frame(dialog, bg="#FFF7ED", pady=8)
        header.pack(fill="x")
        tk.Label(header, text=t("diag_net_btn"), bg="#FFF7ED", fg="#9A3412",
                 font=(FONT_AR, fs(12), "bold")).pack(side="right" if LANG=="ar" else "left", padx=15)

        # Results area
        results_text = tk.Text(dialog, wrap="word", font=("Consolas", fs(9)),
                              bg="#1E293B", fg="#E2E8F0", insertbackground="#E2E8F0",
                              bd=0, padx=10, pady=10, state="disabled")
        results_text.tag_configure("pass", foreground="#22C55E")
        results_text.tag_configure("fail", foreground="#EF4444")
        results_text.tag_configure("info", foreground="#60A5FA")
        results_text.tag_configure("header", foreground="#F59E0B", font=("Consolas", fs(10), "bold"))

        def run_diagnostics():
            results_text.config(state="normal")
            results_text.delete("1.0", tk.END)
            results_text.insert(tk.END, "=== Wameed Network Diagnostics ===\n", "header")
            results_text.insert(tk.END, f"Time: {time.strftime('%Y-%m-%d %H:%M:%S')}\n", "info")
            results_text.insert(tk.END, f"Version: {VERSION}\n\n", "info")

            # 1. Local network info
            results_text.insert(tk.END, "--- Local Network ---\n", "header")
            local_ip = ""
            try:
                hostname = socket.gethostname()
                local_ip = socket.gethostbyname(hostname)
                results_text.insert(tk.END, f"Hostname: {hostname}\n")
                results_text.insert(tk.END, f"Local IP: {local_ip}\n")
                results_text.insert(tk.END, f"Expected PC receive server: 0.0.0.0:{PORT_WS}\n")
            except Exception as e:
                results_text.insert(tk.END, f"Error getting local info: {e}\n", "fail")

            # 2. Check ports
            results_text.insert(tk.END, f"\n--- Port Status ---\n", "header")

            def check_tcp(host, port, label):
                try:
                    test_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    test_sock.settimeout(1)
                    result = test_sock.connect_ex((host, port))
                    test_sock.close()
                    if result == 0:
                        results_text.insert(tk.END, f"✅ {label} {host}:{port}: OPEN\n", "pass")
                    else:
                        results_text.insert(tk.END, f"❌ {label} {host}:{port}: CLOSED\n", "fail")
                except Exception as e:
                    results_text.insert(tk.END, f"❌ {label} {host}:{port}: Error - {e}\n", "fail")

            # Check PC receiver port from loopback and LAN address.
            check_tcp('127.0.0.1', PORT_WS, "PC receiver TCP")
            if local_ip and local_ip != '127.0.0.1':
                check_tcp(local_ip, PORT_WS, "PC receiver LAN TCP")

            # Check UDP port
            try:
                udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                udp_sock.bind(('', PORT_UDP))
                udp_sock.close()
                results_text.insert(tk.END, f"✅ Port {PORT_UDP} (UDP Discovery): AVAILABLE\n", "pass")
            except OSError:
                results_text.insert(tk.END, f"✅ Port {PORT_UDP} (UDP Discovery): IN USE (OK - server running)\n", "pass")
            except Exception as e:
                results_text.insert(tk.END, f"❌ Port {PORT_UDP} (UDP): Error - {e}\n", "fail")

            # 3. Target device test
            target_ip = state.get("target_ip", "")
            if target_ip:
                results_text.insert(tk.END, f"\n--- Target Device ({target_ip}) ---\n", "header")

                # TCP test
                try:
                    start = time.time()
                    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    s.settimeout(3)
                    s.connect((target_ip, 7789))
                    s.close()
                    elapsed = int((time.time() - start) * 1000)
                    results_text.insert(tk.END, f"✅ TCP {target_ip}:7789 — {elapsed}ms\n", "pass")
                except Exception as e:
                    results_text.insert(tk.END, f"❌ TCP {target_ip}:7789 — {e}\n", "fail")

                # ICMP Ping (Windows has ping command)
                try:
                    import subprocess
                    result = subprocess.run(
                        ["ping", "-n", "1", "-w", "3000", target_ip],
                        capture_output=True, text=True, timeout=5
                    )
                    if result.returncode == 0:
                        # Extract time from ping output
                        for line in result.stdout.split('\n'):
                            if 'time=' in line.lower() or 'وقت=' in line:
                                results_text.insert(tk.END, f"✅ ICMP Ping: {line.strip()}\n", "pass")
                                break
                        else:
                            results_text.insert(tk.END, f"✅ ICMP Ping: OK\n", "pass")
                    else:
                        results_text.insert(tk.END, f"❌ ICMP Ping: No response\n", "fail")
                except Exception as e:
                    results_text.insert(tk.END, f"❌ ICMP Ping: {e}\n", "fail")

                # UDP Discovery test
                results_text.insert(tk.END, f"\n--- UDP Discovery Test ---\n", "header")
                try:
                    start = time.time()
                    test_found = self._broadcast_discovery_multi(timeout=2.0)
                    elapsed = int((time.time() - start) * 1000)
                    if test_found:
                        for d in test_found:
                            results_text.insert(tk.END,
                                f"✅ Found: {d['name']} ({d['ip']}) — {elapsed}ms\n", "pass")
                    else:
                        results_text.insert(tk.END, f"❌ No devices found ({elapsed}ms)\n", "fail")
                except Exception as e:
                    results_text.insert(tk.END, f"❌ Discovery: {e}\n", "fail")
            else:
                results_text.insert(tk.END, f"\n--- No target device configured ---\n", "info")
                # Still run discovery
                results_text.insert(tk.END, f"\n--- UDP Discovery Test ---\n", "header")
                try:
                    start = time.time()
                    test_found = self._broadcast_discovery_multi(timeout=2.0)
                    elapsed = int((time.time() - start) * 1000)
                    if test_found:
                        for d in test_found:
                            results_text.insert(tk.END,
                                f"✅ Found: {d['name']} ({d['ip']}) — {elapsed}ms\n", "pass")
                    else:
                        results_text.insert(tk.END, f"❌ No devices found ({elapsed}ms)\n", "fail")
                except Exception as e:
                    results_text.insert(tk.END, f"❌ Discovery: {e}\n", "fail")

            # 4. Firewall hint
            results_text.insert(tk.END, f"\n--- Firewall Note ---\n", "header")
            results_text.insert(tk.END, "If tests fail, check Windows Firewall and network profile:\n")
            results_text.insert(tk.END, f"  - Phone -> PC requires inbound TCP {PORT_WS}\n")
            results_text.insert(tk.END, f"  - Discovery requires UDP {PORT_UDP}\n")
            results_text.insert(tk.END, "  - PC -> Phone requires outbound TCP 7789\n")
            results_text.insert(tk.END, "  - Private network is recommended; Guest/VPN networks can isolate devices\n")

            results_text.insert(tk.END, f"\n{'='*40}\n", "header")
            results_text.config(state="disabled")
            results_text.see(tk.END)
            logger.info("Network diagnostics completed")

        def copy_results():
            content = results_text.get("1.0", tk.END)
            dialog.clipboard_clear()
            dialog.clipboard_append(content)
            logger.info("Network diagnostic results copied")

        # Buttons
        btn_bar = tk.Frame(dialog, bg="white", pady=8)
        btn_bar.pack(fill="x", padx=15)

        tk.Button(btn_bar, text=t("diag_run"), command=lambda: threading.Thread(target=run_diagnostics, daemon=True).start(),
                  bg="#2E7D32", fg="white", font=(FONT_AR, fs(9), "bold"), bd=0, pady=6, padx=15,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(btn_bar, text=t("diag_copy_results"), command=copy_results,
                  bg="#E2E8F0", fg="#1E293B", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=15,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(btn_bar, text=t("firewall_copy"), command=self._copy_firewall_commands,
                  bg="#EFF6FF", fg="#2563EB", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        tk.Button(btn_bar, text=t("firewall_fix"), command=self._run_firewall_fix,
                  bg="#FEF2F2", fg="#991B1B", font=(FONT_AR, fs(9)), bd=0, pady=6, padx=12,
                  cursor="hand2").pack(side="right" if LANG=="ar" else "left", padx=3)

        scrollbar = tk.Scrollbar(dialog, command=results_text.yview)
        results_text.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        results_text.pack(fill="both", expand=True, padx=5, pady=5)

    def _firewall_commands(self):
        return [
            f'netsh advfirewall firewall add rule name="Wameed TCP {PORT_WS}" dir=in action=allow protocol=TCP localport={PORT_WS}',
            f'netsh advfirewall firewall add rule name="Wameed UDP {PORT_UDP} In" dir=in action=allow protocol=UDP localport={PORT_UDP}',
            f'netsh advfirewall firewall add rule name="Wameed UDP {PORT_UDP} Out" dir=out action=allow protocol=UDP localport={PORT_UDP}',
            'netsh advfirewall firewall add rule name="Wameed TCP 7789 Out" dir=out action=allow protocol=TCP remoteport=7789',
        ]

    def _copy_firewall_commands(self):
        commands = "\n".join(self._firewall_commands())
        self.root.clipboard_clear()
        self.root.clipboard_append(commands)
        logger.info("Firewall commands copied to clipboard")
        messagebox.showinfo("Wameed", t("firewall_copied"))

    def _run_firewall_fix(self):
        if not messagebox.askyesno("Wameed", t("firewall_confirm")):
            return
        try:
            script_path = os.path.join(APP_DATA_DIR, "wameed_firewall_fix.ps1")
            script = "\n".join(self._firewall_commands()) + "\n"
            with open(script_path, "w", encoding="utf-8") as f:
                f.write(script)
            safe_path = script_path.replace('"', '`"')
            subprocess.Popen([
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                f'Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File \\"{safe_path}\\""'
            ])
            logger.info(f"Firewall fix launched with elevation script: {script_path}")
        except Exception as e:
            logger.error(f"Failed to run firewall fix: {e}")
            report_windows_issue("firewall_fix_failed", e)
            messagebox.showerror(t("error"), str(e))

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

            if mode == 0: # ملفات
                if not self.selected_files:
                    logger.warning("⚠️ محاولة إرسال فاشلة: لم يتم اختيار ملفات")
                    messagebox.showerror(t("error"), t("select_file_first"))
                    return
            else: # نص
                txt = self.send_text_area.get("1.0", "end").strip()
                if not txt:
                    logger.warning("Send attempt failed: Text area is empty.")
                    messagebox.showerror(t("error"), t("enter_text_first"))
                    return

            self.send_status_frame.pack(fill="x", before=send_btn, pady=(4, 0))
            send_btn.config(state="disabled", text=t("sending_progress"),
                          bg="#66BB6A", cursor="watch")

            if not self._verify_device_connection(ip, timeout=2.0):
                logger.warning(f"⚠️ Preflight failed before sending to {ip}: TCP 7789 is not reachable")
                report_windows_issue("send_preflight_failed", level="warning", target_ip=ip, target_port=7789)
                device_info = {
                    "id": connected_device.get("id", "") if connected_device else "",
                    "name": device_name or (connected_device.get("name") if connected_device else ip),
                    "ip": ip
                }
                self._set_connection_state("discovered", device_info)
                self._show_inline_message(win, t("preflight_failed"), "#EF4444", duration=4000)
                self.send_status_frame.pack_forget()
                send_btn.config(state="normal", text=f"⚡ {t('send_now')}", bg="#2E7D32", cursor="hand2")
                return

            if mode == 0: # ملفات
                logger.info(f"Starting to send {len(self.selected_files)} files to {ip}")
                threading.Thread(target=self._execute_multi_send, args=(ip, self.selected_files, win, send_btn, device_name), daemon=True).start()
            else: # نص
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
                        # File transfers carry their own progress acknowledgements; websocket pings
                        # are disabled here to avoid Broken Pipe during heavy binary streaming.
                        async with websockets.connect(
                            uri,
                            open_timeout=15,
                            ping_interval=None,
                            ping_timeout=None,
                            max_size=TRANSFER_MAX_FRAME_SIZE,
                            max_queue=8,
                        ) as ws:
                            # Hello
                            await ws.send(json.dumps({
                                "type": "hello",
                                "device": socket.gethostname(),
                                "device_id": "pc_client",
                                "app_version": VERSION,
                                "protocol_version": TRANSFER_PROTOCOL_VERSION,
                            }))

                            # استلام الرد مع مهلة زمنية
                            resp_raw = await asyncio.wait_for(ws.recv(), timeout=15)
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

                                chunk_size = TRANSFER_CHUNK_SIZE
                                total_chunks = (fsize + chunk_size - 1) // chunk_size
                                transfer_id = _transfer_id_for_file(path, fsize)

                                await ws.send(json.dumps({
                                    "type": "file_meta",
                                    "protocol_version": TRANSFER_PROTOCOL_VERSION,
                                    "transfer_id": transfer_id,
                                    "direction": "windows_to_android",
                                    "filename": fname,
                                    "size": fsize,
                                    "chunks": total_chunks,
                                    "chunk_size": chunk_size,
                                }))

                                resume_offset = 0
                                pending_status = None
                                try:
                                    ready_raw = await asyncio.wait_for(ws.recv(), timeout=5)
                                    ready = json.loads(ready_raw)
                                    if ready.get("status") == "ready":
                                        resume_offset = max(0, min(int(ready.get("offset", 0)), fsize))
                                        logger.info(f"الهاتف جاهز لاستقبال {fname}; resume_offset={resume_offset}")
                                    elif ready.get("status") == "error":
                                        raise Exception(ready.get("message", "رفض الهاتف استقبال الملف"))
                                    else:
                                        pending_status = ready
                                except asyncio.TimeoutError:
                                    logger.info("لم يصل ready من الهاتف؛ المتابعة بتوافق البروتوكول القديم")

                                sent = resume_offset
                                with open(path, "rb") as f:
                                    if resume_offset:
                                        f.seek(resume_offset)
                                    while sent < fsize:
                                        chunk = f.read(chunk_size)
                                        if not chunk:
                                            break
                                        await ws.send(chunk)
                                        sent += len(chunk)
                                        pct = (sent / fsize) * 100 if fsize else 100
                                        window.after(0, lambda p=pct: self.progress_var.set(p))
                                        if sent % (16 * 1024 * 1024) < chunk_size:
                                            await asyncio.sleep(0)

                                ack_timeout = _ack_timeout_for_size(fsize)
                                ack_started = time.time()
                                last_status = time.time()
                                saved = False
                                final_resp = pending_status

                                while time.time() - ack_started < ack_timeout:
                                    if final_resp is None:
                                        try:
                                            final_resp_raw = await asyncio.wait_for(ws.recv(), timeout=30)
                                            final_resp = json.loads(final_resp_raw)
                                        except asyncio.TimeoutError:
                                            if time.time() - last_status > min(180, ack_timeout):
                                                raise TimeoutError(f"Timeout waiting for save ACK for {fname}")
                                            continue

                                    status = final_resp.get("status")
                                    if status == "progress":
                                        received = int(final_resp.get("received_bytes", final_resp.get("received", sent)) or 0)
                                        if fsize:
                                            pct = min(100, (received / fsize) * 100)
                                            window.after(0, lambda p=pct: self.progress_var.set(p))
                                        last_status = time.time()
                                    elif status == "saving":
                                        logger.info(f"الهاتف يقوم بحفظ الملف {fname}...")
                                        window.after(0, lambda n=fname: self.progress_label.config(text=t("saving_file").format(name=n)))
                                        last_status = time.time()
                                    elif status == "saved":
                                        saved = True
                                        break
                                    elif status == "error":
                                        raise Exception(final_resp.get("message", "خطأ غير معروف في الهاتف"))

                                    final_resp = None

                                if not saved:
                                    raise TimeoutError(f"Timeout waiting for 'saved' status for {fname}")

                                elapsed = time.time() - start_time
                                logger.info(f"تم إرسال {fname} بنجاح")

                                # إضافة للملفات المرسلة في السجل (تم إصلاح تمرير اسم الجهاز)
                                d_name = device_name if device_name else ip
                                self.root.after(0, lambda n=fname, p=path, dn=d_name: self.add_to_history(n, p, device_name=dn, direction="sent"))

                            logger.info(f"اكتمل إرسال جميع الملفات ({total_files}) بنجاح.")
                            # تحديث حالة الاتصال بعد الإرسال الناجح
                            d_name = device_name if device_name else ip
                            device_info = {
                                "id": connected_device.get("id", "") if connected_device else "",
                                "name": d_name,
                                "ip": ip,
                                "connected_at": datetime.now()
                            }
                            self.root.after(0, lambda: self._set_connection_state("connected", device_info))
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
                report_windows_issue("send_files_failed", e, target_ip=ip, target_port=7789, file_count=len(files))
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
                async with websockets.connect(uri, open_timeout=10) as websocket:
                    # إرسال hello
                    await websocket.send(json.dumps({
                        "type": "hello",
                        "device": socket.gethostname(),
                        "device_id": "pc_client",
                        "app_version": VERSION
                    }))

                    resp_raw = await asyncio.wait_for(websocket.recv(), timeout=10)
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
                            # تحديث حالة الاتصال بعد الإرسال الناجح
                            d_name = device_name if device_name else ip
                            device_info = {
                                "id": connected_device.get("id", "") if connected_device else "",
                                "name": d_name,
                                "ip": ip,
                                "connected_at": datetime.now()
                            }
                            self.root.after(0, lambda: self._set_connection_state("connected", device_info))

                            window.after(0, lambda: progress_var.set(100))
                            window.after(0, lambda: self._show_inline_message(window, "✅ تم إرسال النص بنجاح"))
                            break
                        if final_resp.get("status") == "error":
                            raise Exception(final_resp.get("message", "خطأ في الهاتف"))

            except Exception as e:
                logger.error(f"خطأ في إرسال النص: {e}")
                report_windows_issue("send_text_failed", e, target_ip=ip, target_port=7789, text_length=len(text))
                window.after(0, lambda: self._show_inline_message(window, f"❌ فشل الإرسال: {str(e)[:40]}", "#EF4444"))

        asyncio.run(send_text_task())

    def setup_tray(self):
        """
        Tray Icon ديناميكي:
        - tooltip يعرض IP + حالة الاتصال ويتحدث كل 15 ثانية
        - قائمة تعرض اسم الجهاز المتصل تلقائياً
        """
        # إيقاف أيقونة سابقة إن وُجدت (يمنع التكرار)
        if hasattr(self, "tray_icon") and self.tray_icon is not None:
            try:
                self.tray_icon.stop()
            except Exception:
                pass
            self.tray_icon = None
        def _status_text():
            if connection_state == "connected" and connected_device and connected_device.get("name"):
                name = connected_device["name"]
                ip   = connected_device.get("ip", "")
                return f"✅ متصل: {name} ({ip})"
            elif connection_state == "discovered" and connected_device and connected_device.get("name"):
                name = connected_device["name"]
                ip   = connected_device.get("ip", "")
                return f"🔵 مكتشف فقط: {name} ({ip})"
            elif last_connection_time:
                mins = int((datetime.now() - last_connection_time).total_seconds() / 60)
                return f"⚪ آخر اتصال: {mins} دقيقة"
            return "⚪ غير متصل"

        def on_open(icon, item):
            self.root.after(0, self.show_window)

        def on_send(icon, item):
            if not state.get("target_ip"):
                self.root.after(0, lambda: messagebox.showwarning(
                    "وميض", t("no_device_connected_error")))
                return
            self.root.after(0, self._show_send_dialog)

        def on_folder(icon, item):
            try:
                os.startfile(state["save_dir"])
            except Exception as e:
                logger.error(f"Tray folder: {e}")

        def on_quit(icon, item):
            self.quit_app()

        menu = pystray.Menu(
            item(lambda text: _status_text(), lambda i, it: None, enabled=False),
            pystray.Menu.SEPARATOR,
            item("📂 فتح وميض",        on_open,   default=True),
            item("📤 إرسال ملف",       on_send),
            item("📁 فتح مجلد الحفظ",  on_folder),
            pystray.Menu.SEPARATOR,
            item("⏹ إيقاف",            on_quit),
        )

        self.tray_icon = pystray.Icon(
            name  = "wameed",
            icon  = self.tray_image,
            title = f"وميض {VERSION} | {get_local_ip()}",
            menu  = menu,
        )

        threading.Thread(target=self.tray_icon.run, daemon=True, name="WameedTray").start()

        # تحديث tooltip كل 15 ثانية
        def _tick():
            if state["running"] and hasattr(self, "tray_icon"):
                try:
                    self.tray_icon.title = f"وميض | {get_local_ip()} | {_status_text()}"
                except Exception:
                    pass
            if state["running"]:
                self.root.after(15_000, _tick)
        self.root.after(5_000, _tick)
        logger.info("✅ Tray Icon بدأ")

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
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()

    def hide_window(self):
        self.root.withdraw()
        show_notification(APP_NAME, "وميض يعمل في الخلفية ⚡")

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
        try:
            if hasattr(self, "tray_icon") and self.tray_icon is not None:
                self.tray_icon.stop()
                self.tray_icon = None
        except Exception:
            pass
        try:
            self.root.quit()
        except Exception:
            pass
        # تأخير قصير يمنح Windows وقتاً لإزالة الأيقونة من الـ tray
        time.sleep(0.3)
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

                if mtype == "ping":
                    await websocket.send(json.dumps({"type": "pong"}))
                    continue

                if mtype == "hello":
                    device_id = data.get("device_id")
                    device_name = data.get("device")
                    logger.info(f"طلب مصافحة من جهاز: {device_name} ({device_id})")
                    is_trusted = any(d["id"] == device_id for d in state["trusted_devices"])

                    def _mark_connected(did, dname, dip):
                        device_info = {
                            "id": did,
                            "name": dname,
                            "ip": dip,
                            "connected_at": datetime.now()
                        }
                        state["target_ip"] = dip
                        save_config()
                        if hasattr(app, 'home_ip_var'):
                            app.root.after(0, lambda: app.home_ip_var.set(dip))
                        app._set_connection_state("connected", device_info)
                        app.root.after(0, lambda: app.update_device_history(did, dname))

                    if is_trusted:
                        logger.info(f"تم قبول الاتصال تلقائياً: {device_name} (جهاز موثوق)")
                        await websocket.send(json.dumps({"status": "paired"}))
                        app.root.after(0, lambda: _mark_connected(device_id, device_name, client_ip))
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
                            app.root.after(0, lambda: _mark_connected(device_id, device_name, client_ip))
                            await websocket.send(json.dumps({"status": "paired"}))
                        else:
                            logger.warning(f"تم رفض اقتران الجهاز: {device_name}")
                            await websocket.send(json.dumps({"status": "rejected", "message": "تم رفض الاقتران من المستخدم"}))

                elif mtype == "text":
                    text = data.get("text")
                    logger.info(f"استلام نص من الهاتف (الطول: {len(text)} حرف)")
                    # نسخ النص — pyperclip (أفضل مع Unicode) مع fallback لـ tkinter
                    try:
                        import pyperclip
                        pyperclip.copy(text)
                    except Exception:
                        app.root.clipboard_clear()
                        app.root.clipboard_append(text)
                    # ⚡ إرسال saved فوراً قبل الأعمال الثانوية
                    await websocket.send(json.dumps({"status": "saved"}))
                    device_name = connected_device.get("name") if connected_device else "جهاز غير معروف"
                    app.add_to_history(f"نص: {text[:30]}...", "", device_name)
                    show_notification("Wameed - نص جديد", f"تم نسخ النص إلى الحافظة تلقائياً")

                elif mtype == "url":
                    url = data.get("url")
                    logger.info(f"استلام رابط من الهاتف: {url}")
                    # ⚡ إرسال saved فوراً قبل الأعمال الثانوية
                    await websocket.send(json.dumps({"status": "saved"}))
                    device_name = connected_device.get("name") if connected_device else "جهاز غير معروف"
                    app.add_to_history(f"رابط: {url[:40]}", "", device_name)
                    if state["auto_open"]: webbrowser.open(url)

                elif mtype == "file_meta":
                    filename = _safe_filename(data.get("filename"))
                    chunks = int(data.get("chunks") or 0)
                    fsize = int(data.get("size", 0) or 0)
                    transfer_id = str(data.get("transfer_id") or f"phone-{int(time.time() * 1000)}")
                    chunk_size = int(data.get("chunk_size") or TRANSFER_CHUNK_SIZE)
                    display_mode = data.get("display_mode", "both")
                    save_dir = state["save_dir"]
                    os.makedirs(save_dir, exist_ok=True)
                    filepath = _ensure_unique_path(os.path.join(save_dir, filename))
                    part_path = f"{filepath}.part"

                    logger.info(f"بدء استقبال ملف: {filename} ({fsize} bytes) | عدد الأجزاء: {chunks} | transfer={transfer_id}")
                    start_time = time.time()
                    app.root.after(0, lambda n=filename: app._show_receive_progress(n))

                    try:
                        free_bytes = shutil.disk_usage(save_dir).free
                        if fsize > 0 and free_bytes < fsize + 64 * 1024 * 1024:
                            raise OSError("disk_full")

                        resume_offset = 0
                        if os.path.exists(part_path):
                            resume_offset = os.path.getsize(part_path)
                            if fsize > 0 and resume_offset > fsize:
                                resume_offset = 0
                            if resume_offset == 0:
                                try:
                                    os.remove(part_path)
                                except Exception:
                                    pass

                        await _send_transfer_status(
                            websocket,
                            "ready",
                            transfer_id=transfer_id,
                            offset=resume_offset,
                            received_bytes=resume_offset,
                            received=resume_offset,
                            total_chunks=chunks,
                        )

                        received = resume_offset
                        received_chunks = resume_offset // max(1, chunk_size)
                        last_ack = time.time()
                        with open(part_path, "ab" if resume_offset else "wb") as f:
                            while received_chunks < chunks:
                                chunk = await websocket.recv()
                                if isinstance(chunk, str):
                                    logger.debug(f"تجاهل رسالة تحكم أثناء استقبال الملف: {chunk[:120]}")
                                    continue
                                f.write(chunk)
                                received += len(chunk)
                                received_chunks += 1
                                now = time.time()
                                if fsize > 0:
                                    pct = min(99, int(received * 100 / fsize))
                                    elapsed = max(0.001, now - start_time)
                                    speed_mbps = (received * 8.0) / (1024.0 * 1024.0 * elapsed)
                                    app.root.after(0, lambda p=pct, s=speed_mbps: app._update_receive_progress(p, s))
                                if received_chunks % 8 == 0 or now - last_ack >= 1:
                                    await _send_transfer_status(
                                        websocket,
                                        "progress",
                                        transfer_id=transfer_id,
                                        received_bytes=received,
                                        received=received,
                                        chunk_index=received_chunks,
                                        total_chunks=chunks,
                                    )
                                    last_ack = now
                            f.flush()
                            os.fsync(f.fileno())

                        if fsize > 0 and received != fsize:
                            raise IOError(f"incomplete_file expected={fsize} received={received}")

                        await _send_transfer_status(
                            websocket,
                            "saving",
                            transfer_id=transfer_id,
                            received_bytes=received,
                            received=received,
                            total_chunks=chunks,
                        )
                        os.replace(part_path, filepath)

                        elapsed = time.time() - start_time
                        logger.info(f"تم استقبال الملف بنجاح: {filename} في {elapsed:.2f} ثانية")
                        app.root.after(0, lambda: app._update_receive_progress(100, 0.0))
                        app.root.after(0, app._hide_receive_progress)

                        await _send_transfer_status(
                            websocket,
                            "saved",
                            transfer_id=transfer_id,
                            received_bytes=received,
                            received=received,
                            path=filepath,
                            total_chunks=chunks,
                        )
                    except Exception as exc:
                        reason = "disk_full" if str(exc) == "disk_full" else type(exc).__name__
                        if reason in {"disk_full", "PermissionError"}:
                            try:
                                if os.path.exists(part_path):
                                    os.remove(part_path)
                            except Exception:
                                pass
                        report_windows_issue(
                            "receive_file_failed",
                            exc,
                            direction="android_to_windows",
                            phase="receive",
                            transfer_id=transfer_id,
                            size=fsize,
                        )
                        await _send_transfer_status(
                            websocket,
                            "error",
                            transfer_id=transfer_id,
                            reason=reason,
                            message=str(exc),
                        )
                        app.root.after(0, lambda: app._hide_receive_progress(0))
                        raise

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
                report_windows_issue("ws_message_failed", e, client_ip=client_ip, message_type=locals().get("mtype", "unknown"))
    except Exception as e:
        logger.debug(f"انتهى اتصال WebSocket مع ({client_ip}): {e}")

    # تقليل عدّاد الاتصالات النشطة
    active_connections[client_ip] = max(0, active_connections.get(client_ip, 1) - 1)
    remaining = active_connections.get(client_ip, 0)
    logger.debug(f"إغلاق اتصال من {client_ip} (متبقي: {remaining})")

    # مسح حالة الجهاز فقط إذا لم يتبقَّ أي اتصال نشط من نفس الـ IP
    if remaining == 0 and connected_device and connected_device.get("ip") == client_ip:
        logger.info(f"📴 تم قطع جميع اتصالات WS مع {connected_device.get('name', '?')} ({client_ip})")
        # لا نمسح connected_device فوراً — نترك المراقب الدوري يقرر
        # عبر فحص TCP إذا كان الجهاز لا يزال متاحاً
        if hasattr(app, 'root'):
            app.root.after(0, lambda: app._set_connection_state("unstable", connected_device))

def show_notification(title: str, message: str):
    """
    إشعار Windows Toast موثوق.
    يستخدم plyer أولاً (يدعم العربية وكل Unicode)
    ثم يرجع لـ winsound فقط إذا فشل plyer.
    """
    safe_title   = str(title)[:64]
    safe_message = str(message)[:200]

    try:
        from plyer import notification as _notif
        icon_path = get_resource_path("wameed.ico")
        _notif.notify(
            title    = safe_title,
            message  = safe_message,
            app_name = APP_NAME,
            app_icon = icon_path if os.path.exists(icon_path) else "",
            timeout  = 4,
        )
        logger.debug(f"🔔 إشعار: {safe_title}")
    except Exception as e:
        logger.warning(f"plyer notification failed: {e}")
        try:
            import winsound
            winsound.MessageBeep(winsound.MB_OK)
        except Exception:
            pass

async def run_ws_server():
    async with serve(
        handle_client,
        "0.0.0.0",
        PORT_WS,
        max_size=TRANSFER_MAX_FRAME_SIZE,
        max_queue=8,
        ping_interval=None,
        ping_timeout=None,
    ):
        await asyncio.Future()

def start_ws_thread():
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)
    loop.run_until_complete(run_ws_server())

def _get_subnet_broadcast() -> str:
    """
    يحسب عنوان broadcast الصحيح للـ subnet.
    مثال: 192.168.100.243/24 → 192.168.100.255
    """
    try:
        import ipaddress
        local_ip = get_local_ip()

        # محاولة netifaces أولاً للحصول على netmask الدقيق
        try:
            import netifaces
            for iface in netifaces.interfaces():
                addrs = netifaces.ifaddresses(iface)
                for addr in addrs.get(netifaces.AF_INET, []):
                    if addr.get("addr") == local_ip:
                        mask = addr.get("netmask", "255.255.255.0")
                        net  = ipaddress.IPv4Network(f"{local_ip}/{mask}", strict=False)
                        return str(net.broadcast_address)
        except ImportError:
            pass

        # fallback: افتراض /24
        parts = local_ip.split(".")
        if len(parts) == 4:
            return f"{parts[0]}.{parts[1]}.{parts[2]}.255"
    except Exception as e:
        logger.debug(f"_get_subnet_broadcast: {e}")
    return None


def udp_broadcast():
    """
    مستجيب UDP للـ Discovery:
    - يرد على طلبات الهاتف (wameed_phone / discovery_ping)
    - يُرسل إعلان دوري كل 30 ثانية على subnet broadcast
    - يدعم كلاً من 255.255.255.255 والـ subnet broadcast
    """
    logger.info(f"بدء تشغيل مستجيب Discovery على UDP:{PORT_UDP}")

    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.bind(("", PORT_UDP))
        sock.settimeout(1.0)

        subnet_bc   = _get_subnet_broadcast()
        last_announce = 0.0

        logger.info(f"Subnet broadcast: {subnet_bc or 'يُستخدم 255.255.255.255 فقط'}")

        def _response(peer_ip=None) -> bytes:
            return json.dumps({
                "service": "wameed_pc",
                "name":    socket.gethostname(),
                "ip":      get_local_ip_for_peer(peer_ip),
                "port":    PORT_WS,
                "version": VERSION,
                "ws_ready": True,
                "connection_state": connection_state,
            }, ensure_ascii=False).encode("utf-8")

        while state["running"]:
            try:
                # إعلان دوري: 15 ثانية عند وجود جهاز متصل، 30 ثانية بدون
                announce_interval = 15 if connected_device else 30
                now = time.time()
                if now - last_announce >= announce_interval:
                    msg = _response()
                    if subnet_bc:
                        try:
                            sock.sendto(msg, (subnet_bc, PORT_UDP))
                        except Exception as e:
                            logger.debug(f"subnet announce failed: {e}")
                    try:
                        sock.sendto(msg, ("255.255.255.255", PORT_UDP))
                    except Exception:
                        pass
                    last_announce = now

                # استقبال طلبات
                try:
                    data, addr = sock.recvfrom(2048)
                except socket.timeout:
                    continue

                sender_ip = addr[0]
                if sender_ip == get_local_ip():
                    continue  # تجاهل حزمنا

                try:
                    req = json.loads(data.decode("utf-8"))
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue

                svc      = req.get("service", "")
                req_type = req.get("type", "")

                if svc == "wameed_phone" or req_type == "discovery_ping":
                    sock.sendto(_response(sender_ip), addr)
                    logger.info(f"✅ رد Discovery → {sender_ip} ({req.get('device', '?')})")
                else:
                    logger.debug(f"UDP: تجاهل حزمة من {sender_ip} | svc={svc}")

            except Exception as e:
                if state["running"]:
                    logger.debug(f"UDP loop: {e}")

    except OSError as e:
        logger.error(f"❌ فشل bind على UDP:{PORT_UDP}: {e}")
    except Exception as e:
        logger.error(f"❌ udp_broadcast: {e}")
    finally:
        try:
            sock.close()
        except Exception:
            pass
        logger.info("مستجيب UDP توقف")

# ======================== Single Instance Lock ========================
_instance_lock_sock = None

def _acquire_instance_lock() -> bool:
    """
    يمنع تشغيل أكثر من نسخة واحدة من وميض في نفس الوقت.
    يستخدم socket بدلاً من ملف قفل — أنظف ولا يترك بقايا.
    """
    global _instance_lock_sock
    try:
        _instance_lock_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        _instance_lock_sock.bind(("127.0.0.1", 17788))  # منفذ داخلي فريد لوميض
        return True
    except OSError:
        # المنفذ مشغول → نسخة أخرى تعمل بالفعل
        return False

def _cleanup_on_exit():
    """تنظيف أيقونة الـ tray عند الخروج لأي سبب"""
    try:
        if 'app' in globals() and hasattr(app, 'tray_icon') and app.tray_icon is not None:
            app.tray_icon.stop()
    except Exception:
        pass

# ======================== Main ========================
if __name__ == "__main__":
    install_global_exception_hooks()

    # ── منع التكرار ──
    if not _acquire_instance_lock():
        # نسخة أخرى تعمل → أظهرها بدلاً من فتح نسخة جديدة
        print("وميض يعمل بالفعل! لا يمكن تشغيل نسختين.")
        try:
            root = tk.Tk()
            root.withdraw()
            messagebox.showinfo("وميض", "وميض يعمل بالفعل ⚡\nابحث عن الأيقونة في شريط المهام.")
            root.destroy()
        except Exception:
            pass
        sys.exit(0)

    atexit.register(_cleanup_on_exit)

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
        event_id = report_windows_issue("fatal_app_error", e)
        show_error_report_dialog("fatal_app_error", e, event_id=event_id)
