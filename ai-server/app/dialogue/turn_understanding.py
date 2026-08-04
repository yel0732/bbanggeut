"""Deterministic understanding of one child dialogue turn.

This module does not infer new diary facts. It only recognizes information
that is explicit in the child's latest answer or licensed by the exact question
the child just answered, such as answering a "fun moment" question.
"""

from dataclasses import dataclass

from collections.abc import Collection

from app.dialogue.policy.target import QuestionTarget
from app.dialogue.schemas import DiarySlots


_POSITIVE_EMOTIONS: tuple[tuple[str, str], ...] = (
    ("재밌", "재미있었어"),
    ("재미", "재미있었어"),
    ("신기", "신기했어"),
    ("좋았", "좋았어"),
    ("좋아", "좋았어"),
    ("행복", "행복했어"),
    ("기뻤", "기뻤어"),
    ("멋지", "멋졌어"),
)

_NEGATIVE_EMOTIONS: tuple[tuple[str, str], ...] = (
    ("속상", "속상했어"),
    ("슬퍼", "슬펐어"),
    ("슬펐", "슬펐어"),
    ("화났", "화났어"),
    ("화가", "화났어"),
    ("아쉬", "아쉬웠어"),
    ("무서", "무서웠어"),
    ("싫어", "싫었어"),
)

_REASON_CONNECTORS: tuple[str, ...] = (
    "라서",
    "이라서",
    "해서",
    "때문",
    "니까",
    "어서",
    "아서",
    "는데",
)

_FUN_MOMENT_QUESTION_CUES: tuple[str, ...] = (
    "재미있었어",
    "재밌었어",
    "좋았어",
    "제일 재미",
    "제일 좋",
    "뭐가 그렇게",
)

_CONTINUE_REQUEST_CUES: tuple[str, ...] = (
    "더대화",
    "더얘기",
    "더이야기",
    "계속",
    "왜마지막",
    "왜끝",
    "벌써끝",
)

_CHILD_QUESTION_CUES: tuple[str, ...] = (
    "?",
    "뭐라고",
    "어떻게",
    "왜",
    "너도",
    "너는",
    "좋아해",
    "재밌지",
    "재미있지",
    "해봤어",
    "먹어봤어",
    "가봤어",
)

_NEGATIVE_EVENT_CUES: tuple[str, ...] = (
    "부서",
    "망가",
    "쓰러",
    "밀쳐",
    "밀었",
    "넘어질",
    "넘어졌",
    "못탔",
    "못타",
    "안비켜",
    "안나와",
    "안줘",
)


@dataclass(frozen=True)
class TurnUnderstanding:
    """Meaning that can be read from the latest child answer."""

    emotion: str | None = None
    emotion_reason: str | None = None
    memorable_moment: str | None = None
    child_question: bool = False
    continue_request: bool = False
    negative_event: bool = False
    satisfied_targets: frozenset[QuestionTarget] = frozenset()
    suppressed_targets: frozenset[QuestionTarget] = frozenset()


def understand_child_turn(
    *,
    child_answer: str,
    current_question: str | None,
    asked_target: QuestionTarget | None,
    current_slots: DiarySlots,
) -> TurnUnderstanding:
    """Classify the latest answer for deterministic dialogue policy."""

    answer = child_answer.strip()
    normalized_answer = _normalize(answer)
    normalized_question = _normalize(current_question or "")
    child_question = _contains_any(normalized_answer, _CHILD_QUESTION_CUES)
    continue_request = _contains_any(
        normalized_answer,
        _CONTINUE_REQUEST_CUES,
    )
    negative_event = _contains_any(normalized_answer, _NEGATIVE_EVENT_CUES)
    if child_question:
        return TurnUnderstanding(
            child_question=True,
            continue_request=continue_request,
            negative_event=negative_event,
        )

    emotion = _find_emotion(normalized_answer)
    question_asks_fun_moment = _contains_any(
        normalized_question,
        _FUN_MOMENT_QUESTION_CUES,
    )
    if emotion is None and question_asks_fun_moment and answer:
        emotion = _emotion_from_question(normalized_question)

    reason = None
    if emotion is not None:
        reason = _extract_reason_phrase(answer, emotion)
    if reason is None and question_asks_fun_moment and answer:
        reason = answer
    if (
        reason is None
        and asked_target is QuestionTarget.EMOTION
        and current_slots.event
        and emotion is not None
        and _emotion_question_refers_to_existing_event(current_question)
    ):
        reason = current_slots.event

    memorable = None
    if question_asks_fun_moment and answer:
        memorable = reason or answer
    elif emotion is not None:
        memorable = _extract_reason_phrase(answer, emotion)

    satisfied: set[QuestionTarget] = set()
    suppressed: set[QuestionTarget] = set()
    if emotion is not None:
        satisfied.add(QuestionTarget.EMOTION)
        suppressed.add(QuestionTarget.EMOTION)
    if reason is not None:
        satisfied.add(QuestionTarget.EMOTION_REASON)
        suppressed.add(QuestionTarget.EMOTION_REASON)
    if memorable is not None:
        satisfied.add(QuestionTarget.MEMORABLE_MOMENT)

    return TurnUnderstanding(
        emotion=emotion,
        emotion_reason=_clean_phrase(reason),
        memorable_moment=_clean_phrase(memorable),
        child_question=child_question,
        continue_request=continue_request,
        negative_event=negative_event,
        satisfied_targets=frozenset(satisfied),
        suppressed_targets=frozenset(suppressed),
    )


def apply_turn_understanding(
    slots: DiarySlots,
    understanding: TurnUnderstanding,
    *,
    excluded_targets: Collection[QuestionTarget] = (),
) -> DiarySlots:
    """Fill missing slots with explicit information from a turn."""

    excluded = frozenset(excluded_targets)
    updates: dict[str, str] = {}
    if (
        QuestionTarget.EMOTION not in excluded
        and not _is_filled(slots.emotion)
        and understanding.emotion
    ):
        updates["emotion"] = understanding.emotion
    if (
        QuestionTarget.EMOTION_REASON not in excluded
        and not _is_filled(slots.emotion_reason)
        and understanding.emotion_reason
    ):
        updates["emotion_reason"] = understanding.emotion_reason
    if (
        QuestionTarget.MEMORABLE_MOMENT not in excluded
        and not _is_filled(slots.memorable_moment)
        and understanding.memorable_moment
    ):
        updates["memorable_moment"] = understanding.memorable_moment
    if not updates:
        return slots
    return slots.model_copy(update=updates, deep=True)


def _find_emotion(normalized_answer: str) -> str | None:
    for cue, emotion in (*_NEGATIVE_EMOTIONS, *_POSITIVE_EMOTIONS):
        if cue in normalized_answer:
            return emotion
    return None


def _emotion_from_question(normalized_question: str) -> str:
    if "좋" in normalized_question:
        return "좋았어"
    return "재미있었어"


def _extract_reason_phrase(answer: str, emotion: str) -> str | None:
    normalized_answer = _normalize(answer)
    normalized_emotion = _normalize(emotion)
    emotion_index = normalized_answer.find(normalized_emotion[:2])
    for connector in _REASON_CONNECTORS:
        normalized_connector = _normalize(connector)
        connector_index = normalized_answer.find(normalized_connector)
        if connector_index < 0:
            continue
        if emotion_index >= 0 and connector_index > emotion_index:
            continue
        phrase = answer[
            : _approximate_source_index(
                answer,
                connector_index + len(normalized_connector),
            )
        ]
        phrase = phrase.strip(" ,.!?~")
        if phrase:
            return phrase
    return None


def _approximate_source_index(text: str, normalized_index: int) -> int:
    seen = 0
    for index, character in enumerate(text):
        if character.isspace():
            continue
        if seen >= normalized_index:
            return index
        seen += 1
    return len(text)


def _emotion_question_refers_to_existing_event(
    current_question: str | None,
) -> bool:
    normalized = current_question or ""
    return any(
        cue in normalized
        for cue in ("그때", "그 순간", "그곳", "거기", "할 때")
    )


def _clean_phrase(value: str | None) -> str | None:
    if value is None:
        return None
    cleaned = value.strip(" ,.!?~")
    return cleaned or None


def _contains_any(value: str, cues: tuple[str, ...]) -> bool:
    return any(_normalize(cue) in value for cue in cues)


def _is_filled(value: str | None) -> bool:
    return value is not None and bool(value.strip())


def _normalize(value: str) -> str:
    return "".join(value.strip().casefold().split())
