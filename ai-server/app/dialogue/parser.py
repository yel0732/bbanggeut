"""Strict parsing for diary slot extraction output."""

import json
from collections.abc import Mapping
from dataclasses import dataclass
from enum import StrEnum
from typing import NoReturn

from pydantic import ValidationError

from app.dialogue.schemas import DiarySlots


class _DuplicateJsonKeyError(ValueError):
    """Internal signal for an ambiguous JSON object."""


_KNOWN_SLOT_LOCATIONS = frozenset(
    {
        field_name
        for field_name in DiarySlots.model_fields
    }
    | {
        field.alias
        for field in DiarySlots.model_fields.values()
        if field.alias is not None
    }
)


class SlotParseErrorCode(StrEnum):
    """Stable failure categories for provider-independent recovery."""

    EMPTY_OUTPUT = "EMPTY_OUTPUT"
    INVALID_JSON = "INVALID_JSON"
    INVALID_ROOT = "INVALID_ROOT"
    SCHEMA_VALIDATION_FAILED = "SCHEMA_VALIDATION_FAILED"


@dataclass(frozen=True)
class SlotValidationIssue:
    """Safe validation detail without the rejected value."""

    location: tuple[str | int, ...]
    error_type: str


class SlotParseError(ValueError):
    """Raised when an extraction output cannot be trusted."""

    def __init__(
        self,
        code: SlotParseErrorCode,
        *,
        issues: tuple[SlotValidationIssue, ...] = (),
        recoverable: bool = True,
    ) -> None:
        super().__init__(code.value)
        self.code = code
        self.issues = issues
        self.recoverable = recoverable


def parse_diary_slots(raw_output: str | Mapping[str, object]) -> DiarySlots:
    """Validate provider output without attempting lossy text repair."""

    payload: object
    if isinstance(raw_output, str):
        payload = _load_json(raw_output)
    elif isinstance(raw_output, Mapping):
        payload = dict(raw_output)
    else:
        _raise_parse_error(SlotParseErrorCode.INVALID_ROOT)

    if not isinstance(payload, dict):
        _raise_parse_error(SlotParseErrorCode.INVALID_ROOT)

    try:
        return DiarySlots.model_validate(
            payload,
            by_alias=True,
            by_name=False,
            strict=True,
        )
    except ValidationError as error:
        issues = tuple(
            SlotValidationIssue(
                location=_sanitize_location(tuple(item["loc"])),
                error_type=item["type"],
            )
            for item in error.errors(
                include_url=False,
                include_context=False,
                include_input=False,
            )
        )
        raise SlotParseError(
            SlotParseErrorCode.SCHEMA_VALIDATION_FAILED,
            issues=issues,
        ) from None


def _load_json(raw_output: str) -> object:
    normalized = raw_output.strip()
    if not normalized:
        _raise_parse_error(SlotParseErrorCode.EMPTY_OUTPUT)

    try:
        return json.loads(
            normalized,
            object_pairs_hook=_reject_duplicate_keys,
        )
    except (json.JSONDecodeError, _DuplicateJsonKeyError):
        _raise_parse_error(SlotParseErrorCode.INVALID_JSON)


def _raise_parse_error(code: SlotParseErrorCode) -> NoReturn:
    raise SlotParseError(code)


def _reject_duplicate_keys(
    pairs: list[tuple[str, object]],
) -> dict[str, object]:
    payload: dict[str, object] = {}
    for key, value in pairs:
        if key in payload:
            raise _DuplicateJsonKeyError
        payload[key] = value
    return payload


def _sanitize_location(
    location: tuple[str | int, ...],
) -> tuple[str | int, ...]:
    return tuple(
        part
        if isinstance(part, int) or part in _KNOWN_SLOT_LOCATIONS
        else "<unknown>"
        for part in location
    )
