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
    "타서",
    "해서",
    "서",
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
    "놀렸",
    "새치기",
)

_ACTION_EVENT_CUES: tuple[str, ...] = (
    "했",
    "갔",
    "먹",
    "봤",
    "탔",
    "만들",
    "그렸",
    "놀",
    "자르",
    "잘랐",
    "잘리",
    "맞았",
    "받았",
    "비켜",
    "못타",
    "못탔",
)


@dataclass(frozen=True)
class TurnUnderstanding:
    """Meaning that can be read from the latest child answer."""

    event: str | None = None
    emotion: str | None = None
    emotion_reason: str | None = None
    memorable_moment: str | None = None
    child_question: bool = False
    continue_request: bool = False
    negative_event: bool = False
    topic_refusal: bool = False
    diary_content: str | None = None
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
    topic_refusal = _is_topic_refusal(normalized_answer)
    diary_content = _extract_diary_content(answer)
    analysis_text = diary_content or ("" if child_question else answer)
    normalized_analysis = _normalize(analysis_text)

    emotion = _find_emotion(normalized_analysis)
    question_asks_fun_moment = _contains_any(
        normalized_question,
        _FUN_MOMENT_QUESTION_CUES,
    )
    if emotion is None and question_asks_fun_moment and answer:
        emotion = _emotion_from_question(normalized_question)

    reason = None
    if emotion is not None:
        reason = _extract_reason_phrase(analysis_text, emotion)
    if reason is None and question_asks_fun_moment and answer:
        reason = analysis_text
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
        memorable = reason or analysis_text
    elif emotion is not None:
        memorable = _extract_reason_phrase(analysis_text, emotion)
    elif (
        asked_target is QuestionTarget.MEMORABLE_MOMENT
        and _looks_like_event_content(analysis_text)
    ):
        memorable = analysis_text

    satisfied: set[QuestionTarget] = set()
    suppressed: set[QuestionTarget] = set()
    event = _event_from_content(
        analysis_text,
        asked_target=asked_target,
        emotion=emotion,
    )
    if event is not None:
        satisfied.add(QuestionTarget.EVENT)
    if emotion is not None:
        satisfied.add(QuestionTarget.EMOTION)
        suppressed.add(QuestionTarget.EMOTION)
    if reason is not None:
        satisfied.add(QuestionTarget.EMOTION_REASON)
        suppressed.add(QuestionTarget.EMOTION_REASON)
    if memorable is not None:
        satisfied.add(QuestionTarget.MEMORABLE_MOMENT)

    return TurnUnderstanding(
        event=event,
        emotion=emotion,
        emotion_reason=_clean_phrase(reason),
        memorable_moment=_clean_phrase(memorable),
        child_question=child_question,
        continue_request=continue_request,
        negative_event=negative_event,
        topic_refusal=topic_refusal,
        diary_content=_clean_phrase(diary_content),
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
        QuestionTarget.EVENT not in excluded
        and not _is_filled(slots.event)
        and understanding.event
    ):
        updates["event"] = understanding.event
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
    selected_end_index: int | None = None
    for connector in _REASON_CONNECTORS:
        normalized_connector = _normalize(connector)
        search_from = 0
        while True:
            connector_index = normalized_answer.find(
                normalized_connector,
                search_from,
            )
            if connector_index < 0:
                break
            search_from = connector_index + len(normalized_connector)
            if emotion_index >= 0 and connector_index > emotion_index:
                continue
            connector_end = connector_index + len(normalized_connector)
            if selected_end_index is None or connector_end > selected_end_index:
                selected_end_index = connector_end
    if selected_end_index is None:
        return None
    phrase = answer[
        : _approximate_source_index(
            answer,
            selected_end_index,
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


def _extract_diary_content(answer: str) -> str | None:
    fragments = [
        fragment.strip(" ,.!?~")
        for fragment in _split_turn_fragments(answer)
        if fragment.strip(" ,.!?~")
    ]
    content_fragments = [
        fragment
        for fragment in fragments
        if not _is_control_only_fragment(fragment)
        and not _is_ai_directed_question_fragment(fragment)
        and not _is_question_tail_fragment(fragment)
    ]
    if not content_fragments:
        return None
    return " ".join(content_fragments).strip() or None


def _split_turn_fragments(answer: str) -> list[str]:
    normalized = answer.replace("\r", "\n")
    for marker in ("?", "？", "!", ".", "。", "\n"):
        normalized = normalized.replace(marker, "|")
    return normalized.split("|")


def _is_control_only_fragment(fragment: str) -> bool:
    normalized = _normalize(fragment)
    if not normalized:
        return True
    return any(
        cue in normalized
        for cue in (
            "게임얘기안할래",
            "게임이야기안할래",
            "게임말안할래",
            "게임얘기말고",
            "게임이야기말고",
            "게임말고",
            "게임그만",
            "다른얘기할래",
            "다른이야기할래",
            "말하기싫어",
            "얘기안할래",
        )
    )


def _is_ai_directed_question_fragment(fragment: str) -> bool:
    normalized = _normalize(fragment)
    if not normalized:
        return False
    if not any(
        cue in normalized
        for cue in (
            "너도",
            "너는",
            "넌",
            "너에게",
            "선생님은",
            "ai는",
            "에이아이는",
        )
    ):
        return False
    return any(
        cue in normalized
        for cue in (
            "해봤",
            "해본",
            "해본적",
            "가봤",
            "먹어봤",
            "먹어본",
            "좋아",
            "몇점",
            "할수있",
            "놀수있",
            "갖고놀수있",
            "어때",
            "재밌지",
            "재미있지",
        )
    )


def _is_question_tail_fragment(fragment: str) -> bool:
    normalized = _normalize(fragment)
    if not normalized:
        return True
    if normalized in {"이게임", "그게임", "이거", "그거"}:
        return True
    return any(
        cue in normalized
        for cue in (
            "뭐가궁금",
            "무엇이궁금",
            "뭐궁금",
            "짱이지",
            "잘하지",
            "잘했지",
            "대단하지",
            "멋지지",
            "멋있지",
            "신기하지",
            "재밌지",
            "재미있지",
        )
    )


def _is_topic_refusal(normalized_answer: str) -> bool:
    return any(
        cue in normalized_answer
        for cue in (
            "게임얘기안할래",
            "게임이야기안할래",
            "게임말안할래",
            "게임그만",
            "다른얘기할래",
            "다른이야기할래",
        )
    )


def _event_from_content(
    content: str,
    *,
    asked_target: QuestionTarget | None,
    emotion: str | None,
) -> str | None:
    cleaned = _clean_phrase(content)
    if cleaned is None:
        return None
    normalized = _normalize(cleaned)
    if asked_target is QuestionTarget.EVENT:
        return cleaned
    if emotion is None and asked_target is not QuestionTarget.MEMORABLE_MOMENT:
        return None
    if not _looks_like_event_content(cleaned):
        return None
    return cleaned


def _is_filled(value: str | None) -> bool:
    return value is not None and bool(value.strip())


def _looks_like_event_content(value: str | None) -> bool:
    if value is None:
        return False
    normalized = _normalize(value)
    return any(cue in normalized for cue in _ACTION_EVENT_CUES)


def _normalize(value: str) -> str:
    return "".join(value.strip().casefold().split())
