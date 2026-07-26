"""Tests for GMS-backed question generation and validation adapters."""

from collections import deque

import pytest

from app.dialogue import (
    DiaryQuestionGenerationRequest,
    DiarySlots,
    QuestionGenerationPrompt,
    QuestionRedirectContext,
    QuestionTarget,
    QuestionValidationCode,
)
from app.dialogue.integration.gms import (
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


@pytest.mark.asyncio
async def test_rejects_redirect_that_does_not_answer_child_question() -> None:
    client = FakeClient([])
    validator = GmsGeneratedQuestionValidator(client)

    result = await validator.validate_question(
        DiaryQuestionGenerationRequest(
            target=QuestionTarget.EMOTION,
            current_slots=DiarySlots(event="친구와 영화 보기"),
            redirect_context=QuestionRedirectContext(
                previous_question="영화를 볼 때 기분이 어땠어?",
                child_answer="너는 무슨 영화 좋아해?",
                child_question=True,
                bridge_hint="나는 모험 영화가 좋아.",
            ),
        ),
        (
            "친구랑 영화 봤을 때 어떤 기분이었는지 알려줘. "
            "그나저나, 그때 마음이 어땠는지 말해 줄래?"
        ),
    )

    assert tuple(issue.code for issue in result.issues) == (
        QuestionValidationCode.TARGET_MISMATCH,
    )
    assert not client.calls
