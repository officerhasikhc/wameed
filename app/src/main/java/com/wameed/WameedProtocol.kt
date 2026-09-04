package com.wameed

import org.json.JSONObject

/**
 * بروتوكول وميض السلكي — المالك الوحيد لكل ما يعبر الشبكة بين الهاتف والكمبيوتر:
 * أرقام المنافذ، أنواع الرسائل، مفردات الحالة، مفاتيح JSON، وصيغة معرف النقل.
 *
 * النظير على جهة الكمبيوتر: windows-receiver/src/protocol.py — أي تغيير هنا
 * يجب أن ينعكس هناك بنفس القيم الحرفية.
 *
 * القواعد:
 *  - لا يجوز كتابة منفذ أو نوع رسالة أو اسم حالة كنص حرفي خارج هذا الملف.
 *  - المستقبِلون (كوتلن وبايثون) يتجاهلون المفاتيح غير المعروفة، لذا إضافة
 *    مفتاح جديد لرسالة قائمة آمنة؛ إعادة تسمية مفتاح أو حذفه كسرٌ للتوافق.
 */
object WameedProtocol {

    // ======================== المنافذ ========================

    /** الكمبيوتر يستمع هنا (WebSocket) — وجهة الإرسال من الهاتف. */
    const val PC_WS_PORT = 7788

    /** الهاتف يستمع هنا (WebSocket) — وجهة الإرسال من الكمبيوتر. */
    const val PHONE_WS_PORT = 7789

    /** منفذ اكتشاف UDP — نفس رقم [PHONE_WS_PORT] عمداً (UDP وTCP لا يتعارضان). */
    const val DISCOVERY_UDP_PORT = 7789

    // ======================== الإصدار والتقطيع ========================

    /** يُرسل في hello وfile_meta وكل إطارات الحالة. الطرفان لا يتفاوضان عليه
     *  حالياً — لكنه موجود على السلك ليوم يحتاجانه. */
    const val PROTOCOL_VERSION = 2

    /** حجم قطعة الملف الواحدة على السلك. */
    const val TRANSFER_CHUNK_SIZE = 512 * 1024

    // ======================== هويات الخدمة (اكتشاف UDP وmDNS) ========================

    const val SERVICE_PC = "wameed_pc"
    const val SERVICE_PHONE = "wameed_phone"
    const val NSD_SERVICE_TYPE = "_wameed._tcp"

    // ======================== أنواع الرسائل (مفتاح "type") ========================

    const val TYPE_HELLO = "hello"
    const val TYPE_PING = "ping"
    const val TYPE_TEXT = "text"
    const val TYPE_URL = "url"
    const val TYPE_FILE_META = "file_meta"
    const val TYPE_DISCOVERY_PING = "discovery_ping"
    const val TYPE_DISCOVERY_PONG = "discovery_pong"

    // ======================== مفردات الحالة (مفتاح "status") ========================

    const val STATUS_PAIRING_REQUIRED = "pairing_required"
    const val STATUS_PAIRED = "paired"
    /** بعض إصدارات الكمبيوتر ترد على hello بحالة "hello" بدل "paired". */
    const val STATUS_HELLO = "hello"
    const val STATUS_REJECTED = "rejected"
    const val STATUS_READY = "ready"
    const val STATUS_PROGRESS = "progress"
    const val STATUS_SAVING = "saving"
    const val STATUS_SAVED = "saved"
    const val STATUS_PONG = "pong"
    const val STATUS_ERROR = "error"

    /** حالات تعني أن الاقتران نجح (الكمبيوتر جاهز لاستقبالنا). */
    val PAIRED_STATUSES = setOf(STATUS_PAIRED, STATUS_HELLO)

    // ======================== العناوين ========================

    fun wsUrl(ip: String, port: Int): String = "ws://$ip:$port"

    // ======================== بناء الرسائل الصادرة ========================

    /**
     * تحية المصافحة الموحدة. كل الحقول تُرسل دائماً — المستقبِل يقرأ ما يعرفه
     * ويتجاهل الباقي (كان في الكود سبع نسخ متباينة من هذه الرسالة؛ التوحيد
     * إضافي فقط، فلا يكسر أي إصدار قديم).
     *
     * @param verifyOnly فحص جاهزية فقط — الكمبيوتر لا يعُدّها جلسة إرسال.
     */
    fun hello(
        device: String,
        deviceId: String,
        appVersion: String,
        verifyOnly: Boolean = false,
    ): JSONObject = JSONObject().apply {
        put("type", TYPE_HELLO)
        put("device", device)
        put("name", device) // بعض إصدارات الكمبيوتر تقرأ name بدل device
        put("device_id", deviceId)
        put("app_version", appVersion)
        put("protocol_version", PROTOCOL_VERSION)
        put("receiver_port", PHONE_WS_PORT)
        if (verifyOnly) put("verify_only", true)
    }

    /** الصيغة المختصرة المعتادة: هوية هذا الهاتف من [WameedPrefs] وBuildConfig. */
    fun hello(context: android.content.Context, verifyOnly: Boolean = false): JSONObject =
        hello(
            device = WameedPrefs.getDeviceName(),
            deviceId = WameedPrefs.getOrCreateDeviceId(context),
            appVersion = BuildConfig.VERSION_NAME,
            verifyOnly = verifyOnly,
        )

    fun ping(): JSONObject = JSONObject().put("type", TYPE_PING)

    /** نص أو رابط — يُصنّف تلقائياً حسب البادئة. */
    fun textOrUrlPayload(text: String): JSONObject {
        val isUrl = text.startsWith("http://") || text.startsWith("https://")
        return JSONObject().apply {
            put("type", if (isUrl) TYPE_URL else TYPE_TEXT)
            if (isUrl) put("url", text) else put("text", text)
        }
    }

    /** البيانات الوصفية لملف مرسَل من الهاتف إلى الكمبيوتر. */
    fun fileMeta(
        transferId: String,
        filename: String,
        mime: String,
        size: Long,
        chunks: Int,
        displayMode: String,
    ): JSONObject = JSONObject().apply {
        put("type", TYPE_FILE_META)
        put("protocol_version", PROTOCOL_VERSION)
        put("transfer_id", transferId)
        put("direction", "android_to_windows")
        put("filename", filename)
        put("mime", mime)
        put("size", size)
        put("chunks", chunks)
        put("chunk_size", TRANSFER_CHUNK_SIZE)
        put("display_mode", displayMode)
    }

    /**
     * إطار حالة يبثه سيرفر الاستقبال على الهاتف أثناء نقل وارد.
     * يُرسل received_bytes ومعه المفتاح القديم received للتوافق مع
     * الإصدارات الأقدم من مرسل الكمبيوتر.
     */
    fun transferStatus(
        status: String,
        transferId: String,
        receivedBytes: Long,
        chunkIndex: Int,
        totalChunks: Int,
    ): JSONObject = JSONObject().apply {
        put("status", status)
        put("protocol_version", PROTOCOL_VERSION)
        if (transferId.isNotBlank()) put("transfer_id", transferId)
        put("received_bytes", receivedBytes)
        put("received", receivedBytes)
        put("chunk_index", chunkIndex)
        if (totalChunks > 0) put("total_chunks", totalChunks)
    }

    // ======================== قراءة الرسائل الواردة ========================

    /** قراءة عدد البايتات المستلمة من إطار حالة، مع دعم المفتاح القديم received. */
    fun receivedBytes(resp: JSONObject, fallback: Long = -1L): Long =
        resp.optLong("received_bytes", resp.optLong("received", fallback))

    /** معرّف نقل ثابت لملف: نفس الملف = نفس المعرف = يمكن الاستئناف. */
    fun transferId(filename: String, fileSize: Long): String {
        val raw = "${filename.trim().lowercase()}:$fileSize"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "a2w-${digest.take(20)}"
    }

    // ======================== اكتشاف UDP ========================

    /** حزمة بحث يبثها الهاتف ليرد عليها الكمبيوتر. */
    fun discoveryPing(device: String): JSONObject = JSONObject().apply {
        put("type", TYPE_DISCOVERY_PING)
        put("service", SERVICE_PHONE)
        put("device", device)
    }

    /** رد الهاتف على حزمة بحث قادمة من الكمبيوتر. */
    fun discoveryPong(device: String, deviceId: String, appVersion: String): JSONObject =
        JSONObject().apply {
            put("type", TYPE_DISCOVERY_PONG)
            put("service", SERVICE_PHONE)
            put("device", device)
            put("device_id", deviceId)
            put("ip", "127.0.0.1") // الكمبيوتر يستخدم عنوان مصدر الحزمة؛ الحقل للتوافق فقط
            put("name", device)
            put("port", PHONE_WS_PORT)
            put("version", appVersion)
        }

    /** هل هذه الحزمة بحثٌ صادر من الكمبيوتر يجب الرد عليه؟ */
    fun isDiscoveryPingFromPc(json: JSONObject): Boolean =
        json.optString("type") == TYPE_DISCOVERY_PING &&
            json.optString("service") == SERVICE_PC

    /** إعلان كمبيوتر مكتشف عبر UDP. */
    data class PcAnnouncement(val name: String, val ip: String, val port: Int)

    /**
     * يفسر حزمة UDP واردة كإعلان كمبيوتر. يرجع null إذا لم تكن من خدمة وميض
     * على الكمبيوتر. عنوان الحزمة يُقدَّم كاحتياط عندما يكون الحقل المعلن غير صالح.
     */
    fun parsePcAnnouncement(data: String, senderIp: String, defaultName: String): PcAnnouncement? {
        val json = try { JSONObject(data) } catch (_: Exception) { return null }
        if (json.optString("service") != SERVICE_PC) return null
        val reported = json.optString("ip", "")
        return PcAnnouncement(
            name = json.optString("name", defaultName),
            ip = if (isUsableRemoteIp(reported)) reported else senderIp,
            port = json.optInt("port", PC_WS_PORT),
        )
    }

    /** يزيل بادئة IPv6-mapped-IPv4 (::ffff:) من عناوين الاتصالات الواردة. */
    fun normalizeRemoteIp(ip: String): String =
        if (ip.startsWith("::ffff:")) ip.removePrefix("::ffff:") else ip

    /** عنوان معلن صالح للاتصال به (ليس loopback أو فارغاً). */
    fun isUsableRemoteIp(ip: String): Boolean {
        if (ip.isBlank()) return false
        if (ip == "0.0.0.0" || ip == "::" || ip == "::1") return false
        if (ip.startsWith("127.")) return false
        if (ip.equals("localhost", ignoreCase = true)) return false
        return true
    }
}
