"""Process-memory cache of the current ``sessionEpoch`` per conversation.

MySQL (``ConversationSession.currentSessionEpoch``) is the epoch's source of
truth (docs/diary/DECISIONS.md ADR-010); this registry is only a same-process
cache FastAPI consults to discard events referencing a stale (lower) epoch
after a reconnect, per REALTIME_EVENT_CONTRACT.md §4. No signaling/DataChannel
code calls this yet — W03/W04 wire it into real event processing.
"""

from __future__ import annotations


class SessionEpochRegistry:
    def __init__(self) -> None:
        self._current_epoch_by_conversation: dict[str, int] = {}

    def register(self, conversation_id: str, session_epoch: int) -> None:
        """Record the latest known epoch for a conversation.

        Always overwrites: a newly consumed Ticket's epoch is always the
        latest by construction (Spring only issues Tickets carrying the
        current, just-incremented epoch).
        """

        self._current_epoch_by_conversation[conversation_id] = session_epoch

    def is_current(self, conversation_id: str, session_epoch: int) -> bool:
        """Return whether ``session_epoch`` is still the accepted generation.

        An unregistered conversation is treated as current, so callers don't
        need a special case for the first event of a conversation.
        """

        known_epoch = self._current_epoch_by_conversation.get(conversation_id)
        if known_epoch is None:
            return True
        return session_epoch >= known_epoch
