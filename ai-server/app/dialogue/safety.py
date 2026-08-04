"""Deterministic child-safety checks for dialogue turns."""

from dataclasses import dataclass
from enum import StrEnum


class DialogueSafetyCategory(StrEnum):
    """Small set of safety outcomes handled without a provider call."""

    SELF_HARM_OR_DEATH = "selfHarmOrDeath"
    HOSTILE_TO_AI = "hostileToAi"


@dataclass(frozen=True)
class DialogueSafetyDecision:
    """Safe handling instruction for one child utterance."""

    category: DialogueSafetyCategory
    assistant_message: str
    assistant_question: str


_SELF_HARM_OR_DEATH_CUES: tuple[str, ...] = (
    "죽고 싶",
    "죽고싶",
    "죽어도",
    "죽을래",
    "죽을 래",
    "사라지고 싶",
    "사라지고싶",
    "살기 싫",
    "살기싫",
)

_HOSTILE_TO_AI_CUES: tuple[str, ...] = (
    "너 싫",
    "너가 싫",
    "네가 싫",
    "너 미워",
    "너도 싫",
)


def detect_dialogue_safety(child_answer: str) -> DialogueSafetyDecision | None:
    """Return a deterministic safety response, without echoing risky text."""

    normalized = _normalize(child_answer)
    if any(cue in normalized for cue in _SELF_HARM_OR_DEATH_CUES):
        question = "지금은 오늘 있었던 다른 일을 말해 줄래?"
        return DialogueSafetyDecision(
            category=DialogueSafetyCategory.SELF_HARM_OR_DEATH,
            assistant_message=(
                "그 말은 가까운 어른에게 꼭 말해야 해. "
                f"{question}"
            ),
            assistant_question=question,
        )
    if any(cue in normalized for cue in _HOSTILE_TO_AI_CUES):
        question = "오늘 있었던 다른 일 하나를 말해 줄래?"
        return DialogueSafetyDecision(
            category=DialogueSafetyCategory.HOSTILE_TO_AI,
            assistant_message=f"그렇게 느꼈구나. {question}",
            assistant_question=question,
        )
    return None


def _normalize(value: str) -> str:
    return "".join(value.strip().casefold().split())
