"""Per-session AI response lifecycle (DIARY-3-T13).

Every AI question/TTS run gets a ``responseId`` (FUNCTIONAL_SPEC §4 — FastAPI
issues it, and it exists only on AI turns). ADR-012's rule is that a cancelled
``responseId`` blocks results in every layer, which shapes the two consumers of
this registry:

- the dialogue manager re-checks *its own* response right before publishing —
  an LLM result is a non-cancellable Provider outcome, so it is awaited and
  then discarded rather than interrupted (docs/AI_CONVENTIONS.md §8);
- the inbound ``ConversationEventGuard`` drops client events that reference a
  cancelled response (contract §4, wired via hook).

``cancel()`` has no caller in DIARY-3 — barge-in (``client.interrupt``,
DIARY-4) is the trigger. The boundary exists now so W04's TTS and DIARY-4's
cancellation land on a ready seam instead of retrofitting one.

Sets grow only for the lifetime of one connection (same bound and rationale as
``ConversationEventGuard._seen_event_ids``, W04 plan §11-D).
"""

from __future__ import annotations

import uuid
from collections.abc import Callable

ResponseIdFactory = Callable[[], str]


def _default_response_id_factory() -> str:
    # conversation_turn.response_id는 VARCHAR(40) — 37자.
    return f"resp_{uuid.uuid4().hex}"


class ResponseRegistry:
    """Issues and tracks one session's AI response identifiers."""

    def __init__(
        self, response_id_factory: ResponseIdFactory = _default_response_id_factory
    ) -> None:
        self._response_id_factory = response_id_factory
        self._known: set[str] = set()
        self._cancelled: set[str] = set()

    def issue(self) -> str:
        response_id = self._response_id_factory()
        self._known.add(response_id)
        return response_id

    def cancel(self, response_id: str) -> bool:
        """Mark a response cancelled. Unknown ids are refused (nothing to cancel)."""

        if response_id not in self._known:
            return False
        self._cancelled.add(response_id)
        return True

    def is_cancelled(self, response_id: str) -> bool:
        return response_id in self._cancelled

    def is_known(self, response_id: str) -> bool:
        return response_id in self._known
