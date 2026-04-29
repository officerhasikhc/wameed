package com.wameed

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * يحفظ ويقرأ إعدادات وميض (IP الكمبيوتر وغيره)
 */
object WameedPrefs {

    private const val PREFS_NAME = "wameed_prefs"
    private const val KEY_PC_IP = "pc_ip"
    private const val KEY_PC_PORT = "pc_port"
    private const val KEY_CONNECTED = "last_connected"
    private const val KEY_DISPLAY_MODE = "display_mode"
    private const val KEY_HISTORY = "send_history"
    private const val KEY_KEEP_ALIVE = "keep_alive_enabled"
    private const val KEY_LAST_SEND_AT = "last_send_at"
    private const val KEY_LAST_SEND_TARGET = "last_send_target"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_PC_NAME = "pc_name"
    private const val KEY_TRUSTED_DEVICES = "trusted_devices"
    private const val KEY_LANGUAGE = "app_language"

    /** Friendly computer name reported by the PC (via UDP broadcast `name`
     *  field or the `hello` WS response). Persisted so we can show
     *  "متصل بـ <اسم الكمبيوتر>" even after app restart. */
    fun getPcName(context: Context): String =
        prefs(context).getString(KEY_PC_NAME, "") ?: ""

    fun setPcName(context: Context, name: String) {
        if (name.isNotBlank()) {
            prefs(context).edit().putString(KEY_PC_NAME, name.trim()).apply()
        }
    }

    /**
     * Set of trusted remote device IDs (typically PCs).
     */
    fun isDeviceTrusted(context: Context, deviceId: String?): Boolean {
        if (deviceId.isNullOrBlank()) return false
        val trusted = prefs(context).getStringSet(KEY_TRUSTED_DEVICES, emptySet()) ?: emptySet()
        return trusted.contains(deviceId)
    }

    fun getTrustedDevices(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_TRUSTED_DEVICES, emptySet()) ?: emptySet()
    }

    fun addTrustedDevice(context: Context, deviceId: String) {
        if (deviceId.isBlank()) return
        val p = prefs(context)
        val trusted = p.getStringSet(KEY_TRUSTED_DEVICES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (trusted.add(deviceId)) {
            p.edit().putStringSet(KEY_TRUSTED_DEVICES, trusted).apply()
        }
    }

    fun removeTrustedDevice(context: Context, deviceId: String) {
        val p = prefs(context)
        val trusted = p.getStringSet(KEY_TRUSTED_DEVICES, emptySet())?.toMutableSet() ?: return
        if (trusted.remove(deviceId)) {
            p.edit().putStringSet(KEY_TRUSTED_DEVICES, trusted).apply()
        }
    }

    /**
     * Stable pseudonymous identifier for THIS phone install. Sent to the PC
     * as part of the `hello` handshake so the PC can remember a one-time
     * "allow" decision keyed by device_id (no personal data leaks).
     *
     * Generated lazily on first use and persisted in SharedPreferences.
     * Uninstalling the app clears the prefs → a new id is created next time
     * (so the user will be asked to pair again — that's the desired behaviour).
     */
    fun getOrCreateDeviceId(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_DEVICE_ID, "") ?: ""
        if (existing.isNotEmpty()) return existing
        val id = java.util.UUID.randomUUID().toString()
        p.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    /** Friendly device name sent to the PC via "hello" handshake. */
    fun getDeviceName(): String {
        // Prefer the user-facing model (e.g. "Galaxy S22") over raw android.os.Build.MODEL.
        val manufacturer = android.os.Build.MANUFACTURER ?: ""
        val model = android.os.Build.MODEL ?: "Android"
        return if (model.startsWith(manufacturer, ignoreCase = true) || manufacturer.isBlank()) {
            model
        } else {
            "$manufacturer $model".trim()
        }
    }

    /** Whether the keep-alive foreground service should start on background. */
    fun isKeepAliveEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_KEEP_ALIVE, true)
    }

    fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
    }

    /** App language: "ar" (default) or "en". */
    fun getLanguage(context: Context): String {
        return prefs(context).getString(KEY_LANGUAGE, "ar") ?: "ar"
    }

    fun setLanguage(context: Context, lang: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPcIp(context: Context): String {
        return prefs(context).getString(KEY_PC_IP, "") ?: ""
    }

    fun getPcPort(context: Context): Int {
        return prefs(context).getInt(KEY_PC_PORT, 7788)
    }

    fun getWsUrl(context: Context): String {
        val ip = getPcIp(context)
        val port = getPcPort(context)
        return "ws://$ip:$port"
    }

    fun savePcAddress(context: Context, rawInput: String) {
        var input = rawInput.trim()
        // إزالة البروتوكول إن وجد
        input = input.removePrefix("ws://").removePrefix("http://").removePrefix("https://")
        // إزالة المسار إن وجد
        val slashIndex = input.indexOf('/')
        if (slashIndex != -1) input = input.substring(0, slashIndex)

        // فصل الـ IP عن المنفذ
        val parts = input.split(":")
        val ip = parts[0]
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 7788 else 7788

        prefs(context).edit()
            .putString(KEY_PC_IP, ip)
            .putInt(KEY_PC_PORT, port)
            .apply()
    }

    fun getDisplayAddress(context: Context): String {
        val ip = getPcIp(context)
        val port = getPcPort(context)
        return if (ip.isNotEmpty()) "$ip:$port" else ""
    }

    fun isConfigured(context: Context): Boolean {
        return getPcIp(context).isNotEmpty()
    }

    fun setLastConnected(context: Context, time: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_CONNECTED, time).apply()
    }

    fun getLastConnected(context: Context): Long {
        return prefs(context).getLong(KEY_CONNECTED, 0L)
    }

    /**
     * يُستدعى من WameedSender بعد كل إرسال ناجح (ملف/نص/رابط/ping).
     * يُمكّن MainActivity من معرفة أن الاتصال يعمل حتى لو لم يُفعّل الفحص بنفسه.
     */
    fun setLastSendSuccess(context: Context, ip: String, port: Int,
                           time: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_LAST_SEND_AT, time)
            .putString(KEY_LAST_SEND_TARGET, "$ip:$port")
            .apply()
    }

    /** Returns (timestamp_ms, "ip:port") of the last successful send, or null. */
    fun getLastSendInfo(context: Context): Pair<Long, String>? {
        val t = prefs(context).getLong(KEY_LAST_SEND_AT, 0L)
        if (t == 0L) return null
        val target = prefs(context).getString(KEY_LAST_SEND_TARGET, "") ?: ""
        return t to target
    }

    fun formatLastConnected(context: Context): String {
        val t = getLastConnected(context)
        if (t == 0L) return ""
        val delta = (System.currentTimeMillis() - t) / 1000
        return when {
            delta < 60 -> context.getString(R.string.time_just_now)
            delta < 3600 -> context.getString(R.string.time_minutes_ago, delta / 60)
            delta < 86400 -> context.getString(R.string.time_hours_ago, delta / 3600)
            else -> context.getString(R.string.time_days_ago, delta / 86400)
        }
    }

    // ===== Display Mode =====
    // "open" = فتح الملف فقط (الافتراضي)
    // "path" = فتح المجلد فقط
    // "both" = فتح الملف والمجلد معاً
    // "none" = لا شيء (إشعار فقط)
    fun getDisplayMode(context: Context): String {
        return prefs(context).getString(KEY_DISPLAY_MODE, "open") ?: "open"
    }

    fun setDisplayMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_DISPLAY_MODE, mode).apply()
    }

    // ===== Send History =====
    data class HistoryEntry(
        val filename: String,
        val type: String,
        val size: Long,
        val status: String,
        val time: String,
        val direction: String = "sent"  // "sent" | "received"
    )

    fun addHistoryEntry(context: Context, filename: String, type: String, size: Long, status: String, direction: String = "sent") {
        val arr = getHistoryArray(context)
        val entry = JSONObject().apply {
            put("filename", filename)
            put("type", type)
            put("size", size)
            put("status", status)
            put("direction", direction)
            put("time", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date()))
        }
        // Insert at beginning
        val newArr = JSONArray()
        newArr.put(entry)
        for (i in 0 until minOf(arr.length(), 199)) {
            newArr.put(arr.get(i))
        }
        prefs(context).edit().putString(KEY_HISTORY, newArr.toString()).apply()
    }

    fun getHistory(context: Context): List<HistoryEntry> {
        val arr = getHistoryArray(context)
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(HistoryEntry(
                filename = obj.optString("filename", "?"),
                type = obj.optString("type", ""),
                size = obj.optLong("size", 0),
                status = obj.optString("status", ""),
                time = obj.optString("time", ""),
                direction = obj.optString("direction", "sent")
            ))
        }
        return list
    }

    fun clearHistory(context: Context) {
        prefs(context).edit().putString(KEY_HISTORY, "[]").apply()
    }

    private fun getHistoryArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        return try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }
}
