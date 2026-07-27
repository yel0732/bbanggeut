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

from aiortc import RTCPeerConnection, RTCSessionDescription
from aiortc.sdp import candidate_from_sdp


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


_AIORTC_STATE_MAP: dict[str, PeerConnectionState] = {
    "connected": PeerConnectionState.CONNECTED,
    "failed": PeerConnectionState.FAILED,
    "closed": PeerConnectionState.CLOSED,
}


class AiortcPeerConnection:
    """Real WebRTC PeerConnection backed by aiortc.

    aiortc has no incremental "local candidate gathered" event: unlike a
    browser, ``setLocalDescription()`` awaits full ICE gathering before
    returning (confirmed against aiortc 1.15 — jira/plan/DIARY-2-W03.md
    §11-B), so the Answer SDP returned by ``accept_offer()`` already carries
    every local candidate. Only the remote (React) side's trickled candidates
    need ``add_ice_candidate()``.
    """

    def __init__(self) -> None:
        self._pc = RTCPeerConnection()
        self._pc.on("connectionstatechange", self._handle_state_change)
        self._callback: StateChangeCallback | None = None

    async def accept_offer(self, sdp: str) -> str:
        await self._pc.setRemoteDescription(RTCSessionDescription(sdp=sdp, type="offer"))
        answer = await self._pc.createAnswer()
        await self._pc.setLocalDescription(answer)
        return self._pc.localDescription.sdp

    async def add_ice_candidate(
        self, candidate: str, sdp_mid: str | None, sdp_mline_index: int | None
    ) -> None:
        parsed = candidate_from_sdp(candidate.removeprefix("candidate:"))
        parsed.sdpMid = sdp_mid
        parsed.sdpMLineIndex = sdp_mline_index
        await self._pc.addIceCandidate(parsed)

    def on_state_change(self, callback: StateChangeCallback) -> None:
        self._callback = callback

    async def close(self) -> None:
        await self._pc.close()

    async def _handle_state_change(self) -> None:
        state = _AIORTC_STATE_MAP.get(self._pc.connectionState)
        if state is not None and self._callback is not None:
            await self._callback(state)


class AiortcPeerConnectionFactory:
    def create(self) -> AiortcPeerConnection:
        return AiortcPeerConnection()
