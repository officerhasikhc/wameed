"""اختبارات بروتوكول وميض السلكي.

أي كسر هنا يعني أن ما يخرج على الشبكة تغيّر — تحقق من النظير
app/src/main/java/com/wameed/WameedProtocol.kt قبل تعديل القيم المتوقعة.
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

import protocol  # noqa: E402


class HelloTests(unittest.TestCase):
    def test_hello_carries_full_identity(self):
        hello = protocol.build_hello("DESKTOP-X", "2.0.9")
        self.assertEqual(hello["type"], protocol.TYPE_HELLO)
        self.assertEqual(hello["device"], "DESKTOP-X")
        self.assertEqual(hello["name"], "DESKTOP-X")
        self.assertEqual(hello["device_id"], protocol.PC_DEVICE_ID)
        self.assertEqual(hello["app_version"], "2.0.9")
        self.assertEqual(hello["protocol_version"], protocol.PROTOCOL_VERSION)


class TransferStatusTests(unittest.TestCase):
    def test_adds_protocol_version_and_legacy_received_alias(self):
        frame = protocol.build_transfer_status(
            protocol.STATUS_PROGRESS, transfer_id="w2a-1", received_bytes=42, chunk_index=3
        )
        self.assertEqual(frame["protocol_version"], protocol.PROTOCOL_VERSION)
        self.assertEqual(frame["received_bytes"], 42)
        self.assertEqual(frame["received"], 42)
        self.assertEqual(frame["transfer_id"], "w2a-1")

    def test_does_not_override_explicit_received(self):
        frame = protocol.build_transfer_status(
            protocol.STATUS_READY, received_bytes=10, received=7
        )
        self.assertEqual(frame["received"], 7)

    def test_no_received_alias_without_received_bytes(self):
        frame = protocol.build_transfer_status(protocol.STATUS_ERROR, reason="disk_full")
        self.assertNotIn("received", frame)
        self.assertEqual(frame["reason"], "disk_full")


class TransferIdTests(unittest.TestCase):
    def test_stable_and_prefixed(self):
        a = protocol.transfer_id_for_file(r"C:\Users\x\Photo.JPG", 100)
        b = protocol.transfer_id_for_file(r"D:\other\photo.jpg", 100)
        self.assertEqual(a, b)  # نفس الاسم والحجم = نفس المعرف (يمكّن الاستئناف)
        self.assertTrue(a.startswith("w2a-"))
        self.assertEqual(len(a), len("w2a-") + 20)
        self.assertNotEqual(a, protocol.transfer_id_for_file("photo.jpg", 101))


class FileMetaTests(unittest.TestCase):
    def test_file_meta_key_set(self):
        meta = protocol.build_file_meta("w2a-abc", "فيديو.mp4", 1000, 2)
        self.assertEqual(meta["type"], protocol.TYPE_FILE_META)
        self.assertEqual(meta["direction"], "windows_to_android")
        self.assertEqual(meta["chunk_size"], protocol.TRANSFER_CHUNK_SIZE)
        self.assertEqual(meta["protocol_version"], protocol.PROTOCOL_VERSION)
        self.assertEqual(meta["filename"], "فيديو.mp4")
        self.assertEqual(meta["size"], 1000)
        self.assertEqual(meta["chunks"], 2)


class DiscoveryTests(unittest.TestCase):
    def test_ping_identifies_pc(self):
        ping = protocol.build_discovery_ping("DESKTOP-X")
        self.assertEqual(ping["type"], protocol.TYPE_DISCOVERY_PING)
        self.assertEqual(ping["service"], protocol.SERVICE_PC)
        self.assertEqual(ping["port"], protocol.PC_WS_PORT)

    def test_announcement_matches_android_reader(self):
        # DeviceDiscovery.kt يطابق على service ويقرأ name/ip/port — هذه المفاتيح عقد.
        ann = protocol.build_pc_announcement("DESKTOP-X", "192.168.1.5", "2.0.9", "connected")
        self.assertEqual(ann["service"], protocol.SERVICE_PC)
        for key in ("name", "ip", "port", "version", "ws_ready", "connection_state"):
            self.assertIn(key, ann)
        self.assertEqual(ann["port"], protocol.PC_WS_PORT)

    def test_pong_recognition(self):
        self.assertTrue(protocol.is_pong_from_phone(
            {"type": "discovery_pong", "service": "wameed_phone"}))
        self.assertFalse(protocol.is_pong_from_phone(
            {"type": "discovery_pong", "service": "wameed_pc"}))
        self.assertFalse(protocol.is_pong_from_phone({}))

    def test_request_recognition_accepts_legacy_forms(self):
        self.assertTrue(protocol.is_discovery_request_from_phone({"service": "wameed_phone"}))
        self.assertTrue(protocol.is_discovery_request_from_phone({"type": "discovery_ping"}))
        self.assertFalse(protocol.is_discovery_request_from_phone({"service": "other"}))


class PortContractTests(unittest.TestCase):
    def test_port_values_are_the_wire_contract(self):
        # هذه الأرقام عقد مع WameedProtocol.kt — تغييرها يكسر التوافق مع الهاتف.
        self.assertEqual(protocol.PC_WS_PORT, 7788)
        self.assertEqual(protocol.PHONE_WS_PORT, 7789)
        self.assertEqual(protocol.DISCOVERY_UDP_PORT, 7789)
        self.assertEqual(protocol.PROTOCOL_VERSION, 2)
        self.assertEqual(protocol.TRANSFER_CHUNK_SIZE, 512 * 1024)


if __name__ == "__main__":
    unittest.main()
