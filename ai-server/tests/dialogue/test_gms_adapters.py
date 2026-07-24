"""Tests for GMS-backed question generation and validation adapters."""

from collections import deque

import pytest

from app.dialogue import (
    DiaryQuestionGenerationRequest,
    DiarySlots,
    QuestionGenerationPrompt,
    QuestionTarget,
    QuestionValidationCode,
)
from app.dialogue.gms_adapters import (
    GmsGeneratedQuestionValidator,
    GmsQuestionGenerationProvider,
)


class FakeClient:
    def __init__(self, outputs: list[str]) -> None:
        self.outputs = deque(outputs)
        self.calls = []

    async def complete(self, messages):
        self.calls.append(messages)
        return self.outputs.popleft()


@pytest.mark.asyncio
async def test_question_provider_maps_prompt_roles() -> None:
    client = FakeClient(["질문?"])
    provider = GmsQuestionGenerationProvider(client)

    result = await provider.generate_question(
        QuestionGenerationPrompt(system="system", user="user")
    )

    assert result == "질문?"
    assert [message.role for message in client.calls[0]] == [
        "developer",
        "user",
    ]


@pytest.mark.asyncio
async def test_hard_failure_skips_semantic_gms_call() -> None:
    client = FakeClient([])
    validator = GmsGeneratedQuestionValidator(client)

    result = await validator.validate_question(
        DiaryQuestionGenerationRequest(
            target=QuestionTarget.EVENT,
            current_slots=DiarySlots(),
        ),
        "질문이 아니다",
    )

    assert not result.is_valid
    assert not client.calls


@pytest.mark.asyncio
async def test_combines_valid_semantic_decision() -> None:
    client = FakeClient(['{"valid":true,"issues":[]}'])
    validator = GmsGeneratedQuestionValidator(client)

    result = await validator.validate_question(
        DiaryQuestionGenerationRequest(
            target=QuestionTarget.EMOTION,
            current_slots=DiarySlots(event="블록 놀이"),
        ),
        "그때 어떤 기분이었어?",
    )

    assert result.is_valid
    assert len(client.calls) == 1


@pytest.mark.asyncio
async def test_combines_invalid_semantic_decision() -> None:
    client = FakeClient(
        ['{"valid":false,"issues":["targetMismatch"]}']
    )
    validator = GmsGeneratedQuestionValidator(client)

    result = await validator.validate_question(
        DiaryQuestionGenerationRequest(
            target=QuestionTarget.EMOTION,
            current_slots=DiarySlots(event="블록 놀이"),
        ),
        "오늘 무슨 일이 있었어?",
    )

    assert tuple(issue.code for issue in result.issues) == (
        QuestionValidationCode.TARGET_MISMATCH,
    )
