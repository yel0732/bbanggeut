"""Parse one structured LLM dialogue-turn candidate."""

import json
from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator

from app.dialogue.policy.target import QuestionTarget


class GeneratedDialogueTurnParseErrorCode(StrEnum):
    """Safe structured-output failures without retaining model text."""

    INVALID_JSON = "schemaValidationFailed"
    SCHEMA_VALIDATION_FAILED = "schemaValidationFailed"
    TARGET_MISMATCH = "targetMismatch"
    EVIDENCE_MISMATCH = "bannedExpression"
    MISSING_BRIDGE = "targetMismatch"


@dataclass(frozen=True)
class GeneratedDialogueTurnParseError(ValueError):
    """Rejected generated output represented only by a safe code."""

    code: GeneratedDialogueTurnParseErrorCode

    def __str__(self) -> str:
        return f"generated dialogue turn rejected: {self.code.value}"


class _AcknowledgementEvidencePayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    source: Literal["childAnswer"]
    text: str = Field(min_length=1, max_length=2000, repr=False)

    @field_validator("text")
    @classmethod
    def normalize_text(cls, value: str) -> str:
        return _normalize_spacing(value)


class _GeneratedDialogueTurnPayload(BaseModel):
    model_config = ConfigDict(extra="forbid")

    acknowledgement: str = Field(min_length=1, max_length=40, repr=False)
    bridge: str | None = Field(default=None, max_length=40, repr=False)
    question: str = Field(min_length=1, max_length=49, repr=False)
    target: QuestionTarget
    acknowledgement_evidence: _AcknowledgementEvidencePayload = Field(
        alias="acknowledgementEvidence",
        repr=False,
    )

    @field_validator("acknowledgement", "bridge", "question")
    @classmethod
    def normalize_text(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return _normalize_spacing(value)


@dataclass(frozen=True, repr=False)
class GeneratedDialogueTurnCandidate:
    """Validated components that can be combined only after parsing."""

    acknowledgement: str
    bridge: str | None
    question: str
    target: QuestionTarget
    evidence_text: str
    structured: bool

    @property
    def message(self) -> str:
        return " ".join(
            part
            for part in (
                self.acknowledgement,
                self.bridge,
                self.question,
            )
            if part is not None
        )


def parse_generated_dialogue_turn(
    raw_output: str,
    *,
    expected_target: QuestionTarget,
    child_answer: str | None,
    bridge_required: bool = False,
) -> GeneratedDialogueTurnCandidate:
    """Parse the JSON contract, retaining a safe legacy text transition path."""

    normalized = raw_output.strip()
    if not normalized:
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.SCHEMA_VALIDATION_FAILED
        )

    if not normalized.startswith("{"):
        return _parse_legacy_candidate(normalized, expected_target)

    payload = _load_json_object(normalized)
    try:
        parsed = _GeneratedDialogueTurnPayload.model_validate(payload)
    except ValidationError:
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.SCHEMA_VALIDATION_FAILED
        ) from None

    if parsed.target is not expected_target:
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.TARGET_MISMATCH
        )
    if bridge_required and parsed.bridge is None:
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.MISSING_BRIDGE
        )
    normalized_answer = _normalize_spacing(child_answer or "")
    if (
        not normalized_answer
        or parsed.acknowledgement_evidence.text not in normalized_answer
    ):
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.EVIDENCE_MISMATCH
        )

    return GeneratedDialogueTurnCandidate(
        acknowledgement=parsed.acknowledgement,
        bridge=parsed.bridge,
        question=parsed.question,
        target=parsed.target,
        evidence_text=parsed.acknowledgement_evidence.text,
        structured=True,
    )


def _load_json_object(raw_output: str) -> Mapping[str, object]:
    try:
        payload = json.loads(
            raw_output,
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (json.JSONDecodeError, ValueError):
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.INVALID_JSON
        ) from None
    if not isinstance(payload, Mapping):
        raise GeneratedDialogueTurnParseError(
            GeneratedDialogueTurnParseErrorCode.SCHEMA_VALIDATION_FAILED
        )
    return payload


def _parse_legacy_candidate(
    candidate: str,
    target: QuestionTarget,
) -> GeneratedDialogueTurnCandidate:
    question_start = max(candidate.rfind(". "), candidate.rfind("! "))
    if question_start < 0:
        acknowledgement = "네 이야기를 더 듣고 싶어!"
        question = candidate
    else:
        acknowledgement = candidate[: question_start + 1]
        question = candidate[question_start + 2 :]
    return GeneratedDialogueTurnCandidate(
        acknowledgement=acknowledgement,
        bridge=None,
        question=question,
        target=target,
        evidence_text="",
        structured=False,
    )


def _normalize_spacing(value: str) -> str:
    return " ".join(value.strip().split())


def _reject_duplicate_keys(
    pairs: list[tuple[str, object]],
) -> dict[str, object]:
    payload: dict[str, object] = {}
    for key, value in pairs:
        if key in payload:
            raise ValueError("duplicate JSON key")
        payload[key] = value
    return payload
