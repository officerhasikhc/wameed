import os
import sys
import unittest


SRC_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "src"))
if SRC_DIR not in sys.path:
    sys.path.insert(0, SRC_DIR)


from transfer_utils import (  # noqa: E402
    SendBackpressure,
    TransferAckState,
    parse_dropped_paths,
)


class DroppedPathParsingTests(unittest.TestCase):
    def test_parses_braced_and_plain_windows_paths(self):
        data = r"{C:\Users\super\Videos\big movie.mp4} C:\Temp\clip.txt {D:\Arabic Files\ملف طويل.pdf}"

        paths = parse_dropped_paths(data)

        self.assertEqual(
            paths,
            [
                r"C:\Users\super\Videos\big movie.mp4",
                r"C:\Temp\clip.txt",
                r"D:\Arabic Files\ملف طويل.pdf",
            ],
        )

    def test_ignores_blank_drop_payload(self):
        self.assertEqual(parse_dropped_paths("   "), [])


class TransferAckStateTests(unittest.TestCase):
    def test_updates_received_bytes_monotonically_from_ack_messages(self):
        state = TransferAckState(total_bytes=1_000)

        state.apply_message({"status": "progress", "received_bytes": 400, "chunk_index": 2})
        state.apply_message({"status": "progress", "received_bytes": 200, "chunk_index": 1})

        self.assertEqual(state.status, "progress")
        self.assertEqual(state.received_bytes, 400)
        self.assertEqual(state.chunk_index, 2)
        self.assertEqual(state.progress_percent, 40.0)

    def test_captures_error_message_for_failed_transfer(self):
        state = TransferAckState(total_bytes=1_000)

        state.apply_message({"status": "error", "message": "disk full"})

        self.assertEqual(state.status, "failed")
        self.assertEqual(state.message, "disk full")


class SendBackpressureTests(unittest.TestCase):
    def test_blocks_when_websocket_queue_or_peer_lag_is_too_large(self):
        pressure = SendBackpressure(queue_soft_limit=8_000, peer_in_flight_limit=8_000)

        self.assertFalse(
            pressure.can_send(queue_size=7_800, chunk_size=512, sent_bytes=2_000, peer_received_bytes=2_000)
        )
        self.assertFalse(
            pressure.can_send(queue_size=0, chunk_size=512, sent_bytes=20_000, peer_received_bytes=4_000)
        )
        self.assertTrue(
            pressure.can_send(queue_size=1_000, chunk_size=512, sent_bytes=6_000, peer_received_bytes=4_000)
        )


if __name__ == "__main__":
    unittest.main()
