"""One child speech turn and its single-finalisation rule (DIARY-3-T05).

FUNCTIONAL_SPEC.md §16: "턴당 Final STT는 한 번만 처리한다" and "같은 Final
Event 재수신 시 질문을 중복 생성하지 않는다". Enforcing that in a state object
rather than by convention means W03 gets the guarantee for free when it hangs
the dialogue pipeline off the same callback — a second finalisation cannot
reach it.

``turnSeq`` numbering restarts per connection here. That is a known gap, not an
oversight: the Context Snapshot carries no turn count (FUNCTIONAL_SPEC.md §8
leaves it to FastAPI memory), so a mid-conversation reconnect would restart at
1 and collide with turns already stored under
``(conversation_id, turn_seq)``. Nothing is persisted in W01, so there is no
harm yet; the fix belongs to W05 where the Stream contract is settled
(jira/plan/DIARY-3-W01.md §11-B).
"""

from __future__ import annotations

import uuid
from collections.abc import Callable
from enum import StrEnum

from app.speech.recognizer import SttResult

TurnIdFactory = Callable[[], str]


def _default_turn_id_factory() -> str:
    # conversation_turn.turn_id is VARCHAR(40); this is 37 characters.
    return f"turn_{uuid.uuid4().hex}"


class SttTurnState(StrEnum):
    LISTENING = "listening"
    FINALIZED = "finalized"


class SttTurn:
    """A single child utterance, finalisable exactly once."""

    def __init__(self, turn_id: str, turn_seq: int) -> None:
        self.turn_id = turn_id
        self.turn_seq = turn_seq
        self._state = SttTurnState.LISTENING
        self._result: SttResult | None = None

    @property
    def state(self) -> SttTurnState:
        return self._state

    @property
    def is_finalized(self) -> bool:
        return self._state is SttTurnState.FINALIZED

    @property
    def result(self) -> SttResult | None:
        return self._result

    def accept_final(self, result: SttResult) -> bool:
        """Record the final transcription. Only the first call is accepted.

        Returns ``False`` instead of raising: a duplicate final is an expected
        race (a buffer-full finalisation overlapping a VAD one), not a
        programming error, and the caller simply drops it.
        """

        if self._state is SttTurnState.FINALIZED:
            return False
        self._state = SttTurnState.FINALIZED
        self._result = result
        return True

    def abandon(self) -> bool:
        """Close the turn without a result (rejected audio, provider failure).

        Uses the same one-shot transition so an abandoned turn can never be
        finalised afterwards.
        """

        if self._state is SttTurnState.FINALIZED:
            return False
        self._state = SttTurnState.FINALIZED
        return True


class TurnSequencer:
    """Issues ``turnId``/``turnSeq`` for one conversation connection.

    §4: both identifiers are FastAPI's to generate and ``turnSeq`` starts at 1.
    """

    def __init__(self, turn_id_factory: TurnIdFactory = _default_turn_id_factory) -> None:
        self._turn_id_factory = turn_id_factory
        self._next_seq = 1

    def next_turn(self) -> SttTurn:
        turn = SttTurn(self._turn_id_factory(), self._next_seq)
        self._next_seq += 1
        return turn
