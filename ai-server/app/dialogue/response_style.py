"""Deterministic child-friendly response snippets for dialogue policy."""

from app.dialogue.policy.target import QuestionTarget


_NEGATIVE_CUES: tuple[str, ...] = (
    "속상",
    "슬퍼",
    "슬펐",
    "화났",
    "화가",
    "아쉬",
    "무서",
    "싫어",
    "넘어질",
    "넘어졌",
    "망가",
    "부서",
    "쓰러",
    "못타",
    "못탔",
    "못했",
    "못봐",
    "못봤",
    "안비켜",
    "안나와",
    "안줘",
)

_POSITIVE_CUES: tuple[str, ...] = (
    "재미",
    "신기",
    "좋았",
    "좋아",
    "행복",
    "기뻤",
    "멋지",
    "예쁘",
    "칭찬",
)

_INVITATION_CUES: tuple[str, ...] = (
    "같이",
    "하자",
    "먹자",
    "놀자",
    "만들자",
    "가자",
    "보자",
    "해보자",
)

_AFFIRMATION_QUESTION_CUES: tuple[str, ...] = (
    "재밌지",
    "재미있지",
    "신기하지",
    "멋지지",
    "좋지",
    "예쁘지",
)

_FOOD_CUES: tuple[str, ...] = (
    "케이크",
    "치킨",
    "라면",
    "과자",
    "빵",
    "사탕",
    "초콜릿",
    "아이스크림",
)


def build_fallback_assistant_message(
    *,
    child_answer: str | None,
    question: str,
    bridge_hint: str | None = None,
    child_question: bool = False,
) -> str:
    """Join one acknowledgement and one question without generic echoing."""

    if child_question:
        reply = safe_child_question_reply(child_answer, bridge_hint)
        bridge = bridge_hint if _safe_bridge_hint(bridge_hint) else None
        if bridge is None or bridge == reply:
            return f"{_exclaim(reply)} {question}"
        return f"{_exclaim(reply)} {_exclaim(bridge)} {question}"
    if bridge_hint:
        return f"{_exclaim(bridge_hint)} {question}"
    return f"{acknowledge_child_answer(child_answer)} {question}"


def acknowledge_child_answer(child_answer: str | None) -> str:
    """Return a short acknowledgement grounded in the latest child answer."""

    normalized = _normalize(child_answer or "")
    if any(cue in normalized for cue in _NEGATIVE_CUES):
        if any(cue in normalized for cue in ("속상", "슬퍼", "슬펐")):
            return "많이 속상했겠다."
        if "화" in normalized:
            return "정말 화가 났겠다."
        if any(
            cue in normalized
            for cue in (
                "못타",
                "못탔",
                "못했",
                "못봐",
                "못봤",
                "안비켜",
                "안나와",
                "안줘",
            )
        ):
            return "많이 아쉬웠겠다."
        if any(cue in normalized for cue in ("부서", "쓰러", "망가")):
            return "갑자기 그렇게 되면 속상할 수 있어."
        return "쉽지 않은 일이었겠다."
    if any(cue in normalized for cue in _POSITIVE_CUES):
        if "칭찬" in normalized:
            return "칭찬을 받아서 정말 좋았겠다!"
        if any(cue in normalized for cue in ("신기", "멋지", "예쁘")):
            return "정말 신기했겠다!"
        if "행복" in normalized:
            return "정말 행복했겠다!"
        return "재미있었겠다!"
    if is_child_invitation(child_answer):
        return "같이 하자고 말해줘서 고마워."
    return "그 이야기를 들려줘서 고마워."


def closing_message(
    *,
    child_answer: str | None,
    child_question: bool = False,
    bridge_hint: str | None = None,
) -> str:
    """Build a natural terminal message for the UI diary-consent flow."""

    if child_question:
        prefix = safe_child_question_reply(child_answer, bridge_hint)
    elif is_child_invitation(child_answer):
        prefix = invitation_reply(child_answer)
    else:
        prefix = acknowledge_child_answer(child_answer)
    return (
        f"{_sentence(prefix)} 오늘 이야기해줘서 고마워. "
        "오늘 이야기를 일기로 남겨볼래?"
    )


def optional_final_question() -> str:
    """One optional prompt after required diary slots are filled."""

    return "마지막으로 나한테 하고 싶은 말 있어?"


def soft_continue_question() -> str:
    """A softer extra turn before announcing the last turn."""

    return "조금 더 이야기해 줄래?"


def soft_continue_message(child_answer: str | None) -> str:
    """Acknowledge a quick slot completion before one more open turn."""

    return f"{_sentence(acknowledge_child_answer(child_answer))} {soft_continue_question()}"


def optional_final_message(
    *,
    child_answer: str | None,
    question: str | None = None,
    child_question: bool = False,
    bridge_hint: str | None = None,
) -> str:
    """Acknowledge the latest answer before the single optional final prompt."""

    final_question = question or optional_final_question()
    if child_question:
        prefix = safe_child_question_reply(child_answer, bridge_hint)
    elif is_child_invitation(child_answer):
        prefix = invitation_reply(child_answer)
    else:
        prefix = acknowledge_child_answer(child_answer)
    return f"{_sentence(prefix)} {final_question}"


def fallback_question_for_target(target: QuestionTarget) -> str:
    """Question wording that avoids repeated survey-like phrasing."""

    if target is QuestionTarget.EVENT:
        return "오늘 있었던 일 하나를 말해 줄래?"
    if target is QuestionTarget.EMOTION:
        return "그때 마음은 어땠어?"
    if target is QuestionTarget.EMOTION_REASON:
        return "그 마음이 든 이유를 말해 줄래?"
    return optional_final_question()


def safe_child_question_reply(
    child_answer: str | None,
    bridge_hint: str | None = None,
) -> str:
    """Answer common child questions without claiming human experiences."""

    if _safe_bridge_hint(bridge_hint):
        return bridge_hint.strip()
    normalized = _normalize(child_answer or "")
    if "왜마지막" in normalized or "왜끝" in normalized or "벌써끝" in normalized:
        return "아직 더 말하고 싶었구나."
    if "더대화" in normalized or "더얘기" in normalized or "더이야기" in normalized:
        return "좋아, 조금 더 이야기하자."
    if any(cue in normalized for cue in _AFFIRMATION_QUESTION_CUES):
        return "응, 네가 말해주니까 정말 재미있게 느껴져."
    if "어떻게" in normalized or "뭐라고" in normalized or "어떤말" in normalized:
        return "그럴 땐 '나도 타고 싶어'라고 말해볼 수 있어."
    if "먹" in normalized:
        return "나는 먹지는 못해. 그래도 맛있는 이야기는 좋아."
    if any(cue in normalized for cue in _FOOD_CUES):
        return "나는 먹지는 못해. 그래도 맛있는 이야기는 좋아."
    if "좋아" in normalized:
        return "나는 직접 고르긴 어렵지만, 네 이야기를 듣는 게 좋아."
    if "할수있" in normalized or "할줄알" in normalized or "만들수있" in normalized:
        return "나는 직접 하지는 못하지만, 네 이야기를 같이 들을 수 있어."
    if "가봤" in normalized or "봤어" in normalized:
        return "나는 직접 가보지는 못하지만, 네가 본 이야기는 들을 수 있어."
    return "나는 네 이야기를 듣고 있어."


def is_child_invitation(child_answer: str | None) -> bool:
    normalized = _normalize(child_answer or "")
    if "?" in (child_answer or ""):
        return False
    return any(cue in normalized for cue in _INVITATION_CUES)


def invitation_reply(child_answer: str | None) -> str:
    normalized = _normalize(child_answer or "")
    if "먹자" in normalized:
        return "같이 먹자고 말해줘서 고마워. 나는 먹지는 못해도 이야기는 좋아."
    if "만들자" in normalized:
        return "같이 만들자고 말해줘서 고마워. 네가 만든 이야기를 듣고 싶어."
    if "놀자" in normalized:
        return "같이 놀자고 말해줘서 고마워. 네 놀이 이야기를 듣는 건 좋아."
    return "같이 하자고 말해줘서 고마워."


def _safe_bridge_hint(value: str | None) -> bool:
    return (
        value is not None
        and value.strip().startswith("나는")
        and "?" not in value
        and len(value.strip()) <= 40
    )


def _normalize(value: str) -> str:
    return "".join(value.strip().casefold().split())


def _exclaim(value: str) -> str:
    stripped = value.strip()
    if stripped.endswith("."):
        return f"{stripped[:-1]}!"
    if stripped.endswith(("!", "?")):
        return stripped
    return f"{stripped}!"


def _sentence(value: str) -> str:
    stripped = value.strip()
    if stripped.endswith((".", "!", "?")):
        return stripped
    return f"{stripped}."
