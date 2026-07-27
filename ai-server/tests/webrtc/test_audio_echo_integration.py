"""End-to-end audio echo test against a real aiortc PeerConnection (T13-b).

A genuine aiortc PeerConnection plays the "React" role, sending a real (if
silent) Audio Track. ``AiortcPeerConnection`` (FastAPI side) must receive it,
route it through ``SessionAudioBridge``, and echo it back on its own outbound
track — verified by the client actually receiving Frames on its remote track.
"""

import asyncio

import pytest
from aiortc import RTCPeerConnection, RTCSessionDescription
from aiortc.mediastreams import AudioStreamTrack

from app.webrtc.peer_connection import AiortcPeerConnectionFactory


async def _wait_until(predicate, timeout: float = 10.0, interval: float = 0.01) -> None:
    async def _poll() -> None:
        while not predicate():
            await asyncio.sleep(interval)

    await asyncio.wait_for(_poll(), timeout=timeout)


@pytest.mark.asyncio
async def test_audio_sent_by_client_is_echoed_back_by_the_server_peer_connection() -> None:
    client = RTCPeerConnection()
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

    server = AiortcPeerConnectionFactory().create()

    offer = await client.createOffer()
    await client.setLocalDescription(offer)

    answer_sdp = await server.accept_offer(client.localDescription.sdp)
    assert "m=audio" in answer_sdp

    await client.setRemoteDescription(RTCSessionDescription(sdp=answer_sdp, type="answer"))

    await _wait_until(lambda: len(received_frames) >= 3)

    assert len(received_frames) >= 3
    # Echoed frames carry the outbound track's own monotonically increasing pts.
    assert received_frames[0].pts < received_frames[1].pts < received_frames[2].pts

    await client.close()
    await server.close()


@pytest.mark.asyncio
async def test_audio_less_offer_does_not_negotiate_an_audio_transceiver() -> None:
    client = RTCPeerConnection()
    client.createDataChannel("probe")  # no audio track at all

    server = AiortcPeerConnectionFactory().create()

    offer = await client.createOffer()
    await client.setLocalDescription(offer)

    answer_sdp = await server.accept_offer(client.localDescription.sdp)

    assert "m=audio" not in answer_sdp

    await client.close()
    await server.close()
