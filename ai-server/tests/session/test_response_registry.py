"""AI response lifecycle tests (T13)."""

from app.session.response_registry import ResponseRegistry


def _registry() -> ResponseRegistry:
    ids = iter(f"resp_{index}" for index in range(1, 100))
    return ResponseRegistry(response_id_factory=lambda: next(ids))


class TestIssue:
    def test_issued_ids_are_known_and_not_cancelled(self) -> None:
        registry = _registry()

        response_id = registry.issue()

        assert response_id == "resp_1"
        assert registry.is_known(response_id)
        assert not registry.is_cancelled(response_id)

    def test_default_ids_fit_the_database_column(self) -> None:
        """conversation_turn.response_id is VARCHAR(40)."""

        response_id = ResponseRegistry().issue()

        assert response_id.startswith("resp_")
        assert len(response_id) <= 40


class TestCancel:
    def test_cancel_marks_a_known_response(self) -> None:
        registry = _registry()
        response_id = registry.issue()

        assert registry.cancel(response_id) is True
        assert registry.is_cancelled(response_id)

    def test_cancel_refuses_an_unknown_response(self) -> None:
        registry = _registry()

        assert registry.cancel("resp_ghost") is False
        assert not registry.is_cancelled("resp_ghost")

    def test_cancelling_one_response_does_not_affect_others(self) -> None:
        registry = _registry()
        first = registry.issue()
        second = registry.issue()

        registry.cancel(first)

        assert registry.is_cancelled(first)
        assert not registry.is_cancelled(second)


def test_two_registries_share_no_state() -> None:
    first, second = _registry(), _registry()
    response_id = first.issue()
    first.cancel(response_id)

    assert not second.is_known(response_id)
    assert not second.is_cancelled(response_id)
