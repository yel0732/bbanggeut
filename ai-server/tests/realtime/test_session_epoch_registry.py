"""Tests for the in-process sessionEpoch registry."""

from app.realtime.session_epoch_registry import SessionEpochRegistry


def test_unregistered_conversation_is_treated_as_current() -> None:
    registry = SessionEpochRegistry()

    assert registry.is_current("conv_1", 1) is True


def test_registers_and_matches_current_epoch() -> None:
    registry = SessionEpochRegistry()
    registry.register("conv_1", 2)

    assert registry.is_current("conv_1", 2) is True


def test_lower_epoch_is_not_current() -> None:
    registry = SessionEpochRegistry()
    registry.register("conv_1", 2)

    assert registry.is_current("conv_1", 1) is False


def test_higher_epoch_is_current() -> None:
    registry = SessionEpochRegistry()
    registry.register("conv_1", 2)

    assert registry.is_current("conv_1", 3) is True


def test_register_overwrites_previous_epoch() -> None:
    registry = SessionEpochRegistry()
    registry.register("conv_1", 1)
    registry.register("conv_1", 2)

    assert registry.is_current("conv_1", 1) is False
    assert registry.is_current("conv_1", 2) is True


def test_conversations_are_tracked_independently() -> None:
    registry = SessionEpochRegistry()
    registry.register("conv_1", 5)

    assert registry.is_current("conv_2", 1) is True
