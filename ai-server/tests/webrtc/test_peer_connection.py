"""Integration tests for AiortcPeerConnection against a real aiortc peer.

No STUN/TURN is configured (out of scope per jira/plan/DIARY-2-W03.md §0) —
both sides run in-process on loop-back, so host candidates are sufficient to
reach ``connected`` (verified against aiortc 1.15, §11-B). Test-side client
PeerConnections pass an explicit ``iceServers=[]`` because aiortc's default
configuration otherwise queries Google STUN, stalling gathering ~5s per
PeerConnection on networks where STUN is blocked (measured 2026-07-27).
"""

import asyncio

import pytest
from aiortc import RTCConfiguration, RTCPeerConnection, RTCSessionDescription

from app.webrtc.peer_connection import (
    AiortcPeerConnection,
    AiortcPeerConnectionFactory,
    PeerConnectionState,
)


def _first_ice_candidate_line(sdp: str) -> str | None:
    for line in sdp.splitlines():
        if line.startswith("a=candidate:"):
            return line[len("a=") :]
    return None


@pytest.mark.asyncio
async def test_accept_offer_returns_answer_and_both_sides_reach_connected() -> None:
    client = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    client.createDataChannel("probe")  # ensure at least one m-line
    server = AiortcPeerConnectionFactory().create()

    client_states: list[str] = []
    client.on("connectionstatechange", lambda: client_states.append(client.connectionState))

    server_connected = asyncio.Event()

    async def on_server_state_change(state: PeerConnectionState) -> None:
        if state == PeerConnectionState.CONNECTED:
            server_connected.set()

    server.on_state_change(on_server_state_change)

    offer = await client.createOffer()
    await client.setLocalDescription(offer)

    answer_sdp = await server.accept_offer(client.localDescription.sdp)
    assert "a=candidate:" in answer_sdp  # aiortc가 setLocalDescription 중 후보를 이미 채움
    # stun_urls 미설정이면 STUN 조회 자체가 없어야 한다 — srflx 후보가 나오면
    # 외부 STUN 의존이 되살아난 것이다(기본값이 Google STUN인 aiortc 특성).
    assert "typ srflx" not in answer_sdp

    await client.setRemoteDescription(RTCSessionDescription(sdp=answer_sdp, type="answer"))

    await asyncio.wait_for(server_connected.wait(), timeout=10)
    await asyncio.wait_for(
        _wait_until(lambda: client.connectionState == "connected"), timeout=10
    )

    assert client.connectionState == "connected"

    await client.close()
    await server.close()


async def _wait_until(predicate, interval: float = 0.01) -> None:
    while not predicate():
        await asyncio.sleep(interval)


@pytest.mark.asyncio
async def test_add_ice_candidate_accepts_a_real_remote_candidate() -> None:
    client = RTCPeerConnection(RTCConfiguration(iceServers=[]))
    client.createDataChannel("probe")
    server = AiortcPeerConnectionFactory().create()

    offer = await client.createOffer()
    await client.setLocalDescription(offer)
    candidate_line = _first_ice_candidate_line(client.localDescription.sdp)
    assert candidate_line is not None

    await server.accept_offer(client.localDescription.sdp)

    # sdpMid "0"/mline 0 matches the single m-line created above.
    await server.add_ice_candidate(candidate_line, "0", 0)

    await server.close()


@pytest.mark.asyncio
async def test_close_is_idempotent() -> None:
    server = AiortcPeerConnectionFactory().create()

    await server.close()
    await server.close()


@pytest.mark.asyncio
async def test_state_change_callback_not_invoked_for_unmapped_states() -> None:
    server = AiortcPeerConnection()
    observed: list[PeerConnectionState] = []

    async def record(state: PeerConnectionState) -> None:
        observed.append(state)

    server.on_state_change(record)

    # A fresh PeerConnection's initial state is "new", which has no mapping —
    # closing it transitions through states but only "closed" is mapped.
    await server.close()

    assert observed in ([], [PeerConnectionState.CLOSED])
