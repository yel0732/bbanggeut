"""Tests for strict answer-relevance classification."""

import json
import logging
from collections import deque

import pytest

from app.dialogue.analysis.answer_relevance import (
    AnswerRelevanceDecision,
    AnswerRelevanceLogContext,
    AnswerRelevanceRequest,
    TurnControlAction,
    TurnIntent,
    build_answer_relevance_prompt,
    classify_answer_relevance_with_retry,
    parse_answer_relevance,
)
from app.dialogue.policy.target import QuestionTarget
from app.dialogue.question.fallback import QuestionGenerationExecutionPolicy
from app.dialogue.schemas import DiarySlots


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


def _request() -> AnswerRelevanceRequest:
    return AnswerRelevanceRequest(
        current_question="그때 어떤 기분이었어?",
        child_answer="그런데 지금은 배가 고파.",
        current_slots=DiarySlots(event="친구와 공놀이"),
    )


def test_prompt_contains_only_required_private_context() -> None:
    prompt = build_answer_relevance_prompt(_request())
    payload = json.loads(prompt.user)

    assert payload["currentQuestion"] == "그때 어떤 기분이었어?"
    assert payload["childAnswer"] == "그런데 지금은 배가 고파."
    assert payload["currentSlots"]["event"] == "친구와 공놀이"
    assert "신체 상태만 말하거나" in prompt.system
    assert "AI를 향한 질문" in prompt.system
    assert "반드시 그 질문에 직접 짧게 답한다" in prompt.system
    assert "화제가 바뀌었구나" in prompt.system
    assert "너는 어떤 공룡을 좋아해?" in prompt.system
    assert "영화가 재미있었어" in prompt.system
    assert "오늘 친구랑 영화를 봤어" in prompt.system
    assert '"answered":true' in prompt.system
    assert "그랬구나" in prompt.system
    assert "배가 고파" not in repr(prompt)


@pytest.mark.parametrize(
    (
        "raw_output",
        "expected_answered",
        "expected_child_question",
        "expected_bridge",
    ),
    [
        (
            (
                '{"answered":true,"childQuestion":false,'
                '"bridgeResponse":null}'
            ),
            True,
            False,
            None,
        ),
        (
            (
                '{"answered":false,"childQuestion":false,'
                '"bridgeResponse":"배가 많이 고픈가 보구나."}'
            ),
            False,
            False,
            "배가 많이 고픈가 보구나.",
        ),
        (
            (
                '{"answered":false,"childQuestion":true,'
                '"bridgeResponse":"나는 티라노사우루스가 멋진 것 같아."}'
            ),
            False,
            True,
            "나는 티라노사우루스가 멋진 것 같아.",
        ),
    ],
)
def test_parses_strict_relevance_decision(
    raw_output: str,
    expected_answered: bool,
    expected_child_question: bool,
    expected_bridge: str | None,
) -> None:
    decision = parse_answer_relevance(raw_output)

    assert decision.answered is expected_answered
    assert decision.child_question is expected_child_question
    assert decision.bridge_response == expected_bridge


def test_parses_stop_intent_with_grounded_evidence() -> None:
    decision = parse_answer_relevance(
        (
            '{"intent":"STOP","answered":false,"childQuestion":false,'
            '"controlAction":"END_SESSION","bridgeResponse":null,'
            '"suggestedTarget":null,"evidenceText":"오늘은 여기까지"}'
        ),
        child_answer="응, 오늘은 여기까지 할래",
        available_targets=(QuestionTarget.EVENT,),
    )

    assert decision.intent is TurnIntent.STOP
    assert decision.control_action is TurnControlAction.END_SESSION
    assert decision.suggested_target is None


def test_rejects_control_intent_without_child_evidence() -> None:
    with pytest.raises(ValueError, match="output rejected"):
        parse_answer_relevance(
            (
                '{"intent":"STOP","answered":false,"childQuestion":false,'
                '"controlAction":"END_SESSION","bridgeResponse":null,'
                '"suggestedTarget":null,"evidenceText":"그만할래"}'
            ),
            child_answer="오늘 최고 점수를 받았어",
            available_targets=(QuestionTarget.EVENT,),
        )


def test_rejects_suggested_target_outside_available_targets() -> None:
    with pytest.raises(ValueError, match="output rejected"):
        parse_answer_relevance(
            (
                '{"intent":"ANSWER","answered":true,"childQuestion":false,'
                '"controlAction":"CONTINUE","bridgeResponse":null,'
                '"suggestedTarget":"emotion","evidenceText":null}'
            ),
            child_answer="수박이 세 개 나왔어",
            available_targets=(QuestionTarget.MEMORABLE_MOMENT,),
        )


@pytest.mark.parametrize(
    "raw_output",
    [
        "",
        "not-json",
        (
            '{"answered":"false","childQuestion":false,'
            '"bridgeResponse":"응."}'
        ),
        (
            '{"answered":false,"childQuestion":false,'
            '"bridgeResponse":"응.","reason":"private"}'
        ),
        (
            '{"answered":true,"answered":false,'
            '"childQuestion":false,"bridgeResponse":null}'
        ),
        (
            '{"answered":true,"childQuestion":true,'
            '"bridgeResponse":null}'
        ),
        (
            '{"answered":true,"childQuestion":false,'
            '"bridgeResponse":"응."}'
        ),
        (
            '{"answered":false,"childQuestion":false,'
            '"bridgeResponse":null}'
        ),
        (
            '{"answered":false,"childQuestion":false,'
            '"bridgeResponse":"왜 그랬어?"}'
        ),
        (
            '{"answered":false,"childQuestion":false,"bridgeResponse":"'
            + ("가" * 41)
            + '"}'
        ),
        "[]",
    ],
)
def test_rejects_invalid_decision(raw_output: str) -> None:
    with pytest.raises(ValueError, match="output rejected"):
        parse_answer_relevance(raw_output)


@pytest.mark.asyncio
async def test_retries_invalid_output_then_returns_decision() -> None:
    client = FakeClient(
        [
            "invalid",
            (
                '{"answered":false,"childQuestion":false,'
                '"bridgeResponse":"배가 많이 고픈가 보구나."}'
            ),
        ]
    )

    result = await classify_answer_relevance_with_retry(
        _request(),
        client,
        QuestionGenerationExecutionPolicy(
            timeout_seconds=1,
            max_attempts=2,
        ),
        AnswerRelevanceLogContext("conversation-1", "request-1"),
        logging.getLogger("test.answer-relevance"),
    )

    assert result == AnswerRelevanceDecision(
        answered=False,
        child_question=False,
        bridge_response="배가 많이 고픈가 보구나.",
    )
    assert "배가 많이 고픈가 보구나." not in repr(result)
    assert len(client.calls) == 2


@pytest.mark.asyncio
async def test_returns_none_after_bounded_failures() -> None:
    client = FakeClient([RuntimeError(), RuntimeError()])

    result = await classify_answer_relevance_with_retry(
        _request(),
        client,
        QuestionGenerationExecutionPolicy(
            timeout_seconds=1,
            max_attempts=2,
        ),
        AnswerRelevanceLogContext("conversation-1", "request-1"),
        logging.getLogger("test.answer-relevance"),
    )

    assert result is None
