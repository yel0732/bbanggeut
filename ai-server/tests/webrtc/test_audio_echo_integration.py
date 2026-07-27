"""End-to-end audio echo test against a real aiortc PeerConnection (T13-b).

A genuine aiortc PeerConnection plays the "React" role, sending a real (if
silent) Audio Track. ``AiortcPeerConnection`` (FastAPI side) must receive it,
route it through ``SessionAudioBridge``, and echo it back on its own outbound
track — verified by the client actually receiving Frames on its remote track.

Teardown discipline (added after a CI-hang report, 2026-07-27): every test in
this file must COMPLETE the negotiation it starts — the server's answer is
always applied via ``client.setRemoteDescription()`` and the connection is
awaited until established before closing. Abandoning a half-negotiated
PeerConnection leaves the server's ICE/DTLS machinery attempting to connect to
a peer that never answers, which is timing-dependent at ``close()`` time and
was observed to hang pytest on some machines. ``try/finally`` + explicit
timeouts guarantee that a failure inside the test body can never leak a
PeerConnection or block the suite indefinitely.
"""

import asyncio

import pytest
from aiortc import RTCPeerConnection, RTCSessionDescription
from aiortc.mediastreams import AudioStreamTrack

from app.webrtc.peer_connection import AiortcPeerConnectionFactory

# ICE gathering alone takes ~5s per PeerConnection when the default STUN
# server (stun.l.google.com:19302, aiortc's built-in default) is unreachable —
# measured 5.02s on this network. Budget well above that so the timeout only
# fires on a genuine hang, not on a slow-but-normal gathering pass.
_NEGOTIATION_TIMEOUT_SECONDS = 30.0
_CLEANUP_TIMEOUT_SECONDS = 10.0


async def _wait_until(predicate, timeout: float = 10.0, interval: float = 0.01) -> None:
    async def _poll() -> None:
        while not predicate():
            await asyncio.sleep(interval)

    await asyncio.wait_for(_poll(), timeout=timeout)


async def _close_both(client: RTCPeerConnection, server) -> None:
    """Close both peers even if one close fails, each under its own timeout."""

    try:
        await asyncio.wait_for(client.close(), timeout=_CLEANUP_TIMEOUT_SECONDS)
    finally:
        await asyncio.wait_for(server.close(), timeout=_CLEANUP_TIMEOUT_SECONDS)


@pytest.mark.asyncio
async def test_audio_sent_by_client_is_echoed_back_by_the_server_peer_connection() -> None:
    client = RTCPeerConnection()
    server = AiortcPeerConnectionFactory().create()
    try:
        client.addTrack(AudioStreamTrack())  # client's "mic" — deterministic silence generator

        received_frames: list = []

        @client.on("track")
        def on_track(track) -> None:
            asyncio.ensure_future(_collect(track, received_frames))

        async def _collect(track, sink: list) -> None:
            try:
                while len(sink) < 3:
                    frame = await track.recv()
                    sink.append(frame)
            except Exception:  # noqa: BLE001 - test collector, track ending is fine
                pass

        offer = await client.createOffer()
        await client.setLocalDescription(offer)

        answer_sdp = await asyncio.wait_for(
            server.accept_offer(client.localDescription.sdp),
            timeout=_NEGOTIATION_TIMEOUT_SECONDS,
        )
        assert "m=audio" in answer_sdp

        await client.setRemoteDescription(
            RTCSessionDescription(sdp=answer_sdp, type="answer")
        )

        await _wait_until(lambda: len(received_frames) >= 3)

        assert len(received_frames) >= 3
        # Echoed frames carry the outbound track's own monotonically increasing pts.
        assert received_frames[0].pts < received_frames[1].pts < received_frames[2].pts
    finally:
        await _close_both(client, server)


@pytest.mark.asyncio
async def test_audio_less_offer_does_not_negotiate_an_audio_transceiver() -> None:
    client = RTCPeerConnection()
    server = AiortcPeerConnectionFactory().create()
    try:
        client.createDataChannel("probe")  # no audio track at all

        offer = await client.createOffer()
        await client.setLocalDescription(offer)

        answer_sdp = await asyncio.wait_for(
            server.accept_offer(client.localDescription.sdp),
            timeout=_NEGOTIATION_TIMEOUT_SECONDS,
        )

        assert "m=audio" not in answer_sdp

        # Finish what we started: apply the answer and wait for the connection
        # to actually establish, so close() tears down a settled connection
        # instead of racing a server-side ICE attempt that has no counterpart
        # (the reported pytest hang, see module docstring).
        await client.setRemoteDescription(
            RTCSessionDescription(sdp=answer_sdp, type="answer")
        )
        await _wait_until(lambda: client.connectionState == "connected")
    finally:
        await _close_both(client, server)
