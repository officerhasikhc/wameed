"""بروتوكول وميض السلكي — المالك الوحيد لكل ما يعبر الشبكة بين الكمبيوتر والهاتف:
أرقام المنافذ، أنواع الرسائل، مفردات الحالة، مفاتيح JSON، وصيغة معرف النقل.

النظير على جهة الهاتف: app/src/main/java/com/wameed/WameedProtocol.kt — أي تغيير
هنا يجب أن ينعكس هناك بنفس القيم الحرفية.

القواعد:
- لا يجوز كتابة منفذ أو نوع رسالة أو اسم حالة كنص حرفي خارج هذه الوحدة.
- المستقبِلون (كوتلن وبايثون) يتجاهلون المفاتيح غير المعروفة، لذا إضافة مفتاح
  لرسالة قائمة آمنة؛ إعادة تسمية مفتاح أو حذفه كسرٌ للتوافق.

مكتبة قياسية فقط — استيراد هذه الوحدة بلا أي أثر جانبي، فهي قابلة للاختبار
مباشرة (tests/test_protocol.py).
"""

from __future__ import annotations

import hashlib
import os

# ======================== المنافذ ========================

# الكمبيوتر يستمع هنا (WebSocket) — وجهة الإرسال من الهاتف.
PC_WS_PORT = 7788

# الهاتف يستمع هنا (WebSocket) — وجهة الإرسال من الكمبيوتر.
PHONE_WS_PORT = 7789

# منفذ اكتشاف UDP — نفس رقم PHONE_WS_PORT عمداً (UDP وTCP لا يتعارضان).
DISCOVERY_UDP_PORT = 7789

# ======================== الإصدار والتقطيع ========================

# يُرسل في hello وfile_meta وكل إطارات الحالة. الطرفان لا يتفاوضان عليه
# حالياً — لكنه موجود على السلك ليوم يحتاجانه.
PROTOCOL_VERSION = 2

# حجم قطعة الملف الواحدة على السلك.
TRANSFER_CHUNK_SIZE = 512 * 1024

# ======================== هويات الخدمة (اكتشاف UDP) ========================

SERVICE_PC = "wameed_pc"
SERVICE_PHONE = "wameed_phone"

# معرف الجهاز الذي يقدمه الكمبيوتر في مصافحته مع الهاتف.
PC_DEVICE_ID = "pc_client"

# ======================== أنواع الرسائل (مفتاح "type") ========================

TYPE_HELLO = "hello"
TYPE_PING = "ping"
TYPE_PONG = "pong"
TYPE_TEXT = "text"
TYPE_URL = "url"
TYPE_FILE_META = "file_meta"
TYPE_DISCOVERY_PING = "discovery_ping"
TYPE_DISCOVERY_PONG = "discovery_pong"

# ======================== مفردات الحالة (مفتاح "status") ========================

STATUS_PAIRING_REQUIRED = "pairing_required"
STATUS_PAIRED = "paired"
STATUS_HELLO = "hello"  # بعض الإصدارات ترد على hello بحالة "hello" بدل "paired"
STATUS_REJECTED = "rejected"
STATUS_READY = "ready"
STATUS_PROGRESS = "progress"
STATUS_SAVING = "saving"
STATUS_SAVED = "saved"
STATUS_PONG = "pong"
STATUS_ERROR = "error"

# حالات تعني أن الاقتران نجح.
PAIRED_STATUSES = {STATUS_PAIRED, STATUS_HELLO}


# ======================== العناوين ========================

def ws_url(ip: str, port: int) -> str:
    return f"ws://{ip}:{port}"


# ======================== بناء الرسائل الصادرة ========================

def build_hello(device: str, app_version: str) -> dict:
    """تحية المصافحة الموحدة للكمبيوتر (كانت ثلاث نسخ متباينة في receiver.py).

    كل الحقول تُرسل دائماً — المستقبِل يقرأ ما يعرفه ويتجاهل الباقي،
    فالتوحيد إضافي فقط ولا يكسر أي إصدار قديم من تطبيق الهاتف.
    """
    return {
        "type": TYPE_HELLO,
        "device": device,
        "name": device,
        "device_id": PC_DEVICE_ID,
        "app_version": app_version,
        "protocol_version": PROTOCOL_VERSION,
    }


def build_pong() -> dict:
    return {"type": TYPE_PONG}


def build_pairing_response(status: str, message: str | None = None) -> dict:
    payload = {"status": status}
    if message:
        payload["message"] = message
    return payload


def build_text(text: str) -> dict:
    return {"type": TYPE_TEXT, "text": text}


def build_file_meta(transfer_id: str, filename: str, size: int, chunks: int) -> dict:
    """البيانات الوصفية لملف مرسَل من الكمبيوتر إلى الهاتف."""
    return {
        "type": TYPE_FILE_META,
        "protocol_version": PROTOCOL_VERSION,
        "transfer_id": transfer_id,
        "direction": "windows_to_android",
        "filename": filename,
        "size": size,
        "chunks": chunks,
        "chunk_size": TRANSFER_CHUNK_SIZE,
    }


def build_transfer_status(status: str, **fields) -> dict:
    """إطار حالة يبثه المستقبِل أثناء نقل وارد.

    يضيف protocol_version دائماً، وينسخ received_bytes إلى المفتاح القديم
    received للتوافق مع الإصدارات الأقدم من مرسل الهاتف.
    """
    payload = {"status": status, "protocol_version": PROTOCOL_VERSION}
    payload.update(fields)
    if "received_bytes" in payload and "received" not in payload:
        payload["received"] = payload["received_bytes"]
    return payload


def transfer_id_for_file(path: str, size: int) -> str:
    """معرّف نقل ثابت لملف: نفس الملف = نفس المعرف = يمكن الاستئناف."""
    raw = f"{os.path.basename(path).lower()}:{int(size)}".encode("utf-8", errors="ignore")
    return "w2a-" + hashlib.sha256(raw).hexdigest()[:20]


# ======================== اكتشاف UDP ========================

def build_discovery_ping(device: str) -> dict:
    """حزمة بحث يبثها الكمبيوتر ليرد عليها الهاتف."""
    return {
        "type": TYPE_DISCOVERY_PING,
        "service": SERVICE_PC,
        "device": device,
        "port": PC_WS_PORT,
    }


def build_pc_announcement(name: str, ip: str, version: str, connection_state: str) -> dict:
    """إعلان/رد الكمبيوتر على حزم البحث (يتطابق مع ما يقرؤه DeviceDiscovery.kt)."""
    return {
        "service": SERVICE_PC,
        "name": name,
        "ip": ip,
        "port": PC_WS_PORT,
        "version": version,
        "ws_ready": True,
        "connection_state": connection_state,
    }


def is_pong_from_phone(message: dict) -> bool:
    """هل هذا رد اكتشاف صادر من هاتف وميض؟"""
    return (
        message.get("type") == TYPE_DISCOVERY_PONG
        and message.get("service") == SERVICE_PHONE
    )


def is_discovery_request_from_phone(message: dict) -> bool:
    """هل هذه حزمة بحث يجب أن يرد عليها الكمبيوتر؟

    نقبل service=wameed_phone أو type=discovery_ping — إصدارات قديمة من
    التطبيق أرسلت أحدهما فقط.
    """
    return (
        message.get("service") == SERVICE_PHONE
        or message.get("type") == TYPE_DISCOVERY_PING
    )
