"""PeerConnection abstraction for WebRTC SDP/ICE, Audio Track, and DataChannel.

SDP/ICE/connection-state handling landed in W03 (T11); Audio Track (W04/T13,
see app/webrtc/audio_bridge.py) and DataChannel (W04/T14, see
app/webrtc/data_channel.py) are wired in here. ``FakePeerConnection`` lets
signaling/connection.py's state machine be unit tested without a real
PeerConnection.
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from enum import StrEnum
from typing import Protocol

from aiortc import RTCConfiguration, RTCIceServer, RTCPeerConnection, RTCSessionDescription
from aiortc.mediastreams import MediaStreamTrack
from aiortc.sdp import candidate_from_sdp

from app.webrtc.audio_bridge import AudioSink, SessionAudioBridge
from app.webrtc.data_channel import AiortcDataChannel, DataChannel


class PeerConnectionState(StrEnum):
    NEW = "new"
    CONNECTED = "connected"
    FAILED = "failed"
    CLOSED = "closed"


StateChangeCallback = Callable[[PeerConnectionState], Awaitable[None]]
DataChannelReceivedCallback = Callable[[DataChannel], Awaitable[None]]


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

    def on_data_channel_received(self, callback: DataChannelReceivedCallback) -> None:
        """Register the (single) callback invoked when React's DataChannel arrives.

        FastAPI never calls ``createDataChannel()`` itself — React (the
        offerer) creates ``conversation-events``, and this side only receives
        it (confirmed against aiortc's ``datachannel`` event, jira/plan/
        DIARY-2-W04.md §1).
        """
        ...

    def attach_audio_sink(self, sink: AudioSink) -> None:
        """Route inbound Audio Frames to an STT sink as well as the echo path.

        Called once the ``conversation-events`` DataChannel exists, because the
        sink publishes through it (see ``SessionAudioBridge.attach_sink``).
        """
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
        self._data_channel_callback: DataChannelReceivedCallback | None = None
        self.attached_sink: AudioSink | None = None

    async def accept_offer(self, sdp: str) -> str:
        self.accepted_offer_sdp = sdp
        return self.answer_sdp

    async def add_ice_candidate(
        self, candidate: str, sdp_mid: str | None, sdp_mline_index: int | None
    ) -> None:
        self.added_ice_candidates.append((candidate, sdp_mid, sdp_mline_index))

    def on_state_change(self, callback: StateChangeCallback) -> None:
        self._state_change_callback = callback

    def on_data_channel_received(self, callback: DataChannelReceivedCallback) -> None:
        self._data_channel_callback = callback

    def attach_audio_sink(self, sink: AudioSink) -> None:
        self.attached_sink = sink

    async def close(self) -> None:
        self.closed = True

    async def simulate_state_change(self, state: PeerConnectionState) -> None:
        """Test helper: invoke the registered callback as the real backend would."""

        if self._state_change_callback is not None:
            await self._state_change_callback(state)

    async def simulate_data_channel_received(self, channel: DataChannel) -> None:
        """Test helper: invoke the registered callback as the real backend would."""

        if self._data_channel_callback is not None:
            await self._data_channel_callback(channel)


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

    def __init__(self, stun_urls: tuple[str, ...] = ()) -> None:
        # aiortc treats iceServers=None as "use the built-in Google STUN
        # default" — an unwanted external network dependency that stalls ICE
        # gathering ~5s per PeerConnection when STUN is unreachable (measured,
        # jira/plan/DIARY-3-W01.md). An explicit empty list disables STUN
        # lookups entirely; production injects real URLs via
        # AppSettings.realtime_stun_urls when a deployment actually needs
        # srflx candidates (NAT traversal — INFRA epic scope).
        ice_servers = [RTCIceServer(urls=list(stun_urls))] if stun_urls else []
        self._pc = RTCPeerConnection(RTCConfiguration(iceServers=ice_servers))
        self._pc.on("connectionstatechange", self._handle_state_change)
        self._pc.on("track", self._handle_track)
        self._pc.on("datachannel", self._handle_data_channel)
        self._callback: StateChangeCallback | None = None
        self._data_channel_callback: DataChannelReceivedCallback | None = None
        self._audio_bridge = SessionAudioBridge(logging.getLogger("app.webrtc.audio_bridge"))

    async def accept_offer(self, sdp: str) -> str:
        # setRemoteDescription() processes the offer's m-lines synchronously,
        # so _handle_track() has already started self._audio_bridge (if the
        # offer carried an audio track) by the time we reach the next line —
        # confirmed against aiortc 1.15 source (jira/plan/DIARY-2-W04.md §1).
        #
        # addTrack() must NOT be called for an audio-less offer: aiortc only
        # creates a transceiver per m-line actually present in the offer, and
        # adding a track with no matching transceiver crashes createAnswer()/
        # setLocalDescription() with "None is not in list" (found while wiring
        # this up — a test client offering only a DataChannel, no audio,
        # reproduced it). So the outbound echo track is only added when an
        # inbound audio track was actually received (§11-C correction: no
        # AudioStreamTrack() fallback for a genuinely audio-less offer).
        await self._pc.setRemoteDescription(RTCSessionDescription(sdp=sdp, type="offer"))
        if self._audio_bridge.is_running:
            self._pc.addTrack(self._audio_bridge.create_outbound_track())
        answer = await self._pc.createAnswer()
        await self._pc.setLocalDescription(answer)
        return self._pc.localDescription.sdp

    def _handle_track(self, track: MediaStreamTrack) -> None:
        if track.kind == "audio" and not self._audio_bridge.is_running:
            self._audio_bridge.start(track)

    async def add_ice_candidate(
        self, candidate: str, sdp_mid: str | None, sdp_mline_index: int | None
    ) -> None:
        parsed = candidate_from_sdp(candidate.removeprefix("candidate:"))
        parsed.sdpMid = sdp_mid
        parsed.sdpMLineIndex = sdp_mline_index
        await self._pc.addIceCandidate(parsed)

    def on_state_change(self, callback: StateChangeCallback) -> None:
        self._callback = callback

    def on_data_channel_received(self, callback: DataChannelReceivedCallback) -> None:
        self._data_channel_callback = callback

    def attach_audio_sink(self, sink: AudioSink) -> None:
        self._audio_bridge.attach_sink(sink)

    async def close(self) -> None:
        await self._audio_bridge.stop()
        await self._pc.close()

    async def _handle_state_change(self) -> None:
        state = _AIORTC_STATE_MAP.get(self._pc.connectionState)
        if state is not None and self._callback is not None:
            await self._callback(state)

    async def _handle_data_channel(self, channel) -> None:
        if self._data_channel_callback is not None:
            await self._data_channel_callback(AiortcDataChannel(channel))


class AiortcPeerConnectionFactory:
    def __init__(self, stun_urls: tuple[str, ...] = ()) -> None:
        self._stun_urls = stun_urls

    def create(self) -> AiortcPeerConnection:
        return AiortcPeerConnection(self._stun_urls)
