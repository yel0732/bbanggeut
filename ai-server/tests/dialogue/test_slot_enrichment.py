"""Tests for deterministic participant enrichment."""

from app.dialogue.schemas import DiarySlots
from app.dialogue.analysis.enrichment import enrich_explicit_people


def test_extracts_explicit_teacher_from_first_event() -> None:
    enriched = enrich_explicit_people(
        DiarySlots(event="영화를 봤어"),
        "오늘 선생님이랑 영화를 봤어",
    )

    assert enriched.people == ["선생님"]


def test_preserves_and_deduplicates_existing_people() -> None:
    enriched = enrich_explicit_people(
        DiarySlots(people=["친구"]),
        "친구랑 선생님이 같이 있었어",
    )

    assert enriched.people == ["친구", "선생님"]
