"""Signaling WebSocket route — transport only, no policy logic.

Collaborators (Ticket Verifier, PeerConnection Factory, Registry, Heartbeat
factory, failure-code mapping) are assembled once at application startup and
read from ``app.state`` (docs/AI_CONVENTIONS.md §4 singleton convention, no
``Depends()``). One ``SignalingConnection`` is created per accepted WebSocket.
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, WebSocket

from app.signaling.connection import SignalingConnection

router = APIRouter()

_logger = logging.getLogger("app.signaling.connection")


@router.websocket("/realtime/signaling")
async def signaling_endpoint(websocket: WebSocket) -> None:
    await websocket.accept()
    state = websocket.app.state
    connection = SignalingConnection(
        websocket=websocket,
        ticket_verifier=state.ticket_verifier,
        peer_connection_factory=state.peer_connection_factory,
        registry=state.realtime_connection_registry,
        heartbeat_factory=state.realtime_heartbeat_factory,
        map_failure_code=state.map_ticket_failure_code,
        epoch_registry=state.session_epoch_registry,
        logger=_logger,
        stt_sink_factory=getattr(state, "stt_sink_factory", None),
    )
    await connection.run()
