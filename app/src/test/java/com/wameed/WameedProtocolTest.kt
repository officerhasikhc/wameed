package com.wameed

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات بروتوكول وميض السلكي. أي كسر هنا يعني أن ما يخرج على الشبكة تغيّر —
 * تحقق من النظير windows-receiver/src/protocol.py قبل تعديل القيم المتوقعة.
 */
class WameedProtocolTest {

    @Test
    fun `hello carries full identity and version fields`() {
        val hello = WameedProtocol.hello("Pixel 9", "dev-123", "2.0.9")
        assertEquals(WameedProtocol.TYPE_HELLO, hello.getString("type"))
        assertEquals("Pixel 9", hello.getString("device"))
        assertEquals("Pixel 9", hello.getString("name"))
        assertEquals("dev-123", hello.getString("device_id"))
        assertEquals("2.0.9", hello.getString("app_version"))
        assertEquals(WameedProtocol.PROTOCOL_VERSION, hello.getInt("protocol_version"))
        assertEquals(WameedProtocol.PHONE_WS_PORT, hello.getInt("receiver_port"))
        assertFalse(hello.has("verify_only"))
    }

    @Test
    fun `verify-only hello marks itself`() {
        val hello = WameedProtocol.hello("Pixel", "id", "1.0", verifyOnly = true)
        assertTrue(hello.getBoolean("verify_only"))
    }

    @Test
    fun `text payload classifies urls by prefix`() {
        val url = WameedProtocol.textOrUrlPayload("https://example.com/a")
        assertEquals(WameedProtocol.TYPE_URL, url.getString("type"))
        assertEquals("https://example.com/a", url.getString("url"))
        assertFalse(url.has("text"))

        val text = WameedProtocol.textOrUrlPayload("مرحبا http://example.com")
        assertEquals(WameedProtocol.TYPE_TEXT, text.getString("type"))
        assertEquals("مرحبا http://example.com", text.getString("text"))
        assertFalse(text.has("url"))
    }

    @Test
    fun `file meta writes the agreed key set`() {
        val meta = WameedProtocol.fileMeta(
            transferId = "a2w-abc",
            filename = "صورة.jpg",
            mime = "image/jpeg",
            size = 1_500_000L,
            chunks = 3,
            displayMode = "open",
        )
        assertEquals(WameedProtocol.TYPE_FILE_META, meta.getString("type"))
        assertEquals("android_to_windows", meta.getString("direction"))
        assertEquals("a2w-abc", meta.getString("transfer_id"))
        assertEquals("صورة.jpg", meta.getString("filename"))
        assertEquals("image/jpeg", meta.getString("mime"))
        assertEquals(1_500_000L, meta.getLong("size"))
        assertEquals(3, meta.getInt("chunks"))
        assertEquals(WameedProtocol.TRANSFER_CHUNK_SIZE, meta.getInt("chunk_size"))
        assertEquals("open", meta.getString("display_mode"))
        assertEquals(WameedProtocol.PROTOCOL_VERSION, meta.getInt("protocol_version"))
    }

    @Test
    fun `transfer status keeps the legacy received alias`() {
        val frame = WameedProtocol.transferStatus(
            status = WameedProtocol.STATUS_PROGRESS,
            transferId = "w2a-1",
            receivedBytes = 42L,
            chunkIndex = 7,
            totalChunks = 10,
        )
        assertEquals(42L, frame.getLong("received_bytes"))
        assertEquals(42L, frame.getLong("received"))
        assertEquals(7, frame.getInt("chunk_index"))
        assertEquals(10, frame.getInt("total_chunks"))
        assertEquals("w2a-1", frame.getString("transfer_id"))
    }

    @Test
    fun `transfer status omits blank transfer id and zero total chunks`() {
        val frame = WameedProtocol.transferStatus(
            status = WameedProtocol.STATUS_SAVING,
            transferId = "",
            receivedBytes = 0L,
            chunkIndex = 0,
            totalChunks = 0,
        )
        assertFalse(frame.has("transfer_id"))
        assertFalse(frame.has("total_chunks"))
    }

    @Test
    fun `receivedBytes prefers new key and falls back to legacy then default`() {
        assertEquals(9L, WameedProtocol.receivedBytes(JSONObject().put("received_bytes", 9L).put("received", 5L)))
        assertEquals(5L, WameedProtocol.receivedBytes(JSONObject().put("received", 5L)))
        assertEquals(-1L, WameedProtocol.receivedBytes(JSONObject()))
        assertEquals(77L, WameedProtocol.receivedBytes(JSONObject(), fallback = 77L))
    }

    @Test
    fun `transfer id is stable and normalized`() {
        val a = WameedProtocol.transferId("Photo.JPG", 100L)
        val b = WameedProtocol.transferId("  photo.jpg ", 100L)
        assertEquals(a, b)
        assertTrue(a.startsWith("a2w-"))
        assertEquals("a2w-".length + 20, a.length)
        assertFalse(a == WameedProtocol.transferId("photo.jpg", 101L))
    }

    @Test
    fun `pc announcement parses and falls back to packet ip`() {
        val fromField = WameedProtocol.parsePcAnnouncement(
            """{"service":"wameed_pc","name":"DESKTOP","ip":"192.168.1.20","port":7788}""",
            senderIp = "192.168.1.99", defaultName = "كمبيوتر",
        )
        assertEquals("192.168.1.20", fromField?.ip)
        assertEquals("DESKTOP", fromField?.name)
        assertEquals(WameedProtocol.PC_WS_PORT, fromField?.port)

        val loopback = WameedProtocol.parsePcAnnouncement(
            """{"service":"wameed_pc","ip":"127.0.0.1"}""",
            senderIp = "192.168.1.99", defaultName = "كمبيوتر",
        )
        assertEquals("192.168.1.99", loopback?.ip)
        assertEquals("كمبيوتر", loopback?.name)
    }

    @Test
    fun `foreign packets are not pc announcements`() {
        assertNull(WameedProtocol.parsePcAnnouncement("""{"service":"other"}""", "1.2.3.4", "pc"))
        assertNull(WameedProtocol.parsePcAnnouncement("not json", "1.2.3.4", "pc"))
    }

    @Test
    fun `discovery ping recognition requires both type and service`() {
        assertTrue(
            WameedProtocol.isDiscoveryPingFromPc(
                JSONObject().put("type", "discovery_ping").put("service", "wameed_pc")
            )
        )
        assertFalse(
            WameedProtocol.isDiscoveryPingFromPc(
                JSONObject().put("type", "discovery_ping").put("service", "wameed_phone")
            )
        )
    }

    @Test
    fun `remote ip normalization strips ipv6 mapping only`() {
        assertEquals("192.168.1.5", WameedProtocol.normalizeRemoteIp("::ffff:192.168.1.5"))
        assertEquals("192.168.1.5", WameedProtocol.normalizeRemoteIp("192.168.1.5"))
        assertEquals("fe80::1", WameedProtocol.normalizeRemoteIp("fe80::1"))
    }

    @Test
    fun `usable remote ip rejects loopback and empties`() {
        assertTrue(WameedProtocol.isUsableRemoteIp("192.168.1.7"))
        assertFalse(WameedProtocol.isUsableRemoteIp(""))
        assertFalse(WameedProtocol.isUsableRemoteIp("0.0.0.0"))
        assertFalse(WameedProtocol.isUsableRemoteIp("127.0.0.1"))
        assertFalse(WameedProtocol.isUsableRemoteIp("::1"))
        assertFalse(WameedProtocol.isUsableRemoteIp("localhost"))
    }
}
