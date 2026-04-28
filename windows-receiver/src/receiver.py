import asyncio
import websockets
import json
import os
import sys
import io
import logging
import logging.handlers
import subprocess
import webbrowser
import socket
import threading
import time
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from threading import Thread, Event
from datetime import datetime
import shutil
import tempfile
import traceback
try:
    import winreg  # Windows-only; used for Autostart toggle
except ImportError:
    winreg = None
try:
    from zeroconf import ServiceBrowser, Zeroconf, ServiceStateChange
    HAS_ZEROCONF = True
except ImportError:
    HAS_ZEROCONF = False

try:
    from tkinterdnd2 import TkinterDnD, DND_FILES
    HAS_DND = True
except ImportError:
    HAS_DND = False

# Fix pythonw.exe / PyInstaller --noconsole crash: stdout/stderr may be None
if sys.stdout is None:
    sys.stdout = open(os.devnull, "w")
if sys.stderr is None:
    sys.stderr = open(os.devnull, "w")

import requests
from threading import Timer

VERSION = "1.1.0"
UPDATE_URL = "https://raw.githubusercontent.com/officerhasikhc/wameed/main/update.json"

def check_for_updates():
    try:
        response = requests.get(UPDATE_URL, timeout=5)
        if response.status_code == 200:
            data = response.json()
            remote_version = data['windows']['version']
            if remote_version > VERSION:
                if messagebox.askyesno("تحديث جديد", f"إصدار جديد متوفر: {remote_version}\nهل تريد تحميل التحديث الآن؟"):
                    webbrowser.open(data['windows']['updateUrl'])
    except Exception as e:
        print(f"Update check failed: {e}")

# في دالة بدء البرنامج، سنستدعي هذه الدالة بعد قليل من التشغيل
def start_update_check():
    Timer(5.0, check_for_updates).start()

# سنقوم باستدعاء start_update_check() داخل الكود الرئيسي لاحقاً
    except Exception:
        # Fallback for older Windows (7/8)
        try: ctypes.windll.user32.SetProcessDPIAware()
        except Exception: pass
except Exception:
    pass

# ======================== Logging (file + memory) ========================
# Logs go to ~/.wameed/wameed.log — CRITICAL for debugging the no-console exe.
_LOG_DIR = os.path.join(os.path.expanduser("~"), ".wameed")
os.makedirs(_LOG_DIR, exist_ok=True)
LOG_FILE = os.path.join(_LOG_DIR, "wameed.log")

logger = logging.getLogger("wameed")
logger.setLevel(logging.DEBUG)
_fmt = logging.Formatter("%(asctime)s [%(levelname)s] %(name)s: %(message)s",
                         datefmt="%Y-%m-%d %H:%M:%S")
try:
    _fh = logging.handlers.RotatingFileHandler(
        LOG_FILE, maxBytes=1_000_000, backupCount=3, encoding="utf-8")
    _fh.setFormatter(_fmt)
    logger.addHandler(_fh)
except Exception:
    pass
# Also to stderr when available (dev runs / debug build)
try:
    _sh = logging.StreamHandler()
    _sh.setFormatter(_fmt)
    logger.addHandler(_sh)
except Exception:
    pass

def _log_uncaught(exctype, value, tb):
    logger.error("UNCAUGHT EXCEPTION", exc_info=(exctype, value, tb))
sys.excepthook = _log_uncaught

logger.info("===== Wameed starting (pid=%s, platform=%s, frozen=%s) =====",
            os.getpid(), sys.platform, getattr(sys, "frozen", False))

# Fix asyncio on Windows + PyInstaller windowed mode.
# websockets + ProactorEventLoop can have subtle issues in threaded contexts;
# Selector policy is the safe, well-tested path.
if sys.platform == "win32":
    try:
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
        logger.info("asyncio policy set to WindowsSelectorEventLoopPolicy")
    except Exception:
        logger.exception("Failed to set asyncio policy")

# ======================== Single-Instance Lock ========================
SINGLE_INSTANCE_PORT = 64789

def _acquire_single_instance_lock():
    """Returns the bound socket (keeps lock alive) or None if another instance runs."""
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 0)
    try:
        s.bind(("127.0.0.1", SINGLE_INSTANCE_PORT))
        s.listen(1)
        logger.info("Single-instance lock acquired on :%d", SINGLE_INSTANCE_PORT)
        return s
    except OSError:
        logger.warning("Another Wameed instance is already running — exiting.")
        try: s.close()
        except Exception: pass
        return None

# System tray support
try:
    import pystray
    from PIL import Image as PILImage
    HAS_TRAY = True
except ImportError:
    HAS_TRAY = False

# ======================== Paths & Defaults ========================
SETTINGS_DIR = os.path.join(os.path.expanduser("~"), ".wameed")
SETTINGS_FILE = os.path.join(SETTINGS_DIR, "settings.json")
HISTORY_FILE = os.path.join(SETTINGS_DIR, "history.json")
TRUSTED_FILE = os.path.join(SETTINGS_DIR, "trusted_devices.json")
PAIRING_LOG_FILE = os.path.join(SETTINGS_DIR, "pairing_events.json")
DEFAULT_PORT = 7788
DISCOVERY_PORT = 7789
DEFAULT_SAVE_PATH = os.path.join(os.path.expanduser("~"), "Downloads", "Wameed")
APP_VERSION = "1.1.0"
PAIRING_TIMEOUT_SEC = 60

# Autostart (HKCU\Software\Microsoft\Windows\CurrentVersion\Run)
AUTOSTART_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
AUTOSTART_NAME = "Wameed"

# ======================== Bilingual Strings ========================
_current_lang = "ar"  # default; overwritten by settings.load()

STRINGS = {
    "ar": {
        "app_header":          "⚡ وميض",
        "status_starting":     "⏳ جارٍ التشغيل...",
        "status_connected":    "📱 متصل بـ {dev}{badge}",
        "status_connected_n":  "📱 متصل بـ {dev}{badge} (+{n})",
        "status_ready_sub":    "جاهز لاستقبال الملفات",
        "status_ready_ago":    "✓ جاهز — آخر ملف {ago}",
        "status_ready_sub2":   "الكمبيوتر مستعد لمشاركة جديدة",
        "status_idle":         "⏸️ جاهز لاستقبال الملفات",
        "status_idle_sub":     "شارك ملفاً من هاتفك عبر وميض",
        "status_error":        "❌ مشكلة في الاتصال",
        "status_error_dup":    "يبدو أن وميض يعمل بالفعل في نسخة أخرى",
        "status_diag_hint":    'راجع "🔍 تشخيص" لمعرفة التفاصيل',
        "pairing_request":     "🔐 طلب اقتران من {name}",
        "pairing_sub":         "راجع النافذة المنبثقة للسماح أو الرفض",
        "time_just_now":       "قبل لحظة",
        "time_min":            "قبل {n} د",
        "time_hour":           "قبل {n} س",
        "time_sec":            "قبل {n} ث",
        "time_min_long":       "قبل {n} دقيقة",
        "time_hour_long":      "قبل {n} ساعة",
        "time_sec_long":       "قبل {n} ثانية",
        "last_recv":           "✓ آخر استلام: {name}  •  {when}",
        "today_stats":         "اليوم: {n} ملف • {size}",
        "btn_open_folder":     "📂 فتح مجلد الاستلام",
        "btn_minimize":        "🔽 تصغير للشريط",
        "btn_diagnostics":     "🔍 تشخيص",
        "btn_quit":            "⏻ إنهاء البرنامج",
        "tab_home":            "  🏠 الرئيسية  ",
        "tab_history":         "  📋 السجل  ",
        "tab_settings":        "  ⚙️ الإعدادات  ",
        "btn_send":            "📤 إرسال للهاتف",
        "recent_files":        "آخر الملفات المُستلمة",
        "view_all":            "عرض الكل ←",
        "no_files_yet":        "لا توجد ملفات بعد",
        "empty_hint":          "شارك ملفاً من هاتفك عبر وميض لبدء الاستخدام",
        "no_match":            "  لا توجد ملفات مطابقة",
        "tree_file":           "  الملف",
        "tree_meta":           "الحجم • الوقت",
        "btn_clear_history":   "🗑 مسح السجل",
        "confirm_clear":       "مسح جميع سجل الملفات؟",
        "filter_all":          "الكل",
        "filter_images":       "صور",
        "filter_docs":         "مستندات",
        "filter_other":        "أخرى",
        "sect_network":        "▸ الشبكة",
        "lbl_ip":              "عنوان IP (للعرض فقط — انقر للنسخ)",
        "lbl_port":            "المنفذ (Port)",
        "sect_files":          "▸ الملفات",
        "lbl_save_path":       "مجلد الاستلام",
        "btn_change":          "تغيير",
        "lbl_display_mode":    "وضع العرض عند استلام ملف",
        "mode_open":           "فتح الملف فوراً بالتطبيق المناسب",
        "mode_path":           "عرض إشعار بمسار الملف فقط",
        "mode_both":           "فتح الملف + عرض إشعار المسار",
        "sect_trusted":        "▸ الأجهزة الموثوقة",
        "trusted_desc":        "الهواتف التي سمحت لها بالإرسال. يمكن إلغاء الثقة في أي وقت.",
        "btn_refresh":         "🔄 تحديث",
        "btn_revoke_all":      "🗑 إلغاء الثقة عن الكل",
        "no_trusted":          "لا توجد أجهزة موثوقة بعد — أول اتصال من الهاتف سيُطلب فيه الاقتران.",
        "paired_since":        "مقترن منذ {t}",
        "last_seen":           "آخر اتصال {t}",
        "revoke_confirm":      "إلغاء الثقة عن {name}؟\nسيتم سؤالك من جديد في المرة القادمة.",
        "btn_revoke":          "🗑 إلغاء",
        "revoke_all_confirm":  "إلغاء الثقة عن جميع الأجهزة؟\nسيُطلب الاقتران من كل هاتف في الاتصال القادم.",
        "sect_other":          "▸ أخرى",
        "autostart_label":     "بدء وميض تلقائياً مع ويندوز",
        "lbl_version":         "الإصدار:",
        "btn_save_settings":   "💾 حفظ الإعدادات",
        "settings_saved":      "تم حفظ الإعدادات!\nأعد التشغيل إذا غيّرت المنفذ.",
        "autostart_fail":      "تعذّر تغيير إعداد بدء التشغيل مع ويندوز.",
        "copied":              "تم النسخ للحافظة",
        "ip_copied":           "تم نسخ العنوان: {ip}",
        "tooltip_no_activity": "لم يتم استقبال أي نشاط بعد.",
        "tooltip_last":        "آخر نبضة: {age}",
        "file_not_available":  "الملف غير متاح للفتح (سجل قديم أو محذوف).",
        "old_entry":           "هذا السجل قديم — لا يحتوي على مسار ملف.",
        "file_gone":           "الملف لم يعد موجوداً:\n{path}",
        "open_fail":           "تعذّر فتح الملف تلقائياً.\nيمكنك فتحه يدوياً من:\n{path}",
        "open_folder_fail":    "تعذّر فتح المجلد: {detail}",
        "menu_reveal":         "📂 كشف في المجلد",
        "menu_open":           "🔓 فتح الملف",
        "menu_copy_path":      "📋 نسخ المسار",
        "menu_not_available":  "(الملف غير متاح)",
        "menu_remove":         "🗑 حذف من السجل فقط",
        "port_in_use":         "المنفذ {port} مستخدم — نسخة أخرى تعمل؟",
        "pair_title":          "وميض — طلب اقتران",
        "pair_header":         "طلب اقتران جديد",
        "pair_device_unknown": "جهاز غير معروف",
        "pair_address":        "العنوان: {ip}",
        "pair_body":           "يريد هذا الهاتف إرسال ملفات إلى حاسوبك للمرة الأولى.",
        "pair_hint":           "اسمح فقط إذا كنت تعرف مَن يرسل. بعد الموافقة لن تُسأل مجدداً.",
        "btn_allow":           "السماح دائماً",
        "btn_reject":          "رفض",
        "pair_rejected_msg":   "تم رفض الاقتران على الكمبيوتر",
        "pair_required_msg":   "يجب الاقتران أولاً من الكمبيوتر",
        "send_title":          "وميض — إرسال للهاتف",
        "send_header":         "📤 إرسال للهاتف",
        "send_choose_device":  "📱 اختر الجهاز المستقبل",
        "send_no_devices":     "لا توجد أجهزة متاحة.\nفعّل وضع الاستقبال على الهاتف أولاً.",
        "send_available_now":  "🟢 متاح الآن",
        "send_trusted":        "⚪ موثوق",
        "send_no_ip":          "⚫ لا يوجد عنوان",
        "send_unknown_ip":     "عنوان غير معروف",
        "send_manual_ip":      "أو أدخل عنوان IP يدوياً:",
        "send_content":        "✏️ المحتوى",
        "send_mode_file":      "📎 ملف",
        "send_mode_text":      "📝 نص",
        "send_no_file":        "لم يتم اختيار ملف",
        "send_pick_file":      "📂 اختيار ملف",
        "send_drag_hint":      "أو اسحب الملفات هنا",
        "send_add_more":       "+ إضافة ملفات",
        "send_files_counter":  "{n}/{max} ملفات",
        "send_no_files_warn":  "يرجى إضافة ملف واحد على الأقل",
        "send_max_files_warn": "الحد الأقصى {max} ملفات",
        "send_progress_multi": "ملف {cur} من {total} — {pct}%",
        "send_files_ok":       "✓ تم إرسال {n} ملف بنجاح",
        "send_files_partial":  "✓ {ok} من {total} — {failed} فشل",
        "toast_files_ok":      "تم إرسال {n} ملف بنجاح",
        "send_btn":            "⚡ إرسال",
        "send_cancel":         "إلغاء",
        "send_no_device_warn": "يرجى اختيار جهاز أو إدخال عنوان IP",
        "send_no_file_warn":   "يرجى اختيار ملف أولاً",
        "send_no_text_warn":   "يرجى كتابة النص أولاً",
        "send_progress":       "جاري الإرسال... {pct}%",
        "send_text_progress":  "جاري إرسال النص...",
        "send_file_ok":        "✓ تم الإرسال بنجاح",
        "send_text_ok":        "✓ تم إرسال النص",
        "send_fail":           "✗ فشل: {msg}",
        "toast_file_ok":       "تم إرسال الملف بنجاح",
        "toast_text_ok":       "تم إرسال النص بنجاح",
        "sender_file_missing": "الملف غير موجود",
        "sender_unreachable":  "لا يمكن الوصول للهاتف ({ip}:{port}).\nتأكد أن وضع الاستقبال مفعّل على الهاتف.",
        "sender_pair_fail":    "فشل الاتصال: {msg}",
        "sender_pair_fail2":   "فشل الاتصال",
        "sender_rejected":     "تم الرفض",
        "sender_chunk_error":  "خطأ أثناء الإرسال",
        "sender_ok":           "تم الإرسال بنجاح",
        "sender_timeout":      "انتهت مهلة الاتصال بالهاتف ({ip}:{port}).\nتأكد أن وضع الاستقبال مفعّل.",
        "sender_text_ok":      "تم إرسال النص",
        "sender_save_fail":    "فشل الحفظ",
        "text_copied":         "✓ تم نسخ النص للحافظة",
        "url_opened":          "✓ تم فتح الرابط",
        "toast_new_text":      "وميض — نص جديد",
        "recv_file":           "تم استلام: {name}",
        "recv_save_fail":      "تم حفظ: {name}",
        "recv_save_fail_body": "تعذّر الفتح التلقائي — الملف محفوظ في:\n{path}",
        "recv_saving":         "💾 جارٍ الحفظ...",
        "recv_incoming":       "📥 جارٍ الاستلام: {name}",
        "tray_open":           "فتح وميض",
        "tray_folder":         "فتح مجلد الاستلام",
        "tray_quit":           "إيقاف",
        "toast_click_hint":    "👆 اضغط لفتح مجلد الملف",
        "confirm_quit":        "سيتوقف استقبال الملفات من الهاتف.\nهل تريد إنهاء البرنامج فعلاً؟\n\n(يمكنك بدلاً من ذلك تصغيره للشريط.)",
        "error_title":         "وميض — خطأ",
        "error_startup":       "خطأ في التشغيل:\n{e}",
        "already_running":     "وميض يعمل بالفعل. افتحه من شريط المهام (tray).",
        "fatal_error":         "حدث خطأ أثناء التشغيل:\n{tb}\n\nراجع:\n{log}",
        "diag_title":          "وميض — تشخيص الحالة",
        "diag_window_title":   "تشخيص وميض",
        "diag_version":        "الإصدار:",
        "diag_backend_err":    "لا يوجد",
        "diag_port_yes":       "✓ نعم",
        "diag_port_no":        "✗ لا — الخادم لا يستمع!",
        "diag_fw_present":     "✓ موجودة",
        "diag_fw_missing":     "✗ مفقودة",
        "diag_trusted_header": "--- الأجهزة الموثوقة ---",
        "diag_none":           "  (لا يوجد)",
        "diag_pairing_header": "--- آخر أحداث الاقتران ---",
        "btn_copy_report":     "📋 نسخ التقرير",
        "btn_open_log":        "📄 فتح ملف اللوج",
        "btn_close":           "إغلاق",
        "report_copied":       "تم نسخ التقرير للحافظة",
        "log_open_fail":       "تعذّر فتح اللوج: {detail}",
        "device_generic":      "جهاز",
        "log_text":            "نص",
        "sect_language":       "▸ اللغة / Language",
        "lang_label":          "العربية ↔ English",
        "btn_lang_toggle":     "E",
    },
    "en": {
        "app_header":          "⚡ Wameed",
        "status_starting":     "⏳ Starting...",
        "status_connected":    "📱 Connected to {dev}{badge}",
        "status_connected_n":  "📱 Connected to {dev}{badge} (+{n})",
        "status_ready_sub":    "Ready to receive files",
        "status_ready_ago":    "✓ Ready — last file {ago}",
        "status_ready_sub2":   "PC is ready for a new share",
        "status_idle":         "⏸️ Ready to receive files",
        "status_idle_sub":     "Share a file from your phone via Wameed",
        "status_error":        "❌ Connection problem",
        "status_error_dup":    "Another instance of Wameed seems to be running",
        "status_diag_hint":    'Check "🔍 Diagnostics" for details',
        "pairing_request":     "🔐 Pairing request from {name}",
        "pairing_sub":         "Check the popup to allow or deny",
        "time_just_now":       "just now",
        "time_min":            "{n} min ago",
        "time_hour":           "{n} hrs ago",
        "time_sec":            "{n} sec ago",
        "time_min_long":       "{n} minutes ago",
        "time_hour_long":      "{n} hours ago",
        "time_sec_long":       "{n} seconds ago",
        "last_recv":           "✓ Last received: {name}  •  {when}",
        "today_stats":         "Today: {n} files • {size}",
        "btn_open_folder":     "📂 Open Received Folder",
        "btn_minimize":        "🔽 Minimize to Tray",
        "btn_diagnostics":     "🔍 Diagnostics",
        "btn_quit":            "⏻ Quit",
        "tab_home":            "  🏠 Home  ",
        "tab_history":         "  📋 History  ",
        "tab_settings":        "  ⚙️ Settings  ",
        "btn_send":            "📤 Send to Phone",
        "recent_files":        "Recently Received Files",
        "view_all":            "View All →",
        "no_files_yet":        "No files yet",
        "empty_hint":          "Share a file from your phone via Wameed to get started",
        "no_match":            "  No matching files",
        "tree_file":           "  File",
        "tree_meta":           "Size • Time",
        "btn_clear_history":   "🗑 Clear History",
        "confirm_clear":       "Clear all file history?",
        "filter_all":          "All",
        "filter_images":       "Images",
        "filter_docs":         "Docs",
        "filter_other":        "Other",
        "sect_network":        "▸ Network",
        "lbl_ip":              "IP address (read-only — click to copy)",
        "lbl_port":            "Port",
        "sect_files":          "▸ Files",
        "lbl_save_path":       "Save Folder",
        "btn_change":          "Change",
        "lbl_display_mode":    "Action when a file is received",
        "mode_open":           "Open file immediately with default app",
        "mode_path":           "Show file path notification only",
        "mode_both":           "Open file + show path notification",
        "sect_trusted":        "▸ Trusted Devices",
        "trusted_desc":        "Phones you allowed to send. You can revoke trust anytime.",
        "btn_refresh":         "🔄 Refresh",
        "btn_revoke_all":      "🗑 Revoke All",
        "no_trusted":          "No trusted devices yet — the first phone connection will ask for pairing.",
        "paired_since":        "Paired since {t}",
        "last_seen":           "Last seen {t}",
        "revoke_confirm":      "Revoke trust for {name}?\nYou will be asked again next time.",
        "btn_revoke":          "🗑 Revoke",
        "revoke_all_confirm":  "Revoke trust for all devices?\nEvery phone will need to pair again.",
        "sect_other":          "▸ Other",
        "autostart_label":     "Start Wameed automatically with Windows",
        "lbl_version":         "Version:",
        "btn_save_settings":   "💾 Save Settings",
        "settings_saved":      "Settings saved!\nRestart if you changed the port.",
        "autostart_fail":      "Could not change the autostart setting.",
        "copied":              "Copied to clipboard",
        "ip_copied":           "Address copied: {ip}",
        "tooltip_no_activity": "No activity received yet.",
        "tooltip_last":        "Last heartbeat: {age}",
        "file_not_available":  "File not available to open (old or deleted entry).",
        "old_entry":           "This is an old entry — no file path stored.",
        "file_gone":           "File no longer exists:\n{path}",
        "open_fail":           "Could not open file automatically.\nYou can open it manually from:\n{path}",
        "open_folder_fail":    "Could not open folder: {detail}",
        "menu_reveal":         "📂 Reveal in Folder",
        "menu_open":           "🔓 Open File",
        "menu_copy_path":      "📋 Copy Path",
        "menu_not_available":  "(File not available)",
        "menu_remove":         "🗑 Remove from History",
        "port_in_use":         "Port {port} is in use — another instance running?",
        "pair_title":          "Wameed — Pairing Request",
        "pair_header":         "New Pairing Request",
        "pair_device_unknown": "Unknown device",
        "pair_address":        "Address: {ip}",
        "pair_body":           "This phone wants to send files to your PC for the first time.",
        "pair_hint":           "Allow only if you know who is sending. You won't be asked again after approval.",
        "btn_allow":           "Always Allow",
        "btn_reject":          "Reject",
        "pair_rejected_msg":   "Pairing rejected on PC",
        "pair_required_msg":   "Pairing required from PC first",
        "send_title":          "Wameed — Send to Phone",
        "send_header":         "📤 Send to Phone",
        "send_choose_device":  "📱 Choose receiving device",
        "send_no_devices":     "No devices available.\nEnable receiving mode on the phone first.",
        "send_available_now":  "🟢 Available now",
        "send_trusted":        "⚪ Trusted",
        "send_no_ip":          "⚫ No address",
        "send_unknown_ip":     "Unknown address",
        "send_manual_ip":      "Or enter IP address manually:",
        "send_content":        "✏️ Content",
        "send_mode_file":      "📎 File",
        "send_mode_text":      "📝 Text",
        "send_no_file":        "No file selected",
        "send_pick_file":      "📂 Choose File",
        "send_drag_hint":      "or drag files here",
        "send_add_more":       "+ Add Files",
        "send_files_counter":  "{n}/{max} files",
        "send_no_files_warn":  "Please add at least one file",
        "send_max_files_warn": "Maximum {max} files allowed",
        "send_progress_multi": "File {cur} of {total} — {pct}%",
        "send_files_ok":       "✓ {n} file(s) sent successfully",
        "send_files_partial":  "✓ {ok} of {total} — {failed} failed",
        "toast_files_ok":      "{n} file(s) sent successfully",
        "send_btn":            "⚡ Send",
        "send_cancel":         "Cancel",
        "send_no_device_warn": "Please choose a device or enter an IP address",
        "send_no_file_warn":   "Please choose a file first",
        "send_no_text_warn":   "Please enter text first",
        "send_progress":       "Sending... {pct}%",
        "send_text_progress":  "Sending text...",
        "send_file_ok":        "✓ Sent successfully",
        "send_text_ok":        "✓ Text sent",
        "send_fail":           "✗ Failed: {msg}",
        "toast_file_ok":       "File sent successfully",
        "toast_text_ok":       "Text sent successfully",
        "sender_file_missing": "File not found",
        "sender_unreachable":  "Cannot reach phone ({ip}:{port}).\nMake sure receiving mode is enabled on the phone.",
        "sender_pair_fail":    "Connection failed: {msg}",
        "sender_pair_fail2":   "Connection failed",
        "sender_rejected":     "Rejected",
        "sender_chunk_error":  "Error during send",
        "sender_ok":           "Sent successfully",
        "sender_timeout":      "Connection to phone timed out ({ip}:{port}).\nMake sure receiving mode is enabled.",
        "sender_text_ok":      "Text sent",
        "sender_save_fail":    "Save failed",
        "text_copied":         "✓ Text copied to clipboard",
        "url_opened":          "✓ Link opened",
        "toast_new_text":      "Wameed — New Text",
        "recv_file":           "Received: {name}",
        "recv_save_fail":      "Saved: {name}",
        "recv_save_fail_body": "Could not auto-open — file saved at:\n{path}",
        "recv_saving":         "💾 Saving...",
        "recv_incoming":       "📥 Receiving: {name}",
        "tray_open":           "Open Wameed",
        "tray_folder":         "Open Received Folder",
        "tray_quit":           "Quit",
        "toast_click_hint":    "👆 Click to open file folder",
        "confirm_quit":        "File receiving will stop.\nDo you really want to quit?\n\n(You can minimize to tray instead.)",
        "error_title":         "Wameed — Error",
        "error_startup":       "Startup error:\n{e}",
        "already_running":     "Wameed is already running. Open it from the system tray.",
        "fatal_error":         "An error occurred:\n{tb}\n\nSee:\n{log}",
        "diag_title":          "Wameed — Diagnostics",
        "diag_window_title":   "Wameed Diagnostics",
        "diag_version":        "Version:",
        "diag_backend_err":    "None",
        "diag_port_yes":       "✓ Yes",
        "diag_port_no":        "✗ No — server is not listening!",
        "diag_fw_present":     "✓ Present",
        "diag_fw_missing":     "✗ Missing",
        "diag_trusted_header": "--- Trusted Devices ---",
        "diag_none":           "  (none)",
        "diag_pairing_header": "--- Recent Pairing Events ---",
        "btn_copy_report":     "📋 Copy Report",
        "btn_open_log":        "📄 Open Log File",
        "btn_close":           "Close",
        "report_copied":       "Report copied to clipboard",
        "log_open_fail":       "Could not open log: {detail}",
        "device_generic":      "Device",
        "log_text":            "Text",
        "sect_language":       "▸ Language / اللغة",
        "lang_label":          "English ↔ العربية",
        "btn_lang_toggle":     "ع",
    },
}

def t(key, **kwargs):
    """Return the localized string for *key*. Placeholders use str.format()."""
    s = STRINGS.get(_current_lang, STRINGS["ar"]).get(key)
    if s is None:
        s = STRINGS["ar"].get(key, key)
    if kwargs:
        try:
            return s.format(**kwargs)
        except (KeyError, IndexError):
            return s
    return s

# ======================== Settings ========================
class WameedSettings:
    def __init__(self):
        global _current_lang
        self.port = DEFAULT_PORT
        self.save_path = DEFAULT_SAVE_PATH
        self.display_mode = "both"  # "open" | "path" | "both"
        self.language = "ar"        # "ar" | "en"
        os.makedirs(SETTINGS_DIR, exist_ok=True)
        os.makedirs(self.save_path, exist_ok=True)
        self.load()
        _current_lang = self.language

    def load(self):
        global _current_lang
        try:
            if os.path.exists(SETTINGS_FILE):
                with open(SETTINGS_FILE, "r", encoding="utf-8") as f:
                    d = json.load(f)
                self.port = d.get("port", self.port)
                self.save_path = d.get("save_path", self.save_path)
                self.display_mode = d.get("display_mode", self.display_mode)
                self.language = d.get("language", self.language)
                _current_lang = self.language
        except Exception:
            pass

    def save(self):
        try:
            with open(SETTINGS_FILE, "w", encoding="utf-8") as f:
                json.dump({"port": self.port, "save_path": self.save_path,
                           "display_mode": self.display_mode,
                           "language": self.language}, f, ensure_ascii=False, indent=2)
        except Exception:
            pass


# ======================== Trusted Devices ========================
class TrustedDevices:
    """Manages trusted_devices.json — devices that the user approved once
    and therefore can send without further prompts. Keyed by device_id (UUID).

    File format (dict, human-friendly):
    {
       "<device_id>": {"name": "Galaxy S22",
                        "first_paired": "YYYY-mm-dd HH:MM:SS",
                        "last_seen": "YYYY-mm-dd HH:MM:SS"}
    }
    """
    def __init__(self):
        self.devices = {}
        self.load()

    def load(self):
        try:
            if os.path.exists(TRUSTED_FILE):
                with open(TRUSTED_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if isinstance(data, dict):
                    self.devices = data
                elif isinstance(data, list):
                    # tolerate older list-based format
                    for d in data:
                        did = d.get("device_id")
                        if did:
                            self.devices[did] = {
                                "name": d.get("name", "?"),
                                "first_paired": d.get("first_paired", ""),
                                "last_seen": d.get("last_seen", ""),
                            }
                # Clean up duplicates on load
                self._deduplicate_by_name()
        except Exception:
            logger.exception("Failed to load trusted devices")
            self.devices = {}

    def _deduplicate_by_name(self):
        """Merge entries that share the same device name, keeping the best IP
        and the most-recent last_seen. Removes legacy/stale duplicates."""
        by_name = {}  # name -> [(device_id, info)]
        for did, info in self.devices.items():
            name = (info.get("name") or "?").strip().lower()
            by_name.setdefault(name, []).append((did, info))

        merged = {}
        changed = False
        for name, entries in by_name.items():
            if len(entries) == 1:
                merged[entries[0][0]] = entries[0][1]
                continue
            # Multiple entries for same name — pick best
            changed = True
            # Sort: prefer entries with last_ip, then by most recent last_seen
            entries.sort(key=lambda kv: (
                bool(kv[1].get("last_ip")),
                kv[1].get("last_seen", "")
            ), reverse=True)
            best_did, best_info = entries[0]
            # Collect best IP from all entries
            if not best_info.get("last_ip"):
                for _, info in entries[1:]:
                    if info.get("last_ip"):
                        best_info["last_ip"] = info["last_ip"]
                        break
            # Use earliest first_paired
            all_paired = [e[1].get("first_paired", "") for e in entries if e[1].get("first_paired")]
            if all_paired:
                best_info["first_paired"] = min(all_paired)
            merged[best_did] = best_info
            logger.info("Deduplicated %d entries for '%s' → kept %s",
                        len(entries), name, best_did[:12])

        if changed:
            self.devices = merged
            Thread(target=self._save, daemon=True).start()

    def _save(self):
        try:
            with open(TRUSTED_FILE, "w", encoding="utf-8") as f:
                json.dump(self.devices, f, ensure_ascii=False, indent=2)
        except Exception:
            logger.exception("Failed to save trusted devices")

    def is_trusted(self, device_id):
        return bool(device_id) and device_id in self.devices

    def trust(self, device_id, name, ip=None):
        now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        self.devices[device_id] = {
            "name": (name or "?").strip()[:40] or "?",
            "first_paired": now, "last_seen": now,
            "last_ip": ip or ""
        }
        Thread(target=self._save, daemon=True).start()

    def touch(self, device_id, name=None, ip=None):
        if device_id in self.devices:
            self.devices[device_id]["last_seen"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            if name:
                self.devices[device_id]["name"] = name.strip()[:40]
            if ip:
                self.devices[device_id]["last_ip"] = ip
            Thread(target=self._save, daemon=True).start()

    def revoke(self, device_id):
        if device_id in self.devices:
            del self.devices[device_id]
            Thread(target=self._save, daemon=True).start()
            return True
        return False

    def revoke_all(self):
        self.devices = {}
        Thread(target=self._save, daemon=True).start()

    def ordered(self):
        """Return [(device_id, info)] sorted by most recently seen first."""
        return sorted(self.devices.items(),
                      key=lambda kv: kv[1].get("last_seen", ""), reverse=True)

    def deduplicated_for_send(self):
        """Return one entry per unique device name for the send dialog.
        Prefers entries with a known last_ip. Returns [(device_id, info)]."""
        seen_names = set()
        result = []
        for did, info in self.ordered():
            name = (info.get("name") or "?").strip().lower()
            if name in seen_names:
                continue
            seen_names.add(name)
            result.append((did, info))
        return result


# ======================== Pairing Event Log ========================
class PairingEventLog:
    """Append-only log of the last 50 pairing-related events
    (approve / reject / timeout / blocked). Surfaced in diagnostics."""
    MAX = 50

    def __init__(self):
        self.events = []
        self.load()

    def load(self):
        try:
            if os.path.exists(PAIRING_LOG_FILE):
                with open(PAIRING_LOG_FILE, "r", encoding="utf-8") as f:
                    self.events = json.load(f) or []
        except Exception:
            self.events = []

    def add(self, action, name, ip, device_id=""):
        entry = {
            "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "action": action, "name": name, "ip": ip,
            "device_id": (device_id or "")[:12],
        }
        self.events.insert(0, entry)
        self.events = self.events[:self.MAX]
        Thread(target=self._save, daemon=True).start()

    def _save(self):
        try:
            with open(PAIRING_LOG_FILE, "w", encoding="utf-8") as f:
                json.dump(self.events, f, ensure_ascii=False, indent=2)
        except Exception:
            pass


# ======================== History ========================
class FileHistory:
    def __init__(self):
        self.entries = []
        self.load()

    def load(self):
        try:
            if os.path.exists(HISTORY_FILE):
                with open(HISTORY_FILE, "r", encoding="utf-8") as f:
                    self.entries = json.load(f)
        except Exception:
            self.entries = []

    def add(self, filename, filetype, size, status="success", path=""):
        self.entries.insert(0, {
            "filename": filename, "type": filetype, "size": size,
            "status": status, "path": path or "",
            "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        })
        self.entries = self.entries[:200]
        Thread(target=self._save, daemon=True).start()

    def _save(self):
        try:
            with open(HISTORY_FILE, "w", encoding="utf-8") as f:
                json.dump(self.entries, f, ensure_ascii=False, indent=2)
        except Exception:
            pass


# ======================== Phone Discovery (mDNS) ========================
class PhoneDiscovery:
    """Discovers phones advertising _wameed._tcp via mDNS/zeroconf.
    Thread-safe: discovered phones dict can be read from Tk main thread."""

    def __init__(self):
        self.phones = {}  # {name: {"ip": ..., "port": ..., "seen": time.time()}}
        self._zc = None
        self._browser = None
        self._lock = threading.Lock()

    def start(self):
        if not HAS_ZEROCONF:
            logger.info("zeroconf not available — mDNS phone discovery disabled")
            return
        try:
            self._zc = Zeroconf()
            self._browser = ServiceBrowser(self._zc, "_wameed._tcp.local.", handlers=[self._on_change])
            logger.info("mDNS phone discovery started")
        except Exception:
            logger.exception("Failed to start mDNS discovery")

    def stop(self):
        try:
            if self._browser:
                self._browser.cancel()
            if self._zc:
                self._zc.close()
        except Exception:
            pass

    def _on_change(self, zeroconf, service_type, name, state_change):
        if state_change == ServiceStateChange.Added or state_change == ServiceStateChange.Updated:
            info = zeroconf.get_service_info(service_type, name)
            if info:
                addresses = info.parsed_scoped_addresses()
                if addresses:
                    ip = addresses[0]
                    port = info.port
                    sname = info.server.rstrip(".")
                    # Use service name (device name) as key
                    display = name.replace("._wameed._tcp.local.", "").strip()
                    with self._lock:
                        self.phones[display] = {"ip": ip, "port": port, "seen": time.time(), "server": sname}
                    logger.info("mDNS discovered phone: %s @ %s:%d", display, ip, port)
        elif state_change == ServiceStateChange.Removed:
            display = name.replace("._wameed._tcp.local.", "").strip()
            with self._lock:
                self.phones.pop(display, None)
            logger.info("mDNS phone removed: %s", display)

    def get_phones(self):
        """Return a snapshot of discovered phones [{name, ip, port, fresh}]."""
        now = time.time()
        with self._lock:
            return [
                {"name": n, "ip": d["ip"], "port": d["port"],
                 "fresh": (now - d["seen"]) < 10}
                for n, d in self.phones.items()
            ]


# ======================== Sender ========================
class WameedSender:
    """Initiates connections to the Android receiver server."""
    def __init__(self, app):
        self.app = app
        self.active_transfer = False

    @staticmethod
    def _tcp_reachable(ip, port, timeout=3):
        """Quick TCP check — returns True if we can open a socket."""
        try:
            s = socket.create_connection((ip, port), timeout=timeout)
            s.close()
            return True
        except Exception:
            return False

    async def send_file(self, ip, port, filepath, progress_callback=None):
        if not os.path.exists(filepath):
            return False, t("sender_file_missing")

        if not self._tcp_reachable(ip, port):
            return False, t("sender_unreachable", ip=ip, port=port)

        filename = os.path.basename(filepath)
        filesize = os.path.getsize(filepath)

        uri = f"ws://{ip}:{port}"
        try:
            async with websockets.connect(
                    uri, max_size=None, open_timeout=10,
                    ping_interval=20, ping_timeout=15) as ws:
                # 1. Hello
                await ws.send(json.dumps({
                    "type": "hello",
                    "device": socket.gethostname(),
                    "device_id": "pc-" + socket.gethostname() # Simple PC ID
                }))

                # Wait for pairing/hello response
                resp = json.loads(await ws.recv())
                if resp.get("status") == "pairing_required":
                    # Wait for "paired" status
                    resp = json.loads(await ws.recv())

                if resp.get("status") != "paired" and resp.get("status") != "hello":
                    return False, t("sender_pair_fail", msg=resp.get('message', t('sender_rejected')))

                # 2. File Meta
                # Calculate chunks
                chunk_size = 1024 * 1024 # 1MB chunks
                chunks_count = (filesize + chunk_size - 1) // chunk_size

                await ws.send(json.dumps({
                    "type": "file_meta",
                    "filename": filename,
                    "size": filesize,
                    "chunks": chunks_count
                }))

                # 3. Binary Data
                sent = 0
                with open(filepath, "rb") as f:
                    for i in range(chunks_count):
                        chunk = f.read(chunk_size)
                        if not chunk: break
                        await ws.send(chunk)
                        sent += len(chunk)
                        if progress_callback:
                            progress_callback(sent, filesize)

                        # Wait for periodic progress acks or final saved
                        if (i + 1) % 5 == 0 or (i + 1) == chunks_count:
                            ack = json.loads(await ws.recv())
                            if ack.get("status") == "error":
                                return False, ack.get("message", t("sender_chunk_error"))

                return True, t("sender_ok")
        except TimeoutError:
            return False, t("sender_timeout", ip=ip, port=port)
        except Exception as e:
            logger.exception("Sender error")
            return False, str(e)

    async def send_text(self, ip, port, text):
        if not self._tcp_reachable(ip, port):
            return False, t("sender_unreachable", ip=ip, port=port)

        uri = f"ws://{ip}:{port}"
        try:
            async with websockets.connect(
                    uri, open_timeout=10,
                    ping_interval=20, ping_timeout=15) as ws:
                await ws.send(json.dumps({
                    "type": "hello",
                    "device": socket.gethostname(),
                    "device_id": "pc-" + socket.gethostname()
                }))
                resp = json.loads(await ws.recv())
                if resp.get("status") == "pairing_required":
                    resp = json.loads(await ws.recv())

                if resp.get("status") not in ("paired", "hello"):
                    return False, t("sender_pair_fail2")

                await ws.send(json.dumps({
                    "type": "text",
                    "text": text
                }))

                ack = json.loads(await ws.recv())
                if ack.get("status") == "saved":
                    return True, t("sender_text_ok")
                return False, ack.get("message", t("sender_save_fail"))
        except TimeoutError:
            return False, t("sender_timeout", ip=ip, port=port)
        except Exception as e:
            return False, str(e)


# ======================== App ========================
class WameedApp:
    def __init__(self, root):
        self.root = root
        self.root.title(t("app_header") + " — Wameed Receiver")
        self.root.geometry("560x620")
        self.root.configure(bg="#F8FAFC")
        self.root.minsize(460, 520)

        self.settings = WameedSettings()
        self.history = FileHistory()
        self.trusted = TrustedDevices()
        self.pairing_log = PairingEventLog()
        self.sender = WameedSender(self)
        self.phone_discovery = PhoneDiscovery()
        self.phone_discovery.start()
        self.tray_icon = None
        # device_id -> {"event": threading.Event, "approved": bool}
        # Used to bridge Tk modal dialog and the asyncio WS handler.
        self._pairing_waits = {}
        # Transient "waiting for approval" indicator on the status card.
        self._pending_pair_name = ""

        # Live state
        self.active_clients = 0
        self.last_received = None  # (filename, timestamp)
        self.server_running = False
        # Name of the phone currently connected (from "hello" handshake).
        # Empty string = no known name (fallback displays a generic label).
        self.connected_device_name = ""
        # device_id of the current/last phone. Used to render a "trusted" badge.
        self.connected_device_id = ""
        self.last_activity_at = 0.0
        # Grace window in seconds: UI will show "متصل" while
        # (now - last_activity_at) < this, even if active_clients == 0.
        self.connection_grace_sec = 8.0  # Reduced to prevent showing stale connection
        self.last_file_received_at = 0.0
        self.recent_activity_window_sec = 60.0
        # UDP broadcast is GATED by this flag. If WS server fails, UDP stays silent
        # — preventing the phone from discovering a half-dead server.
        self.ws_alive = Event()

        try:
            self._set_icon()
            self._build_ui()
            # Force an initial update to ensure widgets are drawn before potentially blocking threads
            self.root.update()
            self._init_tray()
            self._start_backend()
        except Exception as e:
            messagebox.showerror(t("error_title"), t("error_startup", e=e))

        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

        # Force window to appear on top
        self.root.deiconify()
        self.root.lift()
        self.root.focus_force()
        self.root.attributes('-topmost', True)
        self.root.after(500, lambda: self.root.attributes('-topmost', False))

        # ---------- Keyboard shortcuts ----------
        # Keep these conservative so they don't fight the user.
        def _nb_select(i):
            try: self.nb.select(i)
            except Exception: pass
        self.root.bind("<Control-Key-1>", lambda _e: _nb_select(0))
        self.root.bind("<Control-Key-2>", lambda _e: _nb_select(1))
        self.root.bind("<Control-Key-3>", lambda _e: _nb_select(2))
        self.root.bind("<Control-o>", lambda _e: self._open_folder())
        self.root.bind("<Control-d>", lambda _e: self._show_diagnostics())
        self.root.bind("<Escape>", lambda _e: self._minimize_to_tray())

    # ---------- Window icon ----------
    def _set_icon(self):
        try:
            ico = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wameed.ico")
            if os.path.exists(ico):
                self.root.iconbitmap(ico)
        except Exception:
            pass

    # ---------- UI ----------
    def _build_ui(self):
        # ── Header ──────────────────────────────────────────────
        hdr = tk.Frame(self.root, bg="#2E7D32", height=56)
        hdr.pack(fill="x"); hdr.pack_propagate(False)
        inner = tk.Frame(hdr, bg="#2E7D32"); inner.pack(expand=True)
        tk.Label(inner, text=t("app_header"), bg="#2E7D32", fg="white",
                 font=("Segoe UI", 17, "bold")).pack(side="left", padx=6)
        tk.Label(inner, text=f"v{APP_VERSION}", bg="#2E7D32", fg="#A5D6A7",
                 font=("Segoe UI", 9)).pack(side="left")

        # ── Bottom action bar (pack BEFORE notebook so it stays visible) ─
        bf = tk.Frame(self.root, bg="#F1F5F9",
                      highlightthickness=1, highlightbackground="#E2E8F0")
        bf.pack(side="bottom", fill="x")

        btn_primary   = dict(bd=0, padx=18, pady=9,
                             font=("Segoe UI", 10, "bold"), cursor="hand2")
        btn_secondary = dict(bd=0, padx=14, pady=9,
                             font=("Segoe UI", 10), cursor="hand2")
        btn_icon      = dict(bd=0, padx=10, pady=7,
                             font=("Segoe UI", 10), cursor="hand2")

        tk.Button(bf, text=t("btn_open_folder"), command=self._open_folder,
                  bg="#2E7D32", fg="white",
                  activebackground="#1B5E20", activeforeground="white",
                  **btn_primary).pack(side="right", padx=(6, 10), pady=7)

        tk.Button(bf, text=t("btn_minimize"), command=self._minimize_to_tray,
                  bg="#E2E8F0", fg="#374151",
                  activebackground="#CBD5E1",
                  **btn_secondary).pack(side="right", padx=3, pady=7)

        tk.Button(bf, text=t("btn_diagnostics"), command=self._show_diagnostics,
                  bg="#F1F5F9", fg="#94A3B8",
                  activebackground="#E2E8F0",
                  **btn_icon).pack(side="left", padx=(10, 3), pady=7)

        tk.Button(bf, text=t("btn_quit"), command=self._confirm_quit,
                  bg="#F1F5F9", fg="#FCA5A5",
                  activebackground="#FEE2E2",
                  **btn_icon).pack(side="left", padx=3, pady=7)

        # ── Notebook ──────────────────────────────────────────────
        style = ttk.Style()
        style.configure("TNotebook", background="#F8FAFC")
        style.configure("TNotebook.Tab",
                        font=("Segoe UI", 10), padding=[16, 5])

        self.nb = ttk.Notebook(self.root)
        self.nb.pack(fill="both", expand=True, padx=12, pady=(8, 4))

        home = tk.Frame(self.nb, bg="white")
        self.nb.add(home, text=t("tab_home"))
        self._build_home(home)

        ht = tk.Frame(self.nb, bg="white")
        self.nb.add(ht, text=t("tab_history"))
        self._build_history(ht)

        st = tk.Frame(self.nb, bg="white")
        self.nb.add(st, text=t("tab_settings"))
        self._build_settings(st)

    def _build_home(self, parent):
        """Home tab: status card → recent files → send button."""

        # ── بطاقة الحالة ───────────────────────────────────────
        card_wrap = tk.Frame(parent, bg="white")
        card_wrap.pack(fill="x", padx=14, pady=(14, 8))

        self.status_frame = tk.Frame(card_wrap, bg="#F8FAFC",
                                     highlightthickness=1,
                                     highlightbackground="#E2E8F0")
        self.status_frame.pack(fill="x")

        self.status_accent = tk.Frame(self.status_frame, bg="#94A3B8", width=4)
        self.status_accent.pack(side="right", fill="y")

        inner = tk.Frame(self.status_frame, bg="#F8FAFC")
        inner.pack(side="right", fill="both", expand=True, padx=16, pady=14)

        row = tk.Frame(inner, bg="#F8FAFC"); row.pack(fill="x")
        self.status_dot = tk.Label(row, text="●", fg="#94A3B8", bg="#F8FAFC",
                                   font=("Segoe UI", 18))
        self.status_dot.pack(side="left", padx=(0, 10))

        title_col = tk.Frame(row, bg="#F8FAFC")
        title_col.pack(side="left", fill="x", expand=True)

        self.status_label = tk.Label(
            title_col, text=t("status_starting"), bg="#F8FAFC",
            font=("Segoe UI", 13, "bold"), anchor="w", fg="#1E293B")
        self.status_label.pack(fill="x")

        self.status_sub_label = tk.Label(
            title_col, text="", bg="#F8FAFC",
            font=("Segoe UI", 10), fg="#64748B", anchor="w")
        self.status_sub_label.pack(fill="x")

        info = tk.Frame(inner, bg="#F8FAFC"); info.pack(fill="x", pady=(8, 0))
        self.ip_label = tk.Label(info, text="", bg="#F8FAFC",
                                 font=("Segoe UI", 9), fg="#94A3B8", anchor="w")
        self.last_recv_label = tk.Label(info, text="", bg="#F8FAFC",
                                        font=("Segoe UI", 9), fg="#16A34A", anchor="w")
        self.last_recv_label.pack(fill="x")

        self._install_status_tooltip()

        # ── آخر الملفات ────────────────────────────────────────
        rec_head = tk.Frame(parent, bg="white")
        rec_head.pack(fill="x", padx=16, pady=(6, 4))
        tk.Label(rec_head, text=t("recent_files"), bg="white",
                 font=("Segoe UI", 10, "bold"), fg="#1E293B", anchor="w").pack(side="right")
        view_all = tk.Label(rec_head, text=t("view_all"), bg="white",
                            font=("Segoe UI", 9), fg="#2E7D32",
                            cursor="hand2", anchor="e")
        view_all.pack(side="left")
        view_all.bind("<Button-1>", lambda _e: self.nb.select(1))

        rec_frame = tk.Frame(parent, bg="white",
                             highlightthickness=1, highlightbackground="#E2E8F0")
        rec_frame.pack(fill="both", expand=True, padx=14, pady=(0, 6))
        self.mini_list = tk.Frame(rec_frame, bg="white")
        self.mini_list.pack(fill="both", expand=True)

        # ── زر الإرسال (أسفل القائمة) ─────────────────────────
        send_frame = tk.Frame(parent, bg="white")
        send_frame.pack(fill="x", padx=14, pady=(4, 10))

        self.btn_send = tk.Button(
            send_frame, text=t("btn_send"),
            command=self._show_unified_send_dialog,
            bg="#2E7D32", fg="white",
            activebackground="#1B5E20", activeforeground="white",
            bd=0, pady=11, font=("Segoe UI", 11, "bold"), cursor="hand2")
        self.btn_send.pack(fill="x")

        # إحصائية اليوم
        self.stats_label = tk.Label(parent, text="", bg="white",
                                    font=("Segoe UI", 8), fg="#94A3B8", anchor="e")
        self.stats_label.pack(fill="x", padx=16, pady=(0, 2))

        self._refresh_mini_list()

    def _build_settings(self, parent):
        """Settings tab organised into three clear sections: Network / Files / Other."""
        c = tk.Frame(parent, bg="white"); c.pack(fill="both", expand=True, padx=18, pady=14)

        def section_header(text):
            tk.Label(c, text=text, bg="white",
                     font=("Segoe UI", 9, "bold"), fg="#6B7280",
                     anchor="w").pack(fill="x", pady=(6, 4))

        def field_label(text):
            tk.Label(c, text=text, bg="white",
                     font=("Segoe UI", 10), fg="#374151",
                     anchor="w").pack(fill="x", padx=4, pady=(6, 2))

        # ========= Language =========
        section_header(t("sect_language"))
        lang_row = tk.Frame(c, bg="white"); lang_row.pack(fill="x", padx=4, pady=(4, 8))
        tk.Label(lang_row, text=t("lang_label"), bg="white",
                 font=("Segoe UI", 10), fg="#374151").pack(side="right" if _current_lang == "ar" else "left", padx=4)
        def _toggle_lang():
            global _current_lang
            new = "en" if _current_lang == "ar" else "ar"
            self.settings.language = new
            _current_lang = new
            self.settings.save()
            # Save current state before UI rebuild
            _saved_ip = self.ip_var.get() if hasattr(self, 'ip_var') else ""
            # Rebuild the whole UI (do NOT restart backend — server is still running)
            for w in list(self.root.winfo_children()):
                try: w.destroy()
                except Exception: pass
            self._build_ui()
            self.root.update()
            # Restore IP and refresh status display (no new server needed)
            if _saved_ip and _saved_ip != "...":
                self.ip_var.set(_saved_ip)
                self._update_status(_saved_ip)
            else:
                self._refresh_status_view()
        lang_btn = tk.Button(lang_row, text=t("btn_lang_toggle"),
                             command=_toggle_lang,
                             bg="#2E7D32", fg="white", bd=0, padx=14, pady=5,
                             font=("Segoe UI", 12, "bold"), cursor="hand2",
                             activebackground="#1B5E20", activeforeground="white")
        lang_btn.pack(side="left" if _current_lang == "ar" else "right", padx=4)

        ttk.Separator(c).pack(fill="x", pady=6)

        # ========= Network =========
        section_header(t("sect_network"))

        field_label(t("lbl_ip"))
        self.ip_var = tk.StringVar(value="...")
        ip_entry = tk.Entry(c, textvariable=self.ip_var, font=("Segoe UI", 10),
                            state="readonly", bd=1, relief="solid",
                            readonlybackground="#F9FAFB", cursor="hand2")
        ip_entry.pack(fill="x", padx=4)
        ip_entry.bind("<Button-1>", lambda _e: self._copy_ip())

        field_label(t("lbl_port"))
        self.port_var = tk.StringVar(value=str(self.settings.port))
        tk.Entry(c, textvariable=self.port_var, font=("Segoe UI", 10),
                 width=12, bd=1, relief="solid").pack(anchor="w", padx=4)

        ttk.Separator(c).pack(fill="x", pady=12)

        # ========= Files =========
        section_header(t("sect_files"))

        field_label(t("lbl_save_path"))
        pf = tk.Frame(c, bg="white"); pf.pack(fill="x", padx=4)
        self.path_var = tk.StringVar(value=self.settings.save_path)
        tk.Entry(pf, textvariable=self.path_var, font=("Segoe UI", 9),
                 state="readonly", bd=1, relief="solid",
                 readonlybackground="#F9FAFB").pack(side="left", fill="x", expand=True)
        tk.Button(pf, text=t("btn_change"), command=self._choose_path,
                  bg="#E2E8F0", bd=0, padx=10, pady=3,
                  font=("Segoe UI", 9), cursor="hand2").pack(side="right", padx=(4, 0))

        field_label(t("lbl_display_mode"))
        self.dm_var = tk.StringVar(value=self.settings.display_mode)
        for val, lbl in [("open", t("mode_open")),
                         ("path", t("mode_path")),
                         ("both", t("mode_both"))]:
            tk.Radiobutton(c, text=lbl, variable=self.dm_var, value=val, bg="white",
                           font=("Segoe UI", 10), anchor="w",
                           activebackground="white").pack(fill="x", padx=8)

        ttk.Separator(c).pack(fill="x", pady=12)

        # ========= Trusted Devices =========
        section_header(t("sect_trusted"))
        tk.Label(c, text=t("trusted_desc"),
                 bg="white", font=("Segoe UI", 9), fg="#6B7280",
                 anchor="e").pack(fill="x", padx=4, pady=(2, 4))
        self.trusted_frame = tk.Frame(c, bg="white",
                                       highlightthickness=1, highlightbackground="#E5E7EB")
        self.trusted_frame.pack(fill="x", padx=4)
        # Footer actions for trusted list
        tr_actions = tk.Frame(c, bg="white"); tr_actions.pack(fill="x", padx=4, pady=(6, 0))
        tk.Button(tr_actions, text=t("btn_refresh"),
                  command=self._refresh_trusted_list,
                  bg="#E2E8F0", fg="#374151", bd=0, padx=10, pady=3,
                  font=("Segoe UI", 9), cursor="hand2").pack(side="right")
        tk.Button(tr_actions, text=t("btn_revoke_all"),
                  command=self._revoke_all_trusted,
                  bg="#FEE2E2", fg="#991B1B", bd=0, padx=10, pady=3,
                  font=("Segoe UI", 9), cursor="hand2").pack(side="left")
        self._refresh_trusted_list()

        ttk.Separator(c).pack(fill="x", pady=12)

        # ========= Other =========
        section_header(t("sect_other"))

        self.autostart_var = tk.BooleanVar(value=self._get_autostart())
        auto_cb = tk.Checkbutton(
            c, text=t("autostart_label"),
            variable=self.autostart_var, bg="white",
            font=("Segoe UI", 10), anchor="w",
            activebackground="white", cursor="hand2",
            command=self._toggle_autostart)
        auto_cb.pack(fill="x", padx=4)
        if winreg is None:
            auto_cb.config(state="disabled")

        ver_row = tk.Frame(c, bg="white"); ver_row.pack(fill="x", padx=4, pady=(8, 0))
        tk.Label(ver_row, text=t("lbl_version"), bg="white",
                 font=("Segoe UI", 10), fg="#6B7280").pack(side="left")
        tk.Label(ver_row, text=f"v{APP_VERSION}", bg="white",
                 font=("Segoe UI", 10, "bold"), fg="#374151").pack(side="left", padx=(6, 0))

        ttk.Separator(c).pack(fill="x", pady=12)

        tk.Button(c, text=t("btn_save_settings"), command=self._save_settings,
                  bg="#2E7D32", fg="white", bd=0, padx=28, pady=9,
                  font=("Segoe UI", 11, "bold"), cursor="hand2",
                  activebackground="#1B5E20", activeforeground="white").pack(pady=8)

    # ---------- Settings actions ----------
    def _choose_path(self):
        p = filedialog.askdirectory(initialdir=self.settings.save_path)
        if p:
            self.path_var.set(p)

    def _save_settings(self):
        self.settings.display_mode = self.dm_var.get()
        self.settings.save_path = self.path_var.get()
        try:
            v = int(self.port_var.get())
            if 1024 <= v <= 65535:
                self.settings.port = v
        except ValueError:
            pass
        os.makedirs(self.settings.save_path, exist_ok=True)
        self.settings.save()
        messagebox.showinfo(t("app_header"), t("settings_saved"))

    def _open_folder(self):
        os.makedirs(self.settings.save_path, exist_ok=True)
        ok, detail = self._shell_open(self.settings.save_path)
        if not ok:
            self._toast(t("app_header"), t("open_folder_fail", detail=detail))

    # ---------- History tab ----------
    def _build_history(self, parent):
        """Full history: filter chips (all/images/docs/other) + search + Treeview + clear."""
        self._history_filter = "all"
        self._history_search = ""

        # Top row: filter chips (right) + search entry (left)
        top = tk.Frame(parent, bg="white")
        top.pack(fill="x", padx=10, pady=(10, 6))
        self._filter_buttons = {}
        for key, lbl in [("all", t("filter_all")), ("images", t("filter_images")),
                         ("docs", t("filter_docs")), ("other", t("filter_other"))]:
            b = tk.Button(top, text=lbl, bd=0, padx=14, pady=5,
                          font=("Segoe UI", 9), cursor="hand2",
                          command=lambda k=key: self._apply_history_filter(k))
            b.pack(side="right", padx=3)
            self._filter_buttons[key] = b
        self._paint_filter_chips()

        # Inline search — filters the Treeview by filename substring (case-insensitive).
        self.search_var = tk.StringVar()
        def _on_search(*_a):
            self._history_search = self.search_var.get().strip().lower()
            self._rebuild_history_tree()
        self.search_var.trace_add("write", _on_search)
        search_entry = tk.Entry(top, textvariable=self.search_var, font=("Segoe UI", 9),
                                 bd=1, relief="solid", width=18)
        search_entry.pack(side="left", padx=(8, 2), ipady=3)
        tk.Label(top, text="🔎", bg="white",
                 font=("Segoe UI", 10), fg="#6B7280").pack(side="left")

        # Treeview (two columns: name + meta)
        tree_wrap = tk.Frame(parent, bg="white")
        tree_wrap.pack(fill="both", expand=True, padx=10, pady=(0, 6))

        style = ttk.Style()
        style.configure("Wameed.Treeview", font=("Segoe UI", 9), rowheight=26,
                        background="white", fieldbackground="white")
        style.configure("Wameed.Treeview.Heading", font=("Segoe UI", 9, "bold"))

        self.htree = ttk.Treeview(tree_wrap, columns=("meta",), show="tree headings",
                                  style="Wameed.Treeview", selectmode="browse")
        self.htree.heading("#0", text=t("tree_file"), anchor="e")
        self.htree.heading("meta", text=t("tree_meta"), anchor="w")
        self.htree.column("#0", anchor="e", stretch=True, minwidth=200)
        self.htree.column("meta", anchor="w", width=180, stretch=False)

        scr = ttk.Scrollbar(tree_wrap, orient="vertical", command=self.htree.yview)
        self.htree.configure(yscrollcommand=scr.set)
        scr.pack(side="left", fill="y")
        self.htree.pack(side="right", fill="both", expand=True)

        # Map tree iid -> history entry (for double-click and right-click actions).
        self._htree_entry_map = {}

        def _tree_open(_e):
            sel = self.htree.selection()
            if not sel: return
            entry = self._htree_entry_map.get(sel[0])
            if not entry: return
            path = entry.get("path", "")
            if path and os.path.exists(path):
                self._safe_startfile(path)
            else:
                self._toast(t("app_header"), t("file_not_available"))

        def _tree_menu(event):
            iid = self.htree.identify_row(event.y)
            if iid:
                self.htree.selection_set(iid)
                entry = self._htree_entry_map.get(iid)
                if entry:
                    self._show_row_menu(entry, event)

        self.htree.bind("<Double-Button-1>", _tree_open)
        self.htree.bind("<Return>", _tree_open)
        self.htree.bind("<Button-3>", _tree_menu)

        # Bottom row: clear button
        br = tk.Frame(parent, bg="white")
        br.pack(fill="x", padx=10, pady=(0, 10))
        tk.Button(br, text=t("btn_clear_history"), command=self._clear_history,
                  bg="#F3F4F6", fg="#6B7280", bd=0, padx=12, pady=5,
                  font=("Segoe UI", 9), cursor="hand2").pack(side="left")

        self._rebuild_history_tree()

    def _apply_history_filter(self, key):
        self._history_filter = key
        self._paint_filter_chips()
        self._rebuild_history_tree()

    def _paint_filter_chips(self):
        if not hasattr(self, "_filter_buttons"):
            return
        for key, btn in self._filter_buttons.items():
            if key == self._history_filter:
                btn.config(bg="#2E7D32", fg="white",
                           activebackground="#1B5E20", activeforeground="white")
            else:
                btn.config(bg="#F3F4F6", fg="#374151",
                           activebackground="#E5E7EB", activeforeground="#111827")

    @staticmethod
    def _classify(mime):
        m = (mime or "").lower()
        if m.startswith("image/"):
            return "images"
        if (m == "application/pdf" or m.startswith("text/")
                or "document" in m or "word" in m or "excel"
                in m or "spreadsheet" in m or "presentation" in m):
            return "docs"
        return "other"

    def _rebuild_history_tree(self):
        if not hasattr(self, "htree") or not self.htree.winfo_exists():
            return
        for iid in self.htree.get_children():
            self.htree.delete(iid)
        if hasattr(self, "_htree_entry_map"):
            self._htree_entry_map.clear()
        flt = getattr(self, "_history_filter", "all")
        q = getattr(self, "_history_search", "") or ""
        shown = 0
        for e in self.history.entries:
            if flt != "all" and self._classify(e.get("type", "")) != flt:
                continue
            if q and q not in (e.get("filename", "") or "").lower():
                continue
            self._hist_insert(e, top=False)
            shown += 1
        if shown == 0:
            # Placeholder row
            self.htree.insert("", "end", text=t("no_match"),
                              values=("",))

    def _hist_insert(self, entry, top=False):
        """Insert one history entry into the Treeview + track entry mapping."""
        if not hasattr(self, "htree") or not self.htree.winfo_exists():
            return
        name = entry.get("filename", "?")
        short = self._smart_truncate(name, 45)
        st = "✓" if entry.get("status") == "success" else "✗"
        sz = self._fmt_size(entry.get("size", 0))
        when = self._rel_time_from_str(entry.get("time", ""))
        meta = f"{sz}  •  {when}"
        pos = 0 if top else "end"
        iid = self.htree.insert("", pos, text=f"  {st}  {short}", values=(meta,))
        if hasattr(self, "_htree_entry_map"):
            self._htree_entry_map[iid] = entry

    def _clear_history(self):
        if not self.history.entries:
            return
        if not messagebox.askyesno(t("app_header"), t("confirm_clear")):
            return
        self.history.entries = []
        Thread(target=self.history._save, daemon=True).start()
        self._rebuild_history_tree()
        self._refresh_mini_list()

    # ---------- History / display helpers ----------
    def _log(self, filename, ftype, size, status="success", path=""):
        entry = {"filename": filename, "type": ftype, "size": size,
                 "status": status, "path": path or "",
                 "time": datetime.now().strftime("%Y-%m-%d %H:%M:%S")}
        self.history.entries.insert(0, entry)
        self.history.entries = self.history.entries[:200]
        Thread(target=self.history._save, daemon=True).start()
        # Respect current filter: rebuild tree so the new entry lands in correct spot.
        self.root.after(0, self._rebuild_history_tree)
        self.root.after(0, self._refresh_mini_list)

    def _refresh_mini_list(self):
        """Refresh Home-tab compact list with clickable rows. Each row has:
          [status]  time  filename  (size)   ⋯
        Single click on the row -> open file (os.startfile on its stored path).
        ⋯ button -> popup menu with reveal/copy/remove.
        Falls back gracefully for legacy entries missing `path`.
        """
        if not hasattr(self, "mini_list") or not self.mini_list.winfo_exists():
            return
        # Nuke previous rows.
        for w in list(self.mini_list.winfo_children()):
            try: w.destroy()
            except Exception: pass

        recent = self.history.entries[:5]
        if not recent:
            # Friendlier empty state — centred block instead of one gray line.
            empty = tk.Frame(self.mini_list, bg="white"); empty.pack(fill="both", expand=True)
            tk.Label(empty, text="⚡", bg="white", fg="#D1D5DB",
                     font=("Segoe UI", 28)).pack(pady=(18, 0))
            tk.Label(empty, text=t("no_files_yet"),
                     bg="white", fg="#6B7280",
                     font=("Segoe UI", 10, "bold")).pack()
            tk.Label(empty, text=t("empty_hint"),
                     bg="white", fg="#9CA3AF",
                     font=("Segoe UI", 9)).pack(pady=(2, 10))
        else:
            for idx, e in enumerate(recent):
                self._build_recent_row(self.mini_list, e, idx)

        # Today's stats summary.
        try:
            self._refresh_stats_label()
        except Exception:
            pass

    def _refresh_stats_label(self):
        if not hasattr(self, "stats_label") or not self.stats_label.winfo_exists():
            return
        today = datetime.now().strftime("%Y-%m-%d")
        n = 0; total = 0
        for e in self.history.entries:
            if e.get("status") == "success" and e.get("time", "").startswith(today):
                n += 1
                total += int(e.get("size") or 0)
        if n == 0:
            self.stats_label.config(text="")
        else:
            self.stats_label.config(text=t("today_stats", n=n, size=self._fmt_size(total)))

    def _build_recent_row(self, parent, entry, idx):
        """صف ملف واحد في قائمة الرئيسية — تصميم محسّن."""
        BG   = "#FAFAFA" if idx % 2 else "white"
        HOVR = "#F0FDF4"

        row = tk.Frame(parent, bg=BG, cursor="hand2")
        row.pack(fill="x")

        # فاصل خفيف بين الصفوف
        sep = tk.Frame(parent, bg="#F1F5F9", height=1)
        sep.pack(fill="x")

        ok       = entry.get("status") == "success"
        dot_clr  = "#22C55E" if ok else "#F87171"
        tm       = (entry.get("time", "") or "")[11:16]
        name     = self._smart_truncate(entry.get("filename", "?"), 36)
        sz       = self._fmt_size(entry.get("size", 0) or 0)
        path     = entry.get("path", "") or ""
        has_file = bool(path) and os.path.exists(path)

        # نقطة الحالة — يمين
        dot = tk.Label(row, text="●", bg=BG, fg=dot_clr,
                       font=("Segoe UI", 9))
        dot.pack(side="right", padx=(10, 8), pady=6)

        # اسم الملف
        name_lbl = tk.Label(row, text=name, bg=BG,
                            fg="#1E293B" if has_file else "#94A3B8",
                            font=("Segoe UI", 10), anchor="e")
        name_lbl.pack(side="right", fill="x", expand=True, padx=(0, 4), pady=6)

        # الحجم والوقت — يسار
        meta_lbl = tk.Label(row, text=f"{sz}  ·  {tm}", bg=BG,
                            fg="#94A3B8", font=("Segoe UI", 9), anchor="w")
        meta_lbl.pack(side="left", padx=(10, 0), pady=6)

        # زر القائمة السياقية — يسار بعد الميتا
        more_btn = tk.Label(row, text="···", bg=BG, fg="#CBD5E1",
                            font=("Segoe UI", 11, "bold"),
                            padx=8, cursor="hand2")
        more_btn.pack(side="left")

        # ── التفاعل ────────────────────────────────────────────
        def on_click(_e=None):
            if not path:
                self._toast(t("app_header"), t("old_entry")); return
            if not os.path.exists(path):
                self._toast(t("app_header"), t("file_gone", path=path)); return
            self.root.after(0, lambda: self._open_file_safe(path))

        def on_more(_e=None):
            self._show_row_menu(entry, _e)

        def on_enter(_e=None):
            for w in (row, name_lbl, dot, meta_lbl, more_btn):
                try: w.config(bg=HOVR)
                except Exception: pass
            try: sep.config(bg="#D1FAE5")
            except Exception: pass

        def on_leave(_e=None):
            for w in (row, name_lbl, dot, meta_lbl, more_btn):
                try: w.config(bg=BG)
                except Exception: pass
            try: sep.config(bg="#F1F5F9")
            except Exception: pass

        for w in (row, name_lbl, dot, meta_lbl):
            w.bind("<Button-1>", on_click)
            w.bind("<Enter>", on_enter)
            w.bind("<Leave>", on_leave)
        more_btn.bind("<Button-1>", on_more)
        more_btn.bind("<Enter>", on_enter)
        more_btn.bind("<Leave>", on_leave)
        row.bind("<Button-3>", on_more)
        name_lbl.bind("<Button-3>", on_more)

    def _show_row_menu(self, entry, event=None):
        """Popup menu for a single recent-file row: reveal / copy path / remove."""
        path = entry.get("path", "") or ""
        has_file = bool(path) and os.path.exists(path)
        m = tk.Menu(self.root, tearoff=0)
        if has_file:
            m.add_command(label=t("menu_reveal"),
                          command=lambda: self._reveal_in_folder(path))
            m.add_command(label=t("menu_open"),
                          command=lambda: self._safe_startfile(path))
            m.add_command(label=t("menu_copy_path"),
                          command=lambda: self._copy_text(path))
            m.add_separator()
        else:
            m.add_command(label=t("menu_not_available"), state="disabled")
            m.add_separator()
        m.add_command(label=t("menu_remove"),
                      command=lambda: self._remove_history_entry(entry))
        try:
            if event is not None and hasattr(event, "x_root"):
                m.tk_popup(event.x_root, event.y_root)
            else:
                m.tk_popup(self.root.winfo_pointerx(), self.root.winfo_pointery())
        finally:
            try: m.grab_release()
            except Exception: pass

    # ------------------------------------------------------------------
    # Safe cross-environment file opener
    # ------------------------------------------------------------------
    @staticmethod
    def _shell_open(path):
        """Try every available mechanism to open *path* with its default app.
        Returns (True, method_name) on success, (False, last_error) on failure.
        Priority:
          1. ctypes ShellExecuteW  — most reliable in PyInstaller noconsole
          2. os.startfile          — standard Python way
          3. cmd /c start          — works on every Windows install
        """
        abs_path = os.path.abspath(path)
        # 1. ShellExecuteW via ctypes (works even in restricted STA contexts)
        try:
            import ctypes
            ret = ctypes.windll.shell32.ShellExecuteW(
                None, "open", abs_path, None, None, 1)  # SW_SHOWNORMAL = 1
            if ret > 32:  # > 32 means success
                return True, "ShellExecuteW"
        except Exception as e:
            _err = str(e)
        # 2. os.startfile
        try:
            os.startfile(abs_path)
            return True, "os.startfile"
        except Exception as e:
            _err = str(e)
        # 3. cmd /c start (universal Windows fallback)
        try:
            # Empty title string is required so paths with spaces aren't split
            subprocess.Popen(
                ["cmd", "/c", "start", "", os.path.normpath(abs_path)],
                creationflags=subprocess.CREATE_NO_WINDOW
            )
            return True, "cmd start"
        except Exception as e:
            _err = str(e)
        return False, _err

    def _open_file_safe(self, path, silent=False):
        """Open *path* with its default app. Shows a toast on failure unless
        *silent* is True. Must be called from the Tk main thread."""
        if not os.path.exists(path):
            if not silent:
                self._toast(t("app_header"), t("file_gone", path=path))
            return False
        ok, detail = self._shell_open(path)
        if not ok:
            logger.warning("_open_file_safe failed for %s: %s", path, detail)
            if not silent:
                self._toast(t("app_header"), t("open_fail", path=path))
        return ok

    def _safe_startfile(self, path):
        """Legacy shim — delegates to _open_file_safe."""
        self._open_file_safe(path)

    def _reveal_in_folder(self, path):
        try:
            if os.path.exists(path):
                subprocess.Popen(["explorer", "/select,", os.path.normpath(path)])
            else:
                folder = os.path.dirname(path) or self.settings.save_path
                ok, detail = self._shell_open(folder)
                if not ok:
                    self._toast(t("app_header"), t("open_folder_fail", detail=detail))
        except Exception as ex:
            self._toast(t("app_header"), t("open_folder_fail", detail=ex))

    def _copy_text(self, text):
        try:
            self.root.clipboard_clear()
            self.root.clipboard_append(text)
            self._toast(t("app_header"), t("copied"))
        except Exception:
            pass

    def _remove_history_entry(self, entry):
        """Remove one entry from history (keeps the file on disk)."""
        try:
            # Match by (filename, time) — tolerant if multiple same-name files.
            self.history.entries = [e for e in self.history.entries
                                    if not (e.get("filename") == entry.get("filename")
                                            and e.get("time") == entry.get("time"))]
            Thread(target=self.history._save, daemon=True).start()
            self._refresh_mini_list()
            self._rebuild_history_tree()
        except Exception:
            logger.exception("Failed to remove history entry")

    @staticmethod
    def _smart_truncate(name, limit=45):
        """Truncate preserving the extension: 'very_long_name.pdf' -> 'very_lo…ame.pdf'."""
        if len(name) <= limit:
            return name
        base, ext = os.path.splitext(name)
        ext = ext[:10]  # safety
        # Reserve room for ellipsis + ext
        budget = limit - 1 - len(ext)  # 1 for '…'
        if budget < 8:
            return name[:limit - 1] + "…"
        head = budget * 2 // 3
        tail = budget - head
        return f"{base[:head]}…{base[-tail:] if tail > 0 else ''}{ext}"

    @staticmethod
    def _rel_time_from_str(ts):
        """Parse 'YYYY-mm-dd HH:MM:SS' and return a short relative Arabic string."""
        if not ts:
            return ""
        try:
            dt = datetime.strptime(ts, "%Y-%m-%d %H:%M:%S")
        except Exception:
            return ts
        delta = (datetime.now() - dt).total_seconds()
        if delta < 60:
            return t("time_just_now")
        if delta < 3600:
            return t("time_min", n=int(delta // 60))
        if delta < 86400:
            return t("time_hour", n=int(delta // 3600))
        return dt.strftime("%Y-%m-%d")

    @staticmethod
    def _fmt_size(s):
        if s < 1024: return f"{s} B"
        if s < 1048576: return f"{s/1024:.1f} KB"
        if s < 1073741824: return f"{s/1048576:.1f} MB"
        return f"{s/1073741824:.1f} GB"

    # ---------- Autostart (HKCU Run) ----------
    def _get_autostart(self):
        if winreg is None:
            return False
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, AUTOSTART_KEY) as k:
                v, _ = winreg.QueryValueEx(k, AUTOSTART_NAME)
                return bool(v)
        except FileNotFoundError:
            return False
        except OSError:
            return False

    def _autostart_command(self):
        """Build the exact command string to store under HKCU\\...\\Run."""
        if getattr(sys, "frozen", False):
            # PyInstaller exe — launch directly.
            return f'"{sys.executable}"'
        # Dev mode: prefer pythonw.exe (no console) next to python.exe.
        py = sys.executable
        pyw = os.path.join(os.path.dirname(py), "pythonw.exe")
        runner = pyw if os.path.exists(pyw) else py
        script = os.path.abspath(__file__)
        return f'"{runner}" "{script}"'

    def _set_autostart(self, enabled):
        if winreg is None:
            return False
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, AUTOSTART_KEY,
                                0, winreg.KEY_SET_VALUE) as k:
                if enabled:
                    winreg.SetValueEx(k, AUTOSTART_NAME, 0, winreg.REG_SZ,
                                      self._autostart_command())
                else:
                    try:
                        winreg.DeleteValue(k, AUTOSTART_NAME)
                    except FileNotFoundError:
                        pass
            return True
        except Exception:
            logger.exception("Autostart toggle failed")
            return False

    def _toggle_autostart(self):
        want = bool(self.autostart_var.get())
        ok = self._set_autostart(want)
        if not ok:
            # Revert the checkbox visually on failure.
            self.autostart_var.set(not want)
            messagebox.showwarning(t("app_header"), t("autostart_fail"))

    # ---------- Trusted Devices UI ----------
    def _refresh_trusted_list(self):
        """Rebuild the trusted devices panel from self.trusted.devices."""
        if not hasattr(self, "trusted_frame") or not self.trusted_frame.winfo_exists():
            return
        for w in list(self.trusted_frame.winfo_children()):
            try: w.destroy()
            except Exception: pass
        devices = self.trusted.ordered()
        if not devices:
            tk.Label(self.trusted_frame,
                     text=t("no_trusted"),
                     bg="white", fg="#9CA3AF", font=("Segoe UI", 9),
                     anchor="e").pack(fill="x", padx=10, pady=10)
            return
        for idx, (did, info) in enumerate(devices):
            row = tk.Frame(self.trusted_frame,
                           bg="#FBFBFB" if idx % 2 else "white")
            row.pack(fill="x")
            # Revoke on the left, info on the right.
            def _revoke(d=did, n=info.get("name", "?")):
                if messagebox.askyesno(
                        t("app_header"),
                        t("revoke_confirm", name=n)):
                    self.trusted.revoke(d)
                    self._refresh_trusted_list()
            tk.Button(row, text=t("btn_revoke"), command=_revoke,
                      bg="#FEE2E2", fg="#991B1B", bd=0, padx=8, pady=3,
                      font=("Segoe UI", 8), cursor="hand2"
                      ).pack(side="left", padx=6, pady=4)
            info_col = tk.Frame(row, bg=row.cget("bg"))
            info_col.pack(side="right", fill="x", expand=True, padx=(8, 10), pady=4)
            tk.Label(info_col, text=f"📱 {info.get('name','?')}",
                     bg=row.cget("bg"), fg="#111827",
                     font=("Segoe UI", 10, "bold"), anchor="e"
                     ).pack(fill="x")
            sub = t("paired_since", t=info.get('first_paired','?'))
            ls = info.get("last_seen", "")
            if ls and ls != info.get("first_paired"):
                sub += "  \u2022  " + t("last_seen", t=ls)
            tk.Label(info_col, text=sub, bg=row.cget("bg"), fg="#6B7280",
                     font=("Segoe UI", 8), anchor="e").pack(fill="x")

    def _revoke_all_trusted(self):
        if not self.trusted.devices:
            return
        if messagebox.askyesno(
                t("app_header"),
                t("revoke_all_confirm")):
            self.trusted.revoke_all()
            self._refresh_trusted_list()

    # ---------- IP copy + status tooltip ----------
    def _copy_ip(self):
        try:
            ip = self.ip_var.get()
            self.root.clipboard_clear()
            self.root.clipboard_append(ip)
            self._toast(t("app_header"), t("ip_copied", ip=ip))
        except Exception:
            pass

    def _install_status_tooltip(self):
        """Simple hover tooltip on the status card showing last-activity age."""
        self._tip_win = None

        def show(_e):
            if self._tip_win or not self.status_frame.winfo_exists():
                return
            if self.last_activity_at <= 0:
                body = t("tooltip_no_activity")
            else:
                age = int(time.time() - self.last_activity_at)
                if age < 60:
                    age_str = t("time_sec_long", n=age)
                elif age < 3600:
                    age_str = t("time_min_long", n=age // 60)
                else:
                    age_str = t("time_hour_long", n=age // 3600)
                body = t("tooltip_last", age=age_str)
            x = self.status_frame.winfo_rootx() + 20
            y = self.status_frame.winfo_rooty() + self.status_frame.winfo_height() + 2
            tw = tk.Toplevel(self.root)
            tw.overrideredirect(True); tw.attributes("-topmost", True)
            tw.geometry(f"+{x}+{y}")
            tk.Label(tw, text=body, bg="#111827", fg="white",
                     font=("Segoe UI", 9), padx=10, pady=5).pack()
            self._tip_win = tw

        def hide(_e):
            if self._tip_win:
                try: self._tip_win.destroy()
                except Exception: pass
                self._tip_win = None

        for w in (self.status_frame, self.status_label, self.status_sub_label, self.status_dot):
            w.bind("<Enter>", show)
            w.bind("<Leave>", hide)

    # ---------- Backend ----------
    def _start_backend(self):
        self._backend_error = None
        # Start backend loop in its own thread
        Thread(target=self._async_loop, daemon=True).start()

        # Fetch IP and update status in background to avoid blocking the main UI thread,
        # which can cause the window to appear as a "white screen" until it returns.
        def _bg_init():
            ip = self._get_ip()
            self.root.after(0, lambda: self.ip_var.set(ip))
            self.root.after(0, lambda: self._update_status(ip))

        Thread(target=_bg_init, daemon=True).start()

    def _update_status(self, ip):
        if self._backend_error:
            self._set_status_colors("#FEF2F2", "#EF4444")
            self.status_dot.config(fg="#F44336")
            self.status_label.config(text=t("status_error"))
            # Map technical errors to friendly text.
            err = self._backend_error
            if "مستخدم" in err or "use" in err.lower() or "in use" in err.lower():
                friendly = t("status_error_dup")
            else:
                friendly = err
            self.status_sub_label.config(text=friendly, fg="#B91C1C")
            # Only in the error state, show a hint inline. Pack if hidden.
            try:
                self.ip_label.config(text=t("status_diag_hint"), fg="#B91C1C")
                if not self.ip_label.winfo_ismapped():
                    self.ip_label.pack(fill="x", before=self.last_recv_label)
            except Exception:
                pass
        else:
            self.server_running = True
            # IP/path moved to Settings tab — don't show on home card.
            try:
                if self.ip_label.winfo_ismapped():
                    self.ip_label.pack_forget()
            except Exception:
                pass
            self._refresh_status_view()
        # Start periodic refresh loop
        self.root.after(1000, self._refresh_status_view)

    def _refresh_status_view(self):
        """Updates connection state text/color based on active_clients and device name."""
        if self._backend_error:
            return

        in_grace = (
            self.connected_device_name
            and (time.time() - self.last_activity_at) < self.connection_grace_sec
        )

        # We only consider "connected" if there is an active socket OR we are in grace window
        # AND we have a known connected device name (not idle).
        is_connected = (self.active_clients > 0 or in_grace) and self.connected_device_name

        recent_file_age = time.time() - self.last_file_received_at
        recently_active = (self.last_file_received_at > 0
                           and recent_file_age < self.recent_activity_window_sec)

        # Pending pairing takes precedence
        if self._pending_pair_name:
            self.status_dot.config(fg="#FB923C")
            self._set_status_colors("#FFF7ED", "#FB923C")
            self.status_label.config(
                text=t("pairing_request", name=self._pending_pair_name), fg="#9A3412")
            self.status_sub_label.config(
                text=t("pairing_sub"), fg="#B45309")
            self.root.after(700, self._refresh_status_view)
            return

        if is_connected:
            # Phone actively connected (or just was, within grace window).
            self.status_dot.config(fg="#22C55E")
            self._set_status_colors("#ECFDF5", "#22C55E")
            dev = self.connected_device_name or t("device_generic")
            extra = max(0, self.active_clients - 1)
            # Shield badge only if this specific device is known-trusted.
            badge = " 🛡" if self.trusted.is_trusted(self.connected_device_id) else ""
            if extra > 0:
                title = t("status_connected_n", dev=dev, badge=badge, n=extra)
            else:
                title = t("status_connected", dev=dev, badge=badge)
            self.status_label.config(text=title, fg="#166534")
            self.status_sub_label.config(text=t("status_ready_sub"), fg="#15803D")
        elif self.server_running and recently_active:
            # Recently received a file but no live WS right now. Show a friendly
            # "ready, still warm" state instead of cold "waiting" — so the user
            # who just shared from their phone doesn't see a contradictory UI.
            self.connected_device_name = ""
            self.status_dot.config(fg="#10B981")
            self._set_status_colors("#F0FDF4", "#86EFAC")
            # Friendly relative time for "last file N seconds ago".
            age = int(recent_file_age)
            if age < 60:
                ago = t("time_sec", n=age)
            else:
                ago = t("time_min", n=age // 60)
            self.status_label.config(text=t("status_ready_ago", ago=ago), fg="#065F46")
            self.status_sub_label.config(text=t("status_ready_sub2"),
                                         fg="#047857")
        elif self.server_running:
            # Truly idle — no client, no recent activity. Now safe to forget the
            # device name so a different phone can connect cleanly. The copy is
            # intentionally friendly: receiver is always "ready", never truly
            # "waiting for a connection" — the phone doesn't need to stay
            # connected to share (share-sheet flow works without it).
            self.connected_device_name = ""
            self.status_dot.config(fg="#3B82F6")
            self._set_status_colors("#EFF6FF", "#3B82F6")
            self.status_label.config(text=t("status_idle"), fg="#1E3A8A")
            self.status_sub_label.config(text=t("status_idle_sub"),
                                         fg="#475569")
        # Last received info
        if self.last_received:
            fname, ts = self.last_received
            delta = (datetime.now() - ts).total_seconds()
            if delta < 60:
                when = t("time_just_now")
            elif delta < 3600:
                when = t("time_min_long", n=int(delta//60))
            else:
                when = t("time_hour_long", n=int(delta//3600))
            self.last_recv_label.config(text=t("last_recv", name=fname, when=when))
        # Polling cadence: 700ms is a good balance between snappiness and CPU.
        # Note: real-time updates already arrive via _notify_state_change() —
        # this poll is just a safety net + handles "last_received" age display.
        self.root.after(700, self._refresh_status_view)

    def _set_status_colors(self, bg, accent):
        """Repaint the status card bg recursively AND set the right-side accent strip.
        The accent frame is skipped from bg recursion so its colour sticks."""
        accent_widget = getattr(self, "status_accent", None)

        def paint(widget):
            if widget is accent_widget:
                return
            try: widget.config(bg=bg)
            except Exception: pass
            for child in getattr(widget, "winfo_children", lambda: [])():
                if isinstance(child, (tk.Frame, tk.Label)):
                    paint(child)
        try:
            paint(self.status_frame)
        except Exception:
            pass
        if accent_widget is not None:
            try: accent_widget.config(bg=accent)
            except Exception: pass

    # Backwards-compat alias in case something else still calls the old name.
    def _set_status_bg(self, color):
        self._set_status_colors(color, self.status_accent.cget("bg")
                                if hasattr(self, "status_accent") else "#9CA3AF")

    def _async_loop(self):
        """Runs WebSocket server + UDP broadcast. Robust error capture is CRITICAL:
        previously silent failures in --noconsole exe masked the root cause."""
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        try:
            logger.info("Starting WS server on 0.0.0.0:%d", self.settings.port)
            srv_coro = websockets.serve(
                self._handle_ws,
                "0.0.0.0",
                self.settings.port,
                max_size=4 * 1024 * 1024,
                max_queue=64,
                ping_interval=30,
                ping_timeout=300,
            )
            server = loop.run_until_complete(srv_coro)
            # Mark server as alive ONLY after successful bind+listen.
            self.ws_alive.set()
            sockets = getattr(server, "sockets", []) or []
            for sk in sockets:
                try:
                    logger.info("WS listening on %s", sk.getsockname())
                except Exception:
                    pass
            loop.create_task(self._udp_broadcast())
            logger.info("WS server started OK — entering run_forever")
            loop.run_forever()
        except OSError as e:
            logger.exception("WS server OSError")
            if e.errno == 10048 or 'already in use' in str(e).lower():
                self._backend_error = t("port_in_use", port=self.settings.port)
            else:
                self._backend_error = f"OSError: {e}"
            self.ws_alive.clear()
        except Exception as e:
            logger.exception("WS server failed")
            self._backend_error = f"{type(e).__name__}: {e}"
            self.ws_alive.clear()

    def _get_ip(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            # استخدام عنوان وهمي لا يحتاج اتصال فعلي لكنه يحدد الواجهة النشطة
            s.connect(("10.255.255.255", 1))
            ip = s.getsockname()[0]
        except Exception:
            try:
                # محاولة بديلة عبر اسم الجهاز
                ip = socket.gethostbyname(socket.gethostname())
            except:
                ip = "127.0.0.1"
        finally:
            s.close()
        return ip

    # ---------- UDP Discovery Broadcast ----------
    async def _udp_broadcast(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        logger.info("UDP broadcast task started")

        counter = 0
        msg = b""

        while True:
            # Only broadcast when WS server is actually alive.
            if self.ws_alive.is_set():
                try:
                    # تحديث الـ IP والرسالة كل 5 دورات (حوالي 10 ثوانٍ) لضمان الدقة
                    if counter % 5 == 0 or not msg:
                        current_ip = self._get_ip()
                        # تحديث الواجهة بالعنوان الجديد إذا تغير
                        self.root.after(0, lambda i=current_ip: self.ip_var.set(i))

                        name = socket.gethostname()
                        msg = json.dumps({
                            "service": "wameed",
                            "ip": current_ip,
                            "port": self.settings.port,
                            "name": name
                        }).encode("utf-8")
                        logger.debug("UDP broadcast updated: %s", current_ip)

                    sock.sendto(msg, ("<broadcast>", DISCOVERY_PORT))
                except Exception as e:
                    logger.debug("UDP broadcast tick failed: %s", e)

            counter += 1
            await asyncio.sleep(2)

    def _notify_state_change(self):
        """Push an immediate UI refresh onto the Tk main thread.
        Safe to call from any thread (including the asyncio WS thread).
        This is what makes connection status feel INSTANT instead of waiting
        for the next ~1.5s polling tick."""
        self.last_activity_at = time.time()
        try:
            self.root.after(0, self._refresh_status_view)
        except Exception:
            # root may be torn down during shutdown
            pass

    # ---------- Pairing approval (called from WS thread) ----------
    def _ask_pairing_approval(self, device_id, name, ip):
        """BLOCKING: Show modal dialog on Tk thread, wait up to PAIRING_TIMEOUT_SEC
        seconds for user decision. Returns True if approved, False otherwise.

        Runs inside `loop.run_in_executor(...)` so the asyncio event loop stays
        responsive (other phones' pings, etc).
        """
        import threading
        ev = threading.Event()
        result = {"approved": False}

        def _show():
            # Bring main window to front so user notices the dialog.
            try:
                if self.root.state() == "withdrawn" or not self.root.winfo_viewable():
                    self.root.deiconify()
                self.root.lift()
                self.root.focus_force()
                self.root.attributes('-topmost', True)
                self.root.after(800, lambda: self.root.attributes('-topmost', False))
            except Exception:
                pass
            try:
                win = tk.Toplevel(self.root)
            except Exception:
                ev.set()
                return
            win.title(t("pair_title"))
            win.configure(bg="white")
            win.transient(self.root)
            win.resizable(False, False)
            try:
                win.iconbitmap(os.path.join(os.path.dirname(os.path.abspath(__file__)), "wameed.ico"))
            except Exception:
                pass
            w, h = 440, 280
            try:
                sw = self.root.winfo_screenwidth(); sh = self.root.winfo_screenheight()
                win.geometry(f"{w}x{h}+{(sw-w)//2}+{(sh-h)//2}")
            except Exception:
                win.geometry(f"{w}x{h}")
            win.attributes('-topmost', True)
            try: win.grab_set()
            except Exception: pass

            hdr = tk.Frame(win, bg="#D97706", height=50)
            hdr.pack(fill="x"); hdr.pack_propagate(False)
            tk.Label(hdr, text=t("pair_header"), bg="#D97706", fg="white",
                     font=("Segoe UI", 13, "bold")).pack(expand=True)

            body = tk.Frame(win, bg="white")
            body.pack(fill="both", expand=True, padx=24, pady=16)

            tk.Label(body, text=name or t("pair_device_unknown"), bg="white",
                     font=("Segoe UI", 13, "bold"), fg="#1E293B",
                     anchor="e").pack(fill="x")
            tk.Label(body, text=ip, bg="white",
                     font=("Segoe UI", 9), fg="#94A3B8",
                     anchor="e").pack(fill="x", pady=(2, 0))
            tk.Frame(body, bg="#E2E8F0", height=1).pack(fill="x", pady=12)
            tk.Label(body,
                     text=t("pair_body"),
                     bg="white", font=("Segoe UI", 10), fg="#374151",
                     anchor="e", wraplength=380, justify="right").pack(fill="x")
            tk.Label(body,
                     text=t("pair_hint"),
                     bg="white", font=("Segoe UI", 9), fg="#94A3B8",
                     anchor="e", wraplength=380, justify="right").pack(fill="x", pady=(6, 0))

            btns = tk.Frame(win, bg="white"); btns.pack(fill="x", padx=24, pady=(0, 18))

            def _decide(ok):
                result["approved"] = ok
                try: win.grab_release()
                except Exception: pass
                try: win.destroy()
                except Exception: pass
                ev.set()

            allow_btn = tk.Button(btns, text=t("btn_allow"), bg="#2E7D32", fg="white", bd=0,
                                  padx=18, pady=9, font=("Segoe UI", 10, "bold"), cursor="hand2",
                                  activebackground="#1B5E20", activeforeground="white",
                                  command=lambda: _decide(True))
            allow_btn.pack(side="right")
            tk.Button(btns, text=t("btn_reject"), bg="#FEE2E2", fg="#991B1B", bd=0,
                      padx=18, pady=9, font=("Segoe UI", 10), cursor="hand2",
                      command=lambda: _decide(False)).pack(side="right", padx=(0, 8))

            # Clicking X on the dialog is treated as "reject".
            win.protocol("WM_DELETE_WINDOW", lambda: _decide(False))
            # Enter key defaults to "allow" (makes approval one keystroke).
            win.bind("<Return>", lambda _e: _decide(True))
            win.bind("<Escape>", lambda _e: _decide(False))
            allow_btn.focus_set()

        try:
            self.root.after(0, _show)
        except Exception:
            logger.exception("Failed to schedule pairing dialog")
            return False

        signaled = ev.wait(timeout=PAIRING_TIMEOUT_SEC)
        if not signaled:
            logger.warning("Pairing timed out for id=%s ip=%s", (device_id or "")[:8], ip)
            self.pairing_log.add("timeout", name, ip, device_id)
            return False
        return result["approved"]

    def _show_unified_send_dialog(self):
        """نافذة الإرسال — تصميم محسّن."""
        win = tk.Toplevel(self.root)
        win.title(t("send_title"))
        win.configure(bg="white")
        win.transient(self.root)
        win.resizable(False, False)
        try:
            ico = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wameed.ico")
            if os.path.exists(ico): win.iconbitmap(ico)
        except Exception:
            pass
        w, h = 480, 560
        try:
            sw = self.root.winfo_screenwidth(); sh = self.root.winfo_screenheight()
            win.geometry(f"{w}x{h}+{(sw-w)//2}+{(sh-h)//2}")
        except Exception:
            win.geometry(f"{w}x{h}")

        # ── Header ───────────────────────────────────────────────
        hdr = tk.Frame(win, bg="#2E7D32", height=50)
        hdr.pack(fill="x"); hdr.pack_propagate(False)
        tk.Label(hdr, text=t("send_header"), bg="#2E7D32", fg="white",
                 font=("Segoe UI", 13, "bold")).pack(expand=True)

        main_body = tk.Frame(win, bg="white")
        main_body.pack(fill="both", expand=True)

        # ── اختيار الجهاز ────────────────────────────────────────
        tk.Label(main_body, text=t("send_choose_device"), bg="white",
                 font=("Segoe UI", 10, "bold"), fg="#1E293B",
                 anchor="e").pack(fill="x", padx=20, pady=(16, 6))

        device_container = tk.Frame(main_body, bg="white",
                                    highlightthickness=1,
                                    highlightbackground="#E2E8F0")
        device_container.pack(fill="x", padx=20)

        selected_ip   = tk.StringVar(value="")
        selected_port = tk.IntVar(value=7789)

        def _build_device_list():
            for w_child in device_container.winfo_children():
                w_child.destroy()

            device_entries = []
            seen_ips = set()

            for phone in self.phone_discovery.get_phones():
                ip = phone["ip"]
                device_entries.append((phone["name"], ip, phone["port"], True))
                seen_ips.add(ip)

            for did, info in self.trusted.deduplicated_for_send():
                ip = info.get("last_ip", "")
                if ip and ip in seen_ips:
                    continue
                device_entries.append(
                    (info.get("name", t("device_generic")), ip, 7789, False))
                if ip: seen_ips.add(ip)

            if not device_entries:
                tk.Label(device_container,
                         text=t("send_no_devices"),
                         bg="white", fg="#94A3B8", font=("Segoe UI", 9),
                         justify="center").pack(pady=16)
                return

            for name, ip, port, is_live in device_entries:
                card = tk.Frame(device_container, bg="#FAFAFA", cursor="hand2",
                                highlightthickness=1, highlightbackground="#E2E8F0")
                card.pack(fill="x", padx=6, pady=3)

                top_row = tk.Frame(card, bg="#FAFAFA")
                top_row.pack(fill="x", padx=12, pady=(8, 2))

                tk.Label(top_row, text=name, bg="#FAFAFA",
                         font=("Segoe UI", 10, "bold"), fg="#1E293B",
                         anchor="e").pack(side="right")

                status_text = t("send_available_now") if is_live else (
                    t("send_trusted") if ip else t("send_no_ip"))
                status_clr  = "#16A34A" if is_live else ("#64748B" if ip else "#94A3B8")
                tk.Label(top_row, text=status_text, bg="#FAFAFA",
                         font=("Segoe UI", 9), fg=status_clr,
                         anchor="w").pack(side="left")

                bottom_row = tk.Frame(card, bg="#FAFAFA")
                bottom_row.pack(fill="x", padx=12, pady=(0, 8))
                tk.Label(bottom_row, text=ip if ip else t("send_unknown_ip"),
                         bg="#FAFAFA", font=("Segoe UI", 9), fg="#94A3B8",
                         anchor="e").pack(side="right")

                def _select(ip_=ip, port_=port, card_=card):
                    selected_ip.set(ip_)
                    selected_port.set(port_)
                    for c in device_container.winfo_children():
                        try:
                            c.config(highlightbackground="#E2E8F0", bg="#FAFAFA")
                            for ch in c.winfo_children():
                                ch.config(bg="#FAFAFA")
                                for gch in ch.winfo_children():
                                    gch.config(bg="#FAFAFA")
                        except Exception:
                            pass
                    try:
                        card_.config(highlightbackground="#2E7D32", bg="#F0FDF4")
                        for ch in card_.winfo_children():
                            ch.config(bg="#F0FDF4")
                            for gch in ch.winfo_children():
                                gch.config(bg="#F0FDF4")
                    except Exception:
                        pass

                card.bind("<Button-1>", lambda e, f=_select: f())
                for child in card.winfo_children():
                    child.bind("<Button-1>", lambda e, f=_select: f())
                    for grandchild in child.winfo_children():
                        grandchild.bind("<Button-1>", lambda e, f=_select: f())

                if ip and not selected_ip.get():
                    _select()

        _build_device_list()

        # تحديث + IP يدوي
        ctrl_row = tk.Frame(main_body, bg="white")
        ctrl_row.pack(fill="x", padx=20, pady=(6, 0))
        tk.Button(ctrl_row, text=t("btn_refresh"), command=_build_device_list,
                  bg="#F1F5F9", fg="#475569", bd=0, padx=12, pady=4,
                  font=("Segoe UI", 9), cursor="hand2").pack(side="right")

        manual_row = tk.Frame(main_body, bg="white")
        manual_row.pack(fill="x", padx=20, pady=(6, 4))
        tk.Label(manual_row, text=t("send_manual_ip"), bg="white",
                 font=("Segoe UI", 9), fg="#94A3B8").pack(side="right")
        manual_ip_var = tk.StringVar()
        tk.Entry(manual_row, textvariable=manual_ip_var, width=18,
                 font=("Segoe UI", 9), bd=1, relief="solid").pack(side="right", padx=(6, 10))

        # ── نوع المحتوى (Tab Buttons) ─────────────────────────────
        ttk.Separator(main_body).pack(fill="x", padx=20, pady=(8, 0))

        mode_var = tk.StringVar(value="file")
        tabs_row = tk.Frame(main_body, bg="white")
        tabs_row.pack(fill="x", padx=20, pady=(10, 6))

        def _switch_tabs(val):
            mode_var.set(val)
            _switch_mode(val)
            file_tab_btn.config(
                bg="#2E7D32" if val == "file" else "#F1F5F9",
                fg="white"   if val == "file" else "#475569")
            text_tab_btn.config(
                bg="#2E7D32" if val == "text" else "#F1F5F9",
                fg="white"   if val == "text" else "#475569")

        file_tab_btn = tk.Button(
            tabs_row, text=t("send_mode_file"),
            command=lambda: _switch_tabs("file"),
            bg="#2E7D32", fg="white",
            bd=0, padx=20, pady=6,
            font=("Segoe UI", 10), cursor="hand2")
        file_tab_btn.pack(side="right")

        text_tab_btn = tk.Button(
            tabs_row, text=t("send_mode_text"),
            command=lambda: _switch_tabs("text"),
            bg="#F1F5F9", fg="#475569",
            bd=0, padx=20, pady=6,
            font=("Segoe UI", 10), cursor="hand2")
        text_tab_btn.pack(side="right", padx=(0, 4))

        # ── Footer elements packed FIRST with side="bottom" ──────
        # Must be packed before content_area so Tkinter reserves their space.
        progress_var   = tk.IntVar(value=0)
        progress_bar   = ttk.Progressbar(main_body,
                                         variable=progress_var, maximum=100)
        progress_label = tk.Label(main_body, text="", bg="white",
                                  font=("Segoe UI", 9), fg="#64748B")
        btn_frame = tk.Frame(main_body, bg="white")
        btn_frame.pack(fill="x", padx=20, pady=(10, 14), side="bottom")
        ttk.Separator(main_body).pack(
            fill="x", padx=20, pady=(4, 0), side="bottom")

        # ── منطقة المحتوى (fills remaining middle space) ─────────
        content_area = tk.Frame(main_body, bg="white")
        content_area.pack(fill="both", expand=True, padx=20, pady=(0, 8))

        # — ملف —
        MAX_FILES  = 10
        files_list = []

        file_frame = tk.Frame(content_area, bg="white")

        # ── Drop Zone (أفقية مدمجة) ────────────────────────────────
        DZ_BG     = "#F8FAFC"
        DZ_HOVER  = "#EFF6FF"
        DZ_BORDER   = "#CBD5E1"
        DZ_BORDER_H = "#93C5FD"

        drop_zone = tk.Frame(file_frame, bg=DZ_BG,
                             highlightthickness=2,
                             highlightbackground=DZ_BORDER,
                             cursor="hand2")
        drop_zone.pack(fill="x", pady=(4, 0))

        dz_inner = tk.Frame(drop_zone, bg=DZ_BG)
        dz_inner.pack(expand=True, pady=8)

        dz_icon = tk.Label(dz_inner, text="📂", bg=DZ_BG,
                           font=("Segoe UI", 22), cursor="hand2")
        dz_icon.pack(side="left", padx=(12, 6))

        dz_text_f = tk.Frame(dz_inner, bg=DZ_BG)
        dz_text_f.pack(side="left", pady=2)

        dz_title = tk.Label(
            dz_text_f,
            text=t("send_drag_hint") if HAS_DND else t("send_pick_file"),
            bg=DZ_BG, font=("Segoe UI", 10, "bold"), fg="#475569",
            cursor="hand2")
        dz_title.pack(anchor="w")

        dz_sub = tk.Label(
            dz_text_f,
            text=("انقر لاختيار ملفات  ·  Ctrl+V للصق مسار" if HAS_DND
                  else "Ctrl+V للصق مسار الملف"),
            bg=DZ_BG, font=("Segoe UI", 8), fg="#94A3B8",
            cursor="hand2")
        dz_sub.pack(anchor="w")

        # ── شريط العداد وزر إضافة ─────────────────────────────────
        files_bar = tk.Frame(file_frame, bg="white")
        files_bar.pack(fill="x", pady=(6, 2))

        counter_label = tk.Label(
            files_bar,
            text=t("send_files_counter", n=0, max=MAX_FILES),
            bg="white", font=("Segoe UI", 8), fg="#94A3B8")
        counter_label.pack(side="right")

        add_btn = tk.Button(
            files_bar, text=t("send_add_more"),
            bg="#F1F5F9", fg="#374151",
            bd=0, padx=10, pady=4,
            font=("Segoe UI", 8), cursor="hand2")
        add_btn.pack(side="left")

        # ── قائمة الملفات (Canvas + Scrollbar) ────────────────────
        list_outer = tk.Frame(file_frame, bg="white",
                              highlightthickness=1,
                              highlightbackground="#E2E8F0")

        files_canvas = tk.Canvas(list_outer, bg="white", bd=0,
                                 highlightthickness=0, height=130)
        list_scroll  = tk.Scrollbar(list_outer, orient="vertical",
                                    command=files_canvas.yview)
        files_canvas.configure(yscrollcommand=list_scroll.set)
        files_canvas.pack(side="left", fill="both", expand=True)
        list_scroll.pack(side="right", fill="y")

        list_inner = tk.Frame(files_canvas, bg="white")
        _list_win  = files_canvas.create_window(
            (0, 0), window=list_inner, anchor="nw")

        files_canvas.bind(
            "<Configure>",
            lambda e: files_canvas.itemconfig(_list_win, width=e.width))
        list_inner.bind(
            "<Configure>",
            lambda _e: files_canvas.configure(
                scrollregion=files_canvas.bbox("all")))

        # ── Helpers ────────────────────────────────────────────────
        def _fmt_size(path):
            try:
                sz = os.path.getsize(path)
                return (f"{sz/1048576:.1f} MB" if sz > 1048576
                        else f"{sz/1024:.0f} KB")
            except Exception:
                return "?"

        def _file_icon(path):
            ext = os.path.splitext(path)[1].lower()
            if ext in (".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"):
                return "🖼"
            if ext in (".mp4", ".mkv", ".avi", ".mov", ".wmv", ".m4v"):
                return "🎬"
            if ext in (".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a"):
                return "🎵"
            if ext == ".pdf":
                return "📕"
            if ext in (".zip", ".rar", ".7z", ".tar", ".gz"):
                return "📦"
            if ext in (".doc", ".docx", ".txt", ".odt", ".rtf"):
                return "📝"
            if ext == ".apk":
                return "📱"
            return "📄"

        def _refresh_files_ui():
            for w in list_inner.winfo_children():
                w.destroy()
            for idx, fpath in enumerate(files_list):
                row = tk.Frame(list_inner, bg="white")
                row.pack(fill="x", padx=4, pady=1)
                tk.Label(row, text=_file_icon(fpath),
                         bg="white", font=("Segoe UI", 11)).pack(
                    side="left", padx=(6, 3))
                name = os.path.basename(fpath)
                if len(name) > 40:
                    name = name[:37] + "..."
                tk.Label(row, text=name, bg="white",
                         font=("Segoe UI", 9), fg="#1E293B",
                         anchor="w").pack(side="left", fill="x", expand=True)
                tk.Label(row, text=_fmt_size(fpath), bg="white",
                         font=("Segoe UI", 8), fg="#94A3B8").pack(
                    side="left", padx=(4, 2))

                def _rm(i=idx):
                    if 0 <= i < len(files_list):
                        files_list.pop(i)
                        _refresh_files_ui()
                        _update_counter()

                x_btn = tk.Label(row, text="×", bg="white",
                                 fg="#EF4444",
                                 font=("Segoe UI", 13, "bold"),
                                 cursor="hand2", padx=8)
                x_btn.pack(side="right")
                x_btn.bind("<Button-1>", lambda _e, f=_rm: f())

                if idx < len(files_list) - 1:
                    tk.Frame(list_inner, bg="#F1F5F9",
                             height=1).pack(fill="x", padx=6)

            if files_list:
                list_outer.pack(fill="x", pady=(0, 4))
            else:
                list_outer.pack_forget()
            files_canvas.update_idletasks()
            files_canvas.configure(scrollregion=files_canvas.bbox("all"))

        def _update_counter():
            n = len(files_list)
            counter_label.config(
                text=t("send_files_counter", n=n, max=MAX_FILES),
                fg="#2E7D32" if n > 0 else "#94A3B8")
            is_full = n >= MAX_FILES
            add_btn.config(
                state="disabled" if is_full else "normal",
                bg="#E2E8F0"    if is_full else "#F1F5F9",
                fg="#9CA3AF"   if is_full else "#374151")

        def _add_files(paths):
            added = 0
            for p in paths:
                p = p.strip()
                if not os.path.isfile(p):
                    continue
                if p in files_list:
                    continue
                if len(files_list) >= MAX_FILES:
                    messagebox.showwarning(
                        t("app_header"),
                        t("send_max_files_warn", max=MAX_FILES),
                        parent=win)
                    break
                files_list.append(p)
                added += 1
            if added:
                _refresh_files_ui()
                _update_counter()

        def _pick_files(_e=None):
            paths = filedialog.askopenfilenames(parent=win)
            if paths:
                _add_files(list(paths))

        # ── Ctrl+V ────────────────────────────────────────────────
        def _paste_file(_e=None):
            try:
                txt = win.clipboard_get().strip().strip('"').strip("'")
                if os.path.isfile(txt):
                    _add_files([txt])
            except Exception:
                pass

        win.bind("<Control-v>", _paste_file)
        win.bind("<Control-V>", _paste_file)

        # كلك على أي جزء من منطقة السحب أو زر الإضافة
        for _w in (drop_zone, dz_inner, dz_icon, dz_text_f, dz_title, dz_sub):
            _w.bind("<Button-1>", _pick_files)
        add_btn.config(command=_pick_files)

        # ── Drag & Drop ───────────────────────────────────────────
        if HAS_DND:
            def _parse_dnd_paths(raw):
                paths = []
                remaining = raw.strip()
                while remaining:
                    if remaining.startswith("{"):
                        end = remaining.find("}")
                        if end > 0:
                            paths.append(remaining[1:end])
                            remaining = remaining[end+1:].strip()
                        else:
                            paths.append(remaining[1:])
                            break
                    else:
                        paths.extend(remaining.split())
                        break
                return [p for p in paths if os.path.isfile(p)]

            def _on_drop(event):
                _add_files(_parse_dnd_paths(event.data))
                drop_zone.config(bg=DZ_BG, highlightbackground=DZ_BORDER)
                for _w in (dz_inner, dz_icon, dz_text_f, dz_title, dz_sub):
                    try: _w.config(bg=DZ_BG)
                    except Exception: pass
                dz_title.config(fg="#475569")

            def _on_drag_enter(_event):
                drop_zone.config(bg=DZ_HOVER, highlightbackground=DZ_BORDER_H)
                for _w in (dz_inner, dz_icon, dz_text_f, dz_title, dz_sub):
                    try: _w.config(bg=DZ_HOVER)
                    except Exception: pass
                dz_title.config(fg="#1E40AF")

            def _on_drag_leave(_event):
                drop_zone.config(bg=DZ_BG, highlightbackground=DZ_BORDER)
                for _w in (dz_inner, dz_icon, dz_text_f, dz_title, dz_sub):
                    try: _w.config(bg=DZ_BG)
                    except Exception: pass
                dz_title.config(fg="#475569")

            _dnd_targets = [drop_zone, dz_inner, dz_icon,
                            dz_text_f, dz_title, dz_sub]
            for _w in _dnd_targets:
                _w.drop_target_register(DND_FILES)
                _w.dnd_bind("<<Drop>>", _on_drop)
                _w.dnd_bind("<<DragEnter>>", _on_drag_enter)
                _w.dnd_bind("<<DragLeave>>", _on_drag_leave)

        _update_counter()

        # — نص —
        text_frame = tk.Frame(content_area, bg="white")
        text_input  = tk.Text(text_frame, font=("Segoe UI", 10),
                              bd=1, relief="solid", height=4, wrap="word")
        text_input.pack(fill="both", expand=True, pady=4)

        def _switch_mode(mode):
            if mode == "file":
                text_frame.pack_forget()
                file_frame.pack(fill="both", expand=True)
            else:
                file_frame.pack_forget()
                text_frame.pack(fill="both", expand=True)

        file_frame.pack(fill="both", expand=True)

        # ── أزرار الإرسال / الإلغاء (btn_frame already created above) ──

        def _do_send():
            ip   = manual_ip_var.get().strip() or selected_ip.get()
            port = selected_port.get() or 7789
            if not ip:
                messagebox.showwarning(t("app_header"),
                    t("send_no_device_warn"), parent=win)
                return
            mode = mode_var.get()
            if mode == "file":
                if not files_list:
                    messagebox.showwarning(t("app_header"),
                        t("send_no_files_warn"), parent=win)
                    return
                files_to_send = list(files_list)
                total_files   = len(files_to_send)
                send_btn.config(state="disabled", bg="#94A3B8")
                progress_bar.pack(fill="x", padx=20, pady=(0, 4))
                progress_label.pack(fill="x", padx=20)

                def _thread():
                    ok_count   = 0
                    fail_count = 0
                    loop = asyncio.new_event_loop()
                    asyncio.set_event_loop(loop)
                    for idx, fpath in enumerate(files_to_send):
                        cur = idx + 1
                        def _progress(sent, total,
                                      _cur=cur, _tot=total_files):
                            pct = int(sent * 100 / total) if total else 0
                            try:
                                win.after(0, lambda p=pct, c=_cur,
                                          tt=_tot: (
                                    progress_var.set(p),
                                    progress_label.config(
                                        text=t("send_progress_multi",
                                               cur=c, total=tt, pct=p))))
                            except Exception:
                                pass
                        success, msg = loop.run_until_complete(
                            self.sender.send_file(
                                ip, port, fpath, _progress))
                        if success:
                            ok_count += 1
                        else:
                            fail_count += 1
                            logger.warning("Send failed %s: %s",
                                           os.path.basename(fpath), msg)
                    try:
                        if fail_count == 0:
                            n = ok_count
                            win.after(0, lambda: progress_label.config(
                                text=t("send_files_ok", n=n)))
                            win.after(1500, win.destroy)
                            self.root.after(0, lambda: self._toast(
                                t("app_header"),
                                t("toast_files_ok", n=ok_count)))
                        else:
                            ok = ok_count
                            failed = fail_count
                            win.after(0, lambda: (
                                progress_label.config(
                                    text=t("send_files_partial",
                                           ok=ok, total=total_files,
                                           failed=failed)),
                                send_btn.config(state="normal",
                                                bg="#2E7D32")))
                    except Exception:
                        pass
                Thread(target=_thread, daemon=True).start()
            else:
                content = text_input.get("1.0", "end-1c").strip()
                if not content:
                    messagebox.showwarning(t("app_header"),
                        t("send_no_text_warn"), parent=win)
                    return
                send_btn.config(state="disabled", bg="#94A3B8")
                progress_label.pack(fill="x", padx=20)
                progress_label.config(text=t("send_text_progress"))

                def _thread():
                    loop = asyncio.new_event_loop()
                    asyncio.set_event_loop(loop)
                    success, msg = loop.run_until_complete(
                        self.sender.send_text(ip, port, content))
                    try:
                        if success:
                            win.after(0, lambda: progress_label.config(
                                text=t("send_text_ok")))
                            win.after(1500, win.destroy)
                            self.root.after(0, lambda: self._toast(
                                t("app_header"), t("toast_text_ok")))
                        else:
                            win.after(0, lambda: (
                                progress_label.config(
                                    text=t("send_fail", msg=msg)),
                                send_btn.config(state="normal",
                                                bg="#2E7D32")))
                    except Exception:
                        pass
                Thread(target=_thread, daemon=True).start()

        send_btn = tk.Button(
            btn_frame, text=t("send_btn"), command=_do_send,
            bg="#2E7D32", fg="white", bd=0, padx=30, pady=10,
            font=("Segoe UI", 11, "bold"), cursor="hand2",
            activebackground="#1B5E20", activeforeground="white")
        send_btn.pack(side="right")

        tk.Button(btn_frame, text=t("send_cancel"), command=win.destroy,
                  bg="#F1F5F9", fg="#64748B", bd=0, padx=20, pady=10,
                  font=("Segoe UI", 10), cursor="hand2").pack(side="right", padx=(0, 8))

        win.bind("<Return>", lambda _e: _do_send())
        win.bind("<Escape>", lambda _e: win.destroy())

    def _set_sender_ui_state(self, sending, label=""):
        if sending:
            self.btn_send.config(state="disabled", bg="#9CA3AF")
            self.status_label.config(text=label)
            self._set_status_colors("#F0F9FF", "#3B82F6")
        else:
            self.btn_send.config(state="normal", bg="#2E7D32")
            self._refresh_status_view()

    # ---------- Visual pulse on status card ----------
    def _pulse_status_card(self):
        """Briefly flash the status card background to acknowledge a new file
        or text arrival. Restores the current palette automatically."""
        if not hasattr(self, "status_frame") or not self.status_frame.winfo_exists():
            return
        try:
            original_accent = self.status_accent.cget("bg")
        except Exception:
            return
        def _flash(step=0):
            colors = ["#86EFAC", "#4ADE80", "#22C55E", "#4ADE80", "#86EFAC"]
            if step < len(colors):
                try: self.status_accent.config(bg=colors[step])
                except Exception: pass
                self.root.after(90, lambda: _flash(step + 1))
            else:
                # Restore via normal refresh; _refresh_status_view will repaint.
                try: self.status_accent.config(bg=original_accent)
                except Exception: pass
                self._refresh_status_view()
        _flash()

    # ---------- WebSocket Handler ----------
    async def _handle_ws(self, websocket, *args):
        # *args absorbs `path` on old websockets (<13) and is empty on new (>=13).
        _loop = asyncio.get_running_loop()
        meta = None
        chunk_count = 0
        got = 0
        tmp_fp = None      # open file handle for streaming writes
        tmp_path = None    # path of the temp file on disk
        last_progress_ui = 0.0  # throttles live progress UI updates
        device_name_local = ""  # Name this particular client announced.
        device_id_local = ""    # Unique phone id (UUID) if announced.
        # Trust state for THIS connection. A connection is "trusted" once we've
        # either matched device_id against trusted_devices.json, or the user
        # approved it via the pairing dialog on this session.
        trusted = False
        # Peer IP shown in the pairing dialog.
        try:
            peer_ip = websocket.remote_address[0] if websocket.remote_address else "?"
        except Exception:
            peer_ip = "?"

        self.active_clients += 1
        # IMMEDIATE UI update — don't wait for the polling tick.
        self._notify_state_change()
        try:
            async for message in websocket:
                # --- JSON text messages ---
                if isinstance(message, str):
                    try:
                        data = json.loads(message)
                    except json.JSONDecodeError:
                        continue
                    msg_type = data.get("type", "")

                    if msg_type == "hello":
                        # Android announces itself:
                        #   {"type":"hello","device":"Galaxy S22","device_id":"<uuid>"}
                        # device_id is optional (older app versions won't send it);
                        # in that case we treat it as an "unknown" device and still
                        # require pairing. No silent-trust fallback.
                        device_name_local = (data.get("device") or "").strip()[:40]
                        device_id_local = (data.get("device_id") or "").strip()[:64]
                        if not device_id_local:
                            # Synthesize a fingerprint so legacy clients still get a
                            # stable identity for this IP — prompts once per IP.
                            device_id_local = "legacy-" + peer_ip
                        if device_name_local:
                            self.connected_device_name = device_name_local
                            self.connected_device_id = device_id_local
                            logger.info("Phone announced: %s (id=%s ip=%s)",
                                        device_name_local, device_id_local[:8], peer_ip)
                            self._notify_state_change()
                        # Check trust. If already trusted, ack immediately.
                        # Otherwise request pairing via Tk dialog (non-blocking on
                        # the WS event loop — we run the wait in a thread).
                        if self.trusted.is_trusted(device_id_local):
                            trusted = True
                            self.trusted.touch(device_id_local, device_name_local, ip=peer_ip)
                            self.pairing_log.add("allowed", device_name_local,
                                                 peer_ip, device_id_local)
                            await websocket.send(json.dumps(
                                {"status": "hello", "name": socket.gethostname(),
                                 "version": APP_VERSION, "paired": True}))
                        else:
                            logger.info("Pairing required for %s (id=%s)",
                                        device_name_local, device_id_local[:8])
                            # Tell phone to display "waiting for approval" UI.
                            await websocket.send(json.dumps(
                                {"status": "pairing_required",
                                 "name": socket.gethostname(),
                                 "version": APP_VERSION,
                                 "timeout": PAIRING_TIMEOUT_SEC}))
                            self._pending_pair_name = device_name_local or t("device_generic")
                            self._notify_state_change()
                            approved = await _loop.run_in_executor(
                                None, self._ask_pairing_approval,
                                device_id_local, device_name_local, peer_ip)
                            self._pending_pair_name = ""
                            self._notify_state_change()
                            if approved:
                                self.trusted.trust(device_id_local, device_name_local, ip=peer_ip)
                                self.pairing_log.add("approved", device_name_local,
                                                     peer_ip, device_id_local)
                                trusted = True
                                await websocket.send(json.dumps(
                                    {"status": "paired"}))
                            else:
                                self.pairing_log.add("rejected", device_name_local,
                                                     peer_ip, device_id_local)
                                await websocket.send(json.dumps(
                                    {"status": "rejected",
                                     "message": t("pair_rejected_msg")}))
                                try: await websocket.close()
                                except Exception: pass
                                return

                    elif msg_type == "ping":
                        # ping is allowed pre-trust (phone uses it for TCP probe).
                        await websocket.send(json.dumps({"status": "pong"}))

                    elif msg_type in ("text", "url", "file_meta") and not trusted:
                        # Safety: if a client skipped `hello` or sent data
                        # before approval, reject immediately.
                        self.pairing_log.add("blocked", device_name_local or "?",
                                             peer_ip, device_id_local)
                        await websocket.send(json.dumps(
                            {"status": "rejected",
                             "message": t("pair_required_msg")}))
                        try: await websocket.close()
                        except Exception: pass
                        return

                    elif msg_type == "text":
                        txt = data.get("text", "")
                        self._recv_text(txt)
                        await websocket.send(json.dumps({"status": "saved"}))

                    elif msg_type == "url":
                        url = data.get("url", "")
                        self._recv_url(url)
                        await websocket.send(json.dumps({"status": "saved"}))

                    elif msg_type == "file_meta":
                        meta = data
                        chunk_count = 0
                        got = 0
                        last_progress_ui = 0.0
                        logger.info("File transfer started: %s (%s bytes, %s chunks)",
                                    meta.get('filename', '?'), meta.get('size', '?'),
                                    meta.get('chunks', '?'))
                        # Open a temp file for streaming writes so we
                        # don't accumulate the entire payload in RAM.
                        try:
                            if tmp_fp:
                                try: tmp_fp.close()
                                except Exception: pass
                            os.makedirs(self.settings.save_path, exist_ok=True)
                            fd, tmp_path = tempfile.mkstemp(
                                dir=self.settings.save_path,
                                prefix=".wameed_tmp_")
                            tmp_fp = os.fdopen(fd, "wb")
                        except Exception:
                            logger.exception("Failed to create temp file for streaming write")
                            tmp_fp = None
                            tmp_path = None
                        # Snappy visual feedback: as soon as we learn a file is
                        # incoming, flip the status card to "receiving" before a
                        # single byte lands. Otherwise the user sees stale
                        # "جاهز" for the whole transfer.
                        try:
                            _fn = meta.get("filename", "file")
                            self.root.after(0, lambda n=_fn: (
                                self.status_label.config(
                                    text=t("recv_incoming", name=n), fg="#1E40AF"),
                                self.status_sub_label.config(
                                    text="0%", fg="#2563EB"),
                                self._set_status_colors("#EFF6FF", "#2563EB"),
                            ))
                        except Exception:
                            pass

                # --- Binary chunk ---
                elif isinstance(message, bytes) and meta:
                    # Stream directly to disk using a thread pool to avoid blocking the event loop.
                    # This is crucial for high-speed transfers on slower HDDs.
                    if tmp_fp:
                        try:
                            await _loop.run_in_executor(None, tmp_fp.write, message)
                        except OSError as _disk_err:
                            logger.error("Disk write failed (%s) — aborting transfer of %s",
                                         _disk_err, meta.get("filename", "?"))
                            try:
                                tmp_fp.close()
                            except Exception:
                                pass
                            tmp_fp = None
                            try:
                                await websocket.send(json.dumps(
                                    {"status": "error",
                                     "message": f"PC disk error: {_disk_err}"}))
                                await websocket.close()
                            except Exception:
                                pass
                            return
                        except Exception:
                            logger.exception("Streaming write failed")
                    chunk_count += 1
                    got += len(message)
                    expected = meta.get("size", 0)
                    expected_chunks = meta.get("chunks", 1)

                    # Throttled live progress update (~6/s) so the user watches
                    # the % climb instead of a frozen label.
                    now_ts = time.time()
                    if expected > 0 and (now_ts - last_progress_ui) > 0.15:
                        pct = min(99, int(got * 100 / expected))
                        try:
                            self.root.after(0, lambda p=pct: (
                                self.status_sub_label.config(
                                    text=f"{p}%", fg="#2563EB"),
                            ))
                        except Exception:
                            pass
                        last_progress_ui = now_ts

                    # Send progress ack to the phone every 5 chunks so its
                    # watchdog stays alive during large-file transfers.  OkHttp
                    # queues chunks to the OS buffer almost instantly, so the
                    # phone's "All N chunks sent" log fires well before the
                    # data physically arrives over WiFi.  Without these acks
                    # the phone's idle-watchdog fires for files ≥ ~30 MB.
                    if chunk_count % 5 == 0:
                        try:
                            await websocket.send(json.dumps(
                                {"status": "progress", "received": got}))
                        except Exception:
                            pass

                    if chunk_count >= expected_chunks or got >= expected:
                        fname = meta.get("filename", "unknown")
                        mime = meta.get("mime", "")
                        # Flip to "saving" before we hit disk — disk write for
                        # a 50–300MB file can take a noticeable fraction of a
                        # second, and the user deserves instant "got it" feedback.
                        try:
                            self.root.after(0, lambda: (
                                self.status_sub_label.config(
                                    text=t("recv_saving"), fg="#15803D"),
                            ))
                        except Exception:
                            pass
                        # Close the streaming temp file and rename/move it
                        # to the final destination.  This is near-instant
                        # because the data is already on disk.
                        if tmp_fp:
                            try: tmp_fp.close()
                            except Exception: pass
                            tmp_fp = None
                        saved = await asyncio.to_thread(
                            self._finalize_file, fname, tmp_path)
                        tmp_path = None
                        if saved:
                            logger.info("File saved OK: %s (%d bytes) -> %s", fname, got, saved)
                            self._log(fname, mime, got, "success", path=saved)
                            self.last_received = (fname, datetime.now())
                            # Tracks "recently active" state on the idle card.
                            self.last_file_received_at = time.time()
                            # Trigger visual pulse on status card (main thread).
                            try: self.root.after(0, self._pulse_status_card)
                            except Exception: pass

                            # Send ack to phone FIRST so the UI feels fast and "Saving" phase ends.
                            # The phone watchdog is sensitive to this final response.
                            await websocket.send(json.dumps(
                                {"status": "saved", "path": saved}))

                            # Now display/toast (non-blocking to the socket loop).
                            self._display_file(saved, fname)
                        else:
                            logger.error("File save FAILED: %s (%d bytes)", fname, got)
                            self._log(fname, mime, got, "error")
                            await websocket.send(json.dumps(
                                {"status": "error", "message": "save failed"}))
                        meta = None; chunk_count = 0; got = 0
                        last_progress_ui = 0.0

        except websockets.exceptions.ConnectionClosed:
            if meta:
                logger.warning("WS connection closed DURING file transfer: %s (got %d/%s bytes)",
                               meta.get('filename', '?'), got, meta.get('size', '?'))
            else:
                logger.debug("WS connection closed by client")
        except Exception:
            logger.exception("Error handling WS client")
        finally:
            # Clean up any leftover temp file from an interrupted transfer.
            if tmp_fp:
                try: tmp_fp.close()
                except Exception: pass
            if tmp_path:
                try:
                    if os.path.exists(tmp_path):
                        os.remove(tmp_path)
                except Exception:
                    pass
            self.active_clients = max(0, self.active_clients - 1)
            # IMPORTANT: do NOT clear connected_device_name immediately.
            # Short-lived sends (sendPing, sendFile, etc.) open a WS, exchange
            # a few messages, then close — all within ~100ms. Clearing the name
            # right away would make the UI flicker between "متصل" and "في انتظار"
            # rapidly. Instead, _refresh_status_view honours `last_activity_at`
            # and keeps showing "متصل" for `connection_grace_sec` seconds.
            self.last_activity_at = time.time()
            # Trigger an immediate refresh so the UI reflects the closed
            # connection (or stays "متصل" if grace window still open).
            self._notify_state_change()

    # ---------- File operations ----------
    def _finalize_file(self, filename, tmp_path):
        """Move the already-written temp file to its final name. Near-instant
        because the data is already on disk (same filesystem = rename)."""
        try:
            if not tmp_path or not os.path.exists(tmp_path):
                return None
            os.makedirs(self.settings.save_path, exist_ok=True)
            fp = os.path.join(self.settings.save_path, filename)
            if os.path.exists(fp):
                base, ext = os.path.splitext(filename)
                i = 1
                while os.path.exists(fp):
                    fp = os.path.join(self.settings.save_path, f"{base}_{i}{ext}")
                    i += 1
            # Atomic rename (same dir → instant). shutil.move handles cross-fs.
            shutil.move(tmp_path, fp)
            return fp
        except Exception:
            logger.exception("Failed to finalize file %s", filename)
            try:
                if tmp_path and os.path.exists(tmp_path):
                    os.remove(tmp_path)
            except Exception:
                pass
            return None

    def _save_file(self, filename, chunks):
        try:
            os.makedirs(self.settings.save_path, exist_ok=True)
            fp = os.path.join(self.settings.save_path, filename)
            if os.path.exists(fp):
                base, ext = os.path.splitext(filename)
                i = 1
                while os.path.exists(fp):
                    fp = os.path.join(self.settings.save_path, f"{base}_{i}{ext}")
                    i += 1
            with open(fp, "wb") as f:
                for ch in chunks:
                    f.write(ch)
            return fp
        except Exception:
            return None

    def _display_file(self, path, filename):
        mode = self.settings.display_mode
        # Schedule everything on the Tk main thread.
        # os.startfile / ShellExecuteW require a COM-STA context which is only
        # guaranteed on the main thread; calling from a daemon thread causes
        # silent failures on many Windows setups (PyInstaller noconsole builds).
        def _main_thread_display():
            # Show notification FIRST so it's visible before the file viewer
            # steals focus (especially full-screen image/PDF viewers).
            if mode in ("path", "both"):
                self._toast(t("recv_file", name=filename), path, open_path=path)

            if mode in ("open", "both"):
                logger.info("Auto-opening file: %s", path)
                ok, detail = self._shell_open(path)
                if not ok:
                    logger.warning("All open methods failed for %s: %s", path, detail)
                    # Show a toast with the path so the user can open it manually
                    self._toast(
                        t("recv_save_fail", name=filename),
                        t("recv_save_fail_body", path=path),
                        open_path=path
                    )

        self.root.after(0, _main_thread_display)

    def _recv_text(self, text):
        try:
            self.root.clipboard_clear()
            self.root.clipboard_append(text)
        except Exception:
            pass
        self._log(t("log_text"), "text/plain", len(text.encode("utf-8")))
        self.root.after(0, lambda: self.status_label.config(text=t("text_copied")))
        self.last_file_received_at = time.time()
        try: self.root.after(0, self._pulse_status_card)
        except Exception: pass
        self._toast(t("toast_new_text"), text[:120])

    def _recv_url(self, url):
        try:
            webbrowser.open(url)
        except Exception:
            pass
        self._log(url[:60], "url", len(url.encode("utf-8")))
        self.root.after(0, lambda: self.status_label.config(text=t("url_opened")))
        self.last_file_received_at = time.time()
        try: self.root.after(0, self._pulse_status_card)
        except Exception: pass

    # ---------- Toast notification ----------
    def _toast(self, title, body, open_path=None):
        """Show a toast. If open_path is given, tapping the toast (or its
        native tray notification) reveals that file in Explorer."""
        # Native Windows notification via pystray (appears in notification center).
        if self.tray_icon:
            # Run in a separate thread because pystray.notify can sometimes hang
            # or be slow depending on the Windows notification queue state.
            def _native_notify():
                try:
                    logger.debug("Sending native tray notification: %s", title)
                    self.tray_icon.notify(title, body[:120])
                except Exception as e:
                    logger.warning("pystray notify failed: %s", e)
            Thread(target=_native_notify, daemon=True).start()

        # Also show our custom Tk toast so it can be clickable (open folder).
        self.root.after(0, lambda: self._show_toast(title, body, open_path))

    def _show_toast(self, title, body, open_path=None):
        try:
            toast_win = tk.Toplevel(self.root)
        except Exception:
            return
        toast_win.overrideredirect(True); toast_win.attributes("-topmost", True); toast_win.configure(bg="#333")
        sw = self.root.winfo_screenwidth()
        sh = self.root.winfo_screenheight()
        tw, th = 360, 92 if open_path else 80
        toast_win.geometry(f"{tw}x{th}+{sw-tw-20}+{sh-th-60}")
        # Ensure toast stays visible even if another app just took focus.
        toast_win.lift()
        toast_win.after(100, lambda: (toast_win.lift(), toast_win.attributes("-topmost", True)) if toast_win.winfo_exists() else None)

        cursor = "hand2" if open_path else ""
        title_lbl = tk.Label(toast_win, text=title, fg="white", bg="#333",
                             font=("Segoe UI", 11, "bold"), anchor="w", cursor=cursor)
        title_lbl.pack(fill="x", padx=14, pady=(10, 0))
        body_lbl = tk.Label(toast_win, text=body[:90], fg="#CCC", bg="#333",
                            font=("Segoe UI", 9), anchor="w", wraplength=330, cursor=cursor)
        body_lbl.pack(fill="x", padx=14, pady=(2, 4))

        if open_path:
            hint = tk.Label(toast_win, text=t("toast_click_hint"), fg="#7FD6B0", bg="#333",
                            font=("Segoe UI", 8, "italic"), anchor="w", cursor=cursor)
            hint.pack(fill="x", padx=14, pady=(0, 8))
            def _open_folder(_e=None):
                try:
                    folder = os.path.dirname(open_path) or self.settings.save_path
                    # Open Explorer and select the file (more useful than plain folder)
                    if os.path.exists(open_path):
                        subprocess.Popen(["explorer", "/select,", os.path.normpath(open_path)])
                    else:
                        ok, detail = self._shell_open(folder)
                        if not ok:
                            logger.warning("Failed to open folder %s: %s", folder, detail)
                except Exception:
                    logger.exception("Failed to open folder for %s", open_path)
                try: toast_win.destroy()
                except Exception: pass
            for w in (toast_win, title_lbl, body_lbl, hint):
                w.bind("<Button-1>", _open_folder)

        toast_win.after(5000 if open_path else 4500, lambda: (toast_win.destroy() if toast_win.winfo_exists() else None))

    # ---------- System tray ----------
    def _init_tray(self):
        """Initialize the system tray icon at startup (always visible).
        This enables native Windows notification center toasts via
        tray_icon.notify() at any time, not just when minimized."""
        if not HAS_TRAY:
            return
        try:
            ico = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wameed.ico")
            img = PILImage.open(ico) if os.path.exists(ico) else PILImage.new("RGB", (64,64), "#2E7D32")
            menu = pystray.Menu(
                pystray.MenuItem(t("tray_open"), self._show_from_tray, default=True),
                pystray.MenuItem(t("tray_folder"),
                                 lambda: self._shell_open(self.settings.save_path)),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem(t("tray_quit"), self._quit))
            self.tray_icon = pystray.Icon("wameed", img, t("app_header") + " \u2014 Wameed", menu)
            Thread(target=self.tray_icon.run, daemon=True).start()
        except Exception:
            logger.exception("Failed to initialize system tray icon")

    def _minimize_to_tray(self):
        if not HAS_TRAY:
            self.root.iconify(); return
        self.root.withdraw()

    def _show_from_tray(self):
        self.root.after(0, self.root.deiconify)

    def _on_close(self):
        if HAS_TRAY:
            self._minimize_to_tray()
        else:
            self._confirm_quit()

    # ---------- Diagnostics ----------
    def _show_diagnostics(self):
        """Comprehensive diagnostics popup — first line of defense for any bug report."""
        lines = []
        lines.append(t("diag_title"))
        lines.append("=" * 40)
        lines.append(f"{t('diag_version')}        {APP_VERSION}")
        lines.append(f"PID:              {os.getpid()}")
        lines.append(f"Frozen (exe):     {bool(getattr(sys, 'frozen', False))}")
        lines.append(f"Python:           {sys.version.split()[0]}")
        lines.append(f"Platform:         {sys.platform}")
        lines.append("")
        lines.append(f"WS server alive:  {self.ws_alive.is_set()}")
        lines.append(f"Backend error:    {self._backend_error or t('diag_backend_err')}")
        lines.append(f"Active sessions:  {self.active_clients}")
        lines.append(f"Local IP:         {self._get_ip()}")
        lines.append(f"WS port (TCP):    {self.settings.port}")
        lines.append(f"Discovery (UDP):  {DISCOVERY_PORT}")
        lines.append("")

        # Is port actually listening?
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(0.5)
            r = sock.connect_ex(("127.0.0.1", self.settings.port))
            sock.close()
            lines.append(f"Port LISTEN test: {t('diag_port_yes') if r == 0 else t('diag_port_no')}")
        except Exception as e:
            lines.append(f"Port LISTEN test: error \u2014 {e}")

        # Firewall rules
        try:
            r = subprocess.run(
                ["netsh", "advfirewall", "firewall", "show", "rule", "name=all", "dir=in"],
                capture_output=True, text=True, timeout=4,
                encoding="utf-8", errors="ignore")
            out = r.stdout or ""
            ws_rule  = ("Wameed WS" in out)
            udp_rule = ("Wameed Discovery" in out)
            lines.append(f"Firewall (WS):    {t('diag_fw_present') if ws_rule else t('diag_fw_missing')}")
            lines.append(f"Firewall (UDP):   {t('diag_fw_present') if udp_rule else t('diag_fw_missing')}")
        except Exception as e:
            lines.append(f"Firewall check:   error \u2014 {e}")

        lines.append("")
        lines.append(t("diag_trusted_header"))
        if not self.trusted.devices:
            lines.append(t("diag_none"))
        else:
            for did, info in self.trusted.ordered()[:10]:
                lines.append(f"  • {info.get('name','?')}  (id={did[:8]}..  last={info.get('last_seen','?')})")

        lines.append("")
        lines.append(t("diag_pairing_header"))
        if not self.pairing_log.events:
            lines.append(t("diag_none"))
        else:
            for ev in self.pairing_log.events[:10]:
                lines.append(f"  {ev.get('time','?')}  [{ev.get('action','?')}]  "
                             f"{ev.get('name','?')}  @ {ev.get('ip','?')}")

        lines.append("")
        lines.append(f"Log file:       {LOG_FILE}")
        lines.append(f"Trusted file:   {TRUSTED_FILE}")
        lines.append(f"Settings dir:   {SETTINGS_DIR}")

        report = "\n".join(lines)
        logger.info("Diagnostics:\n%s", report)

        # Popup window
        win = tk.Toplevel(self.root)
        win.title(t("diag_window_title"))
        win.geometry("540x460")
        win.configure(bg="white")
        try: win.iconbitmap(os.path.join(os.path.dirname(os.path.abspath(__file__)), "wameed.ico"))
        except Exception: pass

        txt = tk.Text(win, font=("Consolas", 9), bg="#F8FAFC", bd=0, padx=14, pady=12)
        txt.insert("1.0", report)
        txt.config(state="disabled")
        txt.pack(fill="both", expand=True, padx=10, pady=(10, 6))

        bf = tk.Frame(win, bg="white"); bf.pack(fill="x", padx=10, pady=(0, 10))
        def _copy():
            try:
                self.root.clipboard_clear(); self.root.clipboard_append(report)
                messagebox.showinfo(t("app_header"), t("report_copied"), parent=win)
            except Exception: pass
        def _open_log():
            ok, detail = self._shell_open(LOG_FILE)
            if not ok:
                messagebox.showwarning(t("app_header"), t("log_open_fail", detail=detail), parent=win)
        tk.Button(bf, text=t("btn_copy_report"), command=_copy,
                  bg="#F1F5F9", fg="#374151",
                  bd=0, padx=14, pady=7, cursor="hand2",
                  font=("Segoe UI", 9)).pack(side="left")
        tk.Button(bf, text=t("btn_open_log"), command=_open_log,
                  bg="#EFF6FF", fg="#1E40AF",
                  bd=0, padx=14, pady=7, cursor="hand2",
                  font=("Segoe UI", 9)).pack(side="left", padx=6)
        tk.Button(bf, text=t("btn_close"), command=win.destroy,
                  bg="#F1F5F9", fg="#64748B",
                  bd=0, padx=14, pady=7, cursor="hand2",
                  font=("Segoe UI", 9)).pack(side="right")

    def _confirm_quit(self):
        ans = messagebox.askyesno(
            t("app_header"),
            t("confirm_quit")
        )
        if ans:
            self._quit()

    def _quit(self):
        logger.info("Application quitting...")
        # تأمين إيقاف الـ Tray Icon
        if self.tray_icon:
            try: self.tray_icon.stop()
            except Exception: pass

        # إيقاف اكتشاف الهواتف
        try: self.phone_discovery.stop()
        except Exception: pass

        # محاولة إيقاف حلقة asyncio بشكل نظيف
        try:
            # تعيين الحدث لإبلاغ المهام بالتوقف
            self.ws_alive.clear()
            # إيقاف خادم الـ WebSocket إذا كان يعمل
            if hasattr(self, 'ws_server') and self.ws_server:
                self.ws_server.close()
        except Exception: pass

        self.root.quit()
        self.root.destroy()


# ======================== Main ========================
if __name__ == "__main__":
    # Enforce single-instance FIRST — before any UI or ports are touched.
    _lock_sock = _acquire_single_instance_lock()
    if _lock_sock is None:
        # Another instance is running — exit silently to avoid noisy duplicates.
        try:
            r = tk.Tk(); r.withdraw()
            messagebox.showinfo(t("app_header"), t("already_running"))
            r.destroy()
        except Exception:
            pass
        sys.exit(0)

    try:
        logger.info("Launching main UI (DnD=%s)", HAS_DND)
        root = TkinterDnD.Tk() if HAS_DND else tk.Tk()
        app = WameedApp(root)
        root.mainloop()
        logger.info("Main UI closed cleanly")
    except Exception:
        logger.exception("Fatal error in main")
        try:
            messagebox.showerror(t("error_title"),
                t("fatal_error", tb=traceback.format_exc(), log=LOG_FILE))
        except Exception:
            pass
    finally:
        try: _lock_sock.close()
        except Exception: pass
