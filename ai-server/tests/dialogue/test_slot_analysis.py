"""Tests for GMS slot analysis retry and repair."""

import logging
from collections import deque

import pytest

from app.dialogue import DiaryAnswerAnalysisRequest, DiarySlots
from app.dialogue.question_fallback import (
    QuestionGenerationExecutionPolicy,
)
from app.dialogue.slot_analysis import (
    SlotAnalysisError,
    SlotAnalysisLogContext,
    analyze_answer_with_retry,
)


class FakeClient:
    def __init__(self, outputs) -> None:
        self.outputs = deque(outputs)
        self.calls = []

    async def complete(self, messages):
        self.calls.append(messages)
        output = self.outputs.popleft()
        if isinstance(output, BaseException):
            raise output
        return output


def _request() -> DiaryAnswerAnalysisRequest:
    return DiaryAnswerAnalysisRequest(
        current_question=None,
        child_answer="합성 답변",
        current_slots=DiarySlots(event="기존 사건"),
    )


@pytest.mark.asyncio
async def test_merges_valid_extracted_slots() -> None:
    client = FakeClient(
        [
            (
                '{"event":null,"people":["친구"],"place":null,'
                '"emotion":"신남","emotionReason":null,'
                '"memorableMoment":null,"result":null}'
            )
        ]
    )

    result = await analyze_answer_with_retry(
        _request(),
        client,
        QuestionGenerationExecutionPolicy(1, 2),
        SlotAnalysisLogContext("conversation", "request"),
        logging.getLogger("test.slot.success"),
    )

    assert result.event == "기존 사건"
    assert result.people == ["친구"]
    assert result.emotion == "신남"


@pytest.mark.asyncio
async def test_retries_parse_failure_with_repair_instruction() -> None:
    client = FakeClient(
        [
            "not-json",
            (
                '{"event":null,"people":[],"place":null,'
                '"emotion":"기쁨","emotionReason":null,'
                '"memorableMoment":null,"result":null}'
            ),
        ]
    )

    result = await analyze_answer_with_retry(
        _request(),
        client,
        QuestionGenerationExecutionPolicy(1, 2),
        SlotAnalysisLogContext("conversation", "request"),
        logging.getLogger("test.slot.repair"),
    )

    assert result.emotion == "기쁨"
    assert len(client.calls) == 2
    assert len(client.calls[1]) == 3
    assert "JSON" in client.calls[1][1].content


@pytest.mark.asyncio
async def test_exhausted_parse_failure_raises_safe_error() -> None:
    client = FakeClient(["not-json"])

    with pytest.raises(SlotAnalysisError) as caught:
        await analyze_answer_with_retry(
            _request(),
            client,
            QuestionGenerationExecutionPolicy(1, 1),
            SlotAnalysisLogContext("conversation", "request"),
            logging.getLogger("test.slot.failure"),
        )

    assert "not-json" not in str(caught.value)
