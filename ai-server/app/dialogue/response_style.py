"""Deterministic child-friendly response snippets for dialogue policy."""

from app.dialogue.policy.target import QuestionTarget
from app.dialogue.schemas import DialogueTopic, DiarySlots


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
    "놀렸",
    "새치기",
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
    "하자",
    "먹자",
    "놀자",
    "만들자",
    "가자",
    "보자",
    "해보자",
    "만나자",
)

_AFFIRMATION_QUESTION_CUES: tuple[str, ...] = (
    "재밌지",
    "재미있지",
    "신기하지",
    "멋지지",
    "멋있지",
    "좋지",
    "예쁘지",
    "짱이지",
    "잘하지",
    "잘했지",
    "대단하지",
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

_BIRTHDAY_CUES: tuple[str, ...] = (
    "생일",
    "할머니생일",
    "생신",
)

_PLAYGROUND_CUES: tuple[str, ...] = (
    "놀이터",
    "그네",
    "시소",
    "미끄럼틀",
)

_PLAYGROUND_ACTION_CUES: tuple[str, ...] = (
    "타고",
    "탔",
    "놀았",
    "놀고",
    "미끄러",
)

_GAME_CUES: tuple[str, ...] = (
    "게임",
    "점",
    "맞았",
    "과일",
)

_FRUIT_ACTION_CUES: tuple[str, ...] = (
    "과일",
    "자르",
    "잘랐",
    "잘려",
    "흔드",
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
        if any(cue in normalized for cue in ("놀렸", "새치기")):
            return "그건 마음이 안 좋을 수 있어."
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
        if any(cue in normalized for cue in _BIRTHDAY_CUES):
            # 무엇을 먹었는지는 아이가 말해야 안다. 케이크로 단정하면 잡채를
            # 말한 아이에게 케이크 이야기를 돌려주게 된다.
            return "생일이라 더 특별했겠다!"
        if any(cue in normalized for cue in _FOOD_CUES):
            return "맛있어서 정말 좋았겠다!"
        if "칭찬" in normalized:
            return "칭찬을 받아서 정말 좋았겠다!"
        if any(cue in normalized for cue in ("신기", "멋지", "예쁘")):
            return "정말 신기했겠다!"
        if "행복" in normalized:
            return "정말 행복했겠다!"
        return "재미있었겠다!"
    if any(cue in normalized for cue in _BIRTHDAY_CUES):
        return "생일 이야기를 들으니 따뜻해."
    if any(cue in normalized for cue in _FOOD_CUES):
        return "맛있는 걸 먹은 이야기를 해줬구나!"
    # 여기까지 왔다는 것은 아이가 좋았다고도 속상했다고도 말하지 않았다는 뜻이다.
    # 그런 답에 "재미있었겠다"를 붙이면 아이가 말하지 않은 감정을 정해 준다.
    # 아이가 실제로 꺼낸 놀이 기구만 되짚어 구체성은 남긴다.
    if any(cue in normalized for cue in _PLAYGROUND_CUES):
        if any(cue in normalized for cue in _PLAYGROUND_ACTION_CUES):
            if "그네" in normalized:
                return "그네 이야기 해줬구나!"
            if "시소" in normalized:
                return "시소 이야기 해줬구나!"
            if "미끄럼틀" in normalized:
                return "미끄럼틀 이야기 해줬구나!"
            return "놀이터에서 놀았구나!"
        return "놀이터에서 있었던 일을 말해줬구나!"
    if any(cue in normalized for cue in _FRUIT_ACTION_CUES):
        return "과일 자른 이야기를 해줬구나!"
    if is_child_invitation(child_answer):
        return "같이 하자고 말해줘서 고마워."
    return "오늘 그런 일이 있었구나."


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

    return optional_final_question()


def soft_continue_message(child_answer: str | None) -> str:
    """Acknowledge a quick slot completion before one more open turn."""

    return f"{_sentence(acknowledge_child_answer(child_answer))} {soft_continue_question()}"


def tomorrow_plan_question() -> str:
    """A child-friendly closing bridge that is not a diary slot."""

    return "내일은 뭐하고 싶어?"


def contextual_followup_question(
    *,
    slots: DiarySlots,
    topic: DialogueTopic,
) -> str:
    """Ask one more natural question from already collected story context."""

    text = _normalize(
        " ".join(
            value
            for value in (
                slots.event,
                slots.emotion,
                slots.emotion_reason,
                slots.memorable_moment,
            )
            if value
        )
    )
    if topic is DialogueTopic.GAME:
        if any(cue in text for cue in _FRUIT_ACTION_CUES + _GAME_CUES):
            return "그 게임에서 어떤 순간이 제일 재미있었어?"
        return "게임하면서 또 생각나는 일이 있어?"
    if any(cue in text for cue in _PLAYGROUND_CUES):
        return "놀이터에서 또 뭐 했어?"
    if any(cue in text for cue in _FOOD_CUES + _BIRTHDAY_CUES):
        return "그때 또 생각나는 일이 있어?"
    return "오늘 또 나한테 해주고 싶은 이야기 있어?"


def tomorrow_closing_message(child_answer: str | None) -> str:
    """Close after the tomorrow bridge without adding tomorrow facts to slots."""

    normalized = _normalize(child_answer or "")
    if any(cue in normalized for cue in ("몰라", "없어", "없음", "글쎄")):
        return "괜찮아. 오늘 이야기해줘서 고마워. 오늘 이야기를 일기로 남겨볼래?"
    activity = _tomorrow_activity_phrase(child_answer)
    if activity:
        return (
            f"좋아! 내일 {activity} 나서 또 만나자. "
            "오늘 이야기해줘서 고마워. 오늘 이야기를 일기로 남겨볼래?"
        )
    return (
        "좋아! 내일 하고 나서 나한테 또 말해줘. "
        "오늘 이야기를 일기로 남겨볼래?"
    )


def transition_acknowledgement(child_answer: str | None) -> str:
    """Acknowledge a same-turn topic switch without ambiguous fallback wording."""

    normalized = _normalize(child_answer or "")
    if any(cue in normalized for cue in ("속상", "슬퍼", "슬펐")):
        return "많이 속상했겠다."
    if "화" in normalized:
        return "정말 화가 났겠다."
    if any(cue in normalized for cue in _BIRTHDAY_CUES):
        # 생일에 먹은 것이 케이크라는 보장은 없다. 아이가 케이크를 말했을 때만
        # 케이크로 받는다(아래 분기).
        if any(cue in normalized for cue in _FOOD_CUES):
            return "생일에 맛있는 것도 먹었구나!"
        return "생일 이야기를 들려줬구나!"
    if "케이크" in normalized:
        return "케이크 맛있었겠다!"
    if any(cue in normalized for cue in _FOOD_CUES):
        return "맛있는 걸 먹었구나!"
    if any(cue in normalized for cue in _PLAYGROUND_CUES):
        # 무엇을 못 탔는지는 아이가 말한 기구를 따른다. 그네로 고정하면
        # 미끄럼틀을 말한 아이에게 그네 이야기를 돌려주게 된다.
        if any(cue in normalized for cue in ("못타", "못탔", "새치기", "안비켜")):
            return "못 타서 많이 아쉬웠겠다."
        return "놀이터 이야기를 들려줬구나!"
    return acknowledge_child_answer(child_answer)


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

    normalized = _normalize(child_answer or "")
    if "왜마지막" in normalized or "왜끝" in normalized or "벌써끝" in normalized:
        return "아직 더 말하고 싶었구나."
    if "더대화" in normalized or "더얘기" in normalized or "더이야기" in normalized:
        return "좋아, 조금 더 이야기하자."
    if "뭐가궁금" in normalized or "무엇이궁금" in normalized:
        return "네가 직접 해본 이야기가 궁금해."
    if any(cue in normalized for cue in _AFFIRMATION_QUESTION_CUES):
        if any(cue in normalized for cue in ("짱이지", "잘하지", "잘했지", "대단하지")):
            return "응, 정말 대단해!"
        if "멋" in normalized:
            return "응, 정말 멋지다!"
        if "신기" in normalized:
            return "응, 정말 신기하다!"
        return "응, 정말 재미있겠다!"
    if "몇점" in normalized or "점받" in normalized or "점수" in normalized:
        return "나는 점수를 받지는 않지만, 네 점수 이야기는 궁금해."
    if "어떻게" in normalized or "뭐라고" in normalized or "어떤말" in normalized:
        # 무엇에 대한 물음인지 모르는 자리다. 순서를 기다리는 상황으로 단정해
        # "나도 타고 싶어"를 알려주면 전혀 다른 질문에도 그 말이 나간다.
        return "그럴 땐 네 마음을 그대로 말해도 괜찮아."
    if any(cue in normalized for cue in _PLAYGROUND_CUES) and (
        "타봤" in normalized
        or "타본" in normalized
        or "타본적" in normalized
        or "타보" in normalized
    ):
        prefix = (
            "간식도 같이 먹었구나! "
            if "간식" in normalized and "먹" in normalized
            else ""
        )
        if "그네" in normalized:
            return f"{prefix}나는 직접 그네를 타보진 못해도, 네 그네 이야기는 좋아."
        if "시소" in normalized:
            return f"{prefix}나는 직접 시소를 타보진 못해도, 네 시소 이야기는 좋아."
        if "미끄럼틀" in normalized:
            return f"{prefix}나는 직접 미끄럼틀을 타보진 못해도, 네 이야기는 좋아."
        return f"{prefix}나는 직접 타보진 못해도, 네 놀이 이야기는 좋아."
    if any(cue in normalized for cue in _GAME_CUES) and (
        "해봤" in normalized or "해본" in normalized or "했어" in normalized
    ):
        return "나는 직접 게임하진 못하지만, 네가 한 이야기는 들을 수 있어."
    if "먹" in normalized:
        return "나는 먹지는 못해. 그래도 맛있는 이야기는 좋아."
    if any(cue in normalized for cue in _FOOD_CUES):
        return "나는 먹지는 못해. 그래도 맛있는 이야기는 좋아."
    if "좋아" in normalized:
        return "나는 직접 고르긴 어렵지만, 네 이야기를 듣는 게 좋아."
    if (
        "할수있" in normalized
        or "놀수있" in normalized
        or "할줄알" in normalized
        or "만들수있" in normalized
    ):
        return "나는 직접 하지는 못하지만, 네 이야기를 같이 들을 수 있어."
    if "가봤" in normalized or "봤어" in normalized:
        return "나는 직접 가보지는 못하지만, 네가 본 이야기는 들을 수 있어."
    if _safe_bridge_hint(bridge_hint):
        return bridge_hint.strip()
    return "나는 직접 해보진 못해도, 네 마음 이야기는 잘 들을 수 있어."


def _tomorrow_activity_phrase(child_answer: str | None) -> str | None:
    normalized = _normalize(child_answer or "")
    if not normalized:
        return None
    if "그네" in normalized:
        return "그네 타고"
    if "시소" in normalized:
        return "시소 타고"
    if "미끄럼틀" in normalized:
        return "미끄럼틀 타고"
    if "놀이터" in normalized:
        return "놀이터에서 놀고"
    if "게임" in normalized:
        return "게임하고"
    if "케이크" in normalized or "치킨" in normalized or "라면" in normalized:
        return "맛있는 거 먹고"
    if "그림" in normalized or "그리" in normalized:
        return "그림 그리고"
    if "친구" in normalized and "놀" in normalized:
        return "친구랑 놀고"
    return None


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
        and "듣" not in value
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
