"""Per-conversation registry of the active realtime connection's resources.

Keyed by ``conversationId`` only (jira/plan/DIARY-2-W03.md §11-E): a Realtime
Ticket can only pass verification when it matches the *current* Session Lease
(``app/realtime/ticket_verification.py``), so at most one successfully
authenticated Signaling connection can exist per conversation at a time.
``realtimeSessionId`` is kept only as a defensive guard in ``remove()`` so a
delayed/duplicate cleanup call from an already-replaced connection cannot
evict a newer, still-active entry.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from app.webrtc.peer_connection import PeerConnection


class Heartbeat(Protocol):
    async def start(
        self, conversation_id: str, realtime_session_id: str, session_epoch: int
    ) -> None: ...

    async def stop(self) -> None: ...


@dataclass
class RealtimeConnectionEntry:
    conversation_id: str
    realtime_session_id: str
    session_epoch: int
    peer_connection: PeerConnection
    heartbeat: Heartbeat


class RealtimeConnectionRegistry:
    def __init__(self) -> None:
        self._entries: dict[str, RealtimeConnectionEntry] = {}

    async def register(self, entry: RealtimeConnectionEntry) -> bool:
        """Register a newly-connected entry, force-cleaning up any stale one left behind.

        A stale entry under the same ``conversation_id`` should not normally exist
        (the owning ``SignalingConnection`` cleans up on close), but if one does
        (e.g. a previous connection leaked without running its own cleanup), it is
        closed here so its PeerConnection/Heartbeat never outlive the new entry.

        Returns ``False`` (without registering) if an entry with a *higher*
        ``session_epoch`` is already registered — this happens when a slow
        reconnect attempt (old epoch) finally reaches ``connected`` after a
        newer reconnect (higher epoch) has already taken over the slot; the
        stale one must back off instead of evicting the current one (mirrors
        the "discard events referencing a stale sessionEpoch" rule in
        REALTIME_EVENT_CONTRACT.md §4). The caller must clean itself up when
        this returns ``False``.
        """

        stale = self._entries.get(entry.conversation_id)
        if stale is not None and stale is not entry:
            if stale.session_epoch > entry.session_epoch:
                return False
            await stale.heartbeat.stop()
            await stale.peer_connection.close()
        self._entries[entry.conversation_id] = entry
        return True

    def find(self, conversation_id: str) -> RealtimeConnectionEntry | None:
        return self._entries.get(conversation_id)

    async def remove(self, conversation_id: str, realtime_session_id: str) -> None:
        """Idempotently drop the entry, but only if it still matches ``realtime_session_id``."""

        entry = self._entries.get(conversation_id)
        if entry is None or entry.realtime_session_id != realtime_session_id:
            return
        del self._entries[conversation_id]
