"""Tests for direct replies to a child's side question."""

import logging
from collections import deque

import pytest

from app.dialogue.question.child_reply import (
    ChildQuestionReplyLogContext,
    ChildQuestionReplyRequest,
    generate_child_question_reply_with_retry,
    parse_child_question_reply,
)
from app.dialogue.question.fallback import QuestionGenerationExecutionPolicy


class FakeClient:
    def __init__(self, outputs) -> None:
        self.outputs = deque(outputs)

    async def complete(self, messages):
        output = self.outputs.popleft()
        if isinstance(output, BaseException):
            raise output
        return output


def test_accepts_short_first_person_reply() -> None:
    assert parse_child_question_reply(
        " 나는 재미있는 모험 영화를 좋아! "
    ) == "나는 재미있는 모험 영화를 좋아!"


@pytest.mark.parametrize(
    "raw_output",
    [
        "",
        "모험 영화를 좋아해!",
        "나는 어떤 영화 좋아해?",
        "나는\n모험 영화를 좋아해!",
        "나는 " + ("아" * 40),
    ],
)
def test_rejects_invalid_reply(raw_output: str) -> None:
    with pytest.raises(ValueError, match="reply rejected"):
        parse_child_question_reply(raw_output)


@pytest.mark.asyncio
async def test_retries_invalid_reply() -> None:
    client = FakeClient(
        [
            "무슨 영화 좋아해?",
            "나는 재미있는 모험 영화를 좋아!",
        ]
    )

    result = await generate_child_question_reply_with_retry(
        ChildQuestionReplyRequest(
            child_question="너는 어떤 영화 좋아해?"
        ),
        client,
        QuestionGenerationExecutionPolicy(1, 2),
        ChildQuestionReplyLogContext("conversation-1", "request-1"),
        logging.getLogger("test.child-question-reply"),
    )

    assert result == "나는 재미있는 모험 영화를 좋아!"
