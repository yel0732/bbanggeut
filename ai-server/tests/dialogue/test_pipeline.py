"""Tests for stateless T09-T14 dialogue orchestration."""

import logging
from collections import deque

import pytest

from app.dialogue import DiarySlots, QuestionTarget
from app.dialogue.pipeline import (
    DialogueTurnDecision,
    DialogueTurnPipeline,
    DialogueTurnRequest,
)
from app.dialogue.question_fallback import (
    QuestionGenerationExecutionPolicy,
)
from app.dialogue.termination_policy import DialogueTerminationPolicy


class FakeClient:
    def __init__(self, outputs) -> None:
        self.outputs = deque(outputs)
        self.call_count = 0

    async def complete(self, messages):
        self.call_count += 1
        output = self.outputs.popleft()
        if isinstance(output, BaseException):
            raise output
        return output


def _pipeline(client: FakeClient) -> DialogueTurnPipeline:
    return DialogueTurnPipeline(
        client=client,
        execution_policy=QuestionGenerationExecutionPolicy(1, 2),
        termination_policy=DialogueTerminationPolicy(
            required_targets=frozenset(QuestionTarget)
        ),
        logger=logging.getLogger("test.pipeline"),
    )


def _request() -> DialogueTurnRequest:
    return DialogueTurnRequest(
        request_id="request-1",
        conversation_id="conversation-1",
        child_answer="합성 답변",
        current_question=None,
        current_slots=DiarySlots(),
        question_count=0,
        max_question_count=6,
    )


@pytest.mark.asyncio
async def test_updates_slots_and_generates_next_question() -> None:
    client = FakeClient(
        [
            (
                '{"event":"블록 놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null,"result":null}'
            ),
            "그때 어떤 기분이었어?",
            '{"valid":true,"issues":[]}',
        ]
    )

    result = await _pipeline(client).process(_request())

    assert result.decision is DialogueTurnDecision.CONTINUE
    assert result.updated_slots.event == "블록 놀이"
    assert result.assistant_question == "그때 어떤 기분이었어?"
    assert client.call_count == 3


@pytest.mark.asyncio
async def test_terminates_without_question_when_required_slots_are_filled() -> None:
    client = FakeClient(
        [
            (
                '{"event":"놀이","people":[],"place":null,'
                '"emotion":"기쁨","emotionReason":"함께해서",'
                '"memorableMoment":"완성 순간","result":"정리함"}'
            )
        ]
    )

    result = await _pipeline(client).process(_request())

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.termination_reason is not None
    assert result.assistant_question is None
    assert client.call_count == 1


@pytest.mark.asyncio
async def test_terminates_at_question_limit_after_slot_analysis() -> None:
    client = FakeClient(
        [
            (
                '{"event":"놀이","people":[],"place":null,'
                '"emotion":null,"emotionReason":null,'
                '"memorableMoment":null,"result":null}'
            )
        ]
    )
    request = _request().model_copy(
        update={"question_count": 6},
    )

    result = await _pipeline(client).process(request)

    assert result.decision is DialogueTurnDecision.TERMINATE
    assert result.termination_reason is not None
    assert client.call_count == 1
