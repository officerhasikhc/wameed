from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Any


def parse_dropped_paths(drop_data: str) -> list[str]:
    """Parse TkinterDnD payloads containing braced and plain file paths."""
    if not drop_data or not drop_data.strip():
        return []
    matches = re.findall(r"\{([^}]*)\}|(\S+)", drop_data)
    return [braced or plain for braced, plain in matches if (braced or plain)]


@dataclass
class TransferAckState:
    total_bytes: int
    received_bytes: int = 0
    chunk_index: int = 0
    total_chunks: int = 0
    status: str = "preparing"
    message: str = ""

    def apply_message(self, message: dict[str, Any]) -> None:
        status = str(message.get("status") or self.status or "preparing")
        self.status = "failed" if status == "error" else status

        received = _coerce_int(
            message.get("received_bytes", message.get("received", self.received_bytes)),
            self.received_bytes,
        )
        self.received_bytes = max(self.received_bytes, received)

        chunk_index = _coerce_int(message.get("chunk_index", self.chunk_index), self.chunk_index)
        self.chunk_index = max(self.chunk_index, chunk_index)

        total_chunks = _coerce_int(message.get("total_chunks", self.total_chunks), self.total_chunks)
        self.total_chunks = max(self.total_chunks, total_chunks)

        if "message" in message:
            self.message = str(message.get("message") or "")

    @property
    def progress_percent(self) -> float:
        if self.total_bytes <= 0:
            return 100.0 if self.status == "saved" else 0.0
        return max(0.0, min(100.0, (self.received_bytes / self.total_bytes) * 100.0))


@dataclass(frozen=True)
class SendBackpressure:
    queue_soft_limit: int
    peer_in_flight_limit: int

    def can_send(
        self,
        *,
        queue_size: int,
        chunk_size: int,
        sent_bytes: int,
        peer_received_bytes: int,
    ) -> bool:
        peer_lag = max(0, sent_bytes - peer_received_bytes)
        return (
            queue_size + chunk_size < self.queue_soft_limit
            and peer_lag < self.peer_in_flight_limit
        )


def _coerce_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default
