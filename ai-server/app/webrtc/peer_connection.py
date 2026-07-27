"""PeerConnection abstraction for WebRTC SDP/ICE handling.

W03 only establishes the connection (Offer/Answer/ICE, T11) — Audio Track and
DataChannel wiring is W04 scope (see jira/plan/DIARY-2-W03.md §0). The real
aiortc-backed implementation is added alongside T11; this module also holds
``FakePeerConnection`` so signaling/connection.py's state machine can be unit
tested without a real PeerConnection.
"""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from enum import StrEnum
from typing import Protocol


class PeerConnectionState(StrEnum):
    NEW = "new"
    CONNECTED = "connected"
    FAILED = "failed"
    CLOSED = "closed"


StateChangeCallback = Callable[[PeerConnectionState], Awaitable[None]]


class PeerConnection(Protocol):
    async def accept_offer(self, sdp: str) -> str:
        """Apply a remote SDP offer and return this side's SDP answer."""
        ...

    async def add_ice_candidate(
        self, candidate: str, sdp_mid: str | None, sdp_mline_index: int | None
    ) -> None: ...

    def on_state_change(self, callback: StateChangeCallback) -> None:
        """Register the (single) callback invoked on every connection state change."""
        ...

    async def close(self) -> None: ...


class PeerConnectionFactory(Protocol):
    def create(self) -> PeerConnection:
        """Create a new PeerConnection for one Signaling connection."""
        ...


class FakePeerConnection:
    """Test double: scripted answer SDP, recorded calls, and manual state triggers."""

    def __init__(self, answer_sdp: str = "v=0...fake-answer") -> None:
        self.answer_sdp = answer_sdp
        self.accepted_offer_sdp: str | None = None
        self.added_ice_candidates: list[tuple[str, str | None, int | None]] = []
        self.closed = False
        self._state_change_callback: StateChangeCallback | None = None

    async def accept_offer(self, sdp: str) -> str:
        self.accepted_offer_sdp = sdp
        return self.answer_sdp

    async def add_ice_candidate(
        self, candidate: str, sdp_mid: str | None, sdp_mline_index: int | None
    ) -> None:
        self.added_ice_candidates.append((candidate, sdp_mid, sdp_mline_index))

    def on_state_change(self, callback: StateChangeCallback) -> None:
        self._state_change_callback = callback

    async def close(self) -> None:
        self.closed = True

    async def simulate_state_change(self, state: PeerConnectionState) -> None:
        """Test helper: invoke the registered callback as the real backend would."""

        if self._state_change_callback is not None:
            await self._state_change_callback(state)


class FakePeerConnectionFactory:
    def __init__(self) -> None:
        self.created: list[FakePeerConnection] = []

    def create(self) -> FakePeerConnection:
        peer_connection = FakePeerConnection()
        self.created.append(peer_connection)
        return peer_connection
