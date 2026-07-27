"""End-to-end DataChannel test against real aiortc PeerConnections (T14-b).

A genuine aiortc PeerConnection plays the "React" role: it creates the
``conversation-events`` DataChannel itself (React is always the offerer/
channel creator — see jira/plan/DIARY-2-W04.md §1), and
``AiortcPeerConnection`` (FastAPI side) must receive it via
``on_data_channel_received`` and exchange real JSON messages both ways.
"""

import asyncio
import json

import pytest
from aiortc import RTCPeerConnection, RTCSessionDescription

from app.webrtc.data_channel import DataChannel
from app.webrtc.peer_connection import AiortcPeerConnectionFactory


async def _wait_until(predicate, timeout: float = 10.0, interval: float = 0.01) -> None:
    async def _poll() -> None:
        while not predicate():
            await asyncio.sleep(interval)

    await asyncio.wait_for(_poll(), timeout=timeout)


@pytest.mark.asyncio
async def test_client_created_data_channel_is_received_and_exchanges_json() -> None:
    client = RTCPeerConnection()
    client_channel = client.createDataChannel("conversation-events", ordered=True)

    client_received: list[dict] = []
    client_channel.on(
        "message", lambda message: client_received.append(json.loads(message))
    )

    server = AiortcPeerConnectionFactory().create()
    received_channels: list[DataChannel] = []

    async def on_data_channel(channel: DataChannel) -> None:
        received_channels.append(channel)

    server.on_data_channel_received(on_data_channel)

    offer = await client.createOffer()
    await client.setLocalDescription(offer)
    answer_sdp = await server.accept_offer(client.localDescription.sdp)
    await client.setRemoteDescription(RTCSessionDescription(sdp=answer_sdp, type="answer"))

    await _wait_until(lambda: len(received_channels) == 1)
    server_channel = received_channels[0]

    server_received: list[dict] = []

    async def on_server_message(payload: dict) -> None:
        server_received.append(payload)

    server_channel.on_message(on_server_message)

    await _wait_until(lambda: server_channel.is_open)
    client_channel.send(
        json.dumps(
            {
                "type": "client.ready",
                "eventId": "evt_1",
                "conversationId": "conv_1",
                "realtimeSessionId": "rt_1",
                "sessionEpoch": 1,
                "eventSeq": 1,
                "occurredAt": "2026-07-22T10:00:10+09:00",
                "payload": {"microphoneReady": True},
            }
        )
    )
    await _wait_until(lambda: len(server_received) == 1)
    assert server_received[0]["type"] == "client.ready"

    server_channel.send_json({"type": "conversation.state.changed", "payload": {}})
    await _wait_until(lambda: len(client_received) == 1)
    assert client_received[0]["type"] == "conversation.state.changed"

    await client.close()
    await server.close()
