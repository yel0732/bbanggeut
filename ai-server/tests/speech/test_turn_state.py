"""Turn identity and the single-finalisation rule (T05)."""

from app.speech.recognizer import SttResult
from app.speech.turn_state import SttTurn, SttTurnState, TurnSequencer

_RESULT = SttResult(text="오늘 재미있었어.", audio_seconds=2.0, avg_logprob=-0.3)
_OTHER = SttResult(text="다른 결과.", audio_seconds=2.0, avg_logprob=-0.4)


class TestSingleFinalisation:
    def test_first_final_is_accepted(self) -> None:
        turn = SttTurn("turn_1", 1)

        assert turn.accept_final(_RESULT) is True
        assert turn.is_finalized
        assert turn.result is _RESULT

    def test_second_final_is_rejected_and_does_not_overwrite(self) -> None:
        """§16: the same turn must not produce two finals, nor two questions."""

        turn = SttTurn("turn_1", 1)
        turn.accept_final(_RESULT)

        assert turn.accept_final(_OTHER) is False
        assert turn.result is _RESULT

    def test_abandoned_turn_cannot_be_finalised_afterwards(self) -> None:
        turn = SttTurn("turn_1", 1)

        assert turn.abandon() is True
        assert turn.accept_final(_RESULT) is False
        assert turn.result is None

    def test_finalised_turn_cannot_be_abandoned(self) -> None:
        turn = SttTurn("turn_1", 1)
        turn.accept_final(_RESULT)

        assert turn.abandon() is False
        assert turn.state is SttTurnState.FINALIZED


class TestSequencer:
    def test_turn_seq_starts_at_one_and_increments(self) -> None:
        """§4: turnSeq는 대화마다 1부터 증가한다."""

        sequencer = TurnSequencer(turn_id_factory=lambda: "turn_x")

        assert [sequencer.next_turn().turn_seq for _ in range(3)] == [1, 2, 3]

    def test_turn_ids_come_from_the_injected_factory(self) -> None:
        ids = iter(["turn_a", "turn_b"])
        sequencer = TurnSequencer(turn_id_factory=lambda: next(ids))

        assert [sequencer.next_turn().turn_id for _ in range(2)] == ["turn_a", "turn_b"]

    def test_default_turn_id_fits_the_database_column(self) -> None:
        """conversation_turn.turn_id is VARCHAR(40)."""

        turn_id = TurnSequencer().next_turn().turn_id

        assert turn_id.startswith("turn_")
        assert len(turn_id) <= 40

    def test_default_turn_ids_are_unique(self) -> None:
        sequencer = TurnSequencer()

        assert len({sequencer.next_turn().turn_id for _ in range(50)}) == 50

    def test_two_sequencers_number_independently(self) -> None:
        first, second = TurnSequencer(), TurnSequencer()
        first.next_turn()
        first.next_turn()

        assert second.next_turn().turn_seq == 1
